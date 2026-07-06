package com.openggf.level.objects.boss;

import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import com.openggf.debug.DebugColor;

/**
 * Base class for boss child components with common functionality.
 * Child components are full object instances so they can participate in collision and rendering buckets.
 */
public abstract class AbstractBossChild extends AbstractObjectInstance implements BossChildComponent {
    protected final AbstractBossInstance parent;
    protected int currentX;
    protected int currentY;
    protected int priority;
    /** Collision flags set by parent via initChildCollisions/removeAllCollision */
    protected int collisionFlags;
    /** X-flip state, set by parent's updateChildFacing() */
    protected boolean flipX;
    private ObjectSpawn dynamicSpawn;
    private int lastUpdatedFrame = -1;
    /**
     * Stable 0-based construction ordinal among this parent's SAME-RUNTIME-CLASS
     * siblings (see {@link AbstractBossInstance#nextChildOrdinal}), carried through
     * every {@link #dynamicSpawn} rebuild via {@link ObjectSpawn#subtype()} --
     * otherwise unused by boss children -- so rewind's dynamic-object adoption can
     * tell indistinguishable same-class siblings (e.g. EHZ's 3 {@code EHZBossWheel}
     * instances) apart even after an earlier sibling was destroyed and pruned
     * before capture. See {@code ObjectManager.adoptRewindReconstructionChild}.
     * Not {@code final}: purely deterministic (recomputed identically by
     * construction order on every reconstruction) so it doesn't strictly need
     * round-trip capture, but a non-final scalar rides the existing generic
     * compact-schema capture for free instead of tripping the rewind-coverage
     * guard's "uncapturable final field" check across every boss-child subclass.
     */
    private int childOrdinal;

    /** See {@link #childOrdinal}. */
    public int getChildOrdinal() {
        return childOrdinal;
    }

    public AbstractBossChild(AbstractBossInstance parent, String name, int priority, int objectId) {
        super(new ObjectSpawn(parent.getX(), parent.getY(), objectId, 0, 0, false, 0), name);
        this.parent = parent;
        this.priority = priority;
        this.childOrdinal = parent.nextChildOrdinal(getClass());
        syncPositionWithParent();
        this.dynamicSpawn = buildDynamicSpawn();
    }

    private ObjectSpawn buildDynamicSpawn() {
        return new ObjectSpawn(
                currentX,
                currentY,
                spawn.objectId(),
                childOrdinal,
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
    }

    protected boolean beginUpdate(int frameCounter) {
        if (lastUpdatedFrame == frameCounter) {
            return false;
        }
        lastUpdatedFrame = frameCounter;
        return true;
    }

    protected void updateDynamicSpawn() {
        if (dynamicSpawn.x() == currentX && dynamicSpawn.y() == currentY) {
            return;
        }
        dynamicSpawn = buildDynamicSpawn();
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn;
    }

    /**
     * Drops this instance from the parent's {@link AbstractBossInstance#childComponents}
     * when {@code ObjectManager} determines, after a rewind restore, that this
     * instance was a freshly-constructed reconstruction child left unmatched by any
     * captured {@code DynamicObjectEntry} (e.g. a sibling position the destroyed
     * original no longer has captured state for). Without this, the boss's own
     * unconditional re-spawn of all children on every reconstruction leaves the
     * orphan live in {@code childComponents} -- not destroyed, never registered with
     * {@code ObjectManager} -- so it keeps receiving {@code update()} forever and can
     * corrupt shared parent state (e.g. EHZ's {@code wheelYAccumulator}).
     */
    @Override
    protected void onDroppedAsUnmatchedRewindReconstructionChild() {
        parent.childComponents.remove(this);
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(priority);
    }

    /**
     * A boss child shares its parent's lifetime: it must survive (or be culled)
     * exactly when the parent does. Without this, a child that leads the boss
     * (e.g. a forward-mounted drill/spike) can cross the off-screen out-of-range
     * cull threshold one chunk ahead of the body, get unloaded as an ordinary
     * dynamic object, and never be rebuilt — the parent only spawns its children
     * once at construction. Inheriting the parent's persistence keeps the whole
     * boss group alive together, matching the ROM, where fixed-arena boss parts
     * are not deleted off-screen during the fight.
     */
    @Override
    public boolean isPersistent() {
        return parent != null && parent.isPersistent();
    }

    @Override
    public void syncPositionWithParent() {
        if (parent != null && !parent.isDestroyed()) {
            this.currentX = parent.getX();
            this.currentY = parent.getY();
        }
    }

    protected boolean shouldUpdate(int frameCounter) {
        if (parent != null && parent.getState().lastUpdatedFrame != frameCounter) {
            return false;
        }
        return beginUpdate(frameCounter);
    }

    @Override
    public boolean isDestroyed() {
        if (destroyWhenParentDestroyed() && (parent == null || parent.isDestroyed())) {
            setDestroyed(true);
        }
        return super.isDestroyed();
    }

    /**
     * Most boss child components are owned by the boss lifetime. A few ROM
     * bosses allocate independent child object slots that run their own delete
     * routines after the parent is gone; those children can opt out.
     */
    protected boolean destroyWhenParentDestroyed() {
        return true;
    }

    @Override
    public void setDestroyed(boolean destroyed) {
        super.setDestroyed(destroyed);
    }

    public int getPriority() {
        return priority;
    }

    /**
     * Set collision flags on this child (used by parent boss for init/remove collision).
     * Individual child getCollisionFlags() implementations should check this field.
     */
    public void setCollisionFlags(int flags) {
        this.collisionFlags = flags;
    }

    /**
     * Get the stored collision flags value (set by parent).
     */
    public int getStoredCollisionFlags() {
        return collisionFlags;
    }

    /**
     * Set the x-flip state on this child (used by parent's updateChildFacing).
     * ROM: loc_3E168 copies body's render_flags.x_flip to all children.
     */
    public void setFlipX(boolean flip) {
        this.flipX = flip;
    }

    /**
     * Get the x-flip state of this child.
     */
    public boolean isFlipX() {
        return flipX;
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Blue cross marker at position
        ctx.drawCross(currentX, currentY, 4, 0.3f, 0.3f, 1f);
        // Cyan name label
        ctx.drawWorldLabel(currentX, currentY, -1, name, DebugColor.CYAN);
    }

    public int getCurrentX() {
        return currentX;
    }

    public int getCurrentY() {
        return currentY;
    }

    /**
     * Set the child's position directly.
     * Used by parent bosses with articulated body parts (e.g., DEZ Robot).
     */
    public void setPosition(int x, int y) {
        this.currentX = x;
        this.currentY = y;
    }
}
