package typinghog;

import com.openggf.game.GameServiceBundle;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import typinghog.art.PlateFont;
import typinghog.art.Plates;
import typinghog.core.Lexicon;
import typinghog.hub.HubScene;
import typinghog.play.Cast;
import typinghog.play.Course;
import typinghog.play.CourseAssets;
import typinghog.play.Props;
import typinghog.play.Sounds;
import typinghog.play.Stagehand;
import typinghog.play.TypingIds;
import typinghog.rails.Rail;
import typinghog.rails.TypingPatch;

/**
 * Typing of the Hedgehog: a Typing of the Dead homage over every supplied Sonic ROM. The hub is
 * the startup scene; each game's patch installs the {@link Course} controller, which drives Sonic
 * along the act's rail (stock badniks stay; runs put bonus words on them) and stages the typed
 * fights at stands.
 */
public final class TypingHedgehogMod implements GgfMod {
    // The mod validator allows only literal static constants, so lists live on the instance.
    private final List<String> games = List.of("s1", "s2", "s3k");
    private final List<String> wordPools = List.of("common", "tricky", "phrases", "rescues", "lore-s1", "lore-s2", "lore-s3k");

    @Override public void register(ModContext context) {
        Lexicon lexicon = new Lexicon();
        for (String pool : wordPools) lexicon.pool(pool, text(context, "words/" + pool + ".txt", ""));
        lexicon.taunts(text(context, "words/taunts.txt", ""));
        lexicon.trivia(text(context, "words/trivia.txt", ""));
        lexicon.pool("user", context.storage().read("words.txt").orElse(""));
        PlateFont font = new PlateFont(text(context, "art/plate-font.txt", null));
        Map<String, Cast> casts = new HashMap<>();
        Map<String, Sounds> sounds = new HashMap<>();
        Map<String, Props> props = new HashMap<>();
        Map<String, Rail> rails = new HashMap<>();
        Map<String, String> badniks = new HashMap<>();
        for (String game : games) {
            casts.put(game, Cast.parse(text(context, "cast/" + game + ".txt", "")));
            sounds.put(game, Sounds.parse(text(context, "sounds/" + game + ".txt", "")));
            props.put(game, Props.parse(text(context, "props/" + game + ".txt", "")));
            badniks.put(game, text(context, "badniks/" + game + ".txt", ""));
        }
        for (String line : text(context, "rails/index.txt", "").split("\n")) {
            String name = line.strip();
            if (name.isEmpty() || name.startsWith("#")) continue;
            Rail rail = Rail.parse(text(context, "rails/" + name, null));
            rails.put(rail.game() + ":" + rail.zone() + ":" + rail.act(), rail);
        }
        CourseAssets assets = new CourseAssets(lexicon, new Plates(font), Map.copyOf(casts), Map.copyOf(sounds),
                Map.copyOf(props), Map.copyOf(rails));
        var storage = context.storage();
        // Rails are validated at the native 320-pixel view: a wider view activates objects earlier and the
        // recorded runs diverge (rails kill test, 2026-10-09).
        context.requireDisplayWidth(320);
        context.registerObject("puppet", (spawn, registry) -> new typinghog.play.Puppet(spawn));
        context.registerServiceBundle("course", () -> {
            Stagehand stagehand = new Stagehand();
            Course course = new Course(storage, assets, stagehand);
            return GameServiceBundle.builder()
                    .frameController("course", course)
                    .service(Course.class, course)
                    .service(Stagehand.class, stagehand)
                    .build();
        });
        for (String game : games) context.registerGamePatch(new TypingPatch(game, badniks.get(game)));
        context.registerStartupScene(() -> new HubScene(assets));
    }

    private static String text(ModContext context, String path, String fallback) {
        try {
            return new String(context.modAssets().readBounded(path, 4 << 20), StandardCharsets.UTF_8);
        } catch (IOException missing) {
            if (fallback != null) return fallback;
            throw new UncheckedIOException("Missing mod file " + path + " in " + TypingIds.MOD, missing);
        }
    }
}
