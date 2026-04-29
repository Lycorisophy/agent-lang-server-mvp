package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ChatConstants;
import cn.lysoy.agentlangservermvp.model.InnerMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InnerMessageCompressSplitRoundsTest {

    @Test
    void splitUserRounds_groupsByUserAnchor() {
        List<InnerMessage> list = new ArrayList<>();
        list.add(msg(ChatConstants.ROLE_SYSTEM, "总结A"));
        list.add(msg(ChatConstants.ROLE_USER, "问1"));
        list.add(msg(ChatConstants.ROLE_ASSISTANT_REPLY, "答1"));
        list.add(msg(ChatConstants.ROLE_USER, "问2"));
        list.add(msg(ChatConstants.ROLE_ASSISTANT_THOUGHT, "想2"));
        list.add(msg(ChatConstants.ROLE_ASSISTANT_REPLY, "答2"));

        var rounds = InnerMessageCompressServiceImpl.splitUserRounds(list);
        assertThat(rounds).hasSize(3);
        assertThat(rounds.get(0)).hasSize(1);
        assertThat(rounds.get(0).get(0).getRole()).isEqualTo(ChatConstants.ROLE_SYSTEM);
        assertThat(rounds.get(1)).hasSize(2);
        assertThat(rounds.get(2)).hasSize(3);
    }

    private static InnerMessage msg(String role, String content) {
        InnerMessage m = new InnerMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}
