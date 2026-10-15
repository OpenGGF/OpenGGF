package com.openggf.game.rewind;

import com.openggf.game.AbstractBonusStageCoordinator;
import com.openggf.game.BonusStageType;
import com.openggf.game.ShieldType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestBonusStageCoordinatorRewindAdapter {

    private static final class FakeCoordinator extends AbstractBonusStageCoordinator {
        @Override public BonusStageType selectBonusStage(int ringCount) { return BonusStageType.NONE; }
        @Override public int getZoneId(BonusStageType type) { return 0; }
        @Override public int getMusicId(BonusStageType type) { return -1; }
    }

    @Test
    void nativeRuntimeRegistrationRestoresExitAlongsideRewardsAndRejectsAnotherVisit() {
        var coordinator=new FakeCoordinator();
        coordinator.onEnter(BonusStageType.GUMBALL,null);
        var registry=new RewindRegistry();
        com.openggf.game.internal.BonusStageRuntimeRewindAccess.register(registry,coordinator);
        registry.register(new BonusStageCoordinatorRewindAdapter(coordinator));
        var before=registry.capture();
        coordinator.requestExit();coordinator.addRings(7);
        var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);
            org.junit.jupiter.api.Assertions.assertFalse(coordinator.isStageComplete());
            assertEquals(0,coordinator.getRewards().rings());
            registry.restore(after);
            org.junit.jupiter.api.Assertions.assertTrue(coordinator.isStageComplete());
            assertEquals(7,coordinator.getRewards().rings());
            for(String key:after.entries().keySet())
                org.junit.jupiter.api.Assertions.assertTrue(RewindSnapshotDiff.keyEquals(key,after.get(key),registry.capture().get(key)),key);
        }
        coordinator.onExit();coordinator.onEnter(BonusStageType.GUMBALL,null);coordinator.addRings(32);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->registry.restore(before));
        assertEquals(32,coordinator.getRewards().rings(),"stale visit fails before reward mutation");
        com.openggf.game.internal.BonusStageRuntimeRewindAccess.unregister(registry);
        org.junit.jupiter.api.Assertions.assertFalse(registry.capture().containsKey(com.openggf.game.internal.BonusStageRuntimeRewindAccess.KEY));
    }

    @Test
    void keyIsStable() {
        FakeCoordinator coordinator = new FakeCoordinator();
        BonusStageCoordinatorRewindAdapter adapter = new BonusStageCoordinatorRewindAdapter(coordinator);
        assertEquals("bonus-stage-coordinator", adapter.key());
        assertEquals(BonusStageCoordinatorRewindAdapter.KEY, adapter.key());
    }

    @Test
    void roundTripsRewardAccumulators() {
        FakeCoordinator coordinator = new FakeCoordinator();
        coordinator.onEnter(BonusStageType.GUMBALL, null);
        BonusStageCoordinatorRewindAdapter adapter = new BonusStageCoordinatorRewindAdapter(coordinator);

        // Capture the empty starting state.
        var floor = adapter.capture();

        // Mutate as item objects would.
        coordinator.addRings(7);
        coordinator.addLife();
        coordinator.setAwardedShield(ShieldType.FIRE);
        assertEquals(7, coordinator.getRewards().rings());

        // Restore rolls the accumulators back to the captured floor.
        adapter.restore(floor);
        assertEquals(0, coordinator.getRewards().rings());
        assertEquals(0, coordinator.getRewards().lives());
        assertNull(coordinator.captureAccumulators().shield());
    }

    @Test
    void resetForMissingSnapshotZeroesAccumulators() {
        FakeCoordinator coordinator = new FakeCoordinator();
        coordinator.onEnter(BonusStageType.GUMBALL, null);
        BonusStageCoordinatorRewindAdapter adapter = new BonusStageCoordinatorRewindAdapter(coordinator);

        coordinator.addRings(7);
        coordinator.addLife();
        coordinator.setAwardedShield(ShieldType.FIRE);
        assertNotNull(coordinator.captureAccumulators().shield());

        adapter.resetForMissingSnapshot();

        assertEquals(0, coordinator.getRewards().rings());
        assertEquals(0, coordinator.getRewards().lives());
        assertNull(coordinator.captureAccumulators().shield());
    }
}
