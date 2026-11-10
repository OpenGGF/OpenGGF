package com.openggf.game.soniccd;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Source-only requirements; no source values confer private Boot invocation. */
class TestSonicCdR4PcmStockCustody {
 @Test void stockRetainsPrivateR4OwnerAndMetadataCannotReplaceIt() throws Exception {
  var source=TestCdOriginalBootPrefix.source();var owner=source.r4PcmSource().orElseThrow();
  assertSame(owner,source.r4PcmSource().orElseThrow());assertEquals(CdNativeFile.R4PCM,owner.file());
  assertEquals(source.release().normalizedSha256(),owner.source().wholeDiscSha256());
  var equal=new com.openggf.game.soniccd.media.CdR4PcmSource.Source(owner.source().variant(),owner.source().wholeDiscSha256(),owner.source().fileSha256());
  assertEquals(owner.source(),equal);assertEquals(0,owner.getClass().getConstructors().length);
 }
 @Test void oldConstructorExplicitlyOmitsR4AndNewBorrowIsPackagePrivate() throws Exception {
  var s=TestCdOriginalBootPrefix.source();
  var old=new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets(),s.r31aAssets(),s.initialSaveAssets(),s.r3PcmSource(),s.systemProgramSource());
  assertTrue(old.r4PcmSource().isEmpty());assertEquals(s.identity(),old.identity());
  var method=CdGameDataSource.class.getDeclaredMethod("r4PcmSource");
  assertFalse(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
  var field=CdGameDataSource.class.getDeclaredField("r4PcmSource");
  assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()));assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()));
  assertThrows(NullPointerException.class,()->new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets(),s.r31aAssets(),s.initialSaveAssets(),s.r3PcmSource(),s.systemProgramSource(),java.util.Map.of(),null));
 }
}
