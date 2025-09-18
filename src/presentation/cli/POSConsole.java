package presentation.cli;

import application.auth.AuthService;
import application.auth.CustomerAuthService;
import application.inventory.InventoryAdminService;
import application.inventory.InventoryService;
import application.inventory.RestockService;
import application.pos.POSController;
import application.reporting.ReportingService;
import domain.common.Money;
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
    private final InventoryService inv; // used for qty checks & restock level

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

    public void run() {
        Scanner sc = new Scanner(System.in);
        sc.useLocale(Locale.US);

        System.out.println("========================================");
        System.out.println("   Synex Outlet Store - POS System");
        System.out.println("========================================");

        // -------- Login loop (cashier / staff) --------
        while (!auth.isLoggedIn()) {
            System.out.print("Username: ");
            String user = sc.nextLine().trim();
            System.out.print("Password: ");
            String pass = sc.nextLine().trim();
            if (auth.login(user, pass)) {
                pos.setUser(user);
                pos.setChannel("POS"); // default channel on login
                System.out.println("✅ Login successful! Welcome, " + user + ".");
            } else {
                System.out.println("❌ Login failed. Please try again.\n");
            }
        }

        boolean running = true;
        while (running) {
            final String role = auth.currentUser().role(); // re-read each loop

            System.out.println("\nMain Menu");
            System.out.println("---------");
            System.out.println("1. New POS Bill");
            System.out.println("2. New ONLINE Order");
            System.out.println("3. View Reports");
            System.out.println("4. Restock");
            if ("INVENTORY_MANAGER".equalsIgnoreCase(role)) {
                System.out.println("5. Inventory Manager");
            }
            System.out.println("0. Logout / Exit");
            System.out.print("Choose an option: ");
            String choice = readLine(sc);

            switch (choice) {
                case "1" -> billMenu(sc, "POS");
                case "2" -> billMenu(sc, "ONLINE");
                case "3" -> reportMenu(sc);
                case "4" -> restockMenu(sc);
                case "5" -> {
                    if ("INVENTORY_MANAGER".equalsIgnoreCase(role)) managerMenu(sc);
                    else System.out.println("Invalid choice.");
                }
                case "0" -> {
                    auth.logout();
                    System.out.println("👋 Logged out. Goodbye!");
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

            // ONLINE flow requires customer login/registration and card payment
            if ("ONLINE".equalsIgnoreCase(channel)) {
                System.out.println("=== Customer Portal (ONLINE) ===");
                boolean loggedIn = false;
                while (!loggedIn) {
                    System.out.println("1. Login");
                    System.out.println("2. Register");
                    System.out.println("0. Cancel");
                    System.out.print("Choose: ");
                    String ch = readLine(sc);
                    switch (ch) {
                        case "1" -> {
                            System.out.print("Username: ");
                            String u = readLine(sc);
                            System.out.print("Password: ");
                            String p = readLine(sc);
                            if (customerAuth.login(u, p)) {
                                System.out.println("✅ Customer login successful. Welcome " + u + "!");
                                loggedIn = true;
                            } else {
                                System.out.println("❌ Login failed.");
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
                                System.out.println("✅ Registration successful. You can now log in.");
                            } else {
                                System.out.println("❌ Username already taken.");
                            }
                        }
                        case "0" -> { return; }
                        default -> System.out.println("Invalid.");
                    }
                }
            }

            pos.newBill();
            boolean active = true;

            while (active) {
                System.out.println("\n--- New " + channel.toUpperCase(Locale.ROOT) + " Bill ---");
                System.out.println("1. Add Item");
                System.out.println("2. Remove Item");
                System.out.println("3. Apply Discount");
                System.out.println("4. Show Total");
                System.out.println("5. Checkout");
                System.out.println("0. Back to Main Menu");
                System.out.print("Choose an option: ");
                String choice = readLine(sc);

                switch (choice) {
                    case "1" -> {
                        System.out.print("Enter Item Code: ");
                        String code = readLine(sc);
                        System.out.print("Enter Quantity: ");
                        int qty = readInt(sc);

                        // Try to add item for this channel
                        pos.addItem(code, qty);
                        System.out.println("✅ Item(s) added.");

                        // Restock level notice
                        try {
                            int restock = inv.restockLevel(code);
                            int remaining = inv.shelfQty(code) + inv.storeQty(code);
                            if (remaining <= restock) {
                                System.out.println("🔁 This item hit or fell below the restock level (" + restock + ").");
                            }
                        } catch (Exception ignored) { }

                        // 🔄 Auto top-up from MAIN when primary area is now empty
                        try {
                            if ("POS".equalsIgnoreCase(channel)) {
                                int storeLeft = inv.storeQty(code);
                                if (storeLeft <= 0) {
                                    // store depleted → pull 100 from MAIN into STORE
                                    admin.addBatch(code, null, 0, 100);
                                    System.out.println("🔄 Store empty — auto top-up 100 from MAIN → STORE.");
                                }
                            } else {
                                int shelfLeft = inv.shelfQty(code);
                                if (shelfLeft <= 0) {
                                    // shelf depleted → pull 100 from MAIN onto SHELF
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
                            // Force card payment online
                            Money due = pos.total();
                            System.out.println("Amount due: " + due);
                            System.out.print("Card last 4 digits: ");
                            String last4 = readLine(sc);
                            pos.checkoutCard(last4);
                            System.out.println("✅ Online checkout complete (Card ****" + last4 + ").");
                        } else {
                            checkoutMenu(sc); // POS: cash or card
                        }
                        active = false; // done with this bill
                    }
                    case "0" -> active = false;
                    default -> System.out.println("Invalid choice.");
                }
            }
        } catch (Exception ex) {
            System.out.println("⚠️ Error: " + ex.getMessage());
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
            System.out.println("✅ Discount applied.");
        } catch (Exception ex) {
            System.out.println("⚠️ Error: " + ex.getMessage());
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
                    System.out.println("✅ Checkout complete (Cash).");
                }
                case "2" -> {
                    System.out.print("Card last 4 digits: ");
                    String last4 = readLine(sc);
                    pos.checkoutCard(last4);
                    System.out.println("✅ Checkout complete (Card ****" + last4 + ").");
                }
                case "0" -> System.out.println("Cancelled.");
                default -> System.out.println("Invalid choice.");
            }
        } catch (Exception ex) {
            System.out.println("⚠️ Error: " + ex.getMessage());
        }
    }

    // ================== REPORT MENU (expanded) ==================
    private void reportMenu(Scanner sc) {
        System.out.println("\n--- Reports ---");
        System.out.println("1. Daily Sales");
        System.out.println("2. Best Sellers");
        System.out.println("3. Revenue Series");
        System.out.println("4. Reshelving (toward shelf target)");
        System.out.println("5. Reorder (below threshold)");
        System.out.println("6. Stock by Batch");
        System.out.println("7. Bills (range)");
        System.out.println("0. Back");
        System.out.print("Choose: ");
        String choice = readLine(sc);

        try {
            switch (choice) {
                case "1" -> {
                    System.out.print("Enter date (yyyy-MM-dd): ");
                    reports.printDailySales(LocalDate.parse(readLine(sc)));
                    System.out.println("✅ Printed to console.");
                }
                case "2" -> {
                    System.out.print("From date (yyyy-MM-dd): ");
                    LocalDate from = LocalDate.parse(readLine(sc));
                    System.out.print("To date (yyyy-MM-dd): ");
                    LocalDate to = LocalDate.parse(readLine(sc));
                    reports.printBestSellers(from, to, 10);
                    System.out.println("✅ Printed to console.");
                }
                case "3" -> {
                    System.out.print("From date (yyyy-MM-dd): ");
                    LocalDate from = LocalDate.parse(readLine(sc));
                    System.out.print("To date (yyyy-MM-dd): ");
                    LocalDate to = LocalDate.parse(readLine(sc));
                    reports.printRevenueSeries(from, to);
                    System.out.println("✅ Printed to console.");
                }
                case "4" -> {
                    System.out.print("Shelf target (e.g., 100): ");
                    int target = readInt(sc);
                    reports.printReshelving(target);
                    System.out.println("✅ Printed to console.");
                }
                case "5" -> {
                    System.out.print("Reorder threshold (e.g., 50): ");
                    int th = readInt(sc);
                    reports.printReorder(th);
                    System.out.println("✅ Printed to console.");
                }
                case "6" -> {
                    System.out.print("Filter by item code (blank for all): ");
                    String code = readLine(sc);
                    if (code.isBlank()) code = null;
                    reports.printStockByBatch(code);
                    System.out.println("✅ Printed to console.");
                }
                case "7" -> {
                    System.out.print("From date (yyyy-MM-dd): ");
                    LocalDate from = LocalDate.parse(readLine(sc));
                    System.out.print("To date (yyyy-MM-dd): ");
                    LocalDate to = LocalDate.parse(readLine(sc));
                    reports.printBills(from, to);
                    System.out.println("✅ Printed to console.");
                }
                case "0" -> { /* back */ }
                default -> System.out.println("Invalid choice.");
            }
        } catch (Exception ex) {
            System.out.println("⚠️ Error: " + ex.getMessage());
        }
    }

    // ================== RESTOCK MENU ==================
    private void restockMenu(Scanner sc) {
        System.out.println("\n--- Restock ---");
        System.out.print("Enter item code: ");
        String code = readLine(sc);
        try {
            restock.restockToTarget(code, 100);
            System.out.println("✅ Shelf topped up to 100 for " + code);
        } catch (Exception e) {
            System.out.println("⚠️ Error: " + e.getMessage());
        }
    }

    // ================== MANAGER MENU ==================
    private void managerMenu(Scanner sc) {
        boolean loop = true;
        while (loop) {
            System.out.println("\n--- Inventory Manager ---");
            System.out.println("1. Add Batch");
            System.out.println("2. Edit Batch Quantities");
            System.out.println("3. Update Batch Expiry");
            System.out.println("4. Delete Batch");
            System.out.println("5. Move Store -> Shelf (FEFO)");
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
                        System.out.println("✅ Batch added.");
                    }
                    case "2" -> {
                        System.out.print("Batch ID: "); long id = Long.parseLong(readLine(sc));
                        System.out.print("New Qty on SHELF: "); int qs = readInt(sc);
                        System.out.print("New Qty in STORE: "); int qst = readInt(sc);
                        admin.editBatchQuantities(id, qs, qst);
                        System.out.println("✅ Batch quantities updated.");
                    }
                    case "3" -> {
                        System.out.print("Batch ID: "); long id = Long.parseLong(readLine(sc));
                        System.out.print("New expiry (yyyy-MM-dd or blank to clear): "); String s = readLine(sc);
                        java.time.LocalDate exp = (s.isBlank() ? null : java.time.LocalDate.parse(s));
                        admin.updateBatchExpiry(id, exp);
                        System.out.println("✅ Batch expiry updated.");
                    }
                    case "4" -> {
                        System.out.print("Batch ID: "); long id = Long.parseLong(readLine(sc));
                        admin.deleteBatch(id);
                        System.out.println("✅ Batch deleted.");
                    }
                    case "5" -> {
                        System.out.print("Item code: "); String code = readLine(sc);
                        System.out.print("Qty to move from STORE to SHELF: "); int q = readInt(sc);
                        admin.moveStoreToShelfFEFO(code, q);
                        System.out.println("✅ Moved " + q + " (STORE -> SHELF) FEFO.");
                    }
                    case "0" -> loop = false;
                    default -> System.out.println("Invalid choice.");
                }
            } catch (Exception ex) {
                System.out.println("⚠️ Error: " + ex.getMessage());
            }
        }
    }

    // -------- Helpers for safe input --------
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
