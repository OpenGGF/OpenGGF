package com.openggf.game.soniccd;

import com.openggf.camera.Camera;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.mode.GameplayFrameDisposition;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.util.Arrays;

/** Existing controlled-frame and terrain-renderer adapters; no native clock or second runner. */
final class SonicCdStaticPresentation implements GameplayFrameController, ScrollHandlerProvider {
    private final SonicCdGameModule module;
    private CdR11aAssets assets;
    private int horizontalOffset;
    private final ZoneScrollHandler scroll = new ZoneScrollHandler() {
        @Override public void update(int[] output, int cameraX, int cameraY, int frameCounter, int actId) {
            if (actId != 0) throw SonicCd.unsupported("background act " + actId);
            // FG follows the development camera; BG remains fixed at the decoded layout origin.
            // No native InitLevelScroll/ScrollBG algorithm or time-dependent parallax is implied.
            Arrays.fill(output, ((-cameraX) & 0xFFFF) << 16);
            horizontalOffset = cameraX;
        }
        @Override public short getVscrollFactorBG() { return 0; }
        @Override public int getMinScrollOffset() { return horizontalOffset; }
        @Override public int getMaxScrollOffset() { return horizontalOffset; }
        @Override public int getBgCameraX() { return 0; }
        @Override public int getBgPeriodWidth() {
            if (assets == null) throw new IllegalStateException("CD static background has no authenticated source");
            return assets.terrain().layout().columns(CdR11aTerrain.Plane.BACKGROUND) * 256;
        }
    };

    SonicCdStaticPresentation(SonicCdGameModule module) { this.module = module; }
    void bind(CdR11aAssets assets) { this.assets = assets; }

    @Override public boolean requiresExclusiveControl() { return true; }
    @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) { return false; }
    @Override public GameplayFrameDisposition beforeFrame(CourseControl course, LogicalInputSnapshot input) {
        return GameplayFrameDisposition.HOLD;
    }
    @Override public void afterTick(CourseControl course, boolean advanced) {
        if (advanced) throw new IllegalStateException("CD static presentation admitted a gameplay tick");
    }
    @Override public boolean drawScene() { return true; }
    @Override public boolean presentationPaused() { return true; }
    @Override public void drawOverlay() {
        module.requireRuntimeSource();
        var level = GameServices.level();
        GameServices.parallax().update(0, 0, GameServices.camera(), level.getFrameCounter());
        level.drawWithRenderOptions(GameServices.sprites(), LevelManager.LevelRenderOptions.tilesOnly());
        // Engine and capture normally flush overlays in screen space. Terrain commands need
        // their normal world flush before those callers advance to any overlay pass.
        GameServices.graphics().flush();
    }

    @Override public void load(Rom rom) {
        if (rom != null) throw SonicCd.unsupported("cartridge-backed scroll loading");
    }
    @Override public ZoneScrollHandler getHandler(int zoneIndex) {
        if (zoneIndex != 0) throw SonicCd.unsupported("scroll zone " + zoneIndex);
        return scroll;
    }
    @Override public ZoneConstants getZoneConstants() {
        return new ZoneConstants() {
            @Override public int getZoneCount() { return 1; }
            @Override public String getZoneName(int index) { return module.getZoneRegistry().getZoneName(index); }
        };
    }
    @Override public void resetZoneState() { horizontalOffset = 0; }

    static void initializeCamera(SonicCdLevel level, Camera camera) {
        var plan = level.assets().loadPlan();
        var bounds = plan.bounds();
        // resetState removes any previous focused sprite; setFocusedSprite(null) dereferences it.
        camera.resetState();
        camera.setMinX((short) bounds.left());
        camera.setMaxX((short) bounds.right());
        camera.setMinY((short) bounds.top());
        camera.setMaxY((short) bounds.bottom());
        camera.setVerticalWrapEnabled(false);
        // LevelSizeLoad_StartPos subtracts half the native 320px width and $60 Y,
        // then clamps to the native bounds (Scroll (Act 1 Present).asm:87-108).
        // A wider development viewport centres horizontally using its actual width.
        camera.setX((short) clamp(plan.initialCentre().centreX() - camera.getWidth() / 2,
                bounds.left(), bounds.right()));
        camera.setY((short) clamp(plan.initialCentre().centreY() - bounds.cameraCentreY(),
                bounds.top(), bounds.bottom()));
        camera.setFrozen(true);
        camera.setLevelStarted(false);
        camera.setShakeOffsets(0, 0);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(value, max)); }

    static void publishDecodedGraphics(SonicCdLevel level, GraphicsManager graphics) {
        if (!graphics.isHeadlessMode() && !graphics.isGlInitialized()) {
            throw new IllegalStateException("Initialize engine graphics before publishing CD static terrain");
        }
        // Both real GPU and logic-headless caches own their normal palette/pattern objects.
        // This is called by transformDecodedLevel before LevelManager builds derived tilemaps.
        for (int line = 0; line < level.getPaletteCount(); line++) {
            graphics.cachePaletteTexture(level.getPalette(line), line);
        }
        graphics.beginPatternAtlasBatch();
        try {
            for (int index = 0; index < level.getPatternCount(); index++) {
                graphics.cachePatternTexture(level.getPattern(index), index);
            }
        } finally {
            graphics.endPatternAtlasBatch();
        }
    }
}
