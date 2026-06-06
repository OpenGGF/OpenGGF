package com.openggf.level.objects;


import com.openggf.audio.GameSound;
import com.openggf.camera.Camera;
import com.openggf.game.CollisionModel;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.GameStateManager;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.solid.PostContactState;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.level.spawn.AbstractPlacementManager;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.game.PlayableEntity;
import com.openggf.game.DamageCause;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.game.GroundMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

final class ObjectTouchResponseController {
    private static final Logger LOGGER = Logger.getLogger(ObjectTouchResponseController.class.getName());
    private final ObjectManager objectManager;
    private final TouchResponseTable table;
    // Double-buffer pattern: swap buffers instead of allocating new sets each frame
    private final Set<ObjectInstance> bufferA = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ObjectInstance> bufferB = Collections.newSetFromMap(new IdentityHashMap<>());
    private Set<ObjectInstance> overlapping = bufferA;
    private Set<ObjectInstance> building = bufferB;
    // Per-sidekick overlap tracking (each sidekick needs independent edge detection)
    private static class OverlapBufferPair {
        final Set<ObjectInstance> bufferA = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<ObjectInstance> bufferB = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<ObjectInstance> overlapping = bufferA;
        Set<ObjectInstance> building = bufferB;

        void swap() {
            Set<ObjectInstance> temp = overlapping;
            overlapping = building;
            building = temp;
        }

        void reset() {
            bufferA.clear();
            bufferB.clear();
            overlapping = bufferA;
            building = bufferB;
        }
    }

    private final Map<PlayableEntity, OverlapBufferPair> sidekickOverlaps = new IdentityHashMap<>();
    private final Map<PlayableEntity, Integer> lastSpecialTouchFrame = new IdentityHashMap<>();
    private final TouchResponseDebugState debugState = new TouchResponseDebugState();
    private static final int SHIELD_TOUCH_HALF_SIZE = 0x18;
    private static final int SHIELD_TOUCH_SIZE = SHIELD_TOUCH_HALF_SIZE * 2;
    private static final int SHIELD_REACTION_BOUNCE_BIT = 1 << 3;
    /**
     * ROM Touch_ChkValue lost-ring re-collection gate (s2.asm:85196-85219): a spilled ring
     * is only collected when {@code invulnerable_time < 90}.
     */
    private static final int LOST_RING_INVULNERABLE_THRESHOLD = 90;
    private int currentFrameCounter;
    private boolean instaShieldActive;
    private PlayableEntity currentPlayer;

    ObjectTouchResponseController(ObjectManager objectManager, TouchResponseTable table) {
        this.objectManager = objectManager;
        this.table = table;
    }

    void reset() {
        bufferA.clear();
        bufferB.clear();
        overlapping = bufferA;
        building = bufferB;
        sidekickOverlaps.values().forEach(OverlapBufferPair::reset);
        lastSpecialTouchFrame.clear();
        currentFrameCounter = 0;
    }

    boolean hadSpecialTouchThisFrame(PlayableEntity player) {
        return player != null
                && lastSpecialTouchFrame.getOrDefault(player, Integer.MIN_VALUE) == currentFrameCounter;
    }

    /**
     * Captures the double-buffer overlap state for rewind. Encodes set
     * content as slot indices (object Java refs change identity on
     * restore) and the buffer-swap parity as a boolean. See
     * {@link com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.TouchResponseOverlapState}
     * for the cross-frame-state rationale.
     */
    com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.TouchResponseOverlapState
            captureRewindState() {
        int[] mainOver = collectSlotIndices(overlapping);
        int[] mainBuild = collectSlotIndices(building);
        boolean mainSwapped = (overlapping == bufferB);
        List<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.SidekickOverlapEntry>
                sidekickEntries = new ArrayList<>(sidekickOverlaps.size());
        for (var entry : sidekickOverlaps.entrySet()) {
            PlayableEntity sk = entry.getKey();
            OverlapBufferPair pair = entry.getValue();
            String code = sk instanceof com.openggf.sprites.Sprite s ? s.getCode() : null;
            if (code == null) continue;
            sidekickEntries.add(
                    new com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.SidekickOverlapEntry(
                            code,
                            collectSlotIndices(pair.overlapping),
                            collectSlotIndices(pair.building),
                            pair.overlapping == pair.bufferB));
        }
        return new com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.TouchResponseOverlapState(
                mainOver, mainBuild, mainSwapped, sidekickEntries);
    }

    /**
     * Restores the double-buffer overlap state. Must run AFTER
     * {@code ObjectManager}'s slot/dynamic-object restore so slot lookup
     * resolves to the post-restore live instances.
     */
    void restoreRewindState(
            com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.TouchResponseOverlapState state) {
        bufferA.clear();
        bufferB.clear();
        overlapping = bufferA;
        building = bufferB;
        sidekickOverlaps.clear();
        if (state == null) return;
        populateBuffer(bufferA,
                state.mainParitySwapped() ? state.mainBuildingSlotIndices() : state.mainOverlappingSlotIndices());
        populateBuffer(bufferB,
                state.mainParitySwapped() ? state.mainOverlappingSlotIndices() : state.mainBuildingSlotIndices());
        if (state.mainParitySwapped()) {
            overlapping = bufferB;
            building = bufferA;
        }
        // Resolve sidekick PlayableEntity refs from the injected live SpriteManager.
        com.openggf.sprites.managers.SpriteManager sm = objectManager.services().spriteManager();
        for (var skEntry : state.sidekickEntries()) {
            com.openggf.sprites.Sprite sprite = sm.getSprite(skEntry.sidekickCode());
            if (!(sprite instanceof PlayableEntity pe)) continue;
            OverlapBufferPair pair = new OverlapBufferPair();
            populateBuffer(pair.bufferA,
                    skEntry.paritySwapped() ? skEntry.buildingSlotIndices() : skEntry.overlappingSlotIndices());
            populateBuffer(pair.bufferB,
                    skEntry.paritySwapped() ? skEntry.overlappingSlotIndices() : skEntry.buildingSlotIndices());
            if (skEntry.paritySwapped()) {
                pair.overlapping = pair.bufferB;
                pair.building = pair.bufferA;
            }
            sidekickOverlaps.put(pe, pair);
        }
    }

