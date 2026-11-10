package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdStagePlayerSource;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit constructed scheduler preflight controls; mocks confer no actual Frame/World authority. */
class TestCdOriginalPastScheduler {
 @Test void simultaneousOwnersRejectBeforeAnyInputClockBodyOrWorldCallback(){
  var source=mock(CdStagePlayerSource.class);var frame=mock(CdPlayerFrameRuntime.class);when(frame.stageSource()).thenReturn(source);
  var ports=mock(CdOrdinaryLoop.Ports.class);var water=mock(CdPlayerFrameRuntime.OriginalProgrammeOwner.class);var standard=mock(CdPlayerFrameRuntime.OriginalProgrammeOwner.class);
  when(ports.originalProgrammeOwner()).thenReturn(Optional.of(water));when(ports.originalStandardProgrammeOwner()).thenReturn(Optional.of(standard));
  var loop=new CdOrdinaryLoop(frame,source,ports);clearInvocations(frame,ports);assertThrows(IllegalStateException.class,()->loop.run(8));
  verify(frame).closed();verifyNoMoreInteractions(frame);verify(ports).originalProgrammeOwner();verify(ports).originalStandardProgrammeOwner();verifyNoMoreInteractions(ports);
  // The same invalid idle request is rejected as a selection error again, not as poisoned admission.
  assertTrue(assertThrows(IllegalStateException.class,()->loop.run(8)).getMessage().contains("Mutually exclusive"));
 }
 @Test void nullOwnerSelectionRejectsBeforePreparingInputOrTouchingPorts(){
  var source=mock(CdStagePlayerSource.class);var frame=mock(CdPlayerFrameRuntime.class);when(frame.stageSource()).thenReturn(source);var ports=mock(CdOrdinaryLoop.Ports.class);when(ports.originalProgrammeOwner()).thenReturn(null);
  var loop=new CdOrdinaryLoop(frame,source,ports);clearInvocations(frame,ports);assertThrows(NullPointerException.class,()->loop.run(8));verify(frame).closed();verifyNoMoreInteractions(frame);verify(ports).originalProgrammeOwner();verifyNoMoreInteractions(ports);
 }
}
