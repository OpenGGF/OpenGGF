package com.openggf.game.sonic3k;

/**
 * Per-module model of the S3K cheat-unlock RAM bytes that persist for the
 * whole power cycle ({@code sonic3k.constants.asm:973-981}):
 * <pre>
 *   Level_select_flag  ds.b 1   ; $FFFFFFE0
 *   Slow_motion_flag   ds.b 1   ; $FFFFFFE1
 *   Debug_cheat_flag   ds.w 1   ; $FFFFFFE2, set if the debug cheat's been entered
 * </pre>
 *
 * <p>The shipped locked-on ROM never clears these outside power-on RAM
 * initialisation (the {@code Title_Screen} and {@code loc_7BE4} writers sit
 * inside {@code if 0} blocks and {@code S3_Level_Select_Code} is an {@code rts}),
 * so the engine does not clear them on level load either; {@link #reset()}
 * exists for the rewind registry's missing-snapshot path and tests.
 *
 * <p>Writers in the locked-on ROM:
 * <ul>
 *   <li>{@code AIZRideVineHandle_CheckButtonSequence} ({@code sonic3k.asm:46560-46586})
 *       stores 1 to {@code Level_select_flag} and {@code Slow_motion_flag} after
 *       L,L,L,R,R,R,U,U,U on the AIZ1 vine handle.</li>
 *   <li>{@code sub_3E598} ({@code sonic3k.asm:82622-82653}), the MHZ pulley-lift
 *       handle, stores 1 to both bytes of {@code Debug_cheat_flag} after the same
 *       sequence when {@code tst.w (Level_select_flag)} is already nonzero.</li>
 * </ul>
 * Readers: {@code Obj_TitleSelection_Main} ({@code tst.b Level_select_flag}),
 * the title-screen level-select entry, {@code LevelSelect_CheckKnuckles}
 * ({@code tst.w Debug_cheat_flag}), the level-start {@code Debug_mode_flag}
 * write ({@code sonic3k.asm:7635-7638}) and the sound-test extras
 * ({@code loc_663A}, {@code loc_85F4}).
 */
public final class Sonic3kCheatFlags {

    /** {@code Level_select_flag} byte. */
    private boolean levelSelect;
    /** {@code Slow_motion_flag} byte. */
    private boolean slowMotion;
    /** {@code Debug_cheat_flag} word; every ROM writer stores {@code $0101}. */
    private boolean debugCheat;

    public Sonic3kCheatFlags() {
    }

    /** Mirrors {@code tst.b (Level_select_flag).w}. */
    public boolean isLevelSelectEnabled() {
        return levelSelect;
    }

    /** Mirrors {@code tst.b (Slow_motion_flag).w}. */
    public boolean isSlowMotionEnabled() {
        return slowMotion;
    }

    /**
     * Mirrors {@code tst.w (Level_select_flag).w}: the word spans the
     * {@code Level_select_flag} and {@code Slow_motion_flag} bytes, so either
     * byte being set reads as nonzero.
     */
    public boolean isLevelSelectWordSet() {
        return levelSelect || slowMotion;
    }

    /** Mirrors {@code tst.b}/{@code tst.w (Debug_cheat_flag).w}. */
    public boolean isDebugCheatEntered() {
        return debugCheat;
    }

    /**
     * Mirrors the AIZ vine / S&amp;K-alone pulley write
     * {@code move.b d1,(a4); move.b d1,Slow_motion_flag-Level_select_flag(a4)}
     * with {@code d1 = 1} and {@code a4 = Level_select_flag}.
     */
    public void enableLevelSelectAndSlowMotion() {
        levelSelect = true;
        slowMotion = true;
    }

    /**
     * Mirrors the locked-on pulley write {@code move.b d1,(a4); move.b d1,1(a4)}
     * with {@code d1 = 1} and {@code a4 = Debug_cheat_flag}, i.e. the word
     * becomes {@code $0101}.
     */
    public void enableDebugCheat() {
        debugCheat = true;
    }

    /** Clears every flag, matching power-on RAM initialisation. */
    public void reset() {
        levelSelect = false;
        slowMotion = false;
        debugCheat = false;
    }

    /** Captures the three flags for rewind restore. See {@link Sonic3kCheatFlagsRewindAdapter}. */
    public Snapshot captureRewindState() {
        return new Snapshot(levelSelect, slowMotion, debugCheat);
    }

    /** Restores the three flags from a rewind snapshot; a missing snapshot clears them. */
    public void restoreRewindState(Snapshot snapshot) {
        if (snapshot == null) {
            reset();
            return;
        }
        levelSelect = snapshot.levelSelect();
        slowMotion = snapshot.slowMotion();
        debugCheat = snapshot.debugCheat();
    }

    public record Snapshot(boolean levelSelect, boolean slowMotion, boolean debugCheat) {
    }
}
