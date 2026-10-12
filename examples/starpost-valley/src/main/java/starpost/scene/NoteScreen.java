package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.CompactFont;
import java.util.List;

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
        // Paper takes ink: the compact font has no outline (the menu font's black outline clots on it).
        int w = 300, x = (canvas.width() - w) / 2;
        List<String> lines = inkWrap(body, w - 28);
        int h = 44 + lines.size() * 11 + 18, y = (canvas.height() - h) / 2;
        canvas.fill(x, y, w, h, 0xFFFFDBB6);
        canvas.fill(x, y, w, 3, 0xFFB66D24);
        canvas.fill(x, y + h - 3, w, 3, 0xFF924900);
        canvas.fill(x, y, 3, h, 0xFFB66D24);
        canvas.fill(x + w - 3, y, 3, h, 0xFF924900);
        CompactFont.draw(canvas, heading, x + 14, y + 12, 2, 0xFF924900);
        for (int i = 0; i < lines.size(); i++) {
            CompactFont.draw(canvas, lines.get(i), x + 14, y + 36 + i * 11, 1, 0xFF492400);
        }
        if ((shell.ticks - opened) / 30 % 2 == 0) {
            CompactFont.draw(canvas, ">", x + w - 18, y + h - 14, 1, 0xFF924900);
        }
    }

    /** Lines of the compact font no wider than {@code pixels}. */
    private static List<String> inkWrap(String text, int pixels) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (CompactFont.width(next, 1) > pixels && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        out.add(line.toString());
        return out;
    }
}
