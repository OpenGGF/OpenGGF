package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@RequiresRom(SonicGame.SONIC_3K)
class TestFbzChainLinkArtWord {
    @Test
    void chainMappingsApplyTheCompleteArtTileWordBeforeRendering() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 0)
                .build();

        Sonic3kObjectArtProvider provider = (Sonic3kObjectArtProvider)
                GameModuleRegistry.getCurrent().getObjectArtProvider();
        ObjectSpriteSheet sheet = provider.getSheet(Sonic3kObjectArtKeys.FBZ_CHAIN_LINK);
        assertNotNull(sheet);
        assertEquals(15, sheet.getFrameCount());
        assertEquals(sheet.getFrame(13), sheet.getFrame(14),
                "the last two table entries share word_3B080");

        SpriteMappingPiece link = sheet.getFrame(0).pieces().get(0);
        assertEquals(3, sheet.getFrame(0).pieces().size());
        assertEquals(1, link.widthTiles());
        assertEquals(2, link.heightTiles());
        assertEquals(1, link.paletteIndex(),
                "$E0EE + $4379 wraps to palette line 1");
        assertFalse(link.priority(),
                "$E0EE + $4379 wraps to $2467 and clears the priority bit");
        assertSame(GameServices.level().getCurrentLevel().getPattern(0x467),
                sheet.getPatterns()[link.tileIndex()]);

        SpriteMappingPiece handle = sheet.getFrame(0).pieces().get(1);
        assertEquals(2, handle.widthTiles());
        assertEquals(2, handle.heightTiles());
        assertEquals(2, handle.paletteIndex());
        assertFalse(handle.priority());
        assertSame(GameServices.level().getCurrentLevel().getPattern(0x379),
                sheet.getPatterns()[handle.tileIndex()]);
    }
}
