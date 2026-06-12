package com.openggf.level.objects;

import com.openggf.game.PowerUpObject;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestShieldRewindPendingRestore {

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void pendingShieldRespawnMatchesOwnerInsteadOfFifoForSameConcreteType() {
        ObjectManager objectManager = new ObjectManager(List.of(), null, 0, null, null);
        DefaultPowerUpSpawner spawner = new DefaultPowerUpSpawner(objectManager);
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 100, (short) 200);
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 140, (short) 200);

        objectManager.enqueuePendingPlayerBoundEntry(
                ShieldObjectInstance.class, shieldEntryFor(objectManager, tails, 50));
        objectManager.enqueuePendingPlayerBoundEntry(
                ShieldObjectInstance.class, shieldEntryFor(objectManager, sonic, 60));

        PowerUpObject sonicShield = spawner.spawnShield(sonic, ShieldType.BASIC);
        ShieldObjectInstance sonicShieldObject = assertInstanceOf(ShieldObjectInstance.class, sonicShield);
        assertSame(sonic, sonicShieldObject.getPlayer(),
                "respawned shield must stay bound to the requesting player");
        assertEquals(60, sonicShieldObject.getSlotIndex(),
                "sonic must consume sonic's pending shield entry, not the FIFO head");

        PowerUpObject tailsShield = spawner.spawnShield(tails, ShieldType.BASIC);
        ShieldObjectInstance tailsShieldObject = assertInstanceOf(ShieldObjectInstance.class, tailsShield);
        assertSame(tails, tailsShieldObject.getPlayer());
        assertEquals(50, tailsShieldObject.getSlotIndex(),
                "tails' entry should remain pending until tails refreshes");
    }

    private static ObjectManagerSnapshot.DynamicObjectEntry shieldEntryFor(
            ObjectManager objectManager, TestablePlayableSprite player, int slotIndex) {
        ShieldObjectInstance source = ObjectConstructionContext.construct(
                objectManager.services(), () -> new ShieldObjectInstance(player));
        source.setSlotIndex(slotIndex);
        return new ObjectManagerSnapshot.DynamicObjectEntry(
                ShieldObjectInstance.class.getName(),
                null,
                slotIndex,
                source.captureRewindState(),
                player);
    }
}
