package com.openggf.game.soniccd.player;

import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.CdGameDataSource;
import com.openggf.game.soniccd.CdStockLaunch;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.level.SonicCdPastLevel;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdPlayerVisualAdapter;
import com.openggf.level.LevelManager;
import com.openggf.sprites.render.PlayerResidentBanks;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Headless component checks over actual original-USA products; no loaded World or Past Main claim. */
class TestCdPastPlayableSonic {
    private static CdGameDataSource launch;
    private static CdR11bAssets past;
    private static CdR11bAssets separatelyOpenedPast;

    @BeforeAll static void sources() throws Exception {
        launch = source();
        past = CdR11bAssets.fromPrograms(launch.timeWarpPrograms().orElseThrow());
        var separateLaunch = source();
        separatelyOpenedPast = CdR11bAssets.fromPrograms(separateLaunch.timeWarpPrograms().orElseThrow());
        assertNotSame(past, separatelyOpenedPast);
        assertEquals(past.identity(), separatelyOpenedPast.identity());
    }

    @BeforeEach void setup() {
        TestEnvironment.configureGameModuleFixture(new com.openggf.game.soniccd.SonicCdGameModule());
        TestEnvironment.activeGameplayMode();
        GameServices.graphics().initHeadless();
    }

    @AfterEach void close() { TestEnvironment.resetAll(); }

    @Test void constructionBorrowsActualPastVisualsAndRestoresEveryResidentTileWithoutPrimingACue() {
        var frame = frame(0, true);
        var actorCut = frame.captureActor();
        var sharedCut = frame.captureShared();
        assertThrows(IllegalStateException.class, frame::assets);
        assertTrue(frame.stageSource().ownsPast(past));
        assertSame(past.sonic(), frame.stageSource().sonic());
        assertEquals(0x230F60, frame.stageSource().mappingAddress());
        var actor = CdPlayableSonic.fromPastFrame("sonic", frame, GameServices.graphics(), null);
        assertEquals(actorCut, frame.captureActor());
        assertShared(sharedCut, frame.captureShared());
        assertSame(actorCut.state(), actor.nativeState());
        assertEquals(past.sonic().mappingFrames().size(), actor.getAnimationFrameCount());
        assertEquals(24, actor.getSpriteRenderer().patternBankCapacity());
        assertResident(actor, frame.artState());
        assertEquals(-1, PlayerResidentBanks.capture(actor.getSpriteRenderer()).lastFrame());
        assertTrue(frame.artState().dirty(), "binding must not consume pending CPU art");
        assertTrue(frame.display().isEmpty());
        assertTrue(actor.lastSoundRequests().isEmpty());
        actor.draw(); actor.draw();
        assertEquals(actorCut, frame.captureActor());
        assertShared(sharedCut, frame.captureShared());
        assertResident(actor, sharedCut.artState());
        frame.close();
    }

