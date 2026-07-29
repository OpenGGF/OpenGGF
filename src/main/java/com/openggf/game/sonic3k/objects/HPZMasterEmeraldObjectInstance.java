package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** ROM {@code Obj_HPZMasterEmerald} (SKL object $B0). */
public final class HPZMasterEmeraldObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int X = 0x1640;
    private static final int Y = 0x340;

    public HPZMasterEmeraldObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HPZMasterEmerald");
    }

    @Override
    public HPZMasterEmeraldObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HPZMasterEmeraldObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
    }

    @Override public int getX() { return X; }
    @Override public int getY() { return Y; }
    @Override public int getOutOfRangeReferenceX() { return X; }
    @Override public int getPriorityBucket() { return 4; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_MASTER_EMERALD);
        if (renderer != null) {
            renderer.drawFrameIndex(0, X, Y, false, false, 0);
        }
    }
}
