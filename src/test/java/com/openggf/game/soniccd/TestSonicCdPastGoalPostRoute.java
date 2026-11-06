package com.openggf.game.soniccd;

import com.openggf.audio.NullAudioBackend;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.CdGoalPost;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerMovement;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/**
 * Actual cold-program controller input only, through completed ordinary call9598. No actor,
 * global, object, camera, terrain, art, queue or clock is seeded. The first observed natural
 * GoalPost routine2->4 supplies its incoming camera and actual prior fields for comparison.
 * Native N1ecc5f3a Level End/Main.asm:568-706 and Object Spawner.asm:385-446 own the goal,
 * draw/despawn and five scalar stores; LoadPLC $12 is decimal18, not camera list12.
 * Full/course9597->9598 compare the complete existing native capture. Real EGL draws the
 * engine's selected image after returned9598 (including its normal prior-CPU selection).
 * This is bounded engine route/pixel evidence, not native-image parity, Signpost, BigRing,
 * Results or act-completion evidence. The positive case never forwards call9599.
 * Root observed this positive route/EGL case passing on 2026-11-04; broader limits above remain.
 */
@Isolated
class TestSonicCdPastGoalPostRoute {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualControllerReachesNaturalGoalReturns9598ReplaysBothScopesAndDrawsSelectedPixels() throws Exception {
        byte[] imageForExport;
        try (var egl = Egl.open()) {
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            var p = ready(); var h = p.harness(); var frame = p.frame();
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            var world = h.mode().getWorldSession();
            long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
            var source = frame.stageSource(); var body = actualField(p.visit(), "goalPost");
            assertInstanceOf(CdGoalPost.class, body);
            var presentation = assertInstanceOf(SonicCdPlayerPresentation.class, actualField(h.module(), "playerPresentation"));
            var row = p.assets().placements().records().get(490);
            assertEquals(0x16, row.objectId()); assertEquals(9984, row.centreX()); assertEquals(740, row.centreY());

            GoalTransition reached = null;
            for (int call = p.returned() + 1; call <= 9597; call++) {
                var beforeGoal = p.placement().pool().state(32);
                var beforeEnvironment = frame.environment();
                var beforeScroll = presentation.capturePastScroll();
                var beforeGlobals = loadingGlobals(p);
                step(h, controller(call)); healthy(p, world, generation, source, body);
                var afterGoal = p.placement().pool().state(32);
                if (isNaturalGoal(beforeGoal) && beforeGoal.routine() == 2
                        && isNaturalGoal(afterGoal) && afterGoal.routine() == 4) {
                    assertNull(reached, "The natural GoalPost must cross its routine2->4 edge once");
                    int camera = beforeScroll.state().cameraX();
                    assertGoalReached(p, beforeGoal, afterGoal, beforeEnvironment, beforeGlobals, camera);
                    reached = new GoalTransition(call, camera, afterGoal);
                    var oldContact = (CdPlayerBody.ContactGlobals) actualField(beforeGlobals, "contact");
                    System.out.printf("Past natural GoalPost completed call%d: incoming cameraX=%d, prior left=%d, "
                                    + "destLeft=%d, warpTimer=%d, warpDir=%d, contactWarp=%d; five stores checked.%n",
                            call, camera, beforeEnvironment.movement().bounds().left(), beforeScroll.state().destLeftBound(),
                            beforeEnvironment.globals().timeWarpTimer(), beforeEnvironment.movement().gates().timeWarpDirection(),
                            oldContact.timeWarp());
                }
            }
            assertNotNull(reached, "Require the actual returned routine2->4 edge; no assumed camera or timer defaults");
            var registry = h.mode().getRewindRegistry();
            var full = registry.capture(); var course = registry.captureCourse(); var before9598 = captureNative(h);
            var selectedBefore = h.module().pastSelectedDisplay();
            assertCompletedGoal(p, reached);
            step(h, controller(9598)); healthy(p, world, generation, source, body);
            assertCompletedGoal(p, reached); var after9598 = captureNative(h); var selected = h.module().pastSelectedDisplay();
            registry.restore(full); assertNativeCapture(before9598, h); assertSame(selectedBefore, h.module().pastSelectedDisplay());
            healthy(p, world, generation, source, body);
            step(h, controller(9598)); healthy(p, world, generation, source, body); assertNativeCapture(after9598, h);
            assertEquals(selected, h.module().pastSelectedDisplay()); assertCompletedGoal(p, reached);
            registry.restoreCourse(course); assertNativeCapture(before9598, h); assertSame(selectedBefore, h.module().pastSelectedDisplay());
            healthy(p, world, generation, source, body);
            step(h, controller(9598)); healthy(p, world, generation, source, body); assertNativeCapture(after9598, h);
            assertEquals(selected, h.module().pastSelectedDisplay()); assertCompletedGoal(p, reached);

            selected = h.module().pastSelectedDisplay(); // Draw the live selection after the final course forward.
            var scene = readyScene(selected); var selectedCpu = selected.inputs().selectedCpu().orElseThrow();
            assertTrue(selectedCpu.provenance().stream().anyMatch(v -> v.slotRef().wholeIndex() == 32),
                    "The selected genuine prior CPU must include the natural GoalPost at this fixed cut");
            var actual = draw(h); assertNativeCapture(after9598, h); imageForExport = actual.clone();
            assertArrayEquals(actual, draw(h)); assertNativeCapture(after9598, h);
            var binding = actualField(presentation, "pastRenderBinding");
            var renderer = (RetainedPlaneSceneRenderer) actualField(binding, "renderer"); var camera = h.mode().getCamera();
            clear(); renderer.draw(omitWhole(scene, selectedCpu, 0), camera.getX(), camera.getY(), h.module()::validatePastLoadingCut);
            assertFalse(Arrays.equals(actual, pixels()), "Genuine selected primary contributes visible engine pixels at returned9598");
            assertNativeCapture(after9598, h);
            clear(); renderer.draw(omitWhole(scene, selectedCpu, 32), camera.getX(), camera.getY(), h.module()::validatePastLoadingCut);
            assertFalse(Arrays.equals(actual, pixels()), "Genuine selected GoalPost contributes visible engine pixels at returned9598");
            assertNativeCapture(after9598, h);
            assertArrayEquals(actual, draw(h)); assertNativeCapture(after9598, h);

            var atlas = egl.graphics.getPatternAtlas(); var selectedIds = scene.nativePatterns().keySet();
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
        } finally {
            fixture.cleanup(); // Optional export follows session, graphics, context AND fixture cleanup.
        }
        writeOptionalCapture(imageForExport);
    }