    private static int[] collectSlotIndices(Set<ObjectInstance> set) {
        int[] result = new int[set.size()];
        int n = 0;
        for (ObjectInstance inst : set) {
            if (inst instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() >= 0) {
                result[n++] = aoi.getSlotIndex();
            }
        }
        return n == result.length ? result : Arrays.copyOf(result, n);
    }

    private void populateBuffer(Set<ObjectInstance> buffer, int[] slotIndices) {
        if (slotIndices == null || slotIndices.length == 0) return;
        // Build a slot -> instance lookup once, since each Set may have
        // multiple slots to resolve.
        Map<Integer, ObjectInstance> bySlot = new java.util.HashMap<>();
        for (ObjectInstance inst : objectManager.getActiveObjects()) {
            if (inst instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() >= 0) {
                bySlot.put(aoi.getSlotIndex(), inst);
            }
        }
        for (int slot : slotIndices) {
            ObjectInstance inst = bySlot.get(slot);
            if (inst != null) {
                buffer.add(inst);
            }
        }
    }

    void update(PlayableEntity player, int frameCounter) {
        update(player, frameCounter, true);
    }

    void update(PlayableEntity player, int frameCounter, boolean usePreUpdateState) {
        currentFrameCounter = frameCounter;
        if (player == null || objectManager == null || player.getDead() || table == null) {
            overlapping.clear();
            debugState.clear();
            return;
        }

        if (player.isDebugMode()) {
            overlapping.clear();
            debugState.clear();
            return;
        }

        // ROM Sonic_Display (sonic3k.asm:22019-22021) and S2/S1 equivalents
        // skip TouchResponse when object_control's bit 7 (or $A0 in S3K) is
        // set — i.e. flight/CATCH_UP_FLIGHT/FLIGHT_AUTO_RECOVERY/super/debug
        // states where the controlling object owns the sprite. Without this
        // gate, balloons and other touch objects fire false positives
        // against a sprite that ROM never collides during these states.
        // See PlayableEntity#isTouchResponseSuppressedByObjectControl() for
        // the cross-game ROM citations.
        if (player.isTouchResponseSuppressedByObjectControl()) {
            overlapping.clear();
            debugState.clear();
            return;
        }

        int playerX = player.getCentreX() - 8;
        int baseYRadius = Math.max(1, player.getYRadius() - 3);
        // ROM: playerY = y_pos - (y_radius - 3). Do NOT subtract 8 from Y (only X).
        int playerY = player.getCentreY() - baseYRadius;
        int playerHeight = baseYRadius * 2;
        boolean crouching = player.getCrouching();
        if (crouching) {
            playerY += 12;
            playerHeight = 20;
        }
        // ROM (sonic3k.asm:20620-20640): Insta-shield expands hitbox to 48x48
        instaShieldActive = false;
        currentPlayer = player;
        int playerWidth = 0x10; // Normal width
        PhysicsFeatureSet fs = player.getPhysicsFeatureSet();
        if (fs != null && fs.instaShieldEnabled()
                && player.getDoubleJumpFlag() == 1
                && player.getShieldType() == null
                && player.getInvincibleFrames() == 0) {
            instaShieldActive = true;
            playerX = player.getCentreX() - 0x18;
            playerY = player.getCentreY() - 0x18;
            playerHeight = 0x30;
            playerWidth = 0x30;
        }
        debugState.setPlayer(playerX, playerY, playerHeight, baseYRadius, crouching);
        debugState.clear();

        // Double-buffer pattern:
        // - 'overlapping' contains last frame's overlapping objects
        // - 'building' will be populated with this frame's overlapping objects
        // Clear building to prepare for this frame's data
        building.clear();

        processCollisionLoop(player, playerX, playerY, playerHeight, playerWidth,
                building, overlapping, false, usePreUpdateState);

        // Swap buffers: building becomes overlapping for next frame
        Set<ObjectInstance> temp = overlapping;
        overlapping = building;
        building = temp;
        instaShieldActive = false;
        currentPlayer = null;
    }

    /**
     * Touch response check for the CPU sidekick (Tails).
     * Uses separate overlap tracking from the main player.
     * ROM: In 1P mode, CPU Tails interacts with objects but doesn't scatter
     * rings when hurt and can never die from enemy contact.
     */
    void updateSidekick(PlayableEntity sidekick, int frameCounter) {
        updateSidekick(sidekick, frameCounter, true);
    }

