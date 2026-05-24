package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.couragegang.ai.api.dto.OrchestratorDtos.InternalRouteRequest;
import com.couragegang.ai.integration.DeepSeekClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrchestratorRouterServiceTest {

    @Mock DeepSeekClient deepSeek;

    @Test
    void parsesToolChainFromDeepSeek() {
        when(deepSeek.completeWithSystem(anyString(), anyString()))
                .thenReturn(
                        """
                        {
                          "mode": "tool_chain",
                          "steps": [
                            {
                              "connectorKey": "notion",
                              "toolName": "notion_search",
                              "arguments": { "query": "roadmap" },
                              "label": "Поиск"
                            }
                          ],
                          "reasoning": "search"
                        }
                        """);
        var svc = new OrchestratorRouterService(deepSeek, new OrchestratorToolCatalog());
        var plan =
                svc.route(
                        new InternalRouteRequest(
                                "найди roadmap в notion",
                                List.of(),
                                List.of("notion"),
                                null));
        assertThat(plan.mode()).isEqualTo("tool_chain");
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().get(0).toolName()).isEqualTo("notion_search");
    }

    @Test
    void returnsChatWhenNoConnectors() {
        var svc = new OrchestratorRouterService(deepSeek, new OrchestratorToolCatalog());
        var plan = svc.route(new InternalRouteRequest("hi", List.of(), List.of(), null));
        assertThat(plan.mode()).isEqualTo("chat");
        assertThat(plan.steps()).isEmpty();
    }
}
