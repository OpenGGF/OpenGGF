package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.boss.AbstractBossInstance;

import java.util.List;

/** ROM {@code Obj_LRZEndBoss}: the 14-hit alternating magma-jump fight and HPZ handoff. */
public final class LrzEndBossInstance extends AbstractBossInstance implements SpawnRewindRecreatable {
    private static final int HITS = 14;
    private int introTimer = 2 * 60;
    private int phaseTimer;
    private int defeatTimer;

    public LrzEndBossInstance() {
        this(new ObjectSpawn(0xB10,0x640,0,0,0,false,0));
    }
    public LrzEndBossInstance(ObjectSpawn spawn) { super(spawn,"LRZEndBoss"); }

    @Override protected void initializeBossState() {
        state.x=0xB10; state.y=0x640; state.xFixed=state.x<<16; state.yFixed=state.y<<16;
        state.hitCount=HITS; state.routine=0; state.yVel=-0x580;
    }
    @Override protected int getInitialHitCount() { return HITS; }
    @Override protected int getCollisionSizeIndex() { return 0x0F; }
    @Override protected int getBossHitSfxId() { return Sonic3kSfx.BOSS_HIT.id; }
    @Override protected int getBossExplosionSfxId() { return Sonic3kSfx.EXPLODE.id; }
    @Override protected void onHitTaken(int remainingHits) { }
    @Override protected boolean usesDefeatSequencer() { return false; }

    @Override protected void updateBossLogic(int frame, PlayableEntity player) {
        if (state.defeated) { updateDefeat(player); return; }
        if (introTimer-- >= 0) return;
        switch (state.routine) {
            case 0 -> {
                state.applyVelocity();
                state.yVel += 0x38;
                if (state.y >= 0x600 && state.yVel >= 0) {
                    state.y=0x600; state.yFixed=state.y<<16; state.routine=2; phaseTimer=0x7F;
                }
            }
            case 2 -> {
                if (phaseTimer-- < 0) { state.routine=4; phaseTimer=0xF7; }
            }
            case 4 -> {
                if (phaseTimer-- < 0) { state.routine=6; state.yVel=0x100; phaseTimer=0x3F; }
            }
            case 6 -> {
                state.applyVelocity();
                if (phaseTimer-- < 0) { state.routine=8; phaseTimer=0x3F; }
            }
            case 8 -> {
                if (phaseTimer-- < 0) {
                    state.routine=0; state.yVel=-0x580;
                    state.x = state.x == 0xB10 ? 0xA30 : 0xB10;
                    state.xFixed=state.x<<16;
                }
            }
            default -> state.routine=0;
        }
    }

    @Override protected void onDefeatStarted() {
        defeatTimer=0x7F;
        state.invulnerable=false;
    }
    private void updateDefeat(PlayableEntity player) {
        if (defeatTimer-- > 0) { state.y++; state.yFixed=state.y<<16; return; }
        int maxX=services().camera().getMaxX()&0xFFFF;
        if (maxX < 0xEC0) {
            maxX=Math.min(0xEC0,maxX+2);
            services().camera().setMaxX((short)maxX);
            services().camera().setMaxXTarget((short)maxX);
            return;
        }
        if (defeatTimer > -240) return;
        services().requestZoneAndAct(Sonic3kZoneIds.ZONE_HPZ,1,true);
    }

    @Override public LrzEndBossInstance recreateForRewind(com.openggf.level.objects.RewindRecreateContext c) {
        return new LrzEndBossInstance(c.spawn());
    }
    @Override public int getPriorityBucket() { return 3; }
    @Override public int getOnScreenHalfWidth() { return 0x28; }
    @Override public int getOnScreenHalfHeight() { return 0x28; }
    @Override public boolean isHighPriority() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
    public int hitCountForTest() { return state.hitCount; }
    public int routineForTest() { return state.routine; }
}
