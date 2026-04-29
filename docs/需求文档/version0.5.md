以下是 **SkillHub（技能中心）** 模块的完整设计方案，涵盖表结构、接口设计、技能存储、与智能体集成方式及权限预留。

---

## 1. 模块定位

SkillHub 用于管理**非 Java 原生 @Tool 注解的外部技能**，包括脚本类（Python、Shell）、HTTP API 调用、自定义指令集等。技能元数据存储在 MySQL，脚本/附件文件存储在 MinIO。技能可被智能体发现并调用，支持未来基于角色的权限控制。

---

## 2. 数据库设计 (MySQL)

```sql
CREATE TABLE skill (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_code      VARCHAR(64)  NOT NULL COMMENT '技能唯一标识，如 pdf_parser_v1',
    name            VARCHAR(128) NOT NULL COMMENT '技能名称',
    description     VARCHAR(512) NOT NULL COMMENT '简短描述，供模型理解用途',
    detail          TEXT         COMMENT '详细说明、使用示例、注意事项',
    version         VARCHAR(32)  NOT NULL COMMENT '版本号，如 1.0.0',
    operation_type  VARCHAR(32)  NOT NULL COMMENT '操作类型：script / http_api / workflow 等',
    security_rating VARCHAR(16)  NOT NULL DEFAULT 'low' COMMENT '安全评级：low / medium / high / critical',
    env_requirement VARCHAR(256) COMMENT '需要的运行环境，如 Python3.9+, jq, curl',
    skill_level     INT          NOT NULL DEFAULT 1 COMMENT '技能等级：1-公开, 2-认证用户, 3-管理员，用于未来RBAC',
    is_active       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    minio_path      VARCHAR(512) COMMENT 'MinIO存储路径，如 skills/pdf_parser/1.0.0/',
    metadata_json   JSON         COMMENT '额外自定义元数据，如参数schema、返回值说明',
    del_flag        TINYINT(1)   NOT NULL DEFAULT 0,
    create_by       VARCHAR(64),
    update_by       VARCHAR(64),
    update_at       DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
    create_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_skill_code_version (skill_code, version)
) COMMENT '技能元数据表';
```

字段说明：
- `skill_code` + `version` 联合唯一，支持多版本共存。
- `metadata_json` 可存放提供给大模型的 Function Description JSON（若操作类型为 `script` 则可定义参数）。
- `minio_path` 为技能文件在 MinIO 中的目录前缀，脚本文件以文件名区分（如 `main.py`、`requirements.txt`）。
- `skill_level` 为未来 RBAC 预留，目前默认 1。

---

## 3. MinIO 存储规划

技能相关文件存储在 bucket `skillhub`，目录结构：
```
skillhub/
  {skill_code}/
    {version}/
      main.py          (主执行脚本)
      requirements.txt (可选)
      README.md        (可选)
      其他资源文件...
```
上传时，前端可打包为 `.zip`，服务端解压后按目录上传；或直接上传单个脚本文件，此时服务端自动生成版本文件夹。

下载接口返回该版本文件夹的压缩包。

---

## 4. HTTP 接口设计

Base URL: `/api/v1/skills`

### 4.1 技能列表查询
- `GET /api/v1/skills`
- 参数：
    - `search`：按名称、描述、skill_code 模糊搜索
    - `operation_type`：筛选操作类型
    - `security_rating`：按安全评级筛选
    - `skill_level`：按等级筛选（权限不足时不返回高等级技能）
    - `is_active`：默认返回启用的（可选参数，管理员可查全部）
    - `page`, `size`：分页
- 返回分页结果，包含 `id`, `skill_code`, `name`, `description`, `version`, `operation_type`, `security_rating`, `skill_level`, `is_active`, `create_at`。

### 4.2 技能详情
- `GET /api/v1/skills/{id}`
- 返回完整字段，包括 `detail`, `env_requirement`, `metadata_json`，可选返回 `minio_path`（或提供文件列表）。

### 4.3 技能注册/上传
- `POST /api/v1/skills`
- Content-Type: `multipart/form-data`
- 表单字段：
    - `skill_code` (必填)
    - `name` (必填)
    - `description` (必填)
    - `detail` (可选)
    - `version` (必填)
    - `operation_type` (必填)
    - `security_rating` (可选，默认 low)
    - `env_requirement` (可选)
    - `skill_level` (必填，默认1)
    - `metadata_json` (可选，JSON字符串)
    - `file` (可选，附件，若为脚本则上传 .zip 或 .py 文件)
- 处理逻辑：
    - 校验 `skill_code` + `version` 唯一性。
    - 若有附件文件，上传至 MinIO 对应路径（`{skill_code}/{version}/`），保存 `minio_path`。
    - 保存元数据至 MySQL。
- 返回：`{id, skill_code, version}`。

