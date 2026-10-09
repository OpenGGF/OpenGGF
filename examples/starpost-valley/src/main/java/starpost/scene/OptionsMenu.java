package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import starpost.ui.Text;

/** Options: music, day length, Momentum or stamina, and (in play) quitting to the title. */
final class OptionsMenu implements Screen {
    private final boolean inGame;
    private int cursor;

    OptionsMenu(boolean inGame) {
        this.inGame = inGame;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    private int rows() {
        return inGame ? 4 : 3;
    }

    @Override
    public void update(Shell shell) {
        Settings s = shell.settings;
        if (shell.in.back || shell.in.menu && !shell.in.confirm) {
            s.save(shell.ctx.storage());
            shell.apply();
            shell.pop();
            return;
        }
        if (shell.in.downPressed) {
            cursor = (cursor + 1) % rows();
        } else if (shell.in.upPressed) {
            cursor = (cursor + rows() - 1) % rows();
        }
        boolean change = shell.in.confirm || shell.in.leftPressed || shell.in.rightPressed;
        if (!change) {
            return;
        }
        shell.sfx(Sfx.SWITCH);
        switch (cursor) {
            case 0 -> s.music = !s.music;
            case 1 -> s.dayMinutes = s.dayMinutes == 14 ? 20 : s.dayMinutes == 20 ? 28 : 14;
            case 2 -> s.stamina = !s.stamina;
            default -> {
                if (shell.in.confirm) {
                    shell.pop();
                    shell.push(new ConfirmMenu("QUIT? TODAY IS LOST SINCE MORNING.", () -> shell.go(new TitleScreen())));
                }
            }
        }
        shell.apply();
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Settings s = shell.settings;
        int w = 280, h = 40 + rows() * 18 + 24, x = (canvas.width() - w) / 2, y = 50;
        Text.panel(canvas, x, y, w, h);
        Text.shadow(canvas, "OPTIONS", x + 10, y + 8, Text.YELLOW);
        String[] labels = {"MUSIC", "DAY LENGTH", "ENERGY", "QUIT TO TITLE"};
        String[] values = {s.music ? "ON" : "OFF", s.dayMinutes + " MIN", s.stamina ? "STAMINA" : "MOMENTUM", ""};
        for (int i = 0; i < rows(); i++) {
            int ry = y + 28 + i * 18;
            int colour = i == cursor ? Text.YELLOW : Text.WHITE;
            Text.shadow(canvas, (i == cursor ? "> " : "  ") + labels[i], x + 12, ry, colour);
            Text.right(canvas, values[i], x + w - 12, ry, colour);
        }
        String hint = cursor == 2 ? (s.stamina ? "ONLY FOOD AND SLEEP RESTORE ENERGY." : "LAPS, SPRINGS AND RINGS RESTORE IT.")
                : cursor == 1 ? "REAL MINUTES FROM 6AM TO 2AM." : "";
        Text.note(canvas, hint, x + 10, y + h - 18, w - 20, Text.GREY);
    }
}
