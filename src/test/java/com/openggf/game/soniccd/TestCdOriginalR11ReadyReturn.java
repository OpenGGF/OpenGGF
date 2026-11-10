package com.openggf.game.soniccd;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Separate loading-return obligation; first ordinary remains a separate positive requirement.
 * Real public ingress and caller rows only. SOURCE / UNRUN, no pixel/native parity claim. */
class TestCdOriginalR11ReadyReturn extends TestCdOriginalR11LoadingWorld {
 @Test void actualMainBeginLevelReturnsAfterBothPlcWaitsAndTwentyTwoFadeRows()throws Exception {
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);var frame=module.originalR11EntryFrame();
   assertTrue(frame.vIntClock().absoluteValue().isEmpty());step(loop,8);assertEquals(0,frame.vIntClock().absoluteValue().orElseThrow());
   assertEquals(0,frame.state().header().slotId());assertEquals(0,frame.state().motion().routine());
   // Exact cleared actual pool is observed, never injected or replaced with fixture bytes.
   assertTrue(module.originalR11PlacementImage().pool().slots().stream().allMatch(slot->slot.id()==0));
   for(int row=0;row<256&&module.originalR11LoadingPrefix().main().levelStartedByte()==0;row++)step(loop,0);
   assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte());assertEquals(1,frame.state().header().slotId());assertEquals(0,frame.state().motion().routine());
   assertEquals(24,module.originalR11FirstLoad().completedWaits()); // first C + second C +22 actual fades
   assertEquals(23,frame.vIntClock().absoluteValue().orElseThrow()); // first C count cleared by actual Main.ClearFlowers
   assertTrue(module.originalR11FirstLoad().selection().inputs().displayEnabled());assertFalse(frame.closed());
   assertSame(source,SessionManager.getCurrentWorldSession().getDataSource());
  }
 }
}
