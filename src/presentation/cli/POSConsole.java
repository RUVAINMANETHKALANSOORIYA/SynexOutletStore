package presentation.cli;

import ports.in.AuthService;
import application.auth.CustomerAuthService;
import application.inventory.InventoryAdminService;
import ports.in.InventoryService;
import application.inventory.RestockService;
import application.pos.controllers.POSController;
import ports.in.ReportingService;
import domain.common.Money;
import domain.inventory.Item;

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
                        try {
                            if (customerAuth.login(u, p)) {
                                System.out.println(" Customer login successful. Welcome " + u + "!");
                                customerLoggedIn = true;
                                currentCustomer = u;
                            } else {
                                System.out.println(" Login failed.");
                            }
                        } catch (application.auth.CustomerAuthService.AuthenticationException e) {
                            System.out.println("Error: " + e.getMessage());
                        }
                    }
                    case "2" -> {
                        System.out.print("Choose Username: ");
                        String u = readLine(sc);
                        System.out.print("Password: ");
                        String p = readLine(sc);
                        System.out.print("Email: ");
                        String e = readLine(sc);
                        try {
                            if (customerAuth.register(u, p, e)) {
                                System.out.println(" Registration successful. You can now log in.");
                            } else {
                                System.out.println(" Username already taken.");
                            }
                        } catch (application.auth.CustomerAuthService.AuthenticationException ex) {
                            System.out.println("Error: " + ex.getMessage());
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
                    pos.setUser(currentCustomer); // Set the logged-in customer username for online orders
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
            try {
                if (auth.login(user, pass)) {
                    pos.setUser(user);
                    pos.setChannel("POS");
                    System.out.println(" Login successful! Welcome, " + user + ".");
                } else {
                    System.out.println(" Login failed. Please try again.\n");
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Error " + e.getMessage());
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

    private void billMenu(Scanner sc, String channel) {
        try {
            pos.setChannel(channel);
            // For online orders, ensure customer username is set properly
            if ("ONLINE".equalsIgnoreCase(channel) && customerAuth.isLoggedIn()) {
                pos.setUser(customerAuth.currentUser().name());
            }
            pos.newBill();
            boolean active = true;

            while (active) {
                System.out.println("\n--- New " + channel.toUpperCase(Locale.ROOT) + " Bill ---");
                System.out.println("1. Add Item");
                System.out.println("2. Remove Item");
                // Removed manual discount option - only inventory managers can apply discounts through batch management
                System.out.println("3. Show Total");
                System.out.println("4. Checkout");
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

                        // Enhanced validation: Check if item exists in catalog
                        Money price;
                        String name;
                        try {
                            // First validate the item exists in the catalog
                            if (!isValidItemCode(code)) {
                                System.out.println(" You can't add item which is not in the item list. Please check the code: " + code);
                                System.out.println(" Type '?' to browse available items or search by name.");
                                break;
                            }

                            name = inv.itemName(code);
                            price = inv.priceOf(code);
                        } catch (Exception e) {
                            System.out.println("You can't add item which is not in the item list. Please check the code: " + code);
                            System.out.println(" Type '?' to browse available items or search by name.");
                            break;
                        }

                        System.out.println("Selected: " + code + "  |  " + name + "  |  Unit: " + price + " LKR");

                        System.out.print("Enter Quantity: ");
                        int qty = readInt(sc);

                        if (qty <= 0) {
                            System.out.println(" Quantity must be greater than 0.");
                            break;
                        }

                        boolean added = tryAddWithMainTopUpInteractive(sc, channel, code, qty);
                        if (!added) break; // user cancelled or still insufficient

                        try {
                            int restock = inv.restockLevel(code);
                            int remaining = inv.shelfQty(code) + inv.storeQty(code);
                            if (remaining <= restock) {
                                System.out.println("This item hit or fell below the restock level (" + restock + ").");
                            }
                        } catch (Exception ignored) { }

                        try {
                            if ("POS".equalsIgnoreCase(channel)) {
                                int storeLeft = inv.storeQty(code);
                                if (storeLeft <= 0) {
                                    admin.addBatch(code, null, 0, 100);
                                    System.out.println(" Store empty — auto top-up 100 from MAIN → STORE.");
                                }
                            } else {
                                int shelfLeft = inv.shelfQty(code);
                                if (shelfLeft <= 0) {
                                    admin.addBatch(code, null, 100, 0);
                                    System.out.println(" Shelf empty — auto top-up 100 from MAIN → SHELF.");
                                }
                            }
                        } catch (Exception e) {
                            System.out.println(" Auto top-up failed: " + e.getMessage());
                        }
                    }
                    case "2" -> {
                        System.out.print("Enter Item Code to remove: ");
                        String code = readLine(sc);
                        pos.removeItem(code);
                        System.out.println(" Item removed.");
                    }
                    case "3" -> {
                        // Enhanced total display with discount details
                        try {
                            Money total = pos.total();
                            System.out.println("\n=== BILL SUMMARY ===");
                            System.out.println("Current Total: LKR " + String.format("%.2f", total.asBigDecimal().doubleValue()));

                            // Show discount information
                            String discountInfo = pos.getCurrentDiscountInfo();
                            if (!"No discount applied".equals(discountInfo)) {
                                System.out.println("💰 " + discountInfo);
                            }

                            // Show available discounts
                            var availableDiscounts = pos.getAvailableDiscounts();
                            if (!availableDiscounts.isEmpty() && !availableDiscounts.get(0).equals("No batch discounts available")) {
                                System.out.println("\n📋 Available Discounts:");
                                for (String discount : availableDiscounts) {
                                    System.out.println("   • " + discount);
                                }
                            }
                            System.out.println("====================");
                        } catch (Exception e) {
                            System.out.println("Error calculating total: " + e.getMessage());
                        }
                    }
                    case "4" -> {
                        if ("ONLINE".equalsIgnoreCase(channel)) {
                            Money due = pos.total();
                            System.out.println("Amount due: " + due);
                            System.out.print("Card last 4 digits: ");
                            String last4 = readLine(sc);
                            pos.checkoutCard(last4);
                            System.out.println(" Online checkout complete (Card ****" + last4 + ").");
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
            System.out.println(" Error: " + ex.getMessage());
        }
    }

    private boolean tryAddWithMainTopUpInteractive(Scanner sc, String channel, String code, int qty) {
        try {
            pos.addItem(code, qty);
            System.out.println(" Item(s) added.");
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
                    System.out.println(" Transferred " + toMove + " MAIN → STORE.");
                } catch (Exception mvEx) {
                    System.out.println(" Transfer failed: " + mvEx.getMessage());
                    return false;
                }

                // retry add
                try {
                    pos.addItem(code, qty);
                    System.out.println("Item(s) added after transfer.");
                    return true;
                } catch (Exception ex2) {
                    System.out.println(" Still insufficient after transfer: " + ex2.getMessage());
                    return false;
                }
            }
            throw ex;
        }
    }

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

    // Helper method to validate if item code exists in catalog
    private boolean isValidItemCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        try {
            // Try to get item details - if it throws exception, item doesn't exist
            inv.itemName(code);
            inv.priceOf(code);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


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


    private void checkoutMenu(Scanner sc) {
        try {
            Money due = pos.total();
            System.out.println("Amount due: " + due + " LKR");
            System.out.println("1. Pay by Cash");
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
            System.out.println("6. Item Catalog (CRUD)");
            System.out.println("7. Move MAIN -> SHELF (FEFO)");
            System.out.println("8. Move STORE -> SHELF (FEFO)");
            System.out.println("9. Batch Discount Management"); // NEW
            System.out.println("0. Back");
            System.out.print("Choose: ");
            String ch = readLine(sc);
            try {
                switch (ch) {
                    case "1" -> {
                        // NEW: Show all items first so manager can see available item codes
                        System.out.println("\n=== Available Items ===");
                        var allItems = inv.listAllItems();
                        if (allItems.isEmpty()) {
                            System.out.println("No items found in catalog. Please add items first.");
                            break;
                        }

                        System.out.printf("%-14s %-30s %-15s%n", "ITEM CODE", "NAME", "UNIT PRICE");
                        System.out.println("-".repeat(65));
                        for (Item item : allItems) {
                            System.out.printf("%-14s %-30s %-15s%n",
                                    item.code(),
                                    item.name().length() > 30 ? item.name().substring(0, 27) + "..." : item.name(),
                                    item.unitPrice().toString());
                        }
                        System.out.println("-".repeat(65));
                        System.out.println("Total items: " + allItems.size());

                        System.out.print("\nItem code: ");
                        String code = readLine(sc);

                        // Validate the entered item code exists
                        boolean itemExists = false;
                        for (Item item : allItems) {
                            if (item.code().equals(code)) {
                                itemExists = true;
                                break;
                            }
                        }

                        if (!itemExists) {
                            System.out.println(" Invalid item code. Please choose from the list above.");
                            break;
                        }

                        System.out.print("Expiry (yyyy-MM-dd or blank): ");
                        String s = readLine(sc);
                        java.time.LocalDate exp = (s.isBlank() ? null : java.time.LocalDate.parse(s));
                        System.out.print("Qty on SHELF: ");
                        int qs = readInt(sc);
                        System.out.print("Qty in STORE: ");
                        int qst = readInt(sc);

                        admin.addBatch(code, exp, qs, qst);
                        System.out.println(" Batch added for item: " + code);

                        // NEW: Prompt for discount when adding batch
                        if (exp != null && exp.isBefore(java.time.LocalDate.now().plusDays(30))) {
                            System.out.println("This batch expires within 30 days.");
                            System.out.print("Add discount for close expiry? (y/n): ");
                            String addDiscount = readLine(sc).toLowerCase();
                            if ("y".equals(addDiscount) || "yes".equals(addDiscount)) {
                                promptAddBatchDiscount(sc, code, "Close to expiry - expires " + exp);
                            }
                        }
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
                            System.out.println(" No stock in MAIN to move.");
                            break;
                        }
                        System.out.print("Qty to move MAIN → STORE (max " + mainBefore + "): ");
                        int q = readInt(sc);
                        if (q <= 0 || q > mainBefore) { System.out.println(" Invalid quantity."); break; }

                        admin.moveMainToStoreFEFO(code, q);

                        int shelfAfter = inv.shelfQty(code);
                        int storeAfter = inv.storeQty(code);
                        int mainAfter  = inv.mainStoreQty(code);
                        System.out.println("After:");
                        System.out.println("  SHELF=" + shelfAfter + "  STORE=" + storeAfter + "  MAIN=" + mainAfter);
                        System.out.println("Moved " + q + " MAIN → STORE (FEFO).");
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
                            System.out.println("No stock in MAIN to move.");
                            break;
                        }
                        System.out.print("Qty to move MAIN → SHELF (max " + mainBefore + "): ");
                        int q = readInt(sc);
                        if (q <= 0 || q > mainBefore) { System.out.println("Invalid quantity."); break; }

                        admin.moveMainToShelfFEFO(code, q);

                        int shelfAfter = inv.shelfQty(code);
                        int storeAfter = inv.storeQty(code);
                        int mainAfter  = inv.mainStoreQty(code);
                        System.out.println("After:");
                        System.out.println("  SHELF=" + shelfAfter + "  STORE=" + storeAfter + "  MAIN=" + mainAfter);
                        System.out.println("Moved " + q + " MAIN → SHELF (FEFO).");
                    }
                    case "8" -> {
                        System.out.print("Item code: "); String code = readLine(sc);
                        System.out.print("Qty to move STORE → SHELF: "); int q = readInt(sc);
                        admin.moveStoreToShelfFEFO(code, q);
                        System.out.println("Moved " + q + " STORE → SHELF (FEFO).");
                    }
                    case "9" -> batchDiscountMenu(sc); // NEW
                    case "0" -> loop = false;
                    default -> System.out.println("Invalid choice.");
                }
            } catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
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
                        System.out.println("Item added to catalog.");
                    }
                    case "2" -> {
                        System.out.print("Item code to update: "); String code = readLine(sc);
                        System.out.print("New name (leave blank to keep current): "); String name = readLine(sc);
                        System.out.print("New unit price (0 to keep current): "); double price = readDouble(sc);

                        if (!name.isBlank()) admin.renameItem(code, name);
                        if (price > 0) admin.setItemPrice(code, domain.common.Money.of(price));
                        System.out.println("Item updated.");
                    }
                    case "3" -> {
                        System.out.print("Item code to delete: "); String code = readLine(sc);
                        admin.deleteItem(code);
                        System.out.println("Item deleted.");
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
                System.out.println("Error: " + ex.getMessage());
            }
        }
    }

    private void batchDiscountMenu(Scanner sc) {
        boolean loop = true;
        while (loop) {
            System.out.println("\n--- Batch Discount Management ---");
            System.out.println("1. Add Discount to Batch");
            System.out.println("2. View All Active Discounts");
            System.out.println("3. Remove Discount");
            System.out.println("0. Back");
            System.out.print("Choose: ");
            String ch = readLine(sc);
            try {
                switch (ch) {
                    case "1" -> addDiscountToBatchMenu(sc);
                    case "2" -> viewAllActiveDiscounts(sc);
                    case "3" -> removeDiscountMenu(sc);
                    case "0" -> loop = false;
                    default -> System.out.println("Invalid choice.");
                }
            } catch (Exception ex) {
                System.out.println("️ Error: " + ex.getMessage());
            }
        }
    }

    private void addDiscountToBatchMenu(Scanner sc) {
        try {
            System.out.print("Batch ID: ");
            long batchId = Long.parseLong(readLine(sc));

            System.out.println("Discount Type:");
            System.out.println("1. Percentage (e.g., 10%)");
            System.out.println("2. Fixed Amount (e.g., LKR 50)");
            System.out.print("Choose: ");
            String typeChoice = readLine(sc);

            domain.inventory.BatchDiscount.DiscountType type;
            Money value;

            if ("1".equals(typeChoice)) {
                type = domain.inventory.BatchDiscount.DiscountType.PERCENTAGE;
                System.out.print("Percentage (0-100): ");
                double percent = readDouble(sc);
                if (percent <= 0 || percent > 100) {
                    System.out.println(" Percentage must be between 0 and 100");
                    return;
                }
                value = Money.of(percent);
            } else if ("2".equals(typeChoice)) {
                type = domain.inventory.BatchDiscount.DiscountType.FIXED_AMOUNT;
                System.out.print("Fixed amount (LKR): ");
                double amount = readDouble(sc);
                if (amount <= 0) {
                    System.out.println(" Amount must be positive");
                    return;
                }
                value = Money.of(amount);
            } else {
                System.out.println(" Invalid choice");
                return;
            }

            System.out.print("Reason (e.g., 'Close to expiry', 'Overstock'): ");
            String reason = readLine(sc);
            if (reason.isBlank()) reason = "Manager discount";

            admin.addBatchDiscount(batchId, type, value, reason, auth.currentUser().username());
            System.out.println(" Discount added to batch " + batchId);

        } catch (NumberFormatException e) {
            System.out.println(" Invalid number format");
        } catch (Exception e) {
            System.out.println(" Failed to add discount: " + e.getMessage());
        }
    }

    private void viewAllActiveDiscounts(Scanner sc) {
        try {
            var discounts = admin.getAllBatchDiscountsWithDetails();
            if (discounts.isEmpty()) {
                System.out.println("No active batch discounts found.");
                return;
            }

            System.out.println("\n=== Active Batch Discounts ===");
            System.out.printf("%-12s %-10s %-12s %-25s %-12s %-15s %-20s %-15s%n",
                    "DISCOUNT_ID", "BATCH_ID", "ITEM_CODE", "ITEM_NAME", "EXPIRY", "TYPE", "VALUE", "REASON");
            System.out.println("=".repeat(140));

            for (var discount : discounts) {
                String expiryStr = discount.expiry() != null ? discount.expiry().toString() : "No expiry";
                String typeStr = discount.discountType() == domain.inventory.BatchDiscount.DiscountType.PERCENTAGE ?
                        "PERCENTAGE" : "FIXED_AMT";
                String valueStr = discount.discountType() == domain.inventory.BatchDiscount.DiscountType.PERCENTAGE ?
                        String.format("%.1f%%", discount.discountValue().asBigDecimal().doubleValue()) :
                        String.format("LKR %.2f", discount.discountValue().asBigDecimal().doubleValue());

                System.out.printf("%-12d %-10d %-12s %-25s %-12s %-15s %-20s %-15s%n",
                        discount.discountId(), discount.batchId(), discount.itemCode(),
                        discount.itemName(), expiryStr, typeStr, valueStr,
                        discount.reason() != null ? discount.reason() : "N/A");
            }

            System.out.println("\nTotal active discounts: " + discounts.size());

        } catch (Exception e) {
            System.out.println(" Failed to retrieve discounts: " + e.getMessage());
        }
    }

    private void removeDiscountMenu(Scanner sc) {
        try {
            // First show current discounts so manager can see what to remove
            viewAllActiveDiscounts(sc);

            System.out.print("\nEnter Discount ID to remove: ");
            long discountId = Long.parseLong(readLine(sc));

            System.out.print("Are you sure you want to remove discount " + discountId + "? (y/n): ");
            String confirm = readLine(sc).toLowerCase();

            if ("y".equals(confirm) || "yes".equals(confirm)) {
                admin.removeBatchDiscount(discountId);
                System.out.println(" Discount " + discountId + " removed successfully");
            } else {
                System.out.println("Discount removal cancelled");
            }

        } catch (NumberFormatException e) {
            System.out.println("Invalid discount ID format");
        } catch (Exception e) {
            System.out.println(" Failed to remove discount: " + e.getMessage());
        }
    }

    private void promptAddBatchDiscount(Scanner sc, String itemCode, String reason) {
        try {
            System.out.print("Batch ID for this item: ");
            long batchId = Long.parseLong(readLine(sc));

            System.out.print("Discount percentage (0-100): ");
            double percent = readDouble(sc);
            if (percent <= 0 || percent > 100) {
                System.out.println("Percentage must be between 0 and 100");
                return;
            }

            String finalReason = reason != null ? reason : "Close to expiry discount";

            admin.addBatchDiscount(batchId, domain.inventory.BatchDiscount.DiscountType.PERCENTAGE,
                    Money.of(percent), finalReason, auth.currentUser().username());
            System.out.println("Done " + percent + "% discount added to batch " + batchId);

        } catch (NumberFormatException e) {
            System.out.println(" Invalid number format");
        } catch (Exception e) {
            System.out.println(" Failed to add discount: " + e.getMessage());
        }
    }


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

