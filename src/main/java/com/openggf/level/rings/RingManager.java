package com.openggf.level.rings;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.spawn.AbstractPlacementManager;
import com.openggf.level.ChunkDesc;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.ShieldType;
import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.PhysicsProvider;
import com.openggf.physics.TrigLookupTable;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.RingSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

/**
 * Handles ring collection state, sparkle animation, rendering, and lost-ring behavior.
 */
public class RingManager implements RewindSnapshottable<RingSnapshot> {
    private static final System.Logger LOG = System.getLogger(RingManager.class.getName());
    private static final int MAX_ATTRACTED_RINGS = 32;
    // ROM: AttractedRing_Move — base acceleration is $30 subpixels/frame²
    private static final int ATTRACT_ACCEL = 0x30;
    // ROM: attraction detection uses a 128×128 box (±$40 from player centre)
    private static final int ATTRACT_BOX_HALF = 0x40;
    // ROM: ring collision half-width (d1=6 in Test_Ring_Collisions)
    private static final int RING_COLLISION_HALF = 6;
    // ROM: ReactToItem/Test_Ring_Collisions skip ring pickup while flashtime >= 90.
    private static final int RING_INVULNERABLE_BLOCK_THRESHOLD = 90;
    // ROM: Obj_Attracted_Ring collision_flags $47 -> Touch_Sizes index 7 = 6x6.
    private static final int ATTRACT_TOUCH_RADIUS = 6;

    private final RingPlacement placement;
    private final RingRenderer renderer;
    private final LostRingPool lostRings;
    private final LevelManager levelManager;
    private final AudioManager audioManager;
    private final boolean stageRingsUseObjectTouchCollection;
    private PatternSpriteRenderer.FrameBounds spinBounds;
    private final AttractedRing[] attractedRings;


    public RingManager(List<RingSpawn> spawns, RingSpriteSheet spriteSheet,
                       LevelManager levelManager, TouchResponseTable touchResponseTable) {
        this(spawns, spriteSheet, levelManager, touchResponseTable, GameServices.audio());
    }

    public RingManager(List<RingSpawn> spawns, RingSpriteSheet spriteSheet,
                       LevelManager levelManager, TouchResponseTable touchResponseTable,
                       AudioManager audioManager) {
        // Feature-flag: ROM parity sources this from the current game's physics feature set.
        // S1 routes stage rings through Obj25's touch-response pipeline (Touch_Rings);
        // S2/S3K collect them via the bounding-box sweep (Touch_Rings_Test).
        GameModule module = GameServices.currentOrBootstrapGameModule();
        PhysicsProvider physProvider = module != null ? module.getPhysicsProvider() : null;
        PhysicsFeatureSet featureSet = physProvider != null ? physProvider.getFeatureSet() : null;
        this.placement = new RingPlacement(spawns,
                featureSet != null && featureSet.stageRingSweepUsesRawCameraWindow());
        this.renderer = (spriteSheet != null && spriteSheet.getFrameCount() > 0)
                ? new RingRenderer(spriteSheet)
                : null;
        this.levelManager = levelManager;
        this.audioManager = audioManager;
        this.lostRings = new LostRingPool(levelManager, this.renderer, touchResponseTable, audioManager);
        this.stageRingsUseObjectTouchCollection =
                featureSet != null && featureSet.stageRingsUseObjectTouchCollection();
        this.attractedRings = new AttractedRing[MAX_ATTRACTED_RINGS];
        for (int i = 0; i < MAX_ATTRACTED_RINGS; i++) {
            attractedRings[i] = new AttractedRing();
        }
    }

    public void reset(int cameraX) {
        placement.reset(cameraX);
        lostRings.reset();
        spinBounds = null;
        releaseAttractedRingSlots();
    }

    /**
     * Replaces the ring spawn list with a new one from the editor.
     * Resets all collection state (collected BitSet, sparkle frames).
     * In editor mode this is acceptable -- all rings become uncollected.
     */
    public void resyncSpawnList(List<RingSpawn> newSpawns) {
        placement.replaceSpawnsAndReset(newSpawns);
        lostRings.reset();
        releaseAttractedRingSlots();
    }

    public void ensurePatternsCached(GraphicsManager graphicsManager, int basePatternIndex) {
        if (renderer != null) {
            renderer.ensurePatternsCached(graphicsManager, basePatternIndex);
        }
    }

    public void update(int cameraX, AbstractPlayableSprite player, int frameCounter) {
        update(cameraX, player, frameCounter, true);
    }

    public void update(int cameraX, AbstractPlayableSprite player, int frameCounter,
                       boolean collectStageRingsInUpdate) {
        placement.update(cameraX);
        if (player == null || player.getDead()) {
            return;
        }

        if (collectStageRingsInUpdate && !stageRingsUseObjectTouchCollection) {
            collectStageRings(player, frameCounter);
        }

        // Lightning shield ring attraction — S3K only
        PhysicsFeatureSet featureSet = player.getPhysicsFeatureSet();
        if (featureSet == null) {
            GameModule module = GameServices.currentOrBootstrapGameModule();
            PhysicsProvider physProvider = module != null ? module.getPhysicsProvider() : null;
            if (physProvider != null) {
                featureSet = physProvider.getFeatureSet();
            }
        }
        boolean lightningAttractionActive = featureSet != null && featureSet.lightningShieldEnabled()
                && player.getShieldType() == ShieldType.LIGHTNING;
        if (lightningAttractionActive) {
            int pcx = player.getCentreX();
            int pcy = player.getCentreY();
            int activeCount = placement.activeIndexCount();
            for (int i = 0; i < activeCount; i++) {
                int index = placement.activeIndexAt(i);
                if (index < 0 || placement.isCollected(index)) {
                    continue;
                }
                RingSpawn ring = placement.getSpawn(index);
                int dx = pcx - ring.x();
                int dy = pcy - ring.y();
                // ROM: box check — ±$40 from player centre, extended by ring half-width
                int ringHalf = featureSet.ringCollisionWidth();
                int effectiveHalf = ATTRACT_BOX_HALF + ringHalf;
                if (Math.abs(dx) <= effectiveHalf && Math.abs(dy) <= effectiveHalf) {
                    if (addAttractedRing(index, ring.x(), ring.y())) {
                        placement.markCollected(index);
                    }
                }
            }
        }
        if (lightningAttractionActive || hasActiveAttractedRings()) {
            updateAttractedRings(player, frameCounter);
        }
    }

