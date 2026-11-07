package com.openggf.game.save;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.save.SaveProfileAccess.*;

/** Tests first; ALL Java UNRUN. Every read/write/quarantine/conflict is confined to TempDir. */
public class TestSaveProfileAccess {
    @TempDir Path root;
    private Path file(int slot){return root.resolve("soniccd").resolve("slot"+slot+".json");}
    @Test void actualMissingReadDiffersFromLegacyEmptyAndCreatesOnlyAfterSynchronousPublication() throws Exception {
        var manager=new SaveManager(root);assertEquals(SaveSlotState.EMPTY,manager.readSlotSummary("soniccd",1).state());
        var missing=lookup(manager,"soniccd",1);assertEquals(Disposition.MISSING,missing.disposition());assertInstanceOf(NoSuchFileException.class,missing.failure().orElseThrow());assertTrue(missing.payload().isEmpty());
        var created=createIfMissing(missing,Map.of("test",1));assertEquals(CreateDisposition.CREATED,created.disposition());assertTrue(created.failure().isEmpty());assertEquals(SaveSlotState.VALID,manager.readSlotSummary("soniccd",1).state());assertEquals(1,manager.readSlotSummary("soniccd",1).payload().get("test"));
    }
    @Test void validLookupFreezesPayloadAndCannotCreateOverIt() throws Exception {
        var manager=new SaveManager(root);manager.writeSlot("soniccd",1,Map.of("nested",Map.of("value",1)));var observed=lookup(manager,"soniccd",1);byte[] before=Files.readAllBytes(file(1));assertEquals(Disposition.VALID,observed.disposition());assertThrows(UnsupportedOperationException.class,()->observed.payload().clear());assertEquals(CreateDisposition.NOT_MISSING,createIfMissing(observed,Map.of("replacement",2)).disposition());assertArrayEquals(before,Files.readAllBytes(file(1)));
    }
    @Test void malformedQuarantineIsNeverAMissingReceiptAndKeepsLegacyEmpty() throws Exception {
        Files.createDirectories(file(1).getParent());Files.writeString(file(1),"{ invalid-json");var observed=lookup(new SaveManager(root),"soniccd",1);assertEquals(Disposition.CORRUPT,observed.disposition());assertTrue(observed.failure().isPresent());assertTrue(Files.exists(file(1).resolveSibling("slot1.json.corrupt")));assertEquals(CreateDisposition.NOT_MISSING,createIfMissing(observed,Map.of()).disposition());assertFalse(Files.exists(file(1)));
        Files.writeString(file(2),"{ invalid-json");assertEquals(SaveSlotState.EMPTY,new SaveManager(root).readSlotSummary("soniccd",2).state());
    }
    @Test void corruptWrongGameAndMissingPayloadKeepOriginalQuarantineBehavior() throws Exception {
        var manager=new SaveManager(root);manager.writeSlot("soniccd",1,Map.of("x",1));Files.writeString(file(1),Files.readString(file(1)).replace("\"game\":\"soniccd\"","\"game\":\"other\""));assertEquals(Disposition.CORRUPT,lookup(manager,"soniccd",1).disposition());assertTrue(Files.exists(file(1).resolveSibling("slot1.json.corrupt")));
        Files.writeString(file(2),"{\"game\":\"soniccd\",\"hash\":\"x\"}");assertEquals(Disposition.CORRUPT,lookup(manager,"soniccd",2).disposition());
    }
    @Test void hashWarningRetainsFileAndPayloadWithoutAMissingOrValidGrant() throws Exception {
        var manager=new SaveManager(root);manager.writeSlot("soniccd",1,Map.of("x",9));Files.writeString(file(1),Files.readString(file(1)).replace("\"hash\":\"","\"hash\":\"broken"));var lookup=lookup(manager,"soniccd",1);assertEquals(Disposition.HASH_WARNING,lookup.disposition());assertEquals(9,lookup.payload().get("x"));assertEquals(SaveSlotState.HASH_WARNING,manager.readSlotSummary("soniccd",1).state());assertTrue(Files.exists(file(1)));assertEquals(CreateDisposition.NOT_MISSING,createIfMissing(lookup,Map.of()).disposition());
    }
    @Test void realReaderIOExceptionKeepsOriginalIdentityAndLegacyUnavailable() throws Exception {
        Files.createDirectories(file(1).getParent());Files.writeString(file(1),"untouched");var original=new IOException("read failed");var manager=new SaveManager(root,path->{throw original;});var observed=lookup(manager,"soniccd",1);assertEquals(Disposition.UNAVAILABLE,observed.disposition());assertSame(original,observed.failure().orElseThrow());assertEquals(SaveSlotState.UNAVAILABLE,manager.readSlotSummary("soniccd",1).state());assertEquals("untouched",Files.readString(file(1)));assertFalse(Files.exists(file(1).resolveSibling("slot1.json.corrupt")));
    }
    @Test void noSuchFileCannotDeclareMissingWhileTheOwningDirectoryStillContainsTheSelectedEntry() throws Exception {
        Files.createDirectories(file(1).getParent());Files.writeString(file(1),"present at exists check");var missing=new NoSuchFileException(file(1).toString());var manager=new SaveManager(root,path->{throw missing;});assertEquals(SaveSlotState.UNAVAILABLE,manager.readSlotSummary("soniccd",1).state());var observed=lookup(manager,"soniccd",1);assertEquals(Disposition.UNKNOWN_HISTORY,observed.disposition());assertSame(missing,observed.failure().orElseThrow());assertEquals(CreateDisposition.NOT_MISSING,createIfMissing(observed,Map.of("x",1)).disposition());assertEquals("present at exists check",Files.readString(file(1)));
    }
    @Test void permissionFailureIsUnavailableRatherThanMissing() throws Exception {
        var denied=new AccessDeniedException("actual reader path");var manager=new SaveManager(root,path->{throw denied;});var observed=lookup(manager,"soniccd",1);assertEquals(Disposition.UNAVAILABLE,observed.disposition());assertSame(denied,observed.failure().orElseThrow());assertEquals(CreateDisposition.NOT_MISSING,createIfMissing(observed,Map.of()).disposition());assertFalse(Files.exists(file(1)));
    }
    @Test void runtimeParseFailureKeepsOriginalExceptionAndLegacyCorruptReturn() throws Exception {
        Files.createDirectories(file(1).getParent());Files.writeString(file(1),"x");var original=new ClassCastException("invalid parsed payload");var observed=lookup(new SaveManager(root,path->{throw original;}),"soniccd",1);assertEquals(Disposition.CORRUPT,observed.disposition());assertSame(original,observed.failure().orElseThrow());assertTrue(Files.exists(file(1).resolveSibling("slot1.json.corrupt")));
    }
    @Test void sameSerializedWriterConflictPreservesTheInsertedProfileAndConsumesOriginalMissingOnce() throws Exception {
        var first=new SaveManager(root);var second=new SaveManager(root);var missing=lookup(first,"soniccd",1);second.writeSlot("soniccd",1,Map.of("winner",7));byte[] before=Files.readAllBytes(file(1));var conflict=createIfMissing(missing,Map.of("loser",8));assertEquals(CreateDisposition.CONFLICT,conflict.disposition());assertArrayEquals(before,Files.readAllBytes(file(1)));assertEquals(CreateDisposition.ALREADY_USED,createIfMissing(missing,Map.of("retry",9)).disposition());
    }
    @Test void twoActualMissingObservationsCannotBothPublish() throws Exception {
        var first=lookup(new SaveManager(root),"soniccd",1);var second=lookup(new SaveManager(root),"soniccd",1);assertEquals(CreateDisposition.CREATED,createIfMissing(first,Map.of("first",1)).disposition());assertEquals(CreateDisposition.CONFLICT,createIfMissing(second,Map.of("second",2)).disposition());assertEquals(Map.of("first",1),new SaveManager(root).readSlotSummary("soniccd",1).payload());
    }
    @Test void queuedEarlierAsyncWriteWinsAgainstMissingCreation() throws Exception {
        var manager=new SaveManager(root);var missing=lookup(manager,"soniccd",1);manager.writeSlotAsync("soniccd",1,Map.of("earlier",1));assertEquals(CreateDisposition.CONFLICT,createIfMissing(missing,Map.of("new",2)).disposition());assertEquals(Map.of("earlier",1),manager.readSlotSummary("soniccd",1).payload());
    }
    @Test void parentFailureProducesNoCompletedCreationAndDoesNotDeleteTheObstacle() throws Exception {
        var manager=new SaveManager(root);var missing=lookup(manager,"soniccd",1);Files.writeString(root.resolve("soniccd"),"parent obstacle");var failed=createIfMissing(missing,Map.of("x",1));assertEquals(CreateDisposition.RECHECK_FAILED,failed.disposition());assertInstanceOf(IOException.class,failed.failure().orElseThrow());assertEquals("parent obstacle",Files.readString(root.resolve("soniccd")));assertEquals(CreateDisposition.ALREADY_USED,createIfMissing(missing,Map.of()).disposition());
    }
    @Test void quarantineFailureRetainsBothOriginalParseFailureAndActualIoFailure() throws Exception {
        Files.createDirectories(file(1).getParent());Files.writeString(file(1),"bad parsed data");
        var parse=new com.fasterxml.jackson.core.JsonParseException((com.fasterxml.jackson.core.JsonParser)null,"bad data");
        var manager=new SaveManager(root,path->{Files.delete(path);throw parse;});
        var observed=lookup(manager,"soniccd",1);assertEquals(Disposition.CORRUPT,observed.disposition());assertSame(parse,observed.failure().orElseThrow());assertInstanceOf(NoSuchFileException.class,observed.quarantineFailure().orElseThrow());assertEquals(CreateDisposition.NOT_MISSING,createIfMissing(observed,Map.of()).disposition());
    }
    @Test void existingLegacyProfileValidationStillQuarantinesAndReturnsEmpty() throws Exception {
        var manager=new SaveManager(root);manager.writeSlot("soniccd",1,Map.of("invalid",1));
        var profile=org.mockito.Mockito.mock(com.openggf.game.dataselect.DataSelectGameProfile.class);
        org.mockito.Mockito.when(profile.isPayloadValid(org.mockito.ArgumentMatchers.anyMap())).thenReturn(false);
        assertEquals(SaveSlotState.EMPTY,manager.readSlotSummary("soniccd",1,profile).state());assertTrue(Files.exists(file(1).resolveSibling("slot1.json.corrupt")));
    }
    @Test void securityReadFailureIsNotAbsenceAndPreservesTheOriginalFailure() throws Exception {
        Files.createDirectories(file(1).getParent());Files.writeString(file(1),"untouched");var denied=new SecurityException("actual read denied");
        var manager=new SaveManager(root,path->{throw denied;});var observed=lookup(manager,"soniccd",1);
        assertEquals(Disposition.ERROR,observed.disposition());assertSame(denied,observed.failure().orElseThrow());assertEquals("untouched",Files.readString(file(1)));assertEquals(CreateDisposition.NOT_MISSING,createIfMissing(observed,Map.of()).disposition());
        Files.writeString(file(2),"legacy");assertEquals(SaveSlotState.EMPTY,manager.readSlotSummary("soniccd",2).state());assertTrue(Files.exists(file(2).resolveSibling("slot2.json.corrupt")));
    }
    @Test void successfulLinkThenCleanupFailureRetainsPublishedPersistentPrefixAndOriginalFailure() throws Exception {
        var original=new IOException("actual temporary cleanup failed");var manager=withCleanupFailure(root,original);
        var missing=lookup(manager,"soniccd",1);var created=createIfMissing(missing,Map.of("committed",1));
        assertEquals(CreateDisposition.CREATED_CLEANUP_FAILED,created.disposition());assertTrue(created.persistentPublished());assertSame(original,created.failure().orElseThrow());assertSame(original,created.cleanupFailure().orElseThrow());assertTrue(Files.exists(file(1)));assertEquals(Map.of("committed",1),new SaveManager(root).readSlotSummary("soniccd",1).payload());
        byte[] before=Files.readAllBytes(file(1));assertEquals(CreateDisposition.ALREADY_USED,createIfMissing(missing,Map.of("replacement",2)).disposition());assertArrayEquals(before,Files.readAllBytes(file(1)));try(var paths=Files.list(file(1).getParent())){assertEquals(1,paths.filter(p->p.getFileName().toString().endsWith(".tmp")).count());}
    }
    @Test void anUnknownWinnerAppearingDuringRecheckStopsBeforeStagingAndCleanup() throws Exception {
        var cleanup=new IOException("cleanup after conflict");int[] reads={0};var manager=new SaveManager(root,path->{try{return actualRead(path);}catch(NoSuchFileException missing){if(++reads[0]==2){Files.createDirectories(path.getParent());Files.writeString(path,"external winner");}throw missing;}},path->{throw cleanup;});var missing=lookup(manager,"soniccd",1);var result=createIfMissing(missing,Map.of("loser",2));assertEquals(CreateDisposition.CONFLICT,result.disposition());assertFalse(result.persistentPublished());assertInstanceOf(NoSuchFileException.class,result.failure().orElseThrow());assertEquals(Disposition.UNKNOWN_HISTORY,result.recheckDisposition().orElseThrow());assertTrue(result.cleanupFailure().isEmpty());assertEquals("external winner",Files.readString(file(1)));
    }
    @Test void cleanupErrorRetainsOriginalHostOutcomeWithoutAHealthyCompletionClaim() throws Exception {
        var original=new AssertionError("host cleanup Error");var manager=new SaveManager(root,TestSaveProfileAccess::actualRead,path->{throw original;});var result=createIfMissing(lookup(manager,"soniccd",1),Map.of("committed",1));assertEquals(CreateDisposition.CREATED_CLEANUP_FAILED,result.disposition());assertTrue(result.persistentPublished());assertSame(original,result.failure().orElseThrow());assertSame(original,result.cleanupFailure().orElseThrow());assertEquals(Map.of("committed",1),new SaveManager(root).readSlotSummary("soniccd",1).payload());
    }
    /** Test-only fault control: publication/lookup stay real; only the later cleanup throws. */
    public static SaveManager withCleanupFailure(Path root,IOException original){return new SaveManager(root,TestSaveProfileAccess::actualRead,path->{throw original;});}
    /** Test-only actual filesystem race after the owning missing read, before exclusive creation. */
    public static SaveManager withParentFailureAfterMissing(Path root){int[] reads={0};return new SaveManager(root,path->{try{return actualRead(path);}catch(NoSuchFileException missing){if(++reads[0]==2)Files.writeString(path.getParent(),"parent obstacle");throw missing;}});}
    public static SaveManager withOneReadError(Path root,AssertionError original){int[] calls={0};return new SaveManager(root,path->{if(calls[0]++==0)throw original;return actualRead(path);});}
    public static SaveManager withReadAction(Path root,Runnable action){boolean[] first={true};return new SaveManager(root,path->{if(first[0]){first[0]=false;action.run();}return actualRead(path);});}
    private static Map<String,Object> actualRead(Path path)throws IOException {
        try(var in=Files.newInputStream(path)){return new com.fasterxml.jackson.databind.ObjectMapper().readValue(in,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}
    }
    @Test void unsupportedExclusiveLinksAreActualHostFailuresWithoutFallbackReplacement() throws Exception {
        var uri=java.net.URI.create("jar:"+root.resolve("profile.zip").toUri());
        try(var fs=FileSystems.newFileSystem(uri,Map.of("create","true"))){
            var storage=fs.getPath("/");Files.createDirectories(storage.resolve("soniccd"));var manager=new SaveManager(storage);var missing=lookup(manager,"soniccd",1);assertEquals(Disposition.MISSING,missing.disposition());
            var result=createIfMissing(missing,Map.of("x",1));assertEquals(CreateDisposition.FAILED,result.disposition());assertFalse(result.persistentPublished());assertInstanceOf(UnsupportedOperationException.class,result.failure().orElseThrow());assertTrue(result.cleanupFailure().isEmpty());assertFalse(Files.exists(storage.resolve("soniccd/slot1.json")));
        }
    }
    @Test void internalSnapshotFreezeFailureIsAnErrorWithoutChangingLegacyValidOrQuarantining() throws Exception {
        Object nested="leaf";for(int i=0;i<70;i++)nested=Map.of("child",nested);var manager=new SaveManager(root);manager.writeSlot("soniccd",1,Map.of("nested",nested));byte[] before=Files.readAllBytes(file(1));assertEquals(SaveSlotState.VALID,manager.readSlotSummary("soniccd",1).state());var observed=lookup(manager,"soniccd",1);assertEquals(Disposition.ERROR,observed.disposition());assertInstanceOf(IllegalArgumentException.class,observed.failure().orElseThrow());assertArrayEquals(before,Files.readAllBytes(file(1)));assertEquals(CreateDisposition.NOT_MISSING,createIfMissing(observed,Map.of()).disposition());
    }
    @Test void quarantineHistoryRemainsACorruptFenceAcrossManagersAndRepeatedLookups() throws Exception {
        Files.createDirectories(file(1).getParent());Files.writeString(file(1),"{ bad");assertEquals(Disposition.CORRUPT,lookup(new SaveManager(root),"soniccd",1).disposition());
        var later=lookup(new SaveManager(root),"soniccd",1);assertEquals(Disposition.QUARANTINED_HISTORY,later.disposition());assertTrue(later.absenceObservation().isPresent());assertEquals(CreateDisposition.NOT_MISSING,createIfMissing(later,Map.of("default",1)).disposition());assertFalse(Files.exists(file(1)));assertEquals("{ bad",Files.readString(file(1).resolveSibling("slot1.json.corrupt")));assertEquals(SaveSlotState.EMPTY,new SaveManager(root).readSlotSummary("soniccd",1).state());
    }
    @Test void actualQuarantineNamesUnknownSiblingsAndOtherSlotsNeverProvideProfileValues() throws Exception {
        Files.createDirectories(file(1).getParent());String[] suffixes={"", ".1", ".2147483646", ".0", ".01", ".2147483647", ".future"};
        for(int i=0;i<suffixes.length;i++){int slot=i+1;Path artifact=file(slot).resolveSibling(file(slot).getFileName()+".corrupt"+suffixes[i]);Files.writeString(artifact,"preserved");var observed=lookup(new SaveManager(root),"soniccd",slot);assertEquals(i<3?Disposition.QUARANTINED_HISTORY:Disposition.UNKNOWN_HISTORY,observed.disposition());assertTrue(observed.payload().isEmpty());assertEquals("preserved",Files.readString(artifact));}
        assertEquals(Disposition.MISSING,lookup(new SaveManager(root),"soniccd",99).disposition());
    }
    @Test void boundedLazyHistoryInspectionStopsUnknownWithoutCreatingOrDeletingAnyFile() throws Exception {
        Files.createDirectories(file(1).getParent());for(int i=0;i<4097;i++)Files.createFile(file(1).getParent().resolve("other-"+i));
        var observed=lookup(new SaveManager(root),"soniccd",1);assertEquals(Disposition.UNKNOWN_HISTORY,observed.disposition());assertEquals(CreateDisposition.NOT_MISSING,createIfMissing(observed,Map.of("default",1)).disposition());assertFalse(Files.exists(file(1)));try(var files=Files.list(file(1).getParent())){assertEquals(4097,files.count());}
    }
    @Test void quarantineAppearingBeforeCreationIsCaughtOnTheSameWriterRecheck() throws Exception {
        var manager=new SaveManager(root);var missing=lookup(manager,"soniccd",1);Files.createDirectories(file(1).getParent());Files.writeString(file(1).resolveSibling("slot1.json.corrupt"),"old data");var result=createIfMissing(missing,Map.of("default",1));assertEquals(CreateDisposition.CONFLICT,result.disposition());assertEquals(Disposition.QUARANTINED_HISTORY,result.recheckDisposition().orElseThrow());assertFalse(result.persistentPublished());assertFalse(Files.exists(file(1)));
    }
    @Test void explicitLegacyDeleteWithoutHistoryStillHasNormalLegacyAndInternalMissingSemantics() throws Exception {
        var manager=new SaveManager(root);manager.writeSlot("soniccd",1,Map.of("x",1));manager.deleteSlot("soniccd",1);assertEquals(SaveSlotState.EMPTY,manager.readSlotSummary("soniccd",1).state());assertEquals(Disposition.MISSING,lookup(manager,"soniccd",1).disposition());
    }
    @Test void aDifferentPathNoSuchFileExceptionIsNotProofThatTheSelectedSlotIsMissing() {
        var original=new NoSuchFileException("different input");var observed=lookup(new SaveManager(root,path->{throw original;}),"soniccd",1);assertEquals(Disposition.UNAVAILABLE,observed.disposition());assertSame(original,observed.failure().orElseThrow());assertEquals(CreateDisposition.NOT_MISSING,createIfMissing(observed,Map.of()).disposition());
    }
    @Test void interruptedWaitReportsUnknownCompletionWhileTheActualRunningWriterMayStillPublish() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);int[] calls={0};
        var manager=new SaveManager(root,path->{if(++calls[0]==2){entered.countDown();try{if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("test release timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}return actualRead(path);});
        var missing=lookup(manager,"soniccd",1);var result=new java.util.concurrent.atomic.AtomicReference<Creation>();var uncaught=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var caller=new Thread(()->{try{result.set(createIfMissing(missing,Map.of("committed",1)));}catch(Throwable e){uncaught.set(e);}},"profile-test-caller");
        try {caller.start();assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));caller.interrupt();caller.join(5000);assertFalse(caller.isAlive());assertNull(uncaught.get());assertEquals(CreateDisposition.COMPLETION_UNKNOWN,result.get().disposition());assertTrue(result.get().publication().isEmpty());assertInstanceOf(InterruptedException.class,result.get().failure().orElseThrow().getCause());}
        finally {release.countDown();if(caller.isAlive())caller.interrupt();caller.join(5000);}
        assertEquals(Map.of("committed",1),new SaveManager(root).readSlotSummary("soniccd",1).payload());
    }
    @Test void lookupAndCreationArePrivatelyConstructedEngineReceipts() {
        assertTrue(java.util.Arrays.stream(Lookup.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));assertTrue(java.util.Arrays.stream(Creation.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
