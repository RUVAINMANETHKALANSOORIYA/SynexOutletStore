package application.pos;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.billing.BillWriter;
import domain.inventory.InventoryReservation;
import ports.out.BillRepository;
import application.events.EventBus;
import application.events.events.BillPaid;
import application.events.events.RestockThresholdHit;
import application.events.events.StockDepleted;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles all checkout-related operations for the POS system
 */
public final class CheckoutService {
    private final BillRepository bills;
    private final BillWriter writer;
    private final EventBus events;
    private final InventoryManager inventoryManager;

    public CheckoutService(BillRepository bills, BillWriter writer, EventBus events, InventoryManager inventoryManager) {
        this.bills = bills;
        this.writer = writer;
        this.events = events;
        this.inventoryManager = inventoryManager;
    }

    /**
     * Complete checkout process with bill persistence and cleanup
     */
    public void completeCheckout(Bill bill, List<InventoryReservation> shelfReservations,
                                List<InventoryReservation> storeReservations, String currentUser, String currentChannel) {
        try {
            // Set bill metadata
            bill.setUserName(currentUser);
            bill.setChannel(currentChannel);

            // Database operations with error handling
            saveBill(bill);
            writeBillReceipt(bill);

            // Inventory commitment operations
            commitInventoryReservations(shelfReservations, storeReservations);

            // Event publication with error handling
            publishBillPaidEvent(bill, currentChannel, currentUser);

            // Stock level event processing
            publishStockLevelEvents(bill);

        } catch (POSOperationException e) {
            throw e; // Re-throw our specific exceptions
        } catch (Exception e) {
            throw new POSOperationException("Critical error during checkout process: " + e.getMessage(), e);
        }
    }

    /**
     * Save bill to database
     */
    private void saveBill(Bill bill) {
        try {
            bills.saveBill(bill);
        } catch (Exception e) {
            throw new POSOperationException("Failed to save bill to database: " + e.getMessage(), e);
        }
    }

    /**
     * Write bill receipt
     */
    private void writeBillReceipt(Bill bill) {
        try {
            writer.write(bill);
        } catch (Exception e) {
            throw new POSOperationException("Failed to write bill receipt: " + e.getMessage(), e);
        }
    }

    /**
     * Commit inventory reservations
     */
    private void commitInventoryReservations(List<InventoryReservation> shelfReservations,
                                           List<InventoryReservation> storeReservations) {
        inventoryManager.commitShelfReservations(shelfReservations);
        inventoryManager.commitStoreReservations(storeReservations);
    }

    /**
     * Publish bill paid event
     */
    private void publishBillPaidEvent(Bill bill, String channel, String user) {
        try {
            events.publish(new BillPaid(bill.number(), bill.total(), channel, user));
        } catch (Exception e) {
            // Log the error but don't fail the checkout - bill is already saved
            System.err.println("Warning: Failed to publish BillPaid event: " + e.getMessage());
        }
    }

    /**
     * Publish stock level events for restock notifications
     */
    private void publishStockLevelEvents(Bill bill) {
        try {
            Set<String> codes = new LinkedHashSet<>();
            for (BillLine l : bill.lines()) codes.add(l.itemCode());

            for (String code : codes) {
                try {
                    InventoryManager.StockInfo stockInfo = inventoryManager.getStockInfo(code);
                    int totalLeft = stockInfo.shelf() + stockInfo.store();
                    int threshold = stockInfo.threshold();

                    if (totalLeft == 0) {
                        events.publish(new StockDepleted(code));
                    } else if (totalLeft <= threshold) {
                        events.publish(new RestockThresholdHit(code, totalLeft, threshold));
                    }
                } catch (Exception e) {
                    // Log individual item stock check failures but continue with others
                    System.err.println("Warning: Failed to check stock levels for item " + code + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // Log the error but don't fail the checkout
            System.err.println("Warning: Failed to process stock level events: " + e.getMessage());
        }
    }

    /**
     * Custom exception for POS operations
     */
    public static class POSOperationException extends RuntimeException {
        public POSOperationException(String message) {
            super(message);
        }

        public POSOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
