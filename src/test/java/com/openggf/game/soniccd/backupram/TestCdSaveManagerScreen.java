package com.openggf.game.soniccd.backupram;

import com.openggf.game.GameServices;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TestCdSaveManagerScreen {
    @Test
    void drawAndClearColorWorkWithoutAnOpenGlContext() {
        var graphics = GameServices.graphics();
        graphics.initHeadless();
        graphics.setProjectionMatrixBuffer(new Matrix4f().get(new float[16]));
        var screen = new CdSaveManagerScreen(null);
        screen.initialize();

        assertAll(
                () -> assertDoesNotThrow(screen::draw),
                () -> assertDoesNotThrow(screen::setClearColor));
    }
}
