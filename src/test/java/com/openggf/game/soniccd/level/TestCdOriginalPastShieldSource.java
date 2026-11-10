package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Actual-media DATA and explicitly constructed component cuts; none grants a live World permit. */
class TestCdOriginalPastShieldSource {
 private static CdLevelLoadingSource source(CdStagePayload stage)throws Exception{
  var root=System.getProperty("soniccd.media.root");assertNotNull(root);
  try(var media=CdProgramAssets.open(Path.of(root,"USA","Sonic CD (US).cue"),Optional.of(CdReleaseVariant.USA),Optional.empty())){
   return CdProgrammeReader.open(media,stage.stage()).levelLoadingSource();
  }
 }
 private static CdAssetBytes bytes(int length,int value){var b=new byte[length];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b);}
 @Test void actualPastShieldUsesTheRetainedBReaderAndOriginalMappingAnimation()throws Exception{
  var s=source(CdStagePayload.R11B);var objects=s.standardPastObjectSource();var a=objects.powerupAssets();
  assertSame(objects.powerupAssets(),a);assertSame(objects.animation(CdR11bObjectData.Animation.ANI_POWERUP),a.animations());
  assertEquals(0x21D528,a.mappingAddressBits());assertEquals(9,a.animations().scriptCount());
  var copy=s.shield().copy(0);assertEquals(1024,copy.length());
  assertEquals("cbca98fc186fec63ea75c2c0dd8da06ec651c02c37232c15e38cecb749d5b7d0",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(copy.copy())));
  assertEquals(3,objects.reservedHandler(CdR11bObjectData.ReservedRole.SHIELD_AND_STARS).objectId());
 }
 @Test void actualBArtCopyRetainsTheCompleteCpuTailForEveryPointerSlot()throws Exception{
  var s=source(CdStagePayload.R11B);var runtime=CdPowerupArtRuntime.fromRawSources(s.shield());var before=bytes(0x480,0xA7);
  for(int slot=0;slot<5;slot++){var copy=runtime.prepareActiveCopy(before,2+slot*2).bytes();assertArrayEquals(s.shield().copy(slot).copy(),copy.slice(0,0x400).copy());assertArrayEquals(before.slice(0x400,0x80).copy(),copy.slice(0x400,0x80).copy());}
  assertArrayEquals(bytes(0x480,0xA7).copy(),before.copy());
 }
 @Test void theBAdapterRejectsPresentTtAndOutOfRangeRawSlots()throws Exception{
  assertThrows(UnsupportedOperationException.class,()->source(CdStagePayload.R11A).shield());
  assertThrows(UnsupportedOperationException.class,()->source(CdStagePayload.R41A).shield());
  var sources=source(CdStagePayload.R11B).shield();assertThrows(IndexOutOfBoundsException.class,()->sources.copy(-1));assertThrows(IndexOutOfBoundsException.class,()->sources.copy(5));
 }
 @Test void inactiveGateNeverTouchesSourceAndRawUnavailableSelectorCannotChangeCpu(){
  var calls=new AtomicInteger();var runtime=CdPowerupArtRuntime.fromRawSources(slot->{calls.incrementAndGet();throw new IllegalStateException("raw");});
  var before=new CdPowerupArtRuntime.State(bytes(0x480,0x31),bytes(0x480,0x52));
  assertSame(before,runtime.service(before,2).state());assertEquals(0,calls.get());
  assertThrows(CdPowerupArtRuntime.UnsupportedArt.class,()->runtime.prepareActiveCopy(before.cpuBuffer(),0));assertEquals(0,calls.get());
  assertArrayEquals(bytes(0x480,0x31).copy(),before.cpuBuffer().copy());
 }
 @Test void rawCopyFaultKeepsTheOriginalFailureAndAllImmutableInputs(){
  var failure=new IllegalArgumentException("actual copy read");var runtime=CdPowerupArtRuntime.fromRawSources(slot->{throw failure;});var before=bytes(0x480,0x61);
  assertSame(failure,assertThrows(IllegalArgumentException.class,()->runtime.prepareActiveCopy(before,2)));assertArrayEquals(bytes(0x480,0x61).copy(),before.copy());assertEquals(0,failure.getSuppressed().length);
 }
 /** Constructed storage only: ID3 is an explicit fixture, never a powerup-derived runtime spawn. */
 private static final class Cut{
  CdAssetBytes raw=bytes(31*64,0);int powerup;final AtomicInteger draws=new AtomicInteger();
  final CdReservedObjectBank bank=CdReservedObjectBank.backedBy(new CdReservedObjectBank.RawBacking(){public CdAssetBytes read(){return raw;}public boolean compareAndSet(CdAssetBytes before,CdAssetBytes next){if(raw!=before)return false;raw=next;return true;}});
  Cut(){bank.write(bank.state(6).moveByte(CdObjectState.ByteField.ID,3));}
  int read(CdShieldPowerup.ReadField f){return switch(f){case SHIELD->1;case TIME_WARP,INVINCIBLE,ZONE->0;case PLAYER_X_WORD->0x1234;case PLAYER_Y_WORD->0x5678;case PLAYER_FLAGS->1;case LAYER->throw new AssertionError("B must not read layer for zone0");};}
  CdShieldPowerup body(CdTimeStars.Assets a,CdShieldPowerup.ReadPort read,CdShieldPowerup.DrawReserved draw){return new CdShieldPowerup(bank,a,read,new CdShieldPowerup.PowerupPort(){public int read(){return powerup;}public void write(int v){powerup=v;}},draw);}
 }
 @Test void constructedBShieldInitializesOnlyTheExactReservedSlotThenUsesTheSharedAnimationAndDraw()throws Exception{
  var c=new Cut();var before=c.raw;var a=source(CdStagePayload.R11B).standardPastObjectSource().powerupAssets();var body=c.body(a,c::read,(bank,id)->{assertSame(c.bank,bank);assertEquals(6,id.wholeIndex());c.draws.incrementAndGet();});
  body.run(6);assertEquals(2,c.bank.state(6).unsignedByte(CdObjectState.ByteField.ROUTINE));assertEquals(0x21D528,c.bank.state(6).longBits(CdObjectState.LongField.MAPPINGS));assertEquals(0,c.draws.get());
  body.run(6);assertEquals(0x1234,c.bank.state(6).word(CdObjectState.WordField.X));assertEquals(0x5678,c.bank.state(6).word(CdObjectState.WordField.Y));assertEquals(0x82,c.powerup);assertEquals(1,c.draws.get());
  for(int whole=1;whole<32;whole++)if(whole!=6)assertArrayEquals(before.slice((whole-1)*64,64).copy(),c.raw.slice((whole-1)*64,64).copy());
 }
 @Test void constructedAbsentShieldFlagDeletesWithoutDrawOrSourceDefault()throws Exception{
  var c=new Cut();var body=c.body(source(CdStagePayload.R11B).standardPastObjectSource().powerupAssets(),field->field==CdShieldPowerup.ReadField.SHIELD?0:c.read(field),(b,id)->c.draws.incrementAndGet());body.run(6);body.run(6);assertArrayEquals(new byte[64],c.bank.state(6).copyBytes());assertEquals(0,c.draws.get());assertEquals(0,c.powerup);
 }
 @Test void constructedPlayerYFaultRetainsNativeXPrefixAndDoesNotWritePowerupOrDraw()throws Exception{
  var c=new Cut();var failure=new IllegalStateException("actual Y read");var body=c.body(source(CdStagePayload.R11B).standardPastObjectSource().powerupAssets(),field->{if(field==CdShieldPowerup.ReadField.PLAYER_Y_WORD)throw failure;return c.read(field);},(b,id)->c.draws.incrementAndGet());body.run(6);
  assertSame(failure,assertThrows(IllegalStateException.class,()->body.run(6)));assertEquals(0x1234,c.bank.state(6).word(CdObjectState.WordField.X));assertEquals(0,c.bank.state(6).word(CdObjectState.WordField.Y));assertEquals(0,c.powerup);assertEquals(0,c.draws.get());
 }
 @Test void constructedDrawFailureRetainsTheCompletedPowerupAndActorPrefixes()throws Exception{
  var c=new Cut();var failure=new IllegalStateException("actual Draw admission");var body=c.body(source(CdStagePayload.R11B).standardPastObjectSource().powerupAssets(),c::read,(b,id)->{c.draws.incrementAndGet();throw failure;});body.run(6);
  assertSame(failure,assertThrows(IllegalStateException.class,()->body.run(6)));assertEquals(0x82,c.powerup);assertEquals(0x1234,c.bank.state(6).word(CdObjectState.WordField.X));assertEquals(1,c.draws.get());
 }
 @Test void constructedOwnerRefusalPrecedesInitPublicationReadPowerupAndDraw()throws Exception{
  var c=new Cut();var before=c.raw;var failure=new IllegalStateException("actual source lease rejected");
  var body=CdShieldPowerup.withWorldOwner(c.bank,source(CdStagePayload.R11B).standardPastObjectSource().powerupAssets(),field->{throw new AssertionError("read after source refusal");},new CdShieldPowerup.PowerupPort(){public int read(){throw new AssertionError("powerup read");}public void write(int v){throw new AssertionError("powerup write");}},(b,id)->{throw new AssertionError("Draw after source refusal");},new CdShieldPowerup.DispatchObserver(){public void beforeOperation(CdShieldPowerup.Call call){throw failure;}public void afterOperation(CdShieldPowerup.Call call){throw new AssertionError("completed refused source");}public void failedOperation(CdShieldPowerup.Call call,Throwable original){assertSame(failure,original);}},new CdShieldPowerup.ReceiptGuard(){public void prepared(CdShieldPowerup.Call call){throw new AssertionError("prepared refused source");}public void completed(CdShieldPowerup.Call call){throw new AssertionError("completed refused source");}});
  assertSame(failure,assertThrows(IllegalStateException.class,()->body.run(6)));assertSame(before,c.raw);assertEquals(0,c.powerup);assertEquals(0,c.draws.get());
 }
 @Test void constructedReservedAliasCannotEnterShieldOrMutateOtherSlots()throws Exception{
  var c=new Cut();var body=c.body(source(CdStagePayload.R11B).standardPastObjectSource().powerupAssets(),c::read,(b,id)->c.draws.incrementAndGet());var before=c.raw;
  assertThrows(RuntimeException.class,()->body.run(7));assertSame(before,c.raw);assertEquals(0,c.powerup);assertEquals(0,c.draws.get());
 }
}
