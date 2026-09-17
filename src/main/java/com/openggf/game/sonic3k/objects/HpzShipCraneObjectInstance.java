package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

import static com.openggf.game.sonic3k.objects.HpzKnucklesCutsceneSupport.*;

/**
 * ROM {@code loc_6502E} ({@code ChildObjDat_66608}, {@code ObjDat3_664FA}): the crane under the
 * Hidden Palace ship, created at {@code (0,$23)}. After the camera pan ({@code _unkFAB8} bit 0) it
 * lowers by adding {@code $80} to {@code y_vel} and using the high byte as extra
 * {@code child_dy} until it reaches {@code _unkFABA}'s Y minus {@code $18}. The grab
 * ({@code loc_650A6}) shakes the screen and scatters emerald sparkles and chips; the crane then
 * winds back up, sets the ship's {@code $38} bit 2 and raises the emerald's priority, and from
 * then on carries the emerald {@code $18} pixels below itself.
 */
public final class HpzShipCraneObjectInstance extends AbstractHpzCutsceneChildObjectInstance {
    private static final int START_DY = 0x23;
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x180);
    /** {@code _unkFABD}: {@code -$18}, written by {@code Obj_HPZMasterEmerald}. */
    static final int EMERALD_HANG_OFFSET = -0x18;

    private static final int PHASE_INIT = 0;
    private static final int PHASE_HANG = 1;
    private static final int PHASE_LOWER = 2;
    private static final int PHASE_RAISE = 3;
    private static final int PHASE_CARRY = 4;

    private int phase;
    private int x;
    private int y;
    private int childDy = START_DY;
    /** {@code $3A(a0)}. */
    private int baseDy;
    private int yVel;
    private int mappingFrame;
    private boolean visible;

    public HpzShipCraneObjectInstance(ObjectSpawn spawn, HpzRobotnikShipObjectInstance ship) {
        super(spawn, "HpzShipCrane", ship);
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public HpzShipCraneObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzShipCraneObjectInstance(ctx.spawn(), null);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (parentGone()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        HpzZoneRuntimeState hpz = hpz(services());
        if (hpz == null) {
            return;
        }
        switch (phase) {
            case PHASE_INIT -> {
                mappingFrame = 0;
                phase = PHASE_HANG;
                // ChildObjDat_66610: loc_65138 at (0,0), loc_6515E at (0,-$30).
                spawnChild(() -> new HpzShipCranePartObjectInstance(
                        new ObjectSpawn(x, y, 0, HpzShipCranePartObjectInstance.KIND_CLAW, 0, false, 0),
                        this));
                spawnChild(() -> new HpzShipCranePartObjectInstance(
                        new ObjectSpawn(x, y - 0x30, 0, HpzShipCranePartObjectInstance.KIND_CABLE, 0,
                                false, 0), this));
                updateDynamicSpawn(x, y);
                return;
            }
            case PHASE_HANG -> {
                if (!hpz.knucklesCutsceneFlag(FLAG_CAMERA_READY)) {
                    refresh();
                    visible = true;
                    break;
                }
                phase = PHASE_LOWER;
                baseDy = childDy;
                lower();
            }
            case PHASE_LOWER -> lower();
            case PHASE_RAISE -> {
                raise();
                carry();
            }
            default -> carry();
        }
        updateDynamicSpawn(x, y);
    }

    /** {@code loc_65068}. */
    private void lower() {
        HPZMasterEmeraldObjectInstance emerald = masterEmerald(services());
        if (emerald != null) {
            int target = (emerald.getY() - 0x18) & 0xFFFF;
            if (target <= y) {
                grab();
                return;
            }
            int d0 = (yVel + 0x80) & 0xFFFF;
            if ((short) d0 >= 0) {
                yVel = d0;
            }
            childDy = (byte) (baseDy + (yVel >> 8));
        }
        refresh();
        visible = true;
    }

    /** {@code loc_650A6}. */
    private void grab() {
        phase = PHASE_RAISE;
        mappingFrame = 2;
        HpzZoneRuntimeState hpz = hpz(services());
        if (hpz != null) {
            hpz.screenShake().writeFlag(0x14);
        }
        services().playSfx(Sonic3kSfx.BIG_RUMBLE.id);
        // ChildObjDat_6661E: $F x loc_6518A; ChildObjDat_66638: 5 x loc_65226.
        for (int i = 0; i < 0xF; i++) {
            int subtype = i * 2;
            spawnChild(() -> new HpzCraneEmeraldDebrisObjectInstance(
                    new ObjectSpawn(x, y, 0, subtype, 0, false, 0), this,
                    HpzCraneEmeraldDebrisObjectInstance.KIND_SPARKLE));
        }
        for (int i = 0; i < 5; i++) {
            int subtype = i * 2;
            spawnChild(() -> new HpzCraneEmeraldDebrisObjectInstance(
                    new ObjectSpawn(x, y, 0, subtype, 0, false, 0), this,
                    HpzCraneEmeraldDebrisObjectInstance.KIND_CHIP));
        }
        raise();
        carry();
    }

    /** {@code loc_650D4}. */
    private void raise() {
        int d2 = (short) (yVel - 0x80);
        if (d2 < 0) {
            phase = PHASE_CARRY;
            d2 = 0;
            if (parent instanceof HpzRobotnikShipObjectInstance ship) {
                ship.markCraneLifted();
            }
            HPZMasterEmeraldObjectInstance emerald = masterEmerald(services());
            if (emerald != null) {
                emerald.setHighPriority(true);
            }
        }
        yVel = d2;
        childDy = (byte) (baseDy + (yVel >> 8));
    }

    /** {@code loc_6510C}. */
    private void carry() {
        refresh();
        HPZMasterEmeraldObjectInstance emerald = masterEmerald(services());
        if (emerald != null) {
            emerald.setCarriedPosition(x, (y - EMERALD_HANG_OFFSET) & 0xFFFF);
        }
        visible = true;
    }

    /** {@code Refresh_ChildPositionAdjusted}: {@code child_dx} is 0; the ship does not flip Y. */
    private void refresh() {
        x = parent.getX() & 0xFFFF;
        y = (parent.getY() + childDy) & 0xFFFF;
    }

    int mappingFrame() {
        return mappingFrame;
    }

    int yVel() {
        return yVel;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.KNUX_FINAL_BOSS_CRANE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, true, false);
        }
    }

    @Override
    protected int[] captureState() {
        return new int[]{phase, x, y, childDy, baseDy, yVel, mappingFrame, bool(visible)};
    }

    @Override
    protected void restoreState(int[] s) {
        phase = s[0];
        x = s[1];
        y = s[2];
        childDy = s[3];
        baseDy = s[4];
        yVel = s[5];
        mappingFrame = s[6];
        visible = s[7] != 0;
    }
}
