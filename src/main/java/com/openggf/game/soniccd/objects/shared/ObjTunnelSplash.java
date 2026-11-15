package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Tunnel splash ({@code TunnelSplashObject}, objects/r1/tunnel_splash.asm:3-38): a placed decoration that
 * plays animation 0 of {@code TunnelSplashAnims} once; the script's {@code $FC} advances the routine and
 * the next frame deletes the object (:37-38). Tile $39E in the past and $3E4 otherwise; it is never
 * range-checked.
 */
public final class ObjTunnelSplash extends CdObject implements ObjectVisualFacing.NonDirectional {
    private boolean initialised;
    private int routine;
    private CdAnimator animator;
    private int mapFrame;

    public ObjTunnelSplash(ObjectSpawn spawn) {
        super(spawn, "Tunnel splash");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 4) {
            setDestroyed(true);                                           // TunnelSplashObject_0_Routine4: DeleteObject
            return;
        }
        if (!initialised) {
            var provider = artProvider();
            var level = cdLevel();
            if (provider == null || level == null) return;
            var payload = level.payload();
            priority = 1;
            tileWord = SharedObjectCatalogue.offset(payload, timeZone() != 0 ? "tunnelSplashTilePresent" : "tunnelSplashTilePast");
            animator = new CdAnimator(provider.image(), SharedObjectCatalogue.offset(payload, "tunnelSplashAnims"), 0);
            initialised = true;
        }
        if (animator.step() == 2) routine = 4;                            // AnimateObject; $FC: addq.b #2,obj.routine(a0)
        mapFrame = animator.mapFrame();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.TUNNEL_SPLASH + tileWord, mapFrame, hFlip(), vFlip());
    }
}
