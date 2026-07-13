package example.flappysample;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * Minimal namespaced pipe obstacle shell. Tasks 3-4 fill in the per-subtype gap
 * geometry, touch/collision response, and the baked pipe art rendering.
 */
public final class FlappyPipe extends AbstractObjectInstance implements RewindRecreatable {
    public FlappyPipe(ObjectSpawn spawn) { super(spawn, "sample-flappy:pipe"); }

    @Override public void update(int frameCounter, PlayableEntity player) {
        // Task 3/4 fills in pipe collision/gap behavior.
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        // Task 3/4 fills in pipe art rendering.
    }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new FlappyPipe(context.spawn());
    }
}
