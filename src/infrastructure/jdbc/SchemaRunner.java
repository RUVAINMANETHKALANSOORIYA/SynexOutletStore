package infrastructure.jdbc;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import infrastructure.jdbc.Db;


public final class SchemaRunner {
    public static void main(String[] args) throws Exception {
        new SchemaRunner().run("db/schema.sql");
        System.out.println("✅ Database schema & seed complete.");
    }

    public void run(String classpathSql) throws Exception {
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                for (String sql : loadSqlStatements(classpathSql)) {
                    if (sql.isBlank()) continue;
                    st.execute(sql);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static String[] loadSqlStatements(String classpathSql) throws Exception {
        InputStream in = SchemaRunner.class.getClassLoader().getResourceAsStream(classpathSql);
        if (in == null) throw new IllegalArgumentException("SQL resource not found: " + classpathSql);

        StringBuilder buf = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("--") || t.isEmpty()) continue;
                buf.append(line).append('\n');
            }
        }
        return buf.toString().split(";");
    }
}