    /**
     * Touch-phase collection for placed rings.
     * <p>
     * ROM parity: normal ring pickup is part of the player/object touch pass
     * (ReactToItem/Test_Ring_Collisions), not a late end-of-frame sweep. Calling
     * this from the touch phase keeps ring routine transitions and SST slot
     * lifetimes aligned with the disassembly. {@link #update(int,
     * AbstractPlayableSprite, int, boolean)} keeps the legacy collection path
     * only for callers that explicitly request it.
     */
    public void collectStageRings(AbstractPlayableSprite player, int frameCounter) {
        if (cannotCollectRings(player)) {
            return;
        }
        if (!stageRingsUseObjectTouchCollection
                && player.getInvulnerableFrames() >= RING_INVULNERABLE_BLOCK_THRESHOLD) {
            return;
        }
        int activeCount = placement.activeIndexCount();
        if (activeCount == 0) {
            return;
        }

        PhysicsFeatureSet featureSet = player.getPhysicsFeatureSet();
        int playerLeft = player.getCentreX() - 8;
        // ROM ReactToItem/Test_Ring_Collisions uses obHeight-3 for Sonic's
        // touch box before the ducking special-case; this is not limited to
        // rolling frames. Using the full standing radius makes airborne ring
        // pickups happen one frame too early in MZ1 trace replay.
        int playerYRadius = Math.max(1, player.getYRadius() - 3);
        int playerTop = player.getCentreY() - playerYRadius;
        int playerHeight = playerYRadius * 2;
        if (player.getCrouching()) {
            playerTop += 12;
            playerHeight = 20;
        }
        int ringWidth = featureSet != null ? featureSet.ringCollisionWidth() : RING_COLLISION_HALF;
        int ringHeight = featureSet != null ? featureSet.ringCollisionHeight() : RING_COLLISION_HALF;

        for (int i = 0; i < activeCount; i++) {
            int index = placement.activeIndexAt(i);
            if (index < 0 || placement.isCollected(index)) {
                continue;
            }
            RingSpawn ring = placement.getSpawn(index);

            if (!ringOverlapsPlayer(playerLeft, playerTop, playerHeight, 0x10,
                    ring.x(), ring.y(), ringWidth, ringHeight)) {
                continue;
            }

            collectPlacedRingAtIndex(index, player, frameCounter);
        }
    }

    public boolean usesObjectTouchCollection() {
        return stageRingsUseObjectTouchCollection;
    }

    public boolean collectPlacedRing(RingSpawn ring, AbstractPlayableSprite player, int frameCounter) {
        if (ring == null || cannotCollectRings(player)) {
            return false;
        }
        if (player.getInvulnerableFrames() >= RING_INVULNERABLE_BLOCK_THRESHOLD) {
            return false;
        }
        int index = placement.getSpawnIndex(ring);
        if (index < 0 || placement.isCollected(index)) {
            return false;
        }
        // S1 Obj25 collection is triggered from Sonic's ReactToItem slot, after
        // this engine has already run the ring object's update for the frame.
        // Start the Obj25 Ring_Sparkle/DeleteObject cadence on the next object
        // execution, matching the ROM routine that owns the slot lifetime.
        collectPlacedRingAtIndex(index, player, frameCounter + 1);
        return true;
    }

    private void collectPlacedRingAtIndex(int index, AbstractPlayableSprite player, int frameCounter) {
        placement.markCollected(index);
        if (renderer != null && renderer.getSparkleFrameCount() > 0) {
            placement.setSparkleStartFrame(index, frameCounter);
        }
        audioManager.playSfx(GameSound.RING);
        player.addRings(1);
    }

    private static boolean cannotCollectRings(AbstractPlayableSprite player) {
        if (player == null || player.getDead()) {
            return true;
        }
        if (player.isTouchResponseSuppressedByObjectControl()) {
            return true;
        }
        return player.isCpuControlled() && player.isObjectControlled();
    }

    private static boolean ringOverlapsPlayer(int playerX, int playerY, int playerHeight,
                                              int playerWidth, int ringX, int ringY,
                                              int ringWidth, int ringHeight) {
        int dx = ringX - ringWidth - playerX;
        if (dx < 0) {
            int sum = (dx & 0xFFFF) + ((ringWidth * 2) & 0xFFFF);
            if (sum <= 0xFFFF) {
                return false;
            }
        } else if (dx > playerWidth) {
            return false;
        }

        int dy = ringY - ringHeight - playerY;
        if (dy < 0) {
            int sum = (dy & 0xFFFF) + ((ringHeight * 2) & 0xFFFF);
            if (sum <= 0xFFFF) {
                return false;
            }
        } else if (dy > playerHeight) {
            return false;
        }

        return true;
    }

    /**
     * Advance the shared spilled-ring spin one frame (ROM ChangeRingFrame,
     * s2.asm Obj37 / Ring_spill_anim_*). Per-ring physics now runs in the object
     * exec loop ({@link LostRingObjectInstance#updateMovement}); this call only
     * ticks the global spin owner once per frame.
     */
    public void updateLostRingPhysics(int frameCounter) {
        lostRings.tickSpillAnimation();
    }

    public void draw(int frameCounter) {
        if (renderer == null) {
            return;
        }

        int spinFrameIndex = renderer.getSpinFrameIndex(frameCounter);
        int activeCount = placement.activeIndexCount();
        for (int i = 0; i < activeCount; i++) {
            int index = placement.activeIndexAt(i);
            if (index < 0) {
                continue;
            }
            RingSpawn ring = placement.getSpawn(index);
            if (!placement.isCollected(index)) {
                renderer.drawFrameIndex(spinFrameIndex, ring.x(), ring.y());
                continue;
            }

            int sparkleStartFrame = placement.getSparkleStartFrame(index);
            if (sparkleStartFrame < 0 || renderer.getSparkleFrameCount() <= 0) {
                continue;
            }

            int elapsed = frameCounter - sparkleStartFrame;
            if (elapsed < 0) {
                elapsed = 0;
            }
            int sparkleFrameOffset = elapsed / renderer.getSparkleFrameDelay();
            if (sparkleFrameOffset >= renderer.getSparkleFrameCount()) {
                if (isCollectedAndSparkleDone(index, frameCounter)) {
                    placement.clearSparkle(index);
                }
                continue;
            }
            int sparkleFrameIndex = renderer.getSparkleStartIndex() + sparkleFrameOffset;
            renderer.drawFrameIndex(sparkleFrameIndex, ring.x(), ring.y());
        }

        // Draw attracted rings and their collected sparkle phase.
        int attractSpinFrame = renderer.getSpinFrameIndex(frameCounter);
        for (AttractedRing ar : attractedRings) {
            if (!ar.active) {
                continue;
            }
            if (ar.collected) {
                int sparkleFrame = attractedSparkleFrame(ar, frameCounter);
                if (sparkleFrame >= 0) {
                    renderer.drawFrameIndex(sparkleFrame, ar.x, ar.y);
                }
            } else {
                renderer.drawFrameIndex(attractSpinFrame, ar.x, ar.y);
            }
        }
    }

    public void drawLostRings(int frameCounter) {
        // Per-ring Obj37 rendering now belongs to LostRingObjectInstance, the
        // same owner that advances per-ring physics. The legacy pool still owns
        // allocation/spawn bookkeeping during the cutover, but drawing it here
        // would render stale spawn-point positions.
    }

    /**
     * Draw a ring sprite at a specific position (for prize rings, etc.).
     * Uses the animated spin frame based on the frame counter.
     *
     * @param x            Screen X position (center of ring)
     * @param y            Screen Y position (center of ring)
     * @param frameCounter Current frame counter for animation
     */
    public void drawRingAt(int x, int y, int frameCounter) {
        if (renderer == null) {
            return;
        }
        int spinFrameIndex = renderer.getSpinFrameIndex(frameCounter);
        renderer.drawFrameIndex(spinFrameIndex, x, y);
    }

    /**
     * Draw a ring sprite using an exact spin-frame index.
     * Used by spilled rings, whose display frame is driven by the shared
     * decelerating Ring_spill_anim_* state rather than a constant frame timer.
     */
    public void drawRingFrameAt(int x, int y, int spinFrameIndex) {
        if (renderer == null) {
            return;
        }
        int spinCount = renderer.getSpinFrameCount();
        if (spinCount <= 0) {
            return;
        }
        renderer.drawFrameIndex(Math.floorMod(spinFrameIndex, spinCount), x, y);
    }

