package com.openggf.graphics;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

/**
 * Optimized pattern render command that minimizes redundant GL state changes.
 * Uses modern OpenGL (VAOs, vertex attributes) for core profile compatibility.
 *
 * Optimizations applied:
 * 1. Uses cached uniform locations (eliminates string hash lookups)
 * 2. Uses static singleton for shader program reference
 * 3. Tracks last-used textures to avoid redundant binds
 * 4. Pre-computes transformed vertices instead of using matrix operations
 * 5. Object pooling to avoid per-pattern allocation
 */
public class PatternRenderCommand implements GLCommandable {

    // Object pool for command reuse
    private static final ArrayDeque<PatternRenderCommand> pool = new ArrayDeque<>(256);

    private int paletteTextureId;
    private float u0;
    private float v0;
    private float u1;
    private float v1;
    private int atlasIndex;
    private int paletteIndex;
    private SpritePresentation.PaletteReplay paletteReplay;
    private boolean paletteReplayUnderwater;
    private boolean hFlip;
    private boolean vFlip;
    private boolean textureCoordinatesResolved;
    private boolean piecePriority; // VDP per-tile priority from PatternDesc bit 15
    private int capturedTileOcclusionPaletteMask;
    private boolean ghostEffectActive;
    private boolean ghostColour;
    private float ghostAlpha;
    private boolean leased;
    private float x;
    private float y;
    private float width;
    private float height;
    private GraphicsManager graphicsManager;
    private float[] presentationQuad;
    private static final int MAX_PRESENTATION_PATTERNS = 512;
    private static final int[] PRESENTATION_TRIANGLES = {3, 2, 1, 3, 1, 0};
    private float[] presentationBatchData;
    private int presentationCount;
    private float presentationHue;
    private float presentationOpacity=1;
    private boolean presentationPriorityShader;

    // Static state tracking for batch optimization
    private static int lastAtlasTextureId = -1;
    private static int lastPaletteTextureId = -1;
    private static int lastPaletteIndex = -1;
    private static int lastTotalPaletteLines = -1;
    private static int lastPriorityShaderProgramId = -1;
    private static int lastPriorityTileTextureId = -1;
    private static int lastPriorityUnderwaterPaletteId = -1;
    private static int lastPriorityViewportX = Integer.MIN_VALUE;
    private static int lastPriorityViewportY = Integer.MIN_VALUE;
    private static int lastPriorityViewportWidth = Integer.MIN_VALUE;
    private static int lastPriorityViewportHeight = Integer.MIN_VALUE;
    private static boolean lastPriorityWaterEnabled;
    private static boolean lastGhostEffectActive;
    private static boolean lastGhostColour;
    private static float lastGhostAlpha = Float.NaN;
    private static float lastPriorityWaterlineScreenY = Float.NaN;
    private static float lastPriorityWindowHeight = Float.NaN;
    private static float lastPriorityScreenHeight = Float.NaN;
    private static boolean stateInitialized = false;
    private static int lastExecutedShaderProgramId=-1;

    // Pre-allocated vertex buffers for transformed coordinates
    private static FloatBuffer vertexBuffer;
    private static FloatBuffer texCoordBuffer;
    private static FloatBuffer paletteBuffer;
    private static final Map<Integer, int[]> TRANSFORM_UNIFORM_LOCATIONS = new HashMap<>();

    @FunctionalInterface
    interface UniformLookup {
        int find(int programId, String name);
    }

    static int pooledCommandCount() {
        return pool.size();
    }

    static boolean hasNativeScratch() {
        return vertexBuffer != null || texCoordBuffer != null || paletteBuffer != null;
    }

    static int[] transformUniformLocations(int programId, UniformLookup lookup) {
        return TRANSFORM_UNIFORM_LOCATIONS.computeIfAbsent(programId,
                id -> new int[] {lookup.find(id, "ProjectionMatrix"), lookup.find(id, "CameraOffset")});
    }

    static void clearUniformLocationCache() {
        TRANSFORM_UNIFORM_LOCATIONS.clear();
    }

    // VAO and VBOs for modern OpenGL (shared across all instances)
    private static int vaoId = 0;
    private static int vertexVboId = 0;
    private static int texCoordVboId = 0;
    private static int paletteVboId = 0;

