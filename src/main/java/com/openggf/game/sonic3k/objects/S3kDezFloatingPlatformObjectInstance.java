package com.openggf.game.sonic3k.objects;

import com.openggf.game.OscillationManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** SKL {@code $4A}, {@code Obj_DEZFloatingPlatform} (sonic3k.asm:51229-51277). */
public final class S3kDezFloatingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private int x;
    private int y;
    private int baseX;
    private int baseY;
    private int mappingFrame;
    private int sweepVelocity;
    private int sweepPosition;
    private boolean sweepReverse;

    public S3kDezFloatingPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZFloatingPlatform");
        x = baseX = spawn.x();
        y = baseY = spawn.y();
    }

    @Override public void update(int vIntRunCount, PlayableEntity playerEntity) {
        int type = spawn.subtype() & 0x0F;
        switch (type) {
            case 1 -> x = baseX + flipped(OscillationManager.getByte(0x08), 0x20);
            case 2 -> x = baseX + flipped(OscillationManager.getByte(0x1C), 0x40);
            case 3 -> updateSweep();
            case 4 -> y = baseY + flipped(OscillationManager.getByte(0x08), 0x20);
            default -> { x = baseX; y = baseY; }
        }
        mappingFrame ^= 1;
    }

    private int flipped(int value, int centre) {
        int displacement = value - centre;
        return (spawn.renderFlags() & 1) == 0 ? displacement : -displacement;
    }

    private void updateSweep() {
        sweepVelocity = (short) (sweepVelocity + (sweepReverse ? -4 : 4));
        sweepPosition = (short) (sweepPosition + sweepVelocity);
        int high = (sweepPosition >> 8) & 0xFF;
        if (!sweepReverse && high >= 0x5F) sweepReverse = true;
        else if (sweepReverse && high < 0x5F) sweepReverse = false;
        x = baseX + flipped(high, 0x60);
    }

    @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(0x2B, 0x10, 0x11); }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getOutOfRangeReferenceX() { return baseX; }
    @Override public int getOnScreenHalfWidth() { return 0x20; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x200); }
    @Override public int romObjectCodePointerHighWord() { return 2; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_FLOATING_PLATFORM);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, x, y, false, false);
    }
    int mappingFrameForTest() { return mappingFrame; }
}
