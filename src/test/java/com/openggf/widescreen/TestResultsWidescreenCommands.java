package com.openggf.widescreen;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameStateManager;
import com.openggf.game.RomDetectionService;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic1.objects.Sonic1ResultsScreenObjectInstance;
import com.openggf.game.sonic2.objects.ResultsScreenObjectInstance;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real ordinary-results command characterization at the supported widths. */
class TestResultsWidescreenCommands {

    private static final int NATIVE_WIDTH = 320;
    private static final int[] WIDTHS = {320, 352, 400};
    private static final int TEST_PATTERN_BASE = 0x40000;

    private RecordingGraphics graphics;
    private Camera camera;
    private ObjectRenderManager renderManager;
    private ObjectSpriteSheet resultsSheet;
    private PatternSpriteRenderer resultsRenderer;
    private TestObjectServices services;

    @BeforeEach
    void setUp() {
        graphics = new RecordingGraphics();
        camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        when(camera.getY()).thenReturn((short) 0);

        resultsSheet = testResultsSheet();
        resultsRenderer = new PatternSpriteRenderer(resultsSheet, graphics);
        resultsRenderer.ensurePatternsCached(graphics, TEST_PATTERN_BASE);
        renderManager = mock(ObjectRenderManager.class);
        when(renderManager.getResultsRenderer()).thenReturn(resultsRenderer);
        when(renderManager.getResultsSheet()).thenReturn(resultsSheet);
        when(renderManager.getResultsHudDigitPatterns()).thenReturn(new Pattern[20]);

        GameStateManager gameState = mock(GameStateManager.class);
        when(gameState.getScore()).thenReturn(0);
        services = new TestObjectServices() {
            @Override
            public ObjectRenderManager renderManager() {
                return renderManager;
            }
        }.withCamera(camera).withGraphicsManager(graphics).withGameState(gameState);

        SonicConfigurationService configuration = mock(SonicConfigurationService.class);
        when(configuration.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn(224);
        EngineServices.configure(new EngineContext(configuration, graphics,
                mock(AudioManager.class), mock(RomManager.class),
                mock(PerformanceProfiler.class), mock(DebugOverlayManager.class),
                mock(PlaybackDebugManager.class), mock(RomDetectionService.class),
                mock(CrossGameFeatureProvider.class)));
    }

    @Test
    void sonic1CentersEveryNativeResultCommandAtLiteralOrigins() throws Exception {
        Sonic1ResultsScreenObjectInstance results = new Sonic1ResultsScreenObjectInstance(0, 0, 1);
        results.setServices(services);
        setField(results, "plcReadinessPassed", true);
        setIntArrayField(results, "elemCurrentX",
                0x124, 0x120, 0x14C, 0x120, 0x120, 0x120, 0x14C);

        for (int width : WIDTHS) {
            graphics.setProjectionWidth(width);
            graphics.clearPatterns();
            List<PatternCommand> expected = List.of(
                    command(5, 0x14C - 128, 0xCC - 128, width),
                    command(0, 0x124 - 128, 0xBC - 128, width),
                    command(1, 0x120 - 128, 0xD0 - 128, width),
                    command(6, 0x14C - 128, 0xD6 - 128, width),
                    command(9, 0x120 - 128, 0xEC - 128, width),
                    command(2, 0x120 - 128, 0xEC - 128, width),
                    command(3, 0x120 - 128, 0xFC - 128, width),
                    command(4, 0x120 - 128, 0x10C - 128, width));

            results.appendRenderCommands(new ArrayList<>());

            assertEquals(expected, graphics.patterns(), "S1 commands at width " + width);
        }
    }

    @Test
    void sonic2CentersEveryNativeResultCommandAtLiteralOrigins() {
        ResultsScreenObjectInstance results = new ResultsScreenObjectInstance(0, 0, 1, false);
        results.setServices(services);

        for (int width : WIDTHS) {
            graphics.setProjectionWidth(width);
            graphics.clearPatterns();
            List<PatternCommand> expected = List.of(
                    command(0, 160 - 256, 56, width),
                    command(3, 128 + 256, 74, width),
                    command(4, 192 + 256, 74, width),
                    command(6, 248 + 256, 62, width),
                    command(10, 160 + 512, 112, width),
                    command(11, 160 + 528, 128, width),
                    command(9, 160 + 560, 160, width));

            results.appendRenderCommands(new ArrayList<>());

            assertEquals(expected, graphics.patterns(), "S2 commands at width " + width);
        }
    }

    @Test
    void sonic1FallbackCommandsUseTheCenteredOriginForLiveViewportCulling() throws Exception {
        Sonic1ResultsScreenObjectInstance results = new Sonic1ResultsScreenObjectInstance(0, 0, 1);
        TestObjectServices fallbackServices = new TestObjectServices() {
            @Override
            public ObjectRenderManager renderManager() {
                return null;
            }
        }.withCamera(camera).withGraphicsManager(graphics);
        results.setServices(fallbackServices);
        setField(results, "plcReadinessPassed", true);
        setIntArrayField(results, "elemCurrentX",
                128 - 20, -1000, -1000, -1000, -1000, -1000, -1000);

        graphics.setProjectionWidth(400);
        List<GLCommand> commands = new ArrayList<>();
        results.appendRenderCommands(commands);

        assertTrue(!commands.isEmpty(),
                "a centered S1 element still inside the 400px viewport must render");
        assertEquals(GLCommand.CommandType.VERTEX2I, commands.get(0).getCommandType());
        assertEquals(20, commands.get(0).getX1(),
                "S1 fallback geometry must include the live 40px centered origin");
    }

    private static PatternCommand command(int frame, int nativeX, int y, int width) {
        return new PatternCommand("PATTERN", TEST_PATTERN_BASE + frame, frame,
                nativeX + (width - NATIVE_WIDTH) / 2, y);
    }

    private static PatternCommand s3kCommand(int frame, int nativeX, int y, int width) {
        return new PatternCommand("PATTERN",
                PatternAtlasRange.RESULTS_SCREENS.base() + frame, frame,
                nativeX + (width - NATIVE_WIDTH) / 2, y);
    }

    private static ObjectSpriteSheet testResultsSheet() {
        Pattern[] patterns = new Pattern[64];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = new Pattern();
        }
        List<SpriteMappingFrame> frames = new ArrayList<>();
        for (int frame = 0; frame < 32; frame++) {
            frames.add(new SpriteMappingFrame(List.of(new SpriteMappingPiece(
                    0, 0, 1, 1, frame, false, false, 0))));
        }
        return new ObjectSpriteSheet(patterns, frames, 0, 1);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setIntArrayField(Object target, String name, int... values)
            throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, values);
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static int getIntField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setBooleanField(Object target, String name, boolean value)
            throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static boolean getBooleanField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void invokePrivate(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through the concrete owner's inheritance chain.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private record PatternCommand(String kind, int patternId, int descriptor, int x, int y) {
    }

    private static final class RecordingGraphics extends GraphicsManager {
        private final List<PatternCommand> patterns = new ArrayList<>();

        @Override
        public void cachePatternTexture(Pattern pattern, int patternId) {
        }

        @Override
        public void updatePatternTexture(Pattern pattern, int patternId) {
        }

        @Override
        public void renderPatternWithId(int patternId, com.openggf.level.PatternDesc desc,
                int x, int y) {
            patterns.add(new PatternCommand("PATTERN", patternId, desc.get(), x, y));
        }

        private List<PatternCommand> patterns() {
            return List.copyOf(patterns);
        }

        private void clearPatterns() {
            patterns.clear();
        }
    }
}
