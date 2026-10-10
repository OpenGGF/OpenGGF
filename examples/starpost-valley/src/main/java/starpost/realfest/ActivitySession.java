package starpost.realfest;

import com.openggf.game.ActExit;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.*;
import starpost.core.Game;
import starpost.festivals.*;
import starpost.realtown.*;
import starpost.scene.*;

/** Captured competition progress; native movement, rings and camera remain engine-owned. */
public final class ActivitySession implements RewindSnapshottable<ActivitySession.Snapshot> {
    private Shell shell;
    private TownSession town;
    private Game game;
    private String kind;
    private boolean active, action, held, menu, jump;
    private int hotbar=-1, ticks, laps, farmer, tricks, trick=-1;
    private boolean wasAir;
    private int crash;
    private int controller=-1;
    private ActExit exit;
    private final List<Race.Rival> rivals=new ArrayList<>();
    private List<RingHunt.Spot> rings=List.of();
    private boolean[] taken=new boolean[0];
    private RingHunt.Champion champion;
    private LakeState lake;
    public void prepare(Shell shell,PlayScreen play,TownSession town,String kind,int seconds) {
        this.shell=shell; this.game=shell.game; this.town=town; this.kind=kind;
        TownBridge.prepare(town,shell,play); town.consumeHandBack();
        active=true; ticks=laps=farmer=tricks=0; trick=-1; controller=-1; wasAir=false; exit=null;
        crash=0; clearInput(); rivals.clear(); champion=null; rings=List.of(); taken=new boolean[0]; lake=null;
        if(kind.equals("race")) for(String who:Race.rivals(game)) rivals.add(new Race.Rival(who,CourseLevel.START,320));
        if(kind.equals("lake")) { lake=new LakeState(game,seconds); lake.poolY(starpost.fishing.LakeOverlay.poolY(shell.art)); }
    }
    public Shell shell() { return shell; }
    public TownSession town() { return town; }
    public Game game() { return game; }
    public String kind() { return kind; }
    public boolean active() { return active; }
    public int ticks() { return ticks; }
    public int laps() { return laps; }
    public int farmer() { return farmer; }
    public int crash() { return crash; }
    public void tumble(AbstractPlayableSprite p) {
        if(crash>0) return; crash=Snowboard.CRASH_TICKS; trick=-1; p.setGSpeed((short)(2*256));
        p.setRingCount(Math.max(0,p.getRingCount()-3)); shell.sfx(starpost.scene.Sfx.RING_LOSS);
    }
    public int tricks() { return tricks; }
    public List<Race.Rival> rivals() { return rivals; }
    public RingHunt.Champion champion() { return champion; }
    public List<RingHunt.Spot> rings() { return rings; }
    public boolean taken(int i) { return taken[i]; }
    public LakeState lake() { return lake; }
    public ActExit exit() { return exit; }
    public int width() { return shell.width(); }
    public boolean claim(int id) { if(controller<0) controller=id; return controller==id; }
    public boolean owns(int id) { return controller==id; }
    public boolean heldPlayer() { return ticks<Race.COUNTDOWN && !kind.equals("lake") || lake!=null && lake.holding(); }
    public void input(boolean action,boolean held,boolean menu,boolean jump,int hotbar) {
        this.action|=action; this.held=held; this.menu|=menu; this.jump|=jump; if(hotbar>=0) this.hotbar=hotbar;
    }
    public void clearInput() { action=held=menu=jump=false; hotbar=-1; }
    public void initHunt(java.util.function.IntUnaryOperator floor) {
        if(!kind.equals("hunt") || champion!=null) return;
        int ledge=7*256;
        rings=RingHunt.layout(game.calendar.year(),floor,ledge+20,ledge+32,ledge+224,128+96);
        taken=new boolean[rings.size()]; champion=new RingHunt.Champion(750,floor.applyAsInt(750)-36);
    }
    public void take(int index,AbstractPlayableSprite player) {
        if(taken[index] || heldPlayer()) return;
        taken[index]=true; farmer++; player.addRings(1);
    }
    public void tick(AbstractPlayableSprite player,java.util.function.IntUnaryOperator floor) {
        if(exit!=null) return;
        ticks++; if(crash>0) crash--;
        if(player.getDead()) { request(ActExit.FAINTED); return; }
        if(hotbar>=0 && (lake==null || !lake.holding())) game.inventory.select(hotbar);
        if(kind.equals("lake")) {
            lake.tick(shell,player,action,held,jump,floor);
            if(lake.done()) request(lake.timeUp()?ActExit.TIME_UP:ActExit.COMPLETED);
            if(menu) request(ActExit.LEFT);
            return;
        }
        if(menu) { request(ActExit.ABORTED); return; }
        if(ticks<=Race.COUNTDOWN) return;
        int run=ticks-Race.COUNTDOWN;
        if(kind.equals("hunt")) {
            champion.step(rings,taken);
            boolean all=true; for(boolean t:taken) all&=t;
            if(run>=RingHunt.TICKS || all) request(ActExit.COMPLETED);
        } else if(kind.equals("race")) {
            float leader=laps*CourseLevel.lapLength()+player.getCentreX();
            for(var r:rivals) leader=Math.max(leader,r.x());
            for(var r:rivals) {
                r.step(x->floor.applyAsInt(CourseLevel.START+Math.floorMod(x-CourseLevel.START,CourseLevel.lapLength())),run,leader,CourseLevel.lapLength());
                if(r.finish<0 && r.x()-r.startX>=Race.LAPS*CourseLevel.lapLength()) r.finish=run;
            }
            if(player.getCentreX()>=CourseLevel.finish(kind,game.calendar.year())) {
                if(++laps>=Race.LAPS) request(ActExit.COMPLETED);
                else {
                    com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player,CourseLevel.START);
                    com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player,floor.applyAsInt(CourseLevel.START)-player.getYRadius());
                    player.setAir(false); player.setYSpeed((short)0); player.setLayer((byte)0);
                }
            }
            if(run>=60*120) request(ActExit.TIME_UP);
        } else {
            // Tricks are native airborne rolls; there is no second movement integrator.
            if(player.getAir() && jump && trick<0 && crash==0) trick=0;
            if(player.getAir() && trick>=0) trick++;
            if(wasAir && !player.getAir()) { if(trick>=30) tricks++; else if(trick>=0) tumble(player); trick=-1; }
            wasAir=player.getAir();
            if(player.getCentreX()>=CourseLevel.finish(kind,game.calendar.year())) request(ActExit.COMPLETED);
            if(run>=60*120) request(ActExit.TIME_UP);
        }
    }
    public void request(ActExit reason) { if(exit==null) exit=reason; }
    public Map<String,String> result(AbstractPlayableSprite player) {
        int run=Math.max(0,ticks-Race.COUNTDOWN);
        int place=Race.place(exit==ActExit.COMPLETED?run:-1,rivals);
        return Map.of("activity.kind",kind,"activity.ticks",Integer.toString(run),"activity.place",Integer.toString(place),
            "activity.rings",Integer.toString(kind.equals("hunt")?farmer:player.getRingCount()),
            "activity.champion",Integer.toString(champion==null?0:champion.score),"activity.tricks",Integer.toString(tricks),
            "activity.points",Integer.toString(lake==null?Snowboard.score(run,player.getRingCount(),tricks):lake.score()));
    }
    public void finish() { if(!active) throw new IllegalStateException("Activity result already applied"); active=false; clearInput(); }
    public record Snapshot(TownSession.Snapshot game,String kind,boolean active,int ticks,int laps,int farmer,int tricks,
        int trick,boolean wasAir,int controller,ActExit exit,List<Race.Rival.Snapshot> rivals,List<RingHunt.Spot> rings,
        List<Boolean> taken,RingHunt.Champion.Snapshot champion,LakeState.Snapshot lake,
        boolean action,boolean held,boolean menu,boolean jump,int hotbar,int crash) {}
    public String key() { return "activities"; }
    public Snapshot capture() {
        List<Boolean> bits=new ArrayList<>(); for(boolean t:taken) bits.add(t);
        return new Snapshot(town==null?null:town.capture(),kind,active,ticks,laps,farmer,tricks,trick,wasAir,controller,exit,
            rivals.stream().map(Race.Rival::capture).toList(),rings,List.copyOf(bits),champion==null?null:champion.capture(),
            lake==null?null:lake.capture(),action,held,menu,jump,hotbar,crash);
    }
    public void restore(Snapshot s) {
        if(s.game()!=null) town.restore(s.game()); kind=s.kind(); active=s.active(); ticks=s.ticks(); laps=s.laps();
        farmer=s.farmer(); tricks=s.tricks(); trick=s.trick(); wasAir=s.wasAir(); controller=s.controller(); exit=s.exit();
        rivals.clear(); s.rivals().forEach(r->rivals.add(Race.Rival.restore(r))); rings=s.rings(); taken=new boolean[s.taken().size()];
        for(int i=0;i<taken.length;i++) taken[i]=s.taken().get(i);
        champion=s.champion()==null?null:RingHunt.Champion.restore(s.champion());
        if(s.lake()!=null) { if(lake==null) lake=new LakeState(game,0); lake.restore(s.lake()); } else lake=null;
        action=s.action(); held=s.held(); menu=s.menu(); jump=s.jump(); hotbar=s.hotbar(); crash=s.crash();
    }
    public void resetForMissingSnapshot() { active=false; controller=-1; clearInput(); }
}
