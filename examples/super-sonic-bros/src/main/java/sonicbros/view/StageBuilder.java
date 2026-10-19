package sonicbros.view;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneRomArt;
import java.util.ArrayList;
import java.util.List;
import sonicbros.content.Stages;
import sonicbros.sim.Arena;

/**
 * Builds a playable stage from its {@link Stages.Stage} entry: carves the main island and the
 * platforms out of the act's level kit, and derives the collision {@link Arena} from the same
 * floor lines the pictures show.
 */
public final class StageBuilder {
    /** A drawable platform: the picture and where its top-left sits relative to the platform's left/top. */
    public record PlatformArt(SceneImage image, double offsetX, double offsetY) { }

    /** Everything a match on this stage needs. */
    public record Built(Stages.Stage stage, Arena arena, SceneImage island, double islandX, double islandY,
            List<PlatformArt> platforms, SceneBackdrop backdrop) { }

    private StageBuilder() { }

    /** Builds the stage, or returns null when the ROM has no kit for it. */
    public static Built build(SceneRomArt rom, Stages.Stage s) {
        SceneLevelKit kit = rom.levelKit(s.zone(), s.act());
        if (kit == null) return null;
        StageCarver carver = new StageCarver(kit);
        StageCarver.Piece main = carver.carve(s.x(), s.floorY(), s.width(), 12, 78, 22, s.zone() * 31L + 7);
        int median = median(main.surface());
        double left = -s.width() / 2.0;
        double[] surface = new double[s.width()];
        double[] bottom = new double[s.width()];
        for (int i = 0; i < s.width(); i++) {
            surface[i] = main.surface()[i] - median;
            bottom[i] = main.bottom()[i] - median;
        }
        List<Arena.Platform> platforms = new ArrayList<>();
        switch (s.layout()) {
            case BATTLEFIELD -> {
                platforms.add(new Arena.Platform(-92, -32, -54, 0, 0, 0, 0));
                platforms.add(new Arena.Platform(32, 92, -54, 0, 0, 0, 0));
                platforms.add(new Arena.Platform(-32, 32, -104, 0, 0, 0, 0));
            }
            case TWIN -> {
                platforms.add(new Arena.Platform(-98, -34, -62, 0, 0, 0, 0));
                platforms.add(new Arena.Platform(34, 98, -62, 0, 0, 0, 0));
            }
            case TRIPLE -> {
                platforms.add(new Arena.Platform(-120, -70, -46, 0, 0, 0, 0));
                platforms.add(new Arena.Platform(-26, 26, -78, 0, 0, 0, 0));
                platforms.add(new Arena.Platform(70, 120, -46, 0, 0, 0, 0));
            }
            case LIFTS -> {
                platforms.add(new Arena.Platform(-100, -44, -70, 0, 34, 320, 0));
                platforms.add(new Arena.Platform(44, 100, -70, 0, 34, 320, Math.PI));
            }
            case SHUTTLE -> platforms.add(new Arena.Platform(-36, 36, -72, 92, 0, 480, 0));
            case NONE -> { }
        }
        List<PlatformArt> arts = new ArrayList<>();
        for (int i = 0; i < platforms.size(); i++) {
            Arena.Platform p = platforms.get(i);
            int w = (int) Math.round(p.x1() - p.x0());
            int px = s.x() + 24 + (i * 97) % Math.max(1, s.width() - w - 48);
            StageCarver.Piece piece = carver.carve(px, s.floorY(), w, 12, 20, 9, s.zone() * 57L + i);
            int pm = median(piece.surface());
            arts.add(new PlatformArt(piece.image(), 0, -pm));
        }
        Arena arena = new Arena(left, surface, bottom, platforms, left - 190, -left + 190, -290, 210,
                new double[] {left * 0.62, -left * 0.62, left * 0.22, -left * 0.22});
        return new Built(s, arena, main.image(), left, -median, arts, crop(kit.backdrop(), s.bgCrop()));
    }

    /** Keeps the columns and row ranges {@code spec} names ({@code "x,width;top-bottom,..."}), as one slow band. */
    static SceneBackdrop crop(SceneBackdrop bd, String spec) {
        if (spec == null) return bd;
        String[] parts = spec.split(";");
        String[] cols = parts[0].split(",");
        int x0 = Integer.parseInt(cols[0]);
        int w = Math.min(Integer.parseInt(cols[1]), bd.image().width() - x0);
        java.util.List<int[]> rows = new java.util.ArrayList<>();
        int total = 0;
        for (String r : parts[1].split(",")) {
            String[] ab = r.split("-");
            int a = Integer.parseInt(ab[0]);
            int b = Math.min(Integer.parseInt(ab[1]), bd.image().height());
            rows.add(new int[] {a, b});
            total += b - a;
        }
        int[] px = new int[w * total];
        int y = 0;
        for (int[] r : rows) {
            for (int sy = r[0]; sy < r[1]; sy++, y++) {
                for (int x = 0; x < w; x++) px[y * w + x] = bd.image().pixel(x0 + x, sy) | 0xFF000000;
            }
        }
        return new SceneBackdrop(new SceneImage(w, total, px), java.util.List.of(new SceneBackdrop.Band(0, total, 0.25, 0)));
    }

    private static int median(int[] values) {
        int[] copy = values.clone();
        java.util.Arrays.sort(copy);
        return copy[copy.length / 2];
    }
}
