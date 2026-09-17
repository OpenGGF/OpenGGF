package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * The Hidden Palace ship's {@code Child1_MakeRoboHead4} and {@code Child1_MakeRoboShipFlame}.
 *
 * <p>{@code Obj_RobotnikHead4} follows the ship at {@code (0,-$1C)} with
 * {@code AniRaw_RobotnikHead} (frames 0/1), shows frame 2 while the ship's {@code status} bit 6 is
 * set, and deletes when the ship's {@code $38} bit 5 is set. {@code Obj_RobotnikShipFlame}
 * follows at {@code ($1E,0)} mirrored by the ship's flip, draws frame 6 only on even
 * {@code V_int_run_count} passes while the ship moves horizontally, and deletes on {@code $38}
 * bit 4. Both are {@code Map_RobotnikShip} over {@code ArtTile_RobotnikShip}.
 */
public final class HpzRobotnikShipChildObjectInstance extends AbstractHpzCutsceneChildObjectInstance {
    static final int KIND_HEAD = 0;
    static final int KIND_FLAME = 1;
    private static final int FLAME_FRAME = 6;
    private static final int FLAME_DX = 0x1E;
    private static final int HEAD_DY = -0x1C;

    private int kind;
    private int x;
    private int y;
    private boolean flipX;
    private boolean initialized;
    private boolean visible;
    private final S3kRawAnimation.State anim = new S3kRawAnimation.State();

    public HpzRobotnikShipChildObjectInstance(ObjectSpawn spawn, HpzRobotnikShipObjectInstance ship,
                                              int kind) {
        super(new ObjectSpawn(spawn.x(), spawn.y(), 0, kind, 0, false, 0),
                kind == KIND_HEAD ? "HpzRobotnikHead" : "HpzRobotnikShipFlame", ship);
        this.kind = kind;
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public HpzRobotnikShipChildObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzRobotnikShipChildObjectInstance(ctx.spawn(), null, ctx.spawn().subtype());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (!(parent instanceof HpzRobotnikShipObjectInstance ship) || ship.isDestroyed()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        if (kind == KIND_HEAD) {
            updateHead(ship);
        } else {
            updateFlame(ship, vIntRunCount);
        }
        updateDynamicSpawn(x, y);
    }

    /** {@code Obj_RobotnikHead4}. */
    private void updateHead(HpzRobotnikShipObjectInstance ship) {
        refreshPosition(ship, 0, HEAD_DY);
        if (!initialized) {
            // Obj_RobotnikHead3Init: ObjDat_RobotnikHead frame 0, $30 = AniRaw_RobotnikHead.
            initialized = true;
            anim.mappingFrame = 0;
        } else {
            // Obj_RobotnikHead3Main: Animate_Raw
            HpzKnucklesCutsceneSupport.robotnikHeadScript(services()).animateNoSst(
                    anim, HpzKnucklesCutsceneSupport.ANI_RAW_ROBOTNIK_HEAD, () -> { });
            if (ship.hitFlag()) {
                anim.mappingFrame = 2;
            }
        }
        if (ship.parentFlag(5)) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        visible = true;
    }

    /** {@code Obj_RobotnikShipFlame}. */
    private void updateFlame(HpzRobotnikShipObjectInstance ship, int vInt) {
        if (!initialized) {
            // ObjDat3_RoboShipFlame; the code pointer moves to Obj_RobotnikShipFlameMain.
            initialized = true;
            return;
        }
        if (ship.parentFlag(4)) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        refreshPosition(ship, FLAME_DX, 0);
        if ((vInt & 1) != 0 || ship.xVel() == 0) {
            return;
        }
        visible = true;
    }

    /** {@code Refresh_ChildPositionAdjusted}: the ship is always drawn flipped. */
    private void refreshPosition(HpzRobotnikShipObjectInstance ship, int dx, int dy) {
        flipX = true;
        x = (ship.getX() - dx) & 0xFFFF;
        y = (ship.getY() + dy) & 0xFFFF;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isHighPriority() { return kind == KIND_HEAD; }
    @Override public int getPriorityBucket() { return kind == KIND_HEAD ? 0 : 5; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(kind == KIND_HEAD ? anim.mappingFrame : FLAME_FRAME, x, y, flipX, false);
        }
    }


}
