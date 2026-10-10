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
    private boolean returnToAct;
    private int returnX, returnY, healthRings;

    private void launchTown(PlayScreen play) {
        boolean fresh = !returnToAct || actPlay != play;
        prepareTownAct();
        if (fresh) {
            returnX = starpost.realvalley.TownTerrain.entryX();
            returnY = starpost.realvalley.TownTerrain.entryY(town.layout(), shell.game.farmer);
            healthRings = 0;
        }
        returnToAct = false;
        shell.in.consume();
        shell.music.parkForAct();
        shell.ctx.startAct(new com.openggf.mods.scene.ActLaunch(
            new com.openggf.game.ZoneKey.Mod("starpost-valley", "valley"), 0,
            com.openggf.game.CharacterKey.parsePersisted(shell.game.farmer), java.util.List.of(),
            java.util.OptionalInt.of(returnX), java.util.OptionalInt.of(returnY), healthRings, java.util.Map.of()));
    }

    @Override public void resume(SceneContext context, com.openggf.mods.scene.ActResult result) {
        if (town == null || actPlay == null) throw new IllegalStateException("No suspended town act");
        town.consumeHandBack();
        healthRings = result.rings();
        var state = result.state();
        String place = state.getOrDefault("town.place", "farm_gate");
        returnX = Integer.parseInt(state.getOrDefault("town.returnX", Integer.toString(returnX)));
        returnY = Integer.parseInt(state.getOrDefault("town.returnY", Integer.toString(returnY)));
        starpost.realtown.TownBridge.resume(shell, actPlay,
            new starpost.realtown.TownSession.HandBack(place, state.get("town.event"), returnX, returnY));
        returnToAct = !place.equals("farm_gate") && !place.equals("time_up") && !place.equals("fainted");
        shell.in.consume();
    }

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
        shell.townAct = town == null ? null : this::launchTown;
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
        if (returnToAct && shell.screen() == actPlay && !shell.hasOverlay() && !shell.transitioning()) {
            launchTown(actPlay);
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
        if (shell == null) return false;
        if (command.equals("town scene")) { shell.sceneValley = true; return true; }
        if (command.equals("town act")) { shell.sceneValley = false; return true; }
        if (command.equals("town enter")) {
            if (!(shell.screen() instanceof PlayScreen play)) return false;
            launchTown(play); return true;
        }
        return Debug.apply(shell, command);
    }
}
