package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Native picture check for all ten Collision Chaos stage programs, see {@link CczNativePictureCheck}. */
@EnabledOnOs(OS.LINUX)
class TestCczBackgroundPicture {
    @Test
    void backgroundMatchesTheNativeFramesInAllTenStages() throws Exception {
        CczNativePictureCheck.runAll(CczNativePictureCheck.STAGES);
    }
}
