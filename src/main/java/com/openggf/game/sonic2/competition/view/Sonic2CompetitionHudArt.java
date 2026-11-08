package com.openggf.game.sonic2.competition.view;

import com.openggf.data.Rom;
import com.openggf.game.sonic2.Sonic2RomAddresses;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import java.io.IOException;
import java.util.Objects;

/** Native HUD pattern publication and the two last-written lives bytes. */
public final class Sonic2CompetitionHudArt implements com.openggf.game.rewind.RewindSnapshottable<Sonic2CompetitionHudArt.State> {
    private final Rom rom;
    private final Sonic2CompetitionArtResidency art;
    private int firstWritten = -1, secondWritten = -1;
    public record State(int firstWritten, int secondWritten) { }
    @Override public String key() { return "sonic2.competition.hud-art"; }
    @Override public State capture() { return new State(firstWritten, secondWritten); }
    @Override public void restore(State state) {
        firstWritten = state.firstWritten(); secondWritten = state.secondWritten();
    }

    public Sonic2CompetitionHudArt(Rom rom, Sonic2CompetitionArtResidency art) {
        this.rom = Objects.requireNonNull(rom);
        this.art = Objects.requireNonNull(art);
    }

    /**
     * Hud_Base (s2.asm:88162-88166) first writes P1 lives synchronously;
     * loc_410BC (s2.asm:88195-88200) writes P2 lives, then queues 22 patterns
     * from Art_Hud. Static labels/icons are inherited Std1 and the completed
     * 2P life PLC. Level (s2.asm:4926-4941) calls this before LoadZoneTiles.
     * Pending uploads belong to the residency snapshot. The last-written lives
     * bytes belong to this owner; HudUpdate derives each pending lives flag.
     */
    public void initialize(int firstLives, int secondLives) throws IOException {
        initialLives(0x7DD, firstLives);
        initialLives(0x6FD, secondLives);
        firstWritten = firstLives & 255; secondWritten = secondLives & 255;
        int source = Sonic2RomAddresses.address(rom, Sonic2Constants.ART_UNC_HUD_NUMBERS_ADDR);
        art.queueUpload(0x6E2, rom.readBytes(source, 22 * 32));
    }

    /**
     * loc_40F50 / loc_40FC8 (s2.asm:88027-88077): pause returns before either
     * lives flag is consumed. In-play lives writers set the corresponding flag;
     * the initial/menu resets instead precede Hud_Base. The resident-number
     * alphabet is shared: BuildHUD_P1/P2 select its digit tiles independently,
     * rather than rewriting score/ring/time glyphs as the one-player HUD does.
     */
    public void update(int firstLives, int secondLives, boolean paused) {
        if (paused) return;
        try {
            int first = firstLives & 255, second = secondLives & 255;
            if (first != firstWritten) { initialLives(0x7DD, first); firstWritten = first; }
            if (second != secondWritten) { initialLives(0x6FD, second); secondWritten = second; }
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /** Hud_Lives2 / Hud_LivesLoop (s2.asm:88497-88556): one pattern per digit, two-slot stride. */
    private void initialLives(int firstSlot, int lives) throws IOException {
        int count = lives & 255;
        int source = Sonic2RomAddresses.address(rom, Sonic2Constants.ART_UNC_LIVES_NUMBERS_ADDR);
        art.writePatterns(firstSlot, count < 10 ? new byte[32] : rom.readBytes(source + count / 10 * 32, 32));
        art.writePatterns(firstSlot + 2, rom.readBytes(source + count % 10 * 32, 32));
    }
}
