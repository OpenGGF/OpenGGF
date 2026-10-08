package survivors;

import com.openggf.level.*;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.*;
import java.util.List;

/**
 * The stock act with its terrain, art and collision untouched, but no stock objects or rings:
 * its only object is the arena's {@link Stage} controller. Without the signpost, capsule,
 * boss triggers and level events the act cannot be finished in the usual way.
 */
final class ArenaLevel extends DelegatingLevel {
    private final Arena arena;

    ArenaLevel(Level source, Arena arena) {
        super(source);
        this.arena = arena;
    }

    @Override public List<ObjectSpawn> getObjects() {
        int y = arena.floorTop() - 64;
        return List.of(Stage.spawnAt(arena.centreX(), y));
    }
    @Override public List<RingSpawn> getRings() { return List.of(); }

}
