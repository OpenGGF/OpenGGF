package sitarhero.audio;

import com.openggf.mods.scene.*;
import sitarhero.model.*;
import java.util.*;

/** Executes the actual mod audio policy over explicit musical inputs, not mocked policy decisions. */
public final class PerformanceAudioChecks {
    private record Cue(long source,int length,double from,double to,double gain,double pan) { }
    private static final class Player implements SceneMusicPlayer {
        boolean audible=true,paused,stopped;final List<Cue> cues=new ArrayList<>();
        public long samplePosition(){return 0;}public long samplePositionAt(long n){return 0;}
        public void pause(){paused=true;}public void resume(){paused=false;}
        public void setPartAudible(boolean v){audible=v;}public void setWhammy(double v){}
        public boolean cuePart(long s,int l,double f,double t,double g,double p){if(paused||stopped)return false;cues.add(new Cue(s,l,f,t,g,p));return true;}
        public boolean finished(){return stopped;}public boolean paused(){return paused;}
        public long underrunCount(){return 0;}public void stop(){stopped=true;cues.clear();}
    }
    private static void require(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private static Chart chart(){return new Chart(List.of(new ChartNote(0,0,1,false,-1,0),new ChartNote(1000,1000,2,false,-1,0),new ChartNote(2000,2000,4,false,-1,0)),10000,500);}
    private static ScenePreparedMusic music(List<SceneNoteEvent> notes){return new ScenePreparedMusic(){public int sampleRate(){return 1000;}public long lengthSamples(){return 10000;}public List<SceneNoteEvent> notes(){return notes;}};}
    private static ScenePreparedMusic music(){return music(List.of(new SceneNoteEvent(0,SceneNoteEvent.Kind.FM,0,128,0,0,500),new SceneNoteEvent(0,SceneNoteEvent.Kind.FM,0,130,0,1000,500),new SceneNoteEvent(0,SceneNoteEvent.Kind.FM,0,131,0,2000,500)));}
    private static PerformanceAudio audio(Player p,Chart c,boolean local){return new PerformanceAudio(p,music(),c,List.of(new SceneMusicPart(0,1,0,false)),Role.SITAR,local,true);}
    public static void soloAndOverstrikes(){
        var c=chart();var p=new Player();var s=new RhythmSession(c,false,1000,true);var a=audio(p,c,false);
        s.advance(150);a.update(s,null,null,150);require(!p.audible && p.cues.size()==1,"first miss chokes the solo part");
        require(p.cues.getFirst().source()==SceneMusicPlayer.PLAYHEAD && p.cues.getFirst().to()<1,"choke continues and bends the playing part");
        s.advance(1200);a.update(s,null,null,1200);require(p.cues.size()==1,"idle misses stay quiet");
        s.input(1700,1,0,true,false,false);a.update(s,null,null,1700);require(p.cues.size()==2,"wrong strikes still plink when already muted");
        s.input(1720,1,0,true,false,false);a.update(s,null,null,1720);require(p.cues.size()==2,"rapid mistakes are rate limited");
        s.input(2000,4,0,true,false,false);a.update(s,null,null,2000);require(p.audible,"successful note restores part");
        var strike=new RhythmSession(c,false,1000,true);var struck=new Player();
        strike.input(0,0,0,true,false,false);audio(struck,c,false).update(strike,null,null,0);
        require(struck.cues.size()==1 && struck.cues.getFirst().to()<1,"first wrong strike chokes with a downward glide");
        var outro=new RhythmSession(c,false,1000,true);var out=new Player();var policy=audio(out,c,false);
        outro.advance(3000);policy.update(outro,null,null,3000);outro.input(3100,1,0,true,false,false);policy.update(outro,null,null,3100);
        require(out.cues.size()==1,"a wrong strike after every gem was missed still has a real instrument source");
    }
    public static void localOwnership(){
        var c=chart();var p=new Player();var first=new RhythmSession(c,false,1000,true);var second=new RhythmSession(c,false,1000,true);var a=audio(p,c,true);
        first.input(0,1,0,true,false,false);second.advance(150);a.update(first,second,null,150);
        require(p.audible && p.cues.size()==1,"partner success preserves shared part");
        require(p.cues.getFirst().pan()==.35 && p.cues.getFirst().source()!=SceneMusicPlayer.PLAYHEAD,"only second player plinks");
        first.advance(1200);second.advance(1200);a.update(first,second,null,1200);
        require(!p.audible && p.cues.size()==2 && p.cues.getLast().pan()==-.35,"both miss: first player chokes, idle second stays quiet");
        second.input(2000,4,0,true,false,false);a.update(first,second,null,2000);require(p.audible,"either player can restore shared part");
    }
    public static void quietTailsAndStalls(){
        Chart c=new Chart(List.of(new ChartNote(0,1000,1,false,-1,8),new ChartNote(2000,2000,2,false,-1,0)),10000,500);
        var p=new Player();var s=new RhythmSession(c,false,1000,true);var a=audio(p,c,false);
        s.input(0,1,0,true,false,false);a.update(s,null,null,0);s.input(100,0,0,false,false,false);a.update(s,null,null,100);
        require(!p.audible && p.cues.isEmpty() && s.streak()==1,"release is quiet and preserves streak");
        for (long offset : new long[]{-250,250}) {
            Chart calibrationChart=new Chart(List.of(new ChartNote(1000,1000,1,false,-1,0)),10000,500);
            var calibrated=new RhythmSession(calibrationChart,false,1000,true);var sound=new Player();
            calibrated.advance(1150);audio(sound,calibrationChart,false).update(calibrated,null,null,1150+offset);
            require(sound.cues.size()==1,"live calibrated miss is not stale or a future cue");
        }
        var idle=new RhythmSession(chart(),false,1000,true);var stale=new Player();audio(stale,chart(),false).update(after(idle,1500),null,null,1500);
        require(stale.cues.isEmpty(),"catch-up misses after a stall cannot play old fumbles");
    }
    private static RhythmSession after(RhythmSession s,long at){s.advance(at);return s;}
    public static void sectionSourceOwnership(){
        Chart c=new Chart(List.of(new ChartNote(1995,1995,1,false,-1,0)),10000,500);
        var notes=List.of(new SceneNoteEvent(0,SceneNoteEvent.Kind.FM,0,128,0,0,500),
                new SceneNoteEvent(1,SceneNoteEvent.Kind.PSG,1,128,0,1100,100),new SceneNoteEvent(1,SceneNoteEvent.Kind.PSG,1,128,0,2001,100));
        var p=new Player();var a=new PerformanceAudio(p,music(notes),c,List.of(new SceneMusicPart(0,1,0,false),new SceneMusicPart(1000,0,2,false),new SceneMusicPart(2000,1,0,false)),Role.SYNTH,true,true);
        var bad=new RhythmSession(c,false,1000,true);var partner=new RhythmSession(c,false,1000,true);bad.advance(2100);a.update(bad,partner,null,2100);
        require(p.cues.size()==1 && p.cues.getFirst().source()==1108,"cue stays in the failed note's section instead of borrowing a future instrument");
    }
}
