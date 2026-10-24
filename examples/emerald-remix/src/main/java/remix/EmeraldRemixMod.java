package remix;

import com.openggf.game.Game;
import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.level.Level;
import com.openggf.level.MutableLevel;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.code.ModZoneContribution;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Emerald Remix: ordinary Sonic 2 with two level changes. Emerald Hill Act 1 is rebuilt at load
 * time from column strips of the player's own ROM ({@code remix/ehz1.remix}), and an original
 * zone, Clover Valley, plays between Emerald Hill Act 2 and Chemical Plant.
 */
public final class EmeraldRemixMod implements GgfMod {
    static final String ID = "emerald-remix";
    static final String RECIPE = "remix/ehz1.remix";

    @Override public void register(ModContext context) {
        RemixRecipe recipe;
        try {
            recipe = RemixRecipe.parse(new String(context.modAssets().readBounded(RECIPE, 64 * 1024), StandardCharsets.UTF_8));
        } catch (IOException missing) {
            throw new IllegalStateException("Emerald Remix cannot read " + RECIPE, missing);
        }
        context.registerZone(ModZoneContribution.singleAct("clover-valley",
                new BakedLevelRef("levels/clover-valley/level.json"), "ehz2", null, false)
                .withRuntime(CloverValley::runtime));
        context.registerGamePatch(new Patch(recipe));
    }

    public static final class Patch implements GamePatch {
        private final RemixRecipe recipe;
        Patch(RemixRecipe recipe) { this.recipe = recipe; }
        @Override public String id() { return ID + ":layout"; }
        @Override public String displayName() { return "Emerald Remix"; }
        @Override public String baseGameId() { return "s2"; }
        @Override public boolean activatesFor(GameplayLaunchRequest request) { return request.gameId().equals("s2"); }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(LogicalRom.S2); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) { return new Module(base, recipe); }
    }

    /** Serves the remixed act in place of the stock one; every other level loads natively. */
    public static final class Module extends DelegatingGameModule {
        private final RemixRecipe recipe;
        private Game game;
        private Game decodedFor;
        private final java.util.Map<RemixRecipe.Act, Level> donors = new LinkedHashMap<>();

        public Module(GameModule base, RemixRecipe recipe) {
            super(base, ID + ":layout");
            this.recipe = recipe;
        }

        @Override public Game createGame(Rom rom) { return game = super.createGame(rom); }
        @Override public Game createGame(com.openggf.game.GameDataSource source) { return game = super.createGame(source); }

        private int levelIndex(RemixRecipe.Act act) {
            return getZoneRegistry().getLevelDataForZone(act.zone()).get(act.act()).levelIndex();
        }

        @Override public Level loadLevelOverride(int index) throws IOException {
            if (game == null || index != levelIndex(recipe.target())) return super.loadLevelOverride(index);
            // Other acts are decoded once per game for their layouts and placements; their
            // art is never shown. The target loads last, so its graphics are the ones published.
            if (decodedFor != game) {
                donors.clear();
                for (RemixRecipe.Act act : recipe.sources())
                    if (!act.equals(recipe.target())) donors.put(act, game.loadLevel(levelIndex(act)));
                decodedFor = game;
            }
            Level target = game.loadLevel(index);
            var sources = new LinkedHashMap<>(donors);
            sources.put(recipe.target(), target);
            return MutableLevel.snapshot(new RemixedLevel(target, recipe, sources));
        }
    }
}