    void updateSidekick(PlayableEntity sidekick, int frameCounter, boolean usePreUpdateState) {
        currentPlayer = null; // Sidekick doesn't get insta-shield
        currentFrameCounter = frameCounter;
        OverlapBufferPair buffers = sidekickOverlaps.computeIfAbsent(sidekick, k -> new OverlapBufferPair());
        if (sidekick == null || objectManager == null || sidekick.getDead() || table == null) {
            buffers.overlapping.clear();
            return;
        }

        if (sidekick.isDebugMode()) {
            buffers.overlapping.clear();
            return;
        }

        // ROM Tails_Display (sonic3k.asm:26263-26266) and S2/S1 equivalents
        // skip TouchResponse when object_control's bit 7 (or $A0 in S3K) is
        // set. For S3K this is critical for Tails_CPU_routine 2/4
        // (Tails_Catch_Up_Flying / Tails_FlySwim_Unknown) which ROM enters
        // with object_control=$81 (sonic3k.asm:26511, 26542) — both
        // routines run from Tails_CPU_Control, NOT from Tails_Display, so
        // ROM never reaches the TouchResponse call in those states. Engine
        // must mirror the skip to avoid balloon/spike/etc. false-positive
        // collisions during catch-up flight.
        if (sidekick.isTouchResponseSuppressedByObjectControl()) {
            buffers.overlapping.clear();
            return;
        }

        int playerX = sidekick.getCentreX() - 8;
        int baseYRadius = Math.max(1, sidekick.getYRadius() - 3);
        int playerY = sidekick.getCentreY() - baseYRadius;
        int playerHeight = baseYRadius * 2;
        boolean crouching = sidekick.getCrouching();
        if (crouching) {
            playerY += 12;
            playerHeight = 20;
        }

        buffers.building.clear();

        processCollisionLoop(sidekick, playerX, playerY, playerHeight, 0x10,
                buffers.building, buffers.overlapping, true, usePreUpdateState);

        buffers.swap();
    }

