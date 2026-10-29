package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.objects.CdTimeWarpLoadData;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import com.openggf.game.soniccd.scroll.CdR11bBackgroundPlane;
import com.openggf.game.soniccd.scroll.CdR11bBackgroundScroll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicit source-port component cuts over the configured authenticated USA B aggregate.
 * No actual B World/Module order, native current pixels, gameplay or GPU coverage is claimed.
 * DynamicSlots stays explicitly UNIMPLEMENTED: no invented inactive producer fills that gap.
 * Every executable oracle in this R1 packet is UNRUN by its author.
 */
class TestSonicCdPastLoadingPresentationPorts {
    private static CdR11aAssets present;
    private static CdR11bAssets past;
    private static CdR11bAssets foreignPast;

    @BeforeAll
    static void loadActualPreparedUsaProducts() throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        assertNotNull(configured, "These explicit retail component checks require soniccd.media.root");
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
    void bindBorrowsAllImagesAndTheActualFrameWithoutExecutingAnything() throws Exception {
        var f = frame(past, 0x80, 0);
        var a = f.captureActor(); var s = f.captureShared();
        var p = presentation();
        var fg = foreground(0); var bg = background(0); var plane = plane(); var copies = copies();
        p.bindPastSource(past, f, fg, bg, plane, copies);
        assertSame(fg, retained(p, "scrollState")); assertSame(bg, retained(p, "backgroundState"));
        assertSame(plane, retained(p, "planeState")); assertSame(copies, retained(p, "drawCopies"));
        assertSame(plane, retained(p, "publishedPlane"));
        assertNull(retained(p, "assets")); assertNull(retained(p, "frame"));
        assertNull(retained(p, "actor")); assertNull(retained(p, "openingObjects"));
        assertNull(retained(p, "background")); assertNull(retained(p, "backgroundPlane"));
        assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared());
        assertTrue((boolean) retained(p, "loadScrollPending"));
        assertTrue((boolean) retained(p, "checkpointSetupPending"));
    }

    @Test
    void allSixExplicitBindArgumentsAreRequiredBeforeAnyRetainedWrite() throws Exception {
        var f = frame(past, 0x80, 0); var fg = foreground(0); var bg = background(0);
        var plane = plane(); var copies = copies();
        for (int absent = 0; absent < 6; absent++) {
            var p = presentation(); int index = absent;
            assertThrows(NullPointerException.class, () -> p.bindPastSource(
                    index == 0 ? null : past, index == 1 ? null : f, index == 2 ? null : fg,
                    index == 3 ? null : bg, index == 4 ? null : plane, index == 5 ? null : copies));
            assertNull(retained(p, "scrollState")); assertNull(retained(p, "backgroundState"));
            assertNull(retained(p, "pastSource"));
        }
    }

    @Test
    void bindRejectsPresentForeignAndClosedFramesWithoutTouchingImages() throws Exception {
        var p = presentation();
        var a = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(present);
        var foreign = frame(foreignPast, 0x80, 0);
        var closed = frame(past, 0x80, 0); closed.close();
        assertThrows(IllegalArgumentException.class,
                () -> p.bindPastSource(past, a, foreground(0), background(0), plane(), copies()));
        assertThrows(IllegalArgumentException.class,
                () -> p.bindPastSource(past, foreign, foreground(0), background(0), plane(), copies()));
        assertThrows(IllegalStateException.class,
                () -> p.bindPastSource(past, closed, foreground(0), background(0), plane(), copies()));
        assertNull(retained(p, "pastSource")); assertNull(retained(p, "scrollState"));
    }

    @Test
    void bindRequiresNamedAdmissionAndAnActualClosedFrameCut() throws Exception {
        var f = frame(past, 0x80, 0);
        var unnamed = new SonicCdPlayerPresentation(new SonicCdGameModule());
        assertThrows(IllegalStateException.class,
                () -> unnamed.bindPastSource(past, f, foreground(0), background(0), plane(), copies()));
        f.prepareInput(8);
        var p = presentation();
        assertThrows(IllegalStateException.class,
                () -> p.bindPastSource(past, f, foreground(0), background(0), plane(), copies()));
        assertNull(retained(p, "pastSource")); assertNull(retained(p, "planeState"));
    }

    @Test
    void bindingHasFreshExistingScrollOwnershipAndDoesNotRetireTheOldFrame() throws Exception {
        var first = frame(past, 0x80, 0); var p = bound(first, 0);
        Object owner = retained(p, "scrollCaptureOwner");
        assertThrows(IllegalStateException.class,
                () -> p.bindPastSource(past, frame(past, 0x80, 0), foreground(0), background(0), plane(), copies()));
        assertFalse(first.closed());
        first.close();
        var next = frame(past, 0x80, 0);
        p.bindPastSource(past, next, foreground(0), background(0), plane(), copies());
        assertNotSame(owner, retained(p, "scrollCaptureOwner"));
        assertFalse(next.closed());
    }

    @Test
    void allNineWordReadsObserveTheirActualRetainedOwners() throws Exception {
        var f = frame(past, 0x80, 0); var p = bound(f, 0);
        var a = f.captureActor(); var s = f.captureShared();
        int[] expected = {0x345, 0x340, 0x180, 0x123, 0x45, 0x67, 0x89, 0xAB, 0xCD};
        for (int i = 0; i < stores().size(); i++)
            assertEquals(expected[i], p.readPastLoadWord(stores().get(i).target()));
        assertEquals(a, f.captureActor()); assertEquals(s, f.captureShared());
    }

    @Test
    void eachAddressedWordPreservesFractionsAllOtherCamerasAndEveryFrameValue() throws Exception {
        for (var instruction : stores()) {
            var f = frame(past, 0x80, 0); var p = bound(f, 0);
            var a = f.captureActor(); var shared = f.captureShared();
            var before = snapshot(p);
            int expected = p.readPastLoadWord(instruction.target());
            p.storePastLoadWord(new CdTimeWarpLoadData.StoreCall(instruction, OptionalInt.of(expected), 0xBEEF));
            var after = snapshot(p);
            assertEquals(0xBEEF, p.readPastLoadWord(instruction.target()));
            assertEquals(expectedForeground(before.foreground(), instruction.target(), 0xBEEF), after.foreground());
            assertEquals(expectedBackground(before.background(), instruction.target(), 0xBEEF), after.background());
            assertSame(before.plane(), after.plane()); assertSame(before.copies(), after.copies());
            assertSame(before.owner(), after.owner()); assertEquals(a, f.captureActor()); assertEquals(shared, f.captureShared());
        }
    }

    @Test
    void staleUnknownAndForeignTargetStoresFailWithoutPartialMutation() throws Exception {
        var f = frame(past, 0x80, 0); var p = bound(f, 0); var before = snapshot(p);
        assertThrows(IllegalArgumentException.class, () -> p.storePastLoadWord(new CdTimeWarpLoadData.StoreCall(
                CdTimeWarpLoadData.NativeStore.RESTORE_FG_X, OptionalInt.empty(), 7)));
        assertThrows(IllegalArgumentException.class, () -> p.storePastLoadWord(new CdTimeWarpLoadData.StoreCall(
                CdTimeWarpLoadData.NativeStore.RESTORE_FG_X, OptionalInt.of(0x341), 7)));
        for (var target : CdTimeWarpLoadData.StoreTarget.values()) {
            if (stores().stream().anyMatch(i -> i.target() == target)) continue;
            assertThrows(IllegalArgumentException.class, () -> p.readPastLoadWord(target));
            var instruction = java.util.Arrays.stream(CdTimeWarpLoadData.NativeStore.values())
                    .filter(i -> i.target() == target).findFirst().orElseThrow();
            assertThrows(IllegalArgumentException.class, () -> p.storePastLoadWord(
                    new CdTimeWarpLoadData.StoreCall(instruction, OptionalInt.of(0), 0)));
        }
        assertEquals(before, snapshot(p));
        assertThrows(IllegalArgumentException.class, () -> new CdTimeWarpLoadData.StoreCall(
                CdTimeWarpLoadData.NativeStore.RESTORE_FG_X, OptionalInt.of(0), 0x10000));
    }

    @Test
    void sameValueWritesAreObservationalAndDoNotClaimExactlyOnceAuthority() throws Exception {
        var p = bound(frame(past, 0x80, 0), 0); var before = snapshot(p);
        var call = new CdTimeWarpLoadData.StoreCall(CdTimeWarpLoadData.NativeStore.RESTORE_FG_X,
                OptionalInt.of(0x340), 0x340);
        p.storePastLoadWord(call); p.storePastLoadWord(call);
        assertEquals(before, snapshot(p));
    }

    @Test
    void rawMarkedPeriodDirtySavedMotionAndAllNonwritesSurviveRealScrollComposition() throws Exception {
        var f = frame(past, 0x80, 0); var p = bound(f, 0);
        var actor = f.captureActor(); var shared = f.captureShared(); var before = snapshot(p);
        var m = f.state().motion(); var environment = f.environment();
        var setup = CdR11aForegroundScroll.checkpointSetupCamera(before.foreground(),
                m.centreX16_16() >>> 16, m.centreY16_16() >>> 16, environment.movement().bounds());
        var initialized = CdR11bBackgroundScroll.init(before.background(), setup);
        var next = CdR11aForegroundScroll.stepForPeriodBackground(setup, input(f, 0x80), 0x80);
        var n = next.state();
        var bg = CdR11bBackgroundScroll.step(initialized, new CdR11bBackgroundScroll.Input(
                n.cameraX(), n.cameraYWord(), (short) n.scrollXDiff(), (short) n.scrollYDiff(), n.scrollLock() >>> 8));
        p.completePastLoadScroll(f, 0x80);
        var after = snapshot(p);
        assertEquals(new CdR11aForegroundScroll.State(n.cameraX(), n.cameraY16_16(), n.horizontalCrossed(),
                n.verticalCrossed(), n.scrollXDiff(), n.scrollYDiff(), n.scrollFlags() | 0x0400,
                n.destBottomBound(), n.destLeftBound(), n.bottomBoundShift(), n.scrollLock()), after.foreground());
        assertBackground(bg, after.background()); assertEquals(0x10000, longAt(bg.deformationRam(), 0));
        assertEquals(actor, f.captureActor()); assertEquals(0x80, f.environment().movement().gates().timeZone());
        assertSame(shared.artState(), f.artState()); assertSame(shared.vIntClock(), f.vIntClock());
        var e = f.environment(); var old = environment.movement();
        assertEquals(new CdPlayerBody.Environment(new CdPlayerMovement.Environment(old.physics(), next.bounds(),
                old.gates(), new CdPlayerMovement.Globals(old.globals().lookMode(), next.cameraYCentre(),
                old.globals().primaryAngle(), old.globals().secondaryAngle(), old.globals().boredTimer(),
                old.globals().scoreChain(), old.globals().debugAngle(), old.globals().debugAngleShift(),
                old.globals().debugQuadrant(), old.globals().debugFloorDistance())),
                environment.controls(), environment.globals(), environment.dynamicSlots()), e);
        assertSame(before.plane(), after.plane()); assertSame(before.copies(), after.copies());
        assertTrue((boolean) retained(p, "loadScrollPending")); assertFalse((boolean) retained(p, "checkpointSetupPending"));
    }

    @Test
    void setupUsesRestoredCurrentBoundsRatherThanTheDefaultStartOrSavedCameraWords() throws Exception {
        var f = frame(past, 0x80, 0x100); var p = bound(f, 0x100);
        p.storePastLoadWord(new CdTimeWarpLoadData.StoreCall(CdTimeWarpLoadData.NativeStore.RESTORE_FG_X,
                OptionalInt.of(0x340), 0xFFFF));
        p.storePastLoadWord(new CdTimeWarpLoadData.StoreCall(CdTimeWarpLoadData.NativeStore.RESTORE_FG_Y,
                OptionalInt.of(0x180), 0xFFFF));
        p.completePastLoadScroll(f, 0);
        var after = snapshot(p);
        assertEquals(0x0460, after.foreground().cameraX()); // actual actor $500 minus native centre $A0
        assertEquals(0x01A055AA, after.foreground().cameraY16_16());
        assertEquals(0x80, f.environment().movement().gates().timeZone());
        assertEquals(0x2345, after.background().cameraBgX() & 0xFFFF);
        assertEquals(0x6789, after.background().cameraBg2X() & 0xFFFF);
        assertEquals(0xABCD, after.background().cameraBg3X() & 0xFFFF);
    }

    @Test
    void setupValidatesIdentityHudWidthClosedCutAndOneLocalOrderBeforeWrites() throws Exception {
        var f = frame(past, 0x80, 0x100); var p = bound(f, 0x100); var before = snapshot(p);
        assertThrows(IllegalArgumentException.class, () -> p.completePastLoadScroll(frame(past, 0x80, 0), 0));
        assertThrows(IllegalArgumentException.class, () -> p.completePastLoadScroll(f, 0x100));
        assertEquals(before, snapshot(p));
        p.completePastLoadScroll(f, 0);
        var after = snapshot(p);
        assertThrows(IllegalStateException.class, () -> p.completePastLoadScroll(f, 0));
        assertThrows(IllegalStateException.class, () -> p.storePastLoadWord(new CdTimeWarpLoadData.StoreCall(
                CdTimeWarpLoadData.NativeStore.RESTORE_BG_X, OptionalInt.of(p.readPastLoadWord(
                CdTimeWarpLoadData.StoreTarget.BG_X)), 7)));
        assertEquals(after, snapshot(p));
    }

    @Test
    void initialPlaneDrawIsSeparateAndUsesTheActualRetainedPlaneSeed() throws Exception {
        var f = frame(past, 0x80, 0x100); var p = bound(f, 0x100); var before = snapshot(p);
        assertThrows(IllegalStateException.class, p::initializePastPlaneAtLoad);
        assertEquals(before, snapshot(p));
        p.completePastLoadScroll(f, 0);
        var scroll = snapshot(p);
        var expected = CdR11bBackgroundPlane.bind(past).initialize(scroll.plane(),
                live(scroll.background()), flags(scroll.background()));
        var actor = f.captureActor(); var shared = f.captureShared();
        p.initializePastPlaneAtLoad();
        assertPlane(expected.state(), (CdR11aBackgroundPlane.State) retained(p, "planeState")); assertSame(retained(p, "planeState"), retained(p, "publishedPlane"));
        assertSame(scroll.foreground(), retained(p, "scrollState")); assertSame(scroll.background(), retained(p, "backgroundState"));
        assertSame(scroll.copies(), retained(p, "drawCopies")); assertFalse((boolean) retained(p, "loadScrollPending"));
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        assertThrows(IllegalStateException.class, p::initializePastPlaneAtLoad);
    }

    @Test
    void selectorCIsCallableAtTheRealFrameCallbackAndRunsNoBackgroundArithmetic() throws Exception {
        var f = frame(past, 0x80, 0); var p = bound(f, 0);
        var before = snapshot(p); var actor = f.captureActor(); var art = f.artState();
        var live = live(before.background());
        var expected = CdR11bBackgroundPlane.bind(past).incremental(before.plane(), live,
                new CdR11aBackgroundPlane.CopiedCameras(live.bg1(), live.bg2(), live.bg3()), flags(before.background()));
        var order = new java.util.ArrayList<String>();
        p.validatePastLevelLoadPlane();
        f.serviceLevelLoad(0xF5, () -> order.add("PUBLICATION"), () -> {
            order.add("COPY_DRAW");
            assertThrows(IllegalStateException.class, f::captureShared);
            p.servicePastPlaneAtLevelLoad();
        }, () -> order.add("FAST_PLC"));
        assertEquals(List.of("PUBLICATION", "COPY_DRAW", "FAST_PLC"), order);
        assertPlane(expected.state(), (CdR11aBackgroundPlane.State) retained(p, "planeState"));
        assertSame(before.foreground(), retained(p, "scrollState")); assertSame(before.background(), retained(p, "backgroundState"));
        assertEquals(new SonicCdPlayerPresentation.DrawCopies(before.foreground().cameraX() << 16,
                before.foreground().cameraY16_16(), new CdR11aBackgroundPlane.CopiedCameras(live.bg1(), live.bg2(), live.bg3()),
                before.foreground().scrollFlags(), expected.flags()), retained(p, "drawCopies"));
        assertEquals(actor, f.captureActor()); assertSame(art, f.artState()); assertFalse(f.closed());
        assertEquals(1, f.vIntClock().deltaSinceAdmittedCut());
        assertEquals(0xF5, f.environment().controls().rawHeld());
        assertEquals(0x80, f.environment().movement().gates().timeZone());
    }

    @Test
    void selectorCUsesTheSamePortInBothLoadingWaitsAndConsumesOnlyCopiedFlags() throws Exception {
        var f = frame(past, 0x80, 0); var p = bound(f, 0);
        var initial = snapshot(p);
        f.serviceLevelLoad(0, () -> { }, p::servicePastPlaneAtLevelLoad, () -> { });
        assertSame(initial.background(), retained(p, "backgroundState"));
        p.completePastLoadScroll(f, 0); p.initializePastPlaneAtLoad();
        var before = snapshot(p);
        f.serviceLevelLoad(8, () -> { }, p::servicePastPlaneAtLevelLoad, () -> { });
        var live = live(before.background());
        var result = CdR11bBackgroundPlane.bind(past).incremental(before.plane(), live,
                new CdR11aBackgroundPlane.CopiedCameras(live.bg1(), live.bg2(), live.bg3()), flags(before.background()));
        assertPlane(result.state(), (CdR11aBackgroundPlane.State) retained(p, "planeState"));
        assertSame(before.foreground(), retained(p, "scrollState")); assertSame(before.background(), retained(p, "backgroundState"));
        assertEquals(flags(before.background()), flags(snapshot(p).background()));
    }

    @Test
    void everyClosedSourcePortRejectsPendingAndClosedFramesBeforeItsOwnWrites() throws Exception {
        var f = frame(past, 0x80, 0); var p = bound(f, 0); var before = snapshot(p);
        f.prepareInput(8);
        assertThrows(IllegalStateException.class, () -> p.readPastLoadWord(CdTimeWarpLoadData.StoreTarget.FG_X));
        assertThrows(IllegalStateException.class, p::validatePastLevelLoadPlane);
        assertThrows(IllegalStateException.class, () -> p.completePastLoadScroll(f, 0));
        assertThrows(IllegalStateException.class, () -> p.storePastLoadWord(new CdTimeWarpLoadData.StoreCall(
                CdTimeWarpLoadData.NativeStore.RESTORE_FG_X, OptionalInt.of(0x340), 7)));
        assertEquals(before, snapshot(p));
        f.close();
        assertThrows(IllegalStateException.class, p::servicePastPlaneAtLevelLoad);
        assertThrows(IllegalStateException.class, p::initializePastPlaneAtLoad);
        assertEquals(before, snapshot(p));
    }

    @Test
    void wrongRawPeriodRejectsBeforeAnyCameraServiceAndDoesNotNormalizeIt() throws Exception {
        for (int period : new int[]{1, 2, 0x7F, 0x83, 0xFF}) {
            var f = frame(past, period, 0); var p = bound(f, 0); var before = snapshot(p);
            assertThrows(IllegalArgumentException.class, p::validatePastLevelLoadPlane);
            assertThrows(IllegalArgumentException.class, p::servicePastPlaneAtLevelLoad);
            assertThrows(IllegalArgumentException.class, () -> p.completePastLoadScroll(f, 0));
            assertEquals(before, snapshot(p)); assertEquals(period, f.environment().movement().gates().timeZone());
        }
    }

    @Test
    void allBoundActiveMarkersUseTheirWholeRawByteWithoutChangingTheFrameActor() throws Exception {
        for (int period : new int[]{0, 0x80, 0x81, 0x82}) {
            var f = frame(past, period, 0x100); var p = bound(f, 0x100); var actor = f.captureActor();
            p.completePastLoadScroll(f, 0); p.initializePastPlaneAtLoad();
            assertEquals(period, f.environment().movement().gates().timeZone());
            assertEquals(actor, f.captureActor()); assertEquals(0, f.vIntClock().deltaSinceAdmittedCut());
        }
    }

    private static SonicCdPlayerPresentation presentation() {
        return new SonicCdPlayerPresentation(SonicCdGameModule.openingObjectsDevelopmentPlayer());
    }
    private static SonicCdPlayerPresentation bound(CdPlayerFrameRuntime frame, int lock) {
        var p = presentation(); p.bindPastSource(past, frame, foreground(lock), background(0), plane(), copies()); return p;
    }
    private static CdPlayerFrameRuntime frame(CdR11bAssets assets, int period, int lock) {
        var motion = new CdPlayerMotionState(0x05000123, 0x0200ABCD, -0x123, 0x234, -0x345,
                0, 0x56, 0x42, 0x23, 7, 11, 6, 7, 9, 0, -0x1234, 0x44, 0x10, 0x77, 0x88);
        var state = new CdPlayerBody.State(motion, new CdPlayerBody.AnimationCursor(5, 6, 3),
                new CdPlayerBody.Header(1, 0x12345678, 0x2345, 3, 0x21),
                new CdPlayerBody.Timers(0x78, 0x1234, 0x2345, 0x3456),
                new CdPlayerBody.History(bytes(0x100, 13), 0x42), 0x67, new CdPlayerBody.ObjectLinks(0x20, 0x7F), 0x89);
        var b = assets.loadPlan().bounds();
        var env = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(
                new CdPlayerMovement.Physics(0x123, 0x23, 0x34),
                new CdPlayerMovement.Bounds(b.left(), b.right(), 0x300, 0x78),
                new CdPlayerMovement.Gates(0, period, 0x12, 0x34, 0x56, 0x78, 0xFF),
                new CdPlayerMovement.Globals(0x12, 0x60, 0x34, 0x56, 0x1234, 0x4567, 0x78, 0x89, 0x9A, 0xAB)),
                new CdPlayerBody.Controls(0x24, 0x18, 8, 0x10, lock & 0xFF),
                new CdPlayerBody.Globals(0x1234, 160, 0x12, 0x34, 0x4567, 0x2345, 0x56, 0x78),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        return CdPlayerFrameRuntime.fromPastEntry(assets, state, env,
                new CdPlayerArtRuntime.State(bytes(0x300, 7).copy(), bytes(0x300, 11).copy(), 7, true),
                new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(), 0));
    }
    private static CdR11aForegroundScroll.State foreground(int lock) {
        return new CdR11aForegroundScroll.State(0x340, 0x018055AA, 0x10, 0x10, 0x1234, 0x5678,
                0xA55A, 0x345, 0x12, 0x789A, lock);
    }
    private static CdR11aBackgroundScroll.State background(int flags) {
        return new CdR11aBackgroundScroll.State(0x01232345, 0x00454567, 0x00676789,
                0x008989AB, 0x00ABABCD, 0x00CDCDEF, bytes(8, 19), flags, 0, 0,
                bytes(512, 23), bytes(512, 29), bytes(1024, 31), 0x2468ACE0);
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
        byte[] bytes = new byte[length];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * multiplier + 3);
        return CdAssetBytes.copyOf(bytes);
    }
    private static List<CdTimeWarpLoadData.NativeStore> stores() {
        return List.of(CdTimeWarpLoadData.NativeStore.RESTORE_DEST_BOTTOM,
                CdTimeWarpLoadData.NativeStore.RESTORE_FG_X, CdTimeWarpLoadData.NativeStore.RESTORE_FG_Y,
                CdTimeWarpLoadData.NativeStore.RESTORE_BG_X, CdTimeWarpLoadData.NativeStore.RESTORE_BG_Y,
                CdTimeWarpLoadData.NativeStore.RESTORE_BG2_X, CdTimeWarpLoadData.NativeStore.RESTORE_BG2_Y,
                CdTimeWarpLoadData.NativeStore.RESTORE_BG3_X, CdTimeWarpLoadData.NativeStore.RESTORE_BG3_Y);
    }
    private static CdR11aForegroundScroll.State expectedForeground(CdR11aForegroundScroll.State f,
            CdTimeWarpLoadData.StoreTarget target, int value) {
        return new CdR11aForegroundScroll.State(target == CdTimeWarpLoadData.StoreTarget.FG_X ? value : f.cameraX(),
                target == CdTimeWarpLoadData.StoreTarget.FG_Y ? word(f.cameraY16_16(), value) : f.cameraY16_16(),
                f.horizontalCrossed(), f.verticalCrossed(), f.scrollXDiff(), f.scrollYDiff(), f.scrollFlags(),
                target == CdTimeWarpLoadData.StoreTarget.DEST_BOTTOM_BOUND ? value : f.destBottomBound(),
                f.destLeftBound(), f.bottomBoundShift(), f.scrollLock());
    }
    private static CdR11aBackgroundScroll.State expectedBackground(CdR11aBackgroundScroll.State b,
            CdTimeWarpLoadData.StoreTarget t, int value) {
        return new CdR11aBackgroundScroll.State(t == CdTimeWarpLoadData.StoreTarget.BG_X ? word(b.cameraBgX(), value) : b.cameraBgX(),
                t == CdTimeWarpLoadData.StoreTarget.BG_Y ? word(b.cameraBgY(), value) : b.cameraBgY(),
                t == CdTimeWarpLoadData.StoreTarget.BG2_X ? word(b.cameraBg2X(), value) : b.cameraBg2X(),
                t == CdTimeWarpLoadData.StoreTarget.BG2_Y ? word(b.cameraBg2Y(), value) : b.cameraBg2Y(),
                t == CdTimeWarpLoadData.StoreTarget.BG3_X ? word(b.cameraBg3X(), value) : b.cameraBg3X(),
                t == CdTimeWarpLoadData.StoreTarget.BG3_Y ? word(b.cameraBg3Y(), value) : b.cameraBg3Y(),
                b.crossingRam(), b.scrollFlagsBg(), b.scrollFlagsBg2(), b.scrollFlagsBg3(), b.cloudRam(),
                b.deformationRam(), b.hscrollRam(), b.vscroll());
    }
    private static CdR11aForegroundScroll.Input input(CdPlayerFrameRuntime f, int hud) {
        var m = f.state().motion(); var e = f.environment(); var shared = e.movement();
        return new CdR11aForegroundScroll.Input(new CdR11aForegroundScroll.Actor(m.centreX16_16() >>> 16,
                m.centreY16_16() >>> 16, m.flags(), m.groundVelocity(), m.animation(), m.playerControl()),
                shared.bounds(), past.loadPlan().bounds().top(), e.globals().cameraXCentre(),
                shared.globals().cameraYCentre(), CdReleaseVariant.USA, hud, 320, 224);
    }
    private static CdR11aBackgroundPlane.LiveCameras live(CdR11aBackgroundScroll.State b) {
        return new CdR11aBackgroundPlane.LiveCameras(new CdR11aBackgroundPlane.Camera(b.cameraBgX(), b.cameraBgY()),
                new CdR11aBackgroundPlane.Camera(b.cameraBg2X(), b.cameraBg2Y()),
                new CdR11aBackgroundPlane.Camera(b.cameraBg3X(), b.cameraBg3Y()));
    }
    private static CdR11aBackgroundPlane.FlagWords flags(CdR11aBackgroundScroll.State b) {
        return new CdR11aBackgroundPlane.FlagWords(b.scrollFlagsBg(), b.scrollFlagsBg2(), b.scrollFlagsBg3());
    }
    private static void assertPlane(CdR11aBackgroundPlane.State expected, CdR11aBackgroundPlane.State actual) {
        assertArrayEquals(expected.nameTable().copy(), actual.nameTable().copy());
    }
    private static void assertBackground(CdR11aBackgroundScroll.State e, CdR11aBackgroundScroll.State a) {
        assertEquals(List.of(e.cameraBgX(), e.cameraBgY(), e.cameraBg2X(), e.cameraBg2Y(), e.cameraBg3X(), e.cameraBg3Y(),
                e.scrollFlagsBg(), e.scrollFlagsBg2(), e.scrollFlagsBg3(), e.vscroll()),
                List.of(a.cameraBgX(), a.cameraBgY(), a.cameraBg2X(), a.cameraBg2Y(), a.cameraBg3X(), a.cameraBg3Y(),
                a.scrollFlagsBg(), a.scrollFlagsBg2(), a.scrollFlagsBg3(), a.vscroll()));
        assertArrayEquals(e.crossingRam().copy(), a.crossingRam().copy());
        assertArrayEquals(e.cloudRam().copy(), a.cloudRam().copy());
        assertArrayEquals(e.deformationRam().copy(), a.deformationRam().copy());
        assertArrayEquals(e.hscrollRam().copy(), a.hscrollRam().copy());
    }
    private static int word(int before, int value) { return value << 16 | before & 0xFFFF; }
    private static int longAt(CdAssetBytes bytes, int offset) {
        return (bytes.unsignedByte(offset) << 24) | (bytes.unsignedByte(offset + 1) << 16)
                | (bytes.unsignedByte(offset + 2) << 8) | bytes.unsignedByte(offset + 3);
    }
    private static Object retained(SonicCdPlayerPresentation p, String name) throws Exception {
        var field = SonicCdPlayerPresentation.class.getDeclaredField(name); field.setAccessible(true); return field.get(p);
    }
    private record Retained(CdR11aForegroundScroll.State foreground, CdR11aBackgroundScroll.State background,
                            CdR11aBackgroundPlane.State plane, SonicCdPlayerPresentation.DrawCopies copies, Object owner) { }
    private static Retained snapshot(SonicCdPlayerPresentation p) throws Exception {
        return new Retained((CdR11aForegroundScroll.State) retained(p, "scrollState"),
                (CdR11aBackgroundScroll.State) retained(p, "backgroundState"),
                (CdR11aBackgroundPlane.State) retained(p, "planeState"),
                (SonicCdPlayerPresentation.DrawCopies) retained(p, "drawCopies"), retained(p, "scrollCaptureOwner"));
    }
}
