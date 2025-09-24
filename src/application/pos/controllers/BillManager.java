package application.pos.controllers;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.billing.BillNumberGenerator;
import domain.inventory.InventoryReservation;
import domain.payment.Payment;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages bill lifecycle and state
 */
public class BillManager {
    private final BillNumberGenerator billNos;
    private Bill active = null;
    private final List<InventoryReservation> shelfReservations = new ArrayList<>();
    private final List<InventoryReservation> storeReservations = new ArrayList<>();
    private Payment.Receipt paymentReceipt = null;

    private String currentUser = "operator";
    private String currentChannel = "POS";

    public BillManager(BillNumberGenerator billNos) {
        this.billNos = billNos;
    }

    public void setUser(String user) {
        this.currentUser = (user == null || user.isBlank()) ? "operator" : user.trim();
        if (active != null) active.setUserName(this.currentUser);
    }

    public void setChannel(String channel) {
        this.currentChannel = (channel == null || channel.isBlank()) ? "POS" : channel.trim().toUpperCase();
        if (active != null) active.setChannel(this.currentChannel);
    }

    public Bill createNewBill() {
        active = new Bill(billNos.next());
        active.setUserName(currentUser);
        active.setChannel(currentChannel);

        shelfReservations.clear();
        storeReservations.clear();
        paymentReceipt = null;

        return active;
    }

    public void addLine(BillLine line) {
        ensureActiveBill();
        active.addLine(line);
    }

    public void removeLineByCode(String code) {
        ensureActiveBill();
        active.removeLineByCode(code);
    }

    public Bill getActiveBill() {
        return active;
    }

    public void setPaymentReceipt(Payment.Receipt receipt) {
        this.paymentReceipt = receipt;
        if (active != null) {
            active.setPayment(receipt);
        }
    }

    public Payment.Receipt getPaymentReceipt() {
        return paymentReceipt;
    }

    public void addReservations(List<InventoryReservation> reservations, boolean isStore) {
        if (isStore) {
            storeReservations.addAll(reservations);
        } else {
            shelfReservations.addAll(reservations);
        }
    }

    public List<InventoryReservation> getShelfReservations() {
        return new ArrayList<>(shelfReservations);
    }

    public List<InventoryReservation> getStoreReservations() {
        return new ArrayList<>(storeReservations);
    }

    public void resetBill() {
        active = null;
        shelfReservations.clear();
        storeReservations.clear();
        paymentReceipt = null;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public String getCurrentChannel() {
        return currentChannel;
    }

    public boolean hasActiveBill() {
        return active != null;
    }

    public boolean hasItems() {
        return active != null && !active.lines().isEmpty();
    }

    private void ensureActiveBill() {
        if (active == null) throw new IllegalStateException("No active bill");
    }
}
