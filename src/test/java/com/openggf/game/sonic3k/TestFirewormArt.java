package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestFirewormArt {
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void visiblePartsUseNativePaletteOneInBothActs(int act) {
        HeadlessTestFixture.builder().withZoneAndAct(9, act).build();
        var provider = (Sonic3kObjectArtProvider) GameModuleRegistry.getCurrent().getObjectArtProvider();
        for (String key : new String[]{Sonic3kObjectArtKeys.FIREWORM,
                Sonic3kObjectArtKeys.FIREWORM_SEGMENTS}) {
            var sheet = provider.getSheet(key);
            assertNotNull(sheet, key);
            // ObjSlot_Fireworm and ObjDat3_8F9FC both use make_art_tile(...,1,1).
            // Palette 3 belongs only to the invisible placement spawner (ObjDat3_8F9DE).
            assertEquals(1, sheet.getPaletteIndex(), key);
            assertEquals(key.equals(Sonic3kObjectArtKeys.FIREWORM) ? 4 : 8, sheet.getFrameCount());
            for (int frame = 0; frame < sheet.getFrameCount(); frame++) {
                for (var piece : sheet.getFrame(frame).pieces()) {
                    assertEquals(0, piece.paletteIndex(), "native mappings add no palette offset");
                }
            }
        }
    }
}
