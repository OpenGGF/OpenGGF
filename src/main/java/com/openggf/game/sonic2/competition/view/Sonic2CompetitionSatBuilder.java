package com.openggf.game.sonic2.competition.view;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

import com.openggf.data.RomByteReader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;

/**
 * CPU BuildSprites_2P (s2.asm:30987-31448), including native HUD and placed rings.
 * The host supplies actual resolved sources and commits the returned visibility /
 * display-list consumption decisions. Both completed pages are published atomically;
 * the shipped Hint_flag/partial-page race is explicitly unsupported.
 * This class does not call ordinary draw routines or advance any world owner.
 */
public final class Sonic2CompetitionSatBuilder {
    private static final int HUD_LABELS = 0x40BEA, HUD_DIGITS = 0x40C82, RING_MAP = 0x1736A;
    private final RomByteReader rom;

    public Sonic2CompetitionSatBuilder(RomByteReader rom) { this.rom = Objects.requireNonNull(rom); }

    public record SatEntry(int rawY, int size, int link, int word, int rawX) {
        public SatEntry { rawY &= 65535; size &= 15; link &= 127; word &= 65535; rawX &= 65535; }
        public int x() { return (rawX & 511) - 128; }
        public int y() { return (rawY & 1023) - 256; }
        public int widthCells() { return (size >>> 2) + 1; }
        public int heightCells() { return (size & 3) + 1; }
        public Sonic2CellWord name() { return new Sonic2CellWord(word); }
    }
    public record Page(int player, List<SatEntry> entries, int spriteCount,
                       List<Visibility> visibility, boolean consumeDisplayLists) {
        public Page { entries = List.copyOf(entries); visibility = List.copyOf(visibility); }
        public static Page empty(int player) { return new Page(player, List.of(), 0, List.of(), false); }
    }
    public Page buildPlayer1(Build input) { return build(input, 0, List.of()); }
    public Page buildPlayer2(Page first, Build input) {
        if (first.player() != 0) throw new IllegalArgumentException("Expected completed P1 page");
        return build(input, 1, first.visibility());
    }
    private Page build(Build input, int player, List<Visibility> previous) {
        Objects.requireNonNull(input);
        var output = new Emitter();
        if (player == 0) { output.emit(0x1D8, 0xF, 0, 1); output.emit(0x1D8, 0xF, 0, 0); }
        var camera = player == 0 ? input.firstCamera() : input.secondCamera();
        if (input.hud().levelStarted()) {
            hud(output, input.hud(), player);
            rings(output, player == 0 ? input.firstRings() : input.secondRings(), camera,
                    input.ringsAnimFrame(), player);
        }
        var visible = new LinkedHashMap<Integer, Boolean>();
        previous.forEach(v -> visible.put(v.slot(), v.onScreen()));
        var admissions = new ArrayList<>(input.admissions());
        admissions.sort(Comparator.comparingInt(Admission::bucket).thenComparingInt(Admission::ordinal));
        for (var admission : admissions) {
            var object = admission.occupant();
            if (object == null || object.id() == 0) continue;
            if (player == 0) visible.put(admission.slot(), false);
            if (!drawObject(output, object, camera, player)) continue;
            visible.put(admission.slot(), true);
        }
        output.terminate(player);
        return new Page(player, output.entries, output.count,
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
        final ArrayList<SatEntry> entries = new ArrayList<>();
        int count;
        void emit(int y, int size, int art, int x) {
            count = (count + 1) & 255;
            // FixBugs=0's per-object check permits piece overflow. Corruption beyond
            // the 80-entry page is unsupported; preserve every retained entry's link.
            if (entries.size() < 80) entries.add(new SatEntry(y, size, count, art, x));
        }
        void terminate(int player) {
            boolean full = player == 0 ? count >= 80 : count == 80;
            if (full && count == entries.size() && !entries.isEmpty()) {
                int i = entries.size() - 1;
                var last = entries.get(i);
                entries.set(i, new SatEntry(last.rawY(), last.size(), 0, last.word(), last.rawX()));
            } else if (!full && entries.size() < 80) entries.add(new SatEntry(0, 0, 0, 0, 0));
        }
    }
}
