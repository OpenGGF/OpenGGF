package openggf.timeattack;

import com.openggf.game.run.RunEndReason;
import com.openggf.game.run.RunHandle;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;

import java.nio.file.Path;
import java.util.List;

/**
 * The Time Attack menu, opened from the master title. Choosing a route and pressing GO
 * launches a stock run through {@link SceneContext#gameplay()} with a fresh
 * {@link TimeAttackRuntime} as its host; the scene is resumed when the run ends.
 */
public final class TimeAttackScene implements ModScene {
    private static final int BACKGROUND = 0x101830;
    private static final int TITLE = 0xFFFFE070;
    private static final int LABEL = 0xFFB0C8E8;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int FOCUS = 0x803060C0;
    private static final int HINT = 0xFF8090B0;
    private static final int ROW_HEIGHT = 18;
    private static final int FIRST_ROW_Y = 47;

    private final GhostStore ghostStore = new GhostStore(Path.of("ghosts"));
    private final Path identityDir = Path.of("identity");
    private TimeAttackMenuState state;
    private TimeAttackRuntime runtime;
    private int focus;
    private String status = "";

    @Override
    public void enter(SceneContext ctx) {
        List<String> games = ctx.gameplay().availableGames();
        if (!games.isEmpty()) {
            state = new TimeAttackMenuState(games, games.get(0), ghostStore);
        }
    }

    @Override
    public void update(SceneContext ctx) {
        if (state == null) {
            if (backPressed(ctx) || acceptPressed(ctx)) {
                ctx.exitToMasterTitle();
            }
            return;
        }
        if (backPressed(ctx)) {
            ctx.exitToMasterTitle();
            return;
        }
        int rows = state.visibleRows().size();
        int go = rows;
        if (ctx.buttonRepeated(SceneButtons.UP)) focus = Math.floorMod(focus - 1, go + 1);
        if (ctx.buttonRepeated(SceneButtons.DOWN)) focus = (focus + 1) % (go + 1);
        if (focus < rows) {
            while (state.focusedRow() != state.visibleRows().get(focus)) state.moveFocus(1);
            if (ctx.buttonRepeated(SceneButtons.LEFT)) state.adjust(-1);
            if (ctx.buttonRepeated(SceneButtons.RIGHT)) state.adjust(1);
        }
        if (acceptPressed(ctx)) {
            if (focus == go) {
                state.pressGo();
            } else {
                focus = Math.min(focus + 1, go);
            }
        }
        TimeAttackLaunchRequest request = state.consumeLaunchRequest();
        if (request != null) {
            if (state.mode() == TimeAttackMenuState.Mode.SOLO) {
                launch(ctx, request);
            } else {
                status = "Multiplayer is not available yet";
            }
        }
    }

    private void launch(SceneContext ctx, TimeAttackLaunchRequest request) {
        TimeAttackRuntime run = new TimeAttackRuntime(ghostStore, identityDir, () -> false);
        run.setRetryKey(() -> SceneKeys.R);
        run.armForLaunch(request);
        RunHandle handle = ctx.gameplay().launch(run.runSpec(), run);
        run.attachHandle(handle);
        runtime = run;
        status = "";
    }

    @Override
    public void resumed(SceneContext ctx, RunEndReason reason) {
        runtime = null;
        status = switch (reason) {
            case ACT_COMPLETED -> "Run complete";
            case LEFT, ABORTED -> "";
            case LOAD_FAILED -> "The route could not be loaded";
        };
        if (state != null) {
            state.refreshGhostSummary();
        }
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        canvas.clear(BACKGROUND);
        canvas.text("TIME ATTACK", 10, 10, TITLE);
        if (state == null) {
            canvas.text("No game ROM is available.", 10, 40, VALUE);
            canvas.text("Configure ROMs in Settings, then come back.", 10, 56, HINT);
            return;
        }
        canvas.text("Choose a route and race mode", 10, 24, HINT);
        int index = 0;
        for (TimeAttackMenuState.Row row : state.visibleRows()) {
            String label;
            String value;
            switch (row) {
                case GAME -> { label = "Game"; value = state.currentGameId().toUpperCase(); }
                case TRACK -> { label = "Track"; value = state.currentTrack() == null ? "No tracks" : state.currentTrack().label(); }
                case CHARACTER -> { label = "Character"; value = state.currentCharacter() == null ? "None" : state.currentCharacter().toUpperCase(); }
                case MODE -> { label = "Mode"; value = state.mode().name().replace('_', ' '); }
                case POLICY -> { label = "Policy"; value = state.characterPolicy().equals("OPEN") ? "OPEN" : "LOCKED " + state.currentCharacter().toUpperCase(); }
                case WINDOW -> { label = "Window"; value = (state.windowSeconds() / 60) + " minutes"; }
                default -> throw new IllegalStateException("Unexpected row " + row);
            }
            drawRow(canvas, label, "< " + value + " >", index++);
        }
        String action = switch (state.mode()) {
            case SOLO -> "START RUN";
            case HOST_LAN -> "CREATE LAN ROOM";
            case JOIN_LAN -> "JOIN LAN ROOM";
            case BROWSE -> "BROWSE ROOMS";
        };
        int actionY = FIRST_ROW_Y + index * ROW_HEIGHT;
        if (focus == index) canvas.fill(8, actionY - 3, canvas.width() - 16, 16, FOCUS);
        canvas.text(action, 14, actionY, VALUE);
        canvas.text((state.bestExists() ? "Best: saved" : "Best: none")
                + " / Imported ghosts: " + state.importCount(), 10, 181, LABEL);
        if (!status.isEmpty()) {
            canvas.text(status, 10, 194, TITLE);
        }
        canvas.text("Up/Down select  Left/Right change  Start go  B back", 10, 210, HINT);
    }

    private void drawRow(SceneCanvas canvas, String label, String value, int index) {
        int y = FIRST_ROW_Y + index * ROW_HEIGHT;
        if (focus == index) canvas.fill(8, y - 3, canvas.width() - 16, 16, FOCUS);
        canvas.text(label, 14, y, LABEL);
        canvas.text(value, 112, y, VALUE);
    }

    private static boolean acceptPressed(SceneContext ctx) {
        return ctx.buttonPressed(SceneButtons.START | SceneButtons.A | SceneButtons.C)
                || ctx.keyPressed(SceneKeys.ENTER);
    }

    private static boolean backPressed(SceneContext ctx) {
        return ctx.buttonPressed(SceneButtons.B) || ctx.keyPressed(SceneKeys.ESCAPE);
    }

    /** The run being played, or null while the menu is shown (tests). */
    TimeAttackRuntime activeRuntime() {
        return runtime;
    }
}
