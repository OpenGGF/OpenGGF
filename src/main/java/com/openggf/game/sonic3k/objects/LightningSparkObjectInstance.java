package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindStateful;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.art.SpriteArtSet;

import java.util.List;
import java.util.Arrays;

/**
 * Lightning shield spark particle (sonic3k.asm:34851-34898).
 * Created by {@link LightningShieldObjectInstance#triggerSparks()} — 4 sparks fly
 * diagonally with gravity, playing animation script 0 (frames [0,1,2] delay=0).
 * Auto-deletes after {@link #MAX_LIFE} frames.
 *
 * <p>Renders tiles directly via {@link GraphicsManager#renderPatternWithId} rather
 * than through PlayerSpriteRenderer/DPLC, matching the ROM where spark art is
 * DMA-loaded once at init (not managed by PLCLoad_Shields).
 */
public class LightningSparkObjectInstance extends AbstractObjectInstance implements RewindRecreatable {

    /** Gravity per frame in subpixels (sonic3k.asm:34889: addi.w #$18,y_vel(a0)) */
    private static final int GRAVITY = 0x18;

    /** Lifetime in frames before auto-delete */
    private static final int MAX_LIFE = 21;

    /** Animation script index for sparks */
    private static final int SPARK_ANIM_SCRIPT = 0;

    /** Pattern atlas base ID for spark tiles, isolated from level object art. */
    private static final int SPARK_PATTERN_BASE = PatternAtlasRange.TRANSIENT_EFFECTS.base();

    /** Shared descriptor for spark tiles: palette 0, no flip, no priority. */
    private static final PatternDesc SPARK_DESC = new PatternDesc(0);

    // The public constructors accept art independent of the current provider. A
    // provider-key recipe alone would replace it (or turn a null recipe into art).
    // Keep the default owner/scalar capture and snapshot only pixels and scripts.
    private final SparkArtState sparkArtState;

    private static final class SparkArtState implements RewindStateful<SparkArtSnapshot> {
        private SpriteAnimationSet animSet;
        private Pattern[] sparkTiles;
        private boolean tilesCached;

        private SparkArtState(SpriteAnimationSet animSet, Pattern[] sparkTiles) {
            this.animSet = animSet;
            this.sparkTiles = sparkTiles;
        }

        private boolean hasTiles() { return sparkTiles != null; }

        private SpriteAnimationScript script() {
            return animSet == null ? null : animSet.getScript(SPARK_ANIM_SCRIPT);
        }

        /** Cache in the current atlas once; restoration resets this upload scratch. */
        private void ensureTilesCached(GraphicsManager gfx) {
            if (tilesCached) return;
            for (int i = 0; i < sparkTiles.length; i++) {
                gfx.cachePatternTexture(sparkTiles[i], SPARK_PATTERN_BASE + i);
            }
            tilesCached = true;
        }

        @Override
        public SparkArtSnapshot captureRewindStateValue() {
            byte[][] pixels = sparkTiles == null ? null : new byte[sparkTiles.length][];
            if (sparkTiles != null) {
                for (int i = 0; i < sparkTiles.length; i++) {
                    if (sparkTiles[i] != null) {
                        pixels[i] = new byte[Pattern.PATTERN_SIZE_IN_MEM];
                        sparkTiles[i].copyInto(pixels[i], 0);
                    }
                }
            }
            ScriptSnapshot[] scripts = animSet == null ? null : animSet.getAllScripts().entrySet().stream()
                    .map(entry -> new ScriptSnapshot(entry.getKey(), entry.getValue()))
                    .toArray(ScriptSnapshot[]::new);
            return new SparkArtSnapshot(pixels, scripts);
        }