    /**
     * Draw a sparkle animation at a specific position (for collected prize rings, etc.).
     * Calculates the sparkle frame based on elapsed frames since sparkle started.
     *
     * @param x                  Screen X position (center of sparkle)
     * @param y                  Screen Y position (center of sparkle)
     * @param sparkleFrameOffset Frame offset into sparkle animation (0 = first sparkle frame)
     */
    public void drawSparkleAt(int x, int y, int sparkleFrameOffset) {
        if (renderer == null || renderer.getSparkleFrameCount() <= 0) {
            return;
        }
        int frameIndex = renderer.getSparkleStartIndex() +
                (sparkleFrameOffset % renderer.getSparkleFrameCount());
        renderer.drawFrameIndex(frameIndex, x, y);
    }

    /**
     * Check if ring rendering is available.
     */
    public boolean canRenderRings() {
        return renderer != null;
    }

    public void spawnLostRings(AbstractPlayableSprite player, int ringCount, int frameCounter) {
        lostRings.spawnLostRings(player, ringCount, frameCounter,
                player.getCentreX(), player.getCentreY(), -1);
    }

    public void spawnLostRings(AbstractPlayableSprite player, int ringCount, int frameCounter, int x, int y) {
        lostRings.spawnLostRings(player, ringCount, frameCounter, x, y, -1);
    }

    public void spawnLostRings(AbstractPlayableSprite player, int ringCount, int frameCounter,
                               int x, int y, int preallocatedFirstSlot) {
        lostRings.spawnLostRings(player, ringCount, frameCounter, x, y, preallocatedFirstSlot);
    }

    /**
     * S3K delayed-hurt bridge: the engine materializes pending Obj37 rings from
     * the post-player frame phase, after the normal object loop has already run.
     * ROM S3K allocates the Obj_Bouncing_Ring owner during the player slot and
     * then reaches the new Obj37 slots later in the same ExecuteObjects pass
     * (docs/skdisasm/sonic3k.asm:21065-21088, 35490-35616), so apply that first
     * Obj37 movement step immediately when the delayed spawn is flushed.
     */
    public void spawnLostRingsWithInitialObjectStep(AbstractPlayableSprite player, int ringCount,
                                                    int frameCounter, int x, int y,
                                                    int preallocatedFirstSlot) {
        lostRings.spawnLostRings(player, ringCount, frameCounter, x, y, preallocatedFirstSlot,
                true);
    }

    /** Shared spilled-ring spin owner feeding the LostRingObjectInstance object path. */
    public SpillAnimationState getSpillAnimationState() {
        return lostRings.spillAnimation;
    }

    public List<LostRing> getActiveLostRings() {
        return lostRings.getActiveRingsSnapshot();
    }

    public boolean areAllCollected() {
        return placement.areAllCollected();
    }

    public boolean isRenderable(RingSpawn ring, int frameCounter) {
        if (ring == null) {
            return false;
        }
        int index = placement.getSpawnIndex(ring);
        if (index < 0) {
            return false;
        }
        if (!placement.isCollected(index)) {
            return true;
        }
        int sparkleStartFrame = placement.getSparkleStartFrame(index);
        if (sparkleStartFrame < 0 || renderer == null || renderer.getSparkleFrameCount() <= 0) {
            return false;
        }
        int elapsed = frameCounter - sparkleStartFrame;
        if (elapsed < 0) {
            return true;
        }
        int sparkleFrameOffset = elapsed / renderer.getSparkleFrameDelay();
        if (sparkleFrameOffset >= renderer.getSparkleFrameCount()) {
            if (isCollectedAndSparkleDone(index, frameCounter)) {
                placement.clearSparkle(index);
            }
            return false;
        }
        return true;
    }

    public boolean isCollected(RingSpawn ring) {
        if (ring == null) {
            return false;
        }
        int index = placement.getSpawnIndex(ring);
        return placement.isCollected(index);
    }

    /**
     * Checks whether a ring at the given position has been collected.
     * Used by Sonic1RingInstance to detect collection
     * without any frame counter dependency.
     */
    public boolean isRingCollected(int x, int y) {
        RingSpawn probe = new RingSpawn(x, y);
        int index = placement.getSpawnIndex(probe);
        return index >= 0 && placement.isCollected(index);
    }

    public int getSparkleStartFrame(RingSpawn ring) {
        if (ring == null) {
            return -1;
        }
        int index = placement.getSpawnIndex(ring);
        return placement.getSparkleStartFrame(index);
    }

    /**
     * ROM parity: checks whether a ring at the given position has been collected
     * AND its sparkle animation has finished (equivalent to ROM's DeleteObject
     * call at the end of Ring_Sparkle).
     * <p>
     * Used by Sonic1RingInstance to free SST slots at the correct time,
     * matching the ROM's slot lifecycle where a ring's slot is freed only after
     * the sparkle animation completes.
     *
     * @param x            ring X position
     * @param y            ring Y position
     * @param frameCounter current frame counter
     * @return true if the ring was collected and sparkle has finished
     */
    public boolean isCollectedAndSparkleDone(int x, int y, int frameCounter) {
        RingSpawn probe = new RingSpawn(x, y);
        int index = placement.getSpawnIndex(probe);
        return isCollectedAndSparkleDone(index, frameCounter);
    }

    public boolean isCollectedAndSparkleDone(RingSpawn ring, int frameCounter) {
        if (ring == null) {
            return false;
        }
        int index = placement.getSpawnIndex(ring);
        return isCollectedAndSparkleDone(index, frameCounter);
    }

    private boolean isCollectedAndSparkleDone(int index, int frameCounter) {
        if (index < 0 || !placement.isCollected(index)) {
            return false;
        }
        int sparkleStart = placement.getSparkleStartFrame(index);
        if (sparkleStart < 0) {
            // Sparkle already cleared (or no sparkle) — collection is done
            return true;
        }
        if (renderer == null || renderer.getSparkleFrameCount() <= 0) {
            return true;
        }
        int elapsed = frameCounter - sparkleStart;
        // ROM parity: Ani_Ring sparkle uses its own delay byte (5 in S1 = 6 VBlanks/frame
        // via AnimateSprite), distinct from SynchroAnimate's spin rate (8 VBlanks/frame).
        // After sparkleFrameCount frames × sparkleDelay VBlanks, the afRoutine command
        // fires but the ring still displays for one more frame (DisplaySprite runs).
        // Ring_Delete runs on the NEXT frame, calling DeleteObject to free the SST slot.
        // Total duration: sparkleFrameCount * sparkleDelay + 1.
        int sparkleDelay = renderer.getSparkleFrameDelay();
        int totalDuration = renderer.getSparkleFrameCount() * sparkleDelay + 1;
        return elapsed >= totalDuration;
    }

    public PatternSpriteRenderer.FrameBounds getSpinBounds() {
        if (spinBounds == null) {
            spinBounds = renderer != null ? renderer.getSpinBounds() : new PatternSpriteRenderer.FrameBounds(0, 0, 0, 0);
        }
        return spinBounds;
    }

    public PatternSpriteRenderer.FrameBounds getFrameBounds(int frameCounter) {
        if (renderer == null) {
            return new PatternSpriteRenderer.FrameBounds(0, 0, 0, 0);
        }
        return renderer.getFrameBounds(frameCounter);
    }

    public int getSparkleStartIndex() {
        return renderer != null ? renderer.getSparkleStartIndex() : 0;
    }

    public int getSparkleFrameCount() {
        return renderer != null ? renderer.getSparkleFrameCount() : 0;
    }

    public int getFrameDelay() {
        return renderer != null ? renderer.getFrameDelay() : 1;
    }

