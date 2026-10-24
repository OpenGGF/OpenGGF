package com.openggf.editor.stageplay;

import com.openggf.control.InputHandler;
import com.openggf.editor.project.EditorProjectMenu;
import com.openggf.stageplay.StageplayDialogue;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static com.openggf.editor.stageplay.StageplayWorkspace.Action.*;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Both frontends invoke these actual shared forms; no ROM or graphics context is needed. */
class TestStageplayDialogueWorkspace {
    @TempDir Path root;

    private StageplayWorkspace workspace() {
        var workspace = new StageplayWorkspace(new StageplayWorkspace.Host() {
            public StageplayDocument initialDocument() { return StageplayDocument.empty("s2",0,0,0,0,320,224).withSet(new SetSource("s2",0,0,"","")); }
            public Path initialAssetRoot() { return root; }
        });
        workspace.activate(); workspace.invoke(ADD_ACTOR); workspace.menu().setValue("name","Sonic");
        workspace.menu().setValue("mode","POSER"); workspace.menu().submit();
        return workspace;
    }

    private static DialogueCue addLine(StageplayWorkspace workspace) {
        workspace.invoke(DIALOGUE); choose(workspace.menu(),0); choose(workspace.menu(),0);
        workspace.menu().setValue("text","Hello, Tails!"); workspace.menu().submit();
        assertNull(workspace.menu().error());
        return workspace.controller().document().dialogue().getFirst();
    }

    @Test void namedSpeakerDefaultsEditAndRemoveUseSharedUndo() {
        var workspace=workspace(); var cue=addLine(workspace);
        assertEquals("Sonic",StageplayDialogue.speakerName(workspace.controller().document(),cue));
        assertEquals("",cue.skinId()); assertEquals("",cue.typerId());
        workspace.invoke(EDIT_ACTOR); workspace.menu().setValue("name","Super Sonic"); workspace.menu().submit();
        assertEquals("Super Sonic",StageplayDialogue.speakerName(workspace.controller().document(),cue));
        var before=workspace.controller().document();
        workspace.invoke(DELETE_ACTOR); choose(workspace.menu(),0);
        var offscreen=workspace.controller().document().dialogue().getFirst();
        assertTrue(workspace.controller().document().actors().isEmpty());
        assertEquals("",offscreen.speakerActor()); assertEquals("Super Sonic",offscreen.speakerName());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
        assertEquals("",workspace.controller().selectedActorId(),"Undo restores scene data; selection is separate view state");
        workspace.selectActor(cue.speakerActor());
        workspace.invoke(DELETE_ACTOR); choose(workspace.menu(),1);
        assertTrue(workspace.controller().document().dialogue().isEmpty());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
        workspace.invoke(DIALOGUE); choose(workspace.menu(),3); choose(workspace.menu(),6);
        assertTrue(workspace.controller().document().dialogue().isEmpty());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
    }

