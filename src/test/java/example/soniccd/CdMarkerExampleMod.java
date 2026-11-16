package example.soniccd;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

import com.openggf.level.LevelPatch;
import com.openggf.level.objects.*;
import java.util.List;

/** Small compiled CD mod example: replace PP monitors and add a visible marker with disc-loaded art. */
public final class CdMarkerExampleMod implements GgfMod {
    private final int x, y;
    private final Runnable observedUpdate;
    public CdMarkerExampleMod() { this(128, 512, () -> {}); }
    public CdMarkerExampleMod(int x, int y, Runnable observedUpdate) {
        this.x = x; this.y = y; this.observedUpdate = observedUpdate;
    }
    @Override public void register(ModContext context) {
        context.registerObject("greeting", (spawn, registry) -> new Marker(spawn, observedUpdate));
        context.decodedLevelPatch("opening", LevelPatch.empty().forZone(0).nativePlacements()
                .select(spawn -> spawn.objectId() == 0x19 && spawn.subtype() < 8).bind("greeting")
                .addObject("greeting", x, y, 0, 0, true));
    }
    private static final class Marker extends AbstractObjectInstance implements ModRewindRecreatable {
        private final Runnable observedUpdate;
        Marker(ObjectSpawn spawn, Runnable observedUpdate) {
            super(spawn, "CD marker"); this.observedUpdate = observedUpdate;
        }
        @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) { observedUpdate.run(); }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) {
            var renderer = services().renderManager().getRenderer("cd.monitor");
            if (renderer != null) renderer.drawFrameIndex(0, getSpawn().x(), getSpawn().y());
        }
        @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
            return new Marker(getSpawn(), observedUpdate);
        }
    }
}
