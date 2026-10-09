package starpost.ruins;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import starpost.core.Game;
import starpost.scene.PlayScreen;
import starpost.scene.Screen;
import starpost.scene.Shell;

/**
 * How the Ruins plug into the game (design doc §14): the valley's {@code ruins} doorway opens
 * the elevator (or chamber 1 before any elevator is reached), the Ruins' icons join the item
 * icons, and debug commands for captures.
 */
public final class RuinsSystem {
    private RuinsSystem() {
    }

    /** Called from {@code Systems.install} for every new play screen. */
    public static void install(Shell shell, PlayScreen play, Map<String, Consumer<Shell>> places) {
        shell.art.icons.addSource("ruins", new RuinsIcons());
        RuinsArt[] art = new RuinsArt[1];
        places.put("ruins", s -> enter(s, play, art));
    }

    /** The section, created (with a fresh seed) if a save predates the Ruins. */
    public static RuinsSection section(Game game) {
        RuinsSection section = game.section(RuinsSection.class);
        if (section == null) {
            section = new RuinsSection();
            game.sections.add(section);
        }
        if (section.seed == 0) {
            section.seed = game.rng.nextLong() | 1;
        }
        return section;
    }

    private static RuinsArt art(Shell shell, RuinsArt[] holder) {
        if (holder[0] == null) {
            holder[0] = new RuinsArt(shell.art);
        }
        return holder[0];
    }

    /** At the doorway: the elevator's floors once one is reached, otherwise straight in. */
    private static void enter(Shell shell, PlayScreen play, RuinsArt[] holder) {
        RuinsSection section = section(shell.game);
        List<Integer> starts = RuinsRules.starts(section.deepest);
        if (starts.size() == 1) {
            shell.go(new RuinsScreen(shell, play, art(shell, holder), 1));
            return;
        }
        String[] options = new String[starts.size() + 1];
        for (int i = 0; i < starts.size(); i++) {
            int c = starts.get(i);
            options[i] = "CHAMBER " + c + (c == 1 ? "" : "  " + RuinsRules.bandName(RuinsRules.band(c)));
        }
        options[starts.size()] = "NOT NOW";
        shell.push(new RuinsMenu("MARBLE RUINS ELEVATOR", options, choice -> {
            if (choice >= 0 && choice < starts.size()) {
                shell.go(new RuinsScreen(shell, play, art(shell, holder), starts.get(choice)));
            }
        }));
    }

    /**
     * Debug ({@code ruins N}): straight into chamber N. {@code ruins rings N} sets the rings in
     * hand, {@code ruins hit} hurts Sonic, {@code ruins deepest N} sets the elevator, {@code ruins
     * elevator} opens the doorway's elevator menu, {@code ruins at X Y} moves Sonic.
     */
    public static boolean debug(Shell shell, String[] p) {
        if (shell.game == null || p.length < 2) {
            return false;
        }
        Screen screen = shell.screen();
        switch (p[1]) {
            case "rings" -> {
                if (screen instanceof RuinsScreen ruins) {
                    ruins.debugRings(Integer.parseInt(p[2]));
                    return true;
                }
                return false;
            }
            case "hit" -> {
                if (screen instanceof RuinsScreen ruins) {
                    ruins.debugHit();
                    return true;
                }
                return false;
            }
            case "at" -> {
                if (screen instanceof RuinsScreen ruins) {
                    ruins.debugAt(Float.parseFloat(p[2]), Float.parseFloat(p[3]));
                    return true;
                }
                return false;
            }
            case "state" -> {
                if (screen instanceof RuinsScreen ruins) {
                    ruins.debugState();
                    return true;
                }
                return false;
            }
            case "deepest" -> {
                section(shell.game).deepest = Integer.parseInt(p[2]);
                return true;
            }
            case "elevator" -> {
                if (screen instanceof PlayScreen play && play.places.get("ruins") != null) {
                    play.places.get("ruins").accept(shell);
                    return true;
                }
                return false;
            }
            default -> {
                int chamber = Math.max(1, Math.min(RuinsRules.CHAMBERS, Integer.parseInt(p[1])));
                PlayScreen play = screen instanceof PlayScreen ps ? ps
                        : screen instanceof RuinsScreen rs ? rs.play() : null;
                if (play == null) {
                    return false;
                }
                RuinsArt art = screen instanceof RuinsScreen rs ? rs.art() : new RuinsArt(shell.art);
                shell.goNow(new RuinsScreen(shell, play, art, chamber));
                return true;
            }
        }
    }
}
