package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevelLoadingSource;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.scene.CdWarpScene;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Strict independent destination requirement. All Java/GL/runtime/export UNRUN.
 * The public controller alone reaches the real marked restart and selected WARP.
 * A missing transfer/owner, death or bounded timeout is a failure, never success.
 * No state, pose, period, scene, source, input-history or invocation is hydrated.
 */
@Isolated
class TestCdOriginalPastArrivalPixelsAndRewind extends TestCdOriginalR11LoadingWorld {
    private static final int ARRIVAL_BOUND = 512;
    private static final Path CAPTURE_ROOT =
            Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD", "captures", "engine");
    private static final Path CAPTURE_DIRECTORY = CAPTURE_ROOT.resolve("original-past-arrival-20261109");
    private static final String CAPTURE_FILE = "ENGINE-original-Past-first-main.png";

    private record Cut(CompositeSnapshot full, CompositeSnapshot course,
                       Map<String, Object> fullValues, Map<String, Object> courseValues) { }
    private record Arrival(Cut markedLoading, int servicesFromMarkedToReady) { }

    @Test
    void genuinePastArrivalReturnsReadyAndFirstMainWithCompleteReplayAndFreshPixels() throws Exception {
        Path export = Boolean.getBoolean("soniccd.originalPastArrival.capture")
                ? validatedCaptureDirectory() : null;
        var source = TestCdOriginalBootPrefix.source();
        var attempt = SonicCdGameModule.prepareSelectedProfile(source, new SaveManager(root), 1,
                SonicCdGameModule.ProfileEntry.NEW_GAME, 0);
        var module = attempt.module().orElseThrow();
        // This fixture owns the existing configured engine EGL context. No second context.
        try (var boot = boot(); var cleanup = disposeWhileContextCurrent()) {
            var loop = boot.bootSonicCdSelectedProfile(source, attempt);
            reachActualPastRequest(loop, module);
            var destination = module.originalWarpDestination().orElseThrow();
            assertEquals(CdCommand.R11B, destination.command());
            var past = destination.loadingSource().orElseThrow();
            assertSame(source.initialProgramSource().orElseThrow().pastLoadingSource(), past);
            assertEquals(CdStagePayload.R11B.stage(), past.stage());
            assertEquals(CdLevelLoadingSource.StandardBackgroundProgram.PARALLAX_CLOUD_BANDS,
                    past.standardBackgroundProgram());

            var departing = module.originalR11EntryFrame();
            var oldActor = departing.captureActor();
            var oldState = departing.state();
            var oldEnvironment = departing.environment();
            var oldArt = departing.artState();
            var oldClock = departing.vIntClock();
            var oldSaved = module.originalR11TimeWarpSavedImage();
            var beforeTransfer = cut();
            var beforeSounds = List.copyOf(module.programSoundRequests());
            // These restores occur while the selected-request lifetime is still current.
            // Do not force an old capture into an already retired destination lifetime.
            for (boolean course : new boolean[]{false, true}) {
                restore(beforeTransfer, course);
                assertCut(beforeTransfer, course);
                assertEquals(beforeSounds, module.programSoundRequests(), "Restore replays no sound");
                assertEquals(destination, module.originalWarpDestination().orElseThrow());
            }

            var arrival = arriveReady(loop, module, past);
            assertNotSame(departing, module.originalR11EntryFrame(), "Actual destination Frame birth");
            assertSame(oldState, departing.state(), "Old Frame is not serviced during destination loading");
            assertSame(oldEnvironment, departing.environment());
            CdCapturedValueAssertions.assertCapturedValueEquals(oldArt, departing.artState());
            assertEquals(oldClock, departing.vIntClock());
            assertEquals(oldSaved, module.originalR11TimeWarpSavedImage(), "Complete 42-byte saved bank");
            assertReady(module, past);
            var ready = cut();

            step(loop, 0);
            assertFirstMain(module, past);
            var firstMain = cut();
            var firstSounds = List.copyOf(module.programSoundRequests());
            var pixels = drawClosed(module);
            assertTrue(Arrays.stream(pixels.pixels()).distinct().count() > 1,
                    "A flat IPX/white/sentinel picture is not a loaded Past scene");

            for (boolean course : new boolean[]{false, true}) {
                restore(arrival.markedLoading(), course);
                assertCut(arrival.markedLoading(), course);
                assertEquals(0x80, module.originalR11EntryFrame().environment().movement().gates().timeZone(),
                        "The actual marked loading cut must not be normalized by DATA/defaults");
                for (int n = 0; n < arrival.servicesFromMarkedToReady(); n++) step(loop, 0);
                assertReady(module, past);
                assertCut(ready, course);
                step(loop, 0);
                assertFirstMain(module, past);
                assertCut(firstMain, course);
                assertPixels(pixels, drawClosed(module));

                restore(ready, course);
                assertCut(ready, course);
                step(loop, 0);
                assertFirstMain(module, past);
                assertCut(firstMain, course);
                assertPixels(pixels, drawClosed(module));

                restore(firstMain, course);
                assertCut(firstMain, course);
                assertEquals(firstSounds, module.programSoundRequests(), "Current-cut restore is effect-free");
                assertPixels(pixels, drawClosed(module));
                assertSame(oldState, departing.state());
                assertSame(oldEnvironment, departing.environment());
                CdCapturedValueAssertions.assertCapturedValueEquals(oldArt, departing.artState());
                assertEquals(oldClock, departing.vIntClock());
                assertEquals(oldSaved, module.originalR11TimeWarpSavedImage());
            }

            var healthy = cut();
            var failure = new AtomicReference<Throwable>();
            var foreignThread = new Thread(() -> {
                try { module.serviceOriginalR11Loading(0); }
                catch (Throwable rejected) { failure.set(rejected); }
            });
            foreignThread.start(); foreignThread.join();
            assertInstanceOf(IllegalStateException.class, failure.get());
            assertCut(healthy, false);
            assertFalse(module.originalR11EntryFrame().closed(), "Rejection preserves the healthy current Frame");

            // A copied value from the genuine old actor is still not a current source/lifetime seal.
            Method capture = SonicCdGameModule.class.getDeclaredMethod("captureOriginalR11EntryImage");
            capture.setAccessible(true);
            Object currentImage = capture.invoke(module);
            Object forged = copyRecord(currentImage, "actor", oldActor);
            Method validate = SonicCdGameModule.class.getDeclaredMethod(
                    "validateOriginalR11Restore", currentImage.getClass());
            validate.setAccessible(true);
            var rejection = assertThrows(InvocationTargetException.class, () -> validate.invoke(module, forged));
            assertInstanceOf(RuntimeException.class, rejection.getCause());
            assertCut(healthy, false);
            assertFirstMain(module, past);
            assertPixels(pixels, drawClosed(module));

            // No export in finally: only an already proven healthy first-Main image is written.
            if (export != null) exportOnce(export, pixels);
        }
    }

