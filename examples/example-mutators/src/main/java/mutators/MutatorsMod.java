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
        context.registerMutator(Stealth.definition());
        context.registerGamePatch(new Patch());
    }
    public static final class Patch implements GamePatch {
        @Override public String id() { return "example-mutators:lab"; }
        @Override public String displayName() { return "Mutator Lab"; }
        @Override public String baseGameId() { return "s2"; }
        @Override public boolean activatesFor(GameplayLaunchRequest request) {
            return request.gameId().equals("s2") && request.mainCharacter().equals("sonic");
        }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(LogicalRom.S2); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) { return new Module(base); }
    }
    public static final class Module extends DelegatingGameModule {
        private MutatorConfigurationScreen screen;
        private final MutatorSupportProfile support = new MutatorSupportProfile() {
            @Override public Set<MutatorCapability> capabilities(int zone,int act) {
                return zone==0 && act==0 && !GameServices.configuration().getBoolean(
                        com.openggf.configuration.SonicConfiguration.CROSS_GAME_FEATURES_ENABLED) ? Set.of(MutatorCapability.DRY_SONIC_GRAVITY,MutatorCapability.PLAYER_STEALTH) : Set.of();
            }
            @Override public boolean supportsPlayer(String key,boolean leader) { return leader && key.equals("sonic"); }
        };
        public Module(GameModule base) { super(base,"example-mutators:lab"); }
        private MutatorConfigurationScreen screen() {
            if(screen==null) screen=new MutatorConfigurationScreen(GameServices.worldSession(),"MUTATOR LAB",
                    super.getTitleScreenProvider(),cue -> {
                        var audio=GameServices.audio();
                        // Sonic 2 Obj0F menu blip; SndID_Ring confirms; LevelSelect2P error refuses.
                        switch(cue) {
                            case NAVIGATE -> audio.playSfx(0xCD);
                            case CONFIRM -> audio.playSfx(0xB5);
                            case START -> audio.playSfx(com.openggf.audio.GameSound.SPINDASH_RELEASE);
                            case ERROR -> audio.playSfx(0xED);
                        }
                    });
            return screen;
        }
        @Override public TitleScreenProvider getTitleScreenProvider() { return screen(); }
        @Override public String requiredDisplayAspect() { return "NATIVE_4_3"; }
        @Override public boolean suppressesLevelSelect() { return true; }
        @Override public boolean supportsSidekick() { return false; }
        @Override public boolean isSidekickSuppressedForZone(int zone) { return true; }
        @Override public <T> T getGameService(Class<T> type) {
            if(type==LevelInputOverlay.class) return type.cast(screen());
            if(type==MutatorSupportProfile.class) return type.cast(support);
            return super.getGameService(type);
        }
    }
}
