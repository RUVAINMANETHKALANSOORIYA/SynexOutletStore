package infrastructure.jdbc;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.billing.Receipt;
import ports.out.BillRepository;

import infrastructure.jdbc.Db;            // ✅ add this import

import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class JdbcBillRepository implements BillRepository {

    @Override
    public String createBill() {
        // This could generate a new bill number or return a placeholder
        // For now, returning a placeholder since bill numbers are handled elsewhere
        return "BILL-" + System.currentTimeMillis();
    }

    @Override
    public void saveBill(Bill bill) {
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
            throw new RuntimeException("saveBill(bill) failed", e);
        }
    }

    @Override
    public Optional<Bill> findBill(String billId) {
        // Implementation to find a bill by ID
        // For now, returning empty since this might not be needed immediately
        return Optional.empty();
    }

    @Override
    public void savePaidBill(Receipt receipt) {
        // Implementation to save a paid bill receipt
        // This could insert into a receipts table or update the bill status
    }

    @Override
    public List<Bill> findOpenBills() {
        // Implementation to find open (unpaid) bills
        return List.of();
    }

    @Override
    public List<Receipt> findReceiptsByDate(LocalDate date) {
        // Implementation to find receipts by date
        return List.of();
    }

    @Override
    public void deleteBill(String billId) {
        String deleteBillLines = "DELETE FROM bill_lines WHERE bill_id = (SELECT id FROM bills WHERE bill_no = ?)";
        String deleteBill = "DELETE FROM bills WHERE bill_no = ?";

        try (Connection c = Db.get()) {
            c.setAutoCommit(false);

            try (PreparedStatement psLines = c.prepareStatement(deleteBillLines);
                 PreparedStatement psBill = c.prepareStatement(deleteBill)) {

                // Delete bill lines first (foreign key constraint)
                psLines.setString(1, billId);
                psLines.executeUpdate();

                // Delete bill
                psBill.setString(1, billId);
                psBill.executeUpdate();

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("deleteBill(billId) failed", e);
        }
    }
}
