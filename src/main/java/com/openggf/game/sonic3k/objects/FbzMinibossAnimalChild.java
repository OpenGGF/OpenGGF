package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.AnimalType;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomWorldPositionedObject;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

import java.util.List;

/** The five freed-animal roles from {@code ChildObjDat_89ED0}. */
final class FbzMinibossAnimalChild extends AbstractObjectInstance
        implements RewindRecreatable, RomWorldPositionedObject {
    private static final int[] X_OFFSETS = {0, 0x10, -0x10, 0x1C, -0x1C};
    private static final int[] BOUNCE_Y_VELOCITIES = {-0x380, -0x300, -0x280, -0x200, -0x380};
    private static final int FRAMES_PER_MAPPING = 3;
    private static final int ART_VARIANT_COUNT = 2;

    private FbzMinibossInstance boss;
    private int familySlot;
    private int role;
    private int x;
    private int y;
    private int xFixed;
    private int yFixed;
    private int xVelocity;
    private int yVelocity;
    private int bounceYVelocity;
    private int waitDelay;
    private int mappingFrame;
    private int priorityBucket = 5;
    private boolean active;

    FbzMinibossAnimalChild(FbzMinibossInstance boss, int role) {
        super(new ObjectSpawn(boss.getX() + X_OFFSETS[role], boss.getY() - 4,
                0xAA, role * 2, 0, false, 0), "FBZMinibossAnimal");
        this.boss = boss;
        this.familySlot = boss.getSlotIndex();
        this.role = role;
        this.x = getSpawn().x();
        this.y = getSpawn().y();
        this.xFixed = x << 16;
        this.yFixed = y << 16;
        this.waitDelay = role << 3;
        this.bounceYVelocity = BOUNCE_Y_VELOCITIES[role];
        this.yVelocity = bounceYVelocity;
        this.xVelocity = X_OFFSETS[role] < 0 ? -0x200 : 0x200;
    }

    private FbzMinibossAnimalChild(ObjectSpawn spawn) {
        super(spawn, "FBZMinibossAnimal");
        this.x = spawn.x();
        this.y = spawn.y();
        this.xFixed = x << 16;
        this.yFixed = y << 16;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!active) {
            waitDelay = (short) (waitDelay - 1);
            if (waitDelay >= 0) {
                cullByX();
                return;
            }
            active = true;
            priorityBucket = 1;
            cullByX();
            return;
        }

        // MoveSprite_LightGravity uses 8.8 velocity over a 16.16 position.
        xFixed += xVelocity << 8;
        yFixed += yVelocity << 8;
        yVelocity += 0x20;
        x = xFixed >> 16;
        y = yFixed >> 16;

        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(x, y, 8);
        if (floor.distance() < 0) {
            y += floor.distance();
            yFixed += floor.distance() << 16;
            yVelocity = bounceYVelocity;
        }

        mappingFrame = (vIntRunCount & 8) == 0 ? 1 : 0;
        cullByX();
    }

    private void cullByX() {
        if (!isInRangeAt(x)) {
            ObjectLifetimeOps.expireDynamic(this);
        }
    }

    @Override
    protected boolean skipsSameFrameUpdateAfterSpawn() {
        return true;
    }

    @Override
    public FbzMinibossAnimalChild recreateForRewind(RewindRecreateContext ctx) {
        return new FbzMinibossAnimalChild(ctx.spawn());
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
    public void offsetNativePositionWordsPreserveSubpixel(int offsetX, int offsetY) {
        x = (x + offsetX) & 0xFFFF;
        y = (y + offsetY) & 0xFFFF;
        xFixed = (x << 16) | (xFixed & 0xFFFF);
        yFixed = (y << 16) | (yFixed & 0xFFFF);
    }

    @Override
    public int getOnScreenHalfWidth() {
        return 8;
    }

    @Override
    public int getPriorityBucket() {
        return priorityBucket;
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getAnimalRenderer();
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        int artVariant = role & 1;
        int animalIndex = artVariant == 0
                ? renderManager.getAnimalTypeA()
                : renderManager.getAnimalTypeB();
        int mappingSet = AnimalType.fromIndex(animalIndex).mappingSet().ordinal();
        int frameIndex = ((mappingSet * ART_VARIANT_COUNT) + artVariant)
                * FRAMES_PER_MAPPING + mappingFrame;
        renderer.drawFrameIndex(frameIndex, x, y, xVelocity < 0, false);
    }
}
