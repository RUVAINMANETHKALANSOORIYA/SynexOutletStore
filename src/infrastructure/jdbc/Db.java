package infrastructure.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.io.InputStream;


public final class Db {
    private static final String url;
    private static final String user;
    private static final String pass;

    static {
        String tmpUrl = null, tmpUser = null, tmpPass = null;
        try (InputStream in = Db.class.getClassLoader().getResourceAsStream("db.properties")) {
            Properties p = new Properties();
            if (in != null) {
                p.load(in);
            } else {
                System.err.println("⚠️  db.properties not found, using defaults.");
            }

            tmpUrl  = p.getProperty("db.url",
                    "jdbc:mysql://localhost:3306/posdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            tmpUser = p.getProperty("db.user", "root");
            tmpPass = p.getProperty("db.password", "");

            // Load MySQL driver (safe even if already registered)
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Failed to init DB connection: " + e.getMessage());
        }
        url = tmpUrl;
        user = tmpUser;
        pass = tmpPass;
    }

    private Db() {}

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }
}
