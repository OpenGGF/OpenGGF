package starpost.fishing;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Screen;
import starpost.scene.Shell;

/**
 * How fishing plugs in (design doc §14): the farm pond's line (an actor, cast through the pond's
 * hook), the valley's {@code lake} doorway (Waterfall Lake), the catches' icons, and debug
 * commands for captures.
 */
public final class FishingSystem {
    final FishArt art;
    final FishTable table;
    private final PondLine pond;

    private FishingSystem(Shell shell, PlayScreen play) {
        art = new FishArt(shell.art);
        table = Fishing.section(shell.game).table();
        pond = new PondLine(this, play);
    }

    /** Called from {@code Systems.install} for every new play screen. */
    public static void install(Shell shell, PlayScreen play, List<Actor> actors, Map<String, Consumer<Shell>> places) {
        FishingSystem sys = new FishingSystem(shell, play);
        shell.art.icons.addSource("fishing", item -> sys.art.icon(item, sys.table));
        actors.add(sys.pond);
        play.farm().pondAction = () -> sys.pond.cast(shell);
        places.put("lake", s -> s.go(new LakeScreen(s, play, sys)));
    }

    /**
     * The strike: junk comes straight up; anything else fights in the Bubble Bar. {@code landed}
     * hears what was landed; {@code after} runs either way, once the line is in.
     */
    void strike(Shell shell, String id, Consumer<Fishing.Landed> landed, Runnable after) {
        starpost.core.Game game = shell.game;
        FishDef def = table.get(id);
        if (def == null) {
            landed.accept(Fishing.land(game, id, false));
            after.run();
            return;
        }
        long seed = game.rng.nextLong();
        BubbleBar bar = new BubbleBar(def.difficulty(), def.motion(), Fishing.bubbleHalf(game),
                new com.openggf.mods.state.SnapshotRandom(seed));
        shell.push(new BubbleBarScreen(art, def, bar, seed ^ 0x5EEDL, (won, perfect) -> {
            if (won) {
                landed.accept(Fishing.land(game, id, perfect));
            } else {
                shell.toast("THE " + (def.isBadnik() ? "BADNIK" : "FISH") + " GOT AWAY...");
            }
            after.run();
        }));
    }

    /** The fishing system of a play screen (through its pond actor), or null. */
    static FishingSystem of(PlayScreen play) {
        for (Actor actor : play.actors) {
            if (actor instanceof PondLine line) {
                return line.system();
            }
        }
        return null;
    }

    /**
     * Debug ({@code fish ...}): {@code lake} goes to Waterfall Lake; {@code bite} makes the line
     * out bite now; {@code bar ID} opens the Bubble Bar on a catch; {@code land ID} lands one;
     * {@code at X} stands at the lake; {@code flag NAME} sets a story flag (the lake bridge,
     * Barnaby's story).
     */
    public static boolean debug(Shell shell, String[] p) {
        if (shell.game == null || p.length < 2) {
            return false;
        }
        Screen screen = shell.screen();
        PlayScreen play = screen instanceof PlayScreen ps ? ps : screen instanceof LakeScreen lake ? lake.play() : null;
        FishingSystem sys = play == null ? null : of(play);
        if (sys == null) {
            return false;
        }
        String id = p.length > 2 ? String.join("_", java.util.Arrays.copyOfRange(p, 2, p.length)) : null;
        switch (p[1]) {
            case "lake" -> {
                shell.goNow(new LakeScreen(shell, play, sys));
                return true;
            }
            case "at" -> {
                if (screen instanceof LakeScreen lake) {
                    lake.debugAt(Float.parseFloat(p[2]));
                    return true;
                }
                return false;
            }
            case "bite" -> {
                if (screen instanceof LakeScreen lake) {
                    return lake.debugBite(id);
                }
                return sys.pond.debugBite(id);
            }
            case "bar" -> {
                if (sys.table.get(id) == null) {
                    return false;
                }
                if (screen instanceof LakeScreen lake) {
                    return lake.debugFight(id);
                }
                return sys.pond.debugFight(shell, id);
            }
            case "flag" -> {
                shell.game.flags.add(id);
                return true;
            }
            case "land" -> {
                if (screen instanceof LakeScreen lake) {
                    lake.debugLand(id);
                } else {
                    sys.pond.debugLand(shell, id);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
