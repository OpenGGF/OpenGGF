package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** {@code loc_581F2..loc_583A6}: one visible piece of SSZ1's spiral-ramp launch structure. */
public final class SszLaunchStructureObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int GRAVITY = 0x38;
    private int xFixed;
    private int yFixed;
    private int yVelocity;
    private int mappingFrame;
    private int priorityWord;
    private int splitStage;
    private boolean falling;

    public SszLaunchStructureObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZLaunchStructure");
        xFixed = spawn.x() << 16;
        yFixed = spawn.y() << 16;
        mappingFrame = spawn.subtype() & 0xFF;
        priorityWord = spawn.renderFlags() & 0xFFFF;
    }

    public static SszLaunchStructureObjectInstance piece(int x, int y, int frame, int priority) {
        return new SszLaunchStructureObjectInstance(
                new ObjectSpawn(x, y, 0, frame, priority, false, 0));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (falling) {
            yFixed += yVelocity << 8;
            yVelocity += GRAVITY;
            if (!isOnScreen()) ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (player == null) return;
        int threshold = switch (mappingFrame) {
            case 6, 0xB -> 0x1C;
            case 7, 0xC, 9, 0xA -> 0x24;
            default -> 0x2C;
        };
        if ((player.getCentreY() & 0xFFFF) <= ((getY() - threshold) & 0xFFFF)) return;
        if ((mappingFrame == 6 || mappingFrame == 7) && splitStage < 2) {
            int offsetX = mappingFrame == 6 ? -0x40 : -0x20;
            int offsetY = mappingFrame == 6 ? 0x10 : 8;
            spawnChild(() -> piece(getX() + offsetX, getY() + offsetY, 8, priorityWord));
            mappingFrame++;
            splitStage++;
        } else if ((mappingFrame == 0xB || mappingFrame == 0xC) && splitStage < 2) {
            int offsetX = mappingFrame == 0xB ? 0x40 : 0x20;
            int offsetY = mappingFrame == 0xB ? 0x10 : 8;
            spawnChild(() -> piece(getX() + offsetX, getY() + offsetY, 0xD, priorityWord));
            mappingFrame++;
            splitStage++;
        } else {
            falling = true;
        }
    }

    @Override public int getX() { return (xFixed >> 16) & 0xFFFF; }
    @Override public int getY() { return (yFixed >> 16) & 0xFFFF; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(priorityWord); }
    @Override public int getOnScreenHalfWidth() { return 0x40; }
    @Override public int getOnScreenHalfHeight() { return 0x1C; }
    public int mappingFrameForTest() { return mappingFrame; }
    public boolean fallingForTest() { return falling; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_LAUNCH_STRUCTURE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false, 2);
        }
    }
}
