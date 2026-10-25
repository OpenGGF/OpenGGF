package openggf.racing.server.master;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestPasswordSourceBudget {
    private long now;

    @Test
    void twentyAttemptsShareARollingWindowAndTheNextIsDenied() {
        PasswordSourceBudget budget = new PasswordSourceBudget(() -> now);
        for (int i = 0; i < 20; i++) {
            assertTrue(budget.admit("source"));
            now += 1_000;
        }
        now += 39_999;
        assertFalse(budget.admit("source"));
        now++;
        assertTrue(budget.admit("source"));
        assertFalse(budget.admit("source"));
    }

    @Test
    void addressesHaveIndependentLimits() {
        PasswordSourceBudget budget = new PasswordSourceBudget(() -> now);
        for (int i = 0; i < 20; i++) assertTrue(budget.admit("a"));
        assertFalse(budget.admit("a"));
        assertTrue(budget.admit("b"));
    }

    @Test
    void saturatedCacheCannotEvictALiveBucket() {
        PasswordSourceBudget budget = new PasswordSourceBudget(() -> now, 1);
        for (int i = 0; i < 20; i++) assertTrue(budget.admit("a"));
        assertFalse(budget.admit("b"));
        assertFalse(budget.admit("a"));
        now += 60_000;
        assertTrue(budget.admit("b"));
    }
}
