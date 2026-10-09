package starpost.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import starpost.core.Capsule;
import starpost.core.SaveSection;
import starpost.valley.Pickups;

/**
 * Where the game's systems plug in: each contributes its save section, its actors and the
 * places it handles (a doorway id pressed in the valley). New systems add one line to
 * {@link #sections} and register in {@link #install}.
 */
public final class Systems {
    private Systems() {
    }

    /** Fresh sections for a new or loaded game. */
    public static List<SaveSection> sections(Shell shell) {
        List<SaveSection> out = new ArrayList<>();
        out.add(new Pickups());
        out.add(new Capsule());
        out.add(new starpost.core.Skills());
        return out;
    }

    /** Adds the systems' actors and place handlers to a new play screen. */
    public static void install(Shell shell, PlayScreen play, List<Actor> actors, Map<String, Consumer<Shell>> places) {
        actors.addAll(starpost.farm.Pests.spawn(shell.game));
        Pickups pickups = shell.game.section(Pickups.class);
        if (pickups != null) {
            actors.addAll(pickups.today(shell.game, play.valley().valley));
        }
    }
}
