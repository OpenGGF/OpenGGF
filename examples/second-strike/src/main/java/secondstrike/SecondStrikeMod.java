package secondstrike;

import com.openggf.game.GameModule;
import com.openggf.game.BonusStageType;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostFactory;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy;
import com.openggf.game.sonic3k.difficulty.AizLogBridgeRecipe;
import com.openggf.game.sonic3k.difficulty.AizEndBossRecipe;
import com.openggf.game.sonic3k.difficulty.AizBombingRunRecipe;
import com.openggf.game.sonic3k.difficulty.AizTreeExitRecipe;
import com.openggf.mods.objects.AizLogBridgeFactory;
import com.openggf.mods.objects.AizHollowTreeFactory;
import com.openggf.game.sonic3k.difficulty.GumballSpringPolicy;
import com.openggf.level.LevelPatch;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import secondstrike.boss.SecondStrikeBosses;
import secondstrike.campaign.SecondStrikeCampaign;
import secondstrike.enemies.SecondStrikeEnemies;
import secondstrike.enemies.SecondStrikeCaterkiller;
import secondstrike.monitors.SecondStrikeMonitors;
import secondstrike.routes.SecondStrikeRoutes;
import secondstrike.stages.SecondStrikeStages;

import java.util.List;
import java.util.Set;

/** A difficulty layer over the ordinary S3K campaign, with no alternate frame driver. */
public final class SecondStrikeMod implements GgfMod {
    @Override public void register(ModContext context) {
        SecondStrikeEnemies.register(context);
        SecondStrikeCaterkiller.register(context);
        SecondStrikeMonitors.register(context);
        SecondStrikeCampaign.register(context);
        context.registerObject("gumball-post", (spawn, registry) ->
                Sonic3kStarPostFactory.invitation(spawn, BonusStageType.GUMBALL));
        context.registerObject("sphere-post", (spawn, registry) ->
                Sonic3kStarPostFactory.invitation(spawn, BonusStageType.GLOWING_SPHERE));
        context.registerObject("brittle-log", (spawn, registry) ->
                AizLogBridgeFactory.create(spawn, new AizLogBridgeRecipe(10)));
        context.registerObject("exit-lookout-tree", (spawn, registry) ->
                AizHollowTreeFactory.create(spawn, AizTreeExitRecipe.EXIT_LOOKOUT));
        context.decodedLevelPatch("forest", nativeOpening(SecondStrikeRoutes.Bank.FOREST));
        context.decodedLevelPatch("fire", nativeOpening(SecondStrikeRoutes.Bank.FIRE_CONTINUATION));
        context.registerServiceBundle("difficulty", () -> {
            SecondStrikeStages stages = new SecondStrikeStages();
            return GameServiceBundle.builder()
                    .service(AizMinibossAttackPolicy.class, new SecondStrikeBosses())
                    .service(AizEndBossRecipe.class, AizEndBossRecipe.ALTERNATING_ARMS)
                    .service(AizBombingRunRecipe.class, AizBombingRunRecipe.STAGGERED_PORTS)
                    .service(GumballSpringPolicy.class, stages).build();
        });
        context.registerGamePatch(new Patch());
    }

    /** Separate inventory-gated plans make selection independent of mutable globals. */
    private static LevelPatch nativeOpening(SecondStrikeRoutes.Bank bank) {
        return LevelPatch.empty().nativePlacements().forZone(0)
                .whenInventory(objects -> SecondStrikeRoutes.bank(objects) == bank)
                .select(spawn -> SecondStrikeRoutes.invitation(bank, spawn) == BonusStageType.GUMBALL).bind("gumball-post")
                .select(spawn -> SecondStrikeRoutes.invitation(bank, spawn) == BonusStageType.GLOWING_SPHERE).bind("sphere-post")
                .select(spawn -> SecondStrikeRoutes.brittleLog(bank, spawn)).bind("brittle-log")
                .select(spawn -> spawn.ownerModId() == null && bank == SecondStrikeRoutes.Bank.FOREST && spawn.layoutIndex() == 150
                        && spawn.objectId() == 3 && spawn.x() == 0x2D00 && spawn.y() == 0x3CC).bind("exit-lookout-tree")
                .select(spawn -> "spotter".equals(SecondStrikeRoutes.enemyFactory(bank, spawn))).bind("spotter")
                .select(spawn -> "guard".equals(SecondStrikeRoutes.enemyFactory(bank, spawn))).bind("guard")
                .select(spawn -> SecondStrikeRoutes.splitFan(bank, spawn)).bind("artillery-split")
                .select(spawn -> "artillery".equals(SecondStrikeRoutes.enemyFactory(bank, spawn))).bind("artillery")
                .select(spawn -> "caterkiller".equals(SecondStrikeRoutes.enemyFactory(bank, spawn))).bind("caterkiller")
                .select(spawn -> SecondStrikeRoutes.inOpening(bank, spawn) && SecondStrikeMonitors.replaces(spawn)).bind("robotnik-monitor")
                .retainRings(SecondStrikeRoutes::rings);
    }

    public static final class Patch implements GamePatch {
        @Override public String id() { return "second-strike:campaign"; }
        @Override public String displayName() { return "Second Strike"; }
        @Override public String baseGameId() { return "s3k"; }
        @Override public boolean activatesFor(GameplayLaunchRequest request) {
            // Match the backing content patch: every selected S3K character sees
            // the same campaign identity. Native-character route qualification is separate.
            return request.gameId().equals("s3k");
        }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(LogicalRom.S3K); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) {
            return new Module(base);
        }
    }

    /** All campaign, save, stage, game-over and input ownership stays delegated. */
    public static final class Module extends DelegatingGameModule {
        private TitleScreenProvider title;
        public Module(GameModule base) { super(base, "second-strike:campaign"); }
        @Override public TitleScreenProvider getTitleScreenProvider() {
            if (title == null) title = new SecondStrikeTitle(super.getTitleScreenProvider());
            return title;
        }
    }
}
