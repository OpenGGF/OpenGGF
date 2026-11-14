package com.openggf.game.timing;

/**
 * Hardware work classes. The first three may have their final readiness recorded for replay; the
 * Sonic CD kinds are normal-play load pacing only and never appear in a trace stream (contract
 * section "2026-11-13 extension: Sonic CD normal-play load kinds",
 * docs/architecture/designs/2026-07-27-cross-game-hardware-timing-trace-contract.md).
 */
public enum HardwareWorkKind {
    KOS_MODULE_QUEUE,
    KOS_DECOMPRESSION_QUEUE,
    /**
     * Sonic 1's {@code RunPLC} arming edge (docs/s1disasm/sonic.asm:1379).
     * The recorded readiness is the moment the ROM accepts the Nemesis PLC
     * FIFO head for decompression, not the delivery of any decoded art --
     * that payload stays owned by the production PLC pipeline.
     */
    NEMESIS_PLC_QUEUE,
    /**
     * A Sonic CD stage load: {@code LevelStart} from {@code .ClearFlowers} to {@code Level_MainLoop}'s first
     * {@code VSync} (Level/Main.asm:182-333), keyed by the stage program file and the art list
     * {@code LoadLevelData} queues for the entry. Normal-play pacing only.
     */
    SCD_STAGE_LOAD,
    /**
     * A Sonic CD video stream read's readiness: {@code ROMREADN} to the first sector
     * ({@code ReadFMVSectors}, CD System Program/SP.asm:720-757), keyed by file position and sector count.
     * Normal-play pacing only.
     */
    CD_READ_READY;

    /**
     * Whether a trace stream may carry this kind's readiness. The Sonic CD kinds may not: trace replay
     * never admits them, and recorded admission keeps them live.
     */
    public boolean recordable() {
        return this != SCD_STAGE_LOAD && this != CD_READ_READY;
    }

    public static HardwareWorkKind fromWireName(String wireName) {
        if ("kos_module_queue".equals(wireName)) {
            return KOS_MODULE_QUEUE;
        }
        if ("kos_decompression_queue".equals(wireName)) {
            return KOS_DECOMPRESSION_QUEUE;
        }
        if ("nemesis_plc_queue".equals(wireName)) {
            return NEMESIS_PLC_QUEUE;
        }
        if ("scd_stage_load".equals(wireName)) {
            return SCD_STAGE_LOAD;
        }
        if ("cd_read_ready".equals(wireName)) {
            return CD_READ_READY;
        }
        throw new IllegalArgumentException("unknown kind: " + wireName);
    }
}
