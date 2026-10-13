package chaos;

import com.openggf.mods.mutators.*;
import java.util.*;

/** Factory-only host declarations. No factory accesses the world or samples a random stream. */
public final class ChaosDefinitions {
    private ChaosDefinitions() { }
    public static List<MutatorDefinition> create(ChaosCatalog catalogue) {
        var definitions=new ArrayList<MutatorDefinition>();
        for(String family:catalogue.families()) {
            var effects=catalogue.effects().stream().filter(e->e.family().equals(family)).toList();
            List<String> kinds=effects.stream().map(e->family.equals("object-field")?fieldKind(e):e.mode()).distinct().toList();
            var first=catalogue.sample(effects.getFirst(),new com.openggf.mods.state.SnapshotRandom(1));
            var defaults=new LinkedHashMap<>(first);defaults.put("kind",kinds.getFirst());
            int minPercent=family.equals("head")?100:family.equals("dry-gravity") || family.equals("pacing")?25:
                    family.equals("defeat-rebound")?150:family.equals("spring-response") || family.equals("sprite-presentation")?75:25;
            int maxPercent=family.equals("pacing")?400:family.equals("defeat-rebound")?300:family.equals("spring-response")?135:200;
            List<String> targets=family.equals("object-field")?List.of("ring","badnik","projectile"):
                    family.equals("sprite-presentation")?List.of("leader","all-team","badniks","badniks-and-pickups"):
                            List.of("leader","all-team");
            var options=new ArrayList<MutatorOption>();
            options.add(new MutatorOption.Choice("kind","Style","Validated semantic mode",MutatorScope.LIVE,kinds.getFirst(),kinds));
            options.add(new MutatorOption.IntegerSlider("percent","Strength","Bounded for the selected style",MutatorScope.LIVE,
                    (Integer)defaults.get("percent"),minPercent,maxPercent,1,"%"));
            options.add(new MutatorOption.IntegerSlider("cap","Rebound cap","Native vertical speed cap in pixels/tick",MutatorScope.LIVE,
                    (Integer)defaults.get("cap"),family.equals("defeat-rebound")?8:0,family.equals("defeat-rebound")?12:32,1,""));
            options.add(new MutatorOption.Choice("target","Target","Native player or qualified object target",MutatorScope.LIVE,(String)defaults.get("target"),targets));
            options.add(new MutatorOption.Choice("side","Direction","Native encounter side",MutatorScope.LIVE,"ahead",List.of("ahead","behind")));
            options.add(new MutatorOption.Checkbox("audio","Audio follows speed","Native pacing only",MutatorScope.LIVE,false));
            options.add(new MutatorOption.Checkbox("full","Full inventory spill","Remove the native scatter ceiling",MutatorScope.LIVE,(Boolean)defaults.get("full")));
            options.add(new MutatorOption.IntegerSlider("amount","Reward amount","Bounded semantic reward parameter",MutatorScope.LIVE,(Integer)defaults.get("amount"),0,50,1,""));
            options.add(new MutatorOption.IntegerSlider("period","Period","Logical eligible presentations",MutatorScope.LIVE,120,30,1800,30,""));
            options.add(new MutatorOption.IntegerSlider("seed","Layout seed","Captured non-gameplay presentation seed",MutatorScope.LIVE,0,0,999999,1,""));
            options.add(new MutatorOption.IntegerSlider("duration","Native duration","Capped by the effect and native owner",MutatorScope.LIVE,(Integer)defaults.get("duration"),8,3600,1,""));
            options.add(new MutatorOption.IntegerSlider("cue","Native cue","Prepared S3K ROM music or sound ID",MutatorScope.LIVE,(Integer)defaults.get("cue"),0,255,1,""));
            options.add(new MutatorOption.IntegerSlider("delay","Input delay","Admitted native ticks",MutatorScope.LIVE,6,6,9,1,""));
            options.add(new MutatorOption.IntegerSlider("magnitude","Picture amount","Maximum, further bounded by style",MutatorScope.LIVE,(Integer)defaults.get("magnitude"),0,30,1,""));
            options.add(new MutatorOption.IntegerSlider("scenery","Scenery source","Prepared native ROM background bank",MutatorScope.LIVE,(Integer)defaults.get("scenery"),0,13,1,""));
            options.add(new MutatorOption.IntegerSlider("sequence","Gravity sequence","Captured four-step reversal mask",MutatorScope.LIVE,(Integer)defaults.get("sequence"),1,15,1,""));
            options.removeIf(option->!keys(family).contains(option.id()));
            var initial=new LinkedHashMap<String,Object>();options.forEach(o->initial.put(o.id(),o.defaultValue()));
            Set<MutatorCapability> capabilities=new HashSet<>();prepare(family,new MutatorOptions(initial)).forEach(p->capabilities.add(p.capability()));
            definitions.add(new MutatorDefinition(family,"Chaos "+family,"A parameterized Chaos-owned family; pool selection is independent of persistent enable flags.",
                    MutatorScope.LIVE,MutatorScope.LIVE,options,capabilities,values->prepare(family,values)));
        }
        return List.copyOf(definitions);
    }
    private static String fieldKind(ChaosCatalog.Effect effect) {
        String target=switch(effect.number()) {case 41,42,45->"ring";case 46,47->"projectile";default->"badnik";};
        return target+"-"+effect.mode();
    }
    public static Map<String,Object> options(ChaosCatalog.Effect effect,Map<String,Object> sampled,int duration) {
        var result=new LinkedHashMap<>(sampled);
        if(effect.family().equals("object-field")) result.put("kind",fieldKind(effect));
        result.put("duration",Math.clamp(duration==0?600:duration,8,3600));
        result.keySet().retainAll(keys(effect.family()));
        return Map.copyOf(result);
    }
    /** Only actual prepared parameters are public; native budgets stay with their semantic owner. */
    private static Set<String> keys(String family) {
        var keys=new HashSet<String>(Set.of("kind"));
        switch(family) {
            case "head" -> keys.addAll(Set.of("percent","target","period"));
            case "stealth" -> keys.add("target");
            case "sprite-presentation" -> keys.addAll(Set.of("percent","target","period"));
            case "scene-presentation" -> keys.addAll(Set.of("magnitude","period","scenery"));
            case "hud-presentation" -> keys.addAll(Set.of("period","seed"));
            case "dry-gravity","spring-response" -> keys.add("percent");
            case "signed-gravity" -> keys.addAll(Set.of("duration","sequence"));
            case "power-up" -> keys.add("duration");
            case "player-motion" -> keys.addAll(Set.of("percent","side"));
            case "pacing" -> keys.addAll(Set.of("percent","audio"));
            case "input-control" -> keys.add("delay");
            case "defeat-rebound" -> keys.addAll(Set.of("percent","cap"));
            case "encounter" -> keys.add("side");
            case "audio" -> keys.add("cue");
            case "sidegame","pong" -> keys.addAll(Set.of("duration","seed"));
            default -> { }
        }
        return keys;
    }
    private static String token(String value) {return value.replace('-','_').toUpperCase(Locale.ROOT);}
    private static List<MutatorPolicy> prepare(String family,MutatorOptions values) {
        String kind=values.choice("kind"),target=(String)values.values().getOrDefault("target","leader");
        int p=(Integer)values.values().getOrDefault("percent",100),period=(Integer)values.values().getOrDefault("period",120);
        MutatorPolicy.Target team=target.equals("all-team")?MutatorPolicy.Target.ALL_TEAM:MutatorPolicy.Target.LEADER;
        MutatorPolicy policy=switch(family) {
            case "head" -> new MutatorPolicy.BigHead(p,team,false);
            case "stealth" -> new MutatorPolicy.PlayerStealth(true,true,team,false);
            case "dry-gravity" -> new MutatorPolicy.DrySonicGravity(p);
            case "pacing" -> new MutatorPolicy.GameplaySpeed(p,values.checkbox("audio"));
            case "hurt-scatter" -> new MutatorPolicy.Ringfall(100,0,true);
            case "defeat-rebound" -> new MutatorPolicy.DefeatKnockback(p,Math.max(1,values.integer("cap"))<<8);
            case "sprite-presentation" -> {
                var mode=switch(kind) {case "tint"->SpritePresentationPolicy.Mode.RAINBOW;default->SpritePresentationPolicy.Mode.valueOf(token(kind));};
                var t=SpritePresentationPolicy.Target.valueOf(token(target));
                int strength=mode==SpritePresentationPolicy.Mode.SCALE?p:mode==SpritePresentationPolicy.Mode.SQUASH?Math.clamp(p,100,120):100;
                yield new SpritePresentationPolicy(mode,t,strength,period);
            }
            case "scene-presentation" -> {
                var mode=switch(kind) {case "mirror"->ScenePresentationPolicy.Mode.MIRROR_HORIZONTAL;case "upside-down"->ScenePresentationPolicy.Mode.MIRROR_VERTICAL;default->ScenePresentationPolicy.Mode.valueOf(token(kind));};
                int limit=switch(mode) {case SHAKE->6;case ZOOM,INPUT_SQUASH->8;case OUTLINE->30;default->0;};
                yield new ScenePresentationPolicy(mode,Math.min(limit,values.integer("magnitude")),period,mode==ScenePresentationPolicy.Mode.SCENERY?values.integer("scenery"):0);
            }
            case "hud-presentation" -> new HudPresentationPolicy(HudPresentationPolicy.Mode.valueOf(token(kind)),period,values.integer("seed"));
            case "signed-gravity" -> kind.equals("reverse")?new SignedGravityPolicy(List.of(true),Math.clamp(values.integer("duration"),15,180)):
                    new SignedGravityPolicy(java.util.stream.IntStream.range(0,4).mapToObj(index->(values.integer("sequence")&(1<<index))!=0).toList(),Math.clamp(values.integer("duration")/4,15,75));
            case "player-motion" -> switch(kind) {
                case "jump" -> new PlayerMotionPolicy(PlayerMotionPolicy.Mode.JUMP_SCALE,Math.clamp(p,100,150));
                case "traction" -> new PlayerMotionPolicy(PlayerMotionPolicy.Mode.TRACTION,p);
                case "wind" -> new PlayerMotionPolicy(PlayerMotionPolicy.Mode.HORIZONTAL_FORCE,values.choice("side").equals("ahead")?8:-8,0x600);
                case "reverse" -> new PlayerMotionPolicy(PlayerMotionPolicy.Mode.MOMENTUM_PULSE,0);
                case "cruise" -> new PlayerMotionPolicy(PlayerMotionPolicy.Mode.CRUISE,0x300);
                case "roll" -> new PlayerMotionPolicy(PlayerMotionPolicy.Mode.ROLLING,0);
                default -> new PlayerMotionPolicy(PlayerMotionPolicy.Mode.GROUND_STOP,0);
            };
            case "input-control" -> new InputControlPolicy(switch(kind) {case "reverse"->InputControlPolicy.Mode.REVERSE_HORIZONTAL;case "auto-jump"->InputControlPolicy.Mode.LANDING_JUMP;case "jump-gate"->InputControlPolicy.Mode.NO_FRESH_JUMP;default->InputControlPolicy.Mode.DELAY;},kind.equals("delay")?values.integer("delay"):0);
            case "spring-response" -> new SpringResponsePolicy(p);
            case "object-field" -> {
                int split=kind.lastIndexOf('-');
                yield new ObjectFieldPolicy(ObjectFieldPolicy.Target.valueOf(token(kind.substring(0,split))),ObjectFieldPolicy.Mode.valueOf(token(kind.substring(split+1))));
            }
            case "economy" -> new RingEconomyPolicy(RingEconomyPolicy.Kind.valueOf(token(kind)));
            case "encounter" -> new EncounterPolicy(EncounterPolicy.Kind.valueOf(token(kind)),EncounterPolicy.Side.valueOf(token(values.choice("side"))));
            case "enemy-rule" -> new EnemyRulePolicy(EnemyRulePolicy.Kind.valueOf(token(kind)));
            case "power-up" -> new NativePowerupPolicy(NativePowerupPolicy.Kind.valueOf(token(kind)),values.integer("duration"));
            case "transport" -> new TransportPolicy(TransportPolicy.Kind.valueOf(token(kind)));
            case "relocation" -> new RelocationPolicy(RelocationPolicy.Kind.valueOf(token(kind)));
            case "challenge" -> new ChallengePolicy(ChallengePolicy.Kind.valueOf(token(kind)));
            case "projectile" -> new ProjectileRulePolicy(ProjectileRulePolicy.Kind.valueOf(token(kind)));
            case "audio" -> new AudioEffectPolicy(kind.equals("music")?AudioEffectPolicy.Mode.MUSIC:AudioEffectPolicy.Mode.LANDING_CUE,
                    kind.equals("music")?Math.clamp(values.integer("cue"),1,23):values.integer("cue"),45);
            case "chat" -> new OverlayPolicy(OverlayPolicy.Mode.SIMULATED_CHAT);
            case "fluid" -> new FluidContextPolicy(96);
            case "sidegame","pong" -> new OverlayPolicy(switch(kind) {case "pong"->OverlayPolicy.Mode.PONG;case "snake"->OverlayPolicy.Mode.SNAKE;case "stack"->OverlayPolicy.Mode.BLOCK_STACK;case "rhythm"->OverlayPolicy.Mode.RHYTHM;default->OverlayPolicy.Mode.LANE_DEFENCE;});
            default -> throw new IllegalArgumentException("Unknown semantic family: "+family);
        };
        if(family.equals("pong"))return List.of(policy,new GameplayHoldPolicy(Math.min(1200,values.integer("duration"))),
                new RingEconomyPolicy(RingEconomyPolicy.Kind.COMPLETION_REWARD,5));
        return family.equals("sidegame")?List.of(policy,new GameplayHoldPolicy(Math.min(1200,values.integer("duration")))):List.of(policy);
    }
}
