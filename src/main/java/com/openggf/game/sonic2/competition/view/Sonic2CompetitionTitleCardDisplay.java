package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.titlecard.TitleCardManager;
import com.openggf.game.sonic2.titlecard.TitleCardNativeDisplayAccess;
import java.util.List;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;

/** Obj34 DisplaySprite admissions; BuildSprites_2P owns mapping, clipping and publication. */
public final class Sonic2CompetitionTitleCardDisplay {
    private final TitleCardManager title;
    private final int mappings;
    public Sonic2CompetitionTitleCardDisplay(TitleCardManager title, RomByteReader rom) {
        this.title = java.util.Objects.requireNonNull(title);
        // Obj34_Init's actual mappings operand, rather than the 1P overlay's mapping geometry.
        // Obj34_Init (s2.asm:27328-27350), MapUnc_TitleCards (s2.asm:28558-28589).
        int init = com.openggf.game.sonic2.Sonic2RomAddresses.address(rom, 0x13c6e);
        mappings = rom.readU32BE(init + 0x14);
    }
    /** LoadTitleCard / loc_157FE (s2.asm:29206-29247). Native pattern residency
     * retains these bytes after title deletion; the next real PLC may replace them.
     */
    public static void initializeArt(com.openggf.data.Rom rom, int zone,
            Sonic2CompetitionArtResidency art) throws java.io.IOException {
        var reader = RomByteReader.fromRom(rom);
        byte[] alphabet;
        try (var channel = com.openggf.data.RomChannel.at(rom,
                com.openggf.game.sonic2.Sonic2RomAddresses.address(reader, 0x7d22c))) {
            art.writePatterns(0x580, com.openggf.data.compression.NemesisReader.decompress(channel));
        }
        try (var channel = com.openggf.data.RomChannel.at(rom,
                com.openggf.game.sonic2.Sonic2RomAddresses.address(reader, 0x7d58a))) {
            alphabet = com.openggf.data.compression.NemesisReader.decompress(channel);
        }
        int offsets = com.openggf.game.sonic2.Sonic2RomAddresses.address(reader, 0x15820);
        int letters = com.openggf.game.sonic2.Sonic2RomAddresses.address(reader, 0x15832);
        int source = letters + reader.readU8(offsets + zone), destination = 0x5de;
        for (int offset; (offset = reader.readU8(source++)) < 0x80;) {
            int count = reader.readU8(source++);
            art.writePatterns(destination, java.util.Arrays.copyOfRange(alphabet, offset * 32,
                    (offset + count) * 32));
            destination += count;
        }
    }
    public List<Admission> admissions() {
        var result = new java.util.ArrayList<Admission>();
        for (var part : TitleCardNativeDisplayAccess.read(title).parts())
            if (part.alive() && part.displayed()) result.add(new Admission(part.slot(), 0, result.size(),
                    new ObjectSlot(0x34, 0, part.x(), part.y(), part.width(), 0,
                            new Mapping(mappings, part.mappingFrame()), 0, null)));
        return List.copyOf(result);
    }
}
