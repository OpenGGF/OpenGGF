package com.openggf.game.soniccd.player;

import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStockLaunch;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.resources.CdPlayerVisualAdapter;
import com.openggf.level.LevelManager;
import com.openggf.sprites.render.PlayerResidentBanks;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Source-backed adapter checks; this direct slot test does not admit an engine route. */
class TestCdPlayableSonic {
    @BeforeEach void setup() {
        TestEnvironment.configureGameModuleFixture(new com.openggf.game.soniccd.SonicCdGameModule());
        TestEnvironment.activeGameplayMode();
        GameServices.camera().setX((short) 0);
        GameServices.camera().setY((short) 0x70B);
        GameServices.graphics().initHeadless();
    }

    @AfterEach void close() { TestEnvironment.resetAll(); }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void actorProjectsOnlyTheCompletedNativeBodyAndKeepsArtServiceOutsideTheSlot(CdReleaseVariant variant)
            throws Exception {
        var source = source(variant);
        var runtime = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var actor = new CdPlayableSonic("sonic", runtime, GameServices.graphics());
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(new SonicCdLevel(source.assets()));
        var cold = runtime.captureActor();
        assertEquals(0, actor.nativeState().motion().routine());
        assertEquals(64, actor.getCentreX());
        assertEquals(1899, actor.getCentreY());
        assertEquals(24, actor.getSpriteRenderer().patternBankCapacity());
        assertEquals(0, runtime.artState().lastFrame());
        assertTrue(runtime.display().isEmpty());
        assertThrows(IllegalStateException.class, () -> actor.runPlayableSlot(level, 1));
        assertEquals(cold, runtime.captureActor(), "an unserviced slot must not initialize the player");

        runtime.prepareInput(0);
        runtime.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 1);
        assertEquals(2, actor.nativeState().motion().routine());
        assertEquals(1899, actor.getCentreY());
        assertEquals(0, actor.getMappingFrame());
        assertTrue(runtime.display().isEmpty(), "Init is not Main or DrawObject");
        assertEquals(0, runtime.artState().lastFrame());
        assertThrows(IllegalStateException.class, () -> actor.runPlayableSlot(level, 1));
        assertThrows(IllegalStateException.class, actor::captureRewindState);
        actor.finishAfterCamera();

