package eggsky.surface;

import com.openggf.mods.scene.SceneCanvas;
import eggsky.Game;
import eggsky.core.Colour;
import eggsky.core.Rng;
import eggsky.core.Sound;
import eggsky.world.Biome;
import eggsky.world.PlanetSpec;

/**
 * A planet's sky: the day-night cycle (a multiply tint over the world, stars at night) and
 * storms that build, rage and pass. Storms darken the sky, fill the screen with the biome's
 * weather (rain, snow, embers, sand, spores, acid, ash or electrical sparks), drain hazard
 * protection faster and grow Storm Crystals.
 */
public final class Weather {
    private static final int FLAKES = 160;

    private final PlanetSpec spec;
    private final Rng rng;
    /** 0-1 through a day; 0.25 is noon. */
    public float dayPhase;
    /** 0 calm to 1 full storm. */
    public float storm;
    public boolean stormActive;
    private int timer;
    private boolean warned;
    public int lightning;
    private final float[] fx = new float[FLAKES];
    private final float[] fy = new float[FLAKES];
    private final float[] fs = new float[FLAKES];

    public Weather(PlanetSpec spec, long seed) {
        this.spec = spec;
        this.rng = new Rng(seed);
        this.dayPhase = 0.18f + rng.nextFloat() * 0.1f;
        this.timer = calmLength();
        for (int i = 0; i < FLAKES; i++) {
            fx[i] = rng.range(0f, 400f);
            fy[i] = rng.range(0f, 224f);
            fs[i] = rng.range(0.5f, 1.5f);
        }
    }

    private int calmLength() {
        return switch (spec.storms) {
            case 0 -> 60 * 60 * 30;
            case 1 -> rng.range(60 * 150, 60 * 300);
            case 2 -> rng.range(60 * 70, 60 * 160);
            default -> rng.range(60 * 25, 60 * 60);
        };
    }

    public void update(Game g) {
        dayPhase += 1f / (spec.dayMinutes * 3600f);
        if (dayPhase >= 1) {
            dayPhase -= 1;
        }
        timer--;
        if (!stormActive && timer < 60 * 10 && !warned && spec.storms > 0) {
            warned = true;
            g.toast("STORM APPROACHING", 0xFFFFC040);
            g.sound.sfx(Sound.ALARM, 60);
        }
        if (timer <= 0) {
            stormActive = !stormActive;
            warned = false;
            timer = stormActive ? rng.range(60 * 35, 60 * 80) + spec.storms * 600 : calmLength();
            if (stormActive) {
                g.toast(Biome.weatherName(spec.biome.weather()).toUpperCase() + "!", 0xFFFF7050);
            } else {
                g.toast("THE STORM HAS PASSED", 0xFF80FF90);
            }
        }
        storm += ((stormActive ? 1 : 0) - storm) * 0.01f;
        if (lightning > 0) {
            lightning--;
        }
        int w = spec.biome.weather();
        if (storm > 0.6f && (w == Biome.WEATHER_RAIN || w == Biome.WEATHER_SPARKS || w == Biome.WEATHER_ACID)
                && rng.chance(0.004 * storm)) {
            lightning = 10;
            g.sound.sfx(Sound.LIGHTNING, 30);
        }
    }

    /** Daylight 0 (midnight) to 1 (noon). */
    public float daylight() {
        double s = Math.sin((dayPhase) * Math.PI * 2);
        return (float) Math.max(0, Math.min(1, 0.5 + s * 0.8));
    }

    /** The multiply tint for the world this frame. */
    public int tint() {
        float day = daylight();
        int night = 0xFF4858A8;
        int dusk = 0xFFFFB090;
        int c;
        if (day < 0.5f) {
            c = Colour.lerp(night, dusk, day * 2);
        } else {
            c = Colour.lerp(dusk, 0xFFFFFFFF, (day - 0.5f) * 2);
        }
        if (storm > 0) {
            c = Colour.lerp(c, Colour.scale(c, 0.62f), storm);
        }
        if (lightning > 6) {
            c = 0xFFFFFFFF;
        }
        return c | 0xFF000000;
    }

    /** Stars in the sky while dark. */
    public void drawStars(SceneCanvas c, Game g, float scroll) {
        float day = daylight();
        if (day > 0.45f) {
            return;
        }
        int alpha = Math.round((0.45f - day) / 0.45f * 255 * (1 - storm));
        for (int i = 0; i < 60; i++) {
            long h = Rng.mix(i * 31L + spec.seed);
            int x = (int) Math.floorMod((long) ((h & 0x3FF) - scroll * 0.02f), (long) g.width);
            int y = (int) ((h >>> 10) % 110);
            boolean twinkle = ((g.ticks + i * 7) / 20) % 9 == 0;
            c.fill(x, y, 1, 1, Colour.alpha(twinkle ? 0xFF8090FF : 0xFFFFFFFF, alpha));
        }
    }

