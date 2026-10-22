package com.openggf.game.sonic2.specialstage;

import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.internal.NativeSpecialStagePacingRewind;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.Palette;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.lwjgl.system.MemoryUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/**
 * Actual stock-ROM ordinary entry and presentation through HeadlessGameBoot,
 * GameplayCaptureSession and the production provider. No trace, positioned
 * physics, imported art, completion request or synthetic SAT is involved.
 *
 * <p>These are bounded live rendering/reconstruction controls, not a native
 * screenshot comparison or a certification of every stage/route.</p>
 */
@org.junit.jupiter.api.parallel.Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SpecialStageNativePresentation {
    private static final int HEIGHT = GameplayCaptureSession.HEIGHT;
    private static final int POISON = 0xFFFF00FF;

    @BeforeEach
    void requireExplicitIsolatedRendering() {
        assumeTrue("surfaceless-egl".equals(System.getProperty("openggf.headless.gl")),
                "requires explicitly selected display-free surfaceless EGL");
        // Once explicitly requested, boot/context failures must fail the test.
        // In particular, do not catch them and retry against the shared desktop.
    }

    @ParameterizedTest(name = "ordinary S2 H32 presentation, outer width {0}")
    @ValueSource(ints = {320, 528})
    void ordinaryEntryDrawAndProviderRestoreKeepNativePixelsAndControl(int width) throws Exception {
        assertTrue(Arrays.stream(WidescreenAspect.values()).anyMatch(aspect -> aspect.pixelWidth() == width));
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "tails", "off", null, null, null);
        Logger logger = Logger.getLogger(Sonic2SpecialStageManager.class.getName());
        Level previousLogging = logger.getLevel();
        // Optional FINE wall-clock diagnostics are unrelated to gameplay and
        // cannot be replayed against wall time. Leave all captured fields in
        // the comparison; disable only their diagnostic producer here.
        logger.setLevel(Level.INFO);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 2, 1, settings);
            assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass(),
                    "production silent boot must own a no-device backend before level/stage music");
            var entry = enterOrdinaryFirstStage(session);
            var provider = entry.provider();
            var manager = provider.getManager();
            var adapter = adapter(provider);
            assertEquals(SpecialStageProvider.SPECIAL_STAGE_REWIND_KEY, adapter.key());
            assertEquals(0, provider.getCurrentStage(), "fresh ordinary entry selects the first stock stage");
            assertEquals(0, manager.getLiveEntryLoadHoldFrames(), "ordinary load admission has completed naturally");
            assertTrue(manager.getIntro().isComplete());
            assertFalse(GameServices.graphics().isWaterEnabled(), "ordinary S2 stage entry must retire ARZ water rendering");
            assertFalse(GameServices.graphics().isUseUnderwaterPaletteForBackground());
            assertVisibleNativeOwners(manager);

            RgbaImage initial = session.render();
            assertNativeRaster(initial, width, provider.getSpecialStageViewport());
            Object saved = adapter.capture();
            var initialOutputs = manager.getNativeRenderStates();
            var initialControl = provider.pacingState();

            poisonFramebuffer(width);
            RgbaImage repeated = session.render();
            assertArrayEquals(initial.pixels(), repeated.pixels(), "repeat draw must reconstruct fresh pixels");
            assertProviderSnapshotEquals("repeat-draw", saved, adapter.capture());
            assertEquals(initialOutputs, manager.getNativeRenderStates(), "draw cannot advance/publish owners");
            assertEquals(initialControl, provider.pacingState(), "draw cannot consume controller samples");

            // Independent dispatcher control: the capture must use this same
            // provider draw rather than the ARZ world retained under the entry.
            poisonFramebuffer(width);
            RgbaImage direct = renderProviderDirectly(provider, width);
            assertArrayEquals(initial.pixels(), direct.pixels(), "capture must draw its active ordinary S2 provider");
            assertProviderSnapshotEquals("direct-provider-draw", saved, adapter.capture());
            assertNoInheritedWaterContamination(session, provider, adapter, saved, initial, entry.waterPalette());

            List<Bk2FrameInput> input = authoredControl();
            ControlObservation control = advance(session, manager, input, true);
            assertTrue(control.angleChanged(), "authored held direction must reach native player control");
            assertTrue(control.jumped(), "authored jump edge must reach the native player routine");
            assertEquals(GameMode.SPECIAL_STAGE, session.loop().getCurrentGameMode());
            Object expected = adapter.capture();
            var expectedOutputs = manager.getNativeRenderStates();
            var expectedControl = provider.pacingState();
            RgbaImage expectedImage = session.render();
            assertNativeRaster(expectedImage, width, provider.getSpecialStageViewport());
            assertFalse(Arrays.equals(initial.pixels(), expectedImage.pixels()),
                    "the independent forward traversal must exercise changing stage pixels");
            assertNotEquals(initialControl.acceptedSampleOrdinal(), expectedControl.acceptedSampleOrdinal(),
                    "the traversal must admit actual native controller samples");

            adapter.restore(saved);
            assertProviderSnapshotEquals("restore", saved, adapter.capture());
            assertEquals(initialOutputs, manager.getNativeRenderStates());
            assertEquals(initialControl, provider.pacingState());
            poisonFramebuffer(width);
            assertArrayEquals(initial.pixels(), session.render().pixels(),
                    "restore must rebuild native displayed state without saved GPU identities");
            assertProviderSnapshotEquals("restore-draw", saved, adapter.capture());

            // Both branches start/end at neutral held input. The capture's
            // external edge history therefore agrees without a new driver API.
            // Omit intermediate drawing only on replay to expose dependencies
            // on cached framebuffer, render calls, or last queued SAT output.
            assertEquals(control, advance(session, manager, input, false));
            assertProviderSnapshotEquals("restore-forward", expected, adapter.capture());
            assertEquals(expectedOutputs, manager.getNativeRenderStates());
            assertEquals(expectedControl, provider.pacingState());
            poisonFramebuffer(width);
            RgbaImage replay = session.render();
            assertArrayEquals(expectedImage.pixels(), replay.pixels(),
                    "provider restore plus identical input must rebuild the same actual presentation");
            assertNativeRaster(replay, width, provider.getSpecialStageViewport());
            assertProviderSnapshotEquals("restore-forward-draw", expected, adapter.capture());
            assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
        } finally {
            logger.setLevel(previousLogging);
        }
    }

    private record InheritedWaterPalette(Palette[] rows, Palette normalLine0) { }
    private record EnteredStage(Sonic2SpecialStageProvider provider, InheritedWaterPalette waterPalette) { }

    private static EnteredStage enterOrdinaryFirstStage(GameplayCaptureSession session) {
        for (int frame = 0; frame < 240 && GameServices.fade().isActive(); frame++) {
            session.step(null);
            session.renderFrame();
        }
        assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
        assertFalse(GameServices.fade().isActive(), "ordinary initial level reveal must finish");
        assertEquals(2, GameServices.level().getCurrentZone(), "declared ordinary Aquatic Ruin entry");
        assertEquals(1, GameServices.level().getCurrentAct(), "capture act is zero-based ARZ2");
        assertEquals(0x0F, GameServices.level().getRomZoneId());
        assertEquals(0x0F, GameServices.level().getFeatureZoneId());
        assertTrue(GameServices.water().hasWater(0x0F, 1), "the loaded stock ARZ2 owns water");
        session.renderFrame();
        assertTrue(GameServices.graphics().isWaterEnabled(),
                "the ordinary ARZ WATER command, without a synthetic flag write, must execute before entry");
        assertTrue(GameServices.graphics().getWaterlineScreenY() > 0,
                "the cold ordinary start retains the ROM's positive waterline even above the water");
        Integer underwaterTexture = GameServices.graphics().getUnderwaterPaletteTextureId();
        assertNotNull(underwaterTexture, "ordinary ARZ must publish its real underwater palette texture");
        assertTrue(underwaterTexture > 0);
        Palette[] waterRows = GameServices.water().getUnderwaterPalette(0x0F, 1);
        assertNotNull(waterRows);
        assertEquals(4, waterRows.length);
        InheritedWaterPalette inherited = new InheritedWaterPalette(copyPalettes(waterRows),
                GameServices.level().getCurrentLevel().getPalette(0).deepCopy());
        session.loop().enterSpecialStage();
        for (int frame = 0; frame < 1200; frame++) {
            session.step(null);
            session.renderFrame();
            if (session.loop().getCurrentGameMode() == GameMode.SPECIAL_STAGE
                    && session.loop().getActiveSpecialStageProvider() instanceof Sonic2SpecialStageProvider provider
                    && provider.isEntryPresentationReady() && !provider.isEntryFadeToWhiteActive()
                    && !GameServices.fade().isActive() && provider.getManager().getIntro().isComplete()) {
                return new EnteredStage(provider, inherited);
            }
        }
        fail("ordinary S2 entry/intro did not reach live gameplay through GameLoop");
        throw new AssertionError("unreachable");
    }

    private enum DrawingMode { DIRECT, INSTANCED }

    private record RetainedLevelPlayer(int x, int y, int xSpeed, int ySpeed, int groundSpeed,
                                       int rings, boolean inWater) {
        static RetainedLevelPlayer read(GameplayCaptureSession session) {
            var player = session.player();
            return new RetainedLevelPlayer(player.getCentreX(), player.getCentreY(), player.getXSpeed(),
                    player.getYSpeed(), player.getGSpeed(), player.getRingCount(), player.isInWater());
        }
    }

    private static void assertNoInheritedWaterContamination(
            GameplayCaptureSession session, Sonic2SpecialStageProvider provider,
            RewindSnapshottable<Object> adapter, Object saved, RgbaImage cleanReference,
            InheritedWaterPalette inherited) throws Exception {
        GraphicsManager graphics = GameServices.graphics();
        boolean oldBatching = graphics.isBatchingEnabled();
        boolean oldInstancing = graphics.isInstancedBatchingEnabled();
        boolean oldWater = graphics.isWaterEnabled();
        boolean oldBackgroundWater = graphics.isUseUnderwaterPaletteForBackground();
        float oldWaterline = graphics.getWaterlineScreenY();
        var waterState = GameServices.water().capture();
        var retainedPlayer = RetainedLevelPlayer.read(session);
        var control = provider.pacingState();
        Palette[] poisonRows = copyPalettes(inherited.rows());
        for (Palette row : poisonRows) {
            // Independent render-only input, detached from both the loaded
            // level's ROM palette and the special-stage source palette. Keep
            // transparent colour zero; force every opaque colour to magenta.
            for (int colour = 1; colour < Palette.PALETTE_SIZE; colour++) {
                row.getColor(colour).r = (byte) 255;
                row.getColor(colour).g = 0;
                row.getColor(colour).b = (byte) 255;
            }
        }
        try {
            for (DrawingMode mode : DrawingMode.values()) {
                // A batch records some state at construction and other state
                // at execution. Drain the owning projection before changing
                // its batching mode; no old batch crosses this boundary.
                graphics.flushPatternBatch();
                graphics.flush();
                graphics.setBatchingEnabled(mode == DrawingMode.INSTANCED);
                graphics.setInstancedBatchingEnabled(mode == DrawingMode.INSTANCED);
                if (mode == DrawingMode.INSTANCED) {
                    assertNotNull(graphics.getInstancedShaderProgram(),
                            "the explicitly requested instanced control must have a real GL renderer");
                }
                graphics.cacheUnderwaterPaletteTexture(inherited.rows(), inherited.normalLine0());
                poisonFramebuffer(cleanReference.width());
                RgbaImage cleanMode = session.render();
                assertArrayEquals(cleanReference.pixels(), cleanMode.pixels(), mode + " clean stage pixels");
                assertFalse(graphics.isWaterEnabled());
                assertFalse(graphics.isUseUnderwaterPaletteForBackground());
                byte[] ordinaryUnderwater = underwaterTextureBytes(graphics);
                Integer texture = graphics.cacheUnderwaterPaletteTexture(poisonRows, inherited.normalLine0());
                assertNotNull(texture);
                assertTrue(texture > 0);
                assertFalse(Arrays.equals(ordinaryUnderwater, underwaterTextureBytes(graphics)),
                        "the contrasting underwater bytes must actually reach the GPU, not wait in a latch");

                // Check the water selector by itself as well as the legacy
                // priority selector that otherwise hides its raw selection.
                // Neither effective shader reads nor inferred raw restoration
                // is used to manufacture the stage's expected selector state.
                for (boolean legacyPriority : new boolean[] {false, true}) {
                    String context = mode + "/legacy-priority=" + legacyPriority;
                    graphics.setWaterlineScreenY(-HEIGHT); // Entire target lies below this render-only split.
                    graphics.setWaterEnabled(true);
                    graphics.setUseUnderwaterPaletteForBackground(true);
                    graphics.setUseWaterShader(true);
                    graphics.setUseSpritePriorityShader(legacyPriority);
                    graphics.setCurrentSpriteHighPriority(true);
                    assertTrue(graphics.isWaterEnabled());
                    assertTrue(graphics.isUseUnderwaterPaletteForBackground());
                    poisonFramebuffer(cleanReference.width());
                    RgbaImage contaminated = session.render();
                    assertArrayEquals(cleanMode.pixels(), contaminated.pixels(),
                            context + " S2 must own no-water state before pattern construction and execution");
                    assertFalse(graphics.isWaterEnabled(), context + " retires the inherited level water flag");
                    assertFalse(graphics.isUseUnderwaterPaletteForBackground());
                    assertProviderSnapshotEquals(context + ".render-only-water", saved, adapter.capture());
                    assertEquals(control, provider.pacingState());
                    assertEquals(waterState, GameServices.water().capture(), "graphics cannot write water physics");
                    assertEquals(retainedPlayer, RetainedLevelPlayer.read(session),
                            "graphics cannot change the retained level player or its inWater semantics");
                    assertPaletteRowsEqual(inherited.rows(), GameServices.water().getUnderwaterPalette(0x0F, 1));
                }
            }
        } finally {
            graphics.flushPatternBatch();
            graphics.flush();
            // Restore the borrowed stock upload basis and the explicitly
            // observed scalar graphics state. The ordinary S2 scene owns its
            // shader selectors; do not infer or restore a hidden raw selector
            // from GraphicsManager.getShaderProgram()'s effective result.
            try {
                graphics.cacheUnderwaterPaletteTexture(inherited.rows(), inherited.normalLine0());
            } finally {
                graphics.setWaterEnabled(oldWater);
                graphics.setUseUnderwaterPaletteForBackground(oldBackgroundWater);
                graphics.setWaterlineScreenY(oldWaterline);
                graphics.setBatchingEnabled(oldBatching);
                graphics.setInstancedBatchingEnabled(oldInstancing);
            }
        }
    }

    private static Palette[] copyPalettes(Palette[] source) {
        return Arrays.stream(source).map(Palette::deepCopy).toArray(Palette[]::new);
    }

    private static void assertPaletteRowsEqual(Palette[] expected, Palette[] actual) {
        assertNotNull(actual);
        assertEquals(expected.length, actual.length);
        for (int row = 0; row < expected.length; row++) {
            assertTrue(expected[row].dataEquals(actual[row]), "water source CRAM row " + row + " is unchanged");
        }
    }

    private static byte[] underwaterTextureBytes(GraphicsManager graphics) {
        Integer texture = graphics.getUnderwaterPaletteTextureId();
        assertNotNull(texture);
        assertTrue(texture > 0);
        int oldBinding = glGetInteger(GL_TEXTURE_BINDING_2D);
        ByteBuffer bytes = null;
        try {
            glBindTexture(GL_TEXTURE_2D, texture);
            int width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
            int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
            assertTrue(width > 0 && height > 0, "the upload must name an allocated native palette texture");
            bytes = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4));
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            byte[] copy = new byte[bytes.remaining()];
            bytes.get(copy);
            return copy;
        } finally {
            if (bytes != null) MemoryUtil.memFree(bytes);
            glBindTexture(GL_TEXTURE_2D, oldBinding);
        }
    }

    private static void assertVisibleNativeOwners(Sonic2SpecialStageManager manager) {
        var states = manager.getNativeRenderStates();
        assertTrue(states.stream().anyMatch(state -> state.visible()
                && state.artRole() == Sonic2SpecialStageSlotPool.ArtRole.SONIC));
        assertTrue(states.stream().anyMatch(state -> state.visible()
                && state.artRole() == Sonic2SpecialStageSlotPool.ArtRole.TAILS));
        assertTrue(states.stream().anyMatch(state -> state.visible()
                && state.artRole() == Sonic2SpecialStageSlotPool.ArtRole.SHADOW),
                "the live scene must exercise an actual native shadow owner");
        assertTrue(states.stream().anyMatch(state -> state.visible()
                && state.artRole() == Sonic2SpecialStageSlotPool.ArtRole.HUD));
        assertEquals(manager.getNativeSlotTopology().size(), states.size(),
                "every live owner, including hidden owners, has one captured presentation value");
    }

    /** A short independent held-input script; no movie/trace is read. */
    private static List<Bk2FrameInput> authoredControl() {
        var frames = new ArrayList<Bk2FrameInput>();
        append(frames, 2, 0, 0);
        append(frames, 12, AbstractPlayableSprite.INPUT_RIGHT, 0);
        append(frames, 4, 0, 0);
        append(frames, 8, AbstractPlayableSprite.INPUT_JUMP, 1);
        append(frames, 20, 0, 0);
        append(frames, 12, AbstractPlayableSprite.INPUT_LEFT, 0);
        append(frames, 15, 0, 0);
        return List.copyOf(frames);
    }

    private static void append(List<Bk2FrameInput> frames, int count, int held, int actions) {
        for (int i = 0; i < count; i++) {
            frames.add(new Bk2FrameInput(frames.size(), held, actions, false, "authored S2 presentation control"));
        }
    }

    private record ControlObservation(boolean angleChanged, boolean jumped) { }

    private static ControlObservation advance(GameplayCaptureSession session, Sonic2SpecialStageManager manager,
                                   List<Bk2FrameInput> input, boolean renderEveryFrame) {
        int initialAngle = manager.getSonicPlayer().getAngle();
        boolean angleChanged = false;
        boolean jumped = false;
        for (Bk2FrameInput frame : input) {
            session.step(frame);
            if (renderEveryFrame) session.renderFrame();
            angleChanged |= manager.getSonicPlayer().getAngle() != initialAngle;
            jumped |= manager.getSonicPlayer().getRoutine() == Sonic2SpecialStagePlayer.RoutineState.JUMPING;
            assertEquals(GameMode.SPECIAL_STAGE, session.loop().getCurrentGameMode());
        }
        return new ControlObservation(angleChanged, jumped);
    }

    private static RgbaImage renderProviderDirectly(Sonic2SpecialStageProvider provider, int width) {
        provider.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GameServices.camera().setX((short) 0);
        GameServices.camera().setY((short) 0);
        provider.setSpecialStageViewport(SpecialStageViewport.fromLogicalWidth(width));
        provider.draw();
        GameServices.graphics().flush();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(width, HEIGHT);
    }

    private static void poisonFramebuffer(int width) {
        glClearColor(1.0f, 0.0f, 1.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glFinish();
        RgbaImage poisoned = ScreenshotCapture.captureFramebuffer(width, HEIGHT);
        assertTrue(Arrays.stream(poisoned.pixels()).allMatch(pixel -> pixel == POISON),
                "comparison must start with a deliberately overwritten full framebuffer");
    }

    private static void assertNativeRaster(RgbaImage image, int width, SpecialStageViewport viewport) {
        assertEquals(width, image.width());
        assertEquals(HEIGHT, image.height());
        assertEquals(width, viewport.logicalWidth());
        var inner = viewport.innerH32();
        assertEquals(256, inner.width(), "native H32 width is independent of outer aspect");
        assertEquals((width - 256) / 2, inner.x());
        assertEquals(HEIGHT, inner.height());
        int coloured = 0;
        var colours = new java.util.HashSet<Integer>();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.argb(x, y) & 0xFFFFFF;
                if (x < inner.x() || x >= inner.x() + inner.width()) {
                    assertEquals(0, rgb, "native H32 border pixel at (" + x + "," + y + ")");
                } else {
                    if (rgb != 0) coloured++;
                    colours.add(rgb);
                }
            }
        }
        assertTrue(coloured > 1024, "actual ROM stage pixels must replace the cleared target, got " + coloured);
        assertTrue(colours.size() > 8, "actual live terrain/UI/player art must contain varied colours, got " + colours.size());
    }

    @SuppressWarnings("unchecked")
    private static RewindSnapshottable<Object> adapter(Sonic2SpecialStageProvider provider) {
        return (RewindSnapshottable<Object>) provider.rewindAdapter().orElseThrow();
    }

    private static void assertProviderSnapshotEquals(String path, Object expected, Object actual) throws Exception {
        var a = assertInstanceOf(NativeSpecialStagePacingRewind.Snapshot.class, expected);
        var b = assertInstanceOf(NativeSpecialStagePacingRewind.Snapshot.class, actual);
        assertEquals(a.acceptedSamples(), b.acceptedSamples(), path + ".acceptedSamples");
        var nativeA = assertInstanceOf(Sonic2SpecialStageProviderSnapshot.class, a.nativeState());
        var nativeB = assertInstanceOf(Sonic2SpecialStageProviderSnapshot.class, b.nativeState());
        assertEquals(nativeA.resultsPlcSubmitted(), nativeB.resultsPlcSubmitted(), path + ".resultsPlcSubmitted");
        // The manager's outer snapshot is a value class without equals().
        // Descend through EVERY captured instance field; no palette, clock,
        // input, native owner, pending pass or presentation channel is waived.
        var differences = new ArrayList<String>();
        for (Field field : Sonic2SpecialStageSnapshot.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            differences.addAll(RewindSnapshotDiff.diffKey(path + "." + field.getName(),
                    field.get(nativeA.managerSnapshot()), field.get(nativeB.managerSnapshot())));
        }
        assertTrue(differences.isEmpty(), () -> String.join("\n", differences));
    }
}
