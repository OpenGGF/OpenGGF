package sonicbros;

import com.openggf.mods.scene.DebuggableScene;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import sonicbros.input.Controls;
import sonicbros.sim.Rules;
import sonicbros.ui.BattleScreen;
import sonicbros.ui.Game;
import sonicbros.ui.HowToScreen;
import sonicbros.ui.MainMenu;
import sonicbros.ui.RecordsScreen;
import sonicbros.ui.RulesScreen;
import sonicbros.ui.SelectScreen;
import sonicbros.ui.Slot;
import sonicbros.ui.StageScreen;
import sonicbros.ui.TitleScreen;

/**
 * The whole game in one mod scene: it owns a {@link Game} (input, art, rules, slots) and
 * forwards each tick to the current screen.
 *
 * <p>Debug commands ({@link #debugJump}), for tests and the capture tool:
 * <ul>
 *   <li>{@code title}, {@code menu}, {@code rules}, {@code records}, {@code howto},
 *       {@code select}, {@code stage}: open that screen;</li>
 *   <li>{@code battle:<stage>:<fighter>[/c<level>],...[:<rule>...]}: start a battle at once on
 *       stage index {@code stage} with the listed fighters (roster ids; {@code /c5} makes a level
 *       5 CPU, otherwise a human on the next logical pad). Rules: {@code stock3}, {@code time2},
 *       {@code stamina150}, {@code items0..3}, {@code teams}.</li>
 * </ul>
 */
public final class BrosScene implements ModScene, DebuggableScene {
    private Game game;

    @Override public void enter(SceneContext ctx) {
        game = new Game(ctx);
        game.jump(new TitleScreen(game));
    }

    @Override public void update(SceneContext ctx) {
        game.update();
    }

    @Override public void draw(SceneContext ctx, SceneCanvas canvas) {
        game.draw(canvas);
    }

    @Override public void exit(SceneContext ctx) {
        if (game == null) return;
        ctx.audio().stopMusic();
        game.records.save(ctx.storage());
    }

    /** The current screen's name, for tests. */
    public String screen() {
        return game == null || game.screen == null ? "" : game.screen.name();
    }

    /** The current battle, for tests (null outside one). */
    public BattleScreen battle() {
        return game != null && game.screen instanceof BattleScreen b ? b : null;
    }

    @Override public boolean debugJump(String command) {
        if (game == null) return false;
        switch (command) {
            case "title" -> { game.jump(new TitleScreen(game)); return true; }
            case "menu" -> { game.jump(new MainMenu(game)); return true; }
            case "rules" -> { game.jump(new RulesScreen(game)); return true; }
            case "records" -> { game.jump(new RecordsScreen(game)); return true; }
            case "howto" -> { game.jump(new HowToScreen(game)); return true; }
            case "select" -> { game.jump(new SelectScreen(game, null)); return true; }
            case "stage" -> { game.jump(new StageScreen(game)); return true; }
            default -> { }
        }
        if (!command.startsWith("battle:")) return false;
        String[] parts = command.split(":");
        if (parts.length < 3) return false;
        int stage;
        try {
            stage = Integer.parseInt(parts[1]);
        } catch (NumberFormatException bad) {
            return false;
        }
        String[] who = parts[2].split(",");
        if (who.length < 2 || who.length > 4) return false;
        for (Slot s : game.slots) s.kind = Slot.Kind.OFF;
        int humans = 0;
        for (int i = 0; i < who.length; i++) {
            String[] spec = who[i].split("/");
            int index = game.roster.indexOf(spec[0]);
            if (index < 0) return false;
            Slot s = game.slots[i];
            s.fighter = index;
            s.costume = 0;
            for (int j = 0; j < i; j++) if (game.slots[j].fighter == index) s.costume++;
            s.team = i % 2;
            if (spec.length > 1 && spec[1].startsWith("c")) {
                s.kind = Slot.Kind.CPU;
                s.cpuLevel = Integer.parseInt(spec[1].substring(1));
            } else {
                s.kind = Slot.Kind.HUMAN;
                s.device = humans++ == 0 ? Controls.LOGICAL_1 : Controls.LOGICAL_2;
            }
        }
        for (int i = 3; i < parts.length; i++) {
            String r = parts[i];
            if (r.startsWith("stock")) { game.rules.mode = Rules.Mode.STOCK; game.rules.stocks = Integer.parseInt(r.substring(5)); }
            else if (r.startsWith("time")) { game.rules.mode = Rules.Mode.TIME; game.rules.minutes = Integer.parseInt(r.substring(4)); }
            else if (r.startsWith("stamina")) { game.rules.mode = Rules.Mode.STAMINA; game.rules.stamina = Integer.parseInt(r.substring(7)); }
            else if (r.startsWith("items")) game.rules.itemFrequency = Integer.parseInt(r.substring(5));
            else if (r.equals("teams")) game.rules.teams = true;
        }
        game.jump(new BattleScreen(game, stage));
        return true;
    }
}
