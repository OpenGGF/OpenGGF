package com.openggf.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class TestVScrollColumnCount {
    private static int columns(int width) { return (width + 15) / 16; }

    @Test void nativeIs20() { assertEquals(20, columns(320)); }
    @Test void widescreen() {
        assertEquals(25, columns(400));
        assertEquals(33, columns(528));
        assertEquals(50, columns(800));
    }

    @Test
    void shadersDerivePerColumnVScrollCountFromVisibleWidth() throws IOException {
        String parallax = Files.readString(Path.of("src/main/resources/shaders/shader_parallax_bg.glsl"));
        String tilemap = Files.readString(Path.of("src/main/resources/shaders/shader_tilemap.glsl"));

        assertTrue(parallax.contains("ceil(ScreenWidth / 16.0)"),
                "Parallax shader must sample per-column VScroll using the active viewport width");
        assertTrue(tilemap.contains("ceil(WindowWidth / 16.0)"),
                "Tilemap shader must sample per-column VScroll using the active render window width");
        assertFalse(parallax.contains("viewportX * 320.0"),
                "Parallax shader must not collapse widescreen X coordinates into native 320px space");
        assertFalse(parallax.contains("/ 20.0"));
        assertFalse(tilemap.contains("/ 20.0"));
    }
}
