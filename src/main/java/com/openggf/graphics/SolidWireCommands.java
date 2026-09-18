package com.openggf.graphics;

import java.util.List;

/** Appends solid fallback geometry without changing the caller's render command ownership. */
public final class SolidWireCommands {
    private SolidWireCommands() {}

    public static void line(List<GLCommand> commands, int x1, int y1, int x2, int y2,
            float r, float g, float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x2, y2, 0, 0));
    }

    public static void rectangle(List<GLCommand> commands, int left, int top, int right, int bottom,
            float r, float g, float b) {
        line(commands, left, top, right, top, r, g, b);
        line(commands, right, top, right, bottom, r, g, b);
        line(commands, right, bottom, left, bottom, r, g, b);
        line(commands, left, bottom, left, top, r, g, b);
    }

    public static void diamond(List<GLCommand> commands, int cx, int cy, int half,
            float r, float g, float b) {
        line(commands, cx, cy - half, cx + half, cy, r, g, b);
        line(commands, cx + half, cy, cx, cy + half, r, g, b);
        line(commands, cx, cy + half, cx - half, cy, r, g, b);
        line(commands, cx - half, cy, cx, cy - half, r, g, b);
    }
}
