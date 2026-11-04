package com.openggf.game.soniccd;

import com.openggf.audio.NullAudioBackend;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.RetainedPlaneSceneRenderer;
import com.openggf.graphics.SpritePresentation;
import org.lwjgl.BufferUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.objects.CdTitleCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Original-USA controller-only programme handoff; no actor/global/pool/camera/art seeds.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Level/Main.asm:85-407;
 * Title Card/Main.asm:39-193. Call bounds select fixture inputs only.
 * Real EGL pixels, selected-primary omission and owned atlas retirement are checked.
 * This is OpenGGF output, not a native-image or regional-parity claim.
 */
@Isolated
class TestSonicCdAct2ControllerRoute {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualResultsProgrammeReturnLoadsAct2AndRunsItsNativeFirstWalkWithBothReplayScopes() throws Exception {
        byte[] imageForExport;
        try (var egl = TestSonicCdPastResultsRoute.Egl.open()) {
        assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
        var old = ready(); var h = old.harness(); var module = h.module();
        for (int call = old.returned() + 1; call <= 10419; call++) step(h, controller(call));
        assertEquals(SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED, module.pastLoadState().phase());
        assertFalse(old.frame().closed());
        var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var oldSource = old.frame().stageSource();
        int lives = module.pastLoadState().ring().lives();
        var heldRight = controller(10419);
        int firstMain = -1;
        for (int offset = 1; offset <= 256; offset++) {
            int call = 10419 + offset;
            boolean canReplay = module.act2LoadingSelected();
            var registry = h.mode().getRewindRegistry();
            var full = canReplay ? registry.capture() : null;
            var course = canReplay ? registry.captureCourse() : null;
            var before = canReplay ? captureNative(h) : null;
            step(h, heldRight);
            assertTrue(module.act2LoadingSelected(), "The actual programme return must select its next FULL load");
            assertSame(world, h.mode().getWorldSession());
            assertEquals(generation + 1, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            var frame = module.requirePlayerFrame();
            assertNotSame(old.frame(), frame); assertNotSame(oldSource, frame.stageSource());
            assertTrue(old.frame().closed()); assertFalse(frame.closed());
            assertEquals(CdStageKey.parse("R12A"), frame.stageSource().stage());
            assertEquals(1, frame.environment().movement().gates().zoneAct());
            assertEquals(1, frame.environment().movement().gates().timeZone());
            assertEquals(lives, module.act2LoadState().ring().lives());
            assertEquals("6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7",
                    module.act2LoadState().assets().identity().mmdSha256());
            if (canReplay) {
                var after = captureNative(h);
                registry.restore(full); assertNativeCapture(before, h);
                step(h, heldRight); assertNativeCapture(after, h);
                registry.restoreCourse(course); assertNativeCapture(before, h);
                step(h, heldRight); assertNativeCapture(after, h);
                assertSame(frame, module.requirePlayerFrame()); assertSame(world, h.mode().getWorldSession());
                assertEquals(generation + 1, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            }
            if (module.act2LoadState().calls().stream().anyMatch(c -> c.name().equals("ORDINARY_MAIN_RETURN"))) {
                firstMain = call;
                break;
            }
        }
        assertTrue(firstMain > 10419, "A real complete ordinary walk must return within the finite fixture bound");
        var state = module.act2LoadState();
        assertEquals("BEGIN_LEVEL", state.phase().name());
        assertEquals(1, module.requirePlayerFrame().environment().globals().levelFrames());
        assertEquals(1, module.requirePlayerFrame().state().header().slotId());
        assertEquals(CdTitleCard.OBJECT_ID, state.reservedObjects().unsignedByte(3 * 64));
        assertEquals(2, state.reservedObjects().unsignedByte(3 * 64 + 0x24));
        assertEquals(8, state.placement().pool().slots().stream().filter(s -> s.id() == CdTitleCard.OBJECT_ID).count());
        for (int whole = 38; whole <= 45; whole++) {
            var child = state.placement().pool().slots().get(whole - 32);
            assertEquals(CdTitleCard.OBJECT_ID, child.id(), "Title parent4 allocates before the six actual placed bodies");
            assertEquals(4, child.routine(), "Later physical children perform their first slide in the same walk");
        }
        var frame = module.requirePlayerFrame(); var source = frame.stageSource();
        for (int next = 1; next <= 3; next++) {
            var registry = h.mode().getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
            var before = captureNative(h); step(h, heldRight); var after = captureNative(h);
            registry.restore(full); assertNativeCapture(before, h); step(h, heldRight); assertNativeCapture(after, h);
            registry.restoreCourse(course); assertNativeCapture(before, h); step(h, heldRight); assertNativeCapture(after, h);
            assertSame(frame, module.requirePlayerFrame()); assertSame(source, frame.stageSource()); assertFalse(frame.closed());
            assertEquals(1 + next, frame.environment().globals().levelFrames());
        }
        var selected = module.act2SelectedDisplay();
        var cpu = selected.inputs().selectedCpu().orElseThrow();
        assertTrue(cpu.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 0),
                "The real following V-int selects the primary image from a completed ordinary walk");
        var scene = assertInstanceOf(SonicCdPastDisplay.Ready.class, selected.projection(),
                "Selected actual Act2 art at firstMain=" + firstMain + ": " + selected.projection()).scene();
        var unchanged = captureNative(h); var renderRegistry = h.mode().getRewindRegistry();
        var renderCapture = renderRegistry.capture(); var handler = module.getScrollHandlerProvider().getHandler(0);
        var renderScroll = handler.captureRewindState(); var callbacks = new java.util.concurrent.atomic.AtomicInteger();
        GameServices.graphics().registerCommand((x, y, width, height) -> {
            callbacks.incrementAndGet();
            assertThrows(IllegalStateException.class, renderRegistry::capture);
            assertThrows(IllegalStateException.class, () -> renderRegistry.restore(renderCapture));
            assertThrows(IllegalStateException.class, handler::captureRewindState);
            assertThrows(IllegalStateException.class, () -> handler.restoreRewindState(renderScroll));
            assertThrows(IllegalStateException.class, () -> module.serviceAct2Loading(8));
            assertThrows(IllegalStateException.class, () -> module.gameplayFrameController().drawOverlay());
        });
        var actual = draw(h); imageForExport = actual.clone();
        assertEquals(1, callbacks.get()); assertFalse(frame.closed());
        assertArrayEquals(actual, draw(h)); assertNativeCapture(unchanged, h);
        var presentation = assertInstanceOf(SonicCdPlayerPresentation.class, actualField(module, "playerPresentation"));
        var binding = actualField(presentation, "act2RenderBinding");
        var renderer = assertInstanceOf(RetainedPlaneSceneRenderer.class, actualField(binding, "renderer"));
        var camera = h.mode().getCamera(); clearPixels();
        renderer.draw(TestSonicCdPastResultsRoute.omitWhole(scene, cpu, 0), camera.getX(), camera.getY(), module::validateAct2LoadingCut);
        assertFalse(Arrays.equals(actual, pixels()), "Actual selected primary must contribute visible Act2 pixels");
        assertNativeCapture(unchanged, h); assertArrayEquals(actual, draw(h)); assertNativeCapture(unchanged, h);
        var atlas = GameServices.graphics().getPatternAtlas(); var installed = scene.nativePatterns().keySet();
        assertFalse(installed.isEmpty());
        for (int id : installed) assertNotNull(atlas.getEntry(PatternAtlasRange.OBJECTS.base() + id));
        // GPU-only other-producer contrast, never gameplay art or a substitute source.
        atlas.registerRange(PatternAtlasRange.HUD); int other = PatternAtlasRange.HUD.base() + 19;
        if (atlas.getEntry(other) == null) GameServices.graphics().cachePatternTexture(new com.openggf.level.Pattern(), other);
        var unrelated = atlas.getEntry(other); var sample = SpritePresentation.patternSample(GameServices.graphics(), other);
        atlas.beginBatch();
        try {
            assertThrows(IllegalStateException.class, presentation::unbind);
            assertSame(binding, actualField(presentation, "act2RenderBinding"),
                    "Rejected close retains its actual renderer ledger for cleanup retry");
        } finally { atlas.endBatch(); }
        SessionManager.closeGameplaySession();
        for (int id : installed) {
            assertNull(atlas.getEntry(PatternAtlasRange.OBJECTS.base() + id));
            assertNull(SpritePresentation.patternSample(GameServices.graphics(), PatternAtlasRange.OBJECTS.base() + id));
        }
        assertSame(unrelated, atlas.getEntry(other)); assertEquals(sample, SpritePresentation.patternSample(GameServices.graphics(), other));
        System.out.printf("Controller-only Act2: first native Main returned%d; three further returns/full-course replay; actual primary omission pixels and %d selected uploads retired.%n", firstMain, installed.size());
        } finally { fixture.cleanup(); }
        writeOptionalCapture(imageForExport);
    }

