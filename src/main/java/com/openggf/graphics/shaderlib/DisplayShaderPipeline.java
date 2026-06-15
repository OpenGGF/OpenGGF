package com.openggf.graphics.shaderlib;

import com.openggf.util.FboHelper;
import com.openggf.util.FboHelper.FboHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL14.GL_MIRRORED_REPEAT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class DisplayShaderPipeline {
    private static final Logger LOG = Logger.getLogger(DisplayShaderPipeline.class.getName());
    private static final float[] IDENTITY_MATRIX = {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
    };
    private static final String FULLSCREEN_VERTEX_SOURCE = """
            #version 410 core
            out vec2 vTexCoord;
            void main() {
                vec2 positions[4] = vec2[](
                    vec2(-1.0, -1.0),
                    vec2( 1.0, -1.0),
                    vec2(-1.0,  1.0),
                    vec2( 1.0,  1.0)
                );
                vec2 texCoords[4] = vec2[](
                    vec2(0.0, 0.0),
                    vec2(1.0, 0.0),
                    vec2(0.0, 1.0),
                    vec2(1.0, 1.0)
                );
                gl_Position = vec4(positions[gl_VertexID], 0.0, 1.0);
                vTexCoord = texCoords[gl_VertexID];
            }
            """;

    private final List<CompiledPass> passes = new ArrayList<>();
    private final List<PassTarget> passTargets = new ArrayList<>();
    private FboHandle captureFbo;
    private int fragmentOnlyVao;
    private int combinedVao;
    private int combinedVbo;
    private int sourceWidth = 1;
    private int sourceHeight = 1;
    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private ShaderPhase phase = ShaderPhase.FINAL;
    private boolean active;

    public boolean activate(DisplayShaderPreset preset) {
        ShaderPhase requestedPhase = preset == null || preset.phase() == null ? ShaderPhase.FINAL : preset.phase();
        if (preset == null || preset.passes().isEmpty()) {
            dispose();
            phase = requestedPhase;
            return true;
        }

        List<CompiledPass> compiled = new ArrayList<>(preset.passes().size());
        FboSet fbos = null;
        int newFragmentOnlyVao = 0;
        int newCombinedVao = 0;
        int newCombinedVbo = 0;
        try {
            for (DisplayShaderPass pass : preset.passes()) {
                compiled.add(compilePass(pass));
            }
            newFragmentOnlyVao = glGenVertexArrays();
            CombinedQuadResources quad = createCombinedQuadResources();
            newCombinedVao = quad.vaoId();
            newCombinedVbo = quad.vboId();
            fbos = createFbos(compiled);
            if (fbos == null) {
                throw new DisplayShaderLoadException("Display shader FBO allocation failed");
            }

            dispose();
            passes.addAll(compiled);
            captureFbo = fbos.captureFbo();
            passTargets.addAll(fbos.targets());
            fragmentOnlyVao = newFragmentOnlyVao;
            combinedVao = newCombinedVao;
            combinedVbo = newCombinedVbo;
            phase = requestedPhase;
            active = true;
            return true;
        } catch (Exception e) {
            LOG.fine("Display shader activation failed: " + e.getMessage());
            deletePrograms(compiled);
            destroyFbos(fbos);
            deleteVertexResources(newFragmentOnlyVao, newCombinedVao, newCombinedVbo);
            dispose();
            phase = requestedPhase;
            return false;
        }
    }

    public boolean isActive() {
        return active;
    }

    public ShaderPhase phase() {
        return phase;
    }

    public void resize(int sourceW, int sourceH, int viewportW, int viewportH) {
        int nextSourceW = Math.max(1, sourceW);
        int nextSourceH = Math.max(1, sourceH);
        int nextViewportW = Math.max(1, viewportW);
        int nextViewportH = Math.max(1, viewportH);
        boolean changed = nextSourceW != sourceWidth
                || nextSourceH != sourceHeight
                || nextViewportW != viewportWidth
                || nextViewportH != viewportHeight;

        sourceWidth = nextSourceW;
        sourceHeight = nextSourceH;
        viewportWidth = nextViewportW;
        viewportHeight = nextViewportH;

        if (active && changed && !rebuildFbos()) {
            LOG.severe("Display shader FBO rebuild failed after resize; disabling pipeline");
            dispose();
        }
    }

    public void apply(int vpX, int vpY, int vpW, int vpH, int frameCount) {
        if (!active || vpW <= 0 || vpH <= 0) {
            return;
        }

        try {
            if (vpW != viewportWidth || vpH != viewportHeight) {
                resize(sourceWidth, sourceHeight, vpW, vpH);
                if (!active) {
                    return;
                }
            }

            clearGlErrors();
            int[] savedViewport = FboHelper.saveViewport();
            captureViewport(vpX, vpY, vpW, vpH);

            int inputTexture = captureFbo.textureId();
            int inputWidth = viewportWidth;
            int inputHeight = viewportHeight;
            int lastFilter = GL_NEAREST;
            for (int i = 0; i < passes.size(); i++) {
                CompiledPass pass = passes.get(i);
                PassTarget target = passTargets.get(i);
                renderPass(pass, target, inputTexture, inputWidth, inputHeight, frameCount);
                inputTexture = target.fbo().textureId();
                inputWidth = target.width();
                inputHeight = target.height();
                lastFilter = pass.filterLinear() ? GL_LINEAR : GL_NEAREST;
            }

            blitToDefaultViewport(passTargets.get(passTargets.size() - 1), vpX, vpY, vpW, vpH, lastFilter);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glBindTexture(GL_TEXTURE_2D, 0);
            glUseProgram(0);
            glBindVertexArray(0);
            FboHelper.restoreViewport(savedViewport);
            checkGlError("display shader apply");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Display shader apply failed; disabling pipeline", e);
            dispose();
        }
    }

    public void dispose() {
        active = false;
        deletePrograms(passes);
        passes.clear();
        destroyCurrentFbos();
        deleteVertexResources(fragmentOnlyVao, combinedVao, combinedVbo);
        fragmentOnlyVao = 0;
        combinedVao = 0;
        combinedVbo = 0;
    }

    private boolean rebuildFbos() {
        FboSet rebuilt = createFbos(passes);
        if (rebuilt == null) {
            return false;
        }
        destroyCurrentFbos();
        captureFbo = rebuilt.captureFbo();
        passTargets.addAll(rebuilt.targets());
        return true;
    }

    private void captureViewport(int vpX, int vpY, int vpW, int vpH) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, captureFbo.fboId());
        glBlitFramebuffer(vpX, vpY, vpX + vpW, vpY + vpH,
                0, 0, viewportWidth, viewportHeight,
                GL_COLOR_BUFFER_BIT, GL_NEAREST);
    }

    private void renderPass(CompiledPass pass, PassTarget target, int inputTexture,
                            int inputWidth, int inputHeight, int frameCount) {
        glBindFramebuffer(GL_FRAMEBUFFER, target.fbo().fboId());
        glViewport(0, 0, target.width(), target.height());
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glUseProgram(pass.programId());

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTexture);
        configureSampler(pass);
        setUniforms(pass.programId(), inputWidth, inputHeight, target.width(), target.height(), frameCount);

        if (pass.shape() == GlslShape.FRAGMENT_ONLY) {
            glBindVertexArray(fragmentOnlyVao);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        } else {
            glBindVertexArray(combinedVao);
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }
    }

    private void blitToDefaultViewport(PassTarget target, int vpX, int vpY, int vpW, int vpH, int filter) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, target.fbo().fboId());
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glBlitFramebuffer(0, 0, target.width(), target.height(),
                vpX, vpY, vpX + vpW, vpY + vpH,
                GL_COLOR_BUFFER_BIT, filter);
    }

    private void setUniforms(int programId, int inputWidth, int inputHeight,
                             int outputWidth, int outputHeight, int frameCount) {
        setSampler(programId, "s_p");
        setSampler(programId, "SceneTexture");
        setSampler(programId, "Texture");

        setVec2(programId, "IN.video_size", inputWidth, inputHeight);
        setVec2(programId, "IN.texture_size", inputWidth, inputHeight);
        setVec2(programId, "IN.output_size", outputWidth, outputHeight);
        setVec2(programId, "InputSize", inputWidth, inputHeight);
        setVec2(programId, "TextureSize", inputWidth, inputHeight);
        setVec2(programId, "OutputSize", outputWidth, outputHeight);

        setInt(programId, "FrameCount", frameCount);
        setInt(programId, "FrameDirection", 1);
        int mvpLocation = glGetUniformLocation(programId, "MVPMatrix");
        if (mvpLocation >= 0) {
            glUniformMatrix4fv(mvpLocation, false, IDENTITY_MATRIX);
        }
    }

    private void setSampler(int programId, String name) {
        int location = glGetUniformLocation(programId, name);
        if (location >= 0) {
            glUniform1i(location, 0);
        }
    }

    private void setVec2(int programId, String name, int x, int y) {
        int location = glGetUniformLocation(programId, name);
        if (location >= 0) {
            glUniform2f(location, x, y);
        }
    }

    private void setInt(int programId, String name, int value) {
        int location = glGetUniformLocation(programId, name);
        if (location >= 0) {
            glUniform1i(location, value);
        }
    }

    private void configureSampler(CompiledPass pass) {
        int filter = pass.filterLinear() ? GL_LINEAR : GL_NEAREST;
        int wrap = toGlWrapMode(pass.wrapMode());
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);
    }

    private FboSet createFbos(List<CompiledPass> compiledPasses) {
        FboHandle capture = null;
        List<PassTarget> targets = new ArrayList<>(compiledPasses.size());
        try {
            capture = FboHelper.createColorOnly(viewportWidth, viewportHeight, GL_CLAMP_TO_EDGE);
            if (capture == null) {
                return null;
            }

            for (CompiledPass pass : compiledPasses) {
                int baseWidth = pass.scaleType() == ScaleType.VIEWPORT ? viewportWidth : sourceWidth;
                int baseHeight = pass.scaleType() == ScaleType.VIEWPORT ? viewportHeight : sourceHeight;
                int width = Math.max(1, baseWidth * Math.max(1, pass.scale()));
                int height = Math.max(1, baseHeight * Math.max(1, pass.scale()));
                FboHandle fbo = FboHelper.createColorOnly(width, height, toGlWrapMode(pass.wrapMode()));
                if (fbo == null) {
                    return null;
                }
                targets.add(new PassTarget(fbo, width, height));
            }

            return new FboSet(capture, List.copyOf(targets));
        } finally {
            if (targets.size() != compiledPasses.size()) {
                FboHelper.destroy(capture);
                for (PassTarget target : targets) {
                    FboHelper.destroy(target.fbo());
                }
            }
        }
    }

    private CompiledPass compilePass(DisplayShaderPass pass) throws DisplayShaderLoadException {
        if (pass == null || pass.fragmentSource() == null || pass.fragmentSource().isBlank()) {
            throw new DisplayShaderLoadException("Display shader pass requires fragment source");
        }

        GlslShape shape = pass.shape() == null ? GlslShape.FRAGMENT_ONLY : pass.shape();
        String rawVertexSource = shape == GlslShape.FRAGMENT_ONLY
                ? FULLSCREEN_VERTEX_SOURCE
                : firstPresent(pass.vertexSource(), pass.fragmentSource());
        String vertexSource = shape == GlslShape.FRAGMENT_ONLY
                ? rawVertexSource
                : RetroArchGlslCompat.stageSource(rawVertexSource, "VERTEX");
        String fragmentSource = RetroArchGlslCompat.stageSource(pass.fragmentSource(), "FRAGMENT");
        int programId = compileProgram(vertexSource, fragmentSource, shape);
        return new CompiledPass(programId, shape, Math.max(1, pass.scale()),
                pass.scaleType() == null ? ScaleType.SOURCE : pass.scaleType(),
                pass.filterLinear(),
                pass.wrapMode() == null ? WrapMode.CLAMP_TO_EDGE : pass.wrapMode());
    }

    private int compileProgram(String vertexSource, String fragmentSource, GlslShape shape)
            throws DisplayShaderLoadException {
        int vertexShader = 0;
        int fragmentShader = 0;
        int programId = 0;
        try {
            vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
            fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
            programId = glCreateProgram();
            glAttachShader(programId, vertexShader);
            glAttachShader(programId, fragmentShader);
            if (shape == GlslShape.COMBINED) {
                glBindAttribLocation(programId, 0, "VertexCoord");
                glBindAttribLocation(programId, 1, "TexCoord");
                glBindAttribLocation(programId, 2, "COLOR");
            }
            glLinkProgram(programId);
            if (glGetProgrami(programId, GL_LINK_STATUS) == 0) {
                throw new DisplayShaderLoadException("Display shader link failed: " + glGetProgramInfoLog(programId));
            }
            return programId;
        } catch (DisplayShaderLoadException e) {
            if (programId != 0) {
                glDeleteProgram(programId);
                programId = 0;
            }
            throw e;
        } finally {
            if (programId != 0 && vertexShader != 0) {
                glDetachShader(programId, vertexShader);
            }
            if (programId != 0 && fragmentShader != 0) {
                glDetachShader(programId, fragmentShader);
            }
            if (vertexShader != 0) {
                glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                glDeleteShader(fragmentShader);
            }
        }
    }

    private int compileShader(int shaderType, String source) throws DisplayShaderLoadException {
        int shaderId = glCreateShader(shaderType);
        try {
            glShaderSource(shaderId, source);
            glCompileShader(shaderId);
            if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == 0) {
                throw new DisplayShaderLoadException("Display shader compile failed: " + glGetShaderInfoLog(shaderId));
            }
            return shaderId;
        } catch (DisplayShaderLoadException e) {
            glDeleteShader(shaderId);
            throw e;
        }
    }

    private CombinedQuadResources createCombinedQuadResources() {
        int vaoId = glGenVertexArrays();
        int vboId = glGenBuffers();
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, combinedQuadVertices(), GL_STATIC_DRAW);

        int stride = 12 * Float.BYTES;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 4, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 4L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 8L * Float.BYTES);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        return new CombinedQuadResources(vaoId, vboId);
    }

    private static float[] combinedQuadVertices() {
        return new float[] {
                -1.0f, -1.0f, 0.0f, 1.0f,   0.0f, 0.0f, 0.0f, 1.0f,   1.0f, 1.0f, 1.0f, 1.0f,
                 1.0f, -1.0f, 0.0f, 1.0f,   1.0f, 0.0f, 0.0f, 1.0f,   1.0f, 1.0f, 1.0f, 1.0f,
                -1.0f,  1.0f, 0.0f, 1.0f,   0.0f, 1.0f, 0.0f, 1.0f,   1.0f, 1.0f, 1.0f, 1.0f,
                -1.0f,  1.0f, 0.0f, 1.0f,   0.0f, 1.0f, 0.0f, 1.0f,   1.0f, 1.0f, 1.0f, 1.0f,
                 1.0f, -1.0f, 0.0f, 1.0f,   1.0f, 0.0f, 0.0f, 1.0f,   1.0f, 1.0f, 1.0f, 1.0f,
                 1.0f,  1.0f, 0.0f, 1.0f,   1.0f, 1.0f, 0.0f, 1.0f,   1.0f, 1.0f, 1.0f, 1.0f
        };
    }

    private String firstPresent(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private int toGlWrapMode(WrapMode wrapMode) {
        return switch (wrapMode == null ? WrapMode.CLAMP_TO_EDGE : wrapMode) {
            case CLAMP_TO_EDGE -> GL_CLAMP_TO_EDGE;
            case CLAMP_TO_BORDER -> GL_CLAMP_TO_BORDER;
            case REPEAT -> GL_REPEAT;
            case MIRRORED_REPEAT -> GL_MIRRORED_REPEAT;
        };
    }

    private void destroyCurrentFbos() {
        FboHelper.destroy(captureFbo);
        captureFbo = null;
        for (PassTarget target : passTargets) {
            FboHelper.destroy(target.fbo());
        }
        passTargets.clear();
    }

    private void destroyFbos(FboSet fboSet) {
        if (fboSet == null) {
            return;
        }
        FboHelper.destroy(fboSet.captureFbo());
        for (PassTarget target : fboSet.targets()) {
            FboHelper.destroy(target.fbo());
        }
    }

    private void deletePrograms(List<CompiledPass> compiledPasses) {
        for (CompiledPass pass : compiledPasses) {
            if (pass.programId() != 0) {
                glDeleteProgram(pass.programId());
            }
        }
    }

    private void deleteVertexResources(int fragmentVao, int quadVao, int quadVbo) {
        if (fragmentVao != 0) {
            glDeleteVertexArrays(fragmentVao);
        }
        if (quadVao != 0) {
            glDeleteVertexArrays(quadVao);
        }
        if (quadVbo != 0) {
            glDeleteBuffers(quadVbo);
        }
    }

    private void clearGlErrors() {
        while (glGetError() != GL_NO_ERROR) {
            // Drain stale errors so apply failure reflects this pipeline run.
        }
    }

    private void checkGlError(String label) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            throw new IllegalStateException(label + " GL error: 0x" + Integer.toHexString(error));
        }
    }

    private record CompiledPass(int programId, GlslShape shape, int scale, ScaleType scaleType,
                                boolean filterLinear, WrapMode wrapMode) {
    }

    private record PassTarget(FboHandle fbo, int width, int height) {
    }

    private record FboSet(FboHandle captureFbo, List<PassTarget> targets) {
    }

    private record CombinedQuadResources(int vaoId, int vboId) {
    }
}
