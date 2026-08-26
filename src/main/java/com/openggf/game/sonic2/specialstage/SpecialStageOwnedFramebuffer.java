package com.openggf.game.sonic2.specialstage;

import static org.lwjgl.opengl.GL11.GL_REPEAT;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT16;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.GL_RENDERBUFFER;
import static org.lwjgl.opengl.GL30.GL_RENDERBUFFER_BINDING;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindRenderbuffer;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glDeleteRenderbuffers;
import static org.lwjgl.opengl.GL30.glFramebufferRenderbuffer;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL30.glGenRenderbuffers;
import static org.lwjgl.opengl.GL30.glRenderbufferStorage;

/** S2-owned transactional FBO names and restoration state. */
final class SpecialStageOwnedFramebuffer {
    private final int width;
    private final int height;
    private final int wrapMode;
    private int fboId;
    private int textureId;
    private int depthId;
    private int priorFramebuffer;
    private int priorActiveTexture;
    private int priorTexture2d;
    private int priorRenderbuffer;
    private boolean priorFramebufferPending;
    private boolean priorActiveTexturePending;
    private boolean priorTexture2dPending;
    private boolean priorRenderbufferPending;
    private boolean published;

    SpecialStageOwnedFramebuffer(int width, int height, int wrapMode) {
        this.width = width;
        this.height = height;
        this.wrapMode = wrapMode;
    }

    void create() {
        if (hasPendingOwnership() || published) {
            throw new IllegalStateException("special-stage framebuffer ownership is already active");
        }
        capturePriorState();
        boolean restorationAttempted = false;
        try {
            fboId = glGenFramebuffers();
            textureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapMode);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapMode);
            glBindTexture(GL_TEXTURE_2D, 0);
            depthId = glGenRenderbuffers();
            glBindRenderbuffer(GL_RENDERBUFFER, depthId);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, width, height);
            glBindRenderbuffer(GL_RENDERBUFFER, 0);
            glBindFramebuffer(GL_FRAMEBUFFER, fboId);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                    GL_TEXTURE_2D, textureId, 0);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                    GL_RENDERBUFFER, depthId);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("special-stage background framebuffer is incomplete");
            }
            restorationAttempted = true;
            Throwable restorationFailure = restorePriorState(null);
            if (restorationFailure != null) throwUnchecked(restorationFailure);
            published = true;
        } catch (Throwable failure) {
            if (!restorationAttempted) failure = restorePriorState(failure);
            throwUnchecked(failure);
        }
    }

    private void capturePriorState() {
        priorFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING);
        priorActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        priorTexture2d = glGetInteger(GL_TEXTURE_BINDING_2D);
        priorRenderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING);
        priorFramebufferPending = true;
        priorActiveTexturePending = true;
        priorTexture2dPending = true;
        priorRenderbufferPending = true;
    }

    boolean isPublished() { return published; }
    boolean hasPendingOwnership() {
        return hasPendingRestoration() || fboId != 0 || textureId != 0 || depthId != 0;
    }
    boolean hasPendingRestoration() {
        return priorFramebufferPending || priorActiveTexturePending
                || priorTexture2dPending || priorRenderbufferPending;
    }
    boolean needsCleanup() { return !published && hasPendingOwnership(); }
    int fboId() { return fboId; }
    int textureId() { return textureId; }
    int depthId() { return depthId; }

    void cleanup() {
        Throwable failure = cleanup(null);
        if (failure != null) throwUnchecked(failure);
    }

    Throwable cleanup(Throwable originalFailure) {
        Throwable failure = originalFailure;
        published = false;
        failure = restorePriorState(failure);
        if (fboId != 0) {
            try { glDeleteFramebuffers(fboId); fboId = 0; }
            catch (Throwable next) { failure = combineFailure(failure, next); }
        }
        if (textureId != 0) {
            try { glDeleteTextures(textureId); textureId = 0; }
            catch (Throwable next) { failure = combineFailure(failure, next); }
        }
        if (depthId != 0) {
            try { glDeleteRenderbuffers(depthId); depthId = 0; }
            catch (Throwable next) { failure = combineFailure(failure, next); }
        }
        return failure;
    }

    private Throwable restorePriorState(Throwable originalFailure) {
        Throwable failure = originalFailure;
        if (priorTexture2dPending) {
            try { glActiveTexture(priorActiveTexture); glBindTexture(GL_TEXTURE_2D, priorTexture2d);
                priorTexture2dPending = false; }
            catch (Throwable next) { failure = combineFailure(failure, next); }
        }
        if (priorRenderbufferPending) {
            try { glBindRenderbuffer(GL_RENDERBUFFER, priorRenderbuffer);
                priorRenderbufferPending = false; }
            catch (Throwable next) { failure = combineFailure(failure, next); }
        }
        if (priorFramebufferPending) {
            try { glBindFramebuffer(GL_FRAMEBUFFER, priorFramebuffer);
                priorFramebufferPending = false; }
            catch (Throwable next) { failure = combineFailure(failure, next); }
        }
        if (priorActiveTexturePending) {
            try { glActiveTexture(priorActiveTexture); priorActiveTexturePending = false; }
            catch (Throwable next) { failure = combineFailure(failure, next); }
        }
        return failure;
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        throw new RuntimeException(failure);
    }

    private static Throwable combineFailure(Throwable first, Throwable second) {
        if (second == null) return first;
        if (first == null) return second;
        first.addSuppressed(second);
        return first;
    }
}
