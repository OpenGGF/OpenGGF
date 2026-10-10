package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import starpost.core.Game;

/** Shared weather presentation; repeated/skipped draws never advance rules or play sounds. */
public final class WorldWeather {
    private WorldWeather() {}
    public static void draw(Game game, long ticks, SceneCanvas canvas) {
        if (game.raining) drawRain(ticks, canvas, game.weather == Game.STORM);
        else if (game.weather == Game.SNOW) drawSnow(ticks, canvas);
        if (game.aurora && game.calendar.light() == 2) drawAurora(ticks, canvas);
    }

    private static void drawRain(long ticks, SceneCanvas canvas, boolean storm) {
        long seed = ticks * 7;
        int drops = storm ? 140 : 70;
        for (int i = 0; i < drops; i++) {
            int x = (int) ((i * 97 + seed * (storm ? 5 : 3)) % (canvas.width() + 40)) - 20;
            int y = (int) ((i * 53 + seed * 9) % (canvas.height() + 20)) - 10;
            canvas.fill(x, y, 1, storm ? 8 : 6, 0x806DB6FF);
        }
        canvas.fill(0, 0, canvas.width(), canvas.height(), storm ? 0x38000820 : 0x20001848);
        // Lightning: a flash every few seconds, and the shield's crackle with it.
        if (storm) {
            long phase = ticks % 400;
            if (phase < 4 || phase >= 10 && phase < 12) {
                canvas.fill(0, 0, canvas.width(), canvas.height(), 0x90FFFFFF);
            }
        }
    }

    private static void drawSnow(long ticks, SceneCanvas canvas) {
        for (int i = 0; i < 60; i++) {
            float x = (i * 131 + ticks * 0.6f + (float) Math.sin((ticks + i * 40) / 30.0) * 8) % (canvas.width() + 20) - 10;
            float y = (i * 71 + ticks * (0.7f + i % 3 * 0.25f)) % (canvas.height() + 10) - 5;
            canvas.fill(Math.round(x), Math.round(y), i % 4 == 0 ? 2 : 1, i % 4 == 0 ? 2 : 1, 0xE0FFFFFF);
        }
    }

    /** The Emerald Aurora: green and cyan curtains rippling over the night sky. */
    private static void drawAurora(long ticks, SceneCanvas canvas) {
        int w = canvas.width();
        for (int x = 0; x < w; x += 4) {
            double wave = Math.sin((x + ticks * 0.8) / 37.0) + Math.sin((x - ticks * 0.5) / 23.0);
            int top = 18 + (int) (wave * 8);
            int height = 40 + (int) (Math.sin((x + ticks) / 51.0) * 14);
            int colour = (x / 4 % 3 == 0 ? 0x5049FF92 : 0x4024DBDB);
            canvas.fill(x, top, 4, height, colour);
        }
    }

}
