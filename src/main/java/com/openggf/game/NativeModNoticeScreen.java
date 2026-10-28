package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.PngTextureLoader;
import com.openggf.graphics.TexturedQuadRenderer;
import com.openggf.graphics.SolidColorTexture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glEnable;

/**
 * Boot screen shown on a native build when enabled code-bearing mods cannot load.
 * The notice fades in, dismisses on input, and fades back to black before boot
 * continues.
 */
public final class NativeModNoticeScreen {
    private static final Logger LOGGER = Logger.getLogger(NativeModNoticeScreen.class.getName());
    public static final int MAX_VISIBLE_MOD_LINES = 12;
    private static final int SCREEN_W = 320;
    private static final int SCREEN_H = 224;
    private static final int BODY_MAX_WIDTH = SCREEN_W - 58;
    // Ten lines leave room under the panel for the next-step line above the footer.
    private static final int MAX_RENDERED_LINES = 10;
    static final float BODY_SCALE = 0.75f;
    private int page;
    private int logicalWidth = SCREEN_W;
    private InputHandler menuInput;

    private final FadeManager fadeManager;
    private final List<String> noticeLines;
    private boolean dismissed;
    private boolean fadingOut;

    private TexturedQuadRenderer renderer;
    private PixelFont font;
    private int solidWhiteTextureId;
    private List<String> wrappedNoticeLines = List.of();

    public NativeModNoticeScreen(FadeManager fadeManager, List<String> noticeLines) {
        this.fadeManager = Objects.requireNonNull(fadeManager, "fadeManager");
        this.noticeLines = List.copyOf(noticeLines);
        wrappedNoticeLines = wrapLines(this.noticeLines,
                line -> MenuPixelFont.textWidth(line, BODY_SCALE),
                BODY_MAX_WIDTH, MAX_RENDERED_LINES);
    }

    public void initialize() {
        try {
            renderer = new TexturedQuadRenderer();
            renderer.init();
            font = new MenuPixelFont();
            font.init("pixel-font.png", renderer);
            wrappedNoticeLines = wrapLines(noticeLines,
                    line -> font.measureWidth(line, BODY_SCALE),
                    BODY_MAX_WIDTH, MAX_RENDERED_LINES);
            solidWhiteTextureId = SolidColorTexture.createWhite();
            fadeManager.startFadeFromBlack(null);
            LOGGER.info("Native mod notice screen initialized");
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize native mod notice screen", e);
        }
    }

    public void update(InputHandler inputHandler) {
        if (dismissed) {
            return;
        }
        menuInput = inputHandler;
        if (fadingOut) return;
        int delta = MenuInput.up(inputHandler) || MenuInput.left(inputHandler) ? -1
                : MenuInput.down(inputHandler) || MenuInput.right(inputHandler) ? 1 : 0;
        page = Math.clamp(page + delta, 0, pageCount() - 1);
        if (MenuInput.accept(inputHandler)) {
            if (page + 1 < pageCount()) page++;
            else {
                fadingOut = true;
                fadeManager.startFadeToBlack(() -> dismissed = true);
            }
        }
    }

    int pageCount() { return Math.max(1, (wrappedNoticeLines.size() + MAX_RENDERED_LINES - 1) / MAX_RENDERED_LINES); }
    int currentPage() { return page; }

    public boolean isDismissed() {
        return dismissed;
    }

