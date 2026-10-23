package com.openggf.game.sonic3k.competition;

import com.openggf.game.PlayableEntity;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.NativeSpriteSubmissionSource;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.util.List;
import java.util.Objects;

/**
 * Obj_DashDust2P: fixed Dust/Dust_P2 SST98/99 plus its dynamically allocated skid puffs.
 * loc_18F9C (sonic3k.asm:34239-34299) initializes and follows the parent.
 * loc_19064 (sonic3k.asm:34310-34345) emits skid children.
 * The miniature routine keeps the shared skid puff's +$C Y offset; it is not scaled.
 */
public final class S3kMiniatureDust extends AbstractObjectInstance
        implements SpawnRewindRecreatable,NativeSpriteSubmissionSource {
    public static final String ART_KEY="competition_dash_dust";
    public static final int P1_FIXED_SLOT=98,P2_FIXED_SLOT=99;
    private int parentSlot;
    private int routine,x,y,status,renderFlags,artWord,priorityWord,width;
    private int animation,previousAnimation,animationFrame,animationTimer,mappingFrame,spawnTimer;
    private boolean drawPublished;

    public S3kMiniatureDust(ObjectSpawn spawn) {
        super(spawn,"CompetitionDust"); parentSlot=spawn.subtype()&1;
        x=spawn.x()&0xFFFF; y=spawn.y()&0xFFFF;
    }
    public S3kMiniatureDust(int parentSlot) { this(logicalSpawn(parentSlot)); }
    private static ObjectSpawn logicalSpawn(int parentSlot) {
        if(parentSlot<0 || parentSlot>1)throw new IllegalArgumentException("Native dust parent must be P1 or P2");
        return new ObjectSpawn(0,0,0,parentSlot,0,false,0);
    }
    @Override public void update(int vIntRunCount,PlayableEntity ignored) {
        if(isDestroyed())return;
        var session=WorldSessionPolicyAccess.getService(services().worldSession(),S3kCompetitionSession.class);
        if(session==null)return;
        updateNative(session,session.damageContext());
        if(drawPublished)NativeSpriteSubmissionAccess.submitSpilling(services().objectManager(),getSlotIndex(),getPriorityBucket());
    }
    public void updateNative(S3kCompetitionSession session,S3kCompetitionDamage.Context context) {
        if(isDestroyed())return;
        Objects.requireNonNull(session);Objects.requireNonNull(context);drawPublished=false;
        var parent=session.player(parentSlot); // Relink the current miniature after SpriteManager recreation.
        if(routine==0) {
            routine=2;renderFlags|=4;priorityWord=0x80;width=0x10;artWord=0x7E0;
        }
        if(routine==2) {
            if(animation==0 || animation==1)mappingFrame=0;
            else if(animation==2) {
                if(parent.airLeft()<12 || parent.routine()>=4 || parent.spinDashFlag()==0) {
                    animation=0;return; // loc_19058 does not animate or Draw_Sprite this dispatch.
                }
                x=parent.centreX()&0xFFFF;y=parent.centreY()&0xFFFF;status=parent.status()&1;
                if(previousAnimation==0)artWord=(artWord&0x7FFF)|(parent.highPriority()?0x8000:0);
            } else if(animation==3) {
                if(parent.airLeft()<12){animation=0;return;}
            } else throw new IllegalStateException("Miniature dust left the native animation dispatch");
            animate(context.animations(ART_KEY));drawPublished=true;
        } else if(routine==4)setDestroyed(true);
        else if(routine==6) {
            if(parent.animation()!=0xD){routine=2;spawnTimer=0;return;}
            mappingFrame=0;spawnTimer=(spawnTimer-1)&0xFF;
            if((byte)spawnTimer<0) {
                spawnTimer=3;
                context.allocateFree(()->skidChild(parent));
            }
        } else throw new IllegalStateException("Miniature dust left the native routine table");
        if(!isDestroyed())updateDynamicSpawn(x,y);
    }
    private S3kMiniatureDust skidChild(S3kCompetitionPlayer parent) {
        var child=new S3kMiniatureDust(parentSlot);
        child.x=parent.centreX()&0xFFFF;child.y=(parent.centreY()+0xC)&0xFFFF;
        child.animation=3;child.routine=2;child.renderFlags=renderFlags;
        child.priorityWord=0x80;child.width=4;child.artWord=(artWord&0x7FFF)|(parent.highPriority()?0x8000:0);
        return child;
    }
    private void animate(SpriteAnimationSet set) {
        if(set==null)throw new IllegalStateException("Missing miniature dust ROM animation bank");
        if(animation!=previousAnimation){previousAnimation=animation;animationFrame=animationTimer=0;}
        // Animate_Sprite uses byte carry, not the signed timer test used by player animation.
        animationTimer=(animationTimer-1)&0xFF;if(animationTimer!=0xFF)return;
        var script=set.getScript(animation);
        if(script==null)throw new IllegalStateException("Missing miniature dust ROM script");
        animationTimer=script.delay()&0xFF;
        int value=script.flatByteAt(animationFrame);
        if(value<0)throw new IllegalStateException("Miniature dust left its ROM script window");
        if(value<0x80)publishAnimationFrame(value);
        else if(value==0xFF){animationFrame=0;publishAnimationFrame(script.flatByteAt(0));}
        else if(value==0xFE){animationFrame=(animationFrame-script.flatByteAt(animationFrame+1))&0xFF;publishAnimationFrame(script.flatByteAt(animationFrame));}
        else if(value==0xFD)animation=script.flatByteAt(animationFrame+1)&0xFF;
        else if(value==0xFC){routine=(routine+2)&0xFF;animationTimer=0;animationFrame=(animationFrame+1)&0xFF;}
        else if(value==0xFB)x=0x7F00;
    }
    private void publishAnimationFrame(int frame) {
        mappingFrame=frame&0xFF;renderFlags=(renderFlags&0xFC)|(status&3);animationFrame=(animationFrame+1)&0xFF;
    }
    /** Exact MOVE.B writes through the miniature's live a6 dust pointer. */
    public void setAnimation(int value){animation=value&0xFF;}
    public void startSkid(){routine=6;mappingFrame=0x15;}
    public int parentSlot(){return parentSlot;}
    public int nativeFixedSlot(){return P1_FIXED_SLOT+parentSlot;}
    public int routine(){return routine;}
    public int animation(){return animation;}
    public int previousAnimation(){return previousAnimation;}
    public int animationFrame(){return animationFrame;}
    public int animationTimer(){return animationTimer;}
    public int spawnTimer(){return spawnTimer;}
    public int mappingFrame(){return mappingFrame;}
    public int nativeStatus(){return status;}
    public int nativeArtWord(){return artWord;}
    public int nativeRenderFlags(){return renderFlags;}
    public boolean nativeDrawPublished(){return drawPublished;}
    /** Actual queued-pointer admission owns this carried bit, including replacement occupants. */
    public void refreshNativeVisibility(boolean visible){renderFlags=(renderFlags&0x7F)|(visible?0x80:0);}
    @Override public int getLiveObjectId(){return isDestroyed()?0:1;}
    @Override public int getX(){return x;}
    @Override public int getY(){return y;}
    @Override public int getOnScreenHalfWidth(){return width;}
    @Override public int getOnScreenHalfHeight(){return 0;}
    @Override public int getPriorityBucket(){return priorityWord/0x80;}
    @Override public boolean isHighPriority(){return (artWord&0x8000)!=0;}
    @Override public boolean isPersistent(){return true;}
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(!drawPublished || isDestroyed())return;
        var renderer=getRenderer(ART_KEY);
        if(renderer!=null)renderer.drawFrameIndex(mappingFrame,x,y,(renderFlags&1)!=0,(renderFlags&2)!=0);
    }
}
