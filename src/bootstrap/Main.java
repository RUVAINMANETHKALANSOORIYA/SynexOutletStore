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

import domain.billing.BillNumberGenerator;

import infrastructure.console.ConsoleReportPrinter;
import infrastructure.files.TxtBillWriter;
import infrastructure.jdbc.JdbcBillNumberGenerator;
import infrastructure.jdbc.JdbcInventoryRepository;
import infrastructure.jdbc.JdbcReportRepository;       // ✅ here
import infrastructure.jdbc.JdbcCustomerRepository;     // ✅ here

import persistence.jdbc.JdbcBillRepository;            // still here
import persistence.jdbc.JdbcUserRepository;            // still here

import ports.out.BillRepository;
import ports.out.CustomerRepository;
import ports.out.UserRepository;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        // ----- Infra / Inventory -----
        var invRepo   = new JdbcInventoryRepository();
        var selector  = new FefoBatchSelector();
        var inventory = new InventoryService(invRepo, selector);

        // ----- Pricing & Billing -----
        var pricing   = new PricingService(0.0);
        BillRepository billRepo      = new JdbcBillRepository();
        BillNumberGenerator billNos  = new JdbcBillNumberGenerator();
        var writer    = new TxtBillWriter(Path.of("bills"));
        var pos       = new POSController(inventory, pricing, billNos, billRepo, writer);

        // ----- Reporting -----
        ReportRepository reportRepo = new JdbcReportRepository();
        ReportPrinter printer       = new ConsoleReportPrinter();
        var reports   = new ReportingService(reportRepo, printer);

        // ----- Restock / Admin -----
        var restock   = new RestockService(invRepo);
        var admin     = new InventoryAdminService(invRepo);

        // ----- Auth (cashier) -----
        UserRepository userRepo   = new JdbcUserRepository();
        var auth                  = new AuthService(userRepo);

        // ----- Auth (customer) -----
        CustomerRepository custRepo = new JdbcCustomerRepository();
        var customerAuth            = new CustomerAuthService(custRepo);

        // ----- CLI -----
        new presentation.cli.POSConsole(
                pos,
                reports,
                restock,
                auth,
                customerAuth,
                admin,
                inventory
        ).run();
    }
}
