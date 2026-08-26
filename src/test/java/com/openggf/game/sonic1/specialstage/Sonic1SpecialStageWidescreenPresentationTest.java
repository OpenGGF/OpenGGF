package com.openggf.game.sonic1.specialstage;

import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageViewport;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.HScrollBuffer;
import com.openggf.graphics.ParallaxShaderProgram;
import com.openggf.graphics.QuadRenderer;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.util.FboHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.sonic1.constants.Sonic1Constants.SS_LAYOUT_STRIDE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@FullReset
@ExtendWith(SingletonResetExtension.class)
class Sonic1SpecialStageWidescreenPresentationTest {

    private static final int[] WIDTHS = {320, 400, 528};

    private RecordingGraphics graphics;
    private Sonic1SpecialStageRenderer renderer;

    @BeforeEach
    void setUp() {
        graphics = new RecordingGraphics();
        graphics.setBatchingEnabled(false);
        renderer = new Sonic1SpecialStageRenderer(graphics);
        renderer.setPatternBases(
                0x10000, 0x10100, 0x10200, 0x10300, 0x10400, 0x10500,
                0x10600, 0x10700, 0x10800, 0x10900, 0x10A00, 0x10B00,
                0x10C00, 0x10D00, 0x10D20, 0x10D40, 0x10D60, 0x10D80,
                0x10DA0, 0x10E00, 0x10E80);
    }

    @AfterEach
    void tearDown() {
        graphics.cleanup();
    }

    @Test
    void nativeMazeCommandsKeepIdentityAndMoveToTheCenteredOuterOrigin() {
        byte[] layout = filledLayout(1);
        List<PatternCall> nativeCalls = renderMaze(layout, 320);

        for (int width : WIDTHS) {
            List<PatternCall> calls = renderMaze(layout, width);
            int origin = (width - 320) / 2;
            assertEquals(nativeCalls.size(), calls.size(), "maze command count at " + width);
            for (int i = 0; i < calls.size(); i++) {
                PatternCall expected = nativeCalls.get(i);
                PatternCall actual = calls.get(i);
                assertEquals(expected.patternId(), actual.patternId(), "pattern identity at " + width);
                assertEquals(expected.descriptor(), actual.descriptor(), "descriptor identity at " + width);
                assertEquals(expected.x() + origin, actual.x(), "x origin at " + width);
                assertEquals(expected.y(), actual.y(), "y identity at " + width);
            }
        }
    }

    @Test
    void fallbackBackgroundCommandIsCenteredWithoutExpandingIntoSideBands() throws Exception {
        for (int width : WIDTHS) {
            graphics.clearCommands();
            renderer.setSpecialStageViewport(SpecialStageViewport.fromLogicalWidth(width));
            renderer.setBackdropColor(0.1f, 0.2f, 0.3f);
            renderer.renderBackground();

            assertEquals(1, graphics.commands().size(), "background command count at " + width);
            GLCommand command = graphics.commands().get(0);
            assertEquals(GLCommand.CommandType.RECTI, command.getCommandType());
            assertEquals(0, command.getX1(), "backdrop origin at " + width);
            assertEquals(224, command.getY1(), "backdrop top at " + width);
            assertEquals(width, commandInt(command, "x2"), "backdrop width at " + width);
            assertEquals(0, commandInt(command, "y2"), "backdrop bottom at " + width);
            assertEquals(0.1f, command.getColour1(), "backdrop red at " + width);
            assertEquals(0.2f, command.getColour2(), "backdrop green at " + width);
            assertEquals(0.3f, command.getColour3(), "backdrop blue at " + width);
            assertEquals(1.0f, command.getAlpha(), "backdrop alpha at " + width);
            assertEquals(GLCommand.BlendType.ONE_MINUS_SRC_ALPHA, command.getBlendMode());
        }
    }

    @Test
    void providerPublishesViewportToItsS1Manager() {
        Sonic1SpecialStageProvider provider = new Sonic1SpecialStageProvider();

        for (int width : WIDTHS) {
            SpecialStageViewport viewport = SpecialStageViewport.fromLogicalWidth(width);
            provider.setSpecialStageViewport(viewport);

            assertEquals(viewport, provider.getSpecialStageViewport());
            assertEquals(viewport, provider.getManager().getSpecialStageViewport());
        }
    }