    public int getSparkleFrameDelay() {
        return renderer != null ? renderer.getSparkleFrameDelay() : 1;
    }

    public void drawFrameIndex(int frameIndex, int originX, int originY) {
        if (renderer != null) {
            renderer.drawFrameIndex(frameIndex, originX, originY);
        }
    }

    public boolean hasRenderer() {
        return renderer != null;
    }

    public Collection<RingSpawn> getActiveSpawns() {
        return placement.getActiveSpawns();
    }

    private boolean addAttractedRing(int sourceIndex, int x, int y) {
        for (AttractedRing ar : attractedRings) {
            if (!ar.active) {
                ObjectManager objectManager = levelManager != null ? levelManager.getObjectManager() : null;
                int objectSlotIndex = objectManager != null ? objectManager.allocateDynamicSlot() : -1;
                if (objectManager != null && objectSlotIndex < 0) {
                    return false;
                }
                ar.sourceIndex = sourceIndex;
                ar.x = x;
                ar.y = y;
                ar.xSub = 0;
                ar.ySub = 0;
                ar.xVel = 0;
                ar.yVel = 0;
                ar.objectSlotIndex = objectSlotIndex;
                ar.collected = false;
                ar.sparkleStartFrame = -1;
                ar.active = true;
                return true;
            }
        }
        return false;
    }

    /**
     * ROM: AttractedRing_Move (sonic3k.asm:35795).
     * Per-axis acceleration of $30 subpixels/frame². When the ring's velocity
     * opposes the direction to the player, acceleration is 4× stronger to
     * reverse quickly. Position updated via MoveSprite2 (velocity→subpixel).
     */
    private void updateAttractedRings(AbstractPlayableSprite player, int frameCounter) {
        int pcx = player.getCentreX();
        int pcy = player.getCentreY();
        for (AttractedRing ar : attractedRings) {
            if (!ar.active) continue;

            if (ar.collected) {
                if (attractedSparkleFinished(ar, frameCounter)) {
                    deactivateAttractedRing(ar);
                }
                continue;
            }

            // ROM give-ring timing: player processes the collision-response list
            // built by the ring's PREVIOUS-frame Add_SpriteToCollisionResponseList,
            // so the give uses the pre-move position. Test here (pre-move) to defer
            // one frame to match ROM (S3K MGZ rings f539).
            if (attractedRingOverlapsPlayerTouchBox(ar, player)) {
                player.addRings(1);
                audioManager.playSfx(GameSound.RING);
                ar.collected = true;
                ar.sparkleStartFrame = frameCounter;
                continue;
            }

            // --- X axis acceleration (AttractedRing_Move) ---
            int accelX = ATTRACT_ACCEL;
            if (pcx >= ar.x) {
                // Player is right of ring: accelerate right (+)
                if (ar.xVel < 0) {
                    // Moving wrong way: 4× to reverse
                    accelX *= 4;
                }
            } else {
                // Player is left of ring: accelerate left (-)
                accelX = -accelX;
                if (ar.xVel >= 0) {
                    accelX *= 4;
                }
            }
            ar.xVel = (short) (ar.xVel + accelX);

            // --- Y axis acceleration ---
            int accelY = ATTRACT_ACCEL;
            if (pcy >= ar.y) {
                if (ar.yVel < 0) {
                    accelY *= 4;
                }
            } else {
                accelY = -accelY;
                if (ar.yVel >= 0) {
                    accelY *= 4;
                }
            }
            ar.yVel = (short) (ar.yVel + accelY);

            // --- MoveSprite2: apply velocity to position (subpixel precision) ---
            int xLong = (ar.x << 16) | (ar.xSub & 0xFFFF);
            xLong += ar.xVel << 8;
            ar.x = xLong >> 16;
            ar.xSub = xLong & 0xFFFF;

            int yLong = (ar.y << 16) | (ar.ySub & 0xFFFF);
            yLong += ar.yVel << 8;
            ar.y = yLong >> 16;
            ar.ySub = yLong & 0xFFFF;
            // give-ring tested pre-move at top of loop (ROM list timing)
        }
    }

    private boolean attractedRingOverlapsPlayerTouchBox(AttractedRing ar, AbstractPlayableSprite player) {
        int playerLeft = player.getCentreX() - 8;
        int playerTopHalf = Math.max(0, player.getYRadius() - 3);
        int playerTop = player.getCentreY() - playerTopHalf;
        return ringOverlapsPlayer(
                playerLeft,
                playerTop,
                playerTopHalf * 2,
                0x10,
                ar.x,
                ar.y,
                ATTRACT_TOUCH_RADIUS,
                ATTRACT_TOUCH_RADIUS);
    }

    private boolean hasActiveAttractedRings() {
        for (AttractedRing ar : attractedRings) {
            if (ar.active) {
                return true;
            }
        }
        return false;
    }

    private int attractedSparkleFrame(AttractedRing ar, int frameCounter) {
        if (!ar.collected || renderer == null || renderer.getSparkleFrameCount() <= 0) {
            return -1;
        }
        int elapsed = Math.max(0, frameCounter - ar.sparkleStartFrame);
        int offset = elapsed / renderer.getSparkleFrameDelay();
        if (offset >= renderer.getSparkleFrameCount()) {
            return -1;
        }
        return renderer.getSparkleStartIndex() + offset;
    }

    private boolean attractedSparkleFinished(AttractedRing ar, int frameCounter) {
        if (!ar.collected) {
            return false;
        }
        if (renderer == null || renderer.getSparkleFrameCount() <= 0) {
            return true;
        }
        int elapsed = Math.max(0, frameCounter - ar.sparkleStartFrame);
        return elapsed / renderer.getSparkleFrameDelay() >= renderer.getSparkleFrameCount();
    }

    private void releaseAttractedRingSlots() {
        for (AttractedRing ar : attractedRings) {
            deactivateAttractedRing(ar);
        }
    }

    private void deactivateAttractedRing(AttractedRing ar) {
        if (ar == null) {
            return;
        }
        ObjectManager objectManager = levelManager != null ? levelManager.getObjectManager() : null;
        if (objectManager != null && ar.objectSlotIndex >= 0) {
            objectManager.releaseDynamicSlot(ar.objectSlotIndex);
        }
        ar.active = false;
        ar.sourceIndex = 0;
        ar.x = 0;
        ar.y = 0;
        ar.xSub = 0;
        ar.ySub = 0;
        ar.xVel = 0;
        ar.yVel = 0;
        ar.objectSlotIndex = -1;
        ar.collected = false;
        ar.sparkleStartFrame = -1;
    }

    // --- RewindSnapshottable<RingSnapshot> ---

    @Override
    public String key() {
        return "rings";
    }

