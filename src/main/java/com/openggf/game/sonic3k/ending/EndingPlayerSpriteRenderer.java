package com.openggf.game.sonic3k.ending;

import com.openggf.game.EndingSpriteSet;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;

/** Reversible scene adapter: authored gameplay stays delegated; native ending IDs select optional frames. */
final class EndingPlayerSpriteRenderer extends PlayerSpriteRenderer {
    private final PlayerSpriteRenderer ordinary;
    private final AbstractPlayableSprite player;
    private final ObjectServices services;
    EndingPlayerSpriteRenderer(PlayerSpriteRenderer ordinary, AbstractPlayableSprite player, ObjectServices services) {
        super(SpriteArtSet.EMPTY, services.graphicsManager());
        this.ordinary = ordinary; this.player = player; this.services = services;
    }
    @Override public RenderContext getRenderContext() { return ordinary.getRenderContext(); }
    @Override public void setRenderContext(RenderContext context) { ordinary.setRenderContext(context); }
    @Override public int paletteLine() { return ordinary.paletteLine(); }
    @Override public void ensureCached(GraphicsManager graphics) { ordinary.ensureCached(graphics); }
    @Override public void invalidateDplcCache() { ordinary.invalidateDplcCache(); }
    @Override public void drawFrame(int frame, int x, int y, boolean flipX, boolean flipY) {
        if (!(player.getSuperStateController() instanceof com.openggf.sprites.playable.ProfileSuperStateController profile)
                || !profile.isEndingSceneActive()) {
            ordinary.drawFrame(frame,x,y,flipX,flipY); return;
        }
        // publishPlayer hides native P1 with the empty mapping 0. Authored WAIT frame 0 is visible.
        if (frame == 0 && player.isObjectMappingFrameControl()) return;
        var surface = player.isSuperSonic() ? EndingSpriteSet.Surface.S3K_POWERED_PLAYER : EndingSpriteSet.Surface.S3K_PLAYER;
        if (EndingCharacterPresentation.draw(services,surface,frame,x,y,flipX,flipY,player.isHighPriority(),-1)) return;
        ordinary.drawFrame(frame == 0x58 && player.isObjectMappingFrameControl() ? 0 : frame,x,y,flipX,flipY);
    }
}
