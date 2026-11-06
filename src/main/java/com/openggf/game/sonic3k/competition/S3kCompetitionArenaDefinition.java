package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.objects.ObjectSpawn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** The native act-zero arena record; secondary preview rows are not separate races. */
public record S3kCompetitionArenaDefinition(S3kCompetitionSession.Arena arena,
        int startX, int startY, int cameraMinX, int cameraMaxX, int cameraMinY,
        int cameraMaxY, int placementAddress, List<ObjectSpawn> placements) {
    public S3kCompetitionArenaDefinition { placements = List.copyOf(placements); }

    public static S3kCompetitionArenaDefinition load(Rom rom, S3kCompetitionSession.Arena arena) throws IOException {
        return load(rom, arena, S3kCompetitionResources.LOCKED_ON);
    }

    public static S3kCompetitionArenaDefinition load(Rom rom, S3kCompetitionSession.Arena arena, S3kCompetitionResources resources) throws IOException {
        var reader = RomByteReader.fromRom(rom);
        int zone = arena.zoneId();
        int start = resources.starts() + zone * 8;
        int sizes = resources.sizes() + zone * 16;
        int pointer = reader.readU32BE(resources.objects() + zone * 8);
        List<ObjectSpawn> placements = new ArrayList<>();
        // loc_1B720 loads all placements below $800 before the first object pass.
        // No camera cursor or second copy of these shared records exists.
        for (int address = pointer; ; address += 6) {
            int x = reader.readU16BE(address);
            if (x >= 0x800) break;
            if (placements.size() == 128) throw new IOException("Competition placement list exceeds native object RAM");
            int rawY = reader.readU16BE(address + 2);
            placements.add(new ObjectSpawn(x, rawY & 0xFFF, reader.readU8(address + 4),
                    reader.readU8(address + 5), rawY >>> 13 & 3,
                    (rawY & 0x8000) != 0, rawY, placements.size()));
        }
        return new S3kCompetitionArenaDefinition(arena, reader.readU16BE(start), reader.readU16BE(start + 2),
                reader.readU16BE(sizes), reader.readU16BE(sizes + 2),
                (short) reader.readU16BE(sizes + 4), reader.readU16BE(sizes + 6), pointer, placements);
    }

    /** SpawnLevelMainSprites copies P1's initial centre to P2 before miniature initialization. */
    public List<S3kCompetitionPlayer> createPlayers(Rom rom, List<S3kCompetitionPlayer.Character> characters) throws IOException {
        return createPlayers(rom, characters, S3kCompetitionResources.LOCKED_ON);
    }

    public List<S3kCompetitionPlayer> createPlayers(Rom rom, List<S3kCompetitionPlayer.Character> characters, S3kCompetitionResources resources) throws IOException {
        if (characters.isEmpty() || characters.size() > 2) throw new IllegalArgumentException("One or two native human slots required");
        List<S3kCompetitionPlayer> players = new ArrayList<>();
        for (int slot = 0; slot < characters.size(); slot++) {
            var character = characters.get(slot);
            var player = new S3kCompetitionPlayer(slot, character, startX, startY,
                    S3kCompetitionPlayer.readSpeeds(rom, character, resources));
            player.setHighPriority(arena == S3kCompetitionSession.Arena.ENDLESS_MINE);
            players.add(player);
        }
        return List.copyOf(players);
    }
}
