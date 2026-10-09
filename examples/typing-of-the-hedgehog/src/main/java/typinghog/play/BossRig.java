package typinghog.play;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How a zone's boss looks: its ROM sprite parts, drawn back to front around Robotnik's position,
 * each with a frame cycle per {@link Mood}. Read from {@code bosses/<game>.txt}, one part per line:
 *
 * <pre>
 * # zone part  artKey     dx dy  animations and options
 * 0      ship  eggman     0  0   *=15:0
 * 0      face  eggman     0  0   idle=5:1,2 hit=31:5 laugh=4:3,4 defeat=15:7 flee=3:6,1
 * 0      ball  boss_ball  0  32  *=2:0,1 swing=96,50,128 links=swing_ghz:1:0,16,32,48,64
 * </pre>
 *
 * <p>Offsets are pixels for a boss facing left (towards Sonic); a boss facing right mirrors
 * them. An animation is {@code mood=delay:frames} or {@code mood=-} (hidden); see
 * {@link Part#anim} for how unnamed moods fall back. Options:
 * {@code back} draws the part behind the rest of the boss's sprites; {@code flip} mirrors the
 * part itself; {@code swing=length,degrees,periodRows} hangs it on a pendulum from its offset,
 * and {@code links=artKey:frame:distances} draws chain links along the rope;
 * {@code orbit=radius,periodRows,count} circles {@code count} copies round its offset. Zone
 * {@code *} is the rig for zones without their own.
 *
 * <p>A line whose part is {@code hover} draws nothing and sets the boss's stance instead:
 * {@code 0 hover - 6 72} bobs him 6 pixels while he holds 72 pixels above Sonic, and the
 * {@code grounded} option keeps a boss on foot from rising as he flees.
 */
public final class BossRig {
    /** What Robotnik is doing, for his face and the parts that react. */
    public enum Mood { IDLE, OPEN, LUNGE, HIT, LAUGH, DEFEAT, FLEE }

    record Anim(int delay, int[] frames) {
        int frame(int rows) { return frames[Math.floorMod(rows / Math.max(1, delay), frames.length)]; }
    }

    record Swing(int length, int degrees, int periodRows) { }

    record Links(String artKey, int frame, int[] distances) { }

    record Orbit(int radius, int periodRows, int count) { }

    record Part(String name, String artKey, int dx, int dy, boolean back, boolean flip, Map<Mood, Anim> anims,
                Anim fallback, Swing swing, Links links, Orbit orbit) {
        /**
         * The part's animation in {@code mood}, or null when it is hidden. A mood the part does
         * not name borrows its parent's: lunge from open, open, hit and laugh from idle, flee from
         * defeat; then {@code *}. {@code mood=-} hides the part in that mood.
         */
        Anim anim(Mood mood) {
            for (Mood m = mood; m != null; m = parent(m)) {
                Anim anim = anims.get(m);
                if (anim != null) return anim.frames().length == 0 ? null : anim;
            }
            return fallback != null && fallback.frames().length > 0 ? fallback : null;
        }

        private static Mood parent(Mood mood) {
            return switch (mood) {
                case LUNGE -> Mood.OPEN;
                case OPEN, HIT, LAUGH -> Mood.IDLE;
                case FLEE -> Mood.DEFEAT;
                case IDLE, DEFEAT -> null;
            };
        }

        /** Sprites this part draws: links, then the part itself (or each orbiting copy). */
        int sprites() {
            int own = orbit == null ? 1 : orbit.count();
            return own + (links == null ? 0 : links.distances().length);
        }
    }

    private final List<Part> parts;
    private final int bob;
    private final int height;
    private final boolean grounded;

    BossRig(List<Part> parts, int bob, int height, boolean grounded) {
        this.parts = List.copyOf(parts);
        this.bob = bob;
        this.height = height;
        this.grounded = grounded;
    }

    public List<Part> parts() { return parts; }
    /** Pixels he bobs while hovering. */
    public int bob() { return bob; }
    /** Pixels above Sonic's centre he holds. */
    public int height() { return height; }
    /** True for a boss on foot: he runs away level instead of climbing. */
    public boolean grounded() { return grounded; }

    /** Parses a game's rig table; key -1 holds the {@code *} rig. */
    public static Map<Integer, BossRig> parse(String text) {
        Map<Integer, List<Part>> zones = new HashMap<>();
        Map<Integer, int[]> stances = new HashMap<>();
        int number = 0;
        for (String raw : text.split("\n")) {
            number++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\s+");
            if (f.length < 5) throw new IllegalArgumentException("boss line " + number + " needs at least 5 fields: " + line);
            int zone = f[0].equals("*") ? -1 : Integer.parseInt(f[0]);
            if (f[1].equals("hover")) {
                boolean grounded = java.util.Arrays.asList(f).subList(5, f.length).contains("grounded");
                stances.put(zone, new int[] {Integer.parseInt(f[3]), Integer.parseInt(f[4]), grounded ? 1 : 0});
                zones.computeIfAbsent(zone, ignored -> new ArrayList<>());
                continue;
            }
            Map<Mood, Anim> anims = new EnumMap<>(Mood.class);
            Anim fallback = null;
            boolean back = false, flip = false;
            Swing swing = null;
            Links links = null;
            Orbit orbit = null;
            for (int i = 5; i < f.length; i++) {
                String token = f[i];
                if (token.equals("back")) back = true;
                else if (token.equals("flip")) flip = true;
                else if (token.startsWith("swing=")) {
                    int[] v = ints(token.substring(6), ",");
                    swing = new Swing(v[0], v[1], v[2]);
                } else if (token.startsWith("links=")) {
                    String[] l = token.substring(6).split(":");
                    links = new Links(l[0], Integer.parseInt(l[1]), ints(l[2], ","));
                } else if (token.startsWith("orbit=")) {
                    int[] v = ints(token.substring(6), ",");
                    orbit = new Orbit(v[0], v[1], v[2]);
                } else {
                    int eq = token.indexOf('=');
                    int colon = token.indexOf(':');
                    if (eq < 0 || (colon < eq && !token.endsWith("=-"))) throw new IllegalArgumentException("boss line " + number + ": bad token " + token);
                    String mood = token.substring(0, eq);
                    if (token.substring(eq + 1).equals("-")) {
                        anims.put(Mood.valueOf(mood.toUpperCase(java.util.Locale.ROOT)), new Anim(1, new int[0]));
                        continue;
                    }
                    Anim anim = new Anim(Integer.parseInt(token.substring(eq + 1, colon)), ints(token.substring(colon + 1), ","));
                    if (mood.equals("*")) fallback = anim;
                    else anims.put(Mood.valueOf(mood.toUpperCase(java.util.Locale.ROOT)), anim);
                }
            }
            if (links != null && swing == null) throw new IllegalArgumentException("boss line " + number + ": links need a swing");
            zones.computeIfAbsent(zone, ignored -> new ArrayList<>()).add(new Part(f[1], f[2], Integer.parseInt(f[3]),
                    Integer.parseInt(f[4]), back, flip, Map.copyOf(anims), fallback, swing, links, orbit));
        }
        Map<Integer, BossRig> rigs = new HashMap<>();
        zones.forEach((zone, list) -> {
            int[] stance = stances.getOrDefault(zone, stances.getOrDefault(-1, new int[] {6, 72, 0}));
            rigs.put(zone, new BossRig(list, stance[0], stance[1], stance[2] != 0));
        });
        return Map.copyOf(rigs);
    }

    private static int[] ints(String text, String separator) {
        String[] parts = text.split(separator);
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Integer.decode(parts[i].strip());
        return out;
    }
}
