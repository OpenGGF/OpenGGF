package remix;

import com.openggf.level.DelegatingLevel;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The target act with its foreground rebuilt from a {@link RemixRecipe}. Art, chunks, blocks,
 * collision, palettes and the background layer are the target's own (both Emerald Hill acts
 * share one block set); only foreground map cells, placements and the right camera bound change.
 * Authored {@link RemixRecipe.Placement placements} are added at their remixed coordinates.
 */
final class RemixedLevel extends DelegatingLevel {
    private static final int COLUMN = 128, SCREEN_HEIGHT = 224;

    private final Map map;
    private final List<ObjectSpawn> objects;
    private final List<RingSpawn> rings;
    private final int maxX;

    /** {@code sources} holds the decoded level for every act in {@link RemixRecipe#sources()}. */
    RemixedLevel(Level target, RemixRecipe recipe, java.util.Map<RemixRecipe.Act, Level> sources) {
        super(target);
        Map stock = target.getMap();
        map = new Map(stock.getLayerCount(), stock.getWidth(), stock.getHeight(), stock.getData());
        for (int y = 0; y < map.getHeight(); y++)
            for (int x = 0; x < map.getWidth(); x++) map.setValue(0, x, y, (byte) 0);

        // Rows under the target's kill plane (its lowest camera view) are never reachable here.
        int rows = Math.min(map.getHeight(), (target.getMaxY() + SCREEN_HEIGHT + COLUMN - 1) / COLUMN);
        List<ObjectSpawn> placedObjects = new ArrayList<>();
        List<RingSpawn> placedRings = new ArrayList<>();
        int column = 0, remappedMaxX = target.getMaxX();
        for (RemixRecipe.Strip strip : recipe.strips()) {
            Level source = sources.get(strip.source());
            Map from = source.getMap();
            for (int dx = 0; dx < strip.width(); dx++)
                for (int y = 0; y < rows; y++)
                    map.setValue(0, column + dx, y, from.getValue(0, strip.firstColumn() + dx, y));
            int left = strip.firstColumn() * COLUMN, right = (strip.lastColumn() + 1) * COLUMN;
            int shift = (column - strip.firstColumn()) * COLUMN;
            int bottom = rows * COLUMN;
            for (ObjectSpawn spawn : source.getObjects()) {
                if (spawn.x() < left || spawn.x() >= right || spawn.y() >= bottom || recipe.drops(strip.source(), spawn.objectId())) continue;
                placedObjects.add(spawn.withPosition(spawn.x() + shift, spawn.y()));
            }
            for (RingSpawn ring : source.getRings()) {
                if (ring.x() >= left && ring.x() < right && ring.y() < bottom) placedRings.add(new RingSpawn(ring.x() + shift, ring.y()));
            }
            // The camera's right bound moves with the strip that holds the target's own bound.
            if (strip.source().equals(recipe.target()) && target.getMaxX() >= left && target.getMaxX() < right)
                remappedMaxX = target.getMaxX() + shift;
            column += strip.width();
        }
        for (RemixRecipe.Placement extra : recipe.placements())
            placedObjects.add(new ObjectSpawn(extra.x(), extra.y(), extra.objectId(), extra.subtype(), extra.flags(),
                    false, extra.y() | extra.flags() << 13));
        // The object window walks placements in x order; renumber so every slot has one identity.
        placedObjects.sort(Comparator.comparingInt(ObjectSpawn::x));
        List<ObjectSpawn> numbered = new ArrayList<>(placedObjects.size());
        for (int i = 0; i < placedObjects.size(); i++) {
            ObjectSpawn s = placedObjects.get(i);
            numbered.add(new ObjectSpawn(s.x(), s.y(), s.objectId(), s.subtype(), s.renderFlags(),
                    s.respawnTracked(), s.rawYWord(), i));
        }
        placedRings.sort(Comparator.comparingInt(RingSpawn::x));
        List<RingSpawn> ringIds = new ArrayList<>(placedRings.size());
        for (int i = 0; i < placedRings.size(); i++) ringIds.add(new RingSpawn(placedRings.get(i).x(), placedRings.get(i).y(), i));
        objects = List.copyOf(numbered);
        rings = List.copyOf(ringIds);
        maxX = remappedMaxX;
    }

    @Override public Map getMap() { return map; }
    @Override public List<ObjectSpawn> getObjects() { return objects; }
    @Override public List<RingSpawn> getRings() { return rings; }
    @Override public int getMaxX() { return maxX; }
}
