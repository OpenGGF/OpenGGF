package com.openggf.graphics;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glGetUniformf;
import static org.lwjgl.opengl.GL20.glGetUniformi;

/**
 * Read-only GPU sampling provenance for the FBZ completion investigation.
 * Call after drawing on the active GL thread. These are the last tilemap draw's
 * uploaded uniforms, not reconstructed camera state or a gameplay mutation.
 * Origin: FBZ remaining visual acceptance, base 51677cdd2 (2026-09-14).
 */
public final class TilemapSamplingDiagnostics {
    private TilemapSamplingDiagnostics() { }

    public static Map<String, byte[]> readTextures(TilemapGpuRenderer renderer, int atlasTexture, int paletteTexture) {
        return Map.of(
                "foreground.rgba", readTexture(GL_TEXTURE_2D, renderer.foregroundTextureForDiagnostics(), GL_RGBA, 4),
                "background.rgba", readTexture(GL_TEXTURE_2D, renderer.backgroundTextureForDiagnostics(), GL_RGBA, 4),
                "lookup.rgba", readTexture(GL_TEXTURE_1D, renderer.lookupTextureForDiagnostics(), GL_RGBA, 4),
                "atlas.indexed", readTexture(GL_TEXTURE_2D, atlasTexture, GL_RED, 1),
                "palette.rgba", readTexture(GL_TEXTURE_2D, paletteTexture, GL_RGBA, 4));
    }

    public static byte[] readIndexedAtlas(int texture) {
        return readTexture(GL_TEXTURE_2D, texture, GL_RED, 1);
    }

    private static byte[] readTexture(int target, int texture, int format, int channels) {
        int previous = glGetInteger(target == GL_TEXTURE_1D ? GL_TEXTURE_BINDING_1D : GL_TEXTURE_BINDING_2D);
        int pack = glGetInteger(GL_PACK_ALIGNMENT);
        ByteBuffer buffer = null;
        try {
            glBindTexture(target, texture);
            int width = glGetTexLevelParameteri(target, 0, GL_TEXTURE_WIDTH);
            int height = target == GL_TEXTURE_1D ? 1 : glGetTexLevelParameteri(target, 0, GL_TEXTURE_HEIGHT);
            int size = Math.multiplyExact(Math.multiplyExact(width, height), channels);
            if (size <= 0 || size > 64 * 1024 * 1024) throw new IllegalStateException("Invalid diagnostic texture size " + size);
            buffer = MemoryUtil.memAlloc(size);
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glGetTexImage(target, 0, format, GL_UNSIGNED_BYTE, buffer);
            byte[] bytes = new byte[size];
            buffer.get(bytes);
            return bytes;
        } finally {
            if (buffer != null) MemoryUtil.memFree(buffer);
            glPixelStorei(GL_PACK_ALIGNMENT, pack);
            glBindTexture(target, previous);
        }
    }

    public static Map<String, Object> capture(TilemapGpuRenderer renderer) {
        TilemapShaderProgram shader = renderer.samplingShaderForDiagnostics();
        if (shader == null) throw new IllegalStateException("Tilemap shader is not initialized");
        int program = shader.getProgramId();
        Map<String, Object> result = new LinkedHashMap<>();
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        result.put("framebuffer_viewport", List.of(viewport[0], viewport[1], viewport[2], viewport[3]));
        for (String name : List.of("WorldOffsetX", "WorldOffsetY", "WindowWidth", "WindowHeight",
                "ViewportOffsetX", "ViewportOffsetY", "ViewportWidth", "ViewportHeight",
                "TilemapWidth", "TilemapHeight", "TilemapRingBaseX", "TilemapRingBaseY",
                "AtlasWidth", "AtlasHeight", "LookupSize", "PerLineScrollSampleYOffsetPx")) {
            int location = glGetUniformLocation(program, name);
            if (location >= 0) result.put(name, glGetUniformf(program, location));
        }
        for (String name : List.of("PriorityPass", "WrapY", "PerLineScroll", "PerColumnVScroll")) {
            int location = glGetUniformLocation(program, name);
            if (location >= 0) result.put(name, glGetUniformi(program, location));
        }
        return Map.copyOf(result);
    }
}
