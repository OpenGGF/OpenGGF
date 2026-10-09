package starpost.scene;

import com.openggf.mods.scene.DebuggableScene;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneRomArt;
import starpost.art.Art;
import starpost.core.Catalog;
import starpost.ui.Text;

/**
 * Starpost Valley's startup scene. It needs the Sonic 3 &amp; Knuckles base game and the
 * player's Sonic 1 ROM (Green Hill); without Sonic 1 it explains what is missing instead.
 * Debug commands for the capture tool are documented on {@link Debug}.
 */
public final class StarpostScene implements ModScene, DebuggableScene {
    private Shell shell;
    private final starpost.realtown.TownSession town;
    private PlayScreen actPlay;

    public StarpostScene() { this(null); }
    public StarpostScene(starpost.realtown.TownSession town) { this.town = town; }

    /** E1 owner calls this immediately before SceneContext.startAct. */
    public starpost.realtown.TownSession prepareTownAct() {
        if (town == null || !(shell.screen() instanceof PlayScreen play))
            throw new IllegalStateException("Town launch requires the live play screen");
        actPlay = play;
        starpost.realtown.TownBridge.prepare(town, shell, play);
        return town;
    }

    /** E1 owner calls this after returning to the suspended scene, once per exit. */
    public void resumeTownAct() {
        if (town == null || actPlay == null) throw new IllegalStateException("No suspended town act");
        starpost.realtown.TownBridge.resume(shell, actPlay, town.consumeHandBack());
    }
    private String failure;

    @Override
    public void enter(SceneContext ctx) {
        SceneRomArt s1 = ctx.art().rom("s1");
        SceneRomArt s3k = ctx.art().rom();
        if (s1 == null) {
            failure = "STARPOST VALLEY NEEDS YOUR SONIC 1 ROM";
            return;
        }
        if (s3k == null) {
            failure = "SONIC 3 & KNUCKLES ART IS UNAVAILABLE";
            return;
        }
        Art art;
        try {
            art = new Art(s1, s3k);
        } catch (RuntimeException e) {
            failure = "GREEN HILL COULD NOT BE LOADED";
            return;
        }
        shell = new Shell(ctx, art, new Catalog());
        shell.goNow(new TitleScreen());
    }

    @Override
    public void update(SceneContext ctx) {
        if (failure != null) {
            if (ctx.buttonPressed(SceneButtons.START)) {
                ctx.exitToGameTitle();
            }
            return;
        }
        shell.update();
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        if (failure != null) {
            canvas.clear(0x000000);
            Text.centred(canvas, failure, 96, Text.WHITE);
            Text.centred(canvas, "SET IT UP AS FOR THE STOCK GAME, THEN RESTART.", 112, Text.GREY);
            Text.centred(canvas, "START: THE STOCK TITLE SCREEN", 136, Text.GREY);
            return;
        }
        shell.draw(canvas);
    }

    @Override
    public void exit(SceneContext ctx) {
        if (shell != null) {
            shell.music.stop();
        }
    }

    @Override
    public boolean debugJump(String command) {
        return shell != null && Debug.apply(shell, command);
    }
}
