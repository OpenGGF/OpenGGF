package com.openggf.graphics;

import static org.lwjgl.opengl.GL11.GL_NONE;
import static org.lwjgl.opengl.GL30.*;

/**
 * Context-lifetime scratch images for {@link NativeSpriteOwnership} scopes. Creating and
 * deleting a stencil renderbuffer and backup framebuffer for every native SAT draw cost
 * several percent of main-thread time (2026-11-13 level sweep). The names live with the
 * owning {@link GraphicsManager}'s GL context: storage is respecified only when the
 * requested format, sample count or size changes, and {@link GraphicsManager#cleanup()}
 * deletes them. A scope still clears or saves every pixel it uses, so no content carries
 * from one scope to the next.
 */
final class NativeSpriteScratch {
    private int renderbuffer;
    private int framebuffer;
    private int format;
    private int samples;
    private int width;
    private int height;
    private int attachment = GL_NONE;

    /** Binds and returns the scratch renderbuffer with exactly this storage. */
    int renderbuffer(int format, int samples, int width, int height) {
        if (renderbuffer == 0) {
            renderbuffer = glGenRenderbuffers();
            this.format = 0;
        }
        glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
        if (this.format != format || this.samples != samples || this.width != width || this.height != height) {
            if (samples > 0) glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, format, width, height);
            else glRenderbufferStorage(GL_RENDERBUFFER, format, width, height);
            this.format = format;
            this.samples = samples;
            this.width = width;
            this.height = height;
        }
        return renderbuffer;
    }

    /**
     * Binds the backup framebuffer to the draw target with the scratch renderbuffer at
     * {@code attachmentPoint}; a previous, different attachment point is detached first.
     */
    void bindBackup(int attachmentPoint) {
        if (framebuffer == 0) {
            framebuffer = glGenFramebuffers();
            attachment = GL_NONE;
        }
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        if (attachment != attachmentPoint) {
            if (attachment != GL_NONE) glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, attachment, GL_RENDERBUFFER, 0);
            glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, attachmentPoint, GL_RENDERBUFFER, renderbuffer);
            attachment = attachmentPoint;
        }
    }

    int backupFramebuffer() { return framebuffer; }

    /** Deletes the names on the current context; the next scope recreates them. */
    void delete() {
        if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
        if (renderbuffer != 0) glDeleteRenderbuffers(renderbuffer);
        forget();
    }

    /** Drops names whose context is gone (or never existed) without issuing GL calls. */
    void forget() {
        framebuffer = 0;
        renderbuffer = 0;
        format = samples = width = height = 0;
        attachment = GL_NONE;
    }

    /** Test observation of name reuse. */
    int renderbufferName() { return renderbuffer; }
}
