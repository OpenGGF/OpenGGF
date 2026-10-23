package com.openggf.game.sonic2.specialstage;

import com.openggf.physics.TrigLookupTable;
import com.openggf.data.Rom;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSlotPool.ArtRole.*;
import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSlotPool.Kind;

/**
 * Fixed Obj09/Obj10/Obj63/Obj88 presentation values published by a real
 * special-stage RunObjects pass. This adapter advances no player or animation
 * state, and retains only immutable native render values for rewind.
 */
public final class Sonic2SpecialStageNativePlayerPresentation {
    // loc_33D02 indexes this shipped-ROM byte table using abs(ss_x_pos)*128/ss_z_pos.
    // byte_33D32 (s2.asm:69587-69597), including the final padding byte.
    private static final int[] SHADOW_ANGLE = {
            0x40, 0x40, 0x40, 0x40, 0x41, 0x41, 0x41, 0x42,
            0x42, 0x42, 0x43, 0x43, 0x43, 0x44, 0x44, 0x44,
            0x45, 0x45, 0x45, 0x46, 0x46, 0x46, 0x47, 0x47,
            0x47, 0x48, 0x48, 0x48, 0x48, 0x49, 0x49, 0x49,
            0x4A, 0x4A, 0x4A, 0x4B, 0x4B, 0x4B, 0x4C, 0x4C,
            0x4C, 0x4D, 0x4D, 0x4D, 0x4E, 0x4E, 0x4E, 0x4F,
            0x4F, 0x50, 0x50, 0x50, 0x51, 0x51, 0x51, 0x52,
            0x52, 0x52, 0x53, 0x53, 0x53, 0x54, 0x54, 0x54,
            0x55, 0x55, 0x56, 0x56, 0x56, 0x57, 0x57, 0x57,
            0x58, 0x58, 0x59, 0x59, 0x59, 0x5A, 0x5A, 0x5B,
            0x5B, 0x5B, 0x5C, 0x5C, 0x5D, 0x5D, 0x5E, 0x5E,
            0x5E, 0x5F, 0x5F, 0x60, 0x60, 0x61, 0x61, 0x62,
            0x62, 0x63, 0x63, 0x64, 0x64, 0x65, 0x65, 0x66,
            0x66, 0x67, 0x67, 0x68, 0x68, 0x69, 0x6A, 0x6A,
            0x6B, 0x6C, 0x6C, 0x6D, 0x6E, 0x6E, 0x6F, 0x70,
            0x71, 0x72, 0x73, 0x74, 0x75, 0x77, 0x78, 0x7A,
            0x80, 0
    };

    // word_3417A (s2.asm:69999-70008): x, y, Obj63 mapping frame, x flip.
    // The mapping frame's remainder modulo 3 selects flat/diagonal/side art.
    private static final int[][] SHADOW_PRESENTATION = {
            {20, 20, 1, 1}, {0, 24, 0, 0}, {-20, 20, 1, 0}, {-20, 0, 2, 0},
            {-20, -20, 7, 0}, {0, -24, 9, 0}, {20, -20, 7, 1}, {20, 0, 2, 1}
    };

    private List<Sonic2SpecialStageSlotPool.RenderState> states = List.of();
    private Rom rom;

    /** Bind the stage's ROM data source; rewind restores values, not this service binding. */
    public void bindRom(Rom rom) {
        this.rom = rom;
    }

    /** Called after the existing player turn, before the ascending native child scan. */
    public void update(Sonic2SpecialStageSlotPool pool, Sonic2SpecialStagePlayer sonic,
                       Sonic2SpecialStagePlayer tails, int offsetX, int offsetY) {
        Sonic2SpecialStageSnapshot.PlayerSnapshot sonicState = sonic == null
                ? null : sonic.captureRewindSnapshot();
        Sonic2SpecialStageSnapshot.PlayerSnapshot tailsState = tails == null
                ? null : tails.captureRewindSnapshot();
        List<Sonic2SpecialStageSlotPool.RenderState> updated = new ArrayList<>(5);
        addBody(updated, pool.entryAt(0), Kind.SONIC, sonicState, SONIC);
        addBody(updated, pool.entryAt(1), Kind.TAILS, tailsState, TAILS);
        addShadow(updated, pool.entryAt(5), Kind.SONIC_SHADOW, sonicState, offsetX, offsetY, false);
        addShadow(updated, pool.entryAt(6), Kind.TAILS_SHADOW, tailsState, offsetX, offsetY, true);
        addTails(updated, pool.entryAt(7), tailsState);
        states = List.copyOf(updated);
    }

    private static void addBody(List<Sonic2SpecialStageSlotPool.RenderState> result,
                                Sonic2SpecialStageSlotPool.Entry entry, Kind kind,
                                Sonic2SpecialStageSnapshot.PlayerSnapshot player,
                                Sonic2SpecialStageSlotPool.ArtRole artRole) {
        if (entry == null || entry.kind() != kind || player == null) {
            return;
        }
        boolean visible = initialized(player) && (player.ssDplcTimer() & 1) == 0;
        result.add(new Sonic2SpecialStageSlotPool.RenderState(entry, (short) player.xPos(),
                (short) player.yPos(), player.mappingFrame(), player.renderXFlip(),
                player.renderYFlip(), visible, artRole));
    }