    /**
     * Shared collision loop for both main player and sidekick touch responses.
     * Iterates active objects, checks touch regions and overlap, dispatches to the
     * appropriate response handler. Behavioral differences are parameterized:
     * - Main player: records debug hits
     * - Sidekick: no debug recording and uses Hurt_Sidekick handling
     * - Both players exit after the first overlapping object; the ROM's handler
     *   returns from ReactToItem after processing one touch response.
     *
     * @param player         the playable entity to check collisions for
     * @param playerX        hitbox left edge (centreX - 8, or insta-shield adjusted)
     * @param playerY        hitbox top edge (centreY - yRadius adjusted)
     * @param playerHeight   hitbox height
     * @param playerWidth    hitbox width (0x10 normal, 0x30 insta-shield)
     * @param buildingSet    the set being populated with this frame's overlapping objects
     * @param overlappingSet last frame's overlapping objects (for edge-trigger detection)
     * @param isSidekick     true for sidekick (no break-on-hit, sidekick response handler)
     */
    private void processCollisionLoop(PlayableEntity player,
            int playerX, int playerY, int playerHeight, int playerWidth,
            Set<ObjectInstance> buildingSet, Set<ObjectInstance> overlappingSet,
            boolean isSidekick, boolean usePreUpdateState) {
        Collection<ObjectInstance> touchObjects = objectManager.getTouchResponseObjects();

        for (ObjectInstance instance : touchObjects) {
            TouchResponseProvider provider = (TouchResponseProvider) instance;
            if (instance.isSkipTouchThisFrame()) {
                continue;
            }

            // Multi-region providers (e.g., spiked pole helix) check each region independently
            TouchResponseProvider.TouchRegion[] regions = provider.getMultiTouchRegions();
            TouchResponseProfile touchProfile = regions != null
                    ? provider.getTouchResponseProfile(true)
                    : provider.getTouchResponseProfile();
            if (regions != null) {
                boolean hit = processMultiRegionTouch(player, playerX, playerY, playerHeight,
                        instance, provider, touchProfile, regions, playerWidth,
                        buildingSet, overlappingSet, isSidekick);
                if (hit) {
                    break;
                }
                continue;
            }

            // ROM parity (S1-specific provenance):
            // S1's ReactToItem (docs/s1disasm/_incObj/sub ReactToItem.asm:26-27)
            // gates each iteration on `tst.b obRender(a1) / bpl.s .next`. If
            // obRender bit 7 is clear (object not yet displayed by
            // DisplaySprite), the entire object is skipped. This covers:
            //   (a) First-frame objects whose DisplaySprite hasn't run yet
            //   (b) Objects that were offscreen on the previous frame
            //   (c) Objects created by higher-slot makers that haven't run yet
            //
            // Note: this gate is NOT universal across games. S2's TouchResponse
            // (docs/s2disasm/s2.asm Touch_Loop ~line 84537) iterates objects
            // and only checks `collision_flags(a1)` -- there is no render-flag
            // gate, so an off-screen object with a non-zero collision_flags is
            // still considered for touch. S3K does not iterate at all: it
            // pre-builds Collision_response_list during ExecuteObjects and
            // walks only objects that opted in, so the equivalent of the bit-7
            // check happens at list-add time, not at touch time.
            //
            // The engine's TouchResponseProvider.requiresRenderFlagForTouch()
            // defaults to true for portability with the S1 behaviour (the most
            // restrictive of the three). Per-object opt-out is available if a
            // future S2/S3K-specific object needs to skip the render-flag gate.
            // Use isOnScreenForTouch() as the engine's equivalent of obRender
            // bit 7.
            if (touchProfile.requiresRenderFlagForTouch()
                    && instance instanceof AbstractObjectInstance aoi
                    && !aoi.isOnScreenForTouch()) {
                continue;
            }
            int flags;
            if (usePreUpdateState) {
                int preFlags = instance.getPreUpdateCollisionFlags();
                flags = (preFlags >= 0) ? preFlags : provider.getCollisionFlags();
            } else {
                flags = provider.getCollisionFlags();
            }
            if (flags == 0) {
                continue; // Skip collision for objects with no collision flags
            }
            int sizeIndex = flags & 0x3F;
            int width = table.getWidthRadius(sizeIndex);
            int height = table.getHeightRadius(sizeIndex);
            TouchCategory category = decodeCategory(flags, touchProfile);
            if (category == TouchCategory.HURT
                    && tryShieldDeflect(player, instance, provider, touchProfile, width, height)) {
                continue;
            }

            // ROM parity: ReactToItem runs in Sonic's slot (slot 0) BEFORE other
            // objects update. So touch collision sees objects at their pre-update
            // positions. Use getPreUpdateX()/getPreUpdateY() which return the
            // position snapshot taken before the object update loop ran.
            int objX = usePreUpdateState ? instance.getPreUpdateX() : instance.getX();
            int objY = usePreUpdateState ? instance.getPreUpdateY() : instance.getY();
            boolean overlap = isOverlapping(playerX, playerY, playerHeight, objX, objY, width, height, playerWidth);
            if (!isSidekick && debugState.isEnabled()) {
                int slotIndex = instance instanceof AbstractObjectInstance aoi
                        ? aoi.getSlotIndex()
                        : -1;
                debugState.addHit(
                        new TouchResponseDebugHit(slotIndex, instance.getSpawn(), objX, objY, flags, sizeIndex,
                                width, height, category, overlap,
                                instance instanceof AbstractObjectInstance aoi ? aoi.traceDebugDetails() : ""));
            }
            if (!overlap) {
                continue;
            }
            buildingSet.add(instance);

            // Type-keyed lost-ring collectible: ROM Touch_ChkValue ring branch
            // (docs/s2disasm/s2.asm:85196-85219). Evaluated EVERY frame on overlap
            // (NOT edge-triggered) so the ring collects the frame invulnerable_time
            // drops below 90 while the player is still continuously overlapping.
            // Keyed on the LostRingObjectInstance marker, NOT the 0x47 byte shape —
            // so other SPECIAL objects sharing $47 (e.g. S1 placed rings) keep their
            // own listener path. Crediting gate (ROM Touch_ChkValue): only the main
            // character collects/credits (sidekick Tails does not pick up rings);
            // BOTH players still break the loop (ROM rts on the first overlap). This is
            // now the sole lost-ring collection path — the legacy RingManager scan is gone.
            if (instance instanceof LostRingObjectInstance lostRing && lostRing.isLostRingCollectible()) {
                if (!isSidekick && player instanceof AbstractPlayableSprite aps) {
                    int invuln = aps.getInvulnerableFrames(); // AbstractPlayableSprite.java:2117
                    if (invuln < LOST_RING_INVULNERABLE_THRESHOLD && !lostRing.isCollected()) {
                        lostRing.markCollected(currentFrameCounter);
                        aps.addRings(1); // AbstractPlayableSprite.java:1427
                        objectManager.services().playSfx(GameSound.RING);
                    }
                }
                break; // ROM: rts — first overlapping object ends the loop (both paths).
            }
            // ROM touch checks run every frame for BOSS/HURT/ENEMY. SPECIAL
            // (collision_flags 0x40-0x7F) objects in ROM are run every
            // frame too, but the object itself typically transitions to a
            // 'touched' routine on first contact and ignores subsequent
            // overlaps — so engine still edge-triggers SPECIAL callbacks
            // to keep tests asserting that the object only responds once
            // per overlap.
            //
            // ENEMY must be continuous so a ROM Touch_Enemy/Touch_KillEnemy
            // path fires when the player transitions from non-attacking to
            // attacking while still overlapping the badnik. E.g. S2 MCZ
            // Crawlton at trace frame 825: Sonic stood in overlap (Hurt
            // path gated by invulnerable_time → Touch_NoHurt), then
            // initiated Spindash. ROM `Touch_Enemy` re-checks anim each
            // frame; once it sees AniIDSonAni_Spindash it calls
            // Touch_KillEnemy and applies the -$100 side-bounce
            // (s2.asm:84807-84890). Without continuous re-check the
            // badnik stays alive and Sonic's y_vel stays at 0, diverging
            // from ROM.
            //
            // ROM citations: s2.asm:84502-84890 (`TouchResponse`,
            // `Touch_Loop`, `Touch_Enemy`, `Touch_KillEnemy`); same
            // every-frame loop in S1 (`docs/s1disasm/_incObj/sub ReactToItem.asm`)
            // and S3K (`docs/skdisasm/sonic3k.asm` `Collision_response_list`
            // dispatcher).
            boolean shouldTrigger = category == TouchCategory.BOSS
                    || category == TouchCategory.HURT
                    || category == TouchCategory.ENEMY
                    || touchProfile.continuousCallbacks()
                    || !overlappingSet.contains(instance);
            if (shouldTrigger) {
                TouchResponseResult result = new TouchResponseResult(
                        sizeIndex, width, height, category, touchProfile.shieldReactionFlags());
                TouchResponseListener listener = instance instanceof TouchResponseListener casted ? casted : null;
                if (isSidekick) {
                    handleTouchResponseSidekick(player, instance, listener, result, touchProfile);
                } else {
                    handleTouchResponse(player, instance, listener, result, touchProfile);
                }
            }
            // ROM parity: ReactToItem ALWAYS exits after the first overlapping
            // object, regardless of category, player slot, or whether a response
            // was triggered. The ROM's handler returns via rts which exits the
            // entire ReactToItem subroutine.
            break;
        }
    }

