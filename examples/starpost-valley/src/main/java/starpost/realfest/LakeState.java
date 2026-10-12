package starpost.realfest;

import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import starpost.core.Game;
import starpost.fishing.*;
import starpost.scene.Shell;

/** Cast, bite and Bubble Bar rules driven by a level director and captured with the day. */
public final class LakeState {
    private final Game game;
    private final FishTable table;
    public final Line line=new Line();
    private BubbleBar bar;
    private String hooked;
    private int poolY=CourseLevel.POOL_Y;
    private int charge, left, score, cooldown, barResult, endTicks;
    private boolean charging, contest, done, timeUp;
    private String notice="";
    public LakeState(Game game,int seconds) {
        this.game=game; table=Fishing.section(game).table(); contest=seconds>0; left=seconds*60;
    }
    public int poolY() { return poolY; }
    public void poolY(int value) { poolY=value; }
    public int barResult() { return barResult; }
    public boolean holding() { return charging || line.out(); }
    public boolean charging() { return charging; }
    public boolean contest() { return contest; }
    public boolean done() { return done; }
    public boolean timeUp() { return timeUp; }
    public int left() { return left; }
    public int score() { return score; }
    public BubbleBar bar() { return bar; }
    public FishDef hooked() { return hooked==null?null:table.get(hooked); }
    public String notice() { return notice; }
    public float power() { float p=charge%100/50f; return p>1?2-p:p; }
    public void tick(Shell shell,AbstractPlayableSprite player,boolean action,boolean held,boolean jump,
                     java.util.function.IntUnaryOperator floor) {
        if(contest) { if(left>0 && --left==0) { done=true; line.reelIn(); bar=null; return; } }
        else { game.calendar.tick(); if(game.calendar.overtime()) { timeUp=done=true; return; } }
        if(cooldown>0) cooldown--;
        int feet=player.getCentreY()+player.getYRadius();
        if(feet>poolY+12 && player.getCentreX()>=256) {
            line.reelIn(); charging=false; bar=null;
            NativePositionOps.writeXPosResetSubpixel(player,40);
            NativePositionOps.writeYPosResetSubpixel(player,floor.applyAsInt(40)-player.getYRadius());
            player.setAir(false); player.setXSpeed((short)0); player.setYSpeed((short)0); player.setGSpeed((short)0);
            notice="BACK TO THE SHORE"; shell.sfx(0x39); return;
        }
        if(player.getCentreX()<12 && !holding()) { done=true; return; }
        if(bar!=null) {
            if(barResult==BubbleBar.PLAYING) barResult=bar.step(held,game.rng);
            else if(++endTicks>=50) {
                if(barResult==BubbleBar.CAUGHT) land(hooked,bar.perfect);
                else notice="IT GOT AWAY";
                bar=null; hooked=null; line.reelIn(); cooldown=30;
            }
            return;
        }
        if(line.out()) {
            if(action) {
                if(line.strike() && hooked!=null) {
                    var def=table.get(hooked);
                    if(def==null) { land(hooked,false); line.reelIn(); cooldown=30; }
                    else { bar=new BubbleBar(def.difficulty(),def.motion(),Fishing.bubbleHalf(game),game.rng); barResult=0; endTicks=0; }
                } else line.reelIn();
                return;
            }
            if(jump) { line.reelIn(); return; }
            int event=line.step(game.rng);
            if(event==Line.SPLASH || event==Line.MISSED) { if(contest) line.timer=Math.max(30,line.timer/2); shell.sfx(0x39); }
            if(event==Line.BITE_NOW) {
                hooked=table.choose(FishTable.Waters.of(game,FishDef.LAKE,line.depth,Fishing.section(game).landedOnce()),game.rng);
                notice="B / X: STRIKE!"; shell.sfx(0x39);
            }
        } else if(charging) {
            if(held) charge++;
            else {
                charging=false;
                float tx=player.getCentreX()+36+30+power()*230;
                if(tx<264 || player.getDirection()==com.openggf.physics.Direction.LEFT) { notice="THE LINE LANDS ON THE BANK"; return; }
                tx=Math.min(752,tx);
                int depth=Math.round((tx-256)*100/496);
                line.cast(player.getCentreX()+36,feet-30,tx,poolY,depth,Fishing.level(game)); hooked=null;
                shell.sfx(0x4E);
            }
        } else if(action && cooldown==0 && !player.getAir()) {
            if(contest || Fishing.holdingRod(game)) { charging=true; charge=0; }
            else notice="HOLD THE FISHING ROD TO CAST";
        }
    }
    private void land(String id,boolean perfect) {
        var landed=Fishing.land(game,id,perfect); notice=landed.message();
        if(contest) score+=Fishing.contestPoints(table.get(id));
    }
    public record Snapshot(Line.Snapshot line,BubbleBar.Snapshot bar,String hooked,int charge,int left,int score,
        int cooldown,int barResult,int endTicks,boolean charging,boolean contest,boolean done,boolean timeUp,String notice,int poolY) {}
    public Snapshot capture() { return new Snapshot(line.capture(),bar==null?null:bar.capture(),hooked,charge,left,score,
        cooldown,barResult,endTicks,charging,contest,done,timeUp,notice,poolY); }
    public void restore(Snapshot s) {
        line.restore(s.line()); bar=s.bar()==null?null:BubbleBar.restore(s.bar()); hooked=s.hooked(); charge=s.charge();
        left=s.left(); score=s.score(); cooldown=s.cooldown(); barResult=s.barResult(); endTicks=s.endTicks();
        charging=s.charging(); contest=s.contest(); done=s.done(); timeUp=s.timeUp(); notice=s.notice(); poolY=s.poolY();
    }
}
