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
        out.add(new starpost.ruins.RuinsSection());
        out.add(new starpost.people.People());
        out.add(new starpost.fishing.FishingSection());
        out.add(new starpost.barn.Barn());
        out.add(new starpost.festivals.Festivals());
        return out;
    }

    /** What the systems offer at Tails's workshop today (buildings, upgrades). */
    public static List<WorkshopOffer> workshopOffers(Shell shell) {
        List<WorkshopOffer> out = new ArrayList<>();
        out.addAll(starpost.barn.BarnSystem.workshopOffers(shell));
        return out;
    }

    /** Adds the systems' actors and place handlers to a new play screen. */
    public static void install(Shell shell, PlayScreen play, List<Actor> actors, Map<String, Consumer<Shell>> places) {
        actors.addAll(starpost.farm.Pests.spawn(shell.game));
        Pickups pickups = shell.game.section(Pickups.class);
        if (pickups != null) {
            actors.addAll(pickups.today(shell.game, play.valley().valley));
        }
        starpost.ruins.RuinsSystem.install(shell, play, places);
        starpost.people.PeopleSystem.install(shell, play, actors);
        starpost.fishing.FishingSystem.install(shell, play, actors, places);
        starpost.barn.BarnSystem.install(shell, play, actors);
        starpost.festivals.FestivalSystem.install(shell, play, actors, places);
        starpost.festivals.Festivals festivals = shell.game.section(starpost.festivals.Festivals.class);
        if (festivals != null) {
            festivals.fishingContest = starpost.fishing.FishingSystem::contest;   // the Ice Cap Festival's contest
        }
    }

    /** A line for the morning card from the systems (today's festival), or null. */
    static String morningNote(Shell shell) {
        starpost.festivals.Festivals festivals = shell.game.section(starpost.festivals.Festivals.class);
        return festivals == null ? null : festivals.morningNote(shell.game);
    }
}
