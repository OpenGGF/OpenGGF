package com.openggf.game.sonic2.competition.specialstage;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

import com.openggf.data.RomByteReader;
import com.openggf.level.Palette;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Native halfpipe fade, stars and Obj5A rainbow CRAM state. */
public final class CompetitiveHalfpipePalettes {
    public record State(int stage, int fadePass, int starOne, int starTwo,
                        int rainbowPhase, boolean rainbowActive, List<Integer> words) {
        public State { words = List.copyOf(words); }
    }

    private final CompetitiveHalfpipeRomData data;
    private final RomByteReader rom;
    private final int[] targets = new int[64];
    private final int[] words = new int[64];
    private int stage;
    private int fadePass;
    private int starOne;
    private int starTwo;
    private int rainbowPhase;
    private boolean rainbowActive;

    public CompetitiveHalfpipePalettes(CompetitiveHalfpipeRomData data) {
        this.data = Objects.requireNonNull(data, "data");
        rom = data.rom();
        load(0);
    }

    public void load(int stage) {
        data.segments(stage); // Validate against the same injected stage catalogue.
        this.stage = stage;
        int paletteIndex = stage + (stage >= 2 ? 3 : 0);
        int paletteId = rom.readU16BE(address(rom, 0x778E) + paletteIndex * 2);
        int stageAddress = rom.readU32BE(address(rom, 0x2782) + paletteId * 8);
        for (int index = 0; index < targets.length; index++) {
            int address = index < 48 ? address(rom, 0x3162) + index * 2 : stageAddress + (index - 48) * 2;
            targets[index] = rom.readU16BE(address);
        }
        Arrays.fill(words, 0xEEE);
        fadePass = 0;
        // SSInitPalAndData (s2.asm:10231-10239): star one is cleared RAM;
        // star two is explicitly four. Rainbow phase is the cleared RAM byte.
        starOne = 0;
        starTwo = 4;
        rainbowPhase = 0;
        rainbowActive = false;
    }

    /** One of Pal_FadeFromWhite's 22 palette passes; it does not run gameplay. */
    public void fadeStep() {
        if (fadePass >= 22) return;
        for (int index = 0; index < words.length; index++) {
            int current = words[index];
            int target = targets[index];
            if (current == target) continue;
            // Pal_FadeFromWhite.UpdateColour (s2.asm:3529-3565) uses full
            // word subtraction, carry and target comparison: blue first,
            // then green, finally red. There is no simultaneous RGB tween.
            if (current >= 0x200 && current - 0x200 >= target) words[index] = current - 0x200;
            else if (current >= 0x20 && current - 0x20 >= target) words[index] = current - 0x20;
            else words[index] = (current - 2) & 0xFFFF;
        }
        fadePass++;
    }

    /** Main-loop PalCycle_SS, followed by this pass's Obj5A rainbow edge writes. */
    public void tick(int vintCount, boolean rainbow) {
        if ((vintCount & 3) == 0) {
            words[14] = rom.readU16BE(address(rom, 0x542A) + (starOne & 7) * 2);
            words[15] = rom.readU16BE(address(rom, 0x542A) + (starTwo & 7) * 2);
            starOne = (starOne + 1) & 0xFF;
            starTwo = (starTwo + 1) & 0xFF;
        }
        // VS FD creates Obj5A, never Obj59: SS_Pause_Only_flag is not set.
        // The ordinary stage-six summed-ring/PauseOnly palette rotation is
        // therefore absent, including after the third competitive checkpoint.
        if (rainbowActive && (vintCount & 7) == 0) {
            writeRainbow(address(rom, 0x54C4) + (rainbowPhase & 3) * 6);
            rainbowPhase = (rainbowPhase + 1) & 0xFF;
        }
        if (rainbow != rainbowActive) {
            // PalCycle_SS runs before RunObjects; Obj5A's begin/end write
            // wins if the edge shares an eight-VInt palette-cycle boundary.
            writeRainbow(address(rom, 0x35548) + (rainbow ? 6 : 0));
            rainbowActive = rainbow;
        }
    }

    private void writeRainbow(int address) {
        for (int i = 0; i < 3; i++) words[48 + 11 + i] = rom.readU16BE(address + i * 2);
    }

    public Palette[] palettes() {
        Palette[] result = new Palette[4];
        for (int line = 0; line < result.length; line++) {
            result[line] = new Palette();
            for (int color = 0; color < 16; color++) {
                result[line].colors[color].fromSegaFormat(words[line * 16 + color]);
            }
        }
        return result;
    }

    public State capture() {
        List<Integer> snapshot = new ArrayList<>(words.length);
        for (int word : words) snapshot.add(word);
        return new State(stage, fadePass, starOne, starTwo, rainbowPhase, rainbowActive, snapshot);
    }

    public void restore(State state) {
        Objects.requireNonNull(state, "state");
        if (state.words().size() != words.length) throw new IllegalArgumentException("Invalid native palette snapshot");
        load(state.stage());
        fadePass = state.fadePass();
        starOne = state.starOne();
        starTwo = state.starTwo();
        rainbowPhase = state.rainbowPhase();
        rainbowActive = state.rainbowActive();
        for (int index = 0; index < words.length; index++) words[index] = state.words().get(index);
    }
}