    /**
     * Unified multi-region touch check for both player and sidekick.
     * Each region is checked separately; if any overlaps, the object is treated as
     * overlapping with the first matching region's collision flags applied.
     * One hit per object per frame is sufficient — returns after first overlapping region.
     */
    private boolean processMultiRegionTouch(PlayableEntity player,
            int playerX, int playerY, int playerHeight,
            ObjectInstance instance, TouchResponseProvider provider, TouchResponseProfile profile,
            TouchResponseProvider.TouchRegion[] regions, int playerWidth,
            Set<ObjectInstance> buildingSet, Set<ObjectInstance> overlappingSet,
            boolean isSidekick) {
        for (TouchResponseProvider.TouchRegion region : regions) {
            int flags = region.collisionFlags();
            if (flags == 0) {
                continue;
            }
            int sizeIndex = flags & 0x3F;
            int width = table.getWidthRadius(sizeIndex);
            int height = table.getHeightRadius(sizeIndex);
            TouchCategory category = decodeCategory(flags, profile);

            boolean overlap = isOverlappingXY(playerX, playerY, playerHeight,
                    region.x(), region.y(), width, height, playerWidth);
            if (!overlap) {
                continue;
            }

            buildingSet.add(instance);
            // ROM: HURT is continuous (same as BOSS) — see processCollisionLoop comment
            boolean shouldTrigger = category == TouchCategory.BOSS
                    || category == TouchCategory.HURT
                    || profile.continuousCallbacks()
                    || !overlappingSet.contains(instance);
            if (shouldTrigger) {
                TouchResponseResult result = new TouchResponseResult(
                        sizeIndex, width, height, category, region.shieldReactionFlags());
                TouchResponseListener listener = instance instanceof TouchResponseListener casted ? casted : null;
                if (isSidekick) {
                    handleTouchResponseSidekick(player, instance, listener, result, profile);
                } else {
                    handleTouchResponse(player, instance, listener, result, profile);
                }
            }
            // ROM parity: ReactToItem ALWAYS exits on first overlap, even
            // when the response is edge-trigger suppressed. Match the
            // single-region break-on-first-overlap behaviour.
            return !isSidekick;
        }
        return false; // No region overlapped
    }

    private boolean tryShieldDeflect(PlayableEntity player, ObjectInstance instance,
            TouchResponseProvider provider, TouchResponseProfile profile, int objectWidth, int objectHeight) {
        if (player == null || !player.hasShield()) {
            return false;
        }
        if (profile.shieldDeflectCapability() != TouchShieldDeflectCapability.SHIELD_DEFLECT
                || (profile.shieldReactionFlags() & SHIELD_REACTION_BOUNCE_BIT) == 0) {
            return false;
        }

        int shieldLeft = player.getCentreX() - SHIELD_TOUCH_HALF_SIZE;
        int shieldTop = player.getCentreY() - SHIELD_TOUCH_HALF_SIZE;
        boolean overlap = isRectOverlapping(shieldLeft, shieldTop, SHIELD_TOUCH_SIZE, SHIELD_TOUCH_SIZE,
                instance.getX(), instance.getY(), objectWidth, objectHeight);
        if (!overlap) {
            return false;
        }
        return provider.onShieldDeflect(player);
    }

    /**
     * ROM: In 1P mode, CPU Tails interacts with objects but hurt handling differs:
     * - Can destroy badniks while rolling/invincible (same as Sonic)
     * - Gets knocked back when hurt but does NOT scatter rings or die
     * - Special category objects still interact normally
     */
    private void handleTouchResponseSidekick(PlayableEntity sidekick, ObjectInstance instance,
            TouchResponseListener listener, TouchResponseResult result, TouchResponseProfile profile) {
        if (sidekick == null) {
            return;
        }
        if (listener != null) {
            listener.onTouchResponse(sidekick, result, currentFrameCounter);
        }
        if (result.category() == TouchCategory.SPECIAL
                && profile.enablesPostSpecialTouchAirborneSideVelocityPreservation()) {
            lastSpecialTouchFrame.put(sidekick, currentFrameCounter);
        }

        switch (result.category()) {
            case HURT -> applySidekickHurt(sidekick, instance);
            case ENEMY -> {
                if (isPlayerAttacking(sidekick, instance)) {
                    // ROM: Touch_Enemy_Part2 checks collision_property BEFORE decrementing HP.
                    int hpBeforeHit = 0;
                    if (instance instanceof TouchResponseProvider provider2) {
                        hpBeforeHit = provider2.getCollisionProperty();
                    }
                    // ROM parity (sonic3k.asm:20945-20990): Touch_EnemyNormal sets
                    // status bit 7 on the badnik AND applies +/-$100 bounce to the
                    // attacking player in the SAME function. The skip-when-destroyed
                    // behaviour only applies to a SUBSEQUENT collision pass (e.g. after
                    // P1 destroys a badnik in P1's TouchResponse, P2's pass naturally
                    // skips because the object's (a0) was rewritten to Obj_Explosion).
                    // We mirror that by capturing the pre-attack destroyed state and
                    // gating the bounce only on prior destruction - never on the
                    // sidekick's own kill (which would suppress her +/-$100 bounce).
                    boolean wasAlreadyDestroyed = instance instanceof AbstractObjectInstance preAoi
                            && preAoi.isDestroyed();
                    if (instance instanceof TouchResponseAttackable attackable) {
                        attackable.onPlayerAttack(sidekick, result);
                    }
                    // ROM byte zero-test gate, not signed compare (see player path
                    // above): S2 s2.asm:85282-85290 Touch_Enemy_Part2 tst.b
                    // collision_property/beq; S1 React_Enemy tst.b obColProp/beq;
                    // S3K sonic3k.asm:20911-20922 tst.b boss_hitcount2/beq. A NONZERO
                    // byte (incl. 0xFF/-1 always-bounce) negates both velocities.
                    if ((hpBeforeHit & 0xFF) != 0) {
                        // S3K boss-hit path also negates ground_vel; S1/S2 keep it.
                        sidekick.setXSpeed((short) -sidekick.getXSpeed());
                        sidekick.setYSpeed((short) -sidekick.getYSpeed());
                        if (sidekick.getPhysicsFeatureSet() != null
                                && sidekick.getPhysicsFeatureSet().bossHitNegatesGroundSpeed()) {
                            sidekick.setGSpeed((short) -sidekick.getGSpeed());
                        }
                    } else if (!wasAlreadyDestroyed) {
                        // Touch_EnemyNormal bounce: fires when this player's pass kills
                        // the instance. Objects that handle their own bounce (like Crawl
                        // shield mode) without self-destructing are excluded.
                        boolean isNowDestroyed = instance instanceof AbstractObjectInstance postAoi
                                && postAoi.isDestroyed();
                        if (isNowDestroyed) {
                            applyEnemyBounce(sidekick, instance);
                        }
                    }
                } else {
                    applySidekickHurt(sidekick, instance);
                }
            }
            case SPECIAL -> {
                // Listener handles object-specific logic.
            }
            case BOSS -> {
                if (isPlayerAttacking(sidekick, instance)) {
                    if (instance instanceof TouchResponseAttackable attackable) {
                        attackable.onPlayerAttack(sidekick, result);
                    }
                    applyBossBounce(sidekick);
                } else {
                    applySidekickHurt(sidekick, instance);
                }
            }
        }
    }

