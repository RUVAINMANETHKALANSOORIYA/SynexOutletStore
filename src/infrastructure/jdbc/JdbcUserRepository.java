package infrastructure.jdbc;

import domain.auth.User;
import ports.out.UserRepository;
import infrastructure.jdbc.Db;   // ✅ import your Db helper

import java.sql.*;
import java.util.Optional;

public final class JdbcUserRepository implements UserRepository {

    @Override
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT UserID, Username, Role, Email, Status FROM users WHERE Username=?";
        try (Connection c = Db.get();   // ✅ now resolves
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new User(
                        rs.getLong("UserID"),
                        rs.getString("Username"),
                        rs.getString("Role"),
                        rs.getString("Email"),
                        rs.getString("Status")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByUsername failed", e);
        }
    }

    @Override
    public String loadPasswordHash(String username) {
        String sql = "SELECT PasswordHash FROM users WHERE Username=?";
        try (Connection c = Db.get();   // ✅ now resolves
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("loadPasswordHash failed", e);
        }
    }
}
