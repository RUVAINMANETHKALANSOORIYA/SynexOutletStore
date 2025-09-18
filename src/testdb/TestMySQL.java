package testdb;

import java.sql.Connection;
import infrastructure.jdbc.Db;


public class TestMySQL {
    public static void main(String[] args) throws Exception {
        System.out.println("Loading driver & properties...");
        try (Connection c = Db.get()) {
            System.out.println("✅ Connected: " + (c != null));
        }
    }
}