    @Test void actualTitleSlideOutServicesItsQueuedArtThenReleasesTheSamePlayerToOrdinaryRightMovement() throws Exception {
        var old = ready(); var h = old.harness(); var module = h.module();
        for (int call = old.returned() + 1; call <= 10419; call++) step(h, controller(call));
        var right = controller(10419);
        for (int load = 0; load < 256; load++) {
            step(h, right);
            if (module.act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN)) break;
        }
        var frame = module.requirePlayerFrame(); var source = frame.stageSource();
        assertEquals(1, frame.environment().globals().levelFrames());
        assertEquals(1, frame.environment().controls().controlLocked());
        int lockedX = frame.state().motion().centreX16_16();
        boolean sawSlideOut = false, sawQueuedPlc = false, replayedPlc = false, released = false;
        int callsAfterMain = 0;
        for (; callsAfterMain < 512; callsAfterMain++) {
            var before = module.act2LoadState();
            int oldRoutine = before.reservedObjects().unsignedByte(3 * 64 + 0x24);
            long oldHead = before.section().queue().headSource();
            try { step(h, right); }
            catch (Exception | Error failure) {
                System.err.printf("Act2 Title stopped after %d completed ordinary calls; last closed parent routine=%d timer=%d Y=%d PLChead=%08X remaining=%d lock=%d actor=%s; retained actor=%s env=%s%n",
                        callsAfterMain + 1, oldRoutine, before.reservedObjects().unsignedByte(3 * 64 + 0x1E),
                        before.reservedObjects().unsignedWord(3 * 64 + 0xA), oldHead, before.section().remainingTilesWord(),
                        before.shared().environment().controls().controlLocked(), before.actor(), frame.state(), frame.environment());
                throw failure;
            }
            var now = module.act2LoadState();
            int routine = now.reservedObjects().unsignedByte(3 * 64 + 0x24);
            assertSame(frame, module.requirePlayerFrame()); assertSame(source, frame.stageSource()); assertFalse(frame.closed());
            assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN, now.calls().getLast());
            if (routine == 6 && !sawSlideOut) { sawSlideOut = true; replayNextRight(h, right); }
            if (routine == 10) {
                sawQueuedPlc |= now.section().queue().headSource() != 0;
                if (!replayedPlc && oldHead != 0) { replayedPlc = true; replayNextRight(h, right); }
            }
            if (frame.environment().controls().controlLocked() == 0) {
                assertEquals(0, now.reservedObjects().unsignedByte(3 * 64));
                assertEquals(0, now.section().queue().headSource());
                replayNextRight(h, right); released = true; break;
            }
            assertEquals(lockedX, frame.state().motion().centreX16_16(), "Held Right cannot move Sonic before the native Title lock clear");
        }
        assertTrue(sawSlideOut); assertTrue(sawQueuedPlc); assertTrue(replayedPlc);
        assertTrue(released, "The finite Title/PLC2 source must release controls within 512 ordinary calls");
        int releaseX = frame.state().motion().centreX16_16();
        for (int walk = 0; walk < 64; walk++) step(h, right);
        assertTrue(frame.state().motion().centreX16_16() > releaseX, "Actual unlocked Right must move the native player");
        assertEquals(0, frame.environment().controls().controlLocked());
        replayNextRight(h, right);
        System.out.printf("Controller-only Act2 Title: queued PLC observed, same Frame controls released after %d ordinary calls; 64 Right calls and full/course replay returned; X=%d.%n",
                callsAfterMain + 1, frame.state().motion().centreX16_16() >>> 16);
    }

    @Test void actualUnlockedAct2RightRouteReturnsThroughTheLastMeasuredClosedCutWithFullAndCourseReplay() throws Exception {
        var old = ready(); var h = old.harness(); var module = h.module();
        var right = controller(10419);
        for (int call = old.returned() + 1; call <= 10762; call++) {
            step(h, call <= 10419 ? controller(call) : right);
            if (call == 10718) replayNextRight(h, right); // Actual fresh TimePost8/9 Init at10719, no net extra frame.
            if (call == 10719) {
                assertEquals(2, module.act2LoadState().calls().stream().filter(c -> c == SonicCdGameModule.Act2LoadCall.TIME_POST_BODY).count());
                for (int whole = 38; whole <= 39; whole++) {
                    var post = module.act2LoadState().placement().pool().slots().get(whole - 32);
                    assertEquals(0x19, post.id()); assertEquals(2, post.routine());
                    assertEquals(8 + whole - 38, post.subtype());
                }
            }
        }
        var frame = module.requirePlayerFrame(); var source = frame.stageSource();
        assertTrue(old.frame().closed()); assertFalse(frame.closed());
        assertEquals(CdStageKey.parse("R12A"), source.stage());
        assertEquals(0, frame.environment().controls().controlLocked());
        assertEquals(0, module.act2LoadState().reservedObjects().unsignedByte(3 * 64));
        assertEquals(0, module.act2LoadState().section().queue().headSource());
        assertTrue(frame.state().motion().centreX16_16() > (80 << 16), "Ordinary native Right moved beyond its native entry");
        replayNextRight(h, right);
        step(h, right); // Positive endpoint10763; later unowned dispatch is not a permanent success condition.
        assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN, module.act2LoadState().calls().getLast());
        assertSame(frame, module.requirePlayerFrame()); assertSame(source, frame.stageSource()); assertFalse(frame.closed());
        assertInstanceOf(SonicCdPastDisplay.Ready.class, module.act2SelectedDisplay().projection());
        var before = captureNative(h); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse();
        registry.restore(full); assertNativeCapture(before, h);
        registry.restoreCourse(course); assertNativeCapture(before, h);
        System.out.printf("Controller-only Act2 Right returned10763; source-bound full/course10718->10719 and10762->10763 replay, natural TimePosts8/9, controls clear and selected picture Ready; X=%d.%n",
                frame.state().motion().centreX16_16() >>> 16);
    }

    private static void replayNextRight(Harness h, LogicalInputSnapshot right) throws Exception {
        var registry = h.mode().getRewindRegistry(); var before = captureNative(h);
        var full = registry.capture(); var course = registry.captureCourse();
        step(h, right); var after = captureNative(h);
        registry.restore(full); assertNativeCapture(before, h); step(h, right); assertNativeCapture(after, h);
        registry.restoreCourse(course); assertNativeCapture(before, h); step(h, right); assertNativeCapture(after, h);
        registry.restore(full); assertNativeCapture(before, h);
    }

    private static byte[] draw(Harness h) { clearPixels(); h.module().gameplayFrameController().drawOverlay(); return pixels(); }
    private static void clearPixels() {
        glViewport(0, 0, 320, 224); glClearColor(1, 0, 1, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }
    private static byte[] pixels() {
        glFinish(); var bytes = BufferUtils.createByteBuffer(320 * 224 * 4);
        glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, bytes); assertEquals(GL_NO_ERROR, glGetError());
        byte[] result = new byte[bytes.capacity()]; bytes.get(result); return result;
    }
    /** Called after session, module, graphics, context and fixture cleanup; output is never a runtime input. */
    private static void writeOptionalCapture(byte[] rgba) throws Exception {
        String output = System.getenv("SONICCD_ACT2_ENTRY_OUTPUT"); if (output == null || output.isBlank()) return;
        var directory = Path.of(output).toAbsolutePath().normalize();
        var owned = Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD", "captures", "engine").toAbsolutePath().normalize();
        assertTrue(directory.startsWith(owned) && !directory.equals(owned)); Files.createDirectories(directory);
        var image = new java.awt.image.BufferedImage(320, 224, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
            int at = ((223 - y) * 320 + x) * 4;
            int argb = (rgba[at + 3] & 255) << 24 | (rgba[at] & 255) << 16 | (rgba[at + 1] & 255) << 8 | rgba[at + 2] & 255;
            image.setRGB(x, y, argb);
        }
        if (!javax.imageio.ImageIO.write(image, "png", directory.resolve("actual-act2-entry.png").toFile())) throw new java.io.IOException("PNG writer unavailable");
    }

    private static LogicalInputSnapshot controller(int call) {
        if (call < 1 || call > 10419) throw new IllegalArgumentException("Fixed original recipe ends at programme return10419");
        if (call <= 8276) return route(call);
        int directions = 8, actions = call <= 8308 ? 2 : 0;
        if (call >= 9001 && call <= 9203) directions = 4;
        else if (call >= 9206 && call <= 9217) directions = 0;
        else if (call >= 9218 && call <= 9263) { directions = 2; actions = call == 9219 ? 2 : 0; }
        else if (call >= 9336 && call <= 9367) actions = 2;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions, 0, actions, 0, false, false),
                PlayerInputState.neutral());
    }
}
