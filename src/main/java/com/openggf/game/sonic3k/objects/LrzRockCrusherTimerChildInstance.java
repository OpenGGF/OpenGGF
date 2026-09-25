package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * {@code loc_90502} / {@code loc_90512}, the invisible timer {@code Obj_LRZRockCrusher} allocates
 * when both of its camera limits have been reached (sonic3k.asm:197215-197226, ROM {@code $90502}).
 *
 * <p>{@code loc_90502} raises {@code Screen_shake_flag} and loads {@code (3*60)-1} into
 * {@code $2E(a0)}; {@code loc_90512} counts it down and, on the frame it goes negative, performs
 * the crusher's whole world change and deletes itself (:197218-197246):
 *
 * <ul>
 *   <li><b>Subtype 0</b>: {@code st (Events_bg+$0C)} -- the NEGATIVE chunk-edit request that
 *       {@code LRZ1_ScreenEvent}'s {@code loc_56B2C} answers -- clears {@code Screen_shake_flag},
 *       and allocates two {@code Obj_LRZCollapsingBridge} with {@code $32 = 1} at
 *       {@code ($F00,$760)} and {@code ($F80,$760)}.</li>
 *   <li><b>Any other subtype</b>: {@code st (Events_bg+$0D)} -- the same word's LOW byte, so the
 *       request reads POSITIVE and {@code LRZ1_ScreenEvent} takes the single {@code $9C} write
 *       instead -- leaves the shake running, allocates one bridge at {@code ($540,$860)}, restores
 *       {@code Camera_target_max_Y_pos} from {@code Camera_stored_max_Y_pos} and creates
 *       {@code Child7_ChangeLevSize}.</li>
 * </ul>
 *
 * <p>Both branches use plain {@code AllocateObject}, not {@code AllocateObjectAfterCurrent}, so the
 * bridges take the lowest free slots and may run before this object is deleted.
 */
public final class LrzRockCrusherTimerChildInstance extends AbstractObjectInstance
        implements RewindRecreatable {

    /** {@code move.w #(3*60)-1,$2E(a0)} (sonic3k.asm:197217). */
    static final int COUNTDOWN = 3 * 60 - 1;
    /** {@code st (Events_bg+$0C).w}: the whole word set to {@code -1}. */
    static final int CHUNK_EDIT_REQUEST_NEGATIVE = -1;
    /** {@code st (Events_bg+$0D).w}: the low byte only, so the word reads {@code $00FF}. */
    static final int CHUNK_EDIT_REQUEST_POSITIVE = 0x00FF;

    /** ROM {@code subtype(a0)}, copied from the parent (:197247). */
    private int subtype;
    /** ROM {@code $2E(a0)}. */
    private int countdown;
    private boolean armed;

    /** Restore entry: the captured scalar state replaces this harmless initial subtype. */
    private LrzRockCrusherTimerChildInstance(ObjectSpawn spawn) {
        this(spawn.subtype());
    }

    public LrzRockCrusherTimerChildInstance(int subtype) {
        super(new ObjectSpawn(0, 0, Sonic3kObjectIds.SPIKER, subtype, 0, false, 0),
                "LRZRockCrusherTimer");
        this.subtype = subtype & 0xFF;
    }

    @Override
    public LrzRockCrusherTimerChildInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzRockCrusherTimerChildInstance(ctx.spawn().subtype()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!armed) {
            // loc_90502 (:197215-197217).
            armed = true;
            countdown = COUNTDOWN;
            screenShakeFlag(true);
            return;
        }
        // loc_90512 (:197218-197219): subq.w #1,$2E(a0) / bpl.
        countdown--;
        if (countdown >= 0) {
            return;
        }
        if (subtype == 0) {
            fireSubtypeZero();
        } else {
            fireOtherSubtype();
        }
        ObjectLifetimeOps.expireDynamic(this);
    }

    /** {@code loc_90512}'s {@code beq} arm (sonic3k.asm:197221-197245). */
    private void fireSubtypeZero() {
        LrzZoneRuntimeState state = lrzState();
        if (state != null) {
            state.setChunkEditRequest(CHUNK_EDIT_REQUEST_NEGATIVE);
        }
        screenShakeFlag(false);
        spawnBridge(0x0F00, 0x0760);
        spawnBridge(0x0F80, 0x0760);
    }

    /** {@code loc_9056E} (sonic3k.asm:197248-197260). */
    private void fireOtherSubtype() {
        LrzZoneRuntimeState state = lrzState();
        if (state != null) {
            state.setChunkEditRequest(CHUNK_EDIT_REQUEST_POSITIVE);
        }
        spawnBridge(0x0540, 0x0860);
        LrzRockCrusherObjectInstance.restoreTargetMaxY(services(), state);
        for (int kind : LrzRockCrusherObjectInstance.changeLevSizeKinds()) {
            final int k = kind;
            spawnFreeChild(() -> new S3kCameraGradualObjectInstance(k));
        }
    }

    /**
     * {@code move.l #Obj_LRZCollapsingBridge,(a1) / move.b #1,$32(a1)} (:197227-197231). The
     * bridge's {@code $32} is its "already broken loose" flag, which is why these arrive already
     * falling rather than waiting to be stood on.
     */
    private void spawnBridge(int x, int y) {
        spawnFreeChild(() -> {
            LrzCollapsingBridgeInstance slab = new LrzCollapsingBridgeInstance(
                    LrzCollapsingBridgeInstance.crusherDebrisSpawn(x, y));
            slab.markAlreadyBrokenLoose();
            return slab;
        });
    }

    private LrzZoneRuntimeState lrzState() {
        try {
            return services().zoneRuntimeState() instanceof LrzZoneRuntimeState lrz ? lrz : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void screenShakeFlag(boolean raised) {
        LrzZoneRuntimeState state = lrzState();
        if (state != null) {
            state.screenShake().writeFlag(raised ? -1 : 0);
        }
    }

    /** ROM {@code $2E(a0)}. */
    public int countdown() {
        return countdown;
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        return true;
    }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return false;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // loc_90512 never draws: the object exists only to hold the countdown.
    }
}
