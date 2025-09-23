package application.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleThresholdReorderPolicyTest {

    @Test
    @DisplayName("SimpleThresholdReorderPolicy needs restock when below threshold")
    void needs_restock_below_threshold() {
        SimpleThresholdReorderPolicy policy = new SimpleThresholdReorderPolicy();

        assertTrue(policy.needsRestock(5, 10)); // 5 < 10
        assertTrue(policy.needsRestock(0, 5));  // 0 < 5
    }

    @Test
    @DisplayName("SimpleThresholdReorderPolicy needs restock when at threshold")
    void needs_restock_at_threshold() {
        SimpleThresholdReorderPolicy policy = new SimpleThresholdReorderPolicy();

        assertTrue(policy.needsRestock(10, 10)); // 10 = 10 should trigger restock
    }

    @Test
    @DisplayName("SimpleThresholdReorderPolicy doesn't need restock when above threshold")
    void no_restock_above_threshold() {
        SimpleThresholdReorderPolicy policy = new SimpleThresholdReorderPolicy();

        assertFalse(policy.needsRestock(15, 10)); // 15 > 10
        assertFalse(policy.needsRestock(100, 50)); // 100 > 50
    }

    @Test
    @DisplayName("SimpleThresholdReorderPolicy quantity to move calculation")
    void quantity_to_move_calculation() {
        SimpleThresholdReorderPolicy policy = new SimpleThresholdReorderPolicy();

        assertEquals(5, policy.quantityToMove(10, 5)); // min(10, 5) = 5
        assertEquals(8, policy.quantityToMove(8, 15));  // min(8, 15) = 8
        assertEquals(0, policy.quantityToMove(0, 10));  // min(0, 10) = 0
    }

    @Test
    @DisplayName("SimpleThresholdReorderPolicy with zero threshold")
    void zero_threshold_handling() {
        SimpleThresholdReorderPolicy policy = new SimpleThresholdReorderPolicy();

        assertFalse(policy.needsRestock(5, 0)); // Any quantity > 0 threshold
        assertTrue(policy.needsRestock(0, 0));  // 0 = 0 threshold
    }

    @Test
    @DisplayName("SimpleThresholdReorderPolicy with negative values")
    void negative_values_handling() {
        SimpleThresholdReorderPolicy policy = new SimpleThresholdReorderPolicy();

        assertFalse(policy.needsRestock(-5, 10)); // Negative current stock
        assertTrue(policy.needsRestock(5, -1));   // Negative threshold (unusual case)
    }

    @Test
    @DisplayName("SimpleThresholdReorderPolicy with large numbers")
    void large_numbers_handling() {
        SimpleThresholdReorderPolicy policy = new SimpleThresholdReorderPolicy();

        assertFalse(policy.needsRestock(Integer.MAX_VALUE, 1000));
        assertTrue(policy.needsRestock(500, Integer.MAX_VALUE));
        assertEquals(1000, policy.quantityToMove(Integer.MAX_VALUE, 1000));
    }

    @Test
    @DisplayName("SimpleThresholdReorderPolicy edge cases")
    void edge_cases() {
        SimpleThresholdReorderPolicy policy = new SimpleThresholdReorderPolicy();

        // Edge case: very close numbers
        assertTrue(policy.needsRestock(999, 1000));
        assertFalse(policy.needsRestock(1001, 1000));

        // Edge case: same numbers
        assertTrue(policy.needsRestock(50, 50));
        assertEquals(25, policy.quantityToMove(25, 25));
    }

    @Test
    @DisplayName("SimpleThresholdReorderPolicy consistency")
    void policy_consistency() {
        SimpleThresholdReorderPolicy policy1 = new SimpleThresholdReorderPolicy();
        SimpleThresholdReorderPolicy policy2 = new SimpleThresholdReorderPolicy();

        // Same policy instances should behave identically
        assertEquals(policy1.needsRestock(10, 15), policy2.needsRestock(10, 15));
        assertEquals(policy1.quantityToMove(8, 12), policy2.quantityToMove(8, 12));
    }

    @Test
    @DisplayName("SimpleThresholdReorderPolicy toString")
    void policy_to_string() {
        SimpleThresholdReorderPolicy policy = new SimpleThresholdReorderPolicy();
        String description = policy.toString();

        assertNotNull(description);
        assertTrue(description.contains("Threshold") || description.contains("Simple") || description.contains("Policy"));
    }
}
