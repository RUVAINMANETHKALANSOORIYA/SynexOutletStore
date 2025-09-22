package bootstrap;

import application.inventory.FefoBatchSelector;
import application.inventory.InventoryAdminService;
import application.inventory.InventoryService;
import application.inventory.RestockService;
import application.reporting.ReportPrinter;
import application.reporting.ReportRepository;
import application.reporting.ReportingService;
import application.auth.AuthService;
import application.auth.CustomerAuthService;
import application.pos.POSController;
import application.pricing.PricingService;
import application.events.EventBus;
import application.events.SimpleEventBus;
import application.events.events.BillPaid;
import application.events.events.RestockThresholdHit;
import application.events.events.StockDepleted;
import domain.billing.BillNumberGenerator;
import infrastructure.console.ConsoleReportPrinter;
import infrastructure.files.TxtBillWriter;
import infrastructure.jdbc.JdbcBillNumberGenerator;
import infrastructure.jdbc.JdbcInventoryRepository;
import infrastructure.jdbc.JdbcReportRepository;
import infrastructure.jdbc.JdbcCustomerRepository;
import infrastructure.jdbc.JdbcUserRepository;
import infrastructure.security.PermissionCheckedInventoryRepository; // <-- Proxy
import infrastructure.jdbc.JdbcBillRepository;
import ports.out.BillRepository;
import ports.out.CustomerRepository;
import ports.out.UserRepository;
import ports.out.InventoryRepository;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {

        UserRepository userRepo = new JdbcUserRepository();
        AuthService auth = new AuthService(userRepo);

        InventoryRepository rawInvRepo = new JdbcInventoryRepository();
        InventoryRepository invRepo = new PermissionCheckedInventoryRepository(rawInvRepo, auth);

        var selector  = new FefoBatchSelector();
        var inventory = new InventoryService(invRepo, selector);

        var pricing   = new PricingService(0.0, inventory); // tax % configurable, now with inventory service
        BillRepository billRepo      = new JdbcBillRepository();
        BillNumberGenerator billNos  = new JdbcBillNumberGenerator();
        var writer    = new TxtBillWriter(Path.of("bills"));

        EventBus bus = new SimpleEventBus();

        bus.subscribe(BillPaid.class, e ->
                System.out.println("[EVENT] BillPaid " + e.billNo() + " total=" + e.total() + " by " + e.user() + " via " + e.channel()));
        bus.subscribe(RestockThresholdHit.class, e ->
                System.out.println("[EVENT] RestockThresholdHit item=" + e.itemCode() + " left=" + e.totalQtyLeft() + " threshold=" + e.threshold()));
        bus.subscribe(StockDepleted.class, e ->
                System.out.println("[EVENT] StockDepleted item=" + e.itemCode()));

        var restock   = new RestockService(invRepo);
        var admin     = new InventoryAdminService(invRepo);

        // Update POSController to include InventoryAdminService for batch discount functionality
        var pos       = new POSController(inventory, admin, pricing, billNos, billRepo, writer, bus);

        ReportRepository reportRepo = new JdbcReportRepository();
        ReportPrinter printer       = new ConsoleReportPrinter();
        var reports   = new ReportingService(reportRepo, printer);

        CustomerRepository custRepo = new JdbcCustomerRepository();
        var customerAuth            = new CustomerAuthService(custRepo);

        var console = new presentation.cli.POSConsole(
                pos,
                reports,
                restock,
                auth,
                customerAuth,
                admin,
                inventory
        );

        java.util.Scanner sc = new java.util.Scanner(System.in);
        System.out.println("How would you like to shop?");
        System.out.println("1) In-Store (POS)");
        System.out.println("2) Online");
        System.out.print("Choose: ");
        String pick = sc.nextLine().trim();

        if ("2".equals(pick)) {
            console.runOnlineApp();
        } else {
            console.run();
        }
    }
}
