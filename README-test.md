SynexOutletStore — Test Suite Guide

Overview
This project includes a comprehensive JUnit 5 test suite targeting the core application, domain, ports, infrastructure, and presentation (CLI) layers. The tests aim for ~100% line/branch coverage with clean structure and SOLID-friendly design. Where appropriate, tests use doubles (hand-rolled or Mockito) and fluent assertions (AssertJ).

This document shows how to set up dependencies with Gradle or Maven, how to run the tests with coverage, and outlines what is covered. It also includes an H2 schema example for JDBC-style integration tests.

Structure
- Unit tests mirror production packages under src/test/java.
- Support utilities (builders, helpers) live under src/test/java/support.
  - support/builders: ItemBuilder, BatchBuilder, BillBuilder

Key Test Areas
- application.pos.POSController: end-to-end controller behaviors (new bill, add/remove items, smart reservations, payments, checkout, events, state reset). 
- application.inventory.InventoryService: FEFO selections, smart reservation branches, quantity helpers, and movement operations. 
- application.pricing.PricingService: subtotal, discounts (capped), taxes, rounding. 
- domain.billing.Bill & BillLine: state transitions (Draft→Paid) and rendering. 
- application.auth.AuthService & CustomerAuthService: login/registration flows. 
- application.events: SimpleEventBus publish/subscribe and Noop behavior. 
- infrastructure.security.PermissionCheckedInventoryRepository: guards for MAIN transfers.
- domain.spec.inventory utilities and specification combinators.

Recommended Dependencies
Choose either Gradle or Maven and add the following dependencies to enable Mockito, AssertJ, JUnit 5, and H2 for fast JDBC tests.

Gradle (Kotlin DSL)
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")

    testImplementation("org.assertj:assertj-core:3.25.3")

    testImplementation("com.h2database:h2:2.2.224")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

Maven (pom.xml)
<dependencies>
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.12.0</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <version>5.12.0</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>3.25.3</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.224</version>
    <scope>test</scope>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <version>3.2.5</version>
      <configuration>
        <useModulePath>false</useModulePath>
      </configuration>
    </plugin>
    <plugin>
      <groupId>org.jacoco</groupId>
      <artifactId>jacoco-maven-plugin</artifactId>
      <version>0.8.12</version>
      <executions>
        <execution>
          <goals>
            <goal>prepare-agent</goal>
          </goals>
        </execution>
        <execution>
          <id>report</id>
          <phase>verify</phase>
          <goals>
            <goal>report</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>

Running Tests + Coverage
- Gradle: ./gradlew clean test jacocoTestReport
  - Coverage HTML: build/reports/jacoco/test/html/index.html
- Maven: mvn clean verify
  - Coverage HTML: target/site/jacoco/index.html
- IntelliJ IDEA:
  - Ensure JUnit 5 is set as the test framework.
  - Right-click the test directory or class → Run 'Tests' with Coverage.

H2 Integration Testing (example schema)
-- Minimal tables for JDBC repositories
CREATE TABLE items (
  item_code VARCHAR(64) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  restock_level INT
);

CREATE TABLE batches (
  id BIGINT PRIMARY KEY,
  item_code VARCHAR(64) NOT NULL,
  expiry DATE,
  qty_on_shelf INT NOT NULL,
  qty_in_store INT NOT NULL,
  qty_in_main INT NOT NULL,
  CONSTRAINT fk_item FOREIGN KEY (item_code) REFERENCES items(item_code)
);

CREATE TABLE transfers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_code VARCHAR(64) NOT NULL,
  batch_id BIGINT,
  qty INT NOT NULL,
  transferred_by VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

Coverage Targets
- Unit tests: ≥95% per application/domain module.
- Include error paths (invalid inputs, insufficient stock, null guards).
- Include negative path for commitReservations/commitStoreReservations update precondition.

Notes
- If you enable Mockito and AssertJ, you can start introducing them incrementally without changing existing tests. New tests can use @ExtendWith(MockitoExtension.class) with mocks and AssertJ for fluent assertions.
- Builders in support/builders help create domain objects with minimal boilerplate.
