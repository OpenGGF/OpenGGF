package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.DdzDiagnostics;
import com.openggf.game.sonic3k.objects.DdzEndBossBodyObjectInstance;
import com.openggf.game.sonic3k.objects.DdzEndBossObjectInstance;
import com.openggf.game.sonic3k.runtime.DdzZoneRuntimeState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

/** Ordinary controller routes, not a trace comparison; only Chaos progress is declared. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDdzAuthoredRoutes {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest @ValueSource(ints = {320, 800})
    void freshSuperRouteCompletesAndReplaysFightWrapAndExit(int width) throws Exception {
        var config = SonicConfigurationService.getInstance(); config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, width == 800 ? "SUPER_32_9" : "NATIVE_4_3");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(12, 0).withFreshLevelStartLifecycle().build();
        GameServices.gameState().restoreS3kEmeraldProgress(java.util.Collections.nCopies(7, 1), false);
        assertEquals(width, fixture.camera().getWidth());
        assertEquals("sonic", fixture.sprite().getCode());
        assertTrue(GameServices.sprites().getSidekicks().isEmpty());
        // Capture frame0 is the initial sprite pass already performed by the fixture's boot.
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ddz-super-fresh-" + width + ".bk2"));
        assertEquals(width == 800 ? 9923 : 10396, movie.getFrameCount());
        fixture.runner().primeInputState(movie.getFrame(0));
        var registry = fixture.gameplayMode().getRewindRegistry();
        boolean fightChecked = false, wrapChecked = false, exitChecked = false, chaseSeen = false;
        int priorCamera = fixture.camera().getX() & 0xFFFF;
        for (int frame = 1; frame < movie.getFrameCount(); frame++) {
            step(fixture, movie.getFrame(frame));
            assertFalse(fixture.sprite().getDead(), "fresh route death at " + frame);
            var manager = GameServices.level().getObjectManager();
            var body = manager.activeObjectsOfType(DdzEndBossBodyObjectInstance.class).stream().findFirst().orElse(null);
            var boss = manager.activeObjectsOfType(DdzEndBossObjectInstance.class).stream().findFirst().orElse(null);
            int routine = boss == null ? -1 : DdzDiagnostics.bossRoutine(boss);
            chaseSeen |= routine == 14;
            int cameraX = fixture.camera().getX() & 0xFFFF;
            boolean fight = !fightChecked && body != null && DdzDiagnostics.bodyHitPoints(body) == 6;
            boolean wrap = !wrapChecked && chaseSeen && cameraX + 0x1000 < priorCamera;
            boolean exit = !exitChecked && chaseSeen && routine == 0;
            if (fight || wrap || exit) {
                var saved = registry.capture(); String before = observed();
                int horizon = Math.min(45, movie.getFrameCount() - frame - 1);
                for (int n = 1; n <= horizon; n++) step(fixture, movie.getFrame(frame + n));
                String expected = observed();
                registry.restore(saved); assertEquals(before, observed(), "restore at " + frame);
                fixture.runner().primeInputState(movie.getFrame(frame));
                for (int n = 1; n <= horizon; n++) step(fixture, movie.getFrame(frame + n));
                assertEquals(expected, observed(), "forward replay at " + frame);
                registry.restore(saved); fixture.runner().primeInputState(movie.getFrame(frame));
                fightChecked |= fight; wrapChecked |= wrap; exitChecked |= exit;
            }
            priorCamera = cameraX;
        }
        assertTrue(fightChecked && wrapChecked && exitChecked, "all independent route spots reached");
        assertEquals(13, GameServices.level().getRequestedZone()); assertEquals(1, GameServices.level().getRequestedAct());
        assertEquals(width == 800 ? 20 : 12, fixture.sprite().getRingCount());
    }

    private static void step(HeadlessTestFixture fixture, Bk2FrameInput input) {
        int mask = input.p1InputMask();
        fixture.stepFrame((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0, (mask & 16) != 0);
    }

    private static String observed() {
        var player = GameServices.sprites().getMainPlayable(); var camera = GameServices.camera();
        var result = new StringBuilder().append(player.getCentreX()).append(',').append(player.getCentreY())
                .append(',').append(player.getXSpeed()).append(',').append(player.getYSpeed())
                .append(',').append(player.getRingCount()).append(',').append(camera.getX()).append(',').append(camera.getY());
        var manager = GameServices.level().getObjectManager(); result.append(',').append(manager.getVblaCounter());
        manager.getActiveObjects().stream().filter(o -> !o.isDestroyed())
                .map(o -> o.getClass().getName() + (o.getSpawn() == null ? ":fixed" : ":" + o.getX() + "," + o.getY()))
                .sorted().forEach(s -> result.append('|').append(s));
        for (int line = 0; line < 4; line++) for (int color = 0; color < 16; color++)
            result.append('|').append(com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                    GameServices.level().getCurrentLevel().getPalette(line).getColor(color)));
        return result.append('|').append(HexFormat.of().formatHex(
                ((DdzZoneRuntimeState) GameServices.zoneRuntimeState()).captureBytes())).toString();
    }
}
