package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.openggf.game.soniccd.CdProgramCatalogue.CommandDisposition.*;
import static com.openggf.game.soniccd.CdProgramCatalogue.FileDisposition.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCdProgramCatalogue {
    private final CdProgramCatalogue usa = CdProgramCatalogue.forRelease(CdReleaseVariant.USA);

    @ParameterizedTest
    @EnumSource(CdReleaseVariant.class)
    void everyReleaseHasTheCompleteUniqueStageSet(CdReleaseVariant release) {
        var catalogue = CdProgramCatalogue.forRelease(release);
        assertEquals(release, catalogue.release());
        assertEquals(70, catalogue.stages().size());
        assertEquals(70, catalogue.stages().stream().map(CdStagePayload::stage).distinct().count());
        assertEquals(70, catalogue.stages().stream().map(CdStagePayload::file).distinct().count());
        assertEquals(70, catalogue.stages().stream().map(CdStagePayload::loadCommand).distinct().count());
        for (CdStagePayload payload : catalogue.stages()) {
            assertEquals(payload, catalogue.stage(payload.stage()));
            assertEquals(payload, catalogue.stage(payload.stage().nativeId()));
            assertEquals(payload, catalogue.stageForCommand(payload.loadCommand()).orElseThrow());
            assertEquals(STAGE_LOAD, catalogue.disposition(payload.loadCommand()));
            assertEquals(CdNativeFile.Kind.STAGE_PROGRAM, payload.file().kind());
            assertEquals(List.of(payload.file(), payload.pcmDriver()), catalogue.nativeLoadFiles(payload.loadCommand()));
        }
    }

    @ParameterizedTest
    @CsvSource({"R11,0,1", "R12,7,7", "R13,36,50", "R31,26,40", "R32,30,44", "R33,34,48",
            "R41,38,52", "R42,42,56", "R43,46,60", "R51,48,62", "R52,52,66", "R53,56,70",
            "R61,58,72", "R62,62,76", "R63,66,80", "R71,68,82", "R72,72,86", "R73,76,90",
            "R81,78,92", "R82,82,96", "R83,86,100"})
    void stageMappingsFollowNativeFileAndCommandTables(String prefix, int firstFile, int firstCommand) {
        char[] suffixes = (prefix.endsWith("3") ? "CD" : "ABCD").toCharArray();
        for (int i = 0; i < suffixes.length; i++) {
            String id = prefix + suffixes[i];
            var payload = usa.stage(id);
            assertEquals(firstFile + i, payload.file().id());
            assertEquals(firstCommand + i, payload.loadCommand().id());
            assertEquals(id + "__.MMD;1", payload.file().isoName());
        }
    }

    @Test
    void loadBoundaryKeepsTheNativeBossDriverExceptions() {
        assertEquals(CdNativeFile.R3PCM, usa.stage("R33C").pcmDriver());
        assertEquals(CdNativeFile.R3PCM, usa.stage("R33D").pcmDriver());
        for (String id : List.of("R13C", "R13D", "R43C", "R43D", "R53C", "R53D", "R63C", "R63D",
                "R73C", "R73D", "R83C", "R83D")) {
            assertEquals(CdNativeFile.BOSSPCM, usa.stage(id).pcmDriver(), id);
        }
        for (CdStagePayload stage : usa.stages()) {
            if (stage.stage() instanceof CdStageKey.Traversal) {
                assertEquals("R" + stage.stage().zone().nativeRound() + "PCM", stage.pcmDriver().name());
            }
        }
    }

    @Test
    void fileAndCommandIdsAreUniqueAndBoundedIncludingTheUnnamedInvalidSlot() {
        assertEquals(127, usa.files().size());
        assertEquals(216, usa.commands().size());
        assertEquals(127, new HashSet<>(usa.files().stream().map(CdNativeFile::isoName).toList()).size());
        Set<Integer> fileIds = new HashSet<>();
        for (CdNativeFile file : usa.files()) {
            assertTrue(fileIds.add(file.id()));
            assertEquals(file, CdNativeFile.fromId(file.id()));
        }
        for (int id = 0; id < 127; id++) {
            assertTrue(fileIds.contains(id));
        }
        Set<Integer> commandIds = new HashSet<>();
        for (CdCommand command : usa.commands()) {
            assertTrue(commandIds.add(command.id()));
            assertEquals(command, CdCommand.fromId(command.id()));
            assertNotNull(usa.disposition(command));
        }
        for (int id = 1; id <= 216; id++) {
            assertTrue(commandIds.contains(id));
        }
        assertEquals(CdCommand.INVALID_39, CdCommand.fromId(39));
        assertTrue(CdCommand.INVALID_39.isDeclaredInvalid());
        assertEquals(INVALID, usa.disposition(CdCommand.INVALID_39));
        assertTrue(usa.stageForCommand(CdCommand.INVALID_39).isEmpty());
        assertTrue(usa.programForCommand(CdCommand.INVALID_39).isEmpty());
        assertEquals(List.of(CdNativeFile.R11A, CdNativeFile.R11A), usa.nativeLoadFiles(CdCommand.INVALID_39));
        for (int id : new int[]{-1, 0, 217, 65535}) {
            assertThrows(IllegalArgumentException.class, () -> CdCommand.fromId(id));
        }
        for (int id : new int[]{-1, 127, 65535}) {
            assertThrows(IllegalArgumentException.class, () -> CdNativeFile.fromId(id));
        }
    }

    @Test
    void demosAndSpecialStagesAreSeparateFromOrdinaryStages() {
        assertEquals(List.of(CdNativeFile.R11ADEMO, CdNativeFile.R1PCM), usa.nativeLoadFiles(CdCommand.R11ADEMO));
        assertEquals(List.of(CdNativeFile.R43CDEMO, CdNativeFile.BOSSPCM), usa.nativeLoadFiles(CdCommand.R43CDEMO));
        assertEquals(List.of(CdNativeFile.R82ADEMO, CdNativeFile.R8PCM), usa.nativeLoadFiles(CdCommand.R82ADEMO));
        for (CdCommand command : List.of(CdCommand.R11ADEMO, CdCommand.R43CDEMO, CdCommand.R82ADEMO)) {
            assertEquals(DEMO_LOAD, usa.disposition(command));
            assertTrue(usa.stageForCommand(command).isEmpty());
        }
        assertEquals(List.of(CdNativeFile.SPECMAIN, CdNativeFile.SPECSUB), usa.nativeLoadFiles(CdCommand.SPECSTAGE));
        assertEquals(PROGRAM_LOAD, usa.disposition(CdCommand.SPECSTAGE));
        assertThrows(IllegalArgumentException.class, () -> usa.stage("SPECSTAGE"));
    }

    @Test
    void nonstageLoadersKeepAllComponentsInNativeLoadOrder() {
        assertEquals(List.of(CdNativeFile.TITLEMAIN, CdNativeFile.TITLESUB), usa.nativeLoadFiles(CdCommand.TITLE));
        assertEquals(List.of(CdNativeFile.DAGARDMAIN, CdNativeFile.DAGARDDATA, CdNativeFile.DAGARDSUB),
                usa.nativeLoadFiles(CdCommand.DAGARDEN));
        assertEquals(List.of(CdNativeFile.THANKSMAIN, CdNativeFile.THANKSDATA, CdNativeFile.THANKSSUB),
                usa.nativeLoadFiles(CdCommand.THANKYOU));
        assertEquals(List.of(CdNativeFile.BURAMINIT, CdNativeFile.BURAMSUB), usa.nativeLoadFiles(CdCommand.BURAMINIT));
        assertEquals(List.of(CdNativeFile.BURAMMAIN, CdNativeFile.BURAMSUB), usa.nativeLoadFiles(CdCommand.BURAMMGR));
        assertEquals(List.of(CdNativeFile.TIMEATKMAIN), usa.nativeLoadFiles(CdCommand.TIMEATK));
        assertEquals(NO_SPX_LOAD_BINDING, usa.fileDisposition(CdNativeFile.TIMEATKSUB));
        assertEquals(NO_SPX_LOAD_BINDING, usa.fileDisposition(CdNativeFile.FINALPCM));
    }

    @Test
    void streamsAndSwappedEndingFilenamesKeepTheirNativeIdentities() {
        var bad = usa.programForCommand(CdCommand.BADEND).orElseThrow();
        var good = usa.programForCommand(CdCommand.GOODEND).orElseThrow();
        assertEquals(List.of(CdNativeFile.ENDMAIN, CdNativeFile.BADENDSUB), bad.loadFiles());
        assertEquals("GOODEND.BIN;1", bad.loadFiles().getLast().isoName());
        assertEquals(List.of(CdNativeFile.BADENDSTM), bad.streamFiles());
        assertEquals("BADEND.BIN;1", good.loadFiles().getLast().isoName());
        assertEquals(List.of(CdNativeFile.GOODENDSTM), good.streamFiles());
        assertEquals(List.of(CdNativeFile.OPENSTM), CdNativeProgram.OPENING.streamFiles());
        assertEquals(List.of(CdNativeFile.PENCILSTM), CdNativeProgram.PENCILTEST.streamFiles());
        for (CdNativeFile stream : List.of(CdNativeFile.OPENSTM, CdNativeFile.PENCILSTM,
                CdNativeFile.GOODENDSTM, CdNativeFile.BADENDSTM)) {
            assertEquals(CdNativeFile.Kind.VIDEO_STREAM, stream.kind());
            assertEquals(STREAMED_BY_PROGRAM, usa.fileDisposition(stream));
        }
        assertEquals(CdNativeFile.Kind.DATA, CdNativeFile.DAGARDDATA.kind());
        assertEquals(CdNativeFile.Kind.PCM_DRIVER, CdNativeFile.R1PCM.kind());
    }

    @Test
    void dummyCommandsAliasTheMcSonicPayloadWithoutClaimingItsWholeBehavior() {
        for (CdCommand command : List.of(CdCommand.DUMMY1, CdCommand.DUMMY2, CdCommand.DUMMY3,
                CdCommand.DUMMY4, CdCommand.DUMMY5)) {
            assertEquals(UNUSED_PAYLOAD_ALIAS, usa.disposition(command));
            var program = usa.programForCommand(command).orElseThrow();
            assertEquals(CdNativeProgram.MCSONIC, program.payloadAliasOf().orElseThrow());
            assertEquals(List.of(CdNativeFile.MCSONIC), program.loadFiles());
            assertNotEquals(CdCommand.MCSONIC.sourceHandler(), command.sourceHandler());
        }
        for (CdNativeFile file : List.of(CdNativeFile.DUMMY5, CdNativeFile.DUMMY6, CdNativeFile.DUMMY7,
                CdNativeFile.DUMMY8, CdNativeFile.DUMMY9)) {
            assertEquals(UNUSED_PROTOTYPE_PROGRAM, usa.fileDisposition(file));
            assertFalse(usa.programs().stream().anyMatch(program -> program.loadFiles().contains(file)));
        }
        assertTrue(CdNativeFile.DUMMY5.copySource().isEmpty());
        assertEquals(CdNativeFile.DUMMY5, CdNativeFile.DUMMY9.copySource().orElseThrow());
        assertTrue(CdNativeProgram.MCSONIC.payloadAliasOf().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"FUTURESFXT,R1PCM", "PASTSFXT,R1PCM", "ALRGHTSFXT,R1PCM", "GIVEUPSFXT,R1PCM",
            "YESSFXT,R1PCM", "YEAHSFXT,R1PCM", "GIGGLESFXT,R1PCM", "YELPSFXT,R1PCM",
            "STOMPSFXT,R3PCM", "BUMPERSFXT,R3PCM", "R1BMUST,R1PCM", "R3BMUST,R3PCM",
            "R4BMUST,R4PCM", "R5BMUST,R5PCM", "R6BMUST,R6PCM", "R7BMUST,R7PCM", "R8BMUST,R8PCM"})
    void soundTestPcmCommandsKeepAudioDispositionAndPotentialLoadDependency(CdCommand command, CdNativeFile driver) {
        for (CdReleaseVariant release : CdReleaseVariant.values()) {
            var catalogue = CdProgramCatalogue.forRelease(release);
            assertEquals(AUDIO, catalogue.disposition(command));
            assertTrue(catalogue.stageForCommand(command).isEmpty());
            assertTrue(catalogue.programForCommand(command).isEmpty());
            assertEquals(List.of(driver), catalogue.nativeLoadFiles(command));
        }
    }

    @Test
    void residentPcmPlaybackAndCddaCommandsHaveNoLoadTargets() {
        for (CdCommand command : List.of(CdCommand.FUTURESFX, CdCommand.PASTMUS, CdCommand.PAUSEPCM,
                CdCommand.R1AMUST, CdCommand.R3AMUST, CdCommand.FINALMUST, CdCommand.BOSSMUST,
                CdCommand.FINALMUS, CdCommand.SHOESMUST, CdCommand.STOPCDDA)) {
            assertEquals(AUDIO, usa.disposition(command));
            assertEquals(List.of(), usa.nativeLoadFiles(command), command.name());
        }
        assertThrows(UnsupportedOperationException.class,
                () -> usa.nativeLoadFiles(CdCommand.R3BMUST).clear());
    }

    @Test
    void everyDispatchHasOneDispositionAndEveryFileHasAUseOrAnExplicitLimit() {
        assertEquals(33, usa.programs().size());
        assertEquals(33, usa.programs().stream().map(CdNativeProgram::command).distinct().count());
        int audioCommandsWithLoadDependencies = 0;
        for (CdCommand command : usa.commands()) {
            switch (usa.disposition(command)) {
                case STAGE_LOAD -> assertTrue(usa.stageForCommand(command).isPresent());
                case PROGRAM_LOAD, DEMO_LOAD, UNUSED_PAYLOAD_ALIAS -> {
                    assertTrue(usa.programForCommand(command).isPresent());
                    assertFalse(usa.nativeLoadFiles(command).isEmpty());
                }
                case AUDIO -> {
                    assertTrue(usa.stageForCommand(command).isEmpty());
                    assertTrue(usa.programForCommand(command).isEmpty());
                    var files = usa.nativeLoadFiles(command);
                    assertTrue(files.size() <= 1, command.name());
                    for (CdNativeFile file : files) {
                        assertEquals(CdNativeFile.Kind.PCM_DRIVER, file.kind(), command.name());
                        audioCommandsWithLoadDependencies++;
                    }
                }
                case BACKUP_RAM, SPECIAL_STAGE_FLAGS -> {
                    assertTrue(usa.stageForCommand(command).isEmpty());
                    assertTrue(usa.programForCommand(command).isEmpty());
                    assertTrue(usa.nativeLoadFiles(command).isEmpty());
                }
                case INVALID -> assertEquals(CdCommand.INVALID_39, command);
            }
        }
        assertEquals(17, audioCommandsWithLoadDependencies);
        for (CdNativeFile file : usa.files()) {
            switch (usa.fileDisposition(file)) {
                case SPX_LOAD_COMPONENT -> assertTrue(usa.commands().stream()
                        .filter(command -> usa.disposition(command) != INVALID)
                        .anyMatch(command -> usa.nativeLoadFiles(command).contains(file)), file.name());
                case STREAMED_BY_PROGRAM -> assertTrue(usa.programs().stream()
                        .anyMatch(program -> program.streamFiles().contains(file)), file.name());
                case NO_SPX_LOAD_BINDING, UNUSED_PROTOTYPE_PROGRAM -> assertTrue(usa.commands().stream()
                        .noneMatch(command -> usa.nativeLoadFiles(command).contains(file)), file.name());
            }
        }
        assertEquals(AUDIO, usa.disposition(CdCommand.FINALMUS));
        assertEquals(BACKUP_RAM, usa.disposition(CdCommand.READSAVE));
        assertEquals(SPECIAL_STAGE_FLAGS, usa.disposition(CdCommand.INITSS));
    }

    @Test
    void releaseViewsStaySeparateAndAllReturnedTablesAreImmutable() {
        var japan = CdProgramCatalogue.forRelease(CdReleaseVariant.JAPAN);
        var europe = CdProgramCatalogue.forRelease(CdReleaseVariant.EUROPE);
        assertNotEquals(usa, japan);
        assertNotEquals(usa, europe);
        assertEquals(usa.stages(), japan.stages());
        assertEquals(usa.commands(), europe.commands());
        assertEquals("SEGAIPSAMP", japan.release().discHeaderVolumeId());
        assertEquals("SEGASONICCD", usa.release().discHeaderVolumeId());
        assertEquals("08271993", europe.release().sourceBuildDate());
        assertEquals("GM G-6021  -00", japan.release().productId());
        assertEquals("GM MK-4407 -00", usa.release().productId());
        assertEquals("GM MK-4407-00", europe.release().productId());
        assertEquals(List.of("J", "U", "E"), Arrays.stream(CdReleaseVariant.values())
                .map(CdReleaseVariant::headerMarket).toList());
        assertThrows(UnsupportedOperationException.class, () -> usa.stages().clear());
        assertThrows(UnsupportedOperationException.class, () -> usa.files().clear());
        assertThrows(UnsupportedOperationException.class, () -> usa.commands().clear());
        assertThrows(UnsupportedOperationException.class, () -> usa.programs().clear());
        assertThrows(UnsupportedOperationException.class, () -> CdNativeProgram.TITLE.loadFiles().clear());
        assertThrows(UnsupportedOperationException.class, () -> CdNativeProgram.OPENING.streamFiles().clear());
        assertThrows(NullPointerException.class, () -> CdProgramCatalogue.forRelease(null));
        assertThrows(NullPointerException.class, () -> usa.disposition(null));
        assertThrows(NullPointerException.class, () -> usa.stage((CdStageKey) null));
    }
}
