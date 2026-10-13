package chaos;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.mods.code.*;
import com.openggf.mods.mutators.*;
import java.util.*;

/** Stock S3K is decorated, not replaced; each applied module gets a fresh captured run. */
public final class ChaosMod implements GgfMod {
    public static final String ID="sonic3k-chaos-mode";
    @Override public void register(ModContext context) {
        var catalogue=new ChaosCatalog();
        ChaosDefinitions.create(catalogue).forEach(context::registerMutator);
        var storage=context.storage();
        context.registerServiceBundle("run",()->{
            var controller=new ChaosController(catalogue,new ChaosPreferences(storage,catalogue));
            return GameServiceBundle.builder().service(ChaosController.class,controller)
                    .gameplayObserver("state",controller).build();
        });
        context.registerGamePatch(new Patch());
    }
    public static final class Patch implements GamePatch {
        @Override public String id() {return ID+":native";}
        @Override public String displayName() {return "Sonic3K Chaos Mode";}
        @Override public String baseGameId() {return "s3k";}
        @Override public boolean activatesFor(GameplayLaunchRequest request) {
            return request.gameId().equals("s3k") && Set.of("sonic","tails","knuckles").contains(request.mainCharacter());
        }
        @Override public Set<LogicalRom> romPrerequisites() {return Set.of(LogicalRom.S3K);}
        @Override public List<String> providedMainCharacters() {return List.of();}
        @Override public GameModule apply(GameModule base,PatchContext context) {return new Module(base,context);}
    }
    public static final class Module extends DelegatingGameModule {
        private final ChaosController controller;
        private final MutatorSupportProfile support;
        private ChaosTitle title;
        public Module(GameModule base,PatchContext context) {
            super(base,ID+":native");
            controller=Objects.requireNonNull(base.getGameService(ChaosController.class),"registered Chaos run");
            var configuration=Objects.requireNonNull(context,"patch context").configService();
            controller.menu().configuration(configuration);
            support=new NativeSupport(()->configuration.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
        }
        @Override public TitleScreenProvider getTitleScreenProvider() {
            if(title==null)title=new ChaosTitle(super.getTitleScreenProvider(),controller.menu());
            return title;
        }
        @Override public <T> T getGameService(Class<T> type) {
            if(type==LevelInputOverlay.class)return type.cast(controller.menu());
            if(type==MutatorSupportProfile.class)return type.cast(support);
            return super.getGameService(type);
        }
        // Inherit native DataSelectProvider, progression, character/team selection and level select.
    }
    public static final class NativeSupport implements MutatorSupportProfile {
        private final java.util.function.BooleanSupplier donorActive;
        public NativeSupport(java.util.function.BooleanSupplier donorActive) {this.donorActive=Objects.requireNonNull(donorActive);}
        @Override public Set<MutatorCapability> capabilities(int zone,int act) {
            return donorActive.getAsBoolean() || zone<0 || zone>13 || act<0 || act>1?Set.of():Set.copyOf(EnumSet.allOf(MutatorCapability.class));
        }
        @Override public boolean supportsPlayer(String key,boolean leader) {return Set.of("sonic","tails","knuckles").contains(key);}
        @Override public boolean supportsPlayer(MutatorCapability capability,String key,boolean leader) {
            return capability==MutatorCapability.DRY_SONIC_GRAVITY?key.equals("sonic"):supportsPlayer(key,leader);
        }
        @Override public String startLabel() {return "Start Chaos / native data select";}
        @Override public String locationLabel(int zone,int act) {return "Sonic 3 & Knuckles / Zone "+(zone+1)+" / Act "+(act+1);}
    }
}
