package application.inventory;

import domain.common.Money;
import domain.inventory.Batch;
import domain.inventory.InventoryReservation;
import ports.out.InventoryRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public final class InventoryService {
    private final InventoryRepository repo;
    private final BatchSelector selector;

    /** Preferred: pass an explicit selector (e.g., new FefoBatchSelector()). */
    public InventoryService(InventoryRepository repo, BatchSelector selector) {
        this.repo = repo;
        this.selector = selector;
    }

    /** Convenience: defaults to FEFO selector. */
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

    // -------- Channel-aware helpers (existing behavior) --------
    /**
     * POS     -> take from store (qty_in_store)
     * ONLINE  -> take from shelf (qty_on_shelf)
     */
    public List<InventoryReservation> reserveByChannel(String code, int qty, String channel) {
        if ("POS".equalsIgnoreCase(channel)) {
            return reserveFromStoreFEFO(code, qty);
        } else {
            return reserveFromShelfFEFO(code, qty);
        }
    }

    /** Match commit to where stock was reserved from. */
    public void commitReservationByChannel(List<InventoryReservation> r, String channel) {
        if ("POS".equalsIgnoreCase(channel)) {
            commitStoreReservation(r);
        } else {
            commitReservation(r);
        }
    }

    // ----------------------------------------------------------------
    // NEW: Smart inventory helpers (main_store + restock-level support)
    // ----------------------------------------------------------------

    /** Convenience view methods for UI/business rules. */
    public int shelfQty(String code) { return repo.shelfQty(code); }
    public int storeQty(String code) { return repo.storeQty(code); }
    public int mainStoreQty(String code) { return repo.mainStoreQty(code); }           // NEW
    public int restockLevel(String code) { return repo.restockLevel(code); }           // NEW

    /** Movements (FEFO) across areas. */
    public void moveStoreToShelfFEFO(String code, int qty) { repo.moveStoreToShelfFEFO(code, qty); }
    public void moveMainToShelfFEFO(String code, int qty) { repo.moveMainToShelfFEFO(code, qty); } // NEW
    public void moveMainToStoreFEFO(String code, int qty) { repo.moveMainToStoreFEFO(code, qty); } // NEW

    /**
     * Try to reserve for a channel; if primary area is short, auto-top-up
     * from MAIN_STORE (FEFO) just enough to fulfill.
     *
     * Rules:
     *  - POS  primary: STORE;  fallback top-up: MAIN -> STORE
     *  - ONLINE primary: SHELF; fallback top-up: MAIN -> SHELF
     *
     * The result lets the caller decide what to display (e.g., “now out of stock”,
     * “needed manager approval for top-up”, etc.).
     */
    public SmartReserveResult reserveByChannelSmart(String code, int requestedQty, String channel) {
        if (requestedQty <= 0) throw new IllegalArgumentException("qty must be > 0");
        repo.findItemByCode(code).orElseThrow(() -> new NoSuchElementException("Unknown item: " + code));

        final boolean pos = "POS".equalsIgnoreCase(channel);

        int primaryBefore = pos ? storeQty(code) : shelfQty(code);
        boolean willBeOutOfStock = primaryBefore == requestedQty;   // after commit, primary would hit zero
        boolean usedMainTopUp = false;

        List<InventoryReservation> plan;
        try {
            // Try from primary area first
            plan = pos ? reserveFromStoreFEFO(code, requestedQty) : reserveFromShelfFEFO(code, requestedQty);
        } catch (IllegalStateException notEnoughInPrimary) {
            // Attempt top-up from MAIN store into the primary area, then try again.
            int needed = requestedQty - Math.max(0, (pos ? storeQty(code) : shelfQty(code)));
            // If we can’t compute; be conservative and just try the full requested
            if (needed <= 0) needed = requestedQty;

            int mainAvailable = mainStoreQty(code);
            if (mainAvailable <= 0) throw notEnoughInPrimary; // nothing to top-up with

            int topUp = Math.min(needed, mainAvailable);
            if (pos) {
                moveMainToStoreFEFO(code, topUp);
            } else {
                moveMainToShelfFEFO(code, topUp);
            }
            usedMainTopUp = true;

            // re-check availability and plan again
            plan = pos ? reserveFromStoreFEFO(code, requestedQty) : reserveFromShelfFEFO(code, requestedQty);

            // recompute out-of-stock flag using the pre-top-up snapshot:
            // if we exactly matched primaryBefore, it will become 0 after commit
            willBeOutOfStock = (primaryBefore == requestedQty);
        }

        int rLevel = restockLevel(code);
        int primaryAfter = (pos ? storeQty(code) : shelfQty(code)) - requestedQty; // snapshot-ish (approx prior to commit)
        boolean atOrBelowRestockAfter = primaryAfter <= rLevel;

        return new SmartReserveResult(plan, usedMainTopUp, willBeOutOfStock, rLevel, atOrBelowRestockAfter);
    }

    /** Result DTO for the smart reservation attempt. */
    public static final class SmartReserveResult {
        public final List<InventoryReservation> reservations;
        public final boolean usedMainTopUp;
        public final boolean willBeOutOfStockAfterCommit;
        public final int restockLevel;
        public final boolean atOrBelowRestockAfter;

        public SmartReserveResult(List<InventoryReservation> reservations,
                                  boolean usedMainTopUp,
                                  boolean willBeOutOfStockAfterCommit,
                                  int restockLevel,
                                  boolean atOrBelowRestockAfter) {
            this.reservations = reservations;
            this.usedMainTopUp = usedMainTopUp;
            this.willBeOutOfStockAfterCommit = willBeOutOfStockAfterCommit;
            this.restockLevel = restockLevel;
            this.atOrBelowRestockAfter = atOrBelowRestockAfter;
        }
    }

    // -------- Internal: FEFO selection from store (backroom) --------
    private List<InventoryReservation> selectForStore(String itemCode, int requestedQty) {
        if (requestedQty <= 0) throw new IllegalArgumentException("qty must be > 0");
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
