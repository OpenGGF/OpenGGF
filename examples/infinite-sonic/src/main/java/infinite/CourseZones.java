package infinite;

import com.openggf.game.MusicReference;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneProgressionPlan;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.modzone.ModZoneRuntimeContribution;
import com.openggf.level.LevelDescriptor;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.ToIntFunction;

/**
 * Delegates the stock registry, except that course acts start Sonic on the generated
 * seam floor. The level manager reads its start descriptor after the level override
 * has built the terrain, so the start resolves lazily from the current library.
 */
final class CourseZones extends com.openggf.game.DelegatingZoneRegistry {
    private static final int START_X = 80; // GHZ1 stock start centre.
    private final int courseZones;
    private final ToIntFunction<Integer> courseStartY;

    /** {@code courseStartY} returns a centre Y for a loaded course level index, or -1. */
    CourseZones(ZoneRegistry base, int courseZones, ToIntFunction<Integer> courseStartY) {
        super(base);
        this.courseZones = courseZones;
        this.courseStartY = courseStartY;
    }

    ZoneRegistry base() { return source; }

    private record CourseStart(LevelDescriptor stock, ToIntFunction<Integer> courseY) implements LevelDescriptor {
        @Override public int levelIndex() { return stock.levelIndex(); }
        @Override public int startX() { return courseY.applyAsInt(stock.levelIndex()) < 0 ? stock.startX() : START_X; }
        @Override public int startY() {
            int y = courseY.applyAsInt(stock.levelIndex());
            return y < 0 ? stock.startY() : y;
        }
    }

    private List<LevelDescriptor> acts(int zone, List<LevelDescriptor> stock) {
        if (zone >= courseZones) return stock;
        return stock.stream().<LevelDescriptor>map(d -> new CourseStart(d, courseStartY)).toList();
    }

    @Override public List<LevelDescriptor> getLevelDataForZone(int zoneIndex) {
        return acts(zoneIndex, source.getLevelDataForZone(zoneIndex));
    }
    @Override public List<List<LevelDescriptor>> getAllZones() {
        List<List<LevelDescriptor>> zones = source.getAllZones();
        return java.util.stream.IntStream.range(0, zones.size()).mapToObj(z -> acts(z, zones.get(z))).toList();
    }
}
