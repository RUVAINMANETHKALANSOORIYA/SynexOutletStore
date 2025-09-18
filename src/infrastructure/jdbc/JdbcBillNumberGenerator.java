package infrastructure.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import domain.billing.BillNumberGenerator;
import infrastructure.jdbc.Db;

public final class JdbcBillNumberGenerator implements BillNumberGenerator {
    @Override
    public String next() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "POS-" + date + "-";
        int nextSeq = 1;
        String sql = "SELECT bill_no FROM bills WHERE bill_no LIKE ? ORDER BY bill_no DESC LIMIT 1";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String lastBillNo = rs.getString(1);
                    String[] parts = lastBillNo.split("-");
                    if (parts.length == 3) {
                        try {
                            nextSeq = Integer.parseInt(parts[2]) + 1;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to generate bill number", e);
        }
        return prefix + String.format("%04d", nextSeq);
    }
}

