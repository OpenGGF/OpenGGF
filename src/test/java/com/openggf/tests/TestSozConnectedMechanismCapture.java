package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tests.rules.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Optional production render companion to the asserted positioned mechanism scenarios. */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "soz.connected.capture", matches = ".+")
class TestSozConnectedMechanismCapture {
    @ParameterizedTest
    @EnumSource(SozConnectedMechanismRoute.Scene.class)
    void capture(SozConnectedMechanismRoute.Scene scene) throws Exception {
        var out = Path.of(System.getProperty("soz.connected.capture"), scene.name().toLowerCase());
        Files.createDirectories(out.resolve("frames"));
        var settings = new GameplayCaptureSession.Settings(400, "sonic",
                scene == SozConnectedMechanismRoute.Scene.LOWER ? "" : "tails", "off", null, scene.x, scene.y);
        try (var session = new GameplayCaptureSession(settings);
             var csv = Files.newBufferedWriter(out.resolve("state.csv"))) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 1, settings);
            // Finish native setup before the first recorded controller input.
            GameServices.level().consumePendingInitialProcessSpritesPass();
            session.player().setRingCount(99);
            SozConnectedMechanismRoute.assertRoster(scene);
            csv.write(GameplayCaptureSession.stateHeader() + ",bg,sand,collision");
            csv.newLine();
            var route = new SozConnectedMechanismRoute(scene);
            for (int frame = 0; frame < scene.frames; frame++) {
                var input = route.input(frame, session.player());
                session.step(input);
                var events = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow().events();
                csv.write(session.stateLine(frame, input) + "," + events.backgroundRoutine() + ","
                        + events.sandHeight() + "," + events.backgroundCollision());
                csv.newLine();
                if (frame % 4 == 0) ScreenshotCapture.savePNG(session.render(),
                        out.resolve("frames").resolve(String.format("%05d.png", frame)));
                assertFalse(session.player().getDead(), scene + " at frame " + frame);
                if (scene == SozConnectedMechanismRoute.Scene.LOWER && frame > 1000
                        && events.backgroundRoutine() == 0x20) break;
            }
        }
    }
}