    // Vertex attribute locations (standard layout matching shader_basic.vert)
    private static final int ATTRIB_POSITION = 0;
    private static final int ATTRIB_TEXCOORD = 1;
    private static final int ATTRIB_PALETTE = 2;

    /** Acquire and initialize the manager/atlas prefix before the caller samples attributes. */
    static PatternRenderCommand obtain(PatternAtlas.Entry entry, int paletteTextureId, GraphicsManager graphicsManager) {
        PatternRenderCommand cmd = pool.pollFirst();
        if (cmd == null) {
            cmd = new PatternRenderCommand();
        }
        cmd.graphicsManager = Objects.requireNonNull(graphicsManager, "graphicsManager");
        cmd.presentationQuad=null;cmd.presentationCount=0;cmd.presentationHue=0;cmd.presentationOpacity=1;
        cmd.paletteTextureId = paletteTextureId;
        cmd.u0 = entry.u0();
        cmd.v0 = entry.v0();
        cmd.u1 = entry.u1();
        cmd.v1 = entry.v1();
        cmd.atlasIndex = entry.atlasIndex();
        return cmd;
    }

    void resolveStripTextureCoordinates(PatternAtlas.Entry entry, int stripIndex) {
        int rowTop = stripIndex * 2;
        int rowBottom = rowTop + 1;
        float rowStep = (entry.v1() - entry.v0()) / 8.0f;
        float stripTop = entry.v0() + rowStep * ((7 - rowTop) + 0.5f);
        float stripBottom = entry.v0() + rowStep * ((7 - rowBottom) + 0.5f);
        u0 = hFlip ? entry.u1() : entry.u0();
        u1 = hFlip ? entry.u0() : entry.u1();
        v0 = vFlip ? stripTop : stripBottom;
        v1 = vFlip ? stripBottom : stripTop;
        textureCoordinatesResolved = true;
    }

    /**
     * Obtains a command drawing pixel rows {@code [rowStart, rowEnd)} (screen order,
     * 0 = tile top) of an 8x8 tile whose top is at {@code y}. Attributes are primitives
     * so the graphics layer gains no level-type dependency.
     */
    static PatternRenderCommand obtainRows(PatternAtlas.Entry entry, int paletteTextureId, int paletteIndex,
            boolean hFlip, boolean vFlip, boolean piecePriority, int x, int y, int rowStart, int rowEnd,
            GraphicsManager graphicsManager) {
        PatternRenderCommand cmd = pool.pollFirst();
        if (cmd == null) {
            cmd = new PatternRenderCommand();
        }
        cmd.graphicsManager = Objects.requireNonNull(graphicsManager, "graphicsManager");
        cmd.presentationQuad=null;cmd.presentationCount=0;cmd.presentationHue=0;cmd.presentationOpacity=1;
        cmd.paletteTextureId = paletteTextureId;
        cmd.atlasIndex = entry.atlasIndex();
        cmd.paletteIndex = paletteIndex;
        cmd.paletteReplay=SpritePresentation.subject(graphicsManager).paletteReplay();
        cmd.paletteReplayUnderwater=graphicsManager.isUseUnderwaterPaletteForBackground();
        cmd.hFlip = hFlip;
        cmd.vFlip = vFlip;
        cmd.piecePriority = piecePriority;
        cmd.capturedTileOcclusionPaletteMask = graphicsManager.getCurrentSpriteTileOcclusionPaletteMask();
        cmd.ghostEffectActive = graphicsManager.isGhostRenderEffectActive();
        cmd.ghostColour = graphicsManager.isGhostRenderColourPreserved();
        cmd.ghostAlpha = graphicsManager.getGhostRenderAlpha();
        cmd.x = x;
        cmd.width = 8f;
        cmd.height = rowEnd - rowStart;
        cmd.leased = true;
        cmd.y = resolveDisplayHeight(graphicsManager) - (y + rowStart) - cmd.height;
        cmd.resolveRowTextureCoordinates(entry, rowStart, rowEnd);
        return cmd;
    }