    @Test void compactVoiceFormEditsSelectedCastAndObjectSpeakersWithUndoAndReset() throws Exception {
        var workspace=workspace(); var cue=addLine(workspace); var original=workspace.controller().document();
        workspace.invoke(SPEAKER_VOICE);
        assertEquals(6,workspace.menu().values().size());
        assertEquals("HOLLOW",workspace.menu().values().get("tone"));
        workspace.menu().setValue("tone","BUZZ");workspace.menu().setValue("pitch","65");workspace.menu().submit();
        assertNull(workspace.menu().error());
        var voiced=workspace.controller().document();assertEquals(VoiceTone.BUZZ,voiced.voices().getFirst().tone());
        workspace.invoke(SCENE);workspace.menu().setValue("name","Edited");workspace.menu().submit();
        assertEquals(voiced.voices(),workspace.controller().document().voices());
        workspace.seek(100);workspace.invoke(SCENE_CHANGES);choose(workspace.menu(),0);workspace.menu().submit();
        assertNull(workspace.menu().error());assertEquals(voiced.voices(),workspace.controller().document().voices());
        workspace.invoke(UNDO);workspace.invoke(UNDO);workspace.invoke(UNDO);assertEquals(original,workspace.controller().document());
        workspace.invoke(REDO); workspace.selectActor(cue.speakerActor());workspace.invoke(SPEAKER_VOICE);
        workspace.menu().setValue("tone","DEFAULT");workspace.menu().submit();assertTrue(workspace.controller().document().voices().isEmpty());
        workspace.invoke(OBJECTS);choose(workspace.menu(),0);
        workspace.menu().setValue("name","Motobug");workspace.menu().setValue("game","s1");workspace.menu().setValue("object","64");
        workspace.menu().setValue("mode","POSE");workspace.menu().submit();assertNull(workspace.menu().error());
        String object=workspace.controller().document().objectActors().getFirst().id();
        workspace.invoke(DIALOGUE);choose(workspace.menu(),0);choose(workspace.menu(),1);workspace.menu().submit();
        assertNull(workspace.menu().error());assertEquals(object,workspace.controller().document().dialogue().getLast().speakerActor());
        workspace.selectActor(object);workspace.invoke(SPEAKER_VOICE);assertEquals("BUZZ",workspace.menu().values().get("tone"));
        workspace.menu().setValue("tone","OFF");workspace.menu().submit();
        workspace.saveTo(root.resolve("voices.stageplay"));
        assertEquals(workspace.controller().document(),StageplayStore.read(root.resolve("voices.stageplay")));
        workspace.invoke(OBJECTS);choose(workspace.menu(),6);choose(workspace.menu(),5);
        assertNull(workspace.menu().error());assertTrue(workspace.controller().document().objectActors().isEmpty());
        assertTrue(workspace.controller().document().dialogue().stream().noneMatch(c->c.speakerActor().equals(object)));
        assertTrue(workspace.controller().document().voices().stream().noneMatch(v->v.speakerActor().equals(object)));
        workspace.invoke(UNDO);assertFalse(workspace.controller().document().objectActors().isEmpty());
    }

    @Test void reusableProfilesCanBeCreatedSelectedEditedAndRemovedAtomically() {
        var workspace=workspace(); addLine(workspace);
        workspace.invoke(DIALOGUE_TYPERS); choose(workspace.menu(),0);
        workspace.menu().setValue("id","quick"); workspace.menu().setValue("speed","1"); workspace.menu().submit();
        workspace.invoke(DIALOGUE_SKINS); choose(workspace.menu(),0);
        workspace.menu().setValue("id","warm"); workspace.menu().setValue("name","FFFFCC00"); workspace.menu().submit();
        workspace.invoke(DIALOGUE); choose(workspace.menu(),3); choose(workspace.menu(),3); choose(workspace.menu(),1);
        workspace.invoke(DIALOGUE); choose(workspace.menu(),3); choose(workspace.menu(),2); choose(workspace.menu(),1);
        var cue=workspace.controller().document().dialogue().getFirst();
        assertEquals("quick",cue.typerId()); assertEquals("warm",cue.skinId());
        workspace.invoke(DIALOGUE_TYPERS); choose(workspace.menu(),1); choose(workspace.menu(),0);
        workspace.menu().setValue("punctuation","20"); workspace.menu().submit();
        assertEquals(20,workspace.controller().document().dialogueTypers().getFirst().punctuationPauseFrames());
        workspace.invoke(DIALOGUE_SKINS); choose(workspace.menu(),1); choose(workspace.menu(),0);
        workspace.menu().setValue("pointer","false"); workspace.menu().submit();
        assertFalse(workspace.controller().document().dialogueSkins().getFirst().pointer());
        var before=workspace.controller().document();
        workspace.invoke(DIALOGUE_TYPERS); choose(workspace.menu(),1); choose(workspace.menu(),1);
        assertTrue(workspace.controller().document().dialogueTypers().isEmpty());
        assertEquals("",workspace.controller().document().dialogue().getFirst().typerId());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
        workspace.invoke(DIALOGUE_SKINS); choose(workspace.menu(),1); choose(workspace.menu(),1);
        assertTrue(workspace.controller().document().dialogueSkins().isEmpty());
        assertEquals("",workspace.controller().document().dialogue().getFirst().skinId());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
    }

