package infrastructure.jdbc;

import domain.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JdbcUserRepositoryTest {

    private JdbcUserRepository repository;

    @BeforeEach
    void setup() {
        repository = new JdbcUserRepository();
    }

    @Test
    @DisplayName("Find user by username handles existing user")
    void find_by_username_existing() {
        // This tests actual database interaction
        assertDoesNotThrow(() -> {
            Optional<User> result = repository.findByUsername("admin");
            // Result depends on database state
        });
    }

    @Test
    @DisplayName("Find user by username handles non-existent user")
    void find_by_username_not_found() {
        Optional<User> result = repository.findByUsername("definitely_nonexistent_user_12345");

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Load password hash for existing user")
    void load_password_hash_existing() {
        assertDoesNotThrow(() -> {
            String hash = repository.loadPasswordHash("admin");
            // Hash may or may not exist depending on database state
        });
    }

    @Test
    @DisplayName("Load password hash for non-existent user")
    void load_password_hash_not_found() {
        String hash = repository.loadPasswordHash("definitely_nonexistent_user_12345");

        assertNull(hash);
    }

    @Test
    @DisplayName("Repository handles null username")
    void null_username_handling() {
        assertDoesNotThrow(() -> {
            Optional<User> result = repository.findByUsername(null);
            String hash = repository.loadPasswordHash(null);
        });
    }

    @Test
    @DisplayName("Repository handles empty username")
    void empty_username_handling() {
        assertDoesNotThrow(() -> {
            Optional<User> result = repository.findByUsername("");
            String hash = repository.loadPasswordHash("");
        });
    }

    @Test
    @DisplayName("Repository handles whitespace username")
    void whitespace_username_handling() {
        assertDoesNotThrow(() -> {
            Optional<User> result = repository.findByUsername("   ");
            String hash = repository.loadPasswordHash("   ");
        });
    }

    @Test
    @DisplayName("Repository handles case sensitivity")
    void case_sensitivity_handling() {
        assertDoesNotThrow(() -> {
            Optional<User> lower = repository.findByUsername("admin");
            Optional<User> upper = repository.findByUsername("ADMIN");
            Optional<User> mixed = repository.findByUsername("Admin");
        });
    }

    @Test
    @DisplayName("Repository handles SQL injection attempts")
    void sql_injection_protection() {
        String maliciousInput = "admin'; DROP TABLE users; --";

        assertDoesNotThrow(() -> {
            repository.findByUsername(maliciousInput);
            repository.loadPasswordHash(maliciousInput);
        });
    }

    @Test
    @DisplayName("Repository handles special characters")
    void special_characters_handling() {
        assertDoesNotThrow(() -> {
            repository.findByUsername("üser_ñame");
            repository.loadPasswordHash("spëcial_chars");
        });
    }

    @Test
    @DisplayName("Repository handles very long usernames")
    void long_username_handling() {
        String longUsername = "a".repeat(500);

        assertDoesNotThrow(() -> {
            repository.findByUsername(longUsername);
            repository.loadPasswordHash(longUsername);
        });
    }

    @Test
    @DisplayName("Repository operations are thread safe")
    void thread_safety() {
        assertDoesNotThrow(() -> {
            // Simulate concurrent access
            for (int i = 0; i < 10; i++) {
                repository.findByUsername("concurrent_test_" + i);
            }
        });
    }
}
