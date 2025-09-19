// ... same imports ...
package presentation.cli;

import application.auth.AuthService;
import application.auth.CustomerAuthService;
import application.inventory.InventoryAdminService;
import application.inventory.InventoryService;
import application.inventory.RestockService;
import application.pos.POSController;
import application.reporting.ReportingService;
import domain.common.Money;
import domain.inventory.Item;
import domain.pricing.BogoPolicy;
import domain.pricing.DiscountPolicy;
import domain.pricing.PercentageDiscount;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Scanner;

public final class POSConsole {
    private final POSController pos;
    private final ReportingService reports;
    private final RestockService restock;
    private final AuthService auth;
    private final CustomerAuthService customerAuth;
    private final InventoryAdminService admin;
    private final InventoryService inv;

    public POSConsole(POSController pos,
                      ReportingService reports,
                      RestockService restock,
                      AuthService auth,
                      CustomerAuthService customerAuth,
                      InventoryAdminService admin,
                      InventoryService inv) {
        this.pos = pos;
        this.reports = reports;
        this.restock = restock;
        this.auth = auth;
        this.customerAuth = customerAuth;
        this.admin = admin;
        this.inv = inv;
    }

    // ========= ONLINE-ONLY app (no staff login) =========
    public void runOnlineApp() {
        Scanner sc = new Scanner(System.in);
        sc.useLocale(Locale.US);

        boolean customerLoggedIn = false;
        String currentCustomer = null;

        while (true) {
            while (!customerLoggedIn) {
                System.out.println("\n=== Online Portal ===");
                System.out.println("1. Login");
                System.out.println("2. Register");
                System.out.println("0. Exit");
                System.out.print("Choose: ");
                String ch = readLine(sc);
                switch (ch) {
                    case "1" -> {
                        System.out.print("Username: ");
                        String u = readLine(sc);
                        System.out.print("Password: ");
                        String p = readLine(sc);
                        if (customerAuth.login(u, p)) {
                            System.out.println(" Customer login successful. Welcome " + u + "!");
                            customerLoggedIn = true;
                            currentCustomer = u;
                        } else {
                            System.out.println(" Login failed.");
                        }
                    }
                    case "2" -> {
                        System.out.print("Choose Username: ");
                        String u = readLine(sc);
                        System.out.print("Password: ");
                        String p = readLine(sc);
                        System.out.print("Email: ");
                        String e = readLine(sc);
                        if (customerAuth.register(u, p, e)) {
                            System.out.println(" Registration successful. You can now log in.");
                        } else {
                            System.out.println(" Username already taken.");
                        }
                    }
                    case "0" -> { return; }
                    default -> System.out.println("Invalid.");
                }
            }

            System.out.println("\n=== Online Shop ===");
            System.out.println("1. New ONLINE Order");
            System.out.println("2. Logout");
            System.out.println("0. Exit");
            System.out.print("Choose: ");
            String pick = readLine(sc);

            switch (pick) {
                case "1" -> {
                    pos.setChannel("ONLINE");
                    // pos.setUser(currentCustomer); // optional: stamp customer on bill
                    billMenu(sc, "ONLINE");
                }
                case "2" -> {
                    customerLoggedIn = false;
                    currentCustomer = null;
                    System.out.println(" Logged out.");
                }
                case "0" -> { return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // ========= Store app (staff login) =========
    public void run() {
        Scanner sc = new Scanner(System.in);
        sc.useLocale(Locale.US);

        System.out.println("========================================");
        System.out.println("   Synex Outlet Store - POS System");
        System.out.println("========================================");

        while (!auth.isLoggedIn()) {
            System.out.print("Username: ");
            String user = sc.nextLine().trim();
            System.out.print("Password: ");
            String pass = sc.nextLine().trim();
            if (auth.login(user, pass)) {
                pos.setUser(user);
                pos.setChannel("POS");
                System.out.println(" Login successful! Welcome, " + user + ".");
            } else {
                System.out.println(" Login failed. Please try again.\n");
            }
        }

        boolean running = true;
        while (running) {
            final String role = auth.currentUser().role(); // e.g., CASHIER | INVENTORY_MANAGER | ADMIN

            System.out.println("\nMain Menu");
            System.out.println("---------");
            System.out.println("1. New POS Bill");
            System.out.println("2. View Reports");
            if ("INVENTORY_MANAGER".equalsIgnoreCase(role)) {
                System.out.println("3. Inventory Manager");
            }
            System.out.println("0. Logout / Exit");
            System.out.print("Choose an option: ");
            String choice = readLine(sc);

            switch (choice) {
                case "1" -> billMenu(sc, "POS");
                case "2" -> reportMenu(sc);
                case "3" -> {
                    if ("INVENTORY_MANAGER".equalsIgnoreCase(role)) managerMenu(sc);
                    else System.out.println("Invalid choice.");
                }
                case "0" -> {
                    auth.logout();
                    System.out.println(" Logged out. Goodbye!");
                    running = false;
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // ================== BILL MENU ==================
    private void billMenu(Scanner sc, String channel) {
        try {
            pos.setChannel(channel);
            pos.newBill();
            boolean active = true;

            while (active) {
                System.out.println("\n--- New " + channel.toUpperCase(Locale.ROOT) + " Bill ---");
                System.out.println("1. Add Item");
                System.out.println("2. Remove Item");
                System.out.println("3. Apply Discount");
                System.out.println("4. Show Total");
                System.out.println("5. Checkout");
                System.out.println("0. Cancel");
                System.out.print("Choose an option: ");
                String choice = readLine(sc);

                switch (choice) {
                    case "1" -> {
                        String code = promptItemCode(sc);
                        if (code == null) {
                            System.out.println("Cancelled.");
                            break;
                        }
                        Money price;
                        String name;
                        try {
                            name = inv.itemName(code);
                            price = inv.priceOf(code);
                        } catch (Exception e) {
                            System.out.println("Unknown item code. Try again.");
                            break;
                        }
                        System.out.println("Selected: " + code + "  |  " + name + "  |  Unit: " + price);

                        System.out.print("Enter Quantity: ");
                        int qty = readInt(sc);

                        // ---- Try to add; if shortfall, offer MAIN transfer (interactive) ----
                        boolean added = tryAddWithMainTopUpInteractive(sc, channel, code, qty);
                        if (!added) break; // user cancelled or still insufficient

                        // Restock level notice
                        try {
                            int restock = inv.restockLevel(code);
                            int remaining = inv.shelfQty(code) + inv.storeQty(code);
                            if (remaining <= restock) {
                                System.out.println("🔁 This item hit or fell below the restock level (" + restock + ").");
                            }
                        } catch (Exception ignored) { }

                        // Auto top-up when *primary* becomes empty (kept behavior)
                        try {
                            if ("POS".equalsIgnoreCase(channel)) {
                                int storeLeft = inv.storeQty(code);
                                if (storeLeft <= 0) {
                                    admin.addBatch(code, null, 0, 100);
                                    System.out.println("🔄 Store empty — auto top-up 100 from MAIN → STORE.");
                                }
                            } else {
                                int shelfLeft = inv.shelfQty(code);
                                if (shelfLeft <= 0) {
                                    admin.addBatch(code, null, 100, 0);
                                    System.out.println("🔄 Shelf empty — auto top-up 100 from MAIN → SHELF.");
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("⚠️ Auto top-up failed: " + e.getMessage());
                        }
                    }
                    case "2" -> {
                        System.out.print("Enter Item Code to remove: ");
                        String code = readLine(sc);
                        pos.removeItem(code);
                        System.out.println("✅ Item removed.");
                    }
                    case "3" -> applyDiscountMenu(sc);
                    case "4" -> {
                        Money t = pos.total();
                        System.out.println("💰 Current Total: " + t);
                    }
                    case "5" -> {
                        if ("ONLINE".equalsIgnoreCase(channel)) {
                            Money due = pos.total();
                            System.out.println("Amount due: " + due);
                            System.out.print("Card last 4 digits: ");
                            String last4 = readLine(sc);
                            pos.checkoutCard(last4);
                            System.out.println("✅ Online checkout complete (Card ****" + last4 + ").");
                        } else {
                            checkoutMenu(sc);
                        }
                        active = false;
                    }
                    case "0" -> {
                        System.out.println("Cancelled.");
                        active = false;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            }
        } catch (Exception ex) {
            System.out.println("⚠️ Error: " + ex.getMessage());
        }
    }

    // ===== NEW: Try to add; if shortfall, show store/main and offer FEFO MAIN transfer =====
    private boolean tryAddWithMainTopUpInteractive(Scanner sc, String channel, String code, int qty) {
        try {
            pos.addItem(code, qty);
            System.out.println("✅ Item(s) added.");
            return true;
        } catch (IllegalStateException ex) {
            final String msg = ex.getMessage() == null ? "" : ex.getMessage();

            if ("POS".equalsIgnoreCase(channel) && msg.contains("Not enough quantity in store")) {
                int inStore = safe(() -> inv.storeQty(code), 0);
                int inMain  = safe(() -> inv.mainStoreQty(code), 0);
                printStoreMainSnapshot(code, inStore, inMain);

                System.out.println("Only " + inStore + " unit(s) available in STORE.");
                if (inMain <= 0) {
                    System.out.println("MAIN has 0 available. Cannot top up.");
                    return false;
                }
                if (!promptYesNo(sc, "Do you want to transfer from MAIN store? (y/n): ")) {
                    return false;
                }

                int shortage = Math.max(0, qty - inStore);
                int maxTransfer = Math.min(inMain, shortage);
                if (maxTransfer <= 0) maxTransfer = Math.min(inMain, qty); // fallback

                int toMove = promptQty(sc,
                        "How many units to transfer MAIN → STORE? (max " + inMain + ", suggested " + maxTransfer + "): ",
                        1, inMain);

                try {
                    inv.moveMainToStoreFEFOWithUser(code, toMove, auth.currentUser().username()); // Pass operator username
                    System.out.println("➡️ Transferred " + toMove + " MAIN → STORE.");
                } catch (Exception mvEx) {
                    System.out.println("⚠️ Transfer failed: " + mvEx.getMessage());
                    return false;
                }

                // retry add
                try {
                    pos.addItem(code, qty);
                    System.out.println("✅ Item(s) added after transfer.");
                    return true;
                } catch (Exception ex2) {
                    System.out.println("⚠️ Still insufficient after transfer: " + ex2.getMessage());
                    return false;
                }
            }
            throw ex;
        }
    }

    // ===== Helpers =====
    private void printStoreMainSnapshot(String code, int inStore, int inMain) {
        System.out.println("========================================");
        System.out.println("Item: " + code);
        System.out.println(" - qty_in_store: " + inStore);
        System.out.println(" - qty_in_main : " + inMain);
        System.out.println("========================================");
    }

    private boolean promptYesNo(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = readLine(sc).toLowerCase(Locale.ROOT);
            if (s.equals("y") || s.equals("yes")) return true;
            if (s.equals("n") || s.equals("no")) return false;
            System.out.println("Please answer y/n.");
        }
    }

    private int promptQty(Scanner sc, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String s = readLine(sc);
            try {
                int v = Integer.parseInt(s);
                if (v < min) { System.out.println("Enter ≥ " + min); continue; }
                if (v > max) { System.out.println("Enter ≤ " + max); continue; }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid number.");
            }
        }
    }

    private <T> T safe(java.util.concurrent.Callable<T> c, T def) {
        try { return c.call(); } catch (Exception e) { return def; }
    }

    // ===== existing item picker, reports, manager, etc. (unchanged) =====

    private String promptItemCode(Scanner sc) {
        while (true) {
            System.out.print("Enter Item Code (or '?' to browse/search, '0' to cancel): ");
            String input = readLine(sc);
            if ("0".equals(input)) return null;
            if ("?".equals(input)) {
                String picked = lookupItem(sc);
                if (picked != null) return picked;
                continue;
            }
            try { inv.priceOf(input); return input; }
            catch (Exception e) { System.out.println("Unknown code. Type '?' to browse or search."); }
        }
    }

    private String lookupItem(Scanner sc) {
        while (true) {
            System.out.println("\nFind Item");
            System.out.println("1. Browse all items");
            System.out.println("2. Search by code/name");
            System.out.println("0. Back");
            System.out.print("Choose: ");
            String ch = readLine(sc);
            switch (ch) {
                case "1" -> {
                    var items = inv.listAllItems();
                    showItems(items, 50);
                    System.out.print("Enter item code to select (or 0 to back): ");
                    String code = readLine(sc);
                    if ("0".equals(code)) return null;
                    try { inv.priceOf(code); return code; }
                    catch (Exception e) { System.out.println("Invalid code. Try again."); }
                }
                case "2" -> {
                    System.out.print("Search text: ");
                    String q = readLine(sc);
                    var items = inv.searchItems(q);
                    if (items.isEmpty()) { System.out.println("No matches."); break; }
                    showItems(items, 50);
                    System.out.print("Enter item code to select (or 0 to back): ");
                    String code = readLine(sc);
                    if ("0".equals(code)) return null;
                    try { inv.priceOf(code); return code; }
                    catch (Exception e) { System.out.println("Invalid code. Try again."); }
                }
                case "0" -> { return null; }
                default -> System.out.println("Invalid.");
            }
        }
    }

    private void showItems(java.util.List<Item> items, int limit) {
        System.out.printf("%-14s %-30s %s%n", "ITEM CODE", "NAME", "UNIT PRICE");
        System.out.println("---------------------------------------------------------------");
        int count = 0;
        for (Item it : items) {
            System.out.printf("%-14s %-30s %s%n", it.code(), it.name(), it.unitPrice());
            if (++count >= limit) {
                System.out.println("... (showing first " + limit + " only)");
                break;
            }
        }
    }

    private void applyDiscountMenu(Scanner sc) {
        System.out.println("Choose Discount:");
        System.out.println("1. Percentage");
        System.out.println("2. BOGO (Buy One Get One)");
        System.out.println("0. None");
        System.out.print("Selection: ");
        String d = readLine(sc);

        try {
            DiscountPolicy p = null;
            if ("1".equals(d)) {
                System.out.print("Enter percentage (0-100): ");
                int perc = readInt(sc);
                p = new PercentageDiscount(perc);
            } else if ("2".equals(d)) {
                p = new BogoPolicy();
            }
            pos.applyDiscount(p);
            System.out.println(" Discount applied.");
        } catch (Exception ex) {
            System.out.println(" Error: " + ex.getMessage());
        }
    }

    private void checkoutMenu(Scanner sc) {
        try {
            Money due = pos.total();
            System.out.println("Amount due: " + due);
            System.out.println("1. Pay by Cash");
            System.out.println("2. Pay by Card");
            System.out.println("0. Cancel");
            System.out.print("Choose: ");
            String pay = readLine(sc);

            switch (pay) {
                case "1" -> {
                    System.out.print("Cash received: ");
                    double amt = readDouble(sc);
                    pos.checkoutCash(amt);
                    System.out.println(" Checkout complete (Cash).");
                }
                case "2" -> {
                    System.out.print("Card last 4 digits: ");
                    String last4 = readLine(sc);
                    pos.checkoutCard(last4);
                    System.out.println(" Checkout complete (Card ****" + last4 + ").");
                }
                case "0" -> System.out.println("Cancelled.");
                default -> System.out.println("Invalid choice.");
            }
        } catch (Exception ex) {
            System.out.println(" Error: " + ex.getMessage());
        }
    }

    private void reportMenu(Scanner sc) {
        System.out.println("\n--- Reports ---");
        System.out.println("1. Daily Sales");
        System.out.println("2. Best Sellers");
        System.out.println("3. Revenue Series");
        System.out.println("4. Reshelving (toward shelf target)");
        System.out.println("5. Reorder (below threshold)");
        System.out.println("6. Stock by Batch");
        System.out.println("7. Bills (range)");
        System.out.println("8. Restock (≤ max(50, restock level))");
        System.out.println("0. Back");
        System.out.print("Choose: ");
        String choice = readLine(sc);

        try {
            switch (choice) {
                case "1" -> {
                    System.out.print("Enter date (yyyy-MM-dd): ");
                    reports.printDailySales(LocalDate.parse(readLine(sc)));
                    System.out.println(" Printed to console.");
                }
                case "2" -> {
                    System.out.print("From date (yyyy-MM-dd): ");
                    LocalDate from = LocalDate.parse(readLine(sc));
                    System.out.print("To date (yyyy-MM-dd): ");
                    LocalDate to = LocalDate.parse(readLine(sc));
                    reports.printBestSellers(from, to, 10);
                    System.out.println(" Printed to console.");
                }
                case "3" -> {
                    System.out.print("From date (yyyy-MM-dd): ");
                    LocalDate from = LocalDate.parse(readLine(sc));
                    System.out.print("To date (yyyy-MM-dd): ");
                    LocalDate to = LocalDate.parse(readLine(sc));
                    reports.printRevenueSeries(from, to);
                    System.out.println(" Printed to console.");
                }
                case "4" -> {
                    System.out.print("Shelf target (e.g., 100): ");
                    int target = readInt(sc);
                    reports.printReshelving(target);
                    System.out.println(" Printed to console.");
                }
                case "5" -> {
                    System.out.print("Reorder threshold (e.g., 50): ");
                    int th = readInt(sc);
                    reports.printReorder(th);
                    System.out.println("Printed to console.");
                }
                case "6" -> {
                    System.out.print("Filter by item code (blank for all): ");
                    String code = readLine(sc);
                    if (code.isBlank()) code = null;
                    reports.printStockByBatch(code);
                    System.out.println("Printed to console.");
                }
                case "7" -> {
                    System.out.print("From date (yyyy-MM-dd): ");
                    LocalDate from = LocalDate.parse(readLine(sc));
                    System.out.print("To date (yyyy-MM-dd): ");
                    LocalDate to = LocalDate.parse(readLine(sc));
                    reports.printBills(from, to);
                    System.out.println(" Printed to console.");
                }
                case "8" -> {
                    reports.printRestock();
                    System.out.println(" Printed to console.");
                }
                case "0" -> { /* back */ }
                default -> System.out.println("Invalid choice.");
            }
        } catch (Exception ex) {
            System.out.println(" Error: " + ex.getMessage());
        }
    }

    private void restockMenu(Scanner sc) {
        System.out.println("\n--- Restock ---");
        System.out.print("Enter item code: ");
        String code = readLine(sc);
        try {
            restock.restockToTarget(code, 100);
            System.out.println(" Shelf topped up to 100 for " + code);
        } catch (Exception e) {
            System.out.println(" Error: " + e.getMessage());
        }
    }

    private void managerMenu(Scanner sc) {
        boolean loop = true;
        while (loop) {
            System.out.println("\n--- Inventory Manager ---");
            System.out.println("1. Add Batch");
            System.out.println("2. Edit Batch Quantities");
            System.out.println("3. Update Batch Expiry");
            System.out.println("4. Delete Batch");
            System.out.println("5. Move MAIN -> STORE (FEFO)");
            System.out.println("6. Item Catalog (CRUD)"); // <— NEW submenu
            System.out.println("7. Move MAIN -> SHELF (FEFO)");
            System.out.println("8. Move STORE -> SHELF (FEFO)");
            System.out.println("0. Back");
            System.out.print("Choose: ");
            String ch = readLine(sc);
            try {
                switch (ch) {
                    case "1" -> {
                        System.out.print("Item code: "); String code = readLine(sc);
                        System.out.print("Expiry (yyyy-MM-dd or blank): "); String s = readLine(sc);
                        java.time.LocalDate exp = (s.isBlank() ? null : java.time.LocalDate.parse(s));
                        System.out.print("Qty on SHELF: "); int qs = readInt(sc);
                        System.out.print("Qty in STORE: "); int qst = readInt(sc);
                        admin.addBatch(code, exp, qs, qst);
                        System.out.println(" Batch added.");
                    }
                    case "2" -> {
                        System.out.print("Batch ID: "); long id = Long.parseLong(readLine(sc));
                        System.out.print("New Qty on SHELF: "); int qs = readInt(sc);
                        System.out.print("New Qty in STORE: "); int qst = readInt(sc);
                        admin.editBatchQuantities(id, qs, qst);
                        System.out.println(" Batch quantities updated.");
                    }
                    case "3" -> {
                        System.out.print("Batch ID: "); long id = Long.parseLong(readLine(sc));
                        System.out.print("New expiry (yyyy-MM-dd or blank to clear): "); String s = readLine(sc);
                        java.time.LocalDate exp = (s.isBlank() ? null : java.time.LocalDate.parse(s));
                        admin.updateBatchExpiry(id, exp);
                        System.out.println(" Batch expiry updated.");
                    }
                    case "4" -> {
                        System.out.print("Batch ID: "); long id = Long.parseLong(readLine(sc));
                        admin.deleteBatch(id);
                        System.out.println("Batch deleted.");
                    }
                    case "5" -> { // MAIN -> STORE
                        System.out.print("Item code: "); String code = readLine(sc);
                        int shelfBefore = inv.shelfQty(code);
                        int storeBefore = inv.storeQty(code);
                        int mainBefore  = inv.mainStoreQty(code);
                        System.out.println("Before:");
                        System.out.println("  SHELF=" + shelfBefore + "  STORE=" + storeBefore + "  MAIN=" + mainBefore);

                        if (mainBefore <= 0) {
                            System.out.println("⚠️ No stock in MAIN to move.");
                            break;
                        }
                        System.out.print("Qty to move MAIN → STORE (max " + mainBefore + "): ");
                        int q = readInt(sc);
                        if (q <= 0 || q > mainBefore) { System.out.println("⚠️ Invalid quantity."); break; }

                        admin.moveMainToStoreFEFO(code, q);

                        int shelfAfter = inv.shelfQty(code);
                        int storeAfter = inv.storeQty(code);
                        int mainAfter  = inv.mainStoreQty(code);
                        System.out.println("After:");
                        System.out.println("  SHELF=" + shelfAfter + "  STORE=" + storeAfter + "  MAIN=" + mainAfter);
                        System.out.println("✅ Moved " + q + " MAIN → STORE (FEFO).");
                    }
                    case "6" -> itemCatalogMenu(sc); // <— NEW
                    case "7" -> { // MAIN -> SHELF
                        System.out.print("Item code: "); String code = readLine(sc);
                        int shelfBefore = inv.shelfQty(code);
                        int storeBefore = inv.storeQty(code);
                        int mainBefore  = inv.mainStoreQty(code);
                        System.out.println("Before:");
                        System.out.println("  SHELF=" + shelfBefore + "  STORE=" + storeBefore + "  MAIN=" + mainBefore);

                        if (mainBefore <= 0) {
                            System.out.println("⚠️ No stock in MAIN to move.");
                            break;
                        }
                        System.out.print("Qty to move MAIN → SHELF (max " + mainBefore + "): ");
                        int q = readInt(sc);
                        if (q <= 0 || q > mainBefore) { System.out.println("⚠️ Invalid quantity."); break; }

                        admin.moveMainToShelfFEFO(code, q);

                        int shelfAfter = inv.shelfQty(code);
                        int storeAfter = inv.storeQty(code);
                        int mainAfter  = inv.mainStoreQty(code);
                        System.out.println("After:");
                        System.out.println("  SHELF=" + shelfAfter + "  STORE=" + storeAfter + "  MAIN=" + mainAfter);
                        System.out.println("✅ Moved " + q + " MAIN → SHELF (FEFO).");
                    }
                    case "8" -> {
                        System.out.print("Item code: "); String code = readLine(sc);
                        System.out.print("Qty to move STORE → SHELF: "); int q = readInt(sc);
                        admin.moveStoreToShelfFEFO(code, q);
                        System.out.println("✅ Moved " + q + " STORE → SHELF (FEFO).");
                    }
                    case "0" -> loop = false;
                    default -> System.out.println("Invalid choice.");
                }
            } catch (Exception ex) {
                System.out.println("⚠️ Error: " + ex.getMessage());
            }
        }
    }

    private void itemCatalogMenu(Scanner sc) {
        boolean loop = true;
        while (loop) {
            System.out.println("\n--- Item Catalog ---");
            System.out.println("1. Add New Item");
            System.out.println("2. Update Item");
            System.out.println("3. Delete Item");
            System.out.println("4. List All Items");
            System.out.println("0. Back");
            System.out.print("Choose: ");
            String ch = readLine(sc);
            try {
                switch (ch) {
                    case "1" -> {
                        System.out.print("Item code: "); String code = readLine(sc);
                        System.out.print("Name: "); String name = readLine(sc);
                        System.out.print("Unit price: "); double price = readDouble(sc);
                        admin.addNewItem(code, name, domain.common.Money.of(price));
                        System.out.println("✅ Item added to catalog.");
                    }
                    case "2" -> {
                        System.out.print("Item code to update: "); String code = readLine(sc);
                        System.out.print("New name (leave blank to keep current): "); String name = readLine(sc);
                        System.out.print("New unit price (0 to keep current): "); double price = readDouble(sc);

                    if (!name.isBlank()) admin.renameItem(code, name);
                        if (price > 0) admin.setItemPrice(code, domain.common.Money.of(price));
                        System.out.println("✅ Item updated.");
                    }
                    case "3" -> {
                        System.out.print("Item code to delete: "); String code = readLine(sc);
                        admin.deleteItem(code);
                        System.out.println("✅ Item deleted.");
                    }
                    case "4" -> {
                        var items = inv.listAllItems();
                        System.out.println("\nAll Items:");
                        System.out.printf("%-14s %-30s %s%n", "ITEM CODE", "NAME", "UNIT PRICE");
                        System.out.println("---------------------------------------------------------------");
                        for (Item it : items) {
                            System.out.printf("%-14s %-30s %s%n", it.code(), it.name(), it.unitPrice());
                        }
                    }
                    case "0" -> loop = false;
                    default -> System.out.println("Invalid choice.");
                }
            } catch (Exception ex) {
                System.out.println("⚠️ Error: " + ex.getMessage());
            }
        }
    }

    // -------- input helpers --------
    private static String readLine(Scanner sc) {
        String s = sc.nextLine();
        while (s != null && s.isEmpty() && sc.hasNextLine()) {
            s = sc.nextLine();
        }
        return s == null ? "" : s.trim();
    }
    private static int readInt(Scanner sc) {
        while (true) {
            String s = readLine(sc);
            try { return Integer.parseInt(s); }
            catch (NumberFormatException e) { System.out.print("Enter a number: "); }
        }
    }
    private static double readDouble(Scanner sc) {
        while (true) {
            String s = readLine(sc);
            try { return Double.parseDouble(s); }
            catch (NumberFormatException e) { System.out.print("Enter a valid amount: "); }
        }
    }
}
