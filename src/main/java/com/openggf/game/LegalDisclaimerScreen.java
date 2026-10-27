package com.openggf.game;

import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.control.PlayerInputState;
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
 * Legal disclaimer screen shown on engine startup before the master title
 * screen. Renders a white-text-on-black layout, gates dismissal for 5 s,
 * and chains entry/exit fades through {@link FadeManager}.
 */
public class LegalDisclaimerScreen {

    private static final Logger LOGGER = Logger.getLogger(LegalDisclaimerScreen.class.getName());
    private static final int SCREEN_W = 320;
    private static final int SCREEN_H = 224;

    /** Side margin in px on each edge: the body panel's inset plus its padding. */
    private static final int SIDE_MARGIN = 22;

    /** Maximum width in px for a wrapped body line. */
    static final int BODY_MAX_WIDTH = SCREEN_W - (SIDE_MARGIN * 2);

    /** Select the authored compact font; glyphs remain on the native pixel grid. */
    static final float BODY_SCALE = 0.75f;

    /** Pixel advance from one body line to the next. */
    static final int BODY_LINE_HEIGHT = 10;

    /** Pixel gap inserted for each empty paragraph entry. */
    static final int BODY_PARAGRAPH_GAP = 6;

    /** First body line baseline Y. */
    static final int BODY_START_Y = 41;

    /** Dismiss prompt baseline Y. Body must not extend past this. */
    static final int PROMPT_Y = MenuChrome.FOOTER_Y;

    /**
     * Frames over which the dismiss prompt ramps from invisible to fully
     * visible after entering {@link LegalDisclaimerState.Phase#DISMISSIBLE},
     * before the pulse cycle drives brightness. 30 frames = 0.5 s at 60 fps.
     */
    static final int PROMPT_FADE_IN_FRAMES = 30;

    /** Pulse cycle period (frames). 90 frames = 1.5 s at 60 fps. */
    static final int PROMPT_PULSE_PERIOD_FRAMES = 90;

    private static final double PROMPT_PULSE_OMEGA =
            2.0 * Math.PI / PROMPT_PULSE_PERIOD_FRAMES;

    private static final String HEADER = "LEGAL NOTICE";

    /**
     * Disclaimer body, one entry per paragraph. Word-wrapped at draw time
     * by {@link #wrapParagraph} so the layout survives font-metric changes.
     * Empty string entries produce a blank line between paragraphs.
     * Package-private so {@link TestLegalDisclaimerTextWrap} can verify
     * the wrapped body fits before the dismiss prompt.
     */
    static final String[] BODY_PARAGRAPHS = {
        "OpenGGF is an independent, open-source reimplementation of the original Sonic the Hedgehog games for the Sega Mega Drive / Genesis. It is developed and verified against community-maintained disassemblies.",
        "",
        "No copyrighted Sega assets are distributed with this engine. ROM data, sprites, music, and all other game assets must be supplied by the user from a legally obtained copy.",
        "",
        "This project is not affiliated with or endorsed by Sega. Sonic the Hedgehog and all related characters, names, and trademarks are the property of Sega Corporation, to which no claim is made."
    };

    private String confirmKey = "Enter";
    private boolean controller;

    private final FadeManager fadeManager;
    private final LegalDisclaimerState state = new LegalDisclaimerState();

    private TexturedQuadRenderer renderer;
    private PixelFont font;
    private int solidWhiteTextureId;
    private List<String> wrappedBodyLines;
    private int logicalWidth = SCREEN_W;

    public LegalDisclaimerScreen(FadeManager fadeManager) {
        this.fadeManager = Objects.requireNonNull(fadeManager, "fadeManager");
    }

    public void initialize() {
        try {
            renderer = new TexturedQuadRenderer();
            renderer.init();
            font = new MenuPixelFont();
            font.init("pixel-font.png", renderer);
            solidWhiteTextureId = SolidColorTexture.createWhite();
            ToIntFunction<String> measure = s -> font.measureWidth(s, BODY_SCALE);
            wrappedBodyLines = new ArrayList<>();
            for (String paragraph : BODY_PARAGRAPHS) {
                if (paragraph.isEmpty()) {
                    wrappedBodyLines.add("");
                } else {
                    wrappedBodyLines.addAll(wrapParagraph(paragraph, measure, BODY_MAX_WIDTH));
                }
            }
            fadeManager.startFadeFromBlack(state::onFadeInComplete);
            LOGGER.info("Legal disclaimer screen initialized");
        } catch (IOException e) {
            LOGGER.severe("Failed to initialize legal disclaimer screen: " + e.getMessage());
            throw new RuntimeException("Failed to initialize legal disclaimer screen", e);
        }
    }

    public void update(InputHandler inputHandler) {
        confirmKey = MenuInput.confirmLabel(inputHandler);
        controller = MenuInput.controller(inputHandler);
        PlayerInputState p1 = inputHandler.logical().player1();
        boolean p1LogicalDismiss = (p1.actionPressedMask() & InputActionMasks.ACTION_ALL) != 0
                || p1.startPressed();
        boolean keyPressed = inputHandler.isAnyKeyJustPressed()
                || p1LogicalDismiss;
        boolean enteredExiting = state.tick(keyPressed);
        if (enteredExiting) {
            fadeManager.startFadeToBlack(state::onFadeOutComplete);
        }
    }

