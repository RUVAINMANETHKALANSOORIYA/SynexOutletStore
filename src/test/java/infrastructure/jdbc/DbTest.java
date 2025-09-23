package infrastructure.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class DbTest {

    @Test
    @DisplayName("Get database connection successfully")
    void get_database_connection() {
        assertDoesNotThrow(() -> {
            Connection connection = Db.get(); // Fixed method name
            assertNotNull(connection);
            assertFalse(connection.isClosed());
            connection.close();
        });
    }

    @Test
    @DisplayName("Multiple connections can be created")
    void multiple_connections() throws SQLException {
        Connection conn1 = null;
        Connection conn2 = null;

        try {
            conn1 = Db.get(); // Fixed method name
            conn2 = Db.get(); // Fixed method name

            assertNotNull(conn1);
            assertNotNull(conn2);
            assertNotSame(conn1, conn2);
            assertFalse(conn1.isClosed());
            assertFalse(conn2.isClosed());

        } finally {
            if (conn1 != null) conn1.close();
            if (conn2 != null) conn2.close();
        }
    }

    @Test
    @DisplayName("Connection auto-commit is enabled by default")
    void connection_auto_commit_default() throws SQLException {
        try (Connection connection = Db.get()) { // Fixed method name
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    @DisplayName("Connection can be used for transactions")
    void connection_transaction_support() throws SQLException {
        try (Connection connection = Db.get()) { // Fixed method name
            connection.setAutoCommit(false);
            assertFalse(connection.getAutoCommit());

            // Test rollback capability
            assertDoesNotThrow(() -> {
                connection.rollback();
                connection.setAutoCommit(true);
            });
        }
    }

    @Test
    @DisplayName("Connection metadata is accessible")
    void connection_metadata() throws SQLException {
        try (Connection connection = Db.get()) { // Fixed method name
            var metadata = connection.getMetaData();
            assertNotNull(metadata);
            assertNotNull(metadata.getDatabaseProductName());
        }
    }

    @Test
    @DisplayName("Connection catalog can be retrieved")
    void connection_catalog() throws SQLException {
        try (Connection connection = Db.get()) { // Fixed method name
            String catalog = connection.getCatalog();
            // Catalog might be null in some configurations, so just test it doesn't throw
            assertDoesNotThrow(() -> connection.setCatalog(catalog));
        }
    }

    @Test
    @DisplayName("Connection supports prepared statements")
    void connection_prepared_statements() throws SQLException {
        try (Connection connection = Db.get()) { // Fixed method name
            assertDoesNotThrow(() -> {
                var stmt = connection.prepareStatement("SELECT 1");
                stmt.close();
            });
        }
    }

    @Test
    @DisplayName("Connection is valid when created")
    void connection_validity() throws SQLException {
        try (Connection connection = Db.get()) { // Fixed method name
            assertTrue(connection.isValid(5)); // 5 second timeout
        }
    }

    @Test
    @DisplayName("Connection can be closed multiple times safely")
    void connection_multiple_close() throws SQLException {
        Connection connection = Db.get(); // Fixed method name

        assertDoesNotThrow(() -> {
            connection.close();
            connection.close(); // Should not throw exception
        });

        assertTrue(connection.isClosed());
    }

    @Test
    @DisplayName("Connection supports SQL queries")
    void connection_sql_queries() throws SQLException {
        try (Connection connection = Db.get()) { // Fixed method name
            assertDoesNotThrow(() -> {
                var stmt = connection.createStatement();
                var rs = stmt.executeQuery("SELECT 1 as test_column");
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("test_column"));
                rs.close();
                stmt.close();
            });
        }
    }

    @Test
    @DisplayName("Connection supports batch operations")
    void connection_batch_operations() throws SQLException {
        try (Connection connection = Db.get()) { // Fixed method name
            assertDoesNotThrow(() -> {
                var stmt = connection.createStatement();
                stmt.addBatch("SELECT 1");
                stmt.addBatch("SELECT 2");
                int[] results = stmt.executeBatch();
                assertEquals(2, results.length);
                stmt.close();
            });
        }
    }

    @Test
    @DisplayName("Connection handles concurrent access")
    void connection_concurrent_access() throws InterruptedException {
        assertDoesNotThrow(() -> {
            Thread[] threads = new Thread[5];
            for (int i = 0; i < 5; i++) {
                threads[i] = new Thread(() -> {
                    try (Connection conn = Db.get()) { // Fixed method name
                        assertNotNull(conn);
                        assertFalse(conn.isClosed());
                    } catch (SQLException e) {
                        fail("Should not throw SQLException: " + e.getMessage());
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }
        });
    }

    @Test
    @DisplayName("Database connection pool behavior")
    void database_connection_pool() throws SQLException {
        // Test that connections can be created and closed without issues
        for (int i = 0; i < 10; i++) {
            try (Connection connection = Db.get()) { // Fixed method name
                assertNotNull(connection);
                assertFalse(connection.isClosed());
            }
        }
    }
}
