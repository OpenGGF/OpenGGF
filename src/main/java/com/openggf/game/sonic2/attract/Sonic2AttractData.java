package com.openggf.game.sonic2.attract;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameplayLaunchTeam;

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
        this(RomByteReader.fromRom(Objects.requireNonNull(source, "rom")), false);
    }

    Sonic2AttractData(RomByteReader rom, boolean lockOn) throws IOException {
        this.rom = Objects.requireNonNull(rom, "rom");
        try {
            // KiS2 c336fede: TitleScreen ($3036D8), DemoLevels ($3038A0),
            // DemoScriptPointers ($304408), Level timer setup ($303D84) and fade ($303EE8).
            titleTicks = timerImmediate(lockOn ? 0x3036D8 : address(rom, 0x3BA2));
            playbackTicks = timerImmediate(lockOn ? 0x303D84 : address(rom, 0x42A8));
            fadeTicks = timerImmediate(lockOn ? 0x303EE8 : address(rom, 0x4408));
            int levels = lockOn ? 0x3038A0 : address(rom, DEMO_LEVELS);
            int scripts = lockOn ? 0x304408 : address(rom, SCRIPT_POINTERS);
            int p2Operand = lockOn ? -1 : address(rom, P2_SCRIPT_OPERAND);
            if (!lockOn && rom.readU16BE(p2Operand - 2) != 0x43F9) {
                throw new IllegalArgumentException("Missing MoveDemo_On_P2 LEA operand");
            }
            int p2Address = lockOn ? -1 : rom.readU32BE(p2Operand);
            var team = lockOn ? new GameplayLaunchTeam(CharacterKey.KNUCKLES, List.of())
                    : new GameplayLaunchTeam(CharacterKey.SONIC, List.of(CharacterKey.TAILS));
            plans = IntStream.range(0, DEMO_COUNT).mapToObj(index -> {
                int zoneAndAct = rom.readU16BE(levels + index * 2);
                int p1Address = rom.readU32BE(scripts + (zoneAndAct >>> 8) * 4);
                // TitleScreen_Demo compares the complete EHZ1 word; MoveDemo_On_P2 tests its zone.
                boolean competition = !lockOn && zoneAndAct == 0;
                boolean scriptedP2 = !lockOn && (zoneAndAct >>> 8) == 0;
                rom.readU16BE(p1Address);
                if (scriptedP2) rom.readU16BE(p2Address);
                return new Sonic2AttractService.LaunchPlan(index, zoneAndAct,
                        competition ? Sonic2AttractService.SessionKind.COMPETITION
                                : Sonic2AttractService.SessionKind.CAMPAIGN,
                        p1Address, scriptedP2 ? p2Address : -1, team);
            }).toList();
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rom.slice(0, rom.size())));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            throw new IOException("Invalid Sonic 2 attract data in supplied ROM", e);
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
