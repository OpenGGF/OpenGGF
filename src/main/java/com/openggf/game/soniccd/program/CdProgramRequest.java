package com.openggf.game.soniccd.program;

import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.CdStagePayload;

import java.util.Objects;

/**
 * The next program {@link CdProgramFlow} asks for. The scene coordinator turns it into a mode
 * request; {@code GameLoop} commits the mode change.
 */
public sealed interface CdProgramRequest {
    /** IPX {@code .GameLoop}: run the title screen (IPX.asm:50-75). */
    record Title() implements CdProgramRequest { }

    /** Run the stage-select secret program STSEL_.MMD ({@code StageSelect}, IPX.asm:710-728). */
    record StageSelect() implements CdProgramRequest { }

    /** Run one stage program. */
    record Stage(CdStagePayload payload, Route route) implements CdProgramRequest {
        public Stage {
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(route, "route");
        }
    }

    /**
     * A native program this build does not host. The flow records what native would run and
     * continues where native continues after that program returns.
     */
    record NotHosted(CdNativeProgram program, String nativeCaller, CdProgramRequest then) implements CdProgramRequest {
        public NotHosted {
            Objects.requireNonNull(program, "program");
            Objects.requireNonNull(nativeCaller, "nativeCaller");
            Objects.requireNonNull(then, "then");
        }
    }

    /**
     * Run the special stage program ({@code SCMD_SPECSTAGE}) with this entry, then continue with
     * {@code then} once it returns ({@link CdProgramFlow#specialStageEnded}).
     */
    record SpecialStage(com.openggf.game.soniccd.special.CdSpecialStageEntry entry, String nativeCaller,
                        CdProgramRequest then) implements CdProgramRequest {
        public SpecialStage {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(nativeCaller, "nativeCaller");
            Objects.requireNonNull(then, "then");
        }
    }

    // --- lane END: the ending replaces NotHosted(GOODEND/BADEND)
    /**
     * Run the ending program ({@code GoodEnding}/{@code BadEnding}, IPX.asm:312-326: {@code SCMD_GOODEND} or
     * {@code SCMD_BADEND}, again while {@code mmdReturnCode} is negative), then continue with {@code then}.
     */
    record Ending(boolean good, String nativeCaller, CdProgramRequest then) implements CdProgramRequest {
        public Ending {
            Objects.requireNonNull(nativeCaller, "nativeCaller");
            Objects.requireNonNull(then, "then");
        }
    }
    // --- end lane END

    /** Which IPX caller ran the stage, which decides what happens when it returns. */
    enum Route {
        /** {@code NewGame}/{@code LoadGame} through {@code RunLevel}/{@code RunBossLevel}. */
        CAMPAIGN,
        /** {@code StageSelect}: one {@code RunMMD}, then back to the title. */
        STAGE_SELECT,
        /** {@code TimeAttack} (IPX.asm:1214-1238): one {@code RunMMD} with {@code timeAttackMode} set, then the menu again. */
        TIME_ATTACK
    }
}
