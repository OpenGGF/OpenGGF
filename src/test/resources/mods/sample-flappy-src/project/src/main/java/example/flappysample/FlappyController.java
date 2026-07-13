package example.flappysample;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * Minimal namespaced controller shell. Tasks 3-4 fill in the flap/gravity gate,
 * scroll-linked spawn cadence, and score/HUD state that drives the flappy-garden
 * zone's traversal loop.
 */
public final class FlappyController extends AbstractObjectInstance implements RewindRecreatable {
    public FlappyController(ObjectSpawn spawn) { super(spawn, "sample-flappy:controller"); }

    @Override public void update(int frameCounter, PlayableEntity player) {
        // Task 3/4 fills in the controller's state machine.
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        // Task 3/4 fills in HUD/overlay rendering.
    }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new FlappyController(context.spawn());
    }
}
