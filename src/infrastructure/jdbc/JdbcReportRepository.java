package infrastructure.jdbc;

import application.reporting.ReportRepository;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class JdbcReportRepository implements ReportRepository {

    @Override
    public DailySalesRow dailySales(LocalDate day) {
        String q1 = """
            SELECT DATE(created_at) AS d,
                   COUNT(*) AS bills,
                   COALESCE(SUM(total),0) AS revenue,
                   COALESCE(SUM(discount),0) AS discounts
            FROM bills
            WHERE DATE(created_at)=?
            """;
        String q2 = """
            SELECT COALESCE(SUM(bl.qty),0) AS items
            FROM bill_lines bl
            JOIN bills b ON bl.bill_id=b.id
            WHERE DATE(b.created_at)=?
            """;
        try (Connection c = Db.get()) {
            long bills; String revenue; String discounts;
            try (PreparedStatement ps = c.prepareStatement(q1)) {
                ps.setDate(1, Date.valueOf(day));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        bills = rs.getLong("bills");
                        revenue = rs.getBigDecimal("revenue").setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
                        discounts = rs.getBigDecimal("discounts").setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
                    } else {
                        bills = 0; revenue = "0.00"; discounts="0.00";
                    }
                }
            }
            long items;
            try (PreparedStatement ps = c.prepareStatement(q2)) {
                ps.setDate(1, Date.valueOf(day));
                try (ResultSet rs = ps.executeQuery()) { rs.next(); items = rs.getLong(1); }
            }
            return new DailySalesRow(day, bills, revenue, discounts, items);
        } catch (Exception e) {
            throw new RuntimeException("dailySales failed", e);
        }
    }

    @Override
    public List<BestSellerRow> bestSellers(LocalDate from, LocalDate to, int limit) {
        String sql = """
            SELECT i.item_code, i.name, SUM(bl.qty) AS qty_sold, SUM(bl.line_total) AS revenue
            FROM bill_lines bl
            JOIN bills b ON bl.bill_id=b.id
            JOIN items i ON bl.item_code=i.item_code
            WHERE b.created_at >= ? AND b.created_at < DATE_ADD(?, INTERVAL 1 DAY)
            GROUP BY i.item_code, i.name
            ORDER BY qty_sold DESC, revenue DESC
            LIMIT ?
            """;
        List<BestSellerRow> list = new ArrayList<>();
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new BestSellerRow(
                            rs.getString("item_code"),
                            rs.getString("name"),
                            rs.getLong("qty_sold"),
                            rs.getBigDecimal("revenue").setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    ));
                }
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException("bestSellers failed", e);
        }
    }

    @Override
    public List<RevenueRow> revenueByDay(LocalDate from, LocalDate to) {
        String sql = """
            SELECT DATE(created_at) AS d, SUM(total) AS revenue
            FROM bills
            WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY)
            GROUP BY DATE(created_at)
            ORDER BY d
            """;
        List<RevenueRow> list = new ArrayList<>();
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RevenueRow(
                            rs.getDate("d").toLocalDate(),
                            rs.getBigDecimal("revenue").setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    ));
                }
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException("revenueByDay failed", e);
        }
    }


    @Override
    public List<ReshelvingRow> reshelvingSuggestions(int shelfTarget) {
        // Aggregate shelf/store by item, compute suggested move (min(store, target - shelf))
        String sql = """
            SELECT i.item_code, i.name,
                   COALESCE(SUM(b.qty_on_shelf),0) AS shelf_qty,
                   COALESCE(SUM(b.qty_in_store),0) AS store_qty
            FROM items i
            LEFT JOIN batches b ON b.item_code=i.item_code
            GROUP BY i.item_code, i.name
            HAVING shelf_qty < ? AND store_qty > 0
            ORDER BY i.item_code
            """;
        List<ReshelvingRow> list = new ArrayList<>();
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, shelfTarget);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int shelf = rs.getInt("shelf_qty");
                    int store = rs.getInt("store_qty");
                    int move = Math.min(store, Math.max(0, shelfTarget - shelf));
                    if (move > 0) {
                        list.add(new ReshelvingRow(
                                rs.getString("item_code"),
                                rs.getString("name"),
                                shelf, store, move
                        ));
                    }
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("reshelvingSuggestions failed", e);
        }
    }

    @Override
    public List<ReorderRow> reorderBelow(int threshold) {
        String sql = """
            SELECT i.item_code, i.name,
                   COALESCE(SUM(b.qty_on_shelf + b.qty_in_store),0) AS total_qty
            FROM items i
            LEFT JOIN batches b ON b.item_code=i.item_code
            GROUP BY i.item_code, i.name
            HAVING total_qty < ?
            ORDER BY total_qty ASC, i.item_code
            """;
        List<ReorderRow> list = new ArrayList<>();
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, threshold);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ReorderRow(
                            rs.getString("item_code"),
                            rs.getString("name"),
                            rs.getInt("total_qty")
                    ));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("reorderBelow failed", e);
        }
    }

    @Override
    public List<StockBatchRow> stockByBatch(String itemCodeOrNull) {
        String base = """
            SELECT b.id, b.item_code, i.name, b.expiry, b.qty_on_shelf, b.qty_in_store
            FROM batches b
            JOIN items i ON i.item_code=b.item_code
            """;
        String where = (itemCodeOrNull == null || itemCodeOrNull.isBlank())
                ? ""
                : " WHERE b.item_code=?";
        String order = " ORDER BY b.item_code, (b.expiry IS NULL), b.expiry ASC, b.id";
        String sql = base + where + order;

        List<StockBatchRow> list = new ArrayList<>();
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (where.length() > 0) ps.setString(1, itemCodeOrNull);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date d = rs.getDate("expiry");
                    list.add(new StockBatchRow(
                            rs.getLong("id"),
                            rs.getString("item_code"),
                            rs.getString("name"),
                            (d == null ? null : d.toLocalDate()),
                            rs.getInt("qty_on_shelf"),
                            rs.getInt("qty_in_store")
                    ));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("stockByBatch failed", e);
        }
    }

    @Override
    public List<BillRow> billsBetween(LocalDate from, LocalDate to) {
        String sql = """
            SELECT bill_no, created_at, user_name, channel, payment_method,
                   subtotal, discount, tax, total
            FROM bills
            WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY)
            ORDER BY created_at DESC, bill_no DESC
            """;
        List<BillRow> list = new ArrayList<>();
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new BillRow(
                            rs.getString("bill_no"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getString("user_name"),
                            rs.getString("channel"),
                            rs.getString("payment_method"),
                            rs.getBigDecimal("subtotal").setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                            rs.getBigDecimal("discount").setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                            rs.getBigDecimal("tax").setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                            rs.getBigDecimal("total").setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    ));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("billsBetween failed", e);
        }
    }

    @Override
    public List<RestockRow> restockAtOrBelowLevel() {
        String sql = """
            SELECT i.item_code, i.name,
                   COALESCE(SUM(b.qty_on_shelf),0) AS shelf_qty,
                   COALESCE(SUM(b.qty_in_store),0) AS store_qty,
                   COALESCE(SUM(b.qty_in_main),0) AS main_qty,
                   COALESCE(i.restock_level, 50) AS restock_level
            FROM items i
            LEFT JOIN batches b ON b.item_code=i.item_code
            GROUP BY i.item_code, i.name, i.restock_level
            HAVING (COALESCE(SUM(b.qty_on_shelf),0) + COALESCE(SUM(b.qty_in_store),0)) <= 
                   CASE WHEN COALESCE(i.restock_level, 50) > 50 
                        THEN COALESCE(i.restock_level, 50) 
                        ELSE 50 END
            ORDER BY (COALESCE(SUM(b.qty_on_shelf),0) + COALESCE(SUM(b.qty_in_store),0)) ASC, i.item_code
            """;
        List<RestockRow> list = new ArrayList<>();
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RestockRow(
                        rs.getString("item_code"),
                        rs.getString("name"),
                        rs.getInt("shelf_qty"),
                        rs.getInt("store_qty"),
                        rs.getInt("main_qty"),
                        rs.getInt("restock_level")
                    ));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("restockAtOrBelowLevel failed: " + e.getMessage(), e);
        }
    }
}
