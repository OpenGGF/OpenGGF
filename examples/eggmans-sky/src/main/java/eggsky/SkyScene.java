package eggsky;

import com.openggf.mods.scene.DebuggableScene;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;

/**
 * The mod's one scene: hands every engine call to the {@link Game}. Debug commands
 * (for capture tools and tests): {@code new[:seed]} starts an expedition, {@code planet:i[:seed]}
 * lands on planet {@code i}, {@code biome:game:zone:act[:seed]} lands on a planet remixed from one
 * act, {@code space[:i]} launches above planet {@code i}, {@code station}, {@code galaxy},
 * {@code rich} (resources for testing) and {@code title}.
 */
public final class SkyScene implements ModScene, DebuggableScene {
    private final Game game = new Game();

    @Override
    public void enter(SceneContext ctx) {
        game.start(ctx);
    }

    @Override
    public void update(SceneContext ctx) {
        game.update(ctx);
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        game.draw(ctx, canvas);
    }

    @Override
    public void exit(SceneContext ctx) {
        game.ctx = ctx;
        game.save();
    }

    @Override
    public boolean debugJump(String command) {
        return game.debug(command);
    }

    public Game game() {
        return game;
    }
}
