package com.openggf.game.soniccd;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Release-bound view of one finite native table. No media bytes or execution environment
 * are implied by this source catalogue. Runtime consumers must use verified user media.
 * System.inc:12-138,145-360 and .SPCmds (SPX.asm:331-549) own the identities.
 * Paths are relative to docs/scddisasm/src.
 */
public record CdProgramCatalogue(CdReleaseVariant release) {
    private static final List<CdStagePayload> STAGES = List.of(CdStagePayload.values());
    private static final List<CdNativeFile> FILES = List.of(CdNativeFile.values());
    private static final List<CdCommand> COMMANDS = List.of(CdCommand.values());
    private static final List<CdNativeProgram> PROGRAMS = List.of(CdNativeProgram.values());
    private static final Map<CdCommand, CdNativeFile> SOUND_TEST_PCM_DRIVERS = Map.ofEntries(
        Map.entry(CdCommand.FUTURESFXT, CdNativeFile.R1PCM),
        Map.entry(CdCommand.PASTSFXT, CdNativeFile.R1PCM),
        Map.entry(CdCommand.ALRGHTSFXT, CdNativeFile.R1PCM),
        Map.entry(CdCommand.GIVEUPSFXT, CdNativeFile.R1PCM),
        Map.entry(CdCommand.YESSFXT, CdNativeFile.R1PCM),
        Map.entry(CdCommand.YEAHSFXT, CdNativeFile.R1PCM),
        Map.entry(CdCommand.GIGGLESFXT, CdNativeFile.R1PCM),
        Map.entry(CdCommand.YELPSFXT, CdNativeFile.R1PCM),
        Map.entry(CdCommand.STOMPSFXT, CdNativeFile.R3PCM),
        Map.entry(CdCommand.BUMPERSFXT, CdNativeFile.R3PCM),
        Map.entry(CdCommand.R1BMUST, CdNativeFile.R1PCM),
        Map.entry(CdCommand.R3BMUST, CdNativeFile.R3PCM),
        Map.entry(CdCommand.R4BMUST, CdNativeFile.R4PCM),
        Map.entry(CdCommand.R5BMUST, CdNativeFile.R5PCM),
        Map.entry(CdCommand.R6BMUST, CdNativeFile.R6PCM),
        Map.entry(CdCommand.R7BMUST, CdNativeFile.R7PCM),
        Map.entry(CdCommand.R8BMUST, CdNativeFile.R8PCM)
    );

    public enum CommandDisposition {
        STAGE_LOAD, DEMO_LOAD, PROGRAM_LOAD, UNUSED_PAYLOAD_ALIAS,
        AUDIO, BACKUP_RAM, SPECIAL_STAGE_FLAGS, INVALID
    }

    public enum FileDisposition {
        SPX_LOAD_COMPONENT, STREAMED_BY_PROGRAM, NO_SPX_LOAD_BINDING, UNUSED_PROTOTYPE_PROGRAM
    }

    public CdProgramCatalogue {
        Objects.requireNonNull(release, "release");
    }

    public static CdProgramCatalogue forRelease(CdReleaseVariant release) {
        return new CdProgramCatalogue(release);
    }

    public List<CdStagePayload> stages() { return STAGES; }
    public List<CdNativeFile> files() { return FILES; }
    public List<CdCommand> commands() { return COMMANDS; }
    public List<CdNativeProgram> programs() { return PROGRAMS; }

    public CdStagePayload stage(CdStageKey key) {
        return CdStagePayload.valueOf(Objects.requireNonNull(key, "key").nativeId());
    }

    public CdStagePayload stage(String nativeId) {
        return stage(CdStageKey.parse(nativeId));
    }

    public Optional<CdStagePayload> stageForCommand(CdCommand command) {
        Objects.requireNonNull(command, "command");
        return STAGES.stream().filter(stage -> stage.loadCommand() == command).findFirst();
    }

    public Optional<CdNativeProgram> programForCommand(CdCommand command) {
        return CdNativeProgram.forCommand(command);
    }