    @Override
    public RingSnapshot capture() {
        // --- RingPlacement state ---
        long[] collectedWords = placement.collected.toLongArray();
        List<RingSnapshot.SparkleEntry> sparkleTimers = new ArrayList<>();
        for (int i = 0; i < placement.sparkleStartFrames.length; i++) {
            int startFrame = placement.sparkleStartFrames[i];
            if (startFrame != RingPlacement.NO_SPARKLE) {
                sparkleTimers.add(new RingSnapshot.SparkleEntry(i, startFrame));
            }
        }
        int cursorIndex = placement.cursorIndex;
        int lastCameraX = placement.lastCameraX;
        int[] activeSpawnIndices = placement.snapshotActiveSpawnIndices();

        // --- Shared spilled-ring spin owner ---
        // Per-ring lost-ring state is no longer snapshotted here: physics runs in the
        // object exec loop and each LostRingObjectInstance round-trips via the generic
        // field capture + LostRingRewindCodec (Task 4.1). Only the small GLOBAL spin
        // (Ring_spill_anim_counter/accum/frame) is captured, via SpillAnimationState.
        int[] spin = lostRings.spillAnimation.snapshot();

        // --- AttractedRing state ---
        List<RingSnapshot.AttractedRingEntry> atEntries = new ArrayList<>();
        for (int i = 0; i < MAX_ATTRACTED_RINGS; i++) {
            AttractedRing ar = attractedRings[i];
            if (!ar.active) {
                continue;
            }
            atEntries.add(new RingSnapshot.AttractedRingEntry(
                    true, ar.sourceIndex, ar.x, ar.y,
                    ar.xSub, ar.ySub, ar.xVel, ar.yVel, i,
                    ar.objectSlotIndex, ar.collected, ar.sparkleStartFrame));
        }

        return new RingSnapshot(
                collectedWords,
                sparkleTimers.toArray(RingSnapshot.SparkleEntry[]::new),
                cursorIndex,
                lastCameraX,
                activeSpawnIndices,
                0,                // lostRingActiveCount: per-ring pool retired (object loop owns rings)
                spin[0],          // spillAnimCounter
                spin[1],          // spillAnimAccum
                spin[2],          // spillAnimFrame
                0,                // lostRingFrameCounter: retired with per-ring physics
                new RingSnapshot.LostRingEntry[0],
                atEntries.toArray(RingSnapshot.AttractedRingEntry[]::new));
    }

    @Override
    public void restore(RingSnapshot snap) {
        // --- RingPlacement ---
        placement.collected.clear();
        placement.collected.or(snap.collected());
        Arrays.fill(placement.sparkleStartFrames, RingPlacement.NO_SPARKLE);
        RingSnapshot.SparkleEntry[] snapSparkles = snap.sparkleTimers();
        for (RingSnapshot.SparkleEntry entry : snapSparkles) {
            int ringIndex = entry.ringIndex();
            if (ringIndex >= 0 && ringIndex < placement.sparkleStartFrames.length) {
                placement.sparkleStartFrames[ringIndex] = entry.startFrame();
            }
        }
        placement.cursorIndex = snap.placementCursorIndex();
        placement.lastCameraX = snap.placementLastCameraX();
        placement.restoreActiveSpawnIndices(snap.activeSpawnIndices());

        // --- Shared spilled-ring spin owner ---
        // Only the GLOBAL spin is restored here; the spilled rings themselves are
        // dynamic objects recreated via LostRingRewindCodec (Task 4.1). The legacy
        // per-ring ringPool restore is retired with the per-ring physics loop.
        lostRings.spillAnimation.restore(new int[] {
                snap.spillAnimCounter(), snap.spillAnimAccum(), snap.spillAnimFrame() });

        // --- AttractedRings ---
        releaseAttractedRingSlots();
        RingSnapshot.AttractedRingEntry[] snapAt = snap.attractedRings();
        for (int i = 0; i < snapAt.length; i++) {
            RingSnapshot.AttractedRingEntry entry = snapAt[i];
            int slotIndex = entry.slotIndex();
            if (slotIndex < 0 || slotIndex >= MAX_ATTRACTED_RINGS) {
                continue;
            }
            AttractedRing ar = attractedRings[slotIndex];
            int objectSlotIndex = restoreAttractedRingObjectSlot(entry);
            ar.active = entry.active() && (entry.objectSlotIndex() < 0 || objectSlotIndex >= 0);
            ar.sourceIndex = entry.sourceIndex();
            ar.x = entry.x();
            ar.y = entry.y();
            ar.xSub = entry.xSub();
            ar.ySub = entry.ySub();
            ar.xVel = entry.xVel();
            ar.yVel = entry.yVel();
            ar.objectSlotIndex = objectSlotIndex;
            ar.collected = ar.active && entry.collected();
            ar.sparkleStartFrame = entry.sparkleStartFrame();
        }
    }

    private int restoreAttractedRingObjectSlot(RingSnapshot.AttractedRingEntry entry) {
        if (!entry.active() || entry.objectSlotIndex() < 0) {
            return entry.objectSlotIndex();
        }
        ObjectManager objectManager = levelManager != null ? levelManager.getObjectManager() : null;
        if (objectManager == null) {
            return entry.objectSlotIndex();
        }
        return objectManager.reserveDynamicSlot(entry.objectSlotIndex()) ? entry.objectSlotIndex() : -1;
    }

    private static final class AttractedRing {
        int sourceIndex;
        int x, y;
        int xSub, ySub;    // subpixel fraction (ROM: x_sub/y_sub, lower word of position long)
        int xVel, yVel;    // velocity in subpixels/frame (ROM: x_vel/y_vel, 16-bit signed)
        int objectSlotIndex = -1;
        boolean collected;
        int sparkleStartFrame = -1;
        boolean active;
    }

    private static final class RingPlacement extends AbstractPlacementManager<RingSpawn> {
        private static final int EXTRA_AHEAD = 0x140; // 320; native -> 0x280 window
        private static final int UNLOAD_BEHIND = 0x300;
        private static final int S3K_RAW_WINDOW_BEHIND = 0x08;
        private static final int S3K_RAW_WINDOW_AHEAD = 0x148;
        private static final int NO_SPARKLE = -1;

        private final boolean useRawCameraWindow;
        private final BitSet collected = new BitSet();
        private int[] activeIndices = new int[256];
        private int activeIndexCount;
        private final BitSet activeIndexMembership = new BitSet();
        private int[] sparkleStartFrames;
        private int cursorIndex = 0;
        private int lastCameraX = Integer.MIN_VALUE;

        private RingPlacement(List<RingSpawn> spawns, boolean useRawCameraWindow) {
            super(spawns, EXTRA_AHEAD, UNLOAD_BEHIND,
                    com.openggf.level.spawn.PlacementViewportWidth::current);
            this.useRawCameraWindow = useRawCameraWindow;
            this.sparkleStartFrames = new int[this.spawns.size()];
            Arrays.fill(this.sparkleStartFrames, NO_SPARKLE);
        }

        /** Replaces spawns and resets all collection/sparkle state. */
        private void replaceSpawnsAndReset(List<RingSpawn> newSpawns) {
            replaceSpawns(newSpawns);
            collected.clear();
            sparkleStartFrames = new int[this.spawns.size()];
            Arrays.fill(sparkleStartFrames, NO_SPARKLE);
            cursorIndex = 0;
            lastCameraX = Integer.MIN_VALUE;
        }

        private void reset(int cameraX) {
            clearActiveIndices();
            collected.clear();
            Arrays.fill(sparkleStartFrames, NO_SPARKLE);
            cursorIndex = 0;
            lastCameraX = cameraX;
            refreshWindow(cameraX);
        }

        private boolean isCollected(int index) {
            return index >= 0 && collected.get(index);
        }

        private void markCollected(int index) {
            if (index >= 0) {
                collected.set(index);
            }
        }

        private int getSparkleStartFrame(int index) {
            if (index < 0 || index >= sparkleStartFrames.length) {
                return NO_SPARKLE;
            }
            return sparkleStartFrames[index];
        }

        private void setSparkleStartFrame(int index, int startFrame) {
            if (index < 0 || index >= sparkleStartFrames.length) {
                return;
            }
            sparkleStartFrames[index] = startFrame;
        }

        private void clearSparkle(int index) {
            if (index < 0 || index >= sparkleStartFrames.length) {
                return;
            }
            sparkleStartFrames[index] = NO_SPARKLE;
        }

