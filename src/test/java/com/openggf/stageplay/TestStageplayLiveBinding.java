package com.openggf.stageplay;

import com.openggf.game.mode.CourseControl;
import com.openggf.stageplay.runtime.StageplayNativeBinding;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A worker's resolved values must use the held world's existing player lease. */
class TestStageplayLiveBinding {
    private final CourseControl course = mock(CourseControl.class, RETURNS_DEEP_STUBS);
    private final CourseControl.DirectedPlayerState entry = mock(CourseControl.DirectedPlayerState.class);
    private final TestStageplayPlayback.FakeHost host = new TestStageplayPlayback.FakeHost() {
        @Override public StageplayTimeline.Frame resolveFrame(StageplayTimeline.Frame authored) {
            var actual = new StageplayTimeline.ActorPose("hero", "s3k", "sonic",
                    500 + authored.frame(), 350, 0, authored.frame(), 1, false, true, 1);
            return new StageplayTimeline.Frame(authored.frame(), List.of(actual), authored.camera(),
                    authored.captions(), authored.set(), authored.transition(), authored.baseCamera(),
                    authored.dialogue(), List.of());
        }
    };

    private StageplayPlayback player(StageplayNativeBinding.FinishMode mode) {
        var scene = TestStageplayDocument.scene().withDurationFrames(3)
                .withActors(List.of(TestStageplayDocument.actor(List.of(
                        TestStageplayDocument.key(0, 100, 0, -1, Interpolation.HOLD)))))
                .withSet(new SetSource("s3k", 0, 0, "", ""));
        when(course.presentationReady()).thenReturn(true);
        when(course.objectServices().gameModule().getGameCode()).thenReturn("s3k");
        when(course.playerState()).thenReturn(new CourseControl.PlayerState("sonic", 0, 0, 0, 0, 0, 0,
                false, true, false, false, false, 0, 0, 0, 0, 0));
        when(entry.gameId()).thenReturn("s3k"); when(entry.character()).thenReturn("sonic");
        when(course.captureDirectedPlayer()).thenReturn(entry);
        when(course.isDirectedPlayerCurrent(entry)).thenReturn(true);
        return new StageplayPlayback(scene, "hero", mode, () -> host);
    }

    @ParameterizedTest @EnumSource(StageplayNativeBinding.FinishMode.class)
    void completionUsesResolvedNativePoseAndReleasesExactlyOnce(StageplayNativeBinding.FinishMode mode) {
        var player = player(mode);
        player.play();
        for (int i = 0; i < 4; i++) player.beforeFrame(course, null);
        player.beforeFrame(course, null);
        assertEquals(StageplayControl.Status.COMPLETED, player.status());
        verify(course).directPlayer(entry, 502, 350, 0, 0, false, false, true, 100, 200);
        verify(course).releaseDirectedPlayer(entry, mode == StageplayNativeBinding.FinishMode.KEEP_FINAL_POSITION);
        verify(course).captureDirectedPlayer();
        assertEquals(1, host.stops);
    }

    @Test void skipResolvesFinalFrameAndCancellationRestoresEntry() {
        var player = player(StageplayNativeBinding.FinishMode.KEEP_FINAL_POSITION);
        player.play(); player.beforeFrame(course, null);
        player.skip(); player.beforeFrame(course, null);
        verify(course).directPlayer(entry, 502, 350, 0, 0, false, false, true, 100, 200);
        verify(course).releaseDirectedPlayer(entry, true);
        assertEquals(StageplayControl.Status.SKIPPED, player.status());

        clearInvocations(course);
        player.play(); player.beforeFrame(course, null);
        player.cancel(); player.beforeFrame(course, null);
        verify(course).releaseDirectedPlayer(entry, false);
        verify(course, never()).releaseDirectedPlayer(entry, true);
        assertEquals(StageplayControl.Status.CANCELLED, player.status());
    }

    @Test void changedVisitAbandonsTheOldLeaseWithoutApplyingItToTheNewWorld() {
        var player = player(StageplayNativeBinding.FinishMode.KEEP_FINAL_POSITION);
        player.play(); player.beforeFrame(course, null);
        clearInvocations(course);
        when(course.objectServices().levelManager().getCompletedProductionLoadGeneration()).thenReturn(1L);
        player.beforeFrame(course, null);
        verify(course, never()).releaseDirectedPlayer(any(), anyBoolean());
        assertEquals(StageplayControl.Status.CANCELLED, player.status());
        assertEquals(1, host.abandons);
    }
}
