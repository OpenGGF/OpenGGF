package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-port cuts over the actual authenticated USA B aggregate and existing Frame.
 * Literal oracles derive from pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Main405-418, Past Scroll183-331, Events86-123 and shared Scroll Flag Set15-60/166-224.
 * Root owns Main's ordered demo/restart/debug reads, same World/generation/viewport,
 * once-only post-DYNAMIC Scope, actual timeStopTimer and later DrawObjects/tail services.
 * These tests execute no A actor or engine camera seam, and claim no native full-course proof.
 * Every Java oracle in this packet is UNRUN by its author; Root owns compiler RED and execution.
 */
class TestSonicCdPastCameraContinuation {
    private static CdR11aAssets present;
    private static CdR11bAssets past;
    private static CdR11bAssets foreignPast;

    @BeforeAll
    static void loadActualOriginalUsaProducts() throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        assertNotNull(configured, "These retail source-port checks require soniccd.media.root");
        assertFalse(configured.isBlank(), "Configured original media root must not be blank");
        Path cue;
        try (var paths = Files.list(Path.of(configured).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "One actual original USA CUE is required");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            present = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStagePayload.R11A.stage());
            past = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            foreignPast = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
    }

    @Test
    void ordinaryContinuationReadsCompletedPrimaryAndUpdatesTheExistingFgBgAndFeedbackOnly() throws Exception {
        var f = frame(past, 0x80, 0, motion(0x04000123, 0x0200ABCD, 2, 0x42, 6));
        var p = ready(f, foreground(0), background());
        // A real same-owner actor restore supplies the completed object result AFTER binding/load.
        // Caching the earlier actor ($400) instead of reading the actual $500 gives the wrong X delta.
        replaceMotion(f, motion(0x05000123, 0x0200ABCD, 2, 0x42, 6));
        var actor = f.captureActor(); var shared = f.captureShared(); var before = retained(p);
        var cameras = p.continuePastCameraAfterObjects(f, 0xD5);
        assertEquals(new CdR11aForegroundScroll.State(0x350, 0x01800000, 0, 0x10,
                0x1000, 0xFFAA, 0x0800, 0x310, 0x12, 0x019A, 0), p.pastForegroundImage());
        var b = p.pastBackgroundImage();
        assertEquals(0x012B2345, b.cameraBgX()); assertEquals(0x00453AA7, b.cameraBgY());
        assertEquals(0x006A6789, b.cameraBg2X()); assertEquals(0x004589AB, b.cameraBg2Y());
        assertEquals(0x00ADABCD, b.cameraBg3X()); assertEquals(0x0045CDEF, b.cameraBg3Y());
        assertEquals(0xA100, b.scrollFlagsBg()); assertEquals(0, b.scrollFlagsBg2()); assertEquals(0, b.scrollFlagsBg3());
        assertArrayEquals(new byte[]{0x10, 0x10, 0x10, 0x7F, 0x10, (byte) 0xA6, (byte) 0xB7, (byte) 0xC8}, b.crossingRam().copy());
        assertSame(((CdR11aBackgroundScroll.State) before.bg()).cloudRam(), b.cloudRam());
        assertEquals(0x01800045, b.vscroll());
        assertArrayEquals(expectedDeformation(), b.deformationRam().copy());
        assertArrayEquals(expectedHscroll(), b.hscrollRam().copy());
        assertEquals(cameras(0x350, 0x180, 0x12B, 0x45, 0xAD, 0x45), cameras);
        assertFrameFeedbackOnly(f, actor, shared, 0x304, 0x60);
        assertPresentationNonwrites(p, before);
    }

    @Test
    void highByteLockReturnsBeforeAllFourFlagClearsEventsAndBgDeformation() throws Exception {
        var f = frame(past, 0x81, 0, motion(0x1C200123, 0x0200ABCD, 2, 0x42, 6));
        var p = ready(f, foreground(0x0101), background());
        var before = retained(p); var actor = f.captureActor(); var shared = f.captureShared();
        var cameras = p.continuePastCameraAfterObjects(f, 0xFF);
        assertEquals(before, retained(p));
        assertEquals(cameras(0x340, 0x180, 0x123, 0x45, 0xAB, 0xCD), cameras);
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        assertEquals(0xA55A, p.pastForegroundImage().scrollFlags());
        assertEquals(0xBEEF, p.pastBackgroundImage().scrollFlagsBg());
        assertEquals(0x4567, p.pastBackgroundImage().scrollFlagsBg2());
        assertEquals(0x89AB, p.pastBackgroundImage().scrollFlagsBg3());
    }

    @Test
    void lowByteLockDoesNotLockAndEveryWholeRawPastPeriodTakesPpz2NotPresentEvents() throws Exception {
        for (int period : new int[]{0, 0x80, 0x81, 0x82}) {
            var f = frame(past, period, 0, motion(0x1C200123, 0x0200ABCD, 2, 0x42, 6));
            var seed = new CdR11aForegroundScroll.State(0x838, 0x018055AA, 0x10, 0x10,
                    0x1234, 0x5678, 0xA55A, 0x345, 0x12, 0x789A, 1);
            var p = ready(f, seed, background()); var actor = f.captureActor(); var shared = f.captureShared();
            p.continuePastCameraAfterObjects(f, 0xFF);
            assertEquals(new CdR11aForegroundScroll.State(0x848, 0x01800000, 0x10, 0x10,
                    0x1000, 0xFFAA, 0, 0x310, 0x12, 0x019A, 1), p.pastForegroundImage());
            assertEquals(period, f.environment().movement().gates().timeZone());
            // A false $81 -> $01 normalization would select the Present ramp's $88 centre.
            assertFrameFeedbackOnly(f, actor, shared, 0x304, 0x60);
        }
    }

    @Test
    void actualUsaSourceRunsGivingUpEventBeforeYAndDoesNotBorrowAnotherRegion() throws Exception {
        var f = frame(past, 0, 0, motion(0x05000123, 0x0200ABCD, 2, 0, 0x2B));
        var p = ready(f, foreground(0), background()); var actor = f.captureActor(); var shared = f.captureShared();
        assertEquals(CdReleaseVariant.USA, f.stageSource().variant());
        p.continuePastCameraAfterObjects(f, 0);
        // USA sets bottom from the OLD $180 camera before downward Y can reach $186.
        assertEquals(new CdR11aForegroundScroll.State(0x350, 0x018055AA, 0, 0x10,
                0x1000, 0, 0x0800, 0x180, 0x12, 0x789A, 0), p.pastForegroundImage());
        assertEquals(0x00454567, p.pastBackgroundImage().cameraBgY());
        assertFrameFeedbackOnly(f, actor, shared, 0x180, 0x60);
    }

    @Test
    void deadPathChangesOnlyOrderedBottomAndDestinationWordsAndKeepsFractionsAndBg() throws Exception {
        for (int routine : new int[]{6, 8, 0xFF}) {
            var f = frame(past, 0x82, 0, motion(0x05000123, 0x0200ABCD, routine, 0x42, 6));
            var p = ready(f, foreground(0x0101), background());
            var before = retained(p); var actor = f.captureActor(); var shared = f.captureShared();
            var cameras = p.freezePastCameraAfterObjects(f);
            assertEquals(new CdR11aForegroundScroll.State(0x340, 0x018055AA, 0x10, 0x10,
                    0x1234, 0x5678, 0xA55A, 0x180, 0x12, 0x789A, 0x0101), p.pastForegroundImage());
            assertSame(before.bg(), p.pastBackgroundImage());
            assertEquals(cameras(0x340, 0x180, 0x123, 0x45, 0xAB, 0xCD), cameras);
            assertFrameFeedbackOnly(f, actor, shared, 0x180, 0x60);
            assertPresentationNonwrites(p, before);
        }
    }

    @Test
    void actualDebugWordSelectsOrdinaryScrollEvenForDeadRoutineAndWrongBranchIsRejected() throws Exception {
        var f = frame(past, 0, 0x0100, motion(0x05000123, 0x0200ABCD, 6, 0x42, 6));
        var p = ready(f, foreground(0), background()); var before = retained(p); var shared = f.captureShared();
        assertThrows(IllegalStateException.class, () -> p.freezePastCameraAfterObjects(f));
        assertEquals(before, retained(p)); assertEquals(shared, f.captureShared());
        p.continuePastCameraAfterObjects(f, 0);
        assertEquals(0x350, p.pastForegroundImage().cameraX());
        assertEquals(0x304, f.environment().movement().bounds().bottom());
        var dead = frame(past, 0, 0, motion(0x05000123, 0x0200ABCD, 6, 0x42, 6));
        var pd = ready(dead, foreground(0), background()); var bd = retained(pd); var sd = dead.captureShared();
        assertThrows(IllegalStateException.class, () -> pd.continuePastCameraAfterObjects(dead, 0));
        assertEquals(bd, retained(pd)); assertEquals(sd, dead.captureShared());
        var live = frame(past, 0, 0, motion(0x05000123, 0x0200ABCD, 4, 0x42, 6));
        var pl = ready(live, foreground(0), background()); var bl = retained(pl);
        assertThrows(IllegalStateException.class, () -> pl.freezePastCameraAfterObjects(live));
        assertEquals(bl, retained(pl));
    }

    @Test
    void unfinishedLoadAndOutOfWidthHudByteRejectBeforeAnyOwnWrite() throws Exception {
        var f = frame(past, 0, 0, motion(0x05000123, 0x0200ABCD, 2, 0x42, 6));
        var p = presentation(); p.bindPastSource(past, f, foreground(0), background(), plane(), copies());
        var bound = retained(p); var shared = f.captureShared();
        assertThrows(IllegalStateException.class, () -> p.continuePastCameraAfterObjects(f, 0));
        assertEquals(bound, retained(p)); assertEquals(shared, f.captureShared());
        p.completePastLoadScroll(f, 0); var setup = retained(p); var afterSetup = f.captureShared();
        assertThrows(IllegalStateException.class, () -> p.continuePastCameraAfterObjects(f, 0));
        assertEquals(setup, retained(p)); assertEquals(afterSetup, f.captureShared());
        p.initializePastPlaneAtLoad(); var ready = retained(p); var before = f.captureShared();
        for (int hud : new int[]{-1, 0x100}) {
            assertThrows(IllegalArgumentException.class, () -> p.continuePastCameraAfterObjects(f, hud));
            assertEquals(ready, retained(p)); assertEquals(before, f.captureShared());
        }
    }

    @Test
    void foreignPresentRetiredAndStaleActualFramesCannotPublishAnotherOwnersCamera() throws Exception {
        var f = frame(past, 0, 0, motion(0x05000123, 0x0200ABCD, 2, 0x42, 6));
        var p = ready(f, foreground(0), background()); var before = retained(p); var shared = f.captureShared();
        var sameProducts = frame(past, 0, 0, motion(0x05000123, 0x0200ABCD, 2, 0x42, 6));
        var foreign = frame(foreignPast, 0, 0, motion(0x05000123, 0x0200ABCD, 2, 0x42, 6));
        var a = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(present);
        for (var other : new CdPlayerFrameRuntime[]{sameProducts, foreign, a}) {
            assertThrows(IllegalArgumentException.class, () -> p.continuePastCameraAfterObjects(other, 0));
            assertThrows(IllegalArgumentException.class, () -> p.freezePastCameraAfterObjects(other));
            assertEquals(before, retained(p)); assertEquals(shared, f.captureShared());
        }
        f.close();
        assertThrows(IllegalStateException.class, () -> p.continuePastCameraAfterObjects(f, 0));
        assertEquals(before, retained(p));
        p.bindPastSource(past, sameProducts, foreground(0), background(), plane(), copies());
        p.completePastLoadScroll(sameProducts, 0); p.initializePastPlaneAtLoad();
        var rebound = retained(p); var current = sameProducts.captureShared();
        assertThrows(IllegalArgumentException.class, () -> p.continuePastCameraAfterObjects(f, 0));
        assertThrows(IllegalArgumentException.class, () -> p.freezePastCameraAfterObjects(f));
        assertEquals(rebound, retained(p)); assertEquals(current, sameProducts.captureShared());
    }

    @Test
    void pendingAndActualReentrantPublicFrameIngressRejectWithoutPresentationMutation() throws Exception {
        var f = frame(past, 0x80, 0, motion(0x05000123, 0x0200ABCD, 2, 0x42, 6));
        var p = ready(f, foreground(0), background()); var before = retained(p);
        f.prepareInput(8);
        assertThrows(IllegalStateException.class, () -> p.continuePastCameraAfterObjects(f, 0));
        assertThrows(IllegalStateException.class, () -> p.freezePastCameraAfterObjects(f));
        assertEquals(before, retained(p)); f.clearPendingInput();
        // A genuine Frame-owned callback is the public ingress; no inOperation field/token is forged.
        f.serviceLevelLoad(8, () -> {
            assertThrows(IllegalStateException.class, () -> p.continuePastCameraAfterObjects(f, 0));
            assertThrows(IllegalStateException.class, () -> p.freezePastCameraAfterObjects(f));
        }, () -> { }, () -> { });
        assertEquals(before, retained(p)); assertFalse(f.closed());
        assertEquals(1, f.vIntClock().deltaSinceAdmittedCut());
    }

    @Test
    void existingUnsupportedWrapExposesNoInventedPartialCameraOrFrameFeedback() throws Exception {
        var f = frame(past, 0, 0, motion(0x05000123, 0xFE00ABCD, 2, 0, 6));
        var seed = new CdR11aForegroundScroll.State(0x340, 0xFF0055AA, 0x10, 0x10,
                0x1234, 0x5678, 0xA55A, 0x345, 0x12, 0x789A, 0);
        var p = ready(f, seed, background()); var before = retained(p);
        var actor = f.captureActor(); var shared = f.captureShared();
        var stopped = assertThrows(CdR11aForegroundScroll.UnsupportedScroll.class,
                () -> p.continuePastCameraAfterObjects(f, 0));
        assertEquals(CdR11aForegroundScroll.UnsupportedBoundary.TOP_WRAP_ACTOR_AND_BACKGROUND, stopped.boundary());
        assertEquals(before, retained(p)); assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        // Root must close its actual failed Visit; this package value cut does not fabricate a prefix or own its lifecycle.
    }

    private static SonicCdPlayerPresentation presentation() {
        return new SonicCdPlayerPresentation(SonicCdGameModule.openingObjectsDevelopmentPlayer());
    }

    private static SonicCdPlayerPresentation ready(CdPlayerFrameRuntime f, CdR11aForegroundScroll.State fg,
                                                  CdR11aBackgroundScroll.State bg) {
        var p = presentation(); p.bindPastSource(past, f, fg, bg, plane(), copies());
        // Use actual captured same-owner images and genuine completed-load operations, not invented phase tokens.
        var image = p.capturePastScroll(); var shared = f.captureShared();
        p.completePastLoadScroll(f, 0); p.initializePastPlaneAtLoad();
        p.restorePastScroll(image); f.restoreShared(shared);
        return p;
    }

    private static CdPlayerFrameRuntime frame(CdR11bAssets assets, int period, int debug,
                                              CdPlayerMotionState motion) {
        var state = new CdPlayerBody.State(motion, new CdPlayerBody.AnimationCursor(5, 6, 3),
                new CdPlayerBody.Header(1, 0x12345678, 0x2345, 3, 0x21),
                new CdPlayerBody.Timers(0x78, 0x1234, 0x2345, 0x3456),
                new CdPlayerBody.History(bytes(0x100, 13), 0x42), 0x67, new CdPlayerBody.ObjectLinks(0x20, 0x7F), 0x89);
        var b = assets.loadPlan().bounds();
        var environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(
                new CdPlayerMovement.Physics(0x123, 0x23, 0x34),
                new CdPlayerMovement.Bounds(0x12, b.right(), 0x300, 0x78),
                new CdPlayerMovement.Gates(0, period, 0x12, 0x34, 0x56, 0x78, 0xFF),
                new CdPlayerMovement.Globals(0x12, 0x60, 0x34, 0x56, 0x1234, 0x4567, 0x78, 0x89, 0x9A, 0xAB)),
                new CdPlayerBody.Controls(0x24, 0x18, 8, 0x10, 0x56),
                new CdPlayerBody.Globals(0x1234, 160, 0x12, debug, 0x4567, 0x2345, 0x56, 0x78),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        return CdPlayerFrameRuntime.fromPastEntry(assets, state, environment,
                new CdPlayerArtRuntime.State(bytes(0x300, 7).copy(), bytes(0x300, 11).copy(), 7, true),
                new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(), 0));
    }

    private static CdPlayerMotionState motion(int x, int y, int routine, int flags, int animation) {
        return new CdPlayerMotionState(x, y, -0x123, 0x234, -0x345, routine, 0x56, flags,
                0x23, 7, 11, animation, 7, 9, 0, -0x1234, 0x44, 0x10, 0x77, 0x88);
    }

    private static void replaceMotion(CdPlayerFrameRuntime f, CdPlayerMotionState motion) {
        var captured = f.captureActor(); var s = captured.state();
        var next = new CdPlayerBody.State(motion, s.animation(), s.header(), s.timers(), s.history(),
                s.rotationDistance(), s.objectLinks(), s.rotationAngle());
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(captured.owner(), next, captured.display()));
    }

    private static CdR11aForegroundScroll.State foreground(int lock) {
        return new CdR11aForegroundScroll.State(0x340, 0x018055AA, 0x10, 0x10, 0x1234, 0x5678,
                0xA55A, 0x345, 0x12, 0x789A, lock);
    }

    private static CdR11aBackgroundScroll.State background() {
        byte[] deformation = bytes(512, 29).copy(); Arrays.fill(deformation, 0, 16, (byte) 0);
        return new CdR11aBackgroundScroll.State(0x01232345, 0x00454567, 0x00676789,
                0x008989AB, 0x00ABABCD, 0x00CDCDEF,
                CdAssetBytes.copyOf(new byte[]{0x10, 0, 0, 0x7F, 0, (byte) 0xA6, (byte) 0xB7, (byte) 0xC8}),
                0xBEEF, 0x4567, 0x89AB, bytes(512, 23), CdAssetBytes.copyOf(deformation), bytes(1024, 31), 0x2468ACE0);
    }

    private static byte[] expectedDeformation() {
        byte[] result = bytes(512, 29).copy();
        int[] accumulators = {0x00010000, 0x0000C000, 0x00008000, 0x00004000};
        for (int i = 0; i < 4; i++) putLong(result, i * 4, accumulators[i]);
        // Independently enumerated source stores: first two words -($AD+1), next16 -$AD, final24 -$6A.
        for (int i = 16; i < 20; i += 2) putWord(result, i, 0xFF52);
        for (int i = 20; i < 52; i += 2) putWord(result, i, 0xFF53);
        for (int i = 52; i < 100; i += 2) putWord(result, i, 0xFF96);
        return result;
    }

    private static byte[] expectedHscroll() {
        byte[] result = bytes(1024, 31).copy();
        // Native $45 Y selects word32, three first lines then29 full blocks:235 lines, not224.
        for (int line = 0; line < 75; line++) putLong(result, line * 4, 0xFCB0FF53);
        for (int line = 75; line < 235; line++) putLong(result, line * 4, 0xFCB0FF96);
        return result;
    }

    private static void assertFrameFeedbackOnly(CdPlayerFrameRuntime f, CdPlayerFrameRuntime.ActorState actor,
                                                CdPlayerFrameRuntime.SharedState before, int bottom, int yCentre) {
        var e = before.environment(); var m = e.movement(); var g = m.globals(); var b = m.bounds();
        var expected = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(),
                new CdPlayerMovement.Bounds(b.left(), b.right(), bottom, b.bossFight()), m.gates(),
                new CdPlayerMovement.Globals(g.lookMode(), yCentre, g.primaryAngle(), g.secondaryAngle(),
                        g.boredTimer(), g.scoreChain(), g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance())),
                e.controls(), e.globals(), e.dynamicSlots());
        var after = f.captureShared();
        assertEquals(actor, f.captureActor()); assertEquals(expected, after.environment());
        assertSame(before.owner(), after.owner()); assertSame(before.artState(), after.artState());
        assertSame(before.vIntClock(), after.vIntClock()); assertEquals(before.paused(), after.paused());
    }

    private static void assertPresentationNonwrites(SonicCdPlayerPresentation p, Retained before) throws Exception {
        var after = retained(p);
        assertSame(before.plane(), after.plane()); assertSame(before.selectedPlane(), after.selectedPlane());
        assertSame(before.copies(), after.copies()); assertSame(before.owner(), after.owner());
        assertSame(before.source(), after.source()); assertSame(before.prepared(), after.prepared());
        assertSame(before.published(), after.published()); assertSame(before.phase(), after.phase());
        assertEquals(before.load(), after.load()); assertEquals(before.setup(), after.setup());
        for (String field : new String[]{"assets", "frame", "actor", "openingObjects", "background", "backgroundPlane"})
            assertNull(field(p, field), "The actual B source must not install or call an A owner");
    }

    private static CdObjectDrawQueue.DrawCameras cameras(int x, int y, int bx, int by, int b3x, int b3y) {
        return new CdObjectDrawQueue.DrawCameras(new CdObjectDrawQueue.CameraWords(x, y),
                new CdObjectDrawQueue.CameraWords(bx, by), new CdObjectDrawQueue.CameraWords(b3x, b3y));
    }
    private static CdR11aBackgroundPlane.State plane() {
        return new CdR11aBackgroundPlane.State(bytes(CdR11aBackgroundPlane.NAME_TABLE_BYTES, 37));
    }
    private static SonicCdPlayerPresentation.DrawCopies copies() {
        var c = new CdR11aBackgroundPlane.Camera(0x02345678, 0x03456789);
        return new SonicCdPlayerPresentation.DrawCopies(0x12345678, 0x23456789,
                new CdR11aBackgroundPlane.CopiedCameras(c, c, c), 0x3456,
                new CdR11aBackgroundPlane.FlagWords(0x4567, 0x5678, 0x6789));
    }
    private static CdAssetBytes bytes(int length, int multiplier) {
        byte[] result = new byte[length];
        for (int i = 0; i < result.length; i++) result[i] = (byte) (i * multiplier + 3);
        return CdAssetBytes.copyOf(result);
    }
    private static void putWord(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8); bytes[offset + 1] = (byte) value;
    }
    private static void putLong(byte[] bytes, int offset, int value) {
        putWord(bytes, offset, value >>> 16); putWord(bytes, offset + 2, value);
    }
    private static Object field(SonicCdPlayerPresentation p, String name) throws Exception {
        var f = SonicCdPlayerPresentation.class.getDeclaredField(name); f.setAccessible(true); return f.get(p);
    }
    private record Retained(Object fg, Object bg, Object plane, Object selectedPlane, Object copies,
                            Object owner, Object source, Object prepared, Object published, Object phase,
                            Object load, Object setup) { }
    private static Retained retained(SonicCdPlayerPresentation p) throws Exception {
        return new Retained(field(p, "scrollState"), field(p, "backgroundState"), field(p, "planeState"),
                field(p, "publishedPlane"), field(p, "drawCopies"), field(p, "scrollCaptureOwner"), field(p, "pastSource"),
                field(p, "preparedScroll"), field(p, "publishedScroll"), field(p, "heldPhase"),
                field(p, "loadScrollPending"), field(p, "checkpointSetupPending"));
    }
}
