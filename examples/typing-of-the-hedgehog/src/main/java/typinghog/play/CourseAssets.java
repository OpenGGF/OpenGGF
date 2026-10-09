package typinghog.play;

import java.util.Map;
import typinghog.art.PlateFont;
import typinghog.art.Plates;
import typinghog.core.Lexicon;
import typinghog.rails.Rail;

/**
 * Everything the course reads from the jar, decoded once at registration (mod files can only be
 * read then): word lists, the plate face, each game's cast, sound and prop tables, and the rails keyed
 * {@code game:zone:act}.
 */
public record CourseAssets(Lexicon lexicon, Plates plates, Map<String, Cast> casts, Map<String, Sounds> sounds,
                           Map<String, Props> props, Map<String, Rail> rails) {

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
