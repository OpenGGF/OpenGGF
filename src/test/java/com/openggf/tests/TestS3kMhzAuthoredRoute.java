package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.MhzMinibossInstance;
import com.openggf.game.sonic3k.runtime.MhzZoneRuntimeState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh controller-only MHZ1 completion; no trace or seeded boss/player state. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMhzAuthoredRoute {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test void freshSonicCompletesActOneAndReplaysItsLiveInteractions() throws Exception {
        var config = SonicConfigurationService.getInstance(); config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(7, 0).withFreshLevelStartLifecycle().build();
        assertEquals(320, fixture.camera().getWidth());
        assertEquals("sonic", fixture.sprite().getCode());
        assertTrue(GameServices.sprites().getSidekicks().isEmpty());
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/mhz1-sonic-fresh-320.bk2"));
        assertEquals(9301, movie.getFrameCount());
        fixture.runner().primeInputState(movie.getFrame(0));
        var checked = new HashSet<String>();
        int firstActTwoX = -1;
        for (int frame = 1; frame < movie.getFrameCount(); frame++) {
            step(fixture, movie.getFrame(frame));
            var player = fixture.sprite(); var level = GameServices.level();
            assertFalse(player.getDead(), "cold route death at " + frame);
            var boss = level.getObjectManager().activeObjectsOfType(MhzMinibossInstance.class)
                    .stream().findFirst().orElse(null);
            String spot = null;
            if (level.getCurrentAct() == 0) {
                if (player.isObjectControlled() && player.getCentreX() > 6000 && player.getCentreX() < 6400
                        && player.getCentreY() < 1500) spot = "pulley";
                if (player.getSpindash() && player.getCentreX() > 9100 && player.getCentreX() < 9300) spot = "sticky-vine";
                if (boss != null && boss.getState().hitCount == 5) spot = "first-hit";
                if (boss != null && boss.getState().hitCount == 1) spot = "last-hit-approach";
                if (boss != null && boss.isDefeated()) spot = "defeat";
            } else if (level.getCurrentAct() == 1) {
                if (firstActTwoX < 0) firstActTwoX = player.getCentreX();
                if (!player.isObjectControlled() && player.getCentreX() > 700) spot = "act-two-released";
            }
            if (spot != null && checked.add(spot)) {
                var registry = fixture.gameplayMode().getRewindRegistry();
                var saved = registry.capture(); String before = observed();
                int horizon = Math.min(45, movie.getFrameCount() - frame - 1);
                for (int n = 1; n <= horizon; n++) step(fixture, movie.getFrame(frame + n));
                String expected = observed();
                registry.restore(saved); assertEquals(before, observed(), "restore " + spot + " at " + frame);
                fixture.runner().primeInputState(movie.getFrame(frame));
                for (int n = 1; n <= horizon; n++) step(fixture, movie.getFrame(frame + n));
                assertEquals(expected, observed(), "replay " + spot + " at " + frame);
                registry.restore(saved); fixture.runner().primeInputState(movie.getFrame(frame));
            }
        }
        assertEquals(java.util.Set.of("pulley", "sticky-vine", "first-hit", "last-hit-approach", "defeat", "act-two-released"), checked);
        assertEquals(7, GameServices.level().getCurrentZone());
        assertEquals(1, GameServices.level().getCurrentAct());
        // The next act may already own control for the leaf-blower cutscene.
        // The earlier act-two-released spot proves the title handoff released it.
        assertTrue(fixture.sprite().getCentreX() > firstActTwoX + 300, "real movement after seamless title handoff");
    }

    private static void step(HeadlessTestFixture fixture, Bk2FrameInput input) {
        int mask = input.p1InputMask();
        fixture.stepFrame((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0, (mask & 16) != 0);
    }

    private static String observed() {
        var player = GameServices.sprites().getMainPlayable(); var camera = GameServices.camera();
        var result = new StringBuilder().append(player.getCentreX()).append(',').append(player.getCentreY())
                .append(',').append(player.getXSpeed()).append(',').append(player.getYSpeed())
                .append(',').append(player.getRingCount()).append(',').append(player.isObjectControlled())
                .append(',').append(camera.getX()).append(',').append(camera.getY());
        var manager = GameServices.level().getObjectManager(); result.append(',').append(manager.getVblaCounter());
        manager.getActiveObjects().stream().filter(o -> !o.isDestroyed())
                .map(o -> o.getClass().getName() + (o.getSpawn() == null ? ":fixed" : ":" + o.getX() + "," + o.getY()))
                .sorted().forEach(s -> result.append('|').append(s));
        for (int line = 0; line < 4; line++) for (int color = 0; color < 16; color++)
            result.append('|').append(com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                    GameServices.level().getCurrentLevel().getPalette(line).getColor(color)));
        return result.append('|').append(HexFormat.of().formatHex(
                ((MhzZoneRuntimeState) GameServices.zoneRuntimeState()).captureBytes())).toString();
    }
}
