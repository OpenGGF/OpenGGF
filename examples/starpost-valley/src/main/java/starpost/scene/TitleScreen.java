package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import starpost.art.Anim;
import starpost.core.Game;
import starpost.ui.Text;

/** The title: Green Hill's parallax at dawn, the name, and New Game / Continue / the stock game. */
final class TitleScreen implements Screen {
    private int cursor;
    private boolean hasSave;
    private final Anim run = new Anim();

    @Override
    public void enter(Shell shell) {
        hasSave = shell.load() != null;
        cursor = hasSave ? 1 : 0;
        shell.music.want("s1", Music.S1_GHZ);
    }

    private String[] options() {
        return new String[] {"NEW GAME", "CONTINUE", "PLAY SONIC 3 & KNUCKLES"};
    }

    @Override
    public void update(Shell shell) {
        run.set(Anim.RUN, 1);
        run.tick();
        int n = options().length;
        if (shell.in.downPressed) {
            cursor = (cursor + 1) % n;
            if (cursor == 1 && !hasSave) {
                cursor = 2;
            }
            shell.sfx(Sfx.SWITCH);
        } else if (shell.in.upPressed) {
            cursor = (cursor + n - 1) % n;
            if (cursor == 1 && !hasSave) {
                cursor = 0;
            }
            shell.sfx(Sfx.SWITCH);
        } else if (shell.in.confirm || shell.in.menu) {
            switch (cursor) {
                case 0 -> shell.push(new FarmerSelect());
                case 1 -> {
                    Game game = shell.load();
                    if (game != null) {
                        shell.game = game;
                        shell.sfx(Sfx.STARPOST);
                        shell.go(new PlayScreen(shell));
                    }
                }
                default -> shell.ctx.exitToGameTitle();
            }
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = canvas.width(), h = canvas.height();
        canvas.drawBackdrop(shell.art.season(0).backdrop(0), 0, 0, w, h, 0, shell.ticks * 1.5, shell.ticks);
        // Sonic runs across the bottom, as on Sonic 1's title.
        SceneSpriteSet sonic = shell.art.farmer("sonic");
        SceneSprite pose = run.pose(sonic);
        float x = (shell.ticks * 3) % (w + 120) - 60;
        canvas.fill(0, 176, w, h - 176, 0xFF49B600);
        canvas.fill(0, 176, w, 3, 0xFF92FF00);
        canvas.draw(pose, x, 176 - (pose.height() - pose.originY()), SceneDraw.plain());
        Text.panel(canvas, w / 2 - 110, 36, 220, 42);
        Text.centred(canvas, "STARPOST VALLEY", 46, Text.YELLOW);
        Text.centred(canvas, "THE VALLEY AFTER THE CREDITS", 60, Text.WHITE);
        String[] options = options();
        for (int i = 0; i < options.length; i++) {
            boolean disabled = i == 1 && !hasSave;
            String label = (i == cursor ? "> " : "  ") + options[i];
            Text.centred(canvas, label, 104 + i * 16, disabled ? 0xFF6D6D6D : i == cursor ? Text.YELLOW : Text.WHITE);
        }
    }
}
