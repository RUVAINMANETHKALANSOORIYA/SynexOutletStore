package presentation.ui;

import domain.common.Money;
import application.inventory.InventoryService;
import application.pos.POSController;
import domain.pricing.BogoPolicy;
import domain.pricing.DiscountPolicy;
import domain.pricing.PercentageDiscount;
import application.reporting.ReportingService;
import domain.inventory.ItemCodeReader;
import domain.inventory.TypedCodeReader;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;

public final class POSWindow extends JFrame {

    private final POSController pos;
    private final InventoryService inventory;
    private final ReportingService reports;
    private final ItemCodeReader codeReader;

    private final JTextField codeField = new JTextField(12);
    private final JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
    private final JComboBox<String> discountType = new JComboBox<>(new String[]{"None", "Percentage", "BOGO"});
    private final JSpinner percentSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JLabel totalLabel = new JLabel("Total: 0.00");

    // Step 8: User & Channel controls
    private final JTextField userField = new JTextField("operator", 10);
    private final JComboBox<String> channelBox = new JComboBox<>(new String[]{"POS", "ONLINE"});

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Code", "Name", "Qty", "Unit Price", "Line Total"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    public POSWindow(POSController pos, InventoryService inventory, ReportingService reports) {
        super("Synex Outlet Store - POS");
        this.pos = pos;
        this.inventory = inventory;
        this.reports = reports;
        this.codeReader = new TypedCodeReader(codeField); // Step 9

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(920, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(12, 12));

        add(buildTopBar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        onNewBill(); // start fresh bill
    }

