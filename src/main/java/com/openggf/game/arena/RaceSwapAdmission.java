package com.openggf.game.arena;

import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.arena.SwapTarget;
import com.openggf.game.arena.TrackGeometry;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Deterministic checks over the local world; transport and streamed progress have no authority. */
public final class RaceSwapAdmission {
    private RaceSwapAdmission() { }

    public static boolean alive(AbstractPlayableSprite player) {
        Integer routine = player.getObjectRoutineOverride();
        return !player.getDead() && !player.isInDeathRestartRoutine()
                && (routine == null || (routine & 0xFF) < 6);
    }

    public static boolean accepts(GameplayModeContext context,AbstractPlayableSprite local,
            SwapTarget target,int standingXRadius,int standingYRadius,TrackGeometry geometry) {
        if (target == null || !alive(local) || local.isHurt() || local.isObjectControlled()) return false;
        if (target.centreX()<0 || target.centreX()>0xFFFF || target.centreY()<0 || target.centreY()>0xFFFF
                || target.xVel() < -0x1000 || target.xVel() > 0x1000
                || target.yVel() < -0x1000 || target.yVel() > 0x1000
                || target.priorityBucket()<0 || target.priorityBucket()>7
                || target.shoeKind()<0 || target.shoeKind()>2 || target.shoeTimer()<0 || target.shoeTimer()>75
                || target.progress()<0 || target.progress()>15) return false;
        var manager = context.getLevelManager();
        var level = manager.getCurrentLevel();
        int width = level.getLayerWidthBlocks(0)*level.getBlockPixelSize();
        int height = level.getLayerHeightBlocks(0)*level.getBlockPixelSize();
        int x = geometry.wrapWidthPx()>0 ? Math.floorMod(target.centreX(),geometry.wrapWidthPx()) : target.centreX();
        int y = geometry.wrapHeightPx()>0 ? Math.floorMod(target.centreY(),geometry.wrapHeightPx()) : target.centreY();
        if (x<0 || x>=width || y<0 || y>=height) return false;
        var camera = context.getCamera();
        // Eligible signpost tracks keep unreached event regions outside these native bounds.
        if (!insideCameraAxis(x,camera.getMinX(),camera.getMaxX(),camera.getWidth(),geometry.wrapWidthPx())
                || !insideCameraAxis(y,camera.getMinY(),camera.getMaxY(),camera.getHeight(),geometry.wrapHeightPx())) return false;
        var planes = context.getBackgroundPlaneCollisionProvider();
        return shallow(ObjectTerrainUtils.checkFloorDist(manager,planes,target.secondaryPath(),
                    target.centreX(),target.centreY()+standingYRadius),standingYRadius)
                && shallow(ObjectTerrainUtils.checkLeftWallDist(manager,planes,target.secondaryPath(),
                    target.centreX()-standingXRadius,target.centreY()),standingXRadius)
                && shallow(ObjectTerrainUtils.checkRightWallDist(manager,planes,target.secondaryPath(),
                    target.centreX()+standingXRadius,target.centreY()),standingXRadius);
    }
    private static boolean insideCameraAxis(int position,int minimum,int maximum,int screen,int wrap) {
        if (wrap<=0) return position>=minimum-screen && position<=maximum+screen;
        int signedSpan=maximum-minimum;
        int unsignedSpan=(maximum&0xFFFF)-(minimum&0xFFFF);
        if (signedSpan>=wrap-1 || unsignedSpan>=wrap-1) return true;
        int span=Math.floorMod(signedSpan,wrap);
        if (span+2*screen>=wrap-1) return true;
        int offset=Math.floorMod(position-minimum,wrap);
        return offset<=span+screen || offset>=wrap-screen;
    }
    private static boolean shallow(TerrainCheckResult probe,int radius) {
        return !probe.foundSurface() || probe.distance()>=-radius;
    }
}
