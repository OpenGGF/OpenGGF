package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** Locked-on {@code Obj_FBZDEZPlayerLauncher} ($78), sonic3k.asm $3B942-$3BA8A. */
public final class FbzDezPlayerLauncherObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable {
    private final int anchorX;
    private int x,xFixed,xVelocity,launchTimer,doubleTimer;
    private boolean returning,launched;
    public FbzDezPlayerLauncherObjectInstance(ObjectSpawn spawn){super(spawn,"FBZDEZPlayerLauncher");anchorX=x=spawn.x();xFixed=x<<8;}
    @Override public void update(int frameCounter,PlayableEntity ignored){stepMotion();updateDynamicSpawn(x,spawn.y());if(!isOnScreen(0x180))setDestroyedByOffscreen();}
    private void stepMotion(){
        if(returning){if(x==anchorX){returning=false;launched=false;}else{x+=Integer.compare(anchorX,x);xFixed=x<<8;}return;}
        if(launchTimer==0)return;xFixed+=xVelocity;x=xFixed>>8;launchTimer--;if(doubleTimer>0){doubleTimer--;xVelocity=(short)(xVelocity<<1);}if(launchTimer==0){xVelocity=0;returning=true;}
    }
    private void beginLaunch(){if(launchTimer!=0||returning)return;xVelocity=(spawn.renderFlags()&1)!=0?-0x100:0x100;launchTimer=0x0C;doubleTimer=4;launched=true;services().playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.FLOOR_LAUNCHER.id);}
    @Override public void onSolidContact(PlayableEntity player,SolidContact contact,int frameCounter){if(!contact.standing())return;if(player instanceof AbstractPlayableSprite p){boolean left=(spawn.renderFlags()&1)!=0;p.setDirection(left?com.openggf.physics.Direction.LEFT:com.openggf.physics.Direction.RIGHT);NativePositionOps.writeXPosPreserveSubpixel(p,x+(left?-4:4));if(!returning){p.setXSpeed((short)xVelocity);p.setGSpeed((short)xVelocity);if(launchTimer==0)beginLaunch();}else if(launched){p.setAir(true);p.setYSpeed((short)0);}}}
    void beginLaunchForTest(){xVelocity=(spawn.renderFlags()&1)!=0?-0x100:0x100;launchTimer=0x0C;doubleTimer=4;launched=true;} void stepMotionForTest(){stepMotion();}int xVelocity(){return xVelocity;}boolean returning(){return returning;}
    ObjectPlayerParticipationPolicy participationPolicy(){return ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED;}
    @Override public int getX(){return x;}@Override public SolidObjectParams getSolidParams(){return new SolidObjectParams(0x10,3,3);}@Override public boolean isTopSolidOnly(){return true;}@Override public SolidRoutineProfile getSolidRoutineProfile(){return SolidRoutineProfile.topSolid(false);}
    @Override public int getPriorityBucket(){return 5;}
    @Override public void appendRenderCommands(List<GLCommand> commands){PatternSpriteRenderer r=getRenderer(Sonic3kObjectArtKeys.FBZ_DEZ_PLAYER_LAUNCHER);if(r!=null&&r.isReady())r.drawFrameIndex(0,x,spawn.y(),(spawn.renderFlags()&1)!=0,false);}
}
