package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.game.soniccd.zones.qqz.QqzScrollHandler;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

/**
 * Conveyor switch, object $31 ({@code ConveyorSwitchObject}, objects/r5/conveyor_switch.asm:3-9): a panel
 * that reverses the stage's conveyors. Standing within 8 pixels of it horizontally and 16 vertically
 * toggles bit 0 of {@code r5_conveyor_reverse} and plays sound {@code $BF} (routine 2, :36-62); a switch that
 * sees the flag change without being touched also runs the press animation (:68-77). The flag is read by
 * the player's block check ({@code loc_2068AA}, src/r5/player_block_check.asm:308-339, in
 * {@code QqzPlayerProfile}) and by the palette cycle (r5/palette_cycle_a.asm:21-24).
 *
 * <p><b>Where the flag lives:</b> {@link QqzScrollHandler#conveyorReverse()} (the typed input the zone's
 * palette cycle already reads), captured and restored by that handler's rewind state. It is cleared by the
 * handler's creation at stage load ({@code clr.b r5_conveyor_reverse}, src/r5/main.asm:24).
 *
 * <p>Routines (off_20CE34, :13-18): 0 init (flows into 2; the frame is 2 when the flag is set), 2 wait for
 * the player, 4 animate for 14 frames (frame 1 at the 7th, then the settled frame 0 or 2), 6 wait for the
 * player to leave ({@code cmpi.b} on the low byte of the distance, :115-126, as assembled). DrawObject and
 * CheckObjectDespawn run after every routine (:7-9). Shipped {@code FixBugs = 0}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjConveyorSwitch extends QqzObject implements ObjectVisualFacing.NonDirectional {
    private int seenReverse;       // oVar3C
    private int remoteMarker;      // oVar3D
    private int pressTimer;        // oVar3A

    public ObjConveyorSwitch(ObjectSpawn spawn) {
        super(spawn, "Conveyor switch");
    }

    @Override protected String sheetKind() { return QqzObjectArt.CONVEYOR_SWITCH; }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return ObjectVisualFacing.Basis.NON_DIRECTIONAL;
    }

    /** The scroll handler that owns {@code r5_conveyor_reverse}, or {@code null} outside a Quartz Quadrant stage. */
    private QqzScrollHandler state() {
        var level = cdLevel();
        var parallax = services().parallaxManager();
        return level != null && parallax != null
                && parallax.getHandler(SonicCdZoneRegistry.zoneIndex(level.payload())) instanceof QqzScrollHandler h ? h : null;
    }

    private boolean reverse() {
        QqzScrollHandler state = state();
        return state != null && state.conveyorReverse();
    }

    @Override
    protected void run(PlayableEntity player) {
        if (standDownInBossAct()) return;
        switch (routine) {
            case 0 -> { init(); waiting(player); }           // Routine0 falls into Routine2
            case 2 -> waiting(player);
            case 4 -> pressing();
            case 6 -> leaving(player);
            default -> { }
        }
    }

    /** ConveyorSwitchObject_0_Routine0 (objects/r5/conveyor_switch.asm:21-34). */
    private void init() {
        routine += 2;
        priority = 3;
        width = 0x18;
        yRadius = 0x18;
        tileWord = QqzObjectArt.CONVEYOR_SWITCH_TILE;
        if (reverse()) mapFrame = 2;
        seenReverse = reverse() ? 1 : 0;
        initialised = true;
    }

    /** ConveyorSwitchObject_0_Routine2 (objects/r5/conveyor_switch.asm:36-77). */
    private void waiting(PlayableEntity player) {
        if (player != null && !player.isDebugMode()
                && absDiff(centreY(player), y) < 0x10 && absDiff(centreX(player), x) < 8) {
            QqzScrollHandler state = state();
            if (state != null) state.setConveyorReverse(!state.conveyorReverse());   // bchg #0,r5_conveyor_reverse
            routine += 2;
            if (onScreen()) services().playSfx(0xBF);        // tst.b obj.sprite_flags(a0); bpl.s; move.w #$BF,d0
            return;
        }
        int now = reverse() ? 1 : 0;                         // loc_20CECC
        if (now != seenReverse) {
            remoteMarker = 0xFF;
            routine += 2;
        }
        seenReverse = now;
    }

    /** ConveyorSwitchObject_0_Routine4 (objects/r5/conveyor_switch.asm:81-104). */
    private void pressing() {
        pressTimer = (pressTimer + 1) & 0xFF;
        if (pressTimer == 7) {
            mapFrame = 1;
            return;
        }
        if (pressTimer != 0xE) return;
        pressTimer = 0;
        mapFrame = reverse() ? 2 : 0;
        routine += 2;
        remoteMarker = 0;
    }

    /** ConveyorSwitchObject_0_Routine6 (objects/r5/conveyor_switch.asm:108-133): the byte compares are as assembled. */
    private void leaving(PlayableEntity player) {
        if (player != null && (absDiff(centreY(player), y) & 0xFF) < 0x20
                && (absDiff(centreX(player), x) & 0xFF) < 0x10) {
            return;
        }
        routine = 2;
        seenReverse = reverse() ? 1 : 0;
    }

    int remoteMarker() { return remoteMarker; }
}
