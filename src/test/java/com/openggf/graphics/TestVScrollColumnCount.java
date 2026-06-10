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

        assertTrue(parallax.contains("uniform float ActiveDisplayWidth;"),
                "Parallax shader must receive the logical display width separately from framebuffer width");
        assertTrue(parallax.contains("float activeDisplayWidth = ActiveDisplayWidth > 0.0 ? ActiveDisplayWidth : ScreenWidth;"),
                "Parallax shader must fall back safely when the logical width uniform is unavailable");
        assertTrue(parallax.contains("float gameX = floor((viewportX * activeDisplayWidth) / ScreenWidth);"),
                "Parallax shader must convert physical framebuffer X to logical game X before sampling HScroll");
        assertTrue(parallax.contains("ceil(activeDisplayWidth / 16.0)"),
                "Parallax shader must sample per-column VScroll using the logical active display width");
        assertTrue(tilemap.contains("uniform float VScrollColumnCount;"),
                "Tilemap shader must receive the column texture entry count separately from the FBO width");
        assertTrue(tilemap.contains("float vScrollColumnCount = VScrollColumnCount > 0.0"),
                "Tilemap shader must prefer the explicit column count: WindowWidth is the BG FBO width "
                        + "(e.g. 512 at native 320) and mismatches the 20-texel column texture");
        assertFalse(parallax.contains("float gameX = floor(viewportX);"),
                "Parallax shader must not treat physical framebuffer pixels as logical game pixels");
        assertFalse(parallax.contains("viewportX * 320.0"),
                "Parallax shader must not collapse widescreen X coordinates into native 320px space");
        assertFalse(parallax.contains("/ 20.0"));
        assertFalse(tilemap.contains("/ 20.0"));
    }

    @Test
    void tilemapRendererSuppliesColumnTextureEntryCountToShader() throws IOException {
        String renderer = Files.readString(Path.of("src/main/java/com/openggf/graphics/TilemapGpuRenderer.java"));

        assertTrue(renderer.contains("setVScrollColumnCount(columnVScrollBuffer.getEntryCount())"),
                "TilemapGpuRenderer must pass the VScroll column texture's actual entry count to the shader");
    }

    @Test
    void backgroundRendererSuppliesLogicalDisplayWidthToParallaxShader() throws IOException {
        String renderer = Files.readString(Path.of("src/main/java/com/openggf/level/render/BackgroundRenderer.java"));

        assertTrue(renderer.contains("setActiveDisplayWidth((float) GameServices.configuration()"),
                "BackgroundRenderer must pass the logical configured screen width to the parallax shader");
        assertTrue(renderer.contains("SonicConfiguration.SCREEN_WIDTH_PIXELS"),
                "The active display width should come from the logical screen-width configuration");
    }
}
