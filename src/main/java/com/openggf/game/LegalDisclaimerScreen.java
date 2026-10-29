package com.openggf.game;

import com.openggf.i18n.UiMessages;
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

    /** Side margin in px on each edge. */
    private static final int SIDE_MARGIN = 20;

    /** Maximum width in px for a wrapped body line. */
    static final int BODY_MAX_WIDTH = SCREEN_W - (SIDE_MARGIN * 2);

    /** Select the authored compact font; glyphs remain on the native pixel grid. */
    static final float BODY_SCALE = 0.75f;

    /** Pixel advance from one body line to the next. */
    static final int BODY_LINE_HEIGHT = 8;

    /** Pixel gap inserted for each empty paragraph entry. */
    static final int BODY_PARAGRAPH_GAP = 10;

    /** First body line baseline Y. */
    static final int BODY_START_Y = 48;

    /** Dismiss prompt baseline Y. Body must not extend past this. */
    static final int PROMPT_Y = 210;

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



    /**
     * Historical English source fixture, one entry per paragraph. Runtime content is read only
     * from the source catalog; this fixture retains the monospaced wrapping regression.
     * Word-wrapped at draw time
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

    private String prompt = "";
    private long localeRevision = -1;
    private long textRevision = -1;
    private int bodyPage;
    private List<List<String>> bodyPages = List.of(List.of());
    private String confirmKey = "Enter";

    private final FadeManager fadeManager;
    private final com.openggf.configuration.SonicConfigurationService config;
    private LanguagePickerScreen languagePicker;
    private String languageKey = "F1";
    private final LegalDisclaimerState state = new LegalDisclaimerState();

    private TexturedQuadRenderer renderer;
    private PixelFont font;
    private int solidWhiteTextureId;
    private List<String> wrappedBodyLines;
    private int logicalWidth = SCREEN_W;

    public LegalDisclaimerScreen(FadeManager fadeManager) {
        this(fadeManager, null);
    }

    public LegalDisclaimerScreen(FadeManager fadeManager,
                                 com.openggf.configuration.SonicConfigurationService config) {
        this.fadeManager = Objects.requireNonNull(fadeManager, "fadeManager");
        this.config = config;
    }

    public void initialize() {
        try {
            renderer = new TexturedQuadRenderer();
            renderer.init();
            font = new MenuPixelFont();
            font.init("pixel-font.png", renderer);
            solidWhiteTextureId = SolidColorTexture.createWhite();
            refreshLocalizedBody();
            fadeManager.startFadeFromBlack(state::onFadeInComplete);
            LOGGER.info("Legal disclaimer screen initialized");
        } catch (IOException e) {
            LOGGER.severe("Failed to initialize legal disclaimer screen: " + e.getMessage());
            throw new RuntimeException("Failed to initialize legal disclaimer screen", e);
        }
    }

    public void update(InputHandler inputHandler) {
        refreshLocalizedBody();
        if (languagePicker != null) {
            if (languagePicker.update(inputHandler)) languagePicker = null;
            state.tick(false);
            return;
        }
        languageKey = MenuInput.detailsLabel(inputHandler);
        if (config != null && MenuInput.details(inputHandler)) {
            languagePicker = new LanguagePickerScreen(
                    config.getString(com.openggf.configuration.SonicConfiguration.UI_LANGUAGE),
                    tag -> UiLanguageSettings.save(config, tag));
            state.tick(false);
            return;
        }
        confirmKey = MenuInput.confirmLabel(inputHandler);
        PlayerInputState p1 = inputHandler.logical().player1();
        boolean p1LogicalDismiss = (p1.actionPressedMask() & InputActionMasks.ACTION_ALL) != 0
                || p1.startPressed();
        boolean keyPressed = inputHandler.isAnyKeyJustPressed()
                || p1LogicalDismiss;
        boolean movedBack = bodyPage > 0 && (MenuInput.left(inputHandler) || MenuInput.up(inputHandler));
        if (movedBack) { bodyPage--; keyPressed = false; }
        if (keyPressed && bodyPage + 1 < bodyPages.size()) {
            bodyPage++;
            keyPressed = false;
        }
        refreshPrompt();
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
        if (languagePicker != null) { languagePicker.render(font, logicalWidth); return; }

        // Solid black background (separate texture; not batched with text).
        renderer.drawTexture(solidWhiteTextureId, 0, 0, backdropWidthForWidth(logicalWidth), SCREEN_H,
                0f, 0f, 0f, 1f);

        // All text (header + body + prompt) shares the font atlas, so we
        // mega-batch into a single GL draw call.
        font.beginMegaBatch();

        refreshLocalizedBody();
        refreshPrompt();
        if (config != null) font.drawText(UiMessages.text("legal.language", java.util.Map.of("key", languageKey)),
                nativeOriginForWidth(logicalWidth) + 8, 7, BODY_SCALE, .7f, .8f, .95f, 1f);
        String header = UiMessages.text("legal.header");
        // Header (full scale)
        font.drawText(header, centeredTextXForWidth(logicalWidth, font.measureWidth(header)),
                22, 1f, 1f, 1f, 1f);

        // Body — iterate cached wrapped lines (computed once at init).
        int bodyY = BODY_START_Y + (font instanceof MenuPixelFont menu ? menu.lineMetrics(BODY_SCALE).above() : 0);
        ToIntFunction<String> measure = s -> font.measureWidth(s, BODY_SCALE);
        for (String line : bodyPages.get(bodyPage)) {
            if (line.isEmpty()) {
                bodyY += BODY_PARAGRAPH_GAP;
                continue;
            }
            int x = centeredTextXForWidth(logicalWidth, measure.applyAsInt(line));
            font.drawText(line, x, bodyY, BODY_SCALE, 0.95f, 0.95f, 0.95f, 1f);
            bodyY += bodyLineHeight();
        }

        // Dismiss prompt — fade-in and pulse share the same dismissible-phase
        // frame counter so they're aligned. Pulse starts at its trough
        // (brightness 0.2) and rises with the fade-in for a clean bloom up
        // to peak, then settles into the 0.2..1.0 cycle.
        if (state.isDismissPromptVisible()) {
            int dismissibleFrames = state.getDismissibleFrameCounter();
            float fadeIn = dismissibleFrames >= PROMPT_FADE_IN_FRAMES
                    ? 1.0f
                    : dismissibleFrames / (float) PROMPT_FADE_IN_FRAMES;
            float pulse = 0.6f - 0.4f * (float) Math.cos(dismissibleFrames * PROMPT_PULSE_OMEGA);
            float brightness = pulse * fadeIn;
            font.drawText(prompt, centeredTextXForWidth(logicalWidth, font.measureWidth(prompt)),
                    PROMPT_Y, brightness, brightness, brightness, 1f);
        }

        font.endMegaBatch();
    }

    private int bodyLineHeight() {
        return font instanceof MenuPixelFont menu ? menu.lineHeight(BODY_SCALE) : BODY_LINE_HEIGHT;
    }

    private void refreshLocalizedBody() {
        long locale = UiMessages.revision();
        long metrics = com.openggf.graphics.text.HostTextContext.revision();
        if (font == null || locale == localeRevision && metrics == textRevision && wrappedBodyLines != null) return;
        if (!(font instanceof MenuPixelFont) && wrappedBodyLines != null) {
            bodyPages = paginateBody(wrappedBodyLines, bodyLineHeight());
            localeRevision = locale;
            textRevision = metrics;
            return;
        }
        wrappedBodyLines = com.openggf.graphics.text.HostText.shared().wrap(UiMessages.text("legal.body"),
                BODY_MAX_WIDTH, com.openggf.graphics.text.TextFace.COMPACT,
                com.openggf.graphics.text.HostTextContext.current());
        bodyPages = paginateBody(wrappedBodyLines, bodyLineHeight());
        bodyPage = Math.clamp(bodyPage, 0, bodyPages.size() - 1);
        localeRevision = locale;
        textRevision = metrics;
        refreshPrompt();
    }

    private void refreshPrompt() {
        prompt = bodyPage + 1 < bodyPages.size()
                ? UiMessages.text("legal.next_page", java.util.Map.of("key", confirmKey,
                        "page", bodyPage + 1, "count", bodyPages.size()))
                : UiMessages.text("legal.continue", java.util.Map.of("key", confirmKey));
    }

    /** Measured-pitch pagination keeps every wrapped line reachable above the prompt. */
    static List<List<String>> paginateBody(List<String> lines, int lineHeight) {
        int available = PROMPT_Y - BODY_START_Y - Math.max(8, lineHeight);
        List<List<String>> pages = new ArrayList<>();
        List<String> page = new ArrayList<>();
        int height = 0;
        for (String line : lines) {
            int pitch = line.isEmpty() ? BODY_PARAGRAPH_GAP : lineHeight;
            if (!page.isEmpty() && height + pitch > available) {
                pages.add(List.copyOf(page));
                page.clear();
                height = 0;
            }
            page.add(line);
            height += pitch;
        }
        if (!page.isEmpty() || pages.isEmpty()) pages.add(List.copyOf(page));
        return List.copyOf(pages);
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
        int next = Math.max(SCREEN_W, logicalWidth);
        if (this.logicalWidth != next) {
            this.logicalWidth = next;
            localeRevision = -1;
        }
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
