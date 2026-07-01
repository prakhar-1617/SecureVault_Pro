package com.securevault.database;

import com.securevault.config.ConfigurationManager;
import com.securevault.exceptions.SecureVaultException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.stream.Collectors;

/**
 * Singleton that manages the single JDBC {@link Connection} to MySQL.
 *
 * <p><b>Design Decision — No custom connection pool:</b> Production applications
 * use mature libraries like HikariCP rather than reimplementing pooling logic.
 * A single connection is appropriate for a desktop application with a single
 * concurrent user. The connection is lazily created and kept alive with
 * {@link Connection#isValid(int)} checks before each use.
 *
 * <p><b>Design Pattern:</b> Singleton — ensures exactly one database connection
 * throughout the application lifetime, preventing connection leaks.
 *
 * <p><b>Schema Bootstrapping:</b> On first connection, {@code schema.sql} is
 * executed to create all tables if they don't exist (idempotent {@code CREATE TABLE IF NOT EXISTS}).
 *
 * <p><b>Interview talking point:</b>
 * "Why not HikariCP here?" — Desktop app with one user. HikariCP is the
 * right answer for a Spring Boot REST service handling concurrent HTTP requests.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public final class DatabaseManager {

    // ------------------------------------------------------------------ //
    //  Singleton boilerplate
    // ------------------------------------------------------------------ //

    private static volatile DatabaseManager instance;
    private Connection connection;
    private final ConfigurationManager config;

    private DatabaseManager() {
        this.config = ConfigurationManager.getInstance();
        connect();
        bootstrapSchema();
    }

    /**
     * Returns the singleton instance, creating it on first call.
     *
     * @return the single {@code DatabaseManager}
     */
    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------ //
    //  Connection management
    // ------------------------------------------------------------------ //

    /**
     * Opens the JDBC connection using credentials from {@link ConfigurationManager}.
     */
    private void connect() {
        try {
            // MySQL Connector/J auto-registers via ServiceLoader; explicit load kept for clarity
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.connection = DriverManager.getConnection(
                    config.getDbUrl(),
                    config.getDbUser(),
                    config.getDbPassword()
            );
            this.connection.setAutoCommit(true);
            System.out.println("[DatabaseManager] Connected to MySQL successfully.");
        } catch (ClassNotFoundException e) {
            throw new SecureVaultException("MySQL JDBC Driver not found on classpath", e);
        } catch (SQLException e) {
            throw new SecureVaultException("Failed to connect to database: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a valid {@link Connection}, reconnecting if the existing one is stale.
     *
     * <p>Uses {@link Connection#isValid(int)} with a 2-second timeout to detect
     * broken connections (e.g., MySQL server restarted).
     *
     * @return a live JDBC connection
     */
    public Connection getConnection() {
        try {
            if (connection == null || !connection.isValid(2)) {
                System.out.println("[DatabaseManager] Connection lost. Reconnecting...");
                connect();
            }
        } catch (SQLException e) {
            connect();   // Force reconnect on any error
        }
        return connection;
    }

    /**
     * Closes the database connection. Call this on application shutdown.
     */
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("[DatabaseManager] Connection closed.");
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Error closing connection: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Schema bootstrapping
    // ------------------------------------------------------------------ //

    /**
     * Reads and executes {@code schema.sql} from the classpath.
     *
     * <p>All statements use {@code CREATE TABLE IF NOT EXISTS}, making this
     * operation idempotent — safe to run on every startup.
     */
    private void bootstrapSchema() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (in == null) {
                System.err.println("[DatabaseManager] schema.sql not found — skipping bootstrap.");
                return;
            }
            String sql = new BufferedReader(new InputStreamReader(in))
                    .lines()
                    .collect(Collectors.joining("\n"));

            // Execute each statement separated by semicolon
            String[] statements = sql.split(";");
            try (Statement stmt = connection.createStatement()) {
                for (String statement : statements) {
                    // Strip individual comment lines from each statement block
                    // This handles CREATE TABLE blocks that have leading -- comments
                    String stripped = java.util.Arrays.stream(statement.split("\n"))
                            .filter(line -> !line.trim().startsWith("--"))
                            .collect(Collectors.joining("\n"))
                            .trim();
                    if (!stripped.isEmpty()) {
                        stmt.execute(stripped);
                    }
                }
            }
            System.out.println("[DatabaseManager] Schema bootstrapped successfully.");
        } catch (IOException | SQLException e) {
            throw new SecureVaultException("Failed to bootstrap schema: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ //
    //  Helper: execute an update (INSERT / UPDATE / DELETE)
    // ------------------------------------------------------------------ //

    /**
     * Executes a DML statement and returns the generated key (for INSERT).
     *
     * @param sql    parameterized SQL string
     * @param params positional parameters
     * @return generated ID from auto-increment column, or -1 if none
     * @throws SecureVaultException on SQL error
     */
    public long executeUpdate(String sql, Object... params) {
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindParams(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new SecureVaultException("DB update failed: " + e.getMessage(), e);
        }
        return -1L;
    }

    // ------------------------------------------------------------------ //
    //  Helper: prepare statement with bound parameters
    // ------------------------------------------------------------------ //

    /**
     * Creates a {@link PreparedStatement} with bound positional parameters.
     * Callers are responsible for closing the returned statement.
     *
     * @param sql    parameterized SQL
     * @param params positional parameter values
     * @return a ready-to-execute {@link PreparedStatement}
     * @throws SQLException on JDBC error
     */
    public PreparedStatement prepareStatement(String sql, Object... params) throws SQLException {
        PreparedStatement ps = getConnection().prepareStatement(sql);
        bindParams(ps, params);
        return ps;
    }

    // ------------------------------------------------------------------ //
    //  Private helpers
    // ------------------------------------------------------------------ //

    private void bindParams(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                ps.setNull(i + 1, Types.NULL);
            } else if (params[i] instanceof Integer v) {
                ps.setInt(i + 1, v);
            } else if (params[i] instanceof Long v) {
                ps.setLong(i + 1, v);
            } else if (params[i] instanceof Boolean v) {
                ps.setBoolean(i + 1, v);
            } else if (params[i] instanceof byte[] v) {
                ps.setBytes(i + 1, v);
            } else {
                ps.setString(i + 1, params[i].toString());
            }
        }
    }
}