        private void update(int cameraX) {
            if (spawns.isEmpty()) {
                return;
            }
            if (lastCameraX == Integer.MIN_VALUE) {
                reset(cameraX);
                return;
            }

            int delta = cameraX - lastCameraX;
            if (delta < 0 || delta > (getLoadAhead() + getUnloadBehind())) {
                refreshWindow(cameraX);
            } else {
                spawnForward(cameraX);
                trimActive(cameraX);
            }

            lastCameraX = cameraX;
        }

        private void spawnForward(int cameraX) {
            int spawnLimit = ringWindowEnd(cameraX);
            while (cursorIndex < spawns.size() && spawns.get(cursorIndex).x() <= spawnLimit) {
                addActiveIndex(cursorIndex);
                cursorIndex++;
            }
        }

        private void trimActive(int cameraX) {
            int windowStart = ringWindowStart(cameraX);
            int windowEnd = ringWindowEnd(cameraX);
            // Order-preserving compaction: active-ring order feeds collection
            // and draw order, so removals must not reorder survivors.
            int write = 0;
            for (int read = 0; read < activeIndexCount; read++) {
                int index = activeIndices[read];
                RingSpawn spawn = spawns.get(index);
                if (spawn.x() < windowStart || spawn.x() > windowEnd) {
                    activeIndexMembership.clear(index);
                } else {
                    activeIndices[write++] = index;
                }
            }
            activeIndexCount = write;
        }

        private void refreshWindow(int cameraX) {
            int windowStart = ringWindowStart(cameraX);
            int windowEnd = ringWindowEnd(cameraX);
            int start = lowerBound(windowStart);
            int end = upperBound(windowEnd);
            cursorIndex = end;
            clearActiveIndices();
            for (int i = start; i < end; i++) {
                addActiveIndex(i);
            }
        }

        private int[] snapshotActiveSpawnIndices() {
            return Arrays.copyOf(activeIndices, activeIndexCount);
        }

        private void restoreActiveSpawnIndices(int[] activeSpawnIndices) {
            clearActiveIndices();
            if (activeSpawnIndices == null) {
                return;
            }
            for (int index : activeSpawnIndices) {
                if (index >= 0 && index < spawns.size()) {
                    addActiveIndex(index);
                }
            }
        }

        @Override
        public Collection<RingSpawn> getActiveSpawns() {
            if (activeIndexCount == 0) {
                return List.of();
            }
            List<RingSpawn> activeSpawns = new ArrayList<>(activeIndexCount);
            for (int i = 0; i < activeIndexCount; i++) {
                activeSpawns.add(spawns.get(activeIndices[i]));
            }
            return List.copyOf(activeSpawns);
        }

        private int activeIndexCount() {
            return activeIndexCount;
        }

        private int activeIndexAt(int position) {
            return activeIndices[position];
        }

        private RingSpawn getSpawn(int index) {
            return spawns.get(index);
        }

        private void addActiveIndex(int index) {
            if (activeIndexMembership.get(index)) {
                return;
            }
            activeIndexMembership.set(index);
            if (activeIndexCount == activeIndices.length) {
                activeIndices = Arrays.copyOf(activeIndices, activeIndices.length * 2);
            }
            activeIndices[activeIndexCount++] = index;
        }

        private void clearActiveIndices() {
            activeIndexCount = 0;
            activeIndexMembership.clear();
        }

        private int ringWindowStart(int cameraX) {
            if (!useRawCameraWindow) {
                return getWindowStart(cameraX);
            }
            return Math.max(0, cameraX - S3K_RAW_WINDOW_BEHIND);
        }

        private int ringWindowEnd(int cameraX) {
            if (!useRawCameraWindow) {
                return getWindowEnd(cameraX);
            }
            return cameraX + S3K_RAW_WINDOW_AHEAD;
        }

        private boolean areAllCollected() {
            return !spawns.isEmpty() && collected.cardinality() >= spawns.size();
        }
    }

    private static final class RingRenderer {
        private final RingSpriteSheet spriteSheet;
        private final PatternSpriteRenderer renderer;
        private PatternSpriteRenderer.FrameBounds spinBoundsCache;

        private RingRenderer(RingSpriteSheet spriteSheet) {
            this.spriteSheet = spriteSheet;
            this.renderer = new PatternSpriteRenderer(spriteSheet);
        }

        private void ensurePatternsCached(GraphicsManager graphicsManager, int basePatternIndex) {
            renderer.ensurePatternsCached(graphicsManager, basePatternIndex);
        }

        private int getSpinFrameCount() {
            int count = spriteSheet.getSpinFrameCount();
            return (count > 0) ? count : spriteSheet.getFrameCount();
        }

        private int getSpinFrameIndex(int frameCounter) {
            int frameCount = getSpinFrameCount();
            if (frameCount <= 0) {
                return 0;
            }
            int delay = Math.max(1, spriteSheet.getFrameDelay());
            return (frameCounter / delay) % frameCount;
        }

        private PatternSpriteRenderer.FrameBounds getFrameBounds(int frameCounter) {
            return renderer.getFrameBoundsForIndex(getSpinFrameIndex(frameCounter));
        }

        private PatternSpriteRenderer.FrameBounds getSpinBounds() {
            if (spinBoundsCache != null) {
                return spinBoundsCache;
            }
            int spinCount = spriteSheet.getSpinFrameCount();
            if (spinCount <= 0) {
                spinCount = spriteSheet.getFrameCount();
            }
            if (spinCount <= 0) {
                spinBoundsCache = new PatternSpriteRenderer.FrameBounds(0, 0, 0, 0);
                return spinBoundsCache;
            }
            boolean first = true;
            int minX = 0;
            int minY = 0;
            int maxX = 0;
            int maxY = 0;
            for (int i = 0; i < spinCount; i++) {
                PatternSpriteRenderer.FrameBounds bounds = renderer.getFrameBoundsForIndex(i);
                if (bounds.width() <= 0 || bounds.height() <= 0) {
                    continue;
                }
                if (first) {
                    minX = bounds.minX();
                    minY = bounds.minY();
                    maxX = bounds.maxX();
                    maxY = bounds.maxY();
                    first = false;
                } else {
                    minX = Math.min(minX, bounds.minX());
                    minY = Math.min(minY, bounds.minY());
                    maxX = Math.max(maxX, bounds.maxX());
                    maxY = Math.max(maxY, bounds.maxY());
                }
            }
            spinBoundsCache = first ? new PatternSpriteRenderer.FrameBounds(0, 0, 0, 0)
                    : new PatternSpriteRenderer.FrameBounds(minX, minY, maxX, maxY);
            return spinBoundsCache;
        }

        private void drawFrameIndex(int frameIndex, int originX, int originY) {
            renderer.drawFrameIndex(frameIndex, originX, originY);
        }

        private int getSparkleStartIndex() {
            return spriteSheet.getSparkleStartIndex();
        }

        private int getSparkleFrameCount() {
            return spriteSheet.getSparkleFrameCount();
        }

        private int getFrameDelay() {
            return Math.max(1, spriteSheet.getFrameDelay());
        }

        private int getSparkleFrameDelay() {
            return Math.max(1, spriteSheet.getSparkleFrameDelay());
        }
    }

    private static final class LostRingPool {
        private static final int MAX_LOST_RINGS = 0x20;
        private static final int GRAVITY = 0x18;
        private static final int LIFETIME_FRAMES = 0xFF;
        private static final int RING_TOUCH_SIZE_INDEX = 0x07;
        private static final int SOLIDITY_TOP = 0x0C;
        // ROM: Obj37_Init sets y_radius(a1) = 8. RingCheckFloorDist adds y_radius
        // to y_pos before probing, so it checks from the ring's bottom edge.
        private static final int RING_Y_RADIUS = 8;

