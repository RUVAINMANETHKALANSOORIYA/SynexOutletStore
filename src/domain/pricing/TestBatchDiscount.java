import domain.inventory.BatchDiscount;
import domain.common.Money;
import java.time.LocalDateTime;

public class TestBatchDiscount {
    public static void main(String[] args) {
        // Test batch discount calculation
        BatchDiscount discount = new BatchDiscount(
            1L,                              // id
            100L,                            // batchId
            BatchDiscount.DiscountType.PERCENTAGE,  // type
            Money.of(20.0),                  // 20% discount
            "Manager discount",              // reason
            LocalDateTime.now().minusHours(1), // validFrom
            LocalDateTime.now().plusDays(30),  // validUntil
            "manager",                       // createdBy
            LocalDateTime.now(),             // createdAt
            true                             // isActive
        );
        
        Money originalPrice = Money.of(100.0);
        Money discountedPrice = discount.calculateDiscountedPrice(originalPrice);
        
        System.out.println("Original price: " + originalPrice);
        System.out.println("Discount: " + discount.getDescription());
        System.out.println("Discounted price: " + discountedPrice);
        System.out.println("Discount is valid: " + discount.isValidNow());
    }
}
