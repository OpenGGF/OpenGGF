package starpost.ruins;

import com.openggf.mods.scene.SceneImage;
import starpost.art.ItemIcons;
import starpost.core.Item;

/**
 * Icons for the Ruins' finds: small original pictures in Mega Drive colours (the design allows
 * original art for item icons): ore in Marble Zone's purple-veined stone, cut gems, a shard,
 * cracked geodes, Records as discs with a coloured label, and Pud's golden seed.
 */
final class RuinsIcons implements ItemIcons.Source {
    @Override
    public SceneImage icon(Item item) {
        return switch (item.id()) {
            case "marble_ore" -> grid(ore(), 0xFFB66DFF, 0xFF6D24B6);
            case "lava_ruby" -> grid(gem(), 0xFFFF2400, 0xFFB60000);
            case "tide_sapphire" -> grid(gem(), 0xFF2492FF, 0xFF0024B6);
            case "spark_topaz" -> grid(gem(), 0xFFFFDB00, 0xFFDB6D00);
            case "emerald_shard" -> grid(shard(), 0xFF24DB92, 0xFF00926D);
            case "marble_geode" -> grid(geode(), 0xFFB66DFF, 0xFF6D24B6);
            case "tide_geode" -> grid(geode(), 0xFF6DB6FF, 0xFF2449DB);
            case "scrap_geode" -> grid(geode(), 0xFFFFDB00, 0xFFDB6D00);
            case "record_marble" -> grid(record(), 0xFFB66DFF, 0xFF6D24B6);
            case "record_labyrinth" -> grid(record(), 0xFF2492FF, 0xFF0024B6);
            case "record_scrap_brain" -> grid(record(), 0xFFFF9200, 0xFFB64900);
            case "record_drowning" -> grid(record(), 0xFF6DFFFF, 0xFF24B6B6);
            case "record_boss" -> grid(record(), 0xFFFF2400, 0xFFB60000);
            case "record_invincible" -> grid(record(), 0xFFFFDB00, 0xFFDB9200);
            case "record_final" -> grid(record(), 0xFFFFFFFF, 0xFFB6B6B6);
            case "super_sunflower_seeds" -> grid(seed(), 0xFFFFDB00, 0xFFDB9200);
            default -> null;
        };
    }

    private static int colour(char c, int light, int dark) {
        return switch (c) {
            case 'k' -> 0xFF240000;
            case 'w' -> 0xFFFFFFFF;
            case 'g' -> 0xFFDBDBDB;
            case 'G' -> 0xFF929292;
            case 'n' -> 0xFF242424;
            case 'N' -> 0xFF494949;
            case 'y' -> 0xFFFFDB00;
            case 'o' -> 0xFFDB9200;
            case 'F' -> light;
            case 'f' -> dark;
            default -> 0;
        };
    }

    private static SceneImage grid(String[] rows, int light, int dark) {
        int w = 0;
        for (String row : rows) {
            w = Math.max(w, row.length());
        }
        int h = rows.length;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                px[y * w + x] = colour(rows[y].charAt(x), light, dark);
            }
        }
        return new SceneImage(w, h, px);
    }

    private static String[] ore() {
        return new String[] {
            "....kkkkkk......",
            "..kkgggwggkk....",
            ".kggFgggggFgk...",
            ".kgFFggggFFGk...",
            "kgggggFfgggGGk..",
            "kgggggFFfggGGk..",
            ".kGgggggFgGGk...",
            "..kkGGGGGGkk....",
            "....kkkkkkk.....",
        };
    }

    private static String[] gem() {
        return new String[] {
            "....kkkkkkk.....",
            "...kFwFFFFfk....",
            "..kFwFFFFFFfk...",
            ".kkkkkkkkkkkkk..",
            ".kFFFFFfFFFFfk..",
            "..kFFFFfFFFfk...",
            "...kFFFfFFfk....",
            "....kFFfFfk.....",
            ".....kFffk......",
            "......kfk.......",
            ".......k........",
        };
    }

    private static String[] shard() {
        return new String[] {
            "..........kk....",
            ".........kwFk...",
            "........kwFFk...",
            ".......kFwFfk...",
            "......kFFFfk....",
            ".....kFFFffk....",
            "....kFFFffk.....",
            "...kFFffffk.....",
            "..kFfffffk......",
            "..kkffffk.......",
            "....kkkk........",
        };
    }

    private static String[] geode() {
        return new String[] {
            "....kkkkkk......",
            "..kkGGGGGGkk....",
            ".kGGNkkkNGGGk...",
            ".kGNkFwFkNGGk...",
            "kGGkFFwFFkGGGk..",
            "kGGkfFFFfkGGGk..",
            ".kGNkfffkNGGk...",
            "..kkNNkNNNkk....",
            "....kkkkkkk.....",
        };
    }

    private static String[] record() {
        return new String[] {
            ".....kkkkk......",
            "...kknnnnnkk....",
            "..knnNnnnnnnk...",
            ".knNnnFFFnnnnk..",
            ".knnnFFfFFnnnk..",
            "knnnFFfwfFFnnnk.",
            ".knnnFFfFFnnNk..",
            ".knnnnFFFnnNnk..",
            "..knnnnnnnNnk...",
            "...kknnnnnkk....",
            ".....kkkkk......",
        };
    }

    private static String[] seed() {
        return new String[] {
            "......kkk.......",
            ".....kyFyk......",
            "....kyFwFyk.....",
            "...kyFFwFFok....",
            "...kFFFFFFok....",
            "...kFFFFFfok....",
            "...kfFFFFfok....",
            "....kfFFfok.....",
            ".....kfffk......",
            "......kkk.......",
        };
    }
}
