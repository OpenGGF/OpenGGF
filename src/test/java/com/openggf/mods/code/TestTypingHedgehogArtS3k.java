package com.openggf.mods.code;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

/** Sonic 3 &amp; Knuckles rail acts can draw every frame Typing of the Hedgehog's tables name: {@link TypingHedgehogArtTables}. */
@RequiresRom(SonicGame.SONIC_3K)
class TestTypingHedgehogArtS3k {
    @Test void everyRailActDrawsItsCastPropsAndBoss() throws Exception {
        TypingHedgehogArtTables.check(SonicGame.SONIC_3K, "s3k");
    }
}
