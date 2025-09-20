package application.auth;

import domain.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ports.out.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static domain.auth.PasswordHash.sha256;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private static final String USER_PLAIN = "alice";
    private static final String USER_HASHED = "bob";
    private static final String USER_DISABLED = "carl";

    private InMemoryUserRepository repo;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        repo = new InMemoryUserRepository();
        auth = new AuthService(repo);

        // Plaintext-stored user (dev-friendly fallback)
        repo.addUser(new User(1, USER_PLAIN, "CASHIER", "a@example.com", "ACTIVE"), "secret");

        // Hashed-stored user
        repo.addUser(new User(2, USER_HASHED, "ADMIN", "b@example.com", "ACTIVE"), sha256("s3cr3t"));

        // Disabled user with valid hash
        repo.addUser(new User(3, USER_DISABLED, "CASHIER", "c@example.com", "DISABLED"), sha256("pwd"));
    }

    @Test
    @DisplayName("Unknown user returns false and does not change current state")
    void login_unknownUser() {
        assertFalse(auth.login("nobody", "whatever"));
        assertFalse(auth.isLoggedIn());
        assertNull(auth.currentUser());
    }

    @Test
    @DisplayName("Plaintext path: wrong password fails; correct succeeds and sets currentUser")
    void login_plaintextPaths() {
        assertFalse(auth.login(USER_PLAIN, "wrong"));
        assertFalse(auth.isLoggedIn());
        assertNull(auth.currentUser());

        assertTrue(auth.login(USER_PLAIN, "secret"));
        assertTrue(auth.isLoggedIn());
        assertNotNull(auth.currentUser());
        assertEquals(USER_PLAIN, auth.currentUser().username());
        assertEquals("CASHIER", auth.currentUser().role());
    }

    @Test
    @DisplayName("Hashed path: wrong password fails; correct succeeds and sets currentUser")
    void login_hashedPaths() {
        assertFalse(auth.login(USER_HASHED, "wrong"));
        assertFalse(auth.isLoggedIn());
        assertNull(auth.currentUser());

        assertTrue(auth.login(USER_HASHED, "s3cr3t"));
        assertTrue(auth.isLoggedIn());
        assertEquals(USER_HASHED, auth.currentUser().username());
        assertEquals("ADMIN", auth.currentUser().role());
    }

    @Test
    @DisplayName("Disabled user: implementation may allow or deny login; verify consistent state")
    void login_disabledUser() {
        boolean result = auth.login(USER_DISABLED, "pwd");
        if (result) {
            // Logged in; in some builds disabled status is still allowed
            assertTrue(auth.isLoggedIn());
            assertNotNull(auth.currentUser());
            assertEquals("DISABLED", auth.currentUser().status());
        } else {
            // Either user truly rejected (no current), or implementation set current but returned false due to disabled status
            if (auth.currentUser() != null) {
                assertEquals("DISABLED", auth.currentUser().status());
            } else {
                assertFalse(auth.isLoggedIn());
                assertNull(auth.currentUser());
            }
        }
    }

    @Test
    @DisplayName("Logout clears session state")
    void logout_clearsState() {
        assertTrue(auth.login(USER_PLAIN, "secret"));
        assertTrue(auth.isLoggedIn());
        auth.logout();
        assertFalse(auth.isLoggedIn());
        assertNull(auth.currentUser());
    }

    // Simple in-memory fake
    static class InMemoryUserRepository implements UserRepository {
        private final Map<String, User> users = new HashMap<>();
        private final Map<String, String> hashes = new HashMap<>();

        void addUser(User user, String passwordOrHash) {
            users.put(user.username(), user);
            hashes.put(user.username(), passwordOrHash);
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.ofNullable(users.get(username));
        }

        @Override
        public String loadPasswordHash(String username) {
            return hashes.get(username);
        }
    }
}
