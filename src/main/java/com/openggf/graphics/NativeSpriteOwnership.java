package com.openggf.graphics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.GL_TEXTURE_2D_MULTISAMPLE;
import static org.lwjgl.opengl.GL32.GL_TEXTURE_BINDING_2D_MULTISAMPLE;
import static org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33.glBindSampler;

/**
 * One native SAT draw's opaque-source coverage. Owns all temporary GL objects and
 * preserves an existing stencil image instead of borrowing its contents as scratch.
 * Resources exist only while the queued begin/end pair is executing on its context.
 */
final class NativeSpriteOwnership implements AutoCloseable {
    /** One contiguous source-order span; 255 remains the unclaimed stencil value. */
    record Layer<T>(int rank, boolean ghost, List<T> entries) { }

    @FunctionalInterface
    interface LayerSelection {
        void select(int rank, boolean ghost);
    }

    /** Plan completely before opening a scope or emitting any native commands. */
    static <T> List<Layer<T>> layers(List<T> frontToBack, Predicate<T> isGhost) {
        java.util.Objects.requireNonNull(frontToBack, "native layers");
        java.util.Objects.requireNonNull(isGhost, "ghost predicate");
        if (frontToBack.isEmpty()) return List.of();
        var layers = new ArrayList<Layer<T>>();
        int start = 0;
        boolean ghost = isGhost.test(frontToBack.getFirst()), containsGhost = ghost;
        for (int index = 1; index < frontToBack.size(); index++) {
            boolean nextGhost = isGhost.test(frontToBack.get(index));
            containsGhost |= nextGhost;
            if (nextGhost == ghost) continue;
            addLayer(layers, ghost, frontToBack.subList(start, index));
            start = index;
            ghost = nextGhost;
        }
        if (!containsGhost) return List.of();
        addLayer(layers, ghost, frontToBack.subList(start, frontToBack.size()));
        return List.copyOf(layers);
    }

    private static <T> void addLayer(List<Layer<T>> layers, boolean ghost, List<T> entries) {
        if (layers.size() >= 255)
            throw new IllegalArgumentException("Native ghost replay exceeds 255 ordered spans");
        layers.add(new Layer<>(layers.size(), ghost, List.copyOf(entries)));
    }

    /** Real spans own source coverage first; ghosts then blend back-to-front without owning it. */
    static <T> void replayLayers(List<Layer<T>> layers, LayerSelection selection, Consumer<List<T>> emitter) {
        for (Layer<T> layer : layers) if (!layer.ghost()) {
            selection.select(layer.rank(), false);
            emitter.accept(layer.entries());
        }
        for (int index = layers.size() - 1; index >= 0; index--) {
            Layer<T> layer = layers.get(index);
            if (!layer.ghost()) continue;
            selection.select(layer.rank(), true);
            emitter.accept(layer.entries().reversed());
        }
    }

    /** Deferred commands live outside the published GraphicsManager declaration. */
    static final class Commands {
        private NativeSpriteOwnership scope;
        private final java.util.function.Consumer<NativeSpriteOwnership> activate;
        final GLCommandable begin;
        final GLCommandable end = new GLCommandable() {
            @Override public void execute(int x, int y, int width, int height) { close(); }
            @Override public void discard() { close(); }
            @Override public void unwindAfterFailure(int x, int y, int width, int height) { close(); }
        };

        Commands(GraphicsManager graphics, java.util.function.Consumer<NativeSpriteOwnership> activate) {
            this(graphics, activate, false);
        }

        Commands(GraphicsManager graphics, java.util.function.Consumer<NativeSpriteOwnership> activate, boolean layered) {
            this.activate = activate;
            begin = (x, y, width, height) -> {
                if (graphics.nativeSpriteOwnershipActive()) throw new IllegalStateException("Native sprite scope already executing");
                if (graphics.getSpritePriorityShaderProgram() == null) throw new IllegalStateException("Native sprite priority shader unavailable");
                scope = layered ? NativeSpriteOwnership.openLayered(graphics) : NativeSpriteOwnership.open(graphics);
                activate.accept(scope);
            };
        }

        GLCommandable layer(int rank, boolean ghost) {
            return (x, y, width, height) -> {
                if (scope == null) throw new IllegalStateException("Native sprite layer has no executing scope");
                scope.selectLayer(rank, ghost);
            };
        }

        private void close() {
            if (scope == null) return;
            try { scope.close(); }
            finally { activate.accept(null); scope = null; }
        }
    }

