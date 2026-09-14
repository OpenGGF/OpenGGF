package com.openggf.tools.fbzvisual;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import java.util.ArrayList;
import java.util.List;

/** Read-only AniPLC submission oracle, FBZ remaining visual task (base 51677cdd2).
 * Inputs are verified ROM bytes and a destination, never reference gameplay state.
 * ROM AnimateTiles_DoAniPLC queues after Wait_VSync; VInt Process_DMA_Queue
 * publishes on the following VBlank. Counter advance and visible art are distinct.
 */
record FbzVisualCadenceRomContract(int duration, List<String> framePayloadHashes) {
    FbzVisualCadenceRomContract {
        framePayloadHashes = List.copyOf(framePayloadHashes);
        if (duration < 0 || duration > 127 || framePayloadHashes.isEmpty())
            throw new IllegalArgumentException("Invalid FBZ AniPLC ROM contract");
    }

    static FbzVisualCadenceRomContract read(RomByteReader rom, int act, int destination) {
        int address = act == 1 ? Sonic3kConstants.ANIPLC_FBZ1_ADDR
                : act == 2 ? Sonic3kConstants.ANIPLC_FBZ2_ADDR : -1;
        if (address < 0) throw new IllegalArgumentException("Invalid FBZ act");
        int count = rom.readU16BE(address) + 1;
        int cursor = address + 2;
        for (int channel = 0; channel < count; channel++) {
            int duration = rom.readU8(cursor);
            int source = rom.readU32BE(cursor) & 0xFFFFFF;
            int dest = rom.readU16BE(cursor + 4) / 32;
            int frames = rom.readU8(cursor + 6), tiles = rom.readU8(cursor + 7);
            boolean perFrame = duration >= 128;
            if (dest == destination) {
                if (perFrame) throw new IllegalStateException("FBZ cadence requires global duration");
                List<String> hashes = new ArrayList<>();
                for (int frame = 0; frame < frames; frame++) {
                    int tile = rom.readU8(cursor + 8 + frame);
                    hashes.add(FbzVisualPrebootVerifier.sha256(rom.slice(source + tile * 32, tiles * 32)));
                }
                return new FbzVisualCadenceRomContract(duration, hashes);
            }
            cursor += 8 + ((frames * (perFrame ? 2 : 1) + 1) & ~1);
        }
        throw new IllegalStateException("Destination absent from FBZ AniPLC ROM list");
    }

    boolean stableArt() { return framePayloadHashes.stream().distinct().count() == 1; }
    int nextIndex(int before) { return before >= framePayloadHashes.size() ? 1 : before + 1; }
    String submittedHash(int index) {
        if (index < 1 || index > framePayloadHashes.size())
            throw new IllegalStateException("Uninitialized or invalid AniPLC index " + index);
        return framePayloadHashes.get(index - 1);
    }
}
