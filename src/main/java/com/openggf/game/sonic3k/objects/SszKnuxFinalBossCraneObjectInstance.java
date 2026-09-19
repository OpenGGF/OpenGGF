package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Knuckles' SSZ2 final-boss crane ({@code Obj_KnuxFinalBossCrane},
 * sonic3k.asm:166241-166357).
 *
 * <p>The placed object owns the otherwise easy-to-miss act-2 route gate: init clamps
 * {@code Camera_max_X} to {@code Camera_min_X}; the first airborne player frame starts the
 * leftward pickup approach; the native hand-shake then returns the carrier to {@code $120};
 * and a signed 59-word wait starts final-boss music and locks scrolling before the encounter
 * allocation. The grab children and boss allocation are separate route slices, so this object
 * exposes the same phase boundary without inventing a substitute fight.</p>
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
    private int timer;
    private boolean bossSpawned;
    private Phase phase = Phase.INIT;

    public SszKnuxFinalBossCraneObjectInstance(ObjectSpawn spawn) {
        super(spawn, "KnuxFinalBossCrane");
        x = spawn.x();
        y = spawn.y();
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
                    phase = Phase.WAIT_PICKUP;
                }
            }
            case WAIT_PICKUP -> {
                // _unkFAB8 bits 1..3 are written by the crane's grab children. Until those
                // children land, use the player crossing the hook as the production route gate.
                AbstractPlayableSprite player = services().spriteManager().getMainPlayable();
                if (player != null && Math.abs(player.getCentreX() - x) <= 0x20) {
                    phase = Phase.RETURN;
                }
                swing();
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
        }
    }

    Phase phaseForTest() { return phase; }
    int timerForTest() { return timer; }
}
