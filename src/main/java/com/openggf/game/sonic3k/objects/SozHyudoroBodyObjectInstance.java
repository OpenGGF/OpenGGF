package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.DamageCause;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.physics.SwingMotion;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.ArrayList;

/** Hyudoro_body, its nine routines and away animation ($8F11E..$8F37A). */
public final class SozHyudoroBodyObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable,TouchResponseProvider,TouchResponseListener {
    private static final int ANIM_BASE=0x8F682;
    private static final int[] APPEAR={0x8F682,0x8F695,0x8F6AA};
    private SozHyudoroControllerObjectInstance controller;
    private final List<AbstractPlayableSprite> pendingContacts = new ArrayList<>();
    private int xFixed,yFixed,xVelocity,yVelocity,maxVelocity,acceleration;
    private int routine,darkness,frame,animationAddress,animationIndex,animationTimer,attackTimer,collisionProperty;
    private boolean initialized,worldPosition,facingLeft,directionDown,fading;
    private boolean renderOnScreen=true,deletePending;
    private byte[] animations;
    public SozHyudoroBodyObjectInstance(ObjectSpawn spawn){this(spawn,null);}
    public SozHyudoroBodyObjectInstance(ObjectSpawn spawn,SozHyudoroControllerObjectInstance controller){
        super(spawn,"SOZHyudoro");this.controller=controller;xFixed=spawn.x()<<16;yFixed=spawn.y()<<16;
    }
    @Override public void update(int vIntRunCount,PlayableEntity leader){
        if(deletePending){setDestroyed(true);return;}
        if(controller==null){setDestroyed(true);return;}
        if(!initialized){initialize();}
        int dark=controller.darkness();
        if(fading){animate();return;}
        switch(routine){
            case 2 -> {if(animate()!=0){routine=dark==5?12:4;if(dark==5)setupBob(dark);}}
            case 4 -> {animate();swing();move();bounce();if(dark!=darkness)routine=6;}
            case 6 -> {
                animate();boolean turn=swing();
                if(!turn){move();bounce();}
                else {routine=8;if(dark==3||dark==5){darkness=dark;routine=dark==5?12:4;setupBob(dark);}}
            }
            case 8 -> {
                swing();move();bounce();
                if(animate()!=0&&animationIndex==2){routine=10;darkness=dark;
                    setAnimation(dark>=4?0x8F6D4:0x8F6CC);frame=animationByte(animationAddress);}
            }
            case 10 -> {swing();move();bounce();if(animate()!=0)routine=4;}
            case 12 -> {
                animate();swing();move();
                if(atHorizontalLimit()){routine=14;facingLeft=!facingLeft;xVelocity=(short)-xVelocity;attackTimer=59;}
            }
            case 14 -> {
                animate();if(--attackTimer<0){routine=16;worldPosition=true;
                    xFixed+=((services().camera().getX()&0xFFFF)-0x80)<<16;
                    yFixed+=((services().camera().getY()&0xFFFF)-0x80)<<16;yVelocity=0x100;}
            }
            case 16 -> {animate();move();}
            default -> throw new IllegalStateException("Hyudoro routine "+routine);
        }
        boolean previousVisible=renderOnScreen;
        renderOnScreen=isWithinRenderSpriteBounds(0x10,0x14);
        if(!previousVisible){deleteGhost();return;}
        if(dark==0){fade();return;}
        resolvePendingContacts();
    }
    private void resolvePendingContacts() {
        int property = collisionProperty;
        collisionProperty = 0;
        var main = services().playerQuery().mainPlayerOrNull();
        var second = services().playerQuery().nativeP2OrNull();
        // Check_PlayerCollision/word_85890 chooses P2 when both native bits
        // are set. Additional engine slots retain their actual player identity
        // instead of being collapsed onto that native P2 bit.
        var nativeTarget = (property & 2) == 0 ? main : second;
        var contacts = List.copyOf(pendingContacts);
        pendingContacts.clear();
        if (property != 0 && nativeTarget instanceof AbstractPlayableSprite player
                && resolveContact(player)) return;
        for (var player : contacts) {
            if (player != main && player != second && resolveContact(player)) return;
        }
    }
    private boolean resolveContact(AbstractPlayableSprite player) {
        if (attacking(player)) { fade(); return true; }
        if (!player.getInvulnerable()) {
            player.applyHurtOrDeath(getX(), DamageCause.NORMAL, player.getRingCount() > 0);
        }
        return false;
    }
    private void initialize(){
        initialized=true;routine=2;darkness=controller.darkness();
        try{animations=services().rom().readBytes(ANIM_BASE,0x5A);}catch(IOException e){throw new UncheckedIOException(e);}
        int offset=(services().rng().nextWord()&255)-0x7F;xFixed+=offset<<16;
        // set_Hyudoro_body_init compares the unsigned OFFSET, not the resulting x_pos.
        facingLeft=(offset&0xFFFF)>=0x120;xVelocity=facingLeft?-0x100:0x100;
        animationAddress=APPEAR[darkness>>1];setupBob(darkness);services().playSfx(Sonic3kSfx.GHOST_APPEAR.id);
    }
    private void setupBob(int dark){
        int speed=dark<3?0x100:dark<5?0x180:0x200;xVelocity=xVelocity<0?-speed:speed;
        maxVelocity=dark<3?0x40:dark<5?0x80:0xC0;acceleration=dark<3?4:dark<5?8:0x10;
        directionDown=yVelocity<0;yVelocity=directionDown?-maxVelocity:maxVelocity;
    }
    private boolean swing(){var result=SwingMotion.update(acceleration,yVelocity,maxVelocity,directionDown);
        yVelocity=(short)result.velocity();directionDown=result.directionDown();return result.directionChanged();}
    private void move(){xFixed+=xVelocity<<8;yFixed+=yVelocity<<8;}
    private boolean atHorizontalLimit(){int x=(xFixed>>16)&0xFFFF;return xVelocity<0?x<0xA0:x>0x1A0;}
    private void bounce(){if(atHorizontalLimit()){xVelocity=(short)-xVelocity;facingLeft=!facingLeft;}}
    private int animationByte(int address){return animations[address-ANIM_BASE]&255;}
    private void setAnimation(int address){animationAddress=address;animationIndex=0;animationTimer=0;}
    /** Animate_RawMultiDelay: byte timer, two-byte pairs, FC loop/F8 relative program/F4 callback. */
    private int animate(){
        animationTimer=(byte)(animationTimer-1);if(animationTimer>=0)return 0;
        animationIndex=(animationIndex+2)&255;int value=animationByte(animationAddress+animationIndex);
        if(value<0x80){frame=value;animationTimer=animationByte(animationAddress+animationIndex+1);return 1;}
        if(value==0xF4){animationTimer=0;deleteGhost();animationIndex=0;return -1;}
        if(value==0xF8)animationAddress+=(byte)animationByte(animationAddress+animationIndex+1);
        else if(value!=0xFC)throw new IllegalStateException("Hyudoro raw animation command "+value);
        frame=animationByte(animationAddress);animationTimer=animationByte(animationAddress+1);animationIndex=0;return 1;
    }
    private void fade(){fading=true;setAnimation(0x8F6BF);services().playSfx(Sonic3kSfx.BOUNCY.id);}
    private void deleteGhost(){if(!deletePending){controller.ghostDeleted();deletePending=true;}}
    private boolean attacking(AbstractPlayableSprite p){
        if(p.getInvincibleFrames()>0||p.isSuperSonic()||p.getAnimationId()==9||p.getAnimationId()==2)return true;
        if(p instanceof Knuckles)return p.getDoubleJumpFlag()==1||p.getDoubleJumpFlag()==3;
        if(p instanceof Tails tails &&p.getDoubleJumpFlag()!=0&&!tails.isInWater()){
            int a=TrigLookupTable.calcAngle((short)(p.getCentreX()-getX()),(short)(p.getCentreY()-getY()));return ((a-0x20)&255)<0x40;
        }return false;
    }
    @Override public void onTouchResponse(PlayableEntity p,TouchResponseResult result,int vIntRunCount){
        if(getCollisionFlags()==0)return;
        if (p == services().playerQuery().mainPlayerOrNull()) collisionProperty=(collisionProperty+1)&255;
        else if (p == services().playerQuery().nativeP2OrNull()) collisionProperty=(collisionProperty+2)&255;
        if (p instanceof AbstractPlayableSprite player && !pendingContacts.contains(player)) pendingContacts.add(player);
    }
    @Override public int getCollisionFlags(){return !fading&&routine==16?0xD7:0;}
    @Override public int getCollisionProperty(){return collisionProperty;}
    @Override public boolean usesS3kTouchSpecialPropertyResponse(){return true;}
    @Override public boolean requiresContinuousTouchCallbacks(){return true;}
    @Override public int getX(){return ((xFixed>>16)+(worldPosition?0:(services().camera().getX()&0xFFFF)-0x80))&0xFFFF;}
    @Override public int getY(){return ((yFixed>>16)+(worldPosition?0:(services().camera().getY()&0xFFFF)-0x80))&0xFFFF;}
    @Override public int getOnScreenHalfWidth(){return 0x10;}
    @Override public int getOnScreenHalfHeight(){return 0x14;}
    @Override public int getPriorityBucket(){return 0;}
    @Override public boolean usesCustomOutOfRangeCheck(){return true;}
    @Override public boolean isCustomOutOfRange(int cameraX){return false;}
    @Override public void appendRenderCommands(List<GLCommand> commands){var r=getRenderer(Sonic3kObjectArtKeys.SOZ_GHOSTS);
        if(r!=null&&r.isReady())r.drawFrameIndexForcedPriority(frame,getX(),getY(),!facingLeft,false,-1,true);}
}
