package com.openggf.stageplay;

import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameDisposition;
import com.openggf.stageplay.runtime.StageplayNativeBinding;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStageplayPlayback {
    @Test void nativeBindingRejectsOtherSetsBeforeCreatingAnyRuntimeHost() {
        var actor = TestStageplayDocument.actor(List.of(
                TestStageplayDocument.key(0, 100, 0, -1, StageplayDocument.Interpolation.HOLD),
                TestStageplayDocument.key(100, 200, 0, -1, StageplayDocument.Interpolation.HOLD)));
        var document = TestStageplayDocument.scene().withActors(List.of(actor))
                .withCamera(List.of(new StageplayDocument.CameraKey(0, 100, 200, 1, StageplayDocument.Interpolation.HOLD),
                        new StageplayDocument.CameraKey(100, 500, 300, 2, StageplayDocument.Interpolation.HOLD)))
                .withSceneChanges(List.of(new StageplayDocument.SceneChange("other", 100,
                        new StageplayDocument.SetSource("s3k", 1, 0, "", ""), StageplayDocument.Transition.CUT, 0)));
        assertThrows(IllegalArgumentException.class, () -> new StageplayPlayback(document, "hero",
                StageplayNativeBinding.FinishMode.KEEP_FINAL_POSITION, () -> { fail("must reject before opening runtime resources"); return host; }));
        assertDoesNotThrow(() -> new StageplayPlayback(document.withSceneChanges(List.of())
                .withCameraEffects(List.of(new StageplayDocument.CameraEffect("quake", 0, 60, 8, 4, 6, 3))),
                "hero", StageplayNativeBinding.FinishMode.KEEP_FINAL_POSITION, () -> host));
    }

    @Test void nativeSetMismatchFailsBeforeCapturingThePlayerOrOpeningResources() {
        var document = TestStageplayDocument.scene().withActors(List.of(TestStageplayDocument.actor(List.of(
                TestStageplayDocument.key(0, 100, 0, -1, StageplayDocument.Interpolation.HOLD)))));
        var player = new StageplayPlayback(document, "hero", StageplayNativeBinding.FinishMode.RESTORE_ENTRY,
                () -> { fail("wrong native set must fail before opening runtime resources"); return host; });
        when(course.presentationReady()).thenReturn(true);
        when(course.objectServices().levelManager().getCurrentZone()).thenReturn(1);
        player.play();
        assertThrows(IllegalArgumentException.class, () -> player.beforeFrame(course, null));
        verify(course, never()).captureDirectedPlayer();
        assertEquals(StageplayControl.Status.CANCELLED, player.status());
    }

    @Test void nativeHandoverUsesUnshakenCoordinatesWhileZoomRemainsPresentationOnly() {
        var document = TestStageplayDocument.scene().withDurationFrames(3)
                .withActors(List.of(TestStageplayDocument.actor(List.of(
                        TestStageplayDocument.key(0, 100, 0, -1, StageplayDocument.Interpolation.HOLD)))))
                .withCamera(List.of(new StageplayDocument.CameraKey(0, 300, 200, 2, StageplayDocument.Interpolation.HOLD)))
                .withCameraEffects(List.of(new StageplayDocument.CameraEffect("quake", 0, 3, 8, 4, 7, 42)));
        var entry = mock(CourseControl.DirectedPlayerState.class);
        when(entry.gameId()).thenReturn("s3k"); when(entry.character()).thenReturn("sonic");
        when(course.captureDirectedPlayer()).thenReturn(entry);
        when(course.isDirectedPlayerCurrent(entry)).thenReturn(true);
        when(course.playerState()).thenReturn(new CourseControl.PlayerState("sonic", 0, 0, 0, 0, 0, 0,
                false, true, false, false, false, 0, 0, 0, 0, 0));
        when(course.objectServices().gameModule().getGameCode()).thenReturn("s3k");
        when(course.presentationReady()).thenReturn(true);
        var player = new StageplayPlayback(document, "hero", StageplayNativeBinding.FinishMode.KEEP_FINAL_POSITION, () -> host);
        player.play(); player.beforeFrame(course, null); player.skip(); player.beforeFrame(course, null);
        verify(course, times(2)).directPlayer(entry, 100, 200, 0, 0, false, false, true, 300, 200);
        verify(course).releaseDirectedPlayer(entry, true);
    }

    private final CourseControl course=mock(CourseControl.class,RETURNS_DEEP_STUBS);
    private final FakeHost host=new FakeHost();
    private StageplayPlayback player() {
        when(course.presentationReady()).thenReturn(true);
        when(course.objectServices().levelManager().getCompletedProductionLoadGeneration()).thenReturn(1L);
        return new StageplayPlayback(TestStageplayDocument.scene().withDurationFrames(3),"",
                StageplayNativeBinding.FinishMode.RESTORE_ENTRY,()->host);
    }
    @Test void holdsExactlyTheSceneFramesAndReleasesNativeInputAfterCompletion() {
        var player=player();
        assertEquals(GameplayFrameDisposition.ADVANCE_NATIVE_INPUT,player.beforeFrame(course,null));
        player.play();
        for(int frame=0;frame<3;frame++) {
            assertEquals(GameplayFrameDisposition.HOLD,player.beforeFrame(course,null));
            assertEquals(frame,player.frame()); assertTrue(player.drawScene());
        }
        assertEquals(List.of(0,1,2),host.heard);
        assertEquals(GameplayFrameDisposition.HOLD,player.beforeFrame(course,null));
        assertEquals(StageplayControl.Status.COMPLETED,player.status()); assertEquals(1,host.stops);
        assertEquals(GameplayFrameDisposition.ADVANCE_NATIVE_INPUT,player.beforeFrame(course,null));
        assertTrue(player.requiresExclusiveControl()); player.close(); assertEquals(1,host.abandons);
    }
    @Test void waitsForEntryAndRestoresVisualTransportWithoutRepeatingOldCues() {
        var player=player(); when(course.presentationReady()).thenReturn(false); player.play();
        assertEquals(GameplayFrameDisposition.ADVANCE_NATIVE_INPUT,player.beforeFrame(course,null));
        assertTrue(host.heard.isEmpty()); when(course.presentationReady()).thenReturn(true);
        player.beforeFrame(course,null); var saved=player.capture(); player.beforeFrame(course,null);
        player.restore(saved); assertEquals(List.of(0,1),host.heard); assertEquals(0,host.seek);
        player.beforeFrame(course,null); assertEquals(List.of(0,1,1),host.heard);
        player.cancel(); player.beforeFrame(course,null); assertEquals(StageplayControl.Status.CANCELLED,player.status());
    }
    @Test void sceneFailureCleansUpAtTheNextValidCourseBoundary() {
        var player=player(); player.play(); player.beforeFrame(course,null); host.drawFailure=true;
        assertTrue(player.drawScene()); assertEquals(0,host.stops);
        assertThrows(IllegalStateException.class,()->player.beforeFrame(course,null));
        assertEquals(StageplayControl.Status.CANCELLED,player.status()); assertEquals(1,host.stops);
    }
    @Test void skipDoesNotEmitSkippedCuesAndReloadNeverRestoresIntoTheNewWorld() {
        var player=player(); player.play(); player.beforeFrame(course,null); player.skip(); player.beforeFrame(course,null);
        assertEquals(StageplayControl.Status.SKIPPED,player.status()); assertEquals(List.of(0),host.heard);
        player.play(); player.beforeFrame(course,null);
        when(course.objectServices().levelManager().getCompletedProductionLoadGeneration()).thenReturn(2L);
        assertEquals(GameplayFrameDisposition.ADVANCE_NATIVE_INPUT,player.beforeFrame(course,null));
        assertEquals(StageplayControl.Status.CANCELLED,player.status()); assertEquals(1,host.abandons);
    }
    @Test void quarantineCloseRestoresOnlyTheCurrentVisitsSoundtrack() {
        var player=player();player.play();player.beforeFrame(course,null);player.close();
        assertEquals(1,host.stops);assertEquals(1,host.abandons);
        var replacedHost=new FakeHost(); replacedHost.current=false;
        var replaced=new StageplayPlayback(TestStageplayDocument.scene(),"",StageplayNativeBinding.FinishMode.RESTORE_ENTRY,()->replacedHost);
        replaced.play();replaced.beforeFrame(course,null);replaced.close();
        assertEquals(0,replacedHost.stops,"old audio state must never be restored into a new visit");assertEquals(1,replacedHost.abandons);
    }
    @Test void rewindRetainsBorrowedSoundtrackAcrossCompletionAndMissingStateClearsIt() {
        var player=player();player.play();player.beforeFrame(course,null);
        var entry=mock(com.openggf.audio.rewind.AudioLogicalSnapshot.class);host.entry=entry;
        var saved=player.capture();player.beforeFrame(course,null);player.beforeFrame(course,null);player.beforeFrame(course,null);
        assertNull(host.entry);player.restore(saved);assertSame(entry,host.entry);
        player.resetForMissingSnapshot();assertNull(host.entry);assertEquals(-1,host.seek);
    }
    static class FakeHost implements StageplayPlayback.Host {
        final List<Integer> heard=new ArrayList<>(); int seek,stops,abandons; boolean drawFailure,current=true;
        com.openggf.audio.rewind.AudioLogicalSnapshot entry;
        public void prepare() { } public void seek(int frame) {seek=frame;}
        public void playFrame(int frame) {heard.add(frame);}
        public void draw(int frame) {if(drawFailure)throw new IllegalStateException("renderer lost");}
        public void stop() {stops++;entry=null;} public void abandon() {abandons++;}
        public boolean current() {return current;}
        public com.openggf.audio.rewind.AudioLogicalSnapshot captureEntryAudio() {return entry;}
        public void restoreEntryAudio(com.openggf.audio.rewind.AudioLogicalSnapshot value) {entry=value;}
        public int mappingFrame(StageplayTimeline.ActorPose pose) {return 0;}
    }
}
