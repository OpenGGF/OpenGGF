package com.openggf.game.soniccd;

import com.openggf.game.save.*;
import com.openggf.game.soniccd.media.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.openggf.game.soniccd.CdSaveProfileBootstrap.*;

/** Tests first; Java UNRUN. Real storage is TempDir only; actual source media is optional configured input.
 * Seeded profile values below are labelled codec/component controls, never live Module/World ingress. */
class TestCdSaveProfileBootstrap {
    @TempDir Path root;
    @Test void selectedGenuinelyMissingSlotCreatesFromOriginalTemplateOnlyAfterActualSynchronousWrite() throws Exception {
        var source=actual();var manager=new SaveManager(root);var service=bind(manager,source);var result=service.openSelectedSlot(1);assertEquals(Disposition.CREATED,result.disposition());var receipt=result.receipt().orElseThrow();assertTrue(service.ownsReceipt(receipt));assertEquals(Origin.CREATED,receipt.origin());assertEquals(0,result.nativeSaveDisabledAfterStorage().orElseThrow());assertEquals(SaveSlotState.VALID,manager.readSlotSummary("soniccd",1).state());assertEquals(receipt.profile().encode(),manager.readSlotSummary("soniccd",1).payload());assertEquals(0,receipt.profile().mainFields().timeAttackUnlockByte());assertEquals(0x10,receipt.profile().mainFields().titleFlagsByte());assertTrue(receipt.profile().tail().stream().allMatch(OptionalInt::isEmpty));
    }
    @Test void existingValidProfileLoadsHistoricalFieldsWithoutTemplateResetOrNewGameStores() throws Exception {
        var source=actual();var profile=historical(source);var manager=new SaveManager(root);manager.writeSlot("soniccd",2,profile.encode());byte[] before=Files.readAllBytes(file(2));var service=bind(manager,source);var result=service.openSelectedSlot(2);assertEquals(Disposition.EXISTING_VALID,result.disposition());assertEquals(Origin.EXISTING,result.receipt().orElseThrow().origin());assertEquals(profile.encode(),result.receipt().orElseThrow().profile().encode());assertArrayEquals(before,Files.readAllBytes(file(2)));assertEquals(0,result.nativeSaveDisabledAfterStorage().orElseThrow());assertEquals(0x81,result.receipt().orElseThrow().profile().policy().orElseThrow());
    }
    @Test void corruptJsonIsQuarantinedAndNeverReplacedWithTemplate() throws Exception {
        Files.createDirectories(file(1).getParent());Files.writeString(file(1),"{ bad");var service=bind(new SaveManager(root),actual());var out=service.openSelectedSlot(1);assertEquals(Disposition.CORRUPT,out.disposition());assertTrue(out.receipt().isEmpty());assertTrue(out.preparedPrefix().isEmpty());assertTrue(out.nativeSaveDisabledAfterStorage().isEmpty());assertFalse(Files.exists(file(1)));assertTrue(Files.exists(file(1).resolveSibling("slot1.json.corrupt")));
    }
    @Test void checksumWarningRemainsHistoricalRecoveryDataWithoutCreationOrLoadReceipt() throws Exception {
        var source=actual();var manager=new SaveManager(root);manager.writeSlot("soniccd",1,historical(source).encode());Files.writeString(file(1),Files.readString(file(1)).replace("\"hash\":\"","\"hash\":\"broken"));byte[] before=Files.readAllBytes(file(1));var out=bind(manager,source).openSelectedSlot(1);assertEquals(Disposition.HASH_WARNING,out.disposition());assertTrue(out.receipt().isEmpty());assertTrue(out.preparedPrefix().isEmpty());assertArrayEquals(before,Files.readAllBytes(file(1)));
    }
    @Test void checksummedButInvalidCdSchemaRemainsUntouchedRatherThanDefaulted() throws Exception {
        var manager=new SaveManager(root);manager.writeSlot("soniccd",1,Map.of("foreignGamePayload",1));byte[] before=Files.readAllBytes(file(1));var out=bind(manager,actual()).openSelectedSlot(1);assertEquals(Disposition.INVALID_PROFILE,out.disposition());assertTrue(out.failure().isPresent());assertTrue(out.receipt().isEmpty());assertTrue(out.preparedPrefix().isEmpty());assertArrayEquals(before,Files.readAllBytes(file(1)));
    }
    @Test void initCopyEstablishes676Plus28BytesAndNeverTheTwoDeclaredTailBytes() throws Exception {
        var a=actual();var p=CdSaveProfile.fromTemplate(a);assertEquals(84,p.timeAttackTimes().size());assertEquals(84,p.timeAttackInitials().size());assertEquals(3,p.reservedBeforeSpecial().size());assertEquals(18,p.reservedAfterFields().size());assertEquals(List.of(OptionalInt.empty(),OptionalInt.empty()),p.tail());assertTrue(p.policy().isEmpty());assertEquals(704,((Map<?,?>)p.encode().get("extent")).get("copiedBytes"));assertEquals(706,((Map<?,?>)p.encode().get("extent")).get("declaredBytes"));
        var times=a.view(CdInitialSaveAssets.Table.TIME_ATTACK_TIMES);var names=a.view(CdInitialSaveAssets.Table.TIME_ATTACK_INITIALS);for(int i=0;i<84;i++){assertEquals(times.unsignedLong(i*4),p.timeAttackTimes().get(i).longValue());assertEquals(names.unsignedLong(i*4),p.timeAttackInitials().get(i).longValue());}assertEquals(a.view(CdInitialSaveAssets.Table.DEFAULT_INITIALS).unsignedLong(0),p.defaultInitials());
    }
    @Test void sevenMainFieldsAllComeFromTheCopiedPrefixAndPreserveSavedWordOverlap() throws Exception {
        var a=actual();var p=CdSaveProfile.fromTemplate(a);var raw=a.view(CdInitialSaveAssets.Table.MAIN);var m=p.mainFields();assertEquals(raw.unsignedByte(0),m.zoneByte());assertEquals(raw.unsignedByte(1),m.timeAttackUnlockByte());assertEquals(raw.unsignedByte(2),m.unknownBackupByte());assertEquals(raw.unsignedByte(3),m.goodFuturesByte());assertEquals(raw.unsignedByte(4),m.titleFlagsByte());assertEquals(raw.unsignedByte(8),m.currentSpecialStageByte());assertEquals(raw.unsignedByte(9),m.timeStonesByte());assertEquals(raw.unsignedWord(0),m.levelPairWord());
    }
    @Test void strictCodecRoundTripsKnownTailsReservedFieldsRawWidthsAndPolicyWithoutCurrentAuthority() throws Exception {
        var a=actual();var p=historical(a);var q=CdSaveProfile.decode(a,p.encode());assertEquals(p.encode(),q.encode());assertEquals(List.of(OptionalInt.of(0xFE),OptionalInt.of(0xA5)),q.tail());assertEquals(0x81,q.policy().orElseThrow());assertThrows(UnsupportedOperationException.class,()->q.encode().clear());assertThrows(UnsupportedOperationException.class,()->q.timeAttackTimes().clear());assertThrows(UnsupportedOperationException.class,()->q.tail().clear());
    }
    @Test void actualSavedWordLowAndIndependentUnlockRemainDistinctUntilTheirNativeOrderedWrites() throws Exception {
        var a=actual();var p=CdSaveProfile.fromTemplate(a);var patch=new CdIpxProgress.SavePatch(k(0x1234),k(0xA5),k(0xD0),k(7),k(0xEE),k(6),k(0x7F));var q=p.withPatch(patch);assertEquals(0x1207,q.mainFields().levelPairWord());assertEquals(7,q.mainFields().timeAttackUnlockByte());assertEquals(0xA5,q.mainFields().goodFuturesByte());assertEquals(0xD0,q.mainFields().titleFlagsByte());assertEquals(0xEE,q.mainFields().unknownBackupByte());assertEquals(6,q.mainFields().currentSpecialStageByte());assertEquals(0x7F,q.mainFields().timeStonesByte());assertSame(p.timeAttackTimes(),q.timeAttackTimes());assertSame(p.tail(),q.tail());assertEquals(0,p.mainFields().levelPairWord());
    }
    @Test void partialSavePatchKeepsTheOriginalFirstWordBeforeLaterUnlockOverwrite() throws Exception {
        var p=CdSaveProfile.fromTemplate(actual());var partial=new CdIpxProgress.SavePatch(k(0x1234),k(0xA5),u(),u(),u(),u(),u());var q=p.withPatch(partial);assertEquals(0x1234,q.mainFields().levelPairWord());assertEquals(0x34,q.mainFields().timeAttackUnlockByte());assertEquals(0x10,q.mainFields().titleFlagsByte());assertEquals(0xA5,q.mainFields().goodFuturesByte());
    }
    @Test void nativeReadProjectionPreservesActualCurrentPolicyAndDoesNotInventLevelGoodFutureFlags() throws Exception {
        var p=historical(actual());var current=CdIpxProgress.unknown();var result=p.projectAfterRead(current);assertEquals(p.mainFields().levelPairWord(),result.savedLevel().word().orElseThrow());assertEquals(p.mainFields().timeAttackUnlockByte(),result.timeAttackUnlockByte().orElseThrow());assertTrue(result.saveDisabledByte().isEmpty());assertEquals(0xA5,result.goodFuturesByte().orElseThrow());assertEquals(0xD0,result.titleFlagsByte().orElseThrow());
    }
    @Test void temporaryCopyOverwritesOnlyThe704ByteEquivalentAndRetainsDestinationTailAndPolicy() throws Exception {
        var a=actual();var source=historical(a);var target=CdSaveProfile.fromTemplate(a).withTail(List.of(k(0x11),u())).withPolicy(k(0));var copied=source.temporaryCopyTo(target);assertEquals(source.mainFields(),copied.mainFields());assertEquals(source.timeAttackTimes(),copied.timeAttackTimes());assertSame(target.tail(),copied.tail());assertEquals(0,copied.policy().orElseThrow());assertEquals(List.of(k(0x11),u()),copied.tail());
    }
    @Test void schemaRejectsUnknownKeysMissingKeysForeignSourcesWrongExtentsAndFractionalNumbers() throws Exception {
        var a=actual();var good=CdSaveProfile.fromTemplate(a).encode();for(String key:good.keySet()){var bad=new LinkedHashMap<String,Object>(good);bad.remove(key);assertThrows(IllegalArgumentException.class,()->CdSaveProfile.decode(a,bad),key);}var extra=new LinkedHashMap<String,Object>(good);extra.put("ram",List.of());assertThrows(IllegalArgumentException.class,()->CdSaveProfile.decode(a,extra));
        var wrong=new LinkedHashMap<String,Object>(good);wrong.put("source",Map.of());assertThrows(IllegalArgumentException.class,()->CdSaveProfile.decode(a,wrong));var numeric=new LinkedHashMap<String,Object>(good);numeric.put("schema",1.0);assertThrows(IllegalArgumentException.class,()->CdSaveProfile.decode(a,numeric));
        var extent=new LinkedHashMap<String,Object>(good);extent.put("extent",Map.of("copiedBytes",706,"declaredBytes",706));assertThrows(IllegalArgumentException.class,()->CdSaveProfile.decode(a,extent));
    }
    @Test void codecRejectsBadRecordCountsByteRangesTailShapesAndUnknownKnowledgeEncoding() throws Exception {
        var a=actual();var good=CdSaveProfile.fromTemplate(a).encode();for(var replacement:List.<Object>of(List.of(),Collections.nCopies(83,0L),Collections.nCopies(84,-1L),Collections.nCopies(84,0x1_0000_0000L))){var bad=new LinkedHashMap<String,Object>(good);bad.put("timeAttackTimes",replacement);assertThrows(IllegalArgumentException.class,()->CdSaveProfile.decode(a,bad));}
        assertThrows(IllegalArgumentException.class,()->CdSaveProfile.fromTemplate(a).withTail(List.of(u())));assertThrows(IllegalArgumentException.class,()->CdSaveProfile.fromTemplate(a).withPolicy(k(256)));
        var bad=new LinkedHashMap<String,Object>(good);bad.put("mainTail",List.of(List.of(0,0),List.of()));assertThrows(IllegalArgumentException.class,()->CdSaveProfile.decode(a,bad));
    }
    @Test void providerCaptureReturnsOnlyTheOriginalCurrentReceiptPayloadAndDoesNotProveAnyNewWrite() throws Exception {
        var service=bind(new SaveManager(root),actual());var r=service.openSelectedSlot(1).receipt().orElseThrow();var provider=service.snapshotProvider(r);byte[] before=Files.readAllBytes(file(1));var context=context("soniccd",1);assertEquals(r.profile().encode(),provider.capture(SaveReason.NEW_SLOT_START,context));assertArrayEquals(before,Files.readAllBytes(file(1)));assertThrows(IllegalArgumentException.class,()->provider.capture(SaveReason.NEW_SLOT_START,context("soniccd",2)));assertThrows(IllegalArgumentException.class,()->provider.capture(SaveReason.NEW_SLOT_START,context("s3k",1)));assertThrows(UnsupportedOperationException.class,()->provider.capture(SaveReason.PROGRESSION_SAVE,context));
    }
    @Test void foreignAndRetiredReceiptsNeverAuthorizeProviderCapture() throws Exception {
        var a=actual();var first=bind(new SaveManager(root),a);var second=bind(new SaveManager(root),a);var r=first.openSelectedSlot(1).receipt().orElseThrow();assertFalse(second.ownsReceipt(r));assertThrows(IllegalArgumentException.class,()->second.snapshotProvider(r));var provider=first.snapshotProvider(r);first.openSelectedSlot(2);assertFalse(first.ownsReceipt(r));assertThrows(IllegalArgumentException.class,()->provider.capture(SaveReason.EXISTING_SLOT_LOAD,context("soniccd",1)));
    }
    @Test void missingTemporaryOwnerStopsWithoutSelectingPersistentStorageOrInitializingAnything() throws Exception {
        var service=bind(new SaveManager(root),actual());var out=service.openTemporary();assertEquals(Disposition.TEMPORARY_OWNER_UNAVAILABLE,out.disposition());assertTrue(out.receipt().isEmpty());assertTrue(out.nativeSaveDisabledAfterStorage().isEmpty());try(var files=Files.list(root)){assertEquals(0,files.count());}
    }
    @Test void publishedProfileWithFailedCleanupIsAHonestPersistentPrefixAndNeverATemporarySuccess() throws Exception {
        var original=new java.io.IOException("cleanup after real link");var manager=com.openggf.game.save.TestSaveProfileAccess.withCleanupFailure(root,original);
        var service=bind(manager,actual());var out=service.openSelectedSlot(1);assertEquals(Disposition.CREATED_CLEANUP_FAILED,out.disposition());assertTrue(out.persistentPublished());assertTrue(out.receipt().isEmpty());assertTrue(out.preparedPrefix().isPresent());assertTrue(out.nativeSaveDisabledAfterStorage().isEmpty());assertEquals(NextNativeOperation.HOST_TEMPORARY_CLEANUP,out.nextNativeOperation());assertSame(original,out.failure().orElseThrow());assertSame(original,out.cleanupFailure().orElseThrow());assertEquals(SaveSlotState.VALID,new SaveManager(root).readSlotSummary("soniccd",1).state());
        var later=service.openSelectedSlot(1);assertEquals(Disposition.EXISTING_VALID,later.disposition());assertTrue(later.receipt().isPresent());
    }
    @Test void actualCreationRecheckIoFailureKeepsPrefixWithoutPretendingThatAWriteReturned() throws Exception {
        var manager=com.openggf.game.save.TestSaveProfileAccess.withParentFailureAfterMissing(root);var out=bind(manager,actual()).openSelectedSlot(1);assertEquals(Disposition.CREATION_RECHECK_FAILED,out.disposition());assertFalse(out.persistentPublished());assertTrue(out.receipt().isEmpty());assertTrue(out.preparedPrefix().isPresent());assertTrue(out.nativeSaveDisabledAfterStorage().isEmpty());assertEquals(NextNativeOperation.NONE,out.nextNativeOperation());assertEquals("parent obstacle",Files.readString(root.resolve("soniccd")));
    }
    @Test void unsupportedFilesystemDoesNotBecomeNativeBackupFailureOrATemporaryProfile() throws Exception {
        var uri=java.net.URI.create("jar:"+root.resolve("profile.zip").toUri());
        try(var fs=FileSystems.newFileSystem(uri,Map.of("create","true"))){
            Files.createDirectories(fs.getPath("/soniccd"));
            var out=bind(new SaveManager(fs.getPath("/")),actual()).openSelectedSlot(1);assertEquals(Disposition.HOST_STORAGE_FAILURE,out.disposition());assertFalse(out.persistentPublished());assertTrue(out.receipt().isEmpty());assertTrue(out.nativeSaveDisabledAfterStorage().isEmpty());assertInstanceOf(UnsupportedOperationException.class,out.failure().orElseThrow());
        }
    }
    @Test void repeatedCorruptSelectionAndUnknownSiblingRemainFencesWithoutDefaultCreation() throws Exception {
        Files.createDirectories(file(1).getParent());Files.writeString(file(1),"{ bad");var a=actual();assertEquals(Disposition.CORRUPT,bind(new SaveManager(root),a).openSelectedSlot(1).disposition());var later=bind(new SaveManager(root),a).openSelectedSlot(1);assertEquals(Disposition.QUARANTINED_HISTORY,later.disposition());assertTrue(later.receipt().isEmpty());assertTrue(later.preparedPrefix().isEmpty());assertFalse(Files.exists(file(1)));Files.writeString(file(2).resolveSibling("slot2.json.corrupt.future"),"unknown retained file");assertEquals(Disposition.UNKNOWN_HISTORY,bind(new SaveManager(root),a).openSelectedSlot(2).disposition());assertFalse(Files.exists(file(2)));
    }
    @Test void originalReadErrorRetiresTheActualServiceScopeAndNeverReturnsAProfileReceipt() throws Exception {
        var original=new AssertionError("actual reader Error");var manager=com.openggf.game.save.TestSaveProfileAccess.withOneReadError(root,original);var service=bind(manager,actual());assertSame(original,assertThrows(AssertionError.class,()->service.openSelectedSlot(1)));assertFalse(Files.exists(file(1)));var later=service.openSelectedSlot(1);assertEquals(Disposition.CREATED,later.disposition());assertTrue(service.ownsReceipt(later.receipt().orElseThrow()));
    }
    @Test void genuinePendingReaderReentryRejectsBeforeChangingItsOwningSelection() throws Exception {
        CdSaveProfileBootstrap[] holder={null};boolean[] rejected={false};var manager=com.openggf.game.save.TestSaveProfileAccess.withReadAction(root,()->{assertThrows(IllegalStateException.class,()->holder[0].openSelectedSlot(2));rejected[0]=true;});holder[0]=bind(manager,actual());var out=holder[0].openSelectedSlot(1);assertTrue(rejected[0]);assertEquals(Disposition.CREATED,out.disposition());assertTrue(holder[0].ownsReceipt(out.receipt().orElseThrow()));assertFalse(Files.exists(file(2)));
    }
    @Test void snapshotOriginCannotBeSelectedByChangingTheSaveReason() throws Exception {
        var a=actual();var manager=new SaveManager(root);manager.writeSlot("soniccd",1,historical(a).encode());var service=bind(manager,a);var receipt=service.openSelectedSlot(1).receipt().orElseThrow();var provider=service.snapshotProvider(receipt);assertThrows(IllegalArgumentException.class,()->provider.capture(SaveReason.NEW_SLOT_START,context("soniccd",1)));assertEquals(receipt.profile().encode(),provider.capture(SaveReason.EXISTING_SLOT_LOAD,context("soniccd",1)));assertTrue(service.ownsReceipt(receipt));
    }
    @Test void boundServiceAndReceiptsAreInternalPrivatelyConstructedAndInvalidSelectionIsANonwrite() throws Exception {
        var service=bind(new SaveManager(root),actual());assertThrows(IllegalArgumentException.class,()->service.openSelectedSlot(-1));assertThrows(NullPointerException.class,()->bind(null,actual()));assertTrue(Arrays.stream(Receipt.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));try(var files=Files.list(root)){assertEquals(0,files.count());}
    }
    private Path file(int slot){return root.resolve("soniccd").resolve("slot"+slot+".json");}
    private static OptionalInt k(int x){return OptionalInt.of(x);}private static OptionalInt u(){return OptionalInt.empty();}
    private static CdSaveProfile historical(CdInitialSaveAssets a){
        var p=CdSaveProfile.fromTemplate(a).withPatch(new CdIpxProgress.SavePatch(k(0x1234),k(0xA5),k(0xD0),k(7),k(0xEE),k(6),k(0x7F))).withTail(List.of(k(0xFE),k(0xA5))).withPolicy(k(0x81));
        // Explicit complete historical codec fixture; these are not runtime/native-owner stores.
        var payload=new LinkedHashMap<String,Object>(p.encode());payload.put("reservedBeforeSpecial",List.of(1,2,3));payload.put("reservedAfterFields",java.util.stream.IntStream.range(0,18).map(i->0x80+i).boxed().toList());var times=new ArrayList<>(p.timeAttackTimes());times.set(0,0xFFFFFFFFL);payload.put("timeAttackTimes",times);return CdSaveProfile.decode(a,payload);
    }
    private static RuntimeSaveContext context(String game,int slot){return RuntimeSaveContext.forNewGame(SaveSessionContext.forSlot(game,slot,new SelectedTeam("sonic",List.of()),0,0));}
    private static CdInitialSaveAssets cached;private static String cachedRoot;
    private static synchronized CdInitialSaveAssets actual() throws Exception {
        String path=System.getProperty("soniccd.media.root");assumeTrue(path!=null&&!path.isBlank(),"Optional configured original USA CD media absent");if(cached!=null&&path.equals(cachedRoot))return cached;var usa=Path.of(path).resolve("USA");assertTrue(Files.isDirectory(usa),"Configured media path must exist");List<Path> cues;try(var files=Files.list(usa)){cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();}assertEquals(1,cues.size());try(var programs=CdProgramAssets.open(cues.getFirst(),Optional.of(CdReleaseVariant.USA),Optional.empty())){cached=CdInitialSaveAssets.fromPrograms(programs);}cachedRoot=path;return cached;
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