    private record GoalTransition(int call, int incomingCamera, CdObjectState doneImage) { }

    private static void assertGoalReached(Prepared p, CdObjectState before, CdObjectState after,
                                         CdPlayerBody.Environment oldEnvironment, Object oldGlobals, int incomingCamera) throws Exception {
        // Detached expected image: the native routine BYTE increment and later DrawObjects bit7 are the only writes.
        assertEquals(before.moveByte(ROUTINE, 4).moveByte(SPRITE_FLAGS, after.spriteFlags()), after);
        assertEquals(before.spriteFlags() & 0x7F, after.spriteFlags() & 0x7F);
        var motion = p.frame().state().motion();
        int dy = ((motion.centreY16_16() >>> 16) - before.centreY() + 128) & 0xFFFF;
        assertTrue((short) dy >= 0 && dy < 256); assertTrue((motion.centreX16_16() >>> 16) >= before.centreX());
        var environment = p.frame().environment(); var bounds = oldEnvironment.movement().bounds();
        assertEquals(new CdPlayerMovement.Bounds(incomingCamera, bounds.right(), bounds.bottom(), bounds.bossFight()),
                environment.movement().bounds());
        var gates = oldEnvironment.movement().gates();
        assertEquals(new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone(), gates.miniSonic(), gates.waterSlide(),
                gates.windTunnel(), gates.speedShoes(), 0), environment.movement().gates());
        assertEquals(oldEnvironment.movement().physics(), environment.movement().physics());
        assertEquals(oldEnvironment.globals().timeAttackMode(), environment.globals().timeAttackMode());
        assertEquals(oldEnvironment.globals().debugMode(), environment.globals().debugMode());
        assertEquals(oldEnvironment.globals().debugCheat(), environment.globals().debugCheat());
        assertEquals(oldEnvironment.globals().invincible(), environment.globals().invincible());
        assertEquals(oldEnvironment.globals().bossMusic(), environment.globals().bossMusic());
        assertEquals(0, environment.globals().timeWarpTimer());
        assertEquals(incomingCamera, presentation(p).pastForegroundImage().destLeftBound());
        var oldContact = (CdPlayerBody.ContactGlobals) actualField(oldGlobals, "contact");
        assertEquals(new CdPlayerBody.ContactGlobals(0, oldContact.shield(), oldContact.combineRing()),
                actualField(loadingGlobals(p), "contact"));
        assertEquals(actualField(oldGlobals, "retainedDev"), actualField(loadingGlobals(p), "retainedDev"));
        assertEquals(actualField(oldGlobals, "score"), actualField(loadingGlobals(p), "score"));
        var calls = p.harness().module().pastLoadState().calls();
        assertEquals(1, calls.stream().filter(v -> v == SonicCdGameModule.PastLoadCall.GOAL_POST_BODY).count());
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.GOAL_POST_BODY) < calls.indexOf(SonicCdGameModule.PastLoadCall.DYNAMIC_OBJECTS));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.DYNAMIC_OBJECTS) < calls.indexOf(SonicCdGameModule.PastLoadCall.PREPARE_PLCS));
        assertGoalPlcTail(p);
    }

    private static void assertGoalPlcTail(Prepared p) {
        var queue = p.harness().module().pastLoadState().section().queue().bytes();
        var sources = new ArrayList<Long>(); var destinations = new ArrayList<Integer>();
        for (int at = 0; at + 6 <= queue.length() && queue.unsignedLong(at) != 0; at += 6) {
            sources.add(queue.unsignedLong(at)); destinations.add(queue.unsignedWord(at + 4));
        }
        assertTrue(sources.size() >= 3); int tail = sources.size() - 3;
        assertEquals(List.of(0x8780, 0x9100, 0x7DE0), destinations.subList(tail, destinations.size()));
        var sign = CdR11bAssets.Asset.ART_SIGNPOST.range();
        assertTrue(sources.get(tail) >= sign.nativeAddress() && sources.get(tail) < sign.endExclusive(),
                "After the native cue, ordinary Prepare may advance the genuine first compressed header");
        assertEquals((long) CdR11bAssets.Asset.ART_BIG_RING.range().nativeAddress(), sources.get(tail + 1).longValue());
        assertEquals((long) CdR11bAssets.Asset.ART_BIG_RING_FLASH.range().nativeAddress(), sources.get(tail + 2).longValue());
    }

    private static void assertCompletedGoal(Prepared p, GoalTransition reached) throws Exception {
        var goal = p.placement().pool().state(32); assertTrue(isNaturalGoal(goal)); assertEquals(4, goal.routine());
        assertEquals(reached.doneImage().moveByte(SPRITE_FLAGS, goal.spriteFlags()), goal, "All64 native bytes remain after Done/Draw/despawn");
        assertEquals(reached.doneImage().spriteFlags() & 0x7F, goal.spriteFlags() & 0x7F);
        assertEquals(0x21D9B4, goal.longBits(MAPPINGS)); assertEquals(0x835A, goal.word(TILE));
        assertEquals(5, goal.unsignedByte(MAP_FRAME)); assertEquals(4, goal.unsignedByte(PRIORITY));
        assertEquals(16, goal.unsignedByte(X_RADIUS)); assertEquals(16, goal.unsignedByte(WIDTH)); assertEquals(32, goal.unsignedByte(Y_RADIUS));
        assertEquals(reached.incomingCamera(), p.frame().environment().movement().bounds().left());
        assertEquals(reached.incomingCamera(), presentation(p).pastForegroundImage().destLeftBound());
        assertEquals(0, p.frame().environment().globals().timeWarpTimer());
        assertEquals(0, p.frame().environment().movement().gates().timeWarpDirection());
        assertEquals(0, ((CdPlayerBody.ContactGlobals) actualField(loadingGlobals(p), "contact")).timeWarp());
    }

    private static boolean isNaturalGoal(CdObjectState state) {
        return state.id() == 0x16 && state.savedFlagsId() == 236 && state.centreX() == 9984 && state.centreY() == 740;
    }
    private static void healthy(Prepared p, Object world, long generation, Object source, Object body) throws Exception {
        var h = p.harness(); assertFalse(p.frame().closed()); assertSame(p.frame(), h.module().requirePlayerFrame());
        assertSame(world, h.mode().getWorldSession()); assertSame(source, p.frame().stageSource());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(body, actualField(p.visit(), "goalPost")); assertNull(actualField(p.visit(), "activeGoal"));
        assertFalse((boolean) actualField(p.visit(), "goalPort")); assertFalse((boolean) actualField(p.visit(), "drawingGoal"));
        assertNull(actualField(p.visit(), "drawQueue"));
        assertEquals(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN, h.module().pastLoadState().calls().getLast());
    }
    private static SonicCdPlayerPresentation presentation(Prepared p) throws Exception {
        return (SonicCdPlayerPresentation) actualField(p.harness().module(), "playerPresentation");
    }
    private static Object loadingGlobals(Prepared p) throws Exception { return actualField(actualField(p.visit(), "values"), "globals"); }

    /** Frozen observed controls, used only as test input. Values never select production behavior. */
    private static LogicalInputSnapshot controller(int call) {
        if (call < 1 || call > 9598) throw new IllegalArgumentException("Positive GoalPost route ends at9598");
        if (call <= 8276) return route(call);
        int directions = 8, actions = call <= 8308 ? 2 : 0;
        if (call >= 9001 && call <= 9203) directions = 4;
        else if (call >= 9206 && call <= 9217) directions = 0;
        else if (call >= 9218 && call <= 9263) { directions = 2; actions = call == 9219 ? 2 : 0; }
        else if (call >= 9336 && call <= 9367) actions = 2;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions, 0, actions, 0, false, false), PlayerInputState.neutral());
    }

    private static RetainedPlaneSceneRenderer.Scene readyScene(SonicCdPastDisplay.Selection selection) {
        return assertInstanceOf(SonicCdPastDisplay.Ready.class, selection.projection(), "Actual selected-image prerequisite").scene();
    }
    private static byte[] draw(Harness h) {
        readyScene(h.module().pastSelectedDisplay()); clear(); h.module().gameplayFrameController().drawOverlay(); return pixels();
    }
    private static void clear() { glViewport(0, 0, 320, 224); glClearColor(1, 0, 1, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); }
    private static byte[] pixels() {
        glFinish(); var buffer = BufferUtils.createByteBuffer(320 * 224 * 4);
        glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, buffer); assertEquals(GL_NO_ERROR, glGetError());
        byte[] result = new byte[buffer.capacity()]; buffer.get(result); return result;
    }
    /** Existing pure selected-display omission algorithm, parameterized by actual physical provenance. */
    private static RetainedPlaneSceneRenderer.Scene omitWhole(RetainedPlaneSceneRenderer.Scene scene, CdObjectDrawQueue.CpuSat cpu, int whole) {
        byte[] raw = cpu.bytes(); var tiles = new ArrayList<SpritePresentation.Tile>(); int at = 0;
        for (int index = cpu.spriteCount() - 1; index >= 0; index--) {
            int size = raw[index * 8 + 2] & 15, count = (((size >>> 2) & 3) + 1) * ((size & 3) + 1);
            if (cpu.provenance().get(index).slotRef().wholeIndex() != whole) tiles.addAll(scene.sprites().tiles().subList(at, at + count));
            at += count;
        }
        assertEquals(scene.sprites().tiles().size(), at); assertTrue(tiles.size() < at, "Selected CPU must actually contain whole" + whole);
        var picture = new SpritePresentation.Frame(tiles, List.of(), scene.sprites().patternVersions());
        return new RetainedPlaneSceneRenderer.Scene(scene.foreground(), scene.background(), scene.packedHscroll256(),
                scene.vscrollLongBits(), scene.selectedPalette128(), scene.displayEnabled(), picture, scene.nativePatterns(), scene.selectedBackdropPaletteIndex());
    }

    /** Optional engine pixels only; no expected image/runtime state is read from this output. */
    private static void writeOptionalCapture(byte[] rgba) throws Exception {
        String output = System.getenv("SONICCD_PAST_GOAL_OUTPUT"); if (output == null || output.isBlank()) return;
        var directory = Path.of(output).toAbsolutePath().normalize();
        var owned = Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD", "captures", "engine").toAbsolutePath().normalize();
        assertTrue(directory.startsWith(owned) && !directory.equals(owned), "Optional export needs its own directory under the engine capture root");
        Files.createDirectories(directory);
        var image = new java.awt.image.BufferedImage(320, 224, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
            int at = ((223 - y) * 320 + x) * 4;
            int argb = (rgba[at + 3] & 255) << 24 | (rgba[at] & 255) << 16 | (rgba[at + 1] & 255) << 8 | rgba[at + 2] & 255;
            image.setRGB(x, y, argb);
        }
        if (!javax.imageio.ImageIO.write(image, "png", directory.resolve("past-world-call-9598.png").toFile()))
            throw new java.io.IOException("PNG writer unavailable");
    }

    private static final class Egl implements AutoCloseable {
        private final PresentationOffscreenTestContext context; private final GraphicsManager graphics;
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
