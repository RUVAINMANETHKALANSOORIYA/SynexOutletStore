package application.auth;

import domain.auth.Customer;
import domain.auth.PasswordHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import application.auth.CustomerAuthService.AuthenticationException;

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
    @DisplayName("register succeeds for valid input")
    void register_success() throws AuthenticationException {
        boolean ok = service.register("alice", "Password123!", "alice@example.com");
        assertTrue(ok);
        assertTrue(repo.findByUsername("alice").isPresent());
    }

    @Test
    @DisplayName("register rejects usernames with numbers")
    void register_rejects_numbers() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
            () -> service.register("bob1", "Password123!", "b@example.com"));
        assertEquals("Username cannot contain numbers", ex.getMessage());
    }

    @Test
    @DisplayName("register rejects null username")
    void register_rejects_null_username() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
            () -> service.register(null, "Password123!", "test@example.com"));
        assertEquals("Username cannot be null", ex.getMessage());
    }

    @Test
    @DisplayName("register rejects empty username")
    void register_rejects_empty_username() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
            () -> service.register("", "Password123!", "test@example.com"));
        assertEquals("Username cannot be empty", ex.getMessage());
    }

    @Test
    @DisplayName("register rejects null password")
    void register_rejects_null_password() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
            () -> service.register("alice", null, "alice@example.com"));
        assertEquals("Password cannot be null", ex.getMessage());
    }

    @Test
    @DisplayName("register rejects weak password")
    void register_rejects_weak_password() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
            () -> service.register("alice", "weak", "alice@example.com"));
        assertTrue(ex.getMessage().contains("Password must be at least 8 characters long"));
    }

    @Test
    @DisplayName("register rejects invalid email format")
    void register_rejects_invalid_email() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
            () -> service.register("alice", "Password123!", "invalid-email"));
        assertEquals("Invalid email format", ex.getMessage());
    }

    @Test
    @DisplayName("register rejects null email")
    void register_rejects_null_email() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
            () -> service.register("alice", "Password123!", null));
        assertEquals("Email cannot be null", ex.getMessage());
    }

    @Test
    @DisplayName("register rejects duplicate username")
    void register_rejects_duplicate_username() throws AuthenticationException {
        service.register("alice", "Password123!", "alice@example.com");
        AuthenticationException ex = assertThrows(AuthenticationException.class,
            () -> service.register("alice", "Password456!", "alice2@example.com"));
        assertEquals("Username already exists", ex.getMessage());
    }

    @Test
    @DisplayName("login succeeds for active user with correct password")
    void login_success_active() throws AuthenticationException {
        String user = "charlie";
        String pass = "Password123!";
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
        String pass = "Password123!";
        String hash = PasswordHash.sha256(pass);
        repo.save(new Customer(2L, user, "d@example.com", hash, "0770000001", "DISABLED"));
        AuthenticationException ex = assertThrows(AuthenticationException.class,
            () -> service.login(user, pass));
        assertEquals("Account is disabled", ex.getMessage());
    }

    @Test
    @DisplayName("login fails for non-existent user")
    void login_fails_nonexistent_user() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
            () -> service.login("nonexistent", "Password123!"));
        assertEquals("Invalid username or password", ex.getMessage());
    }

    @Test
    @DisplayName("login fails for incorrect password")
    void login_fails_incorrect_password() {
        String user = "ed";
        String correctPass = "Password123!";
        String wrongPass = "WrongPass456!";
        String hash = PasswordHash.sha256(correctPass);
        repo.save(new Customer(3L, user, "e@example.com", hash, "077", "ACTIVE"));
        AuthenticationException ex = assertThrows(AuthenticationException.class,
            () -> service.login(user, wrongPass));
        assertEquals("Invalid username or password", ex.getMessage());
    }

    @Test
    @DisplayName("login rejects null username")
    void login_rejects_null_username() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
            () -> service.login(null, "Password123!"));
        assertEquals("Username cannot be null", ex.getMessage());
    }

    @Test
    @DisplayName("login rejects null password")
    void login_rejects_null_password() {
        AuthenticationException ex = assertThrows(AuthenticationException.class,
            () -> service.login("alice", null));
        assertEquals("Password cannot be null", ex.getMessage());
    }

    @Test
    @DisplayName("logout clears current user")
    void logout_works() throws AuthenticationException {
        String user = "ed";
        String pass = "Password123!";
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
