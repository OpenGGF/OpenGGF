package com.openggf.game.sonic2.competition.lifecycle;

import com.openggf.audio.NullAudioBackend;
import com.openggf.camera.Camera;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.competition.NativeCompetitionWorld;
import com.openggf.game.competition.NativePlayerDeathPass;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2GameOverFlowProvider;
import com.openggf.game.sonic2.Sonic2PlayerArt;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.game.sonic2.competition.Sonic2CompetitionObjectAccess;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** ROM-loaded canonical-world lifecycle coverage; native interlace and host activation remain separate. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionLifecycleWorld {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualWorldRecreatesMissingHumansAndReplaysTheCameraWaitExactly(boolean removeBoth) throws Exception {
        GameServices.graphics().initHeadless();
        GameServices.audio().setBackend(new NullAudioBackend());
        var session = new Sonic2CompetitionSession();
        session.updateMenu(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()), true);
        session.bindPlayers(Sonic2CompetitionSession.createNativeParticipant(0, "sonic"),
                Sonic2CompetitionSession.createNativeParticipant(1, "tails"));
        session.bindCameras(new Camera(), new Camera());
        NativeCompetitionWorld[] holder = new NativeCompetitionWorld[1];
        var lifecycle = new Sonic2CompetitionLifecycle(TestEnvironment.currentRom(), session,
                (slot, timedOut) -> new Sonic2GameOverFlowProvider().beginNativeGameOverCard(
                        holder[0].context().getObjectManager().getObjectServices(), slot, timedOut),
                () -> { throw new AssertionError("This initial-bank test does not touch a checkpoint"); });
        var policy = new WorldSessionPolicyState() {
            @Override public <T> T getService(Class<T> type) {
                if (type.isInstance(session)) return type.cast(session);
                if (type == NativePlayerDeathPass.class || type == Sonic2CompetitionLifecycle.class) return type.cast(lifecycle);
                return null;
            }
            @Override public RewindSnapshottable<?> rewindAdapter() { return session; }
            @Override public void beforeAssembly(LevelLoadCause cause) { }
            @Override public void bindRoster(SpriteManager sprites) {
                session.bindPlayers((AbstractPlayableSprite) sprites.getSprite("sonic"),
                        (AbstractPlayableSprite) sprites.getSprite("tails"));
            }
            @Override public void afterNativeTick(com.openggf.LevelFrameContext context, LogicalInputSnapshot input) {
                session.advanceActFlow();
            }
            @Override public void failedAssembly(LevelLoadCause cause) { }
            @Override public void closeScreens() { }
            @Override public void retire() { }
        };
        var module = new Sonic2GameModule();
        var world = NativeCompetitionWorld.open(module, module, session, policy, EngineServices.current(), ignored -> { });
        holder[0] = world;
        world.load(0, 0);
        var context = world.context();
        assertSame(context, SessionManager.getCurrentGameplayMode());
        assertSame(context.getLevelManager(), GameServices.level());
        var profile = Sonic2CompetitionLevelProfile.load(TestEnvironment.currentRom(), 0, 0);
        for (int slot = 0; slot < 2; slot++) {
            var start = slot == 0 ? profile.sonicStart() : profile.tailsStart();
            NativePositionOps.writeXPosResetSubpixel(session.player(slot), start.x());
            NativePositionOps.writeYPosResetSubpixel(session.player(slot), start.y());
            var camera = session.camera(slot);
            camera.setFocusedSprite(session.player(slot));
            camera.setMinX((short) profile.bounds().minX()); camera.setMaxX((short) profile.bounds().maxX());
            camera.setMinY((short) profile.bounds().minY()); camera.setMaxY((short) profile.bounds().maxY());
            camera.setLevelStarted(true); camera.updatePosition(true);
        }
        // Seed only after the ROM load and actual native initial centers have been installed.
        lifecycle.initializeAct();
        context.getRewindRegistry().register(lifecycle);
        assertCurrentBindings(world, session, lifecycle);
        assertEquals(profile.sonicStart().x(), lifecycle.checkpoint(0).x());
        assertEquals(profile.tailsStart().x(), lifecycle.checkpoint(1).x());

        // Offset the actual P2 camera. Native routine A must wait for ordinary scrolling, not snap.
        session.camera(1).setX((short) (session.camera(1).getX() + 0x100));
        assertTrue(session.player(1).applyPitDeath());
        NativePositionOps.writeYPosPreserveSubpixel(session.player(1), session.camera(1).getMaxY() + 0x101);
        int lives = session.gameState(1).getLives();
        int p1Lives = session.gameState(0).getLives();
        int displacedCameraX = session.camera(1).getX();
        stepWithNativeSecondCamera(new HeadlessTestRunner(session.player(0)), session, lifecycle);
        assertEquals(Sonic2CompetitionLifecycle.Routine.RESPAWNING, lifecycle.routine(1));
        assertEquals(lives - 1, session.gameState(1).getLives());
        assertEquals(p1Lives, session.gameState(0).getLives());
        assertEquals(displacedCameraX - 16, session.camera(1).getX(), "ScrollHoriz caps the leftward step at 16");
        assertFalse(context.getLevelManager().isRespawnRequestedForRewind());

        // Advance the live RNG through its service, so the registry must preserve this context's seed.
        context.getRng().nextRaw();
        var registry = context.getRewindRegistry();
        CompositeSnapshot checkpoint = registry.capture();
        var camera0 = session.camera(0).capture();
        var camera1 = session.camera(1).capture();
        var original0 = session.player(0);
        var original1 = session.player(1);
        var originalDust0 = original0.getSpindashDustController();
        var originalDust1 = original1.getSpindashDustController();
        var originalTail = original1.getTailsTailsController();
        long beforeTime = session.levelState(0).getTimerFrames();

        // Both drivers begin with neutral edge history. Their diagnostic frameCounter is not an engine clock.
        var baselineRunner = new HeadlessTestRunner(original0);
        List<CompositeSnapshot> forward = new ArrayList<>();
        for (int frame = 0; frame < 32; frame++) {
            stepWithNativeSecondCamera(baselineRunner, session, lifecycle);
            forward.add(registry.capture());
        }
        assertEquals(32, baselineRunner.getFrameCounter());
        assertEquals(Sonic2CompetitionLifecycle.Routine.CONTROL, lifecycle.routine(1));
        assertTrue(session.levelState(0).getTimerFrames() > beforeTime);

        // These are real roster removals, with no test-side actor reconstruction or state hydration.
        assertTrue(context.getSpriteManager().removeSprite(original1.getCode()));
        if (removeBoth) assertTrue(context.getSpriteManager().removeSprite(original0.getCode()));
        context.getRng().nextRaw();
        registry.restore(checkpoint);

        assertNotSame(original1, session.player(1));
        assertNotSame(originalDust1, session.player(1).getSpindashDustController());
        assertNotSame(originalTail, session.player(1).getTailsTailsController());
        if (removeBoth) {
            assertNotSame(original0, session.player(0));
            assertNotSame(originalDust0, session.player(0).getSpindashDustController());
        } else {
            assertSame(original0, session.player(0));
        }
        assertCurrentBindings(world, session, lifecycle);
        assertEquals(camera0, session.camera(0).capture(), "P1 camera words must be restored exactly");
        assertEquals(camera1, session.camera(1).capture(), "P2 camera words must be restored exactly");
        assertEquals(Sonic2CompetitionLifecycle.Routine.RESPAWNING, lifecycle.routine(1));
        assertSameSnapshots(checkpoint, registry.capture(), "immediate destructive restore");
        assertFalse(lifecycle.runDeathPass(original1), "The retired actor cannot retain native lifecycle authority");
        assertEquals(-1, Sonic2CompetitionObjectAccess.slot(context.getObjectManager().getObjectServices(), original1));
        if (removeBoth) assertFalse(lifecycle.runDeathPass(original0));

        var replayRunner = new HeadlessTestRunner(session.player(0));
        for (int frame = 0; frame < forward.size(); frame++) {
            stepWithNativeSecondCamera(replayRunner, session, lifecycle);
            assertSameSnapshots(forward.get(frame), registry.capture(), "replay frame " + frame);
        }
        assertEquals(32, replayRunner.getFrameCounter());
        assertEquals(Sonic2CompetitionLifecycle.Routine.CONTROL, lifecycle.routine(1));
        assertFalse(context.getLevelManager().isRespawnRequestedForRewind());
        assertEquals(List.of(), session.gameState(1).getCollectedSuperEmeraldIndices());
    }

    private static void stepWithNativeSecondCamera(HeadlessTestRunner runner, Sonic2CompetitionSession session,
                                                  Sonic2CompetitionLifecycle lifecycle) {
        boolean waiting = lifecycle.routine(1) == Sonic2CompetitionLifecycle.Routine.RESPAWNING;
        int x = session.player(1).getCentreX(), y = session.player(1).getCentreY();
        int subX = session.player(1).getXSubpixelRaw(), subY = session.player(1).getYSubpixelRaw();
        int cameraX = session.camera(1).getX(), cameraY = session.camera(1).getY();
        runner.stepFrame(false, false, false, false, false);
        if (waiting) {
            assertEquals(x, session.player(1).getCentreX(), "Routine A cannot execute ordinary movement");
            assertEquals(y, session.player(1).getCentreY());
            assertEquals(subX, session.player(1).getXSubpixelRaw());
            assertEquals(subY, session.player(1).getYSubpixelRaw());
        }
        // LevelFrameStep owns P1's camera. This bounded host adapter services P2 once afterward,
        // recording the actual native signed 8.8 camera-difference words for the next player pass.
        session.camera(1).updatePosition(false);
        lifecycle.recordCameraDisplacement(1,
                (short) ((session.camera(1).getX() - cameraX) << 8),
                (short) ((session.camera(1).getY() - cameraY) << 8));
    }

    private static void assertCurrentBindings(NativeCompetitionWorld world, Sonic2CompetitionSession session,
                                               Sonic2CompetitionLifecycle lifecycle) throws Exception {
        GameplayModeContext context = world.context();
        var services = context.getObjectManager().getObjectServices();
        assertSame(context.getWorldSession(), services.worldSession());
        assertSame(context.getSpriteManager(), services.spriteManager());
        assertSame(context.getLevelManager(), services.levelManager());
        assertSame(context.getRng(), services.rng());
        assertSame(session, WorldSessionPolicyAccess.getService(services.worldSession(), Sonic2CompetitionSession.class));
        var romArt = new Sonic2PlayerArt(RomByteReader.fromRom(TestEnvironment.currentRom()));
        for (int slot = 0; slot < 2; slot++) {
            var actor = session.player(slot);
            var art = romArt.loadForCharacter(slot == 0 ? "sonic" : "tails");
            assertSame(actor, context.getSpriteManager().getSprite(actor.getCode()));
            assertSame(actor, session.camera(slot).getFocusedSprite());
            assertSame(session.camera(slot), actor.currentCamera());
            assertSame(context.getLevelManager(), actor.currentLevelManager());
            assertSame(context.getSpriteManager(), actor.currentSpriteManagerOrNull());
            assertSame(context.getRng(), actor.currentRngOrNull());
            assertSame(session.levelState(slot), actor.currentLevelState());
            assertSame(lifecycle, NativePlayerDeathPass.from(actor));
            assertEquals(slot, Sonic2CompetitionObjectAccess.slot(services, actor));
            assertFalse(actor.isCpuControlled());
            assertNull(actor.getCpuController(), "Neither native human may acquire a follower controller");
            assertNotNull(actor.getAnimationProfile());
            assertNotNull(actor.getAnimationSet());
            assertEquals(art.animationSet().getAllScripts(), actor.getAnimationSet().getAllScripts());
            assertNotNull(actor.getSpriteRenderer());
            assertEquals(art.dplcFrames().get(actor.getMappingFrame()), actor.getSpriteRenderer().dplcFrame(actor.getMappingFrame()));
            assertNotNull(actor.getSpindashDustController());
            assertNotNull(actor.getSpindashDustController().getRenderer());
            assertNotNull(actor.getPowerUpSpawner());
            assertNotNull(actor.getGroundSensors());
            assertTrue(actor.getGroundSensors().length > 0);
            if (slot == 1) {
                assertNotNull(actor.getTailsTailsController());
                assertNotNull(actor.getTailsTailsController().getRenderer());
            }
        }
    }

    private static void assertSameSnapshots(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label + " registry coverage");
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
