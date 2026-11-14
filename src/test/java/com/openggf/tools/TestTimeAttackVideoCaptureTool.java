package com.openggf.tools;

import com.openggf.game.session.EngineContext;
import com.openggf.mods.ui.LevelOverlayCanvas;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.system.MemoryStack;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Real capture-host projection and overlay pixels, without ROMs or a network session. */
@Isolated
class TestTimeAttackVideoCaptureTool {
    @TempDir Path directory;

    /** Extras lists the engine's own extras (Blue Spheres with S3&K present) above the mods' entries. */
    @Test void timeAttackIsFoundByItsLabelWhereverExtrasListsIt() throws Exception {
        assertEquals(0, TimeAttackVideoCaptureTool.extrasRow(List.of("Time Attack"),
                TimeAttackVideoCaptureTool.TIME_ATTACK_LABEL));
        assertEquals(1, TimeAttackVideoCaptureTool.extrasRow(List.of("Blue Spheres", "Time Attack"),
                TimeAttackVideoCaptureTool.TIME_ATTACK_LABEL));
        var missing = assertThrows(IllegalStateException.class, () -> TimeAttackVideoCaptureTool.extrasRow(
                List.of("Blue Spheres"), TimeAttackVideoCaptureTool.TIME_ATTACK_LABEL));
        assertTrue(missing.getMessage().contains("Blue Spheres"), missing.getMessage());
        // The driver reads the list from the live title screen; both fields must stay where it looks.
        var owner = TimeAttackVideoCaptureTool.field(com.openggf.Engine.class,
                TimeAttackVideoCaptureTool.EXTRAS_OWNER_FIELD);
        assertEquals(com.openggf.game.MasterTitleScreen.class, owner.getType());
        assertEquals(List.class, TimeAttackVideoCaptureTool.field(owner.getType(),
                TimeAttackVideoCaptureTool.EXTRAS_LABELS_FIELD).getType());
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 400})
    void scaledCaptureUsesTheEnginesLogicalProjectionForHostedDrawing(int logicalWidth) throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "Select EGL for capture-host pixel verification");
        TestEnvironment.resetAll();
        String previousSaveRoot = System.getProperty("openggf.saveRoot");
        int windowWidth = logicalWidth * 3;
        try (var capture = new TimeAttackVideoCaptureTool(directory.resolve("capture"), directory,
                windowWidth, 224 * 3, logicalWidth)) {
            capture.frame();
            var graphics = EngineContext.fromLegacySingletonsForBootstrap().graphics();
            float[] matrix = graphics.getProjectionMatrixBuffer();
            assertSame(capture.engine().getProjectionMatrixBuffer(), matrix,
                    "The earlier standalone boot matrix must not shadow the live Engine");
            assertEquals(2f / logicalWidth, matrix[0], 0.000001f);
            assertEquals(2f / 224, matrix[5], 0.000001f);

            // A host overlay at the logical top-right must reach the physical top-right,
            // rather than staying one native-sized rectangle in the bottom-left corner.
            glDisable(GL_SCISSOR_TEST);
            graphics.resetForFixedFunction();
            new LevelOverlayCanvas(graphics, logicalWidth, 224)
                    .fill(logicalWidth - 4, 0, 4, 4, 0xFFFF00FF);
            graphics.flushScreenSpace();
            try (var stack = MemoryStack.stackPush()) {
                var pixel = stack.malloc(4);
                glReadPixels(windowWidth - 6, 224 * 3 - 6, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
                assertEquals(255, Byte.toUnsignedInt(pixel.get(0)));
                assertEquals(0, Byte.toUnsignedInt(pixel.get(1)));
                assertEquals(255, Byte.toUnsignedInt(pixel.get(2)));
            }
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            if (previousSaveRoot == null) System.clearProperty("openggf.saveRoot");
            else System.setProperty("openggf.saveRoot", previousSaveRoot);
        }
    }
}
