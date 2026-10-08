package slaytherobotnik.art;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelStage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import slaytherobotnik.content.Content;
import slaytherobotnik.core.Catalog;

/** cards.txt parses, and every recipe names a real card, sprite key, icon and effect. */
class CardRecipesTest {
    private static final Set<String> HEROES = Set.of("sonic", "tails", "knuckles");
    private static final Set<String> EFFECTS = Set.of("speed", "burst", "sparkle", "stars", "rings", "fire",
            "water", "zap");

    private static byte[] resource(String path) throws IOException {
        try (InputStream in = CardRecipesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return in.readAllBytes();
        }
    }

    /** A ROM stand-in that answers every request, so RomSprites.load returns null only for unknown keys. */
    private static final SceneRomArt ANY_ROM = new SceneRomArt() {
        @Override public String gameId() { return "s3k"; }
        @Override public String romSha1() { return "cfbf98c36c776677290a872547ac47c53d2761d6"; }
        @Override public byte[] read(int address, int length) { return new byte[length]; }
        @Override public int[] palette(int address, int colors) { return new int[colors]; }
        @Override public SceneSpriteSet sprites(RomSpriteRequest request, int[] palette) { return EMPTY; }
        @Override public SceneSpriteSet character(String characterCode) { return EMPTY; }
        @Override public SceneSpriteSet characterAccessory(String characterCode) { return EMPTY; }
        @Override public int[] characterPalette(String characterCode) { return new int[16]; }
        @Override public SceneImage tiles(int address, RomSpriteRequest.Compression compression, int firstTile,
                int widthTiles, int heightTiles, boolean columnMajor, int[] palette) {
            return new SceneImage(widthTiles * 8, heightTiles * 8, new int[widthTiles * heightTiles * 64]);
        }
        @Override public boolean hasZonePictures(int zone, int act) { return false; }
        @Override public SceneBackdrop zoneBackdrop(int zone, int act) { return null; }
        @Override public SceneImage levelOverview(int zone, int act, int maxHeight) { return null; }
        @Override public List<SceneLevelStage> levelStages(int zone, int act, int width, int headroom, int maxRise) {
            return List.of();
        }
        @Override public SceneImage levelForeground(int zone, int act, int x, int y, int width, int height) {
            return null;
        }
        @Override public boolean hasTitleCard(int zone, int act) { return false; }
        @Override public SceneSpriteSet titleCard(int zone, int act) { return null; }
    };

    private static final SceneSpriteSet EMPTY = new SceneSpriteSet() {
        @Override public int frameCount() { return 0; }
        @Override public SceneSprite frame(int index) { return null; }
        @Override public int[] animationFrames(int animation) { return new int[0]; }
        @Override public int animationDelay(int animation) { return 0; }
    };

    @Test
    void everyRecipeNamesRealThings() throws IOException {
        CardRecipes recipes = new CardRecipes(resource("/art/cards.txt"));
        Catalog catalog = Content.build();
        Set<String> icons = new HashSet<>();
        Matcher m = Pattern.compile("(?m)^sprite (\\S+)").matcher(new String(resource("/art/icons.txt"),
                StandardCharsets.UTF_8));
        while (m.find()) {
            icons.add(m.group(1));
        }
        List<String> problems = new ArrayList<>();
        recipes.all().forEach((id, layers) -> {
            if (!catalog.hasCard(id)) {
                problems.add(id + ": no such card");
            }
            for (CardRecipes.Layer layer : layers) {
                boolean ok = switch (layer.kind()) {
                    case "hero" -> HEROES.contains(layer.key());
                    case "rom" -> RomSprites.load(ANY_ROM, layer.key()) != null;
                    case "icon" -> icons.contains(layer.key());
                    case "fx" -> EFFECTS.contains(layer.key());
                    case "monitor" -> layer.key().matches("[3-9]|10|static|.{1,3}");
                    case "bg" -> layer.key().matches("[0-9A-Fa-f]{6}");
                    default -> false;
                };
                if (!ok) {
                    problems.add(id + ": bad " + layer.kind() + " '" + layer.key() + "'");
                }
            }
        });
        assertEquals(List.of(), problems);
    }

    @Test
    void everyRelicHasAPictureOfRealThings() throws IOException {
        CardRecipes pictures = new CardRecipes(resource("/art/relics.txt"));
        Catalog catalog = Content.build();
        List<String> problems = new ArrayList<>();
        for (Catalog.RelicEntry entry : catalog.allRelics()) {
            if (pictures.recipe(entry.id()) == null) {
                problems.add(entry.id() + ": no picture");
            }
        }
        pictures.all().forEach((id, layers) -> {
            if (!catalog.hasRelic(id)) {
                problems.add(id + ": no such relic");
            }
            for (CardRecipes.Layer layer : layers) {
                if (layer.kind().equals("rom") && RomSprites.load(ANY_ROM, layer.key()) == null) {
                    problems.add(id + ": bad rom key " + layer.key());
                }
            }
        });
        assertEquals(List.of(), problems);
    }

    @Test
    void parsesOffsetsFlipsScalesAndAnimations() {
        CardRecipes r = new CardRecipes("x:y: hero sonic anim=0x2 -6,2 flip x2 a0.5; rom ring anim=0/1/2@3 4,5\n"
                .getBytes(StandardCharsets.UTF_8));
        CardRecipes.Layer hero = r.recipe("x:y").get(0);
        assertEquals(-6f, hero.x());
        assertEquals(2f, hero.scale());
        assertEquals(0.5f, hero.alpha());
        assertEquals(true, hero.flip() && hero.anim());
        CardRecipes.Layer ring = r.recipe("x:y").get(1);
        assertEquals(3, ring.frames().length);
        assertEquals(3, ring.ticks());
        assertThrows(IllegalArgumentException.class,
                () -> new CardRecipes("x:y: wobble 3\n".getBytes(StandardCharsets.UTF_8)));
    }
}
