package com.openggf.game.soniccd.objects;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests authored BEFORE production. ALL CASES UNCOMPILED/UNRUN.
 * Every case opts into the real USA media loader; no invented authenticated aggregate,
 * reflection, substitute Body/queue, production test constructor or fallback bytes.
 * Explicit state edits use existing public owner APIs as constructed ingress, never
 * as evidence of reaching that state by a native route. No engine/GL is required.
 */
@DisplayName("UNRUN: bounded opening object cycle over actual USA assets")
class TestCdOpeningObjectRuntime {
    private static final CameraWords PRE = new CameraWords(0, 0x70B);
    private static final DrawCameras POST = new DrawCameras(PRE, PRE, PRE);
    private static final CdOpeningObjectRuntime.LogicalBanks BANKS =
            new CdOpeningObjectRuntime.LogicalBanks(10000, 11000, 12000);
    private static final CdRingGlobals INITIAL = new CdRingGlobals(0, 3, 0, 0, 0, 0, 0, 0);
    private static CdR11aAssets mediaAssets;

    // Catches snapshot-of-active-list dispatch, omitted offscreen Mosqui, premature tail,
    // wrong ring child ordering, player Init enqueue, or use of the wrong camera cut.
    @Test void unrunFirstCycleExecutesLiveLaterChildrenAndBuildsBeforeTheOneGlobalTail() throws Exception {
        try (var f = new Fixture()) {
            f.service(0);
            assertEquals(CdOpeningObjectRuntime.Phase.SERVICED, f.runtime.phase());
            f.runtime.spawnBeforeReservedSlots();
            assertEquals(12, f.runtime.placement().status().rightRecordIndex());
            assertEquals(List.of(0x10, 0x10, 0x3D),
                    List.of(f.slot(32).id(), f.slot(33).id(), f.slot(34).id()));
            var body = f.body(1);
            assertEquals(CdPlayerBody.Boundary.INIT_RETURN, body.result().boundary());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(0x26F, f.slot(34).centreX(), "Offscreen Mosqui Init falls through into patrol");
            assertEquals(2, f.slot(34).routine());
            assertEquals(List.of(0x110, 0x210, 0x230),
                    List.of(f.slot(35).centreX(), f.slot(36).centreX(), f.slot(37).centreX()));
            assertEquals(List.of(2, 2, 2), List.of(f.slot(35).routine(), f.slot(36).routine(), f.slot(37).routine()));
            assertEquals(INITIAL, f.runtime.ringGlobals());
            var sat = f.runtime.finishAfterCamera(POST);
            assertEquals(List.of(32, 35), sat.provenance().stream().map(p -> p.slotRef().wholeIndex()).toList());
            assertEquals(List.of(2, 2), sat.provenance().stream().map(Provenance::queueBucket).toList());
            assertArrayEquals(new byte[] {0, (byte) 0x8D, 5, 1, (byte) 0xA7, (byte) 0xAE, 1, 0x68,
                    0, (byte) 0x8D, 5, 2, (byte) 0xA7, (byte) 0xAE, 1, (byte) 0x88},
                    java.util.Arrays.copyOf(sat.bytes(), 16));
            assertEquals(0, f.slot(32).unsignedByte(MAP_FRAME));
            assertTrue(f.runtime.displayedFrame().tiles().isEmpty(), "CPU build is not displayed yet");
            f.runtime.finishOrdinaryTail();
            assertEquals(new CdRingGlobals(0, 3, 0, 0, 0, 0, 7, 1), f.runtime.ringGlobals());
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, f.runtime.phase());
            assertTrue(f.runtime.lastSoundRequests().isEmpty());
            assertThrows(IllegalStateException.class, f.runtime::finishOrdinaryTail);
            assertTrue(f.runtime.closed(), "A second tail cannot spend the cycle twice");
        }
    }

    // Catches early picture publication, missing pause publication, lag re-conversion,
    // and any held-service spawn/body/dynamic/animation work.
    @Test void unrunNextPausePublishesPriorCpuButLagRetainsTheExactImmutablePicture() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle();
            var cpu = f.runtime.cpuSat();
            var pool = f.runtime.placement().capture();
            var globals = f.runtime.ringGlobals();
            f.service(0x80);
            assertTrue(f.frame.paused());
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, f.runtime.phase());
            var displayed = f.runtime.displayedFrame();
            assertEquals(8, displayed.tiles().size(), "Two native 2x2 ring pieces");
            assertSame(cpu, f.runtime.cpuSat());
            assertEquals(pool.pool().slots(), f.runtime.placement().capture().pool().slots());
            assertEquals(globals, f.runtime.ringGlobals());
            assertNotNull(f.runtime.captureClosedCut());
            f.frame.prepareInput(0);
            f.runtime.serviceVBlank(PlcLifecyclePhase.LAG);
            assertSame(displayed, f.runtime.displayedFrame());
            assertEquals(globals, f.runtime.ringGlobals());
            assertNotNull(f.runtime.captureClosedCut());
        }
    }

    // Catches capturing old DPLC pixels at CPU-build time. The artificial art ingress
    // below is explicit, not a native entry assertion; selector8 must service it first.
    @Test void unrunPublicationUsesNowServicedPlayerPixelsAndNotTheBuildTimeBank() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle();
            f.makePlayerAbsent();
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            f.body(2);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            var token = f.frame.captureActor();
            var s = token.state();
            var m = s.motion();
            var absolute = new CdPlayerMotionState((0x40 << 16) | 0x0099, 0x076B0000,
                    m.xVelocity(), m.yVelocity(), m.groundVelocity(), m.routine(), m.angle(), m.flags(),
                    0, m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(), m.playerJump(),
                    m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
            // A previously queued Ring pointer is replaced by a source-owned Sonic mapping/tile.
            // This pins pointer-selected art independently of ID and exercises the real converter.
            f.runtime.placement().pool().write(f.slot(32).moveLong(MAPPINGS, s.header().mappingAddress())
                    .moveWord(TILE, 0x780).moveByte(MAP_FRAME, 7));
            f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(token.owner(),
                    new CdPlayerBody.State(absolute, s.animation(), s.header(), s.timers(), s.history()), Optional.empty()));
            f.runtime.finishAfterCamera(POST);
            f.runtime.finishOrdinaryTail();
            var shared = f.frame.captureShared();
            byte[] bright = new byte[0x300];
            java.util.Arrays.fill(bright, (byte) 0xAB);
            var staged = new CdPlayerArtRuntime.State(bright, new byte[0x300], 0, true);
            f.frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), shared.environment(),
                    staged, shared.paused(), shared.vIntClock()));
            f.service(0x80); // ID0 avoids DPLC cue replacement; fixed dirty DMA is still real service.
            assertEquals(1, f.artPublications.size());
            assertEquals(new com.openggf.graphics.SpritePresentation.PatternVersion(
                            0xBABABABABABABABAL, 0xBABABABABABABABAL,
                            0xBABABABABABABABAL, 0xBABABABABABABABAL),
                    f.runtime.displayedFrame().patternVersions().get(BANKS.player()));
        }
    }

    // Catches reading a cached Display pose, taking SCREEN_Y from world Y or one
    // byte, choosing mappings by ID, or adding a camera to absolute-mode entries.
    @Test void unrunPlayerLensUsesLiveRawHeaderAndTheFullXFractionScreenYWord() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle();
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            var body = f.body(2);
            assertTrue(body.result().effects().stream().anyMatch(e -> e instanceof CdPlayerBody.DisplayEligibility cut && cut.eligible()));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            var token = f.frame.captureActor();
            var s = token.state();
            var m = s.motion();
            var absolute = new CdPlayerMotionState(0x00400123, 0x076B0000,
                    m.xVelocity(), m.yVelocity(), m.groundVelocity(), m.routine(), m.angle(), m.flags(),
                    0, m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(), m.playerJump(),
                    m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
            f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(token.owner(),
                    new CdPlayerBody.State(absolute, new CdPlayerBody.AnimationCursor(0, 0, 7),
                            s.header(), s.timers(), s.history()), token.display()));
            var sat = f.runtime.finishAfterCamera(POST);
            int index = -1;
            for (int i = 0; i < sat.provenance().size(); i++) {
                if (sat.provenance().get(i).slotRef().wholeIndex() == 0) { index = i; break; }
            }
            assertTrue(index >= 0);
            byte[] bytes = sat.bytes();
            assertEquals(0x10F, word(bytes, index * 8), "Frame7 first piece: $0123 plus signed -20");
            assertEquals(0x30, word(bytes, index * 8 + 6), "$40 plus signed -16, with no 128 bias");
            assertEquals(0x780, word(bytes, index * 8 + 4));
            assertEquals(0x80, f.frame.state().motion().spriteFlags(), "Build sets only bit7");
            f.runtime.finishOrdinaryTail();
        }
    }

    // Catches feeding Mosqui ingress player XY or only the high debug byte.
    // State edits here declare controlled test ingress through the existing Frame API.
    @Test void unrunMosquiReadsTheLivePostPlayerCentreAndCompleteDebugWord() throws Exception {
        for (int debug : List.of(0, 1)) {
            try (var f = new Fixture()) {
                f.firstCycle();
                f.makePlayerAbsent();
                var token = f.frame.captureActor();
                var s = token.state();
                var m = s.motion();
                var near = new CdPlayerMotionState(0x026F0000, 0x07300000,
                        m.xVelocity(), m.yVelocity(), m.groundVelocity(), m.routine(), m.angle(), m.flags(),
                        m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(),
                        m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                        m.previousPrimaryAngle(), m.previousSecondaryAngle());
                f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(token.owner(),
                        new CdPlayerBody.State(near, s.animation(), s.header(), s.timers(), s.history()), Optional.empty()));
                var shared = f.frame.captureShared();
                var e = shared.environment();
                var g = e.globals();
                var globals = new CdPlayerBody.Globals(g.levelFrames(), g.cameraXCentre(), g.timeAttackMode(),
                        debug, g.debugCheat(), g.timeWarpTimer(), g.invincible(), g.bossMusic());
                f.frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(),
                        new CdPlayerBody.Environment(e.movement(), e.controls(), globals, e.dynamicSlots()),
                        shared.artState(), shared.paused(), shared.vIntClock()));
                f.service(0);
                f.runtime.spawnBeforeReservedSlots();
                f.body(2);
                f.runtime.runDynamicSlotsAfterReservedSlots();
                assertEquals(debug == 0 ? 4 : 2, f.slot(34).routine());
                assertEquals(debug == 0 ? 0x26F : 0x26E, f.slot(34).centreX());
                f.runtime.finishAfterCamera(POST);
                f.runtime.finishOrdinaryTail();
            }
        }
    }

    // Catches ticking globals per Ring, resetting explicit initial globals,
    // gating the global tail on time stop, or overwriting pass-through fields.
    @Test void unrunTimeStopRetainsEachRingMapButNotTheIndependentGlobalTail() throws Exception {
        var initial = new CdRingGlobals(77, 9, 0xA1, 0xC0, 0xD0, 0x1234, 0, 2);
        try (var f = new Fixture(initial)) {
            f.firstCycle();
            assertEquals(0, f.slot(32).unsignedByte(MAP_FRAME));
            assertEquals(0, f.slot(35).unsignedByte(MAP_FRAME));
            assertEquals(new CdRingGlobals(77, 9, 0xA1, 0xC0, 0xD0, 0x1234, 7, 3), f.runtime.ringGlobals());
        }
    }

    // Catches a competing enqueue inside runPlayerBody, accepting a caller's
    // replacement cut, dispatching before actor enqueue, or spending it twice.
    @Test void unrunActorEnqueueSpendsExactlyThePublishedEligibleCutOnce() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle();
            f.frame.publishDrawObjectOnScreen(true);
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            var publication = f.runtime.runPlayerBody(2);
            var cut = publication.result().effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                    .map(CdPlayerBody.DisplayEligibility.class::cast).filter(CdPlayerBody.DisplayEligibility::eligible)
                    .findFirst().orElseThrow();
            assertEquals(0x80, f.frame.state().motion().spriteFlags() & 0x80, "runPlayerBody cannot perform DrawObject");
            f.runtime.enqueuePlayer(cut);
            assertEquals(0, f.frame.state().motion().spriteFlags() & 0x80);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            var sat = f.runtime.finishAfterCamera(POST);
            assertEquals(1, sat.provenance().stream().filter(p -> p.slotRef().wholeIndex() == 0)
                    .map(Provenance::queueOrdinal).distinct().count());
            assertEquals(0x80, f.frame.state().motion().spriteFlags() & 0x80);
            f.runtime.finishOrdinaryTail();
        }
        for (int misuse : List.of(0, 1, 2)) {
            try (var f = new Fixture()) {
                f.firstCycle();
                f.service(0);
                f.runtime.spawnBeforeReservedSlots();
                var publication = f.runtime.runPlayerBody(2);
                var cut = publication.result().effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                        .map(CdPlayerBody.DisplayEligibility.class::cast).filter(CdPlayerBody.DisplayEligibility::eligible)
                        .findFirst().orElseThrow();
                if (misuse == 0) {
                    var before = f.runtime.placement().capture();
                    assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
                    assertEquals(before.pool().slots(), f.runtime.placement().capture().pool().slots());
                } else if (misuse == 1) {
                    var other = new CdPlayerBody.DisplayEligibility(true, cut.centreXWord(), cut.centreYWord(),
                            cut.yRadius(), cut.spriteFlags(), cut.width() ^ 1, cut.priority());
                    assertThrows(IllegalStateException.class, () -> f.runtime.enqueuePlayer(other));
                } else {
                    f.runtime.enqueuePlayer(cut);
                    assertThrows(IllegalStateException.class, () -> f.runtime.enqueuePlayer(cut));
                }
                assertTrue(f.runtime.closed());
                assertTrue(f.frame.closed());
            }
        }
        try (var f = new Fixture()) {
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            assertEquals(CdPlayerBody.Boundary.INIT_RETURN, f.runtime.runPlayerBody(1).result().boundary());
            assertThrows(IllegalStateException.class, () -> f.runtime.enqueuePlayer(
                    new CdPlayerBody.DisplayEligibility(true, 0x40, 0x76B, 19, 4, 24, 2)));
            assertTrue(f.runtime.closed());
        }
    }

    // Catches not committing the detached item, committing it twice, selecting all
    // overlaps, or allowing reward before the dynamic slot executes.
    @Test void unrunOneDetachedItemCommitsOnlyAfterBodyThenCollectsBeforeOrdinaryRingMain() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle();
            var p = f.frame.state().motion();
            int x = p.centreX16_16() >>> 16, y = p.centreY16_16() >>> 16;
            var pool = f.runtime.placement().pool();
            pool.write(f.slot(32).moveWord(X, x).moveWord(Y, y).moveByte(SPRITE_FLAGS, 0x84));
            pool.write(f.slot(35).moveWord(X, x).moveWord(Y, y).moveByte(SPRITE_FLAGS, 0x84));
            var beforePickup = f.runtime.captureClosedCut();
            var beforeActor = f.frame.captureActor();
            var beforeShared = f.frame.captureShared();
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            var before = f.slot(32);
            var pub = f.body(2);
            var touch = pub.result().effects().stream().filter(CdPlayerBody.ObjectTouchResult.class::isInstance)
                    .map(CdPlayerBody.ObjectTouchResult.class::cast).findFirst().orElseThrow().result();
            assertEquals(32, touch.selectedSlot().orElseThrow().identity().wholeIndex());
            assertEquals(before.addByte(ROUTINE, 2), f.slot(32));
            assertEquals(4, f.slot(32).routine());
            assertEquals(2, f.slot(35).routine());
            assertEquals(0, f.runtime.ringGlobals().rings());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(6, f.slot(32).routine());
            assertEquals(4, f.slot(32).unsignedByte(MAP_FRAME));
            assertEquals(1, f.runtime.ringGlobals().rings());
            // MoveGround requests FM_CHARGESTOP even when charge is zero (Sonic/Main.asm:1033-1036).
            assertEquals(List.of(
                    new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ChargeSound(
                            com.openggf.game.soniccd.player.CdPlayerCharge.Sound.FM_CHARGESTOP)),
                    new CdOpeningObjectRuntime.RingFm(CdRing.FmSound.FM_RING)), f.runtime.lastSoundRequests());
            assertEquals(1, f.runtime.placement().savedFlag(3, 1));
            f.runtime.finishAfterCamera(POST);
            f.runtime.finishOrdinaryTail();
            assertEquals(1, f.runtime.ringGlobals().rings());
            var collected = f.runtime.captureClosedCut();
            var cpu = f.runtime.cpuSat();
            f.runtime.validateRestore(beforePickup);
            f.frame.validateActorRestore(beforeActor);
            f.frame.validateSharedRestore(beforeShared);
            f.runtime.restore(beforePickup);
            f.frame.restoreActor(beforeActor);
            f.frame.restoreShared(beforeShared);
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            f.body(2);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST);
            f.runtime.finishOrdinaryTail();
            assertEquals(collected.ringGlobals(), f.runtime.ringGlobals());
            assertEquals(collected.lastSoundRequests(), f.runtime.lastSoundRequests());
            assertEquals(collected.placement().pool().slots(), f.runtime.captureClosedCut().placement().pool().slots());
            assertArrayEquals(collected.placement().savedFlags().copy(), f.runtime.captureClosedCut().placement().savedFlags().copy());
            assertArrayEquals(cpu.bytes(), f.runtime.cpuSat().bytes());
        }
    }

    // Catches queueing Display/clearing player bit7 before the entire Body returns,
    // retrying failed Body, or dispatching dynamics after an incomplete player slot.
    @Test void unrunIncompleteBodyClosesWithoutTouchOrDisplaySideEffects() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle();
            var token = f.frame.captureActor();
            var s = token.state();
            f.frame.publishDrawObjectOnScreen(true);
            s = f.frame.state();
            f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(token.owner(),
                    new CdPlayerBody.State(s.motion(), s.animation(), s.header(), s.timers(),
                            new CdPlayerBody.History(s.history().bytes(), 0x100)), token.display()));
            // A prematurely scanned/committed item would change this routine before
            // the real Body rejects its later-than-Display history address.
            f.runtime.placement().pool().write(f.slot(32)
                    .moveWord(X, s.motion().centreX16_16() >>> 16).moveWord(Y, s.motion().centreY16_16() >>> 16)
                    .moveByte(SPRITE_FLAGS, 0x84));
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            var pool = f.runtime.placement().capture().pool().slots();
            assertThrows(CdPlayerBody.UnsupportedBody.class, () -> f.body(2));
            assertTrue(f.runtime.closed());
            assertTrue(f.frame.closed());
            assertEquals(pool, f.runtime.placement().capture().pool().slots());
            assertEquals(0x80, f.frame.state().motion().spriteFlags() & 0x80);
            assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertThrows(IllegalStateException.class, f.runtime::captureClosedCut);
        }
    }

    // Catches skipping an unknown offscreen ID or running an active-list snapshot
    // that misses a live ID changed before that physical slot's actual dispatch.
    @Test void unrunUnknownIdFailsAtItsLiveDispatchAfterEarlierSlotsHaveRun() throws Exception {
        try (var f = new Fixture()) {
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            f.body(1);
            f.runtime.placement().pool().write(f.slot(34).moveByte(ID, 0x7E).moveWord(X, 0x7FFF));
            var error = assertThrows(UnsupportedOperationException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertTrue(error.getMessage().contains("34"));
            assertEquals(2, f.slot(32).routine(), "Earlier native work remains committed");
            assertEquals(0x10, f.slot(35).id(), "Earlier parent expansion remains committed");
            assertTrue(f.runtime.closed());
            assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class, () -> f.runtime.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL));
        }
    }

    // Catches silently masking raw mapping longs to 24 bits, accepting a half-built
    // SAT, retaining a drawable stale picture after failure, or retrying queue work.
    @Test void unrunInvalidLiveMappingsCloseTheWholeComposition() throws Exception {
        try (var f = new Fixture()) {
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            f.body(1);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.placement().pool().write(f.slot(32).moveLong(MAPPINGS, f.slot(32).longBits(MAPPINGS) | 0x01000000));
            assertThrows(UnsupportedOperationException.class, () -> f.runtime.finishAfterCamera(POST));
            assertTrue(f.runtime.closed());
            assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class, f.runtime::displayedFrame);
            assertThrows(IllegalStateException.class, f.runtime::finishOrdinaryTail);
        }
    }

    // Catches clamping/recomputing a completed CPU Y, swallowing finite-picture
    // policy failure, rolling back the old cycle, or treating it as a build error.
    @Test void unrunFiniteYPublicationFailureClosesAfterTheCpuCycleAlreadyCommitted() throws Exception {
        try (var f = new Fixture()) {
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            f.body(1);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            var farY = new CameraWords(0, 0x400);
            var cpu = f.runtime.finishAfterCamera(new DrawCameras(farY, farY, farY));
            assertEquals(0x398, word(cpu.bytes(), 0));
            f.runtime.finishOrdinaryTail();
            var committed = f.runtime.placement().capture();
            var globals = f.runtime.ringGlobals();
            f.frame.prepareInput(0x80);
            assertThrows(UnsupportedOperationException.class,
                    () -> f.runtime.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE));
            assertTrue(f.runtime.closed());
            assertTrue(f.frame.closed());
            assertEquals(committed.pool().slots(), f.runtime.placement().capture().pool().slots());
            assertEquals(globals, f.runtime.ringGlobals());
            assertEquals(0x398, word(cpu.bytes(), 0));
            assertThrows(IllegalStateException.class, f.runtime::displayedFrame);
        }
    }

    // Catches incomplete captures, a second ordinary service dropping entitlement,
    // or dynamic dispatch admitted before the reserved player has returned.
    @Test void unrunEachPrematureOrDuplicatePhaseFailsClosed() throws Exception {
        List<Consumer<Fixture>> violations = List.of(
                f -> f.runtime.spawnBeforeReservedSlots(),
                f -> { f.service(0); f.runtime.runDynamicSlotsAfterReservedSlots(); },
                f -> { f.service(0); f.runtime.finishAfterCamera(POST); },
                f -> { f.service(0); f.runtime.captureClosedCut(); },
                f -> { f.service(0); f.runtime.spawnBeforeReservedSlots(); f.runtime.cpuSat(); },
                f -> { f.service(0); f.runtime.serviceVBlank(PlcLifecyclePhase.LAG); },
                f -> { f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(1); f.body(1); },
                f -> { f.service(0x80); f.runtime.spawnBeforeReservedSlots(); },
                f -> { f.frame.prepareInput(0); f.runtime.serviceVBlank(PlcLifecyclePhase.LAG); f.runtime.finishOrdinaryTail(); });
        for (var violation : violations) {
            try (var f = new Fixture()) {
                assertThrows(IllegalStateException.class, () -> violation.accept(f));
                assertTrue(f.runtime.closed());
                assertTrue(f.frame.closed());
            }
        }
    }

    // Catches losing either CPU/display generation, recapturing the pool twice,
    // source work during restore/redraw, and replay based on identity-equal Item objects.
    @Test void unrunClosedCaptureRestoresItsOwnPartitionsAndCompositeReplayMatches() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle();
            var owned = f.runtime.captureClosedCut();
            var actor = f.frame.captureActor();
            var shared = f.frame.captureShared();
            var cpu = f.runtime.cpuSat();
            var display = f.runtime.displayedFrame();
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            f.body(2);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST);
            f.runtime.finishOrdinaryTail();
            var next = f.runtime.captureClosedCut();
            var nextActor = f.frame.captureActor();
            var nextShared = f.frame.captureShared();
            var nextCpu = f.runtime.cpuSat();
            var nextDisplay = f.runtime.displayedFrame();
            f.runtime.validateRestore(owned);
            f.frame.validateActorRestore(actor);
            f.frame.validateSharedRestore(shared);
            f.runtime.restore(owned);
            assertSame(cpu, f.runtime.cpuSat());
            assertSame(display, f.runtime.displayedFrame());
            assertEquals(nextActor, f.frame.captureActor(), "Object restore never restores the actor partition");
            f.frame.restoreActor(actor);
            f.frame.restoreShared(shared);
            f.runtime.setPreScrollForeground(PRE);
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            f.body(2);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST);
            f.runtime.finishOrdinaryTail();
            assertEquals(next.placement().pool().slots(), f.runtime.captureClosedCut().placement().pool().slots());
            assertArrayEquals(next.placement().savedFlags().copy(), f.runtime.captureClosedCut().placement().savedFlags().copy());
            assertEquals(next.ringGlobals(), f.runtime.ringGlobals());
            assertEquals(next.lastSoundRequests(), f.runtime.lastSoundRequests());
            assertArrayEquals(nextCpu.bytes(), f.runtime.cpuSat().bytes());
            assertEquals(nextCpu.provenance(), f.runtime.cpuSat().provenance());
            assertEquals(nextDisplay, f.runtime.displayedFrame());
            assertEquals(nextActor, f.frame.captureActor());
            var replayShared = f.frame.captureShared();
            assertEquals(nextShared.environment(), replayShared.environment());
            assertEquals(nextShared.paused(), replayShared.paused());
            assertEquals(nextShared.vIntClock(), replayShared.vIntClock());
            assertEquals(nextShared.artState().lastFrame(), replayShared.artState().lastFrame());
            assertEquals(nextShared.artState().dirty(), replayShared.artState().dirty());
            assertArrayEquals(nextShared.artState().cpuBuffer().copy(), replayShared.artState().cpuBuffer().copy());
            assertArrayEquals(nextShared.artState().residentBank().copy(), replayShared.artState().residentBank().copy());
        }
    }

    // Catches trusting caller-supplied source claims or allowing a foreign owned
    // partition to mutate any of this runtime before all validation completes.
    @Test void unrunForeignRestoreDoesNotMutateAnyOwnedPartitionOrReopenFailure() throws Exception {
        try (var a = new Fixture(); var b = new Fixture()) {
            a.firstCycle();
            b.firstCycle();
            var original = a.runtime.captureClosedCut();
            var foreign = b.runtime.captureClosedCut();
            var cpu = a.runtime.cpuSat();
            var picture = a.runtime.displayedFrame();
            assertThrows(IllegalArgumentException.class, () -> a.runtime.restore(foreign));
            assertEquals(original.placement().pool().slots(), a.runtime.placement().capture().pool().slots());
            assertEquals(original.ringGlobals(), a.runtime.ringGlobals());
            // A failed restore protocol closes admission too; previously immutable pictures
            // and CPU objects remain unchanged, but cannot be republished as successful work.
            assertTrue(a.runtime.closed());
            assertEquals(2, cpu.spriteCount());
            assertTrue(picture.tiles().isEmpty());
            assertThrows(IllegalStateException.class, () -> a.runtime.restore(original));
        }
    }

    // Catches ignoring Mosqui's unapplied shared scratch, accepting reentry when
    // a callback swallows the nested exception, or resuming after a callback throw.
    @Test void unrunRequiredPrimaryAngleWriteOccursOnceAndReentryOrFailureCloses() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle();
            f.makePlayerAbsent();
            f.runtime.placement().pool().write(f.slot(34).moveByte(ROUTINE, 6));
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            f.body(2);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(1, f.angleWrites.size());
            assertTrue(f.angleWrites.getFirst() >= 0 && f.angleWrites.getFirst() <= 255);
            f.runtime.finishAfterCamera(POST);
            f.runtime.finishOrdinaryTail();
        }
        for (boolean swallowReentry : List.of(false, true)) {
            try (var f = new Fixture()) {
                f.firstCycle();
                var cut = f.runtime.captureClosedCut();
                f.makePlayerAbsent();
                f.runtime.placement().pool().write(f.slot(34).moveByte(ROUTINE, 6));
                f.onAngle = value -> {
                    if (!swallowReentry) throw new IllegalStateException("owned angle callback failure");
                    assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
                };
                f.service(0);
                f.runtime.spawnBeforeReservedSlots();
                f.body(2);
                assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
                assertEquals(1, f.angleWrites.size());
                assertTrue(f.runtime.closed());
                assertTrue(f.frame.closed());
                assertThrows(IllegalStateException.class, () -> f.runtime.restore(cut));
            }
        }
    }

    // Catches conversion after a serviced failure, swallowed callback reentry,
    // or permitting a failed Frame to remain coupled to a usable object owner.
    @Test void unrunArtPublicationFailureClosesBothOwnersWithoutRetry() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle();
            var shared = f.frame.captureShared();
            f.frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), shared.environment(),
                    new CdPlayerArtRuntime.State(new byte[0x300], new byte[0x300], 0, true),
                    shared.paused(), shared.vIntClock()));
            f.frame.bindArtPublication((request, state) -> {
                assertThrows(IllegalStateException.class, f.runtime::captureClosedCut);
            });
            f.frame.prepareInput(0x80);
            assertThrows(IllegalStateException.class, () -> f.runtime.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE));
            assertTrue(f.runtime.closed());
            assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class, f.runtime::displayedFrame);
        }
    }

    // Catches silently borrowing an equal-looking second aggregate, retaining
    // the terrain empty-slot policy, or minting an entry proof from a raw SAT.
    @Test void unrunFactoryRequiresExactFrameAggregateExternalTouchAndExplicitDevIngress() throws Exception {
        var assets = load();
        var otherAssets = loadFresh();
        try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets)) {
            assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                    otherAssets, frame, INITIAL, new byte[640], BANKS, PRE, value -> { }));
        }
        try (var terrain = CdPlayerFrameRuntime.terrainOnlyDevelopment(assets)) {
            assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                    assets, terrain, INITIAL, new byte[640], BANKS, PRE, value -> { }));
        }
        try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets)) {
            assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                    assets, frame, INITIAL, new byte[639], BANKS, PRE, value -> { }));
            byte[] nonempty = new byte[640]; nonempty[0] = 1;
            assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                    assets, frame, INITIAL, nonempty, BANKS, PRE, value -> { }));
            assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                    assets, frame, INITIAL, new byte[640], new CdOpeningObjectRuntime.LogicalBanks(0, 0, 0), PRE, value -> { }));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final CdR11aAssets assets;
        final CdPlayerFrameRuntime frame;
        final CdOpeningObjectRuntime runtime;
        final List<CdPlayerArtRuntime.State> artPublications = new ArrayList<>();
        final List<Integer> angleWrites = new ArrayList<>();
        Consumer<Integer> onAngle = value -> { };

        Fixture() throws Exception { this(INITIAL); }
        Fixture(CdRingGlobals initial) throws Exception {
            assets = load();
            frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets);
            frame.bindArtPublication((request, state) -> artPublications.add(state));
            runtime = CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(assets, frame, initial,
                    new byte[640], BANKS, PRE, value -> { angleWrites.add(value); onAngle.accept(value); });
        }
        void service(int held) {
            runtime.setPreScrollForeground(PRE);
            var selected = frame.prepareInput(held);
            runtime.serviceVBlank(selected.phase());
        }
        CdPlayerFrameRuntime.BodyPublication body(int levelFrames) {
            var publication = runtime.runPlayerBody(levelFrames);
            // Mirrors the root-owned actor bridge contract, not another queue owner.
            for (var effect : publication.result().effects()) {
                if (effect instanceof CdPlayerBody.DisplayEligibility cut && cut.eligible()) {
                    runtime.enqueuePlayer(cut);
                }
            }
            return publication;
        }
        CdObjectState slot(int whole) { return runtime.placement().pool().state(whole); }
        void firstCycle() {
            service(0);
            runtime.spawnBeforeReservedSlots();
            body(1);
            runtime.runDynamicSlotsAfterReservedSlots();
            runtime.finishAfterCamera(POST);
            runtime.finishOrdinaryTail();
        }
        void makePlayerAbsent() {
            var token = frame.captureActor();
            var s = token.state();
            frame.restoreActor(new CdPlayerFrameRuntime.ActorState(token.owner(),
                    new CdPlayerBody.State(s.motion(), s.animation(),
                            new CdPlayerBody.Header(0, s.header().mappingAddress(), s.header().tile(),
                                    s.header().priority(), s.header().width()), s.timers(), s.history()), Optional.empty()));
        }
        @Override public void close() { runtime.close(); }
    }

    private static int word(byte[] bytes, int at) { return ((bytes[at] & 255) << 8) | (bytes[at + 1] & 255); }

    private static synchronized CdR11aAssets load() throws Exception {
        // Reuse only immutable actual source assets, never a runtime, a pass or a
        // test result. Each factory still calls actual openingObjects() itself.
        if (mediaAssets == null) { mediaAssets = loadFresh(); }
        return mediaAssets;
    }

    private static CdR11aAssets loadFresh() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "UNRUN media opt-in: set soniccd.media.root");
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured actual USA media must contain exactly one CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            return CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
    }
}
