package persistence.jdbc;

import domain.billing.Bill;
import domain.billing.BillLine;
import ports.out.BillRepository;

import infrastructure.jdbc.Db;            // ✅ add this import

import java.sql.*;

public final class JdbcBillRepository implements BillRepository {

    @Override
    public void save(Bill bill) {
        String insBill = """
            INSERT INTO bills (
                bill_no, created_at, subtotal, discount, tax, total,
                payment_method, paid_amount, change_amount, card_last4,
                channel, user_name
            )
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        String insLine = """
            INSERT INTO bill_lines (bill_id, item_code, qty, unit_price, line_total)
            VALUES (?,?,?,?,?)
            """;

        try (Connection c = Db.get()) {
            c.setAutoCommit(false);

            try (PreparedStatement pb = c.prepareStatement(insBill, Statement.RETURN_GENERATED_KEYS)) {
                pb.setString(1, bill.number());
                pb.setTimestamp(2, Timestamp.valueOf(bill.createdAt()));
                pb.setBigDecimal(3, bill.subtotal().asBigDecimal());
                pb.setBigDecimal(4, bill.discount().asBigDecimal());
                pb.setBigDecimal(5, bill.tax().asBigDecimal());
                pb.setBigDecimal(6, bill.total().asBigDecimal());

                // payment & meta
                pb.setString(7, bill.paymentMethod());
                pb.setBigDecimal(8, bill.paidAmount().asBigDecimal());
                pb.setBigDecimal(9, bill.changeAmount().asBigDecimal());
                pb.setString(10, bill.cardLast4());
                pb.setString(11, bill.channel());
                pb.setString(12, bill.userName());

                pb.executeUpdate();

                long billId;
                try (ResultSet keys = pb.getGeneratedKeys()) {
                    if (!keys.next()) throw new RuntimeException("Bill ID not generated");
                    billId = keys.getLong(1);
                }

                try (PreparedStatement pl = c.prepareStatement(insLine)) {
                    for (BillLine l : bill.lines()) {
                        pl.setLong(1, billId);
                        pl.setString(2, l.itemCode());
                        pl.setInt(3, l.quantity());
                        pl.setBigDecimal(4, l.unitPrice().asBigDecimal());
                        pl.setBigDecimal(5, l.lineTotal().asBigDecimal());
                        pl.addBatch();
                    }
                    pl.executeBatch();
                }

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("save(bill) failed", e);
        }
    }
}