    public CommandDisposition disposition(CdCommand command) {
        Objects.requireNonNull(command, "command");
        if (stageForCommand(command).isPresent()) {
            return CommandDisposition.STAGE_LOAD;
        }
        var program = programForCommand(command);
        if (program.isPresent()) {
            if (program.get().payloadAliasOf().isPresent()) {
                return CommandDisposition.UNUSED_PAYLOAD_ALIAS;
            }
            return switch (command) {
                case R11ADEMO, R43CDEMO, R82ADEMO -> CommandDisposition.DEMO_LOAD;
                default -> CommandDisposition.PROGRAM_LOAD;
            };
        }
        return switch (command) {
            case INVALID_39 -> CommandDisposition.INVALID;
            case READSAVE, WRITESAVE, RDTEMPSAVE, WRTEMPSAVE -> CommandDisposition.BACKUP_RAM;
            case INITSS, INITSS2 -> CommandDisposition.SPECIAL_STAGE_FLAGS;
            case FADECDA, R1AMUS, R1CMUS, R1DMUS, R3AMUS, R3CMUS, R3DMUS,
                R4AMUS, R4CMUS, R4DMUS, R5AMUS, R5CMUS, R5DMUS, R6AMUS,
                R6CMUS, R6DMUS, R7AMUS, R7CMUS, R7DMUS, R8AMUS, R8CMUS,
                R8DMUS, BOSSMUS, FINALMUS, TITLEMUS, TMATKMUS, RESULTMUS, SHOESMUS,
                INVINCMUS, GMOVERMUS, SPECMUS, DAGRDNMUS, PROTOWARP, INTROMUS, ENDINGMUS,
                STOPCDDA, FUTURESFX, PASTSFX, ALRIGHTSFX, GIVEUPSFX, YESSFX, YEAHSFX,
                GIGGLESFX, YELPSFX, STOMPSFX, BUMPERSFX, PASTMUS, FADEPCM, STOPPCM,
                RESETVOL, PAUSEPCM, UNPAUSEPCM, BREAKSFX, R1AMUST, R1CMUST, R1DMUST,
                R3AMUST, R3CMUST, R3DMUST, R4AMUST, R4CMUST, R4DMUST, R5AMUST,
                R5CMUST, R5DMUST, R6AMUST, R6CMUST, R6DMUST, R7AMUST, R7CMUST,
                R7DMUST, R8AMUST, R8CMUST, R8DMUST, BOSSMUST, FINALMUST, TITLEMUST,
                TMATKMUST, RESULTMUST, SHOESMUST, INVINCMUST, GMOVERMUST, SPECMUST, DAGRDNMUST,
                PROTOWARPT, INTROMUST, ENDINGMUST, FUTURESFXT, PASTSFXT, ALRGHTSFXT, GIVEUPSFXT,
                YESSFXT, YEAHSFXT, GIGGLESFXT, YELPSFXT, STOMPSFXT, BUMPERSFXT, R1BMUST,
                R3BMUST, R4BMUST, R5BMUST, R6BMUST, R7BMUST, R8BMUST, PAUSECDA,
                UNPAUSECDA -> CommandDisposition.AUDIO;
            default -> throw new IllegalStateException("Uncatalogued native command: " + command);
        };
    }

    /**
     * Source load targets, not an instruction to execute a command. INVALID_39 retains
     * its invalid disposition despite indexing R11A for both level and PCM targets.
     * SPCmd_LoadLevel, .LevelFiles and .PCMDrivers (SPX.asm:745-1085) own that fallback.
     * Sound-test targets are potential dependencies. SPCmd_TestFutureSFX through
     * SPCmd_TestR8BMus (SPX.asm:2283-2447) call LoadPCMDriver (SPX.asm:2456-2464),
     * which calls LoadFile only when the file-name pointer differs from curPCMDriver.
     * This metadata neither tracks that cache nor guarantees a read on every call.
     */
    public List<CdNativeFile> nativeLoadFiles(CdCommand command) {
        Objects.requireNonNull(command, "command");
        if (command == CdCommand.INVALID_39) {
            return List.of(CdNativeFile.R11A, CdNativeFile.R11A);
        }
        var stage = stageForCommand(command);
        if (stage.isPresent()) {
            return List.of(stage.get().file(), stage.get().pcmDriver());
        }
        var pcmDriver = SOUND_TEST_PCM_DRIVERS.get(command);
        if (pcmDriver != null) {
            return List.of(pcmDriver);
        }
        return programForCommand(command).map(CdNativeProgram::loadFiles).orElse(List.of());
    }

    /**
     * NO_SPX_LOAD_BINDING records an absent loader reference, not global unreachability.
     * File_TimeAttackSub and File_FinalPCM (SPX.asm:51,217) have no loader uses;
     * SPCmd_LoadTimeAttack (SPX.asm:1342-1357) loads only the main program.
     * File_Dummy5-9 (SPX.asm:257-266) and make.sh:353-357 describe unused prototypes.
     */
    public FileDisposition fileDisposition(CdNativeFile file) {
        Objects.requireNonNull(file, "file");
        return switch (file) {
            case TIMEATKSUB, FINALPCM -> FileDisposition.NO_SPX_LOAD_BINDING;
            case DUMMY5, DUMMY6, DUMMY7, DUMMY8, DUMMY9 -> FileDisposition.UNUSED_PROTOTYPE_PROGRAM;
            case PENCILSTM, OPENSTM, BADENDSTM, GOODENDSTM -> FileDisposition.STREAMED_BY_PROGRAM;
            default -> FileDisposition.SPX_LOAD_COMPONENT;
        };
    }
}