    @Test void everyNamedPresetIsSelectableEditableUndoableAndDefaultResetUsesTheApprovedVoice() {
        var workspace=workspace();String actor=workspace.controller().selectedActorId();
        var before=workspace.controller().document();
        var presets=com.openggf.stageplay.StageplaySpeech.Preset.values();
        for(int i=0;i<presets.length;i++) {
            workspace.invoke(SPEAKER_VOICE);workspace.menu().setValue("tone","PRESETS");
            assertEquals("Voice presets",workspace.menu().title());choose(workspace.menu(),i);
            assertEquals(6,workspace.menu().values().size());workspace.menu().submit();
            assertNull(workspace.menu().error());
            assertEquals(List.of(presets[i].voice(actor,"")),workspace.controller().document().voices());
            workspace.invoke(UNDO);assertEquals(before,workspace.controller().document());
        }
        workspace.invoke(SPEAKER_VOICE);workspace.menu().setValue("tone","PRESETS");choose(workspace.menu(),0);
        workspace.menu().setValue("pitch","73");workspace.menu().submit();assertNull(workspace.menu().error());
        assertEquals(VoiceTone.SQUARE,workspace.controller().document().voices().getFirst().tone());
        assertEquals(73,workspace.controller().document().voices().getFirst().pitch());
        workspace.invoke(SPEAKER_VOICE);workspace.menu().setValue("tone","DEFAULT");workspace.menu().submit();
        assertNull(workspace.menu().error());assertTrue(workspace.controller().document().voices().isEmpty());
        assertEquals(com.openggf.stageplay.StageplaySpeech.Preset.SOFT_COOL.voice(actor,""),
                com.openggf.stageplay.StageplaySpeech.voice(workspace.controller().document(),actor,""));
    }

    @Test void longCustomTimingSurvivesUnrelatedEditsAndEveryOffsetRemainsEditable() {
        var workspace=workspace(); String actor=workspace.controller().selectedActorId();
        var offsets=IntStream.range(0,4096).boxed().toList();
        var cue=new DialogueCue("long",0,10000,actor,"","a".repeat(4096),"","",0,0,1920,1080,"",offsets);
        workspace.controller().replace(workspace.controller().document().withSize(1920,1080).withDurationFrames(10000).withDialogue(List.of(cue)));
        workspace.invoke(DIALOGUE); choose(workspace.menu(),3); choose(workspace.menu(),0);
        workspace.menu().setValue("name","Narration"); workspace.menu().submit();
        assertNull(workspace.menu().error());
        assertEquals(offsets,workspace.controller().document().dialogue().getFirst().revealFrames());
        workspace.invoke(DIALOGUE); choose(workspace.menu(),3); choose(workspace.menu(),4);
        assertEquals(32,workspace.menu().values().size());
        assertTrue(workspace.menu().values().get("offsets31").endsWith("4095"));
        workspace.menu().submit(); assertNull(workspace.menu().error());
        assertEquals(offsets,workspace.controller().document().dialogue().getFirst().revealFrames());
        var before=workspace.controller().document();
        workspace.invoke(DIALOGUE); choose(workspace.menu(),3); choose(workspace.menu(),0);
        workspace.menu().setValue("text","shorter"); workspace.menu().submit();
        assertEquals(before,workspace.controller().document()); assertTrue(workspace.menu().error().contains("Use typer timing"));
        workspace.invoke(DIALOGUE); choose(workspace.menu(),3); choose(workspace.menu(),5);
        assertTrue(workspace.controller().document().dialogue().getFirst().revealFrames().isEmpty());
    }

    @Test void dialogueSurvivesSceneBoundariesCameraDraftsRecordingAndRoundTrip() throws Exception {
        var workspace=workspace(); var cue=addLine(workspace);
        workspace.seek(100); workspace.invoke(SCENE_CHANGES); choose(workspace.menu(),0); workspace.menu().submit();
        assertEquals(List.of(cue),workspace.controller().document().dialogue());
        workspace.invoke(CAMERA_MODE); workspace.moveCamera(4,0,2);
        assertEquals(1,workspace.previewFrame().dialogue().size());
        workspace.invoke(SET_CAMERA_KEY); workspace.invoke(RECORD_CAMERA); workspace.recordCameraStep(1,0,1); workspace.stop();
        workspace.invoke(RECORD); workspace.recordStep(true,false,false,false); workspace.stop();
        assertEquals(List.of(cue),workspace.controller().document().dialogue());
        workspace.invoke(SCENE); workspace.menu().setValue("name","Conversation"); workspace.menu().submit();
        workspace.saveTo(root.resolve("conversation.stageplay"));
        assertEquals(workspace.controller().document(),StageplayStore.read(root.resolve("conversation.stageplay")));
        var other=workspace(); other.open(root.resolve("conversation.stageplay"));
        assertEquals(workspace.controller().document(),other.controller().document());
    }

