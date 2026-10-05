package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.ui.Colors;

/**
 * How each enemy looks. Badniks and bosses are assembled from their ROM mapping frames the
 * way the game builds them from child objects (offsets from the engine's object classes and
 * the disassembly's {@code ChildObjDat} tables), then scaled up for elites. Enemies without
 * art get a labelled placeholder.
 */
final class EnemyVisuals {
    /** Where an enemy was drawn this frame (for hotspots, intents and effects). */
    record Box(int x, int y, int w, int h) {
        int centerX() {
            return x + w / 2;
        }
    }

    /** One sprite of a composition, offset from the enemy's centre (art faces left). */
    private record Part(SceneSprite sprite, float dx, float dy, boolean flipX) {
    }

    private EnemyVisuals() {
    }

    /**
     * Draws {@code enemy} standing on {@code groundY} centred on {@code x}. {@code flash} is
     * 0..1 white flash; {@code alpha} fades it out when dying.
     */
    static Box draw(Shell shell, SceneCanvas c, Enemy enemy, int x, int groundY, long ticks, float flash, float alpha) {
        List<Part> parts = compose(shell, enemy.art(), ticks);
        if (parts.isEmpty()) {
            return placeholder(shell, c, enemy, x, groundY, flash, alpha);
        }
        float scale = scale(enemy.art());
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Part p : parts) {
            SceneSprite s = p.sprite();
            float left = p.flipX() ? p.dx() - (s.width() - s.originX()) : p.dx() - s.originX();
            float top = p.dy() - s.originY();
            minX = Math.min(minX, left);
            minY = Math.min(minY, top);
            maxX = Math.max(maxX, left + s.width());
            maxY = Math.max(maxY, top + s.height());
        }
        // Place the composition so its feet touch the ground and its middle sits on x.
        float originX = x - (minX + maxX) / 2f * scale;
        float originY = groundY - maxY * scale;
        int flashColor = flash > 0 ? Colors.alpha(Colors.WHITE, Math.min(1f, flash)) : 0;
        for (Part p : parts) {
            SceneDraw style = SceneDraw.plain().withScale(scale).withFlipX(p.flipX()).withFlash(flashColor)
                    .withAlpha(alpha);
            c.draw(p.sprite(), originX + p.dx() * scale, originY + p.dy() * scale, style);
        }
        return new Box(Math.round(originX + minX * scale), Math.round(originY + minY * scale),
                Math.round((maxX - minX) * scale), Math.round((maxY - minY) * scale));
    }

    private static float scale(String art) {
        return switch (art) {
            case "aiz:mega_rhinobot", "aiz:caterkiller_sr" -> 2f;
            default -> 1f;
        };
    }

    private static SceneSprite frame(Shell shell, String key, int frame) {
        return shell.art.romFrame(key, frame);
    }

    private static List<Part> compose(Shell shell, String art, long ticks) {
        List<Part> parts = new ArrayList<>();
        switch (art) {
            case "aiz:rhinobot", "aiz:mega_rhinobot" -> {
                // Frames 0 (slow) and 1 (run): the Rhinobot revs in place.
                add(parts, frame(shell, "rhinobot", (ticks / 10) % 2 == 0 ? 0 : 1), 0, 0, false);
            }
            case "aiz:bloominator", "aiz:grove_bloominator" -> {
                // Idle frame 0; every few seconds it blooms through 1, 2, 3 (byte_86E42).
                long cycle = ticks % 150;
                int f = cycle < 110 ? 0 : cycle < 120 ? 1 : cycle < 130 ? 2 : 3;
                add(parts, frame(shell, "bloominator", f), 0, 0, false);
            }
            case "aiz:monkey_dude" -> {
                add(parts, frame(shell, "monkey_dude", (ticks / 8) % 2 == 0 ? 0 : 1), 0, 0, false);
                // Arm chain: root at (-14,-2) (MonkeyDudeArm_SetAnchor), three ball links, then the
                // hand holding a coconut (frame 6), swinging slowly.
                double swing = Math.sin(ticks * 0.05) * 0.4;
                float ax = -14;
                float ay = -2;
                for (int i = 0; i < 4; i++) {
                    double angle = Math.PI * 0.6 + swing * (i + 1) / 4.0;
                    ax += (float) Math.cos(angle) * 5;
                    ay += (float) Math.sin(angle) * 5;
                    add(parts, frame(shell, "monkey_dude", i == 3 ? 6 : 3), ax, ay, false);
                }
            }
            case "aiz:caterkiller_jr", "aiz:big_caterkiller", "aiz:caterkiller_sr" -> {
                // Head plus six trailing children (ChildObjDat_CaterKillerJrBodySegments): three tall
                // segments, one thin, and two coconut-ball tail links from the Monkey Dude art.
                int[] trail = {11, 23, 35, 47, 55, 63};
                int length = art.equals("aiz:caterkiller_jr") ? 6 : 6;
                for (int i = length - 1; i >= 0; i--) {
                    float dy = (float) Math.sin(ticks * 0.08 - i * 0.7) * 3;
                    SceneSprite s = i < 3 ? frame(shell, "caterkiller_jr", 1)
                            : i == 3 ? frame(shell, "caterkiller_jr", 2) : frame(shell, "monkey_dude", 3);
                    add(parts, s, trail[i], dy, false);
                }
                add(parts, frame(shell, "caterkiller_jr", 0), 0, (float) Math.sin(ticks * 0.08) * 3, false);
            }
            case "aiz:fire_breath" -> {
                // Obj_AIZMiniboss: body (0), jets (1/2, line 0 in ROM), arm (6) and three barrels (3).
                add(parts, frame(shell, "aiz_miniboss", 3), 0, -0x20, false);
                add(parts, frame(shell, "aiz_miniboss", 3), 9, -0x1C, false);
                add(parts, frame(shell, "aiz_miniboss", 3), 0x12, -0x18, false);
                add(parts, frame(shell, "aiz_miniboss", 0), 0, 0, false);
                add(parts, frame(shell, "aiz_miniboss", 6), -0x24, 8, false);
                add(parts, frame(shell, "aiz_miniboss", (ticks / 2) % 2 == 0 ? 1 : 2), 0, 0x20, false);
            }
            case "aiz:flame_craft" -> {
                // Obj_AIZEndBoss: Robotnik's head and cockpit (ship frame 8) above the body (0) and two
                // arms (1 and $2A) with propellers (4..6).
                add(parts, frame(shell, "robotnik_ship", (ticks / 6) % 2 == 0 ? 0 : 1), 0, -0x14 - 0x1C, false);
                add(parts, frame(shell, "robotnik_ship", 8), 0, -0x14, false);
                add(parts, frame(shell, "aiz_end_boss", 0), 0, 0, false);
                add(parts, frame(shell, "aiz_end_boss", 1), 0x14, -4, false);
                add(parts, frame(shell, "aiz_end_boss", 0x2A), -0x14, -4, false);
                int prop = 4 + (int) ((ticks / 3) % 3);
                add(parts, frame(shell, "aiz_end_boss", prop), 0x14 - 0x1C, -4, false);
                add(parts, frame(shell, "aiz_end_boss", prop), -0x14 + 0x1C, -4, true);
            }
            default -> {
            }
        }
        return parts;
    }

    private static void add(List<Part> parts, SceneSprite sprite, float dx, float dy, boolean flip) {
        if (sprite != null && sprite.width() > 1) {
            parts.add(new Part(sprite, dx, dy, flip));
        }
    }

    private static Box placeholder(Shell shell, SceneCanvas c, Enemy enemy, int x, int groundY, float flash,
            float alpha) {
        boolean big = enemy.maxHp() >= 150;
        boolean elite = enemy.maxHp() >= 80;
        int w = big ? 72 : elite ? 52 : 36;
        int h = big ? 80 : elite ? 52 : 34;
        int hue = Math.abs(enemy.id().hashCode());
        int body = 0xFF000000 | ((0x60 + hue % 0x80) << 16) | ((0x30 + (hue >> 8) % 0x60) << 8) | (0x40 + (hue >> 16) % 0x80);
        int left = x - w / 2;
        int top = groundY - h;
        c.fill(left, top, w, h, Colors.alpha(Colors.BLACK, alpha));
        c.fill(left + 1, top + 1, w - 2, h - 2, Colors.alpha(Colors.mix(body, Colors.WHITE, flash), alpha));
        String label = enemy.name().toUpperCase();
        if (shell.font.width(label) > w - 4) {
            label = label.split(" ")[0];
        }
        shell.font.drawOutlined(c, label, x - shell.font.width(label) / 2, top + h / 2 - 2,
                Colors.alpha(Colors.WHITE, alpha), 1);
        return new Box(left, top, w, h);
    }

    /** The boss shown at the top of the map; false when there is no art for it. */
    static boolean drawPortrait(Shell shell, SceneCanvas c, String encounterId, int x, int bottomY) {
        String art = switch (encounterId) {
            case "aiz:fire_breath" -> "aiz:fire_breath";
            case "aiz:flame_craft" -> "aiz:flame_craft";
            default -> null;
        };
        if (art == null) {
            return false;
        }
        List<Part> parts = compose(shell, art, shell.ticks);
        if (parts.isEmpty()) {
            return false;
        }
        c.clip(x - 25, bottomY - 28, 50, 36);
        for (Part p : parts) {
            c.draw(p.sprite(), x + p.dx() * 0.5f, bottomY - 6 + p.dy() * 0.5f,
                    SceneDraw.plain().withScale(0.5f).withFlipX(p.flipX()));
        }
        c.unclip();
        return true;
    }
}
