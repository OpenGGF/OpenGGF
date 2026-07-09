package com.openggf.net.master;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Single-connection SQLite identity store, confined to the master broker loop. */
public final class SqliteIdentityStore implements IdentityStore {
    private final Connection connection;

    public SqliteIdentityStore(Path dbFile) {
        try {
            Path parent = dbFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS identities (
                          fingerprint TEXT PRIMARY KEY, first_seen INTEGER, last_seen INTEGER,
                          display_name TEXT, tier TEXT, clean_rounds INTEGER)""");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS sanctions (
                          id INTEGER PRIMARY KEY AUTOINCREMENT, fingerprint TEXT, type TEXT,
                          reason TEXT, issuer TEXT, issued_at INTEGER, expiry INTEGER)""");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS verdicts (
                          id INTEGER PRIMARY KEY AUTOINCREMENT, fingerprint TEXT,
                          attempt_ref TEXT, input_recording_hash TEXT, result TEXT,
                          verifier_signature TEXT, timestamp INTEGER)""");
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("failed to open identity store at " + dbFile, e);
        }
    }

    @Override
    public Optional<IdentityRecord> find(String fingerprint) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT first_seen, last_seen, display_name, tier, clean_rounds
                FROM identities WHERE fingerprint = ?""")) {
            statement.setString(1, fingerprint);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new IdentityRecord(fingerprint, row.getLong(1),
                        row.getLong(2), row.getString(3), row.getString(4), row.getInt(5)));
            }
        } catch (SQLException e) {
            throw databaseFailure(e);
        }
    }

    @Override
    public void persistOnDurableEvent(String fingerprint, long firstSeenMillis, long nowMillis) {
        execute("""
                INSERT INTO identities
                  (fingerprint, first_seen, last_seen, display_name, tier, clean_rounds)
                VALUES (?, ?, ?, '', 'NEW', 0)
                ON CONFLICT(fingerprint) DO UPDATE SET last_seen = excluded.last_seen""",
                fingerprint, firstSeenMillis, nowMillis);
    }

    @Override
    public void recordCleanRound(String fingerprint, long nowMillis) {
        execute("""
                UPDATE identities SET clean_rounds = clean_rounds + 1, last_seen = ?
                WHERE fingerprint = ?""", nowMillis, fingerprint);
    }

    @Override
    public void resetCleanRounds(String fingerprint) {
        execute("UPDATE identities SET clean_rounds = 0 WHERE fingerprint = ?", fingerprint);
    }

    @Override
    public void setDisplayName(String fingerprint, String displayName) {
        execute("UPDATE identities SET display_name = ? WHERE fingerprint = ?",
                displayName, fingerprint);
    }

    @Override
    public void setTier(String fingerprint, String tier) {
        execute("UPDATE identities SET tier = ? WHERE fingerprint = ?", tier, fingerprint);
    }

    @Override
    public void addSanction(SanctionRecord sanction) {
        execute("""
                INSERT INTO sanctions
                  (fingerprint, type, reason, issuer, issued_at, expiry)
                VALUES (?, ?, ?, ?, ?, ?)""", sanction.fingerprint(), sanction.type(),
                sanction.reason(), sanction.issuer(), sanction.issuedAtMillis(),
                sanction.expiryMillis());
    }

    @Override
    public List<SanctionRecord> activeSanctions(String fingerprint, long nowMillis) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT type, reason, issuer, issued_at, expiry FROM sanctions
                WHERE fingerprint = ? AND expiry > ?""")) {
            statement.setString(1, fingerprint);
            statement.setLong(2, nowMillis);
            try (ResultSet row = statement.executeQuery()) {
                List<SanctionRecord> sanctions = new ArrayList<>();
                while (row.next()) {
                    sanctions.add(new SanctionRecord(fingerprint, row.getString(1),
                            row.getString(2), row.getString(3), row.getLong(4),
                            row.getLong(5)));
                }
                return List.copyOf(sanctions);
            }
        } catch (SQLException e) {
            throw databaseFailure(e);
        }
    }

    @Override
    public int gcInactiveNewIdentities(long inactiveSinceMillis) {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM identities WHERE tier = 'NEW' AND last_seen < ?")) {
            statement.setLong(1, inactiveSinceMillis);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw databaseFailure(e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw databaseFailure(e);
        }
    }

    private void execute(String sql, Object... arguments) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) {
                statement.setObject(i + 1, arguments[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw databaseFailure(e);
        }
    }

    private static IllegalStateException databaseFailure(SQLException cause) {
        return new IllegalStateException("identity store operation failed", cause);
    }
}
