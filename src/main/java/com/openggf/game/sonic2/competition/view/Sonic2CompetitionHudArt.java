package com.openggf.game.sonic2.competition.view;

import com.openggf.data.Rom;
import com.openggf.game.sonic2.Sonic2RomAddresses;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import java.io.IOException;
import java.util.Objects;

/** Native HUD pattern publication. HudUpdate's later rewrites extend this owner at S5. */
public final class Sonic2CompetitionHudArt {
    private final Rom rom;
    private final Sonic2CompetitionArtResidency art;

    public Sonic2CompetitionHudArt(Rom rom, Sonic2CompetitionArtResidency art) {
        this.rom = Objects.requireNonNull(rom);
        this.art = Objects.requireNonNull(art);
    }

    /**
     * Hud_Base (s2.asm:88162-88166) first writes P1 lives synchronously;
     * loc_410BC (s2.asm:88195-88200) writes P2 lives, then queues 22 patterns
     * from Art_Hud. Static labels/icons are inherited Std1 and the completed
     * 2P life PLC. Level (s2.asm:4926-4941) calls this before LoadZoneTiles.
     * Pending uploads belong to the residency snapshot; this initializer has
     * no independent clock, flags or rewind state.
     */
    public void initialize(int firstLives, int secondLives) throws IOException {
        initialLives(0x7DD, firstLives);
        initialLives(0x6FD, secondLives);
        int source = Sonic2RomAddresses.address(rom, Sonic2Constants.ART_UNC_HUD_NUMBERS_ADDR);
        art.queueUpload(0x6E2, rom.readBytes(source, 22 * 32));
    }

    /** Hud_Lives2 / Hud_LivesLoop (s2.asm:88497-88556): one pattern per digit, two-slot stride. */
    private void initialLives(int firstSlot, int lives) throws IOException {
        int count = lives & 255;
        int source = Sonic2RomAddresses.address(rom, Sonic2Constants.ART_UNC_LIVES_NUMBERS_ADDR);
        art.writePatterns(firstSlot, count < 10 ? new byte[32] : rom.readBytes(source + count / 10 * 32, 32));
        art.writePatterns(firstSlot + 2, rom.readBytes(source + count % 10 * 32, 32));
    }
}
