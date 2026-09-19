package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** {@code loc_584A2}: one delayed 8x8 piece emitted by {@link SszLaunchBackgroundDebrisController}. */
public final class SszLaunchBackgroundDebrisPiece extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private int descriptor;
    private int delay;
    private int sentinelFrame;

    public SszLaunchBackgroundDebrisPiece(ObjectSpawn spawn) {
        super(spawn, "SSZLaunchBackgroundDebrisPiece");
        descriptor = spawn.rawYWord() & 0xFFFF;
        delay = spawn.subtype() & 0xFF;
        if (descriptor == 0xFFFE) { descriptor = 0; sentinelFrame = 1; }
        else if (descriptor == 0xFFFD) { descriptor = 0; sentinelFrame = 2; }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (delay > 0) { delay--; return; }
        // loc_584A2 ends in out_of_range.w: the ROM's coarse X-only test, not a
        // full viewport predicate. Early rows are deliberately born below the screen.
        coarseXCullViewport(getX());
    }

    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x180); }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 8; }
    public int delayForTest() { return delay; }
    public int descriptorForTest() { return descriptor; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_LAUNCH_STRUCTURE);
        if (renderer == null || !renderer.isReady()) return;
        if (sentinelFrame != 0) {
            renderer.drawFrameIndex(sentinelFrame, getX(), getY(), false, false, 2);
            return;
        }
        int pattern = descriptor & 0x7FF;
        PatternDesc desc = new PatternDesc((descriptor & 0xF800)
                | ((renderer.getPatternBase() + pattern) & 0x7FF));
        services().graphicsManager().renderPatternWithId(renderer.getPatternBase() + pattern,
                desc, getX() - 4, getY() - 4);
    }
}
