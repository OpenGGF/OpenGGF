package com.openggf.game.soniccd.objects;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.resources.CdObjectPresentation;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests authored BEFORE production. Bounded execution is recorded in the dated CD implementation plan.
 * Every case opts into the real USA media loader; no invented authenticated aggregate,
 * reflection, substitute Body/queue, production test constructor or fallback bytes.
 * Explicit state edits use existing public owner APIs as constructed ingress, never
 * as evidence of reaching that state by a native route. No engine/GL is required.
 */
@DisplayName("Bounded opening object cycle over actual USA assets")
class TestCdOpeningObjectRuntime {
    private static final CameraWords PRE = new CameraWords(0, 0x70B);
    private static final DrawCameras POST = new DrawCameras(PRE, PRE, PRE);
    private static final CdOpeningObjectRuntime.LogicalBanks BANKS =
            new CdOpeningObjectRuntime.LogicalBanks(10000, 11000, 12000);
    private static final CdPlayerBody.ContactGlobals CONTACT = new CdPlayerBody.ContactGlobals(0, 0, 0);
    private static final CdOpeningObjectRuntime.ScoreGlobals SCORE = new CdOpeningObjectRuntime.ScoreGlobals(0, 5000, 0);
    private static final CdRingGlobals INITIAL = new CdRingGlobals(0, 3, 0, 0, 0, 0, 0, 0);
    /** The module's declared DEV post-LevelStart level globals, supplied explicitly as ingress. */
    private static final CdLevelGlobals LEVEL = new CdLevelGlobals(0, 0, 0, 0, 0x03, 0, 2, 0, 0);
    /** Nonzero count/fade bytes so the restart zero call's long clear is observable (Sonic/Main.asm:2466). */
    private static final CdLevelGlobals RESTART_LEVEL = new CdLevelGlobals(0, 1, 0, 0, 3, 0x112233, 0x80, 8, 0);
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
                    new CdPlayerBody.State(absolute, s.animation(), s.header(), s.timers(), s.history(), s.rotationDistance(), s.objectLinks()), Optional.empty()));
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
                            s.header(), s.timers(), s.history(), s.rotationDistance(), s.objectLinks()), token.display()));
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
                        new CdPlayerBody.State(near, s.animation(), s.header(), s.timers(), s.history(), s.rotationDistance(), s.objectLinks()), Optional.empty()));
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
                            new CdPlayerBody.History(s.history().bytes(), 0x100), s.rotationDistance(), s.objectLinks()), token.display()));
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
                    otherAssets, frame, INITIAL, CONTACT, SCORE, LEVEL, new byte[640], BANKS, PRE, value -> { }));
        }
        try (var terrain = CdPlayerFrameRuntime.terrainOnlyDevelopment(assets)) {
            assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                    assets, terrain, INITIAL, CONTACT, SCORE, LEVEL, new byte[640], BANKS, PRE, value -> { }));
        }
        try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets)) {
            assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                    assets, frame, INITIAL, CONTACT, SCORE, LEVEL, new byte[639], BANKS, PRE, value -> { }));
            byte[] nonempty = new byte[640]; nonempty[0] = 1;
            assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                    assets, frame, INITIAL, CONTACT, SCORE, LEVEL, nonempty, BANKS, PRE, value -> { }));
            assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                    assets, frame, INITIAL, CONTACT, SCORE, LEVEL, new byte[640], new CdOpeningObjectRuntime.LogicalBanks(0, 0, 0), PRE, value -> { }));
        }
    }

    // Catches failing the camera-$200 Animal record, running an Animal body on the inert
    // Present/projector-intact path, or drawing/touching an object that native never draws.
    @Test void unrunCameraChunk200StreamsTheInertAnimalWhichNeverDrawsOrFails() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle();
            var camera = new CameraWords(0x200, 0x70B);
            f.service(0, camera);
            f.runtime.spawnBeforeReservedSlots();
            assertEquals(24, f.runtime.placement().status().rightRecordIndex(), "rows 0..23 consumed below $480");
            assertEquals(0x3D, f.slot(38).id(), "common row 13: damaged Mosqui");
            assertEquals(0x24, f.slot(39).id(), "common row 22: the Present Animal");
            assertEquals(0x468, f.slot(39).centreX());
            assertEquals(0x6E0, f.slot(39).centreY());
            f.body(2);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            var animal = f.slot(39);
            assertEquals(0x24, animal.id(), "CheckObjDespawn kept it: $400-$180 = $280");
            assertEquals(0, animal.routine(), "the popped return address skips Init");
            assertEquals(0, animal.longBits(MAPPINGS));
            assertEquals(0, animal.word(TILE));
            assertEquals(0, animal.spriteFlags() & 0x80);
            assertEquals(0x34F, f.slot(38).centreX(), "damaged Mosqui Init falls into Main with the half-pixel delta");
            var sat = f.runtime.finishAfterCamera(new DrawCameras(camera, camera, camera));
            assertTrue(sat.provenance().stream().noneMatch(p -> p.slotRef().wholeIndex() == 39), "never drawn");
            f.runtime.finishOrdinaryTail();
            assertEquals(new CdOpeningObjectRuntime.DevGlobals(0, 0), f.runtime.devGlobals());
            f.service(0x80, camera);
            assertNotNull(f.runtime.displayedFrame());
        }
    }

    // Constructed camera ingress: catches skipping FloatBlock, replaying ChildCreated, or a stale pool walk.
    @Test void cameraChunk380RunsThePlantFamilyAndOffscreenFloatBlockWithoutPlayerWrites() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle();
            var camera = new CameraWords(0x380, 0x70B);
            f.service(0, camera);
            f.runtime.spawnBeforeReservedSlots();
            assertEquals(42, f.runtime.placement().status().rightRecordIndex(), "rows 0..41 consumed below $600");
            assertEquals(List.of(0x3D, 0x24, 0x3D, 0x10, 0x2C, 0x12), List.of(f.slot(38).id(), f.slot(39).id(),
                    f.slot(40).id(), f.slot(41).id(), f.slot(42).id(), f.slot(43).id()));
            assertEquals(0x590, f.slot(42).centreX());
            assertEquals(1, f.slot(42).subtype());
            f.body(2);
            var playerCut = f.frame.captureActor();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertFalse(f.runtime.closed()); assertFalse(f.frame.closed());
            assertEquals(playerCut.state(), f.frame.state(), "offscreen TopSolid calls do not write the player");
            var parent = f.slot(42);
            assertEquals(2, parent.routine(), "Obj3DPlant_Init commits before the later FloatBlock dispatch");
            assertEquals(0, parent.subtype(), "BCLR #0");
            assertEquals(0x5D0, parent.centreX());
            assertEquals(0x590, parent.word(PLANT_ANCHOR_X));
            assertEquals(0x5D0, parent.word(PLANT_BASE_X));
            assertEquals(0x20E020, parent.longBits(MAPPINGS));
            assertEquals(0x4424, parent.word(TILE));
            assertEquals(0x18, parent.unsignedByte(WIDTH));
            assertEquals(0x14, parent.unsignedByte(Y_RADIUS));
            var mains = new ArrayList<Integer>();
            var smalls = new ArrayList<Integer>();
            for (int whole = 32; whole < 128; whole++) {
                var s = f.slot(whole);
                if (whole == 42 || s.id() != 0x2C) { continue; }
                assertEquals(2, s.routine(), "slot " + whole);
                assertEquals(0x590, s.word(PLANT_ANCHOR_X), "shared original anchor");
                assertTrue(whole < 42, "first-free children landed in slots freed earlier this pass and wait for the next pass");
                if (s.subtype() == 0) { mains.add(whole); assertEquals(0x610, s.centreX()); }
                else { smalls.add(whole); assertEquals(1, s.unsignedByte(MAP_FRAME)); assertEquals(4, s.unsignedByte(PRIORITY)); }
            }
            assertEquals(1, mains.size());
            assertEquals(3, smalls.size());
            assertEquals(List.of(0x590, 0x5F0, 0x530), smalls.stream().map(w -> f.slot(w).centreX()).toList());
            // Derived from the pinned first cycle: 32/35 were drawn (bit 7 kept them), 33/36/37 and 34 despawned first.
            assertEquals(List.of(33), mains);
            assertEquals(List.of(34, 36, 37), smalls);
            var block = f.slot(43);
            assertEquals(List.of(0x12, 2, 0x20A3FA, 0x4334, 0, 4), List.of(block.id(), block.routine(),
                    block.longBits(MAPPINGS), block.word(TILE), block.unsignedByte(MAP_FRAME), block.spriteFlags()));
            var sat = f.runtime.finishAfterCamera(new DrawCameras(camera, camera, camera));
            assertTrue(sat.provenance().stream().noneMatch(p -> p.slotRef().wholeIndex() == 43));
            f.runtime.finishOrdinaryTail();
        }
    }

    // Constructed completed-player cut, not a controller route. Catches omitted/doubled TopSolid commits,
    // integer-only Y stores losing fractions, the horizontal longword carry, dropped angle buffers, stale SAT
    // player sampling, missing Float art and capture partitions that discard a real stand link.
    @Test void floatBlockCommitsEachCallWithFractionsAndAnglesThenPublishesTheLivePicture() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            seedSolidPlayer(f, 2, 0, 0, 0x100, 0x100, (64 << 16) | 0x1234, (0x721 << 16) | 0x5678);
            pool.write(floatBlock(pool.state(40), 8, 0x84, 0).moveWord(X_VELOCITY, 0x80));
            var before = f.frame.state();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            var after = f.frame.state(); var m = after.motion();
            assertEquals((65 << 16) | 0x1234, m.centreX16_16(), "two native X longword moves of $80<<8");
            assertEquals((0x721 << 16) | 0x5678, m.centreY16_16(), "word-only alignment keeps the fraction");
            assertEquals(0, m.yVelocity()); assertEquals(0x100, m.groundVelocity());
            assertEquals(8, m.flags() & 10); assertEquals(new CdPlayerBody.ObjectLinks(0, 40), after.objectLinks());
            assertEquals(8, f.slot(40).flags() & 8); assertEquals(8, f.slot(40).routine());
            assertEquals(5, f.slot(40).unsignedByte(MAP_FRAME));
            assertEquals(0xC0, f.frame.environment().movement().globals().primaryAngle());
            assertEquals(0xC0, f.frame.environment().movement().globals().secondaryAngle());
            assertEquals(before.history(), after.history()); assertEquals(before.animation(), after.animation());
            var sat = f.runtime.finishAfterCamera(POST);
            assertTrue(sat.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 40));
            assertEquals(0x433A, word(sat.bytes(), 4), "priority1 Float frame5 precedes priority2 player");
            assertEquals(0x80 + 64 - 12, word(sat.bytes(), 6));
            int playerEntry = -1;
            for (int i = 0; i < sat.provenance().size(); i++) {
                if (sat.provenance().get(i).slotRef().wholeIndex() == 0) { playerEntry = i; break; }
            }
            assertTrue(playerEntry >= 0); assertEquals(0, m.spriteFlags() & 3, "fixture has no whole-object flips");
            var sonic = f.assets.sonic().mappingFrames().get(after.animation().mapFrame());
            assertTrue(sonic.pieceCount() > 0);
            assertEquals((0x80 + 65 + sonic.bytes().signedByte(5)) & 0x1FF, word(sat.bytes(), playerEntry * 8 + 6),
                    "player SAT samples the post-object X65, not the pre-object X64");
            assertEquals((0x80 + 0x721 - PRE.yWord() + sonic.bytes().signedByte(1)) & 0x1FF,
                    word(sat.bytes(), playerEntry * 8), "post-object player geometry uses the same completed camera");
            f.runtime.finishOrdinaryTail();
            var actor = f.frame.captureActor(); var shared = f.frame.captureShared(); var objects = f.runtime.captureClosedCut();
            var expected = f.frame.state();
            f.service(0x80); assertTrue(f.frame.paused());
            for (int i = 6; i < 15; i++) {
                var pixels = f.assets.openingObjects().floatBlockArtBank().tile(0x334 + i);
                assertEquals(CdObjectPresentation.patternVersion(pixels),
                        f.runtime.displayedFrame().patternVersions().get(BANKS.powerup() + 36 + i));
            }
            f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(objects);
            f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(objects);
            assertEquals(expected, f.frame.state()); assertEquals(objects.placement().pool().slots(), pool.capture().slots());
            f.service(0); f.runtime.spawnBeforeReservedSlots();
            var stop = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.body(3));
            assertEquals(CdPlayerMovement.UnsupportedBoundary.OBJECT_SUPPORT, stop.boundary());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    // Native GetOffObject's charge sound belongs to contact FM, once at the first call. The second call sees
    // the cleared standing bits/link and cannot emit it again. This also catches a pool-only/player-only commit.
    @Test void floatBlockReleaseCommitsItsLinkAndOneOrderedChargeStopBeforeTheSecondCall() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); f.service(0); f.runtime.spawnBeforeReservedSlots(); var publication = f.body(2);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            seedSolidPlayer(f, 8, 40, 1, 0, 0, (0x130 << 16) | 0x1234, (0x721 << 16) | 0x5678);
            pool.write(floatBlock(pool.state(40), 2, 0x84, 8));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(2, f.frame.state().motion().flags() & 10);
            assertEquals(0, f.frame.state().objectLinks().standObject());
            assertEquals(0, f.slot(40).flags() & 8); assertEquals(2, f.slot(40).routine());
            var sounds = new ArrayList<CdOpeningObjectRuntime.SoundRequest>();
            publication.sounds().forEach(v -> sounds.add(new CdOpeningObjectRuntime.PlayerSound(v)));
            sounds.add(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(
                    CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)));
            assertEquals(sounds, f.runtime.lastSoundRequests());
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var actor = f.frame.captureActor(); var shared = f.frame.captureShared(); var objects = f.runtime.captureClosedCut();
            f.service(0x80); f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(objects);
            assertEquals(0, f.frame.state().objectLinks().standObject()); assertEquals(sounds, f.runtime.lastSoundRequests());
        }
    }

    // A later physical slot must see the first block's player patch, then release that earlier block through a2.
    @Test void laterFloatBlockSeesTheFirstStandAndReleasesItsPoolImageInTheSamePass() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            seedSolidPlayer(f, 2, 0, 0, 0, 0x100, 64 << 16, 0x721 << 16);
            pool.write(floatBlock(pool.state(40), 2, 0x84, 0));
            pool.write(floatBlock(pool.state(41), 2, 0x84, 0));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(41, f.frame.state().objectLinks().standObject());
            assertEquals(0, f.slot(40).flags() & 8); assertEquals(8, f.slot(41).flags() & 8);
            assertEquals(6, f.slot(40).routine()); assertEquals(6, f.slot(41).routine());
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    // Visible's animation write precedes the unsupported StandOn a2 dereference, but no staged player/globals
    // escape the failing pure call. The runtime must close queue and Frame without undoing that source prefix.
    @Test void floatBlockSolidBoundaryClosesBothOwnersAfterKeepingThePublishedObjectPrefix() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            seedSolidPlayer(f, 8, 5, 0, 0, 0, 64 << 16, 0x721 << 16);
            pool.write(floatBlock(pool.state(40), 8, 0x84, 8).moveByte(ANIMATION, 2));
            var before = f.frame.state();
            var stop = assertThrows(CdSolidObject.UnsupportedSolidObject.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS, stop.boundary());
            assertEquals(0, f.slot(40).unsignedByte(ANIMATION));
            assertEquals(before, f.frame.state()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    @Test void tenthFloatBankFollowsPowerupAndItsOwnOverlapRejectsBeforeAdmission() throws Exception {
        assertEquals(12311, BANKS.floatBlock());
        var nine = new CdOpeningObjectRuntime.LogicalBanks(1, 2, 3, 4, 5, 6, 7, 8, 47);
        assertEquals(83, nine.floatBlock());
        assertEquals(nine, new CdOpeningObjectRuntime.LogicalBanks(1, 2, 3, 4, 5, 6, 7, 8, 47, 83));
        var assets = load();
        for (int floating : List.of(12310, 12236, 10000, -1, Integer.MAX_VALUE - 13)) {
            try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0)) {
                var banks = new CdOpeningObjectRuntime.LogicalBanks(10000, 11000, 12000, 12070, 12098,
                        12161, 12231, 12236, 12275, floating);
                assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                        assets, frame, INITIAL, CONTACT, SCORE, LEVEL, new byte[640], banks, PRE, value -> { }), "base " + floating);
                assertFalse(frame.closed()); assertEquals(0, frame.vIntClock().deltaSinceAdmittedCut());
            }
        }
    }

    private static CdObjectState floatBlock(CdObjectState s, int routine, int spriteFlags, int flags) {
        return s.moveByte(ID, 0x12).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, spriteFlags)
                .moveLong(MAPPINGS, 0x20A3FA).moveWord(TILE, 0x4334).moveByte(PRIORITY, 1)
                .moveByte(WIDTH, 12).moveByte(Y_RADIUS, 12).moveByte(MAP_FRAME, 5)
                .moveWord(X, 64).moveWord(Y, 0x740).moveByte(FLAGS, flags);
    }

    /** Constructed source cut after Body, before the live object pass; never route evidence. */
    private static void seedSolidPlayer(Fixture f, int flags, int stand, int charge, int xv, int yv, int x, int y) {
        var actor = f.frame.captureActor(); var s = actor.state(); var m = s.motion();
        var next = new CdPlayerMotionState(x, y, xv, yv, 0, 2, 0, flags, m.spriteFlags(), 9, 19,
                0, m.previousAnimation(), 0, charge, 0, 0, 0, m.previousPrimaryAngle(), m.previousSecondaryAngle());
        f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(),
                new CdPlayerBody.State(next, s.animation(), s.header(), s.timers(), s.history(), s.rotationDistance(),
                        new CdPlayerBody.ObjectLinks(0, stand)), actor.display()));
    }

    // Catches applying the staged launch inside the body, deferring it past the next slot,
    // omitting the booster from the queue, or publishing it without the frontier pixels.
    @Test void unrunRampDeviceContractAppliesTheStagedLaunchOnceDrawsAndReplaysAfterRestore() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle();
            var p = f.frame.state().motion();
            int x = p.centreX16_16() >>> 16, y = p.centreY16_16() >>> 16;
            // Constructed SpawnObject-equivalent ingress at the player's position: a device contract, not a route.
            f.runtime.placement().pool().write(f.slot(38).moveByte(ID, 0x2A).moveWord(X, x).moveWord(Y, y));
            var before = f.runtime.captureClosedCut();
            var beforeActor = f.frame.captureActor();
            var beforeShared = f.frame.captureShared();
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            f.body(2);
            int restingVelocity = f.frame.state().motion().yVelocity();
            assertNotEquals(0xC00, restingVelocity, "the launch is applied by the dynamic pass, never by Body");
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(0xC00, f.frame.state().motion().yVelocity(), "MOVE.W #$C00,oYVel(a6): resting player launches down");
            var ramp = f.slot(38);
            assertEquals(2, ramp.routine());
            assertEquals(60, ramp.unsignedByte(RAMP_TIMER));
            assertEquals(x, ramp.word(RAMP_ANCHOR_X));
            assertEquals(x, ramp.centreX());
            assertEquals(0x21FDBC, ramp.longBits(MAPPINGS));
            assertEquals(0x441, ramp.word(TILE));
            assertEquals(0, ramp.unsignedByte(MAP_FRAME));
            var sat = f.runtime.finishAfterCamera(POST);
            assertEquals(8, sat.provenance().stream().filter(pr -> pr.slotRef().wholeIndex() == 38).count(),
                    "MapSpr_3DRamp frame 0 has eight pieces");
            f.runtime.finishOrdinaryTail();
            assertEquals(0xC00, f.frame.state().motion().yVelocity(), "no second application at the tail");
            var after = f.runtime.captureClosedCut();
            var afterActor = f.frame.captureActor();
            var cpu = f.runtime.cpuSat();
            f.service(0x80);
            var tiles = f.runtime.displayedFrame().tiles();
            assertTrue(tiles.stream().anyMatch(t -> t.patternId() >= BANKS.ramp() && t.patternId() < BANKS.ramp() + 63),
                    "booster pixels come from the frontier bank span");
            f.runtime.validateRestore(before);
            f.frame.validateActorRestore(beforeActor);
            f.frame.validateSharedRestore(beforeShared);
            f.runtime.restore(before);
            f.frame.restoreActor(beforeActor);
            f.frame.restoreShared(beforeShared);
            assertEquals(beforeActor.state().motion().yVelocity(), f.frame.state().motion().yVelocity());
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            f.body(2);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST);
            f.runtime.finishOrdinaryTail();
            assertEquals(afterActor, f.frame.captureActor(), "replayed launch reproduces the actor partition");
            assertEquals(after.placement().pool().slots(), f.runtime.captureClosedCut().placement().pool().slots());
            assertArrayEquals(cpu.bytes(), f.runtime.cpuSat().bytes());
            assertEquals(after.devGlobals(), f.runtime.captureClosedCut().devGlobals());
        }
    }

    // Catches dispatching children from a captured list, culling or omitting committed
    // children in later slots, or publishing plant pieces without the frontier pixels.
    @Test void unrunPlantDeviceContractExecutesLaterChildrenThisPassAndDrawsFromTheFrontierBank() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle();
            // Constructed SpawnObject-equivalent ingress near the camera: a device contract, not a route.
            f.runtime.placement().pool().write(f.slot(38).moveByte(ID, 0x2C).moveWord(X, 0x80).moveWord(Y, 0x7C4)
                    .moveByte(SUBTYPE, 1));
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            f.body(2);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(List.of(0x2C, 0x2C, 0x2C, 0x2C, 0x2C),
                    List.of(f.slot(38).id(), f.slot(39).id(), f.slot(40).id(), f.slot(41).id(), f.slot(42).id()));
            assertEquals(List.of(0xC0, 0x100, 0x80, 0xE0, 0x20), List.of(f.slot(38).centreX(), f.slot(39).centreX(),
                    f.slot(40).centreX(), f.slot(41).centreX(), f.slot(42).centreX()));
            assertEquals(0, f.slot(43).id());
            var sat = f.runtime.finishAfterCamera(POST);
            for (int whole = 38; whole <= 42; whole++) {
                int pieces = whole <= 39 ? 4 : 2;
                int slot = whole;
                assertEquals(pieces, sat.provenance().stream().filter(pr -> pr.slotRef().wholeIndex() == slot).count(),
                        "later children ran and queued in the same pass: slot " + whole);
            }
            f.runtime.finishOrdinaryTail();
            f.service(0);
            var tiles = f.runtime.displayedFrame().tiles();
            assertTrue(tiles.stream().anyMatch(t -> t.patternId() >= BANKS.plant() && t.patternId() < BANKS.plant() + 28));
            assertTrue(tiles.stream().noneMatch(t -> t.patternId() >= BANKS.ramp() && t.patternId() < BANKS.ramp() + 63));
            f.runtime.spawnBeforeReservedSlots();
            f.body(3);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(List.of(0xC0, 0x100, 0x80, 0xE0, 0x20), List.of(f.slot(38).centreX(), f.slot(39).centreX(),
                    f.slot(40).centreX(), f.slot(41).centreX(), f.slot(42).centreX()), "static while the player's ramp bit is clear");
            f.runtime.finishAfterCamera(POST);
            f.runtime.finishOrdinaryTail();
        }
    }

    // Catches deriving banks from declarations, dropping the sequential frontier policy,
    // or letting the three-argument bases drift from the module's 24+14+70+28+63 picture.
    @Test void unrunLogicalBanksDeriveTheSequentialFrontierSpansAndDevGlobalsAreExplicitZero() throws Exception {
        assertEquals(12070, BANKS.plant());
        assertEquals(12098, BANKS.ramp());
        var explicit = new CdOpeningObjectRuntime.LogicalBanks(1, 2, 3, 4, 5);
        assertEquals(4, explicit.plant());
        assertEquals(5, explicit.ramp());
        assertEquals(new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236),
                new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138));
        assertThrows(IllegalArgumentException.class, () -> new CdOpeningObjectRuntime.DevGlobals(0x100, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdOpeningObjectRuntime.DevGlobals(0, -1));
        var assets = load();
        try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets)) {
            // Plant base inside the Mosqui span: the frontier pixel policy rejects at construction.
            assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                    assets, frame, INITIAL, CONTACT, SCORE, LEVEL, new byte[640], new CdOpeningObjectRuntime.LogicalBanks(10000, 11000, 12000, 12069, 12098),
                    PRE, value -> { }));
        }
        try (var f = new Fixture()) {
            assertEquals(new CdOpeningObjectRuntime.DevGlobals(0, 0), f.runtime.devGlobals());
            f.firstCycle();
            var cut = f.runtime.captureClosedCut();
            assertEquals(new CdOpeningObjectRuntime.DevGlobals(0, 0), cut.devGlobals());
            f.runtime.restore(cut);
            assertEquals(new CdOpeningObjectRuntime.DevGlobals(0, 0), f.runtime.devGlobals());
        }
    }

    @Test void convertedExplosionAllocatesLaterPointsInTheSamePassAndPublishesBothArtBanks() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); f.makePlayerAbsent();
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(32).moveByte(ID, 0x18).moveByte(SUBTYPE, 1)
                    .moveWord(X, 120).moveWord(Y, 0x760).moveWord(EXPLOSION_POINTS_FRAME, 4));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(2, f.slot(32).routine());
            assertEquals(0x1C, f.slot(33).id());
            assertEquals(2, f.slot(33).routine(), "later allocation executes at its live physical visit");
            assertEquals(23, f.slot(33).unsignedByte(POINTS_TIMER));
            assertEquals(0x82, f.slot(33).subtype());
            assertEquals(0x75E, f.slot(33).centreY());
            var cpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertTrue(cpu.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 32));
            assertTrue(cpu.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 33));
            var frameCut = f.frame.captureActor(); var shared = f.frame.captureShared();
            var objectCut = f.runtime.captureClosedCut();
            f.service(0);
            var shown = f.runtime.displayedFrame();
            assertTrue(shown.patternVersions().keySet().stream().anyMatch(id -> id >= BANKS.explosion() && id < BANKS.explosion() + 70));
            assertTrue(shown.patternVersions().keySet().stream().anyMatch(id -> id >= BANKS.points() && id < BANKS.points() + 5));
            f.runtime.spawnBeforeReservedSlots(); f.body(3); f.runtime.runDynamicSlotsAfterReservedSlots();
            var nextCpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var slots = pool.capture().slots();
            f.frame.validateActorRestore(frameCut); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(objectCut);
            f.frame.restoreActor(frameCut); f.frame.restoreShared(shared); f.runtime.restore(objectCut);
            f.service(0);
            assertEquals(shown, f.runtime.displayedFrame());
            f.runtime.spawnBeforeReservedSlots(); f.body(3); f.runtime.runDynamicSlotsAfterReservedSlots();
            var replayCpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertArrayEquals(nextCpu.bytes(), replayCpu.bytes());
            assertEquals(nextCpu.provenance(), replayCpu.provenance());
            assertEquals(slots, pool.capture().slots());
        }
    }

    @Test void pointsAllocatedBeforeTheExplosionWaitForTheNextPhysicalPass() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); f.makePlayerAbsent();
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(40).moveByte(ID, 0x18).moveByte(SUBTYPE, 1).moveWord(X, 120).moveWord(Y, 0x760));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(0x1C, f.slot(32).id()); assertEquals(0, f.slot(32).routine());
            assertEquals(0, f.slot(32).unsignedByte(POINTS_TIMER)); assertEquals(0x760, f.slot(32).centreY());
            var cpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertFalse(cpu.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 32));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(3); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(2, f.slot(32).routine()); assertEquals(23, f.slot(32).unsignedByte(POINTS_TIMER));
            assertEquals(0x75E, f.slot(32).centreY());
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    @Test void deletedPointsDrawCutIsQueuedButTheZeroIdIsSkippedAtSatDrain() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); f.makePlayerAbsent();
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(32).moveByte(ID, 0x1C).moveByte(SUBTYPE, 0x80)
                    .moveByte(ROUTINE, 2).moveByte(POINTS_TIMER, 1));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertArrayEquals(new byte[64], f.slot(32).copyBytes());
            assertEquals(0, f.runtime.finishAfterCamera(POST).spriteCount());
            f.runtime.finishOrdinaryTail();
            assertFalse(f.runtime.closed(), "no missing-mapping resolution of the deleted slot");
        }
    }

    @Test void flowerScratchAngleIsAppliedOnceBeforeTheNextDispatchAndItsArtPublishesLater() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); f.makePlayerAbsent(); f.angleWrites.clear();
            f.onAngle = f.frame::applyObjectPrimaryAngle;
            f.frame.applyObjectPrimaryAngle(0x55);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(32).moveByte(ID, 0x1F).moveByte(SUBTYPE, 1).moveByte(SUBTYPE2, 1)
                    .moveWord(X, 64).moveWord(Y, 0x720));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(List.of(0), f.angleWrites, "empty floor probe clears only its scratch angle");
            assertEquals(0, f.frame.environment().movement().globals().primaryAngle());
            assertEquals(0x722, f.slot(32).centreY());
            assertEquals(0x06D7, f.slot(32).word(TILE), "NoFlags overwrites the low-priority init tile");
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail(); f.service(0);
            assertTrue(f.runtime.displayedFrame().patternVersions().keySet().stream()
                    .anyMatch(id -> id >= BANKS.flower() && id < BANKS.flower() + 39));
        }
    }

    @Test void contactBanksAppendAfterAllFrontierSpansAndRejectOverlapBeforeFirstService() throws Exception {
        var banks = new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138);
        assertEquals(299, banks.explosion()); assertEquals(369, banks.points()); assertEquals(374, banks.flower());
        var assets = load();
        try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets)) {
            assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                    assets, frame, INITIAL, CONTACT, SCORE, LEVEL, new byte[640],
                    new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 298, 369, 374), PRE, value -> { }));
            assertFalse(frame.closed());
            assertEquals(0, frame.captureShared().vIntClock().deltaSinceAdmittedCut());
        }
    }

    @Test void rollingEnemyContactCommitsTheWholeResponseThenExecutesAndReplaysLiveAftermath() throws Exception {
        try (var f = new Fixture(INITIAL, CONTACT, new CdOpeningObjectRuntime.ScoreGlobals(0, 10, 0))) {
            f.firstCycle(); seedContactPlayer(f);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(40).moveByte(ID, CdMosqui.OBJECT_ID).moveByte(SPRITE_FLAGS, 0x84)
                    .moveByte(COLLISION_TYPE, 0x2B).moveWord(X, 64).moveWord(Y, 0x759));
            var preContact = f.runtime.captureClosedCut();
            f.service(0); f.runtime.spawnBeforeReservedSlots();
            var publication = f.body(2);
            var response = publication.result().effects().stream().filter(CdPlayerBody.ObjectTouchResponse.class::isInstance)
                    .map(CdPlayerBody.ObjectTouchResponse.class::cast).findFirst().orElseThrow().result();
            assertEquals(com.openggf.game.soniccd.player.CdPlayerTouchResponse.Exit.KILL_ENEMY, response.exit());
            assertEquals(0x18, f.slot(40).id()); assertEquals(0, f.slot(40).routine());
            assertEquals(0x38, f.frame.state().motion().yVelocity());
            assertEquals(2, f.frame.environment().movement().globals().scoreChain());
            assertEquals(new CdOpeningObjectRuntime.ScoreGlobals(10, 5010, 1), f.runtime.scoreGlobals());
            assertEquals(4, f.runtime.ringGlobals().lives()); assertEquals(1, f.runtime.ringGlobals().updateHudLives());
            assertEquals(response.changedSlots().getFirst(), f.slot(40));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(0, f.slot(40).unsignedByte(COLLISION_TYPE));
            assertEquals(0x1C, f.slot(32).id()); assertEquals(0, f.slot(32).routine(), "earlier child waits one pass");
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var actor = f.frame.captureActor(); var shared = f.frame.captureShared(); var saved = f.runtime.captureClosedCut();
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(3); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(23, f.slot(32).unsignedByte(POINTS_TIMER));
            var cpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var slots = pool.capture().slots(); var shown = f.runtime.displayedFrame(); var sounds = f.runtime.lastSoundRequests();
            f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(saved);
            f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(saved);
            assertEquals(saved.scoreGlobals(), f.runtime.scoreGlobals()); assertEquals(saved.contactGlobals(), f.runtime.contactGlobals());
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(3); f.runtime.runDynamicSlotsAfterReservedSlots();
            var replay = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertArrayEquals(cpu.bytes(), replay.bytes()); assertEquals(cpu.provenance(), replay.provenance());
            assertEquals(slots, pool.capture().slots()); assertEquals(shown, f.runtime.displayedFrame());
            assertEquals(sounds, f.runtime.lastSoundRequests());
            assertEquals(new CdOpeningObjectRuntime.ScoreGlobals(10, 5010, 1), f.runtime.scoreGlobals());
            f.runtime.restore(preContact); // Unit partition restore; no later gameplay runs with the separately retained Frame.
            assertEquals(new CdOpeningObjectRuntime.ScoreGlobals(0, 10, 0), f.runtime.scoreGlobals());
            assertEquals(3, f.runtime.ringGlobals().lives()); assertEquals(0, f.runtime.ringGlobals().updateHudLives());
            assertEquals(CONTACT, f.runtime.contactGlobals());
        }
    }

    @Test void shieldedHazardCommitsTheContactByteWithoutCreatingAFalseLostRing() throws Exception {
        try (var f = new Fixture(INITIAL, new CdPlayerBody.ContactGlobals(0, 1, 1), SCORE)) {
            f.firstCycle(); seedContactPlayer(f);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(40).moveByte(ID, CdMosqui.OBJECT_ID).moveByte(SPRITE_FLAGS, 0x84)
                    .moveByte(COLLISION_TYPE, 0xAB).moveWord(X, 64).moveWord(Y, 0x759));
            var preContact = f.runtime.captureClosedCut();
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            assertEquals(4, f.frame.state().motion().routine());
            assertEquals(0x78, f.frame.state().timers().hurt());
            assertEquals(new CdPlayerBody.ContactGlobals(0, 0, 1), f.runtime.contactGlobals());
            assertTrue(pool.capture().slots().stream().noneMatch(s -> s.id() == 0x11));
            assertEquals(SCORE, f.runtime.scoreGlobals());
            f.runtime.runDynamicSlotsAfterReservedSlots(); f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            f.runtime.restore(preContact);
            assertEquals(new CdPlayerBody.ContactGlobals(0, 1, 1), f.runtime.contactGlobals());
        }
    }

    @Test void zeroRingContactDeathFinishesLaterSlotsAndKeepsItsSourceDrawCut() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); seedContactPlayer(f);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(40).moveByte(ID, CdMosqui.OBJECT_ID).moveByte(SPRITE_FLAGS, 0x84)
                    .moveByte(COLLISION_TYPE, 0xAB).moveWord(X, 64).moveWord(Y, 0x759));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            assertEquals(6, f.frame.state().motion().routine());
            assertEquals(CdOpeningObjectRuntime.Phase.PLAYER_DONE, f.runtime.phase());
            assertEquals(SCORE, f.runtime.scoreGlobals());
            assertEquals(List.of(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(
                            com.openggf.game.soniccd.player.CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)),
                    new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(
                            com.openggf.game.soniccd.player.CdPlayerTouchResponse.FmSound.FM_HURT))),
                    f.runtime.lastSoundRequests());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(4, pool.state(40).routine(), "the later live slot still executes on a kill frame");
            assertFalse(f.runtime.endCycleAtLevelRestart(), "levelRestart 0: no LevelStart branch on a kill frame");
            assertEquals(CdOpeningObjectRuntime.Phase.DYNAMIC_DONE, f.runtime.phase());
            var cpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, f.runtime.phase());
            assertFalse(f.frame.closed());
            assertTrue(cpu.provenance().stream().anyMatch(piece -> piece.slotRef().wholeIndex() == 0));
        }
    }

    // U5-D, written before production. Level_MainLoop:407-408 branches to LevelStart after RunObjects (:403), so
    // LevelScroll (:418), DrawObjects (:421) and the tail (:423-430) never run on the restart row. Catches a camera,
    // SAT build, bit-7 DrawDone or ring/spill tick at the cut, a cut that closes the visit, or an uncapturable cut.
    // Routine 8's zero call (seedLevelPlayer) is constructed ingress, not a normal-input route to this row.
    @Test void levelRestartStopsAfterAllObjectSlotsBeforeCpuSatAndOrdinaryTail() throws Exception {
        try (var f = new Fixture(RESTART_LEVEL)) {
            f.firstCycle(); seedLevelPlayer(f, 8, 1);
            var priorCpu = f.runtime.cpuSat();
            int mosquiX = f.slot(34).centreX();
            f.service(0);
            var shown = f.runtime.displayedFrame();
            f.runtime.spawnBeforeReservedSlots();
            assertEquals(CdPlayerBody.Boundary.RESTART_RETURN, f.body(2).result().boundary());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(mosquiX - 1, f.slot(34).centreX(), "the later Mosqui slot still patrols on the restart row");
            var rings = f.runtime.ringGlobals(); var spill = f.runtime.spillGlobals(); var level = f.runtime.levelGlobals();
            var sounds = f.runtime.lastSoundRequests(); var placement = f.runtime.placement().capture();
            var contact = f.runtime.contactGlobals(); var score = f.runtime.scoreGlobals(); var dev = f.runtime.devGlobals();
            var powerup = f.runtime.powerupArtState();
            var actor = f.frame.state(); var environment = f.frame.environment(); var display = f.frame.display();
            int publications = f.artPublications.size(); int angles = f.angleWrites.size();
            assertEquals(new CdLevelGlobals(1, 1, 0, 0, 1, 0, 0, 8, 0), level);
            assertTrue(f.runtime.endCycleAtLevelRestart());
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, f.runtime.phase());
            assertFalse(f.runtime.closed()); assertFalse(f.frame.closed());
            assertSame(priorCpu, f.runtime.cpuSat(), "no DrawObjects: the last built bytes, count and provenance");
            assertSame(shown, f.runtime.displayedFrame());
            assertEquals(rings, f.runtime.ringGlobals(), "no UpdateGlobalAnims ring tick");
            assertEquals(spill, f.runtime.spillGlobals(), "no RingSpill tail");
            assertEquals(level, f.runtime.levelGlobals()); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertEquals(contact, f.runtime.contactGlobals()); assertEquals(score, f.runtime.scoreGlobals());
            assertEquals(dev, f.runtime.devGlobals()); assertEquals(powerup, f.runtime.powerupArtState());
            var after = f.runtime.placement().capture();
            assertEquals(placement.status(), after.status());
            assertEquals(placement.pool().slots(), after.pool().slots(), "no DrawDone bit 7 for the abandoned pointers");
            assertArrayEquals(placement.savedFlags().copy(), after.savedFlags().copy());
            assertSame(actor, f.frame.state()); assertSame(environment, f.frame.environment());
            assertEquals(display, f.frame.display());
            assertEquals(publications, f.artPublications.size()); assertEquals(angles, f.angleWrites.size());
            // The old token's preparation port stays the existing no-op on this still-open, capturable visit.
            assertFalse(f.runtime.hasPreparationBoundary(PlcLifecyclePhase.ORDINARY_LEVEL));
            f.runtime.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
            assertFalse(f.runtime.closed());
            var cut = f.runtime.captureClosedCut();
            assertEquals(1, cut.levelGlobals().levelRestart(), "the captured marker identifies the cut; no new phase");
            assertNotNull(f.frame.captureActor()); assertNotNull(f.frame.captureShared());
        }
    }

    // Catches a camera build, ordinary tail, second cut or dynamic walk after the restart cut, or a premature cut.
    // Each misuse is a misphase under the existing policy and closes both owners.
    @Test void restartCutRejectsTheCameraBuildTailAndASecondCutThenClosesBothOwners() throws Exception {
        List<Consumer<Fixture>> misuses = List.of(
                f -> f.runtime.finishAfterCamera(POST),
                f -> f.runtime.finishOrdinaryTail(),
                f -> f.runtime.endCycleAtLevelRestart(),
                f -> f.runtime.runDynamicSlotsAfterReservedSlots());
        for (var misuse : misuses) {
            try (var f = new Fixture(RESTART_LEVEL)) {
                f.firstCycle(); seedLevelPlayer(f, 8, 1); runRestartRow(f);
                assertTrue(f.runtime.endCycleAtLevelRestart());
                assertThrows(IllegalStateException.class, () -> misuse.accept(f));
                assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
            }
        }
        try (var f = new Fixture(RESTART_LEVEL)) {
            f.firstCycle(); seedLevelPlayer(f, 8, 1);
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            assertThrows(IllegalStateException.class, f.runtime::endCycleAtLevelRestart, "the dynamic walk has not run");
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    // No native Level_MainLoop V-int follows the branch: LevelStart clears levelRestart (:299) before :333's VSync.
    // Catches re-entering the old visit's V-int (ReadController, DPLC, LoadShieldArt, Finish/count) at the cut.
    @Test void anyVIntAfterTheRestartMarkerRejectsBeforeFrameServiceAndCloses() throws Exception {
        for (var phase : List.of(PlcLifecyclePhase.ORDINARY_LEVEL, PlcLifecyclePhase.LAG)) {
            try (var f = new Fixture(RESTART_LEVEL)) {
                f.firstCycle(); seedLevelPlayer(f, 8, 1); runRestartRow(f);
                assertTrue(f.runtime.endCycleAtLevelRestart());
                assertVIntRejected(f, phase);
            }
        }
        // The same guard holds for a declared nonzero marker at construction (explicit ingress, not a route).
        try (var f = new Fixture(new CdLevelGlobals(1, 0, 0, 0, 3, 0, 2, 0, 0))) {
            assertVIntRejected(f, PlcLifecyclePhase.ORDINARY_LEVEL);
            assertTrue(f.artPublications.isEmpty());
        }
    }

    // Catches a cut that cannot restore onto itself, a pre-row capture that replays to a different cut, or a restored
    // cut that admits a V-int. Restore replays no service, slot, queue or art work.
    @Test void restartCutRestoresExactlyAndAPreRowCaptureReplaysToAnEqualCut() throws Exception {
        try (var f = new Fixture(RESTART_LEVEL)) {
            f.firstCycle(); seedLevelPlayer(f, 8, 1);
            var preObjects = f.runtime.captureClosedCut(); var preActor = f.frame.captureActor();
            var preShared = f.frame.captureShared();
            runRestartRow(f);
            assertTrue(f.runtime.endCycleAtLevelRestart());
            var cut = f.runtime.captureClosedCut(); var cutActor = f.frame.captureActor(); var cutShared = f.frame.captureShared();
            var cpu = f.runtime.cpuSat(); var shown = f.runtime.displayedFrame(); int publications = f.artPublications.size();
            f.runtime.validateRestore(cut);
            f.runtime.restore(cut);
            assertRestartCut(cut, f);
            assertSame(cpu, f.runtime.cpuSat()); assertSame(shown, f.runtime.displayedFrame());
            f.frame.validateActorRestore(preActor); f.frame.validateSharedRestore(preShared); f.runtime.validateRestore(preObjects);
            f.frame.restoreActor(preActor); f.frame.restoreShared(preShared); f.runtime.restore(preObjects);
            assertEquals(0, f.runtime.levelGlobals().levelRestart());
            assertEquals(publications, f.artPublications.size());
            runRestartRow(f);
            assertTrue(f.runtime.endCycleAtLevelRestart());
            assertRestartCut(cut, f);
            assertArrayEquals(cpu.bytes(), f.runtime.cpuSat().bytes());
            assertEquals(cpu.provenance(), f.runtime.cpuSat().provenance());
            assertEquals(shown, f.runtime.displayedFrame());
            assertEquals(cutActor, f.frame.captureActor());
            var shared = f.frame.captureShared();
            assertEquals(cutShared.environment(), shared.environment()); assertEquals(cutShared.paused(), shared.paused());
            assertEquals(cutShared.vIntClock(), shared.vIntClock());
            assertArrayEquals(cutShared.artState().cpuBuffer().copy(), shared.artState().cpuBuffer().copy());
            assertArrayEquals(cutShared.artState().residentBank().copy(), shared.artState().residentBank().copy());
            f.runtime.restore(cut); f.frame.restoreActor(cutActor); f.frame.restoreShared(cutShared);
            assertVIntRejected(f, PlcLifecyclePhase.ORDINARY_LEVEL);
        }
    }

    // U5-D restart factory, written before production. Root's carry is installed exactly: the saved bank
    // (savedObjFlags 2+OBJFLAGSCNT, Main CPU Variables.inc:47-48; no .NormalLoad writer after routine 8's
    // ResetSavedObjFlags, Sonic/Main.asm:2465) and both powerup windows (.ClearFlowers zeroes the CPU buffer,
    // Level/Main.asm:178-184; ClearScreen leaves VRAM $A880, Functions (General).asm:182-228). With powerup 0 the
    // selector-8 LoadShieldArt returns at once (Powerup/Main.asm:157-159). Catches a cold bank or cold seed on restart,
    // a reset or upload at construction, or a service that rewrites the carried windows.
    @Test void restartFactoryInstallsTheExactCarriedBankAndPowerupWindowsWithoutServiceResetOrUpload() throws Exception {
        var assets = load();
        byte[] bank = new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];
        for (int i = 0; i < bank.length; i++) bank[i] = (byte) (0x5B + i * 0x35);
        byte[] resident = new byte[0x480];
        for (int i = 0; i < resident.length; i++) resident[i] = (byte) (0xC3 ^ i);
        var powerup = new CdPowerupArtRuntime.State(new byte[0x480], resident);
        var publications = new ArrayList<CdPlayerArtRuntime.State>();
        var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0);
        frame.bindArtPublication((request, state) -> publications.add(state));
        var runtime = restart(assets, frame, CdAssetBytes.copyOf(bank), powerup, new byte[640], BANKS);
        try (runtime) {
            assertArrayEquals(bank, runtime.placement().capture().savedFlags().copy(), "every carried byte, header included");
            assertEquals(new CdObjectPlacementRuntime.Status(0, 0, 0, 0, bank[0] & 0xFF, bank[1] & 0xFF, -1, -1),
                    runtime.placement().status());
            assertTrue(runtime.placement().capture().pool().slots().stream().allMatch(slot -> slot.id() == 0));
            assertEquals(powerup, runtime.powerupArtState());
            assertArrayEquals(new byte[0x480], runtime.powerupArtState().cpuBuffer().copy());
            assertArrayEquals(resident, runtime.powerupArtState().residentBank().copy(), "the nonzero resident window");
            assertEquals(LEVEL, runtime.levelGlobals()); assertEquals(INITIAL, runtime.ringGlobals());
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
            assertArrayEquals(new byte[640], runtime.cpuSat().bytes()); assertEquals(0, runtime.cpuSat().spriteCount());
            assertTrue(runtime.displayedFrame().tiles().isEmpty()); assertTrue(runtime.lastSoundRequests().isEmpty());
            assertTrue(publications.isEmpty()); assertEquals(0, frame.vIntClock().deltaSinceAdmittedCut());
            runtime.setPreScrollForeground(PRE);
            runtime.serviceVBlank(frame.prepareInput(0).phase());
            assertEquals(powerup, runtime.powerupArtState(), "powerup 0: no load, so the carried windows stay");
            assertEquals(LEVEL, runtime.levelGlobals());
            assertArrayEquals(bank, runtime.placement().capture().savedFlags().copy(), "the V-int has no bank writer");
            assertTrue(publications.isEmpty());
            assertEquals(1, frame.vIntClock().deltaSinceAdmittedCut());
        }
    }

    // Catches a defaulted or unchecked carry: null, non-$2FE banks, the named empty-SAT policy and the nine-span
    // admission (with the supplied window) all reject before the Frame is serviced or closed.
    @Test void restartFactoryRejectsAMissingOrNonNativeCarryBeforeAdmission() throws Exception {
        var assets = load();
        var bank = CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]);
        var powerup = new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]);
        assertThrows(IllegalArgumentException.class, () -> new CdPowerupArtRuntime.State(new byte[0x47F], new byte[0x480]),
                "a non-$480 powerup window cannot be supplied");
        var overlapping = new CdOpeningObjectRuntime.LogicalBanks(10000, 11000, 12000, 12070, 12098, 12161, 12231, 12236, 12274);
        byte[] nonempty = new byte[640];
        nonempty[0] = 1;
        try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0)) {
            assertThrows(NullPointerException.class, () -> restart(assets, frame, null, powerup, new byte[640], BANKS));
            assertThrows(NullPointerException.class, () -> restart(assets, frame, bank, null, new byte[640], BANKS));
            for (int length : List.of(0x2FD, 0x2FF)) {
                var wrong = CdAssetBytes.copyOf(new byte[length]);
                assertThrows(IllegalArgumentException.class, () -> restart(assets, frame, wrong, powerup, new byte[640], BANKS));
            }
            assertThrows(IllegalArgumentException.class, () -> restart(assets, frame, bank, powerup, nonempty, BANKS));
            assertThrows(IllegalArgumentException.class, () -> restart(assets, frame, bank, powerup, new byte[640], overlapping));
            assertFalse(frame.closed());
            assertEquals(0, frame.captureShared().vIntClock().deltaSinceAdmittedCut());
        }
    }

    @Test void ringBearingHurtExecutesTheRealLostRingPassAndRestoresEveryOwnedSpillByte() throws Exception {
        var initial = new CdRingGlobals(3, 3, 0, 0, 0, 0, 0, 0);
        try (var f = new Fixture(initial)) {
            f.firstCycle(); seedContactPlayer(f);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(40).moveByte(ID, CdMosqui.OBJECT_ID).moveByte(SPRITE_FLAGS, 0x84)
                    .moveByte(COLLISION_TYPE, 0xAB).moveWord(X, 64).moveWord(Y, 0x759));
            var actor = f.frame.captureActor(); var shared = f.frame.captureShared();
            var objects = f.runtime.captureClosedCut();
            for (int replay = 0; replay < 2; replay++) {
                if (replay != 0) {
                    f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(objects);
                    f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(objects);
                    assertEquals(new CdRingSpillGlobals(0, 0, 0), f.runtime.spillGlobals());
                    assertEquals(3, f.runtime.ringGlobals().rings());
                }
                f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
                assertEquals(4, f.frame.state().motion().routine());
                assertEquals(0x11, f.slot(32).id()); assertEquals(0x759, f.slot(32).centreY(), "pre-unroll Y");
                assertEquals(3, f.runtime.ringGlobals().rings(), "LostRing Init owns clearing the ring counter");
                f.runtime.runDynamicSlotsAfterReservedSlots();
                assertEquals(0, f.runtime.ringGlobals().rings()); assertEquals(0x80, f.runtime.ringGlobals().updateHudRings());
                assertEquals(0, f.runtime.ringGlobals().livesFlags());
                assertEquals(new CdRingSpillGlobals(0xFF, 0, 0), f.runtime.spillGlobals());
                assertEquals(List.of(32, 33, 34), pool.capture().slots().stream().filter(slot -> slot.id() == 0x11)
                        .map(slot -> slot.identity().wholeIndex()).toList());
                assertEquals(-0x3D4, (short) f.slot(32).word(Y_VELOCITY));
                assertEquals(-0x3D4, (short) f.slot(33).word(Y_VELOCITY));
                assertEquals(-0x338, (short) f.slot(34).word(Y_VELOCITY));
                assertEquals(1, f.runtime.lastSoundRequests().stream()
                        .filter(CdOpeningObjectRuntime.LostRingFm.class::isInstance).count());
                f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
                assertEquals(new CdRingSpillGlobals(0xFE, 0xFF, 0), f.runtime.spillGlobals());
                assertEquals(f.runtime.spillGlobals(), f.runtime.captureClosedCut().spillGlobals());
            }
        }
    }

    @Test void unknownAbsoluteVIntRejectsTheLostRingBindingWithoutInventingASeed() throws Exception {
        try (var f = new Fixture(INITIAL, CONTACT, SCORE, false)) {
            f.firstCycle(); f.makePlayerAbsent();
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(40).moveByte(ID, 0x11).moveWord(X, 64).moveWord(Y, 0x759));
            var before = pool.capture().slots();
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            var failure = assertThrows(CdOpeningObjectRuntime.UnsupportedContact.class,
                    f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(CdOpeningObjectRuntime.UnsupportedContactBoundary.LOST_RING_NATIVE_CLOCK, failure.boundary());
            assertEquals(before, pool.capture().slots()); assertTrue(f.runtime.closed());
            assertTrue(f.frame.vIntClock().absoluteValue().isEmpty());
        }
    }

    @Test void liveLostRingCollectionUsesTheExistingRewardAndSharedSatWithoutSavedFlagWrites() throws Exception {
        var initial = new CdRingGlobals(99, 3, 0, 0, 0, 0, 0, 0);
        try (var f = new Fixture(initial)) {
            f.firstCycle(); f.makePlayerAbsent();
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            var assets = CdLostRing.Assets.fromObjectAssets(f.assets.openingObjects());
            pool.write(pool.state(40).moveByte(ID, 0x11).moveByte(ROUTINE, 4).moveByte(SPRITE_FLAGS, 4)
                    .moveLong(MAPPINGS, assets.mappingsAddress()).moveWord(TILE, assets.baseTileWord())
                    .moveWord(X, 64).moveWord(Y, 0x760).moveByte(WIDTH, 8).moveByte(Y_RADIUS, 8));
            var flags = f.runtime.placement().capture().savedFlags().copy();
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(100, f.runtime.ringGlobals().rings()); assertEquals(4, f.runtime.ringGlobals().lives());
            assertEquals(List.of(new CdOpeningObjectRuntime.RingSubCpu(CdRing.SubCpuCommand.SCMD_YESSFX)),
                    f.runtime.lastSoundRequests());
            assertEquals(6, f.slot(40).routine()); assertEquals(4, f.slot(40).unsignedByte(MAP_FRAME));
            var cpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertEquals(List.of(40), cpu.provenance().stream().map(p -> p.slotRef().wholeIndex()).distinct().toList());
            assertArrayEquals(flags, f.runtime.placement().capture().savedFlags().copy());
        }
    }

    @Test void pauseAndLagAdvanceOnlyTheFrameClockAndNeverTheUnpausedRingSpillTail() throws Exception {
        try (var f = new Fixture(new CdRingGlobals(1, 3, 0, 0, 0, 0, 0, 0))) {
            f.firstCycle(); f.makePlayerAbsent();
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(40).moveByte(ID, 0x11).moveWord(X, 64).moveWord(Y, 0x759));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2); f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var spill = f.runtime.spillGlobals(); var clock = f.frame.vIntClock();
            f.frame.prepareInput(0); f.runtime.serviceVBlank(PlcLifecyclePhase.LAG);
            assertEquals(spill, f.runtime.spillGlobals());
            assertEquals(clock.deltaSinceAdmittedCut() + 1, f.frame.vIntClock().deltaSinceAdmittedCut());
            f.service(0x80);
            assertTrue(f.frame.paused()); assertEquals(spill, f.runtime.spillGlobals());
            assertEquals(clock.deltaSinceAdmittedCut() + 2, f.frame.vIntClock().deltaSinceAdmittedCut());
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, f.runtime.phase());
        }
    }

    @Test void lostRingFloorPhaseUsesTheDeclaredAbsoluteCounterAndPublishesOneAngle() throws Exception {
        try (var f = new Fixture(INITIAL, CONTACT, SCORE, OptionalLong.of(1))) {
            f.firstCycle(); f.makePlayerAbsent();
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots(); f.angleWrites.clear();
            pool.write(pool.state(42).moveByte(ID, 0x11).moveByte(ROUTINE, 2)
                    .moveWord(X, 64).moveWord(Y, 0x720).moveWord(Y_VELOCITY, 0)
                    .moveByte(Y_RADIUS, 8).moveByte(WIDTH, 8).moveByte(SPRITE_FLAGS, 0));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            assertEquals(3, f.frame.vIntClock().absoluteValue().orElseThrow());
            assertEquals(2, f.frame.vIntClock().deltaSinceAdmittedCut());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            // Ring/Main.asm:415-418: (absolute 3 + d7 85) & 3 == 0.
            // Delta/levelFrames/pre-increment 2 would skip this exact probe.
            assertEquals(List.of(0), f.angleWrites);
            assertEquals(0, f.slot(42).id(), "zero spill timer deletes only after the floor probe");
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    @Test void nonzeroSpillCaptureRestoresItsAccumulatorAndReplaysTheNextTail() throws Exception {
        try (var f = new Fixture(new CdRingGlobals(1, 3, 0, 0, 0, 0, 0, 0))) {
            f.firstCycle(); f.makePlayerAbsent();
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(40).moveByte(ID, 0x11).moveWord(X, 64).moveWord(Y, 0x759));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2); f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertEquals(new CdRingSpillGlobals(0xFE, 0xFF, 0), f.runtime.spillGlobals());
            var actor = f.frame.captureActor(); var shared = f.frame.captureShared(); var objects = f.runtime.captureClosedCut();
            List<CdObjectState> expectedSlots = null;
            for (int replay = 0; replay < 2; replay++) {
                if (replay != 0) {
                    f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(objects);
                    f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(objects);
                    assertEquals(new CdRingSpillGlobals(0xFE, 0xFF, 0), f.runtime.spillGlobals());
                }
                f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(3); f.runtime.runDynamicSlotsAfterReservedSlots();
                f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
                assertEquals(new CdRingSpillGlobals(0xFD, 0x1FD, 0), f.runtime.spillGlobals());
                if (replay == 0) expectedSlots = pool.capture().slots();
                else assertEquals(expectedSlots, pool.capture().slots());
            }
        }
    }

    @Test void contactScoreIngressIsExplicitAndOnlyTheRuntimeSnapshotOwnsIt() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new CdOpeningObjectRuntime.ScoreGlobals(0, 0, 256));
        assertEquals(-1, new CdOpeningObjectRuntime.ScoreGlobals(-1, Integer.MIN_VALUE, 255).score());
        try (var f = new Fixture(INITIAL, new CdPlayerBody.ContactGlobals(0x80, 2, 1),
                new CdOpeningObjectRuntime.ScoreGlobals(1234, 5000, 0x80))) {
            var cut = f.runtime.captureClosedCut();
            assertEquals(new CdPlayerBody.ContactGlobals(0x80, 2, 1), cut.contactGlobals());
            assertEquals(new CdOpeningObjectRuntime.ScoreGlobals(1234, 5000, 0x80), cut.scoreGlobals());
            f.runtime.restore(cut);
            assertEquals(cut.contactGlobals(), f.runtime.contactGlobals()); assertEquals(cut.scoreGlobals(), f.runtime.scoreGlobals());
        }
    }

    // U5-B0, written before production. Catches an implicit level-globals default, a second store, or a
    // capture that drops or re-derives the value. No runtime writer is admitted yet (DeadChkGone, Restart
    // and the $3B owner are later units), so a restore that replaces a CHANGED value cannot be observed here.
    @Test void levelGlobalsIngressIsExplicitAndOnlyTheRuntimeSnapshotOwnsIt() throws Exception {
        var assets = load();
        try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0)) {
            assertThrows(NullPointerException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                    assets, frame, INITIAL, CONTACT, SCORE, null, new byte[640], BANKS, PRE, value -> { }));
            assertFalse(frame.closed());
            assertEquals(0, frame.captureShared().vIntClock().deltaSinceAdmittedCut());
        }
        // Every field differs from the module's declaration, so neither a default nor the DEV value passes.
        var declared = new CdLevelGlobals(1, 1, 2, 1, 0x01, 0x010203, 1, 8, 1);
        try (var f = new Fixture(declared)) {
            assertEquals(declared, f.runtime.levelGlobals());
            var cut = f.runtime.captureClosedCut();
            assertEquals(declared, cut.levelGlobals());
            f.runtime.validateRestore(cut);
            f.runtime.restore(cut);
            assertEquals(declared, f.runtime.levelGlobals());
        }
        try (var f = new Fixture()) {
            var cold = f.runtime.captureClosedCut();
            assertEquals(LEVEL, cold.levelGlobals());
            f.firstCycle();
            f.service(0);
            f.runtime.spawnBeforeReservedSlots();
            f.body(2);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST);
            f.runtime.finishOrdinaryTail();
            // No writer changes these nine values here; the selector-8 LoadShieldArt cut rewrites powerup 0 (bit 7 clear).
            assertEquals(LEVEL, f.runtime.levelGlobals());
            assertEquals(LEVEL, f.runtime.captureClosedCut().levelGlobals());
            f.runtime.restore(cold);
            assertEquals(cold.levelGlobals(), f.runtime.levelGlobals());
        }
    }

    // Source Sonic/Main.asm:2410-2447. Constructed body-call cut, not a normal-input death route.
    @Test void deadGoneCommitsLivesHudAndOnlyTheAllocatedGameOverIdBeforeDynamicSlots() throws Exception {
        var initial = new CdRingGlobals(7, 3, 0x81, 2, 0xFF, 0, 5, 2);
        var level = new CdLevelGlobals(0, 1, 0, 0, 3, 0x112233, 2, 0, 0);
        try (var f = new Fixture(initial, CONTACT, SCORE, level, OptionalLong.of(0))) {
            f.firstCycle(); seedLevelPlayer(f, 6, 0);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            var free = pool.state(32).moveWord(X, 0x1234).moveByte(SPRITE_FLAGS, 0xA5);
            pool.write(free);
            var before = f.runtime.ringGlobals();
            f.service(0); f.runtime.spawnBeforeReservedSlots();
            var publication = f.body(2);
            assertEquals(CdPlayerBody.Boundary.DEAD_RETURN, publication.result().boundary());
            assertEquals(8, f.frame.state().motion().routine()); assertEquals(60, f.frame.state().timers().reset());
            assertEquals(free.moveByte(ID, 0x3B), pool.state(32), "only native oID changes on the allocated dirty slot");
            assertEquals(new CdRingGlobals(7, 2, 0x81, 2, 0, 0, before.ringAnimationTimer(),
                    before.ringAnimationFrame()), f.runtime.ringGlobals());
            assertEquals(new CdLevelGlobals(0, 0, 0, 0, 3, 0x112233, 2, 0, 0), f.runtime.levelGlobals());
            assertEquals(SCORE, f.runtime.scoreGlobals()); assertEquals(CONTACT, f.runtime.contactGlobals());
        }
    }

    @Test void deadGoneFullPoolKeepsTheUncheckedAliasAsMetadataAndNeverOverwritesAnObject() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); seedLevelPlayer(f, 6, 0);
            var pool = f.runtime.placement().pool();
            for (int whole = 32; whole < 128; whole++) pool.write(pool.state(whole).moveByte(ID, 1));
            var slots = pool.capture().slots();
            f.service(0); f.runtime.spawnBeforeReservedSlots(); var publication = f.body(2);
            assertEquals(slots, pool.capture().slots());
            assertEquals(List.of(new CdPlayerBody.UncheckedAliasWrite(0xFFF000, 0x3B)),
                    publication.result().effects().stream().filter(CdPlayerBody.UncheckedAliasWrite.class::isInstance)
                            .map(CdPlayerBody.UncheckedAliasWrite.class::cast).toList());
            assertEquals(2, f.runtime.ringGlobals().lives()); assertEquals(60, f.frame.state().timers().reset());
        }
    }

    @Test void restartZeroCommitsTheSavedFlagResetAndBothCountFadeFieldsBeforeLaterObjects() throws Exception {
        var level = new CdLevelGlobals(0, 1, 0, 0, 3, 0x112233, 0x80, 8, 0);
        try (var f = new Fixture(level)) {
            f.firstCycle(); seedLevelPlayer(f, 8, 1);
            f.runtime.placement().bsetSavedFlag(3, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
            f.service(0); f.runtime.spawnBeforeReservedSlots(); var publication = f.body(2);
            assertEquals(CdPlayerBody.Boundary.RESTART_RETURN, publication.result().boundary());
            assertEquals(0, f.frame.state().timers().reset());
            assertEquals(new CdLevelGlobals(1, 1, 0, 0, 1, 0, 0, 8, 0), f.runtime.levelGlobals());
            assertEquals(0, f.runtime.placement().savedFlag(3, 1));
            var flags = f.runtime.placement().capture().savedFlags();
            assertEquals(1, flags.unsignedByte(0)); assertEquals(1, flags.unsignedByte(1));
            assertEquals(List.of(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.DriverRamWrite(0x1C3E, 1)),
                    new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_FADECDA))),
                    f.runtime.lastSoundRequests());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertTrue(f.runtime.endCycleAtLevelRestart());
            // U5-D: a second runtime built from the carried $0101 + zero-body bank holds that exact bank, and since
            // SpawnObjects_Init rewrites only the header, its first row equals a fresh visit's pool, placement and SAT.
            var cut = f.runtime.captureClosedCut();
            byte[] reset = new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];
            reset[0] = reset[1] = 1;
            assertArrayEquals(reset, cut.placement().savedFlags().copy());
            var actor = f.frame.captureActor().state(); var shared = f.frame.captureShared(); var e = shared.environment();
            var g = e.movement().globals();
            var player = new CdPlayerFrameRuntime.RestartIngress(actor.history().bytes(), shared.artState().cpuBuffer(),
                    shared.artState().residentBank(), g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(),
                    g.debugFloorDistance(), e.globals().debugCheat(), e.controls().rawHeld());
            var powerup = new CdPowerupArtRuntime.State(CdAssetBytes.copyOf(new byte[0x480]), cut.powerupArtState().residentBank());
            try (var next = new Fixture(LEVEL, cut.placement().savedFlags(), powerup, player); var fresh = new Fixture()) {
                assertArrayEquals(reset, next.runtime.placement().capture().savedFlags().copy());
                next.firstCycle(); fresh.firstCycle();
                var a = next.runtime.placement().capture(); var b = fresh.runtime.placement().capture();
                assertEquals(b.status(), a.status());
                // The two runtimes have different pool owners; only native images/physical indices should match.
                for (var expected : b.pool().slots()) {
                    var actual = next.runtime.placement().pool().state(expected.identity().wholeIndex());
                    assertEquals(expected.identity(), actual.identity());
                    assertArrayEquals(expected.copyBytes(), actual.copyBytes());
                }
                assertArrayEquals(b.savedFlags().copy(), a.savedFlags().copy());
                assertArrayEquals(fresh.runtime.cpuSat().bytes(), next.runtime.cpuSat().bytes());
                assertEquals(fresh.runtime.cpuSat().provenance(), next.runtime.cpuSat().provenance());
                assertEquals(fresh.frame.state().motion(), next.frame.state().motion());
                assertEquals(fresh.runtime.ringGlobals(), next.runtime.ringGlobals());
                assertEquals(powerup, next.runtime.powerupArtState(), "powerup 0: the carried windows are untouched");
            }
        }
    }

    // U5-C2, written before production. Catches a missing ninth span, a powerup base derived from the wrong counts
    // (flower + 39 Art_Flower tiles), or an admission that validates only the eight contact spans: the 36-tile
    // window ($480 / 32) must reject a logical overlap or bound failure before the Frame services anything.
    @Test void ninthPowerupBankFollowsFlowerAndAnOverlappingNinthSpanRejectsBeforeAdmission() throws Exception {
        assertEquals(12275, BANKS.powerup(), "12000 + 70 + 28 + 63 + 70 + 5 + 39");
        assertEquals(413, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138).powerup());
        assertEquals(413, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236).powerup());
        assertEquals(new CdOpeningObjectRuntime.LogicalBanks(1, 2, 3, 4, 5, 6, 7, 8, 47),
                new CdOpeningObjectRuntime.LogicalBanks(1, 2, 3, 4, 5, 6, 7, 8));
        assertEquals(9, new CdOpeningObjectRuntime.LogicalBanks(1, 2, 3, 4, 5, 6, 7, 8, 9).powerup());
        var assets = load();
        // Flower's last ID, the explosion/points IDs, the player span, then a negative and an overflowing base.
        for (int powerup : List.of(12274, 12200, 9990, -1, Integer.MAX_VALUE - 34)) {
            try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0)) {
                var banks = new CdOpeningObjectRuntime.LogicalBanks(10000, 11000, 12000, 12070, 12098, 12161, 12231, 12236, powerup);
                assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                        assets, frame, INITIAL, CONTACT, SCORE, LEVEL, new byte[640], banks, PRE, value -> { }), "base " + powerup);
                assertFalse(frame.closed());
                assertEquals(0, frame.captureShared().vIntClock().deltaSinceAdmittedCut());
            }
        }
    }

    // U5-C1 over the committed DeadChkGone cut (Sonic/Main.asm:2410-2444, then Game Over/Main.asm:27-49,64-73).
    // Catches a missing $3B dispatch, a lives writer, writers that replace other level bytes, replaying
    // Deleted/CallReturned metadata, forwarding anything but SubCpuRequest, a C1 draw, or a C1 art request.
    @Test void deadGoneWithLivesRemainingDeletesTheGameOverSlotAfterOnlyItsTwoByteStoresAndOneFadeRequest() throws Exception {
        var ring = new CdRingGlobals(7, 3, 0x81, 2, 0xFF, 0, 5, 2);
        var level = new CdLevelGlobals(0, 1, 0x22, 0x33, 0x44, 0x556677, 0x66, 0x35, 0xFE);
        try (var f = new Fixture(ring, CONTACT, SCORE, level, OptionalLong.of(0))) {
            var cold = f.runtime.powerupArtState();
            assertEquals(new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]), cold);
            f.firstCycle(); seedLevelPlayer(f, 6, 0);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(32).moveWord(X, 0x1234).moveByte(SPRITE_FLAGS, 0xA5));
            f.service(0); f.runtime.spawnBeforeReservedSlots();
            var publication = f.body(2);
            var afterBody = f.runtime.ringGlobals();
            assertEquals(2, afterBody.lives()); assertEquals(0x3B, f.slot(32).id());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertArrayEquals(new byte[64], f.slot(32).copyBytes(), "C1 DeleteObject clears all 64 bytes");
            // MOVE.B #8,powerup and the BCLR #0 store ($FE stays $FE); updateHUDTime 0 is DeadChkGone's write.
            assertEquals(new CdLevelGlobals(0, 0, 0x22, 0x33, 0x44, 0x556677, 0x66, 8, 0xFE), f.runtime.levelGlobals());
            assertEquals(afterBody, f.runtime.ringGlobals(), "ObjGameOver only tests lives");
            var expected = new ArrayList<CdOpeningObjectRuntime.SoundRequest>();
            for (var sound : publication.sounds()) expected.add(new CdOpeningObjectRuntime.PlayerSound(sound));
            expected.add(new CdOpeningObjectRuntime.GameOverSubCpu(CdGameOver.SubCpuCommand.SCMD_FADEPCM));
            assertEquals(expected, f.runtime.lastSoundRequests());
            var cpu = f.runtime.finishAfterCamera(POST);
            assertTrue(cpu.provenance().stream().noneMatch(p -> p.slotRef().wholeIndex() == 32), "C1 never draws");
            f.runtime.finishOrdinaryTail();
            f.service(0);
            assertEquals(8, f.runtime.levelGlobals().powerup(), "bit 7 clear: LoadShieldArt leaves the byte");
            assertEquals(cold, f.runtime.powerupArtState(), "and copies nothing");
        }
    }

    // Game Over/Main.asm:50-88,92-112, then VInt_Level:829-837 -> LoadShieldArt (Powerup/Main.asm:157-176).
    // Catches a stale BSET read ($B5, not $88), dispatch from a captured list (the later OVER child must run Main
    // now), drawing the parent that returned through SubCPUCmd, converting before LoadShieldArt, keeping bit 7,
    // or a partial/aliased window. Literals: Mappings.asm GAME frame 1 and the USA $EF92 oMap operand $20F03E.
    @Test void zeroLivesGameOverRunsTheLaterOverChildNowAndTheNextSelectorEightLoadsItsArtBeforeConversion() throws Exception {
        var ring = new CdRingGlobals(0, 1, 0, 0, 0, 0, 0, 0);
        var level = new CdLevelGlobals(0, 1, 0x22, 0x33, 0x44, 0x556677, 0x66, 0x35, 0xFE);
        try (var f = new Fixture(ring, CONTACT, SCORE, level, OptionalLong.of(0))) {
            var cold = f.runtime.powerupArtState();
            f.firstCycle(); seedLevelPlayer(f, 6, 0);
            f.runtime.placement().pool().clearDynamicSlots();
            f.service(0); f.runtime.spawnBeforeReservedSlots();
            var publication = f.body(2);
            assertEquals(0, f.runtime.ringGlobals().lives()); assertEquals(0x1E0, f.frame.state().timers().reset());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(new CdLevelGlobals(0, 0, 0x22, 0x33, 0x44, 0x556677, 0x66, 0x88, 0xFE), f.runtime.levelGlobals());
            var parent = f.slot(32);
            assertEquals(List.of(0x3B, 2, 0x80, 0x120, 0xE0, 0x8544, 0x20F03E, 0), List.of(parent.id(), parent.routine(),
                    parent.centreX(), parent.word(GAME_OVER_DEST_X), parent.word(X_FRACTION), parent.word(TILE),
                    parent.longBits(MAPPINGS), parent.unsignedByte(MAP_FRAME)), "JMP SubCPUCmd returned before Main");
            var child = f.slot(33);
            assertEquals(List.of(0x3B, 2, 0x1B8, 0x120, 0xE0, 0x8544, 0x20F03E, 1), List.of(child.id(), child.routine(),
                    child.centreX(), child.word(GAME_OVER_DEST_X), child.word(X_FRACTION), child.word(TILE),
                    child.longBits(MAPPINGS), child.unsignedByte(MAP_FRAME)), "the later child ran Main in this pass");
            var expected = new ArrayList<CdOpeningObjectRuntime.SoundRequest>();
            for (var sound : publication.sounds()) expected.add(new CdOpeningObjectRuntime.PlayerSound(sound));
            expected.add(new CdOpeningObjectRuntime.GameOverSubCpu(CdGameOver.SubCpuCommand.SCMD_FADEPCM));
            expected.add(new CdOpeningObjectRuntime.GameOverSubCpu(CdGameOver.SubCpuCommand.SCMD_GMOVERMUS));
            assertEquals(expected, f.runtime.lastSoundRequests());
            var cpu = f.runtime.finishAfterCamera(POST);
            var entries = new ArrayList<Integer>();
            for (int i = 0; i < cpu.provenance().size(); i++) {
                int whole = cpu.provenance().get(i).slotRef().wholeIndex();
                assertNotEquals(32, whole, "the parent queued nothing");
                if (whole == 33) entries.add(i * 8);
            }
            byte[] sat = cpu.bytes();
            // GAME frame 1 pieces (-8,$0D,$0010,+8) and (-8,$0D,$0018,+40), absolute from oYScr $E0 and oX $1B8.
            assertEquals(2, entries.size());
            int a = entries.get(0), b = entries.get(1);
            assertEquals(List.of(0xD8, 0x0D, 0x8554, 0x1C0), List.of(word(sat, a), sat[a + 2] & 255, word(sat, a + 4), word(sat, a + 6)));
            assertEquals(List.of(0xD8, 0x0D, 0x855C, 0x1E0), List.of(word(sat, b), sat[b + 2] & 255, word(sat, b + 4), word(sat, b + 6)));
            f.runtime.finishOrdinaryTail();
            assertEquals(cold, f.runtime.powerupArtState(), "no art moves before the next selector-8 V-int");
            f.service(0);
            assertEquals(0x08, f.runtime.levelGlobals().powerup(), "BCLR #7 clears only the request bit");
            byte[] window = new byte[0x480]; // Art_GameOver's $400-byte copy; the $80-byte RAM tail stays cold zero.
            System.arraycopy(f.assets.shield().copy(3).bytes().copy(), 0, window, 0, 0x400);
            assertEquals(new CdPowerupArtRuntime.State(window, window), f.runtime.powerupArtState());
            var shown = f.runtime.displayedFrame();
            var pixels = CdAssetBytes.copyOf(window);
            for (int tile = 0; tile < 32; tile++) {
                var version = shown.patternVersions().get(BANKS.powerup() + tile);
                if (tile < 16) assertNull(version, "no GAME piece was queued for window tile " + tile);
                else assertEquals(CdObjectPresentation.patternVersion(pixels.slice(tile * 32, 32)), version, "tile " + tile);
            }
        }
    }

    // Game Over/Main.asm:39-46,51-86,92-112 and LoadShieldArt. Catches ignoring the TIME root or its ADDQ ($8A),
    // running the earlier OVER child in its allocation pass, omitting Init's fall-through Main draw, resetting the
    // serviced window between V-ints (the stale Shield picture below), or a picture converted before its load.
    // The $82 powerup byte and the settled slot-36 text are constructed ingress, not a native route.
    @Test void timeOverDrawsFromInitWhileTheEarlierChildWaitsAndItsLoadLeavesTheStaleShieldPictureIntact() throws Exception {
        try (var f = new Fixture(new CdLevelGlobals(0, 0, 0, 0, 3, 0, 2, 0x82, 0x01))) {
            f.firstCycle(); // Its selector-8 V-int services the explicit $82 request: slot 0, Art_Shield.
            byte[] shield = new byte[0x480];
            System.arraycopy(f.assets.shield().copy(0).bytes().copy(), 0, shield, 0, 0x400);
            assertEquals(new CdPowerupArtRuntime.State(shield, shield), f.runtime.powerupArtState());
            assertEquals(0x02, f.runtime.levelGlobals().powerup());
            f.makePlayerAbsent();
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(36).moveByte(ID, 0x3B).moveByte(ROUTINE, 2).moveWord(TILE, 0x8544).moveLong(MAPPINGS, 0x20F042)
                    .moveWord(X_FRACTION, 0xE0).moveWord(X, 0x120).moveWord(GAME_OVER_DEST_X, 0x120));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2); f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            pool.write(pool.state(40).moveByte(ID, 0x3B)); // A clean $3B enters Init at its next dispatch.
            f.service(0);
            var stale = f.runtime.displayedFrame();
            var shieldPixels = CdAssetBytes.copyOf(shield);
            assertEquals(14, stale.patternVersions().size(), "TIME frame 0 draws window tiles 0..13 only");
            for (int tile = 0; tile < 14; tile++) {
                assertEquals(CdObjectPresentation.patternVersion(shieldPixels.slice(tile * 32, 32)),
                        stale.patternVersions().get(BANKS.powerup() + tile), "stale Shield tile " + tile);
            }
            f.runtime.spawnBeforeReservedSlots(); f.body(3); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(new CdLevelGlobals(0, 0, 0, 0, 3, 0, 2, 0x8A, 0), f.runtime.levelGlobals());
            assertEquals(List.of(new CdOpeningObjectRuntime.GameOverSubCpu(CdGameOver.SubCpuCommand.SCMD_FADEPCM)),
                    f.runtime.lastSoundRequests(), "lives remain: no GMOVERMUS");
            var child = f.slot(32);
            assertEquals(List.of(0x3B, 2, 0x1C0, 0x20F042, 1), List.of(child.id(), child.routine(), child.centreX(),
                    child.longBits(MAPPINGS), child.unsignedByte(MAP_FRAME)), "the earlier child waits one pass");
            var parent = f.slot(40);
            assertEquals(List.of(2, 0x88, 0x20F042), List.of(parent.routine(), parent.centreX(), parent.longBits(MAPPINGS)));
            var cpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertEquals(List.of(36, 36, 40, 40), cpu.provenance().stream().map(p -> p.slotRef().wholeIndex()).toList());
            byte[] sat = cpu.bytes();
            // TIME frame 0 pieces (-8,$0D,$0000,-68) and (-8,$09,$0008,-36) from the parent's moved oX $88.
            assertEquals(List.of(0xD8, 0x0D, 0x8544, 0x44), List.of(word(sat, 16), sat[18] & 255, word(sat, 20), word(sat, 22)));
            assertEquals(List.of(0xD8, 0x09, 0x854C, 0x64), List.of(word(sat, 24), sat[26] & 255, word(sat, 28), word(sat, 30)));
            f.service(0);
            assertEquals(0x0A, f.runtime.levelGlobals().powerup());
            byte[] timeOver = new byte[0x480];
            System.arraycopy(f.assets.shield().copy(4).bytes().copy(), 0, timeOver, 0, 0x400);
            assertEquals(new CdPowerupArtRuntime.State(timeOver, timeOver), f.runtime.powerupArtState(),
                    "the complete window: Art_TimeOver's copy plus the cold zero tail");
            var shown = f.runtime.displayedFrame();
            var timePixels = CdAssetBytes.copyOf(timeOver);
            for (int tile = 0; tile < 14; tile++) {
                assertEquals(CdObjectPresentation.patternVersion(timePixels.slice(tile * 32, 32)),
                        shown.patternVersions().get(BANKS.powerup() + tile), "now-serviced tile " + tile);
                assertEquals(CdObjectPresentation.patternVersion(shieldPixels.slice(tile * 32, 32)),
                        stale.patternVersions().get(BANKS.powerup() + tile), "the earlier picture keeps its own pixels");
            }
            f.runtime.spawnBeforeReservedSlots(); f.body(4); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(0x1B8, f.slot(32).centreX(), "the child's first Main runs one pass after its allocation");
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    // VInt_Level (Level/Main.asm:821-847) runs UpdateAnimTiles for ordinary and paused loops alike; PP lag
    // (VInt_Lag:738-742) does not. Catches a lag load or lag re-conversion, a skipped paused load, or paused and
    // ordinary selector-8 dispositions that diverge. The zero-lives text and absent player are constructed ingress.
    @Test void pausedSelectorEightLoadsLikeAnOrdinaryServiceWhileLagRetainsTheRequestWindowAndPicture() throws Exception {
        try (var f = new Fixture(new CdRingGlobals(0, 0, 0, 0, 0, 0, 0, 0))) {
            var cold = f.runtime.powerupArtState();
            f.firstCycle(); f.makePlayerAbsent();
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(32).moveByte(ID, 0x3B));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2); f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertEquals(0x88, f.runtime.levelGlobals().powerup());
            var objects = f.runtime.captureClosedCut(); var actor = f.frame.captureActor(); var shared = f.frame.captureShared();
            var before = f.runtime.displayedFrame();
            f.frame.prepareInput(0); f.runtime.serviceVBlank(PlcLifecyclePhase.LAG);
            assertEquals(0x88, f.runtime.levelGlobals().powerup(), "lag keeps the request bit");
            assertEquals(cold, f.runtime.powerupArtState());
            assertSame(before, f.runtime.displayedFrame());
            f.service(0x80);
            assertTrue(f.frame.paused());
            assertEquals(0x08, f.runtime.levelGlobals().powerup());
            byte[] window = new byte[0x480];
            System.arraycopy(f.assets.shield().copy(3).bytes().copy(), 0, window, 0, 0x400);
            assertEquals(new CdPowerupArtRuntime.State(window, window), f.runtime.powerupArtState());
            var pausedPicture = f.runtime.displayedFrame();
            assertEquals(CdObjectPresentation.patternVersion(CdAssetBytes.copyOf(window).slice(16 * 32, 32)),
                    pausedPicture.patternVersions().get(BANKS.powerup() + 16), "the OVER child's first tile, now serviced");
            f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(objects);
            f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(objects);
            assertEquals(0x88, f.runtime.levelGlobals().powerup()); assertEquals(cold, f.runtime.powerupArtState());
            f.service(0);
            assertFalse(f.frame.paused());
            assertEquals(0x08, f.runtime.levelGlobals().powerup());
            assertEquals(new CdPowerupArtRuntime.State(window, window), f.runtime.powerupArtState());
            assertEquals(pausedPicture, f.runtime.displayedFrame(), "both selector-8 dispositions convert the same previous SAT");
        }
    }

    // Catches a snapshot that drops, re-derives or cold-resets the window or its request byte, or that services
    // during restore: the pending request and the loaded nonzero window both restore exactly and replay forward.
    @Test void nonzeroPowerupWindowAndItsRequestByteRestoreExactlyAndReplayForward() throws Exception {
        try (var f = new Fixture(new CdRingGlobals(0, 0, 0, 0, 0, 0, 0, 0))) {
            var cold = f.runtime.powerupArtState();
            f.firstCycle(); f.makePlayerAbsent();
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(32).moveByte(ID, 0x3B));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2); f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var requested = f.runtime.captureClosedCut();
            var requestedActor = f.frame.captureActor(); var requestedShared = f.frame.captureShared();
            assertEquals(0x88, requested.levelGlobals().powerup()); assertEquals(cold, requested.powerupArtState());
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(3); f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var loaded = f.runtime.captureClosedCut();
            var loadedActor = f.frame.captureActor(); var loadedShared = f.frame.captureShared();
            byte[] window = new byte[0x480];
            System.arraycopy(f.assets.shield().copy(3).bytes().copy(), 0, window, 0, 0x400);
            assertEquals(new CdPowerupArtRuntime.State(window, window), loaded.powerupArtState());
            assertEquals(0x08, loaded.levelGlobals().powerup());
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(4); f.runtime.runDynamicSlotsAfterReservedSlots();
            var cpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var slots = pool.capture().slots(); var shown = f.runtime.displayedFrame(); var level = f.runtime.levelGlobals();
            f.frame.validateActorRestore(loadedActor); f.frame.validateSharedRestore(loadedShared); f.runtime.validateRestore(loaded);
            f.frame.restoreActor(loadedActor); f.frame.restoreShared(loadedShared); f.runtime.restore(loaded);
            assertEquals(loaded.powerupArtState(), f.runtime.powerupArtState(), "the nonzero window, not a cold reset");
            assertEquals(0x08, f.runtime.levelGlobals().powerup());
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(4); f.runtime.runDynamicSlotsAfterReservedSlots();
            var replay = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertArrayEquals(cpu.bytes(), replay.bytes()); assertEquals(slots, pool.capture().slots());
            assertEquals(shown, f.runtime.displayedFrame()); assertEquals(level, f.runtime.levelGlobals());
            assertEquals(loaded.powerupArtState(), f.runtime.powerupArtState());
            f.frame.validateActorRestore(requestedActor); f.frame.validateSharedRestore(requestedShared);
            f.runtime.validateRestore(requested);
            f.frame.restoreActor(requestedActor); f.frame.restoreShared(requestedShared); f.runtime.restore(requested);
            assertEquals(cold, f.runtime.powerupArtState()); assertEquals(0x88, f.runtime.levelGlobals().powerup());
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(3); f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertEquals(loaded.powerupArtState(), f.runtime.powerupArtState(), "the replayed load");
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(4); f.runtime.runDynamicSlotsAfterReservedSlots();
            replay = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertArrayEquals(cpu.bytes(), replay.bytes()); assertEquals(slots, pool.capture().slots());
            assertEquals(shown, f.runtime.displayedFrame()); assertEquals(level, f.runtime.levelGlobals());
        }
    }

    // LoadShieldArt reads ShieldArtIndex after BCLR #7 (Powerup/Main.asm:158-164); selector $80 gives
    // ((0 - 2) & $FF) * 2 = $1FC, past the five pointers. Catches committing the cleared byte or a partial window
    // before the failure, converting or retrying afterwards, Finish/count or the Start toggle after the failed cut,
    // or claiming to roll back the Frame's completed controller read.
    @Test void invalidActiveSelectorAtTheVIntCutClosesBothOwnersBeforeFinishWithNothingCommittedOrRolledBack() throws Exception {
        try (var f = new Fixture(new CdLevelGlobals(0, 0, 0, 0, 3, 0, 2, 0x80, 0))) {
            var cold = f.runtime.powerupArtState();
            var admitted = f.runtime.captureClosedCut();
            assertEquals(PlcLifecyclePhase.NORMAL_PAUSE, f.frame.prepareInput(0x80).phase());
            var failure = assertThrows(CdPowerupArtRuntime.UnsupportedArt.class,
                    () -> f.runtime.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE));
            assertEquals(0x1FC, failure.pointerOffset());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
            assertEquals(0, f.frame.vIntClock().deltaSinceAdmittedCut(), "no VInt_Finish/count after the failed cut");
            assertFalse(f.frame.paused(), "the Start toggle follows Finish, so it never ran");
            assertEquals(0x80, f.frame.environment().controls().rawHeld(), "the completed ReadController stays committed");
            assertEquals(0x80, f.runtime.levelGlobals().powerup(), "no Result existed, so no cleared byte was committed");
            assertEquals(cold, f.runtime.powerupArtState(), "and no window either");
            assertThrows(IllegalStateException.class, f.runtime::displayedFrame);
            assertThrows(IllegalStateException.class, () -> f.runtime.restore(admitted));
        }
    }

    /** The restart row after a constructed routine-8 zero call: service, spawn, the Restart call, then every slot. */
    private static void runRestartRow(Fixture f) {
        f.service(0);
        f.runtime.spawnBeforeReservedSlots();
        assertEquals(CdPlayerBody.Boundary.RESTART_RETURN, f.body(2).result().boundary());
        f.runtime.runDynamicSlotsAfterReservedSlots();
    }

    private static void assertRestartCut(CdOpeningObjectRuntime.Snapshot cut, Fixture f) {
        var now = f.runtime.captureClosedCut();
        assertEquals(1, now.levelGlobals().levelRestart());
        assertEquals(cut.levelGlobals(), now.levelGlobals()); assertEquals(cut.ringGlobals(), now.ringGlobals());
        assertEquals(cut.spillGlobals(), now.spillGlobals()); assertEquals(cut.contactGlobals(), now.contactGlobals());
        assertEquals(cut.scoreGlobals(), now.scoreGlobals()); assertEquals(cut.devGlobals(), now.devGlobals());
        assertEquals(cut.powerupArtState(), now.powerupArtState());
        assertEquals(cut.lastSoundRequests(), now.lastSoundRequests());
        assertEquals(cut.placement().status(), now.placement().status());
        assertEquals(cut.placement().pool().slots(), now.placement().pool().slots());
        assertArrayEquals(cut.placement().savedFlags().copy(), now.placement().savedFlags().copy());
    }

    /** The marker rejects before Frame service: no ReadController, DPLC, LoadShieldArt, Finish/count or picture. */
    private static void assertVIntRejected(Fixture f, PlcLifecyclePhase phase) {
        var clock = f.frame.vIntClock(); var controls = f.frame.environment().controls();
        var level = f.runtime.levelGlobals(); var powerup = f.runtime.powerupArtState();
        int publications = f.artPublications.size();
        f.runtime.setPreScrollForeground(PRE);
        f.frame.prepareInput(0x10);
        assertThrows(IllegalStateException.class, () -> f.runtime.serviceVBlank(phase), String.valueOf(phase));
        assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        assertEquals(clock, f.frame.vIntClock(), "no VInt_Finish/count");
        assertEquals(controls, f.frame.environment().controls(), "no ReadController commit");
        assertEquals(level, f.runtime.levelGlobals()); assertEquals(powerup, f.runtime.powerupArtState());
        assertEquals(publications, f.artPublications.size());
    }

    private static CdOpeningObjectRuntime restart(CdR11aAssets assets, CdPlayerFrameRuntime frame, CdAssetBytes bank,
                                                  CdPowerupArtRuntime.State powerup, byte[] sat,
                                                  CdOpeningObjectRuntime.LogicalBanks banks) throws Exception {
        return CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(assets, frame, INITIAL, CONTACT, SCORE, LEVEL,
                bank, powerup, sat, banks, PRE, value -> { });
    }

    private static void seedLevelPlayer(Fixture f, int routine, int reset) {
        var actor = f.frame.captureActor(); var s = actor.state(); var m = s.motion();
        int y = f.frame.environment().movement().bounds().bottom() + 0x110;
        var next = new CdPlayerMotionState(64 << 16, y << 16, 0, -0x700, 0, routine, 0, 2, 4, 9, 19,
                0x18, 0x18, 0, 0, 0, 0, 0, m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var timers = s.timers();
        f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(),
                new CdPlayerBody.State(next, new CdPlayerBody.AnimationCursor(0, 0, 0), s.header(),
                        new CdPlayerBody.Timers(timers.hurt(), timers.invincibility(), timers.speedShoes(), reset),
                        s.history(), s.rotationDistance(), s.objectLinks()), Optional.empty()));
    }

    /** Constructed ingress for transaction tests, not evidence of a normal-input route to this cut. */
    private static void seedContactPlayer(Fixture f) {
        var cut = f.frame.captureActor(); var s = cut.state();
        var m = new CdPlayerMotionState(64 << 16, 0x7580000, 0, 0x100, 0, 2, 0, 6, 4,
                7, 14, 2, 2, 1, 0, 0, 0, 0, 0, 0);
        f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(cut.owner(),
                new CdPlayerBody.State(m, new CdPlayerBody.AnimationCursor(0, 0, 0), s.header(),
                        s.timers(), s.history(), s.rotationDistance(), s.objectLinks()), Optional.empty()));
    }

    private static final class Fixture implements AutoCloseable {
        final CdR11aAssets assets;
        final CdPlayerFrameRuntime frame;
        final CdOpeningObjectRuntime runtime;
        final List<CdPlayerArtRuntime.State> artPublications = new ArrayList<>();
        final List<Integer> angleWrites = new ArrayList<>();
        Consumer<Integer> onAngle = value -> { };

        Fixture() throws Exception { this(INITIAL, CONTACT, SCORE); }
        Fixture(CdRingGlobals initial) throws Exception { this(initial, CONTACT, SCORE); }
        Fixture(CdRingGlobals initial, CdPlayerBody.ContactGlobals contact, CdOpeningObjectRuntime.ScoreGlobals score) throws Exception {
            this(initial, contact, score, true);
        }
        Fixture(CdRingGlobals initial, CdPlayerBody.ContactGlobals contact,
                CdOpeningObjectRuntime.ScoreGlobals score, boolean declaredClock) throws Exception {
            this(initial, contact, score, declaredClock ? OptionalLong.of(0) : OptionalLong.empty());
        }
        Fixture(CdRingGlobals initial, CdPlayerBody.ContactGlobals contact,
                CdOpeningObjectRuntime.ScoreGlobals score, OptionalLong seed) throws Exception {
            this(initial, contact, score, LEVEL, seed);
        }
        Fixture(CdLevelGlobals level) throws Exception { this(INITIAL, CONTACT, SCORE, level, OptionalLong.of(0)); }
        Fixture(CdRingGlobals initial, CdPlayerBody.ContactGlobals contact, CdOpeningObjectRuntime.ScoreGlobals score,
                CdLevelGlobals level, OptionalLong seed) throws Exception {
            assets = load();
            frame = seed.isPresent() ? CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, seed.getAsLong())
                    : CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets);
            frame.bindArtPublication((request, state) -> artPublications.add(state));
            runtime = CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(assets, frame, initial, contact, score,
                    level, new byte[640], BANKS, PRE, value -> { angleWrites.add(value); onAngle.accept(value); });
        }
        /** Restart-factory composition: Root's carry is explicit ingress, never a route or a foreign restore. */
        Fixture(CdLevelGlobals level, CdAssetBytes savedFlags, CdPowerupArtRuntime.State powerup,
                CdPlayerFrameRuntime.RestartIngress player) throws Exception {
            assets = load();
            frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0, player);
            frame.bindArtPublication((request, state) -> artPublications.add(state));
            runtime = CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(assets, frame, INITIAL, CONTACT, SCORE,
                    level, savedFlags, powerup, new byte[640], BANKS, PRE,
                    value -> { angleWrites.add(value); onAngle.accept(value); });
        }
        void service(int held) { service(held, PRE); }
        /** Constructed pre-scroll camera words test the device contract; they are not natural route evidence. */
        void service(int held, CameraWords camera) {
            runtime.setPreScrollForeground(camera);
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
                                    s.header().priority(), s.header().width()), s.timers(), s.history(), s.rotationDistance(), s.objectLinks()), Optional.empty()));
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