    private static void reachActualPastRequest(GameLoop loop, SonicCdGameModule module) {
        for (int n = 0; n < 256 && module.originalR11LoadingPrefix().main().levelStartedByte() != 1; n++)
            step(loop, 0);
        assertEquals(SonicCdR31aLoadingVisit.Phase.READY, module.originalR11LoadingImage().phase());
        assertEquals(CdStagePayload.R11A.stage(), module.originalR11EntryFrame().stageSource().stage());
        for (int n = 0; n < 6351; n++) {
            int held = ((n >= 4000 && n < 4032) || (n >= 4096 && n < 4128)
                    || (n >= 4608 && n < 4640) || (n >= 6000 && n < 6032)) ? 0x18 : 8;
            step(loop, held);
        }
        assertEquals(SonicCdR31aLoadingVisit.Phase.INITIAL_TO_WHITE, module.originalR11LoadingImage().phase());
        for (int n = 0; n < 22; n++) step(loop, 0);
        assertEquals(SonicCdGameModule.OriginalWarpPhase.LEVEL_RETURN_TAIL, module.originalWarpPhase());
        step(loop, 0); step(loop, 0);
        for (int n = 0; n < 90; n++) step(loop, 0);
        step(loop, 0);
        for (int n = 0; n < 24; n++) step(loop, 0);
        assertEquals(CdWarpScene.Phase.MAIN, module.warpSceneState().orElseThrow().phase());
        step(loop, 0x80); // Actual native Start exit, never a supplied scene result.
        for (int n = 0; n < 24; n++) step(loop, 0);
        assertEquals(SonicCdGameModule.OriginalWarpPhase.WARP_RETURNED, module.originalWarpPhase());
        step(loop, 0);
        assertEquals(SonicCdGameModule.OriginalWarpPhase.WARP_IPX_TAIL, module.originalWarpPhase());
        step(loop, 0);
        assertEquals(SonicCdGameModule.OriginalWarpPhase.DESTINATION_SELECTED, module.originalWarpPhase());
    }

