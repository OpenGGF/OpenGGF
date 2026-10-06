package slaytherobotnik.core;

import java.util.List;

/**
 * One act of the run: a zone and its presentation. {@code zone} and {@code zoneAct} are the
 * ROM zone/act indices whose background is shown behind the map and fights.
 * {@code fixedRooms}, when not null, replaces the generated map with a single path of those
 * rooms (the final act, like Slay the Spire's act 4).
 */
public record ActDef(
        int number,
        String name,
        String zoneName,
        int zone,
        int zoneAct,
        int mapMusic,
        int bossMusic,
        int eliteMusic,
        String tagline,
        List<String> fixedRooms) {

    public boolean fixedLayout() {
        return fixedRooms != null;
    }
}
