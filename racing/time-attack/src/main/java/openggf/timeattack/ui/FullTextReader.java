package openggf.timeattack.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.NameFont;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Read-only, measured full-text fallback for the racing menus' compact previews. */
public final class FullTextReader {
    private Supplier<List<String>> content;
    private int offset;
    private boolean pad;
    private int capacity = 9;
    private int lineCount;
    private List<String> visible = List.of();

    public boolean isOpen() { return content != null; }

    public void open(Supplier<List<String>> source) {
        content = source;
        offset = 0;
    }

    public void close() { content = null; }

    /** Consumes the entire tick, including the closing confirm, before any menu action sees it. */
    public boolean handle(ViewInput input, Supplier<List<String>> source) {
        pad = MenuPage.controller(input, pad);
        if (!isOpen()) {
            if (!input.details()) return false;
            open(source);
            return true;
        }
        if (input.back() || input.accept() || input.keyPressed(SceneKeys.ESCAPE)
                || input.details()) {
            close();
            return true;
        }
        int step = input.up() || input.keyPressed(SceneKeys.UP) ? -1
                : input.down() || input.keyPressed(SceneKeys.DOWN) ? 1
                : input.left() || input.keyPressed(SceneKeys.LEFT) ? -capacity
                : input.right() || input.keyPressed(SceneKeys.RIGHT) ? capacity : 0;
        offset = Math.clamp(offset + step, 0, Math.max(0, lineCount - capacity));
        return true;
    }

    public boolean draw(SceneCanvas canvas) {
        if (!isOpen()) return false;
        MenuPage.page(canvas, "FULL TEXT", null, "Complete values and diagnostics");
        List<String> lines = new ArrayList<>();
        int room = Math.max(1, canvas.width() - 28);
        for (String paragraph : content.get()) {
            if (paragraph == null) continue;
            for (String part : readable(paragraph).split("\\R", -1)) {
                lines.addAll(TextEntry.wrapPixels(part, room));
            }
        }
        int firstRow = 0;
        int lastRow = NameFont.LINE_HEIGHT - 1;
        for (String line : lines) {
            int[] extent = NameFont.extent(line);
            firstRow = Math.min(firstRow, extent[0]);
            lastRow = Math.max(lastRow, extent[1]);
        }
        int pitch = lastRow - firstRow + 1;
        int bodyTop = MenuPage.BODY_TOP;
        int bodyBottom = MenuPage.FOOTER_Y - NameFont.LINE_HEIGHT - 8;
        capacity = Math.max(1, (bodyBottom - bodyTop) / pitch);
        lineCount = lines.size();
        offset = Math.clamp(offset, 0, Math.max(0, lineCount - capacity));
        visible = List.copyOf(lines.subList(offset, Math.min(lines.size(), offset + capacity)));
        int y = bodyTop - firstRow;
        canvas.clip(10, bodyTop, Math.max(1, canvas.width() - 20), capacity * pitch);
        for (String line : visible) {
            NameFont.draw(canvas, line, 12, y, MenuPage.TEXT);
            y += pitch;
        }
        canvas.unclip();
        MenuPage.scrollbar(canvas, canvas.width() - MenuPage.MARGIN - 2, bodyTop,
                capacity * pitch, offset, capacity, lineCount);
        String position = lineCount == 0 ? "0 of 0"
                : (offset + 1) + "-" + (offset + visible.size()) + " of " + lineCount;
        NameFont.draw(canvas, position, 12, MenuPage.FOOTER_Y - NameFont.LINE_HEIGHT - 3, MenuPage.DIM);
        MenuPage.footer(canvas, "", MenuPage.TEXT, List.of(MenuPage.hint(MenuPage.ARROWS_UD, "Line"),
                MenuPage.hint(MenuPage.ARROWS_LR, "Page"), MenuPage.hint(MenuPage.back(pad), "Close")));
        return true;
    }

    /** Missing glyphs keep their exact identity in this bitmap-font reader. */
    public static String readable(String value) {
        StringBuilder out = new StringBuilder();
        value.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r' || NameFont.covers(cp)
                    && !Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT)
                out.appendCodePoint(cp);
            else out.append("[U+").append(Integer.toHexString(cp).toUpperCase(java.util.Locale.ROOT)).append(']');
        });
        return out.toString();
    }

    public int firstVisibleLine() { return offset; }

    /** The exact complete strings sent to the font on the last drawn page. */
    public List<String> visibleLines() { return visible; }
}
