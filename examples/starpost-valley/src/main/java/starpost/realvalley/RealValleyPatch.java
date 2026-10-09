package starpost.realvalley;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.modzone.ModZoneRuntimeContribution;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.sonic1.Sonic1;
import com.openggf.level.Level;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Fills the valley's placeholder act with Green Hill, re-encoded from the player's Sonic 1 ROM at load
 * time. The engine applies a mod's content patch (which serves its registered zones) before the mod's
 * own patches, so this module wraps it: for the valley's level index it hands the host's additive-zone
 * adapter the re-encoded data instead of the placeholder's; every other level passes through.
 * Without Sonic 1 the placeholder loads unchanged.
 */
public final class RealValleyPatch implements GamePatch {
    @Override
    public String id() {
        return "real-valley";
    }

    @Override
    public String displayName() {
        return "Starpost Valley: the valley act";
    }

    @Override
    public String baseGameId() {
        return "s3k";
    }

    @Override
    public boolean activatesFor(GameplayLaunchRequest request) {
        return "s3k".equals(request.gameId());
    }

    @Override
    public Set<LogicalRom> romPrerequisites() {
        return Set.of();
    }

    @Override
    public Set<LogicalRom> optionalRomPrerequisites() {
        return Set.of(LogicalRom.S1);
    }

    @Override
    public List<String> providedMainCharacters() {
        return List.of();
    }

    @Override
    public GameModule apply(GameModule base, PatchContext context) {
        return new Module(base, context);
    }

    /** The decorated module; see {@link RealValleyPatch}. */
    public static final class Module extends DelegatingGameModule {
        private final PatchContext context;
        private S1Terrain terrain;

        Module(GameModule base, PatchContext context) {
            super(base, RealValley.OWNER + ":real-valley");
            this.context = context;
        }

        @Override
        public Level loadLevelOverride(int levelIndex) throws IOException {
            ModZoneRuntimeContribution valley = getZoneRegistry().modZoneRuntimeContribution(levelIndex);
            if (valley == null || !RealValley.OWNER.equals(valley.ownerModId())
                    || !RealValley.ZONE.equals(valley.localKey())) {
                return super.loadLevelOverride(levelIndex);
            }
            S1Terrain source = terrain();
            if (source == null) {
                return super.loadLevelOverride(levelIndex);
            }
            return base().getModZoneAdapter().load(RealValley.OWNER, ValleyLevel.build(valley.levelData(), source));
        }

        /** Green Hill act 1, read once from Sonic 1; null when Sonic 1 is not supplied. */
        private synchronized S1Terrain terrain() {
            if (terrain == null) {
                try (Rom rom = Rom.fromReader(openSonic1(), "Starpost Valley: Sonic 1")) {
                    terrain = S1TerrainReader.read(new Sonic1(rom).buildDetachedLevel(RealValley.S1_GHZ1_LEVEL_INDEX));
                } catch (IOException | RuntimeException failure) {
                    Logger.getLogger(Module.class.getName())
                            .warning("The valley act needs Sonic 1; loading its placeholder: " + failure);
                    return null;
                }
            }
            return terrain;
        }

        private RomByteReader openSonic1() throws IOException {
            if (context != null) {
                return context.openLogicalRom(LogicalRom.S1);
            }
            // Development launches apply patches without a context.
            return LogicalRomResolver.fromRomManager(GameServices.rom()).openOrThrow(LogicalRom.S1);
        }
    }
}
