package starpost.art;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import starpost.core.Item;
import starpost.core.Kind;

/**
 * 16-pixel item icons. The shields are the ROM's own monitor screens (Map_Monitor icon frames);
 * crops use their ripe picture; seed packets and other goods are small original pictures in
 * Mega Drive colours.
 */
public final class ItemIcons {
    /** Map_Monitor frames: the icon shown on a monitor's screen. */
    private static final int MONITOR_FIRE = 6;
    private static final int MONITOR_LIGHTNING = 7;
    private static final int MONITOR_BUBBLE = 8;

    private final Art art;
    private final Map<String, SceneImage> cache = new HashMap<>();
    private final CropArt crops;
    private final Map<String, Source> sources = new LinkedHashMap<>();

    /** Icons supplied by another system for its own items; null leaves an item to the built-in pictures. */
    public interface Source {
        SceneImage icon(Item item);
    }

    ItemIcons(Art art) {
        this.art = art;
        this.crops = new CropArt(new Tone(Tone.SPRING));
    }

    /** Draws an item's icon centred in the 16x16 box at (x, y). */
    public void draw(SceneCanvas canvas, Item item, float x, float y, SceneDraw style) {
        int monitorFrame = switch (item.id()) {
            case "water_shield" -> MONITOR_BUBBLE;
            case "fire_shield" -> MONITOR_FIRE;
            case "lightning_shield" -> MONITOR_LIGHTNING;
            default -> -1;
        };
        if (monitorFrame >= 0 && monitorFrame < art.monitor.frameCount()) {
            SceneSprite icon = art.monitor.frame(monitorFrame);
            canvas.draw(icon.image(), x + (16 - icon.width()) / 2f, y + (16 - icon.height()) / 2f, style);
            return;
        }
        SceneImage image = cache.computeIfAbsent(item.id(), id -> build(item));
        canvas.draw(image, x + (16 - image.width()) / 2f, y + (16 - image.height()) / 2f, style);
    }

    /** Adds (or, under the same key, replaces) a system's icons; they are asked before the built-in pictures. */
    public void addSource(String key, Source source) {
        sources.put(key, source);
        cache.clear();
    }

    /**
     * A small pixel picture from rows of colour letters (the crop letters of {@code CropArt}, with
     * {@code F}/{@code f} as the light and dark accent): for other systems' icons.
     */
    public static SceneImage picture(String[] rows, int light, int dark) {
        return grid(rows, light, dark);
    }

    private SceneImage build(Item item) {
        for (Source source : sources.values()) {
            SceneImage image = source.icon(item);
            if (image != null) {
                return image;
            }
        }
        if (item.kind() == Kind.CROP) {
            return fit(crops.stage(item.id(), 4, false));
        }
        if (item.kind() == Kind.SEED) {
            String crop = item.id().endsWith("_seeds") ? item.id().substring(0, item.id().length() - 6) : item.id();
            int[] fruit = CropArt.fruit(crop);
            return grid(packet(), fruit[0], fruit[1]);
        }
        if (item.kind() == Kind.PLACEABLE) {
            SceneImage picture = switch (item.id()) {
                case "buzz_waterer", "buzz_waterer_mk2" -> art.buzzBomber.frame(0).image();
                case "caterkiller_crawler" -> art.caterkiller.frame(0).image();
                case "item_monitor" -> art.monitor.frame(0).image();
                case "star_post" -> art.starpost.frame(0).image();
                default -> null;
            };
            if (picture != null) {
                return fit(picture);
            }
        }
        return switch (item.kind()) {
            case FOOD -> item.id().equals("robo_cola") ? grid(colaCan(), 0xFFDB0000, 0xFF920000) : grid(chiliDog(), 0, 0);
            case MATERIAL -> grid(item.id().equals("palm_wood") ? log() : item.id().equals("scrap") ? scrap() : stone(), 0, 0);
            case FISH -> grid(fish(), 0xFF6DB6FF, 0xFF2449DB);
            case TOOL -> grid(rod(), 0, 0);
            default -> grid(leaf(), 0xFF92FF00, 0xFF49B600);
        };
    }

