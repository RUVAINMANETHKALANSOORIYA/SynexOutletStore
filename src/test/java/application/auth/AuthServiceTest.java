package application.auth;

import domain.auth.User;
import ports.in.AuthService;
import ports.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private AuthService authService;
    private FakeUserRepository fakeRepository;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeUserRepository();
        authService = new AuthService(fakeRepository); // Fixed constructor

        // Set up test users
        fakeRepository.addUser(new User(1L, "testuser", "USER", "test@email.com", "ACTIVE"));
        fakeRepository.addUser(new User(2L, "admin", "ADMIN", "admin@email.com", "ACTIVE"));
        fakeRepository.addUser(new User(3L, "disabled", "USER", "disabled@email.com", "DISABLED"));
    }

    @Test
    @DisplayName("AuthService successful login with valid credentials")
    void successful_login() {
        fakeRepository.setPassword("testuser", "password123");

        boolean result = authService.login("testuser", "password123");

        assertTrue(result);
        assertTrue(authService.isLoggedIn());
        assertNotNull(authService.currentUser());
        assertEquals("testuser", authService.currentUser().username());
    }

    @Test
    @DisplayName("AuthService failed login with invalid password")
    void failed_login_invalid_password() {
        fakeRepository.setPassword("testuser", "password123");

        boolean result = authService.login("testuser", "wrongpassword");

        assertFalse(result);
        assertFalse(authService.isLoggedIn());
        assertNull(authService.currentUser());
    }

    @Test
    @DisplayName("AuthService failed login with non-existent user")
    void failed_login_nonexistent_user() {
        boolean result = authService.login("nonexistent", "password");

        assertFalse(result);
        assertFalse(authService.isLoggedIn());
        assertNull(authService.currentUser());
    }

    @Test
    @DisplayName("AuthService failed login with null username")
    void failed_login_null_username() {
        boolean result = authService.login(null, "password");

        assertFalse(result);
        assertFalse(authService.isLoggedIn());
        assertNull(authService.currentUser());
    }

    @Test
    @DisplayName("AuthService failed login with null password")
    void failed_login_null_password() {
        boolean result = authService.login("testuser", null);

        assertFalse(result);
        assertFalse(authService.isLoggedIn());
        assertNull(authService.currentUser());
    }

    @Test
    @DisplayName("AuthService failed login with empty username")
    void failed_login_empty_username() {
        boolean result = authService.login("", "password");

        assertFalse(result);
        assertFalse(authService.isLoggedIn());
        assertNull(authService.currentUser());
    }

    @Test
    @DisplayName("AuthService failed login with username containing numbers")
    void failed_login_username_with_numbers() {
        assertThrows(IllegalArgumentException.class, () -> {
            authService.login("user123", "password");
        });
    }

    @Test
    @DisplayName("AuthService failed login with disabled user")
    void failed_login_disabled_user() {
        // Ensure the disabled user has the correct password and status
        fakeRepository.setPassword("disabled", "password123");
        
        // Verify the user exists and is disabled before testing login
        Optional<User> disabledUser = fakeRepository.findByUsername("disabled");
        assertTrue(disabledUser.isPresent());
        assertEquals("DISABLED", disabledUser.get().status());

        boolean result = authService.login("disabled", "password123");

        assertFalse(result);
        assertFalse(authService.isLoggedIn());
        assertNull(authService.currentUser());
    }

    @Test
    @DisplayName("AuthService logout clears current user")
    void logout_clears_user() {
        fakeRepository.setPassword("testuser", "password123");
        authService.login("testuser", "password123");

        assertTrue(authService.isLoggedIn());

        authService.logout();

        assertFalse(authService.isLoggedIn());
        assertNull(authService.currentUser());
    }

    @Test
    @DisplayName("AuthService multiple login attempts")
    void multiple_login_attempts() {
        fakeRepository.setPassword("testuser", "password123");
        fakeRepository.setPassword("admin", "adminpass");

        // First login
        assertTrue(authService.login("testuser", "password123"));
        assertEquals("testuser", authService.currentUser().username());

        // Logout and login as different user
        authService.logout();
        assertTrue(authService.login("admin", "adminpass"));
        assertEquals("admin", authService.currentUser().username());
    }

    @Test
    @DisplayName("AuthService current user returns correct user")
    void current_user_returns_correct_user() {
        fakeRepository.setPassword("admin", "adminpass");

        authService.login("admin", "adminpass");

        User currentUser = authService.currentUser();
        assertNotNull(currentUser);
        assertEquals("admin", currentUser.username());
        assertEquals("ADMIN", currentUser.role());
        assertEquals("admin@email.com", currentUser.email());
    }

    // Fake repository implementation for testing
    static class FakeUserRepository implements UserRepository {
        private final List<User> users = new ArrayList<>();
        private final java.util.Map<String, String> passwords = new java.util.HashMap<>();

        public void addUser(User user) {
            users.add(user);
        }

        public void setPassword(String username, String password) {
            passwords.put(username, password);
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return users.stream().filter(u -> u.username().equals(username)).findFirst();
        }

        @Override
        public String loadPasswordHash(String username) {
            return passwords.get(username);
        }
    }
}
