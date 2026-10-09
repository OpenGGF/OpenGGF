package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import java.util.List;
import starpost.ui.Text;

/** A letter on screen: a parchment panel, its lines, and confirm to put it away. */
final class NoteScreen implements Screen {
    private final String heading;
    private final String body;
    private final Runnable closed;
    private long opened;

    NoteScreen(String heading, String body, Runnable closed) {
        this.heading = heading;
        this.body = body;
        this.closed = closed;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void enter(Shell shell) {
        opened = shell.ticks;
    }

    @Override
    public void update(Shell shell) {
        if (shell.ticks - opened > 20 && (shell.in.confirm || shell.in.back)) {
            shell.pop();
            shell.sfx(Sfx.SWITCH);
            if (closed != null) {
                closed.run();
            }
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = 300, x = (canvas.width() - w) / 2;
        List<String> lines = Text.wrap(canvas, body, w - 28);
        int h = 40 + lines.size() * 12 + 18, y = (canvas.height() - h) / 2;
        canvas.fill(x, y, w, h, 0xFFFFDBB6);
        canvas.fill(x, y, w, 3, 0xFFB66D24);
        canvas.fill(x, y + h - 3, w, 3, 0xFF924900);
        canvas.fill(x, y, 3, h, 0xFFB66D24);
        canvas.fill(x + w - 3, y, 3, h, 0xFF924900);
        canvas.text(heading, x + 14, y + 12, 0xFF924900);
        for (int i = 0; i < lines.size(); i++) {
            canvas.text(lines.get(i), x + 14, y + 30 + i * 12, 0xFF492400);
        }
        if ((shell.ticks - opened) / 30 % 2 == 0) {
            canvas.text(">", x + w - 20, y + h - 16, 0xFF924900);
        }
    }
}
