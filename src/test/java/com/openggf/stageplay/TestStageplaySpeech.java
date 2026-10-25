package com.openggf.stageplay;

import com.openggf.tools.StageplayScriptCompiler;
import com.openggf.tools.StageplayScriptDecompiler;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplaySpeech {
    private StageplayDocument cast() {
        var key = new ActorKey(0,100,100,0,-1,StageplayDocument.Facing.RIGHT,true,1,Interpolation.HOLD);
        var objectKey = new ObjectKey(0,100,100,0,-1,StageplayDocument.Facing.LEFT,true,Interpolation.HOLD,ObjectMode.POSE);
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
        assertEquals(List.of(
                new SpeakerVoice("sonic","",VoiceTone.HOLLOW,VoiceRhythm.WORD,67,2,7,80),
                new SpeakerVoice("tails","",VoiceTone.BUZZ,VoiceRhythm.LETTER,79,2,2,70),
                new SpeakerVoice("knuckles","",VoiceTone.BUZZ,VoiceRhythm.LETTER,62,2,3,78),
                new SpeakerVoice("moto","",VoiceTone.BUZZ,VoiceRhythm.WORD,69,4,8,80),
                new SpeakerVoice("buzz","",VoiceTone.CHIME,VoiceRhythm.LETTER,75,4,3,80)), defaults);
        assertEquals(5,defaults.stream().map(v->v.tone()+":"+v.pitch()+":"+v.rhythm()).distinct().count());
        var renamed=doc.withActors(doc.actors().stream().map(a->new Actor(a.id(),"Other",a.gameId(),a.character(),a.keys())).toList());
        assertEquals(defaults.getFirst(),StageplaySpeech.defaultVoice(renamed,"sonic",""));
        assertThrows(IllegalArgumentException.class,()->StageplaySpeech.defaultVoice(doc,"missing",""));
    }
    @Test void chosenObjectVoicesFollowNativeCharacterIdentityAcrossSubtypesAndRoleRenames() {
        var doc=cast();
        var objects=doc.objectActors().stream().map(a->new ObjectActor(a.id(),"Other",a.gameId(),a.zone(),a.act(),
                a.objectId(),7,a.layoutIndex(),a.keys(),a.commands(),a.sourceKind())).toList();
        var renamed=doc.withObjectActors(objects);
        assertEquals(StageplaySpeech.Preset.PATROL_BUZZ.voice("moto",""),StageplaySpeech.defaultVoice(renamed,"moto",""));
        assertEquals(StageplaySpeech.Preset.WING_CHIME.voice("buzz",""),StageplaySpeech.defaultVoice(renamed,"buzz",""));
        for(String game:List.of("s1","s2","s3k")) {
            var actor=doc.actors().getFirst();
            var donor=doc.withActors(List.of(new Actor(actor.id(),"Other",game,"sonic",actor.keys())));
            assertEquals(StageplaySpeech.Preset.SOFT_COOL.voice("sonic",""),StageplaySpeech.defaultVoice(donor,"sonic",""));
        }
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
    @Test void allAuditionPresetsKeepTheirExactSettingsAndRoundTripAsEditableOverrides() throws Exception {
        // User-auditioned values are the contract, including the three superseded player defaults.
        var expected=List.of(
                new SpeakerVoice("sonic","",VoiceTone.SQUARE,VoiceRhythm.WORD,72,5,5,80),
                new SpeakerVoice("sonic","",VoiceTone.HOLLOW,VoiceRhythm.WORD,67,2,7,80),
                new SpeakerVoice("sonic","",VoiceTone.SQUARE,VoiceRhythm.LETTER,76,4,2,74),
                new SpeakerVoice("sonic","",VoiceTone.CHIME,VoiceRhythm.LETTER,84,3,2,72),
                new SpeakerVoice("sonic","",VoiceTone.SQUARE,VoiceRhythm.WORD,77,3,6,80),
                new SpeakerVoice("sonic","",VoiceTone.BUZZ,VoiceRhythm.LETTER,79,2,2,70),
                new SpeakerVoice("sonic","",VoiceTone.HOLLOW,VoiceRhythm.WORD,55,2,8,88),
                new SpeakerVoice("sonic","",VoiceTone.SQUARE,VoiceRhythm.WORD,57,1,9,88),
                new SpeakerVoice("sonic","",VoiceTone.BUZZ,VoiceRhythm.LETTER,62,2,3,78),
                new SpeakerVoice("sonic","",VoiceTone.BUZZ,VoiceRhythm.WORD,69,4,8,80),
                new SpeakerVoice("sonic","",VoiceTone.SQUARE,VoiceRhythm.WORD,69,4,8,80),
                new SpeakerVoice("sonic","",VoiceTone.HOLLOW,VoiceRhythm.WORD,57,3,10,84),
                new SpeakerVoice("sonic","",VoiceTone.CHIME,VoiceRhythm.LETTER,75,4,3,80),
                new SpeakerVoice("sonic","",VoiceTone.BUZZ,VoiceRhythm.WORD,78,5,5,80),
                new SpeakerVoice("sonic","",VoiceTone.HOLLOW,VoiceRhythm.WORD,62,1,8,84));
        var presets=StageplaySpeech.Preset.values();
        assertEquals(15,presets.length);
        assertEquals(List.of("Sonic / Blue Spark", "Sonic / Soft Cool", "Sonic / Speed Talk", "Tails / Twin Chime", "Tails / Warm Pips", "Tails / Workshop", "Knuckles / Stone Hollow", "Knuckles / Blunt Square", "Knuckles / Red Rattle", "Motobug / Patrol Buzz", "Motobug / Little Square", "Motobug / Rusty Low", "Buzz Bomber / Wing Chime", "Buzz Bomber / Radio Buzz", "Buzz Bomber / Deadpan"),
                java.util.Arrays.stream(presets).map(StageplaySpeech.Preset::label).toList());
        for(int i=0;i<presets.length;i++) {
            var voice=presets[i].voice("sonic","");
            assertEquals(expected.get(i),voice,presets[i].label());
            var doc=cast().withVoices(List.of(voice));
            assertEquals(voice,StageplaySpeech.voice(doc,"sonic",""));
            assertEquals(doc,StageplayStore.decode(StageplayStore.encode(doc)));
            assertEquals(doc,StageplayScriptCompiler.compile(StageplayScriptDecompiler.decompile(doc)));
            var offscreen=presets[i].voice("","Radio");
            assertEquals("",offscreen.speakerActor());assertEquals("Radio",offscreen.speakerName());
            var radio=cast().withVoices(List.of(offscreen));
            assertEquals(offscreen,StageplaySpeech.voice(radio,"","Radio"));
        }
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
