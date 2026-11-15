package com.openggf.game.soniccd;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Nonstage loader bindings, including demos and unused payload aliases.
 * These are source dispositions, not claims of implemented behavior or native reachability.
 * SPCmd_LoadPencilTest through SPCmd_LoadSpecStage
 * (docs/scddisasm/src/CD System Program/SPX.asm:555-1527) own the load sequences.
 */
public enum CdNativeProgram {
    MDINIT(CdCommand.MDINIT, CdNativeFile.MDINIT),
    STAGESEL(CdCommand.STAGESEL, CdNativeFile.STAGESEL),
    TITLE(CdCommand.TITLE, CdNativeFile.TITLEMAIN, CdNativeFile.TITLESUB),
    WARP(CdCommand.WARP, CdNativeFile.WARP),
    TIMEATK(CdCommand.TIMEATK, CdNativeFile.TIMEATKMAIN),
    IPX(CdCommand.IPX, CdNativeFile.IPX),
    R43CDEMO(CdCommand.R43CDEMO, CdNativeFile.R43CDEMO, CdNativeFile.BOSSPCM),
    R82ADEMO(CdCommand.R82ADEMO, CdNativeFile.R82ADEMO, CdNativeFile.R8PCM),
    SNDTEST(CdCommand.SNDTEST, CdNativeFile.SNDTEST),
    SPECSTAGE(CdCommand.SPECSTAGE, CdNativeFile.SPECMAIN, CdNativeFile.SPECSUB),
    DAGARDEN(CdCommand.DAGARDEN, CdNativeFile.DAGARDMAIN, CdNativeFile.DAGARDDATA, CdNativeFile.DAGARDSUB),
    R11ADEMO(CdCommand.R11ADEMO, CdNativeFile.R11ADEMO, CdNativeFile.R1PCM),
    VISMODE(CdCommand.VISMODE, CdNativeFile.VISMODE),
    BURAMINIT(CdCommand.BURAMINIT, CdNativeFile.BURAMINIT, CdNativeFile.BURAMSUB),
    THANKYOU(CdCommand.THANKYOU, CdNativeFile.THANKSMAIN, CdNativeFile.THANKSDATA, CdNativeFile.THANKSSUB),
    BURAMMGR(CdCommand.BURAMMGR, CdNativeFile.BURAMMAIN, CdNativeFile.BURAMSUB),
    BADEND(CdCommand.BADEND, CdNativeFile.ENDMAIN, CdNativeFile.BADENDSUB),
    GOODEND(CdCommand.GOODEND, CdNativeFile.ENDMAIN, CdNativeFile.GOODENDSUB),
    FUNISINF(CdCommand.FUNISINF, CdNativeFile.FUNISINF),
    SS8CREDS(CdCommand.SS8CREDS, CdNativeFile.SS8CREDS),
    MCSONIC(CdCommand.MCSONIC, CdNativeFile.MCSONIC),
    TAILS(CdCommand.TAILS, CdNativeFile.TAILS),
    BATMAN(CdCommand.BATMAN, CdNativeFile.BATMAN),
    CUTESONIC(CdCommand.CUTESONIC, CdNativeFile.CUTESONIC),
    STAFFTIMES(CdCommand.STAFFTIMES, CdNativeFile.STAFFTIMES),
    DUMMY1(CdCommand.DUMMY1, CdNativeFile.MCSONIC),
    DUMMY2(CdCommand.DUMMY2, CdNativeFile.MCSONIC),
    DUMMY3(CdCommand.DUMMY3, CdNativeFile.MCSONIC),
    DUMMY4(CdCommand.DUMMY4, CdNativeFile.MCSONIC),
    DUMMY5(CdCommand.DUMMY5, CdNativeFile.MCSONIC),
    PENCILTEST(CdCommand.PENCILTEST, CdNativeFile.PENCILMAIN, CdNativeFile.PENCILSUB),
    OPENING(CdCommand.OPENING, CdNativeFile.OPENMAIN, CdNativeFile.OPENSUB),
    COMINSOON(CdCommand.COMINSOON, CdNativeFile.COMINSOON);

    private final CdCommand command;
    private final List<CdNativeFile> loadFiles;

    CdNativeProgram(CdCommand command, CdNativeFile... loadFiles) {
        this.command = command;
        this.loadFiles = List.of(loadFiles);
    }

    public CdCommand command() {
        return command;
    }

    /** Direct loader components in native load order. PCM may already be resident. */
    public List<CdNativeFile> loadFiles() {
        return loadFiles;
    }

    /**
     * Streams opened by the sub-program, separately from the SPX loader.
     * Program start (src/FMV/Sub (Opening).asm:25-33, Sub (Pencil Test).asm:20-28,
     * Sub (Ending).asm:25-39) and make.sh:376-377 bind the ending DATAFILE values.
     * Paths are relative to docs/scddisasm.
     */
    public List<CdNativeFile> streamFiles() {
        return switch (this) {
            case OPENING -> List.of(CdNativeFile.OPENSTM);
            case BADEND -> List.of(CdNativeFile.BADENDSTM);
            case GOODEND -> List.of(CdNativeFile.GOODENDSTM);
            case PENCILTEST -> List.of(CdNativeFile.PENCILSTM);
            default -> List.of();
        };
    }

    /**
     * Same loaded payload does not imply identical command behavior.
     * SPCmd_LoadDummyFile1-5 (SPX.asm:1254-1288) omit MCSonic's music branch.
     */
    public Optional<CdNativeProgram> payloadAliasOf() {
        return switch (this) {
            case DUMMY1, DUMMY2, DUMMY3, DUMMY4, DUMMY5 -> Optional.of(MCSONIC);
            default -> Optional.empty();
        };
    }

    public static Optional<CdNativeProgram> forCommand(CdCommand command) {
        Objects.requireNonNull(command, "command");
        for (CdNativeProgram program : values()) {
            if (program.command == command) {
                return Optional.of(program);
            }
        }
        return Optional.empty();
    }
}
