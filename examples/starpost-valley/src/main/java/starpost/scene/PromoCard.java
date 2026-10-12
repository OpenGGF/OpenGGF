package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;

/**
 * A chapter card for promotional captures only ({@code jump=card_...}; players never see it): the
 * morning card's S3K look, the red banner dropping in and a line or two of title-card lettering
 * sliding in from the right, then it lifts off whatever screen it covered.
 */
final class PromoCard implements Screen {
    private static final int TICKS = 100;
    private final String first;
    private final String second;
    private long started;

    PromoCard(String first, String second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void enter(Shell shell) {
        started = shell.ticks;
    }

    @Override
    public void update(Shell shell) {
        if (shell.ticks - started >= TICKS) {
            shell.pop();
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = canvas.width();
        long age = shell.ticks - started;
        canvas.fill(0, 0, w, canvas.height(), 0xFF000000);
        int in = (int) Math.max(0, 320 - age * 16);
        if (shell.art.cardBanner != null) {
            canvas.draw(shell.art.cardBanner, w / 2f - 140, -in * 0.7f, SceneDraw.plain());
        }
        var font = shell.art.cardFont;
        font.draw(canvas, first, w / 2f - 60 + in, second.isEmpty() ? 92 : 76, SceneDraw.plain());
        if (!second.isEmpty()) {
            font.draw(canvas, second, w / 2f - 60 + in * 1.4f, 108, SceneDraw.plain());
        }
    }
}
