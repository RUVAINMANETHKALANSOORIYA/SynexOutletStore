package application.auth;

import domain.auth.Customer;
import ports.out.CustomerRepository;
import domain.auth.PasswordHash;

import java.util.Optional;

public final class CustomerAuthService {
    private final CustomerRepository repo;
    private Customer current = null;

    public CustomerAuthService(CustomerRepository repo) {
        this.repo = repo;
    }

    public boolean register(String username, String password, String email) {
        // Validate username - no numbers allowed
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        if (containsNumbers(username)) {
            throw new IllegalArgumentException("Invalid login credentials. You can't add numbers to login.");
        }

        if (repo.findByUsername(username).isPresent()) {
            return false; // username taken
        }
        String hash = PasswordHash.sha256(password);
        repo.save(username, hash, email);
        return true;
    }

    public boolean login(String username, String password) {
        // Validate username - no numbers allowed
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        if (containsNumbers(username)) {
            throw new IllegalArgumentException("Invalid login credentials. You can't add numbers to login.");
        }

        String stored = repo.loadPasswordHash(username);
        if (stored == null) return false;
        String given = PasswordHash.sha256(password);
        if (!stored.equalsIgnoreCase(given)) return false;
        Optional<Customer> u = repo.findByUsername(username);
        current = u.orElse(null);
        return current != null && !"DISABLED".equalsIgnoreCase(current.status());
    }

    public void logout() { current = null; }
    public boolean isLoggedIn() { return current != null; }
    public Customer currentUser() { return current; }

    private boolean containsNumbers(String text) {
        return text.matches(".*\\d.*");
    }
}
