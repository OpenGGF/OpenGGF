package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.RetainedPlaneSceneRenderer;
import com.openggf.graphics.SpritePresentation;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.ready;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Actual cold-program/controller World ingress; no actor, camera, PLC, art or SAT reseeding. */
@Isolated
class TestSonicCdPastSelectedDisplayWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualPastEntryRetainsDisabledNativeSelectionWithoutBorrowingArtOwners() throws Exception {
        var h = opening();
        for (int call = 1; call <= 7729; call++) step(h, route(call));
        var frame = h.module().requirePlayerFrame(); var before = captureNative(h);
        var selected = h.module().pastSelectedDisplay();
        assertInstanceOf(SonicCdPastDisplay.Disabled.class, selected.projection());
        assertFalse(selected.inputs().displayEnabled()); assertTrue(selected.inputs().selectedCpu().isEmpty());
        assertArrayEquals(new byte[640], selected.inputs().selectedSat().copy());
        assertArrayEquals(new byte[128], selected.inputs().palette().copy());
        assertArrayEquals(new byte[1024], selected.inputs().hscroll().copy());
        assertNull(selected.inputs().foreground()); assertNull(selected.inputs().background());
        assertNull(selected.inputs().current()); assertNull(selected.inputs().player());
        assertSame(selected, h.module().pastSelectedDisplay());
        sameNative(before, captureNative(h), "observational disabled query");
        assertFalse(frame.closed());
    }

    @Test void natural7778SelectsTheGenuine7777CpuWithNowServicedArtAndPhysicalPlanes() throws Exception {
        var p = through(7777); var h = p.harness();
        var prior = cpu(p.visit()); var oldSelected = h.module().pastSelectedDisplay();
        var before = h.module().pastLoadState();
        var scroll = (SonicCdPlayerPresentation.ScrollCapture) h.module().getScrollHandlerProvider().getHandler(0).captureRewindState();
        assertEquals(30, prior.spriteCount());
        assertEquals(2, prior.provenance().stream().filter(v -> v.slotRef().wholeIndex() == 0).count());
        assertTrue(oldSelected.inputs().selectedCpu().orElseThrow().provenance().stream()
                .noneMatch(v -> v.slotRef().wholeIndex() == 0), "7777 displays the real earlier7776 CPU");
        step(h, route(7778));
        var selected = h.module().pastSelectedDisplay(); var actual = h.module().pastLoadState();
        assertSame(prior, selected.inputs().selectedCpu().orElseThrow());
        assertArrayEquals(prior.bytes(), selected.inputs().selectedSat().copy());
        assertNotSame(prior, cpu(p.visit()), "Main has produced the later CPU without replacing selection");
        assertSame(actual.section().residency(), selected.inputs().current());
        assertArrayEquals(p.frame().artState().residentBank().copy(), selected.inputs().player().residentBank().copy());
        var values = actualField(p.visit(), "values");
        assertSame(actualField(values, "foregroundPlane"), selected.inputs().foreground());
        var presentation = (SonicCdPlayerPresentation) actualField(h.module(), "playerPresentation");
        assertSame(presentation.pastPlaneImage(), selected.inputs().background());
        assertArrayEquals(before.cpuPalette().copy(), selected.inputs().palette().copy());
        assertArrayEquals(Arrays.copyOf(scroll.background().hscrollRam().copy(), 0x380),
                selected.inputs().hscroll().slice(0, 0x380).copy());
        assertArrayEquals(oldSelected.inputs().hscroll().slice(0x380, 0x80).copy(),
                selected.inputs().hscroll().slice(0x380, 0x80).copy());
        assertEquals(scroll.background().vscroll(), selected.inputs().vscrollLongBits());
        assertEquals(1, actual.calls().stream().filter(v -> v == SonicCdGameModule.PastLoadCall.ORDINARY_SERVICE).count());
        var nativeAfter = captureNative(h);
        assertSame(selected, h.module().pastSelectedDisplay());
        sameNative(nativeAfter, captureNative(h), "selected query");
    }

    @Test void full7777RestoreThenForward7778RetainsSelectionAndAllNativePartitions() throws Exception {
        var p = through(7777); var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var before = captureNative(h); var selectedBefore = h.module().pastSelectedDisplay();
        step(h, route(7778)); var after = captureNative(h); var selectedAfter = h.module().pastSelectedDisplay();
        step(h, route(7779));
        registry.restore(full); sameNative(before, captureNative(h), "full7777 before forward");
        assertSame(selectedBefore, h.module().pastSelectedDisplay());
        step(h, route(7778)); sameNative(after, captureNative(h), "full7778 after forward");
        assertEquals(selectedAfter, h.module().pastSelectedDisplay());
    }

    @Test void course7806RestoreThenForward7807RetainsThePriorCpuAndSelectedVersions() throws Exception {
        var p = through(7806); var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var course = registry.captureCourse(); var before = captureNative(h); var prior = cpu(p.visit());
        var selectedBefore = h.module().pastSelectedDisplay();
        step(h, route(7807)); var after = captureNative(h); var selectedAfter = h.module().pastSelectedDisplay();
        assertSame(prior, selectedAfter.inputs().selectedCpu().orElseThrow());
        step(h, route(7808));
        registry.restoreCourse(course); sameNative(before, captureNative(h), "course7806 before forward");
        assertSame(selectedBefore, h.module().pastSelectedDisplay());
        step(h, route(7807)); sameNative(after, captureNative(h), "course7807 after forward");
        assertEquals(selectedAfter, h.module().pastSelectedDisplay());
    }

    @Test void realPauseAndUnpauseSelectPriorCpuOnceWithoutRunningPausedMain() throws Exception {
        var p = through(7777); var h = p.harness(); var frame = p.frame(); var prior = cpu(p.visit());
        var actor = frame.captureActor(); int levelFrame = frame.environment().globals().levelFrames();
        long clock = frame.vIntClock().absoluteValue().orElseThrow();
        step(h, start(true)); assertTrue(frame.paused()); assertEquals(actor, frame.captureActor());
        assertEquals(levelFrame, frame.environment().globals().levelFrames());
        assertSame(prior, h.module().pastSelectedDisplay().inputs().selectedCpu().orElseThrow());
        assertFalse(h.module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS));
        step(h, start(false)); assertTrue(frame.paused()); assertEquals(actor, frame.captureActor());
        assertEquals(clock + 2, frame.vIntClock().absoluteValue().orElseThrow());
        assertSame(prior, h.module().pastSelectedDisplay().inputs().selectedCpu().orElseThrow());
        step(h, start(true)); assertFalse(frame.paused());
        assertEquals(levelFrame + 1, frame.environment().globals().levelFrames());
        assertEquals(clock + 3, frame.vIntClock().absoluteValue().orElseThrow());
        assertSame(prior, h.module().pastSelectedDisplay().inputs().selectedCpu().orElseThrow());
        assertNotSame(prior, cpu(p.visit()));
    }

    @Test void actualEgl7778DrawsSelectedSonicReplaysOldPixelsAndRetiresOnlyOwnedResources() throws Exception {
        byte[] imageForExport;
        try (var egl = Egl.open()) {
            var p = through(7777); var h = p.harness(); var registry = h.mode().getRewindRegistry();
            var full = registry.capture(); var before = captureNative(h);
            step(h, route(7778)); var nativeAfter = captureNative(h); var selected = h.module().pastSelectedDisplay();
            var scene = readyScene(selected);
            assertEquals(2, selected.inputs().selectedCpu().orElseThrow().provenance().stream()
                    .filter(v -> v.slotRef().wholeIndex() == 0).count());
            var actual = draw(h); sameNative(nativeAfter, captureNative(h), "first actual draw");
            imageForExport = actual.clone();
            assertArrayEquals(actual, draw(h)); sameNative(nativeAfter, captureNative(h), "repeat actual draw");
            var presentation = actualField(h.module(), "playerPresentation");
            var binding = actualField(presentation, "pastRenderBinding");
            var renderer = (RetainedPlaneSceneRenderer) actualField(binding, "renderer");
            var contrast = omitPrimary(scene, selected.inputs().selectedCpu().orElseThrow());
            var camera = h.mode().getCamera(); clear();
            renderer.draw(contrast, camera.getX(), camera.getY(), h.module()::validatePastLoadingCut);
            var absentPrimary = pixels();
            assertFalse(Arrays.equals(actual, absentPrimary),
                    "Natural7778 must have an actual visible selected whole0 contribution; no pose/clearance fitting");
            assertArrayEquals(actual, draw(h)); sameNative(nativeAfter, captureNative(h), "omission contrast is render-only");
            step(h, route(7779)); draw(h);
            registry.restore(full); sameNative(before, captureNative(h), "EGL full7777 before forward");
            step(h, route(7778)); sameNative(nativeAfter, captureNative(h), "EGL full7778 before pixels");
            assertEquals(selected, h.module().pastSelectedDisplay()); assertArrayEquals(actual, draw(h));
            var atlas = egl.graphics.getPatternAtlas();
            var selectedIds = scene.nativePatterns().keySet();
            for (int id : selectedIds) assertNotNull(atlas.getEntry(PatternAtlasRange.OBJECTS.base() + id));
            atlas.registerRange(PatternAtlasRange.HUD); int other = PatternAtlasRange.HUD.base() + 19;
            if (atlas.getEntry(other) == null) egl.graphics.cachePatternTexture(new com.openggf.level.Pattern(), other);
            var unrelated = atlas.getEntry(other); var unrelatedSample = SpritePresentation.patternSample(egl.graphics, other);
            var sharedMask = egl.graphics.getTilePriorityFBO(); int maskTexture = sharedMask.getTextureId();
            SessionManager.closeGameplaySession();
            for (int id : selectedIds) {
                assertNull(atlas.getEntry(PatternAtlasRange.OBJECTS.base() + id));
                assertNull(SpritePresentation.patternSample(egl.graphics, PatternAtlasRange.OBJECTS.base() + id));
            }
            assertSame(unrelated, atlas.getEntry(other));
            assertEquals(unrelatedSample, SpritePresentation.patternSample(egl.graphics, other));
            assertSame(sharedMask, egl.graphics.getTilePriorityFBO()); assertTrue(glIsTexture(maskTexture));
        }
        writeOptional7778Capture(imageForExport); // Only after session, graphics and context cleanup.
    }

    @Test void actualEglOldFifoCannotCaptureOrRestoreNativeStateDuringSelectedDraw() throws Exception {
        try (var egl = Egl.open()) {
            var p = through(7778); var h = p.harness(); readyScene(h.module().pastSelectedDisplay());
            var registry = h.mode().getRewindRegistry(); var full = registry.capture(); var before = captureNative(h);
            var calls = new AtomicInteger();
            egl.graphics.registerCommand((x, y, width, height) -> {
                calls.incrementAndGet();
                assertThrows(IllegalStateException.class, registry::capture);
                assertThrows(IllegalStateException.class, () -> registry.restore(full));
                assertThrows(IllegalStateException.class, () -> h.module().servicePastLoading(8));
            });
            draw(h); assertEquals(1, calls.get()); assertFalse(p.frame().closed());
            sameNative(before, captureNative(h), "blocked reentry");
            draw(h); assertEquals(1, calls.get()); sameNative(before, captureNative(h), "old FIFO not replayed");
        }
    }

    @Test void actualEglOldFifoCannotDirectlyCaptureOrRestoreTheInstalledScrollHandler() throws Exception {
        try (var egl = Egl.open()) {
            var p = through(7777); var h = p.harness();
            var handler = h.module().getScrollHandlerProvider().getHandler(0);
            var earlier = handler.captureRewindState();
            step(h, route(7778)); readyScene(h.module().pastSelectedDisplay());
            assertNotEquals(earlier, handler.captureRewindState(), "Genuine earlier scroll cut must differ");
            var before = captureNative(h); var calls = new AtomicInteger();
            var captureRejected = new java.util.concurrent.atomic.AtomicBoolean();
            var restoreRejected = new java.util.concurrent.atomic.AtomicBoolean();
            egl.graphics.registerCommand((x, y, width, height) -> {
                calls.incrementAndGet();
                try { handler.captureRewindState(); }
                catch (IllegalStateException expected) { captureRejected.set(true); }
                try { handler.restoreRewindState(earlier); }
                catch (IllegalStateException expected) { restoreRejected.set(true); }
            });
            draw(h); assertFalse(p.frame().closed());
            sameNative(before, captureNative(h), "direct installed scroll reentry must not mutate native state");
            assertTrue(captureRejected.get(), "Direct scroll capture must reject during selected draw");
            assertTrue(restoreRejected.get(), "Direct scroll restore must reject during selected draw");
            assertEquals(1, calls.get());
        }
    }

    @Test void actualEglRecursiveOverlayRejectionLeavesTheHealthyFrameOpen() throws Exception {
        try (var egl = Egl.open()) {
            var p = through(7778); var h = p.harness(); readyScene(h.module().pastSelectedDisplay());
            var before = captureNative(h); var calls = new AtomicInteger();
            var rejected = new java.util.concurrent.atomic.AtomicBoolean();
            egl.graphics.registerCommand((x, y, width, height) -> {
                calls.incrementAndGet();
                try { h.module().gameplayFrameController().drawOverlay(); }
                catch (IllegalStateException expected) { rejected.set(true); }
            });
            byte[] actual = assertDoesNotThrow(() -> draw(h));
            assertTrue(rejected.get()); assertFalse(p.frame().closed()); assertEquals(1, calls.get());
            sameNative(before, captureNative(h), "rejected recursive overlay must preserve the healthy producer");
            assertArrayEquals(actual, draw(h));
        }
    }

    @Test void actualEglStaleAtlasRejectsTheWorldDrawAndPreservesReplacementEntries() throws Exception {
        try (var egl = Egl.open()) {
            var p = through(7778); var h = p.harness(); readyScene(h.module().pastSelectedDisplay()); draw(h);
            var frame = p.frame(); var state = frame.state(); var env = frame.environment(); var clock = frame.vIntClock();
            var originalAtlas = egl.graphics.getPatternAtlas(); egl.graphics.resetPatternAndPaletteState();
            var replacement = egl.graphics.getPatternAtlas(); assertNotSame(originalAtlas, replacement);
            replacement.registerRange(PatternAtlasRange.OBJECTS); int id = PatternAtlasRange.OBJECTS.base() + 77;
            egl.graphics.cachePatternTexture(new com.openggf.level.Pattern(), id);
            var foreign = replacement.getEntry(id); var foreignSample = SpritePresentation.patternSample(egl.graphics, id);
            assertThrows(IllegalStateException.class, () -> h.module().gameplayFrameController().drawOverlay());
            assertTrue(frame.closed()); assertEquals(state, frame.state()); assertEquals(env, frame.environment());
            assertEquals(clock, frame.vIntClock());
            SessionManager.closeGameplaySession();
            assertSame(foreign, replacement.getEntry(id));
            assertEquals(foreignSample, SpritePresentation.patternSample(egl.graphics, id));
        }
    }

    private static TestSonicCdPastRingWorld.Prepared through(int end) throws Exception {
        var p = ready(); for (int call = 7775; call <= end; call++) step(p.harness(), route(call)); return p;
    }
    @SuppressWarnings("unchecked") private static CdObjectDrawQueue.CpuSat cpu(Object visit) throws Exception {
        return ((Optional<CdObjectDrawQueue.CpuSat>) actualField(actualField(actualField(visit, "values"), "loop"), "completedDraw")).orElseThrow();
    }
    private static LogicalInputSnapshot start(boolean pressed) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0, 0, 0, 0, pressed, pressed), PlayerInputState.neutral());
    }
    private static RetainedPlaneSceneRenderer.Scene readyScene(SonicCdPastDisplay.Selection selected) {
        return assertInstanceOf(SonicCdPastDisplay.Ready.class, selected.projection(),
                "Actual strict selected-image prerequisite: " + selected.projection()).scene();
    }
    private static byte[] draw(Harness h) {
        readyScene(h.module().pastSelectedDisplay()); clear(); h.module().gameplayFrameController().drawOverlay();
        return pixels();
    }
    private static void clear() { glViewport(0, 0, 320, 224); glClearColor(1, 0, 1, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); }
    private static byte[] pixels() {
        glFinish(); var b = BufferUtils.createByteBuffer(320 * 224 * 4);
        glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, b); assertEquals(GL_NO_ERROR, glGetError());
        byte[] result = new byte[b.capacity()]; b.get(result); return result;
    }
    private static RetainedPlaneSceneRenderer.Scene omitPrimary(RetainedPlaneSceneRenderer.Scene scene, CdObjectDrawQueue.CpuSat cpu) {
        byte[] raw = cpu.bytes(); var tiles = new ArrayList<SpritePresentation.Tile>(); int at = 0;
        for (int index = cpu.spriteCount() - 1; index >= 0; index--) {
            int size = raw[index * 8 + 2] & 15, count = (((size >>> 2) & 3) + 1) * ((size & 3) + 1);
            if (cpu.provenance().get(index).slotRef().wholeIndex() != 0) tiles.addAll(scene.sprites().tiles().subList(at, at + count));
            at += count;
        }
        assertEquals(scene.sprites().tiles().size(), at); assertTrue(tiles.size() < at);
        var picture = new SpritePresentation.Frame(tiles, List.of(), scene.sprites().patternVersions());
        return new RetainedPlaneSceneRenderer.Scene(scene.foreground(), scene.background(), scene.packedHscroll256(),
                scene.vscrollLongBits(), scene.selectedPalette128(), true, picture, scene.nativePatterns());
    }
    private static void sameNative(Object expected, Object actual, String label) throws Exception {
        if (expected == actual) return;
        assertNotNull(expected, label); assertNotNull(actual, label); assertEquals(expected.getClass(), actual.getClass(), label);
        if (expected instanceof CdAssetBytes b) assertArrayEquals(b.copy(), ((CdAssetBytes) actual).copy(), label);
        else if (expected instanceof CdObjectDrawQueue.CpuSat b) {
            var a = (CdObjectDrawQueue.CpuSat) actual; assertArrayEquals(b.bytes(), a.bytes(), label);
            assertEquals(b.spriteCount(), a.spriteCount(), label); assertEquals(b.provenance(), a.provenance(), label);
        } else if (expected instanceof Optional<?> b) {
            var a = (Optional<?>) actual; assertEquals(b.isPresent(), a.isPresent(), label);
            if (b.isPresent()) sameNative(b.orElseThrow(), a.orElseThrow(), label + ".value");
        } else if (expected instanceof java.util.Map<?, ?> b) {
            var a = (java.util.Map<?, ?>) actual; assertEquals(b.keySet(), a.keySet(), label);
            for (var key : b.keySet()) sameNative(b.get(key), a.get(key), label + '[' + key.toString() + ']');
        } else if (expected instanceof List<?> b) {
            var a = (List<?>) actual; assertEquals(b.size(), a.size(), label);
            for (int i = 0; i < b.size(); i++) sameNative(b.get(i), a.get(i), label + '[' + i + ']');
        } else if (expected.getClass().isArray()) {
            assertEquals(Array.getLength(expected), Array.getLength(actual), label);
            for (int i = 0; i < Array.getLength(expected); i++) sameNative(Array.get(expected, i), Array.get(actual, i), label + '[' + i + ']');
        } else if (expected.getClass().isRecord()) {
            for (var component : expected.getClass().getRecordComponents()) {
                var read = component.getAccessor(); read.setAccessible(true);
                sameNative(read.invoke(expected), read.invoke(actual), label + '.' + component.getName());
            }
        } else if (Set.of("com.openggf.game.soniccd.SonicCdPastLoadingVisit$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPlacementRuntime$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPool$Snapshot").contains(expected.getClass().getName())) {
            for (var field : expected.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true); sameNative(field.get(expected), field.get(actual), label + '.' + field.getName());
            }
        } else assertEquals(expected, actual, label);
    }

    /** Optional observed engine pixels; the export never supplies runtime state or expected pixels. */
    private static void writeOptional7778Capture(byte[] rgba) throws Exception {
        String output = System.getenv("SONICCD_PAST_DISPLAY_OUTPUT");
        if (output == null || output.isBlank()) return;
        var directory = java.nio.file.Path.of(output).toAbsolutePath().normalize();
        java.nio.file.Files.createDirectories(directory);
        var image = new java.awt.image.BufferedImage(320, 224, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
            int at = ((223 - y) * 320 + x) * 4;
            int argb = (rgba[at + 3] & 255) << 24 | (rgba[at] & 255) << 16
                    | (rgba[at + 1] & 255) << 8 | rgba[at + 2] & 255;
            image.setRGB(x, y, argb);
        }
        if (!javax.imageio.ImageIO.write(image, "png", directory.resolve("past-world-call-7778.png").toFile()))
            throw new java.io.IOException("PNG writer unavailable");
    }

    private static final class Egl implements AutoCloseable {
        private final PresentationOffscreenTestContext context;
        private final GraphicsManager graphics;
        private Egl(PresentationOffscreenTestContext context, GraphicsManager graphics) { this.context = context; this.graphics = graphics; }
        static Egl open() throws Exception {
            assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual World EGL/media opt-in");
            assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
            assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
            var context = PresentationOffscreenTestContext.openConfigured(320, 224, PresentationOffscreenTestContext.LegacyProfile.CORE_41);
            var graphics = GameServices.graphics();
            try {
                graphics.setHeadlessMode(false); graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setProjectionWidth(320); graphics.setViewport(0, 0, 320, 224);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]));
                graphics.setWindowHeight(224); graphics.setScreenHeight(224); glViewport(0, 0, 320, 224);
                return new Egl(context, graphics);
            } catch (Exception | Error failure) {
                try { graphics.cleanup(); } catch (Exception | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                try { GraphicsManager.destroyForReinit(); } catch (Exception | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                try { context.close(); } catch (Exception | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        @Override public void close() {
            try { SessionManager.closeGameplaySession(); }
            finally { try { graphics.cleanup(); }
                finally { try { GraphicsManager.destroyForReinit(); TestEnvironment.resetAll(); } finally { context.close(); } } }
        }
    }
}
