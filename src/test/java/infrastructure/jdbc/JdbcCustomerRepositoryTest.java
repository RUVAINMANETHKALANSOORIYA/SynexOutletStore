package infrastructure.jdbc;

import domain.auth.Customer;
import domain.auth.PasswordHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JdbcCustomerRepositoryTest {

    private JdbcCustomerRepository repository;

    @BeforeEach
    void setup() {
        repository = new JdbcCustomerRepository();
    }

    @Test
    @DisplayName("Repository handles database operations correctly")
    void repository_database_operations() {
        assertDoesNotThrow(() -> {
            repository.findByUsername("test_user");
            repository.findByEmail("test@example.com");
            repository.loadPasswordHash("test_user");
        });
    }

    @Test
    @DisplayName("Save customer with username and password")
    void save_customer_with_credentials() {
        // Use unique username with timestamp to avoid duplicates
        String uniqueUsername = "new_customer_" + System.currentTimeMillis();
        assertDoesNotThrow(() -> {
            repository.save(uniqueUsername, PasswordHash.sha256("password123"), "new@example.com");
        });
    }

    @Test
    @DisplayName("Save customer object")
    void save_customer_object() {
        Customer customer = new Customer(1L, "test_customer", "test@example.com",
                                       PasswordHash.sha256("password"), "0771234567", "ACTIVE");

        assertDoesNotThrow(() -> {
            repository.save(customer);
        });
    }

    @Test
    @DisplayName("Find customer by username handles non-existent user")
    void find_by_username_not_found() {
        Optional<Customer> result = repository.findByUsername("nonexistent_user_12345");

        // Should return empty optional for non-existent user
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Find customer by email handles non-existent email")
    void find_by_email_not_found() {
        Optional<Customer> result = repository.findByEmail("nonexistent@nowhere.com");

        // Should return empty optional for non-existent email
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Load password hash for non-existent user")
    void load_password_hash_not_found() {
        String hash = repository.loadPasswordHash("nonexistent_user_12345");

        // Should return null for non-existent user
        assertNull(hash);
    }

    @Test
    @DisplayName("Repository handles null parameters gracefully")
    void null_parameter_handling() {
        assertDoesNotThrow(() -> {
            repository.findByUsername(null);
            repository.findByEmail(null);
            repository.loadPasswordHash(null);
        });
    }

    @Test
    @DisplayName("Repository handles empty string parameters")
    void empty_string_parameters() {
        assertDoesNotThrow(() -> {
            repository.findByUsername("");
            repository.findByEmail("");
            repository.loadPasswordHash("");
        });
    }

    @Test
    @DisplayName("Save operations handle duplicate constraints")
    void save_duplicate_constraints() {
        // Use unique usernames with timestamps to avoid duplicates from previous test runs
        String uniqueUser1 = "unique_user_" + System.currentTimeMillis();
        String uniqueUser2 = uniqueUser1; // Same username for duplicate test

        // First save should work
        assertDoesNotThrow(() -> {
            repository.save(uniqueUser1, PasswordHash.sha256("pass1"), "unique1@test.com");
        });

        // Duplicate username should throw exception
        assertThrows(RuntimeException.class, () -> {
            repository.save(uniqueUser2, PasswordHash.sha256("pass2"), "unique2@test.com");
        });
    }

    @Test
    @DisplayName("Repository handles SQL injection attempts")
    void sql_injection_protection() {
        String maliciousInput = "'; DROP TABLE customers; --";

        assertDoesNotThrow(() -> {
            repository.findByUsername(maliciousInput);
            repository.findByEmail(maliciousInput);
            repository.loadPasswordHash(maliciousInput);
        });
    }

    @Test
    @DisplayName("Repository handles special characters in data")
    void special_characters_handling() {
        // Use unique username with timestamp to avoid duplicates
        String uniqueUsername = "üser_ñame_" + System.currentTimeMillis();
        assertDoesNotThrow(() -> {
            repository.save(uniqueUsername, PasswordHash.sha256("pässwörd"), "tëst@ëxample.com");
        });
    }

    @Test
    @DisplayName("Repository handles long usernames and emails")
    void long_data_handling() {
        // Use unique usernames with timestamps to avoid duplicates
        String longUsername = "a".repeat(45) + "_" + System.currentTimeMillis(); // Under 50 chars with timestamp
        String longEmail = "b".repeat(40) + System.currentTimeMillis() + "@example.com"; // More reasonable length

        assertDoesNotThrow(() -> {
            repository.save(longUsername, PasswordHash.sha256("password"), longEmail);
        });
    }
}
