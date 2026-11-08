package com.openggf.game.sonic1;

import com.openggf.data.RomByteReader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1CreditsDemoLoadSelection {
    @Test void allNativeDemoRecipesAreIndependentlySelectedRomOperations() throws Exception {
        var rom = RomByteReader.fromRom(TestEnvironment.currentRom());
        assertEquals("sha256:1bfcf6efe647cbab9512cfd74b92e106e866ba66717ff13a56095c50d85f2db0", Sonic1CreditsDemoLoad.inputs(rom, 0).fingerprint("level_setup"));
        var unique = new HashSet<String>();
        for (int page = 0; page < 8; page++) unique.add(Sonic1CreditsDemoLoad.inputs(rom, page).fingerprint("level_setup"));
        assertEquals(8, unique.size(), "the two GHZ demos have different ROM start/input selections");
        assertThrows(IllegalArgumentException.class, () -> Sonic1CreditsDemoLoad.inputs(rom, 8));
    }
}