    private final GraphicsManager graphics;
    private final boolean layered;
    private final int drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
    private final int renderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING);
    private final int program = glGetInteger(GL_CURRENT_PROGRAM);
    private final int vertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
    private final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
    private final int shadowSampler = glGetIntegeri(GL_SAMPLER_BINDING, 6);
    private final int[] textures = new int[7];
    private final int[] viewport = integers(GL_VIEWPORT, 4);
    private final int[] scissor = integers(GL_SCISSOR_BOX, 4);
    private final boolean scissorEnabled = glIsEnabled(GL_SCISSOR_TEST);
    private final boolean stencilEnabled = glIsEnabled(GL_STENCIL_TEST);
    private final boolean depthEnabled = glIsEnabled(GL_DEPTH_TEST);
    private final boolean depthWrite = glGetBoolean(GL_DEPTH_WRITEMASK);
    private final boolean blendEnabled = glIsEnabled(GL_BLEND);
    private final int blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
    private final int blendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
    private final int blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
    private final int blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
    private final int blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
    private final int blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
    private final int clearStencil = glGetInteger(GL_STENCIL_CLEAR_VALUE);
    private final Face front = Face.capture(false);
    private final Face back = Face.capture(true);
    private int ownedStencil;
    private int backupFramebuffer;
    private int originalDepthRenderbuffer;
    private boolean attached;
    private boolean backedUp;
    private boolean contentsChanged;
    private boolean closed;

    private NativeSpriteOwnership(GraphicsManager graphics) {
        this(graphics, false);
    }

    private NativeSpriteOwnership(GraphicsManager graphics, boolean layered) {
        this.graphics = graphics;
        this.layered = layered;
        for (int unit = 0; unit < textures.length; unit++) {
            glActiveTexture(GL_TEXTURE0 + unit);
            textures[unit] = glGetInteger(GL_TEXTURE_BINDING_2D);
        }
        glActiveTexture(activeTexture);
    }

    static NativeSpriteOwnership open(GraphicsManager graphics) {
        NativeSpriteOwnership scope = new NativeSpriteOwnership(graphics);
        try {
            scope.begin();
            return scope;
        } catch (RuntimeException | Error failure) {
            try { scope.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static NativeSpriteOwnership openLayered(GraphicsManager graphics) {
        NativeSpriteOwnership scope = new NativeSpriteOwnership(graphics, true);
        try {
            scope.begin();
            return scope;
        } catch (RuntimeException | Error failure) {
            try { scope.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void begin() {
        if (viewport[0] < 0 || viewport[1] < 0 || viewport[2] <= 0 || viewport[3] <= 0) {
            throw new IllegalStateException("Native SAT ownership requires a nonnegative, nonempty viewport");
        }
        int bits = stencilBits();
        if (bits != 0 && bits != 8) {
            throw new IllegalStateException("Native SAT ownership requires an 8-bit stencil image, found " + bits);
        }
        if (drawFramebuffer == 0 && bits == 0) {
            throw new IllegalStateException("The native SAT default framebuffer has no stencil image");
        }
        int samples = glGetInteger(GL_SAMPLES);
        int format = bits == 0 ? GL_STENCIL_INDEX8 : stencilFormat(samples);
        int width = viewport[2];
        int height = viewport[3];
        if (bits == 0 && glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL_RENDERBUFFER) {
            originalDepthRenderbuffer = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER,
                    GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            glBindRenderbuffer(GL_RENDERBUFFER, originalDepthRenderbuffer);
            width = glGetRenderbufferParameteri(GL_RENDERBUFFER, GL_RENDERBUFFER_WIDTH);
            height = glGetRenderbufferParameteri(GL_RENDERBUFFER, GL_RENDERBUFFER_HEIGHT);
            samples = glGetRenderbufferParameteri(GL_RENDERBUFFER, GL_RENDERBUFFER_SAMPLES);
            format = GL_DEPTH24_STENCIL8;
        } else if (bits == 0) {
            width = Math.addExact(viewport[0], viewport[2]);
            height = Math.addExact(viewport[1], viewport[3]);
        }
        ownedStencil = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, ownedStencil);
        if (samples > 0) glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, format, width, height);
        else glRenderbufferStorage(GL_RENDERBUFFER, format, width, height);
        glDisable(GL_SCISSOR_TEST);
        if (bits == 0) {
            // Separate foreign depth and scratch stencil images are not guaranteed supported.
            // Temporarily replace renderbuffer depth with the same packed scratch image;
            // the detached foreign image is never cleared, copied, or drawn into.
            // Mark before either attachment call so begin failures restore both attachments.
            attached = true;
            if (originalDepthRenderbuffer != 0) {
                glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, ownedStencil);
            }
            glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, ownedStencil);
            requireComplete(GL_DRAW_FRAMEBUFFER);
        } else {
            // Keep foreign stencil attachments and their full viewport contents intact.
            backupFramebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, backupFramebuffer);
            glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER,
                    format == GL_STENCIL_INDEX8 ? GL_STENCIL_ATTACHMENT : GL_DEPTH_STENCIL_ATTACHMENT,
                    GL_RENDERBUFFER, ownedStencil);
            glDrawBuffer(GL_NONE);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, backupFramebuffer);
            glReadBuffer(GL_NONE);
            requireComplete(GL_DRAW_FRAMEBUFFER);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, drawFramebuffer);
            glBlitFramebuffer(viewport[0], viewport[1], viewport[0] + viewport[2], viewport[1] + viewport[3],
                    0, 0, viewport[2], viewport[3], GL_STENCIL_BUFFER_BIT, GL_NEAREST);
            requireNoError("saving native SAT stencil contents");
            backedUp = true;
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
        glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
        // Drop renderer tracking without disabling attributes on the caller's VAO.
        PatternRenderCommand.resetFrameState();
        glEnable(GL_SCISSOR_TEST);
        glScissor(viewport[0], viewport[1], viewport[2], viewport[3]);
        glStencilMask(0xFF);
        glClearStencil(layered ? 255 : 0);
        contentsChanged = true;
        glClear(GL_STENCIL_BUFFER_BIT);
        requireNoError("clearing native SAT coverage");
        restoreScissor();
        glEnable(GL_STENCIL_TEST);
        glStencilFunc(layered ? GL_LESS : GL_EQUAL, 0, 0xFF);
        glStencilOp(GL_KEEP, GL_KEEP, layered ? GL_REPLACE : GL_INCR);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
    }

    private void selectLayer(int rank, boolean ghost) {
        if (!layered || rank < 0 || rank >= 255) throw new IllegalStateException("Invalid native sprite layer rank");
        // LESS compares the incoming rank with the frontmost opaque native rank.
        // Ghosts retain that test but can never change the native coverage image.
        glStencilFunc(GL_LESS, rank, 0xFF);
        glStencilMask(ghost ? 0 : 0xFF);
        glStencilOp(GL_KEEP, GL_KEEP, ghost ? GL_KEEP : GL_REPLACE);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            PatternRenderCommand.cleanupFrameState(graphics);
            glDisable(GL_SCISSOR_TEST);
            if (backedUp && contentsChanged) {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, backupFramebuffer);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                glBlitFramebuffer(0, 0, viewport[2], viewport[3], viewport[0], viewport[1],
                        viewport[0] + viewport[2], viewport[1] + viewport[3], GL_STENCIL_BUFFER_BIT, GL_NEAREST);
                requireNoError("restoring native SAT stencil contents");
            }
        } finally {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            if (attached) {
                glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, 0);
                if (originalDepthRenderbuffer != 0) {
                    glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                            GL_RENDERBUFFER, originalDepthRenderbuffer);
                }
            }
            glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
            glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
            if (backupFramebuffer != 0) glDeleteFramebuffers(backupFramebuffer);
            if (ownedStencil != 0) glDeleteRenderbuffers(ownedStencil);
            front.restore(GL_FRONT);
            back.restore(GL_BACK);
            glClearStencil(clearStencil);
            enable(GL_STENCIL_TEST, stencilEnabled);
            enable(GL_DEPTH_TEST, depthEnabled);
            glDepthMask(depthWrite);
            glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            enable(GL_BLEND, blendEnabled);
            restoreScissor();
            glBindVertexArray(vertexArray);
            glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
            glUseProgram(program);
            glBindSampler(6, shadowSampler);
            for (int unit = 0; unit < textures.length; unit++) {
                glActiveTexture(GL_TEXTURE0 + unit);
                glBindTexture(GL_TEXTURE_2D, textures[unit]);
            }
            glActiveTexture(activeTexture);
            // Future pattern commands must rebind their own state after these foreign bindings.
            PatternRenderCommand.resetFrameState();
        }
    }

    private void restoreScissor() {
        glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        enable(GL_SCISSOR_TEST, scissorEnabled);
    }

    /** Stencil blits require matching formats, including a packed depth/stencil image. */
    private int stencilFormat(int samples) {
        if (drawFramebuffer == 0) {
            int depthKind = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_DEPTH,
                    GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            int depthBits = depthKind == GL_NONE ? 0 : glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER,
                    GL_DEPTH, GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE);
            if (depthBits == 0) return GL_STENCIL_INDEX8;
            if (depthBits == 24) return GL_DEPTH24_STENCIL8;
            if (depthBits == 32 && glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER,
                    GL_DEPTH, GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE) == GL_FLOAT) return GL_DEPTH32F_STENCIL8;
            throw new IllegalStateException("Unsupported default depth/stencil format: " + depthBits + "/8");
        }
        int kind = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_STENCIL_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int object = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_STENCIL_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if (kind == GL_RENDERBUFFER) {
            glBindRenderbuffer(GL_RENDERBUFFER, object);
            try { return glGetRenderbufferParameteri(GL_RENDERBUFFER, GL_RENDERBUFFER_INTERNAL_FORMAT); }
            finally { glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer); }
        }
        if (kind == GL_TEXTURE) {
            int level = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_STENCIL_ATTACHMENT,
                    GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
            int face = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_STENCIL_ATTACHMENT,
                    GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE);
            int target = face != 0 ? GL_TEXTURE_CUBE_MAP : samples > 0 ? GL_TEXTURE_2D_MULTISAMPLE : GL_TEXTURE_2D;
            int binding = glGetInteger(face != 0 ? GL_TEXTURE_BINDING_CUBE_MAP
                    : samples > 0 ? GL_TEXTURE_BINDING_2D_MULTISAMPLE : GL_TEXTURE_BINDING_2D);
            try {
                glBindTexture(target, object);
                requireNoError("binding a stencil image for its exact format");
                return glGetTexLevelParameteri(face != 0 ? face : target, level, GL_TEXTURE_INTERNAL_FORMAT);
            } finally { glBindTexture(target, binding); }
        }
        throw new IllegalStateException("Unsupported native SAT stencil attachment type: " + kind);
    }

    static int stencilBits() {
        int attachment = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == 0 ? GL_STENCIL : GL_STENCIL_ATTACHMENT;
        int kind = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, attachment,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        return kind == GL_NONE ? 0 : glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, attachment,
                GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE);
    }

    private static int[] integers(int name, int size) {
        int[] values = new int[size]; glGetIntegerv(name, values); return values;
    }

    private static void enable(int capability, boolean enabled) {
        if (enabled) glEnable(capability); else glDisable(capability);
    }

    private static void requireComplete(int target) {
        int status = glCheckFramebufferStatus(target);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Native SAT stencil framebuffer is incomplete: 0x" + Integer.toHexString(status));
        }
    }

    private static void requireNoError(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) throw new IllegalStateException(operation + ": GL error 0x" + Integer.toHexString(error));
    }

    private record Face(int function, int reference, int valueMask, int writeMask, int fail, int depthFail, int pass) {
        static Face capture(boolean back) {
            return new Face(glGetInteger(back ? GL_STENCIL_BACK_FUNC : GL_STENCIL_FUNC),
                    glGetInteger(back ? GL_STENCIL_BACK_REF : GL_STENCIL_REF),
                    glGetInteger(back ? GL_STENCIL_BACK_VALUE_MASK : GL_STENCIL_VALUE_MASK),
                    glGetInteger(back ? GL_STENCIL_BACK_WRITEMASK : GL_STENCIL_WRITEMASK),
                    glGetInteger(back ? GL_STENCIL_BACK_FAIL : GL_STENCIL_FAIL),
                    glGetInteger(back ? GL_STENCIL_BACK_PASS_DEPTH_FAIL : GL_STENCIL_PASS_DEPTH_FAIL),
                    glGetInteger(back ? GL_STENCIL_BACK_PASS_DEPTH_PASS : GL_STENCIL_PASS_DEPTH_PASS));
        }
        void restore(int face) {
            glStencilFuncSeparate(face, function, reference, valueMask);
            glStencilMaskSeparate(face, writeMask);
            glStencilOpSeparate(face, fail, depthFail, pass);
        }
    }
}