    @Test void imageAssetsInCurrentUndoAndRedoRevisionsPreventRelocation() throws Exception {
        var workspace=workspace();
        workspace.invoke(DIALOGUE_SKINS); choose(workspace.menu(),0);
        workspace.menu().setValue("asset","images/frame.png"); workspace.menu().submit();
        assertThrows(IOException.class,() -> workspace.saveTo(root.resolve("other/film.stageplay")));
        workspace.invoke(UNDO); // Asset only remains in redo history.
        assertThrows(IOException.class,() -> workspace.saveTo(root.resolve("other/film.stageplay")));
        workspace.invoke(REDO);
        workspace.invoke(DIALOGUE_SKINS); choose(workspace.menu(),1); choose(workspace.menu(),1);
        assertThrows(IOException.class,() -> workspace.saveTo(root.resolve("other/film.stageplay")));
        workspace.saveTo(root.resolve("film.stageplay"));
        workspace.open(root.resolve("film.stageplay")); // Start clean history, then add portrait-only dependency.
        addLine(workspace);
        workspace.invoke(DIALOGUE); choose(workspace.menu(),3); choose(workspace.menu(),0);
        workspace.menu().setValue("portrait","images/sonic.png"); workspace.menu().submit();
        assertNull(workspace.menu().error());
        assertThrows(IOException.class,() -> workspace.saveTo(root.resolve("other/film.stageplay")));
    }

    @Test void cameraZoomStepsOnPressAndRepeatWhilePanStillMovesEveryTick() {
        var workspace=workspace(); workspace.invoke(CAMERA_MODE); var input=new InputHandler();
        input.handleKeyEvent(GLFW_KEY_E,GLFW_PRESS); input.handleKeyEvent(GLFW_KEY_RIGHT,GLFW_PRESS);
        input.refreshLogicalSnapshot(); workspace.tick(input); input.update();
        assertEquals(2,workspace.directorCamera().zoom());
        double firstX=workspace.directorCamera().x();
        for (int i=0;i<17;i++) { workspace.tick(input); input.update(); }
        assertEquals(2,workspace.directorCamera().zoom()); assertEquals(firstX+17,workspace.directorCamera().x());
        workspace.tick(input); input.update(); assertEquals(3,workspace.directorCamera().zoom());
        input.handleKeyEvent(GLFW_KEY_E,GLFW_RELEASE); input.handleKeyEvent(GLFW_KEY_RIGHT,GLFW_RELEASE);
        workspace.tick(input); input.update();
        input.handleKeyEvent(GLFW_KEY_Q,GLFW_PRESS); workspace.tick(input); input.update();
        assertEquals(2,workspace.directorCamera().zoom());
        workspace.invoke(SET_CAMERA_KEY); assertEquals(2,workspace.controller().document().camera().getFirst().zoom());
        workspace.seek(10); workspace.invoke(CAMERA_KEY); choose(workspace.menu(),0);
        workspace.menu().setValue("zoom","1.25"); workspace.menu().submit();
        assertNull(workspace.menu().error());
        assertEquals(2,workspace.controller().document().camera().size());
        assertEquals(1.25,workspace.controller().document().camera().getLast().zoom());
        assertEquals(1.25,workspace.previewFrame().camera().zoom(), "Typed fractional zoom reaches the shared camera timeline");
    }

    private static void choose(EditorProjectMenu menu,int row) {
        var input=new InputHandler();
        for (int i=0;i<row;i++) press(menu,input,GLFW_KEY_DOWN);
        press(menu,input,GLFW_KEY_ENTER);
    }
    private static void press(EditorProjectMenu menu,InputHandler input,int key) {
        input.handleKeyEvent(key,GLFW_PRESS); input.refreshLogicalSnapshot(); menu.update(input); input.update();
        input.handleKeyEvent(key,GLFW_RELEASE); input.update();
    }
}
