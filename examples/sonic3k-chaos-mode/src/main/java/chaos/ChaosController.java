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
                           Set<Long> terminalSeen,String chat,boolean wasAirborne,int oldRings,String notice,long noticeUntil,
                           String noticeEffect,String announced,long announcedAt,List<Launch> launches) {
        public Snapshot {
            terminalSeen=Set.copyOf(terminalSeen);launches=List.copyOf(launches);
            if(terminalSeen.size()>64)throw new IllegalArgumentException("Terminal result budget exceeded");
            if(launches.size()>LAUNCH_HISTORY)throw new IllegalArgumentException("Launch history budget exceeded");
        }
    }
    /** Recent activation identities, so later receipts can name the effect that produced them. */
    public record Launch(long id,String effect) { }
    private static final int LAUNCH_HISTORY=16,BANNER=150;
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
    private static final String ART_NOT_PREPARED="Native ROM art is not prepared";
    private String nativeArtError=ART_NOT_PREPARED;
    private long artWorld=-1,noticeUntil;
    private boolean artFailed;
    private String notice="",noticeEffect="",announced="";
    private long announcedAt;
    private final ArrayDeque<Launch> launches=new ArrayDeque<>();
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
    /** The S3K sound table has no ERROR entry; a skid is the mapped native refusal cue. */
    private static GameSound refusal() {return GameSound.SKID;}
    public String qualification(ChaosCatalog.Effect effect) {
        if(unavailable.containsKey(effect.family()))return unavailable.get(effect.family());
        // Art is prepared lazily inside a world; only an attempted, missing or rejected decode is a reason.
        return effect.sidegame() && nativeArt==null && !nativeArtError.equals(ART_NOT_PREPARED)?nativeArtError:"";
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
        if(cueRequested) {mutators.playSfx(errorCue?refusal():GameSound.RING);cueRequested=false;}
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
            if(!alive || cancelSidegame || !schedule.settings().enabled() || nativeArt==null) {
                if(alive && cancelSidegame) {
                    notice="Failed: Cancelled. No reward this time.";noticeEffect=launched(sidegameActivation);
                    noticeUntil=schedule.clock()+300;mutators.playSfx(refusal());
                }
                cancelGame(mutators);
            }
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
                    mutators.playSfx(sidegame.cancelled() || sidegame.score()==0?refusal():GameSound.RING);
                    noticeEffect=launched(sidegameActivation);noticeUntil=schedule.clock()+300;
                    if(sidegame.won()) {
                        var result=mutators.complete(sidegameActivation,true);
                        notice=result.accepted()?"Complete: Three returns! Five rings are on their way.":"Failed: Pong reward "+result.reason();
                        if(!result.accepted())mutators.cancel(sidegameActivation);
                    } else {
                        notice=sidegame.resultText();
                        mutators.cancel(sidegameActivation);
                    }
                    sidegame=null;sidegameActivation=0;
                }
            }
        }
        if(current.phase()!=GameplayObservation.Phase.ACTIVE)return;
        if(current.activePresentations()==lastPresentation)return;
        lastPresentation=current.activePresentations();
        var previous=new HashSet<Long>();schedule.active().forEach(value->previous.add(value.id()));
        if(schedule.clock()>=quietUntil)schedule.presentation(admission);
        else schedule.quietPresentation(admission);
        for(var value:schedule.active())if(!previous.contains(value.id()))announce(value,mutators);
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
            if(player.rings()>oldRings)react(RING_CHAT);
            else if(wasAirborne && !player.airborne())react(LANDING_CHAT);
            else if(player.hurt())react(HURT_CHAT);
            oldRings=player.rings();wasAirborne=player.airborne();
        });else chat="";
    }
    // Packaged mods may keep only literal constants in static fields; lists split at use.
    private static final String VIEWERS="RINGFAN64|CHILIDOG|TAILS_MAIN|KNUX_STAN|BADNIK_IRL|EGGFAN|SPINDASHER";
    private static final String RING_CHAT="NICE RINGS!|RING GET!|+1 RING, LET'S GO|SO RICH RIGHT NOW";
    private static final String LANDING_CHAT="STUCK THE LANDING!|10/10 LANDING|CLEAN!|FEET: CONFIRMED";
    private static final String HURT_CHAT="OOF!|F|THAT HAD TO HURT|CLIP IT!";
    /** Deterministic simulated reaction; a held condition keeps its message rather than flickering. */
    private void react(String choices) {
        String[] lines=choices.split("\\|"),viewers=VIEWERS.split("\\|");
        for(String line:lines)if(chat.endsWith(": "+line))return;
        long beat=schedule.clock()/7;
        chat=viewers[(int)Math.floorMod(beat,(long)viewers.length)]+": "+lines[(int)Math.floorMod(beat/3,(long)lines.length)];
    }
    private void announce(ChaosSchedule.Active value,TemporaryMutators mutators) {
        launches.addLast(new Launch(value.id(),value.effect()));
        while(launches.size()>LAUNCH_HISTORY)launches.removeFirst();
        if(catalogue.effect(value.effect()).sidegame())return;
        announced=value.effect();announcedAt=schedule.clock();
        mutators.playSfx(GameSound.CHECKPOINT);
    }
    private String launched(long id) {
        return launches.stream().filter(value->value.id()==id).map(Launch::effect).findFirst().orElse("");
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
        notice=status;noticeEffect=launched(id);noticeUntil=schedule.clock()+300;
    }
    private void prepareArt(GameplayObservation current,TemporaryMutators mutators) {
        if(artWorld!=current.worldGeneration()) {artWorld=current.worldGeneration();nativeArt=null;artFailed=false;nativeArtError=ART_NOT_PREPARED;}
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
        noticeEffect="";announced="";announcedAt=0;launches.clear();
    }
    @Override public void drawOverlay(PixelCanvas canvas,GameplayObservation current) {
        if(closed)return;
        // The title provider draws the title modal itself; drawing it here too would double the backdrop.
        if(menu.open()) {if(!menu.title())menu.draw(canvas);return;}
        if(sidegame!=null) {
            if(nativeArt!=null)sidegame.draw(canvas,(canvas.width()-196)/2,32,nativeArt);
            else ChaosUi.text(canvas,ChaosUi.fit(nativeArtError,canvas.width()-16,1),8,32,1,ChaosUi.WARN);
            return;
        }
        // Native data select and other setup screens can retain the last observation. Only a visit that
        // has delivered an active presentation shows the Chaos HUD.
        var phase=current.phase();
        if(lastPresentation<0 || current.leader().isEmpty() || !schedule.settings().enabled()
                || phase!=GameplayObservation.Phase.ACTIVE && phase!=GameplayObservation.Phase.HELD
                && phase!=GameplayObservation.Phase.PAUSED && phase!=GameplayObservation.Phase.REWIND)return;
        long clock=schedule.clock();
        drawTimer(canvas,clock);
        drawActive(canvas,clock);
        drawPrompts(canvas,clock);
        drawBanner(canvas,clock);
    }
    /** Genre-standard full-width bar: fills toward the next draw, flashing through the final three seconds. */
    private void drawTimer(PixelCanvas canvas,long clock) {
        if(schedule.settings().selected().isEmpty())return;
        int width=canvas.width();
        if(clock<quietUntil) {canvas.fill(0,0,width,3,ChaosUi.fade(ChaosUi.CYAN,0.45+0.4*ChaosUi.pulse(clock,60)));return;}
        long interval=schedule.settings().intervalSeconds()*60L,countdown=schedule.countdown();
        int colour=countdown<=180?ChaosUi.mix(ChaosUi.GOLD,ChaosUi.WHITE,ChaosUi.pulse(clock,20)):ChaosUi.GOLD;
        ChaosUi.bar(canvas,0,0,width,3,Math.max(0,interval-countdown),interval,0x90000000,colour);
    }
    private static int panelWidth(PixelCanvas canvas) {return Math.min(canvas.width()-8,168);}
    private void drawActive(PixelCanvas canvas,long clock) {
        var active=schedule.active();
        int width=panelWidth(canvas),left=canvas.width()-width-4,top=6;
        ChaosUi.panel(canvas,left,top,width,15+active.size()*13,0xD80C1A33,ChaosUi.EDGE);
        ChaosUi.text(canvas,"CHAOS",left+5,top+4,1,ChaosUi.GOLD);
        if(schedule.settings().selected().isEmpty())ChaosUi.right(canvas,"NO EFFECTS",left+width-5,top+4,1,ChaosUi.DIM);
        else if(clock<quietUntil)ChaosUi.right(canvas,"BREATHER "+ChaosUi.seconds(quietUntil-clock),left+width-5,top+4,1,ChaosUi.CYAN);
        else {
            long countdown=schedule.countdown();
            ChaosUi.right(canvas,"NEXT "+ChaosUi.seconds(countdown),left+width-5,top+4,1,
                    countdown<=180?ChaosUi.mix(ChaosUi.WHITE,ChaosUi.GOLD,ChaosUi.pulse(clock,20)):ChaosUi.WHITE);
        }
        int y=top+16;
        for(var value:active) {
            var effect=catalogue.effect(value.effect());
            String remaining=value.expires()==0?"...":ChaosUi.seconds(Math.max(0,value.expires()-clock));
            ChaosUi.text(canvas,ChaosUi.fit(effect.name(),width-36,1),left+5,y,1,ChaosUi.WHITE);
            ChaosUi.right(canvas,remaining,left+width-5,y,1,ChaosUi.TEXT);
            if(value.expires()>0)ChaosUi.bar(canvas,left+5,y+9,width-10,2,value.expires()-clock,value.expires()-value.started(),
                    0x60000000,ChaosUi.severity(effect.severity()));
            y+=13;
        }
    }
    private static String tagline(String description) {
        for(int i=0;i+1<description.length();i++) {
            char c=description.charAt(i);
            if((c=='.' || c=='!' || c=='?') && description.charAt(i+1)==' ' && i>2)return description.substring(0,i+1);
        }
        return description;
    }
    /**
     * Activation card driven by the captured Chaos clock, so pause, rewind and skipped frames agree. It opens over the
     * Chaos panel in the top band: right of the native score/time/rings column and ending above screen row 69, where the
     * camera holds the player (y 96) on the ground. Opaque once shown; the panel shows through only while it fades.
     */
    private void drawBanner(PixelCanvas canvas,long clock) {
        long since=clock-announcedAt;
        if(announced.isEmpty() || since<0 || since>=BANNER)return;
        var effect=catalogue.effect(announced);int width=canvas.width();
        // S3K centres its 320-pixel HUD in wider views; the score column ends 112 pixels in.
        int right=width-4,room=right-((width-320)/2+116),inner=room-20;
        String name=ChaosUi.safe(effect.name()).toUpperCase(Locale.ROOT);
        int scale=ChaosUi.width(name,2)<=inner?2:1;
        name=ChaosUi.fit(name,inner,scale);
        var lines=ChaosUi.wrap(tagline(effect.description()),inner,3);
        int text=ChaosUi.width(name,scale);
        for(String line:lines)text=Math.max(text,ChaosUi.width(line,1));
        // Never narrower than the panel it covers; at most three summary lines keep the bottom edge at row 66.
        int cardWidth=Math.min(room,Math.max(panelWidth(canvas),text+20)),height=18+7*scale+lines.size()*9+2;
        double amount=Math.min(Math.min(1,since/10.0),Math.min(1,(BANNER-since)/24.0));
        int left=right-cardWidth,top=6;
        int severity=ChaosUi.severity(effect.severity());
        ChaosUi.panel(canvas,left,top,cardWidth,height,ChaosUi.fade(ChaosUi.alpha(ChaosUi.NAVY_DEEP,255),amount),ChaosUi.fade(ChaosUi.GOLD,amount));
        canvas.fill(left+3,top+3,3,height-6,ChaosUi.fade(severity,amount));
        String kicker=switch(effect.severity()) {case ORDINARY->"CHAOS!";case DISRUPTIVE->"UH-OH...";case MAJOR->"HOLD ON TIGHT!";};
        ChaosUi.text(canvas,kicker,left+12,top+4,1,ChaosUi.fade(severity,amount));
        ChaosUi.right(canvas,effect.instant()?"NOW":ChaosUi.seconds(effect.duration(schedule.settings().durationQuarters())),
                right-8,top+4,1,ChaosUi.fade(ChaosUi.TEXT,amount));
        ChaosUi.text(canvas,name,left+12,top+14,scale,ChaosUi.fade(ChaosUi.GOLD,amount));
        for(int i=0;i<lines.size();i++)ChaosUi.text(canvas,lines.get(i),left+12,top+18+7*scale+i*9,1,ChaosUi.fade(ChaosUi.WHITE,amount));
    }
    /** Bottom-centre cards stack upward: latest receipt lowest, live native prompts above. Chat sits left. */
    private void drawPrompts(PixelCanvas canvas,long clock) {
        // Above the native lives counter in the bottom-left corner.
        int y=canvas.height()-32;
        long shown=clock-(noticeUntil-300);
        if(clock<noticeUntil && !notice.isBlank()) {
            boolean complete=notice.startsWith("Complete:"),failed=notice.startsWith("Failed:");
            String body=complete || failed?notice.substring(notice.indexOf(':')+1).trim():notice;
            if(!body.isEmpty())body=Character.toUpperCase(body.charAt(0))+body.substring(1);
            String header=(noticeEffect.isEmpty()?"CHAOS":catalogue.effect(noticeEffect).name())
                    +(complete?" - DONE!":failed && body.startsWith("Cancelled")?" - QUIT":failed?" - NO LUCK":"");
            double amount=Math.min(Math.min(1,shown/8.0),Math.min(1,(noticeUntil-clock)/30.0));
            card(canvas,y+(int)Math.round((1-Math.min(1,shown/8.0))*8),header,body,
                    ChaosUi.fade(complete?ChaosUi.GOOD:failed?ChaosUi.WARN:ChaosUi.GOLD,amount),ChaosUi.fade(ChaosUi.TEXT,amount),amount,0,0);
            y-=28;
        }
        for(var value:hostActive) {
            String status=value.status();
            // Generic lease states repeat the Chaos panel; only native prompts and specific notes are shown.
            if(status.isBlank() || terminal(status) || status.startsWith("Applied:") || status.equals("Active")
                    || status.equals("Pending native service"))continue;
            String effect=launched(value.activationId());
            String header=effect.isEmpty()?"CHAOS":catalogue.effect(effect).name();
            // Native task text is "Task: instruction (progress/goal)".
            String body=status.startsWith("Task: ")?status.substring(6):status;
            int progress=0,goal=0,open=body.lastIndexOf(" ("),slash=body.lastIndexOf('/');
            if(status.startsWith("Task: ") && open>0 && slash>open && body.endsWith(")")) {
                try {progress=Integer.parseInt(body.substring(open+2,slash));goal=Integer.parseInt(body.substring(slash+1,body.length()-1));body=body.substring(0,open);}
                catch(NumberFormatException notCounted) {progress=goal=0;}
            }
            // Host instructions repeat the effect name and may expose a native coordinate.
            if(body.toLowerCase(Locale.ROOT).startsWith(header.toLowerCase(Locale.ROOT)+":"))body=body.substring(header.length()+1).trim()+"!";
            int coordinate=body.indexOf(" at x=");
            if(coordinate>=0) {
                int end=coordinate+6;
                while(end<body.length() && (body.charAt(end)=='-' || Character.isDigit(body.charAt(end))))end++;
                body=body.substring(0,coordinate)+body.substring(end);
            }
            card(canvas,y,header,body,ChaosUi.GOLD,ChaosUi.WHITE,1,progress,goal);
            y-=goal>1?32:28;
        }
        if(!chat.isEmpty()) {
            // A narrow left column below the native HUD and above the receipt cards.
            int split=chat.indexOf(": "),width=Math.min(canvas.width()/2-40,120),left=6,top=118;
            String user=split<0?"VIEWER:":chat.substring(0,split+1),line=split<0?chat:chat.substring(split+2);
            ChaosUi.panel(canvas,left,top,width,36,0xD0140A28,0xFF9146FF);
            ChaosUi.text(canvas,ChaosUi.fit("SIMULATED CHAT",width-12,1),left+6,top+4,1,ChaosUi.DIM);
            ChaosUi.text(canvas,ChaosUi.fit(user,width-12,1),left+6,top+14,1,ChaosUi.CYAN);
            ChaosUi.text(canvas,ChaosUi.fit(line,width-12,1),left+6,top+24,1,ChaosUi.WHITE);
        }
    }
    private static void card(PixelCanvas canvas,int top,String header,String body,int accent,int text,double amount,int progress,int goal) {
        String line=ChaosUi.fit(body,canvas.width()-40,1);
        int width=Math.min(canvas.width()-16,Math.max(ChaosUi.width(header,1),ChaosUi.width(line,1))+20);
        int left=(canvas.width()-width)/2,height=goal>1?28:24;
        ChaosUi.panel(canvas,left,top-height+4,width,height,ChaosUi.fade(0xE00C1A33,amount),accent);
        ChaosUi.text(canvas,ChaosUi.fit(header,width-16,1).toUpperCase(Locale.ROOT),left+8,top-height+8,1,accent);
        ChaosUi.text(canvas,line,left+8,top-height+18,1,text);
        if(goal>1)ChaosUi.bar(canvas,left+8,top-height+27,width-16,2,progress,goal,0x60000000,accent);
    }
    @Override public String key() {return "chaos-run";}
    @Override public Snapshot capture() {return new Snapshot(schedule.capture(),world,visit,lastPresentation,sidegameActivation,
            sidegame==null?null:sidegame.capture(),quietUntil,terminalSeen,chat,wasAirborne,oldRings,notice,noticeUntil,
            noticeEffect,announced,announcedAt,List.copyOf(launches));}
    @Override public void restore(Snapshot snapshot) {
        schedule.restore(snapshot.schedule());world=snapshot.world();visit=snapshot.visit();lastPresentation=snapshot.lastPresentation();
        sidegameActivation=snapshot.sidegameActivation();sidegame=snapshot.sidegame()==null?null:new ChaosSidegame(snapshot.sidegame().kind(),1);
        if(sidegame!=null)sidegame.restore(snapshot.sidegame());quietUntil=snapshot.quietUntil();
        terminalSeen.clear();terminalSeen.addAll(snapshot.terminalSeen());chat=snapshot.chat();wasAirborne=snapshot.wasAirborne();oldRings=snapshot.oldRings();
        notice=snapshot.notice();noticeUntil=snapshot.noticeUntil();noticeEffect=snapshot.noticeEffect();
        announced=snapshot.announced();announcedAt=snapshot.announcedAt();launches.clear();launches.addAll(snapshot.launches());
        hostActive=List.of();pendingSettings=null;clearRequested=cancelSidegame=cueRequested=false;
    }
    @Override public void resetForMissingSnapshot() {
        schedule.boundary();sidegame=null;sidegameActivation=0;terminalSeen.clear();chat="";quietUntil=0;notice="";noticeUntil=0;hostActive=List.of();
        noticeEffect="";announced="";announcedAt=0;launches.clear();
    }
    @Override public void close() {closed=true;menu.close();sidegame=null;nativeArt=null;hostActive=List.of();}
}