    public void draw() {
        if (renderer == null) {
            return;
        }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        drawContent();
    }

    /** Package-private command seam for layout tests without an OpenGL context. */
    void drawForTesting() {
        drawContent();
    }

    private void drawContent() {

        // Solid black underlay (separate texture; not batched with text) so fades start from black.
        renderer.drawTexture(solidWhiteTextureId, 0, 0, backdropWidthForWidth(logicalWidth), SCREEN_H,
                0f, 0f, 0f, 1f);

        // Page chrome, body and prompt share the font atlas, so one mega-batch draws them.
        font.beginMegaBatch();
        MenuChrome.background(font, backdropWidthForWidth(logicalWidth));
        MenuChrome.header(font, backdropWidthForWidth(logicalWidth), HEADER, null);
        int origin = nativeOriginForWidth(logicalWidth);
        MenuChrome.descriptionBox(font, origin + 12, BODY_START_Y - 6, SCREEN_W - 24,
                PROMPT_Y - BODY_START_Y - 1, List.of(), List.of());

        // Body: left-aligned paragraphs with open leading, read like a page rather than a poster.
        int bodyY = BODY_START_Y;
        for (String line : wrappedBodyLines) {
            if (line.isEmpty()) {
                bodyY += BODY_PARAGRAPH_GAP;
                continue;
            }
            font.drawText(line, origin + SIDE_MARGIN, bodyY, BODY_SCALE,
                    MenuChrome.TEXT[0], MenuChrome.TEXT[1], MenuChrome.TEXT[2], 1f);
            bodyY += BODY_LINE_HEIGHT;
        }

        MenuChrome.fill(font, 0, MenuChrome.FOOTER_Y, backdropWidthForWidth(logicalWidth), MenuChrome.FOOTER_H,
                MenuChrome.INK);
        MenuChrome.fill(font, 0, MenuChrome.FOOTER_Y, backdropWidthForWidth(logicalWidth), 1,
                new float[] {.11f, .16f, .37f});
        // The prompt fades in once dismissal is allowed, then breathes gently.
        if (state.isDismissPromptVisible()) {
            int dismissibleFrames = state.getDismissibleFrameCounter();
            float fadeIn = dismissibleFrames >= PROMPT_FADE_IN_FRAMES
                    ? 1.0f
                    : dismissibleFrames / (float) PROMPT_FADE_IN_FRAMES;
            MenuChrome.footer(font, backdropWidthForWidth(logicalWidth), null,
                    List.of(MenuChrome.hint(confirmKey, "CONTINUE")), controller);
            float pulse = .5f + .5f * (float) Math.cos(dismissibleFrames * PROMPT_PULSE_OMEGA);
            float veil = 1f - fadeIn * (.75f + .25f * pulse);
            if (veil > 0) MenuChrome.fill(font, 0, MenuChrome.FOOTER_Y + 1, backdropWidthForWidth(logicalWidth),
                    MenuChrome.FOOTER_H - 1, MenuChrome.INK, veil);
        }

        font.endMegaBatch();
    }

    /**
     * Greedy word-wrap. Decoupled from {@link PixelFont} so it can be tested
     * without OpenGL: the caller supplies the per-string width measure.
     * Words longer than {@code maxWidth} get their own line (do not loop or
     * truncate). An empty input returns a single empty line so the caller
     * can preserve paragraph break spacing.
     */
    static List<String> wrapParagraph(String paragraph,
                                      ToIntFunction<String> measure,
                                      int maxWidth) {
        if (paragraph.isEmpty()) {
            return List.of("");
        }
        List<String> out = new ArrayList<>();
        String[] words = paragraph.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
                continue;
            }
            String candidate = current + " " + word;
            if (measure.applyAsInt(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                out.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    /** Supplies the current logical width for the next legal-screen draw. */
    public void setViewportWidth(int logicalWidth) {
        this.logicalWidth = Math.max(SCREEN_W, logicalWidth);
    }

    static int nativeOriginForWidth(int logicalWidth) {
        return (logicalWidth - SCREEN_W) / 2;
    }

    static int backdropWidthForWidth(int logicalWidth) {
        return Math.max(SCREEN_W, logicalWidth);
    }

    static int centeredTextXForWidth(int logicalWidth, int textWidth) {
        return nativeOriginForWidth(logicalWidth) + (SCREEN_W - textWidth) / 2;
    }

    public void setProjectionMatrix(float[] projectionMatrix) {
        if (renderer != null && projectionMatrix != null) {
            renderer.setProjectionMatrix(projectionMatrix);
        }
    }

    public boolean isDismissed() {
        return state.isDismissed();
    }

    public void cleanup() {
        if (font != null) {
            font.cleanup();
        }
        PngTextureLoader.deleteTexture(solidWhiteTextureId);
        if (renderer != null) {
            renderer.cleanup();
        }
        LOGGER.info("Legal disclaimer screen cleaned up");
    }
}
