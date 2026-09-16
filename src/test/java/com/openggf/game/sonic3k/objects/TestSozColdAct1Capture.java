package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.recording.RecordedFrameInput;
import com.openggf.game.recording.UserRecordingWriter;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cold, ordinary GameLoop SOZ1 traversal through the real boss, results and Act2 handoff.
 * The controller-only asset starts with s3k-complete-sonic-tails.bk2 offset 282195,
 * local frames 0..13999, then authored traversal and SozAct1VictoryRoute inputs.
 * Recorded during SOZ completion on 2026-09-16; InputLogAuthorTool verified its round trip.
 * It supplies no trace timing, positions, physics, object phases or damage calls.
 */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "soz.cold.act1.capture", matches = ".+")
class TestSozColdAct1Capture {
    @Test
    void fixedControllerRouteReachesVisiblePlayableAct2FromColdAct1() throws Exception {
        Path output = Path.of(System.getProperty("soz.cold.act1.capture"));
        Files.createDirectories(output.resolve("frames"));
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(
                Path.of("src/test/resources/routes/s3k/soz1-cold-sonic-tails.bk2"));
        int stride = Integer.getInteger("soz.cold.act1.stride", 4);
        assertTrue(stride > 0);
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        var inputs = new ArrayList<RecordedFrameInput>();
        boolean bossSeen = false;
        boolean sinkingSeen = false;
        int readyFrame = -1;
        try (var session = new GameplayCaptureSession(settings);
             var csv = Files.newBufferedWriter(output.resolve("state.csv"))) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 0, settings);
            assertEquals(8, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals("sonic", session.player().getCode());
            var followers = GameServices.level().getObjectManager().getObjectServices().playerQuery().sidekicks();
            assertEquals(1, followers.size());
            assertInstanceOf(com.openggf.sprites.playable.Tails.class, followers.getFirst());
            // Headless boot leaves the native initial Process_Sprites pass pending.
            GameServices.level().consumePendingInitialProcessSpritesPass();
            csv.write(GameplayCaptureSession.stateHeader() + ",zone,act,boss_routine,boss_phase,p1_mask,p2_mask");
            csv.newLine();
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                var input = movie.getFrame(frame);
                inputs.add(new RecordedFrameInput(frame, input.p1InputMask(), input.p1ActionMask(),
                        input.p1StartPressed(), input.p2InputMask(), input.p2ActionMask(), input.p2StartPressed()));
                session.step(input);
                var boss = SozAct1VictoryRoute.boss();
                bossSeen |= boss != null;
                sinkingSeen |= boss != null && boss.phase() == 3;
                var events = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow().events();
                csv.write(session.stateLine(frame, input) + "," + GameServices.level().getCurrentZone()
                        + "," + GameServices.level().getCurrentAct() + "," + (boss == null ? -1 : boss.routine())
                        + "," + (boss == null ? -1 : boss.phase()) + "," + input.p1InputMask() + "," + input.p2InputMask());
                csv.newLine();
                if (frame % stride == 0) {
                    ScreenshotCapture.savePNG(session.render(), output.resolve("frames/%05d.png".formatted(frame)));
                }
                assertFalse(session.player().getDead(), "player died at frame " + frame);
                if (GameServices.level().getCurrentAct() == 1 && !events.seamlessEntry()
                        && !session.player().isControlLocked() && readyFrame < 0) {
                    readyFrame = frame;
                }
            }
            Files.writeString(output.resolve("Input Log.txt"), UserRecordingWriter.inputLogText(inputs));
            assertTrue(bossSeen, "the cold route must spawn the real miniboss");
            assertTrue(sinkingSeen, "the real battle must win by sinking the miniboss");
            assertEquals(8, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertTrue(readyFrame >= 0, "the native victory must release playable Act2");
            assertTrue(movie.getFrameCount() - readyFrame >= 180, "retain visible destination settling frames");
            for (int line : new int[]{0, 2, 3}) {
                int colors = 0;
                for (int color = 0; color < 16; color++) {
                    colors |= PaletteWriteSupport.segaWordFromColor(
                            GameServices.level().getCurrentLevel().getPalette(line).getColor(color));
                }
                assertNotEquals(0, colors, "destination palette line " + line);
            }
            var image = session.render();
            int visible = 0;
            for (int y = 48; y < 190; y++) {
                for (int x = 0; x < image.width(); x++) {
                    if ((image.argb(x, y) & 0xFFFFFF) != 0) visible++;
                }
            }
            assertTrue(visible > 1000, "destination world must be visible outside the HUD");
            ScreenshotCapture.savePNG(image, output.resolve("destination.png"));
            Files.writeString(output.resolve("milestones.txt"), "Act2 ready frame: " + readyFrame
                    + "\nController frames: " + movie.getFrameCount() + "\nCold Sonic+Tails native320; intro enabled.\n");
        }
    }
}