        @Override
        public void restoreRewindStateValue(SparkArtSnapshot snapshot) {
            byte[][] pixels = snapshot.pixels();
            sparkTiles = pixels == null ? null : new Pattern[pixels.length];
            if (pixels != null) {
                for (int i = 0; i < pixels.length; i++) {
                    if (pixels[i] == null) continue;
                    Pattern tile = new Pattern();
                    for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                        for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                            tile.setPixel(x, y, pixels[i][y * Pattern.PATTERN_WIDTH + x]);
                        }
                    }
                    sparkTiles[i] = tile;
                }
            }
            ScriptSnapshot[] scripts = snapshot.scripts();
            animSet = scripts == null ? null : new SpriteAnimationSet();
            if (scripts != null) {
                for (ScriptSnapshot script : scripts) animSet.addScript(script.id(), script.toScript());
            }
            tilesCached = false;
        }
    }

    private record SparkArtSnapshot(byte[][] pixels, ScriptSnapshot[] scripts) {
        private SparkArtSnapshot {
            pixels = copyPixels(pixels);
            scripts = scripts == null ? null : scripts.clone();
        }

        @Override
        public byte[][] pixels() { return copyPixels(pixels); }

        @Override
        public ScriptSnapshot[] scripts() { return scripts == null ? null : scripts.clone(); }

        private static byte[][] copyPixels(byte[][] source) {
            if (source == null) return null;
            byte[][] copy = source.clone();
            for (int i = 0; i < copy.length; i++) if (copy[i] != null) copy[i] = copy[i].clone();
            return copy;
        }
    }

    private record ScriptSnapshot(int id, int delay, int[] frames, SpriteAnimationEndAction endAction,
                                  int endParam, int[] trailingBytes) {
        private ScriptSnapshot {
            frames = frames.clone();
            trailingBytes = trailingBytes.clone();
        }

        private ScriptSnapshot(int id, SpriteAnimationScript script) {
            this(id, script.delay(), script.frames().stream().mapToInt(Integer::intValue).toArray(),
                    script.endAction(), script.endParam(),
                    script.trailingBytes().stream().mapToInt(Integer::intValue).toArray());
        }

        @Override public int[] frames() { return frames.clone(); }
        @Override public int[] trailingBytes() { return trailingBytes.clone(); }

        private SpriteAnimationScript toScript() {
            return new SpriteAnimationScript(delay, Arrays.stream(frames).boxed().toList(), endAction, endParam,
                    Arrays.stream(trailingBytes).boxed().toList());
        }
    }

    private int currentX;
    private int currentY;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;

    private int life;
    private int frameIndex;
    private int delayCounter;
    private int currentMappingFrame;
    /**
     * ROM Obj_LightningShield_CreateSpark copies the shield's art_tile into each spark
     * (sonic3k.asm:34865), and the shield's bit 15 tracks Player_1's art_tile every frame
     * (init sonic3k.asm:34764-34766, main sonic3k.asm:34791-34795). Captured once at creation;
     * the spark never re-reads it. Plain field so the generic rewind capture restores it.
     */
    private boolean highPriority;

    private final SubpixelMotion.State motionState = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);

    LightningSparkObjectInstance() {
        this(0, 0, 0, 0, null, null, false);
    }

    public LightningSparkObjectInstance(int x, int y, int xVel, int yVel,
            SpriteAnimationSet animSet, Pattern[] sparkTiles) {
        this(x, y, xVel, yVel, animSet, sparkTiles, false);
    }

    /**
     * @param highPriority the creating shield's art-word bit 15 at creation time
     *                     (sonic3k.asm:34865), i.e. the player's flag that frame
     */
    public LightningSparkObjectInstance(int x, int y, int xVel, int yVel,
            SpriteAnimationSet animSet, Pattern[] sparkTiles, boolean highPriority) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "LightningSpark");
        this.highPriority = highPriority;
        this.currentX = x;
        this.currentY = y;
        this.xSub = 0;
        this.ySub = 0;
        this.xVel = xVel;
        this.yVel = yVel;
        this.sparkArtState = new SparkArtState(animSet, sparkTiles);
        this.life = 0;
        this.frameIndex = 0;
        this.currentMappingFrame = 0;
        initAnimation();
    }

    /**
     * Rewind recreate factory. The captured dynamic spawn carries the spark's
     * position; movement/animation scalars and the captured art-word bit 15 are
     * reapplied by the generic field capturer after recreate, so the constructor
     * velocities and high-priority flag are placeholders.
     * Art (animation set + spark tiles) is re-fetched from the live S3K object-art
     * provider exactly as {@link LightningShieldObjectInstance#triggerSparks()}
     * does for direct factory callers. Managed restore then reapplies the captured
     * pixel/script values through sparkArtState, preserving independent and null
     * constructor recipes without retaining a renderer or an atlas handle.
     */
    public static LightningSparkObjectInstance forRewindRecreate(
            ObjectSpawn spawn, ObjectServices services) {
        SpriteAnimationSet animSet = null;
        Pattern[] sparkTiles = null;
        GameModule module = (services != null) ? services.gameModule() : null;
        if (module != null
                && module.getObjectArtProvider() instanceof Sonic3kObjectArtProvider s3k) {
            SpriteArtSet sparkArt = s3k.getShieldArtSet(Sonic3kObjectArtKeys.LIGHTNING_SPARK);
            if (sparkArt != null) {
                animSet = sparkArt.animationSet();
                sparkTiles = sparkArt.artTiles();
            }
        }
        return new LightningSparkObjectInstance(spawn.x(), spawn.y(), 0, 0, animSet, sparkTiles);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return forRewindRecreate(ctx.spawn(), ctx.objectServices());
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public boolean isPersistent() {
        return true; // Don't cull by spawn position — has no spawn
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        life++;
        if (life >= MAX_LIFE) {
            setDestroyed(true);
            return;
        }

        // Apply subpixel movement with gravity
        motionState.x = currentX; motionState.y = currentY;
        motionState.xSub = xSub;  motionState.ySub = ySub;
        motionState.xVel = xVel;  motionState.yVel = yVel;
        SubpixelMotion.moveSprite(motionState, GRAVITY);
        currentX = motionState.x; currentY = motionState.y;
        xSub = motionState.xSub;  ySub = motionState.ySub;
        yVel = motionState.yVel;

        stepAnimation();
    }

    // Obj_LightningShield_CreateSpark writes priority $80 (sonic3k.asm:34867) and copies the
    // shield's art_tile (sonic3k.asm:34865), so the spark carries the player's bit 15 as it
    // stood when the spark was created; see {@link #highPriority}.
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x80);

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        return highPriority;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || !sparkArtState.hasTiles()) return;
        // Frame 2 = empty (invisible flash frame)
        if (currentMappingFrame >= 2) return;

        GraphicsManager gfx = services().graphicsManager();
        sparkArtState.ensureTilesCached(gfx);

        if (currentMappingFrame == 0) {
            // 1×1 tile at (-4, -4) — ROM Map_LightningShield frame $C
            gfx.renderPatternWithId(SPARK_PATTERN_BASE, SPARK_DESC, currentX - 4, currentY - 4);
        } else {
            // 2×2 tiles at (-8, -8) — ROM Map_LightningShield frame $D
            // Column-major: tileOffset = col * heightTiles + row
            gfx.renderPatternWithId(SPARK_PATTERN_BASE + 1, SPARK_DESC, currentX - 8, currentY - 8);
            gfx.renderPatternWithId(SPARK_PATTERN_BASE + 2, SPARK_DESC, currentX - 8, currentY);
            gfx.renderPatternWithId(SPARK_PATTERN_BASE + 3, SPARK_DESC, currentX,     currentY - 8);
            gfx.renderPatternWithId(SPARK_PATTERN_BASE + 4, SPARK_DESC, currentX,     currentY);
        }
    }

    private void initAnimation() {
        frameIndex = 0;
        SpriteAnimationScript script = sparkArtState.script();
        if (script != null) {
            delayCounter = script.delay();
            if (!script.frames().isEmpty()) {
                currentMappingFrame = script.frames().get(0);
            }
        }
    }

    private void stepAnimation() {
        SpriteAnimationScript script = sparkArtState.script();
        if (script == null || script.frames().isEmpty()) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }
        delayCounter = script.delay();

        frameIndex++;
        if (frameIndex >= script.frames().size()) {
            frameIndex = 0; // Always loop for sparks
        }
        currentMappingFrame = script.frames().get(frameIndex);
    }
}
