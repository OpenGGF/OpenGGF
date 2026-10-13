package com.openggf.game.sonic3k.ending;

import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCreditsState {
    private RomByteReader rom() throws Exception { return RomByteReader.fromRom(RomManager.getInstance().getRom()); }

    @Test void restoringPrecreditsZeroRamClearsAFormerlyPopulatedNametable() throws Exception {
        var state=new S3kCreditsState();var saved=ByteBuffer.allocate(state.snapshotBytes());state.captureTo(saved);
        state.initialize(rom(),PlayerCharacter.KNUCKLES,-2,false);
        for(int i=0;i<200&&state.rowIndex()==0;i++)state.step(rom());
        assertEquals(0xA34A,state.tile(3,30));
        state.restoreFrom(saved.flip());
        assertEquals(0,state.routine());assertEquals(7*Integer.BYTES,state.snapshotBytes());
        for(int y=0;y<32;y++)for(int x=0;x<64;x++)assertEquals(0,state.tile(x,y));
    }

    @Test void nativeTablesSelectAllLockedOnCharacterProgressAndRegionBranches() throws Exception {
        int[][] delays = {{750, 660, 0}, {750, 750, 0}, {165, 120, 120}};
        int[][] speeds = {{0xCE, 0xCE, 0xCF}, {0xCE, 0xCE, 0xCD}, {0xC8, 0xBC, 0xBC}};
        PlayerCharacter[] characters = {PlayerCharacter.SONIC_ALONE, PlayerCharacter.TAILS_ALONE, PlayerCharacter.KNUCKLES};
        for (int c = 0; c < characters.length; c++) for (int progress = 0; progress < 3; progress++) {
            var state = new S3kCreditsState(); state.initialize(rom(), characters[c], progress * 2 - 2, false);
            assertEquals(delays[c][progress], state.delay()); assertEquals(speeds[c][progress] << 8, state.speed());
        }
        var pal = new S3kCreditsState(); pal.initialize(rom(), PlayerCharacter.SONIC_AND_TAILS, 0, true);
        assertEquals(413, pal.delay()); assertEquals(0xF500, pal.speed());
    }

    @Test void delayIsSignedPredecrementAndDoesNotScrollOnItsCompletionPass() throws Exception {
        var state = new S3kCreditsState(); state.initialize(rom(), PlayerCharacter.SONIC_ALONE, 2, false);
        assertFalse(state.step(rom())); assertEquals(4, state.routine()); assertEquals(0, state.scroll());
        assertFalse(state.step(rom())); assertEquals(0xCF00, state.scroll());
    }

    @Test void firstNativeTextRowUsesRomGlyphsPositionAndYellowRolePalette() throws Exception {
        var state = new S3kCreditsState(); state.initialize(rom(), PlayerCharacter.KNUCKLES, -2, false);
        for (int i = 0; i < 200 && state.rowIndex() == 0; i++) state.step(rom());
        assertEquals(2, state.rowIndex()); assertEquals(2, state.clearedRows());
        // byte_5BCD2: $0F07 / EXECUTIVE PRODUCER. E starts at row30, column3.
        assertEquals(0xA34A, state.tile(3, 30)); assertEquals(0xA353, state.tile(4, 30));
        assertEquals(0xA34C, state.tile(3, 31)); assertEquals(0xA354, state.tile(4, 31));
    }

    @Test void bothTextPartsFinishAndTheNametableReplaysFromTheSameSnapshot() throws Exception {
        var state = new S3kCreditsState(); state.initialize(rom(), PlayerCharacter.TAILS_ALONE, 2, false);
        for (int i = 0; i < 1100; i++) state.step(rom());
        var saved = ByteBuffer.allocate(S3kCreditsState.SNAPSHOT_BYTES); state.captureTo(saved);
        for (int i = 0; i < 240; i++) state.step(rom());
        var first = ByteBuffer.allocate(S3kCreditsState.SNAPSHOT_BYTES); state.captureTo(first);
        state.restoreFrom(ByteBuffer.wrap(saved.array()));
        for (int i = 0; i < 240; i++) state.step(rom());
        var replay = ByteBuffer.allocate(S3kCreditsState.SNAPSHOT_BYTES); state.captureTo(replay);
        assertArrayEquals(first.array(), replay.array());
        int completionEdges = 0;
        for (int i = 0; i < 9000 && !state.complete(); i++) if (state.step(rom())) completionEdges++;
        assertTrue(state.complete()); assertEquals(4, state.partIndex()); assertEquals(1, completionEdges);
        assertFalse(state.step(rom()), "completion is not repeatedly published");
    }

    @Test void actualNativePostCreditPointersSelectEveryCharacterAndEmeraldBranch() throws Exception {
        int[][] initializers={{0x5F05C,0x5F05C,0x5F05C},{0x5F05C,0x5F05C,0x5F05C},
                {0x5F0C4,0x5F0A6,0x5F0A6}};
        int[][] terminals={{0x5F0E2,0x5F106,0x5F116},{0x5F0E2,0x5F106,0x5F116},
                {0x5F126,0x5F0DA,0x5F116}};
        PlayerCharacter[] characters={PlayerCharacter.SONIC_ALONE,PlayerCharacter.TAILS_ALONE,PlayerCharacter.KNUCKLES};
        for(int c=0;c<3;c++)for(int progress=0;progress<3;progress++){
            var dispatch=S3kPostCreditsData.resolve(rom(),characters[c],progress*2-2);
            assertEquals(initializers[c][progress],dispatch.bannerInitializer());
            assertEquals(terminals[c][progress],dispatch.afterBanner());
        }
    }

    @Test void creditsFontIsTheNativeSixtyFiveTileNemesisSheet() throws Exception {
        var sheet=S3kEndingArt.font(RomManager.getInstance().getRom());
        assertEquals(65,sheet.getPatterns().length);assertEquals(65,sheet.getFrameCount());
        assertEquals(64,sheet.getFrame(64).pieces().getFirst().tileIndex());
    }
}
