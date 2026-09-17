package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_6531E} ({@code ChildSpriteDat_66624}, two copies at {@code (8,-4)}): a spark
 * emitter under the Hidden Palace ship once it reaches {@code X $1890}. It creates a ten-link
 * {@code CreateChild8_TreeListRepeated} chain of {@code loc_65376} (subtype 0) or
 * {@code loc_65360} (subtype 2) orbiters, draws {@code Map_KnuxFinalBossCrane} frame {@code $12}
 * and sets its {@code $38} bit 7 after 240 frames, which releases the chain's spin.
 */
public final class HpzShipSparkEmitterObjectInstance extends AbstractHpzCutsceneChildObjectInstance {
    private static final int DX = 8;
    private static final int DY = -4;
    private static final int FRAME = 0x12;
    private static final int CHAIN_LENGTH = 10;

    private int x;
    private int y;
    private int timer;
    private boolean initialized;
    private boolean released;
    private boolean visible;

    public HpzShipSparkEmitterObjectInstance(ObjectSpawn spawn, HpzRobotnikShipObjectInstance ship) {
        super(spawn, "HpzShipSparkEmitter", ship);
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public HpzShipSparkEmitterObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzShipSparkEmitterObjectInstance(ctx.spawn(), null);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (!initialized) {
            initialized = true;
            timer = 4 * 60;
            // CreateChild8_TreeListRepeated: parent3 = previous link, $44 = this emitter.
            AbstractObjectInstance previous = this;
            for (int i = 0; i < CHAIN_LENGTH; i++) {
                int subtype = i * 2;
                AbstractObjectInstance link = previous;
                previous = spawnChild(() -> new HpzShipSparkOrbiterObjectInstance(
                        new ObjectSpawn(x, y, 0, subtype, spawn.subtype(), false, 0), link, this));
            }
            updateDynamicSpawn(x, y);
            return;
        }
        timer = (short) (timer - 1);
        if (timer == 0) {
            released = true;
        }
        if (parentGone()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        // Refresh_ChildPositionAdjusted under the flipped ship.
        x = (parent.getX() - DX) & 0xFFFF;
        y = (parent.getY() + DY) & 0xFFFF;
        visible = true;
        updateDynamicSpawn(x, y);
    }

    /** {@code $38} bit 7. */
    boolean released() {
        return released;
    }

    /** {@code subtype(a1)} read through the orbiters' {@code $44}. */
    int emitterSubtype() {
        return spawn.subtype() & 0xFF;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return 0; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.KNUX_FINAL_BOSS_CRANE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(FRAME, x, y, true, false);
        }
    }


}
