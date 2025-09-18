package domain.auth;

public record Customer(
        long id,
        String name,
        String email,
        String passwordHash,
        String phone,
        String status
) {}
