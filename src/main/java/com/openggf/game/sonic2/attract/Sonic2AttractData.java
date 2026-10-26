package com.openggf.game.sonic2.attract;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** Immutable REV01 attract data, pinned to the supplied ROM generation, with no external inputs. */
final class Sonic2AttractData {
    private static final int DEMO_LEVELS = 0x3DAC;
    private static final int SCRIPT_POINTERS = 0x4948;
    private static final int P2_SCRIPT_OPERAND = 0x4912;
    static final int DEMO_COUNT = 4;

    final RomByteReader rom;
    final String fingerprint;
    final int titleTicks;
    final int playbackTicks;
    final int fadeTicks;
    final List<Sonic2AttractService.LaunchPlan> plans;

    Sonic2AttractData(Rom source) throws IOException {
        rom = RomByteReader.fromRom(Objects.requireNonNull(source, "rom"));
        try {
            // Verified instruction operands, not values fitted to engine or recorded gameplay.
            titleTicks = timerImmediate(0x3BA2);
            playbackTicks = timerImmediate(0x42A8);
            fadeTicks = timerImmediate(0x4408);
            if (rom.readU16BE(P2_SCRIPT_OPERAND - 2) != 0x43F9) {
                throw new IllegalArgumentException("Missing MoveDemo_On_P2 LEA operand");
            }
            int p2Address = rom.readU32BE(P2_SCRIPT_OPERAND);
            plans = IntStream.range(0, DEMO_COUNT).mapToObj(index -> {
                int zoneAndAct = rom.readU16BE(DEMO_LEVELS + index * 2);
                int p1Address = rom.readU32BE(SCRIPT_POINTERS + (zoneAndAct >>> 8) * 4);
                // TitleScreen_Demo compares the complete EHZ1 word; MoveDemo_On_P2 tests its zone.
                boolean competition = zoneAndAct == 0;
                boolean scriptedP2 = (zoneAndAct >>> 8) == 0;
                rom.readU16BE(p1Address);
                if (scriptedP2) rom.readU16BE(p2Address);
                return new Sonic2AttractService.LaunchPlan(index, zoneAndAct,
                        competition ? Sonic2AttractService.SessionKind.COMPETITION
                                : Sonic2AttractService.SessionKind.CAMPAIGN,
                        p1Address, scriptedP2 ? p2Address : -1);
            }).toList();
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rom.slice(0, rom.size())));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            throw new IOException("Invalid Sonic 2 REV01 attract data in supplied ROM", e);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 unavailable", e);
        }
    }

    private int timerImmediate(int instruction) {
        // MOVE.W #value,(Demo_Time_left).w. All three owners target the same native word.
        if (rom.readU16BE(instruction) != 0x31FC || rom.readU16BE(instruction + 4) != 0xF614) {
            throw new IllegalArgumentException("Missing attract timer immediate at " + Integer.toHexString(instruction));
        }
        int value = rom.readU16BE(instruction + 2);
        if (value == 0) throw new IllegalArgumentException("Zero attract timer");
        return value;
    }
}
