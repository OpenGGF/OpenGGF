package com.openggf.editor.hud;

/**
 * Window-resolution layout for the controller-first editor HUD. UI units are a whole number of
 * framebuffer pixels, chosen so the HUD always has at least 640x360 units: the engine's 9x10 menu
 * font then reads at 18-30 screen pixels on ordinary 720p-4K displays, which suits a couch as well
 * as a desk. The game keeps its own integer scale inside the stage, never under a panel.
 */
public final class EditorHudLayout {
    public record Rect(float x, float y, float w, float h) {
        public float right() { return x + w; }
        public float bottom() { return y + h; }
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; }
        public Rect inset(float d) { return new Rect(x + d, y + d, Math.max(0, w - 2 * d), Math.max(0, h - 2 * d)); }
    }

    static final int TOP_BAR = 22;
    static final int PROMPT_BAR = 18;
    static final int DOCK = 60;
    static final int GAP = 6;
    /** Narrowest workbench that still shows an 8x8 block at 32 units per cell beside its details. */
    static final int MIN_WORKBENCH = 300;

    private final int uiScale;
    private final int width;
    private final int height;
    private final Rect stage;
    private final Rect workbench;
    private final int[] gameViewport;
    private final Rect game;

    private EditorHudLayout(int uiScale, int width, int height, Rect stage, Rect workbench, int[] gameViewport) {
        this.uiScale = uiScale;
        this.width = width;
        this.height = height;
        this.stage = stage;
        this.workbench = workbench;
        this.gameViewport = gameViewport;
        this.game = new Rect(gameViewport[0] / (float) uiScale, gameViewport[1] / (float) uiScale,
                gameViewport[2] / (float) uiScale, gameViewport[3] / (float) uiScale);
    }

    /** Largest integer UI scale that keeps at least 640x360 units. */
    public static int uiScale(int framebufferWidth, int framebufferHeight) {
        return Math.max(1, Math.min(framebufferWidth / 640, framebufferHeight / 360));
    }

    /**
     * Lays out one frame. {@code workbench} selects the composition layout (block/chunk editing),
     * which shrinks the game to the largest integer scale that leaves room for the workbench.
     */
    public static EditorHudLayout of(int framebufferWidth, int framebufferHeight, int logicalWidth, int logicalHeight,
                                     boolean workbench) {
        int scale = uiScale(framebufferWidth, framebufferHeight);
        int width = framebufferWidth / scale;
        int height = framebufferHeight / scale;
        float stageTop = TOP_BAR + GAP;
        float stageBottom = height - PROMPT_BAR - DOCK - GAP;
        Rect stage = new Rect(GAP, stageTop, Math.max(1, width - 2 * GAP), Math.max(1, stageBottom - stageTop));
        int maxScale = Math.max(1, Math.min((int) (stage.w() * scale) / Math.max(1, logicalWidth),
                (int) (stage.h() * scale) / Math.max(1, logicalHeight)));
        int gameScale = maxScale;
        Rect bench = null;
        float gameAreaX = stage.x();
        float gameAreaW = stage.w();
        if (workbench) {
            gameScale = 1;
            for (int s = maxScale; s >= 1; s--) {
                float gameUnits = logicalWidth * s / (float) scale;
                if (stage.w() - gameUnits - GAP >= MIN_WORKBENCH) { gameScale = s; break; }
            }
            float gameUnits = logicalWidth * gameScale / (float) scale;
            float benchW = Math.min(stage.w() - gameUnits - GAP, 460);
            float used = gameUnits + GAP + benchW;
            gameAreaX = stage.x() + (stage.w() - used) / 2f;
            gameAreaW = gameUnits;
            bench = new Rect(gameAreaX + gameUnits + GAP, stage.y(), benchW, stage.h());
        }
        int gameW = logicalWidth * gameScale;
        int gameH = logicalHeight * gameScale;
        int gameX = Math.round(gameAreaX * scale + (gameAreaW * scale - gameW) / 2f);
        int gameY = Math.round(stage.y() * scale + (stage.h() * scale - gameH) / 2f);
        return new EditorHudLayout(scale, width, height, stage, bench, new int[] {gameX, gameY, gameW, gameH, gameScale});
    }

    public int uiScale() { return uiScale; }
    public int width() { return width; }
    public int height() { return height; }
    public Rect topBar() { return new Rect(0, 0, width, TOP_BAR); }
    public Rect dock() { return new Rect(0, height - PROMPT_BAR - DOCK, width, DOCK); }
    public Rect promptBar() { return new Rect(0, height - PROMPT_BAR, width, PROMPT_BAR); }
    public Rect stage() { return stage; }
    /** Composition workbench beside the game, or null at world depth. */
    public Rect workbench() { return workbench; }
    /** Game rectangle in UI units (top-left origin). */
    public Rect game() { return game; }
    /** Modal sheets cover the stage and dock, leaving the top bar and button prompts visible. */
    public Rect sheet() { return new Rect(GAP * 2, TOP_BAR + GAP, width - GAP * 4, height - TOP_BAR - PROMPT_BAR - GAP * 2); }

    /** Game viewport {x, y, width, height, scale} in framebuffer pixels with a top-left origin. */
    public int[] gameViewport() { return gameViewport.clone(); }
}
