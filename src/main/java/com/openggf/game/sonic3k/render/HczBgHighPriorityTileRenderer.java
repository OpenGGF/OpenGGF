package com.openggf.game.sonic3k.render;

import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glGetIntegerv;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.TilemapGpuRenderer;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import java.util.ArrayDeque;

/**
 * Shared HCZ BG-high replay used when hardware BG priority must appear above
 * lower-priority foreground pixels.
 */
final class HczBgHighPriorityTileRenderer {
    private static final ArrayDeque<OverlayCommand> COMMAND_POOL = new ArrayDeque<>();
    private HczBgHighPriorityTileRenderer() {
    }

    static void render(SpecialRenderEffectContext context) {
        GraphicsManager graphicsManager = context.graphicsManager();
        if (graphicsManager.isHeadlessMode() || !graphicsManager.isGlInitialized()) {
            return;
        }
        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer == null) {
            return;
        }

        Integer atlasId = graphicsManager.getPatternAtlasTextureId();
        Integer paletteId = graphicsManager.getCombinedPaletteTextureId();
        if (atlasId == null || paletteId == null) {
            return;
        }

        ParallaxManager parallaxManager = GameServices.parallax();
        int[] hScrollData = parallaxManager.getHScrollForShader();
        if (hScrollData == null || hScrollData.length == 0) {
            return;
        }

        short bgScroll = (short) (hScrollData[hScrollData.length - 1] & 0xFFFF);
        float bgWorldOffsetX = -bgScroll;
        float bgWorldOffsetY = parallaxManager.getVscrollFactorBG();

        SonicConfigurationService configService = GameServices.configuration();
        int screenW = configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        int screenH = configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);

        LevelManager levelManager = context.levelManager();
        Camera camera = context.camera();
        WaterSystem waterSystem = GameServices.water();
        int featureZone = levelManager.getFeatureZoneId();
        int featureAct = levelManager.getFeatureActId();
        boolean hasWater = waterSystem.hasWater(featureZone, featureAct);
        ZoneFeatureProvider zoneFeatureProvider = levelManager.getZoneFeatureProvider();
        boolean suppressUnderwaterPalette = zoneFeatureProvider != null
                && zoneFeatureProvider.shouldSuppressUnderwaterPalette(featureZone, featureAct);
        Integer underwaterPaletteId = graphicsManager.getUnderwaterPaletteTextureId();
        boolean useUnderwaterPalette = hasWater && !suppressUnderwaterPalette && underwaterPaletteId != null;
        int waterLevel = hasWater ? waterSystem.getVisualWaterLevelY(featureZone, featureAct) : 0;
        float waterlineScreenY = (float) (waterLevel - camera.getYWithShake());
        int uwPalId = useUnderwaterPalette ? underwaterPaletteId : 0;

        OverlayCommand command = COMMAND_POOL.pollFirst();
        if (command == null) command = new OverlayCommand();
        graphicsManager.registerCommand(command.configure(renderer, screenW, screenH,
                bgWorldOffsetX, bgWorldOffsetY, graphicsManager.getPatternAtlasWidth(),
                graphicsManager.getPatternAtlasHeight(), atlasId, paletteId, uwPalId,
                useUnderwaterPalette, waterlineScreenY));
    }

    static OverlayCommand acquireCaptured(TilemapGpuRenderer renderer, int[] viewport, int marker) {
        OverlayCommand command = COMMAND_POOL.pollFirst();
        if (command == null) command = new OverlayCommand();
        return command.configureCaptured(renderer, 320, 224, marker, 0, 1, 1,
                2, 3, 0, false, 0, viewport);
    }

    static final class OverlayCommand implements GLCommandable {
        private final int[] viewport = new int[4];
        private TilemapGpuRenderer renderer;
        private int screenW, screenH, atlasWidth, atlasHeight, atlasId, paletteId, underwaterPaletteId;
        private float offsetX, offsetY, waterlineY;
        private boolean underwater, leased;

        OverlayCommand configure(TilemapGpuRenderer renderer, int screenW, int screenH,
                float offsetX, float offsetY, int atlasWidth, int atlasHeight,
                int atlasId, int paletteId, int underwaterPaletteId,
                boolean underwater, float waterlineY) {
            try {
                glGetIntegerv(GL_VIEWPORT, viewport);
            } catch (RuntimeException | Error failure) {
                COMMAND_POOL.addFirst(this);
                throw failure;
            }
            return configureCaptured(renderer, screenW, screenH, offsetX, offsetY, atlasWidth, atlasHeight,
                    atlasId, paletteId, underwaterPaletteId, underwater, waterlineY, viewport);
        }

        OverlayCommand configureCaptured(TilemapGpuRenderer renderer, int screenW, int screenH,
                float offsetX, float offsetY, int atlasWidth, int atlasHeight,
                int atlasId, int paletteId, int underwaterPaletteId,
                boolean underwater, float waterlineY, int[] capturedViewport) {
            this.renderer = renderer; this.screenW = screenW; this.screenH = screenH;
            this.offsetX = offsetX; this.offsetY = offsetY; this.atlasId = atlasId;
            this.atlasWidth = atlasWidth; this.atlasHeight = atlasHeight;
            this.paletteId = paletteId; this.underwaterPaletteId = underwaterPaletteId;
            System.arraycopy(capturedViewport, 0, viewport, 0, 4);
            this.underwater = underwater; this.waterlineY = waterlineY; leased = true;
            return this;
        }

        @Override public void execute(int cx, int cy, int cw, int ch) {
            try {
                if (renderer == null) return;
                renderer.render(TilemapGpuRenderer.Layer.BACKGROUND, screenW, screenH,
                        viewport[0], viewport[1], viewport[2], viewport[3], offsetX, offsetY,
                        atlasWidth, atlasHeight, atlasId,
                        paletteId, underwaterPaletteId, 1, false, false, underwater, waterlineY);
            } finally { release(); }
        }

        @Override public void discard() { release(); }
        private void release() {
            if (!leased) return;
            leased = false; renderer = null; COMMAND_POOL.addFirst(this);
        }
    }
}