        runtime.prepareInput(0);
        runtime.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 2);
        assertEquals(1898, actor.getCentreY());
        assertEquals(0xFC, actor.getAngle() & 255);
        assertEquals(0, actor.getYSubpixelRaw());
        assertEquals(actor.nativeState().animation().mapFrame(), actor.getMappingFrame());
        assertTrue(runtime.display().isPresent());
        assertEquals(0, runtime.artState().lastFrame(), "new Main pose is for the next V-int");
        assertEquals(4, actor.nativeState().motion().spriteFlags(), "DrawObject clears before the camera pass");
        actor.finishAfterCamera();
        assertEquals(0x84, actor.nativeState().motion().spriteFlags(), "SAT sets without a GPU draw");

        runtime.prepareInput(0);
        runtime.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 3);
        actor.finishAfterCamera();
        var tiles = PlayerResidentBanks.capture(actor.getSpriteRenderer()).tiles();
        var expected = CdPlayerVisualAdapter.residentPatterns(runtime.artState());
        assertEquals(24, tiles.length);
        for (int i = 0; i < tiles.length; i++) {
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                assertEquals(expected[i].getPixel(x, y), tiles[i].getPixel(x, y));
            }
        }
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void rendererReplacementGenericResetAndAnotherStageRejectWithoutChangingNativeState(CdReleaseVariant variant)
            throws Exception {
        var source = source(variant);
        var runtime = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var actor = new CdPlayableSonic("sonic", runtime, GameServices.graphics());
        var original = actor.getSpriteRenderer();
        var state = runtime.captureActor();
        var other = new PlayerSpriteRenderer(CdPlayerVisualAdapter.artSet(source.assets().sonic(), 0x20000),
                GameServices.graphics());
        assertThrows(UnsupportedOperationException.class, () -> actor.setSpriteRenderer(other));
        assertThrows(UnsupportedOperationException.class, actor::resetState);
        assertSame(original, actor.getSpriteRenderer());
        assertEquals(state, runtime.captureActor());
        var wrongLevel = mock(LevelManager.class);
        assertThrows(IllegalArgumentException.class, () -> actor.runPlayableSlot(wrongLevel, 1));
        assertEquals(state, runtime.captureActor());
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void unsupportedBodyAfterARealServiceClosesAdmissionInsteadOfSkippingAMain(CdReleaseVariant variant)
            throws Exception {
        var source = source(variant);
        var frame = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var actor = new CdPlayableSonic("sonic", frame, GameServices.graphics());
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(new SonicCdLevel(source.assets()));
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 1); actor.finishAfterCamera();
        frame.applyScrollFeedback(new CdPlayerMovement.Bounds(0, 0x2897, 0, 0), 96);
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        // MdGround's LevelBound reads bottom 0 and calls the shared KillPlayer at Y 1899 = $076B; its RTS
        // resumes the ground tail and Main completes (Sonic/Main.asm:701-707,1697-1705).
        actor.runPlayableSlot(level, 2);
        var killed = actor.nativeState();
        assertEquals(6, killed.motion().routine());
        assertEquals(0x07, killed.motion().stick()); assertEquals(0x6B, killed.rotationDistance());
        assertEquals(0x8780, killed.header().tile()); assertEquals(0, killed.header().priority());
        assertTrue(actor.lastSoundRequests().contains(
                new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_HURT)));
        assertTrue(frame.display().orElseThrow().queued(), "Main's Display follows the MODE kill");
        actor.finishAfterCamera();
        var beforeBody = frame.state();
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        // The direct actor supplies no LevelContext, so ObjSonic_Dead keeps its typed stop.
        assertEquals(CdPlayerBody.UnsupportedBoundary.DEAD_ROUTINE,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> actor.runPlayableSlot(level, 3)).boundary());
        assertSame(beforeBody, frame.state(), "the pure Body must not publish a half death routine");
        assertTrue(frame.closed(), "the V-int already advanced; the engine must rebuild instead of retrying a missing call");
        assertThrows(IllegalStateException.class, () -> frame.prepareInput(0));
        assertThrows(IllegalStateException.class, actor::draw);
    }

    // Routine-6/8 projection (Sonic/Main.asm:2397-2495). Written before production. Only the shared object
    // pass can supply the ephemeral LevelContext; Dead's DrawObject is queued like Hurt's, Restart draws nothing.
    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void deathDrawObjectIsQueuedThroughTheSharedPassAndTheRestartDelayDrawsNothing(CdReleaseVariant variant)
            throws Exception {
        var source = source(variant);
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(new SonicCdLevel(source.assets()));
        var frame = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var pass = new DeathPass(frame);
        var actor = new CdPlayableSonic("sonic", frame, GameServices.graphics(), pass);
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 1); actor.finishSharedAfterCamera();
        assertTrue(pass.cuts.isEmpty(), "Init has no DrawObject");
        installGoneDeath(frame); actor.rebindAfterRestore();

        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 2);
        var dead = pass.last.result();
        assertEquals(CdPlayerBody.Boundary.DEAD_RETURN, dead.boundary());
        // DeadChkGone (2410-2444): routine 8; lives 3 -> 2, so 8*60 then 60; FindObjSlot answers the free slot 32.
        assertEquals(8, actor.nativeState().motion().routine()); assertEquals(0x3C, actor.nativeState().timers().reset());
        var summary = dead.effects().stream().filter(CdPlayerBody.DeadCheckGone.class::isInstance)
                .map(CdPlayerBody.DeadCheckGone.class::cast).toList();
        assertEquals(1, summary.size()); assertEquals(2, summary.getFirst().lives());
        assertEquals(Optional.of(new CdPlayerTouchResponse.FindObjSlot(OptionalInt.of(32))), summary.getFirst().allocation());
        // JMP DrawObject (2404) is unconditional; ObjMoveGrv left Y $0810.C800.
        assertEquals(List.of(new CdPlayerBody.DisplayEligibility(true, 64, 0x810, 19, 4, 24, 0)), pass.cuts);
        assertEquals(0x6A, actor.getMappingFrame(), "SonAni_Death dc.b 3,$6A,$FF (Data/Animations.asm:101)");
        assertTrue(frame.display().orElseThrow().queued());
        frame.publishDrawObjectOnScreen(true); actor.finishSharedAfterCamera();
        var savedActor = actor.captureRewindState(); var savedNative = frame.captureActor(); var savedShared = frame.captureShared();

        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 3);
        assertEquals(CdPlayerBody.Boundary.RESTART_RETURN, pass.last.result().boundary());
        assertEquals(1, pass.cuts.size(), "ObjSonic_Restart calls no DrawObject");
        assertTrue(frame.display().isEmpty()); assertEquals(0x3B, actor.nativeState().timers().reset());
        actor.finishSharedAfterCamera();
        var advanced = frame.captureActor();
        actor.restoreRewindState(savedActor); frame.restoreShared(savedShared); actor.rebindAfterRestore();
        assertEquals(savedNative, frame.captureActor());
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 3); actor.finishSharedAfterCamera();
        assertEquals(advanced, frame.captureActor());
        assertEquals(1, pass.cuts.size());
        frame.close();
    }

    /** Same-owner closed-cut edit: a post-KillPlayer routine-6 pose one pixel below the $0710+$100 gone line. */
    private static void installGoneDeath(CdPlayerFrameRuntime frame) {
        var actor = frame.captureActor(); var s = actor.state(); var m = s.motion(); var h = s.header();
        var motion = new CdPlayerMotionState(64 << 16, 0x0811 << 16, 0, -0x700, 0, 6, 0, 2, 4, m.xRadius(), m.yRadius(),
                0x18, 0x18, 0, 0, 0, 8, 0, m.previousPrimaryAngle(), m.previousSecondaryAngle());
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), new CdPlayerBody.State(motion,
                new CdPlayerBody.AnimationCursor(0, 0, 0), new CdPlayerBody.Header(h.slotId(), h.mappingAddress(),
                h.tile() | 0x8000, 0, h.width()), new CdPlayerBody.Timers(0, 0, 0, 0), s.history(), 0x11), Optional.empty()));
        var shared = frame.captureShared(); var e = shared.environment(); var mv = e.movement();
        var environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(mv.physics(),
                new CdPlayerMovement.Bounds(0, 0x2897, 0x710, 0), mv.gates(), mv.globals()), e.controls(), e.globals(),
                e.dynamicSlots());
        frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), environment, shared.artState(),
                shared.paused(), shared.vIntClock()));
    }

    /**
     * Recording shared-owner port: the real Frame/Body runs with one fresh ephemeral LevelContext per call
     * (DEV lives 3 and declared level globals, empty dynamic RAM); only the queue's bit-7 writes are simulated.
     */
    private static final class DeathPass implements CdPlayableSonic.ObjectPass {
        final CdPlayerFrameRuntime frame;
        final List<CdPlayerBody.DisplayEligibility> cuts = new ArrayList<>();
        CdPlayerFrameRuntime.BodyPublication last;
        DeathPass(CdPlayerFrameRuntime frame) { this.frame = frame; }
        @Override public CdPlayerFrameRuntime.BodyPublication runBody(int levelFrames) {
            last = frame.runBody(levelFrames, Optional.empty(), Optional.of(new CdPlayerBody.LevelContext(
                    new CdObjectPool().capture(), new CdRingGlobals(0, 3, 0, 0, 0, 0, 0, 0),
                    new CdLevelGlobals(0, 0, 0, 0, 3, 0, 2, 0, 0))));
            return last;
        }
        @Override public void enqueuePlayer(CdPlayerBody.DisplayEligibility cut) {
            cuts.add(cut); frame.publishDrawObjectOnScreen(false);
        }
        @Override public void close() { }
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void foreignActorCaptureRejectionReprojectsTheRetainedNativeOwner(CdReleaseVariant variant) throws Exception {
        var source = source(variant);
        var frame = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var actor = new CdPlayableSonic("sonic", frame, GameServices.graphics());
        var foreignFrame = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var foreign = new CdPlayableSonic("sonic", foreignFrame, GameServices.graphics());
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(new SonicCdLevel(source.assets()));
        for (int i = 1; i <= 25; i++) {
            foreignFrame.prepareInput(8); foreignFrame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
            foreign.runPlayableSlot(level, i); foreign.finishAfterCamera();
        }
        assertNotEquals(actor.getCentreX(), foreign.getCentreX());
        var before = frame.captureActor();
        assertThrows(IllegalArgumentException.class, () -> actor.restoreRewindState(foreign.captureRewindState()));
        assertEquals(before, frame.captureActor());
        assertEquals(before.state().motion().centreX16_16() >> 16, actor.getCentreX());
        assertEquals(before.state().motion().centreY16_16() >> 16, actor.getCentreY());
        assertEquals(before.state().motion().xVelocity(), actor.getXSpeed());
        assertFalse(frame.closed(), "an owner mismatch rejects before native restore and does not require a cold path");
        frame.close(); foreignFrame.close();
    }

    // Routine-4 ObjSonic_Hurt projection (Sonic/Main.asm:2351-2366). Written before production.
    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void hurtDrawObjectIsQueuedOnBothActorPathsWhereMainDisplayWouldSkipAndReplaysAfterRestore(CdReleaseVariant variant)
            throws Exception {
        var source = source(variant);
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(new SonicCdLevel(source.assets()));
        var frame = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var actor = new CdPlayableSonic("sonic", frame, GameServices.graphics());
        var sharedFrame = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var pass = new HurtPass(sharedFrame);
        var shared = new CdPlayableSonic("sonic", sharedFrame, GameServices.graphics(), pass);
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 1); actor.finishAfterCamera();
        sharedFrame.prepareInput(0); sharedFrame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        shared.runPlayableSlot(level, 1); shared.finishSharedAfterCamera();
        assertTrue(pass.cuts.isEmpty(), "Init has no DrawObject");
        installPitHurt(frame); actor.rebindAfterRestore();
        installPitHurt(sharedFrame); shared.rebindAfterRestore();

        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 2);
        // Debug low byte: both KillPlayer calls return unwritten, so Hurt keeps routine 4 and still draws.
        assertEquals(4, actor.nativeState().motion().routine());
        assertEquals(120, actor.nativeState().timers().hurt(), "no Display decrement");
        assertEquals(2040, actor.getCentreY()); assertTrue(actor.lastSoundRequests().isEmpty());
        assertTrue(frame.display().orElseThrow().queued());
        assertEquals(0, actor.nativeState().motion().spriteFlags() & 0x80, "DrawObject clears bit 7 before the camera pass");
        actor.finishAfterCamera();
        assertEquals(0x80, actor.nativeState().motion().spriteFlags() & 0x80, "the on-screen Hurt pose reaches DrawDone");
        assertEquals(actor.nativeState().animation().mapFrame(), actor.getMappingFrame());
        assertEquals(0x5D, actor.getMappingFrame(), "SonAni_Hurt dc.b 3,$5D,$FF (Data/Animations.asm:105)");
        var savedActor = actor.captureRewindState(); var savedNative = frame.captureActor(); var savedShared = frame.captureShared();
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 3); actor.finishAfterCamera();
        var advanced = frame.captureActor();
        actor.restoreRewindState(savedActor); frame.restoreShared(savedShared); actor.rebindAfterRestore();
        assertEquals(savedNative, frame.captureActor());
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 3); actor.finishAfterCamera();
        assertEquals(advanced, frame.captureActor());

        sharedFrame.prepareInput(0); sharedFrame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        shared.runPlayableSlot(level, 2);
        var published = pass.last.result();
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, published.boundary());
        var cuts = published.effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                .map(CdPlayerBody.DisplayEligibility.class::cast).toList();
        assertEquals(cuts, pass.cuts); assertTrue(cuts.getFirst().eligible());
        sharedFrame.publishDrawObjectOnScreen(true); shared.finishSharedAfterCamera();
        assertEquals(0x80, shared.nativeState().motion().spriteFlags() & 0x80);
        frame.close(); sharedFrame.close();
    }

    /** Same-owner closed-cut edit: routine-4 pit pose, debug low byte and all three Main Display skip gates. */
    private static void installPitHurt(CdPlayerFrameRuntime frame) {
        var actor = frame.captureActor(); var s = actor.state(); var m = s.motion();
        var motion = new CdPlayerMotionState(m.centreX16_16(), 2040 << 16, 0, 0, 0, 4, m.angle(), 2, 0x84,
                m.xRadius(), m.yRadius(), 0x1A, m.previousAnimation(), 0, 0, 0, 0, 0x40,
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), new CdPlayerBody.State(motion, s.animation(),
                s.header(), new CdPlayerBody.Timers(120, 0, 0, s.timers().reset()), s.history(), s.rotationDistance()), Optional.empty()));
        var shared = frame.captureShared(); var e = shared.environment(); var g = e.globals(); var mv = e.movement();
        var environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(mv.physics(),
                new CdPlayerMovement.Bounds(0, 0x2897, 0x710, 0), mv.gates(), mv.globals()), e.controls(),
                new CdPlayerBody.Globals(g.levelFrames(), g.cameraXCentre(), g.timeAttackMode(), 1, g.debugCheat(), 210,
                        g.invincible(), g.bossMusic()), e.dynamicSlots());
        frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), environment, shared.artState(),
                shared.paused(), shared.vIntClock()));
    }

    /** Recording shared-owner port: the real Frame/Body runs; only the queue's bit-7 writes are simulated. */
    private static final class HurtPass implements CdPlayableSonic.ObjectPass {
        final CdPlayerFrameRuntime frame;
        final List<CdPlayerBody.DisplayEligibility> cuts = new ArrayList<>();
        CdPlayerFrameRuntime.BodyPublication last;
        HurtPass(CdPlayerFrameRuntime frame) { this.frame = frame; }
        @Override public CdPlayerFrameRuntime.BodyPublication runBody(int levelFrames) {
            last = frame.runBody(levelFrames); return last;
        }
        @Override public void enqueuePlayer(CdPlayerBody.DisplayEligibility cut) {
            cuts.add(cut); frame.publishDrawObjectOnScreen(false);
        }
        @Override public void close() { }
    }

    private static com.openggf.game.soniccd.CdGameDataSource source(CdReleaseVariant variant) throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Supply the three retail media directories");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        try (var files = Files.list(Path.of(root).resolve(directory))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            return CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
    }
}
