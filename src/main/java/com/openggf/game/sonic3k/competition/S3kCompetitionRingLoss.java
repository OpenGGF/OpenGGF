package com.openggf.game.sonic3k.competition;

import com.openggf.game.PlayableEntity;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.NativeSpriteSubmissionSource;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Shipped Obj_Bouncing_Ring in a miniature world (sonic3k.asm:35520-35672).
 * There is no competition art/physics replacement in this routine. In particular,
 * P2 chooses its own initial count but the original routine clears/awards P1's bank.
 */
public final class S3kCompetitionRingLoss extends AbstractObjectInstance
        implements SpawnRewindRecreatable,TouchResponseProvider,NativeSpriteSubmissionSource {
    public static final String ART_KEY="competition_ring_loss";
    private int lossOwnerSlot;
    private int xFixed,yFixed,xVelocity,yVelocity;
    private int routine,collisionFlags,renderFlags,mappingFrame,priority;
    private int animationFrame,animationTimer;
    private boolean drawPublished,publishedTouch;

    public S3kCompetitionRingLoss(ObjectSpawn spawn) {
        super(spawn,"CompetitionRingLoss");
        lossOwnerSlot=spawn.subtype()&1; xFixed=spawn.x()<<16; yFixed=spawn.y()<<16;
    }
    public S3kCompetitionRingLoss(int slot,int x,int y) {
        this(new ObjectSpawn(x&0xFFFF,y&0xFFFF,0x37,slot,0,false,y&0xFFFF));
    }
    @Override public void update(int vIntRunCount,PlayableEntity ignored) {
        var session=WorldSessionPolicyAccess.getService(services().worldSession(),S3kCompetitionSession.class);
        if (session==null || isDestroyed()) return;
        updateNative(session,session.damageContext(),vIntRunCount);
        if (drawPublished) NativeSpriteSubmissionAccess.submitSpilling(services().objectManager(),getSlotIndex(),getPriorityBucket());
    }
    public void updateNative(S3kCompetitionSession session,S3kCompetitionDamage.Context context,int vIntRunCount) {
        if (isDestroyed()) return;
        drawPublished=publishedTouch=false;
        if (routine==0) initialize(session,context);
        if (routine==2) bounce(context,vIntRunCount);
        else if (routine==4) {
            routine=6; collisionFlags=0; priority=1;
            S3kCompetitionDamage.giveRing(session,context,0); sparkle(context);
        } else if (routine==6) sparkle(context);
        else if (routine==8) setDestroyed(true);
        if (!isDestroyed()) updateDynamicSpawn(getX(),getY());
    }
    private void initialize(S3kCompetitionSession session,S3kCompetitionDamage.Context context) {
        int remaining=(Math.min(32,session.rings(lossOwnerSlot))-1)&0xFFFF;
        int phase=0x288,vx=0,vy=0;
        S3kCompetitionRingLoss target=this;
        while (target!=null) {
            if ((short)phase>=0) {
                int shift=(phase>>>8)&0xFF;
                vx=(short)(TrigLookupTable.sinHex(phase)<<shift);
                vy=(short)(TrigLookupTable.cosHex(phase)<<shift);
                int low=(phase&0xFF)+0x10;
                phase=(phase&0xFF00)|(low&0xFF);
                if (low>0xFF) { int next=phase-0x80; phase=next<0?0x288:next; }
            }
            target.routine=2; target.renderFlags=0x84; target.collisionFlags=0x47; target.priority=3;
            target.xVelocity=vx; target.yVelocity=vy; context.spillAnimation().restartCounter();
            vx=(short)-vx; phase=(short)-phase;
            if (remaining==0) break;
            remaining=(remaining-1)&0xFFFF;
            target=(S3kCompetitionRingLoss)context.allocateAfter(this,
                    ()->new S3kCompetitionRingLoss(lossOwnerSlot,getX(),getY()));
        }
        S3kCompetitionDamage.finishSpillInitialization(session,context);
        // FixBugs=0: a count changed to zero before this init still initializes
        // its first ring and continues DBF $FFFF until allocation fails.
    }
    private void bounce(S3kCompetitionDamage.Context context,int vIntRunCount) {
        mappingFrame=context.spillAnimation().frame();
        xFixed+=xVelocity<<8; yFixed+=yVelocity<<8;
        yVelocity=(short)(yVelocity+0x18);
        // Process_Sprites starts d7=109. Boundary/lifetime checks share the descending-floor phase.
        if (yVelocity>=0 && ((vIntRunCount+109-getSlotIndex())&7)==0) {
            if ((renderFlags&0x80)!=0) {
                int distance=context.floorDistance(getX(),(getY()+8)&0xFFFF);
                if (distance<0) {
                    yFixed=((getY()+distance)<<16)|(yFixed&0xFFFF);
                    yVelocity=(short)-(yVelocity-(yVelocity>>2));
                }
            }
            if (context.spillAnimation().counter()==0
                    || Integer.compareUnsigned((context.bottomY()+0xE0)&0xFFFF,getY())<0) {
                setDestroyed(true); return;
            }
        }
        publishedTouch=true; drawPublished=true;
        // Competition scroll never writes the campaign Level_repeat_offset, whose cleared word stays zero.
    }
    private void sparkle(S3kCompetitionDamage.Context context) {
        var set=context.animations(ART_KEY);
        if (set==null || set.getScript(0)==null) throw new IllegalStateException("Missing native ring sparkle ROM script");
        var script=set.getScript(0);
        animationTimer=(animationTimer-1)&0xFF;
        if ((byte)animationTimer<0) {
            animationTimer=script.delay()&0xFF;
            int next=script.flatByteAt(animationFrame);
            if (next<0) throw new IllegalStateException("Native ring sparkle left its ROM script");
            if (next<0x80) { mappingFrame=next; animationFrame=(animationFrame+1)&0xFF; }
            else if (next==0xFC) { routine+=2; animationFrame=(animationFrame+1)&0xFF; }
            else throw new IllegalStateException("Unexpected native ring sparkle command");
        }
        drawPublished=true; // FC changes the NEXT dispatch; this one still calls Draw_Sprite.
    }
    /** Touch_ChkValue consumes the overlap even when its invulnerability>=90 gate rejects collection. */
    public boolean touchNativePlayer(S3kCompetitionPlayer player) {
        if (!publishedTouch || collisionFlags==0 || isDestroyed() || !player.touchEnabled()) return false;
        int h=(player.yRadius()-3)&0xFF;
        if (Math.abs((short)(player.centreX()-getX()))>10
                || Math.abs((short)(player.centreY()-getY()))>6+h) return false;
        if (player.invulnerabilityTimer()<90) routine=4;
        return true;
    }
    public int lossOwnerSlot() { return lossOwnerSlot; }
    public int routine() { return routine; }
    public int mappingFrame() { return mappingFrame; }
    public int nativeXVelocity() { return xVelocity; }
    public int nativeYVelocity() { return yVelocity; }
    public int xFraction() { return xFixed&0xFFFF; }
    public int yFraction() { return yFixed&0xFFFF; }
    public int nativeRenderFlags() { return renderFlags; }
    public int nativeArtWord() { return 0xA6BC; }
    public boolean nativeDrawPublished() { return drawPublished; }
    public void refreshNativeVisibility(boolean visible) { renderFlags=(renderFlags&0x7F)|(visible?0x80:0); }
    @Override public int getX() { return (xFixed>>>16)&0xFFFF; }
    @Override public int getY() { return (yFixed>>>16)&0xFFFF; }
    @Override public int getCollisionFlags() { return collisionFlags; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean requiresRenderFlagForTouch() { return false; }
    @Override public boolean publishesTouchResponseListEntryThisFrame() { return publishedTouch; }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 0; }
    @Override public int getPriorityBucket() { return priority; }
    @Override public boolean isHighPriority() { return true; }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawPublished || isDestroyed()) return;
        var renderer=getRenderer(ART_KEY);
        if (renderer!=null) renderer.drawFrameIndex(mappingFrame,getX(),getY(),false,false);
    }
}
