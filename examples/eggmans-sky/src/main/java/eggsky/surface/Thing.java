package eggsky.surface;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import eggsky.art.Art;
import eggsky.core.Colour;
import eggsky.game.Catalog;
import eggsky.world.Planet;
import eggsky.world.Species;

/**
 * Something placed on the terrain: a plant, rock, crystal, resource plant, monitor, Egg Capsule,
 * Starpost, ruin, Giant Ring, emerald shrine, wreck or a ring cluster. Its position is the point
 * on the floor it stands on (bottom centre).
 */
public final class Thing {
    public final int kind;
    public final String id;
    public final int variant;
    public final Species species;
    public float x;
    public float y;
    public float hp;
    public float maxHp;
    public boolean alive = true;
    /** Points of interest that were opened (stay visible but spent). */
    public boolean spent;
    public int hit;
    public int scannedUntil;
    public SceneSprite sprite;
    public int yield;
    public int yieldCount;
    public int ringCount;
    public long seed;
    /** Ring clusters: which rings remain (bit per ring). */
    public int ringMask;

    public Thing(Planet.Placement p, Species species, SceneSprite sprite) {
        this.kind = p.kind();
        this.id = p.id();
        this.variant = p.variant();
        this.species = species;
        this.x = p.x();
        this.y = p.y();
        this.sprite = sprite;
        this.seed = eggsky.core.Rng.mix(p.id().hashCode());
        if (kind == Planet.P_RINGS) {
            ringCount = Math.max(1, Math.min(10, variant));
            ringMask = (1 << ringCount) - 1;
        }
    }

    /** Whether the mining laser can work on it. */
    public boolean mineable() {
        return alive && !spent && switch (kind) {
            case Planet.P_FLORA, Planet.P_MINERAL, Planet.P_CRYSTAL, Planet.P_OXYGEN, Planet.P_SODIUM,
                    Planet.P_SPECIAL, Planet.P_COBALT, Planet.P_GOLD, Planet.P_MONITOR, Planet.P_WRECK -> true;
            default -> false;
        };
    }

    /** Whether the analysis visor can catalogue it. */
    public boolean discoverable() {
        return alive && species != null;
    }

    /** Half-width and height of the hit box above the floor point. */
    public float halfWidth() {
        if (sprite != null) {
            return Math.max(6, sprite.width() / 2f - 2);
        }
        return switch (kind) {
            case Planet.P_MONITOR -> 14;
            case Planet.P_CAPSULE -> 28;
            case Planet.P_GIANT_RING -> 26;
            case Planet.P_STARPOST -> 8;
            case Planet.P_RINGS -> 8 * ringCount;
            default -> 12;
        };
    }

    public float height() {
        if (sprite != null) {
            return Math.max(8, sprite.height() - 2);
        }
        return switch (kind) {
            case Planet.P_MONITOR -> 30;
            case Planet.P_CAPSULE -> 60;
            case Planet.P_GIANT_RING -> 80;
            case Planet.P_STARPOST -> 48;
            case Planet.P_RUINS -> 54;
            case Planet.P_SHRINE -> 60;
            case Planet.P_WRECK -> 50;
            case Planet.P_RINGS -> 40;
            default -> 20;
        };
    }

    /** The centre of the hit box, for aiming. */
    public float centreY() {
        return y - height() / 2;
    }

