package com.openggf.game.sonic3k.mutators;

import com.openggf.level.objects.ObjectServices;
import com.openggf.level.render.LevelFloorScanner;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.Comparator;

/** Bounded live-terrain admission; relocation preserves inventory, timers, and world history. */
final class CoherentRelocationOps {
    record Landing(int x,int y) { }
    private CoherentRelocationOps() { }
    static Landing landing(ObjectServices services,AbstractPlayableSprite player,int x,int y,int radius, AbstractPlayableSprite... ignoredActors) {
        var level=services.currentLevel();
        var camera=services.camera();
        if (level==null || camera==null || camera.getFrozen() || player.isObjectControlled() || player.isControlLocked()
                || player.isHurt() || player.getDead() || player.getLayer()!=0
                || services.useSecondaryTerrainCollisionPath() || services.gameState().isReverseGravityActive()) return null;
        if (x<camera.getMinX()+16 || x>camera.getMaxX()+camera.getWidth()-16) return null;
        // Existing camera constraints are authority; a relocation never unlocks an arena or moves into staging data.
        var candidates=LevelFloorScanner.stages(level,Math.max(0,x-radius),Math.max(0,y-64),
                x+radius+24,y+96,24,Math.max(48,player.getYRadius()*2+8),8);
        var stage=candidates.stream().min(Comparator.comparingInt(s->Math.abs(s.x()+s.width()/2-x))).orElse(null);
        if(stage==null) return null;
        int targetX=stage.x()+stage.width()/2,targetY=stage.floorY()-player.getYRadius();
        if(!compatibleFluid(services,player,targetX,targetY)) return null;
        var ignored=new AbstractPlayableSprite[ignoredActors.length+1]; ignored[0]=player;
        System.arraycopy(ignoredActors,0,ignored,1,ignoredActors.length);
        if(!NativeEncounterGeometry.clear(services,new NativeEncounterGeometry.Body(targetX,targetY-1,player.getXRadius(),player.getYRadius()-1),null,ignored)) return null;
        return new Landing(targetX,targetY);
    }
    static boolean compatibleFluid(ObjectServices services,AbstractPlayableSprite player,int targetX,int targetY) {
        var fluid=com.openggf.game.session.WorldSessionPolicyAccess.getService(services.worldSession(),
                com.openggf.game.mutators.EffectiveFluidContextSource.class);
        boolean submerged;
        if(fluid!=null) submerged=fluid.underwaterAt(player,targetX,targetY);
        else {
            var water=player.currentWaterSystem();
            submerged=water!=null && water.hasWater(services.romZoneId(),services.currentAct())
                    && targetY>water.getWaterLevelY(services.romZoneId(),services.currentAct());
        }
        return submerged==player.isInWater();
    }
    static void move(ObjectServices services,AbstractPlayableSprite player,Landing landing,boolean cameraFollows) {
        var objects=services.objectManager();
        objects.releaseRidingObject(player, objects.getRidingObject(player));
        for(var member:services.spriteManager().getSidekicks()) {
            var carry=member.getTailsCarryController();
            if(carry!=null && carry.isCarryingMainCharacter()) carry.clearAndReleaseMain();
        }
        int dx=landing.x()-player.getCentreX(),dy=landing.y()-player.getCentreY();
        NativePositionOps.writeXPosPreserveSubpixel(player,landing.x());
        NativePositionOps.writeYPosPreserveSubpixel(player,landing.y());
        player.setOnObject(false); player.setPushing(false); player.setAir(true);
        player.setAngle((byte)0); player.setForceFloorCheck(true);
        player.resetPositionAndStatTableHistory();
        if(cameraFollows) {
            var camera=services.camera();
            camera.setX((short)Math.clamp(camera.getX()+dx,camera.getMinX(),camera.getMaxX()));
            camera.setY((short)Math.clamp(camera.getY()+dy,camera.getMinY(),camera.getMaxY()));
            // Followers consume the coherently reinitialized leader history on their next native CPU slot.
            for(var member:services.spriteManager().getSidekicks()) member.resetPositionAndStatTableHistory();
        }
    }
}
