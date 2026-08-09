package com.openggf.audio.synth;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TestYm2612ChipGpgxParity {
    @Test
    void s1BombVoiceRoutesRegisterSlotsThroughAlgorithmTwoLikeGpgx() {
        Ym2612Chip chip = configuredEnhancedChip();
        writeS1BombVoice(chip);
        writeNoteAndKeyOn(chip);

        int[] left = new int[4];
        chip.renderStereo(left, new int[left.length]);

        // Hand-captured from Genesis Plus GX ym2612.c at its native clock/144 rate.
        assertArrayEquals(new int[] { 625, 7760, -6632, -7864 }, left);
    }

    @Test
    void resetEnvelopeCounterAdvancesS1BombVoiceLikeGpgx() {
        Ym2612Chip chip = configuredEnhancedChip();
        writeS1BombVoice(chip);
        writeNoteAndKeyOn(chip);

        int[] left = new int[12];
        chip.renderStereo(left, new int[left.length]);

        // Extends through multiple three-sample EG boundaries in Genesis Plus GX.
        assertArrayEquals(new int[] {
                625, 7760, -6632, -7864, 275, 125,
                8168, 1272, -7908, 2920, 1618, 1863
        }, left);
    }

    @Test
    void decayToSustainTransitionPreservesGpgxAttenuationOvershoot() {
        Ym2612Chip chip = configuredEnhancedChip();
        writeS1BombVoice(chip);
        writeNoteAndKeyOn(chip);

        int[] left = new int[140];
        chip.renderStereo(left, new int[left.length]);

        // GPGX samples 125..140; the first affected decay transition is sample 131.
        assertArrayEquals(new int[] {
                5516, -4208, -1645, 7472, -360, 3838, 695, -2246,
                6492, -7824, 1361, -3706, 7412, -7552, 3666, 7176
        }, Arrays.copyOfRange(left, 124, 140));
    }

    @Test
    void discreteAlgorithmFourQuantizesTheGpgxCarrierSlots() {
        Ym2612Chip chip = new Ym2612Chip();
        chip.setOutputSampleRate(Ym2612Chip.getInternalRate());
        writeS1BombVoice(chip, 0xFC);
        writeNoteAndKeyOn(chip);

        int[] left = new int[8];
        chip.renderStereo(left, new int[left.length]);

        assertArrayEquals(new int[] { 1280, 4640, 8959, 6624, 8959, 8959, 4480, 8959 }, left);
    }

    @Test
    void channelThreeSpecialFrequenciesMapToGpgxOperatorSlots() {
        Ym2612Chip chip = configuredEnhancedChip();
        writeS1BombVoice(chip, 0xFA, 2);
        chip.write(0, 0x27, 0x40);
        chip.write(0, 0xAC, 0x19);
        chip.write(0, 0xA8, 0x34);
        chip.write(0, 0xAD, 0x22);
        chip.write(0, 0xA9, 0x56);
        chip.write(0, 0xAE, 0x2B);
        chip.write(0, 0xAA, 0x78);
        chip.write(0, 0xA6, 0x31);
        chip.write(0, 0xA2, 0x9A);
        chip.write(0, 0xB6, 0xC0);
        chip.write(0, 0x28, 0xF2);

        int[] left = new int[12];
        chip.renderStereo(left, new int[left.length]);

        assertArrayEquals(new int[] {
                625, 7392, -7656, 6908, 5128, -6232,
                -6832, -7020, 5504, -6724, -7696, 3334
        }, left);
    }

    @Test
    void partialKeyOnUsesGpgxOperatorBitOrder() {
        Ym2612Chip chip = new Ym2612Chip();
        chip.setOutputSampleRate(Ym2612Chip.getInternalRate());
        writeS1BombVoice(chip, 0xFF);
        chip.write(0, 0xA4, 0x22);
        chip.write(0, 0xA0, 0x69);
        chip.write(0, 0xB4, 0xC0);
        chip.write(0, 0x28, 0x20); // Key SLOT2 only.

        int[] left = new int[12];
        chip.renderStereo(left, new int[left.length]);

        assertArrayEquals(new int[] {
                768, 832, 864, 960, 992, 1088,
                1120, 1184, 1280, 1312, 1376, 1440
        }, left);
    }

    private static Ym2612Chip configuredEnhancedChip() {
        Ym2612Chip chip = new Ym2612Chip();
        chip.setChipType(2); // GPGX YM2612_ENHANCED: no discrete ladder distortion.
        chip.setOutputSampleRate(Ym2612Chip.getInternalRate());
        return chip;
    }

    private static void writeS1BombVoice(Ym2612Chip chip) {
        writeS1BombVoice(chip, 0xFA);
    }

    private static void writeS1BombVoice(Ym2612Chip chip, int feedbackAndAlgorithm) {
        writeS1BombVoice(chip, feedbackAndAlgorithm, 0);
    }

    private static void writeS1BombVoice(Ym2612Chip chip, int feedbackAndAlgorithm, int channel) {
        int[] registers = {
                0xB0,
                0x30, 0x38, 0x34, 0x3C,
                0x50, 0x58, 0x54, 0x5C,
                0x60, 0x68, 0x64, 0x6C,
                0x70, 0x78, 0x74, 0x7C,
                0x80, 0x88, 0x84, 0x8C,
                0x40, 0x48, 0x44, 0x4C
        };
        int[] values = {
                feedbackAndAlgorithm,
                0x21, 0x30, 0x10, 0x32,
                0x1F, 0x1F, 0x1F, 0x1F,
                0x05, 0x18, 0x05, 0x10,
                0x0B, 0x1F, 0x10, 0x10,
                0x1F, 0x2F, 0x4F, 0x2F,
                0x0D, 0x07, 0x04, 0x80
        };
        for (int i = 0; i < registers.length; i++) {
            chip.write(0, registers[i] + channel, values[i]);
        }
    }

    private static void writeNoteAndKeyOn(Ym2612Chip chip) {
        chip.write(0, 0xA4, 0x22);
        chip.write(0, 0xA0, 0x69);
        chip.write(0, 0xB4, 0xC0);
        chip.write(0, 0x28, 0xF0);
    }
}
