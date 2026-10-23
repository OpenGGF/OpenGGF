package com.openggf.mods.code;

import com.openggf.control.InputActionMasks;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.CheckpointState;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.save.SavePaths;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.game.timing.HardwareWorkSubmission;
import com.openggf.game.sonic3k.objects.Sonic3kSpikeObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostBonusStarChild;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostObjectInstance;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.InputLogAuthorTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
    private static final String STOCK_POST_CONTROL_SHA256 = "1b8cb08073173e6be4237bf99b166e1a13610671273dcba9ea4ad2f7e6c9c3da";
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

    @Test void nativePost476EarnedInvitationDeathReloadAndForwardEscape() throws Exception {
        nativeRemainingPostsDeathReloadAndForwardEscape(476);
    }

    @Test
    @EnabledIfSystemProperty(named = "secondstrike.checkpoint.control.jar", matches = ".+")
    void nativePost476MatchedStockPostDeathReloadControl() throws Exception {
        nativeRemainingPostsDeathReloadAndForwardEscape(476, true);
    }

    /** Independently selectable ROM placements; deliberately excludes697. */
    @ParameterizedTest
    @ValueSource(ints = {26})
    void nativeRemainingPostsDeathReloadAndForwardEscape(int layoutIndex) throws Exception {
        nativeRemainingPostsDeathReloadAndForwardEscape(layoutIndex, false);
    }

    private void nativeRemainingPostsDeathReloadAndForwardEscape(int layoutIndex, boolean stockPosts) throws Exception {
        boolean continuation = layoutIndex == 26;
        int postX = continuation ? 0x2E0 : 0x2A80, postY = continuation ? 0x2BD : 0x57A;
        int checkpointIndex = continuation ? 3 : 5, apparentAct = continuation ? 0 : 1;
        int hazardIndex = continuation ? 29 : 494, hazardX = continuation ? 0x340 : 0x2BD2;
        int hazardY = continuation ? 0x280 : 0x5C1, hazardSubtype = continuation ? 1 : 6;
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        Path rom = Path.of(System.getProperty("s3k.rom.path")).toAbsolutePath();
        Path jar = Path.of(System.getProperty(stockPosts
                ? "secondstrike.checkpoint.control.jar" : "secondstrike.checkpoint.jar")).toAbsolutePath();
        if (stockPosts) {
            assertEquals(ROM_SHA1, digest(rom, "SHA-1")); assertEquals(STOCK_POST_CONTROL_SHA256, digest(jar, "SHA-256"));
        } else {
            assertEquals(ROM_SHA1, digest(rom, "SHA-1")); assertEquals(CANDIDATE_SHA256, digest(jar, "SHA-256"));
        }
        Path output = Path.of(System.getProperty("secondstrike.checkpoint.output", temporary.toString()))
                .resolve("post" + layoutIndex).toAbsolutePath();
        Files.createDirectories(output);
        assertFalse(Files.exists(output.resolve("state.csv")), "preserve prior witnesses");
        String oldDirectory = System.getProperty("user.dir"), oldSaveRoot = System.getProperty(SavePaths.ROOT_PROPERTY);
        System.setProperty("user.dir", Files.createDirectories(output.resolve("preferences")).toString());
        System.setProperty(SavePaths.ROOT_PROPERTY, Files.createDirectories(output.resolve("saves")).toString());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, postX - 48, postY);
        try (var session = new GameplayCaptureSession(settings); var driver = new Driver(session, output)) {
            session.applyMod(jar); session.boot(rom, 0, 1, settings);
            // Declared initial physical-fire continuation identity, not a claim
            // of having executed the forest reload. No post-restart identity write.
            if (continuation) GameServices.level().setApparentAct(0);
            driver.record("declared-bank1-apparent" + apparentAct + "-post" + layoutIndex, "-");
            revealScene(driver); requireNativeFloor(driver);
            assertEquals(apparentAct, GameServices.level().getApparentAct());
            assertFalse(checkpoint().isActive()); assertEquals(0, player().getRingCount());
            assertEquals(0, GameServices.level().getLevelGamestate().getRings());
            var originalPost = GameServices.level().getCurrentLevel().getObjects().stream()
                    .filter(spawn -> spawn.layoutIndex() == layoutIndex).findFirst().orElseThrow();
            assertEquals(0x34, originalPost.objectId()); assertEquals(postX, originalPost.x());
            assertEquals(postY, originalPost.y()); assertEquals(checkpointIndex, originalPost.subtype());
            assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostObjectInstance.class)
                    .stream().anyMatch(post -> post.getSpawn().layoutIndex() == layoutIndex));
            var originalHazard = GameServices.level().getCurrentLevel().getObjects().stream()
                    .filter(spawn -> spawn.layoutIndex() == hazardIndex).findFirst().orElseThrow();
            assertEquals(continuation ? 0x08 : 0x01, originalHazard.objectId()); assertEquals(hazardX, originalHazard.x());
            assertEquals(hazardY, originalHazard.y()); assertEquals(hazardSubtype, originalHazard.subtype());
            Sonic3kSpikeObjectInstance ceilingSpike = null;
            if (continuation) {
                assertEquals(2, originalHazard.renderFlags(), "original29 is a Y-flipped ceiling spike");
                ceilingSpike = GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kSpikeObjectInstance.class)
                        .stream().filter(spike -> spike.getX() == hazardX && Math.abs(spike.getY() - hazardY) <= 32)
                        .findFirst().orElseThrow();
            }
            driver.picture("positive-native-floor");
            int lives = GameServices.gameState().getLives(); assertTrue(lives > 1);
            for (int tick = 0; tick < 240 && checkpoint().getLastCheckpointIndex() != checkpointIndex; tick++)
                driver.step("ordinary-post" + layoutIndex + "-contact", AbstractPlayableSprite.INPUT_RIGHT,
                        continuation && tick % 90 < 28);
            assertEquals(checkpointIndex, checkpoint().getLastCheckpointIndex());
            assertEquals(checkpointIndex, checkpoint().getStarPostActivationMark());
            assertEquals(postX, checkpoint().getSavedX()); assertEquals(postY, checkpoint().getSavedY());
            assertEquals(0, player().getRingCount()); assertFalse(checkpoint().isUsedForSpecialStage());
            if (stockPosts) {
                assertEquals(0, invitationStars(), "stock zero-ring post must follow its native ring threshold");
                var stockPost = GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostObjectInstance.class)
                        .stream().filter(post -> post.getSpawn().layoutIndex() == layoutIndex).findFirst().orElseThrow();
                assertNull(nativeValue(stockPost, "invitationVariant"), "control must use the actual native stock post");
            } else {
                assertEquals(continuation ? 0 : 4, invitationStars(), "only selected476 earns four stars at zero rings");
            }
            assertEquals(BonusStageType.NONE, GameServices.bonusStage().getActiveType());
            assertTrue(checkpoint().hasS3kRuntimeState());
            int savedMaxY = checkpoint().getSavedCameraMaxY(), savedResize = checkpoint().getSavedDynamicResizeRoutine();
            driver.record("native-saved-maxY" + savedMaxY + "-resize" + savedResize, "-");
            driver.picture("earned-checkpoint-before-entry");
            var beforeDeathManager = GameServices.level().getObjectManager();
            int heldJumpAge = -1, ledgeJumpAge = -1;
            boolean ledgeCleared = continuation;
            Sonic3kMonitorObjectInstance eggman = null;
            boolean eggmanCommittedDeath = false, eggmanVisible = false;
            int visibleMonitorFrames = 0;
            for (int tick = 0; tick < 600 && !player().getDead(); tick++) {
                if (!continuation && eggman == null) {
                    eggman = GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kMonitorObjectInstance.class)
                            .stream().filter(box -> box.getSpawn().layoutIndex() == hazardIndex).findFirst().orElse(null);
                    if (eggman != null) {
                        assertEquals("EGGMAN", nativeValue(eggman, "type").toString(), "verify the actual1f6 factory override, not raw subtype6");
                        assertEquals(false, nativeValue(eggman, "encounterRingDisguise"), "ordinary box must retain its truthful native icon");
                        var renderer = GameServices.level().getObjectManager().getObjectServices().renderManager().getMonitorRenderer();
                        assertNotNull(renderer); assertTrue(renderer.isReady(), "actual ROM monitor art required before native damage attribution");
                        driver.record("original494-actual-native-EGGMAN-ROM-art-ready", "-");
                    }
                }
                int targetX = continuation ? ceilingSpike.getX() : eggman == null ? hazardX : eggman.getX();
                // The actual ROM ledge at2AC0 precedes monitor494. R2's
                // walk reached10933 and never reached the box jump trigger.
                if (!continuation && ledgeJumpAge < 0 && !player().getAir()
                        && player().getCentreX() >= postX + 32) {
                    waitForNativeWaterToClearTakeoff(driver); ledgeJumpAge = 0;
                }
                boolean ledgeJump = ledgeJumpAge >= 0 && ledgeJumpAge < 28;
                if (ledgeJumpAge >= 0) ledgeJumpAge++;
                if (!continuation && ledgeJumpAge > 28 && !player().getAir()) ledgeCleared = true;
                if (heldJumpAge < 0 && !player().getAir() && (continuation
                        ? player().getCentreX() >= postX + 16
                        : ledgeCleared && eggmanVisible && Math.abs(player().getCentreX() - targetX) <= 96
                                && Math.abs(player().getCentreY() - eggman.getY()) <= 64))
                    heldJumpAge = 0;
                boolean jump = ledgeJump || heldJumpAge >= 0 && heldJumpAge < 28;
                if (heldJumpAge >= 0) heldJumpAge++;
                boolean broken = eggman != null && (boolean) nativeValue(eggman, "broken");
                if (eggman != null && !broken && !eggmanVisible) {
                    int screenX = eggman.getX() - GameServices.camera().getX();
                    int screenY = eggman.getY() - GameServices.camera().getY();
                    // Native type0 uses ROM Map_Monitor frame1 for Eggman.
                    // Require consecutive real frames so the published SAT has
                    // the same icon, rather than the native static-TV frame0.
                    if (screenX >= 16 && screenX < 304 && screenY >= 16 && screenY < 208
                            && (int) nativeValue(eggman, "mappingFrame") == 1) visibleMonitorFrames++;
                    else visibleMonitorFrames = 0;
                    if (visibleMonitorFrames >= 2) {
                        driver.picture("native-EGGMAN-truthful-icon-visible"); eggmanVisible = true;
                    }
                }
                boolean effectBefore = eggman != null && eggmanContentsApplied(eggman);
                driver.step("ordinary-original-hazard" + hazardIndex, broken ? 0 : steer(targetX), !broken && jump);
                if (eggman != null && !effectBefore && eggmanContentsApplied(eggman) && player().getDead())
                    eggmanCommittedDeath = true;
                assertEquals(BonusStageType.NONE, GameServices.bonusStage().getActiveType(), "do not enter the earned invitation");
                assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            }
            if (eggman != null) driver.record("actual-monitor494-broken" + nativeValue(eggman, "broken")
                    + "-effectApplied" + eggmanContentsApplied(eggman), "-");
            assertTrue(player().getDead(), "ordinary original-hazard approach must cause death; inspect CSV");
            if (continuation) {
                assertTrue(Math.abs(player().getCentreX() - ceilingSpike.getX()) <= 64);
                assertTrue(Math.abs(player().getCentreY() - ceilingSpike.getY()) <= 64, "compare actual native moving ceiling spike");
                driver.record("native-ceiling-spike-death-at" + ceilingSpike.getX() + "-" + ceilingSpike.getY(), "-");
            } else {
                assertNotNull(eggman); assertEquals("EGGMAN", nativeValue(eggman, "type").toString());
                assertEquals(true, nativeValue(eggman, "broken"));
                assertEquals(true, eggmanContentsApplied(eggman), "the actual native monitor payload must have caused the death");
                assertTrue(eggmanVisible, "actual readable ROM monitor must precede its damage");
                assertTrue(eggmanCommittedDeath, "fail if death precedes the native monitor payload commit");
                driver.record("native-EGGMAN-effectApplied-false-to-true-death", "-");
            }
            driver.picture("native-hazard-death-before-entry");
            boolean restored = false;
            for (int tick = 0; tick < 900; tick++) {
                if (!continuation) observeReloadQueues(driver, "before-reload-step");
                try {
                    driver.step("native-full-death-reload", 0, false);
                } catch (RuntimeException | Error original) {
                    // The native fault boundary has already run. Observe its
                    // surviving runtime before Driver/session close, then
                    // preserve the exact original failure and assertion path.
                    if (!continuation) {
                        try { observeReloadQueues(driver, "reload-error-" + original.getClass().getName()); }
                        catch (Exception | Error observerFailure) { original.addSuppressed(observerFailure); }
                    }
                    throw original;
                }
                assertEquals(BonusStageType.NONE, GameServices.bonusStage().getActiveType());
                if (GameServices.level().getObjectManager() != beforeDeathManager && !player().getDead()
                        && player().getCentreX() == postX && player().getCentreY() == postY) {
                    restored = true; break;
                }
            }
            assertTrue(restored, "ordinary native full reload must restore the earned original checkpoint");
            assertEquals(lives - 1, GameServices.gameState().getLives());
            assertEquals(0, player().getRingCount()); assertEquals(0, GameServices.level().getLevelGamestate().getRings());
            assertEquals(0, player().getXSpeed()); assertEquals(0, player().getYSpeed()); assertEquals(0, player().getGSpeed());
            assertEquals(apparentAct, GameServices.level().getApparentAct(), "native reload owns continuation identity");
            assertEquals(checkpointIndex, checkpoint().getLastCheckpointIndex());
            assertEquals(checkpointIndex, checkpoint().getStarPostActivationMark()); assertFalse(checkpoint().isUsedForSpecialStage());
            assertEquals(savedMaxY, checkpoint().getSavedCameraMaxY()); assertEquals(savedResize, checkpoint().getSavedDynamicResizeRoutine());
            assertEquals(savedMaxY, GameServices.camera().getMaxY());
            var events = assertInstanceOf(Sonic3kLevelEventManager.class, GameServices.module().getLevelEventProvider());
            assertEquals(savedResize, events.getDynamicResizeRoutine(), "actual native event owner must restore its saved routine");
            driver.picture("native-stopped-zero-ring-restore");
            revealScene(driver); requireNativeFloor(driver);
            assertEquals(0, invitationStars(), "native activation mark suppresses invitation recreation after death");
            if (continuation) {
                // Native rolling radii fit below the actual Y-flipped ceiling
                // spikes; no physics or collision dimensions are changed here.
                for (int tick = 0; tick < 2; tick++) driver.step("ordinary-face-right", AbstractPlayableSprite.INPUT_RIGHT, false);
                for (int tick = 0; tick < 8; tick++) driver.step("ordinary-stop-before-charge", 0, false);
                for (int tick = 0; tick < 3; tick++) driver.step("ordinary-duck", AbstractPlayableSprite.INPUT_DOWN, false);
                for (int pulse = 0; pulse < 8; pulse++) {
                    driver.step("ordinary-spindash-charge", AbstractPlayableSprite.INPUT_DOWN, true);
                    driver.step("ordinary-spindash-charge", AbstractPlayableSprite.INPUT_DOWN, false);
                }
                driver.step("ordinary-low-ceiling-spindash-release", AbstractPlayableSprite.INPUT_RIGHT, false);
                assertTrue(player().getRolling()); assertTrue(player().getGSpeed() >= 0x480);
            }
            int escapeJumpAge = -1;
            for (int tick = 0; tick < 600 && player().getCentreX() < postX + 160; tick++) {
                //26 rolls below its ceiling.476 jumps the unchanged ROM ledge.
                if (!continuation && escapeJumpAge < 0 && !player().getAir()
                        && player().getCentreX() >= postX + 32) {
                    waitForNativeWaterToClearTakeoff(driver); escapeJumpAge = 0;
                }
                boolean escapeJump = escapeJumpAge >= 0 && escapeJumpAge < 28;
                if (escapeJumpAge >= 0) escapeJumpAge++;
                driver.step("ordinary-zero-ring-forward-escape", AbstractPlayableSprite.INPUT_RIGHT, escapeJump);
                assertFalse(player().getDead()); assertNoBonus();
            }
            assertTrue(player().getCentreX() >= postX + 160, "ordinary escape must leave the restored checkpoint forward");
            assertEquals(lives - 1, GameServices.gameState().getLives()); assertEquals(apparentAct, GameServices.level().getApparentAct());
            assertEquals(1, GameServices.level().getCurrentAct()); assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            driver.picture("ordinary-forward-escape");
            var save = SessionManager.getCurrentWorldSession().getSaveSessionContext();
            assertTrue(save == null || save.activeSlot().isEmpty(), "No Save, not durable-slot progression");
        } finally {
            restore("user.dir", oldDirectory); restore(SavePaths.ROOT_PROPERTY, oldSaveRoot);
            // Existing697 Driver stays byte-identical. Correct only this new
            // case's emitted metadata; the actual pad rows remain unchanged.
            Path script = output.resolve("ordinary-input.script");
            if (Files.exists(script)) {
                String pads = Files.readString(script).replace(
                        "# Positioned true-act2 post697; native pad-only death/reload, not cold/forest-fire/durable-save proof.",
                        "# Positioned bank1/apparent" + apparentAct + " post" + layoutIndex
                                + "; native pad-only checkpoint death/reload, not cold/forest-fire/durable-save proof.");
                Files.writeString(script, pads);
                InputLogAuthorTool.author(pads, output.resolve("ordinary-input.bk2"), "Sonic 3 & Knuckles");
            }
        }
    }

    /**
     * Observer-only FIFO metadata for the post476 reload failure, task2026-10-15.
     * Reads scalar descriptors and pure handles, never preparation snapshots,
     * payload bytes, queue service/claims, clocks or gameplay state setters.
     */
    private static void observeReloadQueues(Driver driver, String phase) throws Exception {
        var level = GameServices.level();
        var timing = GameServices.hardwareTiming();
        var manager = level.getObjectManager();
        var coordinator = (S3kRuntimeArtCoordinator) GameServices.runtimeArtCoordinator();
        var provider = (Sonic3kObjectArtProvider) manager.getObjectServices().renderManager().getArtProvider();
        var cp = checkpoint();
        var p = player();
        var lines = new StringBuilder();
        Path path = driver.output.resolve("reload-queues.csv");
        if (!Files.exists(path)) lines.append("frame,phase,observation,value\n");
        queueObservation(lines, driver.frame, phase, "context", "level=" + System.identityHashCode(level)
                + " manager=" + System.identityHashCode(manager) + " player=" + System.identityHashCode(p)
                + " checkpoint=" + System.identityHashCode(cp) + " timing=" + System.identityHashCode(timing)
                + " coordinator=" + System.identityHashCode(coordinator) + " provider=" + System.identityHashCode(provider)
                + " moduleQueue=" + System.identityHashCode(coordinator.moduleQueue())
                + " providerQueue=" + System.identityHashCode(nativeValue(provider, "enemyKosQueue"))
                + " mode=" + driver.session.loop().getCurrentGameMode()
                + " act=" + level.getCurrentAct() + " apparent=" + level.getApparentAct()
                + " levelFrame=" + level.getFrameCounter() + " objectVInt=" + manager.getVblaCounter()
                + " initialPass=" + level.capturePendingInitialProcessSpritesLifecycleForRewind()
                + " initialPending=" + level.hasPendingInitialProcessSpritesPass()
                + " boundary=" + nativeValue(timing, "lastServicedBoundary")
                + " index=" + cp.getLastCheckpointIndex() + " mark=" + cp.getStarPostActivationMark()
                + " used=" + cp.isUsedForSpecialStage() + " savedX=" + cp.getSavedX() + " savedY=" + cp.getSavedY()
                + " x=" + p.getCentreX() + " y=" + p.getCentreY() + " rings=" + p.getRingCount()
                + " lives=" + GameServices.gameState().getLives() + " dead=" + p.getDead()
                + " fade=" + GameServices.fade().isActive());
        var handles = timing.pendingHandles();
        int physicalModules = 0;
        // Public timing capture includes ROM-bearing preparation snapshots.
        // Instead read only descriptors of the actual unclaimed handles.
        for (Object job : (List<?>) nativeValue(timing, "jobs")) {
            var handle = (HardwareWorkHandle) nativeValue(job, "handle");
            if (!handles.contains(handle)) continue;
            var descriptor = (HardwareWorkSubmission) nativeValue(job, "submission");
            boolean prepared = descriptor.preparation().isPrepared();
            boolean physical = handle.kind() == HardwareWorkKind.KOS_MODULE_QUEUE && !prepared;
            if (physical) physicalModules++;
            queueObservation(lines, driver.frame, phase, "pending-work", "kind=" + handle.kind()
                    + " ordinal=" + handle.ordinal() + " source=" + hex(descriptor.romSourceAddress())
                    + " destinationBytes=" + hex(descriptor.destinationAddress())
                    + " compressedLength=" + descriptor.compressedLength()
                    + " destinationLength=" + descriptor.destinationLength() + " moduleCount=" + descriptor.moduleCount()
                    + " preparationPrepared=" + prepared + " physicalModule=" + physical
                    + " ready=" + nativeValue(job, "ready") + " retired=" + nativeValue(job, "physicallyRetired"));
        }
        var direct = coordinator.directQueue().capture();
        queueObservation(lines, driver.frame, phase, "occupancy", "pendingHandles=" + handles.size()
                + " modulePhysical=" + physicalModules + " moduleCapacity=4"
                + " directPhysical=" + coordinator.directQueue().physicalQueueSize()
                + " directDeferredHeadVisibility=" + direct.deferNewHeadPreparationVisibility());
        for (var entry : direct.entries()) {
            queueObservation(lines, driver.frame, phase, "direct-entry", "kind=" + entry.handle().kind()
                    + " ordinal=" + entry.handle().ordinal() + " source=" + hex(entry.descriptor().sourceAddress())
                    + " destinationBytes=" + hex(entry.descriptor().destinationAddress())
                    + " physical=" + entry.physical() + " prepared=" + entry.prepared());
        }
        for (var diagnostic : coordinator.captureQueueDiagnostics()) {
            queueObservation(lines, driver.frame, phase, "queue-diagnostic", "kind=" + diagnostic.kind()
                    + " busy=" + diagnostic.busy() + " prepared=" + diagnostic.prepared()
                    + " activeSource=" + hex(diagnostic.activeSource())
                    + " activeDestination=" + hex(diagnostic.activeDestination())
                    + " total=" + diagnostic.activeTotalWork() + " remaining=" + diagnostic.activeRemainingWork()
                    + " waitingFingerprints=" + diagnostic.queuedFingerprints());
        }
        var art = provider.capture();
        queueObservation(lines, driver.frame, phase, "provider", "loadEpoch=" + art.loadEpoch()
                + " runtimeState=" + art.runtimeState() + " submissionArmed=" + art.kosSubmissionArmed()
                + " titleTeardownTicks=" + art.titleCardTeardownTicks()
                + " admissionOwner=" + art.runtimeArtAdmissionOwnerKind()
                + " admissionBound=" + art.runtimeArtAdmissionBound() + " admissionConsumed=" + art.runtimeArtAdmissionConsumed()
                + " handles=" + nativeValue(provider, "enemyKosHandles"));
        for (var entry : art.pendingKosModules()) {
            queueObservation(lines, driver.frame, phase, "provider-pending", "source=" + hex(entry.sourceAddress())
                    + " destinationTile=" + hex(entry.destinationTile()) + " destinationBytes=" + hex(entry.destinationTile() * 32));
        }
        var fresh = coordinator.capture();
        queueObservation(lines, driver.frame, phase, "fresh-level", "primarySource=" + hex(fresh.deferredPrimarySource())
                + " secondarySource=" + hex(fresh.deferredSecondarySource()) + " handles=" + fresh.freshLevelHandoffHandles());
        for (var post : manager.activeObjectsOfType(Sonic3kStarPostObjectInstance.class)) {
            queueObservation(lines, driver.frame, phase, "star-post", "identity=" + System.identityHashCode(post)
                    + " slot=" + post.getSlotIndex() + " layout=" + post.getSpawn().layoutIndex()
                    + " x=" + post.getSpawn().x() + " y=" + post.getSpawn().y() + " subtype=" + post.getSpawn().subtype()
                    + " initialized=" + nativeValue(post, "initialized") + " activated=" + nativeValue(post, "activated")
                    + " invitation=" + nativeValue(post, "invitationVariant"));
        }
        Files.writeString(path, lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String hex(int value) { return value < 0 ? Integer.toString(value) : "0x" + Integer.toHexString(value); }

    private static void queueObservation(StringBuilder lines, int frame, String phase, String observation, String value) {
        lines.append(frame).append(',').append(phase).append(',').append(observation).append(',')
                .append('"').append(value.replace("\"", "\"\"")).append('"').append('\n');
    }

    /**
     * A positioned boot starts the native AIZ2 water fall before this ledge.
     * Sonic_Jump's normal +5 roll-radius shift can cross that receding surface,
     * invoking Sonic_Water's native half-X/quarter-Y entry. Wait with neutral
     * pads for the actual surface to clear the prospective native takeoff;
     * never change water, radii, jump strength or any producer state.
     */
    private static void waitForNativeWaterToClearTakeoff(Driver driver) throws Exception {
        for (int tick = 0; tick < 180; tick++) {
            var p = player();
            int waterY = GameServices.water().getGameplayWaterLevelY(0, 1);
            int takeoffY = p.getCentreY() + (p.getRolling() ? 0 : p.getStandYRadius() - p.getRollYRadius());
            driver.record("actual-water" + waterY + "-takeoff" + takeoffY
                    + "-underwater" + p.isInWater() + "-jump" + p.getJump(), "-");
            if (waterY >= takeoffY) return;
            driver.step("ordinary-neutral-native-water-recedes", 0, false);
            assertFalse(player().getDead());
            assertEquals(BonusStageType.NONE, GameServices.bonusStage().getActiveType());
            assertEquals(GameMode.LEVEL, driver.session.loop().getCurrentGameMode());
        }
        fail("native receding water must clear the actual jump takeoff without state writes");
    }

    private static int invitationStars() {
        return GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostBonusStarChild.class).size();
    }

    /**
     * Obj_MonitorContents owns the payload as an independent object
     * (docs/skdisasm/sonic3k.asm:40645-40657); observe the contents that the
     * actual shell allocated at its own x, never the shell's inherited state.
     */
    private static boolean eggmanContentsApplied(Sonic3kMonitorObjectInstance shell) throws Exception {
        for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (object.getClass().getName().endsWith("$MonitorContentsSlot") && object.getX() == shell.getX()
                    && "EGGMAN".equals(nativeValue(object, "type").toString())
                    && (boolean) nativeValue(object, "effectApplied")) return true;
        }
        return false;
    }

    /** Observe the actual native producer; never edit its type, effect or timers. */
    private static Object nativeValue(Object owner, String fieldName) throws Exception {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(fieldName); field.setAccessible(true); return field.get(owner);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(fieldName);
    }

    /** Read the production ROM floor sensor; never move the player to fit it. */
    private static void requireNativeFloor(Driver driver) throws Exception {
        for (int tick = 0; tick < 180; tick++) {
            var p = player();
            for (int x : new int[]{-9, 9}) {
                var hit = new GroundSensor(p, Direction.DOWN, (byte) x, (byte) p.getYRadius(), true).scan();
                if (!p.getAir() && hit != null && hit.tileId() != 0 && Math.abs(hit.distance()) <= 4) {
                    driver.record("positive-ROM-floor-tile" + hit.tileId() + "-distance" + hit.distance(), "-");
                    return;
                }
            }
            driver.step("ordinary-native-floor-settle", 0, false);
            assertFalse(player().getDead(), "the native floor prerequisite must survive without forcing bounds or positions");
        }
        fail("actual native ROM floor is required before checkpoint mechanics");
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
