package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openggf.data.Rom;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.io.ModInputLimits;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.tests.RomTestUtils;

import java.io.File;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TestRomArtMaterializer {

    private static Rom rom;

    @BeforeAll
    static void loadRom() {
        File romFile = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(romFile != null, "s2 ROM not available");
        Rom opened = new Rom();
        assumeTrue(opened.open(romFile.getAbsolutePath()), "s2 ROM failed to open");
        rom = opened;
    }

    @AfterAll
    static void closeRom() {
        if (rom != null) {
            rom.close();
        }
    }

    @Test
    void tailsUncompressedArtWithDplcMaterializes() {
        RomArtRequest tails = new RomArtRequest(
                Sonic2Constants.ART_UNC_TAILS_ADDR, RomArtCompression.UNCOMPRESSED,
                Sonic2Constants.ART_UNC_TAILS_SIZE,
                Sonic2Constants.MAP_UNC_TAILS_ADDR,
                Sonic2Constants.MAP_R_UNC_TAILS_ADDR,
                0, 1);
        Map<String, ObjectSpriteSheet> out = RomArtMaterializer.materialize(
                "owner", Map.of("owner:tails", tails), rom, ModInputLimits.production());
        ObjectSpriteSheet sheet = out.get("owner:tails");
        assertNotNull(sheet);
        // Tails has dozens of mapping frames; DPLC flattening must preserve the frame count.
        assertTrue(sheet.getFrameCount() > 10);
        assertTrue(sheet.getPatterns().length > 0);
    }

    @Test
    void nemesisCompressedArtMaterializes() {
        // Buzzer (Obj4B, EHZ flying wasp): Nemesis-compressed art + uncompressed mapping table,
        // no DPLC. Same ROM addresses Sonic2ObjectArt.loadBuzzerSheet() uses.
        RomArtRequest buzzer = new RomArtRequest(
                Sonic2Constants.ART_NEM_BUZZER_ADDR, RomArtCompression.NEMESIS, 0,
                Sonic2Constants.MAP_UNC_BUZZER_ADDR, 0, 0, 1);
        Map<String, ObjectSpriteSheet> out = RomArtMaterializer.materialize(
                "owner", Map.of("owner:buzzer", buzzer), rom, ModInputLimits.production());
        ObjectSpriteSheet sheet = out.get("owner:buzzer");
        assertNotNull(sheet);
        assertTrue(sheet.getPatterns().length > 0);
        assertTrue(sheet.getFrameCount() >= 1);
    }

    @Test
    void garbageAddressFailsWithStructuredError() {
        // An address inside the ROM but pointing at non-Nemesis data: expect
        // ModRegistrationException with code MOD_ROM_ART_INVALID mentioning the key.
        RomArtRequest garbage = new RomArtRequest(0x000100, RomArtCompression.NEMESIS,
                0, 0x000200, 0, 0, 1);
        ModRegistrationException ex = assertThrows(ModRegistrationException.class,
                () -> RomArtMaterializer.materialize("owner",
                        Map.of("owner:bad", garbage), rom, ModInputLimits.production()));
        assertTrue(ex.getMessage().contains("owner:bad"));
        assertEquals("MOD_ROM_ART_INVALID", ex.findingCode());
    }

    @Test
    void patternCapEnforced() {
        // Lower the sheet-pattern cap below Tails' tile count via the lowering builder,
        // expect MOD_ROM_ART_INVALID.
        ModInputLimits tight = ModInputLimits.loweringBuilder().maxSheetPatterns(8).build();
        RomArtRequest tails = new RomArtRequest(
                Sonic2Constants.ART_UNC_TAILS_ADDR, RomArtCompression.UNCOMPRESSED,
                Sonic2Constants.ART_UNC_TAILS_SIZE,
                Sonic2Constants.MAP_UNC_TAILS_ADDR, 0, 0, 1);
        ModRegistrationException ex = assertThrows(ModRegistrationException.class,
                () -> RomArtMaterializer.materialize("owner",
                        Map.of("owner:tails", tails), rom, tight));
        assertEquals("MOD_ROM_ART_INVALID", ex.findingCode());
    }
}