    /**
     * ROM: Hurt_Sidekick in 1P mode - just knockback, no ring scatter, no death.
     * From s2.asm HurtCharacter: in 1P mode, branches directly to Hurt_Sidekick
     * which applies hurt animation without checking rings.
     */
    private void applySidekickHurt(PlayableEntity sidekick, ObjectInstance instance) {
        if (sidekick.getInvulnerable()) {
            return;
        }
        int sourceX = instance != null ? instance.getX() : sidekick.getCentreX();
        // ROM: Hurt_Sidekick in 1P mode - just apply hurt knockback, no ring scatter
        sidekick.applyHurt(sourceX);
    }

    private boolean isOverlapping(int playerX, int playerY, int playerHeight,
            int objectX, int objectY, int objectWidth, int objectHeight, int playerWidth) {
        int dx = objectX - objectWidth - playerX;
        if (dx < 0) {
            int sum = (dx & 0xFFFF) + ((objectWidth * 2) & 0xFFFF);
            if (sum <= 0xFFFF) {
                return false;
            }
        } else if (dx > playerWidth) {
            return false;
        }

        int dy = objectY - objectHeight - playerY;
        if (dy < 0) {
            int sum = (dy & 0xFFFF) + ((objectHeight * 2) & 0xFFFF);
            if (sum <= 0xFFFF) {
                return false;
            }
        } else if (dy > playerHeight) {
            return false;
        }

        return true;
    }

    private boolean isRectOverlapping(int playerLeft, int playerTop, int playerWidth, int playerHeight,
            int objectX, int objectY, int objectWidth, int objectHeight) {
        int playerRight = playerLeft + playerWidth;
        int playerBottom = playerTop + playerHeight;
        int objectLeft = objectX - objectWidth;
        int objectRight = objectX + objectWidth;
        int objectTop = objectY - objectHeight;
        int objectBottom = objectY + objectHeight;
        return playerRight >= objectLeft
                && playerLeft <= objectRight
                && playerBottom >= objectTop
                && playerTop <= objectBottom;
    }

    /**
     * Overlap check using raw x/y coordinates instead of ObjectSpawn.
     * Used by multi-region touch collision.
     */
    private boolean isOverlappingXY(int playerX, int playerY, int playerHeight,
            int objX, int objY, int objectWidth, int objectHeight, int playerWidth) {
        int dx = objX - objectWidth - playerX;
        if (dx < 0) {
            int sum = (dx & 0xFFFF) + ((objectWidth * 2) & 0xFFFF);
            if (sum <= 0xFFFF) {
                return false;
            }
        } else if (dx > playerWidth) {
            return false;
        }

        int dy = objY - objectHeight - playerY;
        if (dy < 0) {
            int sum = (dy & 0xFFFF) + ((objectHeight * 2) & 0xFFFF);
            if (sum <= 0xFFFF) {
                return false;
            }
        } else if (dy > playerHeight) {
            return false;
        }

        return true;
    }

