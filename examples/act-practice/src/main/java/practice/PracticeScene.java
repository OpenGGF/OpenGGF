package practice;

import com.openggf.control.PhysicalInputEvent;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunSpec;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.scene.SceneStorage;
import com.openggf.mods.ui.NameFont;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks a game's first act and practises it. The run keeps special stages and rewind (unlike a
 * timed attack) but returns here when the act is complete; the host races your previous attempt.
 *
 * <p>The page echoes the engine's menus with the public canvas and fonts only ({@link PracticePage}):
 * a navy page under a gold-ruled header, the choice as one focused card with its best time, a
 * result line whose icon and colour say what happened, and a footer of key caps that name the
 * keyboard or the controller, whichever was pressed last. As in every scene, A, C or Start (Enter
 * on a keyboard) confirms and B (Escape) goes back.
 */
public final class PracticeScene implements ModScene {
    private List<String> games = List.of();
    private int selected;
    private final PracticeHost host = new PracticeHost();
    private final Map<String, Integer> bests = new HashMap<>();
    private PracticePage.Tone resultTone;
    private String result = "";
    /** Key caps name controller buttons after a pad press, keyboard keys after a key press. */
    private boolean pad;

    /** Practice keeps stages and rewind but hands control back when the act is done. */
    static GameplayRunPolicy practicePolicy() {
        return new GameplayRunPolicy(true, true, GameplayRunPolicy.ActCompletion.RETURN_TO_HOST, true, false,
                GameplayRunPolicy.DeathRestart.STOCK, true, true);
    }

    @Override
    public void enter(SceneContext ctx) {
        games = ctx.gameplay().availableGames();
        SceneStorage storage = ctx.storage();
        for (String game : games) bests.put(game, PracticeHost.storedBest(storage, game));
    }

    @Override
    public void update(SceneContext ctx) {
        pad = controller(ctx, pad);
        boolean accept = ctx.buttonPressed(SceneButtons.START | SceneButtons.A | SceneButtons.C)
                || ctx.keyPressed(SceneKeys.ENTER) || ctx.keyPressed(SceneKeys.KP_ENTER);
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

    /** True once the last button or key press came from a controller; unchanged on a quiet tick. */
    private static boolean controller(SceneContext ctx, boolean previous) {
        boolean result = previous;
        for (PhysicalInputEvent event : ctx.physicalInput().events()) {
            if (!event.pressed()) continue;
            if (event.kind() == PhysicalInputEvent.Kind.KEY) result = false;
            else if (event.kind() == PhysicalInputEvent.Kind.BUTTON) result = true;
        }
        return result;
    }

    @Override
    public void resumed(SceneContext ctx, RunEndReason reason) {
        host.retrySave();
        String game = host.game();
        int before = bests.getOrDefault(game, -1);
        if (!game.isEmpty() && host.bestFrames() >= 0) bests.put(game, host.bestFrames());
        switch (reason) {
            case ACT_COMPLETED -> {
                int time = host.lastTimeFrames();
                boolean record = time >= 0 && (before < 0 || time < before);
                resultTone = PracticePage.Tone.GOOD;
                result = "Act complete in " + PracticeHost.format(time) + (record ? ", a new best" : "");
            }
            case LOAD_FAILED -> {
                resultTone = PracticePage.Tone.ERROR;
                result = "That act could not be loaded";
            }
            default -> {
                resultTone = null;
                result = "";
            }
        }
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        int width = canvas.width();
        PracticePage.page(canvas, "ACT PRACTICE");
        int x = PracticePage.MARGIN + 6;
        int text = width - 2 * x;
        if (games.isEmpty()) {
            // An empty choice is information, not a warning: what is missing, then the next step.
            int y = PracticePage.BODY_TOP + 6;
            PracticePage.icon(canvas, PracticePage.Tone.INFO, x, y + 3);
            NameFont.draw(canvas, "No games to practise yet", x + 10, y, PracticePage.TEXT);
            PracticePage.paragraph(canvas, "Add your games from the title screen, then come back.", x + 10,
                    y + NameFont.LINE_HEIGHT + 2, text - 10, PracticePage.MUTED);
            PracticePage.footer(canvas, hints());
            return;
        }
        int y = PracticePage.paragraph(canvas, "Play a game's first act as it shipped. Special stages and rewind stay "
                + "on, and your previous attempt races beside you.", x, PracticePage.BODY_TOP, text, PracticePage.MUTED);
        // The retry key is the keyboard's R in the act, so it is said here rather than offered as a key cap.
        y = PracticePage.paragraph(canvas, "During the act, R on the keyboard retries it.", x, y, text, PracticePage.DIM);
        // The choice: one focused card, the game between arrows and its best time under it.
        int cardY = y + 7;
        int cardX = PracticePage.MARGIN;
        int cardW = width - 2 * PracticePage.MARGIN;
        PracticePage.focus(canvas, cardX, cardY, cardW, PracticePage.CARD_H);
        String game = PracticePage.gameName(games.get(selected));
        canvas.text(game, (width - canvas.textWidth(game)) / 2, cardY + 6, PracticePage.GOLD);
        int best = bests.getOrDefault(games.get(selected), -1);
        String note = "First act, " + (best >= 0 ? "best " + PracticeHost.format(best) : "no best time yet");
        NameFont.draw(canvas, note, (width - NameFont.width(note)) / 2, cardY + 19, PracticePage.TEXT);
        if (games.size() > 1) {
            PracticePage.arrow(canvas, cardX + 8, cardY + 12, -1);
            PracticePage.arrow(canvas, cardX + cardW - 12, cardY + 12, 1);
            PracticePage.dots(canvas, width / 2, cardY + PracticePage.CARD_H + 5, games.size(), selected);
        }
        y = cardY + PracticePage.CARD_H + 14;
        if (resultTone != null) y = PracticePage.notice(canvas, resultTone, result, x, y, text) + 2;
        if (!host.persistenceNotice().isEmpty()) {
            y = PracticePage.notice(canvas, PracticePage.Tone.WARNING, host.persistenceNotice(), x, y, text) + 2;
        }
        PracticePage.footer(canvas, hints());
    }

    /** What the keys do here, named for the device pressed last (Start and C confirm too). */
    private List<PracticePage.Hint> hints() {
        List<PracticePage.Hint> hints = new java.util.ArrayList<>();
        if (games.size() > 1) hints.add(PracticePage.hint(PracticePage.ARROWS_LR, "Game"));
        if (!games.isEmpty()) hints.add(PracticePage.hint(pad ? "A" : "ENTER", "Practise"));
        hints.add(PracticePage.hint(pad ? "B" : "ESC", "Back"));
        return hints;
    }

    @Override public void exit(SceneContext ctx) { host.retrySave(); }

    PracticeHost hostForTest() {
        return host;
    }

    /** The footer's key caps as {@code KEY label} pairs, for the device pressed last (tests). */
    List<String> hintsForTest() {
        return hints().stream().map(hint -> hint.key() + " " + hint.label()).toList();
    }

    /** The result line and its tone, or empty (tests). */
    String resultForTest() { return resultTone == null ? "" : resultTone + ": " + result; }
}
