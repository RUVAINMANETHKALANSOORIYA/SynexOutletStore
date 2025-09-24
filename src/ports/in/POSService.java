package ports.in;

import domain.common.Money;
import domain.billing.Receipt;
import java.util.List;

public interface POSService {
    String startNewBill();
    void addItemToBill(String billId, String itemCode, int quantity);
    void removeItemFromBill(String billId, String itemCode);
    Money calculateBillTotal(String billId);
    Receipt processPayment(String billId, Money amountPaid, String paymentMethod);
    void voidBill(String billId);
    List<String> getOpenBills();
}
