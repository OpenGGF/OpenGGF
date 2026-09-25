package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.level.LevelContinuationCarry;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh LRZ3 load through the shared continuation owner, independent of the cutscene route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLevelContinuationHeadless {
    @ParameterizedTest @EnumSource(value=ShieldType.class, names={"FIRE", "LIGHTNING", "BUBBLE"})
    void carriesCountersAndElementalShieldWithoutCreatingTitleOwner(ShieldType shield) throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 1).build();
        var manager = GameServices.level();
        fixture.sprite().setRingCount(37);
        manager.getLevelGamestate().setTimerFrames(12345);
        fixture.sprite().giveShield(shield);
        LevelContinuationCarry.request(manager, 22, 0, fixture.sprite().getRingCount(),
                manager.getLevelGamestate().getTimerFrames(), shield);
        assertTrue(manager.consumeZoneActRequest());
        manager.loadZoneAndActAtFreshTitleCardBoundary(22, 0);
        assertEquals(22, manager.getCurrentZone());
        assertEquals(0, manager.getCurrentAct());
        assertFalse(manager.getTransitions().isTitleCardRequested());
        assertFalse(manager.hasPendingFreshLevelTransitionBoundary());
        assertTrue(fixture.sprite().hasShield());
        assertEquals(shield, fixture.sprite().getShieldType());
        var title = (Sonic3kTitleCardManager) GameServices.module().getTitleCardProvider();
        assertEquals(-1, title.capture().freshLevelRuntimeArtHandoffLevelIndex());
        assertTrue(title.capture().artHandles().isEmpty(), "no title art queue");
        ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider())
                .getLrzEventsForTest().update(0, 0);
        assertEquals(37, manager.getLevelGamestate().getRings());
        assertEquals(12345, manager.getLevelGamestate().getTimerFrames());
        assertFalse(manager.getLevelGamestate().isTimerPaused());
        manager.getLevelGamestate().setRings(5);
        ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider())
                .getLrzEventsForTest().update(0, 1);
        assertEquals(5, manager.getLevelGamestate().getRings(), "bank was consumed");
        manager.loadZoneAndActWithTitleCard(22, 0);
        assertTrue(manager.consumeTitleCardRequest(), "later direct entry must show its normal card");
        assertFalse(fixture.sprite().hasShield());
        assertEquals(0, manager.getLevelGamestate().getRings());
    }

    @Test void ordinaryDirectBossActEntryStillRequestsTitleCard() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(9, 1).build();
        var manager = GameServices.level();
        manager.loadZoneAndActAtFreshTitleCardBoundary(22, 0);
        assertTrue(manager.getTransitions().isTitleCardRequested());
        assertTrue(manager.hasPendingFreshLevelTransitionBoundary());
        manager.completeFreshLevelTransitionBoundary();
    }
}
