package com.openggf;

import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDiagnosticWidescreenLayout {

    @Test
    void centeredDiagnosticOriginUsesTheNativeCompositionAtLiveWidths() {
        assertEquals(0, Engine.centeredDiagnosticOrigin(320));
        assertEquals(16, Engine.centeredDiagnosticOrigin(352));
        assertEquals(40, Engine.centeredDiagnosticOrigin(400));
        assertEquals(4, Engine.centeredDiagnosticX(320, 4));
        assertEquals(20, Engine.centeredDiagnosticX(352, 4));
        assertEquals(44, Engine.centeredDiagnosticX(400, 4));
    }

    @Test
    void escapeProgressCommandsUseCenteredNativeRectangleOrigin() {
        for (int width : new int[] {320, 352, 400}) {
            var commands = Engine.escapeProgressRectCommands(width, 30, 0.5);
            assertEquals(6, commands.size());
            assertTrue(commands.stream().allMatch(command ->
                    command.getCommandType() == GLCommand.CommandType.RECTI));
            assertEquals((width - 320) / 2 + 4, commands.get(0).getX1());
        }
    }
}
