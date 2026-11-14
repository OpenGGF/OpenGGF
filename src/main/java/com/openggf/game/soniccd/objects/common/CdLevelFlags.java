package com.openggf.game.soniccd.objects.common;

/**
 * Level flags the common objects read and write (Main CPU Variables.inc / Level/_Definitions.inc):
 * {@code ctrlLocked}, {@code scrollLock}, {@code goodFuture}, {@code specialStage} and
 * {@code timeOver}. One instance per loaded stage, on {@code CdLevel}.
 *
 * <p>{@code ctrlLocked} stops ObjSonic copying the pad into {@code playerCtrl}
 * (Sonic/Main.asm:297-299) and {@code scrollLock} stops the zone scroll (Palmtree Panic/Scroll
 * (Act 1 Present).asm:178) and selects the large PLC batch (Functions (General).asm:721-726).
 * The player and camera owners do not read them yet; the PLC wait does. Not rewind-captured, except
 * {@link #bossFlags} ({@code CdBossFlagsRewind}).
 */
public final class CdLevelFlags {
    public boolean ctrlLocked;
    public boolean scrollLock;
    public boolean goodFuture;
    public boolean specialStage;
    public boolean timeOver;
    /** The player's forced pad word while {@code ctrlLocked} ({@code playerCtrlHold}). */
    public int playerCtrlHold;
    /**
     * {@code bossFlags} ({@code boss_flags}, Level/_Definitions.inc:314-316): level RAM, cleared with the level. Tidal
     * Tempest keeps it here (its writer deletes itself, so no boss object can carry it); the other zones' boss objects
     * still hold their own. Rewind-captured by {@code CdBossFlagsRewind}.
     */
    public int bossFlags;

    public CdLevelFlags(boolean goodFuture) {
        this.goodFuture = goodFuture;
    }
}
