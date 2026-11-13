package com.openggf.game.sonic1.credits;

import com.openggf.data.RomByteReader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1CreditsScreenLoadSelection {
    @Test void nativeFontPageAndNextDemoSourcesMatchTheIndependentRetailVector() throws Exception {
        var rom = RomByteReader.fromRom(TestEnvironment.currentRom());
        var first = Sonic1CreditsScreenLoad.inputs(rom, 0).fingerprint("level_setup");
        assertEquals("sha256:2cc3baa64021206ae0768093d53508edca93c1513854977038a9005c4c6da7c9", first);
        assertNotEquals(first, Sonic1CreditsScreenLoad.inputs(rom, 1).fingerprint("level_setup"));
        assertThrows(IllegalArgumentException.class, () -> Sonic1CreditsScreenLoad.inputs(rom, 9));
    }
}
