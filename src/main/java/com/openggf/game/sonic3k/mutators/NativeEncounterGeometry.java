package com.openggf.game.sonic3k.mutators;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.LevelFloorScanner;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Admission reads live ROM terrain and current native solid/actor bodies before allocation. */
final class NativeEncounterGeometry {
    record Body(int x, int y, int halfWidth, int halfHeight) {
        int left() { return x-halfWidth; }
        int right() { return x+halfWidth+1; }
        int top() { return y-halfHeight; }
        int bottom() { return y+halfHeight+1; }
        boolean overlaps(Body other) {
            return left()<other.right() && right()>other.left() && top()<other.bottom() && bottom()>other.top();
        }
    }
    private NativeEncounterGeometry() { }
    static boolean ordinary(ObjectServices services, AbstractPlayableSprite player) {
        var camera=services.camera();
        return camera!=null && !camera.getFrozen() && !player.isObjectControlled() && !player.isControlLocked() && !player.isHurt()
                && !player.getDead() && !player.isDebugMode() && player.getLayer()==0
                && !services.useSecondaryTerrainCollisionPath() && !services.gameState().isReverseGravityActive();
    }
    static boolean clear(ObjectServices services, Body body, ObjectInstance ignoreSolid, AbstractPlayableSprite... ignoreActors) {
        if (!LevelFloorScanner.clearRectangle(services.currentLevel(),body.left(),body.top(),body.right(),body.bottom())) return false;
        var camera=services.camera();
        if(camera==null || body.left()<camera.getMinX() || body.right()>camera.getMaxX()+camera.getWidth()) return false;
        for(var object:services.objectQuery().activeObjectsOfType(ObjectInstance.class)) {
            if(object==ignoreSolid || object.isDestroyed() || !(object instanceof SolidObjectProvider solid)) continue;
            var params=solid.getSolidParams();
            var occupied=new Body(object.getX()+params.offsetX(),object.getY()+params.offsetY(),params.halfWidth(),
                    Math.max(params.airHalfHeight(),params.groundHalfHeight()));
            if(body.overlaps(occupied)) return false;
        }
        var sprites=services.spriteManager();
        if(sprites==null) return false;
        var main=sprites.getMainPlayable();
        if(main!=null && !ignored(main,ignoreActors) && !main.getDead() && body.overlaps(actor(main))) return false;
        for(var player:sprites.getSidekicks()) if(!ignored(player,ignoreActors) && !player.getDead() && body.overlaps(actor(player))) return false;
        return true;
    }
    private static boolean ignored(AbstractPlayableSprite player,AbstractPlayableSprite[] ignored) {
        if(ignored!=null) for(var actor:ignored) if(actor==player) return true;
        return false;
    }
    static Body actor(AbstractPlayableSprite player) {
        return new Body(player.getCentreX(),player.getCentreY(),player.getXRadius(),player.getYRadius());
    }
}
