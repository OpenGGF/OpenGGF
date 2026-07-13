package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** The five flickering fragments from {@code ChildObjDat_86B7A}. */
final class FbzMinibossFragmentChild extends AbstractObjectInstance implements RewindRecreatable {
    private static final int[] X_OFFSETS = {0, -0x10, 0x10, -0x18, 0x18};
    private static final int[][] VELOCITIES = {
            {0x100, -0x100},
            {-0x200, -0x200},
            {0x200, -0x200},
            {-0x300, -0x200},
            {0x300, -0x200}
    };

    @RewindTransient(reason = "structural root link restored from stable family slot")
    private FbzMinibossInstance boss;
    private int familySlot;
    private int role;
    private int x;
    private int y;
    private int xFixed;
    private int yFixed;
    private int xVelocity;
    private int yVelocity;
    private int mappingFrame;
    private boolean flickerBit;
    private boolean visibleThisUpdate = true;

    FbzMinibossFragmentChild(FbzMinibossInstance boss, int role) {
        super(new ObjectSpawn(boss.getX() + X_OFFSETS[role], boss.getY() - 8,
                0xAA, role * 2, 0, false, 0), "FBZMinibossFragment");
        this.boss = boss;
        this.familySlot = boss.getSlotIndex();
        this.role = role;
        this.x = getSpawn().x();
        this.y = getSpawn().y();
        this.xFixed = x << 16;
        this.yFixed = y << 16;
        this.xVelocity = VELOCITIES[role][0];
        this.yVelocity = VELOCITIES[role][1];
        // The level-art registry filters source frames 2,3,A,4,B into compact sheet frames 0..4.
        this.mappingFrame = role;
    }

    private FbzMinibossFragmentChild(ObjectSpawn spawn) {
        super(spawn, "FBZMinibossFragment");
        this.x = spawn.x();
        this.y = spawn.y();
        this.xFixed = x << 16;
        this.yFixed = y << 16;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        // Obj_FlickerMove calls MoveSprite, not a gravity helper.
        xFixed += xVelocity << 8;
        yFixed += yVelocity << 8;
        x = xFixed >> 16;
        y = yFixed >> 16;

        Camera camera = services().camera();
        boolean outsideY = camera != null
                && (((y - camera.getY() + 0x80) & 0xFFFF) > 0x200);
        if (!isInRangeAt(x) || outsideY) {
            visibleThisUpdate = false;
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }

        flickerBit = !flickerBit;
        visibleThisUpdate = !flickerBit;
    }

    @Override
    protected boolean skipsSameFrameUpdateAfterSpawn() {
        return true;
    }

    @Override
    public FbzMinibossFragmentChild recreateForRewind(RewindRecreateContext ctx) {
        return new FbzMinibossFragmentChild(ctx.spawn());
    }

    @Override protected void afterRewindRestoreSettled() {
        if (tryServices() != null) FbzMinibossRewindLinks.settle(services().objectManager(), familySlot);
    }

    int familySlot() { return familySlot; }
    void setBoss(FbzMinibossInstance boss) { this.boss = boss; }

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
        return 0x0C;
    }

    @Override
    public int getPriorityBucket() {
        return 3;
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visibleThisUpdate || isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ1_MINIBOSS_FRAGMENTS);
        if (renderer != null) {
            renderer.drawFrameIndex(mappingFrame, x, y, false, false);
        }
    }
}
