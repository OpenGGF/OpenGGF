package slaytherobotnik.scene;

import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import slaytherobotnik.art.Art;
import slaytherobotnik.content.Content;
import slaytherobotnik.run.Run;
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

    /**
     * Starts a run and jumps straight into one room, for screenshot tools and debugging:
     * {@code "sonic:42:fight:hcz:big_shaker"} or {@code "tails:7:event:event:slot_machine"}
     * (character, seed, then {@code fight} or {@code event} and the id).
     */
    public void debugJump(String command) {
        String[] parts = command.split(":", 4);
        Run run = Run.start(shell.catalog, parts[0], Long.parseLong(parts[1]));
        shell.attach(run);
        if (parts[2].equals("fight")) {
            run.enterFight(parts[3]);
        } else {
            run.state().setPosition(shell.catalog.event(parts[3]).acts().stream().min(Integer::compare).orElse(1),
                    run.state().floor(), run.state().actFloor(), run.state().nodeX());
            run.enterEvent(parts[3]);
        }
        shell.goNow(new RunScreen());
    }

    /** The shell, for tests that drive the scene directly. */
    public Shell shell() {
        return shell;
    }
}
