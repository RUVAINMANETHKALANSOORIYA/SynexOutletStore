package infrastructure.jdbc;

import domain.auth.Customer;
import ports.out.CustomerRepository;

import java.sql.*;
import java.util.Optional;

public final class JdbcCustomerRepository implements CustomerRepository {

    @Override
    public void save(Customer c) {
        String sql = """
            INSERT INTO customers (username, email, password_hash, status)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                username = VALUES(username),
                status   = VALUES(status)
            """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.name());           // name() is the username
            ps.setString(2, c.email());
            ps.setString(3, c.passwordHash());
            ps.setString(4, c.status());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save customer", e);
        }
    }

    @Override
    public void save(String username, String passwordHash, String email) {
        String sql = """
            INSERT INTO customers (username, email, password_hash, status)
            VALUES (?, ?, ?, 'ACTIVE')
            """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save customer", e);
        }
    }

    @Override
    public Optional<Customer> findByUsername(String username) {
        String sql = "SELECT id, username, email, password_hash, status FROM customers WHERE username=?";
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Customer(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        null, // phone not in current schema
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find customer by username", e);
        }
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        String sql = "SELECT id, username, email, password_hash, status FROM customers WHERE email=?";
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Customer(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        null, // phone not in current schema
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find customer by email", e);
        }
    }

    @Override
    public String loadPasswordHash(String username) {
        String sql = "SELECT password_hash FROM customers WHERE username=?";
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("password_hash") : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load password hash", e);
        }
    }
}
