package chaos;

import com.openggf.audio.GameSound;
import com.openggf.control.*;
import com.openggf.game.*;
import com.openggf.mods.ModStorage;
import com.openggf.mods.mutators.*;
import com.openggf.mods.state.SnapshotRandom;
import java.util.*;

/** Exercises controller callbacks against a strict detached lease port, never a substitute world. */
public final class ChaosControllerChecks {
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static final class Memory implements ModStorage {
        final Map<String,String> files=new HashMap<>();boolean writable=true;
        @Override public Optional<String> read(String name) {return Optional.ofNullable(files.get(name));}
        @Override public boolean write(String name,String text) {if(!writable)return false;files.put(name,text);return true;}
        @Override public Optional<byte[]> readBytes(String name) {return Optional.empty();}
        @Override public boolean writeBytes(String name,byte[] value) {return false;}
        @Override public boolean delete(String name) {return files.remove(name)!=null;}
        @Override public List<String> list() {return List.copyOf(files.keySet());}
    }
    private static final class Port implements TemporaryMutators {
        final Map<String,MutatorDefinition> definitions=new HashMap<>();
        final Map<Long,ActiveActivation> leases=new LinkedHashMap<>();
        final Map<Long,ActivationOutcome> results=new LinkedHashMap<>();
        Optional<com.openggf.mods.scene.SceneRomArt> art=Optional.empty();
        int activations,replacements,cancellations,cues,reward;
        Port(ChaosCatalog catalogue) {ChaosDefinitions.create(catalogue).forEach(d->definitions.put(d.localId(),d));}
        private void validate(String id,Map<String,Object> values) {
            var definition=definitions.get(id);check(definition!=null,"Unknown registered family");
            check(values.keySet().equals(definition.defaults().keySet()),"Unknown or missing public option for "+id);
            values.forEach((key,value)->definition.option(key).validate(value));
            var policies=definition.factory().prepare(new MutatorOptions(values));
            check(policies.stream().map(MutatorPolicy::capability).collect(java.util.stream.Collectors.toSet()).equals(definition.capabilities()),"Variable capability set");
        }
        @Override public ActivationResult activate(String id,Map<String,Object> values,long duration) {
            validate(id,values);long number=++activations;
            leases.put(number,new ActiveActivation(number,id,values,0,duration,duration==0,""));
            return new ActivationResult(true,number,"");
        }
        @Override public ActivationResult replace(long id,Map<String,Object> values) {
            var previous=leases.get(id);if(previous==null)return new ActivationResult(false,id,"expired");
            validate(previous.localDefinitionId(),values);replacements++;
            leases.put(id,new ActiveActivation(id,previous.localDefinitionId(),values,previous.agePresentations(),previous.remainingPresentations(),previous.oneShot(),previous.status()));
            return new ActivationResult(true,id,"");
        }
        @Override public boolean cancel(long id) {cancellations++;return leases.remove(id)!=null;}
        @Override public List<ActiveActivation> active() {return List.copyOf(leases.values());}
        @Override public List<ActivationOutcome> outcomes() {return List.copyOf(results.values());}
        @Override public List<Availability> available() {return definitions.keySet().stream().map(id->new Availability(id,true,"")).toList();}
        @Override public void playSfx(GameSound sound) {cues++;}
        @Override public Optional<com.openggf.mods.scene.SceneRomArt> nativeArt() {return art;}
        @Override public ActivationResult complete(long id,boolean won) {
            var lease=leases.get(id);if(lease==null)return new ActivationResult(false,id,"inactive");
            int amount=definitions.get(lease.localDefinitionId()).factory().prepare(new MutatorOptions(lease.options())).stream()
                    .filter(RingEconomyPolicy.class::isInstance).map(RingEconomyPolicy.class::cast)
                    .filter(p->p.kind()==RingEconomyPolicy.Kind.COMPLETION_REWARD).mapToInt(RingEconomyPolicy::amount).sum();
            leases.remove(id);if(won)reward+=amount;return new ActivationResult(true,id,"completion submitted");
        }
    }
    /** Controller-only fixture. Native ROM decoding has a separate real-ROM check. */
    private static com.openggf.mods.scene.SceneRomArt fixtureArt() {
        int[] pixels=new int[64];Arrays.fill(pixels,0xFFFFCC00);
        var image=new com.openggf.mods.scene.SceneImage(8,8,pixels);
        var sprites=new com.openggf.mods.scene.SceneSpriteSet() {
            @Override public int frameCount() {return 256;}
            @Override public com.openggf.mods.scene.SceneSprite frame(int frame) {return com.openggf.mods.scene.SceneSprite.of(image);}
            @Override public int[] animationFrames(int id) {return new int[]{1};}
            @Override public int animationDelay(int id) {return 8;}
        };
        return (com.openggf.mods.scene.SceneRomArt)java.lang.reflect.Proxy.newProxyInstance(
                com.openggf.mods.scene.SceneRomArt.class.getClassLoader(),new Class<?>[]{com.openggf.mods.scene.SceneRomArt.class},(proxy,method,args)->switch(method.getName()) {
                    case "gameId" -> "s3k";
                    case "romSha1" -> "cfbf98c36c776677290a872547ac47c53d2761d6";
                    case "palette" -> new int[(Integer)args[1]];
                    case "sprites","character" -> sprites;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
    private static GameplayObservation observe(long presentation,long ticks,GameplayObservation.Phase phase,int speed,LogicalInputSnapshot input) {
        var leader=new GameplayObservation.Player("sonic",512,512,speed,0,speed,50,false,false,false,false,false,false,"");
        return new GameplayObservation(1,1,presentation,ticks,phase,0,0,320,224,Optional.of(leader),List.of(leader),input,LevelLoadCause.FULL_LEVEL_ASSEMBLY);
    }
    private static GameplayObservation observe(long presentation,long ticks,GameplayObservation.Phase phase) {
        return observe(presentation,ticks,phase,0,LogicalInputSnapshot.neutral());
    }
    private static ChaosController controller(ChaosCatalog catalogue,String effect) {
        var preferences=new ChaosPreferences(new Memory(),catalogue);
        check(preferences.save(new ChaosSettings(5,4,Set.of(effect),true,17)),"Cannot publish fixture settings");
        return new ChaosController(catalogue,preferences);
    }
    public static void factoriesAdmitEveryEffectAndPreserveFixedCapabilities() {
        var catalogue=new ChaosCatalog();var port=new Port(catalogue);
        check(port.definitions.size()==27,"Definition grouping changed");
        for(var effect:catalogue.effects())for(int seed=1;seed<=8;seed++) {
            var values=ChaosDefinitions.options(effect,catalogue.sample(effect,new SnapshotRandom(seed)),effect.duration(seed%12+1));
            port.validate(effect.family(),values);
            var policies=port.definitions.get(effect.family()).factory().prepare(new MutatorOptions(values));
            if(effect.number()==20)check(policies.equals(List.of(new MutatorPolicy.Ringfall(100,0,true))),"Full spill changed to ordinary native scatter");
            if(effect.sidegame())check(policies.stream().anyMatch(GameplayHoldPolicy.class::isInstance),"Sidegame omitted bounded host hold");
            if(effect.number()==19 || effect.number()==90)check(policies.getFirst() instanceof InputControlPolicy,"Fresh-jump rules escaped input ownership");
        }
    }
    public static void presentationClockIgnoresNativeTickMultiplicityAndBoundaryRevokes() {
        var catalogue=new ChaosCatalog();var controller=controller(catalogue,"brake-check");var port=new Port(catalogue);
        for(int n=0;n<500;n++)controller.onPresentation(observe(n,n,GameplayObservation.Phase.SETUP),port);
        check(controller.schedule().clock()==0,"Setup advanced Chaos");
        for(int n=1;n<=600;n++) {
            controller.onPresentation(observe(n,0,GameplayObservation.Phase.ACTIVE),port);
            controller.onTick(observe(n,n*4L,GameplayObservation.Phase.ACTIVE),port);
            controller.onPresentation(observe(n,n*4L,GameplayObservation.Phase.ACTIVE),port);
        }
        check(controller.schedule().clock()==600 && port.activations==1,"Native pacing caused zero or extra Chaos draws");
        check(port.active().getFirst().oneShot(),"One-shot did not retain zero-tick delivery");
        for(int n=601;n<=650;n++)controller.onPresentation(observe(n,0,GameplayObservation.Phase.PAUSED),port);
        check(controller.schedule().clock()==600 && port.activations==1,"Pause aged selection or repeated a one-shot");
        controller.onBoundary(observe(650,0,GameplayObservation.Phase.TRANSITION),port);
        check(port.leases.isEmpty() && controller.schedule().active().isEmpty(),"Boundary retained an owned lease");
        check(controller.schedule().countdown()==600,"Boundary omitted five-second grace");
        controller.close();controller.onPresentation(observe(999,999,GameplayObservation.Phase.ACTIVE),port);
        check(controller.schedule().clock()==600,"Closed observer ran again");
    }
    public static void dynamicPoliciesReplaceIdentityAndKeepAdmittedExpiry() {
        var catalogue=new ChaosCatalog();var head=controller(catalogue,"pulse-head");var port=new Port(catalogue);
        for(int n=1;n<=600;n++)head.onPresentation(observe(n,0,GameplayObservation.Phase.ACTIVE),port);
        var first=port.active().getFirst();int percent=(Integer)first.options().get("percent");
        for(int n=601;n<=630;n++)head.onPresentation(observe(n,n,GameplayObservation.Phase.ACTIVE),port);
        var revised=port.active().getFirst();
        check(first.activationId()==revised.activationId() && first.remainingPresentations()==revised.remainingPresentations(),"Envelope replaced its lease/expiry");
        check(port.replacements>1 && percent!=(Integer)revised.options().get("percent"),"Head pulse is only static metadata");
        var motion=controller(catalogue,"time-moves-with-sonic");var time=new Port(catalogue);
        for(int n=1;n<=600;n++)motion.onPresentation(observe(n,0,GameplayObservation.Phase.ACTIVE),time);
        check(time.active().getFirst().options().get("percent").equals(25),"Stopped pacing lost its nonzero floor");
        motion.onPresentation(observe(601,1,GameplayObservation.Phase.ACTIVE,0x600,LogicalInputSnapshot.neutral()),time);
        check(time.active().getFirst().options().get("percent").equals(200),"Movement did not change pacing");
    }
    public static void heldSidegameCapturesAndCancellationReleasesAuthority() {
        var catalogue=new ChaosCatalog();var controller=controller(catalogue,"ring-snake");var port=new Port(catalogue);
        port.art=Optional.of(fixtureArt());
        for(int n=1;n<=600;n++)controller.onPresentation(observe(n,n,GameplayObservation.Phase.ACTIVE),port);
        check(port.active().getFirst().localDefinitionId().equals("sidegame"),"Optional game never acquired hold");
        for(int n=0;n<8;n++)controller.onPresentation(observe(600,600,GameplayObservation.Phase.HELD),port);
        var captured=controller.capture();check(captured.sidegame()!=null && captured.sidegame().age()==8,"Held presentation did not run the game");
        for(int n=0;n<4;n++)controller.onPresentation(observe(600,600,GameplayObservation.Phase.PAUSED),port);
        check(controller.capture().sidegame().age()==8 && controller.schedule().clock()==600,"Pause progressed sidegame or selection");
        for(int n=0;n<8;n++)controller.onPresentation(observe(600,600,GameplayObservation.Phase.HELD),port);
        var forward=controller.capture();controller.restore(captured);
        for(int n=0;n<8;n++)controller.onPresentation(observe(600,600,GameplayObservation.Phase.HELD),port);
        check(Arrays.equals(forward.sidegame().board(),controller.capture().sidegame().board()),"Rewind changed actual held-game movement");
        controller.requestCancelSidegame();controller.onPresentation(observe(600,600,GameplayObservation.Phase.HELD),port);
        check(!controller.sidegameActive() && port.leases.isEmpty() && controller.capture().quietUntil()==0,"Cancellation trapped or rewarded player");
    }
    public static void titlePublishesChoiceBeforeNativeOnePlayerExit() {
        var catalogue=new ChaosCatalog();var memory=new Memory();var preferences=new ChaosPreferences(memory,catalogue);
        var controller=new ChaosController(catalogue,preferences);var menu=controller.menu();
        var stock=new TitleScreenProvider() {
            @Override public void initialize() { }
            @Override public void update(InputHandler input) {throw new AssertionError("Modal leaked input to stock title");}
            @Override public void draw() { }
            @Override public void setClearColor() { }
            @Override public void reset() { }
            @Override public State getState() {return State.ACTIVE;}
            @Override public boolean isExiting() {return false;}
            @Override public boolean isActive() {return true;}
        };
        var title=new ChaosTitle(stock,menu);title.initialize();var input=new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0,0,1,1,false,false),PlayerInputState.neutral()));
        memory.writable=false;title.update(input);
        check(!title.isExiting() && !preferences.saved().enabled() && !memory.files.containsKey("settings.txt"),"Failed save launched or changed admitted preferences");
        memory.writable=true;title.update(input);
        check(title.isExiting() && preferences.saved().enabled() && memory.files.containsKey("settings.txt"),"Choice did not save before exit");
        check(title.consumeExitAction()==TitleScreenProvider.TitleScreenAction.ONE_PLAYER,"Launch bypasses stock data-select route");
        title.reset();check(!menu.title() && !menu.open(),"Title modal leaked into data select");
    }
    public static void missingNativeArtExcludesGamesAndPongWinSubmitsOneDeclaredReward() {
        var catalogue=new ChaosCatalog();var absent=controller(catalogue,"ring-pong");var empty=new Port(catalogue);
        for(int n=1;n<=700;n++)absent.onPresentation(observe(n,n,GameplayObservation.Phase.ACTIVE),empty);
        check(empty.activations==0 && !absent.qualification(catalogue.effect(101)).isEmpty(),"Missing ROM art used a fake fallback or unexplained admission");
        var pong=controller(catalogue,"ring-pong");var port=new Port(catalogue);port.art=Optional.of(fixtureArt());
        for(int n=1;n<=600;n++)pong.onPresentation(observe(n,n,GameplayObservation.Phase.ACTIVE),port);
        pong.onPresentation(observe(600,600,GameplayObservation.Phase.HELD),port);
        for(int n=0;n<1200 && pong.sidegameActive();n++) {
            var state=pong.capture().sidegame().state();int target=(state[3]>>8)-12,direction=target>state[0]?2:target<state[0]?1:0;
            var input=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(direction,direction,0,0,false,false),PlayerInputState.neutral());
            pong.onPresentation(observe(600,600,GameplayObservation.Phase.HELD,0,input),port);
        }
        check(!pong.sidegameActive() && port.reward==5 && port.leases.isEmpty(),"Actual paddle play failed to submit its declared five-ring completion once");
        for(int n=0;n<20;n++)pong.onPresentation(observe(600,600,GameplayObservation.Phase.HELD),port);
        check(port.reward==5,"Repeated callback submitted a duplicate win reward");
    }
    public static void moduleRetainsNativeDataSelectAndInjectedSupport() {
        var catalogue=new ChaosCatalog();var controller=controller(catalogue,"bobble-head");
        var nativeData=NoOpDataSelectProvider.INSTANCE;
        var base=(GameModule)java.lang.reflect.Proxy.newProxyInstance(GameModule.class.getClassLoader(),new Class<?>[]{GameModule.class},(proxy,method,args)->switch(method.getName()) {
            case "getGameService" -> args[0]==ChaosController.class?controller:null;
            case "getDataSelectProvider" -> nativeData;
            case "getTitleScreenProvider" -> NoOpTitleScreenProvider.INSTANCE;
            case "supportsSidekick" -> true;
            case "requiredDisplayAspect" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        var configuration=com.openggf.configuration.SonicConfigurationService.createStandalone();
        configuration.setSessionOverride(com.openggf.configuration.SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
        var context=new com.openggf.game.patch.PatchContext(rom->{throw new java.io.IOException("No ROM open needed");},configuration);
        var module=new ChaosMod.Module(base,context);
        check(module.getDataSelectProvider()==nativeData && module.supportsSidekick(),"Patch replaced native save/team flow");
        check(module.getTitleScreenProvider() instanceof ChaosTitle,"Explicit title choice missing");
        var support=module.getGameService(MutatorSupportProfile.class);
        check(!support.capabilities(0,0).isEmpty() && !support.supportsPlayer(MutatorCapability.DRY_SONIC_GRAVITY,"tails",true),"Native character qualification missing");
        configuration.setSessionOverride(com.openggf.configuration.SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,true);
        check(support.capabilities(0,0).isEmpty(),"Support read ambient configuration instead of patch context");
    }
    public static void terminalResultsSurviveLeaseRemovalAndRewardOnceAcrossReplay() {
        var catalogue=new ChaosCatalog();var controller=controller(catalogue,"brake-check");var port=new Port(catalogue);
        for(int n=1;n<600;n++)controller.onPresentation(observe(n,n,GameplayObservation.Phase.ACTIVE),port);
        var before=controller.capture();
        // Native completion on the final tick can precede lease expiry and this callback.
        port.results.put(41L,new TemporaryMutators.ActivationOutcome(41,"challenge","Complete: Ring target reached"));
        controller.onPresentation(observe(600,600,GameplayObservation.Phase.ACTIVE),port);
        check(port.activations==0 && controller.capture().quietUntil()==899,"Final-tick completion was lost or allowed the due draw");
        var rewarded=controller.capture();
        for(int n=601;n<=650;n++)controller.onPresentation(observe(n,n,GameplayObservation.Phase.ACTIVE),port);
        check(controller.capture().quietUntil()==899,"Retained completion renewed respite every frame");
        var forward=controller.capture();controller.restore(rewarded);
        for(int n=601;n<=650;n++)controller.onPresentation(observe(n,n,GameplayObservation.Phase.ACTIVE),port);
        check(forward.equals(controller.capture()),"Rewind replay duplicated terminal respite or lost captured receipt tracking");
        controller.restore(before);
        controller.onPresentation(observe(600,600,GameplayObservation.Phase.ACTIVE),port);
        check(rewarded.equals(controller.capture()),"Replaying a newly delivered completion changed its captured result");
        // One-shot native acknowledgement removes its lease before publishing failure to the UI.
        port.results.put(42L,new TemporaryMutators.ActivationOutcome(42,"player-motion","Failed: No safe grounded opportunity"));
        controller.onPresentation(observe(601,601,GameplayObservation.Phase.ACTIVE),port);
        var failed=controller.capture();
        check(failed.notice().equals("Failed: No safe grounded opportunity") && failed.noticeUntil()==900,"Acknowledged failure vanished without a readable notice");
        for(int n=602;n<=610;n++)controller.onPresentation(observe(n,n,GameplayObservation.Phase.ACTIVE),port);
        check(controller.capture().noticeUntil()==900 && controller.capture().quietUntil()==899,"Repeated failure/result extended feedback or earned respite");
        controller.restore(failed);
        for(int n=602;n<=610;n++)controller.onPresentation(observe(n,n,GameplayObservation.Phase.ACTIVE),port);
        check(controller.capture().noticeUntil()==900,"Rewind replay renewed a seen failure notice");
        // A receipt evicted from the FIFO still has an active Complete status.
        port.results.remove(41L);
        port.leases.put(41L,new TemporaryMutators.ActiveActivation(41,"challenge",Map.of(),0,30,false,"Complete: Ring target reached"));
        controller.onPresentation(observe(611,611,GameplayObservation.Phase.ACTIVE),port);
        check(controller.capture().quietUntil()==899,"Active terminal status lost receipt deduplication");
        controller.onBoundary(observe(612,612,GameplayObservation.Phase.TRANSITION),port);
        check(controller.capture().terminalSeen().isEmpty() && controller.capture().quietUntil()==0,"Boundary retained terminal outcome history");
    }
    public static void sceneryUsesPreparedDonorsAndExcludesNativeZone() {
        var catalogue=new ChaosCatalog();var allowed=Set.of(0,1,6,11);
        for(int zone=-1;zone<14;zone++) {
            var random=new SnapshotRandom(17);long captured=random.snapshot();var sampled=new ArrayList<Map<String,Object>>();
            var seen=new HashSet<Integer>();
            for(int n=0;n<256;n++) {
                var values=catalogue.sample(catalogue.effect(99),random,zone);sampled.add(values);
                int source=(Integer)values.get("scenery");seen.add(source);
                check(allowed.contains(source) && source!=zone,"Scenery sampler selected an unsupported or unchanged donor");
            }
            var expected=new HashSet<>(allowed);expected.remove(zone);
            check(seen.equals(expected),"A prepared donor disappeared from the captured pool");
            random.restore(captured);
            for(var values:sampled)check(values.equals(catalogue.sample(catalogue.effect(99),random,zone)),"Scenery sampling changed after RNG restore");
        }
        for(int zone:allowed) {
            var controller=controller(catalogue,"wrong-zone-scenery");var port=new Port(catalogue);
            for(int n=1;n<=600;n++) {
                var base=observe(n,n,GameplayObservation.Phase.ACTIVE);
                var current=new GameplayObservation(base.worldGeneration(),base.visitGeneration(),base.activePresentations(),base.nativeTicks(),base.phase(),zone,0,
                        base.viewportWidth(),base.viewportHeight(),base.leader(),base.team(),base.input(),base.loadCause());
                controller.onPresentation(current,port);
            }
            int source=(Integer)port.active().getFirst().options().get("scenery");
            check(allowed.contains(source) && source!=zone,"Scenery selected an unsupported or unchanged native donor");
        }
    }
}
