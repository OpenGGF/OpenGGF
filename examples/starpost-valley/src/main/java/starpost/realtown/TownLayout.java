package starpost.realtown;

import java.util.ArrayList;
import java.util.List;
import starpost.people.Anchors;
import starpost.valley.Runner;
import starpost.valley.Valley;

/** Placement seam: the town knows destinations, not how Green Hill is encoded. */
public final class TownLayout {
    public final Runner.Ground ground;
    public final int springX;
    public final int loopX;
    public final List<Valley.Place> doors;

    public TownLayout(Runner.Ground ground, int springX, int loopX, List<Valley.Place> doors) {
        this.ground = ground; this.springX = springX; this.loopX = loopX;
        this.doors = List.copyOf(doors);
    }

    public static TownLayout from(Valley valley) {
        return new TownLayout(valley, valley.springX, valley.loopX, valley.places);
    }

    public TownLayout withGround(Runner.Ground next) {
        return new TownLayout(next, springX, loopX, doors);
    }

    public int anchor(String id) {
        for (Valley.Place door : doors) if (door.id().equals(id)) return door.x();
        int x = Anchors.valleyX(id);
        if (x < 0) throw new IllegalArgumentException("Unknown town anchor: " + id);
        return x;
    }

    /** Flat, deliberately undecorated act seam for rules and engine launch tests. */
    public static TownLayout placeholder() {
        List<Valley.Place> doors = new ArrayList<>();
        String[] ids = {"farm_gate", "seed_stall", "inn", "workshop", "museum", "robomart", "ruins", "capsule", "lake", "board"};
        String[] labels = {"TO THE FARM", "DANDEL'S SEEDS", "LAMPPOST INN", "TAILS' WORKSHOP", "WORKSHOP MUSEUM", "ROBOMART", "MARBLE RUINS", "THE GREAT CAPSULE", "WATERFALL LAKE", "SIGNPOST BOARD"};
        for (int i = 0; i < ids.length; i++) {
            int x = ids[i].equals("museum") ? 1283 : ids[i].equals("board") ? 794 : Anchors.valleyX(ids[i]);
            doors.add(new Valley.Place(ids[i], x, 20, labels[i]));
        }
        return new TownLayout(new Runner.Ground() {
            public boolean solid(int x, int y) { return x >= 0 && x < right() && y >= 192; }
            public int floorBelow(int x, int fromY) { return x >= 0 && x < right() ? 192 : 512; }
            public int left() { return 0; }
            public int right() { return 13 * 256; }
        }, 7 * 256 + 20, 9 * 256, doors);
    }
}
