package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** AIZ now reaches the actual shared DoStart owner in the retained physical SST. */
@RequiresRom(SonicGame.SONIC_3K)
class TestAizMinibossCameraUnlock {
    @AfterEach void clear() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
    }
    @Test void nativeControlWaitsForTitleFlagThenPublishesActTwoTargets() throws Exception {
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        var manager = GameServices.level().getObjectManager();
        var flow = manager.createDynamicObject(() -> new S3kBossDefeatSignpostFlow(
                0x11F0, 0, S3kBossDefeatSignpostFlow.CleanupAction.NONE, true));
        flow.beginNativeReplacement(flow.getSlotIndex());
        var phase = S3kBossDefeatSignpostFlow.class.getDeclaredField("phase");
        phase.setAccessible(true);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object doStart = Enum.valueOf((Class) phase.getType(), "AWAIT_ACT_TRANSITION");
        phase.set(flow, doStart); // Declared native DoStart inlet; no timer is changed.
        fixture.camera().setMinX((short) 0x10E0);
        fixture.camera().setMaxX((short) 0x10E0);
        GameServices.gameState().setEndOfLevelFlag(false);
        manager.update(fixture.camera().getX(), fixture.sprite(), List.of(), 1);
        assertSame(flow, NativeSstSlotAccess.currentOwner(manager, flow.getSlotIndex()));
        assertEquals(0x10E0, fixture.camera().getMaxX() & 0xFFFF);
        int slot = flow.getSlotIndex();
        GameServices.gameState().setEndOfLevelFlag(true);
        manager.update(fixture.camera().getX(), fixture.sprite(), List.of(), 2);
        assertFalse(NativeSstSlotAccess.currentOwner(manager, slot) instanceof S3kBossDefeatSignpostFlow);
        assertEquals(0x10E0, fixture.camera().getMinX() & 0xFFFF);
        assertEquals(GameServices.level().getCurrentLevel().getMaxX(), fixture.camera().getMaxXTarget() & 0xFFFF);
        assertEquals(GameServices.level().getCurrentLevel().getMaxY(), fixture.camera().getMaxYTarget() & 0xFFFF);
        assertEquals(1, manager.activeObjectsOfType(S3kIncLevelEndXGradualInstance.class).size());
    }
}