        private final LevelManager levelManager;
        private final RingRenderer renderer;
        private final TouchResponseTable touchResponseTable;
        private final AudioManager audioManager;
        private final LostRing[] ringPool = new LostRing[MAX_LOST_RINGS];
        private int activeRingCount = 0;
        // Shared spilled-ring spin owner (ROM Ring_spill_anim_counter/accum/frame,
        // s2.asm Obj37 ChangeRingFrame). The counter doubles as both lifetime and
        // animation-speed input: the accumulator increases by counter each frame,
        // producing a decelerating spin. This is the SOLE owner of the spin now —
        // per-ring physics runs in the object exec loop (LostRingObjectInstance),
        // and this owner feeds every live ring's displayed mapping frame.
        private final SpillAnimationState spillAnimation = new SpillAnimationState();

        private LostRingPool(LevelManager levelManager, RingRenderer renderer, TouchResponseTable touchResponseTable,
                             AudioManager audioManager) {
            this.levelManager = levelManager;
            this.renderer = renderer;
            this.touchResponseTable = touchResponseTable;
            this.audioManager = audioManager;
            for (int i = 0; i < MAX_LOST_RINGS; i++) {
                ringPool[i] = new LostRing();
            }
        }

        private void reset() {
            // Obj37 slots are owned by LostRingObjectInstance now. S1 RingLoss
            // creates new spilled rings with FindFreeObj and resets the shared
            // v_ani3_time, but it does not sweep existing Obj37 slots first
            // (docs/s1disasm/_incObj/25 & 37 Rings.asm:199-219,284-313).
            // Releasing the legacy LostRing slot here can mark a still-live or
            // later-reused SST slot free, corrupting the allocator before the
            // next ObjPosLoad.
            for (LostRing ring : ringPool) {
                ring.deactivate();
                ring.setSlotIndex(-1);
            }
            activeRingCount = 0;
        }

        private void spawnLostRings(AbstractPlayableSprite player, int ringCount, int frameCounter,
                                    int x, int y, int preallocatedFirstSlot) {
            spawnLostRings(player, ringCount, frameCounter, x, y, preallocatedFirstSlot, false);
        }

        private void spawnLostRings(AbstractPlayableSprite player, int ringCount, int frameCounter,
                                    int x, int y, int preallocatedFirstSlot,
                                    boolean applyInitialObjectStep) {
            if (player == null || renderer == null) {
                return;
            }
            if (ringCount <= 0) {
                return;
            }
            // ROM Obj37_Init (s2.asm:25127-25130): cap spilled rings at $20 (32).
            int toSpawn = Math.min(ringCount, MAX_LOST_RINGS);
            int angle = 0x288;
            int xVel = 0;
            int yVel = 0;
            reset();
            // ROM: Ring_spill_anim_counter = $FF, accumulator reset (s2.asm Obj37_Init).
            // Reset the shared spin owner that feeds every live ring's render frame.
            spillAnimation.reset();
            ObjectManager objectManager = levelManager != null ? levelManager.getObjectManager() : null;

            // Atomic stop-on-(-1) slot-allocation contract (ROM Obj37_Init s2.asm:25137-25138:
            // `bsr.w AllocateObject; bne.w +++` — a failed AllocateObject branches PAST the
            // spill loop, truncating the spill). S1/S2 allocate every Obj37 from the loop. S3K
            // HurtCharacter first allocates the Obj37 owner slot, then Obj37_Init uses that slot
            // for ring 0 and AllocateObjectAfterCurrent for the rest (sonic3k.asm:21065-21088,
            // 35490-35528).
            boolean preallocateOwnerSlot = objectManager != null && objectManager.preallocatesLostRingOwnerSlot();
            int firstReservedSlot = preallocatedFirstSlot;
            if (preallocateOwnerSlot && firstReservedSlot < 0) {
                firstReservedSlot = objectManager.allocateDynamicSlot();
            }
            int previousSlot = preallocateOwnerSlot ? firstReservedSlot : 31;
            int spawned = 0;
            for (int i = 0; i < toSpawn; i++) {
                if (angle >= 0) {
                    int sin = calcSine(angle & 0xFF);
                    int cos = calcCosine(angle & 0xFF);
                    int scale = (angle >> 8) & 0xFF;
                    xVel = sin << scale;
                    yVel = cos << scale;
                    // ROM: addi.b #$10,d4 — byte-only add, high byte (scale) unchanged.
                    // Only the low byte increments; carry from the byte add triggers
                    // the $80 subtraction which eventually drops the scale 2→1→0.
                    int lowByte = (angle & 0xFF) + 0x10;
                    boolean carry = lowByte > 0xFF;
                    angle = (angle & 0xFF00) | (lowByte & 0xFF);
                    if (carry) {
                        angle -= 0x80;
                        if (angle < 0) {
                            angle = 0x288;
                        }
                    }
                }

                int slotIndex = i == 0 && preallocateOwnerSlot
                        ? firstReservedSlot
                        : objectManager != null
                                ? objectManager.allocateSlotAfter(previousSlot)
                                : -1;
                if (slotIndex < 0) {
                    // ROM: no free slot → stop spilling (truncate the remainder).
                    int truncated = toSpawn - spawned;
                    LOG.log(System.Logger.Level.DEBUG, () -> "spawnLostRings: dynamic slot pool "
                            + "exhausted; " + truncated + " of " + toSpawn + " rings truncated");
                    break;
                }

                int phase = phaseOffsetForSlot(objectManager, slotIndex);
                LostRing ring = ringPool[activeRingCount];
                ring.reset(phase, x, y,
                        xVel, yVel, LIFETIME_FRAMES);
                ring.setSlotIndex(slotIndex);
                // Parallel object path: register a LostRingObjectInstance twin onto the
                // SAME reserved slot (no second allocation). The legacy LostRing remains
                // the OWNER of collection/rewind during this stage; the object is exec-only.
                if (objectManager != null) {
                    LostRingObjectInstance ringObject = LostRingObjectInstance.spawn(
                            x, y, xVel, yVel,
                            phase, LIFETIME_FRAMES, spillAnimation);
                    objectManager.spawnLostRingObjectAtSlot(ringObject, slotIndex);
                    if (applyInitialObjectStep) {
                        ringObject.updateMovement();
                    }
                }
                activeRingCount++;
                previousSlot = slotIndex;
                spawned++;
                xVel = -xVel;
                angle = -angle;
            }

            player.setRingCount(0);
            audioManager.playSfx(GameSound.RING_SPILL);
        }

        private static int phaseOffsetForSlot(ObjectManager objectManager, int slotIndex) {
            // Obj37 floor probes use the ring's dynamic-object execution countdown.
            // In the engine this countdown is tied to the managed dynamic slot window;
            // using the broader S3K process table shifts the spill cadence and makes
            // rings bounce hundreds of pixels before the ROM trace.
            int lastSlotExclusive = objectManager != null
                    ? objectManager.getLastDynamicSlotExclusive()
                    : 128;
            return lastSlotExclusive - 1 - slotIndex;
        }

