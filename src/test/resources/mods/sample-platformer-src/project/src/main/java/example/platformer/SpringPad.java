package example.platformer;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * Spring gimmick, registered with {@code art/springpad.ggfs}. This shape is an inert-body
 * stub -- real bounce detection (via {@code SpringBounceHelper}) and rendering (via
 * {@code getRenderer("sample-platformer:springpad")}) arrive in a later task.
 */
public final class SpringPad extends AbstractObjectInstance implements RewindRecreatable {
    public SpringPad(ObjectSpawn spawn) { super(spawn, "sample-platformer:springpad"); }

    @Override public void update(int frameCounter, PlayableEntity player) {
        // Inert body for now; bounce detection lands in a later task.
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) { }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SpringPad(ctx.spawn());
    }
}