    private JPanel buildTopBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));

        JButton newBtn = new JButton("New Bill");
        newBtn.addActionListener(e -> onNewBill());

        JButton addBtn = new JButton("Add Item");
        addBtn.addActionListener(e -> onAddItem());

        JButton removeBtn = new JButton("Remove by Code");
        removeBtn.addActionListener(e -> onRemoveItem());

        JButton applyDisc = new JButton("Apply Discount");
        applyDisc.addActionListener(e -> onApplyDiscount());

        p.add(new JLabel("Code:"));
        p.add(codeField);
        p.add(new JLabel("Qty:"));
        p.add(qtySpinner);
        p.add(addBtn);
        p.add(removeBtn);

        p.add(Box.createHorizontalStrut(16));
        p.add(new JLabel("Discount:"));
        p.add(discountType);
        p.add(new JLabel("%"));
        p.add(percentSpinner);
        p.add(applyDisc);

        // Step 8 UI bits
        p.add(Box.createHorizontalStrut(16));
        p.add(new JLabel("User:"));
        p.add(userField);
        p.add(new JLabel("Channel:"));
        p.add(channelBox);

        p.add(Box.createHorizontalStrut(16));
        p.add(newBtn);

        return p;
    }

    private JPanel buildBottomBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton totalBtn = new JButton("Compute Total");
        totalBtn.addActionListener(e -> refreshTotal());

        JButton checkoutBtn = new JButton("Checkout");
        checkoutBtn.addActionListener(e -> onCheckout());

        JButton rptDaily = new JButton("Daily Report…");
        rptDaily.addActionListener(e -> onDailyReport());

        JButton rptBest = new JButton("Best Sellers…");
        rptBest.addActionListener(e -> onBestReport());

        JButton rptRange = new JButton("Revenue Range…");
        rptRange.addActionListener(e -> onRangeReport());

        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 16f));

        p.add(rptDaily);
        p.add(rptBest);
        p.add(rptRange);
        p.add(Box.createHorizontalStrut(20));
        p.add(totalBtn);
        p.add(totalLabel);
        p.add(Box.createHorizontalStrut(10));
        p.add(checkoutBtn);
        return p;
    }

    private void onNewBill() {
        try {
            // Step 8: push current user/channel to controller for the new bill
            pos.setUser(userField.getText().trim());
            pos.setChannel((String) channelBox.getSelectedItem());

            pos.newBill();
            model.setRowCount(0);
            totalLabel.setText("Total: 0.00");
            codeField.setText("");
            qtySpinner.setValue(1);
            discountType.setSelectedIndex(0);
            percentSpinner.setValue(0);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void onAddItem() {
        String code = codeReader.readCode();
        int qty = (Integer) qtySpinner.getValue();
        if (code.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter item code.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            pos.addItem(code, qty);

            String name = inventory.itemName(code);
            Money unitMoney = inventory.priceOf(code);
            Money lineMoney = unitMoney.multiply(qty);

            model.addRow(new Object[]{code, name, qty, unitMoney.toString(), lineMoney.toString()});
            refreshTotal();

            codeField.setText("");
            qtySpinner.setValue(1);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void onRemoveItem() {
        String code = JOptionPane.showInputDialog(this, "Item code to remove:");
        if (code == null || code.isBlank()) return;
        try {
            pos.removeItem(code.trim());
            for (int i = 0; i < model.getRowCount(); i++) {
                if (code.equals(model.getValueAt(i, 0))) {
                    model.removeRow(i);
                    break;
                }
            }
            refreshTotal();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void onApplyDiscount() {
        try {
            pos.applyDiscount(getCurrentDiscountPolicy());
            refreshTotal();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private DiscountPolicy getCurrentDiscountPolicy() {
        String type = (String) discountType.getSelectedItem();
        if ("Percentage".equals(type)) {
            return new PercentageDiscount((Integer) percentSpinner.getValue());
        } else if ("BOGO".equals(type)) {
            return new BogoPolicy();
        }
        return null;
    }

    private void refreshTotal() {
        try {
            pos.applyDiscount(getCurrentDiscountPolicy());
            Money t = pos.total();
            totalLabel.setText("Total: " + t.toString());
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void onCheckout() {
        try {
            // Step 8: ensure latest user/channel are set before saving
            pos.setUser(userField.getText().trim());
            pos.setChannel((String) channelBox.getSelectedItem());

            // Always apply current discount then compute due
            pos.applyDiscount(getCurrentDiscountPolicy());
            Money due = pos.total();

            Object[] options = {"Cash", "Card", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                    this,
                    "Choose domain.payment method.\nAmount due: " + due,
                    "Payment",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            if (choice == 0) {
                String amt = JOptionPane.showInputDialog(this, "Cash received (amount):", due.toString());
                if (amt == null || amt.isBlank()) return;
                double cash;
                try {
                    cash = Double.parseDouble(amt.trim());
                } catch (NumberFormatException nfe) {
                    JOptionPane.showMessageDialog(this, "Invalid amount.", "Validation", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                pos.checkoutCash(cash);
                JOptionPane.showMessageDialog(this, "Checkout complete (Cash).", "Success", JOptionPane.INFORMATION_MESSAGE);
                onNewBill();
            } else if (choice == 1) {
                String last4 = JOptionPane.showInputDialog(this, "Card last 4 digits:");
                if (last4 == null || last4.isBlank()) return;
                pos.checkoutCard(last4.trim());
                JOptionPane.showMessageDialog(this, "Checkout complete (Card).", "Success", JOptionPane.INFORMATION_MESSAGE);
                onNewBill();
            } // else Cancel
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void onDailyReport() {
        String d = JOptionPane.showInputDialog(this, "Date (yyyy-MM-dd):", LocalDate.now().toString());
        if (d == null || d.isBlank()) return;
        try {
            reports.printDailySales(LocalDate.parse(d));
            JOptionPane.showMessageDialog(this, "Daily report printed to console.", "Report", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) { showError(ex); }
    }

    private void onBestReport() {
        JTextField fromF = new JTextField(LocalDate.now().minusDays(7).toString(), 10);
        JTextField toF   = new JTextField(LocalDate.now().toString(), 10);
        JSpinner limitSp = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        JPanel p = new JPanel(new GridLayout(0,2,8,8));
        p.add(new JLabel("From (yyyy-MM-dd):")); p.add(fromF);
        p.add(new JLabel("To (yyyy-MM-dd):"));   p.add(toF);
        p.add(new JLabel("Limit:"));             p.add(limitSp);
        if (JOptionPane.showConfirmDialog(this, p, "Best Sellers", JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) {
            try {
                reports.printBestSellers(LocalDate.parse(fromF.getText().trim()), LocalDate.parse(toF.getText().trim()), (Integer) limitSp.getValue());
                JOptionPane.showMessageDialog(this, "Best-sellers report printed to console.", "Report", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) { showError(ex); }
        }
    }

    private void onRangeReport() {
        JTextField fromF = new JTextField(LocalDate.now().minusDays(7).toString(), 10);
        JTextField toF   = new JTextField(LocalDate.now().toString(), 10);
        JPanel p = new JPanel(new GridLayout(0,2,8,8));
        p.add(new JLabel("From (yyyy-MM-dd):")); p.add(fromF);
        p.add(new JLabel("To (yyyy-MM-dd):"));   p.add(toF);
        if (JOptionPane.showConfirmDialog(this, p, "Revenue (Range)", JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) {
            try {
                reports.printRevenueSeries(LocalDate.parse(fromF.getText().trim()), LocalDate.parse(toF.getText().trim()));
                JOptionPane.showMessageDialog(this, "Revenue series printed to console.", "Report", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) { showError(ex); }
        }
    }

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}
