package com.openggf.mods.scene.host;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomManager;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSceneRomLibrary {
    @Test
    void allSevenRomSubsetsBorrowActiveAndOwnOtherViewsWithoutSwitchingGames() throws Exception {
        List<String> codes = List.of("s1", "s2", "s3k");
        List<GameId> games = List.of(GameId.S1, GameId.S2, GameId.S3K);
        List<RomIdentity> identities = List.of(RomIdentity.S1, RomIdentity.S2, RomIdentity.S3K);
        for (int subset = 1; subset < 8; subset++) {
            RomManager roms = mock(RomManager.class);
            List<String> expected = new ArrayList<>();
            int active = Integer.numberOfTrailingZeros(subset);
            GameModule module = mock(GameModule.class);
            when(module.getGameId()).thenReturn(games.get(active));
            Rom borrowed = Rom.fromReader(new RomByteReader(new byte[] {(byte) (active + 1)}), "active");
            for (int i = 0; i < 3; i++) {
                boolean present = (subset & 1 << i) != 0;
                when(roms.isLogicalRomAvailable(identities.get(i))).thenReturn(present);
                if (present) {
                    expected.add(codes.get(i));
                    when(roms.openLogicalRom(identities.get(i)))
                            .thenReturn(new RomByteReader(new byte[] {(byte) (i + 1)}));
                }
            }
            SceneRomLibrary library = new SceneRomLibrary(module, borrowed, roms);
            assertEquals(expected, library.availableGames(), "subset " + subset);
            assertEquals(codes.get(active), library.activeGame());
            assertSame(borrowed, library.sourceRom(codes.get(active)));
            for (int i = 0; i < 3; i++) {
                if ((subset & 1 << i) != 0) {
                    assertEquals(i + 1, library.rom(codes.get(i)).read(0, 1)[0]);
                } else {
                    assertNull(library.rom(codes.get(i)));
                    assertNull(library.sourceRom(codes.get(i)));
                }
            }
            List<Rom> owned = expected.stream().filter(code -> !code.equals(codes.get(active)))
                    .map(library::sourceRom).toList();
            assertThrows(IllegalArgumentException.class, () -> library.rom("other"));
            library.close();
            library.close();
            assertTrue(borrowed.isOpen());
            assertTrue(owned.stream().noneMatch(Rom::isOpen));
            assertThrows(IllegalStateException.class, library::availableGames);
            verify(roms, never()).getRom();
            verify(roms, never()).getSecondaryRom(anyString());
            borrowed.close();
        }
    }

    @Test
    void unreadableSecondaryRomDoesNotRemoveHealthyActiveContent() throws Exception {
        RomManager roms = mock(RomManager.class);
        when(roms.isLogicalRomAvailable(RomIdentity.S2)).thenReturn(true);
        when(roms.openLogicalRom(RomIdentity.S2)).thenThrow(new java.io.IOException("unreadable"));
        GameModule module = mock(GameModule.class);
        when(module.getGameId()).thenReturn(GameId.S1);
        try (Rom active = Rom.fromReader(new RomByteReader(new byte[] {1}), "active");
                SceneRomLibrary library = new SceneRomLibrary(module, active, roms)) {
            assertEquals(List.of("s1"), library.availableGames());
            assertNull(library.rom("s2"));
        }
    }
}
