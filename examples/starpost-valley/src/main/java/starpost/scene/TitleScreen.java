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
        return new String[] {"NEW GAME", "CONTINUE", "OPTIONS", "PLAY SONIC 3 & KNUCKLES"};
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
                case 2 -> shell.push(new OptionsMenu(false));
                default -> shell.ctx.exitToGameTitle();
            }
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = canvas.width(), h = canvas.height();
        canvas.drawBackdrop(shell.art.season(0).backdrop(0), 0, 0, w, h, 0, shell.ticks * 1.5, shell.ticks);
        // Green Hill's own ground scrolling under Sonic, as on Sonic 1's title.
        var look = shell.art.season(0);
        int scroll = (int) (shell.ticks * 3 % 256);
        for (int x = -scroll; x < w; x += 256) {
            canvas.drawRegion(look.block(60), 0, 180, 256, 76, x, h - 52, 256, 76, SceneDraw.plain());
        }
        SceneSpriteSet sonic = shell.art.farmer("sonic");
        SceneSprite pose = run.pose(sonic);
        float x = w * 0.3f + (float) Math.sin(shell.ticks / 50.0) * 30;
        canvas.draw(pose, x, h - 40 - (pose.height() - pose.originY()), SceneDraw.plain());
        // The name in Sonic 3 & Knuckles' title-card lettering, dropping in.
        int drop = (int) Math.max(0, 60 - shell.ticks * 3);
        Text.panel(canvas, w / 2 - 92, 22 - drop, 184, 66);
        shell.art.cardFont.centred(canvas, "STARPOST", 30 - drop, SceneDraw.plain());
        shell.art.cardFont.centred(canvas, "VALLEY", 58 - drop, SceneDraw.plain());
        Text.centred(canvas, "THE VALLEY AFTER THE CREDITS", 96, Text.YELLOW);
        String[] options = options();
        for (int i = 0; i < options.length; i++) {
            boolean disabled = i == 1 && !hasSave;
            String label = (i == cursor ? "> " : "  ") + options[i];
            Text.centred(canvas, label, 116 + i * 14, disabled ? 0xFF6D6D6D : i == cursor ? Text.YELLOW : Text.WHITE);
        }
    }
}
