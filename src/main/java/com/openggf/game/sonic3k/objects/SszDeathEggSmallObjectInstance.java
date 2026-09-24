package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_659CC} / {@code loc_65A30} / {@code loc_65A4A} (sonic3k.asm:133751-133812):
 * the small Death Egg that rises out of Sky Sanctuary while cutscene Knuckles watches.
 *
 * <p>{@code CutsceneKnux_SSZ} routine 4 creates it through {@code ChildObjDat_665F6}. It starts at
 * {@code ($200,$C68)} with {@code y_vel} reused as the 16.16 vertical accumulator seeded to the
 * same {@code $C68} and {@code $40 = -$40} as upward acceleration, and moves with
 * {@code MoveSprite_SSZBGAdjust}: the object tracks the halved camera X delta in {@code _unkFA84}
 * and subtracts {@code _unkEE9C >> 2} so it drifts with the background rather than the
 * foreground. After {@code $2E = $100} frames it starts firing missiles, and once it has risen
 * past {@code Camera_Y - $38} it sets {@code _unkFAB8} bit 1 — the flag Knuckles' routine 8 waits
 * on — and deletes itself.
 *
 * <p>The ROM also reseeds {@code RNG_seed} from {@code V_int_run_count}, patches
 * {@code Normal_palette_line_4} from {@code Pal_KnuxSSZEnd} and creates cloud and missile
 * children. Those are presentation and are recorded as gaps in the act-1 matrix; the rise, the
 * timing and the {@code _unkFAB8} handshake are modelled here because the cutscene route depends
 * on them.
 */
public final class SszDeathEggSmallObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code move.w #$200,x_pos(a0)} / {@code move.w #$C68,d0}. */
    static final int SPAWN_X = 0x200;
    static final int SPAWN_Y = 0xC68;
    /**
     * {@code move.w #-$40,$40(a0)}: {@code MoveSprite_SSZBGAdjust} adds {@code $40 << 8} to the
     * longword at {@code y_vel} every frame, so this is a constant -0.25 px per frame rise, not an
     * acceleration. The longword's high word is the Y the object draws at.
     */
    private static final int RISE_PER_FRAME = -0x40;
    /** {@code move.w #$100,$2E(a0)}. */
    private static final int RISE_FRAMES = 0x100;
    /** {@code loc_65A4A}: {@code subi.w #$38,d0}. */
    private static final int CAMERA_MARGIN = 0x38;
    /** {@code ObjDat3_664AA}: {@code dc.w $380}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x380);

    private int x = SPAWN_X;
    private int y = SPAWN_Y;
    /** {@code y_vel(a0)} as the ROM's 16.16 longword. */
    private int riseAccumulator = SPAWN_Y << 16;
    private int timer = RISE_FRAMES;
    private boolean firing;

    private record RewindExtra(int x, int y, int riseAccumulator, int timer, boolean firing)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszDeathEggSmallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SszDeathEggSmall");
    }

    @Override
    public SszDeathEggSmallObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SszDeathEggSmallObjectInstance(ctx.spawn());
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(x, y, riseAccumulator, timer, firing));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            x = extra.x();
            y = extra.y();
            riseAccumulator = extra.riseAccumulator();
            timer = extra.timer();
            firing = extra.firing();
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
        moveSpriteSszBgAdjust(state);
        if (!firing) {
            timer--;
            if (timer < 0) {
                firing = true;
            }
            return;
        }
        int cameraY = services().camera().getY() & 0xFFFF;
        if ((short) (cameraY - CAMERA_MARGIN) <= (short) y) {
            return;
        }
        if (state != null) {
            state.setCutsceneFlag(CutsceneKnucklesSszInstance.FLAG_DEATH_EGG_RISEN);
        }
        com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this);
    }

    /** {@code MoveSprite_SSZBGAdjust} (sonic3k.asm:134355-134371). */
    private void moveSpriteSszBgAdjust(SszZoneRuntimeState state) {
        int cameraDelta = state == null ? 0 : state.backgroundCameraDelta();
        x = (x + cameraDelta) & 0xFFFF;
        riseAccumulator += RISE_PER_FRAME << 8;
        int integer = riseAccumulator >> 16;
        int oscillator = state == null ? 0 : state.cloudOscillator();
        y = (integer - (oscillator >> 2)) & 0xFFFF;
    }

    int timerForTest() { return timer; }
    boolean firingForTest() { return firing; }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x3C; }
    @Override public int getOnScreenHalfHeight() { return 0x30; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_DEATH_EGG_SMALL);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, x, y, false, false);
        }
    }
}
