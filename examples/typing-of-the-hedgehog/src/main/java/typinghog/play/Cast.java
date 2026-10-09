package typinghog.play;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which badniks each zone stages, read from {@code cast/<game>.txt}. One badnik per line:
 *
 * <pre>
 * # zone  name        artKey      archetype  facesRight halfW halfH delay frames
 * 0       Motobug     motobug     RUSHER     false      20    14    8     0,1
 * </pre>
 *
 * Zones are the game's zone registry indices. Lines starting with {@code #} are comments.
 */
public final class Cast {
    private final Map<Integer, List<Species>> zones = new HashMap<>();

    public static Cast parse(String text) {
        Cast cast = new Cast();
        int number = 0;
        for (String raw : text.split("\n")) {
            number++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\s+");
            if (f.length != 9) throw new IllegalArgumentException("cast line " + number + " needs 9 fields: " + line);
            String[] frameText = f[8].split(",");
            int[] frames = new int[frameText.length];
            for (int i = 0; i < frames.length; i++) frames[i] = Integer.parseInt(frameText[i].strip());
            Species species = new Species(f[1].replace('_', ' '), f[2], frames, Integer.parseInt(f[7]),
                    Species.Archetype.valueOf(f[3]), Boolean.parseBoolean(f[4]), Integer.parseInt(f[5]),
                    Integer.parseInt(f[6]));
            cast.zones.computeIfAbsent(Integer.parseInt(f[0]), ignored -> new ArrayList<>()).add(species);
        }
        return cast;
    }

    /** The zone's badniks, or an empty list. */
    public List<Species> zone(int zone) {
        return List.copyOf(zones.getOrDefault(zone, List.of()));
    }
}
