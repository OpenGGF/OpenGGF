package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SpawnConstructionContextRewindRecreatable;
import com.openggf.graphics.GLCommand;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Detached boss fragment used by native ChildObjDat_70F0A/70F24 tables. */
public final class FbzEndBossDebrisChild extends AbstractObjectInstance
        implements SpawnConstructionContextRewindRecreatable {
    public record Spec(int dx, int dy, int velocityX, int velocityY, int frame) { }
    private static final List<Spec> ARM = List.of(
            new Spec(-8, -0x10, -0x200, -0x200, 0xC),
            new Spec(8, -0x10, 0x200, -0x200, 0xD),
            new Spec(-8, 0x10, -0x300, -0x200, 0xE),
            new Spec(8, 0x10, 0x300, -0x200, 0xF));
    private int x;
    private int y;
    private int velocityX;
    private int velocityY;
    private int frame;
    private int xFixed;
    private int yFixed;
    private boolean flipX;
    private boolean initialized;
    private boolean flickerBit;
    private boolean drawThisFrame;
    public FbzEndBossDebrisChild(ObjectSpawn spawn) {
        super(spawn, "FBZEndBossDebris");
        x = spawn.x(); y = spawn.y(); frame = spawn.subtype() & 0xF;
        int[][] velocities = {{-0x200,-0x200},{0x200,-0x200},{0,-0x100},{-0x40,-0x700}};
        int[] selected = velocities[frame & 3];
        velocityX = selected[0]; velocityY = selected[1];
        xFixed = x << 16; yFixed = y << 16;
    }

    FbzEndBossDebrisChild(ObjectSpawn spawn, int velocityX, int velocityY) {
        super(spawn, "FBZEndBossDebris");
        x = spawn.x();
        y = spawn.y();
        frame = spawn.subtype() & 0xF;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        xFixed = x << 16; yFixed = y << 16;
    }
    FbzEndBossDebrisChild(ObjectSpawn spawn, boolean flipX) {
        this(spawn);
        this.flipX = flipX;
        if (flipX) velocityX = -velocityX;
    }
    public static List<Spec> armDebrisTable() { return ARM; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public void update(int frameCounter, PlayableEntity player) {
        if (!initialized) { initialized = true; return; }
        xFixed += velocityX << 8;
        yFixed += velocityY << 8;
        x = xFixed >> 16;
        y = yFixed >> 16;
        velocityY += 0x38;
        if (tryServices() != null && services().camera() != null) {
            int cameraBack = (services().camera().getX() - 0x80) & 0xFF80;
            int coarseDelta = ((x & 0xFF80) - cameraBack) & 0xFFFF;
            int yDelta = (y - services().camera().getY() + 0x80) & 0xFFFF;
            if (coarseDelta > 0x280 || yDelta > 0x200) {
                com.openggf.level.objects.ObjectLifetimeOps.expireDynamic(this);
                return;
            }
        }
        drawThisFrame = flickerBit;
        flickerBit = !flickerBit;
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawThisFrame || isDestroyed()) return;
        PatternSpriteRenderer renderer = services().renderManager().getRenderer(Sonic3kObjectArtKeys.FBZ_END_BOSS);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(frame, x, y, flipX, false);
    }
    @Override public int getPriorityBucket() { return 1; }
    @Override public boolean isHighPriority() { return true; }
    @Override public boolean isPersistent() { return true; }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) { return new FbzEndBossDebrisChild(ctx.spawn()); }
}
