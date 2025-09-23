package infrastructure.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaRunnerTest {

    private SchemaRunner schemaRunner;

    @BeforeEach
    void setUp() {
        schemaRunner = new SchemaRunner();
    }

    @Test
    @DisplayName("SchemaRunner can run schema file")
    void run_schema_file() {
        assertDoesNotThrow(() -> {
            schemaRunner.run("db/schema.sql");
        });
    }

    @Test
    @DisplayName("SchemaRunner handles missing file gracefully")
    void handle_missing_file() {
        assertThrows(Exception.class, () -> {
            schemaRunner.run("nonexistent.sql");
        });
    }

    @Test
    @DisplayName("SchemaRunner creates valid instance")
    void creates_valid_instance() {
        assertNotNull(schemaRunner);
    }

    @Test
    @DisplayName("SchemaRunner can run multiple times")
    void run_multiple_times() {
        assertDoesNotThrow(() -> {
            schemaRunner.run("db/schema.sql");
            schemaRunner.run("db/schema.sql");
        });
    }

    @Test
    @DisplayName("SchemaRunner handles null file path")
    void handle_null_file_path() {
        assertThrows(Exception.class, () -> {
            schemaRunner.run(null);
        });
    }

    @Test
    @DisplayName("SchemaRunner handles empty file path")
    void handle_empty_file_path() {
        assertThrows(Exception.class, () -> {
            schemaRunner.run("");
        });
    }
}
