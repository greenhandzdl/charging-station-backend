package com.charging.infrastructure;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DDL reload test that drops all tables and re-executes the init.sql schema.
 *
 * <p>This test is <b>disabled by default</b> because it requires a running PostgreSQL
 * instance with the correct database configured. It also requires Redis for application
 * runtime, though Redis is not directly tested here.
 *
 * <h3>How to run:</h3>
 * <ol>
 *   <li>Ensure PostgreSQL 14+ is running on localhost:5432</li>
 *   <li>Create the test database:
 *     <pre>createdb charging_station_test</pre>
 *   </li>
 *   <li>Run the test with system property:
 *     <pre>mvn test -Dtest=DdlReloadTest -Ddb.url=jdbc:postgresql://localhost:5432/charging_station_test -Ddb.user=postgres -Ddb.password=postgres</pre>
 *   </li>
 *   <li>Or set the properties in your IDE run configuration</li>
 * </ol>
 */
@Disabled("Requires PostgreSQL and Redis — run manually against test DB")
class DdlReloadTest {

    private static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/charging_station_test";
    private static final String DEFAULT_DB_USER = System.getProperty("db.user", "postgres");
    private static final String DEFAULT_DB_PASS = System.getProperty("db.password", "postgres");

    @Test
    void clearAndReloadDdl() throws Exception {
        String dbUrl = System.getProperty("db.url", DEFAULT_DB_URL);

        try (Connection conn = DriverManager.getConnection(dbUrl, DEFAULT_DB_USER, DEFAULT_DB_PASS);
             Statement stmt = conn.createStatement()) {

            // 1. Drop all tables in reverse dependency order (respecting FK constraints)
            String[] dropTables = {
                    "DROP TABLE IF EXISTS password_history CASCADE",
                    "DROP TABLE IF EXISTS audit_logs CASCADE",
                    "DROP TABLE IF EXISTS repairs CASCADE",
                    "DROP TABLE IF EXISTS payments CASCADE",
                    "DROP TABLE IF EXISTS charge_records CASCADE",
                    "DROP TABLE IF EXISTS chargers CASCADE",
                    "DROP TABLE IF EXISTS stations CASCADE",
                    "DROP TABLE IF EXISTS users CASCADE",
            };
            for (String ddl : dropTables) {
                stmt.execute(ddl);
            }

            // 2. Drop views
            stmt.execute("DROP VIEW IF EXISTS v_user_charge_records CASCADE");
            stmt.execute("DROP VIEW IF EXISTS v_daily_charge_stats CASCADE");
            stmt.execute("DROP VIEW IF EXISTS v_charger_usage_rate CASCADE");

            // 3. Drop trigger function
            stmt.execute("DROP FUNCTION IF EXISTS prevent_audit_logs_modification() CASCADE");

            // 4. Read and execute init.sql from classpath
            String initSql;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("init.sql")) {
                assertNotNull(is, "init.sql not found on classpath");
                initSql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Split by semicolons and execute each statement
            String[] statements = initSql.split(";");
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    stmt.execute(trimmed);
                }
            }

            // 5. Run seed data INSERTs
            String seedSql;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("seed.sql")) {
                assertNotNull(is, "seed.sql not found on classpath");
                seedSql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            String[] seedStatements = seedSql.split(";");
            for (String sql : seedStatements) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    stmt.execute(trimmed);
                }
            }

            // 6. Verify key tables exist and have expected columns
            DatabaseMetaData meta = conn.getMetaData();

            // Check chargers table
            try (ResultSet rs = meta.getColumns(null, null, "chargers", null)) {
                boolean hasOnlineStatus = false;
                boolean hasChargerCode = false;
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    if ("online_status".equals(colName)) hasOnlineStatus = true;
                    if ("charger_code".equals(colName)) hasChargerCode = true;
                }
                assertTrue(hasOnlineStatus, "chargers table should have online_status column");
                assertTrue(hasChargerCode, "chargers table should have charger_code column");
            }

            // Check users table
            try (ResultSet rs = meta.getColumns(null, null, "users", null)) {
                boolean hasPhone = false;
                boolean hasBalance = false;
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    if ("phone".equals(colName)) hasPhone = true;
                    if ("balance".equals(colName)) hasBalance = true;
                }
                assertTrue(hasPhone, "users table should have phone column");
                assertTrue(hasBalance, "users table should have balance column");
            }

            // 7. Verify default online_status is 'OFFLINE' by inserting a charger
            // without specifying online_status and checking the default applies
            stmt.execute("INSERT INTO stations (id, name, status) VALUES " +
                    "('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Test Station', 'NORMAL')");
            stmt.execute("INSERT INTO chargers (id, station_id, charger_code, type, status) VALUES " +
                    "('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', " +
                    "'TEST-001', 'FAST', 'IDLE')");

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT online_status FROM chargers WHERE charger_code = 'TEST-001'")) {
                assertTrue(rs.next(), "Should find the test charger");
                assertEquals("OFFLINE", rs.getString("online_status"),
                        "Default online_status should be 'OFFLINE'");
            }

            // 8. Verify audit_logs action CHECK includes 'PLUG_IN'
            // Insert an audit log with PLUG_IN action — should succeed if CHECK allows it
            stmt.execute("INSERT INTO audit_logs (id, action) VALUES " +
                    "('cccccccc-cccc-cccc-cccc-cccccccccccc', 'PLUG_IN')");

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT action FROM audit_logs WHERE id = 'cccccccc-cccc-cccc-cccc-cccccccccccc'")) {
                assertTrue(rs.next(), "Should find the PLUG_IN audit log");
                assertEquals("PLUG_IN", rs.getString("action"),
                        "audit_logs action CHECK should allow 'PLUG_IN'");
            }
        }
    }
}
