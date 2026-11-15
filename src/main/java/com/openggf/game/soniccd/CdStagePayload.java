package com.openggf.game.soniccd;

/**
 * The finite stage payload set. Demos and special stages have distinct identities.
 * SPCmd_LoadLevel and .LevelFiles (SPX.asm:745-929) select files;
 * .PCMDrivers (SPX.asm:935-1085) selects the driver, including R33C/D's R3 driver.
 */
public enum CdStagePayload {
    R11A(CdNativeFile.R1PCM),
    R11B(CdNativeFile.R1PCM),
    R11C(CdNativeFile.R1PCM),
    R11D(CdNativeFile.R1PCM),
    R12A(CdNativeFile.R1PCM),
    R12B(CdNativeFile.R1PCM),
    R12C(CdNativeFile.R1PCM),
    R12D(CdNativeFile.R1PCM),
    R31A(CdNativeFile.R3PCM),
    R31B(CdNativeFile.R3PCM),
    R31C(CdNativeFile.R3PCM),
    R31D(CdNativeFile.R3PCM),
    R32A(CdNativeFile.R3PCM),
    R32B(CdNativeFile.R3PCM),
    R32C(CdNativeFile.R3PCM),
    R32D(CdNativeFile.R3PCM),
    R33C(CdNativeFile.R3PCM),
    R33D(CdNativeFile.R3PCM),
    R13C(CdNativeFile.BOSSPCM),
    R13D(CdNativeFile.BOSSPCM),
    R41A(CdNativeFile.R4PCM),
    R41B(CdNativeFile.R4PCM),
    R41C(CdNativeFile.R4PCM),
    R41D(CdNativeFile.R4PCM),
    R42A(CdNativeFile.R4PCM),
    R42B(CdNativeFile.R4PCM),
    R42C(CdNativeFile.R4PCM),
    R42D(CdNativeFile.R4PCM),
    R43C(CdNativeFile.BOSSPCM),
    R43D(CdNativeFile.BOSSPCM),
    R51A(CdNativeFile.R5PCM),
    R51B(CdNativeFile.R5PCM),
    R51C(CdNativeFile.R5PCM),
    R51D(CdNativeFile.R5PCM),
    R52A(CdNativeFile.R5PCM),
    R52B(CdNativeFile.R5PCM),
    R52C(CdNativeFile.R5PCM),
    R52D(CdNativeFile.R5PCM),
    R53C(CdNativeFile.BOSSPCM),
    R53D(CdNativeFile.BOSSPCM),
    R61A(CdNativeFile.R6PCM),
    R61B(CdNativeFile.R6PCM),
    R61C(CdNativeFile.R6PCM),
    R61D(CdNativeFile.R6PCM),
    R62A(CdNativeFile.R6PCM),
    R62B(CdNativeFile.R6PCM),
    R62C(CdNativeFile.R6PCM),
    R62D(CdNativeFile.R6PCM),
    R63C(CdNativeFile.BOSSPCM),
    R63D(CdNativeFile.BOSSPCM),
    R71A(CdNativeFile.R7PCM),
    R71B(CdNativeFile.R7PCM),
    R71C(CdNativeFile.R7PCM),
    R71D(CdNativeFile.R7PCM),
    R72A(CdNativeFile.R7PCM),
    R72B(CdNativeFile.R7PCM),
    R72C(CdNativeFile.R7PCM),
    R72D(CdNativeFile.R7PCM),
    R73C(CdNativeFile.BOSSPCM),
    R73D(CdNativeFile.BOSSPCM),
    R81A(CdNativeFile.R8PCM),
    R81B(CdNativeFile.R8PCM),
    R81C(CdNativeFile.R8PCM),
    R81D(CdNativeFile.R8PCM),
    R82A(CdNativeFile.R8PCM),
    R82B(CdNativeFile.R8PCM),
    R82C(CdNativeFile.R8PCM),
    R82D(CdNativeFile.R8PCM),
    R83C(CdNativeFile.BOSSPCM),
    R83D(CdNativeFile.BOSSPCM);

    private final CdStageKey stage;
    private final CdNativeFile file;
    private final CdCommand loadCommand;
    private final CdNativeFile pcmDriver;

    CdStagePayload(CdNativeFile pcmDriver) {
        this.stage = CdStageKey.parse(name());
        this.file = CdNativeFile.valueOf(name());
        this.loadCommand = CdCommand.valueOf(name());
        this.pcmDriver = pcmDriver;
    }

    public CdStageKey stage() { return stage; }
    public CdNativeFile file() { return file; }
    public CdCommand loadCommand() { return loadCommand; }
    public CdNativeFile pcmDriver() { return pcmDriver; }
}