        /**
         * Advance the shared decelerating spin one frame (ROM ChangeRingFrame,
         * s2.asm Obj37: accumulator += counter; frame = bits 10:9; counter--).
         * <p>
         * The per-ring physics loop (velocity integrate, gravity, per-game floor/
         * ceiling probe, lifetime/off-bottom deletion) has been retired from the
         * legacy pool — it now runs in the object exec loop via
         * {@link LostRingObjectInstance#updateMovement}. This call only ticks the
         * global spin owner shared by every live spilled ring.
         */
        private void tickSpillAnimation() {
            spillAnimation.tick();
        }

        private void deactivateRing(LostRing ring, ObjectManager objectManager) {
            if (ring == null || !ring.isActive()) {
                return;
            }
            releaseReservedSlot(ring, objectManager);
            ring.deactivate();
        }

        private void releaseReservedSlots() {
            ObjectManager objectManager = levelManager != null ? levelManager.getObjectManager() : null;
            for (LostRing ring : ringPool) {
                releaseReservedSlot(ring, objectManager);
                ring.deactivate();
            }
        }

        private void releaseReservedSlot(LostRing ring, ObjectManager objectManager) {
            if (ring == null || ring.getSlotIndex() < 0 || objectManager == null) {
                return;
            }
            objectManager.releaseDynamicSlot(ring.getSlotIndex());
            ring.setSlotIndex(-1);
        }

        private List<LostRing> getActiveRingsSnapshot() {
            List<LostRing> active = new ArrayList<>();
            for (int i = 0; i < activeRingCount; i++) {
                LostRing ring = ringPool[i];
                if (ring.isActive()) {
                    active.add(ring);
                }
            }
            return List.copyOf(active);
        }

        private void draw(int frameCounter) {
            if (renderer == null || activeRingCount == 0) {
                return;
            }

            // ROM: scattered rings use shared spillAnimFrame (0-3) driven by the
            // decelerating accumulator, NOT the constant-speed placed-ring animation.
            // Clamp to available spin frames in case sprite sheet differs.
            int spinCount = renderer.getSpinFrameCount();
            int spinFrameIndex = (spinCount > 0) ? (spillAnimation.frame() % spinCount) : 0;

            for (int i = 0; i < activeRingCount; i++) {
                LostRing ring = ringPool[i];
                if (!ring.isActive()) {
                    continue;
                }

                if (!ring.isCollected()) {
                    // ROM: Obj37_Main always calls DisplaySprite — no blink effect.
                    // Rings display every frame until the counter hits 0, then delete.
                    renderer.drawFrameIndex(spinFrameIndex, ring.getX(), ring.getY());
                    continue;
                }

                int sparkleStartFrame = ring.getSparkleStartFrame();
                if (sparkleStartFrame < 0 || renderer.getSparkleFrameCount() <= 0) {
                    continue;
                }

                int elapsed = frameCounter - sparkleStartFrame;
                if (elapsed < 0) {
                    elapsed = 0;
                }
                int sparkleFrameOffset = elapsed / renderer.getSparkleFrameDelay();
                if (sparkleFrameOffset >= renderer.getSparkleFrameCount()) {
                    continue;
                }
                int sparkleFrameIndex = renderer.getSparkleStartIndex() + sparkleFrameOffset;
                renderer.drawFrameIndex(sparkleFrameIndex, ring.getX(), ring.getY());
            }
        }

        private boolean collectedSparkleFinished(LostRing ring, int frameCounter) {
            if (ring == null || !ring.isCollected()) {
                return false;
            }
            if (renderer == null || renderer.getSparkleFrameCount() <= 0) {
                return true;
            }
            int sparkleStartFrame = ring.getSparkleStartFrame();
            if (sparkleStartFrame < 0) {
                return true;
            }
            int elapsed = Math.max(0, frameCounter - sparkleStartFrame);
            int sparkleFrameOffset = elapsed / renderer.getSparkleFrameDelay();
            return sparkleFrameOffset >= renderer.getSparkleFrameCount();
        }

        private int ringCheckFloorDist(int x, int y) {
            if (levelManager == null) {
                return 0;
            }
            ChunkDesc chunkDesc = levelManager.getChunkDescAt((byte) 0, x, y);
            SolidTile tile = getSolidTile(chunkDesc, SOLIDITY_TOP);
            SensorMetric metric = getMetric(tile, chunkDesc, x, y);
            if (metric.metric == 0) {
                return 0;
            }
            if (metric.metric == 16) {
                // ROM: sub.w a3,d2 with a3=$10 → check tile above
                int prevY = y - 16;
                ChunkDesc prevDesc = levelManager.getChunkDescAt((byte) 0, x, prevY);
                SolidTile prevTile = getSolidTile(prevDesc, SOLIDITY_TOP);
                SensorMetric prevMetric = getMetric(prevTile, prevDesc, x, prevY);
                if (prevMetric.metric > 0 && prevMetric.metric < 16) {
                    return calculateDistance(prevMetric.metric, x, y, prevY);
                }
                return calculateDistance(metric.metric, x, y, y);
            }
            return calculateDistance(metric.metric, x, y, y);
        }

        /**
         * Ceiling distance check for S3K reverse gravity rings.
         * ROM: RingCheckFloorDist_ReverseGravity — same as floor check but probes
         * upward (stride -$10 → check tile below when fully solid).
         */
        private int ringCheckCeilingDist(int x, int y) {
            if (levelManager == null) {
                return 0;
            }
            ChunkDesc chunkDesc = levelManager.getChunkDescAt((byte) 0, x, y);
            SolidTile tile = getSolidTile(chunkDesc, SOLIDITY_TOP);
            SensorMetric metric = getMetric(tile, chunkDesc, x, y);
            if (metric.metric == 0) {
                return 0;
            }
            if (metric.metric == 16) {
                // ROM: sub.w a3,d2 with a3=-$10 → check tile below
                int nextY = y + 16;
                ChunkDesc nextDesc = levelManager.getChunkDescAt((byte) 0, x, nextY);
                SolidTile nextTile = getSolidTile(nextDesc, SOLIDITY_TOP);
                SensorMetric nextMetric = getMetric(nextTile, nextDesc, x, nextY);
                if (nextMetric.metric > 0 && nextMetric.metric < 16) {
                    return calculateDistance(nextMetric.metric, x, y, nextY);
                }
                return calculateDistance(metric.metric, x, y, y);
            }
            return calculateDistance(metric.metric, x, y, y);
        }

        private SolidTile getSolidTile(ChunkDesc chunkDesc, int solidityBitIndex) {
            if (chunkDesc == null || !chunkDesc.isSolidityBitSet(solidityBitIndex)) {
                return null;
            }
            return levelManager.getSolidTileForChunkDesc(chunkDesc, solidityBitIndex);
        }

        private SensorMetric getMetric(SolidTile tile, ChunkDesc desc, int x, int y) {
            if (tile == null) {
                return new SensorMetric((byte) 0);
            }
            int index = x & 0x0F;
            if (desc != null && desc.getHFlip()) {
                index = 15 - index;
            }
            byte metric = tile.getHeightAt((byte) index);
            if (metric != 0 && metric != 16) {
                boolean invert = (desc != null && desc.getVFlip());
                if (invert) {
                    metric = (byte) (16 - metric);
                }
            }
            return new SensorMetric(metric);
        }

        private int calculateDistance(byte metric, int x, int y, int checkY) {
            int tileY = checkY & ~0x0F;
            return (tileY + 16 - metric) - y;
        }

        private int calcSine(int angle) {
            return TrigLookupTable.sinHex(angle);
        }

        private int calcCosine(int angle) {
            return TrigLookupTable.cosHex(angle);
        }

        private record SensorMetric(byte metric) {
        }
    }
}
