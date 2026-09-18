package com.openggf.graphics;

import org.junit.jupiter.api.Test;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestSolidWireCommands {
    @Test
    void rectangleAppendsClockwiseEdgesWithoutReplacingExistingCommands() {
        List<GLCommand> commands = new ArrayList<>();
        SolidWireCommands.line(commands, 1, 2, 3, 4, 0.1f, 0.2f, 0.3f);
        GLCommand first = commands.getFirst();
        SolidWireCommands.rectangle(commands, -7, -3, 11, 13, 0.1f, 0.2f, 0.3f);
        assertSame(first, commands.getFirst());
        assertVertices(commands, new int[][] {{1,2},{3,4},{-7,-3},{11,-3},
                {11,-3},{11,13},{11,13},{-7,13},{-7,13},{-7,-3}});
    }

    @Test
    void diamondKeepsTopRightBottomLeftEdgeOrder() {
        List<GLCommand> commands = new ArrayList<>();
        SolidWireCommands.diamond(commands, -2, 3, 5, 0.1f, 0.2f, 0.3f);
        assertVertices(commands, new int[][] {{-2,-2},{3,3},{3,3},{-2,8},
                {-2,8},{-7,3},{-7,3},{-2,-2}});
    }

    private static void assertVertices(List<GLCommand> commands, int[][] vertices) {
        assertEquals(vertices.length, commands.size());
        int screenHeight = GameServices.configuration().getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
        for (int i = 0; i < vertices.length; i++) {
            GLCommand command = commands.get(i);
            assertEquals(GLCommand.CommandType.VERTEX2I, command.getCommandType());
            assertEquals(GLCommand.BlendType.SOLID, command.getBlendMode());
            assertEquals((float) vertices[i][0], command.getX1());
            // GLCommand stores world Y inverted around the configured screen height.
            assertEquals((float) (screenHeight - vertices[i][1]), command.getY1());
            assertEquals(0.1f, command.getColour1());
            assertEquals(0.2f, command.getColour2());
            assertEquals(0.3f, command.getColour3());
        }
    }
}
