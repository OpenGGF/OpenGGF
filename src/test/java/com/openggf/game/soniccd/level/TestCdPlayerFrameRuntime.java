package com.openggf.game.soniccd.level;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.player.CdPlayerCharge;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real component and load-plan decoders consume constructed bounded format inputs; this package
 * uses the existing aggregate constructor, never a production test factory,
 * reflection, mock Body/art implementation, supplied-media fallback or skip.
 * Constructed bytes are not an authenticated retail MMD/native execution.
 */
class TestCdPlayerFrameRuntime {
    @Test void namedDevelopmentIngressKeepsClearedActorUntilActualInitAndUnknownAbsoluteClock() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); var s = f.state(); var e = f.environment();
        assertEquals(64 << 16, s.motion().centreX16_16()); assertEquals(1899 << 16, s.motion().centreY16_16());
        assertEquals(new CdPlayerMotionState(64 << 16, 1899 << 16, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0), s.motion());
        assertEquals(new CdPlayerBody.Header(1, 0, 0, 0, 0), s.header());
        assertEquals(new CdPlayerBody.AnimationCursor(0, 0, 0), s.animation());
        assertEquals(new CdPlayerBody.Timers(0, 0, 0), s.timers());
        assertEquals(0, s.rotationDistance(), "Explicit cold ClearObjects byte39");
        assertArrayEquals(new byte[256], s.history().bytes().copy()); assertEquals(0, s.history().index());
        assertEquals(new CdPlayerMovement.Physics(0, 0, 0), e.movement().physics(), "Init writes physics, not ingress");
        assertEquals(new CdPlayerMovement.Bounds(0, 0x2897, 0x710, 0), e.movement().bounds());
        assertEquals(new CdPlayerMovement.Gates(0, 1, 0, 0, 0, 0, 0), e.movement().gates());
        assertEquals(new CdPlayerMovement.Globals(0, 96, 0, 0, 0, 0, 0, 0, 0, 0), e.movement().globals());
        assertEquals(new CdPlayerBody.Globals(0, 160, 0, 0, 0, 0, 0, 0), e.globals());
        assertEquals(new CdPlayerBody.Controls(0, 0, 0, 0, 0), e.controls(), "Explicit title-omission unlocked policy");
        assertEquals(CdPlayerBody.DynamicSlots.EMPTY_DEVELOPMENT_96, e.dynamicSlots());
        assertArrayEquals(new byte[0x300], f.artState().cpuBuffer().copy());
        assertArrayEquals(new byte[0x300], f.artState().residentBank().copy());
        assertEquals(0, f.artState().lastFrame()); assertFalse(f.artState().dirty());
        assertEquals(OptionalLong.empty(), f.captureShared().vIntClock().absoluteSeed());
        assertEquals(OptionalLong.empty(), f.captureShared().vIntClock().absoluteValue());
        assertEquals(0, f.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertTrue(f.display().isEmpty()); assertFalse(f.paused()); assertFalse(f.closed());
        assertTrue(fixture.dmas.isEmpty()); assertThrows(IllegalStateException.class, () -> f.runBody(1));
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 128, 255})
    void rotationByteSurvivesInitMainVIntProjectionObjectWritesAndActorReplay(int rotation) throws IOException {
        var f = new Fixture().runtime();
        var seeded = new Actor(f); seeded.rotationDistance = rotation; seeded.install(f);
        service(f, 0); f.runBody(1);
        assertEquals(rotation, f.state().rotationDistance(), "Init does not write oPlayerRotDist");
        service(f, 0); f.runBody(2);
        assertEquals(rotation, f.state().rotationDistance(), "Main/MODE/Animate preserve byte39");
        service(f, 0); f.runBody(3);
        assertEquals(rotation, f.state().rotationDistance(), "V-int art/input service preserves the actor byte");
        var saved = f.captureActor(); var savedShared = f.captureShared();
        f.publishDrawObjectOnScreen(true);
        f.applyObjectYVelocity(-0xC00);
        f.applyObjectPrimaryAngle(0x40);
        assertEquals(rotation, f.state().rotationDistance(), "Projection/word write must not reset the adjacent byte");
        var changed = new Actor(f); changed.rotationDistance = rotation ^ 0xFF; changed.install(f);
        assertEquals(rotation ^ 0xFF, f.state().rotationDistance());
        f.restoreActor(saved);
        assertEquals(rotation, f.state().rotationDistance());
        assertEquals(saved, f.captureActor());
        f.restoreShared(savedShared);
        service(f, 0); f.runBody(4);
        var replay = f.captureActor();
        f.restoreActor(saved); f.restoreShared(savedShared);
        service(f, 0); f.runBody(4);
        assertEquals(replay, f.captureActor(), "The byte belongs to actor replay, not shared state");
    }

    @ParameterizedTest @ValueSource(ints = {-1, 256})
    void rotationByteRequiresExplicitUnsignedNativeStorage(int invalid) throws IOException {
        var s = new Fixture().runtime().state();
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerBody.State(s.motion(), s.animation(),
                s.header(), s.timers(), s.history(), invalid));
    }

    @Test void serviceZeroInitOnlyServiceZeroThenMainPublishesForLaterArtService() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime();
        service(f, 0); assertEquals(0, f.state().motion().routine()); assertTrue(fixture.dmas.isEmpty());
        var init = f.runBody(1); assertEquals(CdPlayerBody.Boundary.INIT_RETURN, init.result().boundary());
        assertEquals(List.of(CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH, CdPlayerBody.NativeCall.INIT), calls(init));
        assertEquals(new CdPlayerMovement.Physics(0x600, 12, 0x80), f.environment().movement().physics());
        assertEquals(0, f.state().animation().mapFrame()); assertTrue(f.display().isEmpty());
        service(f, 0); assertTrue(fixture.dmas.isEmpty()); assertEquals(0, f.artState().lastFrame());
        var main = f.runBody(2); assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, main.result().boundary());
        assertEquals(1, f.state().animation().mapFrame()); assertEquals(0, f.artState().lastFrame());
        assertEquals(1, calls(main).stream().filter(c -> c == CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH).count());
        assertEquals(1, calls(main).stream().filter(c -> c == CdPlayerBody.NativeCall.ANIMATE).count());
        assertTrue(fixture.dmas.isEmpty(), "Main publishes a map byte without servicing it");
        service(f, 0); assertEquals(1, fixture.dmas.size()); assertEquals(1, f.artState().lastFrame());
        assertEquals(0x2E0, fixture.dmas.getFirst().bytes().length());
        for (int i = 0; i < 32; i++) assertEquals(0x5A, f.artState().residentBank().unsignedByte(i));
        for (int i = 32; i < 0x300; i++) assertEquals(0, f.artState().residentBank().unsignedByte(i));
        f.clearPendingInput();
    }

    @Test void twoStageActorBindingUsesOwnersExactAssetsAndCannotServiceBeforeBankCallbackIsBound() throws IOException {
        var assets = new Fixture().assets(); var f = CdPlayerFrameRuntime.terrainOnlyDevelopment(assets);
        assertSame(assets, f.assets()); var original = f.captureShared(); f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertEquals(original, f.captureShared()); assertFalse(f.closed());
        f.bindArtPublication((request, full) -> fail("No forced first cue")); service(f, 0); f.runBody(1);
        assertSame(assets, f.assets());
    }

    @Test void prepareIsProspectiveAndRequiresOnePendingSampleAndClosedCaptureBoundary() throws IOException {
        var f = new Fixture().runtime(); var actor = f.captureActor(); var shared = f.captureShared();
        var selected = f.prepareInput(0x80); assertTrue(selected.nextPaused());
        assertEquals(PlcLifecyclePhase.NORMAL_PAUSE, selected.phase());
        assertEquals(actor.state(), f.state()); assertEquals(shared.environment(), f.environment());
        assertSame(shared.artState(), f.artState()); assertFalse(f.paused());
        assertThrows(IllegalStateException.class, () -> f.prepareInput(0));
        assertThrows(IllegalStateException.class, f::captureActor); assertThrows(IllegalStateException.class, f::captureShared);
        f.serviceVBlank(selected.phase()); assertTrue(f.paused());
        assertEquals(0x80, f.environment().controls().rawHeld()); assertEquals(0x80, f.environment().controls().rawTap());
        assertEquals(1, f.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE));
        assertFalse(f.closed());
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 4, 8, 16, 32, 64, 128})
    void everyNativeButtonBitIsReadAsItsOwnHeldAndTapBit(int held) throws IOException {
        var f = new Fixture().runtime(); service(f, held);
        assertEquals(held, f.environment().controls().rawHeld()); assertEquals(held, f.environment().controls().rawTap());
        f.clearPendingInput(); service(f, held);
        assertEquals(held, f.environment().controls().rawHeld()); assertEquals(0, f.environment().controls().rawTap());
        f.clearPendingInput();
    }

    @Test void actionChangesWhileAnotherRemainsHeldUseLastActuallyReadByte() throws IOException {
        var f = new Fixture().runtime(); service(f, 0x10); f.clearPendingInput();
        service(f, 0x30); assertEquals(0x20, f.environment().controls().rawTap()); f.clearPendingInput();
        service(f, 0x60); assertEquals(0x40, f.environment().controls().rawTap()); f.clearPendingInput();
        service(f, 0x40); assertEquals(0, f.environment().controls().rawTap()); f.clearPendingInput();
    }

    @Test void pauseEntryRunsLastOldUnpausedTimersAndResumeRunsOldPausedGates() throws IOException {
        var f = new Fixture().runtime(); Env e = new Env(f); e.bored = 10; e.warp = 20; e.install(f);
        service(f, 0x80); assertTrue(f.paused()); assertEquals(11, f.environment().movement().globals().boredTimer());
        assertEquals(21, f.environment().globals().timeWarpTimer());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
        service(f, 0); assertTrue(f.paused()); assertEquals(11, f.environment().movement().globals().boredTimer());
        service(f, 0x80); assertFalse(f.paused());
        assertEquals(11, f.environment().movement().globals().boredTimer()); assertEquals(21, f.environment().globals().timeWarpTimer());
        f.runBody(1); assertEquals(1, f.environment().globals().levelFrames());
        assertEquals(3, f.captureShared().vIntClock().deltaSinceAdmittedCut());
    }

    @Test void heldPauseServicesArtAndRawInputButNeverDispatchesBodyOrChangesLogicalPair() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); service(f, 0x10); f.clearPendingInput();
        Env e = new Env(f); e.logicalHeld = 8; e.logicalTap = 8; e.install(f);
        restoreArt(f, new CdPlayerArtRuntime.State(fill(0x300, 0x24), new byte[0x300], 0, true));
        var actor = f.captureActor(); service(f, 0x90);
        assertTrue(f.paused()); assertEquals(1, fixture.dmas.size()); assertEquals(0x80, f.environment().controls().rawTap());
        assertEquals(8, f.environment().controls().logicalHeld()); assertEquals(8, f.environment().controls().logicalTap());
        assertEquals(actor, f.captureActor()); assertEquals(0, f.environment().globals().levelFrames());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
    }

    @Test void alreadyHeldActionThroughPauseIsNotAResampledTapOrBody() throws IOException {
        var f = new Fixture().runtime(); service(f, 0x10); f.clearPendingInput();
        var original = f.state(); service(f, 0x90); assertTrue(f.paused());
        assertEquals(0x80, f.environment().controls().rawTap()); assertEquals(original, f.state());
        service(f, 0x90); assertEquals(0, f.environment().controls().rawTap()); assertEquals(original, f.state());
        assertEquals(0, f.environment().controls().logicalHeld()); assertEquals(0, f.environment().globals().levelFrames());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
    }

    @Test void lagConsumesPendingWithoutControllerReadStartToggleOrDplcAndStillRunsFinish() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); Env e = new Env(f); e.bored = 1; e.warp = 1; e.install(f);
        byte[] cpu = fill(0x300, 0x33), resident = fill(0x300, 0x66);
        restoreArt(f, new CdPlayerArtRuntime.State(cpu, resident, 0, true));
        f.prepareInput(0x80); f.serviceVBlank(PlcLifecyclePhase.LAG);
        assertEquals(0, f.environment().controls().rawHeld()); assertEquals(0, f.environment().controls().rawTap());
        assertFalse(f.paused()); assertTrue(f.artState().dirty()); assertArrayEquals(resident, f.artState().residentBank().copy());
        assertTrue(fixture.dmas.isEmpty()); assertEquals(2, f.environment().movement().globals().boredTimer());
        assertEquals(2, f.environment().globals().timeWarpTimer()); assertEquals(1, f.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
        service(f, 0x80); assertTrue(f.paused(), "Lag's unpolled Start must still be a new edge");
        assertEquals(1, fixture.dmas.size());
    }

    @Test void absentSlotStillUploadsPendingDirtyAndDoesNoBodyCharge() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); Actor a = new Actor(f); a.slot = 0; a.charge = 20; a.install(f);
        restoreArt(f, new CdPlayerArtRuntime.State(fill(0x300, 0x22), fill(0x300, 0x77), 0, true));
        service(f, 0); assertEquals(1, fixture.dmas.size()); assertFalse(f.artState().dirty());
        assertEquals(0x22, f.artState().residentBank().unsignedByte(0x2DF));
        assertEquals(0x77, f.artState().residentBank().unsignedByte(0x2E0));
        var result = f.runBody(1); assertEquals(CdPlayerBody.Boundary.SLOT_ABSENT, result.result().boundary());
        assertEquals(20, f.state().motion().charge()); assertTrue(result.sounds().isEmpty());
    }

    @ParameterizedTest @CsvSource({"0,0,0,0", "1,0,2,0", "65535,65535,0,0", "9,20,10,21"})
    void finishTimersUseNativeActiveWordAndWrap(int bored, int warp, int expectedBored, int expectedWarp) throws IOException {
        var f = new Fixture().runtime(); Env e = new Env(f); e.bored = bored; e.warp = warp; e.install(f);
        f.prepareInput(0); f.serviceVBlank(PlcLifecyclePhase.LAG);
        assertEquals(expectedBored, f.environment().movement().globals().boredTimer());
        assertEquals(expectedWarp, f.environment().globals().timeWarpTimer());
    }

    @Test void chargeGateBlocksWarpOnlyAndNeverGetsPredispatchIncrementDuringService() throws IOException {
        var f = new Fixture().runtime(); Actor a = new Actor(f); a.charge = 20; a.install(f);
        Env e = new Env(f); e.bored = 1; e.warp = 7; e.install(f); service(f, 0);
        assertEquals(20, f.state().motion().charge()); assertEquals(2, f.environment().movement().globals().boredTimer());
        assertEquals(7, f.environment().globals().timeWarpTimer()); f.runBody(1);
        assertEquals(21, f.state().motion().charge(), "Only Body predispatch advances charge during Init");
    }

    @Test void lockedNonzeroLogicalPairRetainsActualValuesAndStartDoesNotToggle() throws IOException {
        var f = new Fixture().runtime(); Env e = new Env(f); e.locked = 1; e.logicalHeld = 8; e.logicalTap = 8; e.install(f);
        service(f, 0x84); f.runBody(1); service(f, 4); f.runBody(2);
        assertFalse(f.paused()); assertEquals(4, f.environment().controls().rawHeld());
        assertEquals(8, f.environment().controls().logicalHeld()); assertEquals(8, f.environment().controls().logicalTap());
    }

    @Test void levelWordComesOnlyFromBodyArgumentWhileVIntDeltaCountsServices() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(0x1234FFFF);
        assertEquals(0xFFFF, f.environment().globals().levelFrames());
        f.prepareInput(0); f.serviceVBlank(PlcLifecyclePhase.LAG);
        assertEquals(0xFFFF, f.environment().globals().levelFrames());
        assertEquals(2, f.captureShared().vIntClock().deltaSinceAdmittedCut());
        service(f, 0); f.runBody(0x12340000); assertEquals(0, f.environment().globals().levelFrames());
    }

    @Test void bodyMainPredispatchChargeRunsOnceAndReturnsTypedSoundsInSourceOrder() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        Actor a = new Actor(f); a.charge = 29; a.gv = 0xC00; a.invincibility = 1; a.install(f);
        Env e = new Env(f); e.invincible = 1; e.install(f); service(f, 0);
        var publication = f.runBody(2);
        assertEquals(1, calls(publication).stream().filter(c -> c == CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH).count());
        assertEquals(0, f.state().motion().charge()); assertEquals(3059, f.state().motion().groundVelocity());
        assertEquals(List.of(new CdPlayerFrameRuntime.ChargeSound(CdPlayerCharge.Sound.FM_91),
                new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_R1AMUS)), publication.sounds());
        assertThrows(UnsupportedOperationException.class, () -> publication.sounds().clear());
        assertThrows(IllegalStateException.class, () -> f.runBody(2));
    }

    @Test void modeSoundKeepsItsTypedIdentityWithoutPlayback() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        service(f, 0x10); var p = f.runBody(2);
        assertEquals(List.of(new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_JUMP)), p.sounds());
    }

    @Test void ordinaryMainAirChargeIsIncrementedByBodyPredispatchExactlyOnce() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        Actor a = new Actor(f); a.flags = 2; a.y = 200 << 16; a.charge = 20; a.install(f);
        service(f, 0); var p = f.runBody(2);
        assertEquals(21, f.state().motion().charge());
        assertEquals(1, calls(p).stream().filter(c -> c == CdPlayerBody.NativeCall.CHARGE_BEFORE_DISPATCH).count());
        assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, p.result().boundary());
    }

    @Test void failedLateBodyPublishesNoHalfStateEnvironmentDisplayOrSoundsAndCannotRetrySlot() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        Actor a = new Actor(f); a.invincibility = 1; a.install(f);
        Env e = new Env(f); e.invincible = 1; e.slots = CdPlayerBody.DynamicSlots.UNIMPLEMENTED; e.install(f);
        service(f, 0); var state = f.state(); var environment = f.environment(); var display = f.display(); var art = f.artState();
        assertEquals(CdPlayerBody.UnsupportedBoundary.TOUCH_SCAN,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(2)).boundary());
        assertSame(state, f.state()); assertSame(environment, f.environment()); assertEquals(display, f.display());
        assertSame(art, f.artState()); assertEquals(1, f.state().timers().invincibility());
        assertEquals(0, f.state().history().index()); assertEquals(1, f.environment().globals().invincible());
        assertEquals(1, f.environment().globals().levelFrames(), "Failed Body does not publish its staged level-word argument");
        assertFalse(f.closed()); assertThrows(IllegalStateException.class, () -> f.runBody(2));
        assertNotNull(f.captureActor()); assertNotNull(f.captureShared());
        e = new Env(f); e.slots = CdPlayerBody.DynamicSlots.EMPTY_DEVELOPMENT_96; e.install(f);
        service(f, 0); var completed = f.runBody(2);
        assertEquals(1, completed.sounds().stream().filter(CdPlayerFrameRuntime.BodySound.class::isInstance).count());
    }

    @Test void failedArtPublicationClosesAdmissionRetainsActualReadAndNeverRetriesPartialService() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); var oldActor = f.captureActor(); var oldShared = f.captureShared();
        Env e = new Env(f); e.bored = 1; e.warp = 1; e.install(f);
        restoreArt(f, new CdPlayerArtRuntime.State(fill(0x300, 0x44), new byte[0x300], 0, true));
        fixture.failPublication = true; f.prepareInput(8);
        assertThrows(PublicationFailure.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertTrue(f.closed()); assertEquals(8, f.environment().controls().rawHeld()); assertEquals(8, f.environment().controls().rawTap());
        assertEquals(1, f.environment().movement().globals().boredTimer()); assertEquals(1, f.environment().globals().timeWarpTimer());
        assertEquals(1, fixture.dmas.size()); assertFalse(f.artState().dirty(), "Logical service advanced; do not claim rollback");
        assertThrows(IllegalStateException.class, () -> f.prepareInput(0));
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertThrows(IllegalStateException.class, () -> f.runBody(1));
        assertThrows(IllegalStateException.class, () -> f.restoreActor(oldActor));
        assertThrows(IllegalStateException.class, () -> f.restoreShared(oldShared));
        assertEquals(1, fixture.dmas.size()); f.clearPendingInput(); f.close();
    }

    @ParameterizedTest @EnumSource(value = PlcLifecyclePhase.class, names = {"ORDINARY_LEVEL", "NORMAL_PAUSE", "LAG"}, mode = EnumSource.Mode.EXCLUDE)
    void unsupportedServicePhaseIsRejectedAtPredicateAndClearsFrameWithoutNativeMutation(PlcLifecyclePhase phase) throws IOException {
        var f = new Fixture().runtime(); var actor = f.captureActor(); var shared = f.captureShared();
        assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class, () -> f.hasPreparationBoundary(phase));
        f.prepareInput(8); assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class, () -> f.serviceVBlank(phase));
        assertEquals(actor.state(), f.state()); assertEquals(shared.environment(), f.environment());
        assertSame(shared.artState(), f.artState()); assertFalse(f.closed());
        assertNotNull(f.captureShared()); assertNotNull(f.prepareInput(0)); f.clearPendingInput();
    }

    @Test void supportedPhasesHaveNoPlcPreparationAndMismatchedSelectionNeverCommitsRaw() throws IOException {
        var f = new Fixture().runtime();
        for (var phase : List.of(PlcLifecyclePhase.ORDINARY_LEVEL, PlcLifecyclePhase.NORMAL_PAUSE, PlcLifecyclePhase.LAG)) {
            assertFalse(f.hasPreparationBoundary(phase)); f.prepareAfterLoop(phase);
        }
        assertTrue(f.captureQueueDiagnostics().isEmpty()); f.prepareInput(0x80);
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertEquals(0, f.environment().controls().rawHeld()); assertFalse(f.paused()); assertFalse(f.closed());
        assertNotNull(f.captureShared());
    }

    @ParameterizedTest @EnumSource(value = PlcLifecyclePhase.class, names = {"ORDINARY_LEVEL", "NORMAL_PAUSE", "LAG"})
    void everyAdmittedServiceRequiresOnePreparedSample(PlcLifecyclePhase phase) throws IOException {
        var f = new Fixture().runtime(); var before = f.captureShared();
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(phase));
        assertEquals(before, f.captureShared()); assertFalse(f.closed());
    }

    @Test void usaDeadRoutineSkipsStartBeforeUnsupportedBodyPredicate() throws IOException {
        var f = new Fixture().runtime(); Actor a = new Actor(f); a.routine = 6; a.install(f);
        assertFalse(f.prepareInput(0x80).nextPaused()); f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertFalse(f.paused()); assertEquals(CdPlayerBody.UnsupportedBoundary.DEAD_ROUTINE,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(1)).boundary());
        assertEquals(6, f.state().motion().routine());
    }

    @ParameterizedTest @CsvSource({"USA,16,true", "JAPAN,16,false", "EUROPE,16,false", "JAPAN,112,true", "EUROPE,112,true"})
    void pausedResetIsRejectedAtItsActualRegionalActionTapPredicate(CdReleaseVariant variant, int action, boolean reset) throws IOException {
        Fixture fixture = new Fixture(); fixture.variant = variant; var f = fixture.runtime(); service(f, 0x80);
        f.prepareInput(action);
        if (reset) {
            assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.PAUSED_RESET,
                    assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class,
                            () -> f.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE)).boundary());
            assertTrue(f.closed()); assertEquals(action, f.environment().controls().rawHeld());
        } else { f.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE); assertTrue(f.paused()); assertFalse(f.closed()); }
    }

    @Test void sourceFamilyUnsupportedServiceDoesNotInventPpLagBehavior() throws IOException {
        var f = new Fixture().runtime(); Env e = new Env(f); e.zoneAct = 0x200; e.install(f);
        f.prepareInput(8); assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY,
                assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class, () -> f.serviceVBlank(PlcLifecyclePhase.LAG)).boundary());
        assertEquals(0, f.environment().controls().rawHeld()); assertFalse(f.closed()); assertNotNull(f.captureShared());
    }

    @Test void repeatedDisplayProjectionReadsDoNoSourceWork() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); service(f, 0); f.runBody(1); service(f, 0); f.runBody(2);
        var actor = f.captureActor(); var shared = f.captureShared();
        for (int i = 0; i < 20; i++) {
            assertSame(actor.state(), f.state()); assertEquals(actor.display(), f.display());
            assertSame(shared.environment(), f.environment()); assertSame(shared.artState(), f.artState());
            assertFalse(f.paused()); assertFalse(f.closed());
        }
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared()); assertTrue(fixture.dmas.isEmpty());
    }

    @Test void capturesAreImmutableAndRestoreIsExactPartitionedAndDoesNotReinitializeOrPublishArt() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); service(f, 0); f.runBody(1); service(f, 0); f.runBody(2);
        restoreArt(f, new CdPlayerArtRuntime.State(fill(0x300, 0x31), fill(0x300, 0x73), 1, true));
        var actor = f.captureActor(); var shared = f.captureShared(); byte[] history = actor.state().history().bytes().copy();
        byte[] resident = shared.artState().residentBank().copy(); byte[] cpu = shared.artState().cpuBuffer().copy();
        Arrays.fill(history, (byte) 0xEE); Arrays.fill(resident, (byte) 0xEE); Arrays.fill(cpu, (byte) 0xEE);
        assertNotEquals(0xEE, actor.state().history().bytes().unsignedByte(0));
        assertEquals(0x31, shared.artState().cpuBuffer().unsignedByte(0)); assertEquals(0x73, shared.artState().residentBank().unsignedByte(0));
        service(f, 8); f.runBody(3); int uploads = fixture.dmas.size(); var changedEnvironment = f.environment();
        f.restoreActor(actor); assertEquals(changedEnvironment, f.environment(), "Actor restore never captures shared globals twice");
        assertEquals(actor.state(), f.state()); assertEquals(actor.display(), f.display());
        f.prepareInput(0x80); f.restoreShared(shared);
        assertEquals(actor.state(), f.state()); assertEquals(shared, f.captureShared());
        assertEquals(uploads, fixture.dmas.size()); assertEquals(2, f.state().motion().routine());
        assertEquals(1, f.state().animation().mapFrame()); assertTrue(f.artState().dirty());
        service(f, 8); var first = f.runBody(3); var firstActor = f.captureActor(); var firstShared = f.captureShared();
        f.restoreActor(actor); f.restoreShared(shared); service(f, 8); var second = f.runBody(3);
        assertEquals(first, second); assertEquals(firstActor, f.captureActor()); assertSharedValues(firstShared, f.captureShared());
    }

    @Test void sameSourceDifferentRuntimeSnapshotsRejectBeforeAnyMutationIncludingPendingSample() throws IOException {
        Fixture fixture = new Fixture(); var assets = fixture.assets();
        var a = CdPlayerFrameRuntime.terrainOnlyDevelopment(assets, (dma, state) -> { });
        var b = CdPlayerFrameRuntime.terrainOnlyDevelopment(assets, (dma, state) -> { });
        var actor = a.captureActor(); var shared = a.captureShared(); var before = b.state();
        b.prepareInput(8); assertThrows(IllegalArgumentException.class, () -> b.restoreActor(actor));
        assertThrows(IllegalArgumentException.class, () -> b.restoreShared(shared)); assertSame(before, b.state());
        assertThrows(IllegalStateException.class, b::captureShared, "Foreign rejection preserves target pending sample");
        b.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL); b.runBody(1); assertEquals(8, b.environment().controls().rawHeld());
    }

    @Test void restoreClearsUnconsumedEntitlementAndPendingWithoutColdInitialization() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1); var a = f.captureActor(); var s = f.captureShared();
        service(f, 8); assertThrows(IllegalStateException.class, f::captureActor); assertThrows(IllegalStateException.class, f::captureShared);
        assertThrows(IllegalStateException.class, () -> f.prepareInput(0)); f.restoreActor(a);
        assertThrows(IllegalStateException.class, () -> f.runBody(2)); f.prepareInput(0x80); f.restoreShared(s);
        assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared()); assertEquals(2, f.state().motion().routine());
    }

    @Test void scrollFeedbackChangesOnlyExistingSharedBoundsAndCameraY() throws IOException {
        var f = new Fixture().runtime(); var actor = f.captureActor(); var old = f.environment(); var art = f.artState();
        var bounds = new CdPlayerMovement.Bounds(8, 0x2600, 0x720, 0);
        f.applyScrollFeedback(bounds, 120); var next = f.environment();
        assertEquals(bounds, next.movement().bounds()); assertEquals(120, next.movement().globals().cameraYCentre());
        assertEquals(old.movement().physics(), next.movement().physics()); assertEquals(old.movement().gates(), next.movement().gates());
        assertEquals(old.controls(), next.controls()); assertEquals(old.globals(), next.globals());
        assertEquals(actor, f.captureActor()); assertSame(art, f.artState());
        f.prepareInput(8); assertThrows(IllegalStateException.class, () -> f.applyScrollFeedback(bounds, 96));
        f.clearPendingInput(); service(f, 0); assertThrows(IllegalStateException.class, () -> f.applyScrollFeedback(bounds, 96));
        f.runBody(1); assertEquals(bounds, f.environment().movement().bounds());
    }

    @Test void nextBodyLevelBoundReadsScrollFeedbackAndFailedFeedbackCannotHalfReplaceBounds() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1); var original = f.environment();
        var bounds = new CdPlayerMovement.Bounds(0, 0x2897, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> f.applyScrollFeedback(bounds, 65536));
        assertSame(original, f.environment()); f.applyScrollFeedback(bounds, 96);
        service(f, 0); var beforeBody = f.state(); var beforeEnvironment = f.environment();
        assertEquals(CdPlayerBody.UnsupportedBoundary.KILL_PLAYER,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(2)).boundary());
        assertSame(beforeBody, f.state()); assertSame(beforeEnvironment, f.environment());
        assertEquals(bounds, f.environment().movement().bounds());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 0x34, 0x7F, 0x80, 0xFF})
    void queuePublicationChangesOnlySpriteBitSevenAndMatchingDisplayProjection(int flags) throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime(); service(f, 0); f.runBody(1); service(f, 0); f.runBody(2);
        Actor a = new Actor(f); a.spriteFlags = flags; a.install(f);
        var actor = f.captureActor(); var shared = f.captureShared();
        f.publishDrawObjectOnScreen(true); var set = f.state();
        assertEquals(flags | 0x80, set.motion().spriteFlags()); assertEquals(flags | 0x80, f.display().orElseThrow().spriteFlags());
        f.publishDrawObjectOnScreen(false); var clear = f.state(); var m = clear.motion(); var original = actor.state().motion();
        assertEquals(flags & 0x7F, m.spriteFlags()); assertEquals(flags & 0x7F, f.display().orElseThrow().spriteFlags());
        assertEquals(original, new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                m.routine(), m.angle(), m.flags(), original.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(),
                m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle()));
        assertSame(actor.state().animation(), clear.animation()); assertSame(actor.state().header(), clear.header());
        assertSame(actor.state().timers(), clear.timers()); assertSame(actor.state().history(), clear.history());
        var d = f.display().orElseThrow(); var originalDisplay = actor.display().orElseThrow();
        assertEquals(originalDisplay, new CdPlayerBody.DisplayPublication(d.queued(), d.mappingAddress(), d.tile(), d.priority(),
                d.width(), d.mapFrame(), originalDisplay.spriteFlags(), d.centreX16_16(), d.centreY16_16()));
        assertEquals(shared, f.captureShared()); assertTrue(fixture.dmas.isEmpty());
        var off = f.captureActor(); f.publishDrawObjectOnScreen(true); f.restoreActor(off);
        assertEquals(off, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @Test void queuePublicationRequiresClosedFrameAndItsBitIsCapturedWithoutSourceWork() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1); service(f, 0); f.runBody(2);
        var before = f.state(); f.prepareInput(8);
        assertThrows(IllegalStateException.class, () -> f.publishDrawObjectOnScreen(false)); assertSame(before, f.state());
        f.clearPendingInput(); service(f, 0);
        assertThrows(IllegalStateException.class, () -> f.publishDrawObjectOnScreen(true));
        assertThrows(IllegalStateException.class, f::captureActor); f.runBody(3);
        var shared = f.captureShared(); f.publishDrawObjectOnScreen(true); var on = f.captureActor();
        assertEquals(0x80, on.state().motion().spriteFlags() & 0x80); assertEquals(shared, f.captureShared());
        f.publishDrawObjectOnScreen(false); assertEquals(0, f.state().motion().spriteFlags() & 0x80);
        f.restoreActor(on); assertEquals(on, f.captureActor()); assertEquals(shared, f.captureShared());
    }

    @Test void queuePublicationRejectsClosedFailedArtAdmissionWithoutMutation() throws IOException {
        Fixture fixture = new Fixture(); var f = fixture.runtime();
        restoreArt(f, new CdPlayerArtRuntime.State(fill(0x300, 0x22), new byte[0x300], 0, true));
        fixture.failPublication = true; f.prepareInput(8);
        assertThrows(PublicationFailure.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        var actor = f.state(); var environment = f.environment(); var display = f.display(); var art = f.artState();
        assertThrows(IllegalStateException.class, () -> f.publishDrawObjectOnScreen(true));
        assertThrows(IllegalStateException.class, () -> f.publishDrawObjectOnScreen(false));
        assertSame(actor, f.state()); assertSame(environment, f.environment()); assertEquals(display, f.display()); assertSame(art, f.artState());
    }

    @Test void clockRestorePreservesUnknownSeedAndWrapsOnlyKnownDeltaOrAdmittedAbsoluteSeed() throws IOException {
        var f = new Fixture().runtime(); var s = f.captureShared();
        f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), s.environment(), s.artState(), false,
                new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(), 0xFFFFFFFFL)));
        f.prepareInput(0); f.serviceVBlank(PlcLifecyclePhase.LAG);
        assertEquals(0, f.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertTrue(f.captureShared().vIntClock().absoluteValue().isEmpty());
        s = f.captureShared(); f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), s.environment(), s.artState(), false,
                new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0xFFFFFFFFL), 0)));
        f.prepareInput(0); f.serviceVBlank(PlcLifecyclePhase.LAG);
        assertEquals(OptionalLong.of(0), f.captureShared().vIntClock().absoluteValue());
    }

    @Test void transitionCancellationDropsPreparedOnlyAndRetainsActuallyReadLatch() throws IOException {
        var f = new Fixture().runtime(); service(f, 8); f.clearPendingInput();
        f.prepareInput(4); f.clearPendingInput(); assertEquals(8, f.environment().controls().rawHeld());
        service(f, 8); assertEquals(0, f.environment().controls().rawTap()); f.clearPendingInput();
        f.close(); assertTrue(f.closed()); assertThrows(IllegalStateException.class, () -> f.prepareInput(0));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 256, 65536})
    void inputRequiresAnActualUnsignedByte(int held) throws IOException {
        var f = new Fixture().runtime(); assertThrows(IllegalArgumentException.class, () -> f.prepareInput(held));
        assertNotNull(f.captureActor());
    }

    @Test void externalTouchFactoryRequiresArtBindingAndNeverDeclaresEmptyDynamicRam() throws IOException {
        var assets = new Fixture().assets();
        var f = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets);
        assertSame(assets, f.assets());
        assertEquals(CdPlayerBody.DynamicSlots.UNIMPLEMENTED, f.environment().dynamicSlots());
        var before = f.captureShared();
        f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertSharedValues(before, f.captureShared());
        f.bindArtPublication((dma, complete) -> { });
        service(f, 0);
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.runBody(1).result().boundary());
        service(f, 0);
        var state = f.state(); var environment = f.environment();
        assertEquals(CdPlayerBody.UnsupportedBoundary.TOUCH_SCAN,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(2)).boundary());
        assertEquals(state, f.state()); assertEquals(environment, f.environment());
        assertThrows(IllegalStateException.class, () -> f.runBody(2));
    }

    @Test void frameStagesDetachedTouchAndReplaysOnlyWhenTheCallerResuppliesItsSnapshot() throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 0x47);
        var selected = pool.state(32); var touch = frameTouchContext(pool);
        var beforeActor = f.captureActor(); var beforeShared = f.captureShared();
        service(f, 0);
        var result = f.runBody(2, Optional.of(touch));
        var item = frameTouchResult(result);
        assertEquals(CdPlayerObjectTouch.Kind.ITEM, item.kind());
        assertSame(selected, item.selectedSlot().orElseThrow());
        assertEquals(4, item.changedSlot().orElseThrow().routine());
        assertSame(selected, pool.state(32)); assertEquals(2, pool.state(32).routine());
        var afterActor = f.captureActor(); var afterShared = f.captureShared();
        f.restoreActor(beforeActor); f.restoreShared(beforeShared);
        service(f, 0);
        var replay = f.runBody(2, Optional.of(touch));
        assertEquals(afterActor, f.captureActor()); assertSharedValues(afterShared, f.captureShared());
        assertEquals(result.sounds(), replay.sounds());
        assertArrayEquals(item.changedSlot().orElseThrow().copyBytes(),
                frameTouchResult(replay).changedSlot().orElseThrow().copyBytes());
        service(f, 0);
        assertEquals(CdPlayerBody.UnsupportedBoundary.TOUCH_SCAN,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(3)).boundary(),
                "The previous ephemeral context must not be retained");
    }

    @Test void framePublishesNothingForAnUnsupportedSelectedHandlerAndConsumesOnlyThatSlotTurn() throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 7);
        var selected = pool.state(32); var context = frameTouchContext(pool);
        service(f, 0);
        var state = f.state(); var environment = f.environment(); var display = f.display(); var art = f.artState();
        var failure = assertThrows(CdPlayerBody.UnsupportedTouchHandler.class,
                () -> f.runBody(2, Optional.of(context)));
        assertEquals(CdPlayerObjectTouch.Kind.ENEMY, failure.kind());
        assertSame(selected, failure.selectedSlot());
        assertEquals(state, f.state()); assertEquals(environment, f.environment()); assertEquals(display, f.display());
        assertSame(art, f.artState()); assertSame(selected, pool.state(32));
        assertThrows(IllegalStateException.class, () -> f.runBody(2, Optional.of(context)));
        // The world/actor closes a failed serviced cycle; the pure Frame does not pretend to roll V-int back.
        f.close(); assertTrue(f.closed());
    }

    @Test void externalTouchOverloadKeepsTheExistingUnservicedAndPausedSlotAdmission() throws IOException {
        var f = externalTouchRuntime(); var context = frameTouchContext(new CdObjectPool());
        assertThrows(IllegalStateException.class, () -> f.runBody(2, Optional.of(context)));
        service(f, 0x80); assertTrue(f.paused());
        var before = f.captureActor();
        assertThrows(IllegalStateException.class, () -> f.runBody(2, Optional.of(context)));
        assertEquals(before, f.captureActor());
        service(f, 0); service(f, 0x80); assertFalse(f.paused());
        var result = f.runBody(2, Optional.of(context));
        assertEquals(CdPlayerObjectTouch.Kind.NONE, frameTouchResult(result).kind());
    }

    // Pure U1 response through the real Frame/Body path. Written before the composition.
    // Source pin 1ecc5f3a: Sonic/Main.asm:340-351, PP Player Object Collision.asm:130-298, HUD and Points/Main.asm:157-180.

    @Test void frameJumpKillCommitsTheAdoptedActorOnceAndUnwrapsContactRequestsAtTheTouchPosition() throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 0x2B); var selected = pool.state(32);
        var context = frameResponseTouch(pool, 5, 4990);
        service(f, 0x10); // CheckJump: roll bit, oAnim 2 and yv -$680 before Player_TouchEnemy.
        var p = f.runBody(2, Optional.of(context));
        assertEquals(List.of(new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_JUMP),
                new CdPlayerFrameRuntime.ContactSubCpuCommand(CdPlayerTouchResponse.SubCpuCommand.SCMD_YESSFX),
                new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_DESTROY)), p.sounds());
        var response = frameResponse(p);
        assertEquals(List.of(new CdPlayerTouchResponse.AddPoints(10, true),
                new CdPlayerTouchResponse.SubCpu(CdPlayerTouchResponse.SubCpuCommand.SCMD_YESSFX),
                new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_DESTROY)), response.calls());
        assertEquals(List.of(selected.moveByte(CdObjectState.ByteField.FLAGS, 0x80)
                .moveWord(CdObjectState.WordField.EXPLOSION_POINTS_FRAME, 0).moveByte(CdObjectState.ByteField.ID, 0x18)
                .moveByte(CdObjectState.ByteField.ROUTINE, 0).moveByte(CdObjectState.ByteField.SUBTYPE, 1)),
                response.changedSlots());
        assertEquals(new CdPlayerTouchResponse.WorldGlobals(5, 4, 1, 5000, 10000, 1), response.world());
        assertEquals(response.actor().motion(), f.state().motion());
        assertEquals(-0x580, f.state().motion().yVelocity()); // .BounceDown: -$680 + $100.
        assertEquals(2, f.environment().movement().globals().scoreChain());
        assertSame(p.result().state(), f.state()); assertSame(p.result().environment(), f.environment());
        assertSame(selected, pool.state(32)); assertEquals(2, pool.state(32).routine());
    }

    @Test void frameRestoreReplaysIdenticalPureImagesAndSoundsWithoutCapturingTheCallCut() throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 0x2B); var context = frameResponseTouch(pool, 5, 0);
        var beforeActor = f.captureActor(); var beforeShared = f.captureShared();
        service(f, 0x10); var first = f.runBody(2, Optional.of(context));
        var afterActor = f.captureActor(); var afterShared = f.captureShared();
        f.restoreActor(beforeActor); f.restoreShared(beforeShared);
        service(f, 0x10); var replay = f.runBody(2, Optional.of(context));
        assertEquals(first, replay); assertEquals(frameResponse(first), frameResponse(replay));
        assertEquals(afterActor, f.captureActor()); assertSharedValues(afterShared, f.captureShared());
        for (var type : List.of(CdPlayerFrameRuntime.ActorState.class, CdPlayerFrameRuntime.SharedState.class)) {
            for (var component : type.getRecordComponents()) {
                String name = component.getGenericType().getTypeName();
                for (String forbidden : List.of("TouchContext", "ResponseContext", "ContactGlobals", "Snapshot",
                        "CdPlayerTouchResponse")) {
                    assertFalse(name.contains(forbidden), type.getSimpleName() + "." + component.getName() + " " + name);
                }
            }
        }
    }

    @ParameterizedTest @CsvSource({"5,4", "0,6"})
    void frameCommitsAHazardHurtOrKillOnceAndTheNextBodyCallRunsHurtOrStopsAtTheUnownedDeadRoutine(int rings, int routine)
            throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 0xAB);
        service(f, 0x10);
        var p = f.runBody(2, Optional.of(frameResponseTouch(pool, rings, 0)));
        var s = f.state(); var response = frameResponse(p);
        assertEquals(routine, s.motion().routine());
        assertEquals(0x076B0000, s.motion().centreY16_16()); // CheckJump's +5, then ResetOnFloor's SUBQ.W #5.
        if (rings != 0) {
            assertEquals(List.of(new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_JUMP),
                    new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)), p.sounds());
            assertEquals(List.of(pool.state(33).moveByte(CdObjectState.ByteField.ID, 0x11)
                    .moveWord(CdObjectState.WordField.X, 64).moveWord(CdObjectState.WordField.Y, 0x770)),
                    response.changedSlots()); // The ring takes the pre-reset oY word.
            assertEquals(0x200, s.motion().xVelocity()); // Equal X: CMP.W/BCS falls through to NEG.W (:258-261).
            assertEquals(0x78, s.timers().hurt());
        } else {
            assertEquals(List.of(new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_JUMP),
                    new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP),
                    new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_HURT)), p.sounds());
            assertEquals(0x07, s.motion().stick()); assertEquals(0x6B, s.rotationDistance());
            assertEquals(0x8780, s.header().tile()); assertEquals(0, s.header().priority());
            assertEquals(0x8780, f.display().orElseThrow().tile()); assertEquals(0, f.display().orElseThrow().priority());
            assertEquals(2, displayCut(p).priority(), "The Display cut precedes the touch");
        }
        service(f, 0); var state = f.state(); var environment = f.environment();
        if (rings != 0) {
            var hurt = f.runBody(3); // ObjSonic_Hurt (Sonic/Main.asm:2351-2366) is now the owned next routine.
            assertEquals(CdPlayerBody.Boundary.HURT_RETURN, hurt.result().boundary());
            assertSame(hurt.result().state(), f.state());
        } else {
            assertEquals(CdPlayerBody.UnsupportedBoundary.DEAD_ROUTINE,
                    assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(3)).boundary());
            assertSame(state, f.state()); assertSame(environment, f.environment());
        }
    }

    // Routine-4 ObjSonic_Hurt through the real Frame/Body path. Written before production.
    // Source pin 1ecc5f3a: Sonic/Main.asm:1673-1714,2351-2391; PP Player Object Collision.asm:279-298.

    @Test void frameHurtCallCommitsOnceThenRestoresAndReplaysForwardWithoutATouchContext() throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 0xAB);
        service(f, 0x10); f.runBody(2, Optional.of(frameResponseTouch(pool, 5, 0)));
        assertEquals(4, f.state().motion().routine());
        var beforeActor = f.captureActor(); var beforeShared = f.captureShared();
        service(f, 0);
        // UNIMPLEMENTED dynamic RAM and no context: Hurt makes no Player_ObjCollide call to reject.
        var p = f.runBody(3);
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, p.result().boundary()); assertTrue(p.sounds().isEmpty());
        assertSame(p.result().state(), f.state()); assertSame(p.result().environment(), f.environment());
        var m = f.state().motion();
        // ObjMove's old words: X 64+2, Y 1899-4; then ADDI.W #$30. Nothing is probed in the empty row-6 band.
        assertEquals(66 << 16, m.centreX16_16()); assertEquals(1895 << 16, m.centreY16_16());
        assertEquals(0x200, m.xVelocity()); assertEquals(-0x3D0, m.yVelocity()); assertEquals(4, m.routine());
        assertEquals(0x78, f.state().timers().hurt()); assertEquals(8, f.state().history().index());
        assertEquals(new CdPlayerBody.DisplayEligibility(true, 66, 1895, 19, 4, 24, 2), displayCut(p));
        assertEquals(p.result().display(), f.display()); assertTrue(f.display().orElseThrow().queued());
        var afterActor = f.captureActor(); var afterShared = f.captureShared();
        f.restoreActor(beforeActor); f.restoreShared(beforeShared);
        service(f, 0); var replay = f.runBody(3);
        assertEquals(p, replay); assertEquals(afterActor, f.captureActor()); assertSharedValues(afterShared, f.captureShared());
        service(f, 0); var forward = f.runBody(4);
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, forward.result().boundary());
        assertEquals(68 << 16, f.state().motion().centreX16_16());
        assertEquals((1891 << 16) | 0x3000, f.state().motion().centreY16_16());
        assertEquals(-0x3A0, f.state().motion().yVelocity()); assertEquals(12, f.state().history().index());
        assertEquals(0x78, f.state().timers().hurt(), "the airborne hurt word stays fixed");
    }

    @Test void frameHurtPitUnwrapsBothSharedKillPlayerRequestsInCallOrderAndTheNextCallStopsAtDead() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        Actor a = new Actor(f); a.routine = 4; a.flags = 2; a.y = 2040 << 16; a.install(f);
        service(f, 0); var p = f.runBody(2);
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, p.result().boundary());
        // HurtChkLand's unsigned and LevelBound's signed bottom tests both call KillPlayer (:2376, :1705).
        assertEquals(List.of(new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_HURT),
                new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_HURT)), p.sounds());
        var s = f.state();
        assertEquals(6, s.motion().routine()); assertEquals(0x07, s.motion().stick()); assertEquals(0xF8, s.rotationDistance());
        assertEquals(0x8780, s.header().tile()); assertEquals(0, s.header().priority());
        assertEquals(0, displayCut(p).priority(), "Hurt's DrawObject follows both kills");
        assertTrue(f.display().orElseThrow().queued());
        service(f, 0); var state = f.state(); var environment = f.environment();
        assertEquals(CdPlayerBody.UnsupportedBoundary.DEAD_ROUTINE,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.runBody(3)).boundary());
        assertSame(state, f.state()); assertSame(environment, f.environment());
    }

    @Test void frameHurtLandingResetRequestIsAMovementSoundAtTheAirCollisionPosition() throws IOException {
        var f = new Fixture().runtime(); service(f, 0); f.runBody(1);
        // Ingress bit 2 lets Player_ResetOnFloor request FM_CHARGESTOP; Y1905 sensors meet the row-7 band at -2.
        Actor a = new Actor(f); a.routine = 4; a.flags = 6; a.y = 1886 << 16; a.install(f);
        service(f, 0); var p = f.runBody(2);
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, p.result().boundary());
        assertEquals(List.of(new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_CHARGESTOP)), p.sounds());
        var m = f.state().motion();
        assertEquals(2, m.routine()); assertEquals(0, m.flags()); assertEquals(1879 << 16, m.centreY16_16());
        assertEquals(120, f.state().timers().hurt());
    }

    @Test void frameResponseBoundaryInsideBodyCommitsNoStateEnvironmentDisplayOrSoundsAndConsumesTheSlot() throws IOException {
        var f = externalTouchRuntime(); var pool = frameTouchPool(f, 0x2B);
        Env e = new Env(f); e.scoreChain = 3; e.install(f); // Odd clamped chain: odd-address EnemyPoints word read (:166).
        service(f, 0x10);
        var state = f.state(); var environment = f.environment(); var display = f.display();
        var context = frameResponseTouch(pool, 5, 0);
        assertEquals(CdPlayerTouchResponse.UnsupportedBoundary.ODD_ENEMY_POINTS_ADDRESS,
                assertThrows(CdPlayerTouchResponse.UnsupportedResponse.class,
                        () -> f.runBody(2, Optional.of(context))).boundary());
        assertSame(state, f.state()); assertSame(environment, f.environment()); assertEquals(display, f.display());
        assertThrows(IllegalStateException.class, () -> f.runBody(2, Optional.of(context)));
        assertFalse(f.closed());
    }

    private static CdPlayerFrameRuntime externalTouchRuntime() throws IOException {
        var f = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(new Fixture().assets());
        f.bindArtPublication((dma, complete) -> { });
        service(f, 0); f.runBody(1);
        return f;
    }
    private static CdObjectPool frameTouchPool(CdPlayerFrameRuntime frame, int collisionType) {
        var pool = new CdObjectPool();
        pool.write(pool.state(32).moveByte(CdObjectState.ByteField.ID, 0x10)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 0x80)
                .moveByte(CdObjectState.ByteField.COLLISION_TYPE, collisionType)
                .moveByte(CdObjectState.ByteField.ROUTINE, 2)
                .moveWord(CdObjectState.WordField.X, frame.state().motion().centreX16_16() >>> 16)
                .moveWord(CdObjectState.WordField.Y, frame.state().motion().centreY16_16() >>> 16));
        return pool;
    }
    private static CdPlayerBody.TouchContext frameTouchContext(CdObjectPool pool) {
        var scanner = new CdPlayerObjectTouch(java.util.Collections.nCopies(63,
                new CdR11aObjectAssets.TouchSize(16, 16)));
        return new CdPlayerBody.TouchContext(scanner, pool.capture());
    }
    private static CdPlayerObjectTouch.Result frameTouchResult(CdPlayerFrameRuntime.BodyPublication publication) {
        return publication.result().effects().stream().filter(CdPlayerBody.ObjectTouchResult.class::isInstance)
                .map(CdPlayerBody.ObjectTouchResult.class::cast).map(CdPlayerBody.ObjectTouchResult::result)
                .findFirst().orElseThrow();
    }
    /** Runtime-shaped ephemeral owner: one response, contact (timeWarp, shield, combineRing) and world bytes. */
    private static CdPlayerBody.TouchContext frameResponseTouch(CdObjectPool pool, int rings, int score) {
        var scanner = new CdPlayerObjectTouch(java.util.Collections.nCopies(63, new CdR11aObjectAssets.TouchSize(16, 16)));
        // EnemyPoints dc.w 10,20,50,100 (PP Player Object Collision.asm:200-204): constructed literal only.
        var response = new CdPlayerTouchResponse(CdAssetBytes.copyOf(new byte[]{0, 10, 0, 20, 0, 50, 0, 100}));
        return new CdPlayerBody.TouchContext(scanner, pool.capture(), new CdPlayerBody.ResponseContext(response,
                new CdPlayerBody.ContactGlobals(0, 0, 1), new CdPlayerTouchResponse.WorldGlobals(rings, 3, 0, score, 5000, 0x80)));
    }
    private static CdPlayerTouchResponse.Result frameResponse(CdPlayerFrameRuntime.BodyPublication publication) {
        var responses = publication.result().effects().stream().filter(CdPlayerBody.ObjectTouchResponse.class::isInstance)
                .map(CdPlayerBody.ObjectTouchResponse.class::cast).toList();
        assertEquals(1, responses.size()); return responses.getFirst().result();
    }
    private static CdPlayerBody.DisplayEligibility displayCut(CdPlayerFrameRuntime.BodyPublication publication) {
        return publication.result().effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                .map(CdPlayerBody.DisplayEligibility.class::cast).findFirst().orElseThrow();
    }

    @Test void objectFloorScratchUpdatesOnlyTheExistingSharedByteAndRestoresWithoutActorWrites() throws IOException {
        var f = new Fixture().runtime();
        var actor = f.captureActor(); var shared = f.captureShared(); var oldEnvironment = f.environment();
        var oldMovement = oldEnvironment.movement(); var oldGlobals = oldMovement.globals();
        f.applyObjectPrimaryAngle(0x81);
        assertEquals(0x81, f.environment().movement().globals().primaryAngle(), "Odd scratch byte remains raw");
        assertEquals(oldGlobals.secondaryAngle(), f.environment().movement().globals().secondaryAngle());
        assertEquals(oldGlobals.lookMode(), f.environment().movement().globals().lookMode());
        assertEquals(oldGlobals.cameraYCentre(), f.environment().movement().globals().cameraYCentre());
        assertEquals(oldGlobals.boredTimer(), f.environment().movement().globals().boredTimer());
        assertEquals(oldGlobals.scoreChain(), f.environment().movement().globals().scoreChain());
        assertEquals(oldGlobals.debugAngle(), f.environment().movement().globals().debugAngle());
        assertEquals(oldGlobals.debugAngleShift(), f.environment().movement().globals().debugAngleShift());
        assertEquals(oldGlobals.debugQuadrant(), f.environment().movement().globals().debugQuadrant());
        assertEquals(oldGlobals.debugFloorDistance(), f.environment().movement().globals().debugFloorDistance());
        assertSame(oldMovement.physics(), f.environment().movement().physics());
        assertSame(oldMovement.bounds(), f.environment().movement().bounds());
        assertSame(oldMovement.gates(), f.environment().movement().gates());
        assertSame(oldEnvironment.controls(), f.environment().controls());
        assertSame(oldEnvironment.globals(), f.environment().globals());
        assertEquals(actor, f.captureActor());
        assertEquals(shared.vIntClock(), f.captureShared().vIntClock());
        f.restoreShared(shared); assertSharedValues(shared, f.captureShared());
    }

    @Test void objectScratchRequiresAnOpenClosedFrameCutAndAnUnsignedByte() throws IOException {
        var f = new Fixture().runtime();
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectPrimaryAngle(-1));
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectPrimaryAngle(256));
        f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.applyObjectPrimaryAngle(0x22));
        f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertThrows(IllegalStateException.class, () -> f.applyObjectPrimaryAngle(0x22));
        f.runBody(1);
        f.applyObjectPrimaryAngle(255); assertEquals(255, f.environment().movement().globals().primaryAngle());
        f.close(); assertThrows(IllegalStateException.class, () -> f.applyObjectPrimaryAngle(0));
    }

    @Test void objectLaunchWriteUpdatesOnlyTheActorYVelocityAndRestoresWithoutSharedWrites() throws IOException {
        var f = new Fixture().runtime();
        var actor = f.captureActor(); var shared = f.captureShared(); var oldState = f.state(); var m = oldState.motion();
        f.applyObjectYVelocity(-0xC00);
        var next = f.state().motion();
        assertEquals(-0xC00, next.yVelocity(), "Obj3DRamp .TimerSet: MOVE.W #-$C00,oYVel(a6)");
        assertEquals(m.centreX16_16(), next.centreX16_16()); assertEquals(m.centreY16_16(), next.centreY16_16());
        assertEquals(m.xVelocity(), next.xVelocity()); assertEquals(m.groundVelocity(), next.groundVelocity());
        assertEquals(m.routine(), next.routine()); assertEquals(m.angle(), next.angle()); assertEquals(m.flags(), next.flags());
        assertEquals(m.spriteFlags(), next.spriteFlags()); assertEquals(m.xRadius(), next.xRadius()); assertEquals(m.yRadius(), next.yRadius());
        assertEquals(m.animation(), next.animation()); assertEquals(m.previousAnimation(), next.previousAnimation());
        assertEquals(m.playerJump(), next.playerJump()); assertEquals(m.charge(), next.charge()); assertEquals(m.moveLock(), next.moveLock());
        assertEquals(m.stick(), next.stick()); assertEquals(m.playerControl(), next.playerControl());
        assertEquals(m.previousPrimaryAngle(), next.previousPrimaryAngle()); assertEquals(m.previousSecondaryAngle(), next.previousSecondaryAngle());
        assertSame(oldState.animation(), f.state().animation()); assertSame(oldState.header(), f.state().header());
        assertSame(oldState.timers(), f.state().timers()); assertSame(oldState.history(), f.state().history());
        assertEquals(actor.display(), f.display(), "the optional display projection carries no velocity");
        assertSharedValues(shared, f.captureShared());
        f.applyObjectYVelocity(0xC00); assertEquals(0xC00, f.state().motion().yVelocity());
        f.restoreActor(actor); assertEquals(m.yVelocity(), f.state().motion().yVelocity()); assertEquals(actor, f.captureActor());
    }

    @Test void objectLaunchWriteRequiresAnOpenClosedFrameCutAndASignedWord() throws IOException {
        var f = new Fixture().runtime();
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectYVelocity(0x8000));
        assertThrows(IllegalArgumentException.class, () -> f.applyObjectYVelocity(-0x8001));
        f.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> f.applyObjectYVelocity(0xC00));
        f.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertThrows(IllegalStateException.class, () -> f.applyObjectYVelocity(0xC00), "an entitled body slot is not a closed cut");
        f.runBody(1);
        f.applyObjectYVelocity(-0x8000); assertEquals(-0x8000, f.state().motion().yVelocity());
        f.applyObjectYVelocity(0x7FFF); assertEquals(0x7FFF, f.state().motion().yVelocity());
        f.close(); assertThrows(IllegalStateException.class, () -> f.applyObjectYVelocity(0));
    }

    private static void service(CdPlayerFrameRuntime f, int held) { f.serviceVBlank(f.prepareInput(held).phase()); }
    private static List<CdPlayerBody.NativeCall> calls(CdPlayerFrameRuntime.BodyPublication p) {
        return p.result().effects().stream().filter(CdPlayerBody.CallReturned.class::isInstance)
                .map(CdPlayerBody.CallReturned.class::cast).map(CdPlayerBody.CallReturned::call).toList();
    }
    private static void restoreArt(CdPlayerFrameRuntime f, CdPlayerArtRuntime.State art) {
        var s = f.captureShared(); f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), s.environment(), art, s.paused(), s.vIntClock()));
    }
    private static void assertSharedValues(CdPlayerFrameRuntime.SharedState a, CdPlayerFrameRuntime.SharedState b) {
        assertSame(a.owner(), b.owner()); assertEquals(a.environment(), b.environment()); assertEquals(a.paused(), b.paused());
        assertEquals(a.vIntClock(), b.vIntClock()); assertEquals(a.artState().lastFrame(), b.artState().lastFrame());
        assertEquals(a.artState().dirty(), b.artState().dirty());
        assertArrayEquals(a.artState().cpuBuffer().copy(), b.artState().cpuBuffer().copy());
        assertArrayEquals(a.artState().residentBank().copy(), b.artState().residentBank().copy());
    }
    private static byte[] fill(int count, int value) { byte[] bytes = new byte[count]; Arrays.fill(bytes, (byte) value); return bytes; }

    /** Test edit of an explicit same-owner immutable capture, not another load/factory ingress. */
    private static final class Actor {
        final CdPlayerBody.State base;
        int slot, charge, gv, invincibility, flags, y, routine, spriteFlags, rotationDistance;
        Actor(CdPlayerFrameRuntime f) { base = f.state(); slot = base.header().slotId(); charge = base.motion().charge();
            gv = base.motion().groundVelocity(); invincibility = base.timers().invincibility(); flags = base.motion().flags();
            y = base.motion().centreY16_16(); routine = base.motion().routine(); spriteFlags = base.motion().spriteFlags(); rotationDistance = base.rotationDistance(); }
        void install(CdPlayerFrameRuntime f) {
            var a = f.captureActor(); var m = base.motion(); var h = base.header();
            var motion = new CdPlayerMotionState(m.centreX16_16(), y, m.xVelocity(), m.yVelocity(), gv,
                    routine, m.angle(), flags, spriteFlags, m.xRadius(), m.yRadius(), m.animation(),
                    m.previousAnimation(), m.playerJump(), charge, m.moveLock(), m.stick(), m.playerControl(),
                    m.previousPrimaryAngle(), m.previousSecondaryAngle());
            var state = new CdPlayerBody.State(motion, base.animation(), new CdPlayerBody.Header(slot, h.mappingAddress(), h.tile(), h.priority(), h.width()),
                    new CdPlayerBody.Timers(base.timers().hurt(), invincibility, base.timers().speedShoes()), base.history(), rotationDistance);
            var display = a.display().map(p -> new CdPlayerBody.DisplayPublication(p.queued(), p.mappingAddress(), p.tile(), p.priority(),
                    p.width(), p.mapFrame(), spriteFlags, p.centreX16_16(), p.centreY16_16()));
            f.restoreActor(new CdPlayerFrameRuntime.ActorState(a.owner(), state, display));
        }
    }
    private static final class Env {
        final CdPlayerBody.Environment base;
        int bored, warp, logicalHeld, logicalTap, locked, invincible, zoneAct, scoreChain;
        CdPlayerBody.DynamicSlots slots;
        Env(CdPlayerFrameRuntime f) {
            base = f.environment(); bored = base.movement().globals().boredTimer(); warp = base.globals().timeWarpTimer();
            logicalHeld = base.controls().logicalHeld(); logicalTap = base.controls().logicalTap(); locked = base.controls().controlLocked();
            invincible = base.globals().invincible(); slots = base.dynamicSlots(); zoneAct = base.movement().gates().zoneAct();
            scoreChain = base.movement().globals().scoreChain();
        }
        void install(CdPlayerFrameRuntime f) {
            var s = f.captureShared(); var m = base.movement(); var g = m.globals(); var c = base.controls(); var b = base.globals(); var gates = m.gates();
            var environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(),
                    new CdPlayerMovement.Gates(zoneAct, gates.timeZone(), gates.miniSonic(), gates.waterSlide(), gates.windTunnel(), gates.speedShoes(), gates.timeWarpDirection()),
                    new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(), bored,
                            scoreChain, g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance())),
                    new CdPlayerBody.Controls(c.rawHeld(), c.rawTap(), logicalHeld, logicalTap, locked),
                    new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), b.debugMode(), b.debugCheat(), warp, invincible, b.bossMusic()), slots);
            f.restoreShared(new CdPlayerFrameRuntime.SharedState(s.owner(), environment, s.artState(), s.paused(), s.vIntClock()));
        }
    }

    private static final class PublicationFailure extends RuntimeException { }
    @Test void explicitDevelopmentVIntSeedDoesNotChangeAnyColdPlayerOrArtIngress() throws IOException {
        var assets = new Fixture().assets();
        try (var unknown = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets);
             var seeded = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0x12345678L)) {
            assertEquals(OptionalLong.empty(), unknown.vIntClock().absoluteValue());
            assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0x12345678L), 0), seeded.vIntClock());
            assertEquals(unknown.state(), seeded.state()); assertEquals(unknown.environment(), seeded.environment());
            assertArrayEquals(unknown.artState().cpuBuffer().copy(), seeded.artState().cpuBuffer().copy());
            assertArrayEquals(unknown.artState().residentBank().copy(), seeded.artState().residentBank().copy());
            assertEquals(unknown.artState().lastFrame(), seeded.artState().lastFrame());
            assertEquals(unknown.artState().dirty(), seeded.artState().dirty());
            assertEquals(unknown.display(), seeded.display());
            assertThrows(IllegalStateException.class, () -> seeded.serviceVBlank(PlcLifecyclePhase.LAG));
            assertEquals(0x12345678L, seeded.vIntClock().absoluteValue().orElseThrow());
        }
    }

    @Test void explicitNativeClockWrapsOnServiceAndRestoresThroughItsExistingSharedPartition() throws IOException {
        var assets = new Fixture().assets();
        try (var f = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0xFFFFFFFEL)) {
            f.bindArtPublication((request, state) -> fail("PP LAG cannot publish art"));
            var initialActor = f.state();
            f.prepareInput(0x10); f.serviceVBlank(PlcLifecyclePhase.LAG);
            assertEquals(0xFFFFFFFFL, f.vIntClock().absoluteValue().orElseThrow());
            assertEquals(1, f.vIntClock().deltaSinceAdmittedCut());
            var saved = f.captureShared();
            f.prepareInput(0); f.serviceVBlank(PlcLifecyclePhase.LAG);
            assertEquals(0, f.vIntClock().absoluteValue().orElseThrow());
            assertEquals(2, f.vIntClock().deltaSinceAdmittedCut());
            assertEquals(initialActor, f.state());
            f.restoreShared(saved);
            assertEquals(saved.vIntClock(), f.vIntClock());
            f.prepareInput(0); f.serviceVBlank(PlcLifecyclePhase.LAG);
            assertEquals(0, f.vIntClock().absoluteValue().orElseThrow());
            assertEquals(2, f.vIntClock().deltaSinceAdmittedCut());
            assertEquals(OptionalLong.of(0xFFFFFFFEL), f.vIntClock().absoluteSeed());
        }
    }

    @Test void declaredVIntSeedRequiresUnsignedLongwordAndAnActualAggregate() throws IOException {
        var assets = new Fixture().assets();
        assertThrows(IllegalArgumentException.class, () -> CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, -1));
        assertThrows(IllegalArgumentException.class,
                () -> CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0x100000000L));
        assertThrows(NullPointerException.class, () -> CdPlayerFrameRuntime.externalTouchRequiredDevelopment(null, 0));
    }

    private static final class Fixture {
        CdReleaseVariant variant = CdReleaseVariant.USA;
        boolean failPublication;
        final List<CdPlayerArtRuntime.DmaRequest> dmas = new ArrayList<>();
        CdPlayerFrameRuntime runtime() throws IOException {
            return CdPlayerFrameRuntime.terrainOnlyDevelopment(assets(), (dma, state) -> {
                dmas.add(dma); assertFalse(state.dirty());
                if (failPublication) throw new PublicationFailure();
            });
        }
        CdR11aAssets assets() throws IOException {
            var profile = CdR11aProfile.forStage(variant, CdStagePayload.R11A.stage());
            byte[] fg = new byte[338], bg = new byte[50], chunks = new byte[61952], ids = new byte[900], heights = new byte[4096];
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; Arrays.fill(fg, 2, fg.length, (byte) 2);
            ids[1] = 1; Arrays.fill(heights, 16, 32, (byte) 16);
            for (int x = 0; x < 16; x++) word(chunks, 512 + (7 * 16 + x) * 2, 0x6001);
            var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, new byte[256], heights, new byte[4096]);
            byte[] maps = new byte[398], dplcs = new byte[402], art = new byte[69472], animations = new byte[578];
            for (int i = 0; i < 198; i++) { word(maps, i * 2, 396); word(dplcs, i * 2, 396); }
            word(dplcs, 2, 398); word(dplcs, 398, 1); word(dplcs, 400, 0); Arrays.fill(art, 0, 32, (byte) 0x5A);
            var sonic = CdSonicVisualAssets.decode(maps, dplcs, art);
            for (int i = 0; i < 60; i++) word(animations, i * 2, 120);
            animations[120] = 3; animations[121] = 1; animations[122] = (byte) 0xFF;
            byte[] sine = new byte[640]; word(sine, 0x40 * 2, 256); word(sine, 0xC0 * 2, -256);
            byte[] atan = new byte[257];
            for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8);
            var player = CdR11aPlayerAssets.decode(animations, new byte[60], new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF},
                    new byte[]{0x7F, 0x7F, 0x7F, 0x7F}, sine, atan, sonic);
            var image = loadPlanImage(profile); var plan = CdR11aLoadPlan.decode(profile, image);
            return new CdR11aAssets(profile, null, image, null, null, terrain, plan, null, sonic, player, null);
        }
    }

    /** Complete bounded inputs for the real load-plan decoder, deliberately no retail identity assertion. */
    private static CdMmdImage loadPlanImage(CdR11aProfile profile) throws IOException {
        byte[] bytes = new byte[CdR11aProfile.FILE_BYTES];
        int index = profile.range(CdR11aAsset.LEVEL_DATA_INDEX).fileOffset();
        longword(bytes, index, 0x0323C4EE); longword(bytes, index + 4, 0x0223B0A8); longword(bytes, index + 8, 0x210000);
        word(bytes, index + 12, 0x81); word(bytes, index + 14, 0x0505);
        int start = profile.range(CdR11aAsset.LEVEL_START_LOC).fileOffset(); word(bytes, start, 64); word(bytes, start + 2, 1899);
        int bound = profile.range(CdR11aAsset.CAM_BOUNDS).fileOffset();
        int[] bounds = {4, 0, 0x2897, 0, 0x710, 96}; for (int i = 0; i < bounds.length; i++) word(bytes, bound + i * 2, bounds[i]);
        int layouts = profile.range(CdR11aAsset.LEVEL_LAYOUTS).fileOffset();
        word(bytes, layouts, profile.range(CdR11aAsset.LAYOUT_FG).fileOffset() - layouts);
        word(bytes, layouts + 2, profile.range(CdR11aAsset.LAYOUT_BG).fileOffset() - layouts);
        int collision = profile.range(CdR11aAsset.LEVEL_COL_INDEX).fileOffset();
        for (int i = 0; i < 8; i++) longword(bytes, collision + i * 4, profile.range(CdR11aAsset.LEVEL_COLLISION).nativeAddress());
        int plc = profile.range(CdR11aAsset.PLC_LISTS).fileOffset();
        for (int i = 0; i < 19; i++) word(bytes, plc + i * 2, 38);
        word(bytes, plc + 38, 0); longword(bytes, plc + 40, profile.range(CdR11aAsset.ART_LEVEL_TILES).nativeAddress()); word(bytes, plc + 44, 0);
        for (int i = 0; i < 5; i++) {
            word(bytes, profile.range(CdR11aAsset.SECTION_RANGES).fileOffset() + 2 * i, i == 4 ? 65535 : (i + 1) * 0x800);
            word(bytes, profile.range(CdR11aAsset.SECTION_INIT_PLCS).fileOffset() + 2 * i, 0);
            word(bytes, profile.range(CdR11aAsset.SECTION_UPDATE_PLCS).fileOffset() + 2 * i, 0);
        }
        int palette = profile.range(CdR11aAsset.PALETTE_TABLE).fileOffset();
        longword(bytes, palette + 3 * 8, profile.range(CdR11aAsset.PAL_SONIC).nativeAddress()); word(bytes, palette + 3 * 8 + 4, 0xFB00); word(bytes, palette + 3 * 8 + 6, 7);
        longword(bytes, palette + 5 * 8, profile.range(CdR11aAsset.PAL_LEVEL).nativeAddress()); word(bytes, palette + 5 * 8 + 4, 0xFB20); word(bytes, palette + 5 * 8 + 6, 23);
        return CdMmdImage.parse(bytes);
    }
    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static void longword(byte[] bytes, int at, int value) { word(bytes, at, value >>> 16); word(bytes, at + 2, value); }
}
