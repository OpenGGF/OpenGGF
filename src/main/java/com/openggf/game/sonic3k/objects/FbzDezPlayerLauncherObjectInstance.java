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
    private boolean returning,terminalOutwardEjectThisFrame;
    private boolean returnCompletedThisFrame;
    private boolean normalCallbackEligible = true;
    public FbzDezPlayerLauncherObjectInstance(ObjectSpawn spawn){super(spawn,"FBZDEZPlayerLauncher");anchorX=x=spawn.x();xFixed=x<<8;}
    @Override public void update(int frameCounter,PlayableEntity ignored){
        stepMotion();
        updateDynamicSpawn(x,spawn.y());
        // loc_3B9AC tests the standing bits before SolidObjectTop runs. A
        // player who first lands this frame is therefore not anchored or used
        // to start the launcher until the following object pass.
        ObjectPlayerQuery query=services().playerQuery();
        query.visitPlayers(participationPolicy(),this,(launcher,player)->{
            if(launcher.services().solidExecutionRegistry().previousStanding(launcher,player).standing()){
                launcher.applyStandingRider(player,frameCounter);
            }
        });
        checkpointAll();
        // Sprite_OnScreen_Test2 reads the saved anchor at $44, never the moving
        // x_pos. The native-width limit remains exactly $280; widescreen extends
        // only the visible-screen term of $80 + screen width + $C0.
        coarseXCull(anchorX,0x80+viewportWidth()+0xC0);
    }
    private void stepMotion(){
        terminalOutwardEjectThisFrame=false;
        returnCompletedThisFrame=false;
        if(returning){
            normalCallbackEligible=false;
            if(x==anchorX){
                returning=false;
                returnCompletedThisFrame=true;
            }else{
                x+=Integer.compare(anchorX,x);
                xFixed=x<<8;
            }
            return;
        }
        normalCallbackEligible=true;
        if(launchTimer==0)return;launchTimer--;if(launchTimer==0){xVelocity=0;returning=true;terminalOutwardEjectThisFrame=true;}xFixed+=xVelocity;x=xFixed>>8;if(doubleTimer>0){doubleTimer--;xVelocity=(short)(xVelocity<<1);}
    }
    private void beginLaunch(){if(launchTimer!=0||returning)return;xVelocity=(spawn.renderFlags()&1)!=0?-0x100:0x100;launchTimer=0x0C;doubleTimer=4;services().playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.FLOOR_LAUNCHER.id);}
    @Override public void onSolidContact(PlayableEntity player,SolidContact contact,int frameCounter){
        if(!contact.standing() || !(player instanceof AbstractPlayableSprite p))return;
        applyStandingRider(p,frameCounter);
    }
    private void applyStandingRider(PlayableEntity player,int frameCounter){
        if(!(player instanceof AbstractPlayableSprite p))return;
        // loc_3BA4A always branches directly to SolidObjectTop. Even when its
        // equality case selects loc_3B97A for the next frame, sub_3B9D8 must
        // remain suppressed for this frame.
        if(!normalCallbackEligible || returnCompletedThisFrame)return;
        boolean left=(spawn.renderFlags()&1)!=0;
        p.setDirection(left?com.openggf.physics.Direction.LEFT:com.openggf.physics.Direction.RIGHT);
        NativePositionOps.writeXPosPreserveSubpixel(p,x+(left?-4:4));
        if(terminalOutwardEjectThisFrame){
            p.setAnimationId(0);
            p.setAir(true);
            p.setYSpeed((short)0);
            return;
        }
        p.setXSpeed((short)xVelocity);
        p.setGSpeed((short)xVelocity);
        if(launchTimer==0)beginLaunch();
    }
    void beginLaunchForTest(){xVelocity=(spawn.renderFlags()&1)!=0?-0x100:0x100;launchTimer=0x0C;doubleTimer=4;} void stepMotionForTest(){stepMotion();}int xVelocity(){return xVelocity;}boolean returning(){return returning;}
    ObjectPlayerParticipationPolicy participationPolicy(){return ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED;}
    @Override public SolidExecutionMode solidExecutionMode(){return SolidExecutionMode.MANUAL_CHECKPOINT;}
    @Override public boolean usesPreUpdatePositionForSolidContact(PlayableEntity player){return returning;}
    @Override public boolean carriesRiderOnHorizontalMove(PlayableEntity player){return returning;}
    @Override public int getX(){return x;}@Override public SolidObjectParams getSolidParams(){return new SolidObjectParams(0x10,3,3);}@Override public boolean isTopSolidOnly(){return true;}@Override public SolidRoutineProfile getSolidRoutineProfile(){return SolidRoutineProfile.topSolid(false);}
    @Override public int getPriorityBucket(){return 5;}
    @Override public void appendRenderCommands(List<GLCommand> commands){PatternSpriteRenderer r=getRenderer(Sonic3kObjectArtKeys.FBZ_DEZ_PLAYER_LAUNCHER);if(r!=null&&r.isReady())r.drawFrameIndex(0,x,spawn.y(),(spawn.renderFlags()&1)!=0,false);}
}
