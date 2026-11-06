package com.openggf.game.sonic2.competition.interlace;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

import com.openggf.data.RomByteReader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceWorldInputs.*;

/**
 * CPU BuildSprites_2P (s2.asm:30987-31448), including native HUD and placed rings.
 * The host supplies actual resolved sources and commits the returned visibility /
 * display-list consumption decisions. It must invoke P2 only after Hint_flag clears.
 * This class does not call ordinary draw routines or advance any world owner.
 */
public final class Sonic2InterlaceSatBuilder {
    private static final int HUD_LABELS = 0x40BEA, HUD_DIGITS = 0x40C82, RING_MAP = 0x1736A;
    private final RomByteReader rom;

    public Sonic2InterlaceSatBuilder(RomByteReader rom) { this.rom = Objects.requireNonNull(rom); }

    public record Pass(int player, Sonic2InterlaceWorkRam ram, int spriteCount,
                       List<Visibility> visibility, boolean consumeDisplayLists) {
        public Pass {
            if (player < 0 || player > 1) throw new IllegalArgumentException("Player page");
            Objects.requireNonNull(ram); octet(spriteCount); visibility = List.copyOf(visibility);
        }
        /** The hardware always uploads exactly 80 entries, independently of CPU spill. */
        public List<Integer> hardwareWords() {
            int base = player == 0 ? Sonic2InterlaceWorkRam.SAT_P1 : Sonic2InterlaceWorkRam.SAT_P2;
            var words = new ArrayList<Integer>(320);
            for (int i = 0; i < 320; i++) words.add(ram.readWord(base + i * 2));
            return List.copyOf(words);
        }
    }

    public Pass buildPlayer1(Sonic2InterlaceWorkRam actualRam, Build input) {
        return build(actualRam, input, 0, List.of());
    }

    public Pass buildPlayer2(Pass first, Build input, boolean hintPending) {
        if (first.player() != 0) throw new IllegalArgumentException("Expected P1 CPU page");
        // FixBugs=0 BuildSprites_P2 (s2.asm:31140-31143) waits, rather than page-flipping.
        if (hintPending) throw new IllegalStateException("BuildSprites_P2 is waiting for actual H-int");
        return build(first.ram(), input, 1, first.visibility());
    }

    private Pass build(Sonic2InterlaceWorkRam actualRam, Build input, int player, List<Visibility> previous) {
        Objects.requireNonNull(input);
        var output = new Emitter(actualRam.bytes(), player);
        if (player == 0) {
            output.emit(0x1D8, 0xF, 0, 1);
            output.emit(0x1D8, 0xF, 0, 0);
        }
        var camera = player == 0 ? input.firstCamera() : input.secondCamera();
        if (input.hud().levelStarted()) {
            hud(output, input.hud(), player);
            rings(output, player == 0 ? input.firstRings() : input.secondRings(), camera,
                    input.ringsAnimFrame(), player);
        }
        var visible = new LinkedHashMap<Integer, Boolean>();
        previous.forEach(v -> visible.put(v.slot(), v.onScreen()));
        // Display lists are bucket 0..7; FIFO within each list, including duplicates.
        var admissions = new ArrayList<>(input.admissions());
        admissions.sort(Comparator.comparingInt(Admission::bucket).thenComparingInt(Admission::ordinal));
        for (var admission : admissions) {
            var object = admission.occupant();
            if (object == null || object.id() == 0) continue;
            if (player == 0) visible.put(admission.slot(), false);
            if (!drawObject(output, object, camera, player)) continue;
            visible.put(admission.slot(), true); // P2 ORs the SAME on_screen bit, never clears it.
        }
        output.terminate(player);
        return new Pass(player, new Sonic2InterlaceWorkRam(output.ram), output.count,
                visible.entrySet().stream().map(e -> new Visibility(e.getKey(), e.getValue())).toList(),
                player == 1 && !input.teleportFlag());
    }

    private boolean drawObject(Emitter out, ObjectSlot object, Camera camera, int player) {
        int flags = object.renderFlags();
        var multi = object.multiSprite();
        int baseX, baseY;
        if (multi == null && (flags & 12) == 0) {
            baseX = object.x(); baseY = (object.y() + 128 + player * 224) & 65535;
        } else {
            int dx = (short) (object.x() - camera.x());
            int width = multi == null ? object.width() : multi.width();
            if ((short) (dx + width) < 0 || (short) (dx - width) >= 320) return false;
            int dy = (short) (object.y() - camera.y());
            if ((flags & 16) != 0) {
                int height = multi == null ? object.yRadius() : multi.height();
                if ((short) (dy + height) < 0 || (short) (dy - height) >= 224) return false;
            } else {
                int approximate = (dy + 128) & 65535;
                if (approximate < 96 || approximate >= 384) return false;
            }
            baseX = (dx + 128) & 65535;
            baseY = (dy + 256 + player * 224) & 65535;
        }
        var mapping = object.mapping();
        if (multi == null) {
            mapping(out, mapping.address(), mapping.frame(), (flags & 32) != 0,
                    baseX, baseY, object.artTile(), (flags & 1) != 0, (flags & 2) != 0, true);
        } else {
            // FixBugs=0 ChkDrawSprite_2P (s2.asm:31490-31503) bypasses BOTH object flips.
            if (multi.mainFrame() != 0)
                mapping(out, mapping.address(), multi.mainFrame(), false, baseX, baseY,
                        object.artTile(), false, false, true);
            for (var child : multi.children())
                mapping(out, mapping.address(), child.frame(), false,
                        (child.x() - camera.x() + 128) & 65535,
                        (child.y() - camera.y() + 256 + player * 224) & 65535,
                        object.artTile(), false, false, true);
        }
        return true;
    }