    /**
     * V is interpolated along the same top/bottom mapping a whole tile uses, so flips
     * match the uncropped path.
     */
    private void resolveRowTextureCoordinates(PatternAtlas.Entry entry, int rowStart, int rowEnd) {
        // Unresolved whole tiles put entry.v0 on the top edge unless V-flipped.
        float vTop = vFlip ? entry.v1() : entry.v0();
        float vBottom = vFlip ? entry.v0() : entry.v1();
        u0 = hFlip ? entry.u1() : entry.u0();
        u1 = hFlip ? entry.u0() : entry.u1();
        // Resolved coordinates: v0 is the quad's bottom edge, v1 its top edge.
        v0 = vTop + (vBottom - vTop) * (rowEnd / 8.0f);
        v1 = vTop + (vBottom - vTop) * (rowStart / 8.0f);
        textureCoordinatesResolved = true;
    }

    private PatternRenderCommand() {
        // Private constructor for pooling
    }

    /** The virtual underwater getter can mutate the caller's later attributes or throw. */
    void capturePalette(int paletteIndex) {
        this.paletteIndex = paletteIndex;
        this.paletteReplay=SpritePresentation.subject(graphicsManager).paletteReplay();
        this.paletteReplayUnderwater=graphicsManager.isUseUnderwaterPaletteForBackground();
    }

    void finish(boolean hFlip, boolean vFlip, boolean piecePriority, float x, float y, float width, float height) {
        this.hFlip = hFlip;
        this.vFlip = vFlip;
        this.textureCoordinatesResolved = false;
        this.piecePriority = piecePriority;
        this.capturedTileOcclusionPaletteMask = graphicsManager.getCurrentSpriteTileOcclusionPaletteMask();
        this.ghostEffectActive = graphicsManager.isGhostRenderEffectActive();
        this.ghostColour = graphicsManager.isGhostRenderColourPreserved();
        this.ghostAlpha = graphicsManager.getGhostRenderAlpha();
        this.x = x;
        this.width = width;
        this.height = height;
        this.leased = true;
        // Genesis Y refers to the TOP of the pattern, so we subtract the pattern height
        // to get the OpenGL Y coordinate for the bottom of the quad
        // width/height are allowed to vary for scaled host preview rendering.
        this.y = resolveDisplayHeight(graphicsManager) - y - height;
    }

    void paletteReplay(SpritePresentation.PaletteReplay replay) { paletteReplay=replay; }

    /**
     * Return this command to the pool for reuse.
     */
    public void recycle() {
        if (!leased) {
            return;
        }
        leased = false;
        graphicsManager = null;
        paletteReplay=null;
        presentationQuad=null;
        if (pool.size() < 512) { // Cap pool size to prevent unbounded growth
            pool.offerFirst(this);
        }
    }

    @Override
    public void discard() {
        recycle();
    }

    /**
     * Reset static state at the start of each frame.
     * Call this before beginning a new frame of rendering.
     */
    public static void resetFrameState() {
        lastAtlasTextureId = -1;
        lastPaletteTextureId = -1;
        lastPaletteIndex = -1;
        lastTotalPaletteLines=-1;
        lastPriorityShaderProgramId = -1;
        lastPriorityTileTextureId = -1;
        lastPriorityUnderwaterPaletteId = -1;
        lastPriorityViewportX = Integer.MIN_VALUE;
        lastPriorityViewportY = Integer.MIN_VALUE;
        lastPriorityViewportWidth = Integer.MIN_VALUE;
        lastPriorityViewportHeight = Integer.MIN_VALUE;
        lastPriorityWaterEnabled = false;
        lastGhostEffectActive = false;
        lastGhostColour = false;
        lastGhostAlpha = Float.NaN;
        lastPriorityWaterlineScreenY = Float.NaN;
        lastPriorityWindowHeight = Float.NaN;
        lastPriorityScreenHeight = Float.NaN;
        stateInitialized = false;
        lastExecutedShaderProgramId=-1;
    }

    private static void ensureVbos() {
        ensureNativeScratch();
        if (vaoId != 0) {
            return;
        }
        vaoId = glGenVertexArrays();
        vertexVboId = glGenBuffers();
        texCoordVboId = glGenBuffers();
        paletteVboId = glGenBuffers();
    }

    static void ensureNativeScratch() {
        if (vertexBuffer == null) {
            vertexBuffer = MemoryUtil.memAllocFloat(MAX_PRESENTATION_PATTERNS * 12);
            texCoordBuffer = MemoryUtil.memAllocFloat(MAX_PRESENTATION_PATTERNS * 12);
            paletteBuffer = MemoryUtil.memAllocFloat(MAX_PRESENTATION_PATTERNS * 6);
        }
    }

