package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Native Obj_FBZEndBoss Robotnik ship child. */
public final class FbzEndBossShipChild extends AbstractFbzEndBossChild {
    public static final int COMBAT_FRAME = 0x0B;
    public static final int ESCAPE_FRAME = 5;
    public static final int NATIVE_CHILD_Y_OFFSET = 4;
    private int frame = COMBAT_FRAME;
    private int velocityY;
    private int velocityX;
    private int escapePhase;
    private int escapeTimer;
    private int fractionX;
    private int fractionY;
    private int priorityBucket = 5;
    private boolean highPriority;
    private boolean headSpawnAttempted;
    private boolean explosionControllerAttempted;

    FbzEndBossShipChild(FbzEndBossInstance boss) {
        super(boss, "ship", "FBZEndBossShip");
        x = boss.getX();
        y = boss.getY() + NATIVE_CHILD_Y_OFFSET;
    }
    public FbzEndBossShipChild(com.openggf.level.objects.ObjectSpawn spawn) {
        super(spawn, "ship", "FBZEndBossShip");
    }

    FbzRobotnikHeadChild spawnHead(com.openggf.game.PlayerCharacter character) {
        FbzRobotnikHeadChild child = spawnChild(() -> new FbzRobotnikHeadChild(boss, this, character));
        return child == null || child.getSlotIndex() < 0 ? null : child;
    }

    @Override public void update(int frameCounter, PlayableEntity player) {
        if (!headSpawnAttempted) {
            headSpawnAttempted = true;
            FbzRobotnikHeadChild child = spawnHead(boss.nativeCharacter());
            if (child != null) boss.attach(child);
            return;
        }
        if (boss.isDefeated() && !explosionControllerAttempted) {
            explosionControllerAttempted = true;
            FbzEndBossShipExplosionController controller = spawnChild(
                    () -> new FbzEndBossShipExplosionController(boss, this));
            if (controller != null && controller.getSlotIndex() >= 0) boss.attach(controller);
        }
        if (!boss.isDismantling()) {
            priorityBucket = boss.getPriorityBucket();
            highPriority = boss.isHighPriority();
            x = boss.getX();
            y = boss.getY() + NATIVE_CHILD_Y_OFFSET;
            return;
        }
        frame = ESCAPE_FRAME;
        switch (escapePhase) {
            case 0 -> { velocityY = -0x200; escapeTimer = 0x2F; escapePhase = 1; }
            case 1 -> {
                var moved = FbzEndBossInstance.move8_8(y, fractionY, velocityY);
                y = moved.position(); fractionY = moved.fraction(); velocityY += 0x38;
                if (escapeTimer-- <= 0) escapePhase = 2;
            }
            case 2 -> {
                if (services().camera() != null && services().camera().getY() + 0xC0 >= y) {
                    velocityX = 0x300; escapeTimer = 0x100; escapePhase = 3;
                    FbzEndBossShipFlameChild flame = spawnChild(() -> new FbzEndBossShipFlameChild(boss, this));
                    if (flame != null && flame.getSlotIndex() >= 0) boss.attach(flame);
                } else {
                    y--;
                }
            }
            case 3 -> {
                var moved = FbzEndBossInstance.move8_8(x, fractionX, velocityX);
                x = moved.position(); fractionX = moved.fraction();
                if (escapeTimer-- <= 0) com.openggf.level.objects.ObjectLifetimeOps.expireDynamic(this);
            }
            default -> { }
        }
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = services().renderManager().getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(frame, x, y, escapePhase >= 2, false);
    }

    boolean hasHorizontalEscapeVelocity() { return escapePhase == 3 && velocityX != 0; }
    boolean isHorizontallyFlipped() { return escapePhase >= 2; }
    @Override public int getPriorityBucket() { return priorityBucket; }
    @Override public boolean isHighPriority() { return highPriority; }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FbzEndBossShipChild(ctx.spawn());
    }
}