    public void draw() {
        if (renderer == null) {
            return;
        }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        renderer.drawTexture(solidWhiteTextureId, 0, 0, logicalWidth, SCREEN_H,
                0f, 0f, 0f, 1f);
        font.beginMegaBatch();
        MenuChrome.background(font, logicalWidth);
        MenuChrome.header(font, logicalWidth, "MODS NOT LOADED", "NATIVE BUILD");
        int origin = (logicalWidth - SCREEN_W) / 2;
        int introLines = noticeLines.isEmpty() ? 0 : wrapLine(noticeLines.getFirst(),
                line -> font.measureWidth(line, BODY_SCALE), BODY_MAX_WIDTH).size();
        int first = page * MAX_RENDERED_LINES;
        // The panel fits this page's lines (plus the gap before the mod list) instead of a fixed tall box.
        int shown = Math.min(wrappedNoticeLines.size(), first + MAX_RENDERED_LINES) - first;
        boolean listGap = first <= introLines && first + shown > introLines;
        int boxHeight = Math.min(160, Math.max(40, shown * 12 + (listGap ? 4 : 0) + 12));
        MenuChrome.descriptionBox(font, origin + 12, 36, SCREEN_W - 24, boxHeight, List.of(), List.of());
        if (page + 1 == pageCount()) {
            // The next step, under the list on the last page, wrapped by measured width.
            int stepY = 36 + boxHeight + 6;
            for (String line : MenuStyle.wrap(com.openggf.mods.NativeUnsupportedMods.NEXT_STEP, SCREEN_W - 36, MenuStyle.COMPACT)) {
                MenuChrome.compact(font, line, origin + 18, stepY, MenuChrome.CYAN);
                stepY += MenuChrome.DESCRIPTION_PITCH;
            }
        }
        int y = 42;
        for (int i = first; i < Math.min(wrappedNoticeLines.size(), first + MAX_RENDERED_LINES); i++) {
            String line = wrappedNoticeLines.get(i);
            if (i < introLines) {
                if (i == 0) MenuChrome.icon(font, MenuChrome.Icon.WARNING, origin + 18, y, MenuChrome.AMBER);
                font.drawText(line, origin + 29, y, BODY_SCALE, MenuChrome.TEXT[0], MenuChrome.TEXT[1],
                        MenuChrome.TEXT[2], 1f);
            } else {
                if (i == introLines) y += 4;
                MenuChrome.icon(font, MenuChrome.Icon.PUZZLE, origin + 29, y, MenuChrome.DIM);
                font.drawText(line, origin + 40, y, BODY_SCALE, 1f, 1f, 1f, 1f);
            }
            y += 12;
        }
        String confirm = menuInput == null ? "Enter" : MenuInput.confirmLabel(menuInput);
        List<MenuChrome.Hint> hints = new ArrayList<>();
        if (pageCount() > 1) hints.add(MenuChrome.leftRight(menuInput, "PAGE " + (page + 1) + "/" + pageCount()));
        hints.add(MenuChrome.hint(confirm, page + 1 < pageCount() ? "NEXT PAGE" : "CONTINUE"));
        MenuChrome.footer(font, logicalWidth, null, hints, MenuChrome.controller(menuInput));
        font.endMegaBatch();
    }

    static List<String> wrapLines(List<String> lines,
                                  ToIntFunction<String> measure,
                                  int maxWidth,
                                  int maxLines) {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(measure, "measure");
        if (maxWidth <= 0 || maxLines <= 0) {
            throw new IllegalArgumentException("Wrap dimensions must be positive");
        }

        List<String> wrapped = new ArrayList<>();
        for (String line : lines) {
            wrapped.addAll(wrapLine(Objects.requireNonNull(line, "line"), measure, maxWidth));
        }
        // The viewport is paged: keep every wrapped line for later pages.
        return List.copyOf(wrapped);
    }

    private static List<String> wrapLine(String line,
                                         ToIntFunction<String> measure,
                                         int maxWidth) {
        if (line.isBlank()) {
            return List.of("");
        }
        List<String> wrapped = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : line.trim().split("\\s+")) {
            if (measure.applyAsInt(word) > maxWidth) {
                flush(current, wrapped);
                splitOversizedWord(word, measure, maxWidth, wrapped);
                continue;
            }
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (measure.applyAsInt(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                flush(current, wrapped);
                current.append(word);
            }
        }
        flush(current, wrapped);
        return wrapped;
    }

    private static void splitOversizedWord(String word,
                                           ToIntFunction<String> measure,
                                           int maxWidth,
                                           List<String> output) {
        // Split by whole code points so a surrogate pair is never divided across lines.
        StringBuilder chunk = new StringBuilder();
        for (int index = 0; index < word.length(); ) {
            int next = word.codePointAt(index);
            index += Character.charCount(next);
            if (!chunk.isEmpty() && measure.applyAsInt(chunk.toString() + Character.toString(next)) > maxWidth) {
                output.add(chunk.toString());
                chunk.setLength(0);
            }
            chunk.appendCodePoint(next);
        }
        flush(chunk, output);
    }

    private static void flush(StringBuilder line, List<String> output) {
        if (!line.isEmpty()) {
            output.add(line.toString());
            line.setLength(0);
        }
    }

    public void setViewportWidth(int logicalWidth) { this.logicalWidth = Math.max(SCREEN_W, logicalWidth); }

    public void setProjectionMatrix(float[] projectionMatrix) {
        if (renderer != null && projectionMatrix != null) {
            renderer.setProjectionMatrix(projectionMatrix);
        }
    }

    public void cleanup() {
        if (font != null) {
            font.cleanup();
        }
        PngTextureLoader.deleteTexture(solidWhiteTextureId);
        if (renderer != null) {
            renderer.cleanup();
        }
    }

}
