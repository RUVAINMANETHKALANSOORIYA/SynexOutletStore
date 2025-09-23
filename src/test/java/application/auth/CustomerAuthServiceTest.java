package application.auth;

import domain.auth.Customer;
import domain.auth.PasswordHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CustomerAuthServiceTest {

    private CustomerAuthService service;
    private FakeCustomerRepository repo;

    @BeforeEach
    void setup() {
        repo = new FakeCustomerRepository();
        service = new CustomerAuthService(repo);
    }

    @Test
    @DisplayName("register succeeds for valid username without numbers")
    void register_success() {
        boolean ok = service.register("alice", "secret", "alice@example.com");
        assertTrue(ok);
        assertTrue(repo.findByUsername("alice").isPresent());
    }

    @Test
    @DisplayName("register rejects usernames with numbers")
    void register_rejects_numbers() {
        assertThrows(IllegalArgumentException.class, () -> service.register("bob1", "x", "b@e.com"));
    }

    @Test
    @DisplayName("login succeeds for active user with correct password")
    void login_success_active() {
        String user = "charlie";
        String pass = "pw";
        String hash = PasswordHash.sha256(pass);
        repo.save(new Customer(1L, user, "c@example.com", hash, "0770000000", "ACTIVE"));
        assertTrue(service.login(user, pass));
        assertTrue(service.isLoggedIn());
        assertNotNull(service.currentUser());
        assertEquals(user, service.currentUser().name());
    }

    @Test
    @DisplayName("login fails for disabled user")
    void login_fails_disabled() {
        String user = "diana";
        String pass = "pw";
        String hash = PasswordHash.sha256(pass);
        repo.save(new Customer(2L, user, "d@example.com", hash, "0770000001", "DISABLED"));
        assertFalse(service.login(user, pass));
    }

    @Test
    @DisplayName("logout clears current user")
    void logout_works() {
        String user = "ed";
        String pass = "pw";
        String hash = PasswordHash.sha256(pass);
        repo.save(new Customer(3L, user, "e@example.com", hash, "077", "ACTIVE"));
        assertTrue(service.login(user, pass));
        service.logout();
        assertFalse(service.isLoggedIn());
        assertNull(service.currentUser());
    }

    // minimal fake
    static class FakeCustomerRepository implements ports.out.CustomerRepository {
        private final Map<String, Customer> byUsername = new HashMap<>();
        private final Map<String, Customer> byEmail = new HashMap<>();
        private long seq = 100;

        @Override public void save(Customer customer) {
            byUsername.put(customer.name(), customer);
            byEmail.put(customer.email(), customer);
        }

        @Override public void save(String username, String passwordHash, String email) {
            Customer c = new Customer(++seq, username, email, passwordHash, "", "ACTIVE");
            save(c);
        }

        @Override public Optional<Customer> findByUsername(String username) {
            return Optional.ofNullable(byUsername.get(username));
        }

        @Override public Optional<Customer> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override public String loadPasswordHash(String username) {
            return findByUsername(username).map(Customer::passwordHash).orElse(null);
        }
    }
}
