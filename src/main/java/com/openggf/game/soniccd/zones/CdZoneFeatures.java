package com.openggf.game.soniccd.zones;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.render.AdvancedRenderFrameState;
import com.openggf.game.render.AdvancedRenderMode;
import com.openggf.game.render.AdvancedRenderModeContext;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.soniccd.CdZone;
import com.openggf.game.soniccd.player.CdPlayerStage;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Game-wide presentation facts the shared renderer asks a zone-feature provider for. The Mega-CD
 * water split is a palette swap from the {@code water_y} scanline (r4/main.asm:1316-1358), so the
 * waterline sits at the water level with no offset. Water itself comes from the zone fragments; the
 * player's water-current and air-countdown passes run through {@link CdPlayerStage}.
 */
public final class CdZoneFeatures implements ZoneFeatureProvider {
    private static final AdvancedRenderMode CCZ_FOREGROUND_SCROLL = new AdvancedRenderMode() {
        @Override public String id() { return "scd-ccz-foreground-scroll"; }

        @Override
        public void contribute(AdvancedRenderModeContext context, AdvancedRenderFrameState.Builder builder) {
            builder.enablePerLineForegroundScroll();
        }
    };

    private final CdPlayerStage playerStage;

    public CdZoneFeatures(CdPlayerStage playerStage) {
        this.playerStage = playerStage;
    }

    @Override public void initZoneFeatures(Rom rom, int zoneIndex, int actIndex, int cameraX) { }

    @Override
    public void registerAdvancedRenderModes(AdvancedRenderModeController controller, int zoneIndex, int actIndex) {
        // Every Collision Chaos variant supplies one foreground scroll word per line.
        if (zoneIndex == CdZone.COLLISION_CHAOS.ordinal()) {
            controller.register(CCZ_FOREGROUND_SCROLL);
        }
    }

    /** The level loop after the scroll ({@code StageMainLoopDraw}, src/r4/main.asm:401-402): the water currents. */
    @Override public void update(AbstractPlayableSprite player, int cameraX, int zoneIndex) {
        playerStage.afterScroll(player);
    }

    /** The bubbles slot after the player's: the air countdown (src/objects/r4/player_bubbles.asm). */
    @Override
    public void updateAfterPlayablePhysics(AbstractPlayableSprite player, int cameraX, int zoneIndex) {
        playerStage.afterPlayerSlot(player);
    }

    /**
     * {@code water_current_flag}: the player's main routine puts {@code prev_anim} back over a
     * Walk byte before animating (src/objects/player.asm:320-324).
     */
    @Override public boolean isWaterTunnelActive() {
        return playerStage.waterCurrentActive();
    }
    @Override public void reset() { }
    @Override public boolean hasCollisionFeatures(int zoneIndex) { return false; }
    @Override public boolean hasWater(int zoneIndex) { return false; }
    @Override public int getWaterLevel(int zoneIndex, int actIndex) { return 0; }
    @Override public void render(Camera camera, int frameCounter) { }
    @Override public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) { return baseIndex; }
    @Override public float getWaterlineOffset(int zoneIndex, int actIndex) { return 0.0f; }
}
