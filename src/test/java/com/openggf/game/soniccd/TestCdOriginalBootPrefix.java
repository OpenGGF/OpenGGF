package com.openggf.game.soniccd;

import com.openggf.game.save.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests FIRST. Positive sources use the original USA pipeline. No World/driver/Title claim. Author Java was UNRUN; Root qualification is recorded in the implementation plan. */
class TestCdOriginalBootPrefix {
    @TempDir Path directory;
    @Test void ordinaryValuesCannotConstructBootOrContinuationAuthority() {
        assertTrue(Arrays.stream(CdOriginalBootPrefix.class.getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));
        assertTrue(Arrays.stream(CdOriginalBootPrefix.Continuation.class.getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));
        assertTrue(Arrays.stream(CdOriginalBootPrefix.Preparation.class.getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));
    }
    @Test void missingInputsCannotReachStorage() {
        assertThrows(NullPointerException.class,()->CdOriginalBootPrefix.prepare(null,new SaveManager(directory),1,SonicCdGameModule.ProfileEntry.NEW_GAME,environment()));
        assertFalse(Files.exists(directory.resolve("scd")));
    }
    @Nested class ActualUsa {
        @Test void freshOriginalCallsProduceKnownAbsenceWithoutDriverOrCddaCompletion() throws Exception {
            var p=prepare(directory);var v=p.boot().state();assertEquals(SonicCdProgramLoadServices.PcmKnowledge.KNOWN_ABSENT,v.loads().pcmKnowledge());assertEquals(SonicCdProgramLoadServices.SelectorKnowledge.CLEARED,v.loads().selectorKnowledge());assertTrue(v.loads().installed().isEmpty());assertTrue(v.loads().musicSetting().isEmpty());assertTrue(v.loads().masterSetting().isEmpty());
        }
        @Test void bothMainClearsPrecedeTitleAndFirstStorageSearch() throws Exception {
            var calls=prepare(directory).boot().state().calls();assertEquals(2,Collections.frequency(calls,CdOriginalBootPrefix.Call.MAIN_VARIABLES_CLEAR));assertTrue(calls.indexOf(CdOriginalBootPrefix.Call.TITLE_FLAGS_90)>calls.lastIndexOf(CdOriginalBootPrefix.Call.MAIN_VARIABLES_CLEAR));assertTrue(calls.indexOf(CdOriginalBootPrefix.Call.BRAM_VARIABLES_CLEAR)<calls.indexOf(CdOriginalBootPrefix.Call.BACKEND_INITIALIZED));assertTrue(calls.indexOf(CdOriginalBootPrefix.Call.BACKEND_INITIALIZED)<calls.indexOf(CdOriginalBootPrefix.Call.STORAGE_SEARCH));
        }
        @Test void historicalMdAndBuramClearLogsDoNotAuthorizeTheFinalStageBank() throws Exception {
            var boot=prepare(directory).boot();
            assertTrue(boot.state().calls().contains(CdOriginalBootPrefix.Call.WORK_FILE_CLEAR));
            assertThrows(com.openggf.game.soniccd.resources.CdBlockDefinitionStaging.UnknownRead.class,
                    () -> boot.state().blockDefinitions().unsignedByte(0));
            assertThrows(com.openggf.game.soniccd.resources.CdBlockDefinitionStaging.UnknownRead.class,
                    () -> boot.state().blockDefinitions().unsignedWord(8190));
        }
        @Test void genuineInitializationFactsAndTitle90AreNotLevelStart() throws Exception {
            var v=prepare(directory).boot().state();assertEquals(CdProgramInitialization.KnownBit.SET,v.initialization().controllerSetupBit());assertEquals(CdProgramInitialization.MarkerKnowledge.DIFFERS_FROM_INIT,v.initialization().initMarker());assertEquals(0,v.initialization().versionCache().value());assertEquals(OptionalInt.of(0x90),v.progress().titleFlagsByte());assertEquals(CdProgramInitialization.Branch.DO_INIT,CdProgramInitialization.selectStartBranch(v.initialization()));
        }
        @Test void broadBramClearOwnsBothCpuPalettesAndDisplayedOutputIsSeparate() throws Exception {
            var v=prepare(directory).boot().state();assertArrayEquals(new byte[128],v.cpuPalettes().orElseThrow().normal().copy());assertArrayEquals(new byte[128],v.cpuPalettes().orElseThrow().fade().copy());assertArrayEquals(new byte[128],v.video().displayedPalette().orElseThrow().copy());assertFalse(v.video().displayEnabled().orElseThrow());assertTrue(v.video().patternsCleared());
        }
        @Test void mainWordRamTailAndTemporaryTailHaveDifferentClearProofs() throws Exception {
            var p=prepare(directory);assertEquals(List.of(OptionalInt.of(0),OptionalInt.of(0)),p.boot().state().wordRam().mainTail());assertEquals(List.of(OptionalInt.empty(),OptionalInt.empty()),p.boot().state().wordRam().temporaryTail());assertEquals(List.of(OptionalInt.of(0),OptionalInt.of(0)),p.selected().result().receipt().orElseThrow().profile().tail());
        }
        @Test void oldCreationStillHasUnknownTail() throws Exception {
            var s=source();var old=CdSelectedProfileVisit.prepare(s,new SaveManager(directory),1,SonicCdGameModule.ProfileEntry.NEW_GAME);assertEquals(List.of(OptionalInt.empty(),OptionalInt.empty()),old.result().receipt().orElseThrow().profile().tail());
        }
        @Test void existingUnknownAndDirtyStorageTailIsNeverFilledFromEarlierClear() throws Exception {
            var s=source();var manager=new SaveManager(directory);var original=CdSaveProfile.fromTemplate(s.initialSaveAssets().orElseThrow()).withTail(List.of(OptionalInt.empty(),OptionalInt.of(0xAB)));manager.writeSlot("scd",1,original.encode());var p=CdOriginalBootPrefix.prepare(s,manager,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,environment());assertEquals(original.tail(),p.selected().result().receipt().orElseThrow().profile().tail());assertEquals(original.tail(),p.boot().state().wordRam().mainTail());
        }
        @Test void fmCopiesOnlyActualBankAndFixedReturnRequest() throws Exception {
            var f=prepare(directory).boot().state().fm().orElseThrow();assertEquals(0xE0,f.resident().unsignedByte(0x1C0A));assertFalse(f.resident().definedAt(0x1AD6));assertFalse(f.resident().definedAt(0x1C09));assertFalse(f.resident().definedAt(0x1C0B));for(int i=0;i<f.assets().bank().length();i++)assertEquals(f.assets().bank().unsignedByte(i),f.resident().unsignedByte(i));
        }
        @Test void explicitUnknownEnvironmentIsRetainedUntilNativeVersionRead() throws Exception {
            var unknown=new CdProgramInitialization.ExecutionEnvironment(Optional.empty());var p=CdOriginalBootPrefix.prepare(source(),new SaveManager(directory),1,SonicCdGameModule.ProfileEntry.NEW_GAME,unknown);assertSame(unknown,p.boot().environment());assertThrows(CdProgramInitialization.UnknownRead.class,()->CdProgramInitialization.requireExecutionRegion(p.boot().environment()));
            for(int raw:new int[]{0,0x40,0x80,0xC0}){var explicit=new CdProgramInitialization.ExecutionEnvironment(Optional.of(new CdProgramInitialization.ExecutionRegion(raw)));var next=CdOriginalBootPrefix.prepare(source(),new SaveManager(directory.resolve(Integer.toHexString(raw))),1,SonicCdGameModule.ProfileEntry.NEW_GAME,explicit);assertSame(explicit,next.boot().environment());assertEquals(0,next.boot().state().initialization().versionCache().value(),"Native VERSION read has not happened");}
        }
        @Test void failedBackendDoesNotSearchAndPreservesActualEarlierPrefix() throws Exception {
            var s=source();Files.writeString(directory.resolve("scd"),"blocking namespace");var stop=assertThrows(CdOriginalBootPrefix.Failed.class,()->CdOriginalBootPrefix.prepare(s,new SaveManager(directory),1,SonicCdGameModule.ProfileEntry.NEW_GAME,environment()));assertTrue(stop.completedPrefix().cpuPalettes().isPresent());assertEquals(CdOriginalBootPrefix.Call.BRAM_SELECTOR_CLEAR,stop.completedPrefix().calls().getLast());assertFalse(stop.completedPrefix().calls().contains(CdOriginalBootPrefix.Call.STORAGE_SEARCH));assertEquals("blocking namespace",Files.readString(directory.resolve("scd")));assertFalse(Files.exists(directory.resolve("slot1.json")));
        }
    }
    static CdProgramInitialization.ExecutionEnvironment environment(){return new CdProgramInitialization.ExecutionEnvironment(Optional.of(new CdProgramInitialization.ExecutionRegion(0)));}
    static CdOriginalBootPrefix.Preparation prepare(Path root)throws Exception{return CdOriginalBootPrefix.prepare(source(),new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,environment());}
    static synchronized CdGameDataSource source()throws Exception{
        String root=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Optional original USA source absent");
        if(cached!=null&&root.equals(cachedRoot))return cached;
        try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(v->v.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cached=CdStockLaunch.prepare(cues.getFirst(),CdStockLaunch.Request.r11a());cachedRoot=root;return cached;}
    }
    private static CdGameDataSource cached;private static String cachedRoot;
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