    @Test void exactPastAggregateOwnershipRejectsEqualReopenedPastAndPresentBeforeAnyBodyWork() {
        var frame = frame(0, false);
        var actor = CdPlayableSonic.fromPastFrame("sonic", frame, GameServices.graphics(), null);
        var actorCut = frame.captureActor(); var sharedCut = frame.captureShared();
        assertThrows(IllegalArgumentException.class, () -> actor.runPlayableSlot(null, 1));
        assertThrows(IllegalArgumentException.class, () -> actor.runPlayableSlot(level(new SonicCdLevel(launch.assets())), 1));
        assertThrows(IllegalArgumentException.class,
                () -> actor.runPlayableSlot(level(new SonicCdPastLevel(separatelyOpenedPast)), 1));
        assertEquals(actorCut, frame.captureActor()); assertShared(sharedCut, frame.captureShared());
        assertFalse(frame.closed());
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level(new SonicCdPastLevel(past)), 1);
        assertEquals(2, actor.nativeState().motion().routine());
        actor.finishAfterCamera(); frame.close();
    }

    @Test void servicedInitProjectsSourceStoresWithoutMotionDefaultsMainDisplaySoundOrAnotherArtService() {
        var frame = frame(0, true);
        var ingress = frame.state(); var beforeMotion = ingress.motion();
        var actor = CdPlayableSonic.fromPastFrame("sonic", frame, GameServices.graphics(), null);
        var level = level(new SonicCdPastLevel(past));
        assertEquals(beforeMotion.centreX16_16() >> 16, actor.getCentreX());
        assertEquals(beforeMotion.centreY16_16() >> 16, actor.getCentreY());
        assertEquals(beforeMotion.centreX16_16() & 0xFFFF, actor.getXSubpixelRaw());
        assertEquals(beforeMotion.centreY16_16() & 0xFFFF, actor.getYSubpixelRaw());
        assertEquals(0, actor.nativeState().motion().xRadius());
        assertEquals(0, actor.nativeState().header().mappingAddress());
        assertThrows(IllegalStateException.class, () -> actor.runPlayableSlot(level, 0x1234));
        frame.prepareInput(8); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        var servicedArt = frame.artState(); var servicedClock = frame.vIntClock();
        assertFalse(servicedArt.dirty());
        assertEquals(0, servicedArt.lastFrame(), "unchanged frame zero must not select a cue");
        for (int i = 0; i < 0x300; i++) {
            assertEquals(i < 0x2E0 ? 0x12 : 0x34, servicedArt.residentBank().unsignedByte(i));
        }
        assertResident(actor, servicedArt);
        actor.runPlayableSlot(level, 0x1234);
        var state = actor.nativeState(); var m = state.motion();
        // ObjSonic_Init (native pin 1ecc5f3a, Sonic/Main.asm:135-155), with miniSonic=0.
        assertEquals(2, m.routine()); assertEquals(9, m.xRadius()); assertEquals(0x13, m.yRadius());
        assertEquals(4, m.spriteFlags());
        assertEquals(new CdPlayerBody.Header(1, 0x230F60, 0x780, 2, 0x18), state.header());
        assertEquals(new CdPlayerMovement.Physics(0x600, 0xC, 0x80), frame.environment().movement().physics());
        assertEquals(beforeMotion.centreX16_16(), m.centreX16_16());
        assertEquals(beforeMotion.centreY16_16(), m.centreY16_16());
        assertEquals(beforeMotion.xVelocity(), m.xVelocity()); assertEquals(beforeMotion.yVelocity(), m.yVelocity());
        assertEquals(beforeMotion.groundVelocity(), m.groundVelocity()); assertEquals(beforeMotion.flags(), m.flags());
        assertEquals(beforeMotion.angle(), m.angle()); assertEquals(beforeMotion.animation(), m.animation());
        assertEquals(beforeMotion.previousAnimation(), m.previousAnimation());
        assertEquals(beforeMotion.previousPrimaryAngle(), m.previousPrimaryAngle());
        assertEquals(beforeMotion.previousSecondaryAngle(), m.previousSecondaryAngle());
        assertEquals(ingress.animation(), state.animation()); assertEquals(ingress.timers(), state.timers());
        assertEquals(ingress.history(), state.history()); assertEquals(ingress.objectLinks(), state.objectLinks());
        assertEquals(ingress.rotationDistance(), state.rotationDistance()); assertEquals(ingress.rotationAngle(), state.rotationAngle());
        assertEquals(0x1234, frame.environment().globals().levelFrames());
        assertTrue(frame.display().isEmpty()); assertTrue(actor.lastSoundRequests().isEmpty());
        assertSame(servicedArt, frame.artState()); assertEquals(servicedClock, frame.vIntClock());
        assertThrows(IllegalStateException.class, () -> actor.runPlayableSlot(level, 0x1234));
        assertThrows(IllegalStateException.class, actor::captureRewindState);
        assertThrows(IllegalStateException.class, actor::draw);
        actor.finishAfterCamera();
        var cut = frame.captureActor(); var shared = frame.captureShared();
        actor.draw(); actor.draw();
        assertEquals(cut, frame.captureActor()); assertShared(shared, frame.captureShared());
        assertResident(actor, servicedArt); frame.close();
    }

    @Test void loadLagWrongPhaseAndUnservicedSlotRetainTheirExistingProtocolBoundaries() {
        var frame = frame(0, false);
        var actor = CdPlayableSonic.fromPastFrame("sonic", frame, GameServices.graphics(), null);
        var level = level(new SonicCdPastLevel(past));
        var initial = frame.captureActor();
        var order = new ArrayList<String>();
        frame.serviceLevelLoad(0, () -> order.add("publication"), () -> order.add("copy/draw"), () -> order.add("fast PLC"));
        assertEquals(List.of("publication", "copy/draw", "fast PLC"), order);
        var afterLoad = frame.captureShared();
        assertThrows(IllegalStateException.class, () -> actor.runPlayableSlot(level, 1));
        assertEquals(initial, frame.captureActor()); assertShared(afterLoad, frame.captureShared());
        frame.prepareInput(8);
        assertThrows(IllegalStateException.class, () -> actor.runPlayableSlot(level, 1));
        assertThrows(IllegalStateException.class, () -> frame.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE));
        assertEquals(initial, frame.captureActor()); assertShared(afterLoad, frame.captureShared());
        assertFalse(frame.closed(), "a rejected service before any source write remains open");
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.LAG);
        var afterLag = frame.captureShared();
        assertThrows(IllegalStateException.class, () -> actor.runPlayableSlot(level, 1));
        assertEquals(initial, frame.captureActor()); assertShared(afterLag, frame.captureShared());
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 1); actor.finishAfterCamera();
        assertEquals(2, frame.state().motion().routine()); frame.close();
    }

    @Test void completeActorAndFramePartitionsRestoreAndReplayInitUnderTheirSameFreshOwner() {
        var frame = frame(0, true);
        var actor = CdPlayableSonic.fromPastFrame("sonic", frame, GameServices.graphics(), null);
        var level = level(new SonicCdPastLevel(past));
        var rewind = actor.captureRewindState();
        var coldActor = frame.captureActor(); var coldShared = frame.captureShared();
        frame.prepareInput(8); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 0x2345); actor.finishAfterCamera();
        var completedActor = frame.captureActor(); var completedShared = frame.captureShared();
        var sounds = actor.lastSoundRequests();
        actor.restoreRewindState(rewind); frame.restoreShared(coldShared); actor.rebindAfterRestore();
        assertEquals(coldActor, frame.captureActor()); assertShared(coldShared, frame.captureShared());
        assertResident(actor, coldShared.artState());
        assertEquals(coldActor.state().motion().centreX16_16() & 0xFFFF, actor.getXSubpixelRaw());
        assertEquals(coldActor.state().motion().centreY16_16() & 0xFFFF, actor.getYSubpixelRaw());
        assertTrue(actor.lastSoundRequests().isEmpty()); assertTrue(frame.display().isEmpty());
        frame.prepareInput(8); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 0x2345); actor.finishAfterCamera();
        assertEquals(completedActor, frame.captureActor()); assertShared(completedShared, frame.captureShared());
        assertEquals(sounds, actor.lastSoundRequests()); assertResident(actor, completedShared.artState());
        frame.close();
    }

    @Test void anotherFrameOverTheSamePastAggregateCannotRestoreActorOrSharedOwnership() {
        var frame = frame(0, false); var foreignFrame = frame(6, true);
        var actor = CdPlayableSonic.fromPastFrame("sonic", frame, GameServices.graphics(), null);
        var foreign = CdPlayableSonic.fromPastFrame("sonic", foreignFrame, GameServices.graphics(), null);
        assertNotSame(frame.captureActor().owner(), foreignFrame.captureActor().owner());
        var ownActor = frame.captureActor(); var ownShared = frame.captureShared();
        assertThrows(IllegalArgumentException.class, () -> actor.restoreRewindState(foreign.captureRewindState()));
        assertThrows(IllegalArgumentException.class, () -> frame.restoreShared(foreignFrame.captureShared()));
        assertEquals(ownActor, frame.captureActor()); assertShared(ownShared, frame.captureShared());
        assertEquals(ownActor.state().motion().centreX16_16() >> 16, actor.getCentreX());
        assertEquals(ownActor.state().motion().centreY16_16() >> 16, actor.getCentreY());
        assertEquals(ownActor.state().motion().routine(), actor.nativeState().motion().routine());
        assertFalse(frame.closed()); actor.rebindAfterRestore(); assertResident(actor, ownShared.artState());
        frame.close(); foreignFrame.close();
    }

    @Test void missingActualDeathOwnerStopsAtItsTypedBoundaryAndClosesTheServicedActor() {
        var frame = frame(6, false);
        var actor = CdPlayableSonic.fromPastFrame("sonic", frame, GameServices.graphics(), null);
        var before = frame.state();
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        var servicedArt = frame.artState(); var servicedClock = frame.vIntClock();
        var failure = assertThrows(CdPlayerBody.UnsupportedBody.class,
                () -> actor.runPlayableSlot(level(new SonicCdPastLevel(past)), 1));
        assertEquals(CdPlayerBody.UnsupportedBoundary.DEAD_ROUTINE, failure.boundary());
        assertSame(before, frame.state(), "no staged Body result is adopted on the owning stop");
        assertSame(servicedArt, frame.artState()); assertEquals(servicedClock, frame.vIntClock());
        assertTrue(frame.display().isEmpty()); assertTrue(actor.lastSoundRequests().isEmpty());
        assertTrue(frame.closed());
        assertThrows(IllegalStateException.class, () -> frame.prepareInput(0));
        assertThrows(IllegalStateException.class, actor::draw);
    }

    @Test void oldConstructorsStayPresentOnlyAndNamedFactoryRejectsPresentWithoutClosingIt() {
        assertThrows(NullPointerException.class, () -> new CdPlayableSonic("sonic", null, GameServices.graphics()));
        assertThrows(NullPointerException.class, () -> CdPlayableSonic.fromPastFrame("sonic", null, GameServices.graphics(), null));
        var presentFrame = CdPlayerFrameRuntime.terrainOnlyDevelopment(launch.assets());
        var cut = presentFrame.captureActor();
        assertThrows(IllegalArgumentException.class,
                () -> CdPlayableSonic.fromPastFrame("sonic", presentFrame, GameServices.graphics(), null));
        assertFalse(presentFrame.closed()); assertEquals(cut, presentFrame.captureActor());
        var presentActor = new CdPlayableSonic("sonic", presentFrame, GameServices.graphics());
        assertSame(cut.state(), presentActor.nativeState()); assertSame(launch.assets(), presentFrame.assets());
        var pastForOld = frame(0, false);
        assertThrows(IllegalStateException.class, () -> new CdPlayableSonic("sonic", pastForOld, GameServices.graphics()));
        assertTrue(pastForOld.closed());
        var pastForFailure = frame(0, false);
        var pass = mock(CdPlayableSonic.ObjectPass.class);
        assertThrows(NullPointerException.class, () -> CdPlayableSonic.fromPastFrame("sonic", pastForFailure, null, pass));
        verify(pass).close(); verifyNoMoreInteractions(pass);
        assertTrue(pastForFailure.closed()); presentFrame.close();
    }

    private static CdPlayerFrameRuntime frame(int routine, boolean dirty) {
        return CdPlayerFrameRuntime.fromPastEntry(past, state(routine), environment(), art(dirty),
                new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xFFFFFFFEL), 1));
    }

    /** Synthetic complete width witnesses, not a saved route, entry transcript, warmup or physics defaults. */
    private static CdPlayerBody.State state(int routine) {
        var motion = new CdPlayerMotionState(0x12345678, 0x23456789, -0x1234, 0x2345, -0x3456,
                routine, 0x56, 2, 0, 0, 0, 0, 0x12, 0, 0, -0x123, 0x34, 0, 0x56, 0x78);
        byte[] history = new byte[0x100];
        for (int i = 0; i < history.length; i++) history[i] = (byte) i;
        return new CdPlayerBody.State(motion, new CdPlayerBody.AnimationCursor(3, 4, 0),
                new CdPlayerBody.Header(1, 0, 0, 0, 0), new CdPlayerBody.Timers(0x78, 0x1234, 0x2345, 0x3456),
                new CdPlayerBody.History(CdAssetBytes.copyOf(history), 0xFC), 0x45,
                new CdPlayerBody.ObjectLinks(0x67, 0x89), 0xAB);
    }

    private static CdPlayerBody.Environment environment() {
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0, 0, 0),
                new CdPlayerMovement.Bounds(0, 0x2897, 0x310, 0), new CdPlayerMovement.Gates(0, 0, 0, 0, 0, 0, 0),
                new CdPlayerMovement.Globals(0x12, 0x60, 0x34, 0x56, 1, 0x789A, 0xBC, 0xDE, 0xF0, 0x12)),
                new CdPlayerBody.Controls(0, 0, 0x23, 0x45, 0),
                new CdPlayerBody.Globals(0x6789, 0xA0, 0, 0, 0, 1, 0, 0), CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }

    private static CdPlayerArtRuntime.State art(boolean dirty) {
        byte[] cpu = new byte[0x300]; byte[] resident = new byte[0x300];
        Arrays.fill(cpu, (byte) 0x12); Arrays.fill(resident, (byte) 0x34);
        return new CdPlayerArtRuntime.State(cpu, resident, 0, dirty);
    }

    private static LevelManager level(com.openggf.level.Level value) {
        var manager = mock(LevelManager.class);
        when(manager.getCurrentLevel()).thenReturn(value);
        return manager;
    }

    private static void assertArt(CdPlayerArtRuntime.State expected, CdPlayerArtRuntime.State actual) {
        assertArrayEquals(expected.cpuBuffer().copy(), actual.cpuBuffer().copy());
        assertArrayEquals(expected.residentBank().copy(), actual.residentBank().copy());
        assertEquals(expected.lastFrame(), actual.lastFrame()); assertEquals(expected.dirty(), actual.dirty());
    }

    private static void assertShared(CdPlayerFrameRuntime.SharedState expected, CdPlayerFrameRuntime.SharedState actual) {
        assertSame(expected.owner(), actual.owner()); assertEquals(expected.environment(), actual.environment());
        assertArt(expected.artState(), actual.artState()); assertEquals(expected.paused(), actual.paused());
        assertEquals(expected.vIntClock(), actual.vIntClock());
    }

    private static void assertResident(CdPlayableSonic actor, CdPlayerArtRuntime.State state) {
        var tiles = PlayerResidentBanks.capture(actor.getSpriteRenderer()).tiles();
        var expected = CdPlayerVisualAdapter.residentPatterns(state);
        assertEquals(24, tiles.length); assertEquals(24, expected.length);
        for (int tile = 0; tile < tiles.length; tile++) {
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                assertEquals(expected[tile].getPixel(x, y), tiles[tile].getPixel(x, y), "tile " + tile);
            }
        }
    }

    private static CdGameDataSource source() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Supply the original retail media directories");
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            return CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
    }
}
