package mutators;

import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.mods.code.*;
import com.openggf.mods.mutators.*;
import java.util.List;
import java.util.Set;

/** Boot preparation and per-launch presentation only; no globals or creator world mutation callback. */
public final class MutatorsMod implements GgfMod {
    @Override public void register(ModContext context) {
        context.registerMutator(Gravity.definition());
        context.registerMutator(Ringfall.definition());
        context.registerMutator(BigHead.definition());
        context.registerMutator(Stealth.definition());
        context.registerMutator(NoPowerups.definition());
        context.registerMutator(LevelFilters.checkpoints());
        context.registerMutator(LevelFilters.rings());
        context.registerMutator(GameSpeed.definition());
        context.registerMutator(LevelFilters.specialStages());
        context.registerMutator(LevelFilters.bonusStages());
        context.registerMutator(ViolentExplosions.definition());
        for (String game : List.of("s1", "s2", "s3k")) context.registerGamePatch(new Patch(game));
    }
    public static final class Patch implements GamePatch {
        private final NativeLabProfile profile;
        public Patch() { this("s2"); }
        public Patch(String game) { profile = new NativeLabProfile(game); }
        @Override public String id() { return profile.patchId(); }
        @Override public String displayName() { return "Mutator Lab / " + profile.gameTitle(); }
        @Override public String baseGameId() { return profile.game(); }
        @Override public boolean activatesFor(GameplayLaunchRequest request) {
            return request.gameId().equals(profile.game()) && request.mainCharacter().equals("sonic");
        }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(profile.rom()); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) { return new Module(base); }
    }
    public static final class Module extends DelegatingGameModule {
        private MutatorConfigurationScreen screen;
        private final NativeLabProfile support;
        public Module(GameModule base) {
            super(base,new NativeLabProfile(base.getGameId().code()).patchId());
            support = new NativeLabProfile(base.getGameId().code());
        }
        private MutatorConfigurationScreen screen() {
            if(screen==null) screen=new MutatorConfigurationScreen(GameServices.worldSession(),"MUTATOR LAB",
                    super.getTitleScreenProvider(),cue -> GameServices.audio().playSfx(support.cueId(cue)));
            return screen;
        }
        @Override public TitleScreenProvider getTitleScreenProvider() { return screen(); }
        /** The Lab's Start opens a fresh challenge; it never selects a stock save slot. */
        @Override public DataSelectProvider getDataSelectProvider() { return null; }
        @Override public String requiredDisplayAspect() { return "NATIVE_4_3"; }
        @Override public boolean suppressesLevelSelect() { return true; }
        @Override public <T> T getGameService(Class<T> type) {
            if(type==LevelInputOverlay.class) return type.cast(screen());
            if(type==MutatorSupportProfile.class) return type.cast(support);
            return super.getGameService(type);
        }
    }
}
