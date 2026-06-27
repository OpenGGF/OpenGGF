package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * Invisible LBZ1 cutscene range helper.
 *
 * <p>ROM reference: {@code loc_627C6}, with player box
 * {@code word_62822 = -$40,+$80,-$30,+$60}.
 */
public final class CutsceneKnucklesLbz1RangeHelper extends AbstractObjectInstance implements RewindRecreatable {
    private static final int LEFT = -0x40;
    private static final int RIGHT = 0x80;
    private static final int TOP = -0x30;
    private static final int BOTTOM = 0x60;

    private final CutsceneKnucklesLbz1Instance parent;
    private int x;
    private int y;

    CutsceneKnucklesLbz1RangeHelper(CutsceneKnucklesLbz1Instance parent, int x, int y) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "CutsceneKnuxLBZ1RangeHelper");
        this.parent = parent;
        this.x = x;
        this.y = y;
    }

    CutsceneKnucklesLbz1RangeHelper(ObjectSpawn spawn) {
        this(null, spawn != null ? spawn.x() : 0, spawn != null ? spawn.y() : 0);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        CutsceneKnucklesLbz1Instance liveParent = CutsceneKnucklesLbz1RewindLinks.singleLiveParent(ctx);
        if (liveParent == null) {
            return null;
        }
        ObjectSpawn spawn = ctx.spawn();
        int capturedX = spawn != null ? spawn.x() : x;
        int capturedY = spawn != null ? spawn.y() : y;
        return new CutsceneKnucklesLbz1RangeHelper(liveParent, capturedX, capturedY);
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (parent == null || parent.isDestroyed()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }

        boolean capturedPlayer1 = false;
        if (playerEntity instanceof AbstractPlayableSprite player && isInsideRange(player)) {
            applyCapture(player);
            capturedPlayer1 = true;
        }

        if (services().spriteManager() != null) {
            for (AbstractPlayableSprite sidekick : services().spriteManager().getRegisteredSidekicks()) {
                if (isInsideRange(sidekick)) {
                    applyCapture(sidekick);
                }
            }
        }

        if (capturedPlayer1) {
            parent.signalHelperCapture();
        }
    }

    private boolean isInsideRange(AbstractPlayableSprite player) {
        int playerX = player.getCentreX() & 0xFFFF;
        int playerY = player.getCentreY() & 0xFFFF;
        return playerX >= x + LEFT
                && playerX < x + RIGHT
                && playerY >= y + TOP
                && playerY < y + BOTTOM;
    }

    private void applyCapture(AbstractPlayableSprite player) {
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setControlLocked(true);
        player.clearForcedInputMask();
        player.clearLogicalInputState();
        player.setDirection(Direction.RIGHT);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible trigger helper.
    }
}