    @Override
    public void execute(int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
        try {
            executeLeased(cameraX, cameraY, cameraWidth, cameraHeight);
        } finally {
            recycle();
        }
    }

    private void executeLeased(int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
        GraphicsManager graphicsManager = this.graphicsManager;
        int effectivePalette=paletteReplay==null?paletteIndex:paletteReplay.palette(graphicsManager,paletteIndex);
        if(effectivePalette<0)return;
        graphicsManager.paletteTextures().refreshScopedUnderwaterResidency();
        if(paletteReplay!=null) {
            Integer current=graphicsManager.scopedReplayPaletteTexture(paletteReplayUnderwater);
            if(current==null)return;
            paletteTextureId=current;
        }
        ShaderProgram shaderProgram = presentationQuad==null?graphicsManager.getShaderProgram():
                graphicsManager.presentationShader(presentationPriorityShader);
        if(lastExecutedShaderProgramId!=shaderProgram.getProgramId()) {
            resetFrameState();lastExecutedShaderProgramId=shaderProgram.getProgramId();
        }

        // Initialize persistent state once per batch of patterns
        if (!stateInitialized) {
            ensureVbos();
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            shaderProgram.use();
            shaderProgram.cacheUniformLocations();
            glUniform1i(shaderProgram.getPaletteLocation(), 0);
            glUniform1i(shaderProgram.getIndexedColorTextureLocation(), 1);
            shaderProgram.setTotalPaletteLines((float) RenderContext.getTotalPaletteLines());

            // Set projection matrix uniform - REQUIRED for correct rendering
            int[] transformLocations = transformUniformLocations(shaderProgram.getProgramId(),
                    (programId, name) -> glGetUniformLocation(programId, name));
            int projectionLoc = transformLocations[0];
            if (projectionLoc != -1) {
                float[] projMatrix = graphicsManager.getProjectionMatrixBuffer();
                if (projMatrix != null) {
                    glUniformMatrix4fv(projectionLoc, false, projMatrix);
                }
            }

            // Set camera offset uniform
            // X is negated to scroll objects left when camera moves right
            // Y is NOT negated because vertex Y is already in screen space (flipped from Genesis coords)
            int cameraOffsetLoc = transformLocations[1];
            if (cameraOffsetLoc != -1) {
                glUniform2f(cameraOffsetLoc, -cameraX, cameraY);
            }

            // Bind VAO
            glBindVertexArray(vaoId);

            // If using water shader, bind underwater palette to texture unit 2
            if (shaderProgram instanceof WaterShaderProgram) {
                WaterShaderProgram waterShader = (WaterShaderProgram) shaderProgram;
                Integer underwaterPaletteId = graphicsManager.getUnderwaterPaletteTextureId();
                if (underwaterPaletteId != null) {
                    glActiveTexture(GL_TEXTURE2);
                    glBindTexture(GL_TEXTURE_2D, underwaterPaletteId);
                    int loc = waterShader.getUnderwaterPaletteLocation();
                    if (loc != -1) {
                        glUniform1i(loc, 2);
                    }
                    glActiveTexture(GL_TEXTURE0);
                }
            }

            stateInitialized = true;
        }

        int totalLines=RenderContext.getTotalPaletteLines();
        if(lastTotalPaletteLines!=totalLines) {
            shaderProgram.setTotalPaletteLines((float)totalLines);lastTotalPaletteLines=totalLines;
        }
        if (shaderProgram instanceof SpritePriorityShaderProgram priorityShader) {
            int programId = shaderProgram.getProgramId();
            TilePriorityFBO fbo = graphicsManager.getTilePriorityFBO();
            int tilePriorityTextureId =
                    fbo != null && fbo.isInitialized() ? fbo.getTextureId() : -1;
            int viewportX = graphicsManager.getViewportX();
            int viewportY = graphicsManager.getViewportY();
            int viewportWidth = graphicsManager.getViewportWidth();
            int viewportHeight = graphicsManager.getViewportHeight();
            Integer underwaterPaletteId = graphicsManager.getUnderwaterPaletteTextureId();
            int underwaterPaletteTextureId = underwaterPaletteId != null ? underwaterPaletteId : -1;
            boolean waterEnabled = graphicsManager.isWaterEnabled();
            float waterlineScreenY = graphicsManager.getWaterlineScreenY();
            float windowHeight = graphicsManager.getWindowHeight();
            float screenHeight = graphicsManager.getScreenHeight();

            if (lastPriorityShaderProgramId != programId
                    || lastPriorityTileTextureId != tilePriorityTextureId
                    || lastPriorityViewportX != viewportX
                    || lastPriorityViewportY != viewportY
                    || lastPriorityViewportWidth != viewportWidth
                    || lastPriorityViewportHeight != viewportHeight
                    || lastPriorityUnderwaterPaletteId != underwaterPaletteTextureId
                    || lastPriorityWaterEnabled != waterEnabled
                    || lastPriorityWaterlineScreenY != waterlineScreenY
                    || lastPriorityWindowHeight != windowHeight
                    || lastPriorityScreenHeight != screenHeight) {
                if (tilePriorityTextureId >= 0) {
                    glActiveTexture(GL_TEXTURE5);
                    glBindTexture(GL_TEXTURE_2D, tilePriorityTextureId);
                    priorityShader.setTilePriorityTexture(5);
                    glActiveTexture(GL_TEXTURE0);
                }

                priorityShader.setScreenSize(viewportWidth, viewportHeight);
                priorityShader.setViewportOffset(viewportX, viewportY);

                if (underwaterPaletteTextureId >= 0) {
                    glActiveTexture(GL_TEXTURE2);
                    glBindTexture(GL_TEXTURE_2D, underwaterPaletteTextureId);
                    int loc = priorityShader.getUnderwaterPaletteLocation();
                    if (loc != -1) {
                        glUniform1i(loc, 2);
                    }
                    glActiveTexture(GL_TEXTURE0);
                }

                priorityShader.setWaterEnabled(waterEnabled);
                priorityShader.setWaterlineScreenY(waterlineScreenY);
                priorityShader.setWindowHeight(windowHeight);
                priorityShader.setScreenHeight(screenHeight);
                lastPriorityShaderProgramId = programId;
                lastPriorityTileTextureId = tilePriorityTextureId;
                lastPriorityUnderwaterPaletteId = underwaterPaletteTextureId;
                lastPriorityViewportX = viewportX;
                lastPriorityViewportY = viewportY;
                lastPriorityViewportWidth = viewportWidth;
                lastPriorityViewportHeight = viewportHeight;
                lastPriorityWaterEnabled = waterEnabled;
                lastPriorityWaterlineScreenY = waterlineScreenY;
                lastPriorityWindowHeight = windowHeight;
                lastPriorityScreenHeight = screenHeight;
            }

            // A piece carrying the ROM priority bit bypasses terrain masking;
            // otherwise retain the object's palette-specific occlusion mask.
            priorityShader.setTileOcclusionPaletteMask(piecePriority ? 0 : capturedTileOcclusionPaletteMask);
        }

        // Only bind palette texture if it changed
        if (paletteTextureId != lastPaletteTextureId) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, paletteTextureId);
            lastPaletteTextureId = paletteTextureId;
        }

        // Only bind atlas texture if it changed
        Integer atlasTextureId = graphicsManager.getPatternAtlasTextureId(atlasIndex);
        if (atlasTextureId != null && atlasTextureId != lastAtlasTextureId) {
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, atlasTextureId);
            lastAtlasTextureId = atlasTextureId;
        }

        // Only update palette line uniform if it changed
        if (effectivePalette != lastPaletteIndex) {
            shaderProgram.setPaletteLine(effectivePalette);
            lastPaletteIndex = effectivePalette;
        }

        if (ghostEffectActive != lastGhostEffectActive || ghostColour != lastGhostColour
                || ghostAlpha != lastGhostAlpha) {
            shaderProgram.setGhostEffect(ghostEffectActive, ghostColour, ghostAlpha);
            lastGhostEffectActive = ghostEffectActive;
            lastGhostColour = ghostColour;
            lastGhostAlpha = ghostAlpha;
        }

        // Compute transformed vertices directly (avoids push/pop/translate/scale)
        // Note: camera offset is now handled via uniform, so vertices are in world space
        float screenX = x;
        float screenY = y;

        // Bottom-left, bottom-right, top-right, top-left
        float x0 = screenX;
        float x1 = screenX + width;
        float y0 = screenY;
        float y1 = screenY + height;

        // Apply horizontal flip by swapping left/right
        if (!textureCoordinatesResolved && hFlip) {
            float temp = x0;
            x0 = x1;
            x1 = temp;
        }

        // Apply vertical flip by swapping top/bottom
        // Note: VFlip=false means apply flip (original VDP behavior)
        if (!textureCoordinatesResolved && !vFlip) {
            float temp = y0;
            y0 = y1;
            y1 = temp;
        }

        vertexBuffer.clear();
        texCoordBuffer.clear();
        paletteBuffer.clear();
        if (presentationCount > 1) {
            int displayHeight = resolveDisplayHeight(graphicsManager);
            for (int tile = 0; tile < presentationCount; tile++) {
                int offset = tile * 12;
                for (int vertex : PRESENTATION_TRIANGLES) {
                    vertexBuffer.put(presentationBatchData[offset + vertex * 2])
                            .put(displayHeight - presentationBatchData[offset + vertex * 2 + 1]);
                    texCoordBuffer.put(presentationBatchData[offset + ((vertex == 0 || vertex == 3) ? 8 : 10)])
                            .put(presentationBatchData[offset + ((vertex < 2) ? 11 : 9)]);
                    paletteBuffer.put(effectivePalette);
                }
            }
        } else {
            // Same triangle-fan vertices/UVs as the unbatched reference path.
            if (presentationQuad != null) {
                int displayHeight = resolveDisplayHeight(graphicsManager);
                for (int vertex = 3; vertex >= 0; vertex--)
                    vertexBuffer.put(presentationQuad[vertex * 2]).put(displayHeight - presentationQuad[vertex * 2 + 1]);
            } else {
                vertexBuffer.put(x0).put(y0).put(x1).put(y0).put(x1).put(y1).put(x0).put(y1);
            }
            texCoordBuffer.put(u0).put(v0).put(u1).put(v0).put(u1).put(v1).put(u0).put(v1);
            paletteBuffer.put(effectivePalette).put(effectivePalette).put(effectivePalette).put(effectivePalette);
        }
        vertexBuffer.flip();
        texCoordBuffer.flip();
        paletteBuffer.flip();

        // Upload and bind vertex data
        glBindBuffer(GL_ARRAY_BUFFER, vertexVboId);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(ATTRIB_POSITION, 2, GL_FLOAT, false, 0, 0L);
        glEnableVertexAttribArray(ATTRIB_POSITION);

        glBindBuffer(GL_ARRAY_BUFFER, texCoordVboId);
        glBufferData(GL_ARRAY_BUFFER, texCoordBuffer, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(ATTRIB_TEXCOORD, 2, GL_FLOAT, false, 0, 0L);
        glEnableVertexAttribArray(ATTRIB_TEXCOORD);

        glBindBuffer(GL_ARRAY_BUFFER, paletteVboId);
        glBufferData(GL_ARRAY_BUFFER, paletteBuffer, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(ATTRIB_PALETTE, 1, GL_FLOAT, false, 0, 0L);
        glEnableVertexAttribArray(ATTRIB_PALETTE);

        shaderProgram.setPresentationColor(presentationHue,presentationOpacity);
        try {
            glDrawArrays(presentationCount > 1 ? GL_TRIANGLES : GL_TRIANGLE_FAN, 0,
                    presentationCount > 1 ? presentationCount * 6 : 4);
        }
        finally { shaderProgram.setPresentationColor(0,1); }

    }

    void presentation(float[] quad,float hue,float opacity) {
        if(quad.length!=8) throw new IllegalArgumentException("Presentation quad requires four vertices");
        for(float value:quad) if(!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite picture vertex");
        presentationQuad=quad.clone();presentationCount=1;presentationHue=hue;presentationOpacity=Math.clamp(opacity,0,1);
        presentationPriorityShader=graphicsManager.isUseSpritePriorityShader();
    }

    /** Only adjacent commands with identical draw state may share a submission. */
    boolean appendPresentation(PatternRenderCommand next) {
        if (presentationCount == 0 || next.presentationCount != 1 || presentationCount >= MAX_PRESENTATION_PATTERNS
                || graphicsManager != next.graphicsManager || paletteTextureId != next.paletteTextureId
                || atlasIndex != next.atlasIndex || paletteIndex != next.paletteIndex
                // Deferred resolvers may change rows/residency per invocation. Keep their original call boundary.
                || paletteReplay != null || next.paletteReplay != null
                || paletteReplayUnderwater != next.paletteReplayUnderwater
                || piecePriority != next.piecePriority || capturedTileOcclusionPaletteMask != next.capturedTileOcclusionPaletteMask
                || ghostEffectActive != next.ghostEffectActive || ghostAlpha != next.ghostAlpha
                || presentationPriorityShader != next.presentationPriorityShader
                || presentationHue != next.presentationHue || presentationOpacity != next.presentationOpacity) return false;
        int required = (presentationCount + 1) * 12;
        if (presentationBatchData == null || presentationBatchData.length < required) {
            int capacity = Math.min(MAX_PRESENTATION_PATTERNS * 12,
                    Math.max(required, presentationBatchData == null ? 96 : presentationBatchData.length * 2));
            presentationBatchData = presentationBatchData == null ? new float[capacity]
                    : java.util.Arrays.copyOf(presentationBatchData, capacity);
        }
        if (presentationCount == 1) writePresentationData(this, 0);
        writePresentationData(next, presentationCount++ * 12);
        next.recycle();
        return true;
    }

    private void writePresentationData(PatternRenderCommand source, int offset) {
        System.arraycopy(source.presentationQuad, 0, presentationBatchData, offset, 8);
        presentationBatchData[offset + 8] = source.u0;
        presentationBatchData[offset + 9] = source.v0;
        presentationBatchData[offset + 10] = source.u1;
        presentationBatchData[offset + 11] = source.v1;
    }

    int presentationPatternCount() { return presentationCount; }

    /**
     * Clean up GL state after all patterns are rendered.
     * Call this after the last pattern command in a frame.
     */
    public static void cleanupFrameState(GraphicsManager graphicsManager) {
        if (stateInitialized) {
            glDisableVertexAttribArray(ATTRIB_POSITION);
            glDisableVertexAttribArray(ATTRIB_TEXCOORD);
            glDisableVertexAttribArray(ATTRIB_PALETTE);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
            ShaderProgram shaderProgram = graphicsManager.getShaderProgram();
            if (shaderProgram != null) {
                shaderProgram.stop();
            }
            glDisable(GL_BLEND);
            stateInitialized = false;
        }
    }

    private static int resolveDisplayHeight(GraphicsManager graphicsManager) {
        RenderProjection projection = graphicsManager.getProjectionSource();
        if (projection != null && projection.isFBOProjectionActive()) {
            return projection.getCurrentDisplayHeight();
        }
        // Cached on the GraphicsManager (invalidated on reshape/resetState) instead
        // of a config-service lookup per obtain() — this runs per tile per frame on
        // the SAT replay path.
        return graphicsManager.getConfiguredScreenHeightPx();
    }

    /**
     * Cleanup VBOs and VAO.
     */
    public static void cleanup() {
        if (vaoId != 0) {
            glDeleteVertexArrays(vaoId);
            vaoId = 0;
        }
        if (vertexVboId != 0) {
            glDeleteBuffers(vertexVboId);
            vertexVboId = 0;
        }
        if (texCoordVboId != 0) {
            glDeleteBuffers(texCoordVboId);
            texCoordVboId = 0;
        }
        if (paletteVboId != 0) {
            glDeleteBuffers(paletteVboId);
            paletteVboId = 0;
        }
        cleanupNativeState();
    }

    public static void cleanupHeadless() {
        vaoId = 0;
        vertexVboId = 0;
        texCoordVboId = 0;
        paletteVboId = 0;
        cleanupNativeState();
    }

    private static void cleanupNativeState() {
        if (vertexBuffer != null) {
            MemoryUtil.memFree(vertexBuffer);
            MemoryUtil.memFree(texCoordBuffer);
            MemoryUtil.memFree(paletteBuffer);
            vertexBuffer = null;
            texCoordBuffer = null;
            paletteBuffer = null;
        }
        for (PatternRenderCommand command : pool) {
            command.graphicsManager = null;
            command.leased = false;
        }
        pool.clear();
        clearUniformLocationCache();
        resetFrameState();
    }
}