    private static Arrival arriveReady(GameLoop loop, SonicCdGameModule module,
                                       CdLevelLoadingSource past) throws Exception {
        Cut marked = null; int markedAt = -1;
        for (int service = 1; service <= ARRIVAL_BOUND; service++) {
            // A typed missing-owner exception propagates. It cannot satisfy this positive requirement.
            step(loop, 0);
            var frame = module.originalR11EntryFrame();
            if (!frame.stageSource().stage().equals(CdStagePayload.R11B.stage())) continue;
            assertSame(past.playerSource(), frame.stageSource());
            assertEquals(0, frame.environment().movement().gates().zoneAct());
            int rawPeriod = frame.environment().movement().gates().timeZone();
            assertTrue(rawPeriod == 0x80 || rawPeriod == 0, "Only the actual marked/normalized Past period");
            if (marked == null && rawPeriod == 0x80) { marked = cut(); markedAt = service; }
            if (module.originalR11LoadingImage().phase() == SonicCdR31aLoadingVisit.Phase.READY
                    && module.originalR11LoadingPrefix().main().levelStartedByte() == 1) {
                assertNotNull(marked, "An observable actual marked loading service must precede Main315 clear");
                assertTrue(service > markedAt);
                assertReady(module, past);
                return new Arrival(marked, service - markedAt);
            }
        }
        fail("Genuine R11B loading did not return Ready within " + ARRIVAL_BOUND + " services");
        throw new AssertionError("Unreachable");
    }

    private static void assertReady(SonicCdGameModule module, CdLevelLoadingSource past) throws Exception {
        var frame = module.originalR11EntryFrame();
        assertFalse(frame.closed());
        assertEquals(CdStagePayload.R11B.stage(), frame.stageSource().stage());
        assertSame(past.playerSource(), frame.stageSource());
        assertEquals(0, frame.environment().movement().gates().zoneAct());
        assertEquals(0, frame.environment().movement().gates().timeZone(), "Native Main315 has now cleared bit7");
        assertEquals(SonicCdR31aLoadingVisit.Phase.READY, module.originalR11LoadingImage().phase());
        assertEquals(1, module.originalR11LoadingPrefix().main().levelStartedByte());
        assertEquals(0, frame.environment().globals().levelFrames(), "No old/early ordinary service");
        assertSceneOwnership(module, past);
    }

    private static void assertFirstMain(SonicCdGameModule module, CdLevelLoadingSource past) throws Exception {
        var frame = module.originalR11EntryFrame();
        assertFalse(frame.closed());
        assertSame(past.playerSource(), frame.stageSource());
        assertEquals(0, frame.environment().movement().gates().zoneAct());
        assertEquals(0, frame.environment().movement().gates().timeZone());
        assertEquals(1, frame.environment().globals().levelFrames());
        assertEquals(1, frame.state().header().slotId());
        assertTrue(frame.state().motion().routine() == 2 || frame.state().motion().routine() == 4,
                "The first returned destination Main must leave the primary alive");
        assertTrue(module.originalR11WorldImage().isPresent());
        assertSceneOwnership(module, past);
    }

