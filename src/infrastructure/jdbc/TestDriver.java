package infrastructure.jdbc;

public class TestDriver {
    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        System.out.println("Driver loaded!");
    }
}