### 4.4 更新技能
- `PUT /api/v1/skills/{id}`
- Content-Type: `application/json` (元数据) 或 `multipart/form-data` (含文件)
- 可更新字段：`name`, `description`, `detail`, `security_rating`, `env_requirement`, `skill_level`, `is_active`, `metadata_json`；若需更新附件文件，则使用 `multipart` 并传入 `file`，会替换该版本目录下的文件。
- 注意：`skill_code` 和 `version` 不可更改（若需改变应新建技能）。
- 逻辑删除统一使用 `del_flag`。

### 4.5 删除技能
- `DELETE /api/v1/skills/{id}`
- 逻辑删除 MySQL 记录（`del_flag=1`），MinIO 文件保留（或异步清理）。可选提供物理删除接口（管理员专用）。

### 4.6 下载技能附件
- `GET /api/v1/skills/{id}/download`
- 从 MinIO 打包该版本的文件夹为 zip 流返回，若无附件则返回 404。

### 4.7 获取可用技能列表（供大模型/内部使用）
- `GET /api/v1/skills/active`
- 返回所有 `is_active=1` 且 `del_flag=0` 的技能，仅包含关键字段：`skill_code`, `name`, `description`, `version`, `operation_type`, `metadata_json`（用于生成 Function Calling 描述）。
- 此接口无需认证（内部调用），也可由智能体服务直接通过服务层查询。

---

## 5. 与智能体集成

### 5.1 技能转换为工具描述
智能体加载工具时，除了扫描 `@Tool` 注解的方法，还需从 SkillHub 获取活跃技能列表，将它们转换为 LangChain4j 的 `ToolSpecification` 或 `ChatModel` 可识别的函数描述。

实现方式：
- 定义 `SkillToolSpecificationService`，在 Agent 初始化或每次对话前，调用 `skillService.getActiveSkills()` 获取技能列表。
- 对每个技能，若 `metadata_json` 中包含自定义的 function 描述（JSON Schema 格式），则直接使用；否则根据 `operation_type` 生成默认描述。
- 对于 `script` 类型，可生成一个固定名称的函数 `execute_skill_{skill_code}`，参数由 `metadata_json` 定义（如无则仅接受 `input` 字符串）。
- 对于 `http_api` 类型，可能需要更复杂的调用步骤。

为防止权限问题，根据当前用户等级过滤 `skill_level` 高于用户等级的技能。

### 5.2 技能执行框架
当大模型决定调用某个技能时，传递函数名和参数给 `SkillExecutionService`：
1. 根据 `skill_code` 和版本（可能携带版本）查找技能记录。
2. 检查安全评级、等级权限。
3. 若为 `script` 类型：
    - 从 MinIO 下载脚本文件到临时目录。
    - 根据 `env_requirement` 检查或准备执行环境（如 Docker 容器或子进程）。
    - 执行脚本并传递参数（如通过命令行参数或 stdin JSON）。
    - 捕获输出，返回结果文本。
    - 清理临时文件。
4. 若为 `http_api` 类型：
    - 根据 `metadata_json` 中预设的模板发送 HTTP 请求，返回响应。
5. 返回结果给大模型。

### 5.3 安全考量
- `security_rating` 为 high/critical 的技能可要求用户确认后才执行。
- 脚本执行应使用沙箱环境（Docker），并设置超时、网络限制。
- 所有技能调用应记录审计日志。

---

## 6. 与记忆系统的交互

第四期的记忆相关技能（如记忆存储、记忆查询）可以作为 Java 原生 @Tool 实现，不需要放入 SkillHub。若未来有基于脚本的增强记忆处理，则可注册为 skill。

---

## 7. 代码结构建议

```
skillhub/
├── controller/
│   └── SkillController.java          (CRUD接口)
├── service/
│   ├── SkillService.java             (元数据管理)
│   ├── SkillStorageService.java      (MinIO文件操作)
│   ├── SkillActivationService.java   (转换工具描述)
│   └── SkillExecutionService.java    (技能执行调度)
├── repository/
│   └── SkillRepository.java          (MyBatis)
├── model/
│   └── Skill.java
├── config/
│   └── SkillHubConfig.java
└── dto/
    ├── SkillCreateRequest.java
    ├── SkillQueryRequest.java
    └── SkillVO.java
```

---

## 8. 技能等级与未来 RBAC

- `skill_level` 与用户角色挂钩。当前未实现 RBAC 时，默认所有用户为 level 1，只能访问等级 ≤ 1 的技能。
- 后续引入角色后，用户 → 角色 → 权限等级，控制在技能查询和执行时过滤。

---

该设计完整支持 SkillHub 的注册、管理、下载、与智能体集成，具备可扩展性，并预留了安全与权限控制。如需进一步细化脚本执行沙箱、Docker 集成等实现细节，可继续讨论。