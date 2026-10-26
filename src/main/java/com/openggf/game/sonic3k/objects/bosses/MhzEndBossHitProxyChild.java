package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;

import java.util.List;

/**
 * Vulnerable hit proxy for S3K {@code Obj_MHZEndBoss}.
 *
 * <p>ROM reference: {@code ChildObjDat_76982 -> loc_764A0}. The child refreshes
 * from parent offset {@code +$21,-$10}, uses {@code word_76964}
 * {@code collision_flags=$25}. Accepted contact changes this slot; the parent's
 * post-dispatch {@code sub_76782} then consumes the cleared collision byte.
 */
public final class MhzEndBossHitProxyChild extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable, RewindRecreatable {
    private static final int X_OFFSET = 0x21;
    private static final int Y_OFFSET = -0x10;
    private static final int ACTIVE_COLLISION_FLAGS = 0x25;
    private static final int COLLISION_PROPERTY = -1;
    private static final int RENDER_HALF_WIDTH = 0x18;
    private static final int RENDER_HALF_HEIGHT = 0x28;

    private MhzEndBossInstance parent;
    private int x;
    private int y;
    private int collisionFlags = ACTIVE_COLLISION_FLAGS;
    private int collisionProperty = COLLISION_PROPERTY;
    private PlayableEntity lastHitPlayer;

    private MhzEndBossHitProxyChild() {
        this(new ObjectSpawn(0, 0, Sonic3kObjectIds.MHZ_END_BOSS, 0, 0, false, 0));
    }

    /** Pure recreation shell; parent3 is restored through its captured ObjectRefId. */
    private MhzEndBossHitProxyChild(ObjectSpawn spawn) {
        super(spawn, "MHZEndBossHitProxy");
        x = spawn.x();
        y = spawn.y();
    }

    MhzEndBossHitProxyChild(MhzEndBossInstance parent) {
        this(new ObjectSpawn(parent.getX() + X_OFFSET, parent.getY() + Y_OFFSET,
                Sonic3kObjectIds.MHZ_END_BOSS, 0, 0, false, 0));
        this.parent = parent;
        parent.bindHitProxy(this);
        refreshFromParent();
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new MhzEndBossHitProxyChild(ctx.spawn());
    }

    @Override
    public boolean isPersistent() {
        // loc_764C4 uses Child_AddToTouchList.
        // Those routines have no range cull; update owns native retirement.
        return true;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (parent == null) {
            return; // An unhydrated recreation shell has no native authority yet.
        }
        // Child_AddToTouchList tests native parent status bit7, which loc_76164
        // publishes before the still-live escape body waits for its music fade.
        if (parent.hasRetiredTouchChildren() || parent.isDestroyed()) {
            setDestroyed(true);
            return;
        }
        refreshFromParent();
        updateDynamicSpawn(x, y);
    }

    @Override
    public void setDestroyed(boolean destroyed) {
        super.setDestroyed(destroyed);
        // Allocation rejection also reaches this hook before the child acquires
        // an ObjectManager identity. Never retain that unadmitted reference.
        if (destroyed && parent != null) {
            parent.retireHitProxy(this);
        }
    }

    @Override
    protected void onRemovedFromObjectManager() {
        if (parent != null) {
            parent.retireHitProxy(this);
        }
    }

    private void refreshFromParent() {
        x = parent.getX() + X_OFFSET;
        y = parent.getY() + Y_OFFSET;
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
    public int getOnScreenHalfWidth() {
        return RENDER_HALF_WIDTH;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return RENDER_HALF_HEIGHT;
    }

    @Override
    public int getCollisionFlags() {
        return parent == null || isDestroyed() || parent.getState().defeated ? 0 : collisionFlags;
    }

    void disableCollision() {
        collisionFlags = 0;
    }

    void restoreCollision() {
        collisionFlags = ACTIVE_COLLISION_FLAGS;
        collisionProperty = COLLISION_PROPERTY;
    }

    PlayableEntity getLastHitPlayer() {
        return lastHitPlayer;
    }

    @Override
    public int getCollisionProperty() {
        return collisionProperty;
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (getCollisionFlags() == 0) {
            return;
        }
        // Touch_Enemy.checkhurtenemy mutates only the struck child slot.
        // Its parent reads this state after its own routine dispatch.
        collisionFlags = 0;
        collisionProperty = (byte) (collisionProperty - 1);
        lastHitPlayer = player;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // ROM loc_764C4 ends at Child_AddToTouchList, not a draw helper.
    }

    @Override
    public int getPriorityBucket() {
        return 4; // word_76964 priority $200
    }
}
