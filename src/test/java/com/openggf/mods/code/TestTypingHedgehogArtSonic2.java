package com.openggf.mods.code;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

/** Sonic 2 rail acts can draw every cast and prop frame Typing of the Hedgehog's tables name: {@link TypingHedgehogArtTables}. */
@RequiresRom(SonicGame.SONIC_2)
class TestTypingHedgehogArtSonic2 {
    @Test void everyRailActDrawsItsCastAndProps() throws Exception {
        TypingHedgehogArtTables.check(SonicGame.SONIC_2, "s2");
    }
}
