package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import java.util.function.Supplier;
import starpost.ui.Text;

/**
 * The credits, to Sonic 1's credits music over Green Hill's sky, then the valley's second year
 * begins (the game never ends; the hero keeps coming back).
 */
final class CreditsScreen implements Screen {
    private final int score;
    private final boolean egg;
    private final Supplier<Screen> next;
    private long opened;

    CreditsScreen(int score, boolean egg, Supplier<Screen> next) {
        this.score = score;
        this.egg = egg;
        this.next = next;
    }

    private String[] lines() {
        return new String[] {
            "STARPOST VALLEY", "",
            "THE VALLEY AFTER THE CREDITS", "", "",
            "A FARMING STORY", "IN GREEN HILL", "", "",
            "EVERY TILE, SPRITE AND NOTE", "COMES FROM YOUR OWN", "SONIC 1 AND SONIC 3 & KNUCKLES", "", "",
            "BUILT AS AN OPENGGF", "EXAMPLE MOD", "", "",
            egg ? "ROBOTNIK THANKS YOU" : "THE ANIMALS THANK YOU", egg ? "FOR YOUR BUSINESS." : "FOR A HOME.", "", "",
            "YEAR TWO BEGINS..."
        };
    }

    @Override
    public void enter(Shell shell) {
        opened = shell.ticks;
        shell.music.want("s1", Music.S1_CREDITS);
    }

    @Override
    public void update(Shell shell) {
        long age = shell.ticks - opened;
        if (age > lines().length * 22 + 360 || shell.in.menu && age > 60) {
            shell.go(next.get());
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = canvas.width(), h = canvas.height();
        long age = shell.ticks - opened;
        canvas.drawBackdrop(shell.art.season((int) (age / 400 % 4)).backdrop(0), 0, 0, w, h, 0, age * 0.8, shell.ticks);
        canvas.fill(0, 0, w, h, 0x60000018);
        String[] lines = lines();
        float scroll = h + 10 - age * 0.5f;
        for (int i = 0; i < lines.length; i++) {
            float y = scroll + i * 22;
            if (y < -24 || y > h) {
                continue;
            }
            if (i == 0) {
                shell.art.cardFont.centred(canvas, lines[i], Math.round(y), SceneDraw.plain());
            } else {
                Text.centred(canvas, lines[i], Math.round(y), i % 5 == 2 ? Text.YELLOW : Text.WHITE);
            }
        }
        if (score >= 4 && !egg) {
            Text.right(canvas, "PERFECT", w - 10, h - 16, Text.YELLOW);
        }
    }
}