    private TouchCategory decodeCategory(int flags, TouchResponseProfile profile) {
        int categoryBits = flags & 0xC0;
        int sizeIndex = flags & 0x3F;
        if (profile != null && profile.categoryDecodeMode() == TouchCategoryDecodeMode.FORCE_ENEMY) {
            return TouchCategory.ENEMY;
        }
        if (categoryBits == 0xC0 && profile != null
                && profile.categoryDecodeMode() == TouchCategoryDecodeMode.S3K_SPECIAL_PROPERTY) {
            // ROM: S3K Touch_ChkValue sends every $C0 collision flag to
            // Touch_Special (sonic3k.asm:20773-20778). Touch_Special only
            // increments collision_property for selected size indices and
            // otherwise returns (sonic3k.asm:21162-21194); it is never the
            // generic boss-bounce path.
            return TouchCategory.SPECIAL;
        }
        if (categoryBits == 0xC0 && profile != null) {
            boolean propertySpecial = switch (profile.categoryDecodeMode()) {
                case S3K_SPECIAL_PROPERTY -> isS3kTouchSpecialPropertyIndex(sizeIndex);
                case SONIC2_SPECIAL_PROPERTY -> isSonic2TouchSpecialPropertyIndex(sizeIndex);
                case NORMAL, FORCE_ENEMY -> false;
            };
            if (propertySpecial) {
                return TouchCategory.SPECIAL;
            }
        }
        return switch (categoryBits) {
            case 0x00 -> TouchCategory.ENEMY;
            case 0x40 -> TouchCategory.SPECIAL;
            case 0x80 -> TouchCategory.HURT;
            default -> TouchCategory.BOSS;
        };
    }

    private boolean isS3kTouchSpecialPropertyIndex(int sizeIndex) {
        return switch (sizeIndex) {
            case 0x06, 0x07, 0x0A, 0x0C, 0x15, 0x16, 0x17, 0x18, 0x21 -> true;
            default -> false;
        };
    }

    private boolean isSonic2TouchSpecialPropertyIndex(int sizeIndex) {
        return switch (sizeIndex) {
            case 0x06, 0x07, 0x0A, 0x0B, 0x0C, 0x14, 0x15, 0x16,
                    0x17, 0x18, 0x1A, 0x21 -> true;
            default -> false;
        };
    }

    private void handleTouchResponse(PlayableEntity player, ObjectInstance instance,
            TouchResponseListener listener, TouchResponseResult result, TouchResponseProfile profile) {
        if (player == null) {
            return;
        }
        if (listener != null) {
            listener.onTouchResponse(player, result, currentFrameCounter);
        }
        if (result.category() == TouchCategory.SPECIAL
                && profile.enablesPostSpecialTouchAirborneSideVelocityPreservation()) {
            lastSpecialTouchFrame.put(player, currentFrameCounter);
        }

        switch (result.category()) {
            case HURT -> applyHurt(player, instance, result);
            case ENEMY -> {
                if (isPlayerAttacking(player, instance)) {
                    // ROM: Touch_Enemy_Part2 checks collision_property BEFORE decrementing HP.
                    // Capture HP before onPlayerAttack (which may decrement it).
                    int hpBeforeHit = 0;
                    if (instance instanceof TouchResponseProvider provider2) {
                        hpBeforeHit = provider2.getCollisionProperty();
                    }
                    // ROM parity (s2.asm:84842-84863): Touch_KillEnemy rewrites
                    // the badnik slot to ObjID_Explosion before applying the
                    // bounce at s2.asm:84865-84889. A later touch pass must not
                    // bounce from an engine instance that was already destroyed.
                    boolean wasAlreadyDestroyed = instance instanceof AbstractObjectInstance preAoi
                            && preAoi.isDestroyed();
                    if (instance instanceof TouchResponseAttackable attackable) {
                        attackable.onPlayerAttack(player, result);
                    }
                    // ROM gates the boss-rebound on a BYTE zero-test, not a signed
                    // compare. All three games tst.b the gated byte and beq to the
                    // kill path; a NONZERO byte (incl. the 0xFF "always-bounce" the
                    // S2 DEZ Death Egg Robot head writes, ObjC7_Head rtn 8
                    // s2.asm:83276-83277 move.b #-1,collision_property) runs
                    // neg.w x_vel / neg.w y_vel. References:
                    //   S2  s2.asm:85282-85290 Touch_Enemy_Part2:
                    //       tst.b collision_property(a1); beq Touch_KillEnemy;
                    //       neg.w x_vel(a0); neg.w y_vel(a0)
                    //   S1  s1disasm _incObj/sub ReactToItem.asm:181-191 React_Enemy:
                    //       tst.b obColProp(a1); beq .breakenemy; neg.w obVelX/obVelY
                    //   S3K sonic3k.asm:20911-20922 .checkhurtenemy:
                    //       tst.b boss_hitcount2(a1); beq Touch_EnemyNormal;
                    //       neg.w x_vel; neg.w y_vel; neg.w ground_vel
                    // A signed `> 0` test wrongly rejected 0xFF/-1. For S3K bosses
                    // getCollisionProperty() returns positive HP, so != 0 is
                    // behaviorally identical there; the only changed case is the
                    // 0xFF always-bounce value, treated as nonzero by all 3 ROMs.
                    if ((hpBeforeHit & 0xFF) != 0) {
                        // S3K boss-hit path also negates ground_vel; S1/S2 keep it.
                        player.setXSpeed((short) -player.getXSpeed());
                        player.setYSpeed((short) -player.getYSpeed());
                        if (player.getPhysicsFeatureSet() != null
                                && player.getPhysicsFeatureSet().bossHitNegatesGroundSpeed()) {
                            player.setGSpeed((short) -player.getGSpeed());
                        }
                    } else if (!wasAlreadyDestroyed) {
                        // Touch_KillEnemy: position-based bounce only when onPlayerAttack
                        // destroyed the instance. Objects like Crawl that handle their own
                        // custom bounce without self-destructing must not get a second bounce.
                        boolean isNowDestroyed = instance instanceof AbstractObjectInstance postAoi
                                && postAoi.isDestroyed();
                        if (isNowDestroyed) {
                            applyEnemyBounce(player, instance);
                        }
                    }
                } else {
                    applyHurt(player, instance, result);
                }
            }
            case SPECIAL -> {
                // Listener handles object-specific logic.
            }
            case BOSS -> {
                if (isPlayerAttacking(player, instance)) {
                    if (instance instanceof TouchResponseAttackable attackable) {
                        attackable.onPlayerAttack(player, result);
                    }
                    applyBossBounce(player);
                } else {
                    applyHurt(player, instance, result);
                }
            }
        }
    }

