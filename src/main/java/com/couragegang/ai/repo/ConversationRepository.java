package com.couragegang.ai.repo;

import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@Singleton
public final class ConversationRepository {

    private final DataSource dataSource;

    public ConversationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public UUID insert(UUID workspaceId, UUID orgId, UUID userId, String title) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO conversations (workspace_id, org_id, user_id, title, status)
                        VALUES (?, ?, ?, ?, 'active')
                        RETURNING id
                        """)) {
            ps.setObject(1, workspaceId);
            ps.setObject(2, orgId);
            ps.setObject(3, userId);
            ps.setString(4, title);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public List<ConversationRow> listByWorkspace(UUID workspaceId, boolean includeArchived) throws SQLException {
        var sql =
                """
                SELECT id, workspace_id, org_id, user_id, title, status, created_at, updated_at
                FROM conversations
                WHERE workspace_id = ?
                """;
        if (!includeArchived) {
            sql += " AND status = 'active'";
        }
        sql += " ORDER BY updated_at DESC LIMIT 100";
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(sql)) {
            ps.setObject(1, workspaceId);
            var out = new ArrayList<ConversationRow>();
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        }
    }

    public Optional<ConversationRow> findById(UUID workspaceId, UUID id) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, workspace_id, org_id, user_id, title, status, created_at, updated_at
                        FROM conversations
                        WHERE id = ? AND workspace_id = ?
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, workspaceId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    public boolean updateStatus(UUID workspaceId, UUID id, String status) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE conversations
                        SET status = ?, updated_at = now()
                        WHERE id = ? AND workspace_id = ?
                        """)) {
            ps.setString(1, status);
            ps.setObject(2, id);
            ps.setObject(3, workspaceId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(UUID workspaceId, UUID id) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement("DELETE FROM conversations WHERE id = ? AND workspace_id = ?")) {
            ps.setObject(1, id);
            ps.setObject(2, workspaceId);
            return ps.executeUpdate() > 0;
        }
    }

    public int countMessages(UUID conversationId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement("SELECT COUNT(*) FROM messages WHERE conversation_id = ?")) {
            ps.setObject(1, conversationId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public boolean updateTitle(UUID workspaceId, UUID id, String title) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE conversations
                        SET title = ?, updated_at = now()
                        WHERE id = ? AND workspace_id = ?
                        """)) {
            ps.setString(1, title);
            ps.setObject(2, id);
            ps.setObject(3, workspaceId);
            return ps.executeUpdate() > 0;
        }
    }

    public void touch(UUID id) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement("UPDATE conversations SET updated_at = now() WHERE id = ?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    private static ConversationRow map(java.sql.ResultSet rs) throws SQLException {
        return new ConversationRow(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("org_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("title"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    public record ConversationRow(
            UUID id,
            UUID workspaceId,
            UUID orgId,
            UUID userId,
            String title,
            String status,
            Instant createdAt,
            Instant updatedAt) {}
}