    private void mapping(Emitter out, int base, int frame, boolean direct, int x, int y,
                         int art, boolean hFlip, boolean vFlip, boolean checkLimit) {
        if (checkLimit && out.count >= 80) return;
        int address = direct ? base : base + rom.readS16BE(base + frame * 2);
        int count = direct ? 1 : rom.readU16BE(address);
        if (!direct) { address += 2; if ((short) ((count - 1) & 65535) < 0) return; }
        for (int i = 0; i < count; i++, address += 8) {
            int size = rom.readU8(address + 1);
            if (size > 15) throw new IllegalArgumentException("Invalid native mapping size");
            int dx = rom.readS16BE(address + 6), dy = (byte) rom.readU8(address);
            int px = x + (hFlip ? -dx - ((size >>> 2) + 1) * 8 : dx);
            int py = y + (vFlip ? -dy - ((size & 3) + 1) * 8 : dy);
            int tile = ((art + rom.readU16BE(address + 4)) & 65535)
                    ^ (hFlip ? 0x800 : 0) ^ (vFlip ? 0x1000 : 0);
            px &= 511; if (px == 0) px = 1;
            // The shipped loop has NO per-piece 80 check. Spill is real CPU RAM.
            out.emit(py, nativeSize(size), tile, px);
        }
    }

    /** BuildHUD_P1/P2 (s2.asm:87536-87783), including topology and LF warning frames. */
    private void hud(Emitter out, Hud state, int player) {
        var current = player == 0 ? state.first() : state.second();
        var other = player == 0 ? state.second() : state.first();
        int frame = 0;
        if ((state.levelFrameCounter() & 8) == 0) {
            if (current.rings() == 0) frame++;
            if (current.minute() == 9) frame += 2;
        }
        int y = 392 + player * 224;
        mapping(out, address(rom, HUD_LABELS), frame, false, 144, y, 0x8365, false, false, false);
        int timeY = 264 + player * 224;
        digit(out, current.minute(), 184, timeY);
        digit(out, 10, 192, timeY);
        digit(out, current.second() / 10, 200, timeY);
        digit(out, current.second() % 10, 208, timeY);
        int rings = current.rings();
        if (rings >= 100) digit(out, rings / 100, 192, timeY + 16);
        if (rings >= 10) digit(out, rings / 10 % 10, 200, timeY + 16);
        digit(out, rings % 10, 208, timeY + 16);
        if (other.timerEnabled() == 0 && current.timerEnabled() != 0) {
            int seconds = state.loserTimeLeft() >>> 8;
            digit(out, seconds / 10, 272, 440 + player * 224);
            digit(out, seconds % 10, 280, 440 + player * 224);
        }
        mapping(out, address(rom, HUD_LABELS), player == 0 ? 4 : 5, false, 144, y,
                player == 0 ? 0x8365 : 0x8340, false, false, false);
    }

    private void digit(Emitter out, int digit, int x, int y) {
        mapping(out, address(rom, HUD_DIGITS), digit, false, x, y, 0x8371, false, false, false);
    }

    /** BuildRings_2P_Loop (s2.asm:32124-32195): custom count-less mapping and shipped Y bug. */
    private void rings(Emitter out, RingWindow window, Camera camera, int globalFrame, int player) {
        for (var ring : window.rows()) {
            if ((short) ring.status() < 0) continue;
            int y = ((ring.y() - camera.y()) & 0x7FF) + 136;
            if (y >= 368) continue; // FixBugs=0 masks BEFORE adding the ordinary $88 bias.
            int frame = ring.status() & 255;
            if (frame == 0) frame = globalFrame;
            int piece = address(rom, RING_MAP) + rom.readS16BE(address(rom, RING_MAP) + frame * 2);
            int x = ring.x() - camera.x() + 128 + rom.readS16BE(piece + 6);
            out.emit(y + 120 + player * 224 + (byte) rom.readU8(piece),
                    nativeSize(rom.readU8(piece + 1)),
                    (0x235E + rom.readU16BE(piece + 4)) & 65535, x);
        }
    }

    private static int nativeSize(int ordinary) { return (ordinary & 12) | ((ordinary & 3) >>> 1); }

    private static final class Emitter {
        final byte[] ram;
        int pointer, count;
        Emitter(byte[] ram, int player) {
            this.ram = ram; pointer = player == 0 ? Sonic2InterlaceWorkRam.SAT_P1 : Sonic2InterlaceWorkRam.SAT_P2;
        }
        void emit(int y, int size, int art, int x) {
            count = (count + 1) & 255;
            Sonic2InterlaceWorkRam.writeWord(ram, pointer, y);
            Sonic2InterlaceWorkRam.writeWord(ram, pointer + 2, size << 8 | count);
            Sonic2InterlaceWorkRam.writeWord(ram, pointer + 4, art);
            Sonic2InterlaceWorkRam.writeWord(ram, pointer + 6, x);
            pointer += 8;
        }
        void terminate(int player) {
            boolean full = player == 0 ? count >= 80 : count == 80;
            if (full) ram[Objects.checkIndex(pointer - 5, ram.length)] = 0;
            else {
                Sonic2InterlaceWorkRam.writeWord(ram, pointer, 0);
                Sonic2InterlaceWorkRam.writeWord(ram, pointer + 2, 0);
            }
        }
    }
}
