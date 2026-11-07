package com.openggf.game.soniccd.objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
/** Tests first: pure same-owner DATA fork isolation; no World/restore invocation authority. Java UNRUN. */
class TestCdObjectPoolStagingCopy {
 @Test void all96CompleteImagesAndIdentitiesArePreservedWithoutClearingDirtyFreeSlots(){
  var original=new CdObjectPool();for(int w=32;w<128;w++)original.write(original.state(w).moveLong(MAPPINGS,0xAB0000+w).moveLong(X_POSITION,0xFFFF0000+w).moveByte(ID,w==87?0:w).moveByte(ROUTINE,0xA5));
  var before=original.capture();var work=CdObjectPool.stagingCopy(before);assertEquals(96,work.capture().slots().size());
  for(int n=0;n<96;n++){assertSame(before.slots().get(n),work.capture().slots().get(n));assertArrayEquals(before.slots().get(n).copyBytes(),work.capture().slots().get(n).copyBytes());}
  assertEquals(87,work.findObjSlot().orElseThrow());assertEquals(87,work.findNextObjSlotAfterWholeIndex(86).orElseThrow());assertTrue(work.findNextObjSlotAfterWholeIndex(87).isEmpty());assertEquals(before.slots(),original.capture().slots());
 }
 @Test void everyWriteDeleteClearRestoreAndCaptureActionIsIsolatedFromOriginalAndSibling(){
  var original=new CdObjectPool();original.write(original.state(32).moveByte(ID,0x19));var base=original.capture();var work=CdObjectPool.stagingCopy(base);var sibling=CdObjectPool.stagingCopy(base);
  work.write(work.state(32).moveByte(ID,0x29));var staged=work.capture();assertEquals(0x29,staged.slots().getFirst().id());assertEquals(base.slots(),original.capture().slots());assertEquals(base.slots(),sibling.capture().slots());
  work.objNull(32);assertEquals(0,work.state(32).id());assertEquals(base.slots(),original.capture().slots());work.restore(staged);work.deleteObject(32);work.clearDynamicSlots();work.restore(staged);assertEquals(staged.slots(),work.capture().slots());work.restore(base);assertEquals(base.slots(),work.capture().slots());assertEquals(base.slots(),original.capture().slots());
 }
 @Test void originalImagesAndOverlayDataRemainSameOwnerWhileForeignInputsRejectBeforeMutation(){
  var original=new CdObjectPool();var base=original.capture();var work=CdObjectPool.stagingCopy(base);var image=base.slots().getFirst().moveByte(ID,0x29);work.write(image);
  var derived=CdObjectPool.overlay(base,java.util.List.of(image));assertEquals(derived.slots(),work.capture().slots());assertEquals(base.slots(),original.capture().slots());
  var foreign=new CdObjectPool();assertThrows(NullPointerException.class,()->work.write(null));assertThrows(NullPointerException.class,()->work.restore(null));assertThrows(IllegalArgumentException.class,()->work.write(foreign.state(32)));assertThrows(IllegalArgumentException.class,()->work.restore(foreign.capture()));assertEquals(derived.slots(),work.capture().slots());
 }
 @Test void nullSourceAndExhaustedSearchDoNotInventAnyPoolAction(){assertThrows(NullPointerException.class,()->CdObjectPool.stagingCopy(null));var p=new CdObjectPool();for(int w=32;w<128;w++)p.write(p.state(w).moveByte(ID,1));var before=p.capture();var work=CdObjectPool.stagingCopy(before);assertTrue(work.findObjSlot().isEmpty());assertEquals(before.slots(),p.capture().slots());}
}
