package domain.auth;

public record User(long id, String username, String role, String email, String status) {}
