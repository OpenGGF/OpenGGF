package secondstrike;

import com.openggf.game.GameModule;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy;
import com.openggf.game.sonic3k.difficulty.BlueSphereLayoutPolicy;
import com.openggf.game.sonic3k.difficulty.GumballSpringPolicy;
import com.openggf.level.LevelPatch;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import secondstrike.boss.SecondStrikeBosses;
import secondstrike.enemies.SecondStrikeEnemies;
import secondstrike.monitors.SecondStrikeMonitors;
import secondstrike.routes.SecondStrikeRoutes;
import secondstrike.stages.SecondStrikeStages;

import java.util.List;
import java.util.Set;

/** A difficulty layer over the ordinary S3K campaign, with no alternate frame driver. */
public final class SecondStrikeMod implements GgfMod {
    @Override public void register(ModContext context) {
        SecondStrikeEnemies.register(context);
        SecondStrikeMonitors.register(context);
        context.decodedLevelPatch("forest", nativeOpening(SecondStrikeRoutes.Bank.FOREST));
        context.decodedLevelPatch("fire", nativeOpening(SecondStrikeRoutes.Bank.FIRE_CONTINUATION));
        context.registerServiceBundle("difficulty", () -> {
            SecondStrikeStages stages = new SecondStrikeStages();
            return GameServiceBundle.builder()
                    .service(AizMinibossAttackPolicy.class, new SecondStrikeBosses())
                    .service(BlueSphereLayoutPolicy.class, stages)
                    .service(GumballSpringPolicy.class, stages).build();
        });
        context.registerGamePatch(new Patch());
    }

    /** Separate inventory-gated plans make selection independent of mutable globals. */
    private static LevelPatch nativeOpening(SecondStrikeRoutes.Bank bank) {
        java.util.function.Predicate<com.openggf.level.objects.ObjectSpawn> eligible = spawn ->
                spawn.ownerModId() == null && (bank == SecondStrikeRoutes.Bank.FOREST || spawn.x() < 0x1000);
        return LevelPatch.empty().nativePlacements().forZone(0)
                .whenInventory(objects -> SecondStrikeRoutes.bank(objects) == bank)
                .select(spawn -> eligible.test(spawn) && spawn.objectId() == 0x8E).bind("spotter")
                .select(spawn -> eligible.test(spawn) && spawn.objectId() == 0x8D).bind("guard")
                .select(spawn -> eligible.test(spawn) && spawn.objectId() == 0x8C).bind("artillery")
                .select(spawn -> eligible.test(spawn) && SecondStrikeMonitors.replaces(spawn)).bind("robotnik-monitor")
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
