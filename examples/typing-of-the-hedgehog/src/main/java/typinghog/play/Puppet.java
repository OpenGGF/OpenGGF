package typinghog.play;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ModRewindRecreatable;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectReconstructionContext;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import java.util.List;

/**
 * Draws one {@link Stagehand.Visual} with the zone's ROM art. It has no behaviour, no
 * collision and no native object slot (it is an auxiliary dynamic object), so its presence never
 * changes the deterministic rail run. It deletes itself when its visual is gone or its
 * generation is stale.
 */
public final class Puppet extends AbstractObjectInstance implements ModRewindRecreatable {
    static final String KEY = "puppet";

    private int visualId;
    private int generation;

    public Puppet(ObjectSpawn spawn) {
        super(spawn, "Typing puppet");
    }

    Puppet(ObjectSpawn spawn, int visualId, int generation) {
        this(spawn);
        this.visualId = visualId;
        this.generation = generation;
    }

    static ObjectSpawn spawnAt(int x, int y) {
        return new ObjectSpawn(x, y, 0, 0, 0, false, y, -1, TypingIds.MOD, TypingIds.MOD + ":" + KEY);
    }

    /** Spawns a puppet for {@code visual} through the auxiliary-object path. */
    static Puppet spawn(ObjectServices services, Stagehand.Visual visual, int generation) {
        return ObjectConstructionContext.construct(services, () -> {
            Puppet puppet = new Puppet(spawnAt(visual.x, visual.y), visual.id(), generation);
            if (!ObjectConstructionContext.isProbeConstruction()) {
                services.objectManager().addRewindableAuxiliaryDynamicObject(puppet);
            }
            return puppet;
        });
    }

    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new Puppet(context.spawn());
    }

    @Override public boolean isPersistent() { return !isDestroyed(); }

    @Override public boolean isHighPriority() { return true; }

    private Stagehand stagehand() {
        return services().gameService(Stagehand.class);
    }

    private Stagehand.Visual visual() {
        Stagehand stagehand = stagehand();
        return stagehand != null && stagehand.current(visualId, generation) ? stagehand.get(visualId) : null;
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        Stagehand.Visual visual = visual();
        if (visual == null) {
            setDestroyed(true);
            return;
        }
        // Track the drawn position so the engine's on-screen bookkeeping follows the visual.
        updateDynamicSpawn(visual.x & 0xFFFF, visual.y & 0xFFFF);
    }

    @Override public int getPriorityBucket() {
        Stagehand.Visual visual = visual();
        return RenderPriority.bucket(visual == null ? 4 : visual.bucket);
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        Stagehand.Visual visual = visual();
        if (visual == null || !visual.visible || (visual.blink & 2) != 0) return;
        var renderer = getRenderer(visual.artKey);
        if (renderer == null) return;
        renderer.drawFrameIndex(visual.frame, visual.x, visual.y, visual.flipX, visual.flipY);
    }
}
