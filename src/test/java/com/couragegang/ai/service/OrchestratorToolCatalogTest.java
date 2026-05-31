package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class OrchestratorToolCatalogTest {

    private final OrchestratorToolCatalog catalog = new OrchestratorToolCatalog();

    @Test
    void toolsForRouterExcludesDelegatedConnectors() {
        var routerTools = catalog.toolsForRouter(Set.of("notion", "trello"));
        assertThat(routerTools).isEmpty();
    }

    @Test
    void toolsForConnectorsIncludesHitlDefinitions() {
        var all = catalog.toolsForConnectors(Set.of("notion"));
        assertThat(all).extracting(OrchestratorToolCatalog.ToolDefinition::toolName)
                .contains("notion_search", "notion_write_page", "notion_edit_block", "notion_delete_page");
    }

    @Test
    void hasConnectorWorkflow() {
        assertThat(OrchestratorToolCatalog.hasConnectorWorkflow("notion")).isTrue();
        assertThat(OrchestratorToolCatalog.hasConnectorWorkflow("trello")).isTrue();
        assertThat(OrchestratorToolCatalog.hasConnectorWorkflow("github")).isFalse();
    }

    @Test
    void toolsForConnectorsIncludesTrello() {
        var all = catalog.toolsForConnectors(Set.of("trello"));
        assertThat(all).extracting(OrchestratorToolCatalog.ToolDefinition::toolName)
                .contains(
                        "trello_search_cards",
                        "trello_create_card",
                        "trello_add_comment",
                        "trello_list_lists",
                        "trello_create_list",
                        "trello_rename_list",
                        "trello_archive_list",
                        "trello_move_card",
                        "trello_delete_card");
        assertThat(catalog.connectorCapabilities(Set.of("trello")))
                .extracting(OrchestratorToolCatalog.ConnectorCapability::connectorKey)
                .containsExactly("trello");
    }
}
