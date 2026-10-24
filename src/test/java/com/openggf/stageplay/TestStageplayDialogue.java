package com.openggf.stageplay;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.TestStageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayDialogue {
    private static DialogueCue cue(String text) {
        return new DialogueCue("line", 10, 100, "hero", "", text, "", "", 8, 140, 304, 76, "", List.of());
    }

    private static StageplayDocument cast() {
        return scene().withActors(List.of(actor(List.of(key(0, 100, 0, -1, Interpolation.HOLD)))));
    }

    @Test void defaultsResolveSpeakerImmediatelyAndRevealUnicodeGraphemesOnDeterministicTiming() {
        var line = new DialogueCue("line", 10, 100, "hero", "", "A👩‍🚀e\u0301!\nB", "", "slow",
                8, 140, 304, 76, "", List.of());
        var document = cast().withDialogueTypers(List.of(new DialogueTyper("slow", 2, 5, 3))).withDialogue(List.of(line));
        assertEquals(6, StageplayDialogue.graphemeCount(line.text()));
        assertEquals(List.of(0, 2, 4, 6, 13, 18), StageplayDialogue.revealFrames(document, line));
        assertEquals("Hero", StageplayDialogue.speakerName(document, line));
        assertEquals(6, StageplayDialogue.skin(document, line).padding());
        assertTrue(StageplayDialogue.skin(document, line).pointer());
        assertTrue(StageplayTimeline.sample(document, 9).dialogue().isEmpty());
        var expected = List.of("A", "A👩‍🚀", "A👩‍🚀e\u0301", "A👩‍🚀e\u0301!", "A👩‍🚀e\u0301!\n", line.text());
        var times = List.of(0, 2, 4, 6, 13, 18);
        for (int index : List.of(5, 1, 4, 0, 3, 2, 5, 0)) {
            var sampled = StageplayTimeline.sample(document, line.startFrame() + times.get(index)).dialogue().getFirst();
            assertEquals("Hero", sampled.speakerName());
            assertEquals(expected.get(index), sampled.visibleText());
        }
        assertEquals("A👩‍🚀e\u0301!", StageplayDialogue.visibleText(document, line, 12));
        assertTrue(StageplayTimeline.sample(document, 100).dialogue().isEmpty());
        assertEquals("", StageplayDialogue.visibleText(document, line, -1));
    }

    @Test void castObjectsAreNamedSpeakersWithoutPlayerRoles() {
        var object = new ObjectActor("moto", "Motobug", "s1", 0, 0, 0x40, 0, -1,
                List.of(new ObjectKey(0, 100, 200, 0, -1, StageplayDocument.Facing.LEFT, true, Interpolation.HOLD, ObjectMode.POSE)), List.of());
        var line = new DialogueCue("object-line", 0, 100, "moto", "", "Day off.", "", "", 0, 0, 200, 60, "", List.of());
        var document = scene().withObjectActors(List.of(object)).withDialogue(List.of(line));
        assertEquals("Motobug", StageplayDialogue.speakerName(document, line));
        assertEquals("Motobug", StageplayTimeline.sample(document, 0).dialogue().getFirst().speakerName());
        assertThrows(IllegalArgumentException.class, () -> document.withObjectActors(List.of()));
    }

    @Test void explicitSchedulesCanRevealGroupsAndInstantTypersIgnorePauses() {
        var offsets = new ArrayList<>(List.of(2, 2, 4));
        var line = new DialogueCue("custom", 0, 20, "", "Radio", "Ae\u0301🙂", "", "", 0, 0, 200, 60, "", offsets);
        offsets.clear();
        var document = scene().withDialogue(List.of(line));
        assertEquals(List.of(2, 2, 4), StageplayDialogue.revealFrames(document, line));
        assertEquals("", StageplayDialogue.visibleText(document, line, 1));
        assertEquals("Ae\u0301", StageplayDialogue.visibleText(document, line, 2));
        assertEquals(line.text(), StageplayDialogue.visibleText(document, line, 4));
        assertThrows(UnsupportedOperationException.class, () -> line.revealFrames().clear());
        var instant = new DialogueCue("instant", 0, 1, "", "Radio", "Wait... now!", "", "instant",
                0, 0, 200, 60, "", List.of());
        var immediate = scene().withDialogueTypers(List.of(new DialogueTyper("instant", 0, 120, 120))).withDialogue(List.of(instant));
        assertEquals(instant.text(), StageplayDialogue.visibleText(immediate, instant, 0));
        assertTrue(StageplayDialogue.revealFrames(immediate, instant).stream().allMatch(frame -> frame == 0));
    }

    @Test void fullTextWrapStaysFixedDuringRevealAndKeepsPortraitAndNameSpace() {
        var skin = new DialogueSkin("small", -1, -1, 0xff101522, -1, 4, "", 0, true);
        var line = new DialogueCue("line", 0, 100, "", "N", "one two three", "small", "", 0, 0, 80, 48, "", List.of());
        var document = scene().withDialogueSkins(List.of(skin)).withDialogue(List.of(line));
        var layout = StageplayDialogue.layout(document, line);
        assertEquals(List.of("one two", "three"), layout.rows().stream().map(StageplayDialogue.Row::text).toList());
        assertEquals(8, layout.rows().getLast().startGrapheme());
        assertEquals(4, layout.nameX()); assertEquals(4, layout.nameY());
        assertEquals(18, layout.textY());
        assertEquals(List.of("one ", ""), StageplayDialogue.visibleRows(layout, "one "));
        assertEquals(List.of("one two", "t"), StageplayDialogue.visibleRows(layout, "one two t"));
        assertEquals(layout, StageplayDialogue.layout(document, line));
        assertThrows(UnsupportedOperationException.class, () -> layout.rows().clear());
        var portrait = new DialogueCue("portrait", 0, 100, "", "N", "Hello", "", "", 20, 10, 180, 60, "portraits/n.png", List.of());
        var portraitLayout = StageplayDialogue.layout(scene().withDialogue(List.of(portrait)), portrait);
        assertEquals(66, portraitLayout.nameX());
        assertEquals(66, portraitLayout.textX());
        assertEquals(128, portraitLayout.textWidth());
    }

    @Test void explicitNewlinesAndHardWrappingNeverSplitCombinedCharacters() {
        var skin = new DialogueSkin("small", -1, -1, -1, -1, 4, "", 0, false);
        var line = new DialogueCue("line", 0, 100, "", "N", "abcde\u0301fg\n\nHi", "small", "", 0, 0, 80, 72, "", List.of());
        var document = scene().withDialogueSkins(List.of(skin)).withDialogue(List.of(line));
        assertEquals(List.of("abcde\u0301f", "g", "", "Hi"),
                StageplayDialogue.layout(document, line).rows().stream().map(StageplayDialogue.Row::text).toList());
    }

    @Test void referencesGeometryTimingAndOverflowFailWithUsefulErrors() {
        var normal = cue("Hello");
        assertThrows(IllegalArgumentException.class, () -> scene().withDialogue(List.of(normal)));
        var missingNamedActor = new DialogueCue("missing", 0, 100, "absent", "Radio", "Hi", "", "", 0, 0, 200, 60, "", List.of());
        assertThrows(IllegalArgumentException.class, () -> scene().withDialogue(List.of(missingNamedActor)),
                "speaker must exist even when a name override is supplied");
        var missingSkin = new DialogueCue("line", 0, 100, "", "Radio", "Hi", "missing", "", 0, 0, 200, 60, "", List.of());
        assertThrows(IllegalArgumentException.class, () -> scene().withDialogue(List.of(missingSkin)));
        var missingTyper = new DialogueCue("line", 0, 100, "", "Radio", "Hi", "", "missing", 0, 0, 200, 60, "", List.of());
        assertThrows(IllegalArgumentException.class, () -> scene().withDialogue(List.of(missingTyper)));
        var outside = new DialogueCue("line", 0, 100, "", "N", "Hi", "", "", 250, 0, 80, 60, "", List.of());
        assertThrows(IllegalArgumentException.class, () -> scene().withDialogue(List.of(outside)));
        var overflow = new DialogueCue("overflow", 0, 100, "", "N", "one two three four five six", "", "", 0, 0, 80, 36, "", List.of());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> scene().withDialogue(List.of(overflow))).getMessage().contains("overflow"));
        var tooShort = new DialogueCue("unfinished", 0, 2, "", "N", "Hi", "", "", 0, 0, 80, 36, "", List.of());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> scene().withDialogue(List.of(tooShort))).getMessage().contains("unfinished"));
        var tooSmallForPortrait = new DialogueCue("portrait", 0, 100, "", "N", "Hi", "", "", 0, 0, 80, 36, "p.png", List.of());
        assertThrows(IllegalArgumentException.class, () -> scene().withDialogue(List.of(tooSmallForPortrait)));
        assertThrows(IllegalArgumentException.class, () -> new DialogueCue("bad", 0, 10, "", "", "Hi", "", "", 0, 0, 100, 60, "", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DialogueCue("bad", 0, 10, "", "N", "Hi", "", "", 0, 0, 100, 60, "../p.png", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DialogueCue("bad", 0, 10, "", "N", "Hi", "", "", 0, 0, 100, 60, "", List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> new DialogueCue("bad", 0, 10, "", "N", "Hi", "", "", 0, 0, 100, 60, "", List.of(2, 1)));
        assertThrows(IllegalArgumentException.class, () -> new DialogueCue("bad", 0, 10, "", "N", "Hi", "", "", 0, 0, 100, 60, "", List.of(0, 10)));
        var longName = new DialogueCue("name", 0, 100, "", "Too long for this panel", "Hi", "", "", 0, 0, 80, 60, "", List.of());
        assertThrows(IllegalArgumentException.class, () -> scene().withDialogue(List.of(longName)));
        var exactEnd = new DialogueCue("last", 0, 3, "", "N", "Hi", "", "", 0, 0, 80, 36, "", List.of());
        assertEquals("Hi", StageplayTimeline.sample(scene().withDialogue(List.of(exactEnd)), 2).dialogue().getFirst().visibleText());
    }

    @Test void profilesAndCueCountsAreBoundedAndLegacyConstructorsRemainUsable() {
        for (int cadence : List.of(-1, 31))
            assertThrows(IllegalArgumentException.class, () -> new DialogueTyper("bad", cadence, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new DialogueTyper("bad", 1, 121, 0));
        assertThrows(IllegalArgumentException.class, () -> new DialogueTyper("bad", 1, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new DialogueSkin("bad", -1, -1, -1, -1, 3, "", 0, true));
        assertThrows(IllegalArgumentException.class, () -> new DialogueSkin("bad", -1, -1, -1, -1, 6, "image.jpg", 0, true));
        assertThrows(IllegalArgumentException.class, () -> new DialogueSkin("bad", -1, -1, -1, -1, 6, "C:/image.png", 0, true));
        var typers = new ArrayList<DialogueTyper>();
        var skins = new ArrayList<DialogueSkin>();
        for (int i = 0; i < 65; i++) {
            typers.add(new DialogueTyper("style-" + i, 1, 0, 0));
            skins.add(new DialogueSkin("style-" + i, -1, -1, -1, -1, 6, "", 0, false));
        }
        assertThrows(IllegalArgumentException.class, () -> scene().withDialogueTypers(typers));
        assertThrows(IllegalArgumentException.class, () -> scene().withDialogueSkins(skins));
        var cues = new ArrayList<DialogueCue>();
        for (int i = 0; i < 1025; i++) cues.add(new DialogueCue("line-" + i, 0, 10, "", "N",
                "Hi", "", "", 0, 0, 80, 36, "", List.of()));
        assertThrows(IllegalArgumentException.class, () -> scene().withDialogue(cues));
        var source = scene().withCameraEffects(List.of(new CameraEffect("shake", 0, 20, 1, 2, 3, 4)));
        for (int version : List.of(VERSION)) {
            var legacy = new StageplayDocument(version, source.name(), source.durationFrames(), source.width(), source.height(),
                    source.set(), source.actors(), source.camera(), source.captions(), source.audio());
            assertEquals(VERSION, legacy.version());
            assertTrue(legacy.dialogue().isEmpty());
            var directed = new StageplayDocument(version, source.name(), source.durationFrames(), source.width(), source.height(),
                    source.set(), source.actors(), source.camera(), source.captions(), source.audio(), source.cameraEffects(), source.sceneChanges());
            assertEquals(source, directed);
        }
    }

    @Test void overlappingPanelsHaveImmediateNamesAndFrameCopiesProtectDialogue() {
        var first = new DialogueCue("first", 10, 20, "hero", "", "Hi", "", "", 0, 0, 200, 60, "", List.of(2, 4));
        var second = new DialogueCue("second", 10, 30, "", "Radio", "Go", "", "", 0, 100, 200, 60, "", List.of());
        var document = cast().withDialogue(List.of(first, second));
        var frame = StageplayTimeline.sample(document, 10);
        assertEquals(List.of("Hero", "Radio"), frame.dialogue().stream().map(StageplayTimeline.DialoguePose::speakerName).toList());
        assertEquals(List.of("", "G"), frame.dialogue().stream().map(StageplayTimeline.DialoguePose::visibleText).toList());
        assertEquals(1, StageplayTimeline.sample(document, 20).dialogue().size());
        assertThrows(UnsupportedOperationException.class, () -> frame.dialogue().clear());
        var poses = new ArrayList<>(frame.dialogue());
        var copied = new StageplayTimeline.Frame(frame.frame(), frame.actors(), frame.camera(), frame.captions(),
                frame.set(), frame.transition(), frame.baseCamera(), poses);
        poses.clear();
        assertEquals(frame, copied);
        assertTrue(new StageplayTimeline.Frame(10, frame.actors(), frame.camera(), frame.captions()).dialogue().isEmpty());
        assertTrue(new StageplayTimeline.Frame(10, frame.actors(), frame.camera(), frame.captions(),
                frame.set(), frame.transition(), frame.baseCamera()).dialogue().isEmpty());
    }

    @Test void definitionNamespacesAreIndependentAndOldMutatorsPreserveAllDialogueTracks() {
        var typers = new ArrayList<>(List.of(new DialogueTyper("style", 1, 0, 0)));
        var skins = new ArrayList<>(List.of(new DialogueSkin("style", -1, -1, -1, -1, 6, "skin.png", 3, false)));
        var line = new DialogueCue("line", 0, 100, "hero", "Captain", "Hello", "style", "style", 0, 0, 200, 60, "", List.of());
        var lines = new ArrayList<>(List.of(line));
        var document = cast().withDialogueTypers(typers).withDialogueSkins(skins).withDialogue(lines);
        typers.clear(); skins.clear(); lines.clear();
        var revised = document.withName("Revised").withDurationFrames(601).withSize(640, 448).withSet(document.set())
                .withActors(document.actors()).withCamera(document.camera()).withCaptions(List.of())
                .withAudio(List.of()).withCameraEffects(List.of()).withSceneChanges(List.of());
        assertEquals(document.dialogueTypers(), revised.dialogueTypers());
        assertEquals(document.dialogueSkins(), revised.dialogueSkins());
        assertEquals(document.dialogue(), revised.dialogue());
        assertEquals("Captain", StageplayDialogue.speakerName(document, line));
        assertThrows(IllegalArgumentException.class, () -> document.withDialogueSkins(List.of()));
        assertThrows(IllegalArgumentException.class, () -> document.withDialogueTypers(List.of()));
        assertThrows(IllegalArgumentException.class, () -> document.withActors(List.of()));
        assertThrows(IllegalArgumentException.class, () -> document.withDialogue(List.of(line, line)));
        assertThrows(IllegalArgumentException.class, () -> document.withDialogueSkins(List.of(document.dialogueSkins().getFirst(), document.dialogueSkins().getFirst())));
        assertThrows(IllegalArgumentException.class, () -> document.withCaptions(List.of(new Caption("line", 0, 10, "Other", 0, 0, -1))));
        assertThrows(UnsupportedOperationException.class, () -> document.dialogue().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.dialogueSkins().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.dialogueTypers().clear());
    }
}
