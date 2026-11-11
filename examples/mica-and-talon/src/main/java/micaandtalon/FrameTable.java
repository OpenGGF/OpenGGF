package micaandtalon;

import com.openggf.game.EndingSpriteSet;
import com.openggf.game.PresentationFrameSet;
import com.openggf.game.StageSpriteSet;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the example's art-side frame and palette tables through the bounded Mod API asset root. */
final class FrameTable {
    private final Map<String, StageSpriteSet> banks;
    private final Map<String, int[]> palettes;

    private FrameTable(Map<String, StageSpriteSet> banks, Map<String, int[]> palettes) {
        this.banks = Map.copyOf(banks);
        this.palettes = Map.copyOf(palettes);
    }

    static FrameTable read(ModAssetRoot assets, String table) throws IOException {
        var frames = new LinkedHashMap<String, Map<Integer, StageSpriteSet.Frame>>();
        var palettes = new LinkedHashMap<String, int[]>();
        for (String[] row : rows(assets, table)) {
            try {
                if (row[0].equals("palette")) {
                    if (row.length != 3 || palettes.containsKey(row[1])) {
                        throw new IllegalArgumentException("Expected palette bank image");
                    }
                    palettes.put(
                            row[1],
                            PresentationImages.backing(assets, relative(table, row[2]), 0).rgb());
                    continue;
                }
                if (row.length != 5) {
                    throw new IllegalArgumentException(
                            "Expected bank selector image hotspotX hotspotY");
                }
                var bank = frames.computeIfAbsent(row[0], ignored -> new LinkedHashMap<>());
                var frame =
                        new StageSpriteSet.Frame(
                                PresentationImages.read(assets, relative(table, row[2])),
                                Integer.parseInt(row[3]),
                                Integer.parseInt(row[4]));
                if (bank.putIfAbsent(Integer.parseInt(row[1]), frame) != null) {
                    throw new IllegalArgumentException("Duplicate selector in " + row[0]);
                }
            } catch (IllegalArgumentException error) {
                throw new IOException(
                        "Invalid frame table " + table + ": " + String.join(" ", row), error);
            }
        }
        var banks = new LinkedHashMap<String, StageSpriteSet>();
        frames.forEach((name, bank) -> banks.put(name, new StageSpriteSet(bank)));
        return new FrameTable(banks, palettes);
    }

    Map<String, StageSpriteSet> banks() {
        return banks;
    }

    Map<StageSpriteSet.Surface, StageSpriteSet> stages() {
        var result =
                new EnumMap<StageSpriteSet.Surface, StageSpriteSet>(StageSpriteSet.Surface.class);
        banks.forEach((name, bank) -> result.put(StageSpriteSet.Surface.valueOf(name), bank));
        return Map.copyOf(result);
    }

    Map<EndingSpriteSet.Surface, EndingSpriteSet> endings() {
        var result =
                new EnumMap<EndingSpriteSet.Surface, EndingSpriteSet>(
                        EndingSpriteSet.Surface.class);
        banks.forEach(
                (name, bank) ->
                        result.put(
                                EndingSpriteSet.Surface.valueOf(name),
                                new EndingSpriteSet(bank, palettes.get(name))));
        return Map.copyOf(result);
    }

    Map<String, Map<Integer, PresentationFrameSet.Pose>> screenBanks() {
        var result = new LinkedHashMap<String, Map<Integer, PresentationFrameSet.Pose>>();
        banks.forEach(
                (name, bank) -> {
                    var poses = new LinkedHashMap<Integer, PresentationFrameSet.Pose>();
                    bank.frames()
                            .forEach(
                                    (id, frame) ->
                                            poses.put(
                                                    id,
                                                    new PresentationFrameSet.Pose(
                                                            frame.image(),
                                                            frame.hotspotX(),
                                                            frame.hotspotY())));
                    result.put(name, Map.copyOf(poses));
                });
        return Map.copyOf(result);
    }

    static Map<String, List<List<Integer>>> colors(ModAssetRoot assets, String table)
            throws IOException {
        var result = new LinkedHashMap<String, List<List<Integer>>>();
        for (String[] row : rows(assets, table)) {
            try {
                if (row.length != 17)
                    throw new IllegalArgumentException("Expected bank and sixteen colour words");
                var colors = new ArrayList<Integer>();
                for (int i = 1; i < row.length; i++) colors.add(Integer.parseInt(row[i], 16));
                result.computeIfAbsent(row[0], ignored -> new ArrayList<>())
                        .add(List.copyOf(colors));
            } catch (IllegalArgumentException error) {
                throw new IOException(
                        "Invalid palette table " + table + ": " + String.join(" ", row), error);
            }
        }
        result.replaceAll((name, cycles) -> List.copyOf(cycles));
        return Map.copyOf(result);
    }

    private static List<String[]> rows(ModAssetRoot assets, String table) throws IOException {
        String text = new String(assets.readBounded(table, 65536), StandardCharsets.UTF_8);
        return text.lines()
                .map(line -> line.split("#", 2)[0].strip())
                .filter(line -> !line.isEmpty())
                .map(line -> line.split("\\s+"))
                .toList();
    }

    private static String relative(String table, String image) {
        String entry =
                Path.of(table).getParent().resolve(image).normalize().toString().replace('\\', '/');
        return ModAssetRoot.requireNormalizedEntry(entry);
    }
}