    private static void assertSceneOwnership(SonicCdGameModule module,
                                             CdLevelLoadingSource past) throws Exception {
        var level = assertInstanceOf(SonicCdLevel.class,
                SessionManager.getCurrentGameplayMode().getLevelManager().getCurrentLevel());
        assertSame(past, level.loadingSource());
        assertSame(past.initialCentre(), level.initialCentre());
        assertSame(past.placements(), level.nativePlacements());
        // Three existing, pinned fields only: read-only source identity observation, not a permit.
        var world = assertInstanceOf(CdWorld.class, field(module, "r11World"));
        Object binding = field(world, "binding");
        assertEquals("com.openggf.game.soniccd.CdWorld$R11", binding.getClass().getName(),
                "The destination uses the existing common ordinary World strategy");
        assertSame(past.standardPastObjectSource().mappings(), field(binding, "mappings"));
    }

    private static Cut cut() throws Exception {
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse();
        return new Cut(full, course, values(full), values(course));
    }
    private static void restore(Cut cut, boolean course) {
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        if (course) registry.restoreCourse(cut.course()); else registry.restore(cut.full());
    }
    private static void assertCut(Cut cut, boolean course) throws Exception {
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        assertComplete(course ? cut.courseValues() : cut.fullValues(),
                course ? registry.captureCourse() : registry.capture());
    }

