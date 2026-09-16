package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.recording.RecordedFrameInput;
import com.openggf.game.recording.UserRecordingWriter;
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
 * Cold ordinary-input SOZ2 traversal through eight natural boss hits, capsule and LRZ.
 * The first 1500 inputs come from complete-emeralds BK2 offset 365809; subsequent
 * inputs were authored against live gameplay, including the existing boss controller.
 * Frozen on 819d99cc1 during SOZ completion (2026-09-16). No gameplay hydration.
 */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "soz.cold.act2.capture", matches = ".+")
class TestSozColdAct2Capture {
    @Test
    void fixedControllerRouteReachesVisibleLavaReefFromColdAct2() throws Exception {
        Path output = Path.of(System.getProperty("soz.cold.act2.capture"));
        Files.createDirectories(output.resolve("frames"));
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(
                Path.of("src/test/resources/routes/s3k/soz2-cold-sonic-tails.bk2"));
        int stride = Integer.getInteger("soz.cold.act2.stride", 4);
        assertTrue(stride > 0);
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        var inputs = new ArrayList<RecordedFrameInput>();
        boolean bossSeen = false;
        boolean capsuleSeen = false;
        boolean resultsSeen = false;
        boolean resultsFinished = false;
        int hits = 0;
        var milestones = new StringBuilder();
        int readyFrame = -1;
        try (var session = new GameplayCaptureSession(settings);
             var csv = Files.newBufferedWriter(output.resolve("state.csv"))) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 1, settings);
            assertEquals(8, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals("sonic", session.player().getCode());
            var followers = GameServices.level().getObjectManager().getObjectServices().playerQuery().sidekicks();
            assertEquals(1, followers.size());
            assertInstanceOf(com.openggf.sprites.playable.Tails.class, followers.getFirst());
            // Headless boot leaves the native initial Process_Sprites pass pending.
            GameServices.level().consumePendingInitialProcessSpritesPass();
            csv.write(GameplayCaptureSession.stateHeader() + ",zone,act,boss_hp,capsule_open,results,p1_mask,p2_mask");
            csv.newLine();
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                var input = movie.getFrame(frame);
                inputs.add(new RecordedFrameInput(frame, input.p1InputMask(), input.p1ActionMask(),
                        input.p1StartPressed(), input.p2InputMask(), input.p2ActionMask(), input.p2StartPressed()));
                var beforeBoss = SozEndBossVictoryRoute.boss();
                int beforeHp = beforeBoss == null ? 8 : beforeBoss.getCollisionProperty();
                session.step(input);
                var boss = SozEndBossVictoryRoute.boss();
                bossSeen |= boss != null;
                if (boss != null && boss.getCollisionProperty() < beforeHp) {
                    assertEquals(beforeHp - 1, boss.getCollisionProperty(), "single natural hit");
                    milestones.append("hit ").append(++hits).append(": ").append(frame).append('\n');
                }
                var capsules = GameServices.level().getObjectManager()
                        .activeObjectsOfType(SozEndBossEggCapsule.class);
                boolean capsuleOpen = !capsules.isEmpty() && capsules.getFirst().isOpened();
                if (capsuleOpen && !capsuleSeen) milestones.append("capsule: ").append(frame).append('\n');
                capsuleSeen |= capsuleOpen;
                boolean results = GameServices.gameState().isEndOfLevelActive();
                if (results && !resultsSeen) milestones.append("results: ").append(frame).append('\n');
                if (resultsSeen && !results && !resultsFinished) {
                    resultsFinished = true;
                    milestones.append("results finished: ").append(frame).append('\n');
                }
                resultsSeen |= results;
                csv.write(session.stateLine(frame, input) + "," + GameServices.level().getCurrentZone()
                        + "," + GameServices.level().getCurrentAct() + "," + (boss == null ? -1 : boss.getCollisionProperty())
                        + "," + capsuleOpen + "," + results + "," + input.p1InputMask() + "," + input.p2InputMask());
                csv.newLine();
                if (frame % stride == 0) {
                    ScreenshotCapture.savePNG(session.render(), output.resolve("frames/%05d.png".formatted(frame)));
                }
                assertFalse(session.player().getDead(), "player died at frame " + frame);
                if (GameServices.level().getCurrentZone() == 9 && readyFrame < 0) {
                    readyFrame = frame;
                }
            }
            Files.writeString(output.resolve("Input Log.txt"), UserRecordingWriter.inputLogText(inputs));
            assertTrue(bossSeen, "the cold route must spawn the real end boss");
            assertEquals(8, hits, "eight natural boss hits");
            assertTrue(capsuleSeen, "capsule opened through controller contact");
            assertTrue(resultsSeen && resultsFinished, "results sequence completed");
            assertEquals(9, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertFalse(session.player().isControlLocked(), "destination player control released");
            assertFalse(session.player().isObjectControlled(), "destination object control released");
            assertTrue(readyFrame >= 0, "the native victory must load Lava Reef");
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
            Files.writeString(output.resolve("milestones.txt"), milestones + "LRZ load frame: " + readyFrame
                    + "\nController frames: " + movie.getFrameCount() + "\nCold Sonic+Tails native320; intro enabled.\n");
        }
    }
}
