package chaos;

import com.openggf.audio.GameSound;
import com.openggf.game.*;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.mods.mutators.*;
import com.openggf.mods.ui.*;
import java.util.*;

/** Mod-owned selection, settings, envelopes, sidegames and HUD. Host facades retain mutation authority. */
public final class ChaosController implements GameplayObserver,RewindSnapshottable<ChaosController.Snapshot> {
    public record Snapshot(ChaosSchedule.Snapshot schedule,long world,long visit,long lastPresentation,
                           long sidegameActivation,ChaosSidegame.Snapshot sidegame,long quietUntil,
                           Set<Long> terminalSeen,String chat,boolean wasAirborne,int oldRings,String notice,long noticeUntil) {
        public Snapshot {terminalSeen=Set.copyOf(terminalSeen);if(terminalSeen.size()>64)throw new IllegalArgumentException("Terminal result budget exceeded");}
    }
    private final ChaosCatalog catalogue;
    private final ChaosPreferences preferences;
    private final ChaosSchedule schedule;
    private final ChaosMenu menu;
    private GameplayObservation observation;
    private ChaosSettings pendingSettings;
    private boolean clearRequested,cancelSidegame,cueRequested,errorCue,closed;
    private long world,visit,lastPresentation=-1,sidegameActivation,quietUntil;
    private ChaosSidegame sidegame;
    private ChaosArt nativeArt;
    private String nativeArtError="Native ROM art is not prepared";
    private long artWorld=-1,noticeUntil;
    private boolean artFailed;
    private String notice="";
    private final Set<Long> terminalSeen=new HashSet<>();
    private List<TemporaryMutators.ActiveActivation> hostActive=List.of();
    private Map<String,String> unavailable=Map.of();
    private String chat="";
    private boolean wasAirborne;
    private int oldRings;
    public ChaosController(ChaosCatalog catalogue,ChaosPreferences preferences) {
        this.catalogue=catalogue;this.preferences=preferences;
        long seed=preferences.saved().seed()==0?System.nanoTime():preferences.saved().seed();
        schedule=new ChaosSchedule(catalogue,preferences.saved(),seed,DecisionSource.local());
        menu=new ChaosMenu(this,catalogue,preferences);
    }
    public ChaosMenu menu() {return menu;}
    public ChaosCatalog catalogue() {return catalogue;}
    public ChaosSchedule schedule() {return schedule;}
    public boolean sidegameActive() {return sidegame!=null;}
    public void requestCancelSidegame() {cancelSidegame=true;}
    public void requestSettings(ChaosSettings settings) {pendingSettings=settings;}
    public void requestClear() {clearRequested=true;}
    public void cue(boolean error) {cueRequested=true;errorCue=error;}
    public String qualification(ChaosCatalog.Effect effect) {
        if(unavailable.containsKey(effect.family()))return unavailable.get(effect.family());
        return effect.sidegame() && nativeArt==null?nativeArtError:"";
    }
    @Override public void onPresentation(GameplayObservation current,TemporaryMutators mutators) {
        if(closed)return;
        observation=current;world=current.worldGeneration();visit=current.visitGeneration();
        var availability=new LinkedHashMap<String,String>();
        for(var value:mutators.available())if(!value.available())availability.put(value.localDefinitionId(),value.reason());
        unavailable=Map.copyOf(availability);
        var admission=admission(current,mutators);
        if(pendingSettings!=null) {schedule.edit(pendingSettings,admission);pendingSettings=null;}
        prepareArt(current,mutators);
        if(clearRequested) {schedule.clear(admission);cancelGame(mutators);clearRequested=false;}
        if(cueRequested) {mutators.playSfx(errorCue?GameSound.ERROR:GameSound.RING);cueRequested=false;}
        hostActive=mutators.active();schedule.retainHostActivations(new HashSet<>(hostActive.stream().map(TemporaryMutators.ActiveActivation::activationId).toList()));
        consumeOutcomes(mutators.outcomes());
        for(var active:schedule.active()) {
            var effect=catalogue.effect(active.effect());
            if(effect.sidegame() && sidegame==null) {
                sidegame=new ChaosSidegame(ChaosSidegame.Kind.valueOf(effect.mode().toUpperCase(Locale.ROOT)),
                        ((Number)active.options().get("seed")).longValue()+1);sidegameActivation=active.id();
            }
        }
        if(sidegame!=null) {
            boolean alive=hostActive.stream().anyMatch(a->a.activationId()==sidegameActivation);
            if(!alive || cancelSidegame || !schedule.settings().enabled() || nativeArt==null)cancelGame(mutators);
            else if(current.phase()==GameplayObservation.Phase.HELD) {
                var input=current.input().player1();int direction=input.heldMask();
                boolean up=(direction&1)!=0,down=(direction&2)!=0,left=(direction&4)!=0,right=(direction&8)!=0;
                // Stack navigation rotates/moves on fresh edges; Pong follows held directions.
                if(sidegame.kind()==ChaosSidegame.Kind.STACK) {int edge=input.pressedMask();up=(edge&1)!=0;down=(edge&2)!=0;left=(edge&4)!=0;right=(edge&8)!=0;}
                sidegame.step(new ChaosSidegame.Input(left,right,up,down,input.actionPressedMask(),input.startPressed()));
                if(hostActive.stream().filter(a->a.activationId()==sidegameActivation)
                        .anyMatch(a->a.remainingPresentations()<=1))sidegame.finishAtDeadline();
                int lane=sidegame.cueLane();
                if(lane>=0)mutators.playSfx(switch(lane) {case 0->GameSound.FIRE_SHIELD;case 1->GameSound.LIGHTNING_SHIELD;default->GameSound.BUBBLE_SHIELD;});
                if(sidegame.finished()) {
                    quietUntil=schedule.clock()+sidegame.quietSeconds()*60L;
                    mutators.playSfx(sidegame.cancelled()?GameSound.ERROR:GameSound.RING);
                    if(sidegame.won()) {
                        var result=mutators.complete(sidegameActivation,true);
                        notice=result.accepted()?"PONG WON: RING REWARD SUBMITTED":"Failed: Pong reward "+result.reason();
                        noticeUntil=schedule.clock()+300;
                        if(!result.accepted())mutators.cancel(sidegameActivation);
                    }else mutators.cancel(sidegameActivation);
                    sidegame=null;sidegameActivation=0;
                }
            }
        }
        if(current.phase()!=GameplayObservation.Phase.ACTIVE)return;
        if(current.activePresentations()==lastPresentation)return;
        lastPresentation=current.activePresentations();
        if(schedule.clock()>=quietUntil)schedule.presentation(admission);
        else schedule.quietPresentation(admission);
        for(var value:List.copyOf(schedule.active())) {
            var effect=catalogue.effect(value.effect());
            if(effect.number()==2 || effect.number()==56) {
                int percent;
                if(effect.number()==2) {
                    int maximum=(Integer)value.options().get("percent"),period=(Integer)value.options().get("period");
                    double phase=(schedule.clock()-value.started())*Math.PI*2/period;
                    percent=100+(int)((maximum-100)*(Math.sin(phase)+1)/2);
                } else {
                    int speed=current.leader().map(p->Math.abs(p.airborne()?p.xSpeed():p.groundSpeed())).orElse(0);
                    percent=25+Math.min(175,(int)((long)speed*175/0x600));
                }
                var options=new LinkedHashMap<>(value.options());options.put("percent",percent);
                var result=mutators.replace(value.id(),ChaosDefinitions.options(effect,options,effect.duration(schedule.settings().durationQuarters())));
                if(!result.accepted())mutators.cancel(value.id());
            }
        }
        hostActive=mutators.active();
        if(hostActive.stream().anyMatch(a->a.localDefinitionId().equals("chat")))current.leader().ifPresent(player->{
            if(player.rings()>oldRings)chat="SIMULATED CHAT: NICE RINGS!";
            else if(wasAirborne && !player.airborne())chat="SIMULATED CHAT: STUCK THE LANDING!";
            else if(player.hurt())chat="SIMULATED CHAT: OOF!";
            oldRings=player.rings();wasAirborne=player.airborne();
        });else chat="";
    }
    private void consumeOutcomes(List<TemporaryMutators.ActivationOutcome> outcomes) {
        // At most 32 retained receipts plus one active lease per registered family (cap 32).
        // Keep active terminal identities after receipt FIFO eviction so status cannot renew a reward.
        var retained=new HashSet<Long>();
        outcomes.forEach(value->retained.add(value.activationId()));
        for(var value:hostActive)if(terminal(value.status()))retained.add(value.activationId());
        terminalSeen.retainAll(retained);
        for(var value:outcomes)consumeOutcome(value.activationId(),value.status());
        for(var value:hostActive)consumeOutcome(value.activationId(),value.status());
    }
    private static boolean terminal(String status) {return status.startsWith("Complete:") || status.startsWith("Failed:");}
    private void consumeOutcome(long id,String status) {
        if(!terminal(status) || !terminalSeen.add(id))return;
        if(status.startsWith("Complete:"))quietUntil=Math.max(quietUntil,schedule.clock()+300);
        notice=status;noticeUntil=schedule.clock()+300;
    }
    private void prepareArt(GameplayObservation current,TemporaryMutators mutators) {
        if(artWorld!=current.worldGeneration()) {artWorld=current.worldGeneration();nativeArt=null;artFailed=false;nativeArtError="Native ROM art is not prepared";}
        if(nativeArt!=null || artFailed || schedule.settings().selected().stream().map(catalogue::effect).noneMatch(ChaosCatalog.Effect::sidegame))return;
        var supplied=mutators.nativeArt();
        if(supplied.isEmpty()) {nativeArtError="Prepared native ROM art unavailable";return;}
        try {nativeArt=new ChaosArt(supplied.orElseThrow());nativeArtError="";}
        catch(RuntimeException unavailableArt) {artFailed=true;nativeArtError="Native art unavailable: "+unavailableArt.getMessage();}
    }
    private void cancelGame(TemporaryMutators mutators) {
        if(sidegameActivation!=0)mutators.cancel(sidegameActivation);
        sidegame=null;sidegameActivation=0;cancelSidegame=false;
    }
    private ChaosSchedule.Admission admission(GameplayObservation current,TemporaryMutators mutators) {
        return new ChaosSchedule.Admission() {
            @Override public int nativeZone() {return current.zone();}
            @Override public boolean eligible(ChaosCatalog.Effect effect,Map<String,Object> options) {
                if(unavailable.containsKey(effect.family()) || current.phase()!=GameplayObservation.Phase.ACTIVE || current.leader().isEmpty())return false;
                var player=current.leader().orElseThrow();
                if(player.dead() || player.hurt())return false;
                if(effect.category()!=ChaosCatalog.Category.VISUAL && (player.attached() || player.controlLocked()))return false;
                if(effect.family().equals("dry-gravity") && (!player.character().equals("sonic") || player.inWater()))return false;
                if(effect.number()==20 && player.rings()<=32)return false;
                if(effect.number()==28 && current.team().size()<2)return false;
                if(effect.sidegame() && (nativeArt==null || player.airborne() || player.attached() || player.controlLocked() || player.inWater() || !schedule.active().isEmpty()))return false;
                return true;
            }
            @Override public long activate(ChaosCatalog.Effect effect,Map<String,Object> options,int duration) {
                var result=mutators.activate(effect.family(),ChaosDefinitions.options(effect,options,duration),duration);
                return result.accepted()?result.activationId():0;
            }
            @Override public void cancel(long id) {mutators.cancel(id);}
        };
    }
    @Override public void onTick(GameplayObservation current,TemporaryMutators mutators) {observation=current;}
    @Override public void onBoundary(GameplayObservation current,TemporaryMutators mutators) {
        if(closed)return;
        schedule.clear(admission(current,mutators));cancelGame(mutators);schedule.boundary();
        observation=current;world=current.worldGeneration();visit=current.visitGeneration();lastPresentation=-1;
        hostActive=List.of();terminalSeen.clear();chat="";quietUntil=0;wasAirborne=false;oldRings=0;notice="";noticeUntil=0;
    }
    @Override public void drawOverlay(PixelCanvas canvas,GameplayObservation current) {
        if(closed)return;
        if(menu.open()) {menu.draw(canvas);return;}
        if(sidegame!=null) {
            if(nativeArt!=null)sidegame.draw(canvas,(canvas.width()-196)/2,32,nativeArt);
            else CompactFont.draw(canvas,CompactFont.fit(nativeArtError,canvas.width()-16,1),8,32,1,0xFFFFCC80);
            return;
        }
        if(current.leader().isEmpty() || !schedule.settings().enabled())return;
        String title=schedule.settings().selected().isEmpty()?"NO EFFECTS SELECTED":"CHAOS "+((schedule.countdown()+59)/60)+"S";
        if(schedule.clock()<quietUntil)title="CHAOS BREATHER "+((quietUntil-schedule.clock()+59)/60)+"S";
        int width=Math.min(canvas.width()-8,160),left=canvas.width()-width-4;
        canvas.fill(left,4,width,14+schedule.active().size()*12,0xCC102438);
        CompactFont.draw(canvas,title,left+4,7,1,0xFFFFD15B);
        int y=20;
        for(var value:schedule.active()) {
            var effect=catalogue.effect(value.effect());
            long remaining=value.expires()==0?0:Math.max(0,value.expires()-schedule.clock());
            String suffix=value.expires()==0?"":" "+((remaining+59)/60)+"S";
            CompactFont.draw(canvas,CompactFont.fit(effect.name()+suffix,width-8,1),left+4,y,1,0xFFFFFFFF);y+=12;
        }
        int textY=canvas.height()-30;
        for(var value:hostActive)if(!value.status().isBlank()) {
            CompactFont.shadowed(canvas,CompactFont.fit(value.status(),canvas.width()-16,1),8,textY,1,0xFFFFFF90,0xFF000000);textY-=10;
        }
        if(!chat.isEmpty())CompactFont.shadowed(canvas,chat,8,canvas.height()-10,1,0xFF79D9FB,0xFF000000);
        if(schedule.clock()<noticeUntil)CompactFont.shadowed(canvas,CompactFont.fit(notice,canvas.width()-16,1),8,textY,1,0xFFFFFF90,0xFF000000);
    }
    @Override public String key() {return "chaos-run";}
    @Override public Snapshot capture() {return new Snapshot(schedule.capture(),world,visit,lastPresentation,sidegameActivation,
            sidegame==null?null:sidegame.capture(),quietUntil,terminalSeen,chat,wasAirborne,oldRings,notice,noticeUntil);}
    @Override public void restore(Snapshot snapshot) {
        schedule.restore(snapshot.schedule());world=snapshot.world();visit=snapshot.visit();lastPresentation=snapshot.lastPresentation();
        sidegameActivation=snapshot.sidegameActivation();sidegame=snapshot.sidegame()==null?null:new ChaosSidegame(snapshot.sidegame().kind(),1);
        if(sidegame!=null)sidegame.restore(snapshot.sidegame());quietUntil=snapshot.quietUntil();
        terminalSeen.clear();terminalSeen.addAll(snapshot.terminalSeen());chat=snapshot.chat();wasAirborne=snapshot.wasAirborne();oldRings=snapshot.oldRings();
        notice=snapshot.notice();noticeUntil=snapshot.noticeUntil();
        hostActive=List.of();pendingSettings=null;clearRequested=cancelSidegame=cueRequested=false;
    }
    @Override public void resetForMissingSnapshot() {schedule.boundary();sidegame=null;sidegameActivation=0;terminalSeen.clear();chat="";quietUntil=0;notice="";noticeUntil=0;hostActive=List.of();}
    @Override public void close() {closed=true;menu.close();sidegame=null;nativeArt=null;hostActive=List.of();}
}
