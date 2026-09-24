package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Short placed-boss admission check, independent of the full MHZ route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMhzEndBossAdmission {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void placedBossSurvivesForwardWindowAdmission(WidescreenAspect aspect) {
        verifyAdmission(aspect, false);
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void checkpointReloadAdmitsThePlacedBoss(WidescreenAspect aspect) {
        verifyAdmission(aspect, true);
    }

    private void verifyAdmission(WidescreenAspect aspect, boolean checkpointReload) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, aspect.pixelWidth());
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        // Declared positioned approach before the end-boss load window; no boss
        // injection, camera writes, hit callbacks or copied native gameplay state.
        var builder = HeadlessTestFixture.builder().withZoneAndAct(7, 1)
                .withFreshLevelStartLifecycle();
        if (!checkpointReload) builder.startPosition((short) 15008, (short) 704).startPositionIsCentre();
        var fixture = builder.build();
        if (checkpointReload) {
            ((com.openggf.game.CheckpointState) GameServices.level().getCheckpointState())
                    .saveCheckpoint(7, 15008, 704, false);
            GameServices.level().respawnPlayer();
            assertEquals(15008, fixture.sprite().getCentreX());
        }
        MhzEndBossInstance admitted = null;
        for (int frame = 0; frame < 500; frame++) {
            fixture.stepFrame(false, false, false, true, frame % 40 < 20);
            admitted = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(MhzEndBossInstance.class::isInstance).map(MhzEndBossInstance.class::cast)
                    .filter(o -> !o.isDestroyed() && o.getCustomFlag(0x100) != 0).findFirst().orElse(null);
            if (admitted != null) break;
        }
        assertNotNull(admitted, "placed MHZ2 boss must survive its first forward-window dispatch");
        assertEquals(15568 + 0xC0, admitted.getX(), "Obj_MHZEndBoss adds $C0 after camera admission");
        fixture.stepIdleFrames(2); // newly allocated weather descendants dispatch
        assertEquals(15, encounterCount(), "core + eight direct children + six weather descendants");
        var saved = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepIdleFrames(20);
        int expectedX = boss().getX();
        assertEquals(15, encounterCount(), "offscreen parts must not be culled during admission");
        fixture.gameplayMode().getRewindRegistry().restore(saved);
        fixture.stepIdleFrames(20);
        assertEquals(expectedX, boss().getX(), "admission/lifetime survives forward replay");
        fixture.stepIdleFrames(180);
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .noneMatch(o -> !o.isDestroyed()
                        && o instanceof com.openggf.game.sonic3k.objects.Sonic3kSSEntryRingObjectInstance
                        && o.getX() == 14784),
                "the earlier offscreen ring must finish restoration, not retain a pending owner forever");
    }

    private static long encounterCount() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed() && o.getClass().getSimpleName().startsWith("MhzEndBoss"))
                .count();
    }

    private static MhzEndBossInstance boss() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(MhzEndBossInstance.class::isInstance).map(MhzEndBossInstance.class::cast)
                .filter(o -> !o.isDestroyed()).findFirst().orElseThrow();
    }
}
