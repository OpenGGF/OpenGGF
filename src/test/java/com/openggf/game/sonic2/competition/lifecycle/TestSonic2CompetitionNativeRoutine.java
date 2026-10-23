package com.openggf.game.sonic2.competition.lifecycle;

import com.openggf.audio.NullAudioBackend;
import com.openggf.camera.Camera;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.competition.NativeCompetitionWorld;
import com.openggf.game.competition.NativePlayerDeathPass;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Raw S2 routine projection; the root host's unavailable title wrapper is a separate integration. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionNativeRoutine {
    @BeforeEach
    void headlessAudioAndGraphics() {
        GameServices.graphics().initHeadless();
        GameServices.audio().setBackend(new NullAudioBackend());
    }

    @Test
    void acceptedCanonicalPlayerPassClearsOnlyThatHumansCapturedInitialZero() throws Exception {
        var session = new Sonic2CompetitionSession();
        session.updateMenu(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()), true);
        session.bindPlayers(Sonic2CompetitionSession.createNativeParticipant(0, "sonic"),
                Sonic2CompetitionSession.createNativeParticipant(1, "tails"));
        session.bindCameras(new Camera(), new Camera());
        var lifecycle = new Sonic2CompetitionLifecycle(TestEnvironment.currentRom(), session,
                (slot, timeout) -> { throw new AssertionError("Initial player pass cannot create a death card"); },
                () -> { throw new AssertionError("Initial player pass cannot activate a checkpoint"); });
        var policy = new WorldSessionPolicyState() {
            @Override public <T> T getService(Class<T> type) {
                if (type.isInstance(session)) return type.cast(session);
                if (type == NativePlayerDeathPass.class || type == Sonic2CompetitionLifecycle.class)
                    return type.cast(lifecycle);
                return null;
            }
            @Override public RewindSnapshottable<?> rewindAdapter() { return session; }
            @Override public void beforeAssembly(LevelLoadCause cause) { }
            @Override public void bindRoster(SpriteManager sprites) {
                session.bindPlayers((AbstractPlayableSprite) sprites.getSprite("sonic"),
                        (AbstractPlayableSprite) sprites.getSprite("tails"));
            }
            @Override public void failedAssembly(LevelLoadCause cause) { }
            @Override public void closeScreens() { }
            @Override public void retire() { }
        };
        var module = new Sonic2GameModule();
        var world = NativeCompetitionWorld.open(module, module, session, policy, EngineServices.current(), ignored -> { });
        world.load(0, 0);
        var profile = Sonic2CompetitionLevelProfile.load(TestEnvironment.currentRom(), 0, 0);
        for (int slot = 0; slot < 2; slot++) {
            var start = slot == 0 ? profile.sonicStart() : profile.tailsStart();
            var actor = session.player(slot);
            NativePositionOps.writeXPosResetSubpixel(actor, start.x());
            NativePositionOps.writeYPosResetSubpixel(actor, start.y());
            actor.setControlLocked(true);
            session.camera(slot).setFocusedSprite(actor);
            session.camera(slot).setMinX((short) profile.bounds().minX());
            session.camera(slot).setMaxX((short) profile.bounds().maxX());
            session.camera(slot).setMinY((short) profile.bounds().minY());
            session.camera(slot).setMaxY((short) profile.bounds().maxY());
            session.camera(slot).updatePosition(true);
        }
        lifecycle.initializeAct();
        assertEquals(0, lifecycle.nativeRoutineByte(0));
        assertEquals(0, lifecycle.nativeRoutineByte(1));
        var initial = lifecycle.capture();
        var actor = session.player(0);
        var actorInitial = actor.captureRewindState();
        assertNotNull(actor.getAnimationProfile());
        assertNotNull(actor.getAnimationSet());
        assertNotNull(actor.getSpriteRenderer());
        // This is the existing production player dispatcher, without a direct lifecycle.step call.
        // The S2 host title wrapper lives on the root lane and is not imported into this tree.
        SpriteManager.tickPlayablePhysics(actor, false, false, false, false, false, false,
                false, false, world.context().getLevelManager(), 0);
        assertEquals(2, lifecycle.nativeRoutineByte(0));
        assertEquals(0, lifecycle.nativeRoutineByte(1), "P1's accepted pass cannot initialize P2");
        var forwardLifecycle = lifecycle.capture();
        var forwardActor = actor.captureRewindState();
        actor.restoreRewindState(actorInitial);
        lifecycle.restore(initial);
        assertEquals(0, lifecycle.nativeRoutineByte(0));
        SpriteManager.tickPlayablePhysics(actor, false, false, false, false, false, false,
                false, false, world.context().getLevelManager(), 0);
        same("initial pass lifecycle replay", forwardLifecycle, lifecycle.capture());
        same("initial pass actor replay", forwardActor, actor.captureRewindState());
    }

    @Test
    void initialZeroAndBothControlBytesSurviveCaptureRestoreAndForwardWrites() throws Exception {
        var f = new LifecycleFixture();
        var initialLife = f.lifecycle.capture();
        var initial0 = f.actor(0).captureRewindState();
        var initial1 = f.actor(1).captureRewindState();
        assertEquals(0, f.lifecycle.nativeRoutineByte(0));
        assertEquals(0, f.lifecycle.nativeRoutineByte(1));
        assertFalse(f.lifecycle.runDeathPass(f.actor(0)));
        f.lifecycle.applyCapturedControlRoutineByte(1, 4);
        assertEquals(2, f.lifecycle.nativeRoutineByte(0));
        assertEquals(4, f.lifecycle.nativeRoutineByte(1));
        var forward = observed(f);
        f.actor(0).restoreRewindState(initial0);
        f.actor(1).restoreRewindState(initial1);
        f.lifecycle.restore(initialLife);
        assertEquals(0, f.lifecycle.nativeRoutineByte(0));
        assertEquals(0, f.lifecycle.nativeRoutineByte(1));
        assertFalse(f.lifecycle.runDeathPass(f.actor(0)));
        f.lifecycle.applyCapturedControlRoutineByte(1, 4);
        same("initial-bit forward replay", forward, observed(f));
        var controlLife = f.lifecycle.capture();
        var controlActor = f.actor(1).captureRewindState();
        f.lifecycle.applyCapturedControlRoutineByte(1, 2);
        assertEquals(2, f.lifecycle.nativeRoutineByte(1));
        f.actor(1).restoreRewindState(controlActor);
        f.lifecycle.restore(controlLife);
        assertEquals(4, f.lifecycle.nativeRoutineByte(1));
        f.lifecycle.applyCapturedControlRoutineByte(1, 2);
        assertFalse(f.actor(1).isHurt());
        assertEquals(2, f.lifecycle.nativeRoutineByte(1));
    }

    @Test
    void samePassActResultsKeepsLiveRoutineQueriesAndRawWritesAvailable() throws Exception {
        var f = accepted();
        f.lifecycle.applyCapturedControlRoutineByte(1, 4);
        f.session.deathCardDismissed(0, true);
        assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS, f.session.phase());
        assertFalse(f.lifecycle.ownsDispatch(f.actor(1)));
        assertEquals(4, f.lifecycle.nativeRoutineByte(1));
        f.lifecycle.applyCapturedControlRoutineByte(1, 2);
        assertEquals(2, f.lifecycle.nativeRoutineByte(1));
        f.lifecycle.applyCapturedControlRoutineByte(0, 4);
        assertEquals(4, f.lifecycle.nativeRoutineByte(0));
        assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS, f.session.phase());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4})
    void shippedDrowningWaitRetainsEntryControlByteUntilThe120thTickWritesSix(int entry) throws Exception {
        var f = accepted();
        f.lifecycle.applyCapturedControlRoutineByte(0, entry);
        assertTrue(f.actor(0).applyDrownDeath());
        assertEquals(entry, f.lifecycle.nativeRoutineByte(0));
        var movement = (PlayableSpriteMovement) f.actor(0).getMovementManager();
        for (int tick = 1; tick < 120; tick++) {
            movement.handleMovement(false, false, false, false, false, false, false, false);
            assertTrue(f.actor(0).isDrowningPreDeath(), "Drowning tick " + tick);
            assertFalse(f.actor(0).getDead());
            assertEquals(entry, f.lifecycle.nativeRoutineByte(0), "No S3K $A or early death byte at tick " + tick);
        }
        movement.handleMovement(false, false, false, false, false, false, false, false);
        assertFalse(f.actor(0).isDrowningPreDeath());
        assertTrue(f.actor(0).getDead());
        assertEquals(0, f.actor(0).captureRewindState().playerExtra().drownPreDeathTimer());
        assertEquals(6, f.lifecycle.nativeRoutineByte(0));
    }

    @Test
    void capturedControlWritesChangeOnlyHurtAndTheStaleRawOverride() throws Exception {
        var f = accepted();
        var actor = f.actor(0);
        f.lifecycle.applyCapturedControlRoutineByte(0, 4);
        assertTrue(actor.applyDrownDeath());
        actor.giveShield();
        actor.setForcedAnimationId(0x17);
        actor.setObjectRoutineOverride(2);
        actor.setInvulnerableFrames(71);
        actor.setInvincibleFrames(222);
        actor.setOnObject(true);
        actor.captureOnObjectAtFrameStart();
        actor.setXSpeed((short) 0x123); actor.setYSpeed((short) 0x234); actor.setGSpeed((short) 0x345);
        actor.setSubpixelRaw(0x1234, 0x5678);
        f.session.levelState(0).setTimerFrames(12345);
        f.session.levelState(0).setRings(153);
        f.session.levelState(0).setRingExtraLifeFlags(6);
        f.session.camera(0).setX((short) 0x321);
        f.session.camera(0).setY((short) 0x432);
        var before = observed(f);
        f.lifecycle.applyCapturedControlRoutineByte(0, 2);
        assertEquals(2, f.lifecycle.nativeRoutineByte(0));
        assertFalse(actor.isHurt());
        assertNull(actor.getObjectRoutineOverride());
        assertEquals(0x17, actor.getForcedAnimationId());
        assertTrue(actor.isDrowningPreDeath());
        assertEquals(120, actor.captureRewindState().playerExtra().drownPreDeathTimer());
        assertTrue(actor.hasShield());
        assertEquals(71, actor.getInvulnerableFrames());
        var changes = RewindSnapshotDiff.diffKey("state", before, observed(f));
        assertFalse(changes.isEmpty(), "The captured hurt byte really changed");
        for (String change : changes) {
            assertTrue(change.contains(".playerExtra.hurt:") || change.contains(".playerExtra.objectRoutineOverride:"),
                    () -> "Raw control application changed an unrelated field: " + change);
        }
        var afterTwo = observed(f);
        f.lifecycle.applyCapturedControlRoutineByte(0, 4);
        assertEquals(4, f.lifecycle.nativeRoutineByte(0));
        var reverse = RewindSnapshotDiff.diffKey("state", afterTwo, observed(f));
        assertFalse(reverse.isEmpty());
        for (String change : reverse) assertTrue(change.contains(".playerExtra.hurt:"), () -> change);
    }

    @Test
    void actualObjectLandingRetainsFourButCapturedTwoAndHurtStopDisplayCannotResurrectIt() throws Exception {
        var f = accepted();
        var actor = f.actor(0);
        actor.setAir(true);
        f.lifecycle.applyCapturedControlRoutineByte(0, 4);
        actor.setOnObject(true);
        actor.captureOnObjectAtFrameStart();
        actor.setAirAfterObjectHurtLanding();
        assertTrue(actor.isHurt());
        assertEquals(4, f.lifecycle.nativeRoutineByte(0));
        f.lifecycle.applyCapturedControlRoutineByte(0, 2);
        assertTrue(actor.getHurtAtFrameStart());
        assertTrue(actor.isOnObject());
        assertFalse(actor.getHurtRecoveryCompletedThisFrame());
        assertEquals(2, f.lifecycle.nativeRoutineByte(0), "Frame-start trace latches cannot undo an actual routine write");
        actor.setAir(true);
        f.lifecycle.applyCapturedControlRoutineByte(0, 4);
        actor.setAir(false);
        assertTrue(actor.captureRewindState().playerExtra().hurtRoutineOwnedDisplayThisFrame());
        assertFalse(actor.isHurt());
        assertEquals(2, f.lifecycle.nativeRoutineByte(0), "The final hurt display pass already has raw routine2");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 6, 8, 10, 12, -1, 256})
    void invalidCapturedBytesRejectBeforeAnyActorOrOwnerWrite(int raw) throws Exception {
        var f = accepted();
        var before = observed(f);
        assertThrows(IllegalArgumentException.class, () -> f.lifecycle.applyCapturedControlRoutineByte(0, raw));
        same("invalid captured byte " + raw, before, observed(f));
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 8, 10})
    void serviceOwnedDeathGoneAndCameraWaitBytesCannotBeOverwrittenByControl(int raw) throws Exception {
        var f = accepted();
        if (raw == 6) f.actor(0).setDead(true);
        else {
            if (raw == 8) f.lives(0, 1);
            f.die(0);
        }
        assertEquals(raw, f.lifecycle.nativeRoutineByte(0));
        var before = observed(f);
        assertThrows(IllegalStateException.class, () -> f.lifecycle.applyCapturedControlRoutineByte(0, 2));
        assertThrows(IllegalStateException.class, () -> f.lifecycle.applyCapturedControlRoutineByte(0, 4));
        same("protected native routine " + raw, before, observed(f));
    }

    @Test
    void unseededCpuForeignAndUnregisteredHumansRejectWithZeroWrites() throws Exception {
        var f = accepted();
        f.lifecycle.resetForMissingSnapshot();
        assertEquals(0, f.lifecycle.nativeRoutineByte(0));
        var unseeded = observed(f);
        assertThrows(IllegalStateException.class, () -> f.lifecycle.applyCapturedControlRoutineByte(0, 2));
        same("unseeded rejection", unseeded, observed(f));
        f.lifecycle.initializeAct();
        f.lifecycle.runDeathPass(f.actor(0));
        f.actor(0).setCpuControlled(true);
        var cpu = observed(f);
        assertThrows(IllegalStateException.class, () -> f.lifecycle.nativeRoutineByte(0));
        assertThrows(IllegalStateException.class, () -> f.lifecycle.applyCapturedControlRoutineByte(0, 4));
        same("CPU rejection", cpu, observed(f));
        f.actor(0).setCpuControlled(false);
        var realWorld = f.actors.level.getWorldSession();
        when(f.actors.level.getWorldSession()).thenReturn(new WorldSession(new Sonic2GameModule()));
        var foreign = observed(f);
        assertThrows(IllegalStateException.class, () -> f.lifecycle.nativeRoutineByte(0));
        assertThrows(IllegalStateException.class, () -> f.lifecycle.applyCapturedControlRoutineByte(0, 2));
        same("foreign world rejection", foreign, observed(f));
        when(f.actors.level.getWorldSession()).thenReturn(realWorld);
        assertTrue(f.actors.sprites.removeSprite(f.actor(0).getCode()));
        var unregistered = observed(f);
        assertThrows(IllegalStateException.class, () -> f.lifecycle.nativeRoutineByte(0));
        assertThrows(IllegalStateException.class, () -> f.lifecycle.applyCapturedControlRoutineByte(0, 2));
        same("unregistered actor rejection", unregistered, observed(f));
    }

    private static LifecycleFixture accepted() throws Exception {
        var f = new LifecycleFixture();
        assertFalse(f.lifecycle.runDeathPass(f.actor(0)));
        assertFalse(f.lifecycle.runDeathPass(f.actor(1)));
        return f;
    }

    private record Observed(Object actor0, Object actor1, Object lifecycle, Object session,
                            Object camera0, Object camera1, Object audio, List<String> requests,
                            List<String> cards) { }

    private static Observed observed(LifecycleFixture f) {
        return new Observed(f.actor(0).captureRewindState(), f.actor(1).captureRewindState(),
                f.lifecycle.capture(), f.session.capture(), f.session.camera(0).capture(),
                f.session.camera(1).capture(), GameServices.audio().captureLogicalSnapshot(),
                List.copyOf(f.audio), List.copyOf(f.cardRequests));
    }

    private static void same(String label, Object expected, Object actual) {
        var differences = RewindSnapshotDiff.diffKey(label, expected, actual);
        assertTrue(differences.isEmpty(), () -> differences.toString());
    }
}
