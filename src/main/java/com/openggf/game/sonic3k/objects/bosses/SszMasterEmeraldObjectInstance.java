package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * {@code loc_7C818}: the Master Emerald watching Knuckles' Sky Sanctuary final fight.
 *
 * <p>The ROM allocates it during {@code loc_7BA38}, at {@code Camera_max_X+$100} and
 * {@code Camera_Y+$A8}. Mapping frame 0/1 follows {@code _unkFAB8} bit 6 while the Super
 * transformation toggles it. The all-Super-Emerald branch only adds a palette cycle; the
 * geometry and lifecycle are the same.</p>
 */
public final class SszMasterEmeraldObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x300);
    private int x;
    private int y;

    private record RewindExtra(int x, int y)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszMasterEmeraldObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZMasterEmerald");
        x = spawn.x();
        y = spawn.y();
    }

    public static SszMasterEmeraldObjectInstance atArenaCamera(ObjectSpawn ignored) {
        // Position is finalized on the first object update, after loc_7BA38 expanded max X.
        return new SszMasterEmeraldObjectInstance(
                new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
    }

    @Override
    public SszMasterEmeraldObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new SszMasterEmeraldObjectInstance(context.spawn());
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(x, y));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            x = extra.x();
            y = extra.y();
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (x == 0 && y == 0) {
            x = ((services().camera().getMaxX() & 0xFFFF) + 0x100) & 0xFFFF;
            y = ((services().camera().getY() & 0xFFFF) + 0xA8) & 0xFFFF;
        }
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x20; }
    @Override public int getOnScreenHalfHeight() { return 0x18; }
    @Override public boolean isPersistent() { return true; }

    public int mappingFrameForTest() {
        return S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry())
                .map(state -> state.cutsceneFlag(6) ? 1 : 0).orElse(0);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_MASTER_EMERALD);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrameForTest(), x, y, false, false, -1);
        }
    }
}
