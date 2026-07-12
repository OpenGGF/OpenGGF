package example.phase2sample;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.DestructionEffects.DestructionConfig;
import com.openggf.level.objects.PatrolMovementHelper;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;

/** Minimal namespaced badnik shell. Replace its patrol with game-accurate behavior. */
public final class SampleBadnik extends AbstractBadnikInstance implements RewindRecreatable {
    private int xSub;
    public SampleBadnik(ObjectSpawn spawn) { super(spawn, "phase2-sample:sample-badnik"); }

    @Override protected void updateMovement(int frameCounter, PlayableEntity player) {
        int direction = ((frameCounter / 120) & 1) == 0 ? 1 : -1;
        PatrolMovementHelper.PatrolResult patrol = PatrolMovementHelper.applyVelocity(
                currentX, xSub, direction * 0x100);
        currentX = patrol.newX();
        xSub = patrol.newXSub();
    }

    @Override protected int getCollisionSizeIndex() { return 1; }

    @Override protected DestructionConfig getDestructionConfig() {
        return new DestructionConfig(0, null, false, null, null, false);
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        PatternSpriteRenderer renderer = getRenderer("phase2-sample:sample-badnik");
        if (renderer != null) renderer.drawFrameIndex(0, currentX, currentY, facingLeft, false);
    }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new SampleBadnik(context.spawn());
    }
}
