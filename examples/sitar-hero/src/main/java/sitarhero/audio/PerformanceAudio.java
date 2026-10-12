package sitarhero.audio;

import com.openggf.mods.scene.*;
import sitarhero.model.*;
import sitarhero.net.OnlineMatch;
import java.util.List;
import java.util.Comparator;

/** Mod-owned mistake feel and multiplayer mix policy; the host only plays bounded ROM-part cues. */
public final class PerformanceAudio {
    private final SceneMusicPlayer player;
    private final ScenePreparedMusic music;
    private final Chart chart;
    private record Section(long onset, List<SceneNoteEvent> attacks) { }
    private record CueShape(int millis, double from, double to, double gain) { }
    private final List<Section> sections;
    private final Role role;
    private final boolean local, coop;
    private final boolean[] audible = {true,true};
    private final long[] lastCue = {Long.MIN_VALUE,Long.MIN_VALUE};
    private boolean bandAudible = true;

    public PerformanceAudio(SceneMusicPlayer player, ScenePreparedMusic music, Chart chart,
                            List<SceneMusicPart> parts, Role role, boolean local, boolean coop) {
        this.player=player;this.music=music;this.chart=chart;
        this.role=role;this.local=local;this.coop=coop;
        var selected = new java.util.ArrayList<Section>();
        for (int index = 0; index < parts.size(); index++) {
            SceneMusicPart part = parts.get(index);
            long end = index + 1 < parts.size() ? parts.get(index + 1).onsetSamples() : music.lengthSamples();
            selected.add(new Section(part.onsetSamples(), music.notes().stream()
                    .filter(note -> note.onsetSamples() >= part.onsetSamples() && note.onsetSamples() < end)
                    .filter(note -> switch (note.kind()) {
                        case FM -> (part.fmMask() & (1 << note.channel())) != 0;
                        case PSG -> (part.psgMask() & (1 << note.channel())) != 0;
                        case DAC -> part.dacMuted();
                    })
                    .sorted(Comparator.comparingLong(SceneNoteEvent::onsetSamples)).toList()));
        }
        sections = List.copyOf(selected);
    }
    public void update(RhythmSession first, RhythmSession second, OnlineMatch peer, long now) {
        var events=first.drainFeedback();
        if(peer!=null) {
            // Calibration changes judgment coordinates, not the agreed audible song clock.
            long offset = now - first.position();
            var onSongClock = events.stream().map(event -> new RhythmSession.Feedback(event.sequence(),
                    event.sample() + offset, event.noteIndex(), event.lanes(), event.kind(), event.audible())).toList();
            peer.feedback(now,first.partAudible(),onSongClock);
        }
        boolean desired=first.partAudible() || second!=null && second.partAudible()
                || peer!=null && coop && peer.remoteAudible();
        boolean choke=bandAudible && !desired;
        choke=events(events,0,now,first.position(),choke,desired);
        if(second!=null)choke=events(second.drainFeedback(),1,now,second.position(),choke,desired);
        if(peer!=null)for(OnlineMatch.Cue cue:peer.drainCues()) {
            if(fresh(cue.sample(),now) && eligible(1,now)) {
                boolean useChoke=choke && !role.drums();
                play(cue.noteIndex(),1,now,useChoke,cue.strike(),true,desired);
                choke=false;
            }
        }
        player.setPartAudible(desired);bandAudible=desired;
    }
    private boolean events(List<RhythmSession.Feedback> events,int owner,long now,long judgmentNow,boolean choke,boolean sharedAudible) {
        for(var event:events) {
            boolean mistake=event.kind()==RhythmSession.FeedbackKind.STRIKE
                    || event.kind()==RhythmSession.FeedbackKind.MISS && audible[owner];
            audible[owner]=event.audible();
            if(mistake && fresh(event.sample(),judgmentNow) && eligible(owner,now)) {
                boolean useChoke=choke && !role.drums();
                play(event.noteIndex(),owner,now,useChoke,event.kind()==RhythmSession.FeedbackKind.STRIKE,false,sharedAudible);
                choke=false;
            }
        }
        return choke;
    }
    private boolean fresh(long sample,long now) { return sample<=now+music.sampleRate()/10 && now-sample<=music.sampleRate()/4; }
    private boolean eligible(int owner,long now) {
        long previous=lastCue[owner];return previous==Long.MIN_VALUE || now-previous>=music.sampleRate()*(role.drums()?30:45)/1000;
    }
    private void play(int note,int owner,long now,boolean choke,boolean strike,boolean remote,boolean sharedAudible) {
        if(note<0 || note>=chart.notes().size())return;
        long source=choke?SceneMusicPlayer.PLAYHEAD:source(chart.notes().get(note).onset());
        if(source<0 && source!=SceneMusicPlayer.PLAYHEAD)return;
        CueShape shape = shape(choke,strike);
        double gain = shape.gain();
        double pan=local?(owner==0?-.35:.35):remote?.4:0;
        if(remote)gain*=coop?.5:.4;
        else if(sharedAudible && !strike)gain*=.75;
        if(player.cuePart(source,Math.max(1,music.sampleRate()*shape.millis()/1000),shape.from(),shape.to(),gain,pan))lastCue[owner]=now;
    }
    /** Performer feel belongs here; rates below one bend the actual ROM timbre downward. */
    private CueShape shape(boolean choke, boolean strike) {
        return switch (role) {
            case SITAR -> choke ? new CueShape(90,1,.90,.8)
                    : strike ? new CueShape(55,.971,.971,.35) : new CueShape(60,.985,.90,.4);
            case HARP -> choke ? new CueShape(60,1,.92,.7)
                    : strike ? new CueShape(40,.97,.97,.3) : new CueShape(45,.97,.92,.35);
            case SYNTH -> choke ? new CueShape(50,1,.94,.6)
                    : strike ? new CueShape(30,1,1,.22) : new CueShape(40,.94,.94,.28);
            case BONGOS -> strike ? new CueShape(70,.92,.92,.35) : new CueShape(60,.85,.85,.4);
        };
    }
    private long source(long onset) {
        if (sections.isEmpty()) return -1;
        Section section = sections.getFirst();
        for (Section candidate : sections) {
            if (candidate.onset() > onset) break;
            section = candidate;
        }
        List<SceneNoteEvent> attacks = section.attacks();
        if (attacks.isEmpty()) return -1;
        // Index once at song start; a wrong strike must not scan a full ten-minute song.
        int low = 0, high = attacks.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (attacks.get(middle).onsetSamples() < onset) low = middle + 1;
            else high = middle;
        }
        SceneNoteEvent closest = attacks.get(Math.min(low, attacks.size() - 1));
        if (low > 0) {
            SceneNoteEvent previous = attacks.get(low - 1);
            if (Math.abs(previous.onsetSamples() - onset) <= Math.abs(closest.onsetSamples() - onset))
                closest = previous;
        }
        return Math.min(music.lengthSamples()-1,closest.onsetSamples()+Math.min(music.sampleRate()/125,closest.durationSamples()/4));
    }
}