    /** Screen draw; {@code sx, sy} is the floor point on screen. */
    public void draw(SurfaceMode s, SceneCanvas c, float sx, float sy, long ticks, int tint) {
        Art art = s.game.art;
        SceneDraw style = SceneDraw.plain().withTint(tint);
        if (hit > 0 && (ticks / 2) % 2 == 0) {
            style = style.withFlash(0xFFFFFFFF);
        }
        float shakeX = hit > 0 ? (float) Math.sin(ticks * 2.1) * 1.5f : 0;
        switch (kind) {
            case Planet.P_MONITOR -> {
                SceneSprite box = art.frame("monitor", spent ? 11 : 0);
                if (box != null) {
                    stand(c, box, sx + shakeX, sy, style);
                    if (!spent && (ticks / 4) % 6 != 0) {
                        SceneSprite icon = art.frame("monitor", monitorIcon());
                        if (icon != null) {
                            stand(c, icon, sx + shakeX, sy, style);
                        }
                    }
                }
            }
            case Planet.P_CAPSULE -> {
                SceneSprite cap = art.frame("capsule", spent ? 1 : 0);
                if (cap != null) {
                    stand(c, cap, sx, sy, style);
                }
            }
            case Planet.P_STARPOST -> {
                SceneSprite post = art.frame("starpost", 0);
                if (post != null) {
                    stand(c, post, sx, sy, style);
                }
                SceneSprite ball = art.frame("starpost", spent ? 2 : 1);
                if (ball != null && post != null) {
                    float top = sy - (post.height() - post.originY()) - 2;
                    c.draw(ball, sx, top + ball.originY() - ball.height() + 10, style);
                }
                if (spent && (ticks / 6) % 2 == 0) {
                    c.fill((int) sx - 1, (int) sy - 50, 3, 3, 0xFFFFFF80);
                }
            }
            case Planet.P_GIANT_RING -> {
                if (!spent) {
                    var set = art.set("big_ring");
                    if (set != null && set.frameCount() > 0) {
                        int frames = Math.min(8, set.frameCount());
                        SceneSprite f = set.frame((int) ((ticks / 4) % frames));
                        c.draw(f, sx, sy - 48, style);
                    } else {
                        ring(c, sx, sy - 48, 26, 0xFFFFD040);
                    }
                    for (int i = 0; i < 2; i++) {
                        double a = ticks * 0.1 + i * Math.PI;
                        c.fill((int) (sx + Math.cos(a) * 30), (int) (sy - 48 + Math.sin(a) * 34), 2, 2, 0xFFFFFFFF);
                    }
                }
            }
            case Planet.P_RINGS -> {
                SceneSprite r = art.frame("ring", (int) ((ticks / 6 + seed) % 4));
                for (int i = 0; i < ringCount; i++) {
                    if ((ringMask & (1 << i)) == 0) {
                        continue;
                    }
                    float rx = sx + (i - (ringCount - 1) / 2f) * 16;
                    float ry = sy - 26 - (float) Math.sin(i * 0.8 + ticks * 0.05) * 6 - Math.abs(i - (ringCount - 1) / 2f) * 3;
                    if (r != null) {
                        c.draw(r, rx, ry, style);
                    } else {
                        ring(c, rx, ry, 6, 0xFFFFD040);
                    }
                }
            }
            case Planet.P_RUINS -> drawRuins(s, c, sx, sy, ticks, style);
            case Planet.P_SHRINE -> drawShrine(s, c, sx, sy, ticks, style);
            case Planet.P_WRECK -> drawWreck(s, c, sx + shakeX, sy, ticks, style);
            default -> {
                if (sprite != null) {
                    float scale = 1;
                    if (hit > 0) {
                        scale = 1 + 0.06f * (float) Math.sin(ticks * 1.3);
                    }
                    if (alive && maxHp > 0 && hp < maxHp) {
                        // Shrink as it is mined.
                        scale *= 0.55f + 0.45f * hp / maxHp;
                    }
                    stand(c, sprite, sx + shakeX, sy, style.withScale(scale));
                    if ((kind == Planet.P_CRYSTAL || kind == Planet.P_COBALT) && (ticks + seed) % 50 < 3) {
                        c.fill((int) sx - 1 + (int) (seed % 7) - 3, (int) (sy - sprite.height() * 0.6f), 3, 3, 0xFFFFFFFF);
                    }
                }
            }
        }
    }

    /** The monitor's icon frame in Map_Monitor for its contents. */
    public int monitorIcon() {
        return switch (variant) {
            case 0 -> 4;   // super ring
            case 1 -> 6;   // fire shield
            case 2 -> 7;   // lightning shield
            case 3 -> 9;   // invincibility
            case 4 -> 3;   // Sonic's face: summons him
            default -> 5;  // Robotnik: Chaos Shards
        };
    }

    private static void stand(SceneCanvas c, SceneSprite s, float x, float floorY, SceneDraw style) {
        float bottom = (s.height() - s.originY()) * style.scaleY();
        c.draw(s, x, floorY - bottom, style);
    }

