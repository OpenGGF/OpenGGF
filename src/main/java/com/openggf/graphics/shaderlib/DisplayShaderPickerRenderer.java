package com.openggf.graphics.shaderlib;

import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.TexturedQuadRenderer;

import java.io.IOException;
import java.util.List;

import static com.openggf.game.MenuChrome.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;

/** Render-thread-owned chrome for the in-game picker. Selection and input stay in the controller. */
public final class DisplayShaderPickerRenderer implements AutoCloseable {
    static final int ROWS = 5;
    private static final int ROW_TOP = 79;
    private static final int ROW_HEIGHT = 17;
    private MenuPixelFont font;
    private TexturedQuadRenderer renderer;
    private int frame;

    public void render(DisplayShaderPickerController picker, DisplayShaderPresetRef active,
                       String libraryRoot, String status, boolean downloading, float[] projection, int width) {
        if (picker == null || !picker.isOpen()) return;
        ensureResources();
        renderer.setProjectionMatrix(projection);
        // Gameplay passes may leave blending disabled. Own the translucent-overlay state,
        // then return it to the next display-shader pass (including separate alpha factors).
        boolean blended = glIsEnabled(GL_BLEND), depth = glIsEnabled(GL_DEPTH_TEST);
        int sourceRgb = glGetInteger(GL_BLEND_SRC_RGB), destinationRgb = glGetInteger(GL_BLEND_DST_RGB);
        int sourceAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), destinationAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        glEnable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        font.beginMegaBatch();
        try {
            draw(picker, active, libraryRoot, status, downloading, width);
        } finally {
            try { font.endMegaBatch(); }
            finally {
                glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
                if (!blended) glDisable(GL_BLEND);
                if (depth) glEnable(GL_DEPTH_TEST);
            }
        }
    }

    private void draw(DisplayShaderPickerController picker, DisplayShaderPresetRef active,
                      String root, String status, boolean downloading, int width) {
        int x = 12, w = width - 24;
        fill(font, 0, 0, width, SCREEN_H, INK, .28f);
        fill(font, x + w, 13, 3, 188, INK, .55f);
        fill(font, x + 3, 198, w - 3, 3, INK, .55f);
        fill(font, x, 10, w, 188, NAVY, .82f);
        outline(font, x, 10, w, 188, EDGE, 1f, 1);
        fill(font, x + 1, 11, w - 2, 24, HEAD);
        fill(font, x + 1, 35, w - 2, 1, GOLD);
        label(font, "DISPLAY SHADER", x + 9, 18, GOLD);
        tag(font, x + w - tagWidth("LIBRARY") - 8, 17, "LIBRARY", CYAN);
        icon(font, Icon.FOLDER, x + 9, 44, GOLD);
        compact(font, breadcrumb(root, picker.currentFolder(), w - 38), x + 23, 43, MUTED);
        fill(font, x + 8, 57, w - 16, 15, INK, .9f);
        compact(font, "Filter:", x + 12, 61, MUTED);
        String query = picker.query();
        compact(font, query.isEmpty() ? "Type to filter" : tail(query, w - 74),
                x + 60, 61, query.isEmpty() ? DIM : TEXT);

        var items = picker.visibleItems();
        int selected = items.indexOf(picker.selectedItem());
        int first = firstRow(selected, items.size());
        int end = Math.min(items.size(), first + ROWS);
        for (int i = first; i < end; i++) {
            int y = ROW_TOP + (i - first) * ROW_HEIGHT;
            var item = items.get(i);
            boolean focused = i == selected;
            boolean applied = isActive(item, active);
            if (focused) focus(font, x + 9, y, w - 27, 13, frame);
            icon(font, item.directory() ? Icon.FOLDER : applied ? Icon.CHECK : Icon.FILE,
                    x + 14, y + 3, item.directory() ? GOLD : applied ? GREEN : CYAN);
            String badge = applied ? "ACTIVE" : item.parentDirectory() ? "UP" : item.directory() ? "OPEN" : item.ref().kind().name();
            // Off is a system option, not a file format.
            if (item.ref() != null && item.ref().kind() == DisplayShaderPresetRef.Kind.OFF && !applied) badge = "";
            int badgeW = badge.isEmpty() ? 0 : tagWidth(badge);
            compact(font, fitCompact(rowLabel(item), w - 61 - badgeW), x + 28, y + 3,
                    focused ? WHITE : TEXT);
            if (!badge.isEmpty()) tag(font, x + w - 24 - badgeW, y + 2, badge, applied ? GREEN : MUTED);
        }
        scrollbar(font, x + w - 10, ROW_TOP, ROWS * ROW_HEIGHT - 4, first, ROWS, items.size());

        boolean onlySystemRow = items.size() == 1;
        if (onlySystemRow) {
            boolean emptyLibrary = picker.currentFolder().isEmpty() && query.isEmpty();
            icon(font, Icon.INFO, x + 14, 109, CYAN);
            compact(font, emptyLibrary ? "No shader presets installed" : "No matching presets or folders",
                    x + 28, 108, TEXT);
            compact(font, emptyLibrary ? "Download the libretro pack below." : "Try a different filter.",
                    x + 14, 123, MUTED);
        }
        String detail = status == null || status.isBlank()
                ? rowDetail(picker.selectedItem(), first, end, items.size()) : status;
        compact(font, fitCompact(detail, w - 20), x + 10, 167, downloading ? CYAN : MUTED);
        fill(font, x + 1, 179, w - 2, 18, INK, .94f);
        List<Hint> tools = query.isEmpty() && picker.currentFolder().isEmpty()
                ? List.of(hint("F5", "DOWNLOAD"), hint("A-Z", "FILTER"))
                : List.of(hint("F5", "DOWNLOAD"), hint("Bksp", query.isEmpty() ? "UP" : "DELETE"));
        hints(font, x + 9, 182, tools, false, MUTED);
        footer(font, width, "", List.of(hint(ARROWS_UD, "MOVE"),
                hint("Enter", confirmLabel(picker.selectedItem())), hint("Esc", "CLOSE")), false);
        frame++;
    }

    static int firstRow(int selected, int total) {
        return Math.max(0, Math.min(selected - ROWS / 2, total - ROWS));
    }

    static String confirmLabel(DisplayShaderSelectionModel.SelectionItem item) {
        return item.directory() ? item.parentDirectory() ? "UP" : "OPEN" : "APPLY";
    }

    static boolean isActive(DisplayShaderSelectionModel.SelectionItem item, DisplayShaderPresetRef active) {
        if (item.directory() || item.ref() == null) return false;
        if (active == null) active = DisplayShaderPresetRef.OFF;
        if (item.ref().kind() == DisplayShaderPresetRef.Kind.OFF || active.kind() == DisplayShaderPresetRef.Kind.OFF)
            return item.ref().kind() == active.kind();
        return item.ref().relativePath().equalsIgnoreCase(active.relativePath());
    }

    static String rowLabel(DisplayShaderSelectionModel.SelectionItem item) {
        if (item.parentDirectory()) return "Parent folder";
        String name = item.directory() ? item.displayPath().replaceAll("/+$", "") : item.ref().shortLabel();
        name = name.replace('_', ' ').replace('-', ' ');
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String rowDetail(DisplayShaderSelectionModel.SelectionItem item, int first, int end, int total) {
        String position = (first + 1) + "-" + end + " / " + total;
        return position + "  " + (item.directory() ? "Folder" : item.ref().kind() == DisplayShaderPresetRef.Kind.OFF
                ? "Original game image" : item.displayPath());
    }

    /** Retain the library anchor and nearest folder, dropping middle ancestors first. */
    static String breadcrumb(String root, String folder, int pixels) {
        root = root == null || root.isBlank() ? "shaders" : root.trim().replace('\\', '/');
        root = root.replaceAll("/+$", "");
        if (root.isEmpty()) root = "/";
        int slash = root.lastIndexOf('/');
        String anchor = slash >= 0 && slash + 1 < root.length() ? root.substring(slash + 1) : root;
        String path = folder == null ? "" : folder.replace('\\', '/');
        String full = anchor + (path.isBlank() ? "" : " / " + path.replace("/", " / "));
        int cells = Math.max(0, pixels / COMPACT_ADVANCE);
        if (full.length() <= cells) return full;
        String[] parts = path.split("/");
        String nearest = parts[parts.length - 1];
        String prefix = anchor + (parts.length > 1 ? " / ... / " : " / ");
        if (prefix.length() + 5 > cells) return middle(full, cells);
        String tail = nearest;
        for (int i = parts.length - 2; i > 0; i--) {
            String candidate = parts[i] + " / " + tail;
            if (prefix.length() + candidate.length() > cells) break;
            tail = candidate;
        }
        return prefix + middle(tail, cells - prefix.length());
    }

    private static String middle(String text, int cells) {
        if (text.length() <= cells) return text;
        if (cells <= 3) return ".".repeat(Math.max(0, cells));
        int left = (cells - 3 + 1) / 2;
        return text.substring(0, left) + "..." + text.substring(text.length() - (cells - 3 - left));
    }

    private static String tail(String text, int pixels) {
        int cells = Math.max(4, pixels / COMPACT_ADVANCE);
        return text.length() <= cells ? text : "..." + text.substring(text.length() - cells + 3);
    }

    private void ensureResources() {
        if (renderer != null) return;
        var created = new TexturedQuadRenderer();
        var createdFont = new MenuPixelFont();
        try {
            created.init();
            createdFont.init("pixel-font.png", created);
            renderer = created;
            font = createdFont;
        } catch (IOException | RuntimeException | Error failure) {
            createdFont.cleanup();
            created.cleanup();
            throw new IllegalStateException("Unable to render display shader picker", failure);
        }
    }

    /** Must run on the GL owner thread, before its context is destroyed. Safe before first render. */
    @Override public void close() {
        if (font != null) { font.cleanup(); font = null; }
        if (renderer != null) { renderer.cleanup(); renderer = null; }
        frame = 0;
    }
}
