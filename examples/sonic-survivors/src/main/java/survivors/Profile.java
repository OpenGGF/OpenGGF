package survivors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Permanent progress kept between runs in {@code saves/sonic-survivors/profile.txt}: the ring
 * bank and the shop levels it buys, the Chaos Emeralds won from zone bosses, the furthest zone a
 * run may start from, and lifetime records. Saved as plain {@code key=value} lines whenever it
 * changes; an unreadable file starts a fresh profile rather than failing the game.
 */
final class Profile {
    // Shop items, bought with banked rings in camp.
    static final int SHOP_POWER = 0, SHOP_RINGS = 1, SHOP_REROLL = 2, SHOP_MAGNET = 3, SHOP_GROWTH = 4,
            SHOP_REVIVAL = 5, SHOP_TALENT = 6;
    static final int SHOP_COUNT = 7;
    /** Zone bosses EHZ to OOZ each award one emerald, once. */
    static final int EMERALDS = 7;

    private final Path file;
    private boolean loaded;
    int bank;
    final int[] shop = new int[SHOP_COUNT];
    int emeralds;
    /** Highest stage index a run may start from (0 = Emerald Hill only). */
    int unlocked;
    int mode;
    int runs, wins, bestStages, bestKills, totalKills, bestCombo, bankedTotal;

    Profile(Path file) { this.file = file; }

    static String shopName(int item) {
        return switch (item) {
            case SHOP_POWER -> "POWER UP";
            case SHOP_RINGS -> "RING START";
            case SHOP_REROLL -> "REROLL";
            case SHOP_MAGNET -> "MAGNET";
            case SHOP_GROWTH -> "GROWTH";
            case SHOP_REVIVAL -> "REVIVAL";
            default -> "TALENT";
        };
    }

    static String shopEffect(int item) {
        return switch (item) {
            case SHOP_POWER -> "+10% DAMAGE";
            case SHOP_RINGS -> "+10 STARTING RINGS";
            case SHOP_REROLL -> "+1 REROLL EACH ZONE";
            case SHOP_MAGNET -> "+16PX RING PULL";
            case SHOP_GROWTH -> "+10% XP";
            case SHOP_REVIVAL -> "+1 REVIVE PER RUN";
            default -> "4 CARDS PER LEVEL UP";
        };
    }

    static int shopMax(int item) {
        return switch (item) {
            case SHOP_POWER, SHOP_RINGS -> 5;
            case SHOP_REROLL, SHOP_MAGNET, SHOP_GROWTH -> 3;
            case SHOP_REVIVAL -> 2;
            default -> 1;
        };
    }

    static int shopCost(int item, int level) {
        int base = switch (item) {
            case SHOP_POWER -> 60;
            case SHOP_RINGS -> 40;
            case SHOP_REROLL -> 100;
            case SHOP_MAGNET -> 50;
            case SHOP_GROWTH -> 80;
            case SHOP_REVIVAL -> 300;
            default -> 500;
        };
        return base * (level + 1);
    }

    /** What the emerald from stage {@code index}'s boss grants on every later run. */
    static String emeraldEffect(int index) {
        return switch (index) {
            case 0 -> "A FREE LEVEL UP AT THE START";
            case 1 -> "+1 REROLL EACH ZONE";
            case 2 -> "COMBO MULTIPLIER CAP +2X";
            case 3 -> "+1 RING FROM EVERY BADNIK";
            case 4 -> "HITS COST 3 FEWER RINGS";
            case 5 -> "A SHIELD AT EVERY ZONE START";
            default -> "+1 REVIVE PER RUN";
        };
    }

    static String emeraldName(int index) {
        return switch (index) {
            case 0 -> "GREEN";
            case 1 -> "YELLOW";
            case 2 -> "BLUE";
            case 3 -> "PINK";
            case 4 -> "RED";
            case 5 -> "GREY";
            default -> "CYAN";
        };
    }

    boolean hasEmerald(int index) { return (emeralds & 1 << index) != 0; }
    int emeraldCount() { return Integer.bitCount(emeralds & 0x7F); }
    boolean allEmeralds() { return emeraldCount() == EMERALDS; }

    /** Old profiles already record a first clear in their route unlocks or emeralds. */
    boolean extendedModesUnlocked() { return unlocked > 0 || emeralds != 0 || wins > 0; }

    int selectedMode() { return extendedModesUnlocked() ? mode : RunState.STANDARD; }

    boolean cycleMode() {
        if (!extendedModesUnlocked()) return false;
        mode = (mode + 1) % 3;
        save();
        return true;
    }

    boolean canBuy(int item) {
        return shop[item] < shopMax(item) && bank >= shopCost(item, shop[item]);
    }

    boolean buy(int item) {
        if (!canBuy(item)) return false;
        bank -= shopCost(item, shop[item]);
        shop[item]++;
        save();
        return true;
    }

    Profile load() {
        if (loaded) return this;
        loaded = true;
        if (!Files.isRegularFile(file)) return this;
        try {
            for (String line : Files.readAllLines(file)) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                int value;
                try { value = Integer.parseInt(line.substring(eq + 1).trim()); }
                catch (NumberFormatException bad) { continue; }
                switch (key) {
                    case "mode" -> mode = value >= RunState.STANDARD && value <= RunState.ENDLESS
                            ? value : RunState.STANDARD;
                    case "bank" -> bank = Math.max(0, value);
                    case "emeralds" -> emeralds = value & 0x7F;
                    case "unlocked" -> unlocked = Math.max(0, Math.min(Stages.COUNT - 1, value));
                    case "runs" -> runs = value;
                    case "wins" -> wins = value;
                    case "bestStages" -> bestStages = value;
                    case "bestKills" -> bestKills = value;
                    case "totalKills" -> totalKills = value;
                    case "bestCombo" -> bestCombo = value;
                    case "bankedTotal" -> bankedTotal = value;
                    default -> {
                        if (key.startsWith("shop.")) {
                            try {
                                int item = Integer.parseInt(key.substring(5));
                                if (item >= 0 && item < SHOP_COUNT) shop[item] = Math.max(0, Math.min(shopMax(item), value));
                            } catch (NumberFormatException ignored) { }
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Logger.getLogger(Profile.class.getName()).log(Level.WARNING, "Could not read " + file, e);
        }
        return this;
    }

    void save() {
        var out = new StringBuilder();
        out.append("mode=").append(mode).append('\n');
        out.append("bank=").append(bank).append('\n');
        out.append("emeralds=").append(emeralds).append('\n');
        out.append("unlocked=").append(unlocked).append('\n');
        out.append("runs=").append(runs).append('\n');
        out.append("wins=").append(wins).append('\n');
        out.append("bestStages=").append(bestStages).append('\n');
        out.append("bestKills=").append(bestKills).append('\n');
        out.append("totalKills=").append(totalKills).append('\n');
        out.append("bestCombo=").append(bestCombo).append('\n');
        out.append("bankedTotal=").append(bankedTotal).append('\n');
        for (int i = 0; i < SHOP_COUNT; i++) out.append("shop.").append(i).append('=').append(shop[i]).append('\n');
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString());
        } catch (IOException | RuntimeException e) {
            Logger.getLogger(Profile.class.getName()).log(Level.WARNING, "Could not save " + file, e);
        }
    }
}
