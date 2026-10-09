package practice;

import com.openggf.game.run.RunEndReason;
import com.openggf.game.run.RunSpec;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;

import java.util.List;

/**
 * Picks a game's first act and practises it. The run keeps special stages and rewind (unlike a
 * timed attack) but returns here when the act is complete; the host races your previous attempt.
 */
public final class PracticeScene implements ModScene {
    private List<String> games = List.of();
    private int selected;
    private final PracticeHost host = new PracticeHost();
    private String status = "";

    /** Practice keeps stages and rewind but hands control back when the act is done. */
    static GameplayRunPolicy practicePolicy() {
        return new GameplayRunPolicy(true, true, GameplayRunPolicy.ActCompletion.RETURN_TO_HOST, true, false);
    }

    @Override
    public void enter(SceneContext ctx) {
        games = ctx.gameplay().availableGames();
    }

    @Override
    public void update(SceneContext ctx) {
        boolean accept = ctx.buttonPressed(SceneButtons.START) || ctx.keyPressed(SceneKeys.ENTER);
        if (ctx.buttonPressed(SceneButtons.B) || ctx.keyPressed(SceneKeys.ESCAPE) || games.isEmpty() && accept) {
            ctx.exitToMasterTitle();
            return;
        }
        if (games.isEmpty()) {
            return;
        }
        if (ctx.buttonRepeated(SceneButtons.LEFT)) selected = Math.floorMod(selected - 1, games.size());
        if (ctx.buttonRepeated(SceneButtons.RIGHT)) selected = Math.floorMod(selected + 1, games.size());
        if (accept) {
            String game = games.get(selected);
            host.prepare(game, ctx.storage());
            host.attach(ctx.gameplay().launch(new RunSpec(game, 0, 0, "sonic", practicePolicy()), host));
        }
    }

    @Override
    public void resumed(SceneContext ctx, RunEndReason reason) {
        status = switch (reason) {
            case ACT_COMPLETED -> "Act complete: " + PracticeHost.format(host.lastTimeFrames());
            case LOAD_FAILED -> "That act could not be loaded";
            default -> "";
        };
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        canvas.clear(0x102018);
        canvas.text("ACT PRACTICE", 10, 10, 0xFFFFE070);
        if (games.isEmpty()) {
            canvas.text("No game ROM is available.", 10, 40, 0xFFFFFFFF);
            return;
        }
        canvas.text("< " + games.get(selected).toUpperCase() + " FIRST ACT >", 10, 60, 0xFFFFFFFF);
        canvas.text("Start: practise   R in game: retry   B: back", 10, 200, 0xFF90A890);
        if (!status.isEmpty()) {
            canvas.text(status, 10, 90, 0xFFFFE070);
        }
    }

    PracticeHost hostForTest() {
        return host;
    }
}