    /** The screen-space weather layer. */
    public void drawOverlay(SceneCanvas c, Game g, float camDx, float camDy, boolean sheltered) {
        int w = spec.biome.weather();
        float intensity = storm;
        if (w == Biome.WEATHER_SNOW || w == Biome.WEATHER_SPORES || w == Biome.WEATHER_EMBERS) {
            intensity = Math.max(intensity, 0.15f);
        }
        if (w == Biome.WEATHER_NONE || intensity < 0.02f) {
            return;
        }
        if (sheltered) {
            intensity *= 0.25f;
        }
        int n = Math.round(FLAKES * intensity);
        float wind = storm * 3;
        for (int i = 0; i < n; i++) {
            float vx;
            float vy;
            int col;
            int len;
            switch (w) {
                case Biome.WEATHER_RAIN, Biome.WEATHER_ACID -> {
                    vx = -1 - wind;
                    vy = 7;
                    col = w == Biome.WEATHER_ACID ? 0xB060FF60 : 0xA0B0C8FF;
                    len = 6;
                }
                case Biome.WEATHER_SNOW -> {
                    vx = -0.5f - wind * 0.6f + (float) Math.sin((g.ticks + i * 17) * 0.05) * 0.5f;
                    vy = 1.2f * fs[i];
                    col = 0xE0FFFFFF;
                    len = 0;
                }
                case Biome.WEATHER_EMBERS -> {
                    vx = 0.4f - wind * 0.3f;
                    vy = -1.2f * fs[i];
                    col = (i % 3 == 0) ? 0xF0FFE060 : 0xE0FF6020;
                    len = 0;
                }
                case Biome.WEATHER_SAND -> {
                    vx = -4 - wind * 2;
                    vy = 0.4f;
                    col = 0x90E0C080;
                    len = 0;
                }
                case Biome.WEATHER_SPORES -> {
                    vx = (float) Math.sin((g.ticks + i * 13) * 0.02) * 0.6f;
                    vy = -0.3f * fs[i];
                    col = 0xC0C0FF60;
                    len = 0;
                }
                case Biome.WEATHER_ASH -> {
                    vx = -1.2f - wind;
                    vy = 1.6f * fs[i];
                    col = 0xC0808080;
                    len = 0;
                }
                default -> {
                    vx = 0;
                    vy = 0;
                    col = 0xF080E0FF;
                    len = -1;
                }
            }
            fx[i] += vx - camDx * 0.6f;
            fy[i] += vy - camDy * 0.6f;
            fx[i] = ((fx[i] % g.width) + g.width) % g.width;
            fy[i] = ((fy[i] % g.height) + g.height) % g.height;
            int x = (int) fx[i];
            int y = (int) fy[i];
            if (len > 0) {
                for (int k = 0; k < len; k += 2) {
                    c.fill(x + (int) (vx * k / 7), y + k, 1, 2, col);
                }
            } else if (len == 0) {
                int s = fs[i] > 1.2f ? 2 : 1;
                c.fill(x, y, s, s, col);
                if (w == Biome.WEATHER_SAND) {
                    c.fill(x + 1, y, 6, 1, Colour.fade(col, 0.5f));
                }
            } else if ((g.ticks + i) % 7 == 0) {
                // Sparks: brief crackles.
                c.fill(x, y, 2, 1, col);
                c.fill(x + 2, y + 1, 2, 1, col);
            }
        }
        if (storm > 0.05f) {
            int haze = switch (w) {
                case Biome.WEATHER_SAND -> 0xC09060;
                case Biome.WEATHER_EMBERS, Biome.WEATHER_ASH -> 0x803020;
                case Biome.WEATHER_ACID, Biome.WEATHER_SPORES -> 0x406020;
                case Biome.WEATHER_SNOW -> 0xD0E0FF;
                default -> 0x202840;
            };
            c.fill(0, 0, g.width, g.height, (Math.round(storm * (sheltered ? 20 : 70)) << 24) | haze);
        }
        if (lightning > 6) {
            c.fill(0, 0, g.width, g.height, 0x60FFFFFF);
        }
    }

    public String timeOfDay() {
        float p = dayPhase;
        if (p < 0.05f || p > 0.95f) {
            return "DAWN";
        }
        if (p < 0.45f) {
            return "DAY";
        }
        if (p < 0.55f) {
            return "DUSK";
        }
        return "NIGHT";
    }
}
