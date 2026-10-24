package com.openggf.stageplay.runtime;

import com.openggf.audio.*;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Actual final PCM admission and capture, with no ROM or audio device. */
@Isolated
class TestStageplaySpeechAudio {
    private final AudioManager audio=AudioManager.getInstance();
    @BeforeEach void prepare() { audio.resetState(); SonicConfigurationService.getInstance().resetToDefaults(); audio.setBackend(new NullAudioBackend()); }
    @AfterEach void cleanup() { audio.endCaptureMode();audio.resetState();audio.setBackend(new NullAudioBackend()); }
    private StageplayDocument scene() {
        return StageplayDocument.empty("s2",0,0,0,0,320,224).withDurationFrames(50)
                .withDialogue(List.of(new DialogueCue("line",5,35,"","Radio","Hi there.","","",0,0,304,60,"",List.of())));
    }
    private short[] frame() {
        audio.presentFrame(PresentationMode.FORWARD);var pcm=new short[1600];assertEquals(800,audio.drainCaptureFrame(pcm));return pcm;
    }
    private static boolean sounding(short[] pcm) { for(short value:pcm) if(value!=0)return true;return false; }
    private List<short[]> run(boolean repeatCalls) throws Exception {
        var doc=scene(); var frames=new ArrayList<short[]>();
        try(var speech=new StageplayAudio(audio,mock(SceneRomLibrary.class),asset->{throw new AssertionError("No assets");})) {
            speech.prepare(doc);audio.beginCaptureMode(48000,60);
            for(int i=0;i<50;i++) { speech.playFrame(doc,i); if(repeatCalls)speech.playFrame(doc,i); frames.add(frame()); }
        }
        return frames;
    }
    @Test void capturedBlipsStartOnRevealAreReproducibleAndRepeatedDrawTransportNeverDuplicates() throws Exception {
        var first=run(false);audio.endCaptureMode();audio.resetState();audio.setBackend(new NullAudioBackend());
        var second=run(true);
        for(int i=0;i<50;i++)assertArrayEquals(first.get(i),second.get(i),"Export reproducibility, frame "+i);
        for(int i=0;i<5;i++)assertFalse(sounding(first.get(i)));
        for(var event:StageplaySpeech.plan(scene())) {
            assertTrue(sounding(first.get(event.frame())),"Blip's first PCM belongs to the revealed frame");
            assertEquals(StageplayDialogue.revealFrames(scene(),scene().dialogue().getFirst()).get(event.grapheme()),event.frame()-5);
        }
        for(int i=35;i<50;i++)assertFalse(sounding(first.get(i)),"No speech tail after dialogue");
    }
    @Test void seekIsSilentStopsTailsAndResumesOnlyFutureSpeech() throws Exception {
        var doc=scene();
        try(var speech=new StageplayAudio(audio,mock(SceneRomLibrary.class),asset->{throw new AssertionError();})) {
            speech.prepare(doc);audio.beginCaptureMode(48000,60);
            speech.playFrame(doc,5);assertTrue(sounding(frame()));
            speech.seek(10);assertFalse(sounding(frame()));speech.playFrame(doc,10);assertFalse(sounding(frame()));
            int next=StageplaySpeech.plan(doc).getLast().frame();speech.playFrame(doc,next);assertTrue(sounding(frame()));
            speech.seek(0);assertFalse(sounding(frame()));speech.playFrame(doc,5);assertTrue(sounding(frame()));
            speech.stop();assertFalse(sounding(frame()));
        }
    }
    @Test void chipTimbresProduceDifferentBoundedPcmWithCleanEndsAndFixedIdentity() {
        var waves=new HashSet<Integer>();
        for(var tone:List.of(VoiceTone.SQUARE,VoiceTone.CHIME,VoiceTone.HOLLOW,VoiceTone.BUZZ)) {
            var voice=new SpeakerVoice("","Radio",tone,VoiceRhythm.WORD,69,0,8,100);
            var blip=new StageplaySpeech.Blip("line",0,0,69,8,voice);
            var a=StageplaySpeechSynth.render(blip);var b=StageplaySpeechSynth.render(blip);
            assertArrayEquals(a.samples(),b.samples());assertEquals(6400,a.samples().length);
            assertEquals(0,a.samples()[0]);assertEquals(0,a.samples()[a.samples().length-1]);
            int peak=0;for(short value:a.samples())peak=Math.max(peak,Math.abs((int)value));
            assertTrue(peak>1000 && peak<=9000);waves.add(Arrays.hashCode(a.samples()));
        }
        assertEquals(4,waves.size());
    }
    @Test void transientSpeechCopiesItsInputAndOverloadedMusicKeepsHeadroom() {
        audio.setScenePcmSource((pcm,frames)->Arrays.fill(pcm,0,frames*2,(short)28000));
        var pcm=new short[800];Arrays.fill(pcm,(short)10000);
        try(var scope=audio.prepareSceneSamples(Map.of())) {
            scope.playTransient(new StreamedMusicPort.SfxPcm(48000,1,pcm,1));Arrays.fill(pcm,(short)-30000);
            audio.beginCaptureMode(48000,60);for(short value:frame())assertEquals(30000,value,"Block limiter preserves PCM headroom");
            for(short value:frame())assertEquals(28000,value,"Inactive speech never changes the bed");
            audio.setScenePcmSource((target,frames)->Arrays.fill(target,0,frames*2,(short)32000));
            scope.playTransient(new StreamedMusicPort.SfxPcm(48000,1,new short[800],1));
            for(short value:frame())assertEquals(32000,value,"A silent packet cannot duck the borrowed soundtrack");
            assertThrows(IllegalArgumentException.class,()->scope.playTransient(new StreamedMusicPort.SfxPcm(48000,1,new short[24001],1)));
        }
    }
}
