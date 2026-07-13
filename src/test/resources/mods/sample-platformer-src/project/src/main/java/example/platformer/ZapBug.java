package example.platformer;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DestructionEffects.DestructionConfig;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * Patrolling badnik gimmick, registered with {@code art/zapbug.ggfs}. This shape is an
 * inert-body stub -- real patrol movement (via {@code PatrolMovementHelper}) and rendering
 * (via {@code getRenderer("sample-platformer:zapbug")}) arrive in a later task.
 */
public final class ZapBug extends AbstractBadnikInstance implements RewindRecreatable {
    public ZapBug(ObjectSpawn spawn) { super(spawn, "sample-platformer:zapbug"); }

    @Override protected void updateMovement(int frameCounter, PlayableEntity player) {
        // Inert body for now; patrol movement lands in a later task.
    }

    @Override protected int getCollisionSizeIndex() { return 0; }
    @Override protected DestructionConfig getDestructionConfig() {
        return new DestructionConfig(0, null, false, null, null, false);
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new ZapBug(context.spawn());
    }
}
