package com.openggf.graphics;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.glBindSampler;

/** A draw-local borrowed texture; never placed in a CPU presentation or rewind snapshot. */
record Shadow15Backdrop(int texture, int originX, int originY, int width, int height) {
    Shadow15Backdrop {
        if (texture <= 0 || width <= 0 || height <= 0)
            throw new IllegalArgumentException("A native shadow backdrop needs a texture and positive pixel dimensions");
        Math.addExact(originX, width);
        Math.addExact(originY, height);
    }

    /** The caller supplies a bottom-left-oriented, already shadow/profile-resolved color image. */
    void bind(int[] locations) {
        if (!glIsTexture(texture)) throw new IllegalStateException("Native shadow backdrop texture is unavailable");
        if (glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) != 0) {
            for (int attachment = 0; attachment < glGetInteger(GL_MAX_COLOR_ATTACHMENTS); attachment++) {
                int name = GL_COLOR_ATTACHMENT0 + attachment;
                if (glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, name,
                        GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL_TEXTURE
                        && glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, name,
                        GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) == texture)
                    throw new IllegalStateException("Native shadow backdrop must be distinct from the draw target");
            }
        }
        // A foreign sampler overrides the borrowed texture's own filtering and can
        // interpolate native palette codes or require absent mip levels. Coverage
        // owns and restores this unit's sampler; never mutate the borrowed object.
        glBindSampler(6, 0);
        glActiveTexture(GL_TEXTURE6);
        glBindTexture(GL_TEXTURE_2D, texture);
        if (glGetError() != GL_NO_ERROR) throw new IllegalStateException("Native shadow backdrop must be a 2D texture");
        if (glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH) <= 0
                || glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT) <= 0)
            throw new IllegalStateException("Native shadow backdrop has no allocated color image");
        if (glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER) != GL_NEAREST
                || glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER) != GL_NEAREST)
            throw new IllegalStateException("Native shadow backdrop must use nearest texture filtering");
        glUniform1i(locations[0], 6);
        glUniform2f(locations[1], originX, originY);
        glUniform2f(locations[2], width, height);
    }
}