    /** Obj63 and loc_340CC/loc_3411A (s2.asm:69908-69987). */
    private void addShadow(List<Sonic2SpecialStageSlotPool.RenderState> result,
                                  Sonic2SpecialStageSlotPool.Entry entry, Kind kind,
                                  Sonic2SpecialStageSnapshot.PlayerSnapshot player,
                                  int offsetX, int offsetY, boolean tails) {
        if (entry == null || entry.kind() != kind || player == null) {
            return;
        }
        if (!initialized(player)) {
            result.add(new Sonic2SpecialStageSlotPool.RenderState(entry, 0, 0, 0,
                    false, false, false, SHADOW));
            return;
        }
        int angle;
        int x;
        int y;
        if (player.routine() == Sonic2SpecialStagePlayer.RoutineState.NORMAL) {
            angle = player.angle() & 0xFF;
            x = (short) player.xPos();
            y = (short) player.yPos();
        } else {
            angle = jumpShadowAngle(player);
            int z = (short) player.ssZPos();
            // FixBugs=0: the second MULS.W consumes the low WORD of cos*z,
            // including its sign. Do not widen it into one algebraic product.
            int scaledX = (short) (TrigLookupTable.cosHex(angle) * z) * 0xCC;
            x = (short) ((scaledX >> 16) + offsetX);
            y = (short) (((TrigLookupTable.sinHex(angle) * z) >> 8) + offsetY);
        }
        int index = ((angle - 0x10) & 0xFF) >>> 5;
        int[] presentation = SHADOW_PRESENTATION[index];
        x = (short) (x + presentation[0]);
        y = (short) (y + presentation[1]);
        int frame = presentation[2];
        if ((angle & 0x80) != 0) {
            if (index == 3 || index == 7) {
                frame += 3;
            }
            y = (short) (2 * offsetY - y);
        }
        // loc_341BA applies this after the upper-half reflection, using STATUS
        // orientation rather than the body's current render flags.
        if (tails && player.anim() == 1 && !player.statusYFlip()) {
            x = (short) (x + (player.statusXFlip() ? -4 : 4));
            y = (short) (y - 4);
        }
        result.add(new Sonic2SpecialStageSlotPool.RenderState(entry, x, y, frame,
                presentation[3] != 0, false, true, SHADOW));
    }

    /** loc_33D02 (s2.asm:69561-69584), including byte-width angle operations. */
    private int jumpShadowAngle(Sonic2SpecialStageSnapshot.PlayerSnapshot player) {
        int x = (short) player.ssXPos();
        int magnitude = x < 0 ? (-x) & 0xFFFF : x;
        int z = player.ssZPos() & 0xFFFF;
        if (z == 0) {
            throw new IllegalStateException("Initialized native shadow has a zero ss_z_pos divisor");
        }
        int numerator = magnitude << 7;
        int quotient = numerator / z;
        // DIVU.W leaves the destination unchanged on quotient overflow. The
        // following PC-indexed MOVE uses a SIGNED word displacement either way.
        int displacement = (short) (quotient <= 0xFFFF ? quotient : numerator);
        int angle = readShadowAngle(displacement);
        if (x >= 0) {
            angle = (0x80 - angle) & 0xFF;
        }
        if ((short) player.ssYPos() < 0) {
            angle = (-angle) & 0xFF;
        }
        return angle;
    }

    private int readShadowAngle(int displacement) {
        if (displacement >= 0 && displacement < SHADOW_ANGLE.length) {
            return SHADOW_ANGLE[displacement];
        }
        // FixBugs=0: there is no bounds check before the ROM's PC-indexed
        // MOVE.B. Jumps past the ellipse can read adjacent instruction bytes.
        // Read those bytes through the actual ROM pipeline, never clamp the
        // index or invent a geometric approximation.
        long address = 0x33D32L + displacement;
        if (rom == null) {
            throw new IllegalStateException("Native shadow lookup at ROM $"
                    + Long.toHexString(address) + " requires the bound stage ROM");
        }
        try {
            return rom.readByte(address) & 0xFF;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read native shadow angle at ROM $"
                    + Long.toHexString(address), e);
        }
    }

    /** Obj88 (s2.asm:70664-70680) and LoadSSTailsTailsDynPLC (s2.asm:70692-70702). */
    private static void addTails(List<Sonic2SpecialStageSlotPool.RenderState> result,
                                 Sonic2SpecialStageSlotPool.Entry entry,
                                 Sonic2SpecialStageSnapshot.PlayerSnapshot player) {
        if (entry == null || entry.kind() != Kind.TAILS_TAIL || player == null) {
            return;
        }
        boolean visible = initialized(player) && player.anim() < 3 && (player.ssDplcTimer() & 1) == 0;
        // Obj88 first copies the body's flags. AnimateSprite overwrites its flip
        // bits from status only on an actual frame advance (duration reloaded3).
        boolean animationAdvanced = player.anim() < 3 && player.tailsTailsFrameDuration() == 3;
        boolean xFlip = animationAdvanced ? player.statusXFlip() : player.renderXFlip();
        boolean yFlip = animationAdvanced ? player.statusYFlip() : player.renderYFlip();
        result.add(new Sonic2SpecialStageSlotPool.RenderState(entry, (short) player.xPos(),
                (short) player.yPos(), player.tailsTailsMappingFrame(), xFlip, yFlip,
                visible, TAILS_TAIL));
    }

    private static boolean initialized(Sonic2SpecialStageSnapshot.PlayerSnapshot player) {
        return player.routine() != Sonic2SpecialStagePlayer.RoutineState.INIT;
    }

    public List<Sonic2SpecialStageSlotPool.RenderState> capture() {
        return states;
    }

    public void restore(List<Sonic2SpecialStageSlotPool.RenderState> snapshot) {
        states = List.copyOf(snapshot);
    }

    public void clear() {
        states = List.of();
    }
}
