package com.openggf.stageplay;

import com.openggf.tools.StageplayScriptCompiler;
import com.openggf.tools.StageplayScriptDecompiler;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplaySpeech {
    private StageplayDocument cast() {
        var key = new ActorKey(0,100,100,0,-1,false,true,1,Interpolation.HOLD);
        var objectKey = new ObjectKey(0,100,100,0,-1,false,true,Interpolation.HOLD,ObjectMode.POSE);
        return StageplayDocument.empty("s1",0,0,0,0,320,224).withActors(List.of(
                new Actor("sonic","Sonic","s1","sonic",List.of(key)),
                new Actor("tails","Tails","s2","tails",List.of(key)),
                new Actor("knuckles","Knuckles","s3k","knuckles",List.of(key))))
                .withObjectActors(List.of(new ObjectActor("moto","Motobug","s1",0,0,64,0,-1,List.of(objectKey),List.of()),
                        new ObjectActor("buzz","Buzz Bomber","s1",0,0,34,0,-1,List.of(objectKey),List.of())));
    }
    private DialogueCue line(String text, List<Integer> offsets) {
        return new DialogueCue("line",10,100,"moto","",text,"","",0,0,304,72,"",offsets);
    }
    @Test void characterAndObjectDefaultsAreDistinctAndNamesDoNotSelectSound() {
        var doc=cast();
        var defaults=List.of("sonic","tails","knuckles","moto","buzz").stream()
                .map(id->StageplaySpeech.defaultVoice(doc,id,"")).toList();
        assertEquals(List.of(VoiceTone.SQUARE,VoiceTone.CHIME,VoiceTone.HOLLOW,VoiceTone.BUZZ,VoiceTone.CHIME),
                defaults.stream().map(SpeakerVoice::tone).toList());
        assertEquals(List.of(72,84,55,69,75), defaults.stream().map(SpeakerVoice::pitch).toList());
        assertEquals(5,defaults.stream().map(v->v.tone()+":"+v.pitch()+":"+v.rhythm()).distinct().count());
        var renamed=doc.withActors(doc.actors().stream().map(a->new Actor(a.id(),"Other",a.gameId(),a.character(),a.keys())).toList());
        assertEquals(defaults.getFirst(),StageplaySpeech.defaultVoice(renamed,"sonic",""));
        assertThrows(IllegalArgumentException.class,()->StageplaySpeech.defaultVoice(doc,"missing",""));
    }
    @Test void wordsLettersPunctuationAndGroupedUnicodeFollowTheActualRevealOffsets() {
        var cue=line("Hi, e\u0301! 42",List.of(0,0,1,2,5,6,7,8,8));
        var doc=cast().withDialogue(List.of(cue));
        var word=StageplaySpeech.plan(doc);
        assertEquals(List.of(10,15,18),word.stream().map(StageplaySpeech.Blip::frame).toList());
        assertEquals(List.of(0,4,7),word.stream().map(StageplaySpeech.Blip::grapheme).toList());
        assertEquals(List.of(5,3,8),word.stream().map(StageplaySpeech.Blip::lengthFrames).toList());
        doc=doc.withVoices(List.of(new SpeakerVoice("moto","",VoiceTone.BUZZ,VoiceRhythm.LETTER,69,0,8,80)));
        assertEquals(List.of(10,15,18),StageplaySpeech.plan(doc).stream().map(StageplaySpeech.Blip::frame).toList());
        for(var event:StageplaySpeech.plan(doc)) {
            int elapsed=event.frame()-cue.startFrame();
            assertTrue(StageplayDialogue.graphemeCount(StageplayDialogue.visibleText(doc,cue,elapsed))>event.grapheme());
            assertTrue(StageplayDialogue.graphemeCount(StageplayDialogue.visibleText(doc,cue,elapsed-1))<=event.grapheme());
            assertEquals(69,event.pitch());
        }
        assertTrue(StageplaySpeech.plan(cast().withDialogue(List.of(line("...?!",List.of())))).isEmpty());
        var instant=cast().withDialogueTypers(List.of(new DialogueTyper("instant",0,0,0)));
        var q=new DialogueCue("instant",0,10,"tails","","Many letters at once","","instant",0,0,304,72,"",List.of());
        assertEquals(1,StageplaySpeech.plan(instant.withDialogue(List.of(q))).size());
    }
    @Test void explicitVoicesAndOffscreenVoicesRoundTripAndEveryCopyHelperPreservesThem() throws Exception {
        var doc=cast().withVoices(List.of(new SpeakerVoice("moto","",VoiceTone.HOLLOW,VoiceRhythm.WORD,60,3,8,55),
                new SpeakerVoice("","Radio",VoiceTone.OFF,VoiceRhythm.LETTER,80,0,2,100)))
                .withDialogue(List.of(line("Day off.",List.of())));
        assertEquals(doc,StageplayStore.decode(StageplayStore.encode(doc)));
        assertEquals(doc,StageplayScriptCompiler.compile(StageplayScriptDecompiler.decompile(doc)));
        assertEquals(doc.voices(),doc.withName("Edited").withDurationFrames(601).withSize(400,270).withSet(doc.set())
                .withActors(doc.actors()).withCamera(doc.camera()).withCaptions(doc.captions()).withAudio(doc.audio())
                .withCameraEffects(doc.cameraEffects()).withSceneChanges(doc.sceneChanges()).withDialogueTypers(doc.dialogueTypers())
                .withDialogueSkins(doc.dialogueSkins()).withDialogue(doc.dialogue()).withPuppets(doc.puppets())
                .withObjectActors(doc.objectActors()).withWorldCommands(doc.worldCommands()).voices());
        assertEquals(VoiceTone.OFF,StageplaySpeech.voice(doc,"","Radio").tone());
        var silent=doc.withVoices(List.of(new SpeakerVoice("moto","",VoiceTone.OFF,VoiceRhythm.WORD,60,0,8,100)));
        assertTrue(StageplaySpeech.plan(silent).isEmpty());
        assertEquals(StageplaySpeech.plan(doc),StageplaySpeech.plan(doc));
        assertThrows(UnsupportedOperationException.class,()->doc.voices().clear());
        assertThrows(IllegalArgumentException.class,()->doc.withVoices(List.of(doc.voices().getFirst(),doc.voices().getFirst())));
        assertThrows(IllegalArgumentException.class,()->doc.withVoices(List.of(new SpeakerVoice("missing","",VoiceTone.SQUARE,VoiceRhythm.WORD,72,0,5,80))));
        assertThrows(IllegalArgumentException.class,()->new SpeakerVoice("moto","Name",VoiceTone.SQUARE,VoiceRhythm.WORD,72,0,5,80));
        assertThrows(IllegalArgumentException.class,()->new SpeakerVoice("moto","",VoiceTone.SQUARE,VoiceRhythm.WORD,47,0,5,80));
        assertThrows(IllegalArgumentException.class,()->new SpeakerVoice("moto","",VoiceTone.SQUARE,VoiceRhythm.WORD,72,13,5,80));
        assertThrows(IllegalArgumentException.class,()->new SpeakerVoice("moto","",VoiceTone.SQUARE,VoiceRhythm.WORD,72,0,19,80));
        assertThrows(IllegalArgumentException.class,()->new SpeakerVoice("moto","",VoiceTone.SQUARE,VoiceRhythm.WORD,72,0,5,101));
    }
}
