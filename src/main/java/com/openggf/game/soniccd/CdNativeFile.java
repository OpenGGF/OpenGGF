package com.openggf.game.soniccd;

import java.util.Optional;

/**
 * Native file slots, not independent game programs.
 * FID_R11A through FID_R82ADEMO (System.inc:12-138) and
 * File_R11A through File_Demo82A (SPX.asm:21-274) own the numbering and names.
 * Run tools/soniccd/catalogue.py --check to compare with the pinned source.
 */
public enum CdNativeFile {
    R11A(0, "R11A__.MMD;1", Kind.STAGE_PROGRAM),
    R11B(1, "R11B__.MMD;1", Kind.STAGE_PROGRAM),
    R11C(2, "R11C__.MMD;1", Kind.STAGE_PROGRAM),
    R11D(3, "R11D__.MMD;1", Kind.STAGE_PROGRAM),
    MDINIT(4, "MDINIT.MMD;1", Kind.MAIN_CPU_PROGRAM),
    SNDTEST(5, "SOSEL_.MMD;1", Kind.MAIN_CPU_PROGRAM),
    STAGESEL(6, "STSEL_.MMD;1", Kind.MAIN_CPU_PROGRAM),
    R12A(7, "R12A__.MMD;1", Kind.STAGE_PROGRAM),
    R12B(8, "R12B__.MMD;1", Kind.STAGE_PROGRAM),
    R12C(9, "R12C__.MMD;1", Kind.STAGE_PROGRAM),
    R12D(10, "R12D__.MMD;1", Kind.STAGE_PROGRAM),
    TITLEMAIN(11, "TITLEM.MMD;1", Kind.MAIN_CPU_PROGRAM),
    TITLESUB(12, "TITLES.BIN;1", Kind.SUB_CPU_PROGRAM),
    WARP(13, "WARP__.MMD;1", Kind.MAIN_CPU_PROGRAM),
    TIMEATKMAIN(14, "ATTACK.MMD;1", Kind.MAIN_CPU_PROGRAM),
    TIMEATKSUB(15, "ATTACK.BIN;1", Kind.SUB_CPU_PROGRAM),
    IPX(16, "IPX___.MMD;1", Kind.MAIN_CPU_PROGRAM),
    PENCILSTM(17, "PTEST.STM;1", Kind.VIDEO_STREAM),
    OPENSTM(18, "OPN.STM;1", Kind.VIDEO_STREAM),
    BADENDSTM(19, "BADEND.STM;1", Kind.VIDEO_STREAM),
    GOODENDSTM(20, "GOODEND.STM;1", Kind.VIDEO_STREAM),
    OPENMAIN(21, "OPEN_M.MMD;1", Kind.MAIN_CPU_PROGRAM),
    OPENSUB(22, "OPEN_S.BIN;1", Kind.SUB_CPU_PROGRAM),
    COMINSOON(23, "COME__.MMD;1", Kind.MAIN_CPU_PROGRAM),
    DAGARDMAIN(24, "PLANET_M.MMD;1", Kind.MAIN_CPU_PROGRAM),
    DAGARDSUB(25, "PLANET_S.BIN;1", Kind.SUB_CPU_PROGRAM),
    R31A(26, "R31A__.MMD;1", Kind.STAGE_PROGRAM),
    R31B(27, "R31B__.MMD;1", Kind.STAGE_PROGRAM),
    R31C(28, "R31C__.MMD;1", Kind.STAGE_PROGRAM),
    R31D(29, "R31D__.MMD;1", Kind.STAGE_PROGRAM),
    R32A(30, "R32A__.MMD;1", Kind.STAGE_PROGRAM),
    R32B(31, "R32B__.MMD;1", Kind.STAGE_PROGRAM),
    R32C(32, "R32C__.MMD;1", Kind.STAGE_PROGRAM),
    R32D(33, "R32D__.MMD;1", Kind.STAGE_PROGRAM),
    R33C(34, "R33C__.MMD;1", Kind.STAGE_PROGRAM),
    R33D(35, "R33D__.MMD;1", Kind.STAGE_PROGRAM),
    R13C(36, "R13C__.MMD;1", Kind.STAGE_PROGRAM),
    R13D(37, "R13D__.MMD;1", Kind.STAGE_PROGRAM),
    R41A(38, "R41A__.MMD;1", Kind.STAGE_PROGRAM),
    R41B(39, "R41B__.MMD;1", Kind.STAGE_PROGRAM),
    R41C(40, "R41C__.MMD;1", Kind.STAGE_PROGRAM),
    R41D(41, "R41D__.MMD;1", Kind.STAGE_PROGRAM),
    R42A(42, "R42A__.MMD;1", Kind.STAGE_PROGRAM),
    R42B(43, "R42B__.MMD;1", Kind.STAGE_PROGRAM),
    R42C(44, "R42C__.MMD;1", Kind.STAGE_PROGRAM),
    R42D(45, "R42D__.MMD;1", Kind.STAGE_PROGRAM),
    R43C(46, "R43C__.MMD;1", Kind.STAGE_PROGRAM),
    R43D(47, "R43D__.MMD;1", Kind.STAGE_PROGRAM),
    R51A(48, "R51A__.MMD;1", Kind.STAGE_PROGRAM),
    R51B(49, "R51B__.MMD;1", Kind.STAGE_PROGRAM),
    R51C(50, "R51C__.MMD;1", Kind.STAGE_PROGRAM),
    R51D(51, "R51D__.MMD;1", Kind.STAGE_PROGRAM),
    R52A(52, "R52A__.MMD;1", Kind.STAGE_PROGRAM),
    R52B(53, "R52B__.MMD;1", Kind.STAGE_PROGRAM),
    R52C(54, "R52C__.MMD;1", Kind.STAGE_PROGRAM),
    R52D(55, "R52D__.MMD;1", Kind.STAGE_PROGRAM),
    R53C(56, "R53C__.MMD;1", Kind.STAGE_PROGRAM),
    R53D(57, "R53D__.MMD;1", Kind.STAGE_PROGRAM),
    R61A(58, "R61A__.MMD;1", Kind.STAGE_PROGRAM),
    R61B(59, "R61B__.MMD;1", Kind.STAGE_PROGRAM),
    R61C(60, "R61C__.MMD;1", Kind.STAGE_PROGRAM),
    R61D(61, "R61D__.MMD;1", Kind.STAGE_PROGRAM),
    R62A(62, "R62A__.MMD;1", Kind.STAGE_PROGRAM),
    R62B(63, "R62B__.MMD;1", Kind.STAGE_PROGRAM),
    R62C(64, "R62C__.MMD;1", Kind.STAGE_PROGRAM),
    R62D(65, "R62D__.MMD;1", Kind.STAGE_PROGRAM),
    R63C(66, "R63C__.MMD;1", Kind.STAGE_PROGRAM),
    R63D(67, "R63D__.MMD;1", Kind.STAGE_PROGRAM),
    R71A(68, "R71A__.MMD;1", Kind.STAGE_PROGRAM),
    R71B(69, "R71B__.MMD;1", Kind.STAGE_PROGRAM),
    R71C(70, "R71C__.MMD;1", Kind.STAGE_PROGRAM),
    R71D(71, "R71D__.MMD;1", Kind.STAGE_PROGRAM),
    R72A(72, "R72A__.MMD;1", Kind.STAGE_PROGRAM),
    R72B(73, "R72B__.MMD;1", Kind.STAGE_PROGRAM),
    R72C(74, "R72C__.MMD;1", Kind.STAGE_PROGRAM),
    R72D(75, "R72D__.MMD;1", Kind.STAGE_PROGRAM),
    R73C(76, "R73C__.MMD;1", Kind.STAGE_PROGRAM),
    R73D(77, "R73D__.MMD;1", Kind.STAGE_PROGRAM),
    R81A(78, "R81A__.MMD;1", Kind.STAGE_PROGRAM),
    R81B(79, "R81B__.MMD;1", Kind.STAGE_PROGRAM),
    R81C(80, "R81C__.MMD;1", Kind.STAGE_PROGRAM),
    R81D(81, "R81D__.MMD;1", Kind.STAGE_PROGRAM),
    R82A(82, "R82A__.MMD;1", Kind.STAGE_PROGRAM),
    R82B(83, "R82B__.MMD;1", Kind.STAGE_PROGRAM),
    R82C(84, "R82C__.MMD;1", Kind.STAGE_PROGRAM),
    R82D(85, "R82D__.MMD;1", Kind.STAGE_PROGRAM),
    R83C(86, "R83C__.MMD;1", Kind.STAGE_PROGRAM),
    R83D(87, "R83D__.MMD;1", Kind.STAGE_PROGRAM),
    SPECMAIN(88, "SPMM__.MMD;1", Kind.MAIN_CPU_PROGRAM),
    SPECSUB(89, "SPSS__.BIN;1", Kind.SUB_CPU_PROGRAM),
    R1PCM(90, "SNCBNK1B.BIN;1", Kind.PCM_DRIVER),
    R3PCM(91, "SNCBNK3B.BIN;1", Kind.PCM_DRIVER),
    R4PCM(92, "SNCBNK4B.BIN;1", Kind.PCM_DRIVER),
    R5PCM(93, "SNCBNK5B.BIN;1", Kind.PCM_DRIVER),
    R6PCM(94, "SNCBNK6B.BIN;1", Kind.PCM_DRIVER),
    R7PCM(95, "SNCBNK7B.BIN;1", Kind.PCM_DRIVER),
    R8PCM(96, "SNCBNK8B.BIN;1", Kind.PCM_DRIVER),
    BOSSPCM(97, "SNCBNKB1.BIN;1", Kind.PCM_DRIVER),
    FINALPCM(98, "SNCBNKB2.BIN;1", Kind.PCM_DRIVER),
    DAGARDDATA(99, "PLANET_D.BIN;1", Kind.DATA),
    R11ADEMO(100, "DEMO11A.MMD;1", Kind.MAIN_CPU_PROGRAM),
    VISMODE(101, "VM____.MMD;1", Kind.MAIN_CPU_PROGRAM),
    BURAMINIT(102, "BRAMINIT.MMD;1", Kind.MAIN_CPU_PROGRAM),
    BURAMSUB(103, "BRAMSUB.BIN;1", Kind.SUB_CPU_PROGRAM),
    BURAMMAIN(104, "BRAMMAIN.MMD;1", Kind.MAIN_CPU_PROGRAM),
    THANKSMAIN(105, "THANKS_M.MMD;1", Kind.MAIN_CPU_PROGRAM),
    THANKSSUB(106, "THANKS_S.BIN;1", Kind.SUB_CPU_PROGRAM),
    THANKSDATA(107, "THANKS_D.BIN;1", Kind.DATA),
    ENDMAIN(108, "ENDING.MMD;1", Kind.MAIN_CPU_PROGRAM),
    BADENDSUB(109, "GOODEND.BIN;1", Kind.SUB_CPU_PROGRAM),
    GOODENDSUB(110, "BADEND.BIN;1", Kind.SUB_CPU_PROGRAM),
    FUNISINF(111, "NISI.MMD;1", Kind.MAIN_CPU_PROGRAM),
    SS8CREDS(112, "SPEEND.MMD;1", Kind.MAIN_CPU_PROGRAM),
    MCSONIC(113, "DUMMY0.MMD;1", Kind.MAIN_CPU_PROGRAM),
    TAILS(114, "DUMMY1.MMD;1", Kind.MAIN_CPU_PROGRAM),
    BATMAN(115, "DUMMY2.MMD;1", Kind.MAIN_CPU_PROGRAM),
    CUTESONIC(116, "DUMMY3.MMD;1", Kind.MAIN_CPU_PROGRAM),
    STAFFTIMES(117, "DUMMY4.MMD;1", Kind.MAIN_CPU_PROGRAM),
    DUMMY5(118, "DUMMY5.MMD;1", Kind.UNUSED_COPY),
    DUMMY6(119, "DUMMY6.MMD;1", Kind.UNUSED_COPY),
    DUMMY7(120, "DUMMY7.MMD;1", Kind.UNUSED_COPY),
    DUMMY8(121, "DUMMY8.MMD;1", Kind.UNUSED_COPY),
    DUMMY9(122, "DUMMY9.MMD;1", Kind.UNUSED_COPY),
    PENCILMAIN(123, "PTEST.MMD;1", Kind.MAIN_CPU_PROGRAM),
    PENCILSUB(124, "PTEST.BIN;1", Kind.SUB_CPU_PROGRAM),
    R43CDEMO(125, "DEMO43C.MMD;1", Kind.MAIN_CPU_PROGRAM),
    R82ADEMO(126, "DEMO82A.MMD;1", Kind.MAIN_CPU_PROGRAM);

    public enum Kind {
        STAGE_PROGRAM, MAIN_CPU_PROGRAM, SUB_CPU_PROGRAM, PCM_DRIVER, VIDEO_STREAM, DATA, UNUSED_COPY
    }

    private final int id;
    private final String isoName;
    private final Kind kind;

    CdNativeFile(int id, String isoName, Kind kind) {
        this.id = id;
        this.isoName = isoName;
        this.kind = kind;
    }

    public int id() { return id; }
    public String isoName() { return isoName; }
    public Kind kind() { return kind; }

    /** make.sh:355-357 copies DUMMY5.MMD to these four distinct native file slots. */
    public Optional<CdNativeFile> copySource() {
        return switch (this) {
            case DUMMY6, DUMMY7, DUMMY8, DUMMY9 -> Optional.of(DUMMY5);
            default -> Optional.empty();
        };
    }

    public static CdNativeFile fromId(int id) {
        for (CdNativeFile file : values()) {
            if (file.id == id) {
                return file;
            }
        }
        throw new IllegalArgumentException("Unknown CD file slot: " + id);
    }
}
