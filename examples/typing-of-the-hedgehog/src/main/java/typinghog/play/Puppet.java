package typinghog.play;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ModRewindRecreatable;
import com.openggf.level.objects.MultiBucketRenderable;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectReconstructionContext;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import java.util.List;

/**
 * Draws one {@link Stagehand.Visual} with the zone's ROM art. It has no behaviour, no
 * collision and no native object slot (it is an auxiliary dynamic object), so its presence never
 * changes the deterministic rail run. It deletes itself when its visual is gone or its
 * generation is stale.
 *
 * <p>Puppets stay out of rewind snapshots ({@code addAuxiliaryDynamicObject}): the stagehand,
 * not the engine, owns what is drawn, and the course respawns every puppet after a checkpoint
 * restore, so snapshotting them only added recreation work and drew rewind object ids.
 */
public final class Puppet extends AbstractObjectInstance implements ModRewindRecreatable, MultiBucketRenderable {
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

    /** Spawns a puppet for {@code visual} through the non-rewindable auxiliary-object path. */
    static Puppet spawn(ObjectServices services, Stagehand.Visual visual, int generation) {
        return ObjectConstructionContext.construct(services, () -> {
            Puppet puppet = new Puppet(spawnAt(visual.x, visual.y), visual.id(), generation);
            if (!ObjectConstructionContext.isProbeConstruction()) {
                services.objectManager().addAuxiliaryDynamicObject(puppet);
            }
            return puppet;
        });
    }

    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new Puppet(context.spawn());
    }

    @Override public boolean isPersistent() { return !isDestroyed(); }

    @Override public void onUnload() { setDestroyed(true); }

    @Override public boolean isHighPriority() {
        Stagehand.Visual visual = visual();
        return visual != null && visual.highPriority;
    }

    private void draw(String key, int frame, int x, int y, boolean flipX, boolean flipY, int palette,
                      boolean ownerHigh, boolean priorityClass) {
        Stagehand hand = stagehand();
        if (hand != null && hand.sectionArt() != null
                && hand.sectionArt().draw(key, frame, x, y, flipX, flipY, palette,
                        ownerHigh, priorityClass, services().graphicsManager())) return;
        var renderer = getRenderer(key);
        if (renderer != null) SectionArt.draw(renderer, frame, x, y, flipX, flipY, palette,
                ownerHigh, priorityClass, services().graphicsManager());
    }

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

    @Override public int[] extraRenderBuckets() { return new int[0]; }

    @Override public boolean isHighPriority(int bucket) { return isHighPriority(); }

    private ObjectSpriteSheet sheet(String key) {
        Stagehand hand = stagehand();
        if (hand != null && hand.sectionArt() != null) {
            ObjectSpriteSheet sheet = hand.sectionArt().sheets().get(key);
            if (sheet != null) return sheet;
        }
        return services().renderManager().getSheet(key);
    }

    private int priorities(String key, int frame, boolean ownerHigh) {
        ObjectSpriteSheet sheet = sheet(key);
        if (sheet == null || frame < 0 || frame >= sheet.getFrameCount()) return 0;
        int classes = 0;
        for (var piece : sheet.getFrame(frame).pieces()) {
            classes |= ownerHigh || piece.priority() ? HIGH_PARTS : LOW_PARTS;
        }
        return classes;
    }

    @Override public int partTilePriorities(int bucket) {
        Stagehand.Visual visual = visual();
        if (visual == null || !visual.visible || (visual.blink & 2) != 0) return 0;
        if (visual.sprites.isEmpty()) return priorities(visual.artKey, visual.frame, visual.highPriority);
        int classes = 0;
        for (Stagehand.Sprite sprite : visual.sprites) if (sprite.visible) {
            classes |= priorities(sprite.artKey, sprite.frame, sprite.highPriority);
        }
        return classes;
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        appendRenderCommands(commands, getPriorityBucket());
    }

    @Override public void appendRenderCommands(List<GLCommand> commands, int bucket) {
        appendRenderCommands(commands, bucket, false);
        appendRenderCommands(commands, bucket, true);
    }

    @Override public void appendRenderCommands(List<GLCommand> commands, int bucket, boolean priorityClass) {
        Stagehand.Visual visual = visual();
        if (visual == null || !visual.visible || (visual.blink & 2) != 0) return;
        if (!visual.sprites.isEmpty()) {
            for (Stagehand.Sprite sprite : visual.sprites) {
                if (!sprite.visible) continue;
                draw(sprite.artKey, sprite.frame, sprite.x, sprite.y, sprite.flipX, sprite.flipY, sprite.palette,
                        sprite.highPriority, priorityClass);
            }
            return;
        }
        draw(visual.artKey, visual.frame, visual.x, visual.y, visual.flipX, visual.flipY, -1,
                visual.highPriority, priorityClass);
    }
}