    /** Reuse Root's closed exact-field projector and CoW epoch metadata comparison.
     * New opaque Snapshot/Image types fail below until their complete contract is pinned.
     * Records/containers recurse completely; existing immutable value types retain equals.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> values(CompositeSnapshot snapshot) throws Exception {
        Method reader = TestCdOriginalSelectedWarpTimerPixels.class.getDeclaredMethod("allValues", CompositeSnapshot.class);
        reader.setAccessible(true);
        var result = (Map<String, Object>) reader.invoke(null, snapshot);
        rejectOpaqueSnapshots(result, "snapshot", 0);
        return result;
    }
    private static void assertComplete(Map<String, Object> expected, CompositeSnapshot actual) throws Exception {
        values(actual); // Fail closed on any newly introduced opaque snapshot shape.
        Method compare = TestCdOriginalSelectedWarpTimerPixels.class.getDeclaredMethod(
                "assertAllValues", Map.class, CompositeSnapshot.class);
        compare.setAccessible(true);
        try { compare.invoke(null, expected, actual); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof AssertionError assertion) throw assertion;
            throw failure;
        }
    }
    @Test
    void opaqueSnapshotCarriersCannotBypassClosedCaptureGuard() {
        final class UnmappedSnapshot { int gameplayValue = 1; }
        var snapshot = new UnmappedSnapshot();
        for (Object carrier : java.util.List.of(
                java.util.Map.of("bag", java.util.Set.of(snapshot)),
                java.util.Map.of(snapshot, 1),
                java.util.Map.of("bag", new java.util.ArrayDeque<>(java.util.List.of(snapshot))),
                java.util.List.of(java.util.Optional.of(java.util.Map.of("nested", java.util.Set.of(snapshot)))))) {
            assertThrows(AssertionError.class, () -> rejectOpaqueSnapshots(carrier, "constructed-carrier", 0));
            snapshot.gameplayValue++;
            assertThrows(AssertionError.class, () -> rejectOpaqueSnapshots(carrier, "mutated-same-carrier", 0));
        }
        assertDoesNotThrow(() -> rejectOpaqueSnapshots(java.util.Map.of("known", java.util.Set.of("scalar", 7)), "scalars", 0));
    }

    private static void rejectOpaqueSnapshots(Object value, String path, int depth) {
        if (value == null) return;
        assertTrue(depth <= 64, "Captured graph depth at " + path);
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                rejectOpaqueSnapshots(entry.getKey(), path + "{key}", depth + 1);
                rejectOpaqueSnapshots(entry.getValue(), path + "{value}", depth + 1);
            }
        } else if (value instanceof List<?> list) {
            for (int n = 0; n < list.size(); n++) rejectOpaqueSnapshots(list.get(n), path + "[" + n + "]", depth + 1);
        } else if (value instanceof java.util.Collection<?> collection) {
            int n = 0;
            for (Object item : collection) rejectOpaqueSnapshots(item, path + "[collection:" + n++ + "]", depth + 1);
        } else if (value instanceof Optional<?> option) {
            option.ifPresent(item -> rejectOpaqueSnapshots(item, path + ".value", depth + 1));
        } else if (value instanceof Object[] array) {
            for (int n = 0; n < array.length; n++) rejectOpaqueSnapshots(array[n], path + "[" + n + "]", depth + 1);
        } else {
            var type = value.getClass();
            String simple = type.getSimpleName();
            assertFalse(simple.contains("Snapshot") || simple.endsWith("Image"),
                    "Unprojected snapshot value at " + path + ": " + type.getName());
            // The existing immutable authority ticket intentionally retains identity. Every
            // other Module-owned capture value must have been projected as a complete record
            // or one of the pinned exact-field images by the shared helper above.
            if (!type.isEnum() && type.getName().startsWith("com.openggf.game.soniccd.SonicCdGameModule$"))
                assertEquals("com.openggf.game.soniccd.SonicCdGameModule$OriginalWarpAuthority", type.getName(),
                        "Unprojected Module capture value at " + path);
        }
    }

    private static RgbaImage drawClosed(SonicCdGameModule module) throws Exception {
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var level = assertInstanceOf(com.openggf.level.AbstractLevel.class,
                SessionManager.getCurrentGameplayMode().getLevelManager().getCurrentLevel());
        var before = values(registry.capture()); long epoch = level.currentEpoch();
        var first = drawFresh(module, 0xFF13C75D);
        assertEquals(epoch, level.currentEpoch(), "Draw cannot advance a level CoW boundary");
        assertComplete(before, registry.capture());
        epoch = level.currentEpoch();
        var repeated = drawFresh(module, 0xFFDC3B91);
        assertEquals(epoch, level.currentEpoch(), "Repeated draw cannot advance a level CoW boundary");
        assertComplete(before, registry.capture());
        assertPixels(first, repeated);
        return first;
    }
    private static RgbaImage drawFresh(SonicCdGameModule module, int sentinel) {
        org.lwjgl.opengl.GL11.glViewport(0, 0, 320, 224);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        org.lwjgl.opengl.GL11.glColorMask(true, true, true, true);
        org.lwjgl.opengl.GL11.glClearColor(((sentinel >>> 16) & 255) / 255f,
                ((sentinel >>> 8) & 255) / 255f, (sentinel & 255) / 255f, 1);
        org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
        org.lwjgl.opengl.GL11.glFinish();
        var cleared = ScreenshotCapture.captureFramebuffer(320, 224);
        assertTrue(Arrays.stream(cleared.pixels()).allMatch(pixel -> pixel == sentinel));
        assertEquals(org.lwjgl.opengl.GL11.GL_NO_ERROR, org.lwjgl.opengl.GL11.glGetError());
        module.gameplayFrameController().drawOverlay();
        org.lwjgl.opengl.GL11.glFinish();
        var result = ScreenshotCapture.captureFramebuffer(320, 224);
        assertEquals(org.lwjgl.opengl.GL11.GL_NO_ERROR, org.lwjgl.opengl.GL11.glGetError());
        assertEquals(320, result.width()); assertEquals(224, result.height());
        assertEquals(320 * 224, result.pixels().length);
        assertFalse(Arrays.equals(cleared.pixels(), result.pixels()), "A skipped renderer cannot supply ENGINE output");
        return result;
    }
    private static void assertPixels(RgbaImage expected, RgbaImage actual) {
        assertEquals(expected.width(), actual.width()); assertEquals(expected.height(), actual.height());
        assertArrayEquals(expected.pixels(), actual.pixels());
    }

    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static Object copyRecord(Object original, String fieldName, Object replacement) throws Exception {
        var components = original.getClass().getRecordComponents();
        var types = new Class<?>[components.length]; var values = new Object[components.length]; boolean found = false;
        for (int n = 0; n < components.length; n++) {
            var read = components[n].getAccessor(); read.setAccessible(true); types[n] = components[n].getType();
            if (components[n].getName().equals(fieldName)) { values[n] = replacement; found = true; }
            else values[n] = read.invoke(original);
        }
        assertTrue(found, "Exact current record field required");
        var constructor = original.getClass().getDeclaredConstructor(types); constructor.setAccessible(true);
        return constructor.newInstance(values);
    }

    private static Path validatedCaptureDirectory() throws java.io.IOException {
        if (!CAPTURE_ROOT.isAbsolute() || !CAPTURE_ROOT.equals(CAPTURE_ROOT.normalize())
                || !CAPTURE_ROOT.equals(CAPTURE_ROOT.toRealPath())
                || !Files.isDirectory(CAPTURE_ROOT, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalStateException("Canonical ENGINE capture root required");
        if (!CAPTURE_DIRECTORY.isAbsolute() || !CAPTURE_DIRECTORY.equals(CAPTURE_DIRECTORY.normalize())
                || !CAPTURE_DIRECTORY.getParent().equals(CAPTURE_ROOT))
            throw new IllegalStateException("Fixed Past capture directory required");
        if (!Files.exists(CAPTURE_DIRECTORY, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(CAPTURE_DIRECTORY);
        if (!Files.isDirectory(CAPTURE_DIRECTORY, LinkOption.NOFOLLOW_LINKS)
                || !CAPTURE_DIRECTORY.equals(CAPTURE_DIRECTORY.toRealPath()))
            throw new IllegalStateException("Aliased Past capture directory");
        return CAPTURE_DIRECTORY;
    }
    private static void exportOnce(Path setup, RgbaImage pixels) throws java.io.IOException {
        Path actual = validatedCaptureDirectory();
        if (!actual.equals(setup)) throw new IllegalStateException("Changed canonical Past capture directory");
        try (var rootDirectory = Files.newDirectoryStream(CAPTURE_ROOT)) {
            if (!(rootDirectory instanceof SecureDirectoryStream<Path> parent))
                throw new IllegalStateException("Capture requires an anchored secure directory stream");
            // Open the one literal child relative to the already opened root; a replaced
            // child symlink cannot redirect the writer after the earlier canonical check.
            try (var safe = parent.newDirectoryStream(CAPTURE_DIRECTORY.getFileName(), LinkOption.NOFOLLOW_LINKS)) {
                var options = Set.<OpenOption>of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                try (var channel = safe.newByteChannel(Path.of(CAPTURE_FILE), options); var out = Channels.newOutputStream(channel)) {
                    var image = new java.awt.image.BufferedImage(pixels.width(), pixels.height(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    image.setRGB(0, 0, pixels.width(), pixels.height(), pixels.pixels(), 0, pixels.width());
                    if (!javax.imageio.ImageIO.write(image, "png", out)) throw new java.io.IOException("PNG writer unavailable");
                }
            }
        }
    }
    private static AutoCloseable disposeWhileContextCurrent() {
        return () -> {
            Throwable failure = null;
            try { SessionManager.clear(); } catch (RuntimeException | Error original) { failure = original; }
            try { com.openggf.game.GameServices.graphics().cleanup(); }
            catch (RuntimeException | Error cleanup) {
                if (failure == null) failure = cleanup; else if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            if (failure instanceof RuntimeException original) throw original;
            if (failure instanceof Error original) throw original;
        };
    }
    /** Separate diagnostic, not the strict arrival/pixel/replay requirement above.
     * Only real returned B Ready and first Main authorize the following Right-only probe.
     * Local ordinals count public services AFTER that first Main; setup is not relabeled.
     */
    @Test
    void boundedDiagnosticPastRightAfterFirstOrdinaryReportsFirstStop() throws Exception {
        var source = TestCdOriginalBootPrefix.source();
        var attempt = SonicCdGameModule.prepareSelectedProfile(source, new SaveManager(root), 1,
                SonicCdGameModule.ProfileEntry.NEW_GAME, 0);
        var module = attempt.module().orElseThrow();
        UnsupportedOperationException acceptedBoundary = null;
        try (var boot = boot(); var cleanup = disposeWhileContextCurrent()) {
            var loop = boot.bootSonicCdSelectedProfile(source, attempt);
            reachActualPastRequest(loop, module);
            var destination = module.originalWarpDestination().orElseThrow();
            assertEquals(CdCommand.R11B, destination.command());
            var past = destination.loadingSource().orElseThrow();
            assertSame(source.initialProgramSource().orElseThrow().pastLoadingSource(), past);
            arriveReady(loop, module, past);
            assertReady(module, past);
            // All arrival assertions/calls are outside the accepted later-stop catch.
            // An unbound first ordinary family remains an ERROR, not a diagnostic endpoint.
            step(loop, 0);
            assertFirstMain(module, past);

            Cut firstMain = cut();
            Map.Entry<Cut, String> lastHealthy = Map.entry(firstMain,
                    pastDiagnosticMetadata(module, 0, firstMain));
            for (int additional = 1; additional <= 3000; additional++) {
                try {
                    step(loop, 8); // Public controller, held Right only; no replay/pose/flag inputs.
                } catch (UnsupportedOperationException originalBoundary) {
                    // A native stop with an unexpected cleanup failure is still a failed test.
                    if (originalBoundary.getSuppressed().length != 0) throw originalBoundary;
                    acceptedBoundary = originalBoundary;
                    // Only the original exception and already cached immutable metadata are read.
                    // The failing service may have retired the Frame/World; never inspect it here.
                    String message = String.valueOf(originalBoundary.getMessage());
                    String boundedMessage = message.replace('\n', ' ').replace('\r', ' ');
                    if (boundedMessage.length() > 1024) boundedMessage = boundedMessage.substring(0, 1024);
                    System.out.println("PAST_RIGHT_DIAGNOSTIC outcome=ORIGINAL_BOUNDARY"
                            + " additionalReturned=" + (additional - 1)
                            + " bPostReadyPublicReturned=" + additional
                            + " firstUnreturnedAdditional=" + additional
                            + " firstUnreturnedBPostReadyPublic=" + (additional + 1)
                            + " type=" + originalBoundary.getClass().getName()
                            + " messageCharacters=" + message.length()
                            + " messageTruncated=" + (message.length() > 1024)
                            + " message=" + boundedMessage
                            + " lastHealthy=" + lastHealthy.getValue());
                    return;
                }
                // Check the RETURNED primary death before any pending replay/bookkeeping.
                // This diagnostic does not restore or replay any input/capture at all.
                int returnedRoutine = module.originalR11EntryFrame().state().motion().routine();
                if (returnedRoutine >= 6) {
                    System.out.println("PAST_RIGHT_DIAGNOSTIC outcome=RETURNED_DEATH"
                            + " additionalReturned=" + additional
                            + " bPostReadyPublicReturned=" + (additional + 1)
                            + " firstUnreturnedAdditional=NONE firstUnreturnedBPostReadyPublic=NONE"
                            + " type=RETURNED_PRIMARY_DEATH message=primary-routine-at-least-six"
                            + " lastHealthy=" + lastHealthy.getValue()
                            + " returnedPrimaryRoutine=" + returnedRoutine);
                    return;
                }
                Cut returned = cut();
                String metadata = pastDiagnosticMetadata(module, additional, returned);
                // Keep complete full/course snapshots AND their closed projected values,
                // together with a detached, bounded printable physical-actor census.
                lastHealthy = Map.entry(returned, metadata);
            }
            System.out.println("PAST_RIGHT_DIAGNOSTIC outcome=BOUND_REACHED"
                    + " additionalReturned=3000 bPostReadyPublicReturned=3001"
                    + " firstUnreturnedAdditional=NONE firstUnreturnedBPostReadyPublic=NONE"
                    + " type=LOCAL_SERVICE_BOUND message=3000-additional-Right-services-returned"
                    + " lastHealthy=" + lastHealthy.getValue());
        } catch (Exception | Error cleanupFailure) {
            // A boundary accepted inside the loop remains the original evidence if teardown fails.
            // This handler reads no retired gameplay context and avoids self-suppression.
            if (acceptedBoundary != null && cleanupFailure != acceptedBoundary) {
                acceptedBoundary.addSuppressed(cleanupFailure);
                throw acceptedBoundary;
            }
            throw cleanupFailure;
        }
    }