    /** A picture shrunk to fit 16 pixels by dropping evenly spaced rows and columns. */
    private static SceneImage fit(SceneImage image) {
        if (image.width() <= 16 && image.height() <= 16) {
            return image;
        }
        int w = Math.min(16, image.width()), h = Math.min(16, image.height());
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = image.pixel(x * image.width() / w, y * image.height() / h);
            }
        }
        return new SceneImage(w, h, px);
    }

    private static SceneImage grid(String[] rows, int light, int dark) {
        int w = rows[0].length(), h = rows.length;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < Math.min(w, rows[y].length()); x++) {
                char ch = rows[y].charAt(x);
                px[y * w + x] = ch == 'F' ? light : ch == 'f' ? dark : CropArt.colour(ch);
            }
        }
        return new SceneImage(w, h, px);
    }

    private static String[] packet() {
        return new String[] {
            "...kkkkkkkkkk...",
            "...kYwwwwwwYk...",
            "...kwFFFFFFwk...",
            "...kwFfFFfFwk...",
            "...kwFFfFFFwk...",
            "...kwFFFFfFwk...",
            "...kwwwwwwwwk...",
            "...kwbbwbbwwk...",
            "...kwwwwwwwwk...",
            "...kYwbwbwbYk...",
            "...kkkkkkkkkk...",
        };
    }

    /** A Robo Cola can: Robomart red with a white band. */
    private static String[] colaCan() {
        return new String[] {
            ".....kkkkkk.....",
            "....kYwwwwYk....",
            "....kFFFFFFk....",
            "....kFFFFFFk....",
            "....kwwwwwwk....",
            "....kwkkwkwk....",
            "....kwwwwwwk....",
            "....kFFFFFFk....",
            "....kfFFFFfk....",
            "....kffffffk....",
            ".....kkkkkk.....",
        };
    }

    private static String[] chiliDog() {
        return new String[] {
            "..kkkkkkkkkkk...",
            ".kcYcYcYcYcYck..",
            "kOOmmOmOmmOmOOk.",
            "kccccccccccccck.",
            ".kcccccccccck...",
            "..kkkkkkkkkk....",
        };
    }

    private static String[] log() {
        return new String[] {
            "..kkkkkkkkkkk...",
            ".kbbbbbbbbbbOk..",
            "kbBbbbbBbbbOcOk.",
            "kbbbbBbbbbbOcOk.",
            ".kbbbbbbbbbbOk..",
            "..kkkkkkkkkkk...",
        };
    }

    private static String[] stone() {
        return new String[] {
            "....kkkkkk......",
            "..kkwwYwwwkk....",
            ".kwwYwwwwwwwk...",
            ".kwwwwwwYwwwwk..",
            "kwwwYwwwwwwwwk..",
            ".kkwwwwwwwwkk...",
            "...kkkkkkkk.....",
        };
    }

    private static String[] scrap() {
        return new String[] {
            "......kk........",
            "....kkwwk..kk...",
            "...kwwYwwkkwwk..",
            "..kwYkkkwwwYwk..",
            "..kwwk..kwwwk...",
            "...kwwkkwwYk....",
            "....kkwwwkk.....",
            "......kkk.......",
        };
    }

    private static String[] fish() {
        return new String[] {
            ".........kk.....",
            "..kkkkkkkFfk..kk",
            ".kFFFFFFFFFfkkFk",
            "kFwkFFFFFFFFFFfk",
            ".kFFFFFFFFFfkkFk",
            "..kkkkkkkkkk..kk",
        };
    }

    private static String[] rod() {
        return new String[] {
            "............kk..",
            "..........kkbk..",
            "........kkbk.k..",
            "......kkbk...k..",
            "....kkbk.....k..",
            "..kkbk.......w..",
            ".kbk.........k..",
            "kOk.............",
        };
    }

    private static String[] leaf() {
        return new String[] {
            ".......kk.......",
            ".....kkFFk......",
            "...kkFFFfFk.....",
            "..kFFFFfFFFk....",
            ".kFFFfFFFFFk....",
            ".kFFfFFFFFk.....",
            "..kfFFFFkk......",
            "...kkkkk........",
        };
    }
}
