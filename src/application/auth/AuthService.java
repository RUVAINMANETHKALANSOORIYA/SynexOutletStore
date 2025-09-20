package application.auth;

import domain.auth.PasswordHash;
import domain.auth.User;
import ports.out.UserRepository;

import java.util.Optional;

public final class AuthService {
    private final UserRepository users;
    private User current = null;

    public AuthService(UserRepository users) { this.users = users; }

    public boolean login(String username, String password) {
        String stored = users.loadPasswordHash(username);
        if (stored == null) return false;


        boolean looksHashed = stored.length() == 64 && stored.matches("[0-9a-fA-F]{64}");
        boolean ok = looksHashed
                ? PasswordHash.sha256(password).equalsIgnoreCase(stored)
                : password.equals(stored);

        if (!ok) return false;

        Optional<User> u = users.findByUsername(username);
        current = u.orElse(null);
        return current != null && !"DISABLED".equalsIgnoreCase(current.status());
    }

    public void logout() { current = null; }
    public boolean isLoggedIn() { return current != null; }
    public User currentUser() { return current; }
}
