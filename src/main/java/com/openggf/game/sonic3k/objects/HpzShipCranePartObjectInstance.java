package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code ChildObjDat_66610}: the two sprites the Hidden Palace crane adds to itself.
 * {@code loc_65138} (priority {@code $280}) draws the crane's mapping frame plus one at the
 * crane position, mirrored with it; {@code loc_6515E} (priority {@code $180}, {@code (0,-$30)})
 * draws cable frame {@code 4 + (crane y_vel high byte >> 4)} without mirroring. Both fall through
 * from their init into the per-frame code on the first pass.
 */
public final class HpzShipCranePartObjectInstance extends AbstractHpzCutsceneChildObjectInstance {
    static final int KIND_CLAW = 0;
    static final int KIND_CABLE = 1;
    private static final int CABLE_DY = -0x30;

    private int kind;
    private int x;
    private int y;
    private int mappingFrame;
    private boolean visible;

    public HpzShipCranePartObjectInstance(ObjectSpawn spawn, HpzShipCraneObjectInstance crane) {
        super(spawn, spawn.subtype() == KIND_CLAW ? "HpzShipCraneClaw" : "HpzShipCraneCable", crane);
        kind = spawn.subtype();
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public HpzShipCranePartObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzShipCranePartObjectInstance(ctx.spawn(), null);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (!(parent instanceof HpzShipCraneObjectInstance crane) || crane.isDestroyed()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        x = crane.getX() & 0xFFFF;
        if (kind == KIND_CLAW) {
            y = crane.getY() & 0xFFFF;
            mappingFrame = (crane.mappingFrame() + 1) & 0xFF;
        } else {
            mappingFrame = (((crane.yVel() >> 8) & 0xFF) >> 4) + 4;
            y = (crane.getY() + CABLE_DY) & 0xFFFF;
        }
        visible = true;
        updateDynamicSpawn(x, y);
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isHighPriority() { return true; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(kind == KIND_CLAW ? 0x280 : 0x180);
    }

    @Override
    public int getOnScreenHalfHeight() {
        return kind == KIND_CLAW ? 0xC : 0x80;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.KNUX_FINAL_BOSS_CRANE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, kind == KIND_CLAW, false);
        }
    }

    @Override
    protected int[] captureState() {
        return new int[]{kind, x, y, mappingFrame, bool(visible)};
    }

    @Override
    protected void restoreState(int[] s) {
        kind = s[0];
        x = s[1];
        y = s[2];
        mappingFrame = s[3];
        visible = s[4] != 0;
    }
}