    @Test
    void resultsCommandsMoveTogetherAndKeepNativeIdentityAtEveryWidth() throws Exception {
        TestEnvironment.activeGameplayMode();
        GameServices.graphics().setHeadlessMode(true);
        Sonic1SpecialStageResultsScreen screen =
                new Sonic1SpecialStageResultsScreen(0, false, 0, 0);
        List<GLCommand> nativeCommands = new ArrayList<>();
        screen.setViewportWidth(320);
        screen.appendRenderCommands(nativeCommands);

        for (int width : WIDTHS) {
            List<GLCommand> commands = new ArrayList<>();
            screen.setViewportWidth(width);
            screen.appendRenderCommands(commands);
            int origin = (width - 320) / 2;
            assertEquals(nativeCommands.size(), commands.size(), "results command count at " + width);
            for (int i = 0; i < commands.size(); i++) {
                GLCommand expected = nativeCommands.get(i);
                GLCommand actual = commands.get(i);
                assertEquals(expected.getCommandType(), actual.getCommandType(),
                        "results command identity at " + width + "/" + i);
                assertEquals(expected.getX1() + origin, actual.getX1(),
                        "results x origin at " + width + "/" + i);
                assertEquals(expected.getY1(), actual.getY1(), "results Y at " + width + "/" + i);
                assertEquals(commandInt(expected, "x2"), commandInt(actual, "x2"),
                        "results x2 at " + width + "/" + i);
                assertEquals(commandInt(expected, "y2"), commandInt(actual, "y2"),
                        "results y2 at " + width + "/" + i);
                assertEquals(commandInt(expected, "drawMethod"), commandInt(actual, "drawMethod"),
                        "results draw method at " + width + "/" + i);
                assertEquals(expected.getColour1(), actual.getColour1(), "results red at " + width + "/" + i);
                assertEquals(expected.getColour2(), actual.getColour2(), "results green at " + width + "/" + i);
                assertEquals(expected.getColour3(), actual.getColour3(), "results blue at " + width + "/" + i);
                assertEquals(expected.getAlpha(), actual.getAlpha(), "results alpha at " + width + "/" + i);
                assertEquals(expected.getBlendMode(), actual.getBlendMode(),
                        "results blend state at " + width + "/" + i);
            }
        }
    }

    private List<PatternCall> renderMaze(byte[] layout, int width) {
        graphics.clearPatterns();
        renderer.setSpecialStageViewport(SpecialStageViewport.fromLogicalWidth(width));
        renderer.renderMaze(layout, 0, 0, 0, 0, 0, 0, 0, 0);
        return graphics.patterns();
    }

    private static byte[] filledLayout(int blockId) {
        byte[] layout = new byte[SS_LAYOUT_STRIDE * SS_LAYOUT_STRIDE];
        java.util.Arrays.fill(layout, (byte) blockId);
        return layout;
    }

    private static float[] identityMatrix() {
        float[] matrix = new float[16];
        matrix[0] = matrix[5] = matrix[10] = matrix[15] = 1f;
        return matrix;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object newNativeClip(GraphicsManager graphicsManager,
            SpecialStageViewport viewport) throws Exception {
        Class<?> clipType = Class.forName(
                "com.openggf.game.sonic1.specialstage.Sonic1SpecialStageManager$NativePresentationClip");
        Constructor<?> constructor = clipType.getDeclaredConstructor(
                GraphicsManager.class, SpecialStageViewport.class);
        constructor.setAccessible(true);
        return constructor.newInstance(graphicsManager, viewport);
    }

    private static Object invokeNativeClip(Object clip, String method) throws Exception {
        Method commandMethod = clip.getClass().getDeclaredMethod(method);
        commandMethod.setAccessible(true);
        try {
            return commandMethod.invoke(clip);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw failure;
        }
    }

    private static int commandInt(GLCommand command, String name) throws Exception {
        Field field = GLCommand.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(command);
    }

    private record PatternCall(int patternId, int descriptor, int x, int y) {
    }

    private static final class RecordingGraphics extends GraphicsManager {
        private final List<PatternCall> patterns = new ArrayList<>();
        private final List<GLCommand> commands = new ArrayList<>();
        private final List<com.openggf.graphics.GLCommandable> emitted = new ArrayList<>();

        @Override
        public void renderPatternWithId(int patternId, com.openggf.level.PatternDesc desc, int x, int y) {
            patterns.add(new PatternCall(patternId, desc.get(), x, y));
        }

        @Override
        public void registerCommand(com.openggf.graphics.GLCommandable command) {
            emitted.add(command);
            if (command instanceof GLCommand glCommand) {
                commands.add(glCommand);
            }
        }

        List<PatternCall> patterns() {
            return List.copyOf(patterns);
        }

        List<com.openggf.graphics.GLCommandable> emittedCommands() {
            return List.copyOf(emitted);
        }

        List<GLCommand> commands() {
            return List.copyOf(commands);
        }

        void clearPatterns() {
            patterns.clear();
        }

        void clearCommands() {
            commands.clear();
        }
    }

    private static final class GL14Compat {
        private static final int GL_FUNC_ADD = 0x8006;
    }
}

