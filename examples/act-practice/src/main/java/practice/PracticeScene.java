package practice;

import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunSpec;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.TextLayout;
import com.openggf.mods.ui.UiPrimitives;

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
        return new GameplayRunPolicy(true, true, GameplayRunPolicy.ActCompletion.RETURN_TO_HOST, true, false,
                GameplayRunPolicy.DeathRestart.STOCK, true, true);
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
        host.retrySave();
        status = switch (reason) {
            case ACT_COMPLETED -> "Act complete: " + PracticeHost.format(host.lastTimeFrames());
            case LOAD_FAILED -> "That act could not be loaded";
            default -> "";
        };
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        int width = canvas.width();
        canvas.clear(0x102018);
        canvas.fill(0, 0, width, 29, 0xFF18301F);
        canvas.fill(0, 27, width, 1, 0xFFFFE070);
        canvas.text("ACT PRACTICE", 10, 10, 0xFFFFE070);
        // Sentences use the light name face, measured and wrapped to the screen width.
        int y = paragraph(canvas, "Practise a game's first act. Special stages and rewind stay on; "
                + "your previous attempt races beside you.", 34, 0xFFB8D0B8);
        if (games.isEmpty()) {
            paragraph(canvas, "No game ROM is available. Add your games from the title screen.", y + 8, 0xFFFFFFFF);
            hints(canvas, List.of("ESC", "BACK"));
            return;
        }
        // The choice: one card, its game between arrows.
        int cardY = y + 10;
        canvas.fill(10, cardY, width - 20, 30, 0xFF1C3A26);
        UiPrimitives.frame(canvas, 10, cardY, width - 20, 30, 0xFFFFE070);
        String game = switch (games.get(selected)) {
            case "s1" -> "SONIC 1";
            case "s2" -> "SONIC 2";
            case "s3k" -> "SONIC 3 & KNUCKLES";
            default -> games.get(selected).toUpperCase();
        };
        canvas.text(game, (width - canvas.textWidth(game)) / 2, cardY + 6, 0xFFFFFFFF);
        NameFont.draw(canvas, "First act", (width - NameFont.width("First act")) / 2, cardY + 16, 0xFFB8D0B8);
        if (games.size() > 1) {
            arrow(canvas, 18, cardY + 11, -1);
            arrow(canvas, width - 22, cardY + 11, 1);
        }
        y = cardY + 38;
        if (!status.isEmpty()) y = paragraph(canvas, status, y, 0xFFFFE070);
        if (!host.persistenceNotice().isEmpty()) paragraph(canvas, host.persistenceNotice(), y, 0xFFFFA060);
        List<String> keys = new java.util.ArrayList<>();
        if (games.size() > 1) keys.addAll(List.of("LEFT/RIGHT", "GAME"));
        keys.addAll(List.of("ENTER", "PRACTISE", "R", "RETRY IN GAME", "ESC", "BACK"));
        hints(canvas, keys);
    }

    /** Wrapped name-face lines from {@code y}; returns the next line's y. */
    private static int paragraph(SceneCanvas canvas, String text, int y, int argb) {
        for (String line : TextLayout.wrap(text, canvas.width() - 20, NameFont::width)) {
            NameFont.draw(canvas, line, 10, y, argb);
            y += NameFont.LINE_HEIGHT;
        }
        return y;
    }

    /**
     * Key hints in compact capitals on a footer band: each key (gold) stays with its label, and a
     * pair that would cross the right edge starts the next line, so no hint is cut off.
     */
    private static void hints(SceneCanvas canvas, List<String> keysAndLabels) {
        int width = canvas.width();
        List<List<int[]>> lines = new java.util.ArrayList<>(List.of(new java.util.ArrayList<>()));
        int x = 0;
        for (int i = 0; i < keysAndLabels.size(); i += 2) {
            int pair = CompactFont.width(keysAndLabels.get(i), 1) + 4 + CompactFont.width(keysAndLabels.get(i + 1), 1);
            if (x > 0 && x + 12 + pair > width - 20) {
                lines.add(new java.util.ArrayList<>());
                x = 0;
            }
            if (x > 0) x += 12;
            lines.getLast().add(new int[] {i, x, pair});
            x += pair;
        }
        int top = 224 - 6 - lines.size() * 10;
        canvas.fill(0, top - 4, width, 224 - top + 4, 0xFF0A1810);
        for (int row = 0; row < lines.size(); row++) {
            List<int[]> line = lines.get(row);
            int[] last = line.getLast();
            int left = (width - (last[1] + last[2])) / 2;
            for (int[] item : line) {
                String key = keysAndLabels.get(item[0]);
                int keyX = left + item[1];
                CompactFont.draw(canvas, key, keyX, top + row * 10, 1, 0xFFFFE070);
                CompactFont.draw(canvas, keysAndLabels.get(item[0] + 1), keyX + CompactFont.width(key, 1) + 4,
                        top + row * 10, 1, 0xFFB8D0B8);
            }
        }
    }

    /** A solid 4x7 triangle pointing left or right. */
    private static void arrow(SceneCanvas canvas, int x, int y, int direction) {
        for (int i = 0; i < 4; i++) {
            canvas.fill(direction < 0 ? x + 3 - i : x + i, y + i, 1, 7 - 2 * i, 0xFFFFE070);
        }
    }

    @Override public void exit(SceneContext ctx) { host.retrySave(); }

    PracticeHost hostForTest() {
        return host;
    }
}
