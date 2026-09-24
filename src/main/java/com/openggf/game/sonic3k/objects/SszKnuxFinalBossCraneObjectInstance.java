package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.NativePositionOps;

import java.util.List;

/**
 * Knuckles' SSZ2 final-boss crane ({@code Obj_KnuxFinalBossCrane},
 * sonic3k.asm:166241-166357).
 *
 * <p>The placed object owns the otherwise easy-to-miss act-2 route gate: init clamps
 * {@code Camera_max_X} to {@code Camera_min_X}; the first airborne player frame starts the
 * leftward pickup approach; the native hand-shake then returns the carrier to {@code $120};
 * and a signed 59-word wait starts final-boss music and locks scrolling before the encounter
 * allocation. The hook state below is the gameplay owner represented by the ROM's
 * {@code loc_7CCFE} child: it lowers to Knuckles, captures him, lifts him back to the ship,
 * and raises {@code _unkFAB8} bits 0..3 at the same phase boundaries.</p>
 */
public final class SszKnuxFinalBossCraneObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    static final int TARGET_X = 0x120;
    static final int SPEED = 0x80;
    static final int FINAL_BOSS_WAIT_WORD = 59;
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x280);

    enum Phase { INIT, WAIT_AIRBORNE, APPROACH, WAIT_PICKUP, RETURN, FINAL_WAIT, READY }

    private int x;
    private int xSub;
    private int y;
    private int swingAngle;
    /** ChildObjDat_7D4BC starts loc_7CCFE at child_dy $23. */
    private int hookOffsetY = 0x23;
    /** loc_7CD42's byte y_vel, used as the hook's lowering/lifting step. */
    private int hookVelocity;
    private int timer;
    private boolean bossSpawned;
    private Phase phase = Phase.INIT;

    private record RewindExtra(int x, int xSub, int y, int swingAngle, int hookOffsetY,
                               int hookVelocity, int timer, boolean bossSpawned, int phase)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszKnuxFinalBossCraneObjectInstance(ObjectSpawn spawn) {
        super(spawn, "KnuxFinalBossCrane");
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public SszKnuxFinalBossCraneObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new SszKnuxFinalBossCraneObjectInstance(context.spawn());
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                x, xSub, y, swingAngle, hookOffsetY, hookVelocity, timer,
                bossSpawned, phase.ordinal()));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            x = extra.x();
            xSub = extra.xSub();
            y = extra.y();
            swingAngle = extra.swingAngle();
            hookOffsetY = extra.hookOffsetY();
            hookVelocity = extra.hookVelocity();
            timer = extra.timer();
            bossSpawned = extra.bossSpawned();
            phase = Phase.values()[extra.phase()];
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity ignored) {
        switch (phase) {
            case INIT -> {
                // Obj_KnuxFinalBossCrane: move.w Camera_min_X,Camera_max_X.
                services().camera().setMaxX(services().camera().getMinX());
                phase = Phase.WAIT_AIRBORNE;
            }
            case WAIT_AIRBORNE -> {
                AbstractPlayableSprite player = services().spriteManager().getMainPlayable();
                if (player != null && player.getAir()) {
                    spawnFreeChild(() -> SongFadeTransitionInstance.transitionTo(Sonic3kMusic.BOSS.id));
                    phase = Phase.APPROACH;
                }
            }
            case APPROACH -> {
                swingAndMove(-SPEED);
                if (x < TARGET_X) {
                    state().setCutsceneFlag(0);
                    phase = Phase.WAIT_PICKUP;
                }
            }
            case WAIT_PICKUP -> {
                swing();
                updateGrabHook();
            }
            case RETURN -> {
                swingAndMove(SPEED);
                if (x >= TARGET_X) {
                    x = TARGET_X;
                    timer = FINAL_BOSS_WAIT_WORD;
                    spawnFreeChild(() -> SongFadeTransitionInstance.transitionTo(
                            Sonic3kMusic.FINAL_BOSS.id));
                    phase = Phase.FINAL_WAIT;
                }
            }
            case FINAL_WAIT -> {
                swing();
                if (--timer < 0) {
                    services().camera().setScrollLocked(true);
                    S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry())
                            .ifPresent(state -> state.setCutsceneFlag(4));
                    spawnBoss();
                    phase = Phase.READY;
                }
            }
            case READY -> {
                swing();
                spawnBoss();
            }
        }
    }

    /** loc_7CD22..loc_7CE66, the crane hook's descend, grab and lift graph. */
    private void updateGrabHook() {
        S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).ifPresent(state -> {
            AbstractPlayableSprite player = services().spriteManager().getMainPlayable();
            if (player == null) return;

            if (!state.cutsceneFlag(1)) {
                int hookY = y + hookOffsetY;
                int targetY = player.getCentreY() - 0x10;
                if (hookY < targetY) {
                    hookVelocity = Math.min(0x7F, hookVelocity + 1);
                    hookOffsetY = Math.min(0x7F, hookOffsetY + hookVelocity);
                    return;
                }
                state.setCutsceneFlag(1);
            }

            if (!state.cutsceneFlag(2)) {
                int hookX = x;
                int playerX = player.getCentreX();
                if (hookX > 8 && (playerX < hookX - 0x0C || playerX >= hookX + 0x0C)) {
                    return;
                }
                state.setCutsceneFlag(2);
                player.setControlLocked(true);
                player.setObjectMappingFrameControl(true);
                player.setMappingFrame(0xCB);
                player.setAir(true);
            }

            if (!state.cutsceneFlag(3)) {
                hookVelocity--;
                if (hookVelocity < 0) {
                    hookVelocity = 0;
                    state.setCutsceneFlag(3);
                    phase = Phase.RETURN;
                }
                hookOffsetY = Math.max(0x23, hookOffsetY - hookVelocity);
            }
            if (!state.cutsceneFlag(5)) {
                NativePositionOps.writeXPosPreserveSubpixel(player, x);
                NativePositionOps.writeYPosPreserveSubpixel(player, y + hookOffsetY + 0x16);
            }
        });
    }

    private com.openggf.game.sonic3k.runtime.SszZoneRuntimeState state() {
        return S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElseThrow();
    }

    private void swingAndMove(int velocity) {
        int fixed = (x << 8) | (xSub & 0xFF);
        fixed += velocity;
        x = fixed >> 8;
        xSub = fixed & 0xFF;
        swing();
    }

    private void swing() {
        swingAngle = (swingAngle + 4) & 0xFF;
        y = spawn.y() + (int) Math.round(Math.sin(swingAngle * Math.PI / 128.0) * 4.0);
    }

    private void spawnBoss() {
        if (bossSpawned) return;
        bossSpawned = spawnAfterCurrentSibling(() -> new SszMechaSonicObjectInstance(
                new ObjectSpawn(0x220, 0x4A0, 0, 0, 0, false, 0))) != null;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x40; }
    @Override public int getOnScreenHalfHeight() { return 0x30; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, x, y, false, false, -1);
            // Map_KnuxFinalBossCrane's child is a separate SST in the ROM. The engine keeps its
            // gameplay state with the crane but draws a second ship-art frame at the hook tip.
            renderer.drawFrameIndex(0, x, y + hookOffsetY, false, false, -1);
        }
    }

    Phase phaseForTest() { return phase; }
    int timerForTest() { return timer; }
}
