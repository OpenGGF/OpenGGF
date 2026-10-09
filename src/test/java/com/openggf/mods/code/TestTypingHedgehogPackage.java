package com.openggf.mods.code;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Typing of the Hedgehog packages through {@code ggfmod package} (the mod validator) and registers
 * as one multi-game jar: a startup scene, one patch per stock game, the puppet object and the
 * course bundle shared by every game.
 */
class TestTypingHedgehogPackage {
    @TempDir static Path temp;
    static ExampleModHarness harness;

    @BeforeAll static void build() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/typing-of-the-hedgehog"), temp);
    }

    @AfterAll static void close() throws Exception { if (harness != null) harness.close(); }

    @Test void registersOneMultiGameJarWithAPatchPerGame() {
        var plan = harness.plan();
        assertEquals("any", plan.baseGameId());
        assertNotNull(plan.startupScene());
        assertTrue(plan.objectFactories().keySet().stream().anyMatch(key -> key.endsWith(":puppet")));
        assertTrue(plan.serviceBundles().containsKey("course"));
        var perGame = plan.stockScenePlans();
        assertEquals(List.of("s1", "s2", "s3k"), perGame.stream().map(ModRegistrationPlan::baseGameId).toList());
        for (var stock : perGame) {
            assertEquals(1, stock.explicitPatches().size(), stock.baseGameId());
            assertEquals(stock.baseGameId(), stock.explicitPatches().getFirst().baseGameId());
        }
        assertTrue(harness.findings().isEmpty(), harness.findings().toString());
    }
}
