package com.openggf.mods.code;

import com.openggf.control.InputActionMasks;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.*;
import com.openggf.game.save.SavePaths;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostBonusStarChild;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostObjectInstance;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.InputLogAuthorTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Positioned native invitation gameplay witness, originating in the 2026-10-15
 * Second Strike AIZ M3 task. Opt in with an explicitly strict-built candidate
 * (-Dsecondstrike.invitation.jar); never compile a substitute mod in this test.
 * Optional secondstrike.invitation.output keeps CSV, ordinary input and PNGs.
 * No producer calls, state hydration, reward edits or debug completion are used.
 */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "secondstrike.invitation.jar", matches = ".+")
class TestSecondStrikeInvitationGameplay {
    private static final String CANDIDATE_SHA256 = "1f6bfedd83b5d711c74fc73f01889838680d631c54b93bc308dd9d4ccbfcfef9";
    private static final String ROM_SHA1 = "cfbf98c36c776677290a872547ac47c53d2761d6";
    private static final int POST_X = 0x2778, POST_Y = 0x25c, POST_LAYOUT = 102;
    @TempDir Path temporary;

    @Test void zeroRingInvitationPlaysNativeGumballAndReturnsExactlyOnce() throws Exception {
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY"), "the witness must not connect to the shared desktop");
        assertNull(System.getenv("WAYLAND_DISPLAY"));
        Path rom = Path.of(System.getProperty("s3k.rom.path")).toAbsolutePath();
        Path jar = Path.of(System.getProperty("secondstrike.invitation.jar")).toAbsolutePath();
        assertEquals(ROM_SHA1, digest(rom, "SHA-1"));
        assertEquals(CANDIDATE_SHA256, digest(jar, "SHA-256"));
        Path output = Path.of(System.getProperty("secondstrike.invitation.output",
                temporary.resolve("positioned-gumball").toString())).toAbsolutePath();
        Files.createDirectories(output);
        assertFalse(Files.exists(output.resolve("state.csv")), "preserve earlier capture runs");
        Path saves = Files.createDirectories(output.resolve("saves"));
        Path preferences = Files.createDirectories(output.resolve("preferences"));
        String oldDirectory = System.getProperty("user.dir"), oldSaveRoot = System.getProperty(SavePaths.ROOT_PROPERTY);
        System.setProperty("user.dir", preferences.toString());
        System.setProperty(SavePaths.ROOT_PROPERTY, saves.toString());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null,
                POST_X - 48, POST_Y);
        try (var session = new GameplayCaptureSession(settings);
             var driver = new Driver(session, output)) {
            session.applyMod(jar);
            session.boot(rom, 0, 0, settings);
            assertEquals(0, player().getRingCount(), "fresh positioned entry has no donated rings");
            assertEquals(0, GameServices.level().getLevelGamestate().getRings());
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertEquals(320, GameServices.camera().getWidth());
            var entryPost = posts().stream().filter(post -> post.getSpawn().layoutIndex() == POST_LAYOUT)
                    .findFirst().orElseThrow(() -> new AssertionError("original native post102 is not admitted"));
            assertEquals(POST_X, entryPost.getSpawn().x()); assertEquals(POST_Y, entryPost.getSpawn().y());
            driver.record("positioned-start", "-"); driver.picture("positioned-start");
            int activationFrame = -1, enabledFrame = -1;
            for (int frame = 0; frame < 760 && session.loop().getCurrentGameMode() == GameMode.LEVEL; frame++) {
                var checkpoint = checkpoint();
                if (activationFrame < 0 && checkpoint.getStarPostActivationMark() >= 2) {
                    activationFrame = driver.frame;
                    assertEquals(2, checkpoint.getLastCheckpointIndex());
                    assertEquals(0, player().getRingCount(), "ordinary post contact earned an invitation at zero rings");
                    driver.picture("zero-ring-invitation");
                }
                int age = starAge();
                if (age >= 128 && enabledFrame < 0) {
                    enabledFrame = driver.frame;
                    driver.picture("collision-enabled-stars");
                }
                boolean jump = age >= 128 && !player().getAir() && (driver.frame & 1) == 0;
                driver.step("native-post", steer(POST_X), jump);
            }
            assertTrue(activationFrame >= 0, "ordinary walking must activate native post102; inspect state.csv");
            assertTrue(enabledFrame >= 0, "native stars must reach their128-update collision lifetime");
            for (int tick = 0; tick < 360 && session.loop().getCurrentGameMode() != GameMode.BONUS_STAGE; tick++)
                driver.step("entry-transition", 0, false);
            assertEquals(GameMode.BONUS_STAGE, session.loop().getCurrentGameMode(), "a void request is not proof of actual bonus entry");
            BonusStageProvider provider = GameServices.bonusStage();
            assertEquals(BonusStageType.GUMBALL, provider.getActiveType());
            BonusStageState saved = provider.getSavedState();
            assertNotNull(saved); assertEquals(0, saved.savedRingCount());
            assertEquals(0, saved.savedZoneAndAct()); assertEquals(0, saved.savedApparentZoneAndAct());
            assertEquals(POST_X, saved.playerX()); assertEquals(POST_Y, saved.playerY());
            int entryLives = GameServices.gameState().getLives();
            ShieldType entryShield = player().getShieldType();
            boolean entryHadShield = player().hasShield();
            int interiorExitRings = -1;
            int interiorExitLives = -1, interiorExitScore = -1;
            ShieldType interiorExitShield = null;
            BonusStageProvider.BonusStageRewards exitRewards = null;
            boolean prizePicture = false;
            for (int played = 0; played < 2600 && session.loop().getCurrentGameMode() == GameMode.BONUS_STAGE; played++) {
                int direction = played < 1400 ? steer(256 + ((played / 120 & 1) == 0 ? -24 : 24))
                        : AbstractPlayableSprite.INPUT_RIGHT;
                driver.step("played-gumball", direction, !player().getAir() && (played & 1) == 0);
                if (played == 30) driver.picture("native-gumball-active");
                var rewards = provider.getRewards();
                boolean newlyGrantedShield = player().hasShield()
                        && (!entryHadShield || player().getShieldType() != entryShield);
                if (!prizePicture && (GameServices.gameState().getLives() > entryLives
                        || rewards.rings() > 0 || rewards.lives() > 0 || newlyGrantedShield)) {
                    prizePicture = true; driver.picture("native-prize");
                }
                if (provider.isStageComplete()) {
                    interiorExitRings = GameServices.level().getLevelGamestate().getRings();
                    interiorExitLives = GameServices.gameState().getLives();
                    interiorExitScore = GameServices.gameState().getScore();
                    interiorExitShield = player().getShieldType();
                    exitRewards = provider.getRewards();
                }
            }
            assertNotNull(exitRewards, "ordinary bonus inputs must reach the native exit trigger; inspect state.csv");
            assertTrue(prizePicture, "ordinary bonus play must earn a positive native award before exit");
            for (int tick = 0; tick < 480 && session.loop().getCurrentGameMode() != GameMode.LEVEL; tick++)
                driver.step("native-return", 0, false);
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertEquals(0, GameServices.level().getCurrentZone()); assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals(0, GameServices.level().getApparentAct());
            assertEquals(saved.savedLastStarPostHit(), checkpoint().getLastCheckpointIndex(), "native bonus entry clears Last_star_post_hit");
            assertEquals(2, checkpoint().getStarPostActivationMark(), "persistent native used mark survives the return reload");
            assertEquals(interiorExitRings, GameServices.level().getLevelGamestate().getRings(), "return uses live interior rings, not entry plus bookkeeping rewards");
            assertEquals(interiorExitLives + exitRewards.lives(), GameServices.gameState().getLives(),
                    "native direct1-up awards must not be added a second time on return");
            assertEquals(interiorExitScore, GameServices.gameState().getScore());
            assertEquals(interiorExitShield, player().getShieldType());
            assertEquals(BonusStageType.NONE, provider.getActiveType());
            assertEquals(BonusStageType.NONE, GameServices.bonusStage().getActiveType());
            assertTrue(stars().isEmpty(), "entry stars retire rather than recreating the opportunity");
            int rings = player().getRingCount(), lives = GameServices.gameState().getLives(), score = GameServices.gameState().getScore();
            ShieldType shield = player().getShieldType();
            for (int tick = 0; tick < 150; tick++) {
                driver.step("return-stability", 0, false);
                assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
                assertEquals(rings, player().getRingCount()); assertEquals(lives, GameServices.gameState().getLives());
                assertEquals(score, GameServices.gameState().getScore()); assertEquals(shield, player().getShieldType());
                assertTrue(stars().isEmpty());
            }
            driver.picture("native-return-stable");
            var saveSession = SessionManager.getCurrentWorldSession().getSaveSessionContext();
            assertTrue(saveSession == null || saveSession.activeSlot().isEmpty(), "positioned capture is No Save; durable-slot progression is separate");
        } finally {
            restore("user.dir", oldDirectory); restore(SavePaths.ROOT_PROPERTY, oldSaveRoot);
        }
    }

    @Test void zeroRingActTwoInvitationPlaysNativeGlowingSpheresAndReturnsExactlyOnce() throws Exception {
        // Original ROM layout476 (bytes2a80057a3405) is the selected true-act2 invitation.
        // This positioned Sonic witness does not establish lower-post326 Knuckles eligibility.
        final int postX = 0x2a80, postY = 0x57a, postLayout = 476;
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY"), "the witness must not connect to the shared desktop");
        assertNull(System.getenv("WAYLAND_DISPLAY"));
        Path rom = Path.of(System.getProperty("s3k.rom.path")).toAbsolutePath();
        Path jar = Path.of(System.getProperty("secondstrike.invitation.jar")).toAbsolutePath();
        assertEquals(ROM_SHA1, digest(rom, "SHA-1"));
        assertEquals(CANDIDATE_SHA256, digest(jar, "SHA-256"));
        Path output = Path.of(System.getProperty("secondstrike.invitation.output",
                temporary.resolve("positioned-glowing-spheres").toString())).toAbsolutePath();
        Files.createDirectories(output);
        assertFalse(Files.exists(output.resolve("state.csv")), "preserve earlier capture runs");
        Path saves = Files.createDirectories(output.resolve("saves"));
        Path preferences = Files.createDirectories(output.resolve("preferences"));
        String oldDirectory = System.getProperty("user.dir"), oldSaveRoot = System.getProperty(SavePaths.ROOT_PROPERTY);
        System.setProperty("user.dir", preferences.toString());
        System.setProperty(SavePaths.ROOT_PROPERTY, saves.toString());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null,
                postX - 48, postY);
        Class<?> orbClass = com.openggf.game.sonic3k.objects.PachinkoItemOrbObjectInstance.class;
        Field armedField = orbClass.getDeclaredField("armed");
        Field touchedField = orbClass.getDeclaredField("touchedLastResolvedFrame");
        Field rewardField = orbClass.getDeclaredField("rewardItem");
        armedField.setAccessible(true); touchedField.setAccessible(true); rewardField.setAccessible(true);
        try (var session = new GameplayCaptureSession(settings);
             var driver = new Driver(session, output);
             var orbCsv = Files.newBufferedWriter(output.resolve("native-item-orbs.csv"))) {
            orbCsv.write("frame,orb_x,orb_y,armed,touched,reward_subtype,retired,player_x,player_y,object_control\n");
            session.applyMod(jar);
            session.boot(rom, 0, 1, settings);
            assertEquals(0, player().getRingCount(), "fresh positioned entry has no donated rings");
            assertEquals(0, GameServices.level().getLevelGamestate().getRings());
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertEquals(320, GameServices.camera().getWidth());
            var entryPost = posts().stream().filter(post -> post.getSpawn().layoutIndex() == postLayout)
                    .findFirst().orElseThrow(() -> new AssertionError("original native post476 is not admitted"));
            assertEquals(postX, entryPost.getSpawn().x()); assertEquals(postY, entryPost.getSpawn().y());
            assertEquals(5, entryPost.getSpawn().subtype());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals(1, GameServices.level().getApparentAct());
            driver.record("positioned-start", "-"); driver.picture("positioned-start");
            int activationFrame = -1, enabledFrame = -1;
            for (int frame = 0; frame < 760 && session.loop().getCurrentGameMode() == GameMode.LEVEL; frame++) {
                var checkpoint = checkpoint();
                if (activationFrame < 0 && checkpoint.getStarPostActivationMark() >= 5) {
                    activationFrame = driver.frame;
                    assertEquals(5, checkpoint.getLastCheckpointIndex());
                    assertEquals(0, player().getRingCount(), "ordinary post contact earned an invitation at zero rings");
                    driver.picture("zero-ring-invitation");
                }
                int age = starAge();
                if (age >= 128 && enabledFrame < 0) {
                    enabledFrame = driver.frame;
                    driver.picture("collision-enabled-stars");
                }
                boolean jump = age >= 128 && !player().getAir() && (driver.frame & 1) == 0;
                driver.step("native-post", steer(postX), jump);
            }
            assertTrue(activationFrame >= 0, "ordinary walking must activate native post476; inspect state.csv");
            assertTrue(enabledFrame >= 0, "native stars must reach their128-update collision lifetime");
            for (int tick = 0; tick < 360 && session.loop().getCurrentGameMode() != GameMode.BONUS_STAGE; tick++)
                driver.step("entry-transition", 0, false);
            assertEquals(GameMode.BONUS_STAGE, session.loop().getCurrentGameMode(), "a void request is not proof of actual bonus entry");
            BonusStageProvider provider = GameServices.bonusStage();
            assertEquals(BonusStageType.GLOWING_SPHERE, provider.getActiveType());
            BonusStageState saved = provider.getSavedState();
            assertNotNull(saved); assertEquals(0, saved.savedRingCount());
            assertEquals(1, saved.savedZoneAndAct()); assertEquals(1, saved.savedApparentZoneAndAct());
            assertEquals(postX, saved.playerX()); assertEquals(postY, saved.playerY());
            int entryLives = GameServices.gameState().getLives();
            ShieldType entryShield = player().getShieldType();
            boolean entryHadShield = player().hasShield();
            int interiorExitRings = -1;
            int interiorExitLives = -1, interiorExitScore = -1;
            ShieldType interiorExitShield = null;
            BonusStageProvider.BonusStageRewards exitRewards = null;
            boolean prizePicture = false, jumpWasHeld = false;
            for (int played = 0; played < 5000 && session.loop().getCurrentGameMode() == GameMode.BONUS_STAGE; played++) {
                // Aim at an admitted native item orb along the current trajectory, rather
                // than fight a bumper's native launch toward an orb behind the player.
                // Hold A for a normal variable-height jump; fresh presses also release
                // airborne magnet/flipper holds. After a prize, neutral input lets the
                // rising native energy trap capture and retire the stage normally.
                var orbs = GameServices.level().getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.PachinkoItemOrbObjectInstance.class);
                for (var orb : orbs) {
                    var reward = (com.openggf.game.sonic3k.objects.GumballItemObjectInstance) rewardField.get(orb);
                    orbCsv.write(driver.frame + "," + orb.getX() + "," + orb.getY() + ","
                            + armedField.getBoolean(orb) + "," + touchedField.getBoolean(orb) + ","
                            + (reward == null ? -1 : reward.getSpawn().subtype()) + "," + orb.isDestroyed()
                            + "," + player().getCentreX() + "," + player().getCentreY() + ","
                            + player().isObjectControlled() + "\n");
                }
                orbCsv.flush();
                int target = orbs.stream().min(java.util.Comparator.comparingInt(orb -> {
                    int dx = orb.getX() - player().getCentreX();
                    int behind = Integer.signum(player().getXSpeed()) * dx < 0 ? 1024 : 0;
                    try {
                        // A converted native reward still needs a real collection touch.
                        int unconverted = rewardField.get(orb) == null ? 4096 : 0;
                        return Math.abs(orb.getY() - player().getCentreY()) + Math.abs(dx) + behind + unconverted;
                    } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
                })).map(orb -> orb.getX()).orElse(320);
                // Newly caught magnets/flippers consume a fresh A edge, even in air.
                // Release an already-held pad for one tick, then press; do not wait a
                // fixed pulse period while the native orbit continues turning.
                boolean jump = !prizePicture && (player().isObjectControlled()
                        ? !jumpWasHeld : played % 32 < 20);
                driver.step("played-glowing-spheres", prizePicture ? 0 : steer(target), jump);
                jumpWasHeld = jump;
                if (played == 30) {
                    assertFalse(orbs.isEmpty(), "native ROM item orbs must be admitted during actual play");
                    driver.picture("native-glowing-spheres-active");
                }
                var rewards = provider.getRewards();
                boolean newlyGrantedShield = player().hasShield()
                        && (!entryHadShield || player().getShieldType() != entryShield);
                if (!prizePicture && (GameServices.gameState().getLives() > entryLives
                        || rewards.rings() > 0 || rewards.lives() > 0 || newlyGrantedShield)) {
                    prizePicture = true; driver.picture("native-prize");
                }
                if (provider.isStageComplete()) {
                    interiorExitRings = GameServices.level().getLevelGamestate().getRings();
                    interiorExitLives = GameServices.gameState().getLives();
                    interiorExitScore = GameServices.gameState().getScore();
                    interiorExitShield = player().getShieldType();
                    exitRewards = provider.getRewards();
                }
            }
            assertNotNull(exitRewards, "ordinary bonus inputs must reach the native exit trigger; inspect state.csv");
            assertTrue(prizePicture, "ordinary bonus play must earn a positive native award before exit");
            for (int tick = 0; tick < 480 && session.loop().getCurrentGameMode() != GameMode.LEVEL; tick++)
                driver.step("native-return", 0, false);
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertEquals(0, GameServices.level().getCurrentZone()); assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals(1, GameServices.level().getApparentAct());
            assertEquals(saved.savedLastStarPostHit(), checkpoint().getLastCheckpointIndex(), "native bonus entry clears Last_star_post_hit");
            assertEquals(5, checkpoint().getStarPostActivationMark(), "persistent native used mark survives the return reload");
            assertEquals(interiorExitRings, GameServices.level().getLevelGamestate().getRings(), "return uses live interior rings, not entry plus bookkeeping rewards");
            assertEquals(interiorExitLives + exitRewards.lives(), GameServices.gameState().getLives(),
                    "native direct1-up awards must not be added a second time on return");
            assertEquals(interiorExitScore, GameServices.gameState().getScore());
            assertEquals(interiorExitShield, player().getShieldType());
            assertEquals(BonusStageType.NONE, provider.getActiveType());
            assertEquals(BonusStageType.NONE, GameServices.bonusStage().getActiveType());
            assertTrue(stars().isEmpty(), "entry stars retire rather than recreating the opportunity");
            int rings = player().getRingCount(), lives = GameServices.gameState().getLives(), score = GameServices.gameState().getScore();
            ShieldType shield = player().getShieldType();
            for (int tick = 0; tick < 150; tick++) {
                driver.step("return-stability", 0, false);
                assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
                assertEquals(rings, player().getRingCount()); assertEquals(lives, GameServices.gameState().getLives());
                assertEquals(score, GameServices.gameState().getScore()); assertEquals(shield, player().getShieldType());
                assertTrue(stars().isEmpty());
            }
            driver.picture("native-return-stable");
            var saveSession = SessionManager.getCurrentWorldSession().getSaveSessionContext();
            assertTrue(saveSession == null || saveSession.activeSlot().isEmpty(), "positioned capture is No Save; durable-slot progression is separate");
        } finally {
            restore("user.dir", oldDirectory); restore(SavePaths.ROOT_PROPERTY, oldSaveRoot);
            Path scriptPath = output.resolve("ordinary-input.script");
            if (Files.exists(scriptPath)) {
                String script = Files.readString(scriptPath).replace(
                        "# Positioned post102; not cold-route or durable-slot evidence.",
                        "# Positioned bank1/apparent1 post476; not cold-route, Knuckles eligibility or durable-slot evidence.");
                Files.writeString(scriptPath, script);
                InputLogAuthorTool.author(script, output.resolve("ordinary-input.bk2"), "Sonic 3 & Knuckles");
            }
        }
    }

    private static int steer(int target) {
        int delta = target - player().getCentreX();
        int projected = delta - player().getXSpeed() / 48;
        return projected < -2 ? AbstractPlayableSprite.INPUT_LEFT
                : projected > 2 ? AbstractPlayableSprite.INPUT_RIGHT : 0;
    }
    private static AbstractPlayableSprite player() { return GameServices.camera().getFocusedSprite(); }
    private static CheckpointState checkpoint() { return (CheckpointState) GameServices.level().getCheckpointState(); }
    private static List<Sonic3kStarPostObjectInstance> posts() {
        return GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostObjectInstance.class);
    }
    private static List<Sonic3kStarPostBonusStarChild> stars() {
        return GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostBonusStarChild.class);
    }
    private static int starAge() {
        return stars().stream().mapToInt(star -> integer(star, "lifetime")).max().orElse(-1);
    }
    private static int integer(Object object, String name) {
        try {
            Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.getInt(object);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static String digest(Path path, String algorithm) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(path)));
    }
    private static void restore(String key, String previous) {
        if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
    }

    private static final class Driver implements AutoCloseable {
        final GameplayCaptureSession session;
        final Path output;
        final BufferedWriter csv;
        final List<String> recipe = new ArrayList<>();
        int frame;
        Driver(GameplayCaptureSession session, Path output) throws Exception {
            this.session = session; this.output = output;
            csv = Files.newBufferedWriter(output.resolve("state.csv"));
            csv.write("frame,phase,mode,zone,rom_zone,act,apparent_act,x,y,xvel,yvel,rings,live_rings,score,lives,shield,checkpoint,mark,used,stars,star_age,family,complete,reward_rings,reward_lives,reward_shield,input\n");
        }
        void step(String phase, int direction, boolean jump) throws Exception {
            String held = direction == AbstractPlayableSprite.INPUT_RIGHT ? "R" : direction == AbstractPlayableSprite.INPUT_LEFT ? "L" : "";
            if (jump) held += held.isEmpty() ? "A" : "+A";
            if (held.isEmpty()) held = "-";
            recipe.add(held);
            session.step(new Bk2FrameInput(frame, direction | (jump ? AbstractPlayableSprite.INPUT_JUMP : 0),
                    jump ? InputActionMasks.ACTION_A : 0, false, held));
            session.renderFrame();
            frame++; record(phase, held);
            assertFalse(player().getDead(), "unexpected native death; inspect positioned state.csv");
        }
        void record(String phase, String input) throws Exception {
            var level = GameServices.level(); var actor = player(); var state = checkpoint(); var provider = GameServices.bonusStage();
            var rewards = provider.getRewards();
            csv.write(frame + "," + phase + "," + session.loop().getCurrentGameMode() + "," + level.getCurrentZone()
                    + "," + level.getRomZoneId() + "," + level.getCurrentAct() + "," + level.getApparentAct()
                    + "," + actor.getCentreX() + "," + actor.getCentreY() + "," + actor.getXSpeed() + "," + actor.getYSpeed()
                    + "," + actor.getRingCount() + "," + level.getLevelGamestate().getRings() + "," + GameServices.gameState().getScore()
                    + "," + GameServices.gameState().getLives() + "," + actor.getShieldType() + "," + state.getLastCheckpointIndex()
                    + "," + state.getStarPostActivationMark() + "," + state.isUsedForSpecialStage() + "," + stars().size() + "," + starAge()
                    + "," + provider.getActiveType() + "," + provider.isStageComplete() + "," + rewards.rings() + "," + rewards.lives()
                    + "," + (rewards.shield() || rewards.fireShield() || rewards.lightningShield() || rewards.bubbleShield()) + "," + input + "\n");
            csv.flush();
        }
        void picture(String milestone) throws Exception {
            ScreenshotCapture.savePNG(session.render(), output.resolve(String.format("%05d-%s.png", frame, milestone)));
        }
        @Override public void close() throws Exception {
            csv.close();
            if (recipe.isEmpty()) return;
            var script = new StringBuilder("# Positioned post102; not cold-route or durable-slot evidence.\n# Candidate SHA256 ")
                    .append(CANDIDATE_SHA256).append("\n# ROM SHA1 ").append(ROM_SHA1).append("\n");
            for (int start = 0; start < recipe.size();) {
                int end = start + 1;
                while (end < recipe.size() && recipe.get(start).equals(recipe.get(end))) end++;
                script.append(end - start).append(' ').append(recipe.get(start)).append('\n'); start = end;
            }
            Files.writeString(output.resolve("ordinary-input.script"), script);
            InputLogAuthorTool.author(script.toString(), output.resolve("ordinary-input.bk2"), "Sonic 3 & Knuckles");
        }
    }
}