    private boolean isPlayerAttacking(PlayableEntity player, ObjectInstance target) {
        if (player == null) {
            return false;
        }
        if (player.isSuperSonic()
                || player.getInvincibleFrames() > 0
                || isSpinAttackAnimation(player)
                || (instaShieldActive && player == currentPlayer)) {
            return true;
        }

        PhysicsFeatureSet features = player.getPhysicsFeatureSet();
        if (features != null && features.elementalShieldsEnabled()) {
            return isS3kAbilityAttack(player, target);
        }

        return false;
    }

    private boolean isSpinAttackAnimation(PlayableEntity player) {
        int animation = player.getAnimationId();
        return animation == ObjectManager.ANIM_ROLL || animation == ObjectManager.ANIM_SPINDASH;
    }

    private boolean isS3kAbilityAttack(PlayableEntity player, ObjectInstance target) {
        if (player instanceof Knuckles) {
            int flag = player.getDoubleJumpFlag();
            return flag == 1 || flag == 3;
        }
        if (player instanceof Tails tails) {
            return player.getDoubleJumpFlag() != 0
                    && !tails.isInWater()
                    && isTailsFlightAttackAngle(player, target);
        }
        return false;
    }

    private boolean isTailsFlightAttackAngle(PlayableEntity player, ObjectInstance target) {
        if (target == null) {
            return false;
        }
        int dx = (short) (player.getCentreX() - target.getX());
        int dy = (short) (player.getCentreY() - target.getY());
        int angle = segaAngle(dx, dy);
        return ((angle - 0x20) & 0xFF) < 0x40;
    }

    private int segaAngle(int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return 0x40;
        }
        double radians = Math.atan2(dy, dx);
        int angle = (int) Math.round(radians * 128.0 / Math.PI);
        return angle & 0xFF;
    }

    private void applyEnemyBounce(PlayableEntity player, ObjectInstance instance) {
        // ROM-accurate: React_Enemy (s1.asm) only modifies obVelY, it does NOT
        // set the air flag. Letting the collision system handle air state naturally
        // preserves rolling through enemy bounces (ground roll into badnik).
        short ySpeed = player.getYSpeed();
        if (ySpeed < 0) {
            player.setYSpeed((short) (ySpeed + 0x100));
            return;
        }
        // Use center coordinates to match ROM y_pos behavior
        int playerY = player.getCentreY();
        // ROM: cmp.w y_pos(a1),d0 — use current position, not spawn
        int enemyY = instance != null ? instance.getY() : playerY;
        if (playerY < enemyY) {
            player.setYSpeed((short) -ySpeed);
        } else {
            player.setYSpeed((short) (ySpeed - 0x100));
        }
    }

    /**
     * ROM-accurate boss bounce: negate both X and Y velocities.
     * From s2.asm Touch_Enemy_Part2 lines 84806-84807.
     * Does not set air flag - ROM only modifies velocities here.
     */
    private void applyBossBounce(PlayableEntity player) {
        player.setXSpeed((short) -player.getXSpeed());
        player.setYSpeed((short) -player.getYSpeed());
        if (player.getPhysicsFeatureSet() != null
                && player.getPhysicsFeatureSet().bossHitNegatesGroundSpeed()) {
            player.setGSpeed((short) -player.getGSpeed());
        }
    }

    private void applyHurt(PlayableEntity player, ObjectInstance instance, TouchResponseResult result) {
        if (player.getInvulnerable()) {
            return;
        }

        if (instance != null) {
            String className = instance.getClass().getSimpleName();
            int objectId = instance.getSpawn().objectId();
            LOGGER.fine(() -> "Touch hurt by: " + className + " (ID: 0x" + Integer.toHexString(objectId) + ")");
        }

        int sourceX = instance != null ? instance.getX() : player.getCentreX();
        boolean spikeHit = instance != null && instance.getSpawn().objectId() == 0x36;

        // S3K shield_reaction bit 4: fire shield blocks fire damage
        boolean fireHit = !spikeHit && result != null
                && (result.shieldReactionFlags() & 0x10) != 0;

        DamageCause cause = spikeHit
                ? DamageCause.SPIKE
                : fireHit ? DamageCause.FIRE
                : DamageCause.NORMAL;

        boolean hadRings = player.getRingCount() > 0;
        boolean suppressLostRingSpawn = player instanceof AbstractPlayableSprite aps
                && aps.suppressesLostRingSpawnOnHurt();
        if (hadRings && !player.hasShield() && !suppressLostRingSpawn) {
            // Requires the concrete playable type, but still goes through ObjectServices.
            if (player instanceof AbstractPlayableSprite aps) {
                objectManager.services().spawnLostRings(aps, currentFrameCounter);
            }
        }
        player.applyHurtOrDeath(sourceX, cause, hadRings);
    }

    TouchResponseDebugState getDebugState() {
        return debugState;
    }
}

