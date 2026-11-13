package micaandtalon;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.PoweredEffects;
import com.openggf.game.StageSpriteSet;
import com.openggf.game.StrikeTrailStyle;
import com.openggf.io.ModAssetRoot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strike trail colours, particle pixel art and emission rules, read from one text table per
 * character. Sets name a form ({@code normal}, {@code super}, {@code hyper}); the file's header
 * documents the row syntax.
 */
final class EffectsTable {
    private record Trail(List<Integer> embers, int echoTint, int density, int echoPhaseSlot) {}

    private final Map<String, Trail> trails = new LinkedHashMap<>();
    private final Map<String, List<StrikeTrailStyle.FrontLayer>> fronts = new LinkedHashMap<>();
    private final Map<String, PoweredEffects.Particle> particles = new LinkedHashMap<>();
    private final Map<String, List<String[]>> emitters = new LinkedHashMap<>();
    private final Map<String, List<Integer>> strikePalettes = new LinkedHashMap<>();

    static EffectsTable read(ModAssetRoot assets, String table) throws IOException {
        var result = new EffectsTable();
        String text = new String(assets.readBounded(table, 65536), StandardCharsets.UTF_8);
        String particle = null;
        int ticks = 0;
        var frames = new ArrayList<StageSpriteSet.Frame>();
        int[] hotspot = null;
        var pixels = new ArrayList<String>();
        var lines = new ArrayList<>(text.lines().map(line -> line.split("#", 2)[0].strip())
                .filter(line -> !line.isEmpty()).toList());
        lines.add("end");
        for (String line : lines) {
            String[] row = line.split("\\s+");
            try {
                if (row.length == 1 && row[0].matches("[0-9a-fA-F.]+")) {
                    if (hotspot == null) throw new IllegalArgumentException("Pixel row outside a frame");
                    pixels.add(row[0]);
                    continue;
                }
                // Any other row closes the open frame, and a non-frame row closes the particle.
                if (hotspot != null) {
                    frames.add(new StageSpriteSet.Frame(image(pixels), hotspot[0], hotspot[1]));
                    hotspot = null;
                    pixels.clear();
                }
                if (particle != null && !row[0].equals("frame")) {
                    result.particles.put(particle, new PoweredEffects.Particle(List.copyOf(frames), ticks));
                    particle = null;
                    frames.clear();
                }
                switch (row[0]) {
                    case "trail" -> {
                        if (row.length != 7 && row.length != 8) throw new IllegalArgumentException("Expected 7 or 8 columns");
                        result.trails.put(row[1], new Trail(
                                List.of(rgb(row[2]), rgb(row[3]), rgb(row[4])),
                                row[5].equals("none") ? -1 : rgb(row[5]), Integer.parseInt(row[6]),
                                row.length == 8 ? Integer.parseInt(row[7]) : -1));
                    }
                    case "strike-palette" -> {
                        expect(row, 18);
                        var words = new ArrayList<Integer>();
                        for (int i = 2; i < 18; i++) words.add(Integer.parseInt(row[i], 16));
                        result.strikePalettes.put(row[1], List.copyOf(words));
                    }
                    case "front" -> {
                        expect(row, 7);
                        result.fronts.computeIfAbsent(row[1], ignored -> new ArrayList<>())
                                .add(new StrikeTrailStyle.FrontLayer(rgb(row[2]), Integer.parseInt(row[3]),
                                        Integer.parseInt(row[4]), Integer.parseInt(row[5]),
                                        Integer.parseInt(row[6])));
                    }
                    case "particle" -> {
                        expect(row, 3);
                        if (result.particles.containsKey(row[1]))
                            throw new IllegalArgumentException("Duplicate particle " + row[1]);
                        particle = row[1];
                        ticks = Integer.parseInt(row[2]);
                    }
                    case "frame" -> {
                        expect(row, 3);
                        if (particle == null) throw new IllegalArgumentException("Frame outside a particle");
                        hotspot = new int[] {Integer.parseInt(row[1]), Integer.parseInt(row[2])};
                    }
                    case "emitter" -> {
                        expect(row, 15);
                        result.emitters.computeIfAbsent(row[1], ignored -> new ArrayList<>()).add(row);
                    }
                    case "end" -> { }
                    default -> throw new IllegalArgumentException("Unknown row");
                }
            } catch (IllegalArgumentException error) {
                throw new IOException("Invalid effects table " + table + ": " + line, error);
            }
        }
        return result;
    }

    /** The set's strike colours, or null when the table gives none. */
    StrikeTrailStyle trail(String set) {
        var trail = trails.get(set);
        return trail == null ? null : new StrikeTrailStyle(trail.embers(),
                fronts.getOrDefault(set, List.of()), trail.echoTint(), trail.density(), trail.echoPhaseSlot());
    }

    /** The set's trail and emitters; particles are those its emitters name. */
    PoweredEffects effects(String set) throws IOException {
        var names = new ArrayList<String>();
        var rules = new ArrayList<PoweredEffects.Emitter>();
        for (String[] row : emitters.getOrDefault(set, List.of())) {
            try {
                var indices = new ArrayList<Integer>();
                for (String name : row[3].split(",")) {
                    if (!particles.containsKey(name)) throw new IllegalArgumentException("Missing particle " + name);
                    if (!names.contains(name)) names.add(name);
                    indices.add(names.indexOf(name));
                }
                rules.add(new PoweredEffects.Emitter(PoweredEffects.Trigger.valueOf(row[2]), indices,
                        Integer.decode(row[4]), Integer.decode(row[5]), PoweredEffects.Spread.valueOf(row[6]),
                        Integer.decode(row[7]), Integer.decode(row[8]), Integer.decode(row[9]),
                        Integer.decode(row[10]), Integer.decode(row[11]), Integer.decode(row[12]),
                        Integer.decode(row[13]), Integer.decode(row[14])));
            } catch (IllegalArgumentException error) {
                throw new IOException("Invalid emitter: " + String.join(" ", row), error);
            }
        }
        return new PoweredEffects(trail(set), names.stream().map(particles::get).toList(), rules,
                strikePalettes.getOrDefault(set, List.of()));
    }

    private static CharacterPresentation.Image image(List<String> rows) {
        if (rows.isEmpty()) throw new IllegalArgumentException("Empty particle frame");
        int width = rows.getFirst().length();
        var pixels = new ByteArrayOutputStream();
        for (String row : rows) {
            if (row.length() != width) throw new IllegalArgumentException("Ragged particle frame");
            for (char c : row.toCharArray()) pixels.write(c == '.' ? 0 : Character.digit(c, 16));
        }
        return new CharacterPresentation.Image(width, rows.size(), pixels.toByteArray());
    }

    private static int rgb(String hex) {
        if (!hex.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Expected RRGGBB: " + hex);
        return Integer.parseInt(hex, 16);
    }

    private static void expect(String[] row, int columns) {
        if (row.length != columns) throw new IllegalArgumentException("Expected " + columns + " columns");
    }
}
