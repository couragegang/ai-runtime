package com.couragegang.ai.repo;

import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

@Singleton
public final class MessageRepository {

    private final DataSource dataSource;

    public MessageRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public UUID insert(
            UUID conversationId,
            String role,
            String content,
            String status,
            UUID pendingApprovalId,
            String toolName,
            String connectorKey)
            throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO messages
                            (conversation_id, role, content, status, pending_approval_id, tool_name, connector_key)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """)) {
            ps.setObject(1, conversationId);
            ps.setString(2, role);
            ps.setString(3, content);
            ps.setString(4, status);
            ps.setObject(5, pendingApprovalId);
            ps.setString(6, toolName);
            ps.setString(7, connectorKey);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public List<MessageRow> listByConversation(UUID conversationId, int limit) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, conversation_id, role, content, status, pending_approval_id,
                               tool_name, connector_key, created_at
                        FROM messages
                        WHERE conversation_id = ?
                        ORDER BY created_at DESC
                        LIMIT ?
                        """)) {
            ps.setObject(1, conversationId);
            ps.setInt(2, limit);
            var out = new ArrayList<MessageRow>();
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(
                            new MessageRow(
                                    rs.getObject("id", UUID.class),
                                    rs.getObject("conversation_id", UUID.class),
                                    rs.getString("role"),
                                    rs.getString("content"),
                                    rs.getString("status"),
                                    rs.getObject("pending_approval_id", UUID.class),
                                    rs.getString("tool_name"),
                                    rs.getString("connector_key"),
                                    rs.getTimestamp("created_at").toInstant()));
                }
            }
            out.sort((a, b) -> a.createdAt().compareTo(b.createdAt()));
            return out;
        }
    }

    public List<MessageRow> listByConversation(UUID conversationId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, conversation_id, role, content, status, pending_approval_id,
                               tool_name, connector_key, created_at
                        FROM messages
                        WHERE conversation_id = ?
                        ORDER BY created_at ASC
                        """)) {
            ps.setObject(1, conversationId);
            var out = new ArrayList<MessageRow>();
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MessageRow(
                            rs.getObject("id", UUID.class),
                            rs.getObject("conversation_id", UUID.class),
                            rs.getString("role"),
                            rs.getString("content"),
                            rs.getString("status"),
                            rs.getObject("pending_approval_id", UUID.class),
                            rs.getString("tool_name"),
                            rs.getString("connector_key"),
                            rs.getTimestamp("created_at").toInstant()));
                }
            }
            return out;
        }
    }

    public record MessageRow(
            UUID id,
            UUID conversationId,
            String role,
            String content,
            String status,
            UUID pendingApprovalId,
            String toolName,
            String connectorKey,
            Instant createdAt) {}
}
