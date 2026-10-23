package com.openggf.game.sonic3k.competition;

import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.rings.SpillAnimationState;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Native damage/ring banks, FixBugs=0.
 * HurtCharacter (sonic3k.asm:21101-21197).
 * GiveRing (sonic3k.asm:35490-35555).
 */
public final class S3kCompetitionDamage {
    public interface Context {
        AbstractObjectInstance allocateFree(Supplier<? extends AbstractObjectInstance> factory);
        AbstractObjectInstance allocateAfter(AbstractObjectInstance parent,Supplier<? extends AbstractObjectInstance> factory);
        SpillAnimationState spillAnimation();
        int floorDistance(int x,int bottomY);
        int bottomY();
        void playSfx(Sonic3kSfx sound);
        SpriteAnimationSet animations(String key);
        void awardLife(int slot);
        void playExtraLifeMusic();
    }
    public enum Outcome { IGNORED, HURT, KILLED }
    public record State(int totalRings0,int totalRings1,int extraLifeFlags0,int extraLifeFlags1) { }
    private final int[] totalRings=new int[2];
    private final int[] extraLifeFlags=new int[2];

    public State capture() { return new State(totalRings[0],totalRings[1],extraLifeFlags[0],extraLifeFlags[1]); }
    public void restore(State state) {
        totalRings[0]=state.totalRings0(); totalRings[1]=state.totalRings1();
        extraLifeFlags[0]=state.extraLifeFlags0(); extraLifeFlags[1]=state.extraLifeFlags1();
    }
    public void reset() { restore(new State(0,0,0,0)); }
    /** loc_63B4/loc_6404 clear both totals and P1 flags; the P2 byte survives arena loads. */
    public void resetForArena() { restore(new State(0,0,0,extraLifeFlags[1])); }
    public int totalRings(int slot) { return totalRings[slot]; }
    public int extraLifeFlags(int slot) { return extraLifeFlags[slot]; }
    public void setExtraLifeFlags(int slot,int flags) { extraLifeFlags[slot]=flags&0xFF; }

    /** A6 blade touch enters Touch_ChkHurt/Touch_Hurt, not the spike helper. */
    public static Outcome contact(S3kCompetitionSession session,Context context,S3kCompetitionPlayer player,int sourceX) {
        if (blocked(player)) return Outcome.IGNORED;
        return hurtCharacter(session,context,player,sourceX);
    }
    /** sub_36906 -> sub_24280: gates before undoing this actor's full 16:16 Y movement. */
    public static Outcome projectile(S3kCompetitionSession session,Context context,S3kCompetitionPlayer player,int sourceX) {
        if (blocked(player)) return Outcome.IGNORED;
        int yFixed=(player.centreY()<<16)|player.yFraction();
        yFixed-=player.yVelocity()<<8;
        player.setCentreNative(player.centreX(),yFixed>>16);
        player.setFractions(player.xFraction(),yFixed&0xFFFF);
        return hurtCharacter(session,context,player,sourceX);
    }
    private static boolean blocked(S3kCompetitionPlayer player) {
        return player.routine()>=4 || player.invulnerabilityTimer()!=0 || (player.statusSecondary()&2)!=0;
    }
    private static Outcome hurtCharacter(S3kCompetitionSession session,Context context,S3kCompetitionPlayer player,int sourceX) {
        Objects.requireNonNull(session); Objects.requireNonNull(context);
        if ((player.statusSecondary()&1)==0) {
            if (session.rings(player.slot())==0) {
                player.kill(); player.setHighPriority(true); context.playSfx(Sonic3kSfx.DEATH);
                return Outcome.KILLED;
            }
            // Ring banks are cleared only if the allocated Obj_Bouncing_Ring actually executes.
            context.allocateFree(()->new S3kCompetitionRingLoss(player.slot(),player.centreX(),player.centreY()));
        }
        player.hurt(sourceX); context.playSfx(Sonic3kSfx.DEATH);
        return Outcome.HURT;
    }

    /** GiveRing and GiveRing_Tails differ at the already-capped P2 threshold branch. */
    public static void giveRing(S3kCompetitionSession session,Context context,int slot) {
        var bank=session.damage();
        bank.totalRings[slot]=Math.min(999,bank.totalRings[slot]+1);
        int count=session.rings(slot);
        if (count<999) session.setNativeRings(slot,++count);
        else if (slot==0) { context.playSfx(Sonic3kSfx.RING_RIGHT); return; }
        int flag=count>=100 && (bank.extraLifeFlags[slot]&2)==0?2
                :count>=200 && (bank.extraLifeFlags[slot]&4)==0?4:0;
        if (flag!=0) {
            bank.extraLifeFlags[slot]|=flag;
            context.awardLife(slot); context.playExtraLifeMusic();
        } else context.playSfx(Sonic3kSfx.RING_RIGHT);
    }
    /** loc_1A738 unconditionally clears P1 even when the spill owner was P2 (FixBugs=0). */
    public static void finishSpillInitialization(S3kCompetitionSession session,Context context) {
        context.playSfx(Sonic3kSfx.RING_LOSS);
        session.setNativeRings(0,0); session.damage().extraLifeFlags[0]=0;
    }
}
