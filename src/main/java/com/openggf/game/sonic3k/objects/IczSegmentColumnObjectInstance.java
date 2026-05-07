package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;

/**
 * Object 0xB3 - ICZ segmented column.
 *
 * <p>ROM reference: {@code Obj_ICZSegmentColumn} (sonic3k.asm:188694-188873).
 * The visible/solid pieces are child objects produced by
 * {@code CreateChild8_TreeListRepeated}; the root object only owns the cascade.
 */
public class IczSegmentColumnObjectInstance extends AbstractObjectInstance {

    private static final int OBJECT_ID = 0xB3;
    private static final int NORMAL_SEGMENT_COUNT = 3;
    private static final int TALL_SEGMENT_COUNT = 4;
    private static final int SEGMENT_SUBTYPE_STEP = 2;
    private static final int SEGMENT_Y_SHIFT = 4;
    private static final int NORMAL_MAPPING_FRAME = 0x0A;
    private static final int TOP_CAP_MAPPING_FRAME = 0x03;

    private final int x;
    private final int y;
    private final int segmentCount;
    private boolean spawnedSegments;
    private int screenShakeFrames;
    private boolean ownsScreenShakeFlag;

    public IczSegmentColumnObjectInstance(ObjectSpawn spawn) {
        super(spawn, "ICZSegmentColumn");
        this.x = spawn.x();
        this.y = spawn.y();
        this.segmentCount = (spawn.subtype() & 0xFF) == 0 ? NORMAL_SEGMENT_COUNT : TALL_SEGMENT_COUNT;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!spawnedSegments) {
            spawnSegments();
            spawnedSegments = true;
        }
        updateTimedShake();
    }

    private void spawnSegments() {
        Segment previous = null;
        for (SegmentSpec spec : segmentSpecsForTesting()) {
            Segment parent = previous;
            previous = spawnChild(() -> new Segment(spec.x(), spec.y(), spec.subtype(), spec.mappingFrame(), this, parent));
        }
    }

    List<SegmentSpec> segmentSpecsForTesting() {
        List<SegmentSpec> specs = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            int subtype = i * SEGMENT_SUBTYPE_STEP;
            int mappingFrame = subtype == 6 ? TOP_CAP_MAPPING_FRAME : NORMAL_MAPPING_FRAME;
            specs.add(new SegmentSpec(subtype, x, y - (subtype << SEGMENT_Y_SHIFT), mappingFrame));
        }
        return List.copyOf(specs);
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // The root object is only a child-spawner; ROM rendering is handled by the segment children.
    }

    public record SegmentSpec(int subtype, int x, int y, int mappingFrame) {
    }

    public static final class Segment extends AbstractObjectInstance
            implements SolidObjectProvider, SolidObjectListener {

        private static final String ART_KEY = Sonic3kObjectArtKeys.ICZ_WALL_AND_COLUMN;
        private static final int PRIORITY = 5; // ROM: priority $280
        private static final int SOLID_HALF_WIDTH = 0x2B;
        private static final int SOLID_HALF_HEIGHT = 0x10;
        private static final int TOP_LANDING_HALF_WIDTH = 0x20;
        private static final int PUSH_BREAK_SPEED = 0x600;
        private static final int PUSH_ANIMATION_ID = 2;
        private static final int CASCADE_WAIT_BASE = 0x0F;
        private static final int CASCADE_WAIT_STEP = 4;
        private static final int FALL_TIMER_START = 7;
        private static final int FALL_STEP_PIXELS = 4;
        private static final int LAND_SHAKE_FRAMES = 0x10;

        private enum Phase {
            NORMAL,
            WAITING_TO_FALL,
            FALLING
        }

        private final IczSegmentColumnObjectInstance root;
        private final Segment previous;
        private int x;
        private int y;
        private int subtype;
        private final int mappingFrame;
        private Phase phase = Phase.NORMAL;
        private int timer;
        private boolean cascadeActive;

        private Segment(int x, int y, int subtype, int mappingFrame,
                        IczSegmentColumnObjectInstance root, Segment previous) {
            super(new ObjectSpawn(x, y, OBJECT_ID, subtype, 0, false, y), "ICZSegmentColumnSegment");
            this.x = x;
            this.y = y;
            this.subtype = subtype;
            this.mappingFrame = mappingFrame;
            this.root = root;
            this.previous = previous;
        }

        static Segment forTesting(int x, int y, int subtype, Segment previous) {
            int mappingFrame = subtype == 6 ? TOP_CAP_MAPPING_FRAME : NORMAL_MAPPING_FRAME;
            return new Segment(x, y, subtype, mappingFrame, null, previous);
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (isDestroyed()) {
                return;
            }

            switch (phase) {
                case NORMAL -> updateNormal();
                case WAITING_TO_FALL -> {
                    checkpointAll();
                    if (timer-- <= 0) {
                        startFalling();
                    }
                }
                case FALLING -> {
                    checkpointAll();
                    y += FALL_STEP_PIXELS;
                    if (timer-- <= 0) {
                        landAfterFall();
                    }
                }
            }
        }

        private void updateNormal() {
            if (subtype != 0 && previous != null && previous.cascadeActive) {
                startCascadeWait();
                return;
            }

            SolidCheckpointBatch batch = checkpointAll();
            if (subtype == 0 && mappingFrame != TOP_CAP_MAPPING_FRAME) {
                applyBreakContact(batch);
            }
        }

        private void applyBreakContact(SolidCheckpointBatch batch) {
            for (var entry : batch.perPlayer().entrySet()) {
                if (isDestroyed()) {
                    return;
                }
                if (entry.getKey() instanceof AbstractPlayableSprite player) {
                    tryBreakFromContact(player, entry.getValue());
                }
            }
        }

        private void tryBreakFromContact(AbstractPlayableSprite player, PlayerSolidContactResult result) {
            if (result == null || !result.pushingNow()) {
                return;
            }
            int xSpeed = result.preContact().xSpeed();
            if (Math.abs(xSpeed) < PUSH_BREAK_SPEED || result.preContact().animationId() != PUSH_ANIMATION_ID) {
                return;
            }

            player.setXSpeed((short) xSpeed);
            player.setGSpeed((short) xSpeed);
            player.setPushing(false);
            player.setOnObject(false);
            cascadeActive = true;
            setDestroyed(true);
            playSfx(Sonic3kSfx.COLLAPSE.id);
        }

        private void startCascadeWait() {
            phase = Phase.WAITING_TO_FALL;
            cascadeActive = true;
            timer = (subtype * CASCADE_WAIT_STEP) + CASCADE_WAIT_BASE;
        }

        private void startFalling() {
            phase = Phase.FALLING;
            timer = FALL_TIMER_START;
            subtype -= SEGMENT_SUBTYPE_STEP;
        }

        private void landAfterFall() {
            phase = Phase.NORMAL;
            cascadeActive = false;
            triggerTimedShake();
            playSfx(Sonic3kSfx.MECHA_LAND.id);
        }

        private void playSfx(int sfxId) {
            var services = tryServices();
            if (services != null && services.audioManager() != null) {
                services.audioManager().playSfx(sfxId);
            }
        }

        private void triggerTimedShake() {
            if (root != null) {
                root.triggerScreenShake(LAND_SHAKE_FRAMES);
            }
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);
        }

        @Override
        public int getTopLandingHalfWidth(PlayableEntity playerEntity, int collisionHalfWidth) {
            return TOP_LANDING_HALF_WIDTH;
        }

        @Override
        public boolean isSolidFor(PlayableEntity playerEntity) {
            return !isDestroyed();
        }

        @Override
        public SolidExecutionMode solidExecutionMode() {
            return SolidExecutionMode.MANUAL_CHECKPOINT;
        }

        @Override
        public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
            // Manual checkpoints drive contact state from update(), matching the inline SolidObjectFull call.
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }
            PatternSpriteRenderer renderer = getRenderer(ART_KEY);
            if (renderer != null) {
                renderer.drawFrameIndex(mappingFrame, x, y, false, false);
            }
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY);
        }
    }

    private void triggerScreenShake(int frames) {
        screenShakeFrames = Math.max(screenShakeFrames, frames);
    }

    private void updateTimedShake() {
        var services = tryServices();
        if (services == null || services.gameState() == null) {
            return;
        }
        if (screenShakeFrames > 0) {
            services.gameState().setScreenShakeActive(true);
            screenShakeFrames--;
            ownsScreenShakeFlag = true;
        } else if (ownsScreenShakeFlag) {
            services.gameState().setScreenShakeActive(false);
            ownsScreenShakeFlag = false;
        }
    }
}
