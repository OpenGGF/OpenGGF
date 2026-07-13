package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ExplosionObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Locked-on {@code Obj_FBZMine} ($E1), $3C93E-$3CA08. */
public final class FbzMineObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, SpawnRewindRecreatable {
    private enum State { IDLE, BLINK, ARMED, EXPLODED }

    private State state = State.IDLE;
    private int countdown;
    private int mappingFrame;

    public FbzMineObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FBZMine");
    }

    @Override
    public void update(int frameCounter, PlayableEntity ignored) {
        switch (state) {
            case IDLE -> services().playerQuery().visitPlayers(
                    ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS, this,
                    FbzMineObjectInstance::checkProximity);
            case BLINK -> {
                mappingFrame ^= 1;
                if (--countdown < 0) state = State.ARMED;
            }
            case ARMED -> explodeInPlace();
            case EXPLODED -> { }
        }
    }

    private static void checkProximity(FbzMineObjectInstance mine, PlayableEntity player) {
        if (mine.state != State.IDLE || player.isDebugMode()) return;
        int relativeX = (short) (player.getCentreX() - mine.spawn.x());
        int relativeY = (short) (player.getCentreY() - mine.spawn.y());
        if (Integer.compareUnsigned((relativeX + 0x10) & 0xFFFF, 0x20) < 0
                && Integer.compareUnsigned((relativeY + 0x18) & 0xFFFF, 0x20) < 0) {
            mine.countdown = 0x1E;
            mine.state = State.BLINK;
        }
    }

    private void explodeInPlace() {
        state = State.EXPLODED;
        services().playSfx(Sonic3kSfx.EXPLODE.id);
        int slot = ObjectLifetimeOps.detachSlotForTransfer(this);
        setDestroyed(true);
        ObjectLifetimeOps.addReplacementAtTransferredSlot(
                services().objectManager(),
                new ExplosionObjectInstance(6, spawn.x(), spawn.y(), services().renderManager()),
                slot);
    }

    public int armCountdownReload() { return 0x1E; }
    public boolean isArmed() { return state == State.ARMED; }
    public int mappingFrame() { return mappingFrame; }
    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return spawn.y(); }
    @Override public int getPriorityBucket() { return 1; }
    @Override public int getCollisionFlags() { return state == State.ARMED ? 0x8B : 0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_MINE);
        if (renderer != null && renderer.isReady() && state != State.EXPLODED) {
            renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), false, false);
        }
    }
}
