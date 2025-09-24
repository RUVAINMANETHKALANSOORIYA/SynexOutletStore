package application.auth;

import domain.auth.Customer;
import ports.out.CustomerRepository;
import domain.auth.PasswordHash;

import java.util.Optional;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class CustomerAuthService {
    private static final Logger logger = Logger.getLogger(CustomerAuthService.class.getName());
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    );
    
    private final CustomerRepository repo;
    private Customer current = null;

    public CustomerAuthService(CustomerRepository repo) {
        this.repo = repo;
    }

    public boolean register(String username, String password, String email) throws AuthenticationException {
        logger.info("Registration attempt for username: " + username);
        
        // Validate username
        validateUsername(username);
        
        // Validate password
        validatePassword(password);
        
        // Validate email
        validateEmail(email);
        
        // Check if username already exists
        if (repo.findByUsername(username).isPresent()) {
            logger.warning("Registration failed: Username already exists - " + username);
            throw new AuthenticationException("Username already exists");
        }
        
        try {
            String hash = PasswordHash.sha256(password);
            repo.save(username, hash, email);
            logger.info("User registered successfully: " + username);
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Registration failed for user: " + username, e);
            throw new AuthenticationException("Registration failed due to system error");
        }
    }

    public boolean login(String username, String password) throws AuthenticationException {
        logger.info("Login attempt for username: " + username);
        
        // Validate username
        validateUsername(username);
        
        // Validate password
        validatePassword(password);

        try {
            String stored = repo.loadPasswordHash(username);
            if (stored == null) {
                logger.warning("Login failed: User not found - " + username);
                throw new AuthenticationException("Invalid username or password");
            }
            
            String given = PasswordHash.sha256(password);
            if (!stored.equalsIgnoreCase(given)) {
                logger.warning("Login failed: Incorrect password for user - " + username);
                throw new AuthenticationException("Invalid username or password");
            }
            
            Optional<Customer> u = repo.findByUsername(username);
            current = u.orElse(null);
            
            if (current == null) {
                logger.warning("Login failed: User data not found - " + username);
                throw new AuthenticationException("User account not found");
            }
            
            if ("DISABLED".equalsIgnoreCase(current.status())) {
                logger.warning("Login failed: Account disabled - " + username);
                throw new AuthenticationException("Account is disabled");
            }
            
            logger.info("User logged in successfully: " + username);
            return true;
            
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Login failed for user: " + username, e);
            throw new AuthenticationException("Login failed due to system error");
        }
    }

    public void logout() { current = null; }
    public boolean isLoggedIn() { return current != null; }
    public Customer currentUser() { return current; }

    private void validateUsername(String username) throws AuthenticationException {
        if (username == null) {
            logger.warning("Authentication failed: Username is null");
            throw new AuthenticationException("Username cannot be null");
        }
        if (username.trim().isEmpty()) {
            logger.warning("Authentication failed: Username is empty");
            throw new AuthenticationException("Username cannot be empty");
        }
        if (containsNumbers(username)) {
            logger.warning("Authentication failed: Username contains numbers - " + username);
            throw new AuthenticationException("Username cannot contain numbers");
        }
    }

    private void validatePassword(String password) throws AuthenticationException {
        if (password == null) {
            logger.warning("Authentication failed: Password is null");
            throw new AuthenticationException("Password cannot be null");
        }
        if (password.trim().isEmpty()) {
            logger.warning("Authentication failed: Password is empty");
            throw new AuthenticationException("Password cannot be empty");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            logger.warning("Authentication failed: Password does not meet strength requirements");
            throw new AuthenticationException(
                "Password must be at least 8 characters long and contain at least one uppercase letter, " +
                "one lowercase letter, one digit, and one special character (@$!%*?&)"
            );
        }
    }

    private void validateEmail(String email) throws AuthenticationException {
        if (email == null) {
            logger.warning("Authentication failed: Email is null");
            throw new AuthenticationException("Email cannot be null");
        }
        if (email.trim().isEmpty()) {
            logger.warning("Authentication failed: Email is empty");
            throw new AuthenticationException("Email cannot be empty");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            logger.warning("Authentication failed: Invalid email format - " + email);
            throw new AuthenticationException("Invalid email format");
        }
    }

    private boolean containsNumbers(String text) {
        return text.matches(".*\\d.*");
    }

    // Custom exception class for authentication errors
    public static class AuthenticationException extends Exception {
        public AuthenticationException(String message) {
            super(message);
        }

        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
