package application.inventory;

import domain.common.Money;
import domain.inventory.Batch;
import domain.inventory.InventoryReservation;
import domain.inventory.Item;
import ports.out.InventoryRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public final class InventoryService {
    private final InventoryRepository repo;
    private final BatchSelector selector;

    // Dependency Injection of repository and selection strategy
    public InventoryService(InventoryRepository repo, BatchSelector selector) {
        this.repo = repo;
        this.selector = selector;
    }

    public void moveMainToStoreFEFOWithUser(String code, int qty) {
        repo.moveMainToStoreFEFO(code, qty); // Only two arguments allowed
    }

    @Deprecated(forRemoval = false) // kept for tests/convenience
    @SuppressWarnings("unused")
    public InventoryService(InventoryRepository repo) {
        this(repo, new FefoBatchSelector());
    }

    public String itemName(String code) {
        return repo.findItemByCode(code)
                .orElseThrow(() -> new NoSuchElementException("Unknown item: " + code))
                .name();
    }

    /** Expose unit price (used by UI/Controller). */
    public Money priceOf(String code) { return repo.priceOf(code); }

    /** Reserve from shelf (ONLINE primary source). */
    public List<InventoryReservation> reserveFromShelfFEFO(String code, int qty) {
        return selector.selectFor(code, qty, repo);
    }

    /** Reserve from store/backroom (POS primary source). */
    public List<InventoryReservation> reserveFromStoreFEFO(String code, int qty) {
        return selectForStore(code, qty);
    }

    /** Commit reservations that were taken from shelf. */
    public void commitReservation(List<InventoryReservation> r) { repo.commitReservations(r); }

    /** Commit reservations that were taken from store. */
    public void commitStoreReservation(List<InventoryReservation> r) { repo.commitStoreReservations(r); }

    // -------- Channel-aware helpers (legacy behavior) --------
    public List<InventoryReservation> reserveByChannel(String code, int qty, String channel) {
        if ("POS".equalsIgnoreCase(channel)) {
            return reserveFromStoreFEFO(code, qty);
        } else {
            return reserveFromShelfFEFO(code, qty);
        }
    }

    public void commitReservationByChannel(List<InventoryReservation> r, String channel) {
        if ("POS".equalsIgnoreCase(channel)) {
            commitStoreReservation(r);
        } else {
            commitReservation(r);
        }
    }

    // ----------------------------------------------------------------
    // NEW: Smart inventory (primary -> optional secondary -> main backfill)
    // ----------------------------------------------------------------

    public int shelfQty(String code) { return repo.shelfQty(code); }
    public int storeQty(String code) { return repo.storeQty(code); }
    public int mainStoreQty(String code) { return repo.mainStoreQty(code); }
    public int restockLevel(String code) { return repo.restockLevel(code); }

    public void moveStoreToShelfFEFO(String code, int qty) { repo.moveStoreToShelfFEFO(code, qty); }
    public void moveMainToShelfFEFO(String code, int qty) { repo.moveMainToShelfFEFO(code, qty); }
    public void moveMainToStoreFEFO(String code, int qty) { repo.moveMainToStoreFEFO(code, qty); }

    /**
     * Smart plan for a line item:
     *  - POS  primary = STORE, secondary = SHELF
     *  - ONLINE primary = SHELF, secondary = STORE
     *  - If primary insufficient and approveUseOtherSide==true, pull remainder from secondary (FEFO).
     *  - If secondary still insufficient, with managerApprovedBackfill==true, move MAIN -> secondary to fulfill.
     *  - After using secondary, if secondary would fall <= restock level, and managerApprovedBackfill==true,
     *    top up secondary from MAIN back to restock level.
     *  - Flag “out of stock” if the customer buys exactly the restock level that existed on the primary side.
     */
    public SmartPick reserveSmart(String code, int requestedQty, String channel,
                                  boolean approveUseOtherSide, boolean managerApprovedBackfill) {
        if (requestedQty <= 0) throw new IllegalArgumentException("qty must be > 0");
        repo.findItemByCode(code).orElseThrow(() -> new NoSuchElementException("Unknown item: " + code));

        final boolean pos = "POS".equalsIgnoreCase(channel);
        final int restock = restockLevel(code);

        // Primary/secondary snapshots BEFORE any moves
        int primaryBefore  = pos ? storeQty(code) : shelfQty(code);
        int secondaryBefore= pos ? shelfQty(code)  : storeQty(code);
        int mainBefore     = mainStoreQty(code);

        int primaryTake = Math.min(requestedQty, primaryBefore);
        int remaining   = requestedQty - primaryTake;

        // Collect reservations split by area (no DB writes yet)
        List<InventoryReservation> shelfRes = new ArrayList<>();
        List<InventoryReservation> storeRes = new ArrayList<>();

        if (primaryTake > 0) {
            if (pos) storeRes.addAll(reserveFromStoreFEFO(code, primaryTake));
            else     shelfRes.addAll(reserveFromShelfFEFO(code, primaryTake));
        }

        int movedFromMainToSecondary = 0;
        int movedFromMainForBackfill = 0;

        if (remaining > 0) {
            if (!approveUseOtherSide)
                throw new IllegalStateException("Not enough in primary. Approval to use secondary stock is required.");

            // how much secondary already has
            int secUseFromExisting = Math.min(remaining, secondaryBefore);
            int needBeyondSecondary = remaining - secUseFromExisting;

            if (needBeyondSecondary > 0) {
                if (!managerApprovedBackfill)
                    throw new IllegalStateException("Not enough in secondary. Manager approval required to pull from MAIN.");

                int canTopUpFromMain = Math.min(needBeyondSecondary, mainBefore);
                if (canTopUpFromMain <= 0)
                    throw new IllegalStateException("Insufficient quantity in MAIN to fulfill.");

                // Move MAIN -> secondary to be able to fulfill the remainder
                if (pos) moveMainToShelfFEFO(code, canTopUpFromMain);
                else     moveMainToStoreFEFO(code, canTopUpFromMain);

                movedFromMainToSecondary += canTopUpFromMain;
                secondaryBefore += canTopUpFromMain; // logical snapshot
            }

            // Now reserve the remainder from secondary
            if (pos) shelfRes.addAll(reserveFromShelfFEFO(code, remaining));
            else     storeRes.addAll(reserveFromStoreFEFO(code, remaining));

            // After this sale, simulate secondary remaining
            int secondaryAfter = secondaryBefore - remaining;

            // If secondary falls to/below restock level, optionally backfill to restock level
            if (secondaryAfter <= restock && managerApprovedBackfill) {
                int needToRestockToLevel = restock - secondaryAfter;
                int canTopUp = Math.min(Math.max(0, needToRestockToLevel), mainBefore);
                if (canTopUp > 0) {
                    if (pos) moveMainToShelfFEFO(code, canTopUp);
                    else     moveMainToStoreFEFO(code, canTopUp);
                    movedFromMainForBackfill += canTopUp;
                }
            }
        }

        // Flag: “Item is now out of stock.” when the customer buys exactly the restock level that existed on the primary side.
        boolean itemNowOutOfStockMsg =
                (primaryTake == primaryBefore) && (primaryBefore == restock);

        return new SmartPick(
                shelfRes,
                storeRes,
                itemNowOutOfStockMsg,
                movedFromMainToSecondary > 0,
                movedFromMainForBackfill > 0,
                restock
        );
    }

    /** Result DTO for the smart plan (split reservations + flags). */
    public static final class SmartPick {
        public final List<InventoryReservation> shelfReservations; // to commit with repo.commitReservations()
        public final List<InventoryReservation> storeReservations; // to commit with repo.commitStoreReservations()
        public final boolean showOutOfStockMessage;                // “Item is now out of stock.”
        public final boolean usedMainToFulfill;                    // MAIN was used to meet the requested qty
        public final boolean backfilledSecondaryToRestockLevel;    // MAIN was used to restore secondary to restock level
        public final int restockLevel;

        public SmartPick(List<InventoryReservation> shelfReservations,
                         List<InventoryReservation> storeReservations,
                         boolean showOutOfStockMessage,
                         boolean usedMainToFulfill,
                         boolean backfilledSecondaryToRestockLevel,
                         int restockLevel) {
            this.shelfReservations = shelfReservations;
            this.storeReservations = storeReservations;
            this.showOutOfStockMessage = showOutOfStockMessage;
            this.usedMainToFulfill = usedMainToFulfill;
            this.backfilledSecondaryToRestockLevel = backfilledSecondaryToRestockLevel;
            this.restockLevel = restockLevel;
        }
    }

    // ===== NEW: Catalog helpers for UI =====
    public List<Item> listAllItems() {
        return repo.listAllItems();
    }

    public List<Item> searchItems(String query) {
        return repo.searchItemsByNameOrCode(query);
    }

    // -------- Internal: FEFO selection from store (backroom) --------
    private List<InventoryReservation> selectForStore(String itemCode, int requestedQty) {
        if (requestedQty <= 0) return List.of();
        repo.findItemByCode(itemCode)
                .orElseThrow(() -> new NoSuchElementException("Unknown item: " + itemCode));

        var batches = repo.findBatchesInStore(itemCode); // FEFO-ordered in repo
        int remaining = requestedQty;
        List<InventoryReservation> plan = new ArrayList<>();

        for (Batch b : batches) {
            if (remaining == 0) break;
            int take = Math.min(b.qtyInStore(), remaining);
            if (take > 0) {
                plan.add(new InventoryReservation(b.id(), itemCode, take));
                remaining -= take;
            }
        }
        if (remaining > 0) {
            throw new IllegalStateException("Not enough quantity in store for " + itemCode);
        }
        return plan;
    }
}
