package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.ai.api.dto.ConversationDtos.ConversationCreateRequest;
import com.couragegang.ai.repo.ConversationRepository;
import com.couragegang.ai.repo.MessageRepository;
import io.micronaut.http.exceptions.HttpStatusException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    ConversationRepository conversations;

    @Mock
    MessageRepository messages;

    @Mock
    LlmService llm;

    ConversationService svc;
    UUID wsId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID convId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new ConversationService(conversations, messages, llm);
    }

    @Test
    void listMapsRows() throws Exception {
        when(conversations.listByWorkspace(wsId, false))
                .thenReturn(List.of(row("Active")));
        var res = svc.list(wsId, false);
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().getFirst().title()).isEqualTo("Active");
    }

    @Test
    void createUsesDefaultTitle() throws Exception {
        when(conversations.insert(wsId, orgId, userId, "Новый чат")).thenReturn(convId);
        when(conversations.findById(wsId, convId)).thenReturn(Optional.of(row("Новый чат")));
        var view = svc.create(wsId, orgId, userId, new ConversationCreateRequest(wsId, null));
        assertThat(view.title()).isEqualTo("Новый чат");
    }

    @Test
    void createTrimsCustomTitle() throws Exception {
        when(conversations.insert(wsId, orgId, userId, "Custom")).thenReturn(convId);
        when(conversations.findById(wsId, convId)).thenReturn(Optional.of(row("Custom")));
        svc.create(wsId, orgId, userId, new ConversationCreateRequest(wsId, "  Custom  "));
        verify(conversations).insert(wsId, orgId, userId, "Custom");
    }

    @Test
    void deleteNotFound() throws Exception {
        when(conversations.delete(wsId, convId)).thenReturn(false);
        assertThatThrownBy(() -> svc.delete(wsId, convId)).isInstanceOf(HttpStatusException.class);
    }

    @Test
    void listMessagesNotFound() throws Exception {
        when(conversations.findById(wsId, convId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.listMessages(wsId, convId)).isInstanceOf(HttpStatusException.class);
    }

    @Test
    void ensureConversationCreatesWhenMissing() throws Exception {
        var ctx = new ConversationService.ChatContext(wsId, orgId, userId, null, "hi");
        when(conversations.insert(wsId, orgId, userId, "Новый чат")).thenReturn(convId);
        assertThat(svc.ensureConversation(ctx)).isEqualTo(convId);
    }

    @Test
    void ensureConversationCreatesWhenIdMissing() throws Exception {
        var ctx = new ConversationService.ChatContext(wsId, orgId, userId, convId, "hi");
        when(conversations.findById(wsId, convId)).thenReturn(Optional.empty());
        when(conversations.insert(wsId, orgId, userId, "Новый чат")).thenReturn(UUID.randomUUID());
        assertThat(svc.ensureConversation(ctx)).isNotNull();
    }

    @Test
    void ensureConversationReusesExisting() throws Exception {
        var ctx = new ConversationService.ChatContext(wsId, orgId, userId, convId, "hi");
        when(conversations.findById(wsId, convId)).thenReturn(Optional.of(row("x")));
        assertThat(svc.ensureConversation(ctx)).isEqualTo(convId);
    }

    @Test
    void generateAndUpdateTitle() throws Exception {
        when(llm.generateConversationTitle("first")).thenReturn("Short title");
        when(conversations.updateTitle(wsId, convId, "Short title")).thenReturn(true);
        assertThat(svc.generateAndUpdateTitle(wsId, convId, "first")).contains("Short title");
    }

    @Test
    void generateTitleEmptyWhenUpdateFails() throws Exception {
        when(llm.generateConversationTitle("first")).thenReturn("T");
        when(conversations.updateTitle(wsId, convId, "T")).thenReturn(false);
        assertThat(svc.generateAndUpdateTitle(wsId, convId, "first")).isEmpty();
    }

    @Test
    void listMessagesReturnsRows() throws Exception {
        when(conversations.findById(wsId, convId)).thenReturn(Optional.of(row("T")));
        when(messages.listByConversation(convId))
                .thenReturn(
                        List.of(
                                new MessageRepository.MessageRow(
                                        UUID.randomUUID(),
                                        convId,
                                        "user",
                                        "hi",
                                        null,
                                        null,
                                        null,
                                        null,
                                        Instant.now())));
        assertThat(svc.listMessages(wsId, convId).items()).hasSize(1);
    }

    @Test
    void archiveConversation() throws Exception {
        when(conversations.updateStatus(wsId, convId, "archived")).thenReturn(true);
        when(conversations.findById(wsId, convId))
                .thenReturn(Optional.of(new ConversationRepository.ConversationRow(
                        convId, wsId, orgId, userId, "T", "archived", Instant.now(), Instant.now())));
        var view = svc.archive(wsId, convId);
        assertThat(view.status()).isEqualTo("archived");
    }

    @Test
    void appendMessagesTouchesConversation() throws Exception {
        svc.appendUserMessage(convId, "u");
        verify(messages).insert(eq(convId), eq("user"), eq("u"), any(), any(), any(), any());
        verify(conversations).touch(convId);
    }

    @Test
    void appendAssistantMessageTouchesConversation() throws Exception {
        var pending = UUID.randomUUID();
        svc.appendAssistantMessage(convId, "done", "completed", pending, "notion_write_page", "notion");
        verify(messages)
                .insert(
                        eq(convId),
                        eq("assistant"),
                        eq("done"),
                        eq("completed"),
                        eq(pending),
                        eq("notion_write_page"),
                        eq("notion"));
        verify(conversations).touch(convId);
    }

    @Test
    void countMessagesReturnsValue() throws Exception {
        when(conversations.countMessages(convId)).thenReturn(3);
        assertThat(svc.countMessages(convId)).isEqualTo(3);
    }

    @Test
    void historyForLlmTruncatesToMax() throws Exception {
        when(messages.listByConversation(convId))
                .thenReturn(
                        List.of(
                                msg("user", "1"),
                                msg("user", "2"),
                                msg("assistant", "3")));
        var history = svc.historyForLlm(convId, 2);
        assertThat(history).hasSize(2);
        assertThat(history.getFirst().content()).isEqualTo("2");
    }

    @Test
    void listIncludesArchived() throws Exception {
        when(conversations.listByWorkspace(wsId, true)).thenReturn(List.of(row("Archived")));
        assertThat(svc.list(wsId, true).items()).hasSize(1);
    }

    @Test
    void archiveNotFound() throws Exception {
        when(conversations.updateStatus(wsId, convId, "archived")).thenReturn(false);
        assertThatThrownBy(() -> svc.archive(wsId, convId)).isInstanceOf(HttpStatusException.class);
    }

    private MessageRepository.MessageRow msg(String role, String content) {
        return new MessageRepository.MessageRow(
                UUID.randomUUID(), convId, role, content, null, null, null, null, Instant.now());
    }

    private ConversationRepository.ConversationRow row(String title) {
        var now = Instant.now();
        return new ConversationRepository.ConversationRow(
                convId, wsId, orgId, userId, title, "active", now, now);
    }
}
