package com.openggf.mods.code;

import com.openggf.control.InputActionMasks;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.CheckpointState;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.save.SavePaths;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.Sonic3kSpikeObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostBonusStarChild;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostObjectInstance;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.InputLogAuthorTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Second Strike AIZ M3, 2026-10-15: original post697 and spike690, ordinary pad
 * contact/death/restart/escape. Inputs are the sole gameplay writer after the
 * declared positioned true-act2 entry. Requires the exact strict-built mod;
 * no substitute build, debug damage, checkpoint/reward/life/position writes.
 * This is Sonic/320/No Save positioned checkpoint evidence, not a cold route,
 * forest/fire reload, Knuckles post7 mapping or durable-slot qualification.
 */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "secondstrike.checkpoint.jar", matches = ".+")
class TestSecondStrikeCheckpointGameplay {
    private static final String CANDIDATE_SHA256 = "1f6bfedd83b5d711c74fc73f01889838680d631c54b93bc308dd9d4ccbfcfef9";
    private static final String ROM_SHA1 = "cfbf98c36c776677290a872547ac47c53d2761d6";
    private static final int POST_X = 0x378C, POST_Y = 0x15A, SPIKE_X = 0x3754, SPIKE_Y = 0x163;
    @TempDir Path temporary;

    @Test void nativePost697DeathReloadStartsStoppedAtZeroRingsAndEscapesForward() throws Exception {
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        Path rom = Path.of(System.getProperty("s3k.rom.path")).toAbsolutePath();
        Path jar = Path.of(System.getProperty("secondstrike.checkpoint.jar")).toAbsolutePath();
        assertEquals(ROM_SHA1, digest(rom, "SHA-1"));
        assertEquals(CANDIDATE_SHA256, digest(jar, "SHA-256"));
        Path output = Path.of(System.getProperty("secondstrike.checkpoint.output",
                temporary.resolve("post697").toString())).toAbsolutePath();
        Files.createDirectories(output);
        assertFalse(Files.exists(output.resolve("state.csv")), "preserve prior witnesses");
        String oldDirectory = System.getProperty("user.dir"), oldSaveRoot = System.getProperty(SavePaths.ROOT_PROPERTY);
        System.setProperty("user.dir", Files.createDirectories(output.resolve("preferences")).toString());
        System.setProperty(SavePaths.ROOT_PROPERTY, Files.createDirectories(output.resolve("saves")).toString());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, POST_X - 24, POST_Y);
        try (var session = new GameplayCaptureSession(settings); var driver = new Driver(session, output)) {
            session.applyMod(jar); session.boot(rom, 0, 1, settings);
            driver.record("declared-positioned-start", "-");
            revealScene(driver);
            driver.picture("native-scene-ready");
            assertFalse(checkpoint().isActive(), "the test must earn its checkpoint by contact");
            assertEquals(0, player().getRingCount()); assertEquals(0, GameServices.level().getLevelGamestate().getRings());
            var post = GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostObjectInstance.class)
                    .stream().filter(p -> p.getSpawn().layoutIndex() == 697).findFirst().orElseThrow();
            assertEquals(POST_X, post.getSpawn().x()); assertEquals(POST_Y, post.getSpawn().y());
            var originalSpike = GameServices.level().getCurrentLevel().getObjects().stream()
                    .filter(spawn -> spawn.layoutIndex() == 690).findFirst().orElseThrow();
            assertEquals(0x08, originalSpike.objectId()); assertEquals(0, originalSpike.subtype());
            assertEquals(SPIKE_X, originalSpike.x()); assertEquals(SPIKE_Y, originalSpike.y());
            assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kSpikeObjectInstance.class)
                    .stream().anyMatch(spike -> spike.getX() == SPIKE_X && spike.getY() == SPIKE_Y));
            int lives = GameServices.gameState().getLives(); assertTrue(lives > 1);
            for (int tick = 0; tick < 240 && checkpoint().getLastCheckpointIndex() != 6; tick++)
                driver.step("ordinary-post-contact", AbstractPlayableSprite.INPUT_RIGHT, false);
            assertEquals(6, checkpoint().getLastCheckpointIndex(), "ordinary walking must activate original697");
            assertEquals(6, checkpoint().getStarPostActivationMark());
            assertEquals(POST_X, checkpoint().getSavedX()); assertEquals(POST_Y, checkpoint().getSavedY());
            assertFalse(checkpoint().isUsedForSpecialStage());
            driver.picture("native-checkpoint-activated");
            var beforeDeathManager = GameServices.level().getObjectManager();
            for (int tick = 0; tick < 480 && !player().getDead(); tick++) {
                boolean jump = !player().getAir() && player().getXSpeed() < 0
                        && player().getCentreX() > SPIKE_X + 12 && (tick & 1) == 0;
                driver.step("ordinary-original-spike-approach", steer(SPIKE_X), jump);
                assertNoBonus();
            }
            assertTrue(player().getDead(), "ordinary spike approach must cause actual death; inspect CSV/input");
            assertTrue(Math.abs(player().getCentreX() - SPIKE_X) < 40, "death must occur at the original hazard");
            driver.picture("native-death");
            int restoredAt = -1;
            for (int tick = 0; tick < 900; tick++) {
                driver.step("native-death-restart", 0, false);
                assertNoBonus();
                if (GameServices.level().getObjectManager() != beforeDeathManager && !player().getDead()
                        && player().getCentreX() == POST_X && player().getCentreY() == POST_Y) {
                    restoredAt = driver.frame; break;
                }
            }
            assertTrue(restoredAt >= 0, "native death/fade/full reload must restore the earned checkpoint");
            assertEquals(lives - 1, GameServices.gameState().getLives(), "exactly one native death costs one life");
            assertEquals(0, player().getRingCount()); assertEquals(0, GameServices.level().getLevelGamestate().getRings());
            assertEquals(0, player().getXSpeed()); assertEquals(0, player().getYSpeed()); assertEquals(0, player().getGSpeed());
            assertEquals(6, checkpoint().getLastCheckpointIndex()); assertEquals(6, checkpoint().getStarPostActivationMark());
            assertFalse(checkpoint().isUsedForSpecialStage());
            driver.picture("native-stopped-zero-ring-restore");
            revealScene(driver);
            // Original699/sub04 is a side-break rock. Native
            // Obj_AIZLRZEMZRock_PushBreakMain requires ROLL and >=$480;
            // ordinary jump taps from this stopped checkpoint cannot supply it.
            // Face right and use the character's ordinary down+A spindash.
            for (int tick = 0; tick < 2; tick++) driver.step("ordinary-face-right", AbstractPlayableSprite.INPUT_RIGHT, false);
            for (int tick = 0; tick < 8; tick++) driver.step("ordinary-stop-before-charge", 0, false);
            for (int tick = 0; tick < 3; tick++) driver.step("ordinary-duck", AbstractPlayableSprite.INPUT_DOWN, false);
            for (int pulse = 0; pulse < 8; pulse++) {
                driver.step("ordinary-spindash-charge", AbstractPlayableSprite.INPUT_DOWN, true);
                driver.step("ordinary-spindash-charge", AbstractPlayableSprite.INPUT_DOWN, false);
            }
            driver.step("ordinary-spindash-release", AbstractPlayableSprite.INPUT_RIGHT, false);
            assertTrue(player().getRolling(), "native input must launch an ordinary rolling spindash");
            // Release publishes ground inertia first; native x_vel is derived on
            // the next rolling frame (PlayableSpriteMovement.doReleaseSpindash).
            assertTrue(player().getGSpeed() >= 0x480, "native launch must supply the original side-break speed");
            driver.picture("ordinary-native-spindash-release");
            for (int tick = 0; tick < 480 && player().getCentreX() < POST_X + 160; tick++) {
                driver.step("ordinary-forward-escape", AbstractPlayableSprite.INPUT_RIGHT, false);
                assertFalse(player().getDead(), "zero-ring restart must escape with ordinary native abilities");
                assertNoBonus();
            }
            assertTrue(player().getCentreX() >= POST_X + 160, "ordinary escape must cross the original rock/auto-spin area");
            assertEquals(lives - 1, GameServices.gameState().getLives());
            assertEquals(0, GameServices.level().getCurrentZone()); assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals(1, GameServices.level().getApparentAct());
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            driver.picture("ordinary-zero-ring-escape");
            var save = SessionManager.getCurrentWorldSession().getSaveSessionContext();
            assertTrue(save == null || save.activeSlot().isEmpty(), "No Save witness cannot claim durable-slot progression");
        } finally {
            restore("user.dir", oldDirectory); restore(SavePaths.ROOT_PROPERTY, oldSaveRoot);
        }
    }

    private static void revealScene(Driver driver) throws Exception {
        for (int tick = 0; tick < 240; tick++) {
            driver.step("ordinary-fade-scene-reveal", 0, false);
            assertFalse(player().getDead(), "positioned reveal must survive without fixture state edits");
            var ui = GameServices.graphics().getUiRenderPipeline();
            if (driver.session.loop().getCurrentGameMode() == GameMode.LEVEL && !GameServices.fade().isActive()
                    && (ui == null || !ui.isFadeActive())
                    && LevelSpritePresentation.published(GameServices.level()).tiles().stream()
                    .anyMatch(tile -> tile.layer() == SpritePresentation.Layer.PLAYER)
                    && Arrays.stream(driver.session.render().pixels()).distinct().count() > 16) return;
        }
        fail("ordinary fade/player/scene publication must complete");
    }
    private static void assertNoBonus() {
        assertEquals(BonusStageType.NONE, GameServices.bonusStage().getActiveType());
        assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostBonusStarChild.class).isEmpty(),
                "unselected zero-ring post must not manufacture an invitation");
    }
    private static int steer(int target) {
        int projected = target - player().getCentreX() - player().getXSpeed() / 48;
        return projected < -2 ? AbstractPlayableSprite.INPUT_LEFT : projected > 2 ? AbstractPlayableSprite.INPUT_RIGHT : 0;
    }
    private static AbstractPlayableSprite player() { return GameServices.camera().getFocusedSprite(); }
    private static CheckpointState checkpoint() { return (CheckpointState) GameServices.level().getCheckpointState(); }
    private static String digest(Path path, String algorithm) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(path)));
    }
    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key); else System.setProperty(key, value);
    }

    private static final class Driver implements AutoCloseable {
        final GameplayCaptureSession session;
        final Path output;
        final BufferedWriter csv;
        final List<String> input = new ArrayList<>();
        int frame;
        Driver(GameplayCaptureSession session, Path output) throws Exception {
            this.session = session; this.output = output;
            csv = Files.newBufferedWriter(output.resolve("state.csv"));
            csv.write("frame,phase,mode,act,apparent_act,level_frame,object_vint,manager,x,y,xvel,yvel,gspeed,air,rolling,spindash,dead,rings,live_rings,lives,checkpoint,mark,used,cam_x,cam_y,fade,ui_fade,input\n");
        }
        void step(String phase, int direction, boolean jump) throws Exception {
            var buttons = new ArrayList<String>();
            if ((direction & AbstractPlayableSprite.INPUT_UP) != 0) buttons.add("U");
            if ((direction & AbstractPlayableSprite.INPUT_DOWN) != 0) buttons.add("D");
            if ((direction & AbstractPlayableSprite.INPUT_LEFT) != 0) buttons.add("L");
            if ((direction & AbstractPlayableSprite.INPUT_RIGHT) != 0) buttons.add("R");
            if (jump) buttons.add("A");
            String held = buttons.isEmpty() ? "-" : String.join("+", buttons);
            input.add(held);
            session.step(new Bk2FrameInput(frame, direction | (jump ? AbstractPlayableSprite.INPUT_JUMP : 0),
                    jump ? InputActionMasks.ACTION_A : 0, false, held));
            session.renderFrame(); frame++; record(phase, held);
        }
        void record(String phase, String held) throws Exception {
            var p = player(); var level = GameServices.level(); var ui = GameServices.graphics().getUiRenderPipeline();
            csv.write(frame + "," + phase + "," + session.loop().getCurrentGameMode() + "," + level.getCurrentAct()
                    + "," + level.getApparentAct() + "," + level.getFrameCounter() + "," + level.getObjectManager().getVblaCounter()
                    + "," + System.identityHashCode(level.getObjectManager()) + "," + p.getCentreX() + "," + p.getCentreY()
                    + "," + p.getXSpeed() + "," + p.getYSpeed() + "," + p.getGSpeed() + "," + p.getAir()
                    + "," + p.getRolling() + "," + p.getSpindash() + "," + p.getDead()
                    + "," + p.getRingCount() + "," + level.getLevelGamestate().getRings() + "," + GameServices.gameState().getLives()
                    + "," + checkpoint().getLastCheckpointIndex() + "," + checkpoint().getStarPostActivationMark()
                    + "," + checkpoint().isUsedForSpecialStage() + "," + GameServices.camera().getX() + "," + GameServices.camera().getY()
                    + "," + GameServices.fade().isActive() + "," + (ui != null && ui.isFadeActive()) + "," + held + "\n");
            csv.flush();
        }
        void picture(String label) throws Exception {
            ScreenshotCapture.savePNG(session.render(), output.resolve(String.format("%05d-%s.png", frame, label)));
        }
        @Override public void close() throws Exception {
            csv.close();
            if (input.isEmpty()) return;
            var script = new StringBuilder("# Positioned true-act2 post697; native pad-only death/reload, not cold/forest-fire/durable-save proof.\n")
                    .append("# Candidate SHA256 ").append(CANDIDATE_SHA256).append("\n# ROM SHA1 ").append(ROM_SHA1).append('\n');
            for (int start = 0; start < input.size();) {
                int end = start + 1;
                while (end < input.size() && input.get(start).equals(input.get(end))) end++;
                script.append(end - start).append(' ').append(input.get(start)).append('\n'); start = end;
            }
            Files.writeString(output.resolve("ordinary-input.script"), script);
            InputLogAuthorTool.author(script.toString(), output.resolve("ordinary-input.bk2"), "Sonic 3 & Knuckles");
        }
    }
}