    private static void ring(SceneCanvas c, float x, float y, int r, int colour) {
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            c.fill((int) (x + Math.cos(a) * r), (int) (y + Math.sin(a) * r * 1.2), 2, 2, colour);
        }
    }

    private void drawRuins(SurfaceMode s, SceneCanvas c, float sx, float sy, long ticks, SceneDraw style) {
        // A carved echidna monolith with glowing glyphs.
        int stone = Colour.scale(0xFF8A7F70, (Colour.r(style.tint()) / 255f));
        int dark = Colour.scale(stone, 0.6f);
        int light = Colour.lerp(stone, 0xFFFFFFFF, 0.25f);
        int w = 22;
        int h = 52;
        int x0 = (int) sx - w / 2;
        int y0 = (int) sy - h;
        c.fill(x0 - 6, (int) sy - 8, w + 12, 8, dark);
        c.fill(x0 - 4, (int) sy - 10, w + 8, 2, light);
        c.fill(x0, y0 + 4, w, h - 10, stone);
        c.fill(x0 + 2, y0, w - 4, 4, stone);
        c.fill(x0, y0 + 4, 2, h - 10, light);
        c.fill(x0 + w - 2, y0 + 4, 2, h - 10, dark);
        int glow = spent ? 0xFF506070 : Colour.lerp(0xFF30E0FF, 0xFF80FFE0, (float) (0.5 + 0.5 * Math.sin(ticks * 0.08)));
        for (int i = 0; i < 5; i++) {
            long h2 = eggsky.core.Rng.mix(seed + i);
            int gx = x0 + 5 + (int) (h2 & 7);
            int gy = y0 + 8 + i * 7;
            c.fill(gx, gy, 3 + (int) ((h2 >>> 8) & 7), 2, glow);
            c.fill(gx + 2, gy + 2, 2, 2, glow);
        }
        if (!spent) {
            c.fill(x0 + w / 2 - 3, y0 - 8 - (int) (Math.sin(ticks * 0.1) * 2), 6, 6, Colour.alpha(glow, 0x90));
        }
    }

    private void drawShrine(SurfaceMode s, SceneCanvas c, float sx, float sy, long ticks, SceneDraw style) {
        // A pedestal with a floating Chaos Emerald in a column of light.
        c.fill((int) sx - 18, (int) sy - 10, 36, 10, 0xFF6A6280);
        c.fill((int) sx - 14, (int) sy - 14, 28, 4, 0xFF9A90B8);
        c.fill((int) sx - 10, (int) sy - 30, 20, 16, 0xFF7A7098);
        if (!spent) {
            int beam = Colour.alpha(0xFF80FFE0, 40 + (int) (30 * Math.sin(ticks * 0.1)));
            c.fill((int) sx - 8, 0, 16, (int) sy - 30, beam);
            int emerald = s.emeraldColour(s.game.player.emeraldCount());
            float ey = sy - 50 + (float) Math.sin(ticks * 0.07) * 4;
            SceneSprite gem = s.emeraldSprite(s.game.player.emeraldCount());
            if (gem != null) {
                c.draw(gem, sx, ey, SceneDraw.plain().withScale(1.5f));
            } else {
                c.fill((int) sx - 6, (int) ey - 6, 12, 12, emerald);
            }
            if ((ticks / 5) % 3 == 0) {
                c.fill((int) sx + (int) (Math.sin(ticks) * 10), (int) ey - 10, 2, 2, 0xFFFFFFFF);
            }
        }
    }

    private void drawWreck(SurfaceMode s, SceneCanvas c, float sx, float sy, long ticks, SceneDraw style) {
        SceneSprite hulk = s.wreckSprite();
        if (hulk != null) {
            c.draw(hulk, sx, sy - 20, style.withTint(Colour.scale(style.tint(), 0.55f)).withFlipX(seed % 2 == 0));
        } else {
            c.fill((int) sx - 30, (int) sy - 30, 60, 30, 0xFF505868);
        }
        if (alive && !spent && ticks % 9 == 0) {
            s.particles.add(x + (float) Math.sin(ticks) * 10, y - 40, 0.1f, -0.4f, -0.002f, 4, 0xC0606060, 80, false);
        }
    }

    /** What breaking it yields, set up when it is created. */
    public void setYield(int item, int count, float health) {
        this.yield = item;
        this.yieldCount = count;
        this.hp = health;
        this.maxHp = health;
    }

    public static int defaultHealth(int kind) {
        return switch (kind) {
            case Planet.P_FLORA, Planet.P_OXYGEN, Planet.P_SODIUM, Planet.P_SPECIAL -> 40;
            case Planet.P_MINERAL -> 90;
            case Planet.P_CRYSTAL, Planet.P_COBALT -> 70;
            case Planet.P_GOLD -> 120;
            case Planet.P_MONITOR -> 25;
            case Planet.P_WRECK -> 400;
            default -> 1;
        };
    }

    /** The item the kind yields by default. */
    public static int defaultYield(int kind, Planet planet, int variant) {
        return switch (kind) {
            case Planet.P_OXYGEN -> Catalog.OXYGEN;
            case Planet.P_SODIUM -> Catalog.SODIUM;
            case Planet.P_SPECIAL -> planet.spec.special;
            case Planet.P_CRYSTAL -> variant == 0 ? Catalog.DIHYDROGEN : planet.spec.starMetal;
            case Planet.P_COBALT -> Catalog.COBALT;
            case Planet.P_GOLD -> Catalog.GOLD;
            case Planet.P_WRECK -> Catalog.BADNIK_SCRAP;
            default -> 0;
        };
    }
}
