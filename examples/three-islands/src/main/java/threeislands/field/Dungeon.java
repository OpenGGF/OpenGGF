package threeislands.field;

import java.util.List;
import threeislands.core.EnemyKind;
import threeislands.core.Zone;

/** Authored interiors for the story landmarks; kit indices are the public ROM level-kit order. */
public record Dungeon(Zone zone, String label, int artZone, int artAct, int music, String goal, AreaLayout layout) {
    private Dungeon(Zone zone, String label, int artZone, int artAct, int music, String goal) {
        this(zone, label, artZone, artAct, music, goal, AreaLayout.inside(zone));
    }
    public static Dungeon of(Zone zone) {
        return switch (zone) {
            case GREEN_HILL -> new Dungeon(zone, "Seaside Shrine", 1, 0, 0x82, "Missing Flicky");
            case STAR_LIGHT -> new Dungeon(zone, "Observatory Vault", 4, 0, 0x84, "Observatory log");
            case SPRING_YARD -> new Dungeon(zone, "Freight Catacombs", 1, 0, 0x82, "Freight records");
            case EMERALD_HILL -> new Dungeon(zone, "Workshop Caverns", 5, 0, 0x84, "Compass notes");
            case CHEMICAL_PLANT -> new Dungeon(zone, "Pump Station", 1, 0, 0x8C, "Valve chart");
            case MYSTIC_CAVE -> new Dungeon(zone, "Lantern Shrine", 5, 1, 0x84, "Mine ledger");
            case ANGEL_ISLAND -> new Dungeon(zone, "Guardian Shrine", 9, 1, 0x15, "Guardian memorial");
            case HYDROCITY -> new Dungeon(zone, "Tidal Sanctuary", 1, 1, 0x04, "Ancient mural");
            case LAUNCH_BASE -> new Dungeon(zone, "Mooring Vault", 9, 0, 0x14, "Flight records");
            case DEATH_EGG -> new Dungeon(zone, "Sky Archive", 11, 1, 0x17, "Convergence schedule");
        };
    }

    public boolean floor(double x, double y) { return layout.floor(x, y); }

    public List<EnemyKind> guards(int index) {
        List<EnemyKind> kinds = zone.enemyKinds();
        // The opening shrine is survivable by a level-one Sonic; later rooms use pairs.
        return zone == Zone.GREEN_HILL ? List.of(index == 0 ? EnemyKind.CRABMEAT : EnemyKind.NEWTRON)
                : List.of(kinds.get(index % kinds.size()), kinds.get((index + 1) % kinds.size()));
    }

    public String completionKey() { return zone.key + "-dungeon-complete"; }
}
