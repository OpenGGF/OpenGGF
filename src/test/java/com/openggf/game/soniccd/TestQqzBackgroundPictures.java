package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Native background check for R51A-R53D (both screenshots of each): see {@link QqzPictureSupport}. */
@EnabledOnOs(OS.LINUX)
class TestQqzBackgroundPictures {
    @Test
    void backgroundsMatchTheNativeScreenshots() throws Exception {
        QqzPictureSupport.assertMatches(QqzPictureSupport.run(
                "R51A", "R51B", "R51C", "R51D", "R52A", "R52B", "R52C", "R52D", "R53C", "R53D"));
    }
}
