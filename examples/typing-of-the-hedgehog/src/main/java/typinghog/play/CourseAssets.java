package typinghog.play;

import java.util.Map;
import typinghog.art.PlateFont;
import typinghog.art.Plates;
import typinghog.core.Lexicon;
import typinghog.rails.Rail;
import typinghog.rails.SectionPlan;

/**
 * Everything the course reads from the jar, decoded once at registration (mod files can only be
 * read then): word lists, the plate face, each game's cast, sound and prop tables, the rails keyed
 * {@code game:zone:act}, and the shipped pixel props ({@code hub/art.txt}, e.g. the backpack).
 */
public record CourseAssets(Lexicon lexicon, Plates plates, Map<String, Cast> casts, Map<String, Sounds> sounds,
                           Map<String, Props> props, Map<String, Rail> rails,
                           Map<String, typinghog.hub.model.TextArt.Picture> pictures,
                           Map<String, SectionPlan> sections) {
    public CourseAssets(Lexicon lexicon, Plates plates, Map<String, Cast> casts, Map<String, Sounds> sounds,
                        Map<String, Props> props, Map<String, Rail> rails,
                        Map<String, typinghog.hub.model.TextArt.Picture> pictures) {
        this(lexicon, plates, casts, sounds, props, rails, pictures, Map.of());
    }

    public SectionPlan section(String game, int zone, int act) {
        return sections.get(game + ":" + zone + ":" + act);
    }

    public boolean playable(String game, int zone, int act) {
        return rail(game, zone, act) != null || section(game, zone, act) != null;
    }


    /** A flight escort has no boss, and a boss-only finale has no endless wave arena. */
    public boolean supports(typinghog.save.LaunchIntent.Mode mode, String game, int zone, int act) {
        if (!playable(game, zone, act)) return false;
        SectionPlan section = section(game, zone, act);
        if (section != null) {
            return !(mode == typinghog.save.LaunchIntent.Mode.BOSS_RUSH && section.mode() == SectionPlan.Mode.ESCORT)
                    && !(mode == typinghog.save.LaunchIntent.Mode.ENDLESS && section.mode() == SectionPlan.Mode.FINALE);
        }
        Rail rail = rail(game, zone, act);
        return mode != typinghog.save.LaunchIntent.Mode.ENDLESS || !rail.plan().kind().equals("boss-only");
    }

    public Rail rail(String game, int zone, int act) {
        return rails.get(game + ":" + zone + ":" + act);
    }

    public Cast cast(String game) {
        return casts.getOrDefault(game, Cast.parse(""));
    }

    public Sounds sounds(String game) {
        return sounds.getOrDefault(game, Sounds.parse(""));
    }

    public Props props(String game) {
        return props.getOrDefault(game, Props.parse(""));
    }

    public PlateFont font() { return plates.font(); }
}
