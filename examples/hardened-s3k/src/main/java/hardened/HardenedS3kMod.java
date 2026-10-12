package hardened;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.game.dataselect.DataSelectPresentationProvider;
import com.openggf.level.LevelPlacementPlan;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Maintained JVM encounter example. All native gameplay/art/audio owners are delegated. */
public final class HardenedS3kMod implements GgfMod {
    @Override public void register(ModContext context) {
        EncounterPlan.validatePattern(EncounterPlan.TELL_TICKS, EncounterPlan.PROJECTILE_CAP,
                EncounterPlan.PROJECTILE_LIFE_TICKS);
        context.registerObject("spore-sentry", (spawn, registry) -> new Sentry(spawn));
        context.registerObject("spore-shot", (spawn, registry) -> new Spore(spawn));
        context.registerLevelPlacementPlan(EncounterPlan.LEVEL_INDEX,
                new LevelPlacementPlan(new LevelPlacementPlan.Bounds(EncounterPlan.MIN_X, EncounterPlan.MIN_Y,
                        EncounterPlan.MAX_X, EncounterPlan.MAX_Y), EncounterPlan.retainedRings(),
                        List.of(new LevelPlacementPlan.ObjectAddition("spore-sentry", EncounterPlan.SENTRY_X,
                                EncounterPlan.SENTRY_Y, 0, 0)),
                        List.of(new LevelPlacementPlan.RingAddition(EncounterPlan.SAFE_RING_X,
                                EncounterPlan.SAFE_RING_Y))));
        context.registerGamePatch(new Patch());
    }

    public static final class Patch implements GamePatch {
        @Override public String id() { return "hardened-s3k:post-two"; }
        @Override public String displayName() { return "Post Two Ambush"; }
        @Override public String baseGameId() { return "s3k"; }
        @Override public boolean activatesFor(GameplayLaunchRequest request) { return request.gameId().equals("s3k"); }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(LogicalRom.S3K); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) {
            var config = context == null ? GameServices.configuration() : context.configService();
            requireSupported(config);
            requireSurveyedRom(context);
            if (base.gameplayFrameController() != null) throw new IllegalArgumentException(
                    "Post Two Ambush cannot share another gameplay mode. Disable the other mode and retry.");
            return new Module(base);
        }
        private static void requireSurveyedRom(PatchContext context) {
            // DevelopmentPatchLoader intentionally has no logical-ROM context;
            // reproducible development capture verifies the ROM identity outside
            // the mod. The normal launcher supplies its declared logical reader.
            if (context == null) return;
            try {
                var rom = context.openLogicalRom(LogicalRom.S3K);
                if (rom.size() != 0x400000) throw new IllegalArgumentException("Use the surveyed locked-on S3K ROM.");
                String actual = java.util.HexFormat.of().withUpperCase().formatHex(
                        java.security.MessageDigest.getInstance("SHA-1").digest(rom.slice(0, rom.size())));
                if (!actual.equals(EncounterPlan.ROM_SHA1)) throw new IllegalArgumentException(
                        "This encounter was surveyed for the locked-on S3K World ROM. Select that ROM and retry.");
            } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
                throw new IllegalStateException("The encounter ROM could not be verified. Select the ROM and retry.", failure);
            }
        }
        private static void requireSupported(SonicConfigurationService config) {
            var requested = GameplayLaunchRequest.fromConfig(config, "s3k");
            if (!requested.mainCharacter().equals("sonic") || !requested.sidekicks().isEmpty()
                    || config.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED)
                    || WidescreenAspect.parse(config.getString(SonicConfiguration.DISPLAY_ASPECT)).pixelWidth() != 320) {
                throw new IllegalArgumentException("Post Two Ambush supports solo Sonic, donor off and a 320px view."
                        + " Use the example launcher or select those options, then retry.");
            }
        }
    }

    public static final class Module extends DelegatingGameModule {
        private final EncounterState encounter = new EncounterState();
        private final Canvas canvas = new Canvas();
        private final AmbushFlow flow = new AmbushFlow(encounter, canvas);
        private final Title title = new Title(canvas, flow::newLaunch);
        private final LevelInputOverlay input = handler -> {
            flow.attachInput(handler);
            return flow.ownsMenuInput(handler);
        };
        public Module(GameModule base) { super(base, "hardened-s3k:post-two"); }
        @Override public boolean requiresNoSaveSession() { return true; }
        @Override public Optional<LevelStartPosition> freshLevelStartPosition(int zone, int act) {
            return zone == 7 && act == 0
                    ? Optional.of(new LevelStartPosition(EncounterPlan.START_X, EncounterPlan.START_Y))
                    : Optional.empty();
        }
        @Override public TitleScreenProvider getTitleScreenProvider() { return title; }
        @Override public boolean suppressesLevelSelect() { return true; }
        @Override public DataSelectProvider getDataSelectProvider() { return null; }
        @Override public DataSelectPresentationProvider getDataSelectPresentationProvider() { return null; }
        @Override public com.openggf.game.mode.GameplayFrameController gameplayFrameController() { return flow; }
        @Override public List<com.openggf.game.rewind.RewindSnapshottable<?>> rewindAdapters() {
            var result = new java.util.ArrayList<com.openggf.game.rewind.RewindSnapshottable<?>>(super.rewindAdapters());
            result.add(encounter); result.add(flow); return List.copyOf(result);
        }
        @Override public <T> T getGameService(Class<T> type) {
            if (type == EncounterState.class) return type.cast(encounter);
            if (type == LevelInputOverlay.class) return type.cast(input);
            return super.getGameService(type);
        }
    }
}
