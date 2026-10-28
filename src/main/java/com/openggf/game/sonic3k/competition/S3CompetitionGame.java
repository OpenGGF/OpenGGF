package com.openggf.game.sonic3k.competition;

import com.openggf.audio.GameSound;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.Game;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.level.Level;
import com.openggf.level.resources.LevelResourcePlan;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.rings.RingMappingFrames;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.util.PatternDecompressor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Original S3's five Competition arenas, through the ordinary ROM resource decoder.
 * This is not a standalone one-player campaign loader. Native LoadSolids and
 * LoadLevelLoadBlock (s3.asm:7470-7544) use original cartridge pointers and two
 * non-interleaved collision banks. No scanner or global address state participates.
 */
public final class S3CompetitionGame extends Game {
    public static final int LEVEL_LOAD_BLOCK = 0x5AB0C, SOLID_INDEXES = 0x746A0;
    public static final Sonic3kLevel.CollisionProfile COLLISION =
            new Sonic3kLevel.CollisionProfile(0x706A0, 0x726A0, 0x704A0, 512,
                    Sonic3kLevel.CollisionIndexEncoding.UNSIGNED_WORD);
    private final Rom rom;
    private final RomByteReader reader;
    private final S3kCompetitionResources resources = S3kCompetitionResources.ORIGINAL_S3;

    public S3CompetitionGame(Rom rom) throws IOException {
        if (S3kCompetitionResources.identify(rom) != resources)
            throw new IOException("Original Sonic 3 competition requires its original cartridge view");
        this.rom = rom;
        reader = RomByteReader.fromRom(rom);
        COLLISION.validate(rom);
    }

    private static int nativeLevelIndex(int levelIndex) {
        // Stock LevelData uses the existing $C0 S3K namespace; direct ROM-level callers use 0-based indices.
        int nativeIndex = levelIndex >= 0xC0 ? levelIndex - 0xC0 : levelIndex;
        S3kCompetitionSession.Arena.forZone(nativeIndex / 2);
        return nativeIndex;
    }

    @Override public Level loadLevel(int levelIndex) throws IOException {
        levelIndex = nativeLevelIndex(levelIndex);
        int zone = levelIndex / 2, act = levelIndex % 2;
        var arena = S3kCompetitionSession.Arena.forZone(zone);
        if (act != 0) throw new IOException("Competition's alternate preview row is not a playable act");
        int record = LEVEL_LOAD_BLOCK + levelIndex * 24;
        int[] sources = new int[6];
        for (int i = 0; i < sources.length; i++) sources[i] = reader.readU32BE(record + i * 4) & 0xFFFFFF;
        var plan = LevelResourcePlan.builder();
        plan.addPatternOp(LoadOp.kosinskiMBase(sources[0]));
        if (sources[1] != sources[0] && sources[1] != 0) plan.addPatternOp(LoadOp.kosinskiMAppend(sources[1]));
        plan.addChunkOp(LoadOp.kosinskiBase(sources[2]));
        if (sources[3] != sources[2] && sources[3] != 0) plan.addChunkOp(LoadOp.kosinskiAppend(sources[3]));
        plan.addBlockOp(LoadOp.kosinskiBase(sources[4]));
        if (sources[5] != sources[4] && sources[5] != 0) plan.addBlockOp(LoadOp.kosinskiAppend(sources[5]));
        int collision = reader.readU32BE(SOLID_INDEXES + levelIndex * 4);
        if (collision < 0 || (long) collision + 0xC00 > reader.size())
            throw new IOException("Original S3 collision banks exceed the supplied cartridge");
        int layout = reader.readU32BE(resources.layouts() + levelIndex * 4);
        var definition = S3kCompetitionArenaDefinition.load(rom, arena, resources);
        int palette = resources.at(0xA99DC + (zone - 0xE) * 96);
        // The shared archive continues into HUD/text art; the ring owner consumes its first 14 tiles.
        var patterns = java.util.Arrays.copyOf(PatternDecompressor.nemesis(rom, 0x15F2EC), 14);
        var ringSheet = new RingSpriteSheet(patterns,
                RingMappingFrames.fromMappings(S3kSpriteDataLoader.loadMappingFrames(reader, 0x190DE)),
                1, 8, 5, 4, 4);
        List<RingSpawn> rings = new ArrayList<>();
        int ringAddress = reader.readU32BE(0x5E198 + levelIndex * 4);
        for (int cursor = ringAddress, index = 0; ; cursor += 4, index++) {
            int x = reader.readU16BE(cursor), y = reader.readU16BE(cursor + 2);
            if (x == 0xFFFF) break;
            if (index != 0 || x != 0 || y != 0) rings.add(new RingSpawn(x, y, index));
        }
        return new Sonic3kLevel(COLLISION, rom, zone, plan.build(), collision, collision + 0x600,
                false, layout, resources.sizes() + levelIndex * 8,
                resources.at(0xA8A3C), palette, null, definition.placements(), rings,
                ringSheet, true);
    }

    @Override public boolean isCompatible() { return true; }
    @Override public String getIdentifier() { return "Sonic3Competition"; }
    @Override public List<String> getTitleCards() { return List.of(); }
    @Override public int getMusicId(int index) {
        index = nativeLevelIndex(index);
        return com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile.SONIC_3
                .musicId(reader.readU8(0x4680 + index));
    }
    @Override public Map<GameSound, Integer> getSoundMap() { return Map.of(); }
    @Override public boolean canRelocateLevels() { return false; }
    @Override public boolean canSave() { return false; }
    @Override public boolean relocateLevels(boolean unsafe) { return false; }
    @Override public boolean save(int index, Level level) { return false; }
    @Override public int[] getBackgroundScroll(int index, int x, int y) { return new int[]{0, 0}; }
    @Override public Rom getRom() { return rom; }
}
