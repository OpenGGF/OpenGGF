package com.openggf.editor.render;

import com.openggf.debug.DebugColor;
import com.openggf.debug.FontSize;
import com.openggf.game.GameServices;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.PixelFontVariant;
import com.openggf.graphics.PatternRenderCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EditorTextRenderer {
    public record TextCommand(String text, int x, int y, int lineHeight, DebugColor color, FontSize fontSize, float scale) {
        public TextCommand(String text,int x,int y,int lineHeight,DebugColor color,FontSize fontSize) {
            this(text,x,y,lineHeight,color,fontSize,1f);
        }
    }
    public record PositionedLine(String text, int x, int y) {}

    private static final int DEFAULT_LINE_HEIGHT = 10;
    private static final DebugColor DEFAULT_COLOR = DebugColor.WHITE;
    private static final FontSize DEFAULT_FONT_SIZE = FontSize.SMALL;

    private final GraphicsManager graphicsManager;
    private final PixelFontTextRenderer textRenderer;

    public EditorTextRenderer() {
        this(GameServices.graphics(), new NativeEditorTextRenderer());
    }

    public EditorTextRenderer(GraphicsManager graphicsManager) {
        this(graphicsManager, new NativeEditorTextRenderer());
    }

    public EditorTextRenderer(GraphicsManager graphicsManager, PixelFontTextRenderer textRenderer) {
        this.graphicsManager = Objects.requireNonNull(graphicsManager, "graphicsManager");
        this.textRenderer = Objects.requireNonNull(textRenderer, "textRenderer");
    }

    public void renderLines(List<String> lines, int x, int y) {
        List<TextCommand> commands = buildTextCommands(lines, x, y);
        if (!commands.isEmpty()) {
            graphicsManager.registerCommand(buildTextBatchCommand(commands));
        }
    }

    public void renderFittedLines(List<String> lines,int x,int y,int maxWidth,float scale,int lineHeight) {
        List<TextCommand> commands=buildFittedTextCommands(lines,x,y,maxWidth,scale,lineHeight);
        if(!commands.isEmpty())graphicsManager.registerCommand(buildTextBatchCommand(commands));
    }
    public List<TextCommand> buildFittedTextCommands(List<String> lines,int x,int y,int maxWidth,float scale,int lineHeight) {
        List<TextCommand> result=new ArrayList<>();
        int advance = MenuPixelFont.glyphAdvance(scale);
        int fittedLineHeight = Math.max(lineHeight, MenuPixelFont.glyphLineHeight(scale) + 1);
        for(String line:lines) {
            if(line!=null&&!line.isBlank()) {
                int maxChars=Math.max(1,maxWidth / advance);
                String fitted=line.length()>maxChars
                        ? (maxChars <= 3 ? ".".repeat(maxChars) : line.substring(0,maxChars-3)+"...") : line;
                result.add(new TextCommand(fitted,x,y,fittedLineHeight,DEFAULT_COLOR,DEFAULT_FONT_SIZE,scale));
            }
            y+=fittedLineHeight;
        }
        return List.copyOf(result);
    }

    public void renderPositionedLines(List<PositionedLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<TextCommand> commands = new ArrayList<>(lines.size());
        for (PositionedLine line : lines) {
            if (line != null && line.text() != null && !line.text().isBlank()) {
                commands.add(new TextCommand(line.text(), line.x(), line.y(), DEFAULT_LINE_HEIGHT,
                        DEFAULT_COLOR, DEFAULT_FONT_SIZE));
            }
        }
        if (!commands.isEmpty()) {
            graphicsManager.registerCommand(buildTextBatchCommand(commands));
        }
    }

    protected List<TextCommand> buildTextCommands(List<String> lines, int x, int y) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }

        List<TextCommand> commands = new ArrayList<>();
        int lineY = y;
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                commands.add(new TextCommand(line, x, lineY, DEFAULT_LINE_HEIGHT, DEFAULT_COLOR, DEFAULT_FONT_SIZE));
            }
            lineY += DEFAULT_LINE_HEIGHT;
        }
        return commands;
    }

    protected GLCommandable buildTextBatchCommand(List<TextCommand> commands) {
        return new TextBatchCommand(List.copyOf(commands));
    }

    /** Fractional size requests choose the authored 6x8 face rather than resampling 9x10 glyphs. */
    private static final class NativeEditorTextRenderer extends PixelFontTextRenderer {
        private NativeEditorTextRenderer() { super(new MenuPixelFont(), PixelFontVariant.PIXEL_FONT_NO_SHADOW); }
    }

    private final class TextBatchCommand implements GLCommandable {
        private final List<TextCommand> commands;

        private TextBatchCommand(List<TextCommand> commands) {
            this.commands = commands;
        }

        @Override
        public void execute(int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
            if (commands.isEmpty()) {
                return;
            }

            float[] projectionMatrix = graphicsManager.getProjectionMatrixBuffer();
            if (projectionMatrix != null) {
                textRenderer.setProjectionMatrix(projectionMatrix);
            }
            try {
                for (TextCommand command : commands) {
                    if(command.scale()==1f)textRenderer.drawShadowedText(command.text(),command.x(),command.y(),command.color());
                    else textRenderer.drawShadowedText(command.text(),command.x(),command.y(),command.color(),command.scale());
                }
            } finally {
                // Text changes programs and texture bindings between preview batches. Rebind the following tiles.
                PatternRenderCommand.resetFrameState();
            }
        }
    }
}
