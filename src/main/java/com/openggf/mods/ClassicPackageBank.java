package com.openggf.mods;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;
import java.util.TreeSet;

import static com.openggf.mods.SmpsPackageReader.check;

/** Validates the finite S1/S2 stream subset emitted by OpenSMPSDeck. */
final class ClassicPackageBank {
    private ClassicPackageBank() {}

    static void validate(byte[] bank, ByteOrder pointerOrder, int minimumTempo, int base, int voiceCount,
                         Set<Integer> voiceOffsets, Set<Integer> psgSlots, Set<Integer> dacNotes)
            throws IOException {
        int fm = bank[2] & 255;
        int psg = bank[3] & 255;
        check(fm <= 7 && psg <= 3 && fm + psg > 0, "Invalid classic SMPS channel counts");
        check(bank[4] != 0, "Header timing divisor must be nonzero");
        check((bank[5] & 255) >= minimumTempo, "Header tempo cannot advance music durations");
        int fmEnd = 6 + 4 * fm;
        int headerEnd = fmEnd + 6 * psg;
        check(headerEnd <= bank.length, "Truncated classic SMPS music header");
        var pointers = ByteBuffer.wrap(bank).order(pointerOrder);
        int voicePointer = Short.toUnsignedInt(pointers.getShort(0));
        int streamEnd = voiceCount == 0 ? bank.length : voicePointer - base;
        check(voiceCount == 0 ? voicePointer == 0 : voicePointer != 0 && streamEnd >= headerEnd
                        && bank.length - streamEnd == voiceCount * 25,
                "Invalid local FM voice table");
        check(voiceOffsets.size() == voiceCount, "FM assets must match the local voice slots");
        for (int slot = 0; slot < voiceCount; slot++) {
            check(voiceOffsets.contains(streamEnd + slot * 25), "Missing local FM asset for slot " + slot);
        }

        int[] starts = new int[fm + psg];
        var boundaries = new TreeSet<Integer>();
        boundaries.add(streamEnd);
        for (int channel = 0; channel < starts.length; channel++) {
            int offset = channel < fm ? 6 + channel * 4 : fmEnd + (channel - fm) * 6;
            int start = Short.toUnsignedInt(pointers.getShort(offset)) - base;
            check(start >= headerEnd && start < streamEnd, "Track pointer outside the stream region at " + offset);
            starts[channel] = start;
            boundaries.add(start);
            if (channel >= fm) {
                check(bank[offset + 4] == 0, "PSG modulation envelope in header");
                requireEnvelope(psgSlots, bank[offset + 5] & 255);
            }
        }
        for (int channel = 0; channel < starts.length; channel++) {
            int start = starts[channel];
            scan(bank, start, boundaries.higher(start), channel, fm, voiceCount, psgSlots, dacNotes);
        }
    }

    private static void scan(byte[] bank, int at, int end, int channel, int fm, int voiceCount,
                             Set<Integer> psgSlots, Set<Integer> dacNotes) throws IOException {
        boolean dac = channel < fm && channel == 0;
        boolean psg = channel >= fm;
        boolean haveNote = false;
        while (at < end) {
            int command = bank[at++] & 255;
            if (command == 0xf2) return;
            if (command < 0x80) {
                check(!dac && haveNote && command != 0, "Invalid duration-only stream unit");
                continue;
            }
            if (command < 0xe0) {
                if (dac) {
                    check(command == 0x80 || fm < 7 && dacNotes.contains(command),
                            "Undefined or unavailable DAC note " + command);
                } else if (!psg && command != 0x80) {
                    check(voiceCount > 0, "FM notes require a local voice table");
                }
                check(at < end && bank[at] > 0, "Note or rest requires explicit duration in 1..127");
                at++;
                haveNote = true;
                continue;
            }
            int arity = switch (command) {
                case 0xe7, 0xf4 -> 0;
                case 0xe1, 0xe8, 0xe9 -> 1;
                case 0xe0, 0xe6, 0xef -> psg ? -1 : 1;
                case 0xec, 0xf5 -> psg ? 1 : -1;
                case 0xf3 -> channel == fm + 2 ? 1 : -1;
                case 0xf0 -> 4;
                default -> -1;
            };
            check(!dac && arity >= 0, "Unsupported classic SMPS command " + command + " at " + (at - 1));
            check(at + arity <= end, "Truncated classic SMPS command " + command);
            if (command == 0xef) check((bank[at] & 255) < voiceCount, "Undefined FM voice " + (bank[at] & 255));
            if (command == 0xf5) requireEnvelope(psgSlots, bank[at] & 255);
            at += arity;
        }
        throw new IOException("Track has no F2 stop before the next track or voice table");
    }

    private static void requireEnvelope(Set<Integer> slots, int slot) throws IOException {
        check(slot == 0 || slots.contains(slot), "Undefined PSG envelope " + slot);
    }
}
