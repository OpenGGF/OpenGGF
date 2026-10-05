package slaytherobotnik.scene;

import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import slaytherobotnik.art.Art;
import slaytherobotnik.content.Content;
import slaytherobotnik.ui.SmallFont;

/**
 * The startup scene: owns the {@link Shell} and forwards the engine's calls to it. The
 * assets arrive as bytes read during registration (mod files are only open then).
 */
public final class SlayScene implements ModScene {
    private final byte[] fontText;
    private final byte[] iconText;
    private Shell shell;

    public SlayScene(byte[] fontText, byte[] iconText) {
        this.fontText = fontText;
        this.iconText = iconText;
    }

    @Override
    public void enter(SceneContext ctx) {
        SmallFont font = new SmallFont(ctx.art(), fontText);
        Art art = new Art(ctx, iconText);
        shell = new Shell(ctx, font, art, Content.build());
        shell.goNow(new TitleScreen());
    }

    @Override
    public void update(SceneContext ctx) {
        shell.update();
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        shell.draw(canvas);
    }

    @Override
    public void exit(SceneContext ctx) {
        if (shell != null) {
            shell.profile.save(ctx.storage());
        }
    }

    /** The shell, for tests that drive the scene directly. */
    public Shell shell() {
        return shell;
    }
}
