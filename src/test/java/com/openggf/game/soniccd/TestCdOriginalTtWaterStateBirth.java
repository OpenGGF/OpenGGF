package com.openggf.game.soniccd;
import com.openggf.game.save.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Source-first obligations; actual public boot or inert values only. All Java UNRUN. */
class TestCdOriginalTtWaterStateBirth extends TestCdOriginalR11LoadingWorld {
 @Test void absentWaterImageNeverInventsAngleOrPendingKnowledge(){
  var unknown=SonicCdGameModule.OriginalLoadingWater.unknown();
  assertTrue(unknown.swayAngleByte().isEmpty());assertTrue(unknown.pendingByte().isEmpty());
  var height=unknown.store(0,0x200);assertTrue(height.swayAngleByte().isEmpty());assertTrue(height.pendingByte().isEmpty());
 }
 @Test void actualOriginalClearCarriesAngleAndPendingIntoTheLoadingImage()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var saves=new SaveManager(root);
  var profile=CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(OptionalInt.of(0x200),OptionalInt.of(4),OptionalInt.of(0x90),OptionalInt.of(0),OptionalInt.of(0x35),OptionalInt.of(2),OptionalInt.of(3)));
  saves.writeSlot("scd",1,profile.encode());var attempt=SonicCdGameModule.prepareSelectedProfile(source,saves,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot()){var loop=boot.bootSonicCdSelectedProfile(source,attempt);
   for(int row=0;row<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;row++)step(loop,0);
   var before=m.originalLoadingWaterImage().orElseThrow();assertEquals(0,before.swayAngleByte().orElseThrow());assertEquals(0,before.pendingByte().orElseThrow());
   step(loop,0);var after=m.originalLoadingWaterImage().orElseThrow();assertEquals(2,after.swayAngleByte().orElseThrow());assertEquals(0,after.pendingByte().orElseThrow());assertTrue(after.selectedPalette().isPresent());
  }
 }
}
