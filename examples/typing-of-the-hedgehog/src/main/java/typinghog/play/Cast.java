package typinghog.play;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Who each zone stages: its badniks, read from {@code cast/<game>.txt}, and its boss's
 * {@link BossRig}, read from {@code bosses/<game>.txt}. One badnik per line:
 *
 * <pre>
 * # zone  name        artKey      archetype  facesRight halfW halfH delay frames
 * 0       Motobug     motobug     RUSHER     false      20    14    8     0,1
 * </pre>
 *
 * Zones are the game's zone registry indices; {@code 1@0} limits a badnik to one act (index
 * from 0) of its zone, for art the other act never loads. Lines starting with {@code #} are
 * comments.
 */
public final class Cast {
    private record Entry(int act, Species species) { }

    private final Map<Integer, List<Entry>> zones = new HashMap<>();
    private Map<String, BossRig> bosses = Map.of();

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
            int at = f[0].indexOf('@');
            int zone = Integer.parseInt(at < 0 ? f[0] : f[0].substring(0, at));
            int act = at < 0 ? -1 : Integer.parseInt(f[0].substring(at + 1));
            cast.zones.computeIfAbsent(zone, ignored -> new ArrayList<>()).add(new Entry(act, species));
        }
        return cast;
    }

    /** This cast with {@code rigs} (from {@link BossRig#parse}) as its bosses. */
    public Cast withBosses(Map<String, BossRig> rigs) {
        bosses = Map.copyOf(rigs);
        return this;
    }

    /** The act's boss rig, else the zone's, else the game's {@code *} rig, or null. */
    public BossRig boss(int zone, int act) {
        BossRig rig = bosses.get(zone + "@" + act);
        if (rig == null) rig = bosses.get(Integer.toString(zone));
        return rig != null ? rig : bosses.get("*");
    }

    /** The act's badniks, or an empty list. */
    public List<Species> zone(int zone, int act) {
        List<Species> out = new ArrayList<>();
        for (Entry entry : zones.getOrDefault(zone, List.of())) {
            if (entry.act() < 0 || entry.act() == act) out.add(entry.species());
        }
        return List.copyOf(out);
    }
}
