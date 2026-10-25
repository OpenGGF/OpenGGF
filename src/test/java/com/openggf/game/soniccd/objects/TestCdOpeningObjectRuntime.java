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

    @Test void planeServiceCutRunsAfterPowerupArtAndBeforeFinishWithoutASecondInputOrClock() throws Exception {
        var level = new CdLevelGlobals(0, 0, 0, 0, 3, 0, 2, 0x88, 0);
        try (var f = new Fixture(level)) {
            f.runtime.setPreScrollForeground(PRE); var selection = f.frame.prepareInput(1);
            var calls = new ArrayList<Integer>();
            f.runtime.serviceVBlank(selection.phase(), () -> {
                assertEquals(CdOpeningObjectRuntime.Phase.SERVICING, f.runtime.phase());
                assertEquals(8, f.runtime.levelGlobals().powerup(), "LoadShieldArt completed before plane service");
                assertNotEquals(new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]), f.runtime.powerupArtState());
                assertEquals(1, f.frame.environment().controls().rawHeld(), "ReadController already committed");
                assertEquals(0, f.frame.vIntClock().deltaSinceAdmittedCut(), "Finish/count has not run");
                calls.add(1);
            });
            assertEquals(List.of(1), calls); assertEquals(1, f.frame.vIntClock().deltaSinceAdmittedCut());
            assertEquals(CdOpeningObjectRuntime.Phase.SERVICED, f.runtime.phase());
        }
    }

    @Test void planeServiceCutRunsOnEveryPausedSelectorEightButNeverLag() throws Exception {
        try (var f = new Fixture()) {
            var calls = new ArrayList<Long>();
            f.runtime.setPreScrollForeground(PRE); var selected = f.frame.prepareInput(0x80);
            assertEquals(PlcLifecyclePhase.NORMAL_PAUSE, selected.phase());
            f.runtime.serviceVBlank(selected.phase(), () -> calls.add(f.frame.vIntClock().deltaSinceAdmittedCut()));
            assertTrue(f.frame.paused());
            selected = f.frame.prepareInput(0);
            assertEquals(PlcLifecyclePhase.NORMAL_PAUSE, selected.phase());
            f.runtime.serviceVBlank(selected.phase(), () -> calls.add(f.frame.vIntClock().deltaSinceAdmittedCut()));
            assertTrue(f.frame.paused());
            f.frame.prepareInput(0);
            f.runtime.serviceVBlank(PlcLifecyclePhase.LAG, () -> fail("PP lag has no plane copy/draw service"));
            assertEquals(List.of(0L, 1L), calls);
            assertEquals(3, f.frame.vIntClock().deltaSinceAdmittedCut());
        }
    }

    @Test void nullPlaneServiceDoesNotConsumeThePreparedInputOrCloseEitherOwner() throws Exception {
        try (var f = new Fixture()) {
            f.runtime.setPreScrollForeground(PRE); var selected = f.frame.prepareInput(0);
            assertThrows(NullPointerException.class, () -> f.runtime.serviceVBlank(selected.phase(), null));
            assertFalse(f.runtime.closed()); assertFalse(f.frame.closed());
            assertEquals(0, f.frame.vIntClock().deltaSinceAdmittedCut());
            f.runtime.serviceVBlank(selected.phase(), () -> { });
            assertEquals(1, f.frame.vIntClock().deltaSinceAdmittedCut());
            assertEquals(CdOpeningObjectRuntime.Phase.SERVICED, f.runtime.phase());
        }
    }

    @Test void planeServiceFailureRetainsCompletedArtButClosesBeforeFinishAndRejectsRendering() throws Exception {
        var level = new CdLevelGlobals(0, 0, 0, 0, 3, 0, 2, 0x88, 0);
        try (var f = new Fixture(level)) {
            f.runtime.setPreScrollForeground(PRE); var selected = f.frame.prepareInput(0x80);
            var failure = new IllegalStateException("constructed plane boundary");
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> f.runtime.serviceVBlank(selected.phase(), () -> { throw failure; })));
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
            assertEquals(8, f.runtime.levelGlobals().powerup(), "Completed source work is not rolled back");
            assertEquals(0, f.frame.vIntClock().deltaSinceAdmittedCut());
            assertFalse(f.frame.paused(), "Start toggles only after Finish");
            assertThrows(IllegalStateException.class, f.runtime::displayedFrame);
        }
    }

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
            CdPlayerBody.State expectedPlayer = null; CdPlayerBody.Environment expectedEnvironment = null;
            List<CdObjectState> expectedSlots = null; byte[] expectedSat = null;
            for (int replay = 0; replay < 2; replay++) {
                if (replay != 0) {
                    f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(objects);
                    f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(objects);
                }
                f.service(0); f.runtime.spawnBeforeReservedSlots();
                assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.body(3).result().boundary());
                f.runtime.runDynamicSlotsAfterReservedSlots();
                assertEquals(40, f.frame.state().objectLinks().standObject());
                assertEquals(8, f.frame.state().motion().flags() & 10);
                assertTrue(f.frame.state().motion().centreX16_16() > expected.motion().centreX16_16());
                assertEquals(expected.motion().centreY16_16(), f.frame.state().motion().centreY16_16());
                var completed = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
                if (replay == 0) {
                    expectedPlayer = f.frame.state(); expectedEnvironment = f.frame.environment();
                    expectedSlots = pool.capture().slots(); expectedSat = completed.bytes();
                } else {
                    assertEquals(expectedPlayer, f.frame.state()); assertEquals(expectedEnvironment, f.frame.environment());
                    assertEquals(expectedSlots, pool.capture().slots()); assertArrayEquals(expectedSat, completed.bytes());
                }
            }
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

    // Begins at a native Solid-produced standing state, then ordinary Action input runs the real Main jump.
    // The later Float calls, rather than MODE, detach the link. This is constructed device ingress, not a route.
    @Test void supportedFloatJumpKeepsItsLinkUntilTheLaterSolidPassAndReplaysFromTheStandingCut() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            seedSolidPlayer(f, 2, 0, 0, 0, 0x100, 64 << 16, 0x721 << 16);
            pool.write(floatBlock(pool.state(40), 2, 0x84, 0));
            f.runtime.runDynamicSlotsAfterReservedSlots(); f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertEquals(40, f.frame.state().objectLinks().standObject());
            var actor = f.frame.captureActor(); var shared = f.frame.captureShared(); var objects = f.runtime.captureClosedCut();
            CdPlayerBody.State after = null; List<CdObjectState> slots = null; byte[] sat = null;
            for (int replay = 0; replay < 2; replay++) {
                if (replay != 0) {
                    f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(objects);
                    f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(objects);
                }
                f.service(0x10); f.runtime.spawnBeforeReservedSlots(); var publication = f.body(3);
                assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, publication.result().boundary());
                assertEquals(CdPlayerMovement.Boundary.JUMP_MODE_RETURN, publication.result().modeBoundary().orElseThrow());
                assertEquals(-0x680, f.frame.state().motion().yVelocity());
                assertEquals(40, f.frame.state().objectLinks().standObject(), "CheckJump leaves the link for the object owner");
                assertEquals(8, f.frame.state().motion().flags() & 8);
                f.runtime.runDynamicSlotsAfterReservedSlots();
                assertEquals(0, f.frame.state().objectLinks().standObject());
                assertEquals(0, f.frame.state().motion().flags() & 8);
                assertEquals(0, f.slot(40).flags() & 8);
                var cpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
                if (replay == 0) { after = f.frame.state(); slots = pool.capture().slots(); sat = cpu.bytes(); }
                else { assertEquals(after, f.frame.state()); assertEquals(slots, pool.capture().slots()); assertArrayEquals(sat, cpu.bytes()); }
            }
        }
    }

    // Dirty-ID construction exercises generic ChkFlipper and the later ENEMY handler on the SAME slot.
    // Without Body's local overlay the touch image would restore animation0 over the earlier native store1.
    @Test void flipperAnimationPatchAndLaterEnemyTouchFoldOnceAgainstTheOriginalPoolCut() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            seedSolidPlayer(f, 8, 40, 0, 0, 0, 64 << 16, 0x721 << 16);
            pool.write(pool.state(40).moveByte(ID, 0x1E).moveByte(SPRITE_FLAGS, 0x80)
                    .moveByte(COLLISION_TYPE, 0x2F).moveByte(WIDTH, 12)
                    .moveWord(X, 64).moveWord(Y, 0x740));
            f.service(0x10); f.runtime.spawnBeforeReservedSlots(); var publication = f.body(2);
            assertEquals(0x18, f.slot(40).id(), "Main kills the constructed flipper/enemy before its dynamic visit");
            assertEquals(1, f.slot(40).unsignedByte(ANIMATION), "the later response must preserve the earlier patch");
            assertEquals(10, f.runtime.scoreGlobals().score());
            assertEquals(List.of(new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_SPRING),
                    new CdPlayerFrameRuntime.MovementSound(CdPlayerMovement.Sound.FM_JUMP),
                    new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_DESTROY)), publication.sounds());
            assertEquals(publication.sounds().stream().map(CdOpeningObjectRuntime.PlayerSound::new).toList(),
                    f.runtime.lastSoundRequests());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(0x18, f.slot(40).id(), "live later dispatch ran the newly selected Explosion");
            assertTrue(pool.capture().slots().stream().anyMatch(slot -> slot.id() == 0x1C));
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    // Constructed device contracts below use actual authenticated media/owners, not natural route evidence.
    @Test void eleventhSpringBankDerivesAfterFloatForEveryLegacyConstructorAndRejectsBeforeAdmission() throws Exception {
        assertThrows(NullPointerException.class, () -> new CdOpeningObjectRuntime.SpringFm(null));
        assertEquals(12326, BANKS.spring());
        var explicit = new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449, 464);
        assertEquals(explicit, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138));
        assertEquals(explicit, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236));
        assertEquals(explicit, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374));
        assertEquals(explicit, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413));
        assertEquals(explicit, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449));
        assertEquals(98, new CdOpeningObjectRuntime.LogicalBanks(1, 2, 3, 4, 5, 6, 7, 8, 47, 83).spring());
        var assets = load();
        var saved = CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]);
        var window = new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]);
        // Float's last tile, Powerup, player, a negative and the 36-tile end overflow. Empty SAT cannot hide them.
        for (int spring : List.of(12325, 12275, 10000, -1, Integer.MAX_VALUE - 34)) {
            var banks = new CdOpeningObjectRuntime.LogicalBanks(10000, 11000, 12000, 12070, 12098,
                    12161, 12231, 12236, 12275, 12311, spring);
            try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0)) {
                assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                        assets, frame, INITIAL, CONTACT, SCORE, LEVEL, new byte[640], banks, PRE, value -> { }));
                assertThrows(IllegalArgumentException.class, () -> restart(assets, frame, saved, window, new byte[640], banks));
                assertFalse(frame.closed()); assertEquals(0, frame.vIntClock().deltaSinceAdmittedCut());
            }
        }
    }

    // Init RTS precedes Solid: all three normal families draw in physical order without a player launch.
    @Test void springInitUsesActualIndependentRootsAndNormalArtThroughThePhysicalQueue() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f);
            var pool = f.runtime.placement().pool();
            pool.write(pool.state(40).moveByte(ID, 0x0A).moveWord(X, 0x40).moveWord(Y, 0x780));
            pool.write(pool.state(41).moveByte(ID, 0x0A).moveWord(X, 0x80).moveWord(Y, 0x780).moveByte(SUBTYPE, 6));
            pool.write(pool.state(42).moveByte(ID, 0x0A).moveWord(X, 0xC0).moveWord(Y, 0x780).moveByte(SPRITE_FLAGS, 2));
            var before = f.frame.state(); var environment = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(before, f.frame.state()); assertEquals(environment, f.frame.environment());
            assertEquals(sounds, f.runtime.lastSoundRequests());
            assertEquals(List.of(2, 8, 14), List.of(f.slot(40).routine(), f.slot(41).routine(), f.slot(42).routine()));
            assertEquals(List.of(0x209BA0, 0x209BA6, 0x209BA0),
                    List.of(f.slot(40).longBits(MAPPINGS), f.slot(41).longBits(MAPPINGS), f.slot(42).longBits(MAPPINGS)));
            assertEquals(List.of(0x520, 0x2520, 0x520), List.of(f.slot(40).word(TILE), f.slot(41).word(TILE), f.slot(42).word(TILE)));
            assertEquals(List.of(-4096, -2560, -4096), List.of((int) f.slot(40).signedWord(SPRING_SPEED),
                    (int) f.slot(41).signedWord(SPRING_SPEED), (int) f.slot(42).signedWord(SPRING_SPEED)));
            assertEquals(List.of(16, 8, 16), List.of(f.slot(40).unsignedByte(WIDTH), f.slot(41).unsignedByte(WIDTH), f.slot(42).unsignedByte(WIDTH)));
            assertEquals(List.of(8, 16, 8), List.of(f.slot(40).unsignedByte(Y_RADIUS), f.slot(41).unsignedByte(Y_RADIUS), f.slot(42).unsignedByte(Y_RADIUS)));
            assertEquals(2, f.slot(42).flags());
            for (int whole : List.of(40, 41, 42)) {
                assertEquals(f.slot(whole).centreX(), f.slot(whole).word(SPRING_ANCHOR_X));
                assertEquals(0, f.slot(whole).spriteFlags() & 0x80);
            }
            var sat = f.runtime.finishAfterCamera(POST);
            var springPieces = sat.provenance().stream().filter(v -> v.slotRef().wholeIndex() >= 40).toList();
            assertEquals(List.of(40, 40, 41, 41, 42, 42), springPieces.stream().map(v -> v.slotRef().wholeIndex()).toList());
            assertEquals(List.of(0, 0, 1, 1, 2, 2), springPieces.stream().map(Provenance::queueOrdinal).toList());
            assertTrue(springPieces.stream().allMatch(v -> v.queueBucket() == 4));
            for (int whole : List.of(40, 41, 42)) assertEquals(0x80, f.slot(whole).spriteFlags() & 0x80);
            f.runtime.finishOrdinaryTail(); f.service(0x80);
            var shown = f.runtime.displayedFrame();
            var ids = shown.patternVersions().keySet().stream().filter(id -> id >= BANKS.spring() && id < BANKS.spring() + 36).toList();
            assertFalse(ids.isEmpty());
            for (int id : ids) assertEquals(CdObjectPresentation.patternVersion(
                    f.assets.openingObjects().springArtBank().tile(0x520 + id - BANKS.spring())), shown.patternVersions().get(id));
        }
    }

    // Solid lands at $780-(8+19)=$765; Spring's integer +8 gives $76D and keeps both fractions.
    @Test void springUpCommitsSolidThenExactLaunchFieldsAndKeepsAllOtherFrameOwners() throws Exception {
        for (int subtype : List.of(0, 2)) {
            try (var f = new Fixture()) {
                springDeviceCut(f); var pool = f.runtime.placement().pool();
                seedSpringPlayer(f, 0x01008000, 0x07671234, 0x234, 0x100, 2, 0, 17);
                pool.write(spring(f, pool.state(40), 2, 0x84, 0, subtype));
                var before = f.frame.state(); var env = f.frame.environment(); var display = f.frame.display();
                var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
                f.runtime.runDynamicSlotsAfterReservedSlots();
                var m = before.motion();
                var expected = new CdPlayerMotionState(0x01008000, 0x076D1234, 0x234, subtype == 0 ? -4096 : -2560,
                        0x345, 2, 0, 2, m.spriteFlags(), 9, 19, 0x10, 0x12, 0, 17, 0x4567, 1, 1, 0x31, 0x42);
                assertEquals(new CdPlayerBody.State(expected, before.animation(), before.header(), before.timers(),
                        before.history(), before.rotationDistance(), new CdPlayerBody.ObjectLinks(0, 40)), f.frame.state());
                assertEquals(env, f.frame.environment()); assertEquals(display, f.frame.display());
                assertEquals(4, f.slot(40).routine()); assertEquals(0, f.slot(40).flags() & 8);
                sounds.add(new CdOpeningObjectRuntime.SpringFm(CdSpring.FmSound.FM_SPRING));
                assertEquals(sounds, f.runtime.lastSoundRequests());
                f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
                assertEquals(subtype == 0 ? -4096 : -2560, f.frame.state().motion().yVelocity(), "no effect replay at build/tail");
            }
        }
    }

    // Native bottom Solid gives $79D; Spring -8 gives $795. GetOff's sound precedes the separate Spring call.
    @Test void springDownCommitsChargeStopThenLaunchThenSpringFmAndItsFirstAnimation() throws Exception {
        for (int subtype : List.of(0, 2)) {
            try (var f = new Fixture()) {
                springDeviceCut(f); var pool = f.runtime.placement().pool();
                seedSpringPlayer(f, 0x01008000, 0x079B1234, 0x234, -0x400, 0x0A, 40, 17);
                pool.write(spring(f, pool.state(40), 14, 0x86, 0x0A, subtype).moveByte(ROUTINE_2, 0xA7));
                var before = f.frame.state(); var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
                f.runtime.runDynamicSlotsAfterReservedSlots();
                var expected = new CdPlayerMotionState(0x01008000, 0x07951234, 0x234, subtype == 0 ? 4096 : 2560,
                        0x345, 2, 0x40, 2, before.motion().spriteFlags(), 9, 19, 0x1A, 0x12, 1, 17, 0x4567, 0, 0, 0x31, 0x42);
                assertEquals(new CdPlayerBody.State(expected, before.animation(), before.header(), before.timers(),
                        before.history(), before.rotationDistance(), new CdPlayerBody.ObjectLinks(0, 0)), f.frame.state());
                sounds.add(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)));
                sounds.add(new CdOpeningObjectRuntime.SpringFm(CdSpring.FmSound.FM_SPRING));
                assertEquals(sounds, f.runtime.lastSoundRequests());
                assertEquals(16, f.slot(40).routine()); assertEquals(0, f.slot(40).unsignedByte(ROUTINE_2));
                assertEquals(1, f.slot(40).unsignedByte(ANIMATION_FRAME)); assertEquals(0, f.slot(40).unsignedByte(MAP_FRAME));
                assertEquals(0, f.slot(40).flags() & 8);
                f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
                assertEquals(sounds, f.runtime.lastSoundRequests());
            }
        }
    }

    // Width8 gives half18: X$F0 is depth2, Solid resolves to $EE, then Spring gives $E6 or $F6.
    @Test void springSideKeepsFractionsUsesMoveLock3eAndFinalPlayerSatInsteadOfEarlierBodyPose() throws Exception {
        for (int flip : List.of(0, 1)) for (int rolling : List.of(0, 4)) {
            try (var f = new Fixture()) {
                var publication = springDeviceCut(f); var pool = f.runtime.placement().pool();
                var cut = publication.result().effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                        .map(CdPlayerBody.DisplayEligibility.class::cast).filter(CdPlayerBody.DisplayEligibility::eligible).findFirst().orElseThrow();
                seedSpringPlayer(f, 0x00F08000, 0x07801234, 0x234, 0, rolling, 0, 9);
                pool.write(spring(f, pool.state(40), 8, 0x84 | flip, flip, 4));
                var before = f.frame.state(); var display = f.frame.display();
                f.runtime.runDynamicSlotsAfterReservedSlots();
                int x = flip == 0 ? 0xE6 : 0xF6, speed = flip == 0 ? 4096 : -4096;
                var expected = new CdPlayerMotionState((x << 16) | 0x8000, 0x07801234, speed, 0, speed, 2, 0,
                        rolling | flip, before.motion().spriteFlags(), 9, 19, rolling == 0 ? 0 : 0x1A, 0x12, 1, 9, 15, 1, 1, 0x31, 0x42);
                assertEquals(new CdPlayerBody.State(expected, before.animation(), before.header(), before.timers(),
                        before.history(), before.rotationDistance(), before.objectLinks()), f.frame.state());
                assertEquals(0x2233, f.frame.state().timers().invincibility(), "$32 is not Spring's $3E move lock");
                assertEquals(display, f.frame.display(), "earlier Body projection is not rewritten by launch");
                assertEquals(10, f.slot(40).routine()); assertEquals(flip, f.slot(40).flags());
                var sat = f.runtime.finishAfterCamera(POST); int at = satEntry(sat, 0);
                var provenance = sat.provenance().get(at / 8);
                assertEquals(cut.priority() & 7, provenance.queueBucket());
                assertNotEquals(cut.centreXWord(), x); assertEquals(0, expected.spriteFlags() & 3);
                var map = f.assets.sonic().mappingFrames().get(before.animation().mapFrame());
                assertTrue(map.pieceCount() > 0);
                assertEquals((128 + x + map.bytes().signedByte(5)) & 0x1FF, word(sat.bytes(), at + 6));
                assertNotEquals((128 + cut.centreXWord() + map.bytes().signedByte(5)) & 0x1FF, word(sat.bytes(), at + 6));
                assertEquals((128 + 0x780 - PRE.yWord() + map.bytes().signedByte(1)) & 0x1FF, word(sat.bytes(), at));
                f.runtime.finishOrdinaryTail();
            }
        }
    }

    // Real pure Solid is a composition oracle here, not a fabricated Result or an independent physics proof.
    @Test void springSolidPublishesProbeGlobalsAndOlderStandImageBeforeTheLaterPhysicalSlots() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpringPlayer(f, 0x01008000, 0x07671234, 0x234, 0x100, 8, 41, 0);
            var shared = f.frame.captureShared(); var e = shared.environment(); var m = e.movement(); var g = m.globals();
            var seeded = new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), 0x55, 0x66, g.boredTimer(), g.scoreChain(),
                    g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance());
            f.frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), new CdPlayerBody.Environment(
                    new CdPlayerMovement.Environment(m.physics(), m.bounds(), m.gates(), seeded), e.controls(), e.globals(), e.dynamicSlots()),
                    shared.artState(), shared.paused(), shared.vIntClock()));
            pool.write(spring(f, pool.state(40), 2, 0x84, 0, 0).moveWord(X_VELOCITY, 0x123).moveWord(Y_VELOCITY, -0x180));
            pool.write(floatBlock(pool.state(41), 2, 4, 0xA8));
            pool.write(spring(f, pool.state(42), 2, 4, 0, 0).moveByte(ROUTINE_2, 5)
                    .moveWord(X_FRACTION, 0x4567).moveWord(Y_FRACTION, 0x89AB));
            var state = f.frame.state(); var h = state.header(); var env = f.frame.environment();
            var expected = CdSolidObject.fromAssets(f.assets).run(CdSolidObject.Entry.SOLID_OBJECT, f.slot(40),
                    new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(state.motion(), h.tile(), h.priority(), state.timers().hurt(), state.rotationDistance()),
                            h.slotId(), state.objectLinks().pushObject(), state.objectLinks().standObject()),
                    new CdSolidObject.Gates(env.globals().debugMode(), env.movement().gates().miniSonic()),
                    new CdSolidObject.Globals(env.globals().invincible(), g.scoreChain(), 0x55, 0x66), pool.capture());
            assertEquals(CdSolidObject.Path.STAND_ON, expected.path());
            assertEquals(List.of(41), expected.otherSlots().stream().map(v -> v.identity().wholeIndex()).toList());
            assertTrue(expected.globalWrites().contains(CdSolidObject.GlobalField.PRIMARY_ANGLE));
            assertTrue(expected.globalWrites().contains(CdSolidObject.GlobalField.SECONDARY_ANGLE));
            int angleCalls = f.angleWrites.size();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            var after = f.frame.state().motion(); var actual = f.frame.environment().movement().globals();
            assertEquals(0xA0, f.slot(41).flags(), "native StandOn releases the older a2 image");
            assertEquals(40, f.frame.state().objectLinks().standObject());
            assertEquals(expected.player().actor().motion().centreX16_16(), after.centreX16_16());
            assertEquals(0xA300, after.centreX16_16() & 0xFFFF, "$8000 + ($123<<8) retains the native longword carry");
            assertEquals(0x076B9234, after.centreY16_16(), "$765.1234 - $180<<8, then integer +8");
            assertEquals(expected.globals().primaryAngle(), actual.primaryAngle());
            assertEquals(expected.globals().secondaryAngle(), actual.secondaryAngle());
            assertEquals(angleCalls, f.angleWrites.size(), "Frame adopts both buffers once; no PrimaryAngle metadata replay");
            assertEquals(after.centreX16_16() >>> 16, f.slot(42).centreX());
            assertEquals(0x76B, f.slot(42).centreY(), "later mirror reads this physical pass's launched player");
            assertEquals(0x4567, f.slot(42).word(X_FRACTION)); assertEquals(0x89AB, f.slot(42).word(Y_FRACTION));
            assertEquals(1, f.runtime.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.SpringFm.class::isInstance).count());
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    @Test void springCompositeRestoreKeepsTheLaunchAndReplaysTheNextRealBodyAnimationAndPicture() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpringPlayer(f, 0x01008000, 0x07671234, 0, 0x100, 2, 0, 0, 0);
            pool.write(spring(f, pool.state(40), 2, 0x84, 0, 0));
            f.runtime.runDynamicSlotsAfterReservedSlots(); f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var objects = f.runtime.captureClosedCut(); var actor = f.frame.captureActor(); var shared = f.frame.captureShared();
            var cpu = f.runtime.cpuSat(); var picture = f.runtime.displayedFrame();
            assertEquals(-4096, actor.state().motion().yVelocity()); assertEquals(4, f.slot(40).routine());
            f.service(0); f.runtime.spawnBeforeReservedSlots();
            assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.body(3).result().boundary());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            var nextCpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var next = f.runtime.captureClosedCut(); var nextActor = f.frame.captureActor(); var nextShared = f.frame.captureShared();
            assertNotEquals(actor.state().animation(), nextActor.state().animation(), "the next real Body animates the launched actor");
            f.service(0x80); var shown = f.runtime.displayedFrame(); int publications = f.artPublications.size();
            f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(objects);
            f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(objects);
            assertSame(cpu, f.runtime.cpuSat()); assertSame(picture, f.runtime.displayedFrame());
            assertEquals(actor, f.frame.captureActor()); assertEquals(shared.environment(), f.frame.environment());
            assertEquals(objects.lastSoundRequests(), f.runtime.lastSoundRequests());
            assertEquals(publications, f.artPublications.size(), "restore performs no service or upload");
            f.service(0); f.runtime.spawnBeforeReservedSlots();
            assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.body(3).result().boundary());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            var replay = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail(); var current = f.runtime.captureClosedCut();
            assertEquals(nextActor, f.frame.captureActor());
            assertEquals(nextShared.environment(), f.frame.environment()); assertEquals(nextShared.vIntClock(), f.frame.vIntClock());
            assertEquals(nextShared.artState().lastFrame(), f.frame.artState().lastFrame());
            assertEquals(nextShared.artState().dirty(), f.frame.artState().dirty());
            assertArrayEquals(nextShared.artState().cpuBuffer().copy(), f.frame.artState().cpuBuffer().copy());
            assertArrayEquals(nextShared.artState().residentBank().copy(), f.frame.artState().residentBank().copy());
            assertEquals(nextShared.paused(), f.frame.paused());
            assertArrayEquals(nextCpu.bytes(), replay.bytes()); assertEquals(nextCpu.provenance(), replay.provenance());
            assertEquals(next.displayedFrame(), current.displayedFrame()); assertEquals(next.lastSoundRequests(), current.lastSoundRequests());
            assertEquals(next.placement().pool().slots(), current.placement().pool().slots());
            assertArrayEquals(next.placement().savedFlags().copy(), current.placement().savedFlags().copy());
            assertEquals(next.placement().status(), current.placement().status());
            assertEquals(next.ringGlobals(), current.ringGlobals()); assertEquals(next.spillGlobals(), current.spillGlobals());
            assertEquals(next.contactGlobals(), current.contactGlobals()); assertEquals(next.scoreGlobals(), current.scoreGlobals());
            assertEquals(next.devGlobals(), current.devGlobals()); assertEquals(next.levelGlobals(), current.levelGlobals());
            assertEquals(next.powerupArtState(), current.powerupArtState());
            f.service(0x80); assertEquals(shown, f.runtime.displayedFrame());
        }
    }

    // Dynamic ID0 is still real owned RAM. DrawObject clears bit7 before following; the SAT fetches final XY.
    @Test void springParentReadsAnEmptyLiveDynamicImageAndDeletedQueuedSlotsStayAbsentAtDrain() throws Exception {
        for (boolean delete : List.of(false, true)) {
            try (var f = new Fixture()) {
                springDeviceCut(f); var pool = f.runtime.placement().pool();
                pool.write(pool.state(41).moveWord(X, 0x140).moveWord(Y, 0x800)); // ID remains 0, never dispatched.
                var initial = spring(f, pool.state(40), 6, 0x84, 8, 0).moveByte(MAP_FRAME, 2)
                        .moveWord(X_FRACTION, 0x4321).moveWord(Y_FRACTION, 0x8765).moveWord(SPRING_PARENT, 0xDA40)
                        .moveByte(SPRING_PARENT_X_OFFSET, -128).moveByte(SPRING_PARENT_Y_OFFSET, -128)
                        .moveWord(SPRING_ANCHOR_X, delete ? 0x380 : 0x100);
                pool.write(initial); f.runtime.runDynamicSlotsAfterReservedSlots();
                if (delete) assertArrayEquals(new byte[64], f.slot(40).copyBytes());
                else assertEquals(initial.moveByte(ROUTINE, 2).moveByte(FLAGS, initial.flags() & ~8)
                        .moveByte(PREVIOUS_ANIMATION, 1).moveByte(MAP_FRAME, 0)
                        .moveByte(SPRITE_FLAGS, 4).moveWord(X, 0xC0).moveWord(Y, 0x780), f.slot(40));
                var sat = f.runtime.finishAfterCamera(POST);
                assertTrue(sat.provenance().stream().noneMatch(v -> v.slotRef().wholeIndex() == 41));
                if (delete) assertTrue(sat.provenance().stream().noneMatch(v -> v.slotRef().wholeIndex() == 40));
                else {
                    int at = satEntry(sat, 40);
                    assertEquals(128 + 0xC0 - 16, word(sat.bytes(), at + 6), "final parent-follow X, not DrawObject X$100");
                    assertEquals(128 + 0x780 - PRE.yWord() - 8, word(sat.bytes(), at));
                    assertEquals(0x80, f.slot(40).spriteFlags() & 0x80);
                    assertEquals(0x4321, f.slot(40).word(X_FRACTION)); assertEquals(0x8765, f.slot(40).word(Y_FRACTION));
                }
                f.runtime.finishOrdinaryTail();
            }
        }
    }

    @Test void springLaunchPrecedesAnUnknownLaterIdAndItsCompletedPrefixSurvivesTerminalClosure() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpringPlayer(f, 0x01008000, 0x07671234, 0, 0x100, 2, 0, 0);
            pool.write(spring(f, pool.state(40), 2, 0x84, 0, 0));
            pool.write(pool.state(41).moveByte(ID, 0x7E));
            var failure = assertThrows(UnsupportedOperationException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertTrue(failure.getMessage().contains("41")); assertTrue(failure.getMessage().contains("7e"));
            assertEquals(4, f.slot(40).routine()); assertEquals(0, f.slot(40).spriteFlags() & 0x80);
            assertEquals(0x076D1234, f.frame.state().motion().centreY16_16()); assertEquals(-4096, f.frame.state().motion().yVelocity());
            assertEquals(1, f.runtime.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.SpringFm.class::isInstance).count());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
        }
    }

    @Test void reachedDiagonalSpringInitializesSuccessfullyAtItsActualNativeSelectorAndReturns() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); var before = f.frame.state();
            var env = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            pool.write(pool.state(40).moveByte(ID, 0x0A).moveByte(SUBTYPE, 8).moveWord(X, 0x100).moveWord(Y, 0x780));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(20, f.slot(40).routine()); assertEquals(0x209BF0, f.slot(40).longBits(MAPPINGS));
            assertEquals(0x490, f.slot(40).word(TILE)); assertEquals(-4096, f.slot(40).signedWord(SPRING_SPEED));
            assertEquals(before, f.frame.state()); assertEquals(env, f.frame.environment());
            assertFalse(f.runtime.closed()); assertFalse(f.frame.closed()); assertEquals(sounds, f.runtime.lastSoundRequests());
            monitorFinish(f);
        }
    }

    /** Existing public owner edits after the completed player slot: constructed device ingress, never a route. */
    private static CdPlayerFrameRuntime.BodyPublication springDeviceCut(Fixture f) {
        f.firstCycle(); f.service(0); f.runtime.spawnBeforeReservedSlots(); var publication = f.body(2);
        f.runtime.placement().pool().clearDynamicSlots();
        return publication;
    }

    private static CdObjectState spring(Fixture f, CdObjectState s, int routine, int spriteFlags, int flags, int subtype) throws java.io.IOException {
        var assets = CdSpring.Assets.fromObjectAssets(f.assets.openingObjects());
        boolean side = (subtype & 4) != 0;
        return s.moveByte(ID, CdSpring.OBJECT_ID).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, spriteFlags)
                .moveByte(SUBTYPE, subtype).moveByte(FLAGS, flags).moveLong(MAPPINGS, assets.mappingsAddress() + (side ? 6 : 0))
                .moveWord(TILE, (subtype & 2) == 0 ? 0x520 : 0x2520).moveWord(SPRING_SPEED, assets.speedWord(subtype))
                .moveByte(WIDTH, side ? 8 : 16).moveByte(Y_RADIUS, side ? 16 : 8).moveByte(PRIORITY, 4)
                .moveWord(X, 0x100).moveWord(Y, 0x780).moveWord(SPRING_ANCHOR_X, 0x100);
    }

    private static void seedSpringPlayer(Fixture f, int x, int y, int xv, int yv, int flags, int stand, int charge) {
        seedSpringPlayer(f, x, y, xv, yv, flags, stand, charge, 1);
    }

    private static void seedSpringPlayer(Fixture f, int x, int y, int xv, int yv, int flags, int stand, int charge,
                                         int playerControl) {
        var actor = f.frame.captureActor(); var s = actor.state();
        var motion = new CdPlayerMotionState(x, y, xv, yv, 0x345, 2, 0x40, flags, s.motion().spriteFlags(),
                9, 19, 0x1A, 0x12, 1, charge, 0x4567, 1, playerControl, 0x31, 0x42);
        f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), new CdPlayerBody.State(motion, s.animation(),
                s.header(), new CdPlayerBody.Timers(0x123, 0x2233, 0x3344, 0x55), s.history(), s.rotationDistance(),
                new CdPlayerBody.ObjectLinks(0, stand)), actor.display()));
    }

    private static int satEntry(CpuSat sat, int whole) {
        for (int i = 0; i < sat.provenance().size(); i++) if (sat.provenance().get(i).slotRef().wholeIndex() == whole) return i * 8;
        fail("Missing SAT slot " + whole); return -1;
    }

    private static CdObjectState floatBlock(CdObjectState s, int routine, int spriteFlags, int flags) {
        return s.moveByte(ID, 0x12).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, spriteFlags)
                .moveLong(MAPPINGS, 0x20A3FA).moveWord(TILE, 0x4334).moveByte(PRIORITY, 1)
                .moveByte(WIDTH, 12).moveByte(Y_RADIUS, 12).moveByte(MAP_FRAME, 5)
                .moveWord(X, 64).moveWord(Y, 0x740).moveByte(FLAGS, flags);
    }

    /** Explicit constructed state at an existing closed Frame cut; never route evidence. */
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
                bank, powerup, cameraArtDevIngress(assets), sat, banks, PRE, value -> { });
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
                    level, savedFlags, powerup, cameraArtDevIngress(assets), new byte[640], BANKS, PRE,
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

    // Spin tunnel Runtime composition: constructed actual-owner post-Body cuts, not ordinary-route evidence.
    // Native authority: ObjSpinTunnel (Spin Tunnel/Main (Tag).asm:7-188) and CheckObjDespawn
    // (Object Spawner.asm:385-446), scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
    @Test void spinTunnelDirtyInitFallsThroughAndPreservesEveryUnownedActorAndObjectByte() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            var motion = spinTunnelMotion(f, 0x01001234, 0x0780BEEF, -1, 0x234, -0x345, 0xE9, 0x1A);
            seedSpinTunnelMotion(f, motion);
            var tag = pool.state(40);
            for (var field : CdObjectState.ByteField.values()) tag = tag.moveByte(field, 0xA7);
            for (var field : CdObjectState.WordField.values()) tag = tag.moveWord(field, 0xB6C5);
            for (var field : CdObjectState.LongField.values()) tag = tag.moveLong(field, 0xD4E3F201);
            tag = tag.moveByte(ID, 0x34).moveByte(ROUTINE, 0).moveByte(SPRITE_FLAGS, 0x81)
                    .moveByte(SUBTYPE, 0).moveByte(SAVED_FLAGS_ID, 0).moveWord(X, 0x100).moveWord(Y, 0x780);
            pool.write(tag);
            var before = f.frame.state(); var shared = f.frame.captureShared(); var display = f.frame.display();
            var sounds = f.runtime.lastSoundRequests(); var flags = f.runtime.placement().capture().savedFlags();
            var untouched = pool.capture().slots();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            var expected = new CdPlayerMotionState(0x01001234, 0x0785BEEF, -0xA00, 0x234, 0xA00,
                    2, 0x60, 0xED, motion.spriteFlags(), 7, 14, 2, 0x12, 1, 0, 0x4567, 0, 0, 0x31, 0x42);
            assertEquals(new CdPlayerBody.State(expected, before.animation(), before.header(), before.timers(),
                    before.history(), before.rotationDistance(), before.objectLinks()), f.frame.state());
            var expectedTag = tag.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0x85).moveWord(TILE, 0x544)
                    .moveLong(MAPPINGS, 0x23EE9C).moveByte(MAP_FRAME, 1);
            assertArrayEquals(expectedTag.copyBytes(), f.slot(40).copyBytes());
            for (int i = 0; i < untouched.size(); i++) if (i != 8) assertEquals(untouched.get(i), pool.capture().slots().get(i));
            assertEquals(display, f.frame.display()); assertEquals(shared.environment(), f.frame.environment());
            assertSpinTunnelArt(shared.artState(), f.frame.artState());
            assertEquals(shared.vIntClock(), f.frame.vIntClock()); assertEquals(shared.paused(), f.frame.paused());
            assertEquals(sounds, f.runtime.lastSoundRequests());
            assertArrayEquals(flags.copy(), f.runtime.placement().capture().savedFlags().copy());
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    @Test void spinTunnelNativeSubtypeBranchesUseActualRawHeldWithRightWinningOverLeft() throws Exception {
        // subtype, old X/Y, raw held, final X/Y/GVel, final flags. Angle $60 inverts only subtype0 GVel.
        int[][] rows = {
                {0, -1, 0x234, 0, -0xA00, 0x234, 0xA00, 0xED},
                {1, 0x234, -1, 0, 0x234, -0xD00, -0xD00, 0xEF},
                {2, 0x234, -1, 0x0C, 0xD00, -1, 0xD00, 0xED},
                {2, 0x234, 0, 0x0C, 0x234, 0, -0x345, 0xED},
                {3, 0x234, 0, 4, -0xD00, 0, -0xD00, 0xEF},
                {4, 0, -0x123, 1, 0, -0xA00, -0xA00, 0xEF},
                {0xFF, 0x234, 0, 8, 0xD00, 0, 0xD00, 0xEF}
        };
        for (int[] row : rows) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            var motion = spinTunnelMotion(f, 0x01001234, 0x0780BEEF, row[1], row[2], -0x345, 0xE9, 0x1A);
            seedSpinTunnelMotion(f, motion);
            // Existing shared owner stores actual raw Right+Left separately from retained logical Left.
            seedSpinTunnelEnvironment(f, 0, 0, row[3], 4, 1, 0);
            pool.write(spinTunnelTag(f, pool.state(40), 0, 0x81, row[0], 0x100, 0x780, 0));
            var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(new CdPlayerMotionState(0x01001234, 0x0785BEEF, row[4], row[5], row[6],
                    2, 0x60, row[7], motion.spriteFlags(), 7, 14, 2, 0x12, 1, 0, 0x4567, 0, 0, 0x31, 0x42),
                    f.frame.state().motion(), "native subtype " + row[0]);
            assertEquals(row[3], f.frame.environment().controls().rawHeld());
            assertEquals(4, f.frame.environment().controls().logicalHeld());
            assertEquals((row[0] + 1) & 255, f.slot(40).unsignedByte(MAP_FRAME));
            assertEquals(sounds, f.runtime.lastSoundRequests());
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    @Test void spinTunnelIndependentHighDebugBytesGateContactAndRealDrawObject() throws Exception {
        for (int mode : List.of(1, 0x100)) for (int cheat : List.of(1, 0x100)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            var motion = spinTunnelMotion(f, 0x01001234, 0x0780BEEF, 0x234, -1, -0x345, 0xE9, 0x1A);
            seedSpinTunnelMotion(f, motion); seedSpinTunnelEnvironment(f, mode, cheat, 0, 0, 1, 0);
            pool.write(spinTunnelTag(f, pool.state(40), 0, 0x81, 1, 0x100, 0x780, 0));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            if (mode == 1) {
                assertEquals(-0xD00, f.frame.state().motion().yVelocity());
                assertEquals(0x0785BEEF, f.frame.state().motion().centreY16_16());
                assertEquals(0xEF, f.frame.state().motion().flags());
            } else assertEquals(motion, f.frame.state().motion(), "HIGH debugMode rejects range, after the real Body cut");
            assertEquals(cheat == 1 ? 0x85 : 5, f.slot(40).spriteFlags(), "only HIGH debugCheat calls real BCLR7");
            var sat = f.runtime.finishAfterCamera(POST);
            assertEquals(cheat == 1 ? 0 : 4, sat.provenance().stream().filter(p -> p.slotRef().wholeIndex() == 40).count());
            assertEquals(0x85, f.slot(40).spriteFlags(), "DrawDone restores bit7 only on the queued path");
            assertEquals(0x23EE9C, f.slot(40).longBits(MAPPINGS));
            f.runtime.finishOrdinaryTail();
        }
    }

    @Test void spinTunnelAlreadyRollingKeepsYRadiiAnimationAndBothFractions() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            var motion = spinTunnelMotion(f, 0x01001234, 0x0780BEEF, -0x8000, -7, -0x345, 0xED, 0x1A);
            seedSpinTunnelMotion(f, motion);
            var tag = spinTunnelTag(f, pool.state(40), 2, 0x84, 0, 0x100, 0x780, 0); pool.write(tag);
            var before = f.frame.state(); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(new CdPlayerMotionState(0x01001234, 0x0780BEEF, -0x8000, -7, -0x8000,
                    2, 0x60, 0xED, motion.spriteFlags(), 9, 19, 0x1A, 0x12, 1, 0, 0x4567, 0, 0, 0x31, 0x42),
                    f.frame.state().motion());
            assertEquals(before.animation(), f.frame.state().animation()); assertEquals(tag, f.slot(40));
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    @Test void spinTunnelLaterPhysicalRampReadsTheAlreadyPublishedNegativeYVelocity() throws Exception {
        for (boolean tagFirst : List.of(true, false)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpinTunnelMotion(f, spinTunnelMotion(f, 0x01001234, 0x0780BEEF, 0, 0x100, -0x345, 0, 0x1A));
            seedSpinTunnelEnvironment(f, 0, 0, 1, 0, 1, 0);
            int tagSlot = tagFirst ? 40 : 41, rampSlot = tagFirst ? 41 : 40;
            pool.write(spinTunnelTag(f, pool.state(tagSlot), 0, 0x81, 4, 0x100, 0x780, 0));
            // Actual ramp Init/Main, with its represented nonzero timer admitting the airborne contact.
            pool.write(pool.state(rampSlot).moveByte(ID, 0x2A).moveWord(X, 0x100).moveWord(Y, 0x780).moveByte(RAMP_TIMER, 1));
            var priorSounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(tagFirst ? -0xC00 : -0xA00, f.frame.state().motion().yVelocity());
            assertEquals(-0xA00, f.frame.state().motion().groundVelocity()); assertEquals(6, f.frame.state().motion().flags());
            assertEquals(60, f.slot(rampSlot).unsignedByte(RAMP_TIMER)); assertEquals(2, f.slot(rampSlot).routine());
            assertEquals(priorSounds, f.runtime.lastSoundRequests(), "Tag/ramp preserve the earlier Body sound ledger");
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    @Test void spinTunnelDebugDrawReloadChangesFarDespawnAndClearsOnlyLoadedBit() throws Exception {
        for (int cheat : List.of(1, 0x100)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); var placement = f.runtime.placement();
            seedSpinTunnelEnvironment(f, 0, cheat, 0, 0, 1, 0);
            seedSpinTunnelLoaded(placement, 7, 1);
            var tag = spinTunnelTag(f, pool.state(40), 0, 0x81, 0, 0x380, 0x780, 7); pool.write(tag);
            var before = f.frame.state(); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(before, f.frame.state(), "far from the actual player: no contact");
            if (cheat == 1) {
                assertEquals(0x85, f.slot(40).spriteFlags()); assertEquals(0xD5, placement.savedFlag(7, 1));
                assertEquals(2, f.slot(40).routine(), "live bit7 bypasses distance when debug is off");
            } else {
                assertArrayEquals(new byte[64], f.slot(40).copyBytes());
                assertEquals(0x55, placement.savedFlag(7, 1), "actual loaded BCLR retains all seven lower bits");
            }
            var sat = f.runtime.finishAfterCamera(POST);
            assertTrue(sat.provenance().stream().noneMatch(p -> p.slotRef().wholeIndex() == 40));
            f.runtime.finishOrdinaryTail();
        }
    }

    @Test void spinTunnelActualSavedBankEndsAt765AndEscaping766KeepsCompletedInitPlayerAndDrawPrefix() throws Exception {
        for (int period : List.of(1, 2)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); var placement = f.runtime.placement();
            // Public same-owner post-Body ingress exercises the reached raw tail; no service at timezone2 is admitted.
            seedSpinTunnelEnvironment(f, 0, 0x100, 0, 0, period, 0);
            seedSpinTunnelMotion(f, spinTunnelMotion(f, 0x03801234, 0x0780BEEF, 0, 7, -0x345, 0, 0x1A));
            seedSpinTunnelLoaded(placement, 254, 1);
            var tag = spinTunnelTag(f, pool.state(40), 0, 0x81, 0, 0x380, 0x780, 254); pool.write(tag);
            var bank = placement.capture().savedFlags().copy(); var sounds = f.runtime.lastSoundRequests();
            assertEquals(766, bank.length);
            if (period == 1) {
                f.runtime.runDynamicSlotsAfterReservedSlots();
                assertArrayEquals(new byte[64], f.slot(40).copyBytes()); assertEquals(0x55, placement.savedFlag(254, 1));
                byte[] expected = bank.clone(); expected[765] = 0x55; assertArrayEquals(expected, placement.capture().savedFlags().copy());
                f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            } else {
                assertThrows(IndexOutOfBoundsException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
                assertArrayEquals(bank, placement.capture().savedFlags().copy(), "index766 preflight precedes any bank clear/delete");
                assertEquals(tag.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 5).moveWord(TILE, 0x544)
                        .moveLong(MAPPINGS, 0x23EE9C).moveByte(MAP_FRAME, 1), f.slot(40), "Init and real culled DrawObject survived");
                assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
                assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            }
            assertEquals(0x0785BEEF, f.frame.state().motion().centreY16_16());
            assertEquals(0xA00, f.frame.state().motion().xVelocity()); assertEquals(-0xA00, f.frame.state().motion().groundVelocity());
            assertEquals(sounds, f.runtime.lastSoundRequests(), "tag has no sound even on a tail failure");
        }
    }

    @Test void spinTunnelRawWarpDirectionClearsTheActualSelectedPeriodAndSavedZeroNeverAccessesBank() throws Exception {
        int[][] rows = {{0x81, 1, 0}, {0x80, 1, 0}, {0x82, 0xFF, 2}, {0xFF, 0x80, 2}};
        for (int[] row : rows) for (int saved : List.of(0, 7)) try (var f = new Fixture()) {
            springDeviceCut(f); var placement = f.runtime.placement(); var pool = placement.pool();
            seedSpinTunnelEnvironment(f, 0, 0, 0, 0, row[0], row[1]);
            seedSpinTunnelMotion(f, spinTunnelMotion(f, 0x01001234, 0x0780BEEF, 0, 7, -0x345, 0, 0x1A));
            for (int p = 0; p < 3; p++) seedSpinTunnelLoaded(placement, 7, p);
            var before = placement.capture().savedFlags().copy();
            pool.write(spinTunnelTag(f, pool.state(40), 0, 0x81, 0, 0x100, 0x780, saved));
            var priorSounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertArrayEquals(new byte[64], f.slot(40).copyBytes());
            byte[] expected = before.clone(); if (saved != 0) expected[2 + 3 * saved + row[2]] &= 0x7F;
            assertArrayEquals(expected, placement.capture().savedFlags().copy());
            assertEquals(0x0785BEEF, f.frame.state().motion().centreY16_16(), "player prefix precedes forced warp deletion");
            assertEquals(priorSounds, f.runtime.lastSoundRequests(), "Tag/ramp preserve the earlier Body sound ledger");
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    @Test void spinTunnelQueuedThenDeletedPhysicalSlotIsSkippedAndEmptyFrameZeroStillGetsDrawDone() throws Exception {
        for (boolean delete : List.of(true, false)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpinTunnelEnvironment(f, 0, 0x100, 0, 0, 1, 0);
            // Main2 may inherit screen-relative flags0. Real DrawObject queues this far slot before tail deletion.
            var tag = spinTunnelTag(f, pool.state(40), delete ? 2 : 0, delete ? 0x80 : 0x81,
                    delete ? 0 : 0xFF, delete ? 0x380 : 0x100, 0x780, 0); pool.write(tag);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            if (delete) assertArrayEquals(new byte[64], f.slot(40).copyBytes());
            else {
                assertEquals(0, f.slot(40).unsignedByte(MAP_FRAME)); assertEquals(5, f.slot(40).spriteFlags());
            }
            var sat = f.runtime.finishAfterCamera(POST);
            assertTrue(sat.provenance().stream().noneMatch(p -> p.slotRef().wholeIndex() == 40));
            if (delete) assertArrayEquals(new byte[64], f.slot(40).copyBytes(), "queue drain does not resurrect ID0");
            else assertEquals(0x85, f.slot(40).spriteFlags(), "real empty mapping frame0 reaches DrawDone");
            f.runtime.finishOrdinaryTail();
        }
    }

    @Test void spinTunnelFinalSatUsesCurrentXYButTheEarlierBodyEligibilityBucketAndMapFrame() throws Exception {
        try (var f = new Fixture()) {
            var publication = springDeviceCut(f); var pool = f.runtime.placement().pool();
            var cut = publication.result().effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                    .map(CdPlayerBody.DisplayEligibility.class::cast).filter(CdPlayerBody.DisplayEligibility::eligible).findFirst().orElseThrow();
            var motion = spinTunnelMotion(f, 0x01001234, 0x0780BEEF, 0, 7, -0x345, 0, 0x1A);
            seedSpinTunnelMotion(f, motion); var before = f.frame.state(); var display = f.frame.display();
            pool.write(spinTunnelTag(f, pool.state(40), 0, 0x81, 0, 0x100, 0x780, 0));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(before.animation(), f.frame.state().animation()); assertEquals(before.history(), f.frame.state().history());
            assertEquals(before.header(), f.frame.state().header()); assertEquals(display, f.frame.display());
            assertEquals(2, f.frame.state().motion().animation()); assertEquals(7, f.frame.state().motion().xRadius());
            assertEquals(14, f.frame.state().motion().yRadius());
            var sat = f.runtime.finishAfterCamera(POST); int at = satEntry(sat, 0);
            var map = f.assets.sonic().mappingFrames().get(before.animation().mapFrame()); assertTrue(map.pieceCount() > 0);
            assertEquals(cut.priority() & 7, sat.provenance().get(at / 8).queueBucket());
            assertEquals((128 + 0x100 + map.bytes().signedByte(5)) & 0x1FF, word(sat.bytes(), at + 6));
            assertEquals((128 + 0x785 - PRE.yWord() + map.bytes().signedByte(1)) & 0x1FF, word(sat.bytes(), at));
            assertNotEquals((128 + cut.centreYWord() - PRE.yWord() + map.bytes().signedByte(1)) & 0x1FF, word(sat.bytes(), at));
            assertEquals(before.animation().mapFrame(), f.frame.state().animation().mapFrame());
            f.runtime.finishOrdinaryTail();
        }
    }

    @Test void spinTunnelCompositeRestoreReplaysTheNextActualBodyArtSatAndUnchangedSoundLedger() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpinTunnelMotion(f, spinTunnelMotion(f, 0x01001234, 0x0767BEEF, 0, -0x100, 0, 2, 0x1A));
            pool.write(spinTunnelTag(f, pool.state(40), 0, 0x81, 1, 0x100, 0x767, 0));
            var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertEquals(-0xD00, f.frame.state().motion().yVelocity()); assertEquals(6, f.frame.state().motion().flags());
            assertEquals(0, f.frame.state().motion().playerControl(), "next real Body remains its actual owner");
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var objects = f.runtime.captureClosedCut(); var actor = f.frame.captureActor(); var shared = f.frame.captureShared();
            var cpu = f.runtime.cpuSat(); var picture = f.runtime.displayedFrame();
            f.service(0); f.runtime.spawnBeforeReservedSlots();
            assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.body(3).result().boundary());
            f.runtime.runDynamicSlotsAfterReservedSlots(); var nextCpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var next = f.runtime.captureClosedCut(); var nextActor = f.frame.captureActor(); var nextShared = f.frame.captureShared();
            assertNotEquals(actor.state().motion().centreY16_16(), nextActor.state().motion().centreY16_16());
            assertNotEquals(actor.state().animation(), nextActor.state().animation(), "the next Body owns roll animation");
            f.service(0); var shown = f.runtime.displayedFrame(); int publications = f.artPublications.size();
            // An ordinary service opens the next loop. Complete it before invoking closed-cut restore.
            f.runtime.spawnBeforeReservedSlots();
            assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.body(4).result().boundary());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, f.runtime.phase());
            f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(objects);
            f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(objects);
            assertEquals(actor, f.frame.captureActor()); assertEquals(shared.environment(), f.frame.environment());
            assertSpinTunnelArt(shared.artState(), f.frame.artState()); assertEquals(shared.vIntClock(), f.frame.vIntClock());
            assertSame(cpu, f.runtime.cpuSat()); assertSame(picture, f.runtime.displayedFrame());
            assertEquals(publications, f.artPublications.size(), "restore has no service/upload callback");
            f.service(0); f.runtime.spawnBeforeReservedSlots();
            assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.body(3).result().boundary());
            f.runtime.runDynamicSlotsAfterReservedSlots(); var replay = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var current = f.runtime.captureClosedCut();
            assertEquals(nextActor, f.frame.captureActor()); assertEquals(nextShared.environment(), f.frame.environment());
            assertSpinTunnelArt(nextShared.artState(), f.frame.artState()); assertEquals(nextShared.vIntClock(), f.frame.vIntClock());
            assertEquals(nextShared.paused(), f.frame.paused());
            assertArrayEquals(nextCpu.bytes(), replay.bytes()); assertEquals(nextCpu.provenance(), replay.provenance());
            assertEquals(next.placement().pool().slots(), current.placement().pool().slots());
            assertArrayEquals(next.placement().savedFlags().copy(), current.placement().savedFlags().copy());
            assertEquals(next.placement().status(), current.placement().status());
            assertEquals(next.displayedFrame(), current.displayedFrame()); assertEquals(next.lastSoundRequests(), current.lastSoundRequests());
            assertEquals(next.ringGlobals(), current.ringGlobals()); assertEquals(next.spillGlobals(), current.spillGlobals());
            assertEquals(next.contactGlobals(), current.contactGlobals()); assertEquals(next.scoreGlobals(), current.scoreGlobals());
            assertEquals(next.devGlobals(), current.devGlobals()); assertEquals(next.levelGlobals(), current.levelGlobals());
            assertEquals(next.powerupArtState(), current.powerupArtState());
            f.service(0); assertEquals(shown, f.runtime.displayedFrame(), "ordinary selector8 publishes; Start/pause is not used");
        }
    }

    @Test void spinTunnelUnknownLaterIdRetainsTheEarlierSpringSoundAndCompletedTagPrefix() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpringPlayer(f, 0x01008000, 0x07671234, 0, 0x100, 2, 0, 0, 0);
            pool.write(spring(f, pool.state(39), 2, 0x84, 0, 0));
            pool.write(spinTunnelTag(f, pool.state(40), 0, 0x81, 0, 0x100, 0x780, 0));
            pool.write(pool.state(41).moveByte(ID, 0x7E));
            var failure = assertThrows(UnsupportedOperationException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertTrue(failure.getMessage().contains("41")); assertTrue(failure.getMessage().contains("7e"));
            assertEquals(2, f.slot(40).routine()); assertEquals(0x23EE9C, f.slot(40).longBits(MAPPINGS));
            assertEquals(0x07721234, f.frame.state().motion().centreY16_16());
            assertEquals(0xA00, f.frame.state().motion().xVelocity()); assertEquals(-0x1000, f.frame.state().motion().yVelocity());
            assertEquals(0xA00, f.frame.state().motion().groundVelocity()); assertEquals(6, f.frame.state().motion().flags());
            assertEquals(List.of(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ChargeSound(
                    com.openggf.game.soniccd.player.CdPlayerCharge.Sound.FM_CHARGESTOP)),
                    new CdOpeningObjectRuntime.SpringFm(CdSpring.FmSound.FM_SPRING)), f.runtime.lastSoundRequests());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
        }
    }

    @Test void spinTunnelUnownedRoutineStopsBeforeInitOrPlayerWritesAtItsRealVisit() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            var tag = spinTunnelTag(f, pool.state(40), 4, 0x81, 0, 0x100, 0x780, 0); pool.write(tag);
            var actor = f.frame.state(); var slots = pool.capture().slots(); var bank = f.runtime.placement().capture().savedFlags();
            var failure = assertThrows(CdSpinTunnelTag.UnsupportedSpinTunnelTag.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(CdSpinTunnelTag.UnsupportedBoundary.ROUTINE_DISPATCH, failure.boundary());
            assertEquals(40, failure.wholeIndex()); assertEquals(4, failure.value());
            assertEquals(actor, f.frame.state()); assertEquals(slots, pool.capture().slots());
            assertArrayEquals(bank.copy(), f.runtime.placement().capture().savedFlags().copy());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    private static CdObjectState spinTunnelTag(Fixture f, CdObjectState image, int routine, int spriteFlags,
                                               int subtype, int x, int y, int saved) throws java.io.IOException {
        return image.moveByte(ID, 0x34).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, spriteFlags)
                .moveByte(SUBTYPE, subtype).moveByte(SAVED_FLAGS_ID, saved).moveWord(X, x).moveWord(Y, y)
                .moveLong(MAPPINGS, f.assets.openingObjects().view(
                        com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.POWERUP_MAPPINGS).range().nativeAddress())
                .moveWord(TILE, 0x544).moveByte(MAP_FRAME, (subtype + 1) & 255)
                .moveByte(WIDTH, 16).moveByte(Y_RADIUS, 16).moveByte(PRIORITY, 3);
    }

    private static CdPlayerMotionState spinTunnelMotion(Fixture f, int x, int y, int xv, int yv, int gv,
                                                        int flags, int animation) {
        return new CdPlayerMotionState(x, y, xv, yv, gv, 2, 0x60, flags, f.frame.state().motion().spriteFlags(),
                9, 19, animation, 0x12, 1, 0, 0x4567, 0, 0, 0x31, 0x42);
    }

    private static void seedSpinTunnelMotion(Fixture f, CdPlayerMotionState motion) {
        var actor = f.frame.captureActor(); var s = actor.state();
        f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), new CdPlayerBody.State(motion,
                s.animation(), s.header(), s.timers(), s.history(), s.rotationDistance(), s.objectLinks()), actor.display()));
    }

    /** Explicit public same-owner closed Frame ingress, never a claim of service/route reachability. */
    private static void seedSpinTunnelEnvironment(Fixture f, int mode, int cheat, int raw, int logical, int zone, int direction) {
        var shared = f.frame.captureShared(); var e = shared.environment(); var m = e.movement(); var g = m.gates(); var b = e.globals();
        var gates = new CdPlayerMovement.Gates(g.zoneAct(), zone, g.miniSonic(), g.waterSlide(),
                g.windTunnel(), g.speedShoes(), direction);
        var movement = new CdPlayerMovement.Environment(m.physics(), m.bounds(), gates, m.globals());
        var controls = new CdPlayerBody.Controls(raw, e.controls().rawTap(), logical, e.controls().logicalTap(), e.controls().controlLocked());
        var globals = new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), mode, cheat,
                b.timeWarpTimer(), b.invincible(), b.bossMusic());
        f.frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(),
                new CdPlayerBody.Environment(movement, controls, globals, e.dynamicSlots()), shared.artState(), shared.paused(), shared.vIntClock()));
    }

    private static void seedSpinTunnelLoaded(CdObjectPlacementRuntime placement, int saved, int period) {
        for (var bit : List.of(CdObjectPlacementRuntime.SavedBit.MEMBER_1, CdObjectPlacementRuntime.SavedBit.MEMBER_3,
                CdObjectPlacementRuntime.SavedBit.MEMBER_5, CdObjectPlacementRuntime.SavedBit.MEMBER_7, CdObjectPlacementRuntime.SavedBit.LOADED)) {
            placement.bsetSavedFlag(saved, period, bit);
        }
    }

    private static void assertSpinTunnelArt(CdPlayerArtRuntime.State expected, CdPlayerArtRuntime.State actual) {
        assertEquals(expected.lastFrame(), actual.lastFrame()); assertEquals(expected.dirty(), actual.dirty());
        assertArrayEquals(expected.cpuBuffer().copy(), actual.cpuBuffer().copy());
        assertArrayEquals(expected.residentBank().copy(), actual.residentBank().copy());
    }


    // DoorV Runtime composition. Constructed post-Body ingress over actual USA assets, not World/route evidence.
    // ObjTunnelDoorV (Tunnel Door/Main.asm:7-83) and .Map (Tunnel Door/Data/Mappings.asm:1-27),
    // scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. All new Java is UNRUN by this external author.
    @Test void tunnelDoorTwelfthBankPreservesEveryLegacyPrefixAndRejectsEmptySatOverlapAndOverflow() throws Exception {
        assertThrows(NullPointerException.class, () -> new CdOpeningObjectRuntime.TunnelDoorVerticalFm(null));
        assertEquals(12362, BANKS.tunnelDoorVertical());
        var explicit = new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449, 464, 500);
        assertEquals(412, explicit.tunnelDoorVertical() + 12 - explicit.player());
        assertEquals(explicit, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138));
        assertEquals(explicit, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236));
        assertEquals(explicit, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374));
        assertEquals(explicit, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413));
        assertEquals(explicit, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449));
        assertEquals(explicit, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449, 464));
        assertEquals(600, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449, 464, 600)
                .tunnelDoorVertical(), "canonical twelfth base is explicit");
        var assets = load(); var saved = CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]);
        var window = new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]);
        for (int door : List.of(12361, 12275, -1, Integer.MAX_VALUE - 10)) {
            var banks = new CdOpeningObjectRuntime.LogicalBanks(10000, 11000, 12000, 12070, 12098,
                    12161, 12231, 12236, 12275, 12311, 12326, door);
            try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0)) {
                assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                        assets, frame, INITIAL, CONTACT, SCORE, LEVEL, new byte[640], banks, PRE, value -> { }));
                assertThrows(IllegalArgumentException.class, () -> restart(assets, frame, saved, window, new byte[640], banks));
                assertFalse(frame.closed()); assertEquals(0, frame.vIntClock().deltaSinceAdmittedCut());
            }
        }
    }

    @Test void tunnelDoorLiveInitFallsIntoClosedAndOpenedMainBeforeTheIndependentSolidDebugGate() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 2, 0, 0, 0, 0, 0x01001234, 0x07805678);
            seedSpinTunnelEnvironment(f, 0x100, 0, 0, 0, 1, 0);
            var closed = tunnelDoor(f, pool.state(40), 0, 0x80, 0x140, 0x780, 0, 0x12AB)
                    .moveWord(TILE, 0xCAFE).moveByte(PRIORITY, 7).moveByte(WIDTH, 25).moveByte(Y_RADIUS, 3)
                    .moveByte(ROUTINE_2, 0xA7).moveByte(ANIMATION, 0xA6)
                    .moveWord(X_FRACTION, 0x4321).moveWord(Y_FRACTION, 0x8765);
            var opened = tunnelDoor(f, pool.state(41), 0, 0x80, 0x100, 0x780, 0, 0xFEDC);
            pool.write(closed); pool.write(opened);
            var actor = f.frame.state(); var environment = f.frame.environment();
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(closed.moveByte(ROUTINE, 2).moveLong(MAPPINGS, 0x20CE9A).moveByte(PRIORITY, 1)
                    .moveByte(SPRITE_FLAGS, 4).moveByte(WIDTH, 4).moveByte(Y_RADIUS, 24)
                    .moveWord(TILE, 0x8328).moveByte(ROUTINE_2, 0), f.slot(40));
            assertEquals(opened.moveByte(ROUTINE, 4).moveByte(SPRITE_FLAGS, 4).moveWord(TUNNEL_DOOR_TIMER, 0)
                    .moveByte(MAP_FRAME, 1), f.slot(41));
            assertEquals(actor, f.frame.state()); assertEquals(environment, f.frame.environment());
            sounds.add(new CdOpeningObjectRuntime.TunnelDoorVerticalFm(CdTunnelDoorVertical.FmSound.FM_A4));
            assertEquals(sounds, f.runtime.lastSoundRequests(), "Door range runs before the independent Solid debug gate");
            var sat = f.runtime.finishAfterCamera(POST);
            assertEquals(2, sat.provenance().stream().filter(p -> p.slotRef().wholeIndex() == 40).count());
            assertEquals(2, sat.provenance().stream().filter(p -> p.slotRef().wholeIndex() == 41).count());
            f.runtime.finishOrdinaryTail();
        }
    }

    // Thirty-two real Runtime cycles are required: Reset increments byte $3A, retaining low byte $3B.
    @Test void tunnelDoorThirtyTwoResetDispatchesOmitSolidEvenOnCarryAndOnlyTheFollowingMainReopens() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            pool.write(tunnelDoor(f, pool.state(40), 4, 0x84, 0x100, 0x780, 1, 0x00A5));
            for (int reset = 1; reset <= 32; reset++) {
                if (reset != 1) {
                    f.service(0); f.runtime.spawnBeforeReservedSlots();
                    assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.body(reset + 1).result().boundary());
                }
                seedSolidPlayer(f, 0, 0, 0, 0x123, 0, 0x00FA8000, 0x07801234);
                var actor = f.frame.state(); var environment = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
                f.runtime.runDynamicSlotsAfterReservedSlots();
                assertEquals(actor, f.frame.state(), "Reset must not resolve the deliberately overlapping player");
                assertEquals(environment, f.frame.environment()); assertEquals(sounds, f.runtime.lastSoundRequests());
                assertEquals(((reset * 8 & 255) << 8) | 0xA5, f.slot(40).word(TUNNEL_DOOR_TIMER));
                assertEquals(reset == 32 ? 2 : 4, f.slot(40).routine());
                assertEquals(reset == 32 ? 0 : 1, f.slot(40).unsignedByte(MAP_FRAME));
                f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            }
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(34);
            seedSolidPlayer(f, 0, 0, 0, 0x123, 0, 0x00FA8000, 0x07801234);
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(0x00F28000, f.frame.state().motion().centreX16_16(), "width4 + native 9+1 resolves to X $F2");
            assertEquals(0x07801234, f.frame.state().motion().centreY16_16());
            assertEquals(0, f.frame.state().motion().xVelocity()); assertEquals(0x20, f.frame.state().motion().flags());
            assertEquals(4, f.slot(40).routine()); assertEquals(1, f.slot(40).unsignedByte(MAP_FRAME));
            assertEquals(0, f.slot(40).word(TUNNEL_DOOR_TIMER), "Main clears the entire word after the carry dispatch returned");
            sounds.add(new CdOpeningObjectRuntime.TunnelDoorVerticalFm(CdTunnelDoorVertical.FmSound.FM_A4));
            assertEquals(sounds, f.runtime.lastSoundRequests());
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    // Actual shared Solid is a composition oracle, not a fabricated result or an independent native-physics proof.
    @Test void tunnelDoorSolidPublishesFractionsGlobalsAndOtherSlotBeforeLaterPhysicalConsumers() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 8, 41, 0, 0x234, 0x100, 0x01008000, 0x07561234);
            seedTunnelDoorGlobals(f, 1, 0x3456, 0x55, 0x66);
            var door = tunnelDoor(f, pool.state(40), 2, 0x84, 0x100, 0x780, 0, 0x12AB)
                    .moveWord(X_VELOCITY, 0x123);
            var oldStand = tunnelDoor(f, pool.state(41), 4, 4, 0x100, 0x780, 1, 0x12AB).moveByte(FLAGS, 0xA8);
            pool.write(door); pool.write(oldStand);
            pool.write(spring(f, pool.state(42), 2, 4, 0, 0).moveByte(ROUTINE_2, 5)
                    .moveWord(X_FRACTION, 0x4567).moveWord(Y_FRACTION, 0x89AB));
            var untouched = pool.state(127).moveWord(TUNNEL_DOOR_TIMER, 0xDEAD); pool.write(untouched);
            var actor = f.frame.state(); var env = f.frame.environment(); var display = f.frame.display();
            var expected = CdSolidObject.fromAssets(f.assets).run(CdSolidObject.Entry.SOLID_OBJECT, door,
                    tunnelDoorPlayer(f), new CdSolidObject.Gates(env.globals().debugMode(), env.movement().gates().miniSonic()),
                    tunnelDoorGlobals(f), pool.capture());
            assertEquals(CdSolidObject.Path.STAND_ON, expected.path());
            assertEquals(List.of(41), expected.otherSlots().stream().map(v -> v.identity().wholeIndex()).toList());
            assertTrue(expected.globalWrites().contains(CdSolidObject.GlobalField.PRIMARY_ANGLE));
            assertTrue(expected.globalWrites().contains(CdSolidObject.GlobalField.SECONDARY_ANGLE));
            int angleWrites = f.angleWrites.size(); var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertTunnelDoorSolidApplied(f, actor, env, expected);
            assertEquals(display, f.frame.display()); assertEquals(angleWrites, f.angleWrites.size());
            assertEquals(0xA300, f.frame.state().motion().centreX16_16() & 0xFFFF);
            assertEquals(0x07551234, f.frame.state().motion().centreY16_16(), "word alignment retains the Y fraction");
            assertEquals(expected.object().moveByte(SPRITE_FLAGS, 4), f.slot(40));
            assertEquals(oldStand.moveByte(FLAGS, 0xA0).moveWord(TUNNEL_DOOR_TIMER, 0x1AAB), f.slot(41),
                    "the later Reset consumes its already released live image and calls no Solid");
            assertEquals(expected.player().actor().motion().centreX16_16() >>> 16, f.slot(42).centreX());
            assertEquals(0x755, f.slot(42).centreY());
            assertEquals(0x4567, f.slot(42).word(X_FRACTION)); assertEquals(0x89AB, f.slot(42).word(Y_FRACTION));
            assertEquals(untouched, f.slot(127)); assertEquals(sounds, f.runtime.lastSoundRequests());
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    // A dirty Main radius8 admits both Door range (dy25) and Solid's bottom edge; Init would set radius24.
    @Test void tunnelDoorFmPrecedesSharedKillFmAndUnknownLaterIdRetainsTheCompletedSourcePrefix() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 0, 0, 0, 0x123, 0, 0x01008000, 0x07991234);
            seedTunnelDoorGlobals(f, 1, 0x3456, 0x55, 0x66);
            var door = tunnelDoor(f, pool.state(40), 2, 0x84, 0x100, 0x780, 0, 0xFEDC)
                    .moveByte(Y_RADIUS, 8).moveWord(Y_VELOCITY, 0x100);
            pool.write(door); pool.write(pool.state(41).moveByte(ID, 0x7E));
            var actor = f.frame.state(); var env = f.frame.environment();
            var prefix = door.moveByte(ROUTINE, 4).moveWord(TUNNEL_DOOR_TIMER, 0).moveByte(MAP_FRAME, 1);
            var expected = CdSolidObject.fromAssets(f.assets).run(CdSolidObject.Entry.SOLID_OBJECT, prefix,
                    tunnelDoorPlayer(f), new CdSolidObject.Gates(0, env.movement().gates().miniSonic()),
                    tunnelDoorGlobals(f), pool.capture());
            assertEquals(CdSolidObject.Path.HIT_BOTTOM, expected.path());
            assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_HURT), expected.fmRequests());
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            sounds.add(new CdOpeningObjectRuntime.TunnelDoorVerticalFm(CdTunnelDoorVertical.FmSound.FM_A4));
            sounds.add(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_HURT)));
            var failure = assertThrows(UnsupportedOperationException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertTrue(failure.getMessage().contains("41")); assertTrue(failure.getMessage().contains("7e"));
            assertTunnelDoorSolidApplied(f, actor, env, expected);
            assertEquals(0x079D1234, f.frame.state().motion().centreY16_16());
            assertEquals(6, f.frame.state().motion().routine()); assertEquals(0, f.frame.state().motion().yVelocity());
            assertEquals(expected.object().moveByte(SPRITE_FLAGS, 4), f.slot(40));
            assertEquals(sounds, f.runtime.lastSoundRequests(), "metadata replay would duplicate Door or shared Kill FM");
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
        }
    }

    @Test void tunnelDoorReadsAnEarlierSpringPatchAndALaterMirrorReadsTheDoorSolidPatchInTheSameWalk() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpringPlayer(f, 0x01008000, 0x07671234, 0x100, 0x100, 2, 0, 0, 0);
            pool.write(spring(f, pool.state(39), 2, 0x84, 0, 0));
            pool.write(tunnelDoor(f, pool.state(40), 2, 0x84, 0x108, 0x78C, 0, 0xABCD));
            pool.write(spring(f, pool.state(42), 2, 4, 0, 0).moveByte(ROUTINE_2, 5));
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(4, f.slot(39).routine()); assertEquals(4, f.slot(40).routine());
            assertEquals(1, f.slot(40).unsignedByte(MAP_FRAME)); assertEquals(0, f.slot(40).word(TUNNEL_DOOR_TIMER));
            assertEquals(0x076D1234, f.frame.state().motion().centreY16_16(), "earlier Spring moved Y from $767 to $76D");
            assertEquals(0x00FA8000, f.frame.state().motion().centreX16_16(), "Door sees dy31 and resolves its native half-width14");
            assertEquals(0, f.frame.state().motion().xVelocity()); assertEquals(-0x1000, f.frame.state().motion().yVelocity());
            assertEquals(0xFA, f.slot(42).centreX()); assertEquals(0x76D, f.slot(42).centreY());
            sounds.add(new CdOpeningObjectRuntime.SpringFm(CdSpring.FmSound.FM_SPRING));
            sounds.add(new CdOpeningObjectRuntime.TunnelDoorVerticalFm(CdTunnelDoorVertical.FmSound.FM_A4));
            assertEquals(sounds, f.runtime.lastSoundRequests());
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    @Test void tunnelDoorRealMapperBuildsExactClosedOpenSatAndServicedTwelveTilePictureAfterLiveDrawAndDespawn() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var placement = f.runtime.placement(); var pool = placement.pool();
            seedSolidPlayer(f, 2, 0, 0, 0, 0, 0x01001234, 0x07805678);
            seedSpinTunnelEnvironment(f, 0x100, 0, 0, 0, 1, 0);
            pool.write(tunnelDoor(f, pool.state(40), 0, 0x80, 0x80, 0x780, 0, 0));
            pool.write(tunnelDoor(f, pool.state(41), 0, 0x80, 0x100, 0x780, 0, 0));
            pool.write(tunnelDoor(f, pool.state(42), 4, 0x84, 0x200, 0x780, 1, 0xF87B));
            pool.write(tunnelDoor(f, pool.state(43), 4, 0x84, 0x280, 0x780, 1, 0).moveByte(SAVED_FLAGS_ID, 7));
            seedSpinTunnelLoaded(placement, 7, 1); var bank = placement.capture().savedFlags().copy();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(4, f.slot(40).spriteFlags()); assertEquals(4, f.slot(41).spriteFlags());
            assertEquals(4, f.slot(42).spriteFlags(), "real Draw clears bit7 even for the culled retained window edge");
            assertEquals(2, f.slot(42).routine()); assertEquals(0x007B, f.slot(42).word(TUNNEL_DOOR_TIMER));
            assertArrayEquals(new byte[64], f.slot(43).copyBytes());
            byte[] expectedBank = bank.clone(); expectedBank[2 + 3 * 7 + 1] &= 0x7F;
            assertArrayEquals(expectedBank, placement.capture().savedFlags().copy());
            var sat = f.runtime.finishAfterCamera(POST);
            assertEquals(0, satEntry(sat, 40)); assertEquals(16, satEntry(sat, 41));
            assertTrue(sat.provenance().stream().noneMatch(p -> p.slotRef().wholeIndex() == 42 || p.slotRef().wholeIndex() == 43));
            assertEquals(List.of(40, 40, 41, 41), sat.provenance().stream().limit(4).map(p -> p.slotRef().wholeIndex()).toList());
            assertTrue(sat.provenance().stream().limit(4).allMatch(p -> p.queueBucket() == 1));
            assertArrayEquals(new byte[] {
                    0, (byte) 0xDD, 1, 1, (byte) 0x9B, 0x2C, 0, (byte) 0xFC,
                    0, (byte) 0xED, 3, 2, (byte) 0x9B, 0x28, 0, (byte) 0xFC,
                    0, (byte) 0xDD, 12, 3, (byte) 0x83, 0x2E, 1, 0x7C,
                    0, (byte) 0xDD, 4, 4, (byte) 0x83, 0x32, 1, (byte) 0x9C
            }, java.util.Arrays.copyOf(sat.bytes(), 32));
            assertEquals(0x84, f.slot(40).spriteFlags()); assertEquals(0x84, f.slot(41).spriteFlags());
            assertEquals(4, f.slot(42).spriteFlags(), "DrawObjects never visits a culled pointer");
            f.runtime.finishOrdinaryTail(); f.service(0x80);
            var shown = f.runtime.displayedFrame(); var objects = f.assets.openingObjects();
            var pixels = CdObjectPresentation.usaR11aTunnelDoorVerticalDevPreloadedArt(f.frame.artState(), f.runtime.powerupArtState(),
                    objects, objects.frontierArtBank(), objects.contactArtBank(), objects.floatBlockArtBank(), objects.springArtBank(),
                    objects.tunnelDoorArtBank(), BANKS.player(), BANKS.ring(), BANKS.mosqui(), BANKS.plant(), BANKS.ramp(),
                    BANKS.explosion(), BANKS.points(), BANKS.flower(), BANKS.powerup(), BANKS.floatBlock(), BANKS.spring(), BANKS.tunnelDoorVertical());
            assertEquals(CdObjectPresentation.convert(sat, pixels), shown, "conversion uses the NOW-serviced windows and the 412-pattern policy");
            var tiles = shown.tiles().stream().filter(t -> t.patternId() >= BANKS.tunnelDoorVertical()
                    && t.patternId() < BANKS.tunnelDoorVertical() + 12).toList();
            assertEquals(12, tiles.size());
            assertTrue(tiles.stream().allMatch(t -> t.palette() == 0 && t.priority()));
            assertTrue(tiles.stream().filter(t -> t.patternId() < BANKS.tunnelDoorVertical() + 6).allMatch(t -> t.hFlip() && t.vFlip()));
            assertTrue(tiles.stream().filter(t -> t.patternId() >= BANKS.tunnelDoorVertical() + 6).allMatch(t -> !t.hFlip() && !t.vFlip()));
            for (int tile = 0; tile < 12; tile++) assertEquals(CdObjectPresentation.patternVersion(objects.tunnelDoorArtBank().tile(0x328 + tile)),
                    shown.patternVersions().get(BANKS.tunnelDoorVertical() + tile));
            assertNotNull(shown.patternVersions().get(BANKS.tunnelDoorVertical() + 11), "last native tile is $333, offset11");
        }
    }

    @Test void tunnelDoorCompositeClosedCutRestoreReplaysTheNextActualBodyArtTableSoundPowerupAndLevel() throws Exception {
        try (var f = new Fixture(new CdLevelGlobals(0, 0, 0, 0, 3, 0, 2, 0x88, 0))) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpringPlayer(f, 0x01008000, 0x0767BEEF, 0x123, 0x100, 2, 0, 0, 0);
            pool.write(tunnelDoor(f, pool.state(40), 0, 0x80, 0x100, 0x780, 0, 0xA55A));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(0, f.frame.state().motion().playerControl());
            assertEquals(4, f.slot(40).routine()); assertEquals(0, f.slot(40).word(TUNNEL_DOOR_TIMER));
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var objects = f.runtime.captureClosedCut(); var actor = f.frame.captureActor(); var shared = f.frame.captureShared();
            var cpu = f.runtime.cpuSat(); var picture = f.runtime.displayedFrame();
            assertEquals(8, objects.levelGlobals().powerup());
            int uploads = f.artPublications.size();
            f.service(0); f.runtime.spawnBeforeReservedSlots();
            assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.body(3).result().boundary());
            f.runtime.runDynamicSlotsAfterReservedSlots(); var nextCpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var next = f.runtime.captureClosedCut(); var nextActor = f.frame.captureActor(); var nextShared = f.frame.captureShared();
            var nextUploads = List.copyOf(f.artPublications.subList(uploads, f.artPublications.size()));
            assertEquals(0x0800, f.slot(40).word(TUNNEL_DOOR_TIMER));
            f.service(0); var shown = f.runtime.displayedFrame();
            // Ordinary service opened the next real cycle. Finish it before any restore preflight.
            f.runtime.spawnBeforeReservedSlots();
            assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.body(4).result().boundary());
            f.runtime.runDynamicSlotsAfterReservedSlots(); f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, f.runtime.phase());
            int publications = f.artPublications.size();
            f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(objects);
            f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(objects);
            assertEquals(actor, f.frame.captureActor()); assertEquals(shared.environment(), f.frame.environment());
            assertSpinTunnelArt(shared.artState(), f.frame.artState()); assertEquals(shared.vIntClock(), f.frame.vIntClock());
            assertEquals(publications, f.artPublications.size(), "restore performs no service or DMA callback");
            assertSame(cpu, f.runtime.cpuSat()); assertSame(picture, f.runtime.displayedFrame());
            assertEquals(objects.lastSoundRequests(), f.runtime.lastSoundRequests());
            assertEquals(objects.powerupArtState(), f.runtime.powerupArtState());
            f.service(0); f.runtime.spawnBeforeReservedSlots();
            assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.body(3).result().boundary());
            f.runtime.runDynamicSlotsAfterReservedSlots(); var replay = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var current = f.runtime.captureClosedCut();
            assertEquals(nextActor, f.frame.captureActor()); assertEquals(nextShared.environment(), f.frame.environment());
            assertSpinTunnelArt(nextShared.artState(), f.frame.artState()); assertEquals(nextShared.vIntClock(), f.frame.vIntClock());
            assertEquals(nextShared.paused(), f.frame.paused());
            var replayUploads = f.artPublications.subList(publications, f.artPublications.size());
            assertEquals(nextUploads.size(), replayUploads.size());
            for (int i = 0; i < nextUploads.size(); i++) assertSpinTunnelArt(nextUploads.get(i), replayUploads.get(i));
            assertArrayEquals(nextCpu.bytes(), replay.bytes()); assertEquals(nextCpu.provenance(), replay.provenance());
            assertEquals(next.placement().pool().slots(), current.placement().pool().slots());
            assertArrayEquals(next.placement().savedFlags().copy(), current.placement().savedFlags().copy());
            assertEquals(next.placement().status(), current.placement().status());
            assertEquals(next.displayedFrame(), current.displayedFrame()); assertEquals(next.lastSoundRequests(), current.lastSoundRequests());
            assertEquals(next.ringGlobals(), current.ringGlobals()); assertEquals(next.spillGlobals(), current.spillGlobals());
            assertEquals(next.contactGlobals(), current.contactGlobals()); assertEquals(next.scoreGlobals(), current.scoreGlobals());
            assertEquals(next.devGlobals(), current.devGlobals()); assertEquals(next.levelGlobals(), current.levelGlobals());
            assertArrayEquals(next.powerupArtState().cpuBuffer().copy(), current.powerupArtState().cpuBuffer().copy());
            assertArrayEquals(next.powerupArtState().residentBank().copy(), current.powerupArtState().residentBank().copy());
            f.service(0); assertEquals(shown, f.runtime.displayedFrame(), "ordinary selector8 republishes the replayed CPU table");
            f.runtime.spawnBeforeReservedSlots(); f.body(4); f.runtime.runDynamicSlotsAfterReservedSlots();
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    private static CdObjectState tunnelDoor(Fixture f, CdObjectState image, int routine, int spriteFlags,
                                            int x, int y, int frame, int timer) throws java.io.IOException {
        return image.moveByte(ID, CdTunnelDoorVertical.OBJECT_ID).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, spriteFlags)
                .moveLong(MAPPINGS, f.assets.openingObjects().view(
                        com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.TUNNEL_DOOR_VERTICAL_MAPPINGS).range().nativeAddress())
                .moveWord(TILE, 0x8328).moveByte(WIDTH, 4).moveByte(Y_RADIUS, 24).moveByte(PRIORITY, 1)
                .moveWord(X, x).moveWord(Y, y).moveByte(MAP_FRAME, frame).moveWord(TUNNEL_DOOR_TIMER, timer);
    }

    private static CdSolidObject.Player tunnelDoorPlayer(Fixture f) {
        var s = f.frame.state(); var h = s.header();
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(), h.tile(), h.priority(),
                s.timers().hurt(), s.rotationDistance()), h.slotId(), s.objectLinks().pushObject(), s.objectLinks().standObject());
    }

    private static CdSolidObject.Globals tunnelDoorGlobals(Fixture f) {
        var e = f.frame.environment(); var g = e.movement().globals();
        return new CdSolidObject.Globals(e.globals().invincible(), g.scoreChain(), g.primaryAngle(), g.secondaryAngle());
    }

    private static void seedTunnelDoorGlobals(Fixture f, int invincible, int score, int primary, int secondary) {
        var shared = f.frame.captureShared(); var e = shared.environment(); var m = e.movement(); var g = m.globals(); var b = e.globals();
        var movement = new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), primary, secondary, g.boredTimer(), score,
                g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance());
        var globals = new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), b.debugMode(),
                b.debugCheat(), b.timeWarpTimer(), invincible, b.bossMusic());
        f.frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), new CdPlayerBody.Environment(
                new CdPlayerMovement.Environment(m.physics(), m.bounds(), m.gates(), movement), e.controls(), globals, e.dynamicSlots()),
                shared.artState(), shared.paused(), shared.vIntClock()));
    }

    private static void assertTunnelDoorSolidApplied(Fixture f, CdPlayerBody.State before, CdPlayerBody.Environment env,
                                                     CdSolidObject.Result result) {
        var p = result.player(); var a = p.actor(); var g = result.globals(); var h = before.header(); var t = before.timers();
        assertEquals(new CdPlayerBody.State(a.motion(), before.animation(),
                new CdPlayerBody.Header(h.slotId(), h.mappingAddress(), a.tile(), a.priority(), h.width()),
                new CdPlayerBody.Timers(a.hurt(), t.invincibility(), t.speedShoes(), t.reset()), before.history(), a.rotationDistance(),
                new CdPlayerBody.ObjectLinks(p.pushObject(), p.standObject())), f.frame.state());
        var m = env.movement(); var old = m.globals(); var b = env.globals();
        assertEquals(new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(), m.gates(),
                new CdPlayerMovement.Globals(old.lookMode(), old.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(), old.boredTimer(),
                        g.scoreChain(), old.debugAngle(), old.debugAngleShift(), old.debugQuadrant(), old.debugFloorDistance())), env.controls(),
                new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), b.debugMode(), b.debugCheat(),
                        b.timeWarpTimer(), g.invincible(), b.bossMusic()), env.dynamicSlots()), f.frame.environment());
    }

    // BEGIN Monitor Runtime R1 tests-first additions. Native source pin
    // 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Constructed same-owner cuts over the existing
    // media-gated fixture; never World/route/native-entry evidence. ALL JAVA UNRUN by this author.

    @Test void monitorThirteenthBankPreservesEveryLegacyConstructorAndAll494Patterns() {
        assertThrows(NullPointerException.class, () -> new CdOpeningObjectRuntime.MonitorFm(null));
        assertThrows(NullPointerException.class, () -> new CdOpeningObjectRuntime.MonitorItemSubCpu(null));
        assertEquals(12374, BANKS.monitorTime());
        var all = new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449, 464, 500, 512);
        assertEquals(494, all.monitorTime() + 82 - all.player());
        assertEquals(all, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138));
        assertEquals(all, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236));
        assertEquals(all, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374));
        assertEquals(all, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413));
        assertEquals(all, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449));
        assertEquals(all, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449, 464));
        assertEquals(all, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449, 464, 500));
        assertEquals(900, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449, 464, 500, 900).monitorTime());
        assertEquals(0x82, com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_FADEPCM.nativeId());
        assertEquals(0x6C, com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS.nativeId());
    }

    @Test void monitorAllThirteenSpansRejectOverlapAndEndOverflowEvenWithAnEmptySatAtBothIngresses() throws Exception {
        var assets = load(); var saved = CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]);
        var window = new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]);
        for (int base : List.of(10000, 11000, 12000, 12070, 12098, 12161, 12231, 12236,
                12275, 12311, 12326, 12362, 12373, -1, Integer.MAX_VALUE - 80)) {
            var banks = new CdOpeningObjectRuntime.LogicalBanks(10000, 11000, 12000, 12070, 12098,
                    12161, 12231, 12236, 12275, 12311, 12326, 12362, base);
            try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0)) {
                assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                        assets, frame, INITIAL, CONTACT, SCORE, LEVEL, new byte[640], banks, PRE, value -> { }));
                assertThrows(IllegalArgumentException.class, () -> restart(assets, frame, saved, window, new byte[640], banks));
                assertFalse(frame.closed()); assertEquals(0, frame.vIntClock().deltaSinceAdmittedCut());
            }
        }
    }

    // ObjMonitor_Init:300-330: replacement flags4 bypass Solid/Animate on the first call.
    @Test void monitorFreshInitClearsTheActualLoadedBitAndWaitsForTheRealSpriteBuildVisibility() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var placement = f.runtime.placement(); var pool = placement.pool();
            placement.bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
            placement.bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.MEMBER_3);
            var before = monitorImage(f, pool.state(40), 0, 0xFF, 4, 0x100, 0x780, 7)
                    .moveWord(X_FRACTION, 0x1234).moveWord(Y_FRACTION, 0x5678)
                    .moveByte(MAP_FRAME, 4).moveByte(PREVIOUS_ANIMATION, 7).moveByte(ANIMATION_TIME, 0x65)
                    .moveByte(RAW_CONTROL_2C, 0xA4);
            pool.write(before); var actor = f.frame.state(); var env = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            var expected = before.moveByte(ROUTINE, 2).moveByte(Y_RADIUS, 14).moveByte(X_RADIUS, 14)
                    .moveByte(SPRITE_FLAGS, 4).moveByte(WIDTH, 15).moveByte(PRIORITY, 3).moveWord(TILE, 0x5A8)
                    .moveByte(COLLISION_TYPE, 0x46).moveByte(ANIMATION, 4);
            assertEquals(expected, f.slot(40)); assertEquals(4, placement.savedFlag(7, 1));
            assertEquals(actor, f.frame.state()); assertEquals(env, f.frame.environment()); assertEquals(sounds, f.runtime.lastSoundRequests());
            var sat = f.runtime.finishAfterCamera(POST);
            assertEquals(4, sat.provenance().stream().filter(v -> v.slotRef().wholeIndex() == 40).count());
            assertEquals(0x84, f.slot(40).spriteFlags(), "only the real sprite build installs the next call's bit7");
            f.runtime.finishOrdinaryTail();
        }
    }

    @Test void monitorAlreadyDestroyedInitReturnsBeforeDrawAndPreservesUnwrittenBytes() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var placement = f.runtime.placement(); var pool = placement.pool();
            seedSpinTunnelLoaded(placement, 7, 1);
            var before = monitorImage(f, pool.state(40), 0, 0x84, 4, 0x100, 0x780, 7)
                    .moveByte(COLLISION_TYPE, 0xA7).moveByte(ANIMATION, 6).moveByte(ANIMATION_TIME, 0x33);
            pool.write(before); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(before.moveByte(ROUTINE, 8).moveByte(MAP_FRAME, 17).moveByte(Y_RADIUS, 14).moveByte(X_RADIUS, 14)
                    .moveWord(TILE, 0x5A8).moveByte(PRIORITY, 3).moveByte(WIDTH, 15).moveByte(SPRITE_FLAGS, 4), f.slot(40));
            assertEquals(0x55, placement.savedFlag(7, 1));
            var sat = f.runtime.finishAfterCamera(POST);
            assertTrue(sat.provenance().stream().noneMatch(v -> v.slotRef().wholeIndex() == 40));
            f.runtime.finishOrdinaryTail();
        }
    }

    @Test void monitorPriorVisibilityAndTheExistingTimeStopWordGateOnlyTheirNativeCuts() throws Exception {
        for (int stop : List.of(0, 1, 0xFFFF)) for (int visible : List.of(4, 0x84)) {
            try (var f = new Fixture(new CdRingGlobals(0, 3, 0, 0, 0, stop, 0, 0))) {
                springDeviceCut(f); var pool = f.runtime.placement().pool();
                seedSolidPlayer(f, 8, 40, 1, 0x180, 0x100, 0x02001234, 0x07005678);
                var before = monitorImage(f, pool.state(40), 2, visible, 4, 0x100, 0x780, 0).moveByte(FLAGS, 8)
                        .moveByte(ANIMATION, 4).moveByte(PREVIOUS_ANIMATION, 4).moveByte(ANIMATION_FRAME, 2)
                        .moveByte(ANIMATION_TIME, 7).moveByte(MAP_FRAME, 4);
                pool.write(before); var player = f.frame.state(); var env = f.frame.environment();
                var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
                f.runtime.runDynamicSlotsAfterReservedSlots();
                if (visible == 4) {
                    assertEquals(player, f.frame.state()); assertEquals(env, f.frame.environment());
                } else {
                    assertEquals(0, f.frame.state().objectLinks().standObject());
                    assertEquals(0, f.frame.state().motion().flags() & 8);
                    sounds.add(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)));
                }
                assertEquals(stop == 0 && visible == 0x84 ? 6 : 7, f.slot(40).unsignedByte(ANIMATION_TIME));
                assertEquals(4, f.slot(40).unsignedByte(MAP_FRAME)); assertEquals(4, f.slot(40).spriteFlags());
                assertEquals(stop, f.runtime.ringGlobals().timeStopTimer()); assertEquals(sounds, f.runtime.lastSoundRequests());
                f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
                assertEquals(stop, f.runtime.ringGlobals().timeStopTimer(), "no invented time-stop decrement in the ordinary tail");
            }
        }
    }

    @Test void monitorActualSolidCommitsTheWholePlayerAndBothAngleBuffersBeforeALaterPhysicalConsumer() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 8, 41, 0, 0x234, 0x100, 0x01008000, 0x075F1234);
            seedTunnelDoorGlobals(f, 1, 0x3456, 0x55, 0x66);
            var self = monitorImage(f, pool.state(40), 2, 0x84, 4, 0x100, 0x780, 0)
                    .moveByte(ANIMATION, 4).moveWord(X_VELOCITY, 0x123);
            var oldStand = monitorImage(f, pool.state(41), 8, 4, 4, 0x100, 0x780, 0).moveByte(FLAGS, 0xA8).moveByte(MAP_FRAME, 17);
            pool.write(self); pool.write(oldStand);
            pool.write(spring(f, pool.state(42), 2, 4, 0, 0).moveByte(ROUTINE_2, 5)
                    .moveWord(X_FRACTION, 0x4567).moveWord(Y_FRACTION, 0x89AB));
            var untouched = pool.state(127).moveWord(TUNNEL_DOOR_TIMER, 0xDEAD); pool.write(untouched);
            var actor = f.frame.state(); var env = f.frame.environment(); var display = f.frame.display();
            var expected = CdSolidObject.fromAssets(f.assets).run(CdSolidObject.Entry.SOLID_OBJECT, self,
                    tunnelDoorPlayer(f), new CdSolidObject.Gates(env.globals().debugMode(), env.movement().gates().miniSonic()),
                    tunnelDoorGlobals(f), pool.capture());
            // The real terrain/probe owns its distance and branch. No fabricated floor result or landing assertion.
            assertTrue(expected.globalWrites().contains(CdSolidObject.GlobalField.PRIMARY_ANGLE));
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            expected.fmRequests().forEach(v -> sounds.add(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(v))));
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertTunnelDoorSolidApplied(f, actor, env, expected);
            assertEquals(display, f.frame.display()); assertEquals(untouched, f.slot(127));
            var released = expected.otherSlots().stream().filter(v -> v.identity().wholeIndex() == 41).findFirst().orElseThrow();
            assertEquals(released.moveByte(SPRITE_FLAGS, 4), f.slot(41));
            assertEquals(expected.player().actor().motion().centreX16_16() >>> 16, f.slot(42).centreX());
            assertEquals(expected.player().actor().motion().centreY16_16() >>> 16, f.slot(42).centreY());
            assertEquals(0x4567, f.slot(42).word(X_FRACTION)); assertEquals(0x89AB, f.slot(42).word(Y_FRACTION));
            assertEquals(sounds, f.runtime.lastSoundRequests()); f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    @Test void monitorFallUsesRealRawGravityAndFloorThenWritesTheFramePrimaryAngleAtThatCallOnly() throws Exception {
        for (int control : List.of(0, 4)) {
            try (var f = new Fixture(new CdRingGlobals(0, 3, 0, 0, 0, 1, 0, 0))) {
                springDeviceCut(f); var pool = f.runtime.placement().pool(); seedTunnelDoorGlobals(f, 0, 0x3456, 0x55, 0x66);
                var before = monitorImage(f, pool.state(40), 2, 0x84, 4, 0x100, 0x700, 0)
                        .moveWord(X_FRACTION, 0x1234).moveWord(Y_FRACTION, 0x5678)
                        .moveWord(X_VELOCITY, 0x100).moveWord(Y_VELOCITY, -0x180)
                        .moveByte(ROUTINE_2, 4).moveByte(RAW_CONTROL_2C, control).moveByte(ANIMATION_TIME, 9);
                pool.write(before); var actor = f.frame.state(); var env = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
                var moved = before.moveLong(CdObjectState.LongField.X_POSITION, 0x01011234)
                        .moveLong(CdObjectState.LongField.Y_POSITION, 0x06FED678).moveWord(Y_VELOCITY, control == 0 ? -0x148 : -0x180);
                var floor = CdObjectCollisionCheck.objGetFloorDist(new com.openggf.game.soniccd.player.CdPlayerTerrain(f.assets.terrain()), moved);
                if (floor.distance() < 0) moved = moved.addWord(Y, floor.distance()).moveWord(Y_VELOCITY, 0).moveByte(ROUTINE_2, 0);
                int externalWrites = f.angleWrites.size(); f.runtime.runDynamicSlotsAfterReservedSlots();
                assertEquals(moved.moveByte(SPRITE_FLAGS, 4), f.slot(40)); assertEquals(actor, f.frame.state());
                assertEquals(floor.primaryAngle(), f.frame.environment().movement().globals().primaryAngle());
                assertEquals(env.movement().globals().secondaryAngle(), f.frame.environment().movement().globals().secondaryAngle());
                assertEquals(env.movement().physics(), f.frame.environment().movement().physics());
                assertEquals(externalWrites, f.angleWrites.size(), "Monitor directly uses Frame's writer, never a second external angle port");
                assertEquals(sounds, f.runtime.lastSoundRequests()); assertEquals(1, f.runtime.ringGlobals().timeStopTimer());
                f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            }
        }
    }

    @Test void monitorMmzGlobalLayerIsAnExplicitTypedUnownedBoundaryBeforeAnyBodyPrefix() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); monitorEnvironment(f, 0, 0, 6, 0);
            var before = monitorImage(f, pool.state(40), 0, 0x84, 4, 0x100, 0x780, 7).moveByte(LAYER, 0xA5);
            pool.write(before); var actor = f.frame.state(); var env = f.frame.environment();
            var bank = f.runtime.placement().capture().savedFlags().copy();
            var error = assertThrows(CdOpeningObjectRuntime.UnsupportedMonitorBinding.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(CdOpeningObjectRuntime.UnsupportedMonitorBindingBoundary.MMZ_GLOBAL_LAYER, error.boundary());
            assertEquals(40, error.wholeIndex()); assertEquals(before, f.slot(40));
            assertEquals(actor, f.frame.state()); assertEquals(env, f.frame.environment());
            assertArrayEquals(bank, f.runtime.placement().capture().savedFlags().copy());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    @Test void monitorTimeAttackZeroSubtypeBypassesEvenAnUnownedRoutineWithoutDrawOrInit() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); monitorEnvironment(f, 1, 0, 0, 1);
            var before = monitorImage(f, pool.state(40), 0xFF, 4, 0, 0x100, 0x780, 7);
            pool.write(before); f.runtime.runDynamicSlotsAfterReservedSlots(); assertEquals(before, f.slot(40));
            var sat = f.runtime.finishAfterCamera(POST);
            assertTrue(sat.provenance().stream().noneMatch(v -> v.slotRef().wholeIndex() == 40)); f.runtime.finishOrdinaryTail();
        }
    }

    @Test void monitorBreakReadsActualRawTimeZoneDirectionAndTheOneLinearSavedBank() throws Exception {
        int[][] rows = {{1, 0, 7, 1}, {0x82, 1, 7, 1}, {0x80, 1, 7, 0}, {0x82, 0xFF, 7, 2}, {4, 0, 8, 1}};
        for (int[] row : rows) try (var f = new Fixture()) {
            springDeviceCut(f); var placement = f.runtime.placement(); var pool = placement.pool();
            monitorEnvironment(f, row[0], row[1], 0, 0);
            placement.bsetSavedFlag(row[2], row[3], CdObjectPlacementRuntime.SavedBit.LOADED);
            var bank = placement.capture().savedFlags().copy();
            pool.write(monitorImage(f, pool.state(40), 4, 0x84, 4, 0x100, 0x780, 7).moveByte(ANIMATION, 4));
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests()); f.runtime.runDynamicSlotsAfterReservedSlots();
            byte[] expected = bank.clone(); expected[2 + 3 * row[2] + row[3]] |= 1;
            assertArrayEquals(expected, placement.capture().savedFlags().copy());
            assertEquals(8, f.slot(40).routine()); assertEquals(17, f.slot(40).unsignedByte(MAP_FRAME));
            assertEquals(0x1A, f.slot(32).id()); assertEquals(0, f.slot(32).routine());
            assertEquals(0x18, f.slot(33).id()); assertEquals(0, f.slot(33).routine());
            sounds.add(new CdOpeningObjectRuntime.MonitorFm(CdMonitor.FmSound.FM_DESTROY));
            assertEquals(sounds, f.runtime.lastSoundRequests(), "Break has one direct sound and no metadata replay");
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    @Test void monitorActualBodyTouchBouncesThenBreaksInTheSameLivePassWithoutScoreOrChainReward() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            seedSpinTunnelMotion(f, spinTunnelMotion(f, 0x01000000, 0x07000000, 0, 0x100, 0, 6, 2));
            pool.write(monitorImage(f, pool.state(40), 2, 0x84, 4, 0x100, 0x710, 7)
                    .moveByte(ANIMATION, 4).moveByte(COLLISION_TYPE, 0x46));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); var publication = f.body(2);
            var response = publication.result().effects().stream().filter(CdPlayerBody.ObjectTouchResponse.class::isInstance)
                    .map(CdPlayerBody.ObjectTouchResponse.class::cast).findFirst().orElseThrow().result();
            assertEquals(CdPlayerTouchResponse.Exit.MONITOR_DOWNWARD_BOUNCE, response.exit());
            assertEquals(4, f.slot(40).routine()); assertEquals(response.changedSlots().getFirst(), f.slot(40));
            var actor = f.frame.state(); var env = f.frame.environment(); var rings = f.runtime.ringGlobals();
            var score = f.runtime.scoreGlobals(); var contact = f.runtime.contactGlobals();
            var sounds = new ArrayList<CdOpeningObjectRuntime.SoundRequest>();
            publication.sounds().forEach(v -> sounds.add(new CdOpeningObjectRuntime.PlayerSound(v)));
            sounds.add(new CdOpeningObjectRuntime.MonitorFm(CdMonitor.FmSound.FM_DESTROY));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(actor, f.frame.state()); assertEquals(env, f.frame.environment());
            assertEquals(rings, f.runtime.ringGlobals()); assertEquals(score, f.runtime.scoreGlobals()); assertEquals(contact, f.runtime.contactGlobals());
            assertEquals(8, f.slot(40).routine()); assertEquals(0, f.slot(40).unsignedByte(COLLISION_TYPE));
            assertEquals(1, f.runtime.placement().savedFlag(7, 1)); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertEquals(0x1A, f.slot(32).id()); assertEquals(0, f.slot(32).routine());
            assertEquals(0x18, f.slot(33).id()); assertEquals(1, f.slot(33).unsignedByte(ROUTINE_2));
            assertTrue(pool.capture().slots().stream().noneMatch(s -> s.id() == CdPoints.OBJECT_ID));
            f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    @Test void monitorEarlierChildrenWaitAndLaterChildrenRunAtTheirActualPhysicalVisit() throws Exception {
        for (int parent : List.of(32, 40)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            pool.write(monitorImage(f, pool.state(parent), 4, 0x84, 4, 0x100, 0x780, 7).moveByte(ANIMATION, 4));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            int item = parent == 32 ? 33 : 32; int explosion = parent == 32 ? 34 : 33;
            assertEquals(0x1A, f.slot(item).id()); assertEquals(parent == 32 ? 2 : 0, f.slot(item).routine());
            assertEquals(parent == 32 ? 0x77D : 0x780, f.slot(item).centreY());
            assertEquals(parent == 32 ? 0xFD18 : 0, f.slot(item).word(Y_VELOCITY));
            assertEquals(0x18, f.slot(explosion).id()); assertEquals(parent == 32 ? 2 : 0, f.slot(explosion).routine());
            assertEquals(1, f.slot(explosion).unsignedByte(ROUTINE_2));
            assertTrue(pool.capture().slots().stream().noneMatch(s -> s.id() == CdPoints.OBJECT_ID));
            assertEquals(SCORE, f.runtime.scoreGlobals()); f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            if (parent == 32) {
                // Native explosion Done: nonzero explodeBadnik deletes without points/flower.
                pool.write(f.slot(explosion).moveByte(ROUTINE, 4));
                f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(3); f.runtime.runDynamicSlotsAfterReservedSlots();
                assertArrayEquals(new byte[64], f.slot(explosion).copyBytes());
                assertTrue(pool.capture().slots().stream().noneMatch(s -> s.id() == CdPoints.OBJECT_ID || s.id() == CdFlower.OBJECT_ID));
                f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            }
        }
    }

    @Test void monitorBreakPreservesDirtyEarlierFreeSlotBytesAndTheFirstFreeSearchRemainsIdOnly() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            var dirty = pool.state(32).moveWord(X_FRACTION, 0x1234).moveWord(Y_FRACTION, 0x5678)
                    .moveByte(ROUTINE, 6).moveByte(RAW_CONTROL_2C, 0xA4).moveByte(ANIMATION_TIME, 0xB5);
            pool.write(dirty); pool.write(monitorImage(f, pool.state(40), 4, 0x84, 4, 0x100, 0x780, 7).moveByte(ANIMATION, 4).moveByte(LAYER, 0xA5));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(dirty.moveByte(ID, 0x1A).moveWord(X, 0x100).moveWord(Y, 0x780).moveByte(ANIMATION, 4).moveByte(LAYER, 0xA5), f.slot(32));
            assertEquals(0, f.slot(33).routine()); f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(3);
            var error = assertThrows(CdMonitorItem.UnsupportedMonitorItem.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(CdMonitorItem.UnsupportedBoundary.ROUTINE_DISPATCH, error.boundary());
            assertEquals(6, f.slot(32).routine()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    @Test void monitorNoFreeOrOneFreeSlotStillBreaksAndNeverRetriesOrInventsAnExplosion() throws Exception {
        for (int free : List.of(0, 1)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            for (int whole = 32; whole < 128; whole++) pool.write(pool.state(whole).moveByte(ID, CdAnimal.OBJECT_ID)
                    .moveWord(X, 0x100).moveWord(Y, 0x780));
            if (free == 1) pool.deleteObject(32);
            pool.write(monitorImage(f, pool.state(40), 4, 0x84, 4, 0x100, 0x780, 7).moveByte(ANIMATION, 4));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(free, pool.capture().slots().stream().filter(s -> s.id() == 0x1A).count());
            assertTrue(pool.capture().slots().stream().noneMatch(s -> s.id() == 0x18));
            assertEquals(1, f.runtime.placement().savedFlag(7, 1)); assertEquals(8, f.slot(40).routine());
            assertEquals(17, f.slot(40).unsignedByte(MAP_FRAME)); f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    // Main:465-482,624-638. Ordinals count this actual item body's calls, not World frames.
    @Test void monitorCleanItemMoves32TimesAwardsOn33ThenDeletesOnTheThirtiethWordTimerCall() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); f.makePlayerAbsent(); var pool = f.runtime.placement().pool();
            pool.write(pool.state(40).moveByte(ID, 0x1A).moveWord(X, 0x850).moveWord(Y, 0x2D8).moveByte(ANIMATION, 4));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(0x02D50000, f.slot(40).longBits(CdObjectState.LongField.Y_POSITION));
            assertEquals(0xFD18, f.slot(40).word(Y_VELOCITY)); monitorFinish(f);
            for (int call = 2; call <= 32; call++) { monitorBegin(f, call + 1); f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f); }
            assertEquals(0x02A68000, f.slot(40).longBits(CdObjectState.LongField.Y_POSITION));
            assertEquals(0, f.slot(40).word(Y_VELOCITY)); assertEquals(2, f.slot(40).routine());
            assertEquals(0, f.frame.environment().movement().gates().speedShoes());
            monitorBegin(f, 34); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(4, f.slot(40).routine()); assertEquals(29, f.slot(40).word(MONITOR_ITEM_DELETE));
            assertEquals(1320, f.frame.state().timers().speedShoes());
            assertEquals(List.of(new CdOpeningObjectRuntime.MonitorItemSubCpu(
                    com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS)), f.runtime.lastSoundRequests());
            monitorFinish(f);
            for (int call = 1; call <= 30; call++) {
                monitorBegin(f, 34 + call); f.runtime.runDynamicSlotsAfterReservedSlots();
                if (call < 30) assertEquals(29 - call, f.slot(40).word(MONITOR_ITEM_DELETE));
                else assertArrayEquals(new byte[64], f.slot(40).copyBytes());
                var sat = f.runtime.finishAfterCamera(POST);
                if (call == 30) assertTrue(sat.provenance().stream().noneMatch(v -> v.slotRef().wholeIndex() == 40),
                        "native post-delete Draw queues a pointer; live ID0 skips it at drain");
                f.runtime.finishOrdinaryTail();
                assertEquals(1320, f.frame.state().timers().speedShoes(), "absent primary never runs Display");
                assertTrue(f.runtime.lastSoundRequests().isEmpty(), "Delete never repeats the award");
            }
        }
    }

    @Test void monitorItemStandingMoveBorrowsTheActualPrimaryHeaderIdAndStatusByte() throws Exception {
        for (int id : List.of(0, 0x1E)) for (int flags : List.of(0, 1)) {
            try (var f = new Fixture()) {
                springDeviceCut(f); var pool = f.runtime.placement().pool();
                var actor = f.frame.captureActor(); var s = actor.state(); var m = s.motion();
                var motion = spinTunnelMotion(f, m.centreX16_16(), m.centreY16_16(), 0, 0, 0, flags, 0);
                f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), new CdPlayerBody.State(motion, s.animation(),
                        new CdPlayerBody.Header(id, s.header().mappingAddress(), s.header().tile(), s.header().priority(), s.header().width()),
                        s.timers(), s.history(), s.rotationDistance(), s.objectLinks()), actor.display()));
                var before = pool.state(40).moveByte(ID, 0x1A).moveByte(ANIMATION, 4).moveByte(FLAGS, 8)
                        .moveByte(STAND_LINK_3D, 0).moveWord(X, 0x100).moveWord(X_FRACTION, 0x1234)
                        .moveWord(Y, 0x780).moveWord(Y_FRACTION, 0x5678).moveWord(X_VELOCITY, 0x180);
                pool.write(before); var player = f.frame.state(); f.runtime.runDynamicSlotsAfterReservedSlots();
                int local = id != 0x1E ? 0x180 : flags == 0 ? 0x80 : 0x280;
                assertEquals(0x01001234 + (local << 8), f.slot(40).longBits(CdObjectState.LongField.X_POSITION));
                assertEquals(0x077D5678, f.slot(40).longBits(CdObjectState.LongField.Y_POSITION));
                assertEquals(0x180, f.slot(40).word(X_VELOCITY)); assertEquals(0xFD18, f.slot(40).word(Y_VELOCITY));
                assertEquals(player, f.frame.state()); monitorFinish(f);
            }
        }
    }

    @Test void monitorItemReadsActualLaterDynamicSupportBeforeThatUnownedIdFailsAtItsOwnVisit() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            pool.write(pool.state(40).moveByte(ID, 0x1A).moveByte(ANIMATION, 4).moveByte(FLAGS, 8).moveByte(STAND_LINK_3D, 42)
                    .moveWord(X, 0x100).moveWord(Y, 0x780).moveWord(X_VELOCITY, 0x180));
            pool.write(pool.state(42).moveByte(ID, 0x1E).moveByte(FLAGS, 1));
            assertThrows(UnsupportedOperationException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(0x01028000, f.slot(40).longBits(CdObjectState.LongField.X_POSITION));
            assertEquals(2, f.slot(40).routine()); assertEquals(0xFD18, f.slot(40).word(Y_VELOCITY));
            assertEquals(0x1E, f.slot(42).id()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    @Test void monitorShoesAwardUsesFiveActualFrameStoresAndWholeRawTimeZoneCommandsAfterTheOrdinaryBodySound() throws Exception {
        for (int raw : List.of(0, 1, 0x80, 0x81, 0xFF)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); monitorEnvironment(f, raw, 0, 0, 0);
            pool.write(monitorItemImage(f, pool.state(40), 2, 4, 0x100, 0x780).moveWord(Y_VELOCITY, 0));
            var before = f.frame.state(); var env = f.frame.environment(); var display = f.frame.display();
            var ring = f.runtime.ringGlobals(); var contact = f.runtime.contactGlobals(); var score = f.runtime.scoreGlobals();
            var level = f.runtime.levelGlobals(); var powerup = f.runtime.powerupArtState(); var bank = f.runtime.placement().capture().savedFlags().copy();
            assertEquals(List.of(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ChargeSound(
                    com.openggf.game.soniccd.player.CdPlayerCharge.Sound.FM_CHARGESTOP))), f.runtime.lastSoundRequests());
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            if (raw == 0) sounds.add(new CdOpeningObjectRuntime.MonitorItemSubCpu(
                    com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_FADEPCM));
            sounds.add(new CdOpeningObjectRuntime.MonitorItemSubCpu(
                    com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS));
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertMonitorFiveStores(f, before, env);
            assertEquals(4, f.slot(40).routine()); assertEquals(29, f.slot(40).word(MONITOR_ITEM_DELETE));
            assertEquals(display, f.frame.display()); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertEquals(ring, f.runtime.ringGlobals()); assertEquals(contact, f.runtime.contactGlobals()); assertEquals(score, f.runtime.scoreGlobals());
            assertEquals(level, f.runtime.levelGlobals());
            assertArrayEquals(powerup.cpuBuffer().copy(), f.runtime.powerupArtState().cpuBuffer().copy());
            assertArrayEquals(powerup.residentBank().copy(), f.runtime.powerupArtState().residentBank().copy());
            assertArrayEquals(bank, f.runtime.placement().capture().savedFlags().copy());
            monitorFinish(f); assertEquals(29, f.slot(40).word(MONITOR_ITEM_DELETE)); assertEquals(1320, f.frame.state().timers().speedShoes());
        }
    }

    @Test void monitorOtherRewardsKeepTheirTypedSourcePrefixAndEarlierBreakWhileClosingBothOwnersWithoutRetry() throws Exception {
        int[] kinds = {0, 1, 2, 3, 5, 6, 7, 255};
        var boundaries = List.of(CdMonitorItem.UnsupportedBoundary.REWARD_ONE_UP, CdMonitorItem.UnsupportedBoundary.REWARD_RINGS,
                CdMonitorItem.UnsupportedBoundary.REWARD_SHIELD, CdMonitorItem.UnsupportedBoundary.REWARD_INVINCIBILITY,
                CdMonitorItem.UnsupportedBoundary.REWARD_TIME_STOP, CdMonitorItem.UnsupportedBoundary.REWARD_COMBINE_RING,
                CdMonitorItem.UnsupportedBoundary.REWARD_S, CdMonitorItem.UnsupportedBoundary.REWARD_S);
        for (int i = 0; i < kinds.length; i++) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            pool.write(monitorImage(f, pool.state(39), 4, 0x84, 4, 0x100, 0x780, 7).moveByte(ANIMATION, 4));
            var item = monitorItemImage(f, pool.state(40), 2, 4, 0x100, 0x780).moveByte(ANIMATION, kinds[i]).moveWord(Y_VELOCITY, 0);
            pool.write(item); var actor = f.frame.state(); var env = f.frame.environment();
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests()); sounds.add(new CdOpeningObjectRuntime.MonitorFm(CdMonitor.FmSound.FM_DESTROY));
            var error = assertThrows(CdMonitorItem.UnsupportedMonitorItem.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(boundaries.get(i), error.boundary()); assertEquals(kinds[i], error.value());
            assertEquals(item.moveByte(ROUTINE, 4).moveWord(MONITOR_ITEM_DELETE, 29), f.slot(40));
            assertEquals(8, f.slot(39).routine()); assertEquals(1, f.runtime.placement().savedFlag(7, 1));
            assertEquals(actor, f.frame.state()); assertEquals(env, f.frame.environment()); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(item.moveByte(ROUTINE, 4).moveWord(MONITOR_ITEM_DELETE, 29), f.slot(40));
        }
    }

    @Test void monitorEarlierSpringLaunchIsVisibleToMonitorSolidAndALaterMirrorInTheSamePass() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpringPlayer(f, 0x01008000, 0x07671234, 0x100, 0x100, 2, 0, 0, 0);
            pool.write(spring(f, pool.state(39), 2, 0x84, 0, 0));
            pool.write(monitorImage(f, pool.state(40), 2, 0x84, 4, 0x100, 0x78E, 0).moveByte(ANIMATION, 4));
            pool.write(spring(f, pool.state(42), 2, 4, 0, 0).moveByte(ROUTINE_2, 5));
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests()); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(4, f.slot(39).routine()); assertEquals(-0x1000, f.frame.state().motion().yVelocity());
            assertEquals(0x076D1234, f.frame.state().motion().centreY16_16(), "rising-above Monitor sees the already launched player");
            assertEquals(0x100, f.slot(42).centreX()); assertEquals(0x76D, f.slot(42).centreY());
            assertEquals(16, f.slot(40).unsignedByte(MAP_FRAME));
            sounds.add(new CdOpeningObjectRuntime.SpringFm(CdSpring.FmSound.FM_SPRING));
            assertEquals(sounds, f.runtime.lastSoundRequests()); monitorFinish(f);
        }
    }

    @Test void monitorEarlierTagRollIsVisibleToMonitorAndTheLaterShoesAwardPreservesThoseActualMotionWrites() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpinTunnelMotion(f, spinTunnelMotion(f, 0x01001234, 0x0757BEEF, 0x100, 0x100, 0, 2, 0));
            pool.write(spinTunnelTag(f, pool.state(39), 2, 4, 0, 0x100, 0x757, 0));
            pool.write(monitorImage(f, pool.state(40), 2, 0x84, 4, 0x100, 0x778, 0).moveByte(ANIMATION, 4));
            pool.write(monitorItemImage(f, pool.state(41), 2, 4, 0x100, 0x740).moveWord(Y_VELOCITY, 0));
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests()); f.runtime.runDynamicSlotsAfterReservedSlots();
            var motion = f.frame.state().motion();
            assertEquals(0xA00, motion.xVelocity()); assertEquals(-0xA00, motion.groundVelocity(), "angle $60 selects the inverted ground sign");
            assertEquals(0x075CBEEF, motion.centreY16_16()); assertEquals(6, motion.flags());
            assertEquals(2, motion.animation()); assertEquals(14, motion.yRadius()); assertEquals(7, motion.xRadius());
            assertEquals(0, f.slot(40).flags() & 8, "native Monitor rolling branch does not land the newly rolled player");
            assertEquals(1320, f.frame.state().timers().speedShoes());
            sounds.add(new CdOpeningObjectRuntime.MonitorItemSubCpu(com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS));
            assertEquals(sounds, f.runtime.lastSoundRequests()); monitorFinish(f);
        }
    }

    @Test void monitorLaterUnknownIdRetainsTheCommittedFiveStoresAndOrderedCommandsWithoutRollback() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); monitorEnvironment(f, 0, 0, 0, 0);
            pool.write(monitorItemImage(f, pool.state(40), 2, 4, 0x100, 0x780).moveWord(Y_VELOCITY, 0));
            pool.write(pool.state(42).moveByte(ID, 0x7E));
            var before = f.frame.state(); var env = f.frame.environment(); var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            sounds.add(new CdOpeningObjectRuntime.MonitorItemSubCpu(com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_FADEPCM));
            sounds.add(new CdOpeningObjectRuntime.MonitorItemSubCpu(com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS));
            assertThrows(UnsupportedOperationException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertMonitorFiveStores(f, before, env); assertEquals(29, f.slot(40).word(MONITOR_ITEM_DELETE));
            assertEquals(sounds, f.runtime.lastSoundRequests()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(sounds, f.runtime.lastSoundRequests());
        }
    }

    @Test void monitorCountedShoesAndStaticItemBuildExactSatAndUseTheNowServiced494PatternPolicy() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); f.makePlayerAbsent(); var pool = f.runtime.placement().pool();
            pool.write(monitorImage(f, pool.state(40), 8, 0x84, 4, 0x100, 0x780, 0).moveByte(MAP_FRAME, 4));
            pool.write(pool.state(41).moveByte(ID, 0x1A).moveByte(ANIMATION, 4).moveWord(X, 0x140).moveWord(Y, 0x780));
            f.runtime.runDynamicSlotsAfterReservedSlots(); var sat = f.runtime.finishAfterCamera(POST);
            assertEquals(4, sat.provenance().stream().filter(v -> v.slotRef().wholeIndex() == 40).count());
            assertEquals(1, sat.provenance().stream().filter(v -> v.slotRef().wholeIndex() == 41).count());
            int parent = satEntry(sat, 40), item = satEntry(sat, 41);
            assertArrayEquals(new byte[] {
                    0, (byte) 0xEB, 5, 1, 5, (byte) 0xCA, 1, 0x78,
                    0, (byte) 0xE5, 6, 2, 5, (byte) 0xA8, 1, 0x70,
                    0, (byte) 0xE5, 6, 3, 13, (byte) 0xA8, 1, (byte) 0x80,
                    0, (byte) 0xFD, 12, 4, 5, (byte) 0xAE, 1, 0x70
            }, java.util.Arrays.copyOfRange(sat.bytes(), parent, parent + 32));
            assertArrayEquals(new byte[] {0, (byte) 0xE8, 5, 5, (byte) 0x85, (byte) 0xCA, 1, (byte) 0xB8},
                    java.util.Arrays.copyOfRange(sat.bytes(), item, item + 8));
            assertEquals(0x20AACF, f.slot(41).longBits(MAPPINGS)); assertEquals(0xA4, f.slot(41).spriteFlags());
            f.runtime.finishOrdinaryTail(); f.service(0x80);
            var objects = f.assets.openingObjects(); var pixels = monitorPixels(f);
            var previous = CdObjectPresentation.usaR11aTunnelDoorVerticalDevPreloadedArt(f.frame.artState(), f.runtime.powerupArtState(), objects,
                    objects.frontierArtBank(), objects.contactArtBank(), objects.floatBlockArtBank(), objects.springArtBank(), objects.tunnelDoorArtBank(),
                    BANKS.player(), BANKS.ring(), BANKS.mosqui(), BANKS.plant(), BANKS.ramp(), BANKS.explosion(), BANKS.points(), BANKS.flower(),
                    BANKS.powerup(), BANKS.floatBlock(), BANKS.spring(), BANKS.tunnelDoorVertical());
            int[][] spans = {{0x780, 24}, {0x7AE, 14}, {0x3A0, 70}, {0x424, 28}, {0x441, 63}, {0x680, 70},
                    {0x6C6, 5}, {0x6D7, 39}, {0x544, 36}, {0x334, 15}, {0x520, 36}, {0x328, 12}};
            int checked = 0;
            for (int[] span : spans) for (int i = 0; i < span[1]; i++) {
                assertEquals(previous.resolve(span[0] + i), pixels.resolve(span[0] + i)); checked++;
            }
            for (int i = 0; i < 82; i++) {
                var pattern = pixels.resolve(0x5A8 + i);
                assertEquals(BANKS.monitorTime() + i, pattern.logicalPatternId());
                assertEquals(CdObjectPresentation.patternVersion(objects.monitorTimeArtBank().tile(0x5A8 + i)), pattern.version()); checked++;
            }
            assertEquals(494, checked);
            assertEquals(CdObjectPresentation.convert(sat, pixels), f.runtime.displayedFrame());
            var shown = f.runtime.displayedFrame();
            var icons = shown.tiles().stream().filter(t -> t.patternId() >= BANKS.monitorTime() + 0x22
                    && t.patternId() < BANKS.monitorTime() + 0x26).toList();
            assertEquals(8, icons.size()); assertEquals(4, icons.stream().filter(t -> t.priority()).count());
            for (int tile = 0x22; tile < 0x26; tile++) assertEquals(
                    CdObjectPresentation.patternVersion(objects.monitorTimeArtBank().tile(0x5A8 + tile)),
                    shown.patternVersions().get(BANKS.monitorTime() + tile));
            assertEquals(82, objects.monitorTimeArtBank().binding(
                    com.openggf.game.soniccd.level.CdR11aAsset.ART_MONITOR_TIME_POSTS).bytes().length() / 32);
        }
    }

    @Test void monitorNonzeroRewardSavedFlagsAndItemWordRestoreAndReplayTheNextActualDisplay1319() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            pool.write(monitorItemImage(f, pool.state(40), 2, 4, 0x100, 0x740).moveWord(Y_VELOCITY, 0));
            pool.write(monitorImage(f, pool.state(41), 4, 0x84, 4, 0x140, 0x780, 7).moveByte(ANIMATION, 4));
            f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f);
            var actor = f.frame.captureActor(); var shared = f.frame.captureShared(); var objects = f.runtime.captureClosedCut();
            var cpu = f.runtime.cpuSat(); var picture = f.runtime.displayedFrame();
            assertEquals(1320, actor.state().timers().speedShoes()); assertEquals(29, f.slot(40).word(MONITOR_ITEM_DELETE));
            assertEquals(1, f.runtime.placement().savedFlag(7, 1)); assertEquals(0x1A, f.slot(32).id());
            int publications = f.artPublications.size();
            monitorBegin(f, 3); assertEquals(1319, f.frame.state().timers().speedShoes());
            f.runtime.runDynamicSlotsAfterReservedSlots(); var nextCpu = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            var nextActor = f.frame.captureActor(); var nextShared = f.frame.captureShared(); var next = f.runtime.captureClosedCut();
            var uploads = List.copyOf(f.artPublications.subList(publications, f.artPublications.size()));
            assertEquals(28, f.slot(40).word(MONITOR_ITEM_DELETE));
            f.service(0x80); // Held selector8 changes the displayed picture, no Body/timer; remains a closed capture cut.
            assertEquals(1319, f.frame.state().timers().speedShoes());
            int replayStart = f.artPublications.size();
            f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(objects);
            f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(objects);
            assertEquals(actor, f.frame.captureActor()); assertEquals(shared.environment(), f.frame.environment());
            assertSpinTunnelArt(shared.artState(), f.frame.artState()); assertEquals(shared.vIntClock(), f.frame.vIntClock());
            assertSame(cpu, f.runtime.cpuSat()); assertSame(picture, f.runtime.displayedFrame()); assertMonitorSnapshot(objects, f.runtime.captureClosedCut());
            monitorBegin(f, 3); f.runtime.runDynamicSlotsAfterReservedSlots(); var replay = f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
            assertEquals(nextActor, f.frame.captureActor()); assertEquals(nextShared.environment(), f.frame.environment());
            assertSpinTunnelArt(nextShared.artState(), f.frame.artState()); assertEquals(nextShared.vIntClock(), f.frame.vIntClock());
            assertArrayEquals(nextCpu.bytes(), replay.bytes()); assertEquals(nextCpu.provenance(), replay.provenance());
            assertMonitorSnapshot(next, f.runtime.captureClosedCut());
            var replayUploads = f.artPublications.subList(replayStart, f.artPublications.size()); assertEquals(uploads.size(), replayUploads.size());
            for (int i = 0; i < uploads.size(); i++) assertSpinTunnelArt(uploads.get(i), replayUploads.get(i));
        }
    }

    private static CdObjectState monitorImage(Fixture f, CdObjectState image, int routine, int sprite, int subtype,
                                               int x, int y, int saved) throws java.io.IOException {
        return image.moveByte(ID, 0x19).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, sprite).moveByte(SUBTYPE, subtype)
                .moveByte(SAVED_FLAGS_ID, saved).moveWord(X, x).moveWord(Y, y).moveWord(TILE, 0x5A8)
                .moveByte(WIDTH, 15).moveByte(Y_RADIUS, 14).moveByte(X_RADIUS, 14).moveByte(PRIORITY, 3)
                .moveLong(MAPPINGS, CdMonitor.Assets.fromObjectAssets(f.assets.openingObjects()).mappingsAddress());
    }

    private static CdObjectState monitorItemImage(Fixture f, CdObjectState image, int routine, int animation,
                                                   int x, int y) throws java.io.IOException {
        var assets = CdMonitor.Assets.fromObjectAssets(f.assets.openingObjects());
        int pointer = assets.mappingsAddress() + (short) assets.visual().mappings().unsignedWord((animation * 2) & 255) + 1;
        return image.moveByte(ID, 0x1A).moveByte(ROUTINE, routine).moveByte(ANIMATION, animation).moveByte(MAP_FRAME, animation)
                .moveByte(SPRITE_FLAGS, 0x24).moveWord(TILE, 0x85A8).moveByte(PRIORITY, 3).moveByte(WIDTH, 8)
                .moveLong(MAPPINGS, pointer).moveWord(X, x).moveWord(Y, y);
    }

    /** Actual closed Frame ingress, never a second global owner or a media/zone execution certificate. */
    private static void monitorEnvironment(Fixture f, int rawZone, int direction, int zone, int timeAttack) {
        var shared = f.frame.captureShared(); var e = shared.environment(); var m = e.movement(); var g = m.gates(); var b = e.globals();
        var gates = new CdPlayerMovement.Gates((zone << 8) | (g.zoneAct() & 255), rawZone, g.miniSonic(), g.waterSlide(),
                g.windTunnel(), g.speedShoes(), direction);
        var body = new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), timeAttack, b.debugMode(), b.debugCheat(),
                b.timeWarpTimer(), b.invincible(), b.bossMusic());
        f.frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), new CdPlayerBody.Environment(
                new CdPlayerMovement.Environment(m.physics(), m.bounds(), gates, m.globals()), e.controls(), body, e.dynamicSlots()),
                shared.artState(), shared.paused(), shared.vIntClock()));
    }

    private static void monitorBegin(Fixture f, int levelFrames) {
        f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(levelFrames);
    }

    private static void monitorFinish(Fixture f) { f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail(); }

    private static void assertMonitorFiveStores(Fixture f, CdPlayerBody.State before, CdPlayerBody.Environment env) {
        var t = before.timers(); var m = env.movement(); var g = m.gates();
        assertEquals(new CdPlayerBody.State(before.motion(), before.animation(), before.header(),
                new CdPlayerBody.Timers(t.hurt(), t.invincibility(), 1320, t.reset()), before.history(), before.rotationDistance(), before.objectLinks()), f.frame.state());
        assertEquals(new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0xC00, 0x18, 0x80), m.bounds(),
                new CdPlayerMovement.Gates(g.zoneAct(), g.timeZone(), g.miniSonic(), g.waterSlide(), g.windTunnel(), 1, g.timeWarpDirection()), m.globals()),
                env.controls(), env.globals(), env.dynamicSlots()), f.frame.environment());
    }

    private static CdObjectPresentation.PatternSource monitorPixels(Fixture f) throws java.io.IOException {
        var objects = f.assets.openingObjects();
        return CdObjectPresentation.usaR11aMonitorTimeDevPreloadedArt(f.frame.artState(), f.runtime.powerupArtState(), objects,
                objects.frontierArtBank(), objects.contactArtBank(), objects.floatBlockArtBank(), objects.springArtBank(),
                objects.tunnelDoorArtBank(), objects.monitorTimeArtBank(), BANKS.player(), BANKS.ring(), BANKS.mosqui(), BANKS.plant(), BANKS.ramp(),
                BANKS.explosion(), BANKS.points(), BANKS.flower(), BANKS.powerup(), BANKS.floatBlock(), BANKS.spring(), BANKS.tunnelDoorVertical(), BANKS.monitorTime());
    }

    private static void assertMonitorSnapshot(CdOpeningObjectRuntime.Snapshot expected, CdOpeningObjectRuntime.Snapshot actual) {
        assertEquals(expected.placement().status(), actual.placement().status());
        assertEquals(expected.placement().pool().slots(), actual.placement().pool().slots());
        assertArrayEquals(expected.placement().savedFlags().copy(), actual.placement().savedFlags().copy());
        assertEquals(expected.ringGlobals(), actual.ringGlobals()); assertEquals(expected.spillGlobals(), actual.spillGlobals());
        assertEquals(expected.devGlobals(), actual.devGlobals()); assertEquals(expected.contactGlobals(), actual.contactGlobals());
        assertEquals(expected.scoreGlobals(), actual.scoreGlobals()); assertEquals(expected.levelGlobals(), actual.levelGlobals());
        assertEquals(expected.lastSoundRequests(), actual.lastSoundRequests()); assertEquals(expected.displayedFrame(), actual.displayedFrame());
        assertArrayEquals(expected.powerupArtState().cpuBuffer().copy(), actual.powerupArtState().cpuBuffer().copy());
        assertArrayEquals(expected.powerupArtState().residentBank().copy(), actual.powerupArtState().residentBank().copy());
    }
    // END Monitor Runtime R1 tests-first additions.

    // BEGIN Time Post Runtime R1 tests-first additions. ALL authored Java is UNRUN here.
    // Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Monitor and Time Post/Main.asm:64-236,
    // Data/Animations.asm:45-50, Data/Mappings.asm:103-155, Object Spawner.asm:385-446.
    // Every pool/actor/global edit below is explicit constructed ingress over the inherited real-media
    // harness. SLOT_ABSENT cycles isolate object dispatch; they never claim a normal World warp route.
    // OpenAI Codex GPT-6.1 Sol <noreply@openai.com>.

    // Catches routing subtype8/9 through Monitor, a duplicate saved bank, eager bit7, and wrong post mapping.
    @Test void timePostBothLiveInitBranchesUseSavedBankAndRealFivePieceSpriteVisibility() throws Exception {
        for (int subtype : List.of(8, 9)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); var placement = f.runtime.placement();
            var before = timePostImage(f, pool.state(40), 0, subtype, 7, 0)
                    .moveByte(SPRITE_FLAGS, 0xFF).moveByte(Y_RADIUS, 0x11).moveByte(X_RADIUS, 0x22)
                    .moveByte(WIDTH, 0x33).moveByte(PRIORITY, 0x7F).moveWord(TILE, 0x2345)
                    .moveByte(ANIMATION, 0x44).moveByte(MAP_FRAME, 0x55).moveByte(COLLISION_TYPE, 0x66);
            pool.write(before); placement.bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
            placement.bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.MEMBER_2);
            var actor = f.frame.state(); var environment = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            var expected = before.moveByte(ROUTINE, 2).moveByte(Y_RADIUS, 0x20).moveByte(X_RADIUS, 0xE)
                    .moveWord(TILE, 0x5A8).moveByte(SPRITE_FLAGS, 4).moveByte(PRIORITY, 3).moveByte(WIDTH, 15)
                    .moveByte(ANIMATION, subtype).moveByte(MAP_FRAME, subtype == 8 ? 10 : 12).moveByte(COLLISION_TYPE, 0xDF);
            assertEquals(expected, f.slot(40)); assertEquals(2, placement.savedFlag(7, 1));
            assertEquals(actor, f.frame.state()); assertEquals(environment, f.frame.environment());
            assertEquals(sounds, f.runtime.lastSoundRequests());
            var sat = f.runtime.finishAfterCamera(POST); int at = satEntry(sat, 40);
            assertEquals(5, sat.provenance().stream().filter(p -> p.slotRef().wholeIndex() == 40).count());
            assertEquals(0xCD, word(sat.bytes(), at)); assertEquals(0xD, sat.bytes()[at + 2] & 255);
            assertEquals(subtype == 8 ? 0x5DA : 0x5E2, word(sat.bytes(), at + 4));
            assertEquals(0x170, word(sat.bytes(), at + 6)); assertEquals(0x84, f.slot(40).spriteFlags());
            f.runtime.finishOrdinaryTail(); f.service(0x80);
            assertEquals(CdObjectPresentation.convert(sat, monitorPixels(f)), f.runtime.displayedFrame());
        }
    }

    // Activated Init returns to the wrapper, not Main; dirty collision and $2A are not consumed.
    @Test void timePostActivatedInitReturnsToWrapperWithoutConsumingDirtyContact() throws Exception {
        for (int subtype : List.of(8, 9)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); var placement = f.runtime.placement();
            var before = timePostImage(f, pool.state(40), 0, subtype, 7, 0x55).moveByte(COLLISION_TYPE, 0x66);
            pool.write(before);
            for (var bit : List.of(CdObjectPlacementRuntime.SavedBit.LOADED, CdObjectPlacementRuntime.SavedBit.MEMBER_1,
                    CdObjectPlacementRuntime.SavedBit.MEMBER_2)) placement.bsetSavedFlag(7, 1, bit);
            var actor = f.frame.state(); var environment = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(before.moveByte(ROUTINE, 6).moveByte(SPRITE_FLAGS, 4)
                    .moveByte(MAP_FRAME, subtype == 8 ? 11 : 13), f.slot(40));
            assertEquals(3, placement.savedFlag(7, 1)); assertEquals(actor, f.frame.state());
            assertEquals(environment, f.frame.environment()); assertEquals(sounds, f.runtime.lastSoundRequests());
            var sat = f.runtime.finishAfterCamera(POST); int at = satEntry(sat, 40);
            assertEquals(subtype == 8 ? 0xDDA : 0xDE2, word(sat.bytes(), at + 4));
            f.runtime.finishOrdinaryTail();
        }
    }

    // Dirty contact may activate during Init fallthrough, but activation itself never spends spin call1.
    @Test void timePostDirtyInitActivatesBeforeDrawWithoutSpinningOnThatCall() throws Exception {
        for (int subtype : List.of(8, 9)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            var before = timePostImage(f, pool.state(40), 0, subtype, 7, 3).moveByte(COLLISION_TYPE, 0x66);
            pool.write(before); f.runtime.placement().bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
            var actor = f.frame.state(); var environment = f.frame.environment(); var display = f.frame.display();
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            sounds.add(new CdOpeningObjectRuntime.TimePostSubCpu(subtype == 8
                    ? CdTimePost.SubCpuCommand.SCMD_PASTSFX : CdTimePost.SubCpuCommand.SCMD_FUTURESFX));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(before.moveByte(ROUTINE, 4).moveByte(SPRITE_FLAGS, 4).moveByte(COLLISION_STATUS, 0)
                    .moveByte(TIME_POST_SPIN, 60).moveByte(COLLISION_TYPE, 0xDF), f.slot(40));
            assertEquals(1, f.runtime.placement().savedFlag(7, 1)); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertTimePostOnlyDirection(f, actor, environment, subtype == 8 ? 0xFF : 1);
            assertEquals(display, f.frame.display()); monitorFinish(f);
            assertEquals(sounds, f.runtime.lastSoundRequests(), "completed SubCpuRequest metadata is never forwarded again");
        }
    }

    // Removing real SPECIAL admission/commit, gating it on hurt/contact globals, or delaying activation breaks this.
    @Test void timePostRealSpecialContactCommitsStatusThenActivatesInSamePassDespiteHandlerGlobals() throws Exception {
        var initial = new CdRingGlobals(17, 4, 0xA5, 0x5A, 0x91, 0xBEEF, 3, 2);
        var contact = new CdPlayerBody.ContactGlobals(0xFF, 0xA5, 0x5A);
        var score = new CdOpeningObjectRuntime.ScoreGlobals(0x12345678, 0x23456789, 0xA5);
        for (int subtype : List.of(8, 9)) for (int status : List.of(0, 0xFE))
                try (var f = new Fixture(initial, contact, score)) {
            f.firstCycle(); var pool = f.runtime.placement().pool(); pool.clearDynamicSlots(); timePostBodyIngress(f);
            var before = timePostImage(f, pool.state(40), 2, subtype, 7, status).moveWord(Y, 0x710);
            pool.write(before); var rings = f.runtime.ringGlobals();
            f.service(0); f.runtime.spawnBeforeReservedSlots(); var publication = f.body(2);
            var response = publication.result().effects().stream().filter(CdPlayerBody.ObjectTouchResponse.class::isInstance)
                    .map(CdPlayerBody.ObjectTouchResponse.class::cast).findFirst().orElseThrow().result();
            assertEquals(CdPlayerTouchResponse.Exit.SPECIAL_FLAG_INCREMENT, response.exit());
            assertEquals(List.of(before.moveByte(COLLISION_STATUS, status + 1)), response.changedSlots());
            assertEquals(response.changedSlots().getFirst(), f.slot(40)); assertTrue(response.calls().isEmpty()); assertTrue(response.d0().isEmpty());
            assertEquals(0x122, response.actor().hurt()); assertEquals(0xFF, response.player().timeWarp());
            assertEquals(0xA5, response.player().invincible()); assertEquals(0xA5, response.player().shield());
            assertEquals(0x5A, response.player().combineRing()); assertEquals(0x12, response.player().debugMode());
            assertEquals(0xABCD, response.player().debugCheat());
            assertEquals(new CdPlayerTouchResponse.WorldGlobals(rings.rings(), rings.lives(), rings.updateHudLives(),
                    score.score(), score.nextLifeScore(), score.updateHudScore()), response.world());
            var actor = f.frame.state(); var environment = f.frame.environment(); var display = f.frame.display();
            var sounds = new ArrayList<CdOpeningObjectRuntime.SoundRequest>();
            publication.sounds().forEach(s -> sounds.add(new CdOpeningObjectRuntime.PlayerSound(s)));
            sounds.add(new CdOpeningObjectRuntime.TimePostSubCpu(subtype == 8
                    ? CdTimePost.SubCpuCommand.SCMD_PASTSFX : CdTimePost.SubCpuCommand.SCMD_FUTURESFX));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(before.moveByte(COLLISION_STATUS, 0).moveByte(TIME_POST_SPIN, 60)
                    .moveByte(ROUTINE, 4).moveByte(SPRITE_FLAGS, 4), f.slot(40));
            assertTimePostOnlyDirection(f, actor, environment, subtype == 8 ? 0xFF : 1); assertEquals(display, f.frame.display());
            assertEquals(rings, f.runtime.ringGlobals()); assertEquals(contact, f.runtime.contactGlobals());
            assertEquals(score, f.runtime.scoreGlobals()); assertEquals(1, f.runtime.placement().savedFlag(7, 1));
            assertEquals(sounds, f.runtime.lastSoundRequests()); monitorFinish(f); assertEquals(sounds, f.runtime.lastSoundRequests());
        }
    }

    // ADDQ.B wraps $FF to zero; a nonzero-input boolean shortcut would wrongly activate this post.
    @Test void timePostSpecialStatusWrapToZeroLeavesMainUnactivated() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); var pool = f.runtime.placement().pool(); pool.clearDynamicSlots(); timePostBodyIngress(f);
            var before = timePostImage(f, pool.state(40), 2, 8, 7, 0xFF).moveWord(Y, 0x710); pool.write(before);
            f.service(0); f.runtime.spawnBeforeReservedSlots(); var publication = f.body(2);
            var response = publication.result().effects().stream().filter(CdPlayerBody.ObjectTouchResponse.class::isInstance)
                    .map(CdPlayerBody.ObjectTouchResponse.class::cast).findFirst().orElseThrow().result();
            assertEquals(CdPlayerTouchResponse.Exit.SPECIAL_FLAG_INCREMENT, response.exit());
            assertEquals(before.moveByte(COLLISION_STATUS, 0), f.slot(40));
            var actor = f.frame.state(); var environment = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(before.moveByte(COLLISION_STATUS, 0).moveByte(SPRITE_FLAGS, 4), f.slot(40));
            assertEquals(actor, f.frame.state()); assertEquals(environment, f.frame.environment());
            assertEquals(0, f.runtime.placement().savedFlag(7, 1)); assertEquals(sounds, f.runtime.lastSoundRequests()); monitorFinish(f);
        }
    }

    // Real Flower scratch callbacks observe physical ordering; no callback stands in for either post owner.
    @Test void timePostPastThenFuturePublishOrderedVoicesAtLivePhysicalCutsAndReadChangedSubtype() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); monitorEnvironment(f, 1, 0x80, 0, 0);
            pool.write(timePostImage(f, pool.state(40), 2, 4, 7, 1)); // subtype changes BEFORE this actual visit.
            pool.write(timePostImage(f, pool.state(42), 2, 9, 8, 1));
            for (int whole : List.of(39, 41, 43)) pool.write(pool.state(whole).moveByte(ID, 0x1F)
                    .moveByte(SUBTYPE, 1).moveByte(SUBTYPE2, 1).moveWord(X, 64).moveWord(Y, 0x720));
            var initial = List.copyOf(f.runtime.lastSoundRequests()); var observed = new int[1];
            f.onAngle = value -> {
                int visit = observed[0]++;
                if (visit == 0) {
                    assertEquals(0x80, f.frame.environment().movement().gates().timeWarpDirection());
                    assertEquals(initial, f.runtime.lastSoundRequests()); pool.write(pool.state(40).moveByte(SUBTYPE, 8));
                } else {
                    assertEquals(visit == 1 ? 0xFF : 1, f.frame.environment().movement().gates().timeWarpDirection());
                    var expected = new ArrayList<>(initial);
                    expected.add(new CdOpeningObjectRuntime.TimePostSubCpu(CdTimePost.SubCpuCommand.SCMD_PASTSFX));
                    if (visit == 2) expected.add(new CdOpeningObjectRuntime.TimePostSubCpu(CdTimePost.SubCpuCommand.SCMD_FUTURESFX));
                    assertEquals(expected, f.runtime.lastSoundRequests());
                    assertEquals(4, f.slot(40).routine()); assertEquals(60, f.slot(40).unsignedByte(TIME_POST_SPIN));
                    assertEquals(0, f.slot(40).unsignedByte(COLLISION_STATUS));
                }
            };
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertEquals(3, observed[0]);
            assertEquals(1, f.runtime.placement().savedFlag(7, 1)); assertEquals(1, f.runtime.placement().savedFlag(8, 1));
            assertEquals(4, f.slot(42).routine()); assertEquals(60, f.slot(42).unsignedByte(TIME_POST_SPIN));
            var sounds = f.runtime.lastSoundRequests(); assertEquals(initial.size() + 2, sounds.size()); monitorFinish(f);
            assertEquals(sounds, f.runtime.lastSoundRequests());
        }
    }

    // Raw $81 selects the activation saved byte with ENTRY direction, then despawn uses the committed final byte.
    @Test void timePostRawDirectionAndWarpPeriodStoresUseOneActualSavedBankBeforeDespawn() throws Exception {
        int[][] rows = {{0, 1}, {1, 0}, {0x80, 2}, {0xFF, 2}};
        for (int subtype : List.of(8, 9)) for (int[] row : rows) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); var placement = f.runtime.placement();
            monitorEnvironment(f, 0x81, row[0], 0, 0);
            for (int period = 0; period < 3; period++) {
                placement.bsetSavedFlag(7, period, CdObjectPlacementRuntime.SavedBit.LOADED);
                placement.bsetSavedFlag(7, period, CdObjectPlacementRuntime.SavedBit.MEMBER_2);
            }
            pool.write(timePostImage(f, pool.state(40), 2, subtype, 7, 1));
            var actor = f.frame.state(); var environment = f.frame.environment(); var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            sounds.add(new CdOpeningObjectRuntime.TimePostSubCpu(subtype == 8
                    ? CdTimePost.SubCpuCommand.SCMD_PASTSFX : CdTimePost.SubCpuCommand.SCMD_FUTURESFX));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertArrayEquals(new byte[64], f.slot(40).copyBytes(), "wrapper deletes only AFTER the synchronous voice request");
            for (int period = 0; period < 3; period++) {
                int expected = period == row[1] ? 0x83 : 0x82;
                if (period == (subtype == 8 ? 2 : 0)) expected &= 0x7F;
                assertEquals(expected, placement.savedFlag(7, period));
            }
            assertTimePostOnlyDirection(f, actor, environment, subtype == 8 ? 0xFF : 1);
            assertEquals(sounds, f.runtime.lastSoundRequests()); var sat = f.runtime.finishAfterCamera(POST);
            assertTrue(sat.provenance().stream().noneMatch(p -> p.slotRef().wholeIndex() == 40));
            f.runtime.finishOrdinaryTail(); assertEquals(sounds, f.runtime.lastSoundRequests());
        }
    }

    @Test void timePostDirectionVoiceAndSavedPrefixSurviveLaterUnknownIdWithoutRetry() throws Exception {
        for (int subtype : List.of(8, 9)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); monitorEnvironment(f, 1, 0xFF, 0, 0);
            var before = timePostImage(f, pool.state(40), 2, subtype, 7, 0xFF); pool.write(before);
            pool.write(pool.state(42).moveByte(ID, 0x7E)); var actor = f.frame.state(); var environment = f.frame.environment();
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests()); sounds.add(new CdOpeningObjectRuntime.TimePostSubCpu(subtype == 8
                    ? CdTimePost.SubCpuCommand.SCMD_PASTSFX : CdTimePost.SubCpuCommand.SCMD_FUTURESFX));
            var error = assertThrows(UnsupportedOperationException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertTrue(error.getMessage().contains("42"));
            var prefix = before.moveByte(ROUTINE, 4).moveByte(TIME_POST_SPIN, 60).moveByte(COLLISION_STATUS, 0).moveByte(SPRITE_FLAGS, 4);
            assertEquals(prefix, f.slot(40)); assertEquals(1, f.runtime.placement().savedFlag(7, 1));
            assertTimePostOnlyDirection(f, actor, environment, subtype == 8 ? 0xFF : 1); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(prefix, f.slot(40)); assertEquals(sounds, f.runtime.lastSoundRequests());
        }
    }

    @Test void timePostCompletedVoicePrecedesLaterSatMappingFailureAndIsNeverReplayed() throws Exception {
        for (int subtype : List.of(8, 9)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            var before = timePostImage(f, pool.state(40), 2, subtype, 7, 1).moveLong(MAPPINGS, 0x00FFFFFF); pool.write(before);
            var actor = f.frame.state(); var environment = f.frame.environment(); var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            sounds.add(new CdOpeningObjectRuntime.TimePostSubCpu(subtype == 8
                    ? CdTimePost.SubCpuCommand.SCMD_PASTSFX : CdTimePost.SubCpuCommand.SCMD_FUTURESFX));
            f.runtime.runDynamicSlotsAfterReservedSlots(); var prefix = f.slot(40);
            assertEquals(sounds, f.runtime.lastSoundRequests()); assertEquals(60, prefix.unsignedByte(TIME_POST_SPIN));
            var error = assertThrows(UnsupportedOperationException.class, () -> f.runtime.finishAfterCamera(POST));
            assertTrue(error.getMessage().contains("slot 40")); assertEquals(prefix, f.slot(40));
            assertEquals(1, f.runtime.placement().savedFlag(7, 1)); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertTimePostOnlyDirection(f, timePostDrawDoneActor(actor), environment, subtype == 8 ? 0xFF : 1);
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class, () -> f.runtime.finishAfterCamera(POST)); assertEquals(sounds, f.runtime.lastSoundRequests());
        }
    }

    // This remains an actual primary Main; no charge seed, absent actor or direction clear bypasses TimeWarp.
    @Test void timePostActivationCompletesThisPassThenNextActualMainStopsAtTimeWarpActive() throws Exception {
        for (int subtype : List.of(8, 9)) try (var f = new Fixture()) {
            f.firstCycle(); var pool = f.runtime.placement().pool(); pool.clearDynamicSlots(); timePostBodyIngress(f);
            pool.write(timePostImage(f, pool.state(40), 2, subtype, 7, 0).moveWord(Y, 0x710));
            f.service(0); f.runtime.spawnBeforeReservedSlots(); var body = f.body(2);
            assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, body.result().boundary());
            f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f); var post = f.slot(40);
            assertEquals(60, post.unsignedByte(TIME_POST_SPIN)); assertEquals(4, post.routine());
            assertEquals(List.of(new CdOpeningObjectRuntime.TimePostSubCpu(subtype == 8
                    ? CdTimePost.SubCpuCommand.SCMD_PASTSFX : CdTimePost.SubCpuCommand.SCMD_FUTURESFX)), f.runtime.lastSoundRequests());
            f.service(0); f.runtime.spawnBeforeReservedSlots(); var actor = f.frame.state(); var environment = f.frame.environment();
            var error = assertThrows(CdPlayerMovement.UnsupportedMotion.class, () -> f.body(3));
            assertEquals(CdPlayerMovement.UnsupportedBoundary.TIME_WARP_ACTIVE, error.boundary());
            assertEquals(actor, f.frame.state()); assertEquals(environment, f.frame.environment()); assertEquals(post, f.slot(40));
            assertEquals(subtype == 8 ? 0xFF : 1, environment.movement().gates().timeWarpDirection());
            assertEquals(1, f.runtime.placement().savedFlag(7, 1)); assertTrue(f.runtime.lastSoundRequests().isEmpty());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed(), "the outer Runtime owns failed-Body closure");
            assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots); assertEquals(post, f.slot(40));
        }
    }

    // Explicit absent-primary component scheduling only: all 60 calls retain the nonzero direction and time-stop word.
    @Test void timePostSixtyAbsentPrimaryObjectCallsPreserveDirectionAndNativeAnimationDelayUntilDone() throws Exception {
        var initial = new CdRingGlobals(0, 3, 0, 0, 0, 0xBEEF, 0, 0);
        for (int subtype : List.of(8, 9)) try (var f = new Fixture(initial)) {
            springDeviceCut(f); f.makePlayerAbsent(); var pool = f.runtime.placement().pool();
            pool.write(timePostImage(f, pool.state(40), 2, subtype, 7, 1));
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertEquals(60, f.slot(40).unsignedByte(TIME_POST_SPIN)); monitorFinish(f);
            int[] sequence = subtype == 8 ? new int[] {10,10,14,14,15,15,14,14,11,11,14,14,15,15,14,14}
                    : new int[] {12,12,14,14,15,15,14,14,13,13,14,14,15,15,14,14};
            for (int call = 1; call <= 60; call++) {
                timePostAbsentBegin(f, call + 2); f.runtime.runDynamicSlotsAfterReservedSlots();
                assertEquals(60 - call, f.slot(40).unsignedByte(TIME_POST_SPIN));
                assertEquals(((60 - call) << 8) | 0xCD, f.slot(40).word(MOSQUI_ANCHOR_X));
                assertEquals(call == 60 ? 6 : 4, f.slot(40).routine());
                assertEquals(call == 60 ? (subtype == 8 ? 11 : 13) : sequence[(call - 1) % 16], f.slot(40).unsignedByte(MAP_FRAME));
                assertEquals(subtype == 8 ? 0xFF : 1, f.frame.environment().movement().gates().timeWarpDirection());
                assertEquals(0xBEEF, f.runtime.ringGlobals().timeStopTimer()); assertTrue(f.runtime.lastSoundRequests().isEmpty()); monitorFinish(f);
            }
            var done = f.slot(40); timePostAbsentBegin(f, 63); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(done.moveByte(SPRITE_FLAGS, 4), f.slot(40)); monitorFinish(f);
        }
    }

    @Test void timePostNonzeroDirectionSpinSavedSoundAndAllExistingPartitionsRestoreAndReplay() throws Exception {
        for (int subtype : List.of(8, 9)) try (var f = new Fixture()) {
            springDeviceCut(f); f.makePlayerAbsent(); var pool = f.runtime.placement().pool();
            pool.write(timePostImage(f, pool.state(40), 2, subtype, 7, 1));
            f.runtime.placement().bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.MEMBER_2);
            f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f);
            var actor = f.frame.captureActor(); var shared = f.frame.captureShared(); var objects = f.runtime.captureClosedCut();
            var cpu = f.runtime.cpuSat(); var picture = f.runtime.displayedFrame();
            assertEquals(0, actor.state().header().slotId()); assertEquals(60, f.slot(40).unsignedByte(TIME_POST_SPIN));
            assertEquals(subtype == 8 ? 0xFF : 1, shared.environment().movement().gates().timeWarpDirection());
            assertEquals(3, f.runtime.placement().savedFlag(7, 1));
            // MODE's completed ChargeStop precedes the completed Time Post voice on this same frame.
            assertEquals(List.of(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ChargeSound(
                            com.openggf.game.soniccd.player.CdPlayerCharge.Sound.FM_CHARGESTOP)),
                    new CdOpeningObjectRuntime.TimePostSubCpu(subtype == 8
                            ? CdTimePost.SubCpuCommand.SCMD_PASTSFX : CdTimePost.SubCpuCommand.SCMD_FUTURESFX)),
                    objects.lastSoundRequests());
            int publicationStart = f.artPublications.size();
            for (int call = 1; call <= 7; call++) { timePostAbsentBegin(f, call + 2); f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f); }
            var nextActor = f.frame.captureActor(); var nextShared = f.frame.captureShared(); var next = f.runtime.captureClosedCut();
            var nextCpu = f.runtime.cpuSat(); var uploads = List.copyOf(f.artPublications.subList(publicationStart, f.artPublications.size()));
            assertEquals(53, f.slot(40).unsignedByte(TIME_POST_SPIN)); f.service(0x80); int replayStart = f.artPublications.size();
            f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(objects);
            f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(objects);
            assertEquals(actor, f.frame.captureActor()); assertEquals(shared.environment(), f.frame.environment());
            assertSpinTunnelArt(shared.artState(), f.frame.artState()); assertEquals(shared.vIntClock(), f.frame.vIntClock());
            assertSame(cpu, f.runtime.cpuSat()); assertSame(picture, f.runtime.displayedFrame()); assertMonitorSnapshot(objects, f.runtime.captureClosedCut());
            for (int call = 1; call <= 7; call++) { timePostAbsentBegin(f, call + 2); f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f); }
            assertEquals(nextActor, f.frame.captureActor()); assertEquals(nextShared.environment(), f.frame.environment());
            assertSpinTunnelArt(nextShared.artState(), f.frame.artState()); assertEquals(nextShared.vIntClock(), f.frame.vIntClock());
            assertArrayEquals(nextCpu.bytes(), f.runtime.cpuSat().bytes()); assertEquals(nextCpu.provenance(), f.runtime.cpuSat().provenance());
            assertMonitorSnapshot(next, f.runtime.captureClosedCut());
            var replayUploads = f.artPublications.subList(replayStart, f.artPublications.size()); assertEquals(uploads.size(), replayUploads.size());
            for (int i = 0; i < uploads.size(); i++) assertSpinTunnelArt(uploads.get(i), replayUploads.get(i));
        }
    }

    @Test void timePostTimeAttackDeletesHudSubtypeBeforeAnyEntryOrMissingMmzRead() throws Exception {
        for (int subtype : List.of(8, 9, 0xA, 0xFF)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); monitorEnvironment(f, 0x7F, 0x80, 6, 0xFF);
            pool.write(timePostImage(f, pool.state(40), 0xFF, subtype, 0xFF, 0x55).moveByte(LAYER, 0xA5));
            var saved = f.runtime.placement().capture().savedFlags().copy(); var actor = f.frame.state();
            var environment = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertArrayEquals(new byte[64], f.slot(40).copyBytes());
            assertArrayEquals(saved, f.runtime.placement().capture().savedFlags().copy());
            assertEquals(actor, f.frame.state()); assertEquals(environment, f.frame.environment()); assertEquals(sounds, f.runtime.lastSoundRequests());
            var sat = f.runtime.finishAfterCamera(POST); assertTrue(sat.provenance().stream().noneMatch(p -> p.slotRef().wholeIndex() == 40));
            f.runtime.finishOrdinaryTail();
        }
    }

    @Test void timePostHudSubtypeRemainsTypedAndNeverCreatesReservedSlot31() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); var before = timePostImage(f, pool.state(40), 0, 0xA, 7, 1);
            pool.write(before); var saved = f.runtime.placement().capture().savedFlags().copy();
            var error = assertThrows(CdTimePost.UnsupportedTimePost.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(CdTimePost.UnsupportedBoundary.TIME_ICON_RESERVED_SLOT, error.boundary()); assertEquals(40, error.wholeIndex());
            assertEquals(before, f.slot(40)); assertArrayEquals(saved, f.runtime.placement().capture().savedFlags().copy());
            assertEquals(96, pool.capture().slots().size()); assertEquals(32, pool.capture().slots().getFirst().identity().wholeIndex());
            assertEquals(127, pool.capture().slots().getLast().identity().wholeIndex()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    // No Monitor MMZ preguard: Init's layer priority is owned, and only nonzero contact reaches the missing global.
    @Test void timePostMmzInitWithoutContactDrawsButContactClearsBeforeMissingLayerBoundary() throws Exception {
        for (int contact : List.of(0, 7)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); monitorEnvironment(f, 1, 0, 6, 0);
            var before = timePostImage(f, pool.state(40), 0, 8, 7, contact).moveByte(LAYER, 0); pool.write(before);
            f.runtime.placement().bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
            var actor = f.frame.state(); var environment = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            var prefix = before.moveByte(ROUTINE, 2).moveWord(TILE, 0x85A8).moveByte(PRIORITY, 0)
                    .moveByte(SPRITE_FLAGS, 4).moveByte(COLLISION_STATUS, 0);
            if (contact == 0) {
                f.runtime.runDynamicSlotsAfterReservedSlots(); assertEquals(prefix, f.slot(40));
                assertEquals(actor, f.frame.state()); assertEquals(environment, f.frame.environment());
                var sat = f.runtime.finishAfterCamera(POST); assertEquals(5, sat.provenance().stream().filter(p -> p.slotRef().wholeIndex() == 40).count());
                f.runtime.finishOrdinaryTail(); assertFalse(f.runtime.closed());
            } else {
                var error = assertThrows(CdTimePost.UnsupportedTimePost.class, f.runtime::runDynamicSlotsAfterReservedSlots);
                assertEquals(CdTimePost.UnsupportedBoundary.MMZ_LAYER_READ, error.boundary()); assertEquals(prefix, f.slot(40));
                assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
                assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots); assertEquals(prefix, f.slot(40));
            }
            assertEquals(0, f.runtime.placement().savedFlag(7, 1)); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertEquals(contact == 0 ? timePostDrawDoneActor(actor) : actor, f.frame.state());
            assertEquals(environment, f.frame.environment());
        }
    }

    @Test void timePostMmzActivatedInitSkipsMissingLayerAndKeepsContactAndCollision() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); monitorEnvironment(f, 1, 0x80, 6, 0);
            var before = timePostImage(f, pool.state(40), 0, 9, 7, 0xFF).moveByte(LAYER, 0xA5).moveByte(COLLISION_TYPE, 0x66);
            pool.write(before); f.runtime.placement().bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.MEMBER_1);
            var environment = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(before.moveByte(ROUTINE, 6).moveByte(MAP_FRAME, 13).moveByte(SPRITE_FLAGS, 4), f.slot(40));
            assertEquals(environment, f.frame.environment()); assertEquals(sounds, f.runtime.lastSoundRequests()); monitorFinish(f);
        }
    }

    // Real earlier/later tag despawn owners use $81 minus signed direction; the two saved rows must differ.
    @Test void timePostEarlierAndLaterPhysicalTagsReadActualCurrentWarpDirection() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); var placement = f.runtime.placement();
            monitorEnvironment(f, 0x81, 0x80, 0, 0);
            for (int saved : List.of(11, 12)) for (int period = 0; period < 3; period++) {
                placement.bsetSavedFlag(saved, period, CdObjectPlacementRuntime.SavedBit.LOADED);
                placement.bsetSavedFlag(saved, period, CdObjectPlacementRuntime.SavedBit.MEMBER_2);
            }
            pool.write(spinTunnelTag(f, pool.state(39), 2, 4, 0, 0x600, 0x780, 11));
            pool.write(timePostImage(f, pool.state(40), 2, 9, 7, 1));
            pool.write(spinTunnelTag(f, pool.state(41), 2, 4, 0, 0x600, 0x780, 12));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(0x82, placement.savedFlag(11, 0)); assertEquals(0x82, placement.savedFlag(11, 1)); assertEquals(2, placement.savedFlag(11, 2));
            assertEquals(2, placement.savedFlag(12, 0)); assertEquals(0x82, placement.savedFlag(12, 1)); assertEquals(0x82, placement.savedFlag(12, 2));
            assertEquals(1, f.frame.environment().movement().gates().timeWarpDirection());
            assertEquals(List.of(0, 0, 0), List.of(f.slot(39).id(), f.slot(40).id(), f.slot(41).id())); monitorFinish(f);
        }
    }

    @Test void timePostLaterLegacyCallbackFailureRetainsEarlierPrefixAndClosesWithoutRetry() throws Exception {
        for (boolean swallow : List.of(false, true)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            pool.write(timePostImage(f, pool.state(40), 2, 9, 7, 1));
            pool.write(pool.state(41).moveByte(ID, 0x1F).moveByte(SUBTYPE, 1).moveWord(X, 64).moveWord(Y, 0x720));
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            sounds.add(new CdOpeningObjectRuntime.TimePostSubCpu(CdTimePost.SubCpuCommand.SCMD_FUTURESFX));
            f.onAngle = value -> {
                assertEquals(1, f.frame.environment().movement().gates().timeWarpDirection()); assertEquals(sounds, f.runtime.lastSoundRequests());
                if (swallow) assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
                else throw new IllegalStateException("constructed later legacy callback failure");
            };
            assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(4, f.slot(40).routine()); assertEquals(60, f.slot(40).unsignedByte(TIME_POST_SPIN));
            assertEquals(0, f.slot(40).unsignedByte(COLLISION_STATUS)); assertEquals(1, f.runtime.placement().savedFlag(7, 1));
            assertEquals(sounds, f.runtime.lastSoundRequests()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots); assertEquals(sounds, f.runtime.lastSoundRequests());
        }
    }

    @Test void timePostDistanceDespawnUsesActualPreScrollCameraAndOnlyClearsLoadedBit() throws Exception {
        for (int cameraX : List.of(0, 0x600)) try (var f = new Fixture()) {
            f.firstCycle(); f.makePlayerAbsent(); var camera = new CameraWords(cameraX, PRE.yWord());
            f.service(0, camera); f.runtime.spawnBeforeReservedSlots(); f.body(2); var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(timePostImage(f, pool.state(40), 6, 8, 7, 0).moveWord(X, 0x600).moveByte(MAP_FRAME, 11));
            f.runtime.placement().bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
            f.runtime.placement().bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.MEMBER_1);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(cameraX == 0 ? 0 : 0x19, f.slot(40).id());
            assertEquals(cameraX == 0 ? 1 : 0x81, f.runtime.placement().savedFlag(7, 1));
            assertEquals(0, f.frame.environment().movement().gates().timeWarpDirection());
            f.runtime.finishAfterCamera(new DrawCameras(camera, camera, camera)); f.runtime.finishOrdinaryTail();
        }
    }

    /** Dirty but source-bound constructed post image; Monitor assets and the real shared 64-byte slot are reused. */
    private static CdObjectState timePostImage(Fixture f, CdObjectState image, int routine, int subtype, int saved, int contact)
            throws java.io.IOException {
        return monitorImage(f, image, routine, 0x84, subtype, 0x100, 0x780, saved)
                .moveByte(Y_RADIUS, 0x20).moveByte(X_RADIUS, 0xE).moveByte(ANIMATION, subtype)
                .moveByte(MAP_FRAME, subtype == 8 ? 10 : 12).moveByte(COLLISION_TYPE, 0xDF).moveByte(COLLISION_STATUS, contact)
                .moveByte(PREVIOUS_ANIMATION, 0xA5).moveByte(ANIMATION_FRAME, 0x71).moveByte(ANIMATION_TIME, 0x67)
                .moveWord(MOSQUI_ANCHOR_X, 0xABCD).moveWord(X_FRACTION, 0x4321).moveWord(Y_FRACTION, 0x8765);
    }

    /** Contact/global ingress exercises the real handler; no time-warp direction or timer is seeded for spin. */
    private static void timePostBodyIngress(Fixture f) {
        seedSpinTunnelMotion(f, spinTunnelMotion(f, 0x01000000, 0x07000000, 0, 0x100, 0, 2, 0));
        var actor = f.frame.captureActor(); var s = actor.state();
        f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), new CdPlayerBody.State(s.motion(), s.animation(), s.header(),
                new CdPlayerBody.Timers(0x123, 0, 0, 0), s.history(), s.rotationDistance(), s.objectLinks()), Optional.empty()));
        var shared = f.frame.captureShared(); var e = shared.environment(); var b = e.globals();
        var globals = new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), 0x12, 0xABCD,
                b.timeWarpTimer(), 0xA5, 0xA6);
        f.frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(),
                new CdPlayerBody.Environment(e.movement(), e.controls(), globals, e.dynamicSlots()),
                shared.artState(), shared.paused(), shared.vIntClock()));
    }

    /** Native DrawObjects.DrawDone changes only player sprite bit7, including before a later bucket fails. */
    private static CdPlayerBody.State timePostDrawDoneActor(CdPlayerBody.State before) {
        var m = before.motion();
        var motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(),
                m.groundVelocity(), m.routine(), m.angle(), m.flags(), m.spriteFlags() | 0x80,
                m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(), m.playerJump(), m.charge(),
                m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        return new CdPlayerBody.State(motion, before.animation(), before.header(), before.timers(), before.history(),
                before.rotationDistance(), before.objectLinks());
    }

    private static void assertTimePostOnlyDirection(Fixture f, CdPlayerBody.State actor, CdPlayerBody.Environment before, int direction) {
        var m = before.movement(); var g = m.gates();
        var gates = new CdPlayerMovement.Gates(g.zoneAct(), g.timeZone(), g.miniSonic(), g.waterSlide(), g.windTunnel(), g.speedShoes(), direction);
        assertEquals(actor, f.frame.state());
        assertEquals(new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(), gates, m.globals()),
                before.controls(), before.globals(), before.dynamicSlots()), f.frame.environment());
    }

    private static void timePostAbsentBegin(Fixture f, int levelFrames) {
        assertEquals(0, f.frame.state().header().slotId(), "explicit absent primary isolates object dispatch; no World warp admission");
        f.service(0); f.runtime.spawnBeforeReservedSlots();
        assertEquals(CdPlayerBody.Boundary.SLOT_ABSENT, f.body(levelFrames).result().boundary());
    }
    // END Time Post Runtime R1 tests-first additions.

    // BEGIN horizontal Door Runtime R1 additions, authored before the sole production pass.
    // N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: LevelStart/.ClearObjects (Main.asm:186-192),
    // later omitted HUD/title owners (:261-274); ObjTunnelDoor (Spin Tunnel/Main (Door).asm:7-129).
    // Every positive Runtime uses the inherited actual-media loader. Reflection below is confined to
    // malformed immutable STORAGE captures or invoking CLOSED private seams; it never authenticates
    // assets, changes phase/operating/dispatch fields, injects a helper result or replaces a callback.

    @Test void horizontalDoorUsesDedicatedFmAndLeavesTheThirteenBank494ContractUnchanged() {
        assertThrows(NullPointerException.class, () -> new CdOpeningObjectRuntime.TunnelDoorFm(null));
        assertEquals(CdTunnelDoor.FmSound.FM_A4,
                new CdOpeningObjectRuntime.TunnelDoorFm(CdTunnelDoor.FmSound.FM_A4).sound());
        assertEquals(16, CdOpeningObjectRuntime.LogicalBanks.class.getRecordComponents().length);
        var compact = new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138);
        assertEquals(494, compact.monitorTime() + 82 - compact.player());
        assertEquals(12, compact.monitorTime() - compact.tunnelDoorVertical());
        assertEquals(96, CdObjectPool.DYNAMIC_COUNT);
        assertEquals(32, CdObjectPool.FIRST_WHOLE_INDEX); assertEquals(128, CdObjectPool.END_WHOLE_INDEX);
        assertEquals(1, java.util.Arrays.stream(CdOpeningObjectRuntime.class.getDeclaredFields())
                .filter(field -> field.getType() == CdReservedObjectBank.class).count());
        assertEquals(1, java.util.Arrays.stream(CdOpeningObjectRuntime.Snapshot.class.getDeclaredFields())
                .filter(field -> field.getType() == CdReservedObjectBank.Snapshot.class).count());
        for (Class<?> type : List.of(CdOpeningObjectRuntime.class, CdOpeningObjectRuntime.Snapshot.class)) {
            assertTrue(java.util.Arrays.stream(type.getDeclaredFields()).noneMatch(field ->
                    field.getType() == CdTunnelDoor.SolidCut.class || field.getType() == CdTunnelDoor.SolidCall.class));
        }
    }

    @Test void horizontalDoorColdAndRestartOwnExactlyTheClearCutBankWithoutClaimingLaterHudOrTitleStores() throws Exception {
        try (var cold = new Fixture()) {
            assertHorizontalClearBank(cold.runtime);
            assertEquals(0, cold.frame.vIntClock().deltaSinceAdmittedCut());
            assertTrue(cold.artPublications.isEmpty()); assertTrue(cold.runtime.lastSoundRequests().isEmpty());
            var assets = cold.assets; var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0);
            var uploads = new ArrayList<CdPlayerArtRuntime.State>();
            frame.bindArtPublication((request, state) -> uploads.add(state));
            byte[] saved = new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]; saved[127] = (byte) 0xA5;
            byte[] resident = new byte[0x480]; resident[0x47F] = (byte) 0xC3;
            var powerup = new CdPowerupArtRuntime.State(new byte[0x480], resident);
            try (var runtime = restart(assets, frame, CdAssetBytes.copyOf(saved), powerup, new byte[640], BANKS)) {
                assertHorizontalClearBank(runtime);
                assertArrayEquals(saved, runtime.placement().capture().savedFlags().copy());
                assertEquals(powerup, runtime.powerupArtState()); assertTrue(uploads.isEmpty());
                assertEquals(0, frame.vIntClock().deltaSinceAdmittedCut());
                assertNotEquals(cold.runtime.reservedState(1), runtime.reservedState(1), "distinct runtime lifetimes");
                var local = runtime.captureClosedCut();
                assertThrows(IllegalArgumentException.class, () -> runtime.writeReservedImage(cold.runtime.reservedState(1)));
                assertEquals(local.reserved().slots(), horizontalReserved(runtime));
                assertTrue(runtime.closed()); assertTrue(frame.closed());
            }
        }
    }

    @Test void horizontalDoorAllThirtyOneNonzeroImagesRestoreOnceWithoutAnActorOrDynamicShadow() throws Exception {
        try (var f = new Fixture()) {
            for (int whole = 1; whole < 32; whole++) {
                byte[] bytes = horizontalPattern(whole);
                f.runtime.writeReservedImage(f.runtime.reservedState(whole).withBytes(bytes));
                assertArrayEquals(bytes, f.runtime.reservedState(whole).copyBytes());
            }
            var original = f.runtime.captureClosedCut(); var actor = f.frame.captureActor(); var shared = f.frame.captureShared();
            var cpu = f.runtime.cpuSat(); var picture = f.runtime.displayedFrame();
            assertThrows(UnsupportedOperationException.class, () -> original.reserved().slots().clear());
            for (int whole = 1; whole < 32; whole++) f.runtime.writeReservedImage(f.runtime.reservedState(whole).moveByteAt(63, 0));
            var dynamic = f.slot(127).moveWord(X_FRACTION, 0xCAFE); f.runtime.placement().pool().write(dynamic);
            f.runtime.validateRestore(original);
            assertEquals(dynamic, f.slot(127), "pure preflight does not restore any partition");
            f.runtime.restore(original);
            assertEquals(original.reserved().slots(), horizontalReserved(f.runtime));
            for (int whole = 1; whole < 32; whole++) assertArrayEquals(horizontalPattern(whole), f.runtime.reservedState(whole).copyBytes());
            assertEquals(original.placement().pool().slots(), f.runtime.placement().pool().capture().slots());
            assertEquals(actor, f.frame.captureActor()); assertEquals(shared.environment(), f.frame.environment());
            assertSame(cpu, f.runtime.cpuSat()); assertSame(picture, f.runtime.displayedFrame());
            assertMonitorSnapshot(original, f.runtime.captureClosedCut());
            assertThrows(IndexOutOfBoundsException.class, () -> f.runtime.reservedState(0));
            assertThrows(IndexOutOfBoundsException.class, () -> f.runtime.reservedState(32));
        }
    }

    @Test void horizontalDoorForeignLifetimePreflightIsPureAndRestoreClosesBeforeAnyPartitionWrite() throws Exception {
        try (var a = new Fixture(); var b = new Fixture()) {
            a.runtime.writeReservedImage(a.runtime.reservedState(31).moveLongAt(60, 0x89ABCDEF));
            b.runtime.writeReservedImage(b.runtime.reservedState(1).moveByteAt(63, 0xA5));
            a.runtime.placement().pool().write(a.slot(127).moveWord(X, 0x1234));
            var before = a.runtime.captureClosedCut(); var foreign = b.runtime.captureClosedCut();
            var actor = a.frame.captureActor(); var shared = a.frame.captureShared();
            assertThrows(IllegalArgumentException.class, () -> a.runtime.validateRestore(foreign));
            assertFalse(a.runtime.closed()); assertMonitorSnapshot(before, a.runtime.captureClosedCut());
            assertEquals(before.reserved().slots(), horizontalReserved(a.runtime));
            assertThrows(IllegalArgumentException.class, () -> a.runtime.restore(foreign));
            assertEquals(before.reserved().slots(), horizontalReserved(a.runtime));
            assertEquals(before.placement().pool().slots(), a.runtime.placement().pool().capture().slots());
            assertEquals(actor.state(), a.frame.state()); assertEquals(shared.environment(), a.frame.environment());
            assertTrue(a.runtime.closed()); assertTrue(a.frame.closed());
            assertThrows(IllegalStateException.class, () -> a.runtime.restore(before));
        }
    }

    @Test void horizontalDoorMalformedLateReservedAndDynamicImagesPreflightBeforeEveryCompositeWrite() throws Exception {
        try (var a = new Fixture(); var b = new Fixture()) {
            var good = a.runtime.captureClosedCut();
            a.runtime.writeReservedImage(a.runtime.reservedState(1).moveByteAt(63, 0x5A));
            a.runtime.placement().pool().write(a.slot(127).moveByte(FLAGS, 0xA5));
            var before = a.runtime.captureClosedCut();
            var badReserved = new ArrayList<>(good.reserved().slots()); badReserved.set(30, b.runtime.reservedState(31));
            var malformedBank = (CdReservedObjectBank.Snapshot) horizontalReplacePartition(good.reserved(), List.class, badReserved);
            var malformed = (CdOpeningObjectRuntime.Snapshot) horizontalReplacePartition(good, CdReservedObjectBank.Snapshot.class, malformedBank);
            assertThrows(IllegalArgumentException.class, () -> a.runtime.validateRestore(malformed));
            assertMonitorSnapshot(before, a.runtime.captureClosedCut()); assertEquals(before.reserved().slots(), horizontalReserved(a.runtime));
            var badDynamic = new ArrayList<>(good.placement().pool().slots()); badDynamic.set(95, b.slot(127));
            var malformedPool = (CdObjectPool.Snapshot) horizontalReplacePartition(good.placement().pool(), List.class, badDynamic);
            var malformedPlacement = (CdObjectPlacementRuntime.Snapshot) horizontalReplacePartition(good.placement(), CdObjectPool.Snapshot.class, malformedPool);
            var malformedDynamic = (CdOpeningObjectRuntime.Snapshot) horizontalReplacePartition(good, CdObjectPlacementRuntime.Snapshot.class, malformedPlacement);
            assertThrows(IllegalArgumentException.class, () -> a.runtime.validateRestore(malformedDynamic));
            assertEquals(before.reserved().slots(), horizontalReserved(a.runtime));
            assertMonitorSnapshot(before, a.runtime.captureClosedCut());
            assertThrows(IllegalArgumentException.class, () -> a.runtime.restore(malformed));
            assertEquals(before.reserved().slots(), horizontalReserved(a.runtime));
            assertEquals(before.placement().pool().slots(), a.runtime.placement().pool().capture().slots());
            assertTrue(a.runtime.closed()); assertTrue(a.frame.closed());
        }
    }

    @Test void horizontalDoorInitThenActualZeroSecondaryKeepTheRealPrimaryFractionGlobalAndOtherSlotPublication() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 8, 41, 0, 0x234, 0x100, 0x01008000, 0x07661234);
            seedTunnelDoorGlobals(f, 1, 0x3456, 0x55, 0x66);
            var dirty = horizontalDoor(f, pool.state(40), 0, 0x80, 1, 0x100, 0x780)
                    .moveByte(WIDTH, 1).moveByte(Y_RADIUS, 2).moveByte(PRIORITY, 7).moveWord(TILE, 0xDEAD)
                    .moveByte(ROUTINE_2, 0xA5).moveWord(X_VELOCITY, 0x123).moveWord(Y_FRACTION, 0xBEEF);
            var oldStand = horizontalDoor(f, pool.state(41), 6, 4, 1, 0x100, 0x780).moveByte(FLAGS, 0xA8);
            pool.write(dirty); pool.write(oldStand);
            pool.write(spring(f, pool.state(42), 2, 4, 0, 0).moveByte(ROUTINE_2, 5)
                    .moveWord(X_FRACTION, 0x4567).moveWord(Y_FRACTION, 0x89AB));
            var untouched = pool.state(127).moveWord(TUNNEL_DOOR_TIMER, 0xDEAD); pool.write(untouched);
            var actor = f.frame.state(); var env = f.frame.environment(); var display = f.frame.display();
            var expected = horizontalPrimary(f, horizontalInit(f, dirty));
            assertEquals(CdSolidObject.Path.STAND_ON, expected.path());
            assertEquals(List.of(41), expected.otherSlots().stream().map(s -> s.identity().wholeIndex()).toList());
            assertTrue(expected.globalWrites().contains(CdSolidObject.GlobalField.PRIMARY_ANGLE));
            assertTrue(expected.globalWrites().contains(CdSolidObject.GlobalField.SECONDARY_ANGLE));
            var bank = horizontalReserved(f.runtime); var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertTunnelDoorSolidApplied(f, actor, env, expected);
            assertEquals(0xA300, f.frame.state().motion().centreX16_16() & 0xFFFF);
            assertEquals(0x07651234, f.frame.state().motion().centreY16_16());
            assertEquals(expected.object().moveByte(SPRITE_FLAGS, 4), f.slot(40));
            assertEquals(oldStand.moveByte(FLAGS, 0xA0).moveByte(PREVIOUS_ANIMATION, 1).moveByte(MAP_FRAME, 0)
                    .moveByte(ROUTINE, 2), f.slot(41));
            assertEquals(0x765, f.slot(42).centreY());
            assertEquals(f.frame.state().motion().centreX16_16() >>> 16, f.slot(42).centreX());
            assertEquals(0x4567, f.slot(42).word(X_FRACTION)); assertEquals(0x89AB, f.slot(42).word(Y_FRACTION));
            assertEquals(untouched, f.slot(127)); assertEquals(bank, horizontalReserved(f.runtime));
            assertEquals(display, f.frame.display()); assertEquals(sounds, f.runtime.lastSoundRequests());
            monitorFinish(f);
        }
    }

    @Test void horizontalDoorMainReleaseForwardsSharedChargeStopOnceAndZeroSecondaryNeverResetsFrame() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 8, 40, 1, 0x123, 0, 0x02001234, 0x07805678);
            var image = horizontalDoor(f, pool.state(40), 2, 0x84, 1, 0x100, 0x780).moveByte(FLAGS, 8); pool.write(image);
            var actor = f.frame.state(); var env = f.frame.environment(); var expected = horizontalPrimary(f, image);
            assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP), expected.fmRequests());
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests()); horizontalSharedSounds(sounds, expected);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertTunnelDoorSolidApplied(f, actor, env, expected); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertEquals(0, f.frame.state().objectLinks().standObject());
            assertEquals(expected.object().moveByte(SPRITE_FLAGS, 4), f.slot(40)); monitorFinish(f);
        }
    }

    @Test void horizontalDoorEveryDirtySecondaryByteIncludingIdZeroStopsAfterTheCompletedPrimaryPrefix() throws Exception {
        for (int offset = 0; offset < 64; offset++) try (var f = new Fixture()) {
            f.runtime.writeReservedImage(f.runtime.reservedState(1).moveByteAt(offset, 0xA5));
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 8, 40, 1, 0x123, 0, 0x02001234, 0x07805678);
            var before = horizontalDoor(f, pool.state(40), 0, 0x80, 1, 0x100, 0x780).moveByte(FLAGS, 8); pool.write(before);
            var actor = f.frame.state(); var env = f.frame.environment(); var expected = horizontalPrimary(f, horizontalInit(f, before));
            var bank = horizontalReserved(f.runtime); var sounds = new ArrayList<>(f.runtime.lastSoundRequests()); horizontalSharedSounds(sounds, expected);
            var stop = assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class, f.runtime::runDynamicSlotsAfterReservedSlots, "offset " + offset);
            assertEquals(CdTunnelDoor.UnsupportedBoundary.SECONDARY_PLAYER_OWNER, stop.boundary());
            assertEquals(offset, stop.value()); assertEquals(40, stop.wholeIndex());
            if (offset != 0) assertEquals(0, f.runtime.reservedState(1).byteAt(0), "ID zero does not authorize dirty RAM");
            assertEquals(expected.object(), f.slot(40), "failure precedes DrawObject's bit7 clear");
            assertTunnelDoorSolidApplied(f, actor, env, expected); assertEquals(bank, horizontalReserved(f.runtime));
            assertEquals(sounds, f.runtime.lastSoundRequests()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    @Test void horizontalDoorFirstHelperFailureKeepsInitButPublishesNoStagedSharedResult() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 8, 5, 0, 0, 0x100, 0x01001234, 0x07661234);
            var before = horizontalDoor(f, pool.state(40), 0, 0x80, 1, 0x100, 0x780); pool.write(before);
            var actor = f.frame.state(); var env = f.frame.environment(); var bank = horizontalReserved(f.runtime);
            var sounds = f.runtime.lastSoundRequests();
            var stop = assertThrows(CdSolidObject.UnsupportedSolidObject.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS, stop.boundary());
            assertEquals(horizontalInit(f, before), f.slot(40)); assertEquals(actor, f.frame.state()); assertEquals(env, f.frame.environment());
            assertEquals(bank, horizontalReserved(f.runtime)); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    @Test void horizontalDoorOpeningUsesActualNegativeVelocityBeforeDebugGateAndNonzeroSubtypeNeverRequestsSplashFm() throws Exception {
        for (int subtype : List.of(1, 2, 0xFF)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 2, 0, 0, 0x123, -1, 0x01001234, 0x07805678);
            seedSpinTunnelEnvironment(f, 0xABCD, 0, 0, 0, 1, 0);
            var before = horizontalDoor(f, pool.state(40), 0, 0x80, subtype, 0x100, 0x780); pool.write(before);
            var actor = f.frame.state(); var env = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(horizontalInit(f, before).moveByte(ROUTINE, 4).moveByte(ROUTINE_2, 0).moveByte(SPRITE_FLAGS, 4), f.slot(40));
            assertEquals(subtype == 2 ? 0x18 : 0x2C, f.slot(40).unsignedByte(WIDTH));
            assertEquals(actor, f.frame.state()); assertEquals(env, f.frame.environment()); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertTrue(pool.capture().slots().stream().noneMatch(s -> s.id() == 0x0B)); monitorFinish(f);
        }
    }

    @Test void horizontalDoorActualDebugHighByteAndMiniGateReachTheSameSharedPrimaryOwner() throws Exception {
        for (int debug : List.of(0, 0x00FF, 0x0100)) for (int mini : List.of(0, 1)) try (var f = new Fixture()) {
            springDeviceCut(f); seedSolidPlayer(f, 6, 0, 0, 0, 0x100, 0x01001234, 0x07661234);
            horizontalGates(f, debug, mini); var pool = f.runtime.placement().pool();
            var image = horizontalDoor(f, pool.state(40), 2, 0x84, 1, 0x100, 0x780); pool.write(image);
            var actor = f.frame.state(); var env = f.frame.environment(); var expected = horizontalPrimary(f, image);
            if (debug == 0x0100) assertEquals(CdSolidObject.GetOffReason.DEBUG_MODE, expected.getOffReason().orElseThrow());
            else assertEquals(CdSolidObject.Path.STAND_ON, expected.path());
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertTunnelDoorSolidApplied(f, actor, env, expected);
            assertArrayEquals(new byte[64], f.runtime.reservedState(1).copyBytes()); monitorFinish(f);
        }
    }

    @Test void horizontalDoorEarlierSplashPreservesDirtyFreeBytesAndWaitsUntilTheNextPhysicalPass() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 2, 0, 0, 0, -0x100, 0x01001234, 0x07805678);
            var free = pool.state(32).moveByte(ROUTINE, 6).moveByte(SUBTYPE, 0xA5).moveWord(X_FRACTION, 0xBEEF)
                    .moveWord(Y_FRACTION, 0xCAFE).moveWord(TUNNEL_DOOR_TIMER, 0x1234); pool.write(free);
            pool.write(horizontalDoor(f, pool.state(40), 0, 0x80, 0, 0x100, 0x780));
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests()); sounds.add(new CdOpeningObjectRuntime.TunnelDoorFm(CdTunnelDoor.FmSound.FM_A4));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(free.moveByte(ID, 0x0B).moveWord(X, 0x100).moveWord(Y, 0x77C), f.slot(32));
            assertEquals(4, f.slot(40).routine()); assertEquals(0, f.slot(40).unsignedByte(MAP_FRAME));
            assertEquals(sounds, f.runtime.lastSoundRequests()); monitorFinish(f);
            f.makePlayerAbsent(); timePostAbsentBegin(f, 3); var prefix = pool.capture().slots();
            var stop = assertThrows(UnsupportedOperationException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertTrue(stop.getMessage().contains("b")); assertTrue(stop.getMessage().contains("32"));
            assertEquals(prefix, pool.capture().slots()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    @Test void horizontalDoorLaterRealSplashStopsAtItsOwnVisitAfterOneCreationFmAndCompletedWrapper() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            for (int whole = 32; whole < 40; whole++) pool.write(pool.state(whole).moveByte(ID, 0x24));
            seedSolidPlayer(f, 2, 0, 0, 0, -0x100, 0x01001234, 0x07805678);
            pool.write(horizontalDoor(f, pool.state(40), 0, 0x80, 0, 0x100, 0x780));
            var free = pool.state(41).moveWord(X_FRACTION, 0xBEEF).moveByte(SUBTYPE, 0xA5); pool.write(free);
            var actor = f.frame.state(); var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            sounds.add(new CdOpeningObjectRuntime.TunnelDoorFm(CdTunnelDoor.FmSound.FM_A4));
            var stop = assertThrows(UnsupportedOperationException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertTrue(stop.getMessage().contains("41")); assertTrue(stop.getMessage().contains("$b"));
            assertEquals(free.moveByte(ID, 0x0B).moveWord(X, 0x100).moveWord(Y, 0x77C), f.slot(41));
            assertEquals(4, f.slot(40).routine()); assertEquals(4, f.slot(40).spriteFlags());
            assertEquals(actor, f.frame.state()); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed()); assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
        }
    }

    @Test void horizontalDoorFullDynamicPoolOpensWithoutAChildOrCreationSound() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            for (int whole = 32; whole < 128; whole++) pool.write(pool.state(whole).moveByte(ID, 0x24));
            seedSolidPlayer(f, 2, 0, 0, 0, -0x100, 0x01001234, 0x07805678);
            pool.write(horizontalDoor(f, pool.state(40), 2, 0x84, 0, 0x100, 0x780));
            var actor = f.frame.state(); var sounds = f.runtime.lastSoundRequests(); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(4, f.slot(40).routine()); assertEquals(actor, f.frame.state()); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertTrue(pool.capture().slots().stream().noneMatch(s -> s.id() == 0x0B)); monitorFinish(f);
        }
    }

    @Test void horizontalDoorCreationAndFmPrecedeDirtySecondaryStopAndEarlierSharedFmStaysInSourceOrder() throws Exception {
        try (var f = new Fixture()) {
            // An active alias-sensitive secondary remains storage, with no player-2 actor or helper execution.
            f.runtime.writeReservedImage(f.runtime.reservedState(1).moveByteAt(0x22, 8).moveByteAt(0x3D, 0));
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 8, 39, 1, 0, -0x100, 0x01001234, 0x07805678);
            pool.write(horizontalDoor(f, pool.state(39), 2, 0x84, 1, 0x200, 0x780).moveByte(FLAGS, 8));
            pool.write(horizontalDoor(f, pool.state(40), 2, 0x84, 0, 0x100, 0x780));
            // Slot39 itself reaches the secondary boundary, so its primary FM is the completed prefix.
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            sounds.add(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)));
            var stop = assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(39, stop.wholeIndex()); assertEquals(0x22, stop.value());
            assertEquals(2, f.slot(40).routine()); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertTrue(pool.capture().slots().stream().noneMatch(s -> s.id() == 0x0B));
        }
        try (var f = new Fixture()) {
            f.runtime.writeReservedImage(f.runtime.reservedState(1).moveByteAt(63, 0xA5));
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 2, 0, 0, 0, -0x100, 0x01001234, 0x07805678);
            pool.write(horizontalDoor(f, pool.state(40), 0, 0x80, 0, 0x100, 0x780));
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests()); sounds.add(new CdOpeningObjectRuntime.TunnelDoorFm(CdTunnelDoor.FmSound.FM_A4));
            var stop = assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(63, stop.value()); assertEquals(4, f.slot(40).routine()); assertEquals(0x84, f.slot(40).spriteFlags());
            assertEquals(0x0B, f.slot(32).id()); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    @Test void horizontalDoorEarlierSharedChargeStopPrecedesLaterSplashFmWithoutCompletedEffectReplay() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 8, 39, 1, 0, -0x100, 0x01001234, 0x07805678);
            pool.write(horizontalDoor(f, pool.state(39), 2, 0x84, 1, 0x200, 0x780).moveByte(FLAGS, 8));
            pool.write(horizontalDoor(f, pool.state(40), 2, 0x84, 0, 0x100, 0x780));
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            sounds.add(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)));
            sounds.add(new CdOpeningObjectRuntime.TunnelDoorFm(CdTunnelDoor.FmSound.FM_A4));
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(0, f.frame.state().objectLinks().standObject()); assertEquals(4, f.slot(40).routine());
            assertEquals(0x0B, f.slot(32).id()); assertEquals(sounds, f.runtime.lastSoundRequests()); monitorFinish(f);
        }
    }

    @Test void horizontalDoorEarlierSpringFmThenCreationFmStayOrderedAcrossALaterUnknownFailure() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpringPlayer(f, 0x01008000, 0x07671234, 0x100, 0x100, 2, 0, 0, 0);
            pool.write(spring(f, pool.state(39), 2, 0x84, 0, 0));
            pool.write(horizontalDoor(f, pool.state(40), 2, 0x84, 0, 0x100, 0x76D));
            pool.write(pool.state(42).moveByte(ID, 0x7E));
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            sounds.add(new CdOpeningObjectRuntime.SpringFm(CdSpring.FmSound.FM_SPRING));
            sounds.add(new CdOpeningObjectRuntime.TunnelDoorFm(CdTunnelDoor.FmSound.FM_A4));
            var stop = assertThrows(UnsupportedOperationException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertTrue(stop.getMessage().contains("42")); assertEquals(4, f.slot(39).routine()); assertEquals(4, f.slot(40).routine());
            assertEquals(0x0B, f.slot(32).id()); assertEquals(0x769, f.slot(32).centreY());
            assertEquals(sounds, f.runtime.lastSoundRequests()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    @Test void horizontalDoorRealClosedAndOpenSatShareTheExistingTwelveTilesAnd494PatternPresentation() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpinTunnelEnvironment(f, 0x100, 0, 0, 0, 1, 0);
            pool.write(horizontalDoor(f, pool.state(40), 0, 0x80, 1, 0x80, 0x780));
            pool.write(horizontalDoor(f, pool.state(41), 4, 0x84, 1, 0x100, 0x780)
                    .moveByte(ANIMATION_FRAME, 2).moveByte(MAP_FRAME, 1));
            var sounds = f.runtime.lastSoundRequests(); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(sounds, f.runtime.lastSoundRequests()); var sat = f.runtime.finishAfterCamera(POST);
            assertEquals(List.of(40, 40, 41, 41), sat.provenance().stream().limit(4).map(p -> p.slotRef().wholeIndex()).toList());
            assertArrayEquals(new byte[] {
                    0, (byte) 0xED, 12, 1, (byte) 0x83, 0x2E, 0, (byte) 0xE8,
                    0, (byte) 0xED, 4, 2, (byte) 0x83, 0x32, 1, 8,
                    0, (byte) 0xC5, 3, 3, (byte) 0x83, 0x28, 1, 0x64,
                    0, (byte) 0xE5, 1, 4, (byte) 0x83, 0x2C, 1, 0x64
            }, java.util.Arrays.copyOf(sat.bytes(), 32));
            f.runtime.finishOrdinaryTail(); f.service(0x80);
            var objects = f.assets.openingObjects(); var shown = f.runtime.displayedFrame();
            assertEquals(CdObjectPresentation.convert(sat, monitorPixels(f)), shown);
            var tiles = shown.tiles().stream().filter(t -> t.patternId() >= BANKS.tunnelDoorVertical()
                    && t.patternId() < BANKS.tunnelDoorVertical() + 12).toList();
            assertEquals(12, tiles.size()); assertTrue(tiles.stream().allMatch(t -> t.priority() && t.palette() == 0 && !t.hFlip() && !t.vFlip()));
            for (int tile = 0; tile < 12; tile++) assertEquals(CdObjectPresentation.patternVersion(objects.tunnelDoorArtBank().tile(0x328 + tile)),
                    shown.patternVersions().get(BANKS.tunnelDoorVertical() + tile));
            assertEquals(0x23F038, objects.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.TUNNEL_DOOR_MAPPINGS).range().nativeAddress());
        }
    }

    @Test void horizontalDoorEleventhAnimationCallReachesFcThenResetSkipsTheDirtyBankUntilFollowingMain() throws Exception {
        try (var f = new Fixture()) {
            f.runtime.writeReservedImage(f.runtime.reservedState(1).moveByteAt(63, 0xA5));
            springDeviceCut(f); f.makePlayerAbsent(); var pool = f.runtime.placement().pool();
            pool.write(horizontalDoor(f, pool.state(40), 4, 0x84, 1, 0x100, 0x780));
            for (int call = 1; call <= 11; call++) {
                if (call != 1) timePostAbsentBegin(f, call + 1);
                f.runtime.runDynamicSlotsAfterReservedSlots();
                assertEquals(call == 11 ? 6 : 4, f.slot(40).routine(), "call " + call);
                assertEquals(call <= 2 ? 0 : 1, f.slot(40).unsignedByte(MAP_FRAME));
                assertEquals(0xA5, f.runtime.reservedState(1).byteAt(63)); monitorFinish(f);
            }
            timePostAbsentBegin(f, 13); var actor = f.frame.state(); var env = f.frame.environment();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(2, f.slot(40).routine()); assertEquals(1, f.slot(40).unsignedByte(PREVIOUS_ANIMATION));
            assertEquals(0, f.slot(40).unsignedByte(MAP_FRAME)); assertEquals(actor, f.frame.state()); assertEquals(env, f.frame.environment());
            monitorFinish(f); timePostAbsentBegin(f, 14);
            var stop = assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(CdTunnelDoor.UnsupportedBoundary.SECONDARY_PLAYER_OWNER, stop.boundary()); assertEquals(63, stop.value());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    @Test void horizontalDoorRawWarpAndPreScrollDespawnClearOnlyTheActualSavedPeriodAfterLiveDraw() throws Exception {
        for (int direction : List.of(0xFF, 1, 0x80)) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpinTunnelEnvironment(f, 0, 0, 0, 0, 0x81, direction);
            for (int period = 0; period < 3; period++) seedSpinTunnelLoaded(f.runtime.placement(), 7, period);
            pool.write(horizontalDoor(f, pool.state(40), 4, 0x84, 1, 0x100, 0x780).moveByte(SAVED_FLAGS_ID, 7));
            var before = f.runtime.placement().capture().savedFlags().copy(); var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots(); int selected = direction == 1 ? 0 : 2;
            byte[] expected = before.clone(); expected[2 + 3 * 7 + selected] &= 0x7F;
            assertArrayEquals(expected, f.runtime.placement().capture().savedFlags().copy());
            assertArrayEquals(new byte[64], f.slot(40).copyBytes()); assertEquals(sounds, f.runtime.lastSoundRequests());
            var sat = f.runtime.finishAfterCamera(POST); assertTrue(sat.provenance().stream().noneMatch(p -> p.slotRef().wholeIndex() == 40));
            f.runtime.finishOrdinaryTail();
        }
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            pool.write(horizontalDoor(f, pool.state(40), 4, 0x84, 1, 0x280, 0x780).moveByte(SAVED_FLAGS_ID, 7));
            seedSpinTunnelLoaded(f.runtime.placement(), 7, 1); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertArrayEquals(new byte[64], f.slot(40).copyBytes());
            assertEquals(0, f.runtime.placement().savedFlag(7, 1) & 0x80, "real pre-scroll FG=0 reaches strict outside chunk");
            monitorFinish(f);
        }
    }

    @Test void horizontalDoorClosedReplayRetainsAllNonzeroReservedImagesAndEveryExistingPartitionWithoutInitialization() throws Exception {
        try (var f = new Fixture()) {
            for (int whole = 1; whole < 32; whole++) f.runtime.writeReservedImage(f.runtime.reservedState(whole).withBytes(horizontalPattern(whole)));
            springDeviceCut(f); f.makePlayerAbsent(); var pool = f.runtime.placement().pool();
            pool.write(horizontalDoor(f, pool.state(40), 4, 0x84, 1, 0x100, 0x780));
            f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f);
            var cut = f.runtime.captureClosedCut(); var actor = f.frame.captureActor(); var shared = f.frame.captureShared();
            var cpu = f.runtime.cpuSat(); var picture = f.runtime.displayedFrame();
            timePostAbsentBegin(f, 3); f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f);
            var next = f.runtime.captureClosedCut(); var nextActor = f.frame.captureActor(); var nextShared = f.frame.captureShared(); var nextCpu = f.runtime.cpuSat();
            int uploads = f.artPublications.size();
            f.runtime.validateRestore(cut); f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared);
            f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(cut);
            assertSame(cpu, f.runtime.cpuSat()); assertSame(picture, f.runtime.displayedFrame()); assertEquals(uploads, f.artPublications.size());
            assertEquals(cut.reserved().slots(), horizontalReserved(f.runtime));
            timePostAbsentBegin(f, 3); f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f);
            assertMonitorSnapshot(next, f.runtime.captureClosedCut()); assertEquals(next.reserved().slots(), horizontalReserved(f.runtime));
            assertEquals(nextActor, f.frame.captureActor()); assertEquals(nextShared.environment(), f.frame.environment());
            assertEquals(nextShared.vIntClock(), f.frame.vIntClock()); assertEquals(nextShared.paused(), f.frame.paused());
            assertSpinTunnelArt(nextShared.artState(), f.frame.artState());
            assertArrayEquals(nextCpu.bytes(), f.runtime.cpuSat().bytes()); assertEquals(nextCpu.provenance(), f.runtime.cpuSat().provenance());
        }
    }

    @Test void horizontalDoorFullCutReplayCreatesExactlyOneRealSplashAndOneDedicatedFmAgainWithoutDuplicateReplay() throws Exception {
        try (var f = new Fixture()) {
            f.firstCycle(); f.runtime.placement().pool().clearDynamicSlots(); f.makePlayerAbsent();
            seedSolidPlayer(f, 2, 0, 0, 0, -0x100, 0x01001234, 0x07805678);
            f.runtime.writeReservedImage(f.runtime.reservedState(31).moveLongAt(60, 0x89ABCDEF));
            var pool = f.runtime.placement().pool(); pool.write(horizontalDoor(f, pool.state(40), 0, 0x80, 0, 0x100, 0x780));
            var cut = f.runtime.captureClosedCut(); var actor = f.frame.captureActor(); var shared = f.frame.captureShared();
            timePostAbsentBegin(f, 2); f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f);
            var next = f.runtime.captureClosedCut(); var nextActor = f.frame.captureActor(); var nextShared = f.frame.captureShared(); var nextCpu = f.runtime.cpuSat();
            assertEquals(List.of(new CdOpeningObjectRuntime.TunnelDoorFm(CdTunnelDoor.FmSound.FM_A4)), next.lastSoundRequests());
            assertEquals(1, pool.capture().slots().stream().filter(s -> s.id() == 0x0B).count());
            f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(cut);
            timePostAbsentBegin(f, 2); f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f);
            assertMonitorSnapshot(next, f.runtime.captureClosedCut()); assertEquals(next.reserved().slots(), horizontalReserved(f.runtime));
            assertEquals(nextActor, f.frame.captureActor()); assertEquals(nextShared.environment(), f.frame.environment());
            assertEquals(nextShared.vIntClock(), f.frame.vIntClock()); assertSpinTunnelArt(nextShared.artState(), f.frame.artState());
            assertArrayEquals(nextCpu.bytes(), f.runtime.cpuSat().bytes()); assertEquals(nextCpu.provenance(), f.runtime.cpuSat().provenance());
            assertEquals(next.lastSoundRequests(), f.runtime.lastSoundRequests());
        }
    }

    @Test void horizontalDoorPrivateSolidAndDedicatedFmCallbacksRejectClosedCutsWithoutPublishing() throws Exception {
        try (var f = new Fixture()) {
            var before = f.runtime.captureClosedCut(); var actor = f.frame.captureActor(); var shared = f.frame.captureShared();
            var gates = new CdSolidObject.Gates(f.frame.environment().globals().debugMode(), f.frame.environment().movement().gates().miniSonic());
            var cut = new CdTunnelDoor.SolidCut(CdTunnelDoor.Participant.PRIMARY, f.slot(40).identity(), f.runtime.placement().pool().capture(),
                    tunnelDoorPlayer(f), gates, tunnelDoorGlobals(f), Optional.empty());
            var preflight = CdOpeningObjectRuntime.class.getDeclaredMethod("preflightTunnelDoorSolid", CdTunnelDoor.SolidCut.class); preflight.setAccessible(true);
            var failedPreflight = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> preflight.invoke(f.runtime, cut));
            assertInstanceOf(IllegalStateException.class, failedPreflight.getCause());
            var commit = CdOpeningObjectRuntime.class.getDeclaredMethod("commitTunnelDoorSolid", CdTunnelDoor.SolidCall.class); commit.setAccessible(true);
            var failedCommit = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> commit.invoke(f.runtime, new Object[] { null }));
            assertInstanceOf(IllegalStateException.class, failedCommit.getCause());
            var fm = CdOpeningObjectRuntime.class.getDeclaredMethod("playTunnelDoorFm", CdTunnelDoor.FmSound.class); fm.setAccessible(true);
            var failedFm = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> fm.invoke(f.runtime, CdTunnelDoor.FmSound.FM_A4));
            assertInstanceOf(IllegalStateException.class, failedFm.getCause());
            assertFalse(f.runtime.closed()); assertMonitorSnapshot(before, f.runtime.captureClosedCut());
            assertEquals(before.reserved().slots(), horizontalReserved(f.runtime));
            assertEquals(actor, f.frame.captureActor()); assertEquals(shared.environment(), f.frame.environment());
        }
    }

    @Test void horizontalDoorReservedIngressReentryClosesBothOwnersAndKeepsEarlierDoorAndSoundPrefix() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 2, 0, 0, 0, -0x100, 0x01001234, 0x07805678);
            pool.write(horizontalDoor(f, pool.state(40), 2, 0x84, 0, 0x100, 0x780));
            pool.write(pool.state(41).moveByte(ID, 0x1F).moveByte(ROUTINE, 0).moveByte(SUBTYPE, 1)
                    .moveWord(X, 64).moveWord(Y, 0x720));
            var bank = horizontalReserved(f.runtime); var sounds = new ArrayList<>(f.runtime.lastSoundRequests());
            sounds.add(new CdOpeningObjectRuntime.TunnelDoorFm(CdTunnelDoor.FmSound.FM_A4));
            f.onAngle = value -> assertThrows(IllegalStateException.class,
                    () -> f.runtime.writeReservedImage(f.runtime.reservedState(31).moveByteAt(63, 0xA5)));
            assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(bank, horizontalReserved(f.runtime)); assertEquals(4, f.slot(40).routine()); assertEquals(0x0B, f.slot(32).id());
            assertEquals(sounds, f.runtime.lastSoundRequests()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    private static CdObjectState horizontalDoor(Fixture f, CdObjectState image, int routine, int sprite,
                                                 int subtype, int x, int y) throws java.io.IOException {
        return image.moveByte(ID, CdTunnelDoor.OBJECT_ID).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, sprite)
                .moveByte(SUBTYPE, subtype).moveLong(MAPPINGS, f.assets.openingObjects().view(
                        com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.TUNNEL_DOOR_MAPPINGS).range().nativeAddress())
                .moveWord(TILE, 0x8328).moveByte(PRIORITY, 1).moveByte(WIDTH, subtype == 2 ? 0x18 : 0x2C)
                .moveByte(Y_RADIUS, 8).moveWord(X, x).moveWord(Y, y);
    }

    private static CdObjectState horizontalInit(Fixture f, CdObjectState image) throws java.io.IOException {
        var objects = f.assets.openingObjects();
        return image.addByte(ROUTINE, 2).moveLong(MAPPINGS, objects.view(
                com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.TUNNEL_DOOR_MAPPINGS).range().nativeAddress())
                .moveByte(PRIORITY, 1).moveByte(SPRITE_FLAGS, image.spriteFlags() | 4)
                .moveByte(WIDTH, image.subtype() == 2 ? 0x18 : 0x2C).moveByte(Y_RADIUS, 8).moveWord(TILE, 0x8328);
    }

    private static CdSolidObject.Result horizontalPrimary(Fixture f, CdObjectState image) {
        var e = f.frame.environment();
        return CdSolidObject.fromAssets(f.assets).run(CdSolidObject.Entry.TOP_SOLID_OBJECT, image, tunnelDoorPlayer(f),
                new CdSolidObject.Gates(e.globals().debugMode(), e.movement().gates().miniSonic()), tunnelDoorGlobals(f),
                f.runtime.placement().pool().capture());
    }

    private static void horizontalSharedSounds(List<CdOpeningObjectRuntime.SoundRequest> sounds, CdSolidObject.Result result) {
        for (var fm : result.fmRequests()) sounds.add(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
    }

    private static void horizontalGates(Fixture f, int debug, int mini) {
        var shared = f.frame.captureShared(); var e = shared.environment(); var m = e.movement(); var g = m.gates(); var b = e.globals();
        var gates = new CdPlayerMovement.Gates(g.zoneAct(), g.timeZone(), mini, g.waterSlide(), g.windTunnel(), g.speedShoes(), g.timeWarpDirection());
        var globals = new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), debug, b.debugCheat(),
                b.timeWarpTimer(), b.invincible(), b.bossMusic());
        f.frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), new CdPlayerBody.Environment(
                new CdPlayerMovement.Environment(m.physics(), m.bounds(), gates, m.globals()), e.controls(), globals, e.dynamicSlots()),
                shared.artState(), shared.paused(), shared.vIntClock()));
    }

    private static List<CdReservedObjectBank.Image> horizontalReserved(CdOpeningObjectRuntime runtime) {
        var images = new ArrayList<CdReservedObjectBank.Image>();
        for (int whole = 1; whole < 32; whole++) images.add(runtime.reservedState(whole));
        return List.copyOf(images);
    }

    private static void assertHorizontalClearBank(CdOpeningObjectRuntime runtime) {
        var snapshot = runtime.captureClosedCut(); assertEquals(31, snapshot.reserved().slots().size());
        for (int whole = 1; whole < 32; whole++) {
            var image = runtime.reservedState(whole); assertEquals(whole, image.identity().wholeIndex());
            assertEquals(0xFFD000 + 64 * whole, image.identity().nativeAddress24());
            assertArrayEquals(new byte[64], image.copyBytes(), "ClearObjects cut, not post-HUD/title whole " + whole);
            assertSame(image, snapshot.reserved().slots().get(whole - 1));
        }
        assertEquals(96, snapshot.placement().pool().slots().size());
    }

    private static byte[] horizontalPattern(int whole) {
        byte[] bytes = new byte[64]; for (int at = 0; at < bytes.length; at++) bytes[at] = (byte) (0x5B ^ whole * 29 ^ at * 53);
        return bytes;
    }

    /** Corrupt STORAGE-only immutable capture shape; no media/runtime admission or phase is manufactured. */
    private static Object horizontalReplacePartition(Object capture, Class<?> partitionType, Object replacement) throws Exception {
        var constructors = capture.getClass().getDeclaredConstructors(); assertEquals(1, constructors.length);
        var constructor = constructors[0]; var types = constructor.getParameterTypes(); var values = new Object[types.length];
        for (int index = 0; index < types.length; index++) {
            if (types[index] == partitionType) { values[index] = replacement; continue; }
            final Class<?> type = types[index];
            var fields = java.util.Arrays.stream(capture.getClass().getDeclaredFields()).filter(field -> field.getType() == type).toList();
            assertEquals(1, fields.size(), "unambiguous immutable storage partition " + type);
            var field = fields.getFirst(); field.setAccessible(true); values[index] = field.get(capture);
        }
        constructor.setAccessible(true); return constructor.newInstance(values);
    }
    // END horizontal Door Runtime R1 additions.

    // BEGIN current-camera/Pata Runtime source-only tests-first additions.
    // N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Main.asm:407-430,821-847;
    // Pata-Bata/Main.asm:7-80; Section Art Load.asm:7-46; Functions (General).asm:588-785.
    // Constructed actual-owner cuts isolate the Runtime joins. No natural route, native restart,
    // full title/LoadPLCImm history, execution, compile or render claim is made. ALL JAVA UNRUN.

    // Catches a fourteenth bank, an assumed known Anton/gap, or a cold section request/decode.
    @Test void cameraArtColdDevSeedKeepsThirteen494SpansAndLeavesOnlyUnadmittedIngressUnknown() throws Exception {
        try (var f = new Fixture()) {
            var state = f.runtime.sectionArtState();
            assertEquals(0, state.sectionWord()); assertEquals(0, state.remainingTilesWord());
            assertEquals(0, state.queue().headSource()); assertTrue(state.decoder().isEmpty());
            assertArrayEquals(new byte[512], state.codeTable().copy());
            var objects = f.assets.openingObjects();
            cameraArtBytes(state.residency(), 0x7400, objects.initialArtBank().binding(
                    com.openggf.game.soniccd.level.CdR11aAsset.ART_MOSQUI).bytes());
            cameraArtBytes(state.residency(), 0x8480, objects.frontierArtBank().binding(
                    com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_PLANT).bytes());
            cameraArtBytes(state.residency(), 0x8820, objects.frontierArtBank().binding(
                    com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_RAMP_BOOST).bytes());
            assertThrows(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.OwnershipBoundary.class,
                    () -> com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(state.residency(), 0x409));
            assertThrows(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.OwnershipBoundary.class,
                    () -> com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(state.residency(), 0x440));
            assertEquals(16, CdOpeningObjectRuntime.LogicalBanks.class.getRecordComponents().length);
            var compact = new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138);
            assertEquals(494, compact.monitorTime() + 82 - compact.player());
            assertEquals(state, f.runtime.captureClosedCut().sectionArtState());
        }
    }

    // Catches the pre-scroll camera being used for the CPU request, or tiles written during prepare.
    @Test void cameraArtPostScroll680RequestsWholePlc9OnlyAfterSuccessfulBuildAndPreparesWithoutWriting() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); var before = f.runtime.sectionArtState();
            cameraArtBegin(f, 0);
            f.runtime.spawnBeforeReservedSlots(); f.body(2); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(before, f.runtime.sectionArtState());
            var c = new CameraWords(0x680, PRE.yWord()); f.runtime.finishAfterCamera(new DrawCameras(c, PRE, PRE));
            assertEquals(before, f.runtime.sectionArtState(), "SAT build must not request or prepare");
            f.runtime.finishOrdinaryTail(); var after = f.runtime.sectionArtState();
            assertEquals(0x0200, after.sectionWord()); assertEquals(50, after.remainingTilesWord());
            assertEquals(before.residency(), after.residency());
            var rows = f.assets.loadPlan().plcs().get(9).entries(); assertEquals(3, rows.size());
            assertEquals(0x7600, after.queue().headDestination());
            assertEquals(rows.get(1).sourceRange().nativeAddress(), after.queue().bytes().unsignedLong(6));
            assertEquals(0x8120, after.queue().bytes().unsignedWord(10));
            assertEquals(rows.get(2).sourceRange().nativeAddress(), after.queue().bytes().unsignedLong(12));
            assertEquals(0x8500, after.queue().bytes().unsignedWord(16));
            assertTrue(after.decoder().isPresent());
        }
    }

    // Catches incorrectly selecting the faster budget from the low byte of raw scrollLock WORD.
    @Test void cameraArtRawScrollLock0001WritesThreeWhere0100WritesEighteen() throws Exception {
        for (int lock : List.of(0x0001, 0x0100)) try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtCycle(f, 0x680, 0); var before = f.runtime.sectionArtState();
            cameraArtBegin(f, lock); var after = f.runtime.sectionArtState(); int count = lock == 1 ? 3 : 18;
            assertEquals(50 - count, after.remainingTilesWord());
            assertEquals(0x7600 + count * 32, after.queue().headDestination());
            var expected = cameraArtSource(f, com.openggf.game.soniccd.level.CdR11aAsset.ART_PATA_BATA);
            cameraArtBytes(after.residency(), 0x7600, expected.slice(0, count * 32));
            assertArrayEquals(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(before.residency(),
                    0x3B0 + count).copy(), com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(
                    after.residency(), 0x3B0 + count).copy(), "next tile must retain its prior native value");
        }
    }

    // Catches consuming input/closing the owner before validating an unsigned source WORD.
    @Test void cameraArtInvalidRawScrollLockRejectsBeforePreparedInputIsConsumed() throws Exception {
        try (var f = new Fixture()) {
            f.runtime.setPreScrollForeground(PRE); var selected = f.frame.prepareInput(1);
            var state = f.runtime.sectionArtState(); var clock = f.frame.vIntClock();
            for (int invalid : List.of(-1, 0x10000, Integer.MAX_VALUE)) {
                assertThrows(IllegalArgumentException.class, () -> f.runtime.serviceVBlank(selected.phase(), () -> { }, invalid));
                assertFalse(f.runtime.closed()); assertFalse(f.frame.closed());
                assertEquals(clock, f.frame.vIntClock()); assertEquals(state, f.runtime.sectionArtState());
            }
            f.runtime.serviceVBlank(selected.phase(), () -> { }, 0);
            assertEquals(1, f.frame.vIntClock().deltaSinceAdmittedCut());
        }
    }

    // Catches moving the camera commit before plane publication or after Finish/count.
    @Test void cameraArtPlaneCallbackSeesPowerupCommittedButOldCameraBeforeItsWriteAndFinish() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtCycle(f, 0x680, 0); var before = f.runtime.sectionArtState();
            f.runtime.setPreScrollForeground(PRE); var selected = f.frame.prepareInput(0);
            long tick = f.frame.vIntClock().deltaSinceAdmittedCut(); var calls = new ArrayList<Integer>();
            f.runtime.serviceVBlank(selected.phase(), () -> {
                assertEquals(before, f.runtime.sectionArtState());
                assertEquals(tick, f.frame.vIntClock().deltaSinceAdmittedCut());
                assertEquals(CdOpeningObjectRuntime.Phase.SERVICING, f.runtime.phase()); calls.add(1);
            }, 0x0100);
            assertEquals(List.of(1), calls); assertEquals(32, f.runtime.sectionArtState().remainingTilesWord());
            assertEquals(tick + 1, f.frame.vIntClock().deltaSinceAdmittedCut());
        }
    }

    // Catches a retained service arm/callback, or incorrectly servicing/preparing at PP lag.
    @Test void cameraArtNormalPauseServicesAlreadyPreparedHeadWhileLagKeepsEveryCameraFieldAndPicture() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtCycle(f, 0x680, 0);
            f.frame.prepareInput(0x80); f.runtime.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE, () -> { }, 0);
            assertTrue(f.frame.paused()); assertEquals(47, f.runtime.sectionArtState().remainingTilesWord());
            var held = f.runtime.captureClosedCut(); var picture = f.runtime.displayedFrame();
            f.frame.prepareInput(0); f.runtime.serviceVBlank(PlcLifecyclePhase.LAG, () -> fail("lag callback"), 0x0100);
            assertEquals(held.sectionArtState(), f.runtime.sectionArtState()); assertEquals(picture, f.runtime.displayedFrame());
            f.frame.prepareInput(0); f.runtime.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE, () -> { }, 0);
            assertEquals(44, f.runtime.sectionArtState().remainingTilesWord());
            assertEquals(0x0200, f.runtime.sectionArtState().sectionWord(), "pause must not request section0 from old PRE");
        }
    }

    // Catches a next-head decode during the final tile's unused selected budget.
    @Test void cameraArtPataSeventeenthSlowServicePopsWithoutTouchingOrPreparingAnton() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtCycle(f, 0x680, 0);
            for (int i = 0; i < 16; i++) cameraArtCycle(f, 0x680, 0);
            assertEquals(2, f.runtime.sectionArtState().remainingTilesWord()); cameraArtBegin(f, 0);
            var popped = f.runtime.sectionArtState(); assertEquals(0, popped.remainingTilesWord());
            assertEquals(2, popped.processBudgetWord());
            assertEquals(0x236E46L, popped.queue().headSource()); assertEquals(0x8120, popped.queue().headDestination());
            assertThrows(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.OwnershipBoundary.class,
                    () -> com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(popped.residency(), 0x409));
            cameraArtFinish(f, 0x680); assertEquals(22, f.runtime.sectionArtState().remainingTilesWord());
            cameraArtBytes(f.runtime.sectionArtState().residency(), 0x7600,
                    cameraArtSource(f, com.openggf.game.soniccd.level.CdR11aAsset.ART_PATA_BATA));
        }
    }

    // Catches dropping PLC9's Anton/Tam rows or using an atomic whole-source picture instead of native writes.
    @Test void cameraArtWholeActualPlc9CompletesIn45SelectedSlowServicesAndChangesPlantAndRamp() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtCycle(f, 0x680, 0); var seed = f.runtime.sectionArtState();
            for (int i = 0; i < 45; i++) cameraArtCycle(f, 0x680, 0);
            var done = f.runtime.sectionArtState(); assertEquals(0, done.remainingTilesWord()); assertEquals(0, done.queue().headSource());
            cameraArtBytes(done.residency(), 0x7600, cameraArtSource(f, com.openggf.game.soniccd.level.CdR11aAsset.ART_PATA_BATA));
            cameraArtBytes(done.residency(), 0x8120, cameraArtSource(f, com.openggf.game.soniccd.level.CdR11aAsset.ART_ANTON));
            cameraArtBytes(done.residency(), 0x8500, cameraArtSource(f, com.openggf.game.soniccd.level.CdR11aAsset.ART_TAMABBOH));
            assertFalse(java.util.Arrays.equals(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(seed.residency(), 0x428).copy(),
                    com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(done.residency(), 0x428).copy()));
            assertFalse(java.util.Arrays.equals(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(seed.residency(), 0x441).copy(),
                    com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(done.residency(), 0x441).copy()));
            assertArrayEquals(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(seed.residency(), 0x3A0).copy(),
                    com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(done.residency(), 0x3A0).copy(), "outside Pata overwrite stays intact");
        }
    }

    // Catches stale object-specific Mosqui pixels at native$3B0 after a Pata write and picture mutation on tail.
    @Test void cameraArtPreviousMosquiSatUsesFirstThreeNowCurrentPataTilesAtNextVIntAndOldPictureStaysFrozen() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtBegin(f, 0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            var objects = f.assets.openingObjects(); var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(40).moveByte(ID, CdMosqui.OBJECT_ID).moveByte(ROUTINE, 8).moveByte(SPRITE_FLAGS, 0x84)
                    .moveLong(MAPPINGS, objects.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.MOSQUI_NORMAL_MAPPINGS).range().nativeAddress())
                    .moveWord(TILE, 0x23B0).moveWord(X, 0x100).moveWord(Y, 0x780).moveWord(MOSQUI_ANCHOR_X, 0x100)
                    .moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 0x10).moveByte(PRIORITY, 3));
            f.runtime.runDynamicSlotsAfterReservedSlots(); var c = new CameraWords(0x680, PRE.yWord());
            // World X is offset to stay visible in the POST camera; queue eligibility used PRE as native DrawObject did.
            pool.write(pool.state(40).moveWord(X, 0x780));
            var sat = f.runtime.finishAfterCamera(new DrawCameras(c, c, c));
            assertTrue(sat.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 40));
            f.runtime.finishOrdinaryTail(); var oldPicture = f.runtime.displayedFrame();
            var frozen = oldPicture.patternVersions(); var oldCpu = sat.bytes();
            cameraArtBegin(f, 0); assertArrayEquals(oldCpu, f.runtime.cpuSat().bytes());
            var shown = f.runtime.displayedFrame(); var pata = cameraArtSource(f, com.openggf.game.soniccd.level.CdR11aAsset.ART_PATA_BATA);
            for (int i = 0; i < 3; i++) assertEquals(CdObjectPresentation.patternVersion(pata.slice(i * 32, 32)),
                    shown.patternVersions().get(BANKS.mosqui() + 16 + i));
            assertEquals(frozen, oldPicture.patternVersions());
            assertEquals(CdObjectPresentation.convert(sat, cameraArtPixels(f)), shown);
        }
    }

    // Catches suppressing unowned later PLCs, rollback of the current generation, or continued CPU tail after a stop.
    @Test void cameraArtActiveReversalReachesUnknownBoulderPrepareAndRetainsAlreadyWrittenPrefix() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtCycle(f, 0x680, 0); cameraArtBegin(f, 0);
            f.runtime.spawnBeforeReservedSlots(); f.body(2); f.runtime.runDynamicSlotsAfterReservedSlots(); f.runtime.finishAfterCamera(POST);
            f.runtime.finishOrdinaryTail(); // The real reversal list now prepares its owned first Boulder row.
            assertEquals(19, f.runtime.sectionArtState().remainingTilesWord());
            assertEquals(0x6E80, f.runtime.sectionArtState().queue().headDestination());
            for (int i = 0; i < 6; i++) cameraArtCycle(f, 0, 0); // Eighteen real complete-tile writes, no filtering.
            assertEquals(1, f.runtime.sectionArtState().remainingTilesWord());
            cameraArtBegin(f, 0); // Nineteenth tile completes and pops Boulder; Animals waits for ProcessPLCs.
            var prefix = f.runtime.sectionArtState().residency(); var rings = f.runtime.ringGlobals();
            cameraArtBytes(prefix, 0x6E80, cameraArtSource(f, com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER));
            f.runtime.spawnBeforeReservedSlots(); f.body(2); f.runtime.runDynamicSlotsAfterReservedSlots(); f.runtime.finishAfterCamera(POST);
            var fault = assertThrows(com.openggf.game.soniccd.resources.CdR11aSectionArt.SectionArtException.class, f.runtime::finishOrdinaryTail);
            assertEquals(com.openggf.game.soniccd.resources.CdR11aSectionArt.Reason.UNKNOWN_SOURCE, fault.reason());
            assertEquals(prefix, f.runtime.sectionArtState().residency()); assertEquals(fault.state(), f.runtime.sectionArtState());
            assertEquals(0, f.runtime.sectionArtState().sectionWord()); assertEquals(0, f.runtime.sectionArtState().remainingTilesWord());
            assertEquals(0x7100, f.runtime.sectionArtState().queue().headDestination());
            assertTrue(f.runtime.sectionArtState().stopped()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
            assertEquals(rings, f.runtime.ringGlobals(), "ring tail follows the failed native ProcessPLCs cut");
            assertThrows(IllegalStateException.class, () -> f.runtime.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE));
        }
    }

    // Catches flattening state capture into residency only, implicit restore decode, or loss of saved flags/display.
    @Test void cameraArtAggregateReplayAtQueuedPartialAndCompleteTamCutsRetainsAllExistingPartitions() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); f.runtime.placement().bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.MEMBER_1);
            cameraArtCycle(f, 0x680, 0);
            for (int advance : List.of(0, 26, 19)) {
                for (int i = 0; i < advance; i++) cameraArtCycle(f, 0x680, 0);
                var cut = f.runtime.captureClosedCut(); var actor = f.frame.captureActor(); var shared = f.frame.captureShared();
                assertEquals(advance == 0 ? 50 : advance == 26 ? 56 : 0, cut.sectionArtState().remainingTilesWord());
                var cpu = f.runtime.cpuSat().bytes(); var picture = f.runtime.displayedFrame();
                cameraArtCycle(f, 0x680, 0); var future = f.runtime.captureClosedCut();
                var futureActor = f.frame.captureActor(); var futureShared = f.frame.captureShared(); var futureCpu = f.runtime.cpuSat().bytes();
                f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(cut);
                assertEquals(cut.sectionArtState(), f.runtime.sectionArtState()); assertEquals(picture, f.runtime.displayedFrame());
                assertArrayEquals(cpu, f.runtime.cpuSat().bytes()); assertEquals(1, f.runtime.placement().savedFlag(7, 1));
                cameraArtCycle(f, 0x680, 0); var replay = f.runtime.captureClosedCut();
                assertEquals(future.sectionArtState(), replay.sectionArtState()); assertMonitorSnapshot(future, replay);
                assertEquals(future.reserved().slots(), replay.reserved().slots());
                assertEquals(futureActor, f.frame.captureActor()); assertEquals(futureShared, f.frame.captureShared());
                assertArrayEquals(futureCpu, f.runtime.cpuSat().bytes());
                // Continue from the original cut, so advances name exact service counts rather than replay calls.
                f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(cut);
            }
        }
    }

    // Catches accepting a foreign private aggregate token or serving an unprepared phase to hide missed CPU work.
    @Test void cameraArtForeignCaptureAndMisphaseRejectBeforeAnyArtStateReplacement() throws Exception {
        try (var a = new Fixture(); var b = new Fixture()) {
            var before = a.runtime.sectionArtState(); var foreign = b.runtime.captureClosedCut();
            assertThrows(IllegalArgumentException.class, () -> a.runtime.validateRestore(foreign));
            assertEquals(before, a.runtime.sectionArtState()); assertFalse(a.runtime.closed());
            assertThrows(IllegalArgumentException.class, () -> a.runtime.restore(foreign));
            assertEquals(before, a.runtime.sectionArtState()); assertTrue(a.runtime.closed());
        }
        try (var f = new Fixture()) {
            var before = f.runtime.sectionArtState();
            assertThrows(IllegalStateException.class, f.runtime::finishOrdinaryTail);
            assertEquals(before, f.runtime.sectionArtState()); assertTrue(f.runtime.closed());
        }
    }

    // Catches recreating cold/zero pixels/table on restart or carrying CPU decoder/queue as a native restart claim.
    @Test void cameraArtRestartRequiresExplicitNonzeroResidentTableAndPreservesRawSectionLowByteVerbatim() throws Exception {
        var assets = load(); var seed = cameraArtDevIngress(assets);
        byte[] tile = new byte[32]; java.util.Arrays.fill(tile, (byte) 0xA7);
        var resident = com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.writeTile(seed.residency(), 0x7600, CdAssetBytes.copyOf(tile));
        byte[] table = new byte[512]; for (int i = 0; i < table.length; i++) table[i] = (byte) (i ^ 0xC3);
        var ingress = new CdOpeningObjectRuntime.CameraArtRestartIngress(resident, CdAssetBytes.copyOf(table), 0x005A);
        try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0)) {
            var publications = new ArrayList<CdPlayerArtRuntime.State>(); frame.bindArtPublication((request, state) -> publications.add(state));
            try (var runtime = CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(assets, frame, INITIAL, CONTACT, SCORE, LEVEL,
                    CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]),
                    new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]), ingress, new byte[640], BANKS, PRE, value -> { })) {
                var state = runtime.sectionArtState(); assertEquals(resident, state.residency()); assertArrayEquals(table, state.codeTable().copy());
                assertEquals(0x005A, state.sectionWord()); assertEquals(0, state.queue().headSource()); assertTrue(state.decoder().isEmpty());
                assertEquals(0, state.remainingTilesWord()); assertEquals(0, state.processBudgetWord()); assertEquals(0, state.hintWord()); assertEquals(0, state.padWord());
                assertEquals(com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.CLEARED, state.savedDecoder().writer());
                var cut = runtime.captureClosedCut(); runtime.restore(cut); assertEquals(state, runtime.sectionArtState());
                assertTrue(publications.isEmpty()); assertEquals(0, frame.vIntClock().deltaSinceAdmittedCut());
                assertTrue(runtime.displayedFrame().tiles().isEmpty());
            }
        }
    }

    // Catches a restart factory silently taking a cold default, and a lossy table/section declaration.
    @Test void cameraArtRestartIngressRejectsAbsentAndMalformedExplicitValuesBeforeFactoryAdmission() throws Exception {
        var seed = cameraArtDevIngress(load());
        assertThrows(NullPointerException.class, () -> new CdOpeningObjectRuntime.CameraArtRestartIngress(null, seed.codeTable512(), 0));
        assertThrows(NullPointerException.class, () -> new CdOpeningObjectRuntime.CameraArtRestartIngress(seed.residency(), null, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdOpeningObjectRuntime.CameraArtRestartIngress(seed.residency(), CdAssetBytes.copyOf(new byte[511]), 0));
        assertThrows(IllegalArgumentException.class, () -> new CdOpeningObjectRuntime.CameraArtRestartIngress(seed.residency(), seed.codeTable512(), 0x10000));
        assertThrows(IllegalArgumentException.class, () -> new CdOpeningObjectRuntime.CameraArtRestartIngress(seed.residency(), seed.codeTable512(), -1));
        var assets = load(); try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0)) {
            frame.bindArtPublication((request, state) -> { });
            assertThrows(NullPointerException.class, () -> CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(assets, frame,
                    INITIAL, CONTACT, SCORE, LEVEL, CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]),
                    new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]), null, new byte[640], BANKS, PRE, value -> { }));
            assertFalse(frame.closed()); assertEquals(0, frame.vIntClock().deltaSinceAdmittedCut());
        }
    }

    // Catches snapshotting a second mutable resident authority or adding another logical Pata span.
    @Test void cameraArtOneAggregateCaptureAndOneRealPataBodyUseTheExistingNativePool() {
        assertEquals(1, java.util.Arrays.stream(CdOpeningObjectRuntime.class.getDeclaredFields()).filter(f ->
                f.getType() == com.openggf.game.soniccd.resources.CdR11aSectionArt.State.class).count());
        assertEquals(1, java.util.Arrays.stream(CdOpeningObjectRuntime.Snapshot.class.getDeclaredFields()).filter(f ->
                f.getType() == com.openggf.game.soniccd.resources.CdR11aSectionArt.State.class).count());
        assertEquals(1, java.util.Arrays.stream(CdOpeningObjectRuntime.class.getDeclaredFields()).filter(f -> f.getType() == CdPataBata.class).count());
        for (Class<?> type : List.of(CdOpeningObjectRuntime.class, CdOpeningObjectRuntime.Snapshot.class))
            assertTrue(java.util.Arrays.stream(type.getDeclaredFields()).noneMatch(f ->
                    f.getType() == com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State.class
                    || f.getType() == com.openggf.game.soniccd.level.CdR11aObjectAssets.PataBataArtBank.class));
    }

    // Catches dropping Init fallthrough, inventing a player/time-stop/debug gate, or replaying completed motion.
    @Test void pataLiveInitMainUsesActualAssetsAndNativeAnchorsAnimationAndFractionalBobOnce() throws Exception {
        for (int subtype : List.of(0, 1)) try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtBegin(f, 0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(pool.state(40).moveByte(ID, CdPataBata.OBJECT_ID).moveByte(SUBTYPE, subtype)
                    .moveWord(X, 0x100).moveWord(Y, 0x780));
            var sounds = f.runtime.lastSoundRequests(); f.runtime.runDynamicSlotsAfterReservedSlots(); var s = f.slot(40);
            assertEquals(2, s.routine()); assertEquals(0x100, s.word(PATA_BATA_ANCHOR_X));
            assertEquals(0x07808000, s.longBits(CdObjectState.LongField.PATA_BATA_ANCHOR_Y));
            assertEquals(subtype == 0 ? 0x00FF8000 : 0x00FFC000, s.longBits(CdObjectState.LongField.X_POSITION));
            assertEquals(subtype == 0 ? 0x077F1FFF : 0x07802FFF, s.longBits(CdObjectState.LongField.Y_POSITION));
            assertEquals(subtype == 0 ? 0xFE00 : 0xFF00, s.word(PATA_BATA_PHASE));
            assertEquals(subtype == 0 ? 7 : 3, s.unsignedByte(ANIMATION_TIME)); assertEquals(1, s.unsignedByte(ANIMATION_FRAME));
            assertEquals(0x23B0, s.word(TILE)); assertEquals(0x2A, s.unsignedByte(COLLISION_TYPE));
            assertEquals(sounds, f.runtime.lastSoundRequests()); var sat = f.runtime.finishAfterCamera(POST);
            assertTrue(sat.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 40)); f.runtime.finishOrdinaryTail();
        }
    }

    // Catches dispatching Pata through Mosqui's routine table or applying completed motion twice.
    @Test void pataEveryRepresentativeNonzeroRoutineEntersMainWithoutInitAndPreservesItsNativeWordFields() throws Exception {
        for (int routine : List.of(1, 3, 0x80, 0xFF)) try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtBegin(f, 0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(cameraPataMain(f, pool.state(40)).moveByte(ROUTINE, routine)
                    .moveLong(CdObjectState.LongField.PATA_BATA_X_DELTA, -0x8000));
            f.runtime.runDynamicSlotsAfterReservedSlots(); var s = f.slot(40);
            assertEquals(routine, s.routine()); assertEquals(0x00FF8000, s.longBits(CdObjectState.LongField.X_POSITION));
            assertEquals(0x100, s.word(PATA_BATA_ANCHOR_X)); assertEquals(0, s.word(PATA_BATA_PHASE_SPEED));
            assertEquals(0x23B0, s.word(TILE)); f.runtime.finishAfterCamera(POST); f.runtime.finishOrdinaryTail();
        }
    }

    // Catches an active-list snapshot/reordered dispatch and exposing pre-body state to a later real consumer.
    @Test void pataEarlierPhysicalSourceCompletesBeforeLaterMosquiTerrainConsumerWithoutMetadataReplay() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtBegin(f, 0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(cameraPataMain(f, pool.state(39)).moveLong(CdObjectState.LongField.PATA_BATA_X_DELTA, -0x8000));
            var oldMosqui = f.assets.openingObjects();
            pool.write(pool.state(40).moveByte(ID, CdMosqui.OBJECT_ID).moveByte(ROUTINE, 6)
                    .moveLong(MAPPINGS, oldMosqui.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.MOSQUI_NORMAL_MAPPINGS).range().nativeAddress())
                    .moveWord(X, 0x100).moveWord(Y, 0x780).moveWord(MOSQUI_ANCHOR_X, 0x100).moveByte(SPRITE_FLAGS, 4));
            var observed = new ArrayList<Integer>(); f.onAngle = value -> observed.add(f.slot(39).longBits(CdObjectState.LongField.X_POSITION));
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertEquals(List.of(0x00FF8000), observed);
            assertEquals(0x00FF8000, f.slot(39).longBits(CdObjectState.LongField.X_POSITION));
        }
    }

    // Catches using current X instead of the native anchor for despawn, or clearing member rather than loaded.
    @Test void pataLiveAnchorDespawnUsesPreScrollCameraAndClearsOnlyActualSavedLoadedBit() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtBegin(f, 0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            var placement = f.runtime.placement(); var pool = placement.pool(); pool.clearDynamicSlots();
            pool.write(cameraPataMain(f, pool.state(40)).moveWord(X, 0x600).moveWord(PATA_BATA_ANCHOR_X, 0x600).moveByte(SAVED_FLAGS_ID, 7));
            seedSpinTunnelLoaded(placement, 7, 1); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertArrayEquals(new byte[64], f.slot(40).copyBytes()); assertEquals(0x55, placement.savedFlag(7, 1), "dirty D5 loses only LOADED bit7");
            var sat = f.runtime.finishAfterCamera(POST); assertTrue(sat.provenance().stream().noneMatch(p -> p.slotRef().wholeIndex() == 40));
            f.runtime.finishOrdinaryTail();
        }
    }

    // Raw warp periods are still outside Frame service admission; object-side raw reads are component-owned.
    @Test void pataRawWarpPeriodStopsAtExistingFrameFamilyBeforeAnyTimePostOrPataVisit() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f);
            var placement = f.runtime.placement(); var pool = placement.pool();
            pool.write(timePostImage(f, pool.state(39), 2, 8, 0, 1));
            pool.write(cameraPataMain(f, pool.state(40)).moveByte(SAVED_FLAGS_ID, 7));
            for (int period = 0; period < 3; period++) seedSpinTunnelLoaded(placement, 7, period);
            var post = pool.state(39); var pata = pool.state(40); var bank = placement.capture().savedFlags().copy();
            var section = f.runtime.sectionArtState(); var clock = f.frame.vIntClock();
            monitorEnvironment(f, 0x81, 0, 0, 0);
            var failure = assertThrows(CdPlayerFrameRuntime.UnsupportedFrame.class, () -> cameraArtBegin(f, 0));
            assertEquals(CdPlayerFrameRuntime.UnsupportedBoundary.SOURCE_FAMILY, failure.boundary());
            assertEquals(post, pool.state(39)); assertEquals(pata, pool.state(40));
            assertArrayEquals(bank, placement.capture().savedFlags().copy());
            assertEquals(section, f.runtime.sectionArtState()); assertEquals(clock, f.frame.vIntClock());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    // Catches effect replay, native sound invention, clearing saved flags, or executing the converted same slot twice.
    @Test void pataGoodFutureConvertsActualSlotSilentlyAndLaterUnknownFailureRetainsItsCompletedPrefix() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f);
            var declared = (CdOpeningObjectRuntime.Snapshot) horizontalReplacePartition(f.runtime.captureClosedCut(),
                    CdOpeningObjectRuntime.DevGlobals.class, new CdOpeningObjectRuntime.DevGlobals(1, 0));
            f.runtime.restore(declared); cameraArtBegin(f, 0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            pool.write(cameraPataMain(f, pool.state(39)).moveByte(SUBTYPE, 1).moveByte(SPRITE_FLAGS, 0x84).moveByte(SAVED_FLAGS_ID, 7));
            pool.write(pool.state(40).moveByte(ID, 0x7F)); seedSpinTunnelLoaded(f.runtime.placement(), 7, 1);
            var sounds = f.runtime.lastSoundRequests(); assertThrows(UnsupportedOperationException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            var s = f.slot(39); assertEquals(CdExplosion.OBJECT_ID, s.id()); assertEquals(0, s.routine());
            assertEquals(0x100, s.centreX()); assertEquals(0x780, s.centreY()); assertEquals(0, s.spriteFlags());
            assertEquals(0xD5, f.runtime.placement().savedFlag(7, 1), "good-future conversion does not change dirty saved members"); assertEquals(sounds, f.runtime.lastSoundRequests());
            assertEquals(0x7F, f.slot(40).id()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    // Catches restarting/tailing a source cycle that branched before camera/SAT/CPU PLC work.
    @Test void cameraArtLevelRestartCutOmitsTheSectionRequestAndPreparationDespiteLiveDynamicWork() throws Exception {
        try (var f = new Fixture(RESTART_LEVEL)) {
            seedLevelPlayer(f, 8, 1); var before = f.runtime.sectionArtState(); runRestartRow(f);
            assertTrue(f.runtime.endCycleAtLevelRestart()); assertEquals(before, f.runtime.sectionArtState());
            assertEquals(before, f.runtime.captureClosedCut().sectionArtState());
        }
    }

    // Catches a failed plane callback nevertheless decoding tiles or finishing its V-int.
    @Test void cameraArtPlaneFailureLeavesCameraPrefixUnservicedAndPreventsFinishOrRetry() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtCycle(f, 0x680, 0); var before = f.runtime.sectionArtState();
            f.runtime.setPreScrollForeground(PRE); var selected = f.frame.prepareInput(1); var clock = f.frame.vIntClock();
            var failure = new IllegalStateException("constructed plane boundary before camera service");
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> f.runtime.serviceVBlank(selected.phase(), () -> { throw failure; }, 0x0100)));
            assertEquals(before, f.runtime.sectionArtState()); assertEquals(clock, f.frame.vIntClock());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    // Catches preparing the next head during a held selector8 instead of the omitted CPU ProcessPLCs call.
    @Test void cameraArtPausedCompletionLeavesAntonUnpreparedAcrossFurtherSelectedServices() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtCycle(f, 0x680, 0);
            for (int held : List.of(0x80, 0, 0)) {
                f.frame.prepareInput(held); f.runtime.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE, () -> { }, 0x0100);
            }
            var done = f.runtime.sectionArtState(); assertEquals(0, done.remainingTilesWord());
            assertEquals(0x236E46L, done.queue().headSource()); assertEquals(0x8120, done.queue().headDestination());
            assertEquals(5, done.processBudgetWord(), "18-budget final14 ends before final decrement");
            f.frame.prepareInput(0); f.runtime.serviceVBlank(PlcLifecyclePhase.NORMAL_PAUSE, () -> { }, 0x0100);
            assertEquals(done, f.runtime.sectionArtState());
            assertThrows(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.OwnershipBoundary.class,
                    () -> com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(done.residency(), 0x409));
        }
    }

    // Catches arming a post-scroll section request before the CPU SAT actually finishes successfully.
    @Test void cameraArtSatBuildFailureNeverArmsOrRunsTheSectionTail() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtBegin(f, 0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots(); pool.write(cameraPataMain(f, pool.state(40)));
            f.runtime.runDynamicSlotsAfterReservedSlots(); var before = f.runtime.sectionArtState();
            pool.write(pool.state(40).moveLong(MAPPINGS, 0x12345678).moveWord(X, 0x780));
            var c = new CameraWords(0x680, PRE.yWord());
            assertThrows(UnsupportedOperationException.class, () -> f.runtime.finishAfterCamera(new DrawCameras(c, c, c)));
            assertEquals(before, f.runtime.sectionArtState()); assertTrue(f.runtime.closed());
            assertThrows(IllegalStateException.class, f.runtime::finishOrdinaryTail); assertEquals(before, f.runtime.sectionArtState());
        }
    }

    // Catches a swallowed plane reentry proceeding to camera writes/Finish after admission has already failed.
    @Test void cameraArtSwallowedPlaneReentryFailsBeforeCameraServiceAndFrameFinish() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtCycle(f, 0x680, 0); var before = f.runtime.sectionArtState();
            f.runtime.setPreScrollForeground(PRE); var selected = f.frame.prepareInput(0); var clock = f.frame.vIntClock();
            assertThrows(IllegalStateException.class, () -> f.runtime.serviceVBlank(selected.phase(), () -> {
                assertThrows(IllegalStateException.class, () -> f.runtime.setPreScrollForeground(PRE));
            }, 0x0100));
            assertEquals(before, f.runtime.sectionArtState()); assertEquals(clock, f.frame.vIntClock());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    /** Explicit DEV preload for the two inherited restart helpers, not a native carry/history assertion. */
    private static CdOpeningObjectRuntime.CameraArtRestartIngress cameraArtDevIngress(CdR11aAssets assets) throws java.io.IOException {
        var objects = assets.openingObjects(); var frontier = objects.frontierArtBank();
        var resident = com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.devSeed(objects.initialArtBank().binding(
                com.openggf.game.soniccd.level.CdR11aAsset.ART_MOSQUI).bytes(), frontier.binding(
                com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_PLANT).bytes(), frontier.binding(
                com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_RAMP_BOOST).bytes());
        // Explicit TEST ingress for the new eagerly validated nineteen-tile policy; never a restart default.
        var source = objects.sectionArtSources().source(com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER);
        for (int offset = 0; offset < source.decode().bytes().length(); offset += 32)
            resident = com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.writeTile(resident,
                    source.canonicalPlc().vramAddress() + offset, source.decode().bytes().slice(offset, 32));
        // Explicit TEST ingress for eagerly validated C25; not a production restart seed or native history.
        var diagonal = objects.sectionArtSources().source(com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING);
        for (int offset = 0; offset < diagonal.decode().bytes().length(); offset += 32)
            resident = com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.writeTile(resident,
                    diagonal.canonicalPlc().vramAddress() + offset, diagonal.decode().bytes().slice(offset, 32));
        // Explicit TEST ingress for eagerly validated C26; never a production restart seed or native history.
        var spikes = objects.sectionArtSources().source(com.openggf.game.soniccd.level.CdR11aAsset.ART_SPIKES);
        for (int offset = 0; offset < spikes.decode().bytes().length(); offset += 32)
            resident = com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.writeTile(resident,
                    spikes.canonicalPlc().vramAddress() + offset, spikes.decode().bytes().slice(offset, 32));
        return new CdOpeningObjectRuntime.CameraArtRestartIngress(resident, CdAssetBytes.copyOf(new byte[512]), 0);
    }

    private static void cameraArtIdle(Fixture f) {
        f.firstCycle(); f.makePlayerAbsent(); f.runtime.placement().pool().clearDynamicSlots();
    }
    private static void cameraArtBegin(Fixture f, int lockWord) {
        f.runtime.setPreScrollForeground(PRE); var selected = f.frame.prepareInput(0);
        assertEquals(PlcLifecyclePhase.ORDINARY_LEVEL, selected.phase());
        f.runtime.serviceVBlank(selected.phase(), () -> { }, lockWord);
    }
    private static void cameraArtFinish(Fixture f, int postX) {
        f.runtime.spawnBeforeReservedSlots(); f.body(2); f.runtime.runDynamicSlotsAfterReservedSlots();
        var c = new CameraWords(postX, PRE.yWord()); f.runtime.finishAfterCamera(new DrawCameras(c, c, c)); f.runtime.finishOrdinaryTail();
    }
    private static void cameraArtCycle(Fixture f, int postX, int lockWord) {
        cameraArtBegin(f, lockWord); cameraArtFinish(f, postX);
    }
    private static CdAssetBytes cameraArtSource(Fixture f, com.openggf.game.soniccd.level.CdR11aAsset source) throws java.io.IOException {
        return f.assets.openingObjects().sectionArtSources().source(source).decode().bytes();
    }
    private static void cameraArtBytes(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State state,
                                       int destination, CdAssetBytes expected) {
        assertEquals(0, expected.length() % 32);
        for (int offset = 0; offset < expected.length(); offset += 32) assertArrayEquals(expected.slice(offset, 32).copy(),
                com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(state, (destination + offset) >>> 5).copy(),
                "current physical tile $" + Integer.toHexString((destination + offset) >>> 5));
    }
    private static CdObjectPresentation.PatternSource cameraArtPixels(Fixture f) throws java.io.IOException {
        var objects = f.assets.openingObjects();
        return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.frame.artState(), f.runtime.powerupArtState(), objects,
                objects.frontierArtBank(), objects.contactArtBank(), objects.floatBlockArtBank(), objects.springArtBank(),
                objects.tunnelDoorArtBank(), objects.monitorTimeArtBank(), f.runtime.sectionArtState().residency(),
                BANKS.player(), BANKS.ring(), BANKS.mosqui(), BANKS.plant(), BANKS.ramp(), BANKS.explosion(), BANKS.points(), BANKS.flower(),
                BANKS.powerup(), BANKS.floatBlock(), BANKS.spring(), BANKS.tunnelDoorVertical(), BANKS.monitorTime());
    }
    private static CdObjectState cameraPataMain(Fixture f, CdObjectState s) throws java.io.IOException {
        return s.moveByte(ID, CdPataBata.OBJECT_ID).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4)
                .moveWord(X, 0x100).moveWord(Y, 0x780).moveWord(PATA_BATA_ANCHOR_X, 0x100)
                .moveLong(CdObjectState.LongField.PATA_BATA_ANCHOR_Y, 0x07808000).moveWord(PATA_BATA_SHIFT, 3)
                .moveWord(TILE, 0x23B0).moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 0x10).moveByte(PRIORITY, 3)
                .moveLong(MAPPINGS, f.assets.openingObjects().view(
                        com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.PATA_BATA_NORMAL_MAPPINGS).range().nativeAddress());
    }
    // END current-camera/Pata Runtime tests-first additions.

    // BEGIN Boulder Runtime R1 tests-first additions. All Java UNRUN by this source-only author.
    // N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Boulder/Main.asm:7-51;
    // Object Solid.asm:15-146,188-479; Palmtree Panic/Data (Act 1 Present).asm:85-100,138-143,209-214.
    // Every ingress is constructed over actual media/owners, not a World, route or native-entry certificate.
    // Private-commit adversarial cases reflect ONLY the runtime phase/dispatch/guard seam, use a real pure
    // Solid result, and inspect all pool/Frame stores before/after. They do not fabricate Result values.
    // Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>

    @Test void boulderFourteenthBankPreservesEveryOldConstructorAndTheOneAggregate() {
        var expected = new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374,
                413, 449, 464, 500, 512, 594);
        assertEquals(16, CdOpeningObjectRuntime.LogicalBanks.class.getRecordComponents().length);
        assertEquals(513, expected.boulder() + 19 - expected.player());
        assertEquals(expected, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138));
        assertEquals(expected, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236));
        assertEquals(expected, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374));
        assertEquals(expected, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413));
        assertEquals(expected, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449));
        assertEquals(expected, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449, 464));
        assertEquals(expected, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449, 464, 500));
        assertEquals(expected, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374, 413, 449, 464, 500, 512));
        assertEquals(982, new CdOpeningObjectRuntime.LogicalBanks(100, 124, 138, 208, 236, 299, 369, 374,
                413, 449, 464, 500, 900).boulder());
        assertEquals(1, java.util.Arrays.stream(CdOpeningObjectRuntime.class.getDeclaredFields())
                .filter(field -> field.getType() == CdBoulder.class).count());
        assertEquals(1, java.util.Arrays.stream(CdOpeningObjectRuntime.Snapshot.class.getDeclaredFields())
                .filter(field -> field.getType() == com.openggf.game.soniccd.resources.CdR11aSectionArt.State.class).count());
        assertTrue(java.util.Arrays.stream(CdOpeningObjectRuntime.Snapshot.class.getDeclaredFields()).noneMatch(field ->
                field.getType() == CdBoulder.class || field.getType() == CdBoulder.Result.class
                || field.getType() == CdBoulder.SolidCall.class
                || field.getType() == com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State.class));
        assertTrue(java.util.Arrays.stream(CdOpeningObjectRuntime.SoundRequest.class.getPermittedSubclasses())
                .noneMatch(type -> type.getSimpleName().contains("Boulder")));
    }

    @Test void boulderColdDevSeedsExactlyNineteenCompleteTilesFromTheSameSevenSourceBank() throws Exception {
        try (var f = new Fixture()) {
            var objects = f.assets.openingObjects(); var bank = objects.sectionArtSources();
            assertSame(bank, objects.sectionArtSources()); assertEquals(9, bank.sources().size());
            var source = bank.source(com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER);
            assertEquals(2, source.canonicalPlcId()); assertEquals(0, source.canonicalRowIndex());
            assertEquals(f.assets.loadPlan().plcs().get(2).entries().getFirst(), source.canonicalPlc());
            assertEquals(0x6E80, source.canonicalPlc().vramAddress()); assertEquals(19, source.tileCount());
            var state = f.runtime.sectionArtState(); var resident = state.residency();
            cameraArtBytes(resident, 0x6E80, source.decode().bytes());
            int known = 0;
            for (var region : com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.values())
                for (byte value : resident.region(region).knownMask().copy()) known += value;
            assertEquals((70 + 28 + 63 + 19 + 46 + 8) * 32, known, "legacy three preloads plus explicit Boulder, diagonal and Spikes DEV writes");
            var lower = resident.region(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_6E00);
            for (int offset = 0; offset < 0x600; offset++) {
                boolean expected = offset >= 0x80 && offset < 0x2E0;
                assertEquals(expected, lower.knownByte(offset).isPresent(), "lower known byte " + offset);
            }
            for (int nativeId : List.of(0x370, 0x373, 0x387, 0x388, 0x39B)) boulderUnknown(resident, nativeId);
            assertEquals(0, state.sectionWord()); assertEquals(0, state.remainingTilesWord());
            assertEquals(0, state.queue().headSource()); assertTrue(state.decoder().isEmpty());
            assertArrayEquals(new byte[512], state.codeTable().copy());
            var pixels = boulderPixels(f, resident);
            for (int i = 0; i < 19; i++) {
                var tile = pixels.resolve(0x374 + i); assertEquals(BANKS.boulder() + i, tile.logicalPatternId());
                assertEquals(CdObjectPresentation.patternVersion(source.decode().bytes().slice(i * 32, 32)), tile.version());
            }
        }
    }

    @Test void boulderAllFourteenSpansRejectEveryPairAndBothBoundsBeforeEmptySatAdmission() throws Exception {
        var assets = load(); var carry = cameraArtDevIngress(assets);
        int[] bases = {100,124,138,208,236,299,369,374,413,449,464,500,512,594};
        int[] counts = {24,14,70,28,63,70,5,39,36,15,36,12,82,19};
        var bad = new ArrayList<CdOpeningObjectRuntime.LogicalBanks>();
        for (int left = 0; left < 14; left++) for (int right = left + 1; right < 14; right++) {
            int[] overlap = bases.clone(); overlap[right] = bases[left]; bad.add(boulderBanks(overlap));
        }
        for (int index = 0; index < 14; index++) for (int value : new int[] {-1, Integer.MAX_VALUE - counts[index] + 2}) {
            int[] invalid = bases.clone(); invalid[index] = value; bad.add(boulderBanks(invalid));
        }
        assertEquals(119, bad.size());
        try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0)) {
            for (var banks : bad) {
                assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                        assets, frame, INITIAL, CONTACT, SCORE, LEVEL, new byte[640], banks, PRE, value -> { }));
                assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(
                        assets, frame, INITIAL, CONTACT, SCORE, LEVEL, CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]),
                        new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]), carry, new byte[640], banks, PRE, value -> { }));
                assertFalse(frame.closed()); assertEquals(0, frame.vIntClock().deltaSinceAdmittedCut());
            }
        }
    }

    @Test void boulderFreshInitDrawBuildThenNextMainUsesActualVisibilityAndSharedSolid() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 2, 0, 0, 0x123, 0x100, 0x01001234, 0x075D5678);
            var image = boulderImage(f, pool.state(40), 0, 0).moveWord(X_FRACTION, 0xA55A).moveWord(Y_FRACTION, 0x5AA5);
            pool.write(image); var actor = f.frame.state(); var env = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(boulderInit(f, image), f.slot(40)); assertEquals(actor, f.frame.state());
            assertEquals(env, f.frame.environment()); assertEquals(sounds, f.runtime.lastSoundRequests());
            var sat = f.runtime.finishAfterCamera(POST); assertEquals(2, sat.provenance().stream().filter(p -> p.slotRef().wholeIndex() == 40).count());
            int offset = satEntry(sat, 40); assertEquals(0x374, word(sat.bytes(), offset + 4));
            assertEquals(0x377, word(sat.bytes(), offset + 12)); assertEquals(0x84, f.slot(40).spriteFlags());
            f.runtime.finishOrdinaryTail();
            f.service(0); assertEquals(CdObjectPresentation.convert(sat, boulderPixels(f, f.runtime.sectionArtState().residency())), f.runtime.displayedFrame());
            assertEquals(19, f.runtime.displayedFrame().patternVersions().keySet().stream()
                    .filter(id -> id >= BANKS.boulder() && id < BANKS.boulder() + 19).count());
            f.runtime.spawnBeforeReservedSlots(); f.body(3); pool.clearDynamicSlots();
            seedSolidPlayer(f, 2, 0, 0, 0x123, 0x100, 0x01001234, 0x075D5678);
            pool.write(boulderInit(f, image).moveByte(SPRITE_FLAGS, 0x84));
            actor = f.frame.state(); env = f.frame.environment(); var expected = boulderSolid(f, f.slot(40));
            assertEquals(CdSolidObject.Path.STAND_ON, expected.path()); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertTunnelDoorSolidApplied(f, actor, env, expected); assertEquals(40, f.frame.state().objectLinks().standObject());
            assertEquals(0x5678, f.frame.state().motion().centreY16_16() & 0xFFFF); monitorFinish(f);
        }
    }

    @Test void boulderDirtyInitPublishesSharedFractionsGlobalsAndOtherSlotBeforeLaterConsumers() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 8, 41, 0, 0x234, 0x100, 0x01008000, 0x075E1234);
            seedTunnelDoorGlobals(f, 1, 0x3456, 0x55, 0x66);
            var dirty = boulderImage(f, pool.state(40), 0, 0x80).moveByte(WIDTH, 1).moveByte(Y_RADIUS, 2)
                    .moveByte(PRIORITY, 7).moveWord(TILE, 0xDEAD).moveByte(ROUTINE_2, 0xA5)
                    .moveWord(X_VELOCITY, 0x123).moveWord(Y_FRACTION, 0xBEEF);
            var olderStand = boulderImage(f, pool.state(41), 2, 4).moveByte(FLAGS, 0xA8);
            pool.write(dirty); pool.write(olderStand);
            pool.write(spring(f, pool.state(42), 2, 4, 0, 0).moveByte(ROUTINE_2, 5)
                    .moveWord(X_FRACTION, 0x4567).moveWord(Y_FRACTION, 0x89AB));
            var untouched = pool.state(127).moveWord(TUNNEL_DOOR_TIMER, 0xDEAD); pool.write(untouched);
            var actor = f.frame.state(); var env = f.frame.environment(); var display = f.frame.display();
            var bank = horizontalReserved(f.runtime); var sounds = f.runtime.lastSoundRequests();
            var expected = boulderSolid(f, boulderInit(f, dirty));
            assertEquals(CdSolidObject.Path.STAND_ON, expected.path());
            assertEquals(List.of(41), expected.otherSlots().stream().map(s -> s.identity().wholeIndex()).toList());
            assertTrue(expected.globalWrites().contains(CdSolidObject.GlobalField.PRIMARY_ANGLE));
            assertTrue(expected.globalWrites().contains(CdSolidObject.GlobalField.SECONDARY_ANGLE));
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertTunnelDoorSolidApplied(f, actor, env, expected);
            assertEquals(0xA300, f.frame.state().motion().centreX16_16() & 0xFFFF);
            assertEquals(0x1234, f.frame.state().motion().centreY16_16() & 0xFFFF);
            assertEquals(expected.object().moveByte(SPRITE_FLAGS, 4), f.slot(40));
            assertEquals(olderStand.moveByte(FLAGS, 0xA0), f.slot(41));
            assertEquals(expected.player().actor().motion().centreX16_16() >>> 16, f.slot(42).centreX());
            assertEquals(expected.player().actor().motion().centreY16_16() >>> 16, f.slot(42).centreY());
            assertEquals(0x4567, f.slot(42).word(X_FRACTION)); assertEquals(0x89AB, f.slot(42).word(Y_FRACTION));
            assertEquals(untouched, f.slot(127)); assertEquals(display, f.frame.display());
            assertEquals(bank, horizontalReserved(f.runtime)); assertEquals(sounds, f.runtime.lastSoundRequests()); monitorFinish(f);
        }
    }

    @Test void boulderSidePushAndDetachUseSharedPhysicalLinksFractionsAndOneChargeStop() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 0, 0, 0, 0x600, 0, 0x00E71234, 0x07805678);
            pool.write(boulderImage(f, pool.state(46), 2, 0x84));
            var actor = f.frame.state(); var env = f.frame.environment(); var expected = boulderSolid(f, f.slot(46));
            assertEquals(CdSolidObject.Path.HALT_ON_X, expected.path()); assertEquals(46, expected.player().pushObject());
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertTunnelDoorSolidApplied(f, actor, env, expected);
            assertEquals(0x00E61234, f.frame.state().motion().centreX16_16());
            assertEquals(0, f.frame.state().motion().xVelocity()); assertEquals(0x20, f.slot(46).flags() & 0x20); monitorFinish(f);
        }
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 8, 40, 1, 0x123, 0, 0x02001234, 0x07805678);
            pool.write(boulderImage(f, pool.state(40), 2, 0x84).moveByte(FLAGS, 8));
            pool.write(boulderImage(f, pool.state(41), 2, 0x84));
            var actor = f.frame.state(); var env = f.frame.environment(); var expected = boulderSolid(f, f.slot(40));
            assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP), expected.fmRequests());
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests()); horizontalSharedSounds(sounds, expected);
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertTunnelDoorSolidApplied(f, actor, env, expected);
            assertEquals(0, f.frame.state().objectLinks().standObject()); assertEquals(0, f.slot(40).flags() & 8);
            assertEquals(sounds, f.runtime.lastSoundRequests(), "one helper sound, no Boulder sound type or metadata replay"); monitorFinish(f);
        }
    }

    @Test void boulderSharedKillFmAndCompletedPrefixSurviveTheLaterPhysicalUnknown() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 0, 0, 0, 0x123, 0, 0x01008000, 0x07991234);
            seedTunnelDoorGlobals(f, 1, 0x3456, 0x55, 0x66);
            pool.write(boulderImage(f, pool.state(40), 2, 0x84).moveWord(Y_VELOCITY, 0x100));
            pool.write(pool.state(41).moveByte(ID, 0x7E));
            var actor = f.frame.state(); var env = f.frame.environment(); var expected = boulderSolid(f, f.slot(40));
            assertEquals(CdSolidObject.Path.HIT_BOTTOM, expected.path()); assertEquals(6, expected.player().actor().motion().routine());
            assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_HURT), expected.fmRequests());
            var sounds = new ArrayList<>(f.runtime.lastSoundRequests()); horizontalSharedSounds(sounds, expected);
            var failure = assertThrows(UnsupportedOperationException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertTrue(failure.getMessage().contains("41")); assertTrue(failure.getMessage().contains("7e"));
            assertTunnelDoorSolidApplied(f, actor, env, expected); assertEquals(expected.object().moveByte(SPRITE_FLAGS, 4), f.slot(40));
            assertEquals(sounds, f.runtime.lastSoundRequests()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class, f.runtime::runDynamicSlotsAfterReservedSlots);
        }
    }

    @Test void boulderOuterVisibilityGatesOnlyItsOwnSolidAndGoodFutureNeverConvertsIt() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f);
            f.runtime.restore((CdOpeningObjectRuntime.Snapshot) horizontalReplacePartition(f.runtime.captureClosedCut(),
                    CdOpeningObjectRuntime.DevGlobals.class, new CdOpeningObjectRuntime.DevGlobals(1, 1)));
            cameraArtBegin(f, 0); f.runtime.spawnBeforeReservedSlots(); f.body(2); var pool = f.runtime.placement().pool(); pool.clearDynamicSlots();
            seedSolidPlayer(f, 8, 5, 0, 0, 0, 0x01001234, 0x075D5678);
            pool.write(boulderImage(f, pool.state(40), 0, 0));
            var actor = f.frame.state(); var env = f.frame.environment();
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertEquals(CdBoulder.OBJECT_ID, f.slot(40).id());
            assertEquals(actor, f.frame.state()); assertEquals(env, f.frame.environment()); monitorFinish(f);
        }
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 8, 5, 0, 0, 0, 0x01001234, 0x075D5678);
            var dirty = boulderImage(f, pool.state(40), 0, 0x80); pool.write(dirty);
            var actor = f.frame.state(); var env = f.frame.environment();
            var failure = assertThrows(CdSolidObject.UnsupportedSolidObject.class, f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS, failure.boundary());
            assertEquals(boulderInit(f, dirty), f.slot(40)); assertEquals(actor, f.frame.state()); assertEquals(env, f.frame.environment());
            assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    @Test void boulderRestartCarriesChangedNineteenValuesAndEveryMaskWithoutReseedingOrService() throws Exception {
        try (var f = new Fixture()) {
            var resident = f.runtime.sectionArtState().residency();
            for (int tile = 0; tile < 19; tile++) {
                byte[] bytes = new byte[32]; java.util.Arrays.fill(bytes, (byte) (0x81 + tile));
                resident = com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.writeTile(resident,
                        0x6E80 + tile * 32, CdAssetBytes.copyOf(bytes));
            }
            byte[] table = new byte[512]; for (int i = 0; i < table.length; i++) table[i] = (byte) (i ^ 0xAC);
            var ingress = new CdOpeningObjectRuntime.CameraArtRestartIngress(resident, CdAssetBytes.copyOf(table), 0x005A);
            var carried = resident;
            try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(f.assets, 0)) {
                var publications = new ArrayList<CdPlayerArtRuntime.State>(); frame.bindArtPublication((request, state) -> publications.add(state));
                try (var runtime = CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(f.assets, frame, INITIAL, CONTACT, SCORE, LEVEL,
                        CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]), new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]),
                        ingress, new byte[640], BANKS, PRE, value -> fail("restart has no angle callback"))) {
                    assertSame(carried, runtime.sectionArtState().residency()); assertArrayEquals(table, runtime.sectionArtState().codeTable().copy());
                    assertEquals(0x005A, runtime.sectionArtState().sectionWord()); assertEquals(0, runtime.sectionArtState().remainingTilesWord());
                    assertEquals(0, runtime.sectionArtState().queue().headSource()); assertTrue(runtime.sectionArtState().decoder().isEmpty());
                    for (var region : com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.values())
                        assertEquals(carried.region(region), runtime.sectionArtState().residency().region(region));
                    boulderUnknown(carried, 0x373); boulderUnknown(carried, 0x387); boulderUnknown(carried, 0x388);
                    var pixels = boulderPixels(f, carried);
                    for (int tile = 0; tile < 19; tile++) assertEquals(CdObjectPresentation.patternVersion(
                            com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(carried, 0x374 + tile)), pixels.resolve(0x374 + tile).version());
                    assertTrue(publications.isEmpty()); assertEquals(0, frame.vIntClock().deltaSinceAdmittedCut());
                    assertTrue(runtime.displayedFrame().tiles().isEmpty());
                }
            }
        }
    }

    @Test void boulderRestartWithUnknownBoulderMaskStopsWithoutManufacturingKnownSourceBytes() throws Exception {
        var assets = load(); var objects = assets.openingObjects(); var frontier = objects.frontierArtBank();
        var sparse = com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.devSeed(objects.initialArtBank().binding(
                com.openggf.game.soniccd.level.CdR11aAsset.ART_MOSQUI).bytes(), frontier.binding(
                com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_PLANT).bytes(), frontier.binding(
                com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_RAMP_BOOST).bytes());
        for (int tile = 0; tile < 18; tile++) {
            byte[] changed = new byte[32]; java.util.Arrays.fill(changed, (byte) (0xA1 + tile));
            sparse = com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.writeTile(sparse,
                    0x6E80 + tile * 32, CdAssetBytes.copyOf(changed));
        }
        var partial = sparse;
        var mask = partial.region(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_6E00).knownMask();
        var carry = new CdOpeningObjectRuntime.CameraArtRestartIngress(partial, CdAssetBytes.copyOf(new byte[512]), 0);
        try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0)) {
            var failure = assertThrows(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.OwnershipBoundary.class,
                    () -> CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(assets, frame, INITIAL, CONTACT, SCORE, LEVEL,
                            CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]), new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]),
                            carry, new byte[640], BANKS, PRE, value -> { }));
            assertEquals(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN, failure.boundary());
            assertEquals(0x386, failure.nativePatternId()); assertFalse(frame.closed()); assertEquals(0, frame.vIntClock().deltaSinceAdmittedCut());
            assertArrayEquals(mask.copy(), partial.region(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_6E00).knownMask().copy()); boulderUnknown(partial, 0x386);
        }
    }

    @Test void boulderRelocatedAntonWritesEighteenOverlapsAtSelectedServiceAndReplaysPartialGeneration() throws Exception {
        try (var f = new Fixture()) {
            cameraArtIdle(f); cameraArtBegin(f, 0); f.runtime.spawnBeforeReservedSlots(); f.body(2);
            var pool = f.runtime.placement().pool(); pool.clearDynamicSlots(); pool.write(boulderImage(f, pool.state(40), 0, 0));
            f.runtime.runDynamicSlotsAfterReservedSlots(); pool.write(f.slot(40).moveWord(X, 0x2080));
            var c = new CameraWords(0x1980, PRE.yWord()); var sat = f.runtime.finishAfterCamera(new DrawCameras(c, c, c));
            f.runtime.finishOrdinaryTail(); assertEquals(22, f.runtime.sectionArtState().remainingTilesWord());
            assertEquals(0x6E00, f.runtime.sectionArtState().queue().headDestination());
            var boulder = cameraArtSource(f, com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER);
            var anton = cameraArtSource(f, com.openggf.game.soniccd.level.CdR11aAsset.ART_ANTON);
            f.service(0x80); assertTrue(f.frame.paused()); var first = f.runtime.displayedFrame(); var oldVersions = first.patternVersions();
            for (int tile = 0; tile < 19; tile++) assertEquals(CdObjectPresentation.patternVersion(boulder.slice(tile * 32, 32)), oldVersions.get(BANKS.boulder() + tile));
            f.service(0); var partial = f.runtime.displayedFrame();
            assertArrayEquals(sat.bytes(), f.runtime.cpuSat().bytes()); assertEquals(16, f.runtime.sectionArtState().remainingTilesWord());
            for (int tile = 0; tile < 19; tile++) assertEquals(CdObjectPresentation.patternVersion(
                    tile < 2 ? anton.slice((tile + 4) * 32, 32) : boulder.slice(tile * 32, 32)), partial.patternVersions().get(BANKS.boulder() + tile));
            assertEquals(CdObjectPresentation.convert(sat, boulderPixels(f, f.runtime.sectionArtState().residency())), partial);
            var actor = f.frame.captureActor(); var shared = f.frame.captureShared(); var objects = f.runtime.captureClosedCut();
            for (int i = 0; i < 6; i++) f.service(0);
            var complete = f.runtime.captureClosedCut(); var picture = f.runtime.displayedFrame();
            assertEquals(0, f.runtime.sectionArtState().remainingTilesWord()); assertEquals(0x7100, f.runtime.sectionArtState().queue().headDestination());
            for (int tile = 0; tile < 19; tile++) assertEquals(CdObjectPresentation.patternVersion(
                    tile < 18 ? anton.slice((tile + 4) * 32, 32) : boulder.slice(tile * 32, 32)), picture.patternVersions().get(BANKS.boulder() + tile));
            assertEquals(oldVersions, first.patternVersions()); boulderUnknown(f.runtime.sectionArtState().residency(), 0x388);
            f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(objects);
            f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(objects);
            assertEquals(objects.sectionArtState(), f.runtime.sectionArtState()); assertEquals(objects.reserved().slots(), horizontalReserved(f.runtime));
            assertSame(partial, f.runtime.displayedFrame());
            for (int i = 0; i < 6; i++) f.service(0);
            assertMonitorSnapshot(complete, f.runtime.captureClosedCut()); assertEquals(complete.sectionArtState(), f.runtime.sectionArtState());
            assertEquals(complete.reserved().slots(), horizontalReserved(f.runtime)); assertArrayEquals(sat.bytes(), f.runtime.cpuSat().bytes());
            assertEquals(picture, f.runtime.displayedFrame());
            var stable = f.runtime.displayedFrame(); f.frame.prepareInput(0); f.runtime.serviceVBlank(PlcLifecyclePhase.LAG);
            assertSame(stable, f.runtime.displayedFrame()); assertEquals(complete.sectionArtState(), f.runtime.sectionArtState());
        }
    }

    @Test void boulderClosedStandingCutRestoresAndReplaysTheNextRealBodyAndWholePool() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 2, 0, 0, 0, 0x100, 0x01001234, 0x075D5678);
            pool.write(boulderImage(f, pool.state(40), 2, 0x84)); f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f);
            assertEquals(40, f.frame.state().objectLinks().standObject());
            var actor = f.frame.captureActor(); var shared = f.frame.captureShared(); var cut = f.runtime.captureClosedCut();
            f.service(0); f.runtime.spawnBeforeReservedSlots(); assertEquals(CdPlayerBody.Boundary.MAIN_RETURN, f.body(3).result().boundary());
            f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f);
            var next = f.runtime.captureClosedCut(); var nextActor = f.frame.captureActor(); var nextShared = f.frame.captureShared(); var cpu = f.runtime.cpuSat().bytes();
            f.frame.validateActorRestore(actor); f.frame.validateSharedRestore(shared); f.runtime.validateRestore(cut);
            f.frame.restoreActor(actor); f.frame.restoreShared(shared); f.runtime.restore(cut);
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(3); f.runtime.runDynamicSlotsAfterReservedSlots(); monitorFinish(f);
            assertMonitorSnapshot(next, f.runtime.captureClosedCut()); assertEquals(next.sectionArtState(), f.runtime.sectionArtState());
            assertEquals(next.reserved().slots(), horizontalReserved(f.runtime)); assertEquals(nextActor, f.frame.captureActor());
            assertEquals(nextShared.environment(), f.frame.environment()); assertSpinTunnelArt(nextShared.artState(), f.frame.artState());
            assertArrayEquals(cpu, f.runtime.cpuSat().bytes());
        }
    }

    @Test void boulderPrivateCommitRejectsWrongPhaseEveryMutualGuardAndWrongPhysicalVisitBeforeStores() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 2, 0, 0, 0, 0x100, 0x01001234, 0x075D5678);
            pool.write(boulderImage(f, pool.state(40), 2, 0x84)); var call = boulderCall(f, pool.state(40));
            boulderRejectCommit(f, call); // Actual PLAYER_DONE, not the admitted callback phase.
            boulderSet(f.runtime, "phase", CdOpeningObjectRuntime.Phase.DYNAMIC_SLOTS); boulderSet(f.runtime, "operating", true);
            try {
                boulderSet(f.runtime, "dispatchWholeIndex", 41); boulderRejectCommit(f, call); boulderSet(f.runtime, "dispatchWholeIndex", 40);
                for (String guard : List.of("inDrawCallback", "inSolidCommit", "inSpringLaunch", "inSpinTunnelCommit", "inTunnelDoorFm",
                        "inHorizontalTunnelDoorSolid", "inHorizontalTunnelDoorFm", "inMonitorAngle", "inMonitorFm", "inMonitorAward",
                        "inTimePostDirection", "inTimePostSubCpu")) {
                    boulderSet(f.runtime, guard, true); boulderRejectCommit(f, call); boulderSet(f.runtime, guard, false);
                }
            } finally { boulderUnstage(f); }
        }
    }

    @Test void boulderPrivateCommitPreflightsFullNinetySixImagesForeignOwnershipAndExactFrameCas() throws Exception {
        for (int scenario = 0; scenario < 7; scenario++) try (var f = new Fixture(); var foreign = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSolidPlayer(f, 8, 41, 0, 0x234, 0x100, 0x01008000, 0x075E1234);
            pool.write(boulderImage(f, pool.state(40), 2, 0x84));
            pool.write(boulderImage(f, pool.state(41), 2, 4).moveByte(FLAGS, 8));
            var call = boulderCall(f, pool.state(40));
            if (scenario == 0) pool.write(pool.state(127).moveWord(TUNNEL_DOOR_TIMER, 0xA55A)); // Unchanged-result slot still CAS-protected.
            if (scenario == 1) pool.write(pool.state(40).moveByte(ID, 0x7E));
            if (scenario == 2) { // Real foreign pure result/cut, never a copied or fabricated Result.
                var otherPool = foreign.runtime.placement().pool(); otherPool.write(boulderImage(foreign, otherPool.state(40), 2, 0x84));
                call = boulderCall(foreign, otherPool.state(40));
            }
            if (scenario == 3) seedSolidPlayer(f, 8, 41, 0, 0x234, 0x100, 0x01008001, 0x075E1234);
            if (scenario == 4) seedTunnelDoorGlobals(f, 0, 0xA55A, 0x12, 0x34);
            if (scenario == 5 || scenario == 6) {
                var images = new ArrayList<>(call.callCut().slots());
                if (scenario == 5) images.removeLast();
                else images.set(95, foreign.runtime.placement().pool().state(127));
                var malformed = (CdObjectPool.Snapshot) horizontalReplacePartition(call.callCut(), List.class, images);
                call = new CdBoulder.SolidCall(malformed, call.expectedPlayer(), call.expectedGlobals(), call.result());
            }
            boulderSet(f.runtime, "phase", CdOpeningObjectRuntime.Phase.DYNAMIC_SLOTS); boulderSet(f.runtime, "operating", true);
            boulderSet(f.runtime, "dispatchWholeIndex", 40);
            try { boulderRejectCommit(f, call); } finally { boulderUnstage(f); }
        }
    }

    @Test void boulderSharedCommitFlagRejectsEveryExistingDrawOrCallbackBeforeAnyStores() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); pool.write(boulderImage(f, pool.state(40), 2, 0x84));
            boulderSet(f.runtime, "phase", CdOpeningObjectRuntime.Phase.DYNAMIC_SLOTS); boulderSet(f.runtime, "operating", true);
            boulderSet(f.runtime, "dispatchWholeIndex", 40); boulderSet(f.runtime, "inSolidCommit", true);
            var before = pool.capture().slots(); var actor = f.frame.state(); var env = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            try {
                for (String name : List.of("commitTopSolid", "commitSpringSolid", "commitSpringLaunch", "playSpringFm",
                        "commitTunnelDoorVerticalSolid", "playTunnelDoorVerticalFm", "preflightTunnelDoorSolid", "commitTunnelDoorSolid",
                        "playTunnelDoorFm", "commitMonitorSolid", "playMonitorFm", "awardMonitorItem", "commitTimePostDirection", "requestTimePostSubCpu")) {
                    var method = java.util.Arrays.stream(CdOpeningObjectRuntime.class.getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
                    method.setAccessible(true);
                    var fault = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(f.runtime, new Object[] {null}));
                    assertInstanceOf(IllegalStateException.class, fault.getCause(), name);
                }
                var draw = CdOpeningObjectRuntime.class.getDeclaredMethod("drawDynamic", CdObjectPool.class, int.class); draw.setAccessible(true);
                var fault = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> draw.invoke(f.runtime, pool, 40));
                assertInstanceOf(IllegalStateException.class, fault.getCause());
                assertEquals(before, pool.capture().slots()); assertEquals(actor, f.frame.state()); assertEquals(env, f.frame.environment());
                assertEquals(sounds, f.runtime.lastSoundRequests());
            } finally { boulderSet(f.runtime, "inSolidCommit", false); boulderUnstage(f); }
        }
    }

    @Test void boulderFullPhysicalPoolRunsEveryOwnedInitWithoutAllocatingOrInventingSound() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); var actor = f.frame.state(); var env = f.frame.environment();
            for (int whole = 32; whole < 128; whole++) pool.write(boulderImage(f, pool.state(whole), 0, 0));
            var sounds = f.runtime.lastSoundRequests(); assertTrue(pool.findObjSlot().isEmpty());
            f.runtime.runDynamicSlotsAfterReservedSlots();
            for (int whole = 32; whole < 128; whole++) {
                assertEquals(CdBoulder.OBJECT_ID, pool.state(whole).id()); assertEquals(2, pool.state(whole).routine());
                assertEquals(4, pool.state(whole).spriteFlags()); assertEquals(0x374, pool.state(whole).word(TILE));
            }
            assertTrue(pool.findObjSlot().isEmpty()); assertEquals(actor, f.frame.state()); assertEquals(env, f.frame.environment());
            assertEquals(sounds, f.runtime.lastSoundRequests()); monitorFinish(f);
        }
    }

    private static CdOpeningObjectRuntime.LogicalBanks boulderBanks(int[] b) {
        return new CdOpeningObjectRuntime.LogicalBanks(b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13]);
    }
    private static CdObjectState boulderImage(Fixture f, CdObjectState image, int routine, int sprite) throws java.io.IOException {
        var assets = CdBoulder.Assets.fromObjectAssets(f.assets.openingObjects());
        return image.moveByte(ID, CdBoulder.OBJECT_ID).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, sprite)
                .moveLong(MAPPINGS, assets.mappingsAddress()).moveWord(TILE, 0x374).moveByte(WIDTH, 16).moveByte(Y_RADIUS, 16)
                .moveByte(PRIORITY, 4).moveWord(X, 0x100).moveWord(Y, 0x780);
    }
    private static CdObjectState boulderInit(Fixture f, CdObjectState image) throws java.io.IOException {
        var assets = CdBoulder.Assets.fromObjectAssets(f.assets.openingObjects());
        return image.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, image.spriteFlags() | 4).moveByte(PRIORITY, 4)
                .moveLong(MAPPINGS, assets.mappingsAddress()).moveByte(WIDTH, 16).moveByte(Y_RADIUS, 16).moveByte(MAP_FRAME, 0)
                .moveWord(TILE, com.openggf.game.soniccd.level.CdR11aObjectAssets.decodeTileWord(assets.objectTileIds().copy(), 0xB, image.subtype2()));
    }
    private static CdSolidObject.Result boulderSolid(Fixture f, CdObjectState image) {
        var env = f.frame.environment();
        return CdSolidObject.fromAssets(f.assets).run(CdSolidObject.Entry.SOLID_OBJECT, image, tunnelDoorPlayer(f),
                new CdSolidObject.Gates(env.globals().debugMode(), env.movement().gates().miniSonic()), tunnelDoorGlobals(f), f.runtime.placement().pool().capture());
    }
    private static CdBoulder.SolidCall boulderCall(Fixture f, CdObjectState image) {
        var pool = f.runtime.placement().pool(); var cut = pool.capture(); var env = f.frame.environment();
        var player = tunnelDoorPlayer(f); var globals = tunnelDoorGlobals(f);
        var result = CdSolidObject.fromAssets(f.assets).run(CdSolidObject.Entry.SOLID_OBJECT, image, player,
                new CdSolidObject.Gates(env.globals().debugMode(), env.movement().gates().miniSonic()), globals, cut);
        return new CdBoulder.SolidCall(cut, player, globals, result);
    }
    private static void boulderUnknown(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State state, int tile) {
        var fault = assertThrows(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.OwnershipBoundary.class,
                () -> com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(state, tile));
        assertEquals(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN, fault.boundary());
    }
    private static CdObjectPresentation.PatternSource boulderPixels(Fixture f,
            com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State resident) throws java.io.IOException {
        var objects = f.assets.openingObjects();
        return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.frame.artState(), f.runtime.powerupArtState(), objects,
                objects.frontierArtBank(), objects.contactArtBank(), objects.floatBlockArtBank(), objects.springArtBank(),
                objects.tunnelDoorArtBank(), objects.monitorTimeArtBank(), resident, BANKS.player(), BANKS.ring(), BANKS.mosqui(),
                BANKS.plant(), BANKS.ramp(), BANKS.explosion(), BANKS.points(), BANKS.flower(), BANKS.powerup(), BANKS.floatBlock(),
                BANKS.spring(), BANKS.tunnelDoorVertical(), BANKS.monitorTime(), BANKS.boulder());
    }
    private static void boulderSet(CdOpeningObjectRuntime runtime, String name, Object value) throws Exception {
        var field = CdOpeningObjectRuntime.class.getDeclaredField(name); field.setAccessible(true); field.set(runtime, value);
    }
    private static void boulderUnstage(Fixture f) throws Exception {
        boulderSet(f.runtime, "operating", false); boulderSet(f.runtime, "phase", CdOpeningObjectRuntime.Phase.PLAYER_DONE);
        boulderSet(f.runtime, "dispatchWholeIndex", -1);
    }
    private static void boulderRejectCommit(Fixture f, CdBoulder.SolidCall call) throws Exception {
        var pool = f.runtime.placement().pool(); var before = pool.capture().slots(); var actor = f.frame.state();
        var env = f.frame.environment(); var sounds = f.runtime.lastSoundRequests(); var resident = f.runtime.sectionArtState();
        var method = CdOpeningObjectRuntime.class.getDeclaredMethod("commitBoulderSolid", CdBoulder.SolidCall.class); method.setAccessible(true);
        var fault = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(f.runtime, call));
        assertTrue(fault.getCause() instanceof IllegalArgumentException || fault.getCause() instanceof IllegalStateException);
        assertEquals(before, pool.capture().slots()); assertEquals(actor, f.frame.state()); assertEquals(env, f.frame.environment());
        assertEquals(sounds, f.runtime.lastSoundRequests()); assertSame(resident, f.runtime.sectionArtState());
    }
    // END Boulder Runtime R1 tests-first additions.

    // BEGIN diagonal Spring Runtime R1 tests-first additions. ALL JAVA UNRUN by this author.
    // Immutable J=a18ca432d4605ba8fb19ec10a8da2a17ae3350c3; N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
    // ObjSpring_Init/Main_Diag/Anim_Diag/Reset_Diag (Spring/Main.asm:186-239,416-481).
    // Constructed external device ingress over actual media, not a natural route/World/entry certificate.
    // Real pure Solid results are comparison oracles; no production physics or Result is fabricated.
    // Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>

    @Test void diagonalFifteenthBankAppendsAfterEveryLegacyBaseWithoutAnotherOwnerOrSoundType() {
        var full = new CdOpeningObjectRuntime.LogicalBanks(100,124,138,208,236,299,369,374,413,449,464,500,512,594,613);
        var old14 = new CdOpeningObjectRuntime.LogicalBanks(100,124,138,208,236,299,369,374,413,449,464,500,512,594);
        assertEquals(full, old14); assertEquals(full, new CdOpeningObjectRuntime.LogicalBanks(100,124,138));
        assertEquals(16, CdOpeningObjectRuntime.LogicalBanks.class.getRecordComponents().length);
        assertEquals("diagonalSpring", CdOpeningObjectRuntime.LogicalBanks.class.getRecordComponents()[14].getName());
        assertEquals(559, full.diagonalSpring() + 46 - full.player()); assertEquals(12475, BANKS.diagonalSpring());
        assertEquals(919, new CdOpeningObjectRuntime.LogicalBanks(100,124,138,208,236,299,369,374,413,449,464,500,512,900).diagonalSpring());
        assertEquals(1, java.util.Arrays.stream(CdOpeningObjectRuntime.class.getDeclaredFields()).filter(v -> v.getType() == CdSpring.class).count());
        assertTrue(java.util.Arrays.stream(CdOpeningObjectRuntime.Snapshot.class.getDeclaredFields()).noneMatch(v ->
                v.getType() == CdSpring.class || v.getType() == CdSpring.Result.class || v.getType() == CdSpring.SolidCall.class
                        || v.getType() == CdSpring.LaunchCall.class || v.getType() == com.openggf.game.soniccd.player.CdPlayerMath.class
                        || v.getType() == com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State.class));
        assertEquals(1, java.util.Arrays.stream(CdOpeningObjectRuntime.Snapshot.class.getDeclaredFields()).filter(v ->
                v.getType() == com.openggf.game.soniccd.resources.CdR11aSectionArt.State.class).count());
        assertEquals(1, java.util.Arrays.stream(CdOpeningObjectRuntime.SoundRequest.class.getPermittedSubclasses())
                .filter(v -> v == CdOpeningObjectRuntime.SpringFm.class).count());
    }

    @Test void diagonalAllFifteenRangesRejectEveryPairAndBothEndsAtColdAndRestartBeforeFrameMutation() throws Exception {
        var assets = load(); var carry = cameraArtDevIngress(assets);
        int[] bases = {100,124,138,208,236,299,369,374,413,449,464,500,512,594,613};
        int[] sizes = {24,14,70,28,63,70,5,39,36,15,36,12,82,19,46};
        var invalid = new ArrayList<CdOpeningObjectRuntime.LogicalBanks>();
        for (int left = 0; left < 15; left++) for (int right = left + 1; right < 15; right++) {
            int[] next = bases.clone(); next[right] = bases[left]; invalid.add(diagonalBanks(next));
        }
        for (int index = 0; index < 15; index++) for (int bad : new int[] {-1, Integer.MAX_VALUE - sizes[index] + 2}) {
            int[] next = bases.clone(); next[index] = bad; invalid.add(diagonalBanks(next));
        }
        assertEquals(135, invalid.size());
        try (var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets, 0)) {
            var actor = frame.captureActor(); var shared = frame.captureShared();
            for (var banks : invalid) {
                assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                        assets, frame, INITIAL, CONTACT, SCORE, LEVEL, new byte[640], banks, PRE, value -> fail("no callback")));
                assertThrows(IllegalArgumentException.class, () -> CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(
                        assets, frame, INITIAL, CONTACT, SCORE, LEVEL, CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]),
                        new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]), carry, new byte[640], banks, PRE, value -> fail("no callback")));
                assertEquals(actor, frame.captureActor()); assertEquals(shared.environment(), frame.environment());
                assertSame(shared.artState(), frame.artState()); assertEquals(shared.vIntClock(), frame.vIntClock()); assertFalse(frame.closed());
            }
        }
    }

    @Test void diagonalColdSeedUsesExactlyTheEighthCanonicalSourceAndFortySixCompleteCurrentTiles() throws Exception {
        try (var f = new Fixture()) {
            var objects = f.assets.openingObjects(); var bank = objects.sectionArtSources(); assertSame(bank, objects.sectionArtSources());
            assertEquals(9, bank.sources().size()); var source = bank.sources().get(7);
            assertEquals(com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING, source.source());
            assertSame(source, bank.source(com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING));
            assertEquals(1, source.canonicalPlcId()); assertEquals(5, source.canonicalRowIndex());
            assertEquals(f.assets.loadPlan().plcs().get(1).entries().get(5), source.canonicalPlc());
            assertEquals(0x9200, source.canonicalPlc().vramAddress()); assertEquals(46, source.tileCount());
            assertEquals(1472, source.decode().bytes().length()); assertEquals(780, source.readerBytes().length());
            assertEquals(779, source.decode().consumedInputBytes());
            var state = f.runtime.sectionArtState(); var resident = state.residency(); cameraArtBytes(resident, 0x9200, source.decode().bytes());
            var upper = resident.region(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_9000);
            for (int offset = 0; offset < 0x1400; offset++) assertEquals(offset >= 0x200 && offset < 0x7C0,
                    upper.knownByte(offset).isPresent(), "actual AT_9000 mask byte " + offset);
            int known = 0;
            for (var region : com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.values())
                for (byte value : resident.region(region).knownMask().copy()) known += value;
            assertEquals((70+28+63+19+46+8)*32, known);
            for (int tile : List.of(0x480,0x48F,0x4BE,0x4BF,0x51F)) boulderUnknown(resident, tile);
            assertEquals(0, state.sectionWord()); assertEquals(0, state.remainingTilesWord()); assertEquals(0, state.queue().headSource());
            assertTrue(state.decoder().isEmpty()); assertArrayEquals(new byte[512], state.codeTable().copy());
            var pixels = diagonalPixels(f, resident);
            for (int tile = 0; tile < 46; tile++) {
                var resolved = pixels.resolve(0x490 + tile); assertEquals(BANKS.diagonalSpring() + tile, resolved.logicalPatternId());
                assertEquals(CdObjectPresentation.patternVersion(source.decode().bytes().slice(tile*32,32)), resolved.version());
            }
            int[] first = {0x780,0x7AE,0x3A0,0x424,0x441,0x680,0x6C6,0x6D7,0x544,0x334,0x520,0x328,0x5A8,0x374,0x490};
            int[] count = {24,14,70,28,63,70,5,39,36,15,36,12,82,19,46};
            var ids = new java.util.HashSet<Integer>();
            for (int span = 0; span < first.length; span++) for (int tile = 0; tile < count[span]; tile++)
                assertTrue(ids.add(pixels.resolve(first[span] + tile).logicalPatternId()), "fifteen disjoint logical spans");
            assertEquals(559, ids.size());
            assertThrows(UnsupportedOperationException.class, () -> pixels.resolve(0x4BE), "no BigRing or unbound native fallback");
        }
    }

    @Test void diagonalInitRtsConsumesF0SpeedAndPaletteWithoutSameCallSolidAcrossBothStrengthsAndAllFlips() throws Exception {
        for (int subtype : List.of(8,10)) for (int flips = 0; flips < 4; flips++) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpringPlayer(f,0x01008000,0x07631234,0x234,0x100,8,5,17);
            var dirty = pool.state(40).moveByte(ID,0x0A).moveByte(SUBTYPE,subtype).moveByte(SPRITE_FLAGS,0x80|flips)
                    .moveByte(FLAGS,0xA0|flips).moveWord(X,0x100).moveWord(Y,0x780).moveWord(SPRING_SPEED,0x1234);
            pool.write(dirty); var actor = f.frame.state(); var env = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            var expected = dirty.moveByte(ROUTINE,20).moveLong(MAPPINGS,0x209BF0).moveWord(TILE,subtype==8 ? 0x490 : 0x2490)
                    .moveByte(SPRITE_FLAGS,4|flips).moveByte(WIDTH,24).moveByte(Y_RADIUS,12).moveByte(PRIORITY,4)
                    .moveWord(SPRING_ANCHOR_X,0x100).moveWord(SPRING_SPEED,subtype==8 ? -4096 : -2560);
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertEquals(expected,f.slot(40));
            assertEquals(actor,f.frame.state()); assertEquals(env,f.frame.environment()); assertEquals(sounds,f.runtime.lastSoundRequests());
            assertEquals(5,f.frame.state().objectLinks().standObject(),"reserved link would fail a reached Solid; Init RTS must not read it");
            monitorFinish(f);
        }
    }

    @Test void diagonalMalformedSubtypeTwoRetainsTheNativePublishedInitPrefixBeforePaletteSpeedDrawAndLaterSlots() throws Exception {
        try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            var dirty = pool.state(40).moveByte(ID,0x0A).moveByte(SUBTYPE,10).moveByte(SUBTYPE2,1)
                    .moveByte(SPRITE_FLAGS,0x82).moveByte(FLAGS,0xA5).moveWord(X,0x100).moveWord(Y,0x780)
                    .moveWord(SPRING_SPEED,0x3456).moveByte(ANIMATION_FRAME,0xA7);
            pool.write(dirty); var later = pool.state(41).moveByte(ID,0x0A); pool.write(later);
            var actor = f.frame.state(); var env = f.frame.environment(); var sounds = f.runtime.lastSoundRequests();
            var fault = assertThrows(CdSpring.UnsupportedSpring.class,f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(CdSpring.UnsupportedBoundary.DIAGONAL_TILE_SELECTOR,fault.boundary()); assertEquals(15,fault.value());
            assertEquals(dirty.moveByte(ROUTINE,20).moveLong(MAPPINGS,0x209BF0).moveWord(TILE,0x520).moveByte(SPRITE_FLAGS,0x86)
                    .moveByte(WIDTH,24).moveByte(Y_RADIUS,12).moveByte(PRIORITY,4).moveWord(SPRING_ANCHOR_X,0x100),f.slot(40));
            assertEquals(later,f.slot(41)); assertEquals(actor,f.frame.state()); assertEquals(env,f.frame.environment());
            assertEquals(sounds,f.runtime.lastSoundRequests()); assertTrue(f.runtime.closed()); assertTrue(f.frame.closed());
        }
    }

    @Test void diagonalActualTopSolidLaunchesBothStrengthsAndAllFourDistinctStatusHSpriteVWithCompletePostSolidState() throws Exception {
        for (int subtype : List.of(8,10)) for (int h = 0; h < 2; h++) for (int v = 0; v < 2; v++) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool();
            seedSpringPlayer(f,0x01008000,0x07631234,0x234,0x100,8,41,17);
            seedTunnelDoorGlobals(f,0,0x3456,0x55,0x66);
            pool.write(diagonalImage(f,pool.state(40),20,0x84|(h^1)|(v<<1),h|((v^1)<<1),subtype)
                    .moveWord(X_VELOCITY,0x123).moveWord(Y_VELOCITY,-0x180));
            var older = pool.state(41).moveByte(FLAGS,0xA8).moveWord(X_FRACTION,0x4567); pool.write(older);
            var last = pool.state(127).moveWord(TUNNEL_DOOR_TIMER,0xA55A); pool.write(last);
            var actor = f.frame.state(); var env = f.frame.environment(); var display = f.frame.display(); var reserved = horizontalReserved(f.runtime);
            var call = diagonalSolidCall(f,f.slot(40)); var result = call.result(); assertEquals(CdSolidObject.Path.STAND_ON,result.path());
            assertEquals(1,result.d0().orElseThrow()); assertEquals(List.of(41),result.otherSlots().stream().map(s -> s.identity().wholeIndex()).toList());
            var expected = diagonalLaunchPlayer(result.player(),subtype,h,v); var sounds = diagonalSounds(f,result,true); int angles = f.angleWrites.size();
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertDiagonalApplied(f,actor,env,result,expected);
            assertEquals(0xA300,f.frame.state().motion().centreX16_16()&0xFFFF); assertEquals(0x9234,f.frame.state().motion().centreY16_16()&0xFFFF);
            assertEquals(older.moveByte(FLAGS,0xA0),f.slot(41)); assertEquals(last,f.slot(127)); assertEquals(40,f.frame.state().objectLinks().standObject());
            assertEquals(display,f.frame.display()); assertEquals(reserved,horizontalReserved(f.runtime)); assertEquals(angles,f.angleWrites.size());
            assertEquals(diagonalAnimated(f,result.object().moveByte(ROUTINE,22).moveByte(FLAGS,result.object().flags()&~0x28))
                    .moveByte(SPRITE_FLAGS,4|(result.object().flags()&3)),f.slot(40));
            assertEquals(sounds,f.runtime.lastSoundRequests()); monitorFinish(f); assertEquals(sounds,f.runtime.lastSoundRequests());
        }
    }

    @Test void diagonalActualSideFlagFiveLaunchesWithD0ZeroForBothStrengthsAndEveryOrientation() throws Exception {
        for (int subtype : List.of(8,10)) for (int h = 0; h < 2; h++) for (int v = 0; v < 2; v++) try (var f = new Fixture()) {
            springDeviceCut(f); var pool = f.runtime.placement().pool(); seedSpringPlayer(f,0x00E81234,0x07805678,0x600,0,0,0,9);
            pool.write(diagonalImage(f,pool.state(40),20,0x84|(h^1)|(v<<1),h|((v^1)<<1),subtype));
            var actor=f.frame.state(); var env=f.frame.environment(); var result=diagonalSolidCall(f,f.slot(40)).result();
            assertEquals(CdSolidObject.Path.HALT_ON_X,result.path()); assertEquals(0,result.d0().orElseThrow()); assertEquals(0x20,result.object().flags()&0x20);
            assertEquals(0,result.player().pushObject(),"shared PushObject's Spring special case remains authoritative");
            var expected=diagonalLaunchPlayer(result.player(),subtype,h,v); var sounds=diagonalSounds(f,result,true);
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertDiagonalApplied(f,actor,env,result,expected);
            assertEquals(0,f.slot(40).flags()&0x28); assertEquals(22,f.slot(40).routine()); assertEquals(1,f.slot(40).unsignedByte(ANIMATION_FRAME));
            assertEquals(sounds,f.runtime.lastSoundRequests()); monitorFinish(f);
        }
    }

    @Test void diagonalD0ZeroWithoutPushAndOffscreenMainNeverInventALaunchOrConsumeAnimation() throws Exception {
        for (boolean offscreen : List.of(false,true)) try (var f=new Fixture()) {
            springDeviceCut(f); var pool=f.runtime.placement().pool(); seedSpringPlayer(f,0x02001234,0x07805678,0x123,0,8,40,7);
            pool.write(diagonalImage(f,pool.state(40),20,offscreen ? 4 : 0x84,8,8).moveByte(ANIMATION_FRAME,0x99));
            var actor=f.frame.state(); var env=f.frame.environment(); var before=f.slot(40); var sounds=new ArrayList<>(f.runtime.lastSoundRequests());
            CdSolidObject.Result result=offscreen ? null : diagonalSolidCall(f,before).result();
            if(result!=null) { assertEquals(0,result.d0().orElseThrow()); assertEquals(0,result.object().flags()&0x20); horizontalSharedSounds(sounds,result); }
            f.runtime.runDynamicSlotsAfterReservedSlots();
            if(result==null) { assertEquals(actor,f.frame.state()); assertEquals(env,f.frame.environment()); }
            else assertTunnelDoorSolidApplied(f,actor,env,result);
            assertEquals(20,f.slot(40).routine()); assertEquals(0x99,f.slot(40).unsignedByte(ANIMATION_FRAME));
            assertEquals(sounds,f.runtime.lastSoundRequests()); monitorFinish(f);
        }
    }

    @Test void diagonalSharedGetOffChargeStopIsAppendedOnceAfterOrdinaryBodyPrefixAndBeforeReturningSpringFm() throws Exception {
        try(var f=new Fixture()) {
            var body=springDeviceCut(f); var pool=f.runtime.placement().pool();
            assertEquals(body.sounds().stream().map(CdOpeningObjectRuntime.PlayerSound::new).toList(),f.runtime.lastSoundRequests());
            // Preserve ANY ordinary Body prefix, including MovementSound(FM_CHARGESTOP); never replace it with ContactFmSound.
            seedSpringPlayer(f,0x02001234,0x07805678,0x123,0,8,40,17);
            pool.write(diagonalImage(f,pool.state(40),20,0x84,0x28,10));
            var actor=f.frame.state(); var env=f.frame.environment(); var result=diagonalSolidCall(f,f.slot(40)).result();
            assertEquals(0,result.d0().orElseThrow()); assertEquals(0x20,result.object().flags()&0x20);
            assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP),result.fmRequests());
            var sounds=diagonalSounds(f,result,true); var expected=diagonalLaunchPlayer(result.player(),10,0,0);
            f.runtime.runDynamicSlotsAfterReservedSlots(); assertDiagonalApplied(f,actor,env,result,expected);
            assertEquals(17,f.frame.state().motion().charge()); assertEquals(0,f.frame.state().objectLinks().standObject());
            assertEquals(sounds,f.runtime.lastSoundRequests()); assertEquals(1,f.slot(40).unsignedByte(ANIMATION_FRAME));
            assertEquals(0,f.slot(40).unsignedByte(MAP_FRAME)); monitorFinish(f); assertEquals(sounds,f.runtime.lastSoundRequests());
        }
    }

    @Test void diagonalReturningFmConsumesTokenZeroThenNativeFcAndASeparateResetWithoutSameCallMain() throws Exception {
        try(var f=new Fixture()) {
            springDeviceCut(f); var pool=f.runtime.placement().pool(); seedSpringPlayer(f,0x01008000,0x07631234,0,0x100,2,0,0);
            pool.write(diagonalImage(f,pool.state(40),20,0x84,0,8)); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(22,f.slot(40).routine()); assertEquals(1,f.slot(40).unsignedByte(ANIMATION_FRAME)); assertEquals(0,f.slot(40).unsignedByte(MAP_FRAME));
            monitorFinish(f); f.makePlayerAbsent();
            int[] tokens={0,1,1,2,2,2,2,2,2,1};
            for(int eligible=1;eligible<=10;eligible++) {
                f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(eligible+2); f.runtime.placement().pool().write(f.slot(40).moveByte(SPRITE_FLAGS,0x84));
                var actor=f.frame.state(); var sounds=f.runtime.lastSoundRequests(); f.runtime.runDynamicSlotsAfterReservedSlots();
                assertEquals(eligible==10 ? 24 : 22,f.slot(40).routine());
                assertEquals(Math.min(eligible+1,10),f.slot(40).unsignedByte(ANIMATION_FRAME));
                assertEquals(tokens[Math.min(eligible,9)],f.slot(40).unsignedByte(MAP_FRAME));
                assertEquals(actor,f.frame.state()); assertEquals(sounds,f.runtime.lastSoundRequests()); monitorFinish(f);
            }
            f.service(0); f.runtime.spawnBeforeReservedSlots(); f.body(13); pool.write(f.slot(40).moveByte(SPRITE_FLAGS,0x84).moveByte(ANIMATION,0xFE));
            var actor=f.frame.state(); var sounds=f.runtime.lastSoundRequests(); f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(20,f.slot(40).routine()); assertEquals(1,f.slot(40).unsignedByte(PREVIOUS_ANIMATION)); assertEquals(0,f.slot(40).unsignedByte(MAP_FRAME));
            assertEquals(10,f.slot(40).unsignedByte(ANIMATION_FRAME)); assertEquals(actor,f.frame.state()); assertEquals(sounds,f.runtime.lastSoundRequests()); monitorFinish(f);
        }
    }

    @Test void diagonalEarlierAndLaterPhysicalMirrorsReadTheLivePreAndPostLaunchPlayerWithoutMovingFractions() throws Exception {
        try(var f=new Fixture()) {
            springDeviceCut(f); var pool=f.runtime.placement().pool(); seedSpringPlayer(f,0x01008000,0x07631234,0,0x100,2,0,0);
            for(int whole:List.of(39,42)) pool.write(spring(f,pool.state(whole),2,4,0,0).moveByte(ROUTINE_2,5)
                    .moveWord(X_FRACTION,0x4567).moveWord(Y_FRACTION,0x89AB));
            pool.write(diagonalImage(f,pool.state(40),20,0x84,1,10)); var before=f.frame.state();
            var result=diagonalSolidCall(f,f.slot(40)).result(); var expected=diagonalLaunchPlayer(result.player(),10,1,0);
            f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(before.motion().centreX16_16()>>>16,f.slot(39).centreX()); assertEquals(before.motion().centreY16_16()>>>16,f.slot(39).centreY());
            assertEquals(expected.actor().motion().centreX16_16()>>>16,f.slot(42).centreX()); assertEquals(expected.actor().motion().centreY16_16()>>>16,f.slot(42).centreY());
            for(int whole:List.of(39,42)) { assertEquals(0x4567,f.slot(whole).word(X_FRACTION)); assertEquals(0x89AB,f.slot(whole).word(Y_FRACTION)); }
            monitorFinish(f);
        }
    }

    @Test void diagonalUnknownLaterIdRetainsActualSolidLaunchFmAnimationAndEarlierPhysicalPrefixWithoutRetry() throws Exception {
        try(var f=new Fixture()) {
            springDeviceCut(f); var pool=f.runtime.placement().pool(); seedSpringPlayer(f,0x01008000,0x07631234,0x234,0x100,2,0,0);
            pool.write(spring(f,pool.state(39),2,4,0,0).moveByte(ROUTINE_2,5));
            pool.write(diagonalImage(f,pool.state(40),20,0x84,0,8)); pool.write(pool.state(41).moveByte(ID,0x7E));
            var later=pool.state(42).moveByte(ID,0x0A);pool.write(later);
            var actor=f.frame.state();var env=f.frame.environment();var result=diagonalSolidCall(f,f.slot(40)).result();
            var expected=diagonalLaunchPlayer(result.player(),8,0,0);var sounds=diagonalSounds(f,result,true);
            var fault=assertThrows(UnsupportedOperationException.class,f.runtime::runDynamicSlotsAfterReservedSlots);
            assertTrue(fault.getMessage().contains("41"));assertTrue(fault.getMessage().contains("7e"));
            assertDiagonalApplied(f,actor,env,result,expected);assertEquals(22,f.slot(40).routine());
            assertEquals(1,f.slot(40).unsignedByte(ANIMATION_FRAME));assertEquals(0,f.slot(40).unsignedByte(MAP_FRAME));
            assertEquals(beforeWord(actor.motion().centreY16_16()),f.slot(39).centreY());assertEquals(later,f.slot(42));
            assertEquals(sounds,f.runtime.lastSoundRequests());assertTrue(f.runtime.closed());assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class,f.runtime::runDynamicSlotsAfterReservedSlots);assertEquals(sounds,f.runtime.lastSoundRequests());
        }
    }

    @Test void diagonalCurrentPictureUsesAllFortySixChangedValuesAndRejectsASeparateBigRingFallback() throws Exception {
        try(var f=new Fixture()) {
            var original=f.runtime.sectionArtState().residency();var changed=diagonalChanged(original);
            var pixels=diagonalPixels(f,changed);var source=cameraArtSource(f,com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING);
            for(int tile=0;tile<46;tile++) {
                var expected=com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(changed,0x490+tile);
                var resolved=pixels.resolve(0x490+tile);assertEquals(BANKS.diagonalSpring()+tile,resolved.logicalPatternId());
                assertEquals(CdObjectPresentation.patternVersion(expected),resolved.version());
                assertNotEquals(CdObjectPresentation.patternVersion(source.slice(tile*32,32)),resolved.version());
            }
            cameraArtBytes(original,0x9200,source);assertThrows(UnsupportedOperationException.class,()->pixels.resolve(0x4BE));
        }
    }

    @Test void diagonalPreviousSatUsesNowServicedRealThreeTileGenerationAndCaptureReplayKeepsTheOldPictureImmutable() throws Exception {
        try(var f=new Fixture()) {
            cameraArtIdle(f);cameraArtBegin(f,0);f.runtime.spawnBeforeReservedSlots();f.body(2);var pool=f.runtime.placement().pool();pool.clearDynamicSlots();
            pool.write(diagonalImage(f,pool.state(40),22,0x84,0,8));f.runtime.runDynamicSlotsAfterReservedSlots();
            var sat=f.runtime.finishAfterCamera(POST);f.runtime.finishOrdinaryTail();
            assertTrue(sat.provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==40));
            var original=f.runtime.sectionArtState();var resident=diagonalChanged(original.residency());
            var row=f.assets.openingObjects().sectionArtSources().source(com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING).canonicalPlc();
            // Constructed external decoder ingress: one ACTUAL canonical row, no new production gate or natural-route assertion.
            var queue=com.openggf.game.soniccd.resources.CdR11aPlcBuffer.append(
                    com.openggf.game.soniccd.resources.CdR11aPlcBuffer.cleared(),List.of(new com.openggf.game.soniccd.resources.CdR11aPlcBuffer.Entry(
                            row.sourceRange().nativeAddress(),row.vramAddress())));
            var prepared=diagonalSectionCopy(original,resident,queue);var ownerField=CdOpeningObjectRuntime.class.getDeclaredField("sectionArt");ownerField.setAccessible(true);
            var owner=(com.openggf.game.soniccd.resources.CdR11aSectionArt)ownerField.get(f.runtime);prepared=owner.prepare(prepared);
            assertEquals(46,prepared.remainingTilesWord());assertEquals(0x9200,prepared.queue().headDestination());
            f.runtime.restore((CdOpeningObjectRuntime.Snapshot)horizontalReplacePartition(f.runtime.captureClosedCut(),
                    com.openggf.game.soniccd.resources.CdR11aSectionArt.State.class,prepared));
            var cut=f.runtime.captureClosedCut();var actor=f.frame.captureActor();var shared=f.frame.captureShared();
            var oldPicture=f.runtime.displayedFrame();var oldVersions=oldPicture.patternVersions();var oldBytes=sat.bytes();var before=f.runtime.sectionArtState();
            var selected=f.frame.prepareInput(0x80);assertEquals(PlcLifecyclePhase.NORMAL_PAUSE,selected.phase());
            // Observe the private publication value without invoking the guarded public read during a callback.
            var displayField=CdOpeningObjectRuntime.class.getDeclaredField("displayed");displayField.setAccessible(true);
            f.runtime.serviceVBlank(selected.phase(),()->{
                assertSame(before,f.runtime.sectionArtState());
                try {assertSame(oldPicture,displayField.get(f.runtime));}
                catch(IllegalAccessException impossible) {throw new AssertionError(impossible);}
            },0);
            assertEquals(43,f.runtime.sectionArtState().remainingTilesWord());assertArrayEquals(oldBytes,f.runtime.cpuSat().bytes());
            var source=cameraArtSource(f,com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING);var current=f.runtime.sectionArtState().residency();
            var pixels=diagonalPixels(f,current);
            for(int tile=0;tile<46;tile++) {
                var bytes=tile<3 ? source.slice(tile*32,32) : com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(resident,0x490+tile);
                assertEquals(CdObjectPresentation.patternVersion(bytes),pixels.resolve(0x490+tile).version());
            }
            var shown=f.runtime.displayedFrame();assertEquals(CdObjectPresentation.convert(sat,pixels),shown);
            var diagonalIds=shown.patternVersions().keySet().stream().filter(id->id>=BANKS.diagonalSpring()&&id<BANKS.diagonalSpring()+46).toList();
            assertFalse(diagonalIds.isEmpty());for(int id:diagonalIds)assertEquals(pixels.resolve(0x490+id-BANKS.diagonalSpring()).version(),shown.patternVersions().get(id));
            assertEquals(oldVersions,oldPicture.patternVersions());assertSame(oldPicture,cut.displayedFrame());
            var after=f.runtime.captureClosedCut();var nextShared=f.frame.captureShared();
            f.frame.validateActorRestore(actor);f.frame.validateSharedRestore(shared);f.runtime.validateRestore(cut);
            f.frame.restoreActor(actor);f.frame.restoreShared(shared);f.runtime.restore(cut);
            assertSame(oldPicture,f.runtime.displayedFrame());assertSame(sat,f.runtime.cpuSat());
            var repeat=f.frame.prepareInput(0x80);f.runtime.serviceVBlank(repeat.phase(),()->{},0);
            assertEquals(shown,f.runtime.displayedFrame());assertEquals(after.sectionArtState(),f.runtime.sectionArtState());
            assertEquals(after.lastSoundRequests(),f.runtime.lastSoundRequests());assertEquals(nextShared.vIntClock(),f.frame.vIntClock());
            assertEquals(oldVersions,oldPicture.patternVersions());
        }
    }

    @Test void diagonalRestartCarriesChangedFortySixEveryKnownMaskTableAndSectionVerbatimWithoutDecodeRepairOrService() throws Exception {
        try(var f=new Fixture()) {
            var original=f.runtime.sectionArtState().residency();var resident=diagonalChanged(original);
            resident=com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.writeTile(resident,0x9000,CdAssetBytes.copyOf(new byte[32]));
            byte[] table=new byte[512];for(int i=0;i<table.length;i++)table[i]=(byte)(i^0xC7);
            var carry=new CdOpeningObjectRuntime.CameraArtRestartIngress(resident,CdAssetBytes.copyOf(table),0x035A);var exact=resident;
            try(var frame=CdPlayerFrameRuntime.externalTouchRequiredDevelopment(f.assets,0)) {
                var actor=frame.captureActor();var shared=frame.captureShared();var publications=new ArrayList<CdPlayerArtRuntime.State>();
                frame.bindArtPublication((request,state)->publications.add(state));
                try(var runtime=CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(f.assets,frame,INITIAL,CONTACT,SCORE,LEVEL,
                        CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]),new CdPowerupArtRuntime.State(new byte[0x480],new byte[0x480]),
                        carry,new byte[640],BANKS,PRE,value->fail("restart performs no helper calls"))) {
                    assertSame(exact,runtime.sectionArtState().residency());assertArrayEquals(table,runtime.sectionArtState().codeTable().copy());
                    assertEquals(0x035A,runtime.sectionArtState().sectionWord());assertEquals(0,runtime.sectionArtState().remainingTilesWord());
                    assertEquals(0,runtime.sectionArtState().queue().headSource());assertTrue(runtime.sectionArtState().decoder().isEmpty());
                    for(var region:com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.values())
                        assertEquals(exact.region(region),runtime.sectionArtState().residency().region(region));
                    var pixels=diagonalPixels(f,exact);for(int tile=0;tile<46;tile++)assertEquals(CdObjectPresentation.patternVersion(
                            com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(exact,0x490+tile)),pixels.resolve(0x490+tile).version());
                    assertEquals(actor,frame.captureActor());assertEquals(shared.environment(),frame.environment());assertEquals(shared.vIntClock(),frame.vIntClock());
                    assertTrue(publications.isEmpty());assertTrue(runtime.lastSoundRequests().isEmpty());assertTrue(runtime.displayedFrame().tiles().isEmpty());
                }
            }
            cameraArtBytes(original,0x9200,cameraArtSource(f,com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING));
        }
    }

    @Test void diagonalRestartUnknownFirstOrLastPatternRejectsBeforeConstructionAndAnyFrameStoreWithMasksIntact() throws Exception {
        var assets=load();var objects=assets.openingObjects();var frontier=objects.frontierArtBank();
        for(int missing:List.of(0,45)) {
            var resident=com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.devSeed(objects.initialArtBank().binding(
                    com.openggf.game.soniccd.level.CdR11aAsset.ART_MOSQUI).bytes(),frontier.binding(com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_PLANT).bytes(),
                    frontier.binding(com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_RAMP_BOOST).bytes());
            for(var source:List.of(objects.sectionArtSources().source(com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER),
                    objects.sectionArtSources().source(com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING)))
                for(int tile=0;tile<source.tileCount();tile++)if(source.source()!=com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING||tile!=missing)
                    resident=com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.writeTile(resident,source.canonicalPlc().vramAddress()+tile*32,source.decode().bytes().slice(tile*32,32));
            var exact=resident;var mask=resident.region(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_9000).knownMask();
            var carry=new CdOpeningObjectRuntime.CameraArtRestartIngress(resident,CdAssetBytes.copyOf(new byte[512]),0x005A);
            try(var frame=CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets,0)) {
                var actor=frame.captureActor();var shared=frame.captureShared();var publications=new ArrayList<CdPlayerArtRuntime.State>();frame.bindArtPublication((request,state)->publications.add(state));
                var fault=assertThrows(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.OwnershipBoundary.class,()->
                        CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(assets,frame,INITIAL,CONTACT,SCORE,LEVEL,
                                CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]),new CdPowerupArtRuntime.State(new byte[0x480],new byte[0x480]),
                                carry,new byte[640],BANKS,PRE,value->fail("admission has no helper callback")));
                assertEquals(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,fault.boundary());
                assertEquals(0x490+missing,fault.nativePatternId());assertEquals(actor,frame.captureActor());assertEquals(shared.environment(),frame.environment());
                assertSame(shared.artState(),frame.artState());assertEquals(shared.vIntClock(),frame.vIntClock());assertFalse(frame.closed());assertTrue(publications.isEmpty());
                assertArrayEquals(mask.copy(),exact.region(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_9000).knownMask().copy());boulderUnknown(exact,0x490+missing);
            }
        }
    }

    @Test void diagonalSolidPrivateCallbackRejectsWrongPhasePhysicalVisitAndEveryMutualGuardBeforeStores() throws Exception {
        try(var f=new Fixture()) {
            springDeviceCut(f);var pool=f.runtime.placement().pool();seedSpringPlayer(f,0x01008000,0x07631234,0,0x100,2,0,0);
            pool.write(diagonalImage(f,pool.state(40),20,0x84,0,8));var call=diagonalSolidCall(f,f.slot(40));
            diagonalReject(f,"commitSpringSolid",CdSpring.SolidCall.class,call);
            boulderSet(f.runtime,"phase",CdOpeningObjectRuntime.Phase.DYNAMIC_SLOTS);boulderSet(f.runtime,"operating",true);
            try {
                boulderSet(f.runtime,"dispatchWholeIndex",41);diagonalReject(f,"commitSpringSolid",CdSpring.SolidCall.class,call);boulderSet(f.runtime,"dispatchWholeIndex",40);
                for(String guard:diagonalGuards()) {
                    boulderSet(f.runtime,guard,true);diagonalReject(f,"commitSpringSolid",CdSpring.SolidCall.class,call);boulderSet(f.runtime,guard,false);
                }
            } finally { boulderUnstage(f); }
        }
    }

    @Test void diagonalSolidPreflightsAllNinetySixImagesForeignOwnershipExactFrameCasAndItsOrdinaryEntry() throws Exception {
        for(int scenario=0;scenario<8;scenario++)try(var f=new Fixture();var foreign=new Fixture()) {
            springDeviceCut(f);var pool=f.runtime.placement().pool();seedSpringPlayer(f,0x01008000,0x07631234,0x234,0x100,8,41,0);
            pool.write(diagonalImage(f,pool.state(40),20,0x84,0,8));pool.write(pool.state(41).moveByte(FLAGS,8));var call=diagonalSolidCall(f,f.slot(40));
            if(scenario==0)pool.write(pool.state(127).moveWord(TUNNEL_DOOR_TIMER,0xA55A));
            if(scenario==1)pool.write(pool.state(40).moveByte(ID,0x7E));
            if(scenario==2) { var p=foreign.runtime.placement().pool();p.write(diagonalImage(foreign,p.state(40),20,0x84,0,8));call=diagonalSolidCall(foreign,p.state(40)); }
            if(scenario==3)seedSpringPlayer(f,0x01008001,0x07631234,0x234,0x100,8,41,0);
            if(scenario==4)seedTunnelDoorGlobals(f,0,0xA55A,0x12,0x34);
            if(scenario==5||scenario==6) {
                var images=new ArrayList<>(call.callCut().slots());if(scenario==5)images.removeLast();else images.set(95,foreign.runtime.placement().pool().state(127));
                var cut=(CdObjectPool.Snapshot)horizontalReplacePartition(call.callCut(),List.class,images);
                call=new CdSpring.SolidCall(cut,call.expectedPlayer(),call.expectedGlobals(),call.result());
            }
            if(scenario==7) {
                var env=f.frame.environment();var result=CdSolidObject.fromAssets(f.assets).run(CdSolidObject.Entry.BTM_SOLID_OBJECT,f.slot(40),call.expectedPlayer(),
                        new CdSolidObject.Gates(env.globals().debugMode(),env.movement().gates().miniSonic()),call.expectedGlobals(),call.callCut());
                call=new CdSpring.SolidCall(call.callCut(),call.expectedPlayer(),call.expectedGlobals(),result);
            }
            boulderSet(f.runtime,"phase",CdOpeningObjectRuntime.Phase.DYNAMIC_SLOTS);boulderSet(f.runtime,"operating",true);boulderSet(f.runtime,"dispatchWholeIndex",40);
            try {diagonalReject(f,"commitSpringSolid",CdSpring.SolidCall.class,call);}finally{boulderUnstage(f);}
        }
    }

    @Test void diagonalActualSourceLaunchCallContainsExactlyFiveStoresAndTheExistingFrameAcceptsTwentyTwoOnce() throws Exception {
        try(var f=new Fixture()) {
            springDeviceCut(f);var pool=f.runtime.placement().pool();seedSpringPlayer(f,0x01008000,0x07631234,0x234,0x100,2,0,17);
            pool.write(diagonalImage(f,pool.state(40),20,0x84,0,8));var call=diagonalSourceLaunch(f);
            assertEquals(java.util.Set.of(CdSpring.LaunchPlayerField.CENTRE_X,CdSpring.LaunchPlayerField.CENTRE_Y,
                    CdSpring.LaunchPlayerField.X_VELOCITY,CdSpring.LaunchPlayerField.Y_VELOCITY,CdSpring.LaunchPlayerField.FLAGS),call.playerWrites());
            assertTrue(call.globalWrites().isEmpty());assertEquals(call.expectedGlobals(),call.finalGlobals());assertEquals(22,f.slot(40).routine());
            assertEquals(call.expectedPlayer(),tunnelDoorPlayer(f));assertEquals(diagonalLaunchPlayer(call.expectedPlayer(),8,0,0),call.finalPlayer());
            var sounds=f.runtime.lastSoundRequests();boulderSet(f.runtime,"phase",CdOpeningObjectRuntime.Phase.DYNAMIC_SLOTS);
            boulderSet(f.runtime,"operating",true);boulderSet(f.runtime,"dispatchWholeIndex",40);
            try {
                var method=CdOpeningObjectRuntime.class.getDeclaredMethod("commitSpringLaunch",CdSpring.LaunchCall.class);method.setAccessible(true);method.invoke(f.runtime,call);
                assertEquals(call.finalPlayer(),tunnelDoorPlayer(f));assertEquals(sounds,f.runtime.lastSoundRequests());
                diagonalReject(f,"commitSpringLaunch",CdSpring.LaunchCall.class,call);
            }finally{boulderUnstage(f);}
        }
    }

    @Test void diagonalSourceLaunchAndFmRejectPhaseEveryGuardIdentityRoutineAndExactPlayerGlobalCasWithoutReplaying() throws Exception {
        for(int scenario=0;scenario<6;scenario++)try(var f=new Fixture();var foreign=new Fixture()) {
            springDeviceCut(f);var pool=f.runtime.placement().pool();seedSpringPlayer(f,0x01008000,0x07631234,0,0x100,2,0,0);
            pool.write(diagonalImage(f,pool.state(40),20,0x84,0,8));var call=diagonalSourceLaunch(f);
            diagonalReject(f,"commitSpringLaunch",CdSpring.LaunchCall.class,call);diagonalReject(f,"playSpringFm",CdSpring.FmSound.class,CdSpring.FmSound.FM_SPRING);
            boulderSet(f.runtime,"phase",CdOpeningObjectRuntime.Phase.DYNAMIC_SLOTS);boulderSet(f.runtime,"operating",true);boulderSet(f.runtime,"dispatchWholeIndex",40);
            try {
                for(String guard:diagonalGuards()) {
                    boulderSet(f.runtime,guard,true);diagonalReject(f,"commitSpringLaunch",CdSpring.LaunchCall.class,call);
                    diagonalReject(f,"playSpringFm",CdSpring.FmSound.class,CdSpring.FmSound.FM_SPRING);boulderSet(f.runtime,guard,false);
                }
                if(scenario==0)boulderSet(f.runtime,"dispatchWholeIndex",41);
                if(scenario==1)pool.write(pool.state(40).moveByte(ID,0x7E));
                if(scenario==2)pool.write(pool.state(40).moveByte(ROUTINE,20));
                if(scenario==3)seedSpringPlayer(f,0x01008001,0x07631234,0,0x100,2,0,0);
                if(scenario==4)seedTunnelDoorGlobals(f,0,0xA55A,0x12,0x34);
                if(scenario==5) {
                    springDeviceCut(foreign);var p=foreign.runtime.placement().pool();seedSpringPlayer(foreign,0x01008000,0x07631234,0,0x100,2,0,0);
                    p.write(diagonalImage(foreign,p.state(40),20,0x84,0,8));call=diagonalSourceLaunch(foreign);
                    // LaunchCall is plain value-CAS data: equal native whole indices carry no pool capability.
                    assertEquals(tunnelDoorPlayer(f),call.expectedPlayer());assertEquals(tunnelDoorGlobals(f),call.expectedGlobals());
                    assertEquals(pool.state(40).identity(),call.identity());
                    // Reject a different native identity, preserving all real source-derived patch values.
                    call=new CdSpring.LaunchCall(new CdObjectState.Identity(41),call.expectedPlayer(),call.expectedGlobals(),
                            call.finalPlayer(),call.finalGlobals(),call.playerWrites(),call.globalWrites());
                }
                diagonalReject(f,"commitSpringLaunch",CdSpring.LaunchCall.class,call);
            }finally{boulderUnstage(f);}
        }
    }

    @Test void diagonalRealSolidFailureRetainsEarlierSpringSoundAndStopsBeforeAnyLaunchPublicationOrLaterVisit() throws Exception {
        try(var f=new Fixture()) {
            springDeviceCut(f);var pool=f.runtime.placement().pool();seedSpringPlayer(f,0x01008000,0x07631234,0,0x100,8,5,17);
            pool.write(diagonalImage(f,pool.state(40),20,0x84,0,8));var before=f.slot(40);var later=pool.state(41).moveByte(ID,0x0A);pool.write(later);
            var actor=f.frame.state();var env=f.frame.environment();var sounds=f.runtime.lastSoundRequests();
            var fault=assertThrows(CdSolidObject.UnsupportedSolidObject.class,f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS,fault.boundary());
            assertEquals(before,f.slot(40));assertEquals(later,f.slot(41));assertEquals(actor,f.frame.state());assertEquals(env,f.frame.environment());
            assertEquals(sounds,f.runtime.lastSoundRequests());assertTrue(f.runtime.closed());assertTrue(f.frame.closed());
        }
    }

    @Test void diagonalSharedSolidFlagRejectsExistingDrawAndAllSiblingCallbacksBeforeAnyStore() throws Exception {
        try(var f=new Fixture()) {
            springDeviceCut(f);var pool=f.runtime.placement().pool();pool.write(diagonalImage(f,pool.state(40),20,0x84,0,8));
            boulderSet(f.runtime,"phase",CdOpeningObjectRuntime.Phase.DYNAMIC_SLOTS);boulderSet(f.runtime,"operating",true);
            boulderSet(f.runtime,"dispatchWholeIndex",40);boulderSet(f.runtime,"inSolidCommit",true);
            var before=pool.capture().slots();var actor=f.frame.state();var env=f.frame.environment();var sounds=f.runtime.lastSoundRequests();
            try {
                for(String name:List.of("commitTopSolid","commitBoulderSolid","commitSpringSolid","commitSpringLaunch","playSpringFm",
                        "commitTunnelDoorVerticalSolid","playTunnelDoorVerticalFm","preflightTunnelDoorSolid","commitTunnelDoorSolid","playTunnelDoorFm",
                        "commitMonitorSolid","playMonitorFm","awardMonitorItem","commitTimePostDirection","requestTimePostSubCpu")) {
                    var method=java.util.Arrays.stream(CdOpeningObjectRuntime.class.getDeclaredMethods()).filter(m->m.getName().equals(name)).findFirst().orElseThrow();
                    method.setAccessible(true);var fault=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(f.runtime,new Object[]{null}));
                    assertInstanceOf(IllegalStateException.class,fault.getCause(),name);
                }
                var draw=CdOpeningObjectRuntime.class.getDeclaredMethod("drawDynamic",CdObjectPool.class,int.class);draw.setAccessible(true);
                var fault=assertThrows(java.lang.reflect.InvocationTargetException.class,()->draw.invoke(f.runtime,pool,40));assertInstanceOf(IllegalStateException.class,fault.getCause());
                assertEquals(before,pool.capture().slots());assertEquals(actor,f.frame.state());assertEquals(env,f.frame.environment());assertEquals(sounds,f.runtime.lastSoundRequests());
            }finally{boulderSet(f.runtime,"inSolidCommit",false);boulderUnstage(f);}
        }
    }

    private static CdOpeningObjectRuntime.LogicalBanks diagonalBanks(int[] b) {
        return new CdOpeningObjectRuntime.LogicalBanks(b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14]);
    }
    private static CdObjectState diagonalImage(Fixture f,CdObjectState image,int routine,int sprite,int flags,int subtype) throws java.io.IOException {
        var assets=CdSpring.Assets.fromObjectAssets(f.assets.openingObjects());
        return image.moveByte(ID,0x0A).moveByte(ROUTINE,routine).moveByte(SPRITE_FLAGS,sprite).moveByte(FLAGS,flags).moveByte(SUBTYPE,subtype)
                .moveLong(MAPPINGS,assets.mappingsAddress()+80).moveWord(TILE,(subtype&2)==0 ? 0x490 : 0x2490).moveWord(SPRING_SPEED,assets.speedWord(subtype))
                .moveByte(WIDTH,24).moveByte(Y_RADIUS,12).moveByte(PRIORITY,4).moveWord(X,0x100).moveWord(Y,0x780).moveWord(SPRING_ANCHOR_X,0x100);
    }
    private static CdSpring.SolidCall diagonalSolidCall(Fixture f,CdObjectState object) {
        var cut=f.runtime.placement().pool().capture();var player=tunnelDoorPlayer(f);var globals=tunnelDoorGlobals(f);var env=f.frame.environment();
        var result=CdSolidObject.fromAssets(f.assets).run(CdSolidObject.Entry.SOLID_OBJECT,object,player,
                new CdSolidObject.Gates(env.globals().debugMode(),env.movement().gates().miniSonic()),globals,cut);
        return new CdSpring.SolidCall(cut,player,globals,result);
    }
    private static CdSolidObject.Player diagonalLaunchPlayer(CdSolidObject.Player postSolid,int subtype,int h,int v) {
        var a=postSolid.actor();var m=a.motion();int component=(subtype&2)==0 ? 2896 : 1810;
        int x=(((m.centreX16_16()>>>16)+(h==0 ? -8 : 8))&0xFFFF)<<16|(m.centreX16_16()&0xFFFF);
        int y=(((m.centreY16_16()>>>16)+(v==0 ? 8 : -8))&0xFFFF)<<16|(m.centreY16_16()&0xFFFF);
        var next=new CdPlayerMotionState(x,y,h==0 ? component : -component,v==0 ? -component : component,m.groundVelocity(),
                m.routine(),m.angle(),(m.flags()&~0x29)|2|h,m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),
                m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(next,a.tile(),a.priority(),a.hurt(),a.rotationDistance()),
                postSolid.slotId(),postSolid.pushObject(),postSolid.standObject());
    }
    private static void assertDiagonalApplied(Fixture f,CdPlayerBody.State before,CdPlayerBody.Environment env,CdSolidObject.Result solid,CdSolidObject.Player expected) {
        var a=expected.actor();var h=before.header();var t=before.timers();
        assertEquals(new CdPlayerBody.State(a.motion(),before.animation(),new CdPlayerBody.Header(h.slotId(),h.mappingAddress(),a.tile(),a.priority(),h.width()),
                new CdPlayerBody.Timers(a.hurt(),t.invincibility(),t.speedShoes(),t.reset()),before.history(),a.rotationDistance(),
                new CdPlayerBody.ObjectLinks(expected.pushObject(),expected.standObject())),f.frame.state());
        var g=solid.globals();var movement=env.movement();var old=movement.globals();var b=env.globals();
        assertEquals(new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(),movement.bounds(),movement.gates(),
                new CdPlayerMovement.Globals(old.lookMode(),old.cameraYCentre(),g.primaryAngle(),g.secondaryAngle(),old.boredTimer(),g.scoreChain(),old.debugAngle(),
                        old.debugAngleShift(),old.debugQuadrant(),old.debugFloorDistance())),env.controls(),
                new CdPlayerBody.Globals(b.levelFrames(),b.cameraXCentre(),b.timeAttackMode(),b.debugMode(),b.debugCheat(),b.timeWarpTimer(),g.invincible(),b.bossMusic()),env.dynamicSlots()),f.frame.environment());
        assertEquals(solid.player().actor().motion().centreX16_16()&0xFFFF,a.motion().centreX16_16()&0xFFFF);
        assertEquals(solid.player().actor().motion().centreY16_16()&0xFFFF,a.motion().centreY16_16()&0xFFFF);
    }
    private static List<CdOpeningObjectRuntime.SoundRequest> diagonalSounds(Fixture f,CdSolidObject.Result solid,boolean launched) {
        var sounds=new ArrayList<>(f.runtime.lastSoundRequests());horizontalSharedSounds(sounds,solid);
        if(launched)sounds.add(new CdOpeningObjectRuntime.SpringFm(CdSpring.FmSound.FM_SPRING));return sounds;
    }
    private static CdObjectState diagonalAnimated(Fixture f,CdObjectState object) throws java.io.IOException {
        var visual=f.assets.openingObjects().springDiagonal();
        var next=CdObjectAnimation.step(new CdObjectAnimation.State(object.unsignedByte(ANIMATION),object.unsignedByte(PREVIOUS_ANIMATION),
                object.unsignedByte(ANIMATION_FRAME),object.unsignedByte(ANIMATION_TIME),object.unsignedByte(MAP_FRAME),object.spriteFlags(),object.flags(),
                object.routine(),object.unsignedByte(ROUTINE_2)),new CdObjectAnimation.AnimationTable(visual.animationTable(),0,1));
        return object.moveByte(ANIMATION,next.animationId()).moveByte(PREVIOUS_ANIMATION,next.previousId()).moveByte(ANIMATION_FRAME,next.scriptIndex())
                .moveByte(ANIMATION_TIME,next.timer()).moveByte(MAP_FRAME,next.mapFrame()).moveByte(SPRITE_FLAGS,next.spriteFlags())
                .moveByte(ROUTINE,next.routine()).moveByte(ROUTINE_2,next.routine2());
    }
    private static int beforeWord(int position) { return position>>>16; }
    private static CdObjectPresentation.PatternSource diagonalPixels(Fixture f,com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State resident) throws java.io.IOException {
        var objects=f.assets.openingObjects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.frame.artState(),f.runtime.powerupArtState(),objects,
                objects.frontierArtBank(),objects.contactArtBank(),objects.floatBlockArtBank(),objects.springArtBank(),objects.tunnelDoorArtBank(),objects.monitorTimeArtBank(),resident,
                BANKS.player(),BANKS.ring(),BANKS.mosqui(),BANKS.plant(),BANKS.ramp(),BANKS.explosion(),BANKS.points(),BANKS.flower(),BANKS.powerup(),BANKS.floatBlock(),
                BANKS.spring(),BANKS.tunnelDoorVertical(),BANKS.monitorTime(),BANKS.boulder(),BANKS.diagonalSpring());
    }
    private static com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State diagonalChanged(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State original) {
        var state=original;for(int tile=0;tile<46;tile++) {byte[] bytes=new byte[32];java.util.Arrays.fill(bytes,(byte)(0x81+tile));
            state=com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.writeTile(state,0x9200+tile*32,CdAssetBytes.copyOf(bytes));}return state;
    }
    private static com.openggf.game.soniccd.resources.CdR11aSectionArt.State diagonalSectionCopy(
            com.openggf.game.soniccd.resources.CdR11aSectionArt.State original,com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State resident,
            com.openggf.game.soniccd.resources.CdR11aPlcBuffer.State queue) throws Exception {
        // Rebuild a detached, same-owner external ingress; never mutate the immutable original or production owners.
        String[] fields={"owner","sectionWord","queue","codeTable","decoder","savedDecoder","remainingTilesWord","processBudgetWord","hintWord","padWord","residency","stopped"};
        var constructors=original.getClass().getDeclaredConstructors();assertEquals(1,constructors.length);var constructor=constructors[0];
        assertEquals(fields.length,constructor.getParameterCount());Object[] values=new Object[fields.length];
        for(int i=0;i<fields.length;i++){var field=original.getClass().getDeclaredField(fields[i]);field.setAccessible(true);values[i]=field.get(original);}
        values[2]=queue;values[10]=resident;constructor.setAccessible(true);
        return (com.openggf.game.soniccd.resources.CdR11aSectionArt.State)constructor.newInstance(values);
    }
    private static List<String> diagonalGuards() {
        return List.of("inDrawCallback","inSolidCommit","inSpringLaunch","inSpinTunnelCommit","inTunnelDoorFm","inHorizontalTunnelDoorSolid",
                "inHorizontalTunnelDoorFm","inMonitorAngle","inMonitorFm","inMonitorAward","inTimePostDirection","inTimePostSubCpu");
    }
    private static void diagonalReject(Fixture f,String name,Class<?> type,Object call) throws Exception {
        var pool=f.runtime.placement().pool();var before=pool.capture().slots();var actor=f.frame.state();var env=f.frame.environment();
        var sounds=f.runtime.lastSoundRequests();var resident=f.runtime.sectionArtState();var reserved=horizontalReserved(f.runtime);
        var method=CdOpeningObjectRuntime.class.getDeclaredMethod(name,type);method.setAccessible(true);
        var fault=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(f.runtime,call));
        assertTrue(fault.getCause() instanceof IllegalStateException||fault.getCause() instanceof IllegalArgumentException,name+": "+fault.getCause());
        assertEquals(before,pool.capture().slots());assertEquals(actor,f.frame.state());assertEquals(env,f.frame.environment());assertEquals(sounds,f.runtime.lastSoundRequests());
        assertSame(resident,f.runtime.sectionArtState());assertEquals(reserved,horizontalReserved(f.runtime));
    }
    private static CdSpring.LaunchCall diagonalSourceLaunch(Fixture f) throws java.io.IOException {
        var captured=new CdSpring.LaunchCall[1];var stop=new IllegalStateException("constructed external launch-port stop, before application");
        var env=f.frame.environment();var player=tunnelDoorPlayer(f);var globals=tunnelDoorGlobals(f);var pool=f.runtime.placement().pool();
        var body=new CdSpring(f.runtime.placement(),CdSpring.Assets.fromObjectAssets(f.assets.openingObjects()),
                new com.openggf.game.soniccd.player.CdPlayerMath(f.assets.player()),CdSolidObject.fromAssets(f.assets),
                call->{pool.write(call.result().object());for(var image:call.result().otherSlots())pool.write(image);
                    f.frame.applyObjectSolidResult(call.expectedPlayer(),call.expectedGlobals(),call.result());},
                call->{captured[0]=call;throw stop;},(p,s)->fail("launch stop precedes DrawObject"),sound->fail("launch stop precedes FM"));
        var fault=assertThrows(IllegalStateException.class,()->body.run(40,new CdSpring.Globals(PRE.xWord()),new CdSpring.SolidInputs(player,
                new CdSolidObject.Gates(env.globals().debugMode(),env.movement().gates().miniSonic()),globals)));
        assertSame(stop,fault);assertNotNull(captured[0]);return captured[0];
    }
    // END diagonal Spring Runtime R1 tests-first additions.

    // BEGIN common Spikes Runtime R1 tests FIRST; all Java UNRUN by this source-only author.
    // J=8f283a9597999c991a9233c19cf8543fd21b892b; N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
    // Actual shared pure Results and actual pool captures; constructed device ingress is not route evidence.
    // Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>

    @Test void spikesSixteenthBankPreservesEveryOlderConstructorAndOneCachedActualBody() {
        var full = new CdOpeningObjectRuntime.LogicalBanks(100,124,138,208,236,299,369,374,413,449,464,500,512,594,613,659);
        assertEquals(16, CdOpeningObjectRuntime.LogicalBanks.class.getRecordComponents().length);
        assertEquals("spikes", CdOpeningObjectRuntime.LogicalBanks.class.getRecordComponents()[15].getName());
        assertEquals(full, new CdOpeningObjectRuntime.LogicalBanks(100,124,138));
        assertEquals(full, new CdOpeningObjectRuntime.LogicalBanks(100,124,138,208,236));
        assertEquals(full, new CdOpeningObjectRuntime.LogicalBanks(100,124,138,208,236,299,369,374));
        assertEquals(full, new CdOpeningObjectRuntime.LogicalBanks(100,124,138,208,236,299,369,374,413));
        assertEquals(full, new CdOpeningObjectRuntime.LogicalBanks(100,124,138,208,236,299,369,374,413,449));
        assertEquals(full, new CdOpeningObjectRuntime.LogicalBanks(100,124,138,208,236,299,369,374,413,449,464));
        assertEquals(full, new CdOpeningObjectRuntime.LogicalBanks(100,124,138,208,236,299,369,374,413,449,464,500));
        assertEquals(full, new CdOpeningObjectRuntime.LogicalBanks(100,124,138,208,236,299,369,374,413,449,464,500,512));
        assertEquals(full, new CdOpeningObjectRuntime.LogicalBanks(100,124,138,208,236,299,369,374,413,449,464,500,512,594));
        assertEquals(full, new CdOpeningObjectRuntime.LogicalBanks(100,124,138,208,236,299,369,374,413,449,464,500,512,594,613));
        assertEquals(567, full.spikes()+8-full.player()); assertEquals(BANKS.diagonalSpring()+46,BANKS.spikes());
        assertEquals(List.of(3,5,8,9,10,11,12,13,14,15,16), java.util.Arrays.stream(CdOpeningObjectRuntime.LogicalBanks.class.getDeclaredConstructors())
                .map(java.lang.reflect.Constructor::getParameterCount).sorted().toList());
        assertEquals(1, java.util.Arrays.stream(CdOpeningObjectRuntime.class.getDeclaredFields()).filter(v->v.getType()==CdSpikes.class).count());
        assertFalse(java.util.Arrays.stream(CdOpeningObjectRuntime.Snapshot.class.getDeclaredFields()).anyMatch(v->
                v.getType()==CdSpikes.class || v.getType()==CdSpikes.Result.class || v.getType()==CdSpikes.SolidCall.class
                        || v.getType()==CdSpikes.YRewindCall.class || v.getType()==CdSpikes.HurtCall.class || v.getType()==CdSpikes.Inputs.class));
    }

    @Test void spikesColdSeedsOnlyEightCompleteNinthCanonicalTilesAndKeepsTheSourceBankCached() throws Exception {
        try(var f=new Fixture()) {
            var objects=f.assets.openingObjects();var bank=objects.sectionArtSources();assertSame(bank,objects.sectionArtSources());
            assertEquals(9,bank.sources().size());var source=bank.sources().get(8);
            assertSame(source,bank.source(com.openggf.game.soniccd.level.CdR11aAsset.ART_SPIKES));
            assertEquals(1,source.canonicalPlcId());assertEquals(0,source.canonicalRowIndex());assertEquals(8,source.tileCount());
            assertEquals(0x8008,source.readerBytes().unsignedWord(0));assertEquals(256,source.decode().bytes().length());
            assertEquals(f.assets.loadPlan().plcs().get(1).entries().getFirst(),source.canonicalPlc());assertEquals(0x6400,source.canonicalPlc().vramAddress());
            var state=f.runtime.sectionArtState();cameraArtBytes(state.residency(),0x6400,source.decode().bytes());
            var region=state.residency().region(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_6400);
            for(byte mask:region.knownMask().copy())assertEquals(1,mask);
            int known=0;for(var r:com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.values())
                for(byte mask:state.residency().region(r).knownMask().copy())known+=mask;
            assertEquals((70+28+63+19+46+8)*32,known);
            assertEquals(0,state.remainingTilesWord());assertEquals(0,state.queue().headSource());assertTrue(state.decoder().isEmpty());
            assertArrayEquals(new byte[512],state.codeTable().copy());assertEquals(0,state.sectionWord());
        }
    }

    @Test void spikesCurrentPolicyResolvesAll567DistinctSamplesAndOnlyItsEightCurrentValues() throws Exception {
        try(var f=new Fixture()) {
            var original=f.runtime.sectionArtState().residency();var changed=spikesChanged(original);var pixels=spikesPixels(f,changed);
            int[] first={0x780,0x7AE,0x3A0,0x424,0x441,0x680,0x6C6,0x6D7,0x544,0x334,0x520,0x328,0x5A8,0x374,0x490,0x320};
            int[] count={24,14,70,28,63,70,5,39,36,15,36,12,82,19,46,8};var ids=new java.util.HashSet<Integer>();
            for(int span=0;span<first.length;span++)for(int tile=0;tile<count[span];tile++)assertTrue(ids.add(pixels.resolve(first[span]+tile).logicalPatternId()));
            assertEquals(567,ids.size());var source=cameraArtSource(f,com.openggf.game.soniccd.level.CdR11aAsset.ART_SPIKES);
            for(int tile=0;tile<8;tile++) {
                var resolved=pixels.resolve(0x320+tile);assertEquals(BANKS.spikes()+tile,resolved.logicalPatternId());
                assertEquals(CdObjectPresentation.patternVersion(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(changed,0x320+tile)),resolved.version());
                assertNotEquals(CdObjectPresentation.patternVersion(source.slice(tile*32,32)),resolved.version());
            }
            cameraArtBytes(original,0x6400,source);assertThrows(UnsupportedOperationException.class,()->pixels.resolve(0x31F));
            assertThrows(UnsupportedOperationException.class,()->pixels.resolve(0x4BE));
        }
    }

    @Test void spikesSixteenBankEveryPairAndBothBoundsRejectAtBothFactoriesBeforeFrameMutation() throws Exception {
        var assets=load();var carry=cameraArtDevIngress(assets);
        int[] bases={100,124,138,208,236,299,369,374,413,449,464,500,512,594,613,659};
        int[] sizes={24,14,70,28,63,70,5,39,36,15,36,12,82,19,46,8};var bad=new ArrayList<CdOpeningObjectRuntime.LogicalBanks>();
        for(int left=0;left<16;left++)for(int right=left+1;right<16;right++){var next=bases.clone();next[right]=bases[left];bad.add(spikesBanks(next));}
        for(int index=0;index<16;index++)for(int value:new int[]{-1,Integer.MAX_VALUE-sizes[index]+2}){var next=bases.clone();next[index]=value;bad.add(spikesBanks(next));}
        assertEquals(152,bad.size());try(var frame=CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets,0)) {
            var actor=frame.captureActor();var shared=frame.captureShared();
            for(var banks:bad) {
                assertThrows(IllegalArgumentException.class,()->CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                        assets,frame,INITIAL,CONTACT,SCORE,LEVEL,new byte[640],banks,PRE,value->fail("no admission callback")));
                assertThrows(IllegalArgumentException.class,()->CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(
                        assets,frame,INITIAL,CONTACT,SCORE,LEVEL,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]),
                        new CdPowerupArtRuntime.State(new byte[0x480],new byte[0x480]),carry,new byte[640],banks,PRE,value->fail("no admission callback")));
                assertEquals(actor,frame.captureActor());assertEquals(shared.environment(),frame.environment());assertSame(shared.artState(),frame.artState());
                assertEquals(shared.vIntClock(),frame.vIntClock());assertFalse(frame.closed());
            }
        }
    }

    @Test void spikesRestartCarriesChangedEightMasksTableAndRawSectionVerbatimWithoutAService() throws Exception {
        try(var f=new Fixture()) {
            var original=f.runtime.sectionArtState().residency();var resident=spikesChanged(original);byte[] table=new byte[512];
            for(int i=0;i<table.length;i++)table[i]=(byte)(i^0x5D);var carry=new CdOpeningObjectRuntime.CameraArtRestartIngress(resident,CdAssetBytes.copyOf(table),0x035A);
            try(var frame=CdPlayerFrameRuntime.externalTouchRequiredDevelopment(f.assets,0)) {
                var actor=frame.captureActor();var shared=frame.captureShared();var publications=new ArrayList<CdPlayerArtRuntime.State>();frame.bindArtPublication((r,s)->publications.add(s));
                try(var runtime=CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(f.assets,frame,INITIAL,CONTACT,SCORE,LEVEL,
                        CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]),new CdPowerupArtRuntime.State(new byte[0x480],new byte[0x480]),
                        carry,new byte[640],BANKS,PRE,value->fail("restart has no helper callback"))) {
                    assertSame(resident,runtime.sectionArtState().residency());assertArrayEquals(table,runtime.sectionArtState().codeTable().copy());
                    assertEquals(0x035A,runtime.sectionArtState().sectionWord());assertEquals(0,runtime.sectionArtState().queue().headSource());
                    assertTrue(runtime.sectionArtState().decoder().isEmpty());assertEquals(0,runtime.sectionArtState().remainingTilesWord());
                    for(var region:com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.values())assertEquals(resident.region(region),runtime.sectionArtState().residency().region(region));
                    assertEquals(actor,frame.captureActor());assertEquals(shared.environment(),frame.environment());assertEquals(shared.vIntClock(),frame.vIntClock());
                    assertTrue(publications.isEmpty());assertTrue(runtime.lastSoundRequests().isEmpty());assertTrue(runtime.displayedFrame().tiles().isEmpty());
                }
            }
            cameraArtBytes(original,0x6400,cameraArtSource(f,com.openggf.game.soniccd.level.CdR11aAsset.ART_SPIKES));
        }
    }

    @Test void spikesRestartUnknownFirstOrLastRefusesWithoutManufacturingMaskOrChangingFrame() throws Exception {
        var assets=load();for(int missing:List.of(0,7)) {
            var objects=assets.openingObjects();var frontier=objects.frontierArtBank();
            var resident=com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.devSeed(objects.initialArtBank().binding(
                    com.openggf.game.soniccd.level.CdR11aAsset.ART_MOSQUI).bytes(),frontier.binding(com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_PLANT).bytes(),
                    frontier.binding(com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_RAMP_BOOST).bytes());
            for(var id:List.of(com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER,com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING,com.openggf.game.soniccd.level.CdR11aAsset.ART_SPIKES)) {
                var source=objects.sectionArtSources().source(id);for(int tile=0;tile<source.tileCount();tile++)if(id!=com.openggf.game.soniccd.level.CdR11aAsset.ART_SPIKES||tile!=missing)
                    resident=com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.writeTile(resident,source.canonicalPlc().vramAddress()+tile*32,source.decode().bytes().slice(tile*32,32));
            }
            var exact=resident;var carry=new CdOpeningObjectRuntime.CameraArtRestartIngress(exact,CdAssetBytes.copyOf(new byte[512]),0x005A);
            try(var frame=CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets,0)) {
                var actor=frame.captureActor();var shared=frame.captureShared();var mask=exact.region(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_6400).knownMask();
                var fault=assertThrows(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.OwnershipBoundary.class,()->
                        CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(assets,frame,INITIAL,CONTACT,SCORE,LEVEL,
                                CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]),new CdPowerupArtRuntime.State(new byte[0x480],new byte[0x480]),
                                carry,new byte[640],BANKS,PRE,value->fail("no admission callback")));
                assertEquals(0x320+missing,fault.nativePatternId());assertEquals(actor,frame.captureActor());assertEquals(shared.environment(),frame.environment());
                assertEquals(shared.vIntClock(),frame.vIntClock());assertSame(shared.artState(),frame.artState());assertFalse(frame.closed());
                assertArrayEquals(mask.copy(),exact.region(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_6400).knownMask().copy());
            }
        }
    }

    @Test void spikesActualInitFallsThroughAtAllFlipsAndOnlyVerticalFlipSetsHazardByte() throws Exception {
        for(int flip=0;flip<4;flip++)try(var f=new Fixture()) {
            springDeviceCut(f);var pool=f.runtime.placement().pool();seedSolidPlayer(f,0,0,0,0,0,0x01001234,0x07005678);
            var dirty=spikesImage(f,pool.state(40),0,flip,0).moveByte(COLLISION_TYPE,0x17).moveWord(TILE,0xDEAD)
                    .moveWord(X_FRACTION,0xA55A).moveWord(Y_FRACTION,0x5AA5).moveByte(ROUTINE_2,0xC7);
            pool.write(dirty);var actor=f.frame.state();f.runtime.runDynamicSlotsAfterReservedSlots();var actual=f.slot(40);
            assertEquals(2,actual.routine());assertEquals(0x20C1F0,actual.longBits(MAPPINGS));assertEquals(0x320,actual.word(TILE));
            assertEquals((flip&2)==0?16:18,actual.unsignedByte(WIDTH));assertEquals(8,actual.unsignedByte(Y_RADIUS));assertEquals(3,actual.unsignedByte(PRIORITY));
            assertEquals((flip&2)==0?0x17:0x83,actual.unsignedByte(COLLISION_TYPE));assertEquals(0xA55A,actual.word(X_FRACTION));
            assertEquals(0x5AA5,actual.word(Y_FRACTION));assertEquals(0xC7,actual.unsignedByte(ROUTINE_2));assertEquals(actor,f.frame.state());
            var sat=f.runtime.finishAfterCamera(POST);assertEquals(4,sat.provenance().stream().filter(v->v.slotRef().wholeIndex()==40).count());f.runtime.finishOrdinaryTail();
        }
    }

    @Test void spikesBadTileSubtypeRetainsOnlyItsNativeInitPrefixBeforeLaterDispatch() throws Exception {
        try(var f=new Fixture()) {
            springDeviceCut(f);var pool=f.runtime.placement().pool();var dirty=spikesImage(f,pool.state(40),0,0,0).moveByte(SUBTYPE2,1)
                    .moveWord(TILE,0xDEAD).moveByte(WIDTH,0xA5).moveByte(Y_RADIUS,0x5A);pool.write(dirty);pool.write(pool.state(41).moveByte(ID,0x7E));
            var actor=f.frame.state();var fault=assertThrows(CdSpikes.UnsupportedSpikes.class,f.runtime::runDynamicSlotsAfterReservedSlots);
            assertEquals(CdSpikes.UnsupportedBoundary.TILE_ID_SUBTYPE2,fault.boundary());
            assertEquals(dirty.addByte(ROUTINE,2).moveLong(MAPPINGS,0x20C1F0).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,3),f.slot(40));
            assertEquals(actor,f.frame.state());assertEquals(0x7E,f.slot(41).id());assertTrue(f.runtime.closed());assertTrue(f.frame.closed());
        }
    }

    @Test void spikesUnsignedBorrowAbsDyHasExactThirtyTwoBoundaryAndNoOuterVisibilityGate() throws Exception {
        int[][] rows={{0x761,0x780,1},{0x760,0x780,0},{0x79F,0x780,1},{0x7A0,0x780,0},{0,0xFFFF,0},{0xFFFF,0,0}};
        for(var row:rows)try(var f=new Fixture()) {
            springDeviceCut(f);var pool=f.runtime.placement().pool();seedSolidPlayer(f,8,40,1,0x123,0,0x01001234,(row[0]<<16)|0x5678);
            pool.write(spikesImage(f,pool.state(40),2,4,8).moveWord(Y,row[1]));var sounds=new ArrayList<>(f.runtime.lastSoundRequests());
            if(row[2]!=0)sounds.add(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)));
            f.runtime.runDynamicSlotsAfterReservedSlots();assertEquals(sounds,f.runtime.lastSoundRequests());
            assertEquals(row[2]==0?40:0,f.frame.state().objectLinks().standObject());
            // Extreme unsigned-Y ingress intentionally stops before an unrelated finite-SAT policy boundary.
        }
    }

    @Test void spikesDirectNormalDebugCheatKillAndFullWordDebugNoWriteAreActualSourceCalls() throws Exception {
        int[][] rows={{1,0,0,4},{0x100,0,0,4},{0,0,0,6},{0,1,0,2},{0,0,2,4},{0,0,3,4}};
        for(var row:rows)try(var f=new Fixture(INITIAL,new CdPlayerBody.ContactGlobals(0,row[2],0xA5),SCORE)) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,0,0x123,0x01001234,0x0767FF80,0);spikesDebug(f,row[1],row[0]);
            var pool=f.runtime.placement().pool();pool.write(spikesImage(f,pool.state(40),2,0x84,8));var world=spikesWorld(f);
            f.runtime.runDynamicSlotsAfterReservedSlots();assertEquals(row[3],f.frame.state().motion().routine());assertEquals(world,spikesWorld(f));
            assertEquals(row[3]==4?0x78:0,f.frame.state().timers().hurt());assertEquals(2,f.slot(40).routine());
            assertEquals(0x20C1F0,f.slot(40).longBits(MAPPINGS));monitorFinish(f);
        }
    }

    @Test void spikesActualDirectHurtBranchesPublishOnlyTheOwnedActorContactAndOrderedFmOnce() throws Exception {
        int[][] rows={{1,0,0,8},{0x100,0,0,8},{0,0,0,8},{0,1,0,8},{0,0,2,8},{0,0,3,8},{1,0,0,0x48},{1,0,0,0x0C},{0,0,0,0x0C}};
        for(var row:rows)try(var f=new Fixture(INITIAL,new CdPlayerBody.ContactGlobals(0,row[2],0xA5),SCORE)) {
            springDeviceCut(f);spikesSeedPlayer(f,row[3],40,7,0x123,0x01001234,0x0767FF80,0);spikesDebug(f,row[1],row[0]);
            var pool=f.runtime.placement().pool();pool.write(spikesImage(f,pool.state(40),2,0x84,8));var world=spikesWorld(f);
            var call=spikesReachedHurt(f);var expected=call.result();var source=f.slot(40);var display=f.frame.display();var animation=f.frame.state().animation();
            var sounds=spikesHurtSounds(f,expected);spikesInvoke(f,"commitSpikesHurt",CdSpikes.HurtCall.class,call);
            assertEquals(expected.actor(),tunnelDoorPlayer(f).actor());assertEquals(expected.player(),spikesPlayerGlobals(f));assertEquals(world,spikesWorld(f));
            assertEquals(source,f.slot(40));assertEquals(display,f.frame.display());assertEquals(animation,f.frame.state().animation());assertEquals(sounds,f.runtime.lastSoundRequests());
            assertTrue(expected.changedSlots().isEmpty());assertEquals(List.of(),expected.calls().stream().filter(CdPlayerTouchResponse.AddPoints.class::isInstance).toList());
            if(expected.exit()==CdPlayerTouchResponse.Exit.KILL_PLAYER) {
                var fm=expected.calls().stream().filter(CdPlayerTouchResponse.PlayFm.class::isInstance).map(CdPlayerTouchResponse.PlayFm.class::cast).map(CdPlayerTouchResponse.PlayFm::sound).toList();
                assertEquals((row[3]&4)!=0?List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP,CdPlayerTouchResponse.FmSound.FM_HURT):List.of(CdPlayerTouchResponse.FmSound.FM_HURT),fm);
            } else if(expected.exit()==CdPlayerTouchResponse.Exit.HURT) {
                assertEquals(0x78,f.frame.state().timers().hurt());assertEquals((row[3]&0x40)!=0?-0x200:-0x400,f.frame.state().motion().yVelocity());
                assertEquals(row[2]&~1,f.runtime.contactGlobals().shield());assertEquals((row[2]&1)==0?0:0xA5,f.runtime.contactGlobals().combineRing());
            } else {assertEquals(call.expectedActor(),expected.actor());assertEquals(call.expectedPlayer(),expected.player());assertTrue(expected.calls().isEmpty());}
            boulderUnstage(f);
        }
    }

    @Test void spikesPublicPhysicalDispatchPerformsHurtThenDrawAndRetainsPrefixesBeforeLaterUnknown() throws Exception {
        try(var f=new Fixture(INITIAL,new CdPlayerBody.ContactGlobals(0,3,0xA5),SCORE)) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,7,0x123,0x01001234,0x0767FF80,0);var pool=f.runtime.placement().pool();
            pool.write(spikesImage(f,pool.state(40),0,0x80,8));pool.write(pool.state(41).moveByte(ID,0x7E));var before=f.frame.display();
            var fault=assertThrows(UnsupportedOperationException.class,f.runtime::runDynamicSlotsAfterReservedSlots);
            assertTrue(fault.getMessage().contains("41"));assertEquals(2,f.slot(40).routine());assertEquals(0x20C1F0,f.slot(40).longBits(MAPPINGS));
            assertEquals(4,f.frame.state().motion().routine());assertEquals(0x78,f.frame.state().timers().hurt());
            assertEquals(0x0764DC80,f.frame.state().motion().centreY16_16()); // 0765FF80 - 00012300, native LONG subtraction.
            assertEquals(2,f.runtime.contactGlobals().shield());assertEquals(0xA5,f.runtime.contactGlobals().combineRing());assertEquals(before,f.frame.display());
            assertEquals(0,f.slot(40).spriteFlags()&0x80);assertTrue(f.runtime.closed());assertTrue(f.frame.closed());
            assertThrows(IllegalStateException.class,f.runtime::runDynamicSlotsAfterReservedSlots);
        }
    }

    @Test void spikesSolidPublishesPostGlobalsOtherSlotsFractionsAndLinksBeforeHurt() throws Exception {
        try(var f=new Fixture(INITIAL,new CdPlayerBody.ContactGlobals(0,3,5),SCORE)) {
            springDeviceCut(f);spikesSeedPlayer(f,8,41,0,0x100,0x01008000,0x07671234,0);seedTunnelDoorGlobals(f,0,0x3456,0x55,0x66);
            var pool=f.runtime.placement().pool();pool.write(pool.state(41).moveByte(FLAGS,0xA8));
            pool.write(spikesImage(f,pool.state(40),2,0x84,0).moveWord(X_VELOCITY,0x123));var before=f.frame.state();var env=f.frame.environment();
            var call=spikesSolidCall(f);assertEquals(CdSolidObject.Path.STAND_ON,call.result().path());
            assertEquals(List.of(41),call.result().otherSlots().stream().map(v->v.identity().wholeIndex()).toList());spikesStage(f,40);
            spikesInvoke(f,"commitSpikesSolid",CdSpikes.SolidCall.class,call);assertTunnelDoorSolidApplied(f,before,env,call.result());
            assertEquals(0xA300,f.frame.state().motion().centreX16_16()&0xFFFF);assertEquals(0x1234,f.frame.state().motion().centreY16_16()&0xFFFF);
            assertEquals(0xA0,f.slot(41).flags());assertEquals(40,f.frame.state().objectLinks().standObject());boulderUnstage(f);
        }
    }

    @Test void spikesAllThreePortsRejectEveryExistingMutualGuardWrongPhaseAndPhysicalVisit() throws Exception {
        try(var f=new Fixture(INITIAL,new CdPlayerBody.ContactGlobals(0,3,7),SCORE)) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,0,0x123,0x01001234,0x0767FF80,0);var pool=f.runtime.placement().pool();pool.write(spikesImage(f,pool.state(40),2,0x84,8));
            var solid=spikesSolidCall(f);var hurt=spikesReachedHurt(f);var actor=tunnelDoorPlayer(f).actor();
            var y=new CdSpikes.YRewindCall(f.slot(40).identity(),actor,actor.motion().centreY16_16()-(((short)actor.motion().yVelocity())<<8));boulderUnstage(f);
            String[] names={"commitSpikesSolid","commitSpikesYRewind","commitSpikesHurt"};Class<?>[] types={CdSpikes.SolidCall.class,CdSpikes.YRewindCall.class,CdSpikes.HurtCall.class};
            Object[] calls={solid,y,hurt};for(int i=0;i<names.length;i++)spikesReject(f,names[i],types[i],calls[i]);
            spikesStage(f,41);for(int i=0;i<names.length;i++)spikesReject(f,names[i],types[i],calls[i]);spikesStage(f,40);
            for(var guard:diagonalGuards()) {boulderSet(f.runtime,guard,true);for(int i=0;i<names.length;i++)spikesReject(f,names[i],types[i],calls[i]);boulderSet(f.runtime,guard,false);}
            boulderUnstage(f);
        }
    }

    @Test void spikesSharedCommitFlagRejectsAllSiblingCallbacksAndDrawWithoutAnotherFlag() throws Exception {
        try(var f=new Fixture()) {
            springDeviceCut(f);spikesStage(f,40);boulderSet(f.runtime,"inSolidCommit",true);
            for(String name:List.of("commitBoulderSolid","commitTopSolid","commitSpringSolid","commitSpringLaunch","playSpringFm","commitTunnelDoorVerticalSolid",
                    "playTunnelDoorVerticalFm","preflightTunnelDoorSolid","commitTunnelDoorSolid","playTunnelDoorFm","commitMonitorSolid","commitMonitorPrimaryAngle",
                    "playMonitorFm","awardMonitorItem","commitTimePostDirection","requestTimePostSubCpu","commitSpinTunnelTag")) {
                var method=java.util.Arrays.stream(CdOpeningObjectRuntime.class.getDeclaredMethods()).filter(v->v.getName().equals(name)).findFirst().orElseThrow();method.setAccessible(true);
                Object[] args=new Object[method.getParameterCount()];for(int i=0;i<args.length;i++)if(method.getParameterTypes()[i]==int.class)args[i]=0;
                var fault=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(f.runtime,args));assertInstanceOf(IllegalStateException.class,fault.getCause(),name);
            }
            var draw=CdOpeningObjectRuntime.class.getDeclaredMethod("drawDynamic",CdObjectPool.class,int.class);draw.setAccessible(true);
            var fault=assertThrows(java.lang.reflect.InvocationTargetException.class,()->draw.invoke(f.runtime,f.runtime.placement().pool(),40));assertInstanceOf(IllegalStateException.class,fault.getCause());
            boulderSet(f.runtime,"inSolidCommit",false);boulderUnstage(f);
        }
    }

    @Test void spikesSolidPreflightsAll96FullImagesForeignOwnersAndExactFrameCasBeforeAnyWrite() throws Exception {
        for(int scenario=0;scenario<8;scenario++)try(var f=new Fixture();var other=new Fixture()) {
            springDeviceCut(f);spikesSeedPlayer(f,8,41,0,0x100,0x01008000,0x07671234,0);var pool=f.runtime.placement().pool();
            pool.write(spikesImage(f,pool.state(40),2,0x84,0));pool.write(pool.state(41).moveByte(FLAGS,8));var call=spikesSolidCall(f);
            if(scenario==0)pool.write(pool.state(127).moveWord(TUNNEL_DOOR_TIMER,0xA55A));
            if(scenario==1)pool.write(pool.state(40).moveByte(ID,0x7E));
            if(scenario==2){var p=other.runtime.placement().pool();p.write(spikesImage(other,p.state(40),2,0x84,0));call=spikesSolidCall(other);}
            if(scenario==3)spikesSeedPlayer(f,8,41,0,0x100,0x01008001,0x07671234,0);
            if(scenario==4)seedTunnelDoorGlobals(f,0,0xA55A,0x12,0x34);
            if(scenario==5||scenario==6){var images=new ArrayList<>(call.callCut().slots());if(scenario==5)images.removeLast();else images.set(95,other.runtime.placement().pool().state(127));
                call=new CdSpikes.SolidCall((CdObjectPool.Snapshot)horizontalReplacePartition(call.callCut(),List.class,images),call.expectedPlayer(),call.expectedGlobals(),call.result());}
            if(scenario==7){var p=other.runtime.placement().pool();p.write(spikesImage(other,p.state(40),2,0x84,0));var foreign=spikesSolidCall(other);call=new CdSpikes.SolidCall(call.callCut(),call.expectedPlayer(),call.expectedGlobals(),foreign.result());}
            spikesStage(f,40);try{spikesReject(f,"commitSpikesSolid",CdSpikes.SolidCall.class,call);}finally{boulderUnstage(f);}
        }
    }

    @Test void spikesYPrefixIsOneFullLongStoreAndSurvivesRejectedNextHurtWithoutPoolContactOrFm() throws Exception {
        try(var f=new Fixture(INITIAL,new CdPlayerBody.ContactGlobals(0,3,0xA5),SCORE)) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,0,0x123,0x01001234,0x0767FF80,0);var pool=f.runtime.placement().pool();pool.write(spikesImage(f,pool.state(40),2,0x84,8));
            var call=spikesReachedHurt(f);assertEquals(0x0764DC80,call.expectedActor().motion().centreY16_16());var actor=f.frame.state();
            var images=pool.capture().slots();var contact=f.runtime.contactGlobals();var sounds=f.runtime.lastSoundRequests();
            pool.write(pool.state(127).moveWord(TUNNEL_DOOR_TIMER,0xA55A));spikesReject(f,"commitSpikesHurt",CdSpikes.HurtCall.class,call);
            assertEquals(actor,f.frame.state());assertEquals(contact,f.runtime.contactGlobals());assertEquals(sounds,f.runtime.lastSoundRequests());assertEquals(images.get(8),f.slot(40));
            boulderUnstage(f);
        }
    }

    @Test void spikesYPrefixValidatesCurrentActorFullWordHurtAndLiveGatesWithoutAPostSolidDistanceGate() throws Exception {
        for(int scenario=0;scenario<8;scenario++)try(var f=new Fixture()) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,0,0x123,0x01001234,0x0700FF80,0);var pool=f.runtime.placement().pool();pool.write(spikesImage(f,pool.state(40),2,0x84,8));
            var actor=tunnelDoorPlayer(f).actor();var call=new CdSpikes.YRewindCall(f.slot(40).identity(),actor,actor.motion().centreY16_16()-(((short)actor.motion().yVelocity())<<8));spikesStage(f,40);
            if(scenario==0){spikesInvoke(f,"commitSpikesYRewind",CdSpikes.YRewindCall.class,call);assertEquals(call.finalYBits(),f.frame.state().motion().centreY16_16());}
            else {
                if(scenario==1)pool.write(f.slot(40).moveByte(SPRITE_FLAGS,0x86));if(scenario==2)pool.write(f.slot(40).moveByte(FLAGS,0));
                if(scenario==3)boulderSet(f.runtime,"contactGlobals",new CdPlayerBody.ContactGlobals(1,0,0));if(scenario==4)seedTunnelDoorGlobals(f,1,0,0,0);
                if(scenario==5)spikesSeedPlayer(f,8,40,0,0x123,0x01001234,0x0700FF80,0x100);
                if(scenario==6)spikesSeedPlayer(f,8,40,0,0x123,0x01001235,0x0700FF80,0);
                if(scenario==7)call=new CdSpikes.YRewindCall(call.sourceIdentity(),call.expectedActor(),call.finalYBits()+1);
                spikesReject(f,"commitSpikesYRewind",CdSpikes.YRewindCall.class,call);
            }
            boulderUnstage(f);
        }
    }

    @Test void spikesHurtEmptyChangedCutStillCasChecksSourceWorldContactFrameAndEveryPoolByte() throws Exception {
        for(int scenario=0;scenario<10;scenario++)try(var f=new Fixture(INITIAL,new CdPlayerBody.ContactGlobals(0,3,0xA5),SCORE);var other=new Fixture()) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,0,0x123,0x01001234,0x0767FF80,0);var pool=f.runtime.placement().pool();pool.write(spikesImage(f,pool.state(40),2,0x84,8));
            var call=spikesReachedHurt(f);assertTrue(call.result().changedSlots().isEmpty());
            if(scenario==0)pool.write(pool.state(127).moveWord(TUNNEL_DOOR_TIMER,0xA55A));if(scenario==1)pool.write(f.slot(40).moveWord(X_FRACTION,0xA55A));
            if(scenario==2)boulderSet(f.runtime,"ringGlobals",new CdRingGlobals(1,3,0,0,0,0,0,0));
            if(scenario==3)boulderSet(f.runtime,"scoreGlobals",new CdOpeningObjectRuntime.ScoreGlobals(1,5000,0));
            if(scenario==4)boulderSet(f.runtime,"contactGlobals",new CdPlayerBody.ContactGlobals(0,2,0xA5));
            if(scenario==5)spikesDebug(f,1,0);if(scenario==6)seedTunnelDoorGlobals(f,0,0xA55A,0,0);
            if(scenario==7)spikesSeedPlayer(f,8,40,0,0x123,0x01001235,0x0764DC80,0);
            if(scenario==8||scenario==9){var images=new ArrayList<>(call.callCut().slots());if(scenario==8)images.removeLast();else images.set(95,other.runtime.placement().pool().state(127));
                var cut=(CdObjectPool.Snapshot)horizontalReplacePartition(call.callCut(),List.class,images);call=new CdSpikes.HurtCall(cut,call.sourceA2(),call.expectedActor(),call.expectedPlayer(),call.expectedWorld(),call.result());}
            try{spikesReject(f,"commitSpikesHurt",CdSpikes.HurtCall.class,call);}finally{boulderUnstage(f);}
        }
    }

    @Test void spikesFirstFreeDirtyEarlierAllocationPreservesAllOther59BytesAndDoesNotDispatchItTwice() throws Exception {
        try(var f=new Fixture(new CdRingGlobals(3,3,0,0,0,0,0,0))) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,0,0x123,0x01001234,0x0767FF80,0);var pool=f.runtime.placement().pool();pool.write(spikesImage(f,pool.state(40),2,0x84,8));
            var dirty=pool.state(32);for(var field:CdObjectState.ByteField.values())if(field!=ID)dirty=dirty.moveByte(field,0xA5);
            dirty=dirty.moveByte(ID,0).moveWord(X_FRACTION,0xBEEF).moveWord(Y_FRACTION,0xCAFE);pool.write(dirty);
            var call=spikesReachedHurt(f);assertEquals(List.of(32),call.result().changedSlots().stream().map(v->v.identity().wholeIndex()).toList());
            var expected=dirty.moveByte(ID,0x11).moveWord(X,call.expectedActor().motion().centreX16_16()>>>16).moveWord(Y,call.expectedActor().motion().centreY16_16()>>>16);
            assertEquals(expected,call.result().changedSlots().getFirst());spikesInvoke(f,"commitSpikesHurt",CdSpikes.HurtCall.class,call);assertEquals(expected,f.slot(32));
            assertEquals(3,f.runtime.ringGlobals().rings());assertEquals(0xBEEF,f.slot(32).word(X_FRACTION));assertEquals(0xCAFE,f.slot(32).word(Y_FRACTION));boulderUnstage(f);
        }
    }

    @Test void spikesPublicEarlierLostRingWaitsWhileLaterPhysicalLostRingSeesTheAllocationThisPass() throws Exception {
        for(boolean earlier:List.of(true,false))try(var f=new Fixture(new CdRingGlobals(3,3,0,0,0,0,0,0))) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,0,0x123,0x01001234,0x0767FF80,0);var pool=f.runtime.placement().pool();
            if(!earlier)for(int whole=32;whole<40;whole++)pool.write(pool.state(whole).moveByte(ID,0x24).moveWord(X,0x100).moveWord(Y,0x780));
            pool.write(spikesImage(f,pool.state(40),2,0x84,8));f.runtime.runDynamicSlotsAfterReservedSlots();
            if(earlier){assertEquals(0x11,f.slot(32).id());assertEquals(0,f.slot(32).routine());assertEquals(3,f.runtime.ringGlobals().rings());}
            else {assertEquals(0x11,f.slot(41).id());assertEquals(2,f.slot(41).routine());assertEquals(0,f.runtime.ringGlobals().rings());assertNotEquals(new CdRingSpillGlobals(0,0,0),f.runtime.spillGlobals());}
            monitorFinish(f);
        }
    }

    @Test void spikesFullPoolActualFindObjSlotMissNeverFreesTheSourceOrWritesRingLivesOrScore() throws Exception {
        try(var f=new Fixture(new CdRingGlobals(3,3,0,0,0,0,0,0))) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,0,0x123,0x01001234,0x0767FF80,0);var pool=f.runtime.placement().pool();
            for(int whole=32;whole<128;whole++)pool.write(pool.state(whole).moveByte(ID,0x24));pool.write(spikesImage(f,pool.state(40),2,0x84,8));
            var call=spikesReachedHurt(f);assertEquals(List.of(new CdPlayerTouchResponse.FindObjSlot(java.util.OptionalInt.empty()),new CdPlayerTouchResponse.ResetOnFloor()),call.result().calls());
            assertTrue(call.result().changedSlots().isEmpty());var world=spikesWorld(f);spikesInvoke(f,"commitSpikesHurt",CdSpikes.HurtCall.class,call);
            assertEquals(world,spikesWorld(f));assertEquals(0x26,f.slot(40).id());boulderUnstage(f);
        }
    }


    @Test void spikesHurtPreflightsActualForeignLaterDirtyAndFrameInvalidResultsBeforeAnyChildStore() throws Exception {
        for(int scenario=0;scenario<4;scenario++)try(var f=new Fixture(new CdRingGlobals(3,3,0,0,0,0,0,0));var other=new Fixture()) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,0,0x123,0x01001234,0x0767FF80,0);var pool=f.runtime.placement().pool();pool.write(spikesImage(f,pool.state(40),2,0x84,8));
            var call=spikesReachedHurt(f);var result=call.result();var actor=f.frame.captureActor();
            if(scenario==0) {
                var foreign=other.runtime.placement().pool();foreign.write(spikesImage(other,foreign.state(40),2,0x84,8));
                result=CdPlayerTouchResponse.hurtPlayer(call.expectedActor(),call.expectedPlayer(),call.expectedWorld(),foreign.state(40),foreign.capture());
            } else {
                if(scenario==1)pool.write(pool.state(32).moveByte(ID,0x24));
                if(scenario==2)pool.write(pool.state(32).moveWord(Y_FRACTION,0xA55A));
                if(scenario==3)spikesSeedPlayer(f,8,40,0,0x123,0x01001235,call.expectedActor().motion().centreY16_16(),0);
                result=CdPlayerTouchResponse.hurtPlayer(tunnelDoorPlayer(f).actor(),call.expectedPlayer(),call.expectedWorld(),f.slot(40),pool.capture());
                pool.restore(call.callCut());f.frame.restoreActor(actor);
            }
            var bad=new CdSpikes.HurtCall(call.callCut(),call.sourceA2(),call.expectedActor(),call.expectedPlayer(),call.expectedWorld(),result);
            try{spikesReject(f,"commitSpikesHurt",CdSpikes.HurtCall.class,bad);}finally{boulderUnstage(f);}
        }
    }

    @Test void spikesYAndHurtChangedImagesArePlainValuesWithNoPersistentTokenOrMetadataReplay() throws Exception {
        try(var f=new Fixture(INITIAL,new CdPlayerBody.ContactGlobals(0,3,0xA5),SCORE)) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,0,0x123,0x01001234,0x0767FF80,0);f.runtime.placement().pool().write(spikesImage(f,f.slot(40),2,0x84,8));
            var call=spikesReachedHurt(f);var reconstructed=new CdSpikes.HurtCall(call.callCut(),call.sourceA2(),call.expectedActor(),call.expectedPlayer(),call.expectedWorld(),call.result());
            spikesInvoke(f,"commitSpikesHurt",CdSpikes.HurtCall.class,reconstructed);spikesReject(f,"commitSpikesHurt",CdSpikes.HurtCall.class,call);boulderUnstage(f);
        }
    }

    @Test void spikesPreviousSatUsesNowCurrentSpikesAfterPlaneCallbackAndSlowServiceInThatOrder() throws Exception {
        try(var f=new Fixture()) {
            cameraArtIdle(f);cameraArtBegin(f,0);f.runtime.spawnBeforeReservedSlots();f.body(2);var pool=f.runtime.placement().pool();pool.clearDynamicSlots();
            pool.write(spikesImage(f,pool.state(40),2,0x84,0));f.runtime.runDynamicSlotsAfterReservedSlots();var sat=f.runtime.finishAfterCamera(POST);f.runtime.finishOrdinaryTail();
            assertEquals(4,sat.provenance().stream().filter(v->v.slotRef().wholeIndex()==40).count());var original=f.runtime.sectionArtState();
            var changed=spikesChanged(original.residency());var row=f.assets.openingObjects().sectionArtSources().source(com.openggf.game.soniccd.level.CdR11aAsset.ART_SPIKES).canonicalPlc();
            var queue=com.openggf.game.soniccd.resources.CdR11aPlcBuffer.append(com.openggf.game.soniccd.resources.CdR11aPlcBuffer.cleared(),
                    List.of(new com.openggf.game.soniccd.resources.CdR11aPlcBuffer.Entry(row.sourceRange().nativeAddress(),row.vramAddress())));
            var prepared=diagonalSectionCopy(original,changed,queue);var field=CdOpeningObjectRuntime.class.getDeclaredField("sectionArt");field.setAccessible(true);
            prepared=((com.openggf.game.soniccd.resources.CdR11aSectionArt)field.get(f.runtime)).prepare(prepared);
            f.runtime.restore((CdOpeningObjectRuntime.Snapshot)horizontalReplacePartition(f.runtime.captureClosedCut(),com.openggf.game.soniccd.resources.CdR11aSectionArt.State.class,prepared));
            var oldPicture=f.runtime.displayedFrame();var oldVersions=oldPicture.patternVersions();var before=f.runtime.sectionArtState();var display=CdOpeningObjectRuntime.class.getDeclaredField("displayed");display.setAccessible(true);
            var selection=f.frame.prepareInput(0x80);f.runtime.serviceVBlank(selection.phase(),()->{
                assertSame(before,f.runtime.sectionArtState());try{assertSame(oldPicture,display.get(f.runtime));}catch(IllegalAccessException failure){throw new AssertionError(failure);}
            },0);
            assertEquals(5,f.runtime.sectionArtState().remainingTilesWord());assertArrayEquals(sat.bytes(),f.runtime.cpuSat().bytes());
            var source=cameraArtSource(f,com.openggf.game.soniccd.level.CdR11aAsset.ART_SPIKES);var pixels=spikesPixels(f,f.runtime.sectionArtState().residency());
            for(int tile=0;tile<8;tile++)assertEquals(CdObjectPresentation.patternVersion(tile<3?source.slice(tile*32,32):
                    com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(changed,0x320+tile)),pixels.resolve(0x320+tile).version());
            assertEquals(CdObjectPresentation.convert(sat,pixels),f.runtime.displayedFrame());assertEquals(oldVersions,oldPicture.patternVersions());
        }
    }

    @Test void spikesQueuedPartialAndFullArtRestoreNonzeroHurtContactSoundAndEveryExistingPartitionOnce() throws Exception {
        for(int services:List.of(0,1,3))try(var f=new Fixture(INITIAL,new CdPlayerBody.ContactGlobals(0,3,0xA5),SCORE)) {
            springDeviceCut(f);spikesSeedPlayer(f,0x0C,40,7,0x123,0x01001234,0x0767FF80,0);var pool=f.runtime.placement().pool();pool.write(spikesImage(f,pool.state(40),2,0x84,8));
            f.runtime.runDynamicSlotsAfterReservedSlots();f.runtime.finishAfterCamera(POST);f.runtime.finishOrdinaryTail();assertEquals(0x78,f.frame.state().timers().hurt());
            var original=f.runtime.sectionArtState();var source=f.assets.openingObjects().sectionArtSources().source(com.openggf.game.soniccd.level.CdR11aAsset.ART_SPIKES);
            var queue=com.openggf.game.soniccd.resources.CdR11aPlcBuffer.append(com.openggf.game.soniccd.resources.CdR11aPlcBuffer.cleared(),List.of(
                    new com.openggf.game.soniccd.resources.CdR11aPlcBuffer.Entry(source.canonicalPlc().sourceRange().nativeAddress(),0x6400)));
            var state=diagonalSectionCopy(original,spikesChanged(original.residency()),queue);var field=CdOpeningObjectRuntime.class.getDeclaredField("sectionArt");field.setAccessible(true);
            var owner=(com.openggf.game.soniccd.resources.CdR11aSectionArt)field.get(f.runtime);if(services!=0)state=owner.prepare(state);
            f.runtime.restore((CdOpeningObjectRuntime.Snapshot)horizontalReplacePartition(f.runtime.captureClosedCut(),com.openggf.game.soniccd.resources.CdR11aSectionArt.State.class,state));
            for(int service=0;service<services;service++){var selected=f.frame.prepareInput(service==0?0x80:0);f.runtime.serviceVBlank(selected.phase(),()->{},0);}
            assertEquals(services==1?5:0,f.runtime.sectionArtState().remainingTilesWord());
            if(services==0){assertNotEquals(0,f.runtime.sectionArtState().queue().headSource());assertTrue(f.runtime.sectionArtState().decoder().isEmpty());}
            var cut=f.runtime.captureClosedCut();var actor=f.frame.captureActor();var shared=f.frame.captureShared();var cpu=f.runtime.cpuSat();var picture=f.runtime.displayedFrame();var sounds=f.runtime.lastSoundRequests();
            assertEquals(0x78,actor.state().timers().hurt());assertEquals(2,cut.contactGlobals().shield());assertEquals(0xA5,cut.contactGlobals().combineRing());assertFalse(sounds.isEmpty());
            int held=f.frame.paused()?0:0x80;var selection=f.frame.prepareInput(held);f.runtime.serviceVBlank(selection.phase(),()->{},0);var next=f.runtime.captureClosedCut();var nextShared=f.frame.captureShared();
            f.frame.validateActorRestore(actor);f.frame.validateSharedRestore(shared);f.runtime.validateRestore(cut);f.frame.restoreActor(actor);f.frame.restoreShared(shared);f.runtime.restore(cut);
            assertSame(cpu,f.runtime.cpuSat());assertSame(picture,f.runtime.displayedFrame());assertSame(sounds,f.runtime.lastSoundRequests());assertEquals(actor,f.frame.captureActor());assertEquals(shared.environment(),f.frame.environment());
            assertEquals(cut.placement().pool().slots(),pool.capture().slots());assertEquals(cut.contactGlobals(),f.runtime.contactGlobals());assertEquals(cut.ringGlobals(),f.runtime.ringGlobals());assertEquals(cut.scoreGlobals(),f.runtime.scoreGlobals());
            assertEquals(cut.spillGlobals(),f.runtime.spillGlobals());assertEquals(cut.levelGlobals(),f.runtime.levelGlobals());assertEquals(cut.devGlobals(),f.runtime.devGlobals());assertEquals(cut.powerupArtState(),f.runtime.powerupArtState());
            var again=f.frame.prepareInput(held);f.runtime.serviceVBlank(again.phase(),()->{},0);assertEquals(next.sectionArtState(),f.runtime.sectionArtState());assertEquals(next.displayedFrame(),f.runtime.displayedFrame());
            assertEquals(nextShared.vIntClock(),f.frame.vIntClock());assertEquals(next.lastSoundRequests(),f.runtime.lastSoundRequests());
        }
    }


    @Test void spikesContactTimeWarpIsIndependentOfTheUnownedFrameTimeWarpTimer() throws Exception {
        try(var f=new Fixture(INITIAL,new CdPlayerBody.ContactGlobals(0,3,0xA5),SCORE)) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,0,0x123,0x01001234,0x0767FF80,0);
            var shared=f.frame.captureShared();var e=shared.environment();var b=e.globals();
            f.frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(),new CdPlayerBody.Environment(e.movement(),e.controls(),
                    new CdPlayerBody.Globals(b.levelFrames(),b.cameraXCentre(),b.timeAttackMode(),b.debugMode(),b.debugCheat(),0xA5,b.invincible(),b.bossMusic()),e.dynamicSlots()),
                    shared.artState(),shared.paused(),shared.vIntClock()));
            f.runtime.placement().pool().write(spikesImage(f,f.slot(40),2,0x84,8));f.runtime.runDynamicSlotsAfterReservedSlots();
            assertEquals(4,f.frame.state().motion().routine());assertEquals(0x78,f.frame.state().timers().hurt());
            assertEquals(0xA5,f.frame.environment().globals().timeWarpTimer());assertEquals(0,f.runtime.contactGlobals().timeWarp());monitorFinish(f);
        }
    }

    @Test void spikesActualMainHonoursCurrentTimeWarpInvincibilityFullHurtAndVerticalFlipOnlyAfterSolid() throws Exception {
        int[][] rows={{1,0,0,0},{0,1,0,0},{0,0,0x100,0},{0,0,0,2}};
        for(var row:rows)try(var f=new Fixture(INITIAL,new CdPlayerBody.ContactGlobals(row[0],3,0xA5),SCORE)) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,0,0x123,0x01001234,0x0767FF80,row[2]);seedTunnelDoorGlobals(f,row[1],0x3456,0x55,0x66);
            var pool=f.runtime.placement().pool();pool.write(spikesImage(f,pool.state(40),2,0x84|row[3],8));var call=spikesSolidCall(f);
            var state=f.frame.state();var env=f.frame.environment();var contact=f.runtime.contactGlobals();
            f.runtime.runDynamicSlotsAfterReservedSlots();assertTunnelDoorSolidApplied(f,state,env,call.result());
            assertEquals(contact,f.runtime.contactGlobals());assertEquals(row[2],f.frame.state().timers().hurt());assertEquals(0x3456,f.frame.environment().movement().globals().scoreChain());monitorFinish(f);
        }
    }

    @Test void spikesSolidCutRejectsOutsideNativeAbsDyButYAndHurtDoNotRepeatThatOuterTest() throws Exception {
        try(var f=new Fixture()) {
            springDeviceCut(f);spikesSeedPlayer(f,8,40,1,0x123,0x01001234,0x0700FF80,0);f.runtime.placement().pool().write(spikesImage(f,f.slot(40),2,4,8));
            var call=spikesSolidCall(f);spikesStage(f,40);spikesReject(f,"commitSpikesSolid",CdSpikes.SolidCall.class,call);boulderUnstage(f);
        }
    }

    private static CdOpeningObjectRuntime.LogicalBanks spikesBanks(int[] b) {
        return new CdOpeningObjectRuntime.LogicalBanks(b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14],b[15]);
    }
    private static CdObjectState spikesImage(Fixture f,CdObjectState image,int routine,int sprite,int flags) throws java.io.IOException {
        var assets=CdSpikes.BodyAssets.fromObjectAssets(f.assets.openingObjects());
        return image.moveByte(ID,0x26).moveByte(ROUTINE,routine).moveByte(SPRITE_FLAGS,sprite).moveByte(FLAGS,flags)
                .moveLong(MAPPINGS,assets.mappingsAddress()).moveWord(TILE,0x320).moveByte(WIDTH,16).moveByte(Y_RADIUS,8).moveByte(PRIORITY,3)
                .moveWord(X,0x100).moveWord(Y,0x780);
    }
    private static void spikesSeedPlayer(Fixture f,int flags,int stand,int charge,int yv,int x,int y,int hurt) {
        seedSolidPlayer(f,flags,stand,charge,0x234,yv,x,y);var actor=f.frame.captureActor();var s=actor.state();
        f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(),new CdPlayerBody.State(s.motion(),s.animation(),s.header(),
                new CdPlayerBody.Timers(hurt,s.timers().invincibility(),s.timers().speedShoes(),s.timers().reset()),s.history(),s.rotationDistance(),s.objectLinks()),actor.display()));
    }
    private static void spikesDebug(Fixture f,int debug,int cheat) {
        var shared=f.frame.captureShared();var e=shared.environment();var b=e.globals();
        f.frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(),new CdPlayerBody.Environment(e.movement(),e.controls(),
                new CdPlayerBody.Globals(b.levelFrames(),b.cameraXCentre(),b.timeAttackMode(),debug,cheat,b.timeWarpTimer(),b.invincible(),b.bossMusic()),e.dynamicSlots()),
                shared.artState(),shared.paused(),shared.vIntClock()));
    }
    private static CdPlayerTouchResponse.PlayerGlobals spikesPlayerGlobals(Fixture f) {
        var e=f.frame.environment();var b=e.globals();var m=e.movement();var c=f.runtime.contactGlobals();
        return new CdPlayerTouchResponse.PlayerGlobals(c.timeWarp(),b.invincible(),c.shield(),c.combineRing(),b.debugCheat(),b.debugMode(),m.globals().scoreChain(),m.gates().miniSonic());
    }
    private static CdPlayerTouchResponse.WorldGlobals spikesWorld(Fixture f) {
        var r=f.runtime.ringGlobals();var s=f.runtime.scoreGlobals();return new CdPlayerTouchResponse.WorldGlobals(r.rings(),r.lives(),r.updateHudLives(),s.score(),s.nextLifeScore(),s.updateHudScore());
    }
    private static CdSpikes.SolidCall spikesSolidCall(Fixture f) {
        var pool=f.runtime.placement().pool();var cut=pool.capture();var player=tunnelDoorPlayer(f);var globals=tunnelDoorGlobals(f);var e=f.frame.environment();
        var result=CdSolidObject.fromAssets(f.assets).run(CdSolidObject.Entry.SOLID_OBJECT,pool.state(40),player,
                new CdSolidObject.Gates(e.globals().debugMode(),e.movement().gates().miniSonic()),globals,cut);return new CdSpikes.SolidCall(cut,player,globals,result);
    }
    private static void spikesStage(Fixture f,int whole) throws Exception {
        boulderSet(f.runtime,"phase",CdOpeningObjectRuntime.Phase.DYNAMIC_SLOTS);boulderSet(f.runtime,"operating",true);boulderSet(f.runtime,"dispatchWholeIndex",whole);
    }
    private static void spikesInvoke(Fixture f,String name,Class<?> type,Object call) {
        try {var method=CdOpeningObjectRuntime.class.getDeclaredMethod(name,type);method.setAccessible(true);method.invoke(f.runtime,call);}
        catch(java.lang.reflect.InvocationTargetException fault){if(fault.getCause() instanceof RuntimeException failure)throw failure;if(fault.getCause() instanceof Error failure)throw failure;throw new AssertionError(fault.getCause());}
        catch(ReflectiveOperationException fault){throw new AssertionError(fault);}
    }
    private static CdSpikes.HurtCall spikesReachedHurt(Fixture f) throws Exception {
        spikesStage(f,40);var call=new CdSpikes.HurtCall[1];var stop=new IllegalStateException("actual source stopped before direct Hurt publication");
        var body=new CdSpikes(f.runtime.placement(),CdSpikes.BodyAssets.fromObjectAssets(f.assets.openingObjects()),CdSolidObject.fromAssets(f.assets),
                value->spikesInvoke(f,"commitSpikesSolid",CdSpikes.SolidCall.class,value),value->spikesInvoke(f,"commitSpikesYRewind",CdSpikes.YRewindCall.class,value),
                value->{call[0]=value;throw stop;},(p,s)->fail("Hurt stop precedes DrawObject"));var e=f.frame.environment();
        var fault=assertThrows(IllegalStateException.class,()->body.run(40,new CdSpikes.Globals(e.movement().gates().timeZone(),e.movement().gates().timeWarpDirection(),PRE.xWord()),
                new CdSpikes.Inputs(tunnelDoorPlayer(f),new CdSolidObject.Gates(e.globals().debugMode(),e.movement().gates().miniSonic()),tunnelDoorGlobals(f),spikesPlayerGlobals(f),spikesWorld(f))));
        assertSame(stop,fault);assertNotNull(call[0]);return call[0];
    }
    private static List<CdOpeningObjectRuntime.SoundRequest> spikesHurtSounds(Fixture f,CdPlayerTouchResponse.Result result) {
        var sounds=new ArrayList<>(f.runtime.lastSoundRequests());for(var call:result.calls())if(call instanceof CdPlayerTouchResponse.PlayFm fm)
            sounds.add(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(fm.sound())));return List.copyOf(sounds);
    }
    private static void spikesReject(Fixture f,String name,Class<?> type,Object call) throws Exception {
        var contact=f.runtime.contactGlobals();var ring=f.runtime.ringGlobals();var score=f.runtime.scoreGlobals();var spill=f.runtime.spillGlobals();var level=f.runtime.levelGlobals();
        diagonalReject(f,name,type,call);assertEquals(contact,f.runtime.contactGlobals());assertEquals(ring,f.runtime.ringGlobals());assertEquals(score,f.runtime.scoreGlobals());
        assertEquals(spill,f.runtime.spillGlobals());assertEquals(level,f.runtime.levelGlobals());
    }
    private static CdObjectPresentation.PatternSource spikesPixels(Fixture f,com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State resident) throws java.io.IOException {
        var o=f.assets.openingObjects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.frame.artState(),f.runtime.powerupArtState(),o,
                o.frontierArtBank(),o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),resident,
                BANKS.player(),BANKS.ring(),BANKS.mosqui(),BANKS.plant(),BANKS.ramp(),BANKS.explosion(),BANKS.points(),BANKS.flower(),BANKS.powerup(),
                BANKS.floatBlock(),BANKS.spring(),BANKS.tunnelDoorVertical(),BANKS.monitorTime(),BANKS.boulder(),BANKS.diagonalSpring(),BANKS.spikes());
    }
    private static com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State spikesChanged(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State original) {
        var state=original;for(int tile=0;tile<8;tile++){byte[] bytes=new byte[32];java.util.Arrays.fill(bytes,(byte)(0xA1+tile));
            state=com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.writeTile(state,0x6400+tile*32,CdAssetBytes.copyOf(bytes));}return state;
    }
    // END common Spikes Runtime R1 tests-first additions.
}