    /** Read only a just-returned, current cut. Never called by a failure catch or cleanup. */
    private static String pastDiagnosticMetadata(SonicCdGameModule module, int additionalReturned,
                                                  Cut complete) throws Exception {
        var frame = module.originalR11EntryFrame();
        var actor = frame.state();
        var motion = actor.motion();
        var loading = module.originalR11LoadingImage();
        var prefix = module.originalR11LoadingPrefix();
        var placed = module.originalR11PlacementImage();
        assertEquals(31 * 64, loading.reserved().length());
        assertEquals(96, placed.pool().slots().size());
        int[] ids = new int[256];
        int active = 0;
        var actors = new StringBuilder("[");
        if (actor.header().slotId() != 0) {
            int id = actor.header().slotId(); ids[id]++; active++;
            actors.append("0:$").append(Integer.toHexString(id))
                    .append("/r").append(motion.routine())
                    .append("/x").append(motion.centreX16_16() >>> 16)
                    .append("/y").append(motion.centreY16_16() >>> 16);
        }
        for (int whole = 1; whole < 32; whole++) {
            int at = (whole - 1) * 64;
            int id = loading.reserved().unsignedByte(at);
            if (id == 0) continue;
            ids[id]++; active++;
            actors.append(";").append(whole).append(":$").append(Integer.toHexString(id))
                    .append("/r").append(loading.reserved().unsignedByte(at + 0x24))
                    .append("/x").append(loading.reserved().unsignedWord(at + 8))
                    .append("/y").append(loading.reserved().unsignedWord(at + 12));
        }
        for (int slot = 0; slot < 96; slot++) {
            var object = placed.pool().slots().get(slot);
            assertEquals(slot + 32, object.identity().wholeIndex());
            int id = object.id();
            if (id == 0) continue;
            ids[id]++; active++;
            actors.append(";").append(object.identity().wholeIndex())
                    .append(":$").append(Integer.toHexString(id))
                    .append("/r").append(object.routine())
                    .append("/x").append(object.centreX())
                    .append("/y").append(object.centreY())
                    .append("/sub$").append(Integer.toHexString(object.subtype()));
        }
        actors.append("]");
        var census = new StringBuilder("{");
        for (int id = 1; id < ids.length; id++) if (ids[id] != 0)
            census.append("$").append(Integer.toHexString(id)).append("=").append(ids[id]).append(";");
        census.append("}");
        return "{additionalReturned=" + additionalReturned
                + ",bPostReadyPublicReturned=" + (additionalReturned + 1)
                + ",stage=" + frame.stageSource().stage()
                + ",loadingPhase=" + loading.phase()
                + ",rawPeriod=" + frame.environment().movement().gates().timeZone()
                + ",nativeLevelFrames=" + frame.environment().globals().levelFrames()
                + ",vIntClock=" + frame.vIntClock()
                + ",levelStarted=" + prefix.main().levelStartedByte()
                + ",primaryRoutine=" + motion.routine()
                + ",primaryX16_16=" + Integer.toUnsignedString(motion.centreX16_16())
                + ",primaryY16_16=" + Integer.toUnsignedString(motion.centreY16_16())
                + ",primaryVelocity=" + motion.xVelocity() + ":" + motion.yVelocity()
                + ",primaryFlags=$" + Integer.toHexString(motion.flags())
                + ",ringGlobals=" + loading.globals().ring()
                + ",fullComponents=" + complete.fullValues().size()
                + ",courseComponents=" + complete.courseValues().size()
                + ",physicalSlots=128,active=" + active + ",inactive=" + (128 - active)
                + ",ids=" + census + ",actors=" + actors + "}";
    }

}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
