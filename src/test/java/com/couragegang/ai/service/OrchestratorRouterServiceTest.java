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
    void normalizesLegacyToolChainToConnectorTask() {
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
                              "label": "Поиск roadmap"
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
        assertThat(plan.mode()).isEqualTo("connector_chain");
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().get(0).toolName()).isNull();
        assertThat(plan.steps().get(0).task().message()).contains("roadmap");
    }

    @Test
    void parsesConnectorChainFromDeepSeek() {
        when(deepSeek.completeWithSystem(anyString(), anyString()))
                .thenReturn(
                        """
                        {
                          "mode": "connector_chain",
                          "steps": [
                            {
                              "connectorKey": "notion",
                              "task": { "message": "Замени на странице Ideas фразу было на очень", "constraints": { "page_hint": "Ideas" } },
                              "label": "Обновить Ideas"
                            }
                          ],
                          "reasoning": "notion edit"
                        }
                        """);
        var svc = new OrchestratorRouterService(deepSeek, new OrchestratorToolCatalog());
        var plan =
                svc.route(
                        new InternalRouteRequest(
                                "замени в notion", List.of(), List.of("notion"), null));
        assertThat(plan.mode()).isEqualTo("connector_chain");
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().get(0).toolName()).isNull();
        assertThat(plan.steps().get(0).task().message()).contains("Замени");
    }

    @Test
    void computesRequiresPlanApprovalForTwoSteps() {
        var steps =
                List.of(
                        new com.couragegang.ai.api.dto.OrchestratorDtos.PlanStep(
                                "notion", null, null, null, "Шаг 1", null, null),
                        new com.couragegang.ai.api.dto.OrchestratorDtos.PlanStep(
                                "notion", null, null, null, "Шаг 2", null, null));
        assertThat(OrchestratorRouterService.computeRequiresPlanApproval(steps)).isTrue();
    }

    @Test
    void parsesSkipIfAndOnFailureFromDeepSeek() {
        when(deepSeek.completeWithSystem(anyString(), anyString()))
                .thenReturn(
                        """
                        {
                          "mode": "connector_chain",
                          "steps": [
                            {
                              "connectorKey": "notion",
                              "task": { "message": "Найти Ideas" },
                              "label": "Notion",
                              "onFailure": "abort"
                            },
                            {
                              "connectorKey": "trello",
                              "task": { "message": "Создай карточку на доске Roadmap" },
                              "label": "Trello",
                              "skipIf": "priorConnector:notion.failed"
                            }
                          ],
                          "reasoning": "branch"
                        }
                        """);
        var svc = new OrchestratorRouterService(deepSeek, new OrchestratorToolCatalog());
        var plan =
                svc.route(
                        new InternalRouteRequest(
                                "notion then trello",
                                List.of(),
                                List.of("notion", "trello"),
                                null));
        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.steps().get(0).onFailure()).isEqualTo("abort");
        assertThat(plan.steps().get(1).skipIf()).isEqualTo("priorConnector:notion.failed");
    }

    @Test
    void returnsChatWhenNoConnectors() {
        var svc = new OrchestratorRouterService(deepSeek, new OrchestratorToolCatalog());
        var plan = svc.route(new InternalRouteRequest("hi", List.of(), List.of(), null));
        assertThat(plan.mode()).isEqualTo("chat");
        assertThat(plan.steps()).isEmpty();
    }
}
