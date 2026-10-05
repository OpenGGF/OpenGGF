package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.Relic;
import slaytherobotnik.core.RelicTier;
import slaytherobotnik.ui.Colors;

/** Small icons for rings, potions (item monitors) and relics, from the ROM where possible. */
final class HudIcons {
    private HudIcons() {
    }

    /** A spinning ring, 12x12. */
    static void ring(Shell shell, SceneCanvas c, int x, int y, long ticks) {
        SceneSprite ring = shell.art.romFrame("ring", (int) ((ticks / 8) % 4));
        if (ring != null) {
            c.draw(ring, x + 6, y + 6, com.openggf.mods.scene.SceneDraw.plain());
            return;
        }
        int phase = (int) ((ticks / 8) % 4);
        int rw = phase == 2 ? 2 : phase == 1 || phase == 3 ? 6 : 10;
        int rx = x + (12 - rw) / 2;
        c.fill(rx, y + 1, rw, 10, Colors.BLACK);
        c.fill(rx + 1, y + 2, Math.max(1, rw - 2), 8, 0xFFFFDA24);
        if (rw > 4) {
            c.fill(rx + 3, y + 4, rw - 6, 4, 0xFF906C00);
        }
    }

    /** An item monitor for a potion slot (empty slots draw a dim box). */
    static void potion(Shell shell, SceneCanvas c, PotionDef potion, int x, int y) {
        if (potion == null) {
            c.draw(shell.art.icon("ui_potion_slot"), x + 1, y + 1);
            return;
        }
        if (shell.art.romFrame("monitor", 0) != null) {
            monitor(shell, c, monitorFace(potion.id()), x + 6, y + 12, 0.75f, shell.ticks);
            return;
        }
        int color = switch (potion.rarity()) {
            case "Rare" -> 0xFFFFDA24;
            case "Uncommon" -> 0xFF6CB6FF;
            default -> 0xFFB6B6B6;
        };
        c.fill(x, y, 12, 11, Colors.BLACK);
        c.fill(x + 1, y + 1, 10, 9, 0xFF6C6C6C);
        c.fill(x + 2, y + 2, 8, 6, color);
        c.fill(x + 3, y + 3, 2, 2, Colors.WHITE);
    }

    /**
     * Draws an item monitor showing {@code face}: a {@code Map_Monitor} frame number as a
     * string ("3" Robotnik, "4" rings, "5" shoes, "6" fire, "7" lightning, "8" bubble, "9"
     * invincibility, "10" super), "static", or a short text such as "1UP" painted onto a
     * static screen (the ROM loads the 1-Up face from the life icon art, which this table
     * does not). {@code x, y} is the sprite origin, as for the ROM object.
     */
    static void monitor(Shell shell, SceneCanvas c, String face, float x, float y, float scale, long ticks) {
        SceneDraw style = SceneDraw.plain().withScale(scale);
        int frame = switch (face) {
            case "3", "4", "5", "6", "7", "8", "9", "10" -> Integer.parseInt(face);
            default -> (int) ((ticks / 2) % 3); // frames 0-2: static
        };
        SceneSprite sprite = shell.art.romFrame("monitor", frame);
        if (sprite == null) {
            return;
        }
        c.draw(sprite, x, y, style);
        if (frame <= 2 && !face.equals("static")) {
            // Paint the face on the screen: the screen is the box's top 16x14 area.
            float left = x - sprite.originX() * scale;
            float top = y - sprite.originY() * scale;
            int sw = Math.round(14 * scale);
            int sh = Math.round(12 * scale);
            int sx = Math.round(left + sprite.width() * scale / 2f - sw / 2f);
            int sy = Math.round(top + 4 * scale);
            c.fill(sx, sy, sw, sh, 0xFF102048);
            int tw = shell.font.width(face);
            shell.font.draw(c, face, sx + (sw - tw) / 2 + 1, sy + (sh - 5) / 2, faceColor(face));
        }
    }

    private static int faceColor(String face) {
        return switch (face) {
            case "1UP" -> 0xFF6CB6FF;
            case "P" -> 0xFFFF6C48;
            case "F" -> 0xFF6CDAFF;
            case "G" -> 0xFF48DA48;
            case "+" -> 0xFFFF4890;
            case "E" -> 0xFFFFDA24;
            default -> Colors.WHITE;
        };
    }

    /** What a potion's monitor shows (see {@link #monitor}). */
    static String monitorFace(String potionId) {
        return switch (potionId) {
            case "potion:robotnik_monitor" -> "3";
            case "potion:ring_burst", "potion:super_ring" -> "4";
            case "potion:speed_shoes" -> "5";
            case "potion:fire_shield" -> "6";
            case "potion:lightning_shield" -> "7";
            case "potion:blue_shield" -> "8";
            case "potion:invincibility" -> "9";
            case "potion:super_monitor" -> "10";
            case "potion:extra_life" -> "1UP";
            case "potion:power_monitor" -> "P";
            case "potion:focus_monitor" -> "F";
            case "potion:glove_monitor" -> "G";
            case "potion:health_monitor" -> "+";
            case "potion:energy_capsule" -> "E";
            default -> "static";
        };
    }

    /** A relic badge, 12x12: a coloured gem with the relic's initial (art per relic comes later). */
    static void relic(Shell shell, SceneCanvas c, Relic relic, int x, int y) {
        int color = switch (relic.tier()) {
            case RelicTier.STARTER -> 0xFFDA4824;
            case RelicTier.UNCOMMON -> 0xFF2490FF;
            case RelicTier.RARE -> 0xFFFFDA24;
            case RelicTier.BOSS -> 0xFFB648FF;
            case RelicTier.SHOP -> 0xFF48DA48;
            default -> 0xFFB6B6B6;
        };
        int fill = relic.usedUp() ? 0xFF484848 : color;
        c.fill(x + 2, y, 8, 12, Colors.BLACK);
        c.fill(x, y + 2, 12, 8, Colors.BLACK);
        c.fill(x + 1, y + 1, 10, 10, Colors.BLACK);
        c.fill(x + 2, y + 1, 8, 10, fill);
        c.fill(x + 1, y + 2, 10, 8, fill);
        c.fill(x + 3, y + 2, 3, 2, Colors.alpha(Colors.WHITE, 0.6f));
        String initial = relic.name().substring(0, 1);
        shell.font.drawOutlined(c, initial, x + 6 - shell.font.width(initial) / 2, y + 4, Colors.WHITE, 1);
        if (relic.counter() >= 0) {
            String n = Integer.toString(relic.counter());
            shell.font.drawOutlined(c, n, x + 12 - shell.font.width(n), y + 8, Colors.GOLD, 1);
        }
    }
}
