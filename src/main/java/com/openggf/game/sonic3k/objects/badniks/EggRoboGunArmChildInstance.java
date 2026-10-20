package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_916EE}-{@code loc_91750} (sonic3k.asm:198597-198631): the EggRobo's gun arm,
 * the second row of {@code ChildObjDat_919D0} at {@code (-$1C,-4)}.
 *
 * <p>INIT loads {@code word_919C4} and returns without following, firing or drawing. MAIN's
 * {@code sub_91930} uses the parent's X and its delayed {@code $32} Y, falling back to live Y
 * only when that word is zero. When the parent arms it, the gun makes one shot allocation and
 * selects a {@code $5F} cooldown before that attempt. The cooldown draws its latched position.
 *
 * <p>Both active tails test parent status bit 7 only after their own work. A retired parent
 * therefore still supplies the final refresh or timer operands; {@code Go_Delete_Sprite}
 * selects an undrawn delete for the next own dispatch.
 */
public final class EggRoboGunArmChildInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code word_919C4}: {@code dc.w $280}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x280);
    /** {@code ChildObjDat_919D0} row 1: {@code dc.b -$1C,-4}. */
    static final int CHILD_DX = -0x1C;
    static final int CHILD_DY = -0x04;
    /** {@code word_919C4}: {@code dc.b $10,$C,2,0}. */
    private static final int MAPPING_FRAME = 2;
    /** {@code move.w #$5F,$2E(a0)}. */
    static final int FIRE_COOLDOWN = 0x5F;
    /** {@code ChildObjDat_919DE}: {@code dc.b $B,-4}. */
    static final int SHOT_DX = 0x0B;
    static final int SHOT_DY = -0x04;

    private enum Phase { INIT, MAIN, COOLDOWN, DELETE }

    private EggRoboBadnikInstance parent;
    private int x;
    private int y;
    private boolean hFlip;
    private boolean vFlip;
    /** CreateChild1_Normal copies art_tile before this child ever executes INIT. */
    private boolean artHigh;
    private int mappingFrame;
    /** Native {@code $2E}; the routine selects when this cleared word becomes a timer. */
    private int cooldown;
    private Phase phase = Phase.INIT;
    private boolean drawSubmitted;

    private record RewindExtra(ObjectRefId parentId, int x, int y, int cooldown, boolean hFlip, boolean vFlip,
                               int mappingFrame, Phase phase, boolean drawSubmitted,
                               boolean worldPositioned, boolean artHigh)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public EggRoboGunArmChildInstance(ObjectSpawn spawn, EggRoboBadnikInstance parent) {
        super(spawn, "SSZEggRoboGunArm");
        this.parent = parent;
        this.x = spawn.x();
        this.y = spawn.y();
        // CreateChild1_Normal copies mappings/art, not render flags, into the cleared SST.
        setRomWorldPositioned(false);
    }

    /** Pure native allocation copy; public/probe constructors retain cleared art priority. */
    static EggRoboGunArmChildInstance nativeChild(ObjectSpawn spawn, EggRoboBadnikInstance parent,
                                                        boolean artHigh) {
        var child = new EggRoboGunArmChildInstance(spawn, parent);
        child.artHigh = artHigh;
        return child;
    }

    /** Pure probe/rewind shell; restore resolves the captured exact parent. */
    public EggRoboGunArmChildInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    @Override
    public EggRoboGunArmChildInstance recreateForRewind(RewindRecreateContext ctx) {
        return new EggRoboGunArmChildInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        drawSubmitted = false;
        if (isDestroyed()) {
            return;
        }
        if (phase == Phase.DELETE) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (phase == Phase.INIT) {
            // loc_916EE: SetUp_ObjAttributes3, inherit only parent coordinate bit 2, then RTS.
            mappingFrame = MAPPING_FRAME;
            setRomWorldPositioned(parent != null && parent.participatesInRomWorldTransitionOffset());
            phase = Phase.MAIN;
            return;
        }
        if (parent == null) {
            // A missing captured owner supplies no native refresh or timer-clear operands.
            selectDelete();
            return;
        }
        if (phase == Phase.COOLDOWN) {
            // loc_9173A neither follows nor fires, including the timer-underflow entry.
            if (--cooldown < 0) {
                phase = Phase.MAIN;
                parent.clearGunArmed();
            }
        } else {
            // sub_91930 precedes the fire request and the final parent-status test.
            hFlip = !parent.badnikFacingLeft();
            x = (parent.getX() + (hFlip ? -CHILD_DX : CHILD_DX)) & 0xFFFF;
            // sub_91930 / loc_91964 (FixBugs=0): select $32/live Y first, then
            // copy render bit 1 and negate the signed child_dy word when set.
            int anchorY = parent.childAnchorY();
            vFlip = parent.nativeVFlip();
            y = (anchorY + (vFlip ? -CHILD_DY : CHILD_DY)) & 0xFFFF;
            updateDynamicSpawn(x, y);
            if (parent.gunArmed()
                    && com.openggf.game.mutators.NativeObjectPolicyAccess.permitsFiring(services(), parent.getX())) {
                phase = Phase.COOLDOWN;
                cooldown = com.openggf.game.mutators.NativeObjectPolicyAccess.firingCooldown(services(), FIRE_COOLDOWN);
                int shotX = (x + (hFlip ? -SHOT_DX : SHOT_DX)) & 0xFFFF;
                int shotY = (y + SHOT_DY) & 0xFFFF;
                boolean facingLeft = !hFlip;
                spawnChild(() -> EggRoboShotInstance.gunShot(
                        new ObjectSpawn(shotX, shotY, 0, 0, facingLeft ? 0 : 1, false, 0), facingLeft, this, artHigh));
            }
        }
        // Child_Draw_Sprite runs even on the cooldown-underflow and failed-allocation paths.
        if (parent.isDestroyed()) {
            selectDelete();
            return;
        }
        drawSubmitted = true;
    }

    private void selectDelete() {
        phase = Phase.DELETE;
        parent = null; // The selected Delete_Current_Sprite will not read this raw link again.
    }

    @Override public boolean isHighPriority() { return artHigh; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return phase == Phase.INIT ? 0 : PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return phase == Phase.INIT ? 0 : 0x10; }
    @Override public int getOnScreenHalfHeight() { return phase == Phase.INIT ? 0 : 0x0C; }

    /** Child_DrawTouch_Sprite sees status7 as soon as Go_Delete_Sprite selects DELETE. */
    boolean hasNativeRetirementStatus() {
        return phase == Phase.DELETE || isDestroyed();
    }

    public int cooldownForTest() { return cooldown; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawSubmitted || phase == Phase.DELETE || isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_EGG_ROBO);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndexForcedPriority(mappingFrame, x, y, hFlip, vFlip, 0, artHigh);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(parentId, x, y, cooldown, hFlip, vFlip,
                        mappingFrame, phase, drawSubmitted, participatesInRomWorldTransitionOffset(), artHigh));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            x = extra.x();
            y = extra.y();
            cooldown = extra.cooldown();
            hFlip = extra.hFlip();
            vFlip = extra.vFlip();
            artHigh = extra.artHigh();
            mappingFrame = extra.mappingFrame();
            phase = extra.phase();
            drawSubmitted = extra.drawSubmitted();
            setRomWorldPositioned(extra.worldPositioned());
            parent = extra.parentId() == null ? null
                    : (EggRoboBadnikInstance) context.requireIdentityTable()
                    .resolveObject(extra.parentId(), true);
        }
    }
}
