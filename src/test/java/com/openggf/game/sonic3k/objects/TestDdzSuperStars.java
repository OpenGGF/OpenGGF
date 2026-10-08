package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.DdzZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.openggf.configuration.WidescreenAspect;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDdzSuperStars {
    @AfterEach void cleanup() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
    }
    private HeadlessTestFixture boot(boolean hyper) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(12, 0).build();
        GameServices.gameState().restoreS3kEmeraldProgress(java.util.Collections.nCopies(7, hyper ? 3 : 1), hyper);
        return fixture;
    }
    private DdzSuperStarsObjectInstance stars() {
        return GameServices.level().getObjectManager().activeObjectsOfType(DdzSuperStarsObjectInstance.class)
                .stream().findFirst().orElseThrow();
    }
    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void superEntryUsesTheFixedSlotAndReplaysAcrossRelease(WidescreenAspect aspect) {
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, aspect.pixelWidth());
        var fixture = boot(false);
        assertEquals(aspect.pixelWidth(), fixture.camera().getWidth());
        fixture.stepIdleFrames(49);
        assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(DdzSuperStarsObjectInstance.class).isEmpty());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        String expected = null;
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) registry.restore(saved);
            fixture.stepIdleFrames(18);
            var stars = stars();
            assertEquals(fixture.sprite().getGameRules().powerUp().superStarsFixedSlotIndex(), stars.getSlotIndex());
            assertTrue(stars.isHighPriority()); assertEquals(1, stars.getPriorityBucket());
            assertTrue(stars.isDrawing());
            String actual = stars.getX()+","+stars.getY()+","+stars.mappingFrame();
            if (expected == null) expected = actual; else assertEquals(expected, actual);
        }
    }
    @Test void nativeEntryObservationMatchesTheFixedSlotsFirstDrawAndFrameCadence() {
        var fixture = boot(false); fixture.stepIdleFrames(50);
        // Native entry save514214, no Super Emeralds: pass50 executes loc_8242A,
        // pass51 anchors frame0, then frames0..5 last two passes each; pass63 reanchors.
        assertEquals(6, stars().mappingFrame()); assertFalse(stars().isDrawing());
        int anchorX = 0;
        for (int pass = 51; pass <= 63; pass++) {
            fixture.stepIdleFrames(1);
            int age = (pass - 51) % 12;
            if (age == 0) anchorX = fixture.sprite().getCentreX() & 0xFFFF;
            assertEquals(age / 2, stars().mappingFrame(), "native pass " + pass);
            assertEquals((anchorX - age * 8) & 0xFFFF, stars().getX(), "native anchored offset " + pass);
        }
    }

    @Test void hyperInitWaitsForNativeArtQueueBeforeStartingEachChild() throws Exception {
        var fixture = boot(true);
        fixture.stepIdleFrames(50);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        // Native save514214, unchanged emeralds: queue submitted on pass50;
        // first child starts on pass52, remaining children on53/54/55.
        // Restore while loading must rebind the submitted job, not queue another.
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) registry.restore(saved);
            var stars = GameServices.level().getObjectManager()
                    .activeObjectsOfType(HyperSonicStarsObjectInstance.class).stream().findFirst().orElseThrow();
            for (int pass = 50; pass <= 65; pass++) {
                for (int child = 0; child < 4; child++) {
                    var angle = HyperSonicStarsObjectInstance.class.getDeclaredField("angle" + child);
                    angle.setAccessible(true);
                    int age = Math.max(0, pass - (52 + child) + 1);
                    assertEquals((child * 64 - age * 16) & 255, angle.getInt(stars),
                            "native orbit phase at pass " + pass + " child " + child);
                }
                fixture.stepIdleFrames(1);
            }
        }
    }

    @Test void hyperEntryUsesItsOwnStars() {
        var fixture = boot(true); fixture.stepIdleFrames(60);
        assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(DdzSuperStarsObjectInstance.class).isEmpty());
        assertFalse(GameServices.level().getObjectManager().activeObjectsOfType(HyperSonicStarsObjectInstance.class).isEmpty());
    }
    @Test void cadenceReanchorsEveryTwelvePassesAndAppliesWrapBeforeMotion() {
        var fixture = boot(false); fixture.stepIdleFrames(60);
        var star = new DdzSuperStarsObjectInstance(new ObjectSpawn(0,0,0,0,0,false,0));
        star.setServices(TestEnvironment.objectServices());
        star.update(0, fixture.sprite()); assertFalse(star.isDrawing());
        int playerX = fixture.sprite().getCentreX() & 0xFFFF;
        int playerY = fixture.sprite().getCentreY() & 0xFFFF;
        star.update(1, fixture.sprite());
        assertEquals(0, star.mappingFrame()); assertEquals(playerX, star.getX()); assertEquals(playerY, star.getY());
        for (int pass=1; pass<12; pass++) {
            star.update(pass+1, fixture.sprite());
            assertEquals(pass/2, star.mappingFrame());
            assertEquals((playerX-8*pass)&0xFFFF, star.getX());
        }
        star.update(13, fixture.sprite()); assertEquals(0, star.mappingFrame()); assertEquals(playerX, star.getX());
        var state = (DdzZoneRuntimeState) GameServices.zoneRuntimeState(); state.setWrapOffset(0x2000);
        star.update(14, fixture.sprite()); assertEquals((playerX-0x2008)&0xFFFF, star.getX());
    }
}
