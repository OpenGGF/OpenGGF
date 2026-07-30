package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;
import java.util.logging.Logger;

/**
 * Sonic 3&K Special Stage Entry Ring (Obj_SSEntryRing, object ID 0x85).
 * <p>
 * The giant gold ring that warps the player into a Special Stage when touched.
 * Each ring has a subtype (0-31) used as a bit index into the 32-bit
 * {@code Collected_special_ring_array} bitfield. If the ring's bit is already
 * set, it is deleted immediately on spawn.
 * <p>
 * Animation (AniRaw_SSEntryRing):
 * <ul>
 *   <li>Formation (anim 0): delay=4, frames 0,0,1,2,3,4,5,6,7, loop</li>
 *   <li>Idle (anim 1): delay=6, frames 10,9,8,11, loop</li>
 * </ul>
 * <p>
 * On touch (SSEntryRing_Main collision):
 * <ol>
 *   <li>Play sfx_BigRing</li>
 *   <li>Branch based on emerald state:
 *     <ul>
 *       <li>Chaos emeralds &lt; 7: enter Special Stage (full flash sequence)</li>
 *       <li>All emeralds collected: award 50 rings, ring vanishes immediately</li>
 *       <li>Hidden Palace routes are disabled until HPZ is registered as a loadable level</li>
 *     </ul>
 *   </li>
 *   <li>For Special Stage path: lock player (hidden + object controlled),
 *       freeze camera, spawn {@link Sonic3kSSEntryFlashObjectInstance} which
 *       handles the flash animation, wait, sfx_EnterSS, and transition</li>
 * </ol>
 * <p>
 * Art: ArtUnc_SSEntryRing (9984 bytes), Map_SSEntryRing (12 frames),
 * DPLC_SSEntryRing (12 frames). art_tile = make_art_tile(ArtTile_Explosion,1,0).
 * <p>
 * Reference: docs/skdisasm/sonic3k.asm Obj_SSEntryRing (lines 128211-128530)
 */
public class Sonic3kSSEntryRingObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kSSEntryRingObjectInstance.class.getName());

    // Collision extents from center (ROM: SSEntry_Range: dc.w -$18, $30, -$28, $50)
    // Check_PlayerInRange interprets as {offset, span} pairs:
    //   left  = ring_x + (-$18),  right  = left + $30 = ring_x + $18
    //   top   = ring_y + (-$28),  bottom = top  + $50 = ring_y + $28
    // Symmetric box: X [-24, +24), Y [-40, +40) relative to ring center
    private static final int COLLISION_X_MIN = -0x18;  // -24
    private static final int COLLISION_X_MAX =  0x18;  //  24  (-$18 + $30)
    private static final int COLLISION_Y_MIN = -0x28;  // -40
    private static final int COLLISION_Y_MAX =  0x28;  //  40  (-$28 + $50)

    // ROM: render_flags = 4 (on-screen check), priority = $280 (bucket 5)
    private static final int RENDER_PRIORITY = 5;

    // Formation animation: mapping frames 0-7, delay=4 game frames per anim frame
    // ROM: AniRaw_SSEntryRing anim 0: dc.b 4, 0, 0, 1, 2, 3, 4, 5, 6, 7, $F8, $0C
    // ROM Animate_Raw uses down-counter: delay N means N+1 game frames per anim frame
    private static final int FORMATION_DELAY = 4;
    private static final int[] FORMATION_FRAMES = {0, 0, 1, 2, 3, 4, 5, 6, 7};

    // Idle animation: mapping frames 8-11, delay=6 game frames per anim frame
    // ROM: AniRaw_SSEntryRing anim 1: dc.b 6, $A, 9, 8, $B, $FC
    private static final int IDLE_DELAY = 6;
    private static final int[] IDLE_FRAMES = {10, 9, 8, 11};

    // ROM collision gate: mapping_frame must be >= 8 for collision to be active.
    // Formation frames are 0-7, idle frames are 8-11. This is the definitive guard
    // matching sonic3k.asm line 128261: cmpi.b #8,mapping_frame(a0) / blo.s locret_61708
    private static final int COLLISION_FRAME_THRESHOLD = 8;

    // Ring award when all emeralds already collected
    private static final int RING_REWARD = 50;
    private static final String PALETTE_OWNER = "s3k.ssEntryRing";
    private static final int NORMAL_PALETTE_RAM_BASE = 0xFC00;

    /** Object states matching ROM routine progression. */
    private enum State {
        /**
         * Main state: ring animates (formation then idle) and checks collision.
         * Matches ROM's SSEntryRing_Main (routine 2) which handles BOTH formation
         * and idle via Animate_Raw + mapping_frame >= 8 gate.
         */
        MAIN,
        /** Player touched ring, flash animation playing, awaiting deletion mark. */
        ENTERED,
        /** Ring marked for deletion by flash (bit 5 in ROM). */
        MARKED_DELETE
    }

    /** Subtype low bits are the bit index (0-31) into Collected_special_ring_array. */
    private int bitIndex;
    private boolean forcedSanctuaryRoute;
    private boolean superEmeraldRing;
    private int paletteStep;
    private int paletteTimer;
    private int paletteDataAddress;
    private int paletteCursor;
    private int paletteLine;
    private int paletteStartColor;
    private int paletteColorCount;
    private int palette2Timer;
    private int palette2DataAddress;
    private int palette2Cursor;
    private int palette2Line;
    private int palette2StartColor;
    private int palette2ColorCount;
    private boolean paletteScriptsLoaded;
    private boolean paletteScriptsUnavailable;
    private int[] lastAppliedPaletteWords;

    private State state;
    private boolean initialized;

    /**
     * Animation down-counter matching ROM's Animate_Raw.
     * Starts at the delay value and decrements each frame.
     * When it underflows below 0, reload from current delay and advance frame.
     * This gives delay+1 game frames per animation frame (ROM-accurate).
     */
    private int animTimer;

    /** Current index into the active animation frame array. */
    private int animIndex;

    /** Current mapping frame to display. */
    private int mappingFrame;

    /** Which animation is active: formation (false) or idle (true). */
    private boolean inIdleAnim;

    public Sonic3kSSEntryRingObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSEntryRing");
        this.bitIndex = spawn.subtype() & 0x1F;
        this.forcedSanctuaryRoute = (spawn.subtype() & 0x80) != 0;

        // Default to MAIN state; ensureInitialized will check collection status
        this.state = State.MAIN;
        this.inIdleAnim = false;
        this.animTimer = 0;
        this.animIndex = 0;
        this.mappingFrame = FORMATION_FRAMES[0];
    }

    @Override
    public Sonic3kSSEntryRingObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new Sonic3kSSEntryRingObjectInstance(ctx.spawn());
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        // ROM pre-check: if already collected, delete immediately
        var gameState = services().gameState();
        if (gameState.isSpecialRingCollected(bitIndex)) {
            setDestroyed(true);
            this.state = State.MARKED_DELETE;
            return;
        }
        // Obj_SSEntryRing loc_616C6: a negative subtype is always presented
        // as a Super Emerald ring. In locked-on S3K, an ordinary ring also
        // gets that presentation on the SK side once all Chaos Emeralds exist.
        this.superEmeraldRing = forcedSanctuaryRoute
                || (Sonic3kZoneIds.isSkSideZone(services().currentZone()) && gameState.hasAllEmeralds());
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        ensureInitialized();
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        switch (state) {
            case MAIN -> updateMain(player);
            case ENTERED -> { /* Ring continues displaying; flash controls deletion */ }
            case MARKED_DELETE -> {
                // ROM restores palette + reloads ArtKosM_BadnikExplosion here because
                // the ring's DPLC overwrites shared VRAM at ArtTile_Explosion. Our engine
                // uses standalone Pattern[] arrays so no restoration is needed.
                setDestroyed(true);
            }
        }
    }

    /**
     * Combined formation + idle handler matching ROM's SSEntryRing_Main.
     * <p>
     * ROM flow (sonic3k.asm line 128257-128269):
     * <ol>
     *   <li>{@code jsr (Animate_Raw).l} — advance animation</li>
     *   <li>{@code cmpi.b #8,mapping_frame(a0) / blo.s locret} — gate collision on frame number</li>
     *   <li>{@code jsr (Check_PlayerInRange).l} — manual range check</li>
     * </ol>
     * <p>
     * ROM also calls {@code Obj_WaitOffscreen} before this routine, which prevents
     * ALL processing while the ring is off-screen. We replicate this by skipping
     * the entire update when the ring is not visible.
     */
    private void updateMain(AbstractPlayableSprite player) {
        // ROM: Obj_WaitOffscreen installs a 0x20 x 0x20 render box and does not
        // restore the normal object routine until Render_Sprites sets bit 7.
        // The ring therefore starts forming only when that box overlaps the
        // viewport, rather than at the wider placement/spawn boundary.
        if (!isWithinRenderSpriteBounds(OFFSCREEN_HALF_EXTENT, OFFSCREEN_HALF_EXTENT)) {
            return;
        }
        updateSuperEmeraldPalette();

        // ROM: jsr (Animate_Raw).l — advance animation using down-counter
        advanceAnimation();

        // ROM: tst.w (Debug_placement_mode).w / bne.s locret_61708
        // ROM explicitly disables big ring collision in debug mode.
        if (player != null && player.isDebugMode()) {
            return;
        }

        // ROM: cmpi.b #8,mapping_frame(a0) / blo.s locret_61708
        // If ring hasn't finished forming (mapping_frame < 8), don't allow collision.
        if (mappingFrame < COLLISION_FRAME_THRESHOLD) {
            return;
        }

        // ROM: Check_PlayerInRange — manual range collision
        if (player != null) {
            checkCollision(player);
        }
    }

    /** ROM Obj_WaitOffscreen width_pixels/height_pixels values. */
    private static final int OFFSCREEN_HALF_EXTENT = 0x20;

    /**
     * Advances animation matching ROM's Animate_Raw down-counter pattern.
     * Timer starts at delay value, decrements each frame. When it underflows
     * below 0, the delay is reloaded and the frame index advances.
     * This gives delay+1 game frames per animation frame (ROM-accurate).
     */
    private void advanceAnimation() {
        animTimer--;
        if (animTimer >= 0) {
            return; // Still counting down — hold current frame
        }

        // Timer underflowed: advance to next frame
        if (!inIdleAnim) {
            // Currently in formation animation
            animIndex++;
            if (animIndex >= FORMATION_FRAMES.length) {
                // Formation complete → transition to idle animation
                // ROM: $F8,$0C command jumps to idle animation data
                inIdleAnim = true;
                animIndex = 0;
                animTimer = IDLE_DELAY;
                mappingFrame = IDLE_FRAMES[0];
                return;
            }
            animTimer = FORMATION_DELAY;
            mappingFrame = FORMATION_FRAMES[animIndex];
        } else {
            // Currently in idle animation (looping)
            animIndex++;
            if (animIndex >= IDLE_FRAMES.length) {
                animIndex = 0; // ROM: $FC = loop to start
            }
            animTimer = IDLE_DELAY;
            mappingFrame = IDLE_FRAMES[animIndex];
        }
    }

    /**
     * Checks collision between the player and this ring.
     * Uses center-to-center distance matching the ROM's SSEntry_Range box.
     */
    private void checkCollision(AbstractPlayableSprite player) {
        // ROM: skip if player dead (routine >= 6)
        if (player.getDead()) {
            return;
        }

        int playerCX = player.getCentreX();
        int playerCY = player.getCentreY();
        int ringX = spawn.x();
        int ringY = spawn.y();

        int dx = playerCX - ringX;
        int dy = playerCY - ringY;

        if (dx >= COLLISION_X_MIN && dx < COLLISION_X_MAX
                && dy >= COLLISION_Y_MIN && dy < COLLISION_Y_MAX) {
            onTouched(player);
        }
    }

    /**
     * Handle ring touch. Branches based on emerald state.
     * <p>
     * ROM branching (SSEntryRing_Main lines 128272-128323):
     * <ul>
     *   <li>Chaos emeralds &lt; 7 → enter Special Stage</li>
     *   <li>SK_alone + 7 chaos → award 50 rings</li>
     *   <li>S3 level + 7 chaos → enter Special Stage (for super emeralds)</li>
     *   <li>SK level + 7 chaos + 7 super → award 50 rings</li>
     *   <li>Subtype bit 7 set → Hidden Palace after the flash sequence, once HPZ is loadable</li>
     * </ul>
     */
    private void onTouched(AbstractPlayableSprite player) {
        LOGGER.fine(() -> String.format(
                "SSEntryRing #%d TOUCHED at (%d,%d) — mappingFrame=%d, inIdleAnim=%b, player(%d,%d)",
                bitIndex, spawn.x(), spawn.y(), mappingFrame, inIdleAnim,
                player.getCentreX(), player.getCentreY()));
        var gameState = services().gameState();

        // Special stage entry is fully disabled during a time attack (see
        // GameLoop.enterSpecialStage()). Unlike the star-post-bonus-star path,
        // the special-stage-entry branch below hides/control-locks the player
        // and freezes the camera well before the GameLoop chokepoint is ever
        // reached — swallowing the request there alone would leave the run
        // stuck. Check here, before any state change, so the ring stays inert
        // and the player simply passes through.
        if (gameState != null && gameState.isTimeAttackActive()) {
            return;
        }

        // Play sfx_BigRing ($B3) — always plays on touch
        services().playSfx(Sonic3kSfx.BIG_RING.id);

        // loc_6170A: the immediate 50-ring reward is reached only with all
        // Chaos Emeralds and either an S3-side level, SK-alone, or all seven
        // Super Emeralds on the SK side. The supported locked-on ROM has
        // SK_alone_flag clear.
        if (gameState.hasAllEmeralds()
                && (!Sonic3kZoneIds.isSkSideZone(services().currentZone()) || gameState.hasAllSuperEmeralds())) {
            LOGGER.fine("SSEntryRing #" + bitIndex + " — all emeralds, awarding 50 rings");
            // loc_61794 plays sfx_BigRing a second time before AddRings.
            services().playSfx(Sonic3kSfx.BIG_RING.id);
            gameState.markSpecialRingCollected(bitIndex);
            player.addRings(RING_REWARD);
            restoreRingPalette();
            setDestroyed(true);
        } else {
            // Path A: Enter Special Stage — full flash sequence
            // ROM: loc_61774 — lock player, spawn flash
            LOGGER.fine("SSEntryRing #" + bitIndex + " — entering Special Stage sequence");
            enterSpecialStageSequence(player);
        }
    }

    /**
     * Initiates the special stage entry sequence.
     * ROM: SSEntryRing_Main lines 128287-128305
     * <ol>
     *   <li>Lock player: object_control=$53, anim=$1C, Player_prev_frame=-1</li>
     *   <li>Freeze camera at current position</li>
     *   <li>Spawn Obj_SSEntryFlash child</li>
     *   <li>Ring enters ENTERED state (continues displaying until flash marks it)</li>
     * </ol>
     */
    private void enterSpecialStageSequence(AbstractPlayableSprite player) {
        state = State.ENTERED;

        // Save_Level_Data2 is deliberately deferred until SSEntryFlash_GoSS,
        // after the existing flash animation and wait.
        player.setHidden(true);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        services().camera().setFrozen(true);

        S3kBigRingTransitionIntent intent =
                new S3kBigRingTransitionIntent(spawn.subtype(), parentRewindIdOrNull());
        spawnDynamicObject(new Sonic3kSSEntryFlashObjectInstance(
                this, spawn.x(), spawn.y(), intent));
    }

    ObjectRefId parentRewindIdOrNull() {
        var objectManager = services().objectManager();
        if (objectManager == null) {
            return null;
        }
        return objectManager.captureIdentityContext().requireIdentityTable().idFor(this);
    }

    int rawSubtype() {
        return spawn.subtype();
    }

    /**
     * Called by {@link Sonic3kSSEntryFlashObjectInstance} at anim_frame 3
     * to mark this ring for deletion.
     * ROM: bset #5,$38(a1) — sets deletion flag on parent ring.
     */
    public void markForDeletion() {
        state = State.MARKED_DELETE;
        restoreRingPalette();
        LOGGER.fine("SSEntryRing #" + bitIndex + " marked for deletion by flash");
    }

    private void updateSuperEmeraldPalette() {
        if (!superEmeraldRing || !ensurePaletteScriptsLoaded()) {
            return;
        }
        int[] first = advancePaletteScript(false);
        int[] second = advancePaletteScript(true);
        if (first != null) {
            applyRingPalette(paletteLine, paletteStartColor, first);
            paletteStep++;
        }
        if (second != null) {
            applyRingPalette(palette2Line, palette2StartColor, second);
        }
        if (first != null || second != null) {
            if (lastAppliedPaletteWords == null) {
                lastAppliedPaletteWords = new int[3];
            }
            if (first != null) {
                System.arraycopy(first, 0, lastAppliedPaletteWords, 0, Math.min(2, first.length));
            }
            if (second != null && second.length > 0) {
                lastAppliedPaletteWords[2] = second[0];
            }
        }
    }

    private void restoreRingPalette() {
        if (!superEmeraldRing) {
            return;
        }
        try {
            int pair = services().rom().read32BitAddr(
                    Sonic3kConstants.PAL_SS_ENTRY_NORMAL_PAIR_ADDR);
            int[] normalWords = {
                    (pair >>> 16) & 0xFFFF,
                    pair & 0xFFFF,
                    services().rom().read16BitAddr(
                            Sonic3kConstants.PAL_SS_ENTRY_NORMAL_FINAL_ADDR)
            };
            applyRingPalette(1, 5,
                    new int[] {normalWords[0], normalWords[1]});
            applyRingPalette(1, 15, new int[] {normalWords[2]});
            lastAppliedPaletteWords = normalWords;
        } catch (java.io.IOException e) {
            paletteScriptsUnavailable = true;
            LOGGER.warning("Unable to restore SS-entry palette from ROM: " + e.getMessage());
        }
    }

    private boolean ensurePaletteScriptsLoaded() {
        if (paletteScriptsLoaded) {
            return true;
        }
        if (paletteScriptsUnavailable) {
            return false;
        }
        try {
            loadPaletteScript(Sonic3kConstants.PAL_SCRIPT_SS_ENTRY_ADDR, false);
            loadPaletteScript(Sonic3kConstants.PAL_SCRIPT_SS_ENTRY_2_ADDR, true);
            paletteScriptsLoaded = true;
            return true;
        } catch (java.io.IOException | RuntimeException e) {
            paletteScriptsUnavailable = true;
            LOGGER.warning("Unable to load SS-entry palette scripts from ROM: " + e.getMessage());
            return false;
        }
    }

    private void loadPaletteScript(int pointerAddress, boolean second) throws java.io.IOException {
        var rom = services().rom();
        int displacement = rom.read16BitAddr(pointerAddress);
        int header = rom.read32BitAddr(pointerAddress + 4) & 0x00FF_FFFF;
        int destination = rom.read16BitAddr(header);
        int colorCount = Byte.toUnsignedInt(rom.readByte(header + 2)) + 1;
        int line = ((destination - NORMAL_PALETTE_RAM_BASE) >>> 5) & 3;
        int startColor = ((destination - NORMAL_PALETTE_RAM_BASE) & 0x1F) >>> 1;
        if (second) {
            palette2DataAddress = header + displacement;
            palette2Cursor = palette2DataAddress;
            palette2Line = line;
            palette2StartColor = startColor;
            palette2ColorCount = colorCount;
        } else {
            paletteDataAddress = header + displacement;
            paletteCursor = paletteDataAddress;
            paletteLine = line;
            paletteStartColor = startColor;
            paletteColorCount = colorCount;
        }
    }

    private int[] advancePaletteScript(boolean second) {
        int timer = second ? palette2Timer : paletteTimer;
        timer--;
        if (second) {
            palette2Timer = timer;
        } else {
            paletteTimer = timer;
        }
        if (timer >= 0) {
            return null;
        }
        try {
            var rom = services().rom();
            int cursor = second ? palette2Cursor : paletteCursor;
            int dataAddress = second ? palette2DataAddress : paletteDataAddress;
            int colorCount = second ? palette2ColorCount : paletteColorCount;
            int command = rom.read16BitAddr(cursor);
            if ((command & 0x8000) != 0) {
                if (command != 0xFFFC) {
                    throw new IllegalStateException(
                            String.format("Unsupported palette script command 0x%04X", command));
                }
                cursor = dataAddress;
            }
            int[] colors = new int[colorCount];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = rom.read16BitAddr(cursor);
                cursor += 2;
            }
            int delay = rom.read16BitAddr(cursor) & 0xFF;
            cursor += 2;
            if (second) {
                palette2Cursor = cursor;
                palette2Timer = delay;
            } else {
                paletteCursor = cursor;
                paletteTimer = delay;
            }
            return colors;
        } catch (java.io.IOException e) {
            paletteScriptsUnavailable = true;
            return null;
        }
    }

    private void applyRingPalette(int line, int startColor, int[] segaWords) {
        byte[] bytes = new byte[segaWords.length * 2];
        for (int i = 0; i < segaWords.length; i++) {
            bytes[i * 2] = (byte) (segaWords[i] >>> 8);
            bytes[i * 2 + 1] = (byte) segaWords[i];
        }
        S3kPaletteWriteSupport.applyContiguousPatch(
                services().paletteOwnershipRegistryOrNull(),
                services().currentLevel(),
                services().graphicsManager(),
                PALETTE_OWNER,
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                line,
                startColor,
                bytes);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || state == State.MARKED_DELETE) {
            return;
        }

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager != null) {
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.SS_ENTRY_RING);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), false, false);
                return;
            }
        }

        // Fallback wireframe if art not loaded
        appendFallbackRing(commands);
    }

    private void appendFallbackRing(List<GLCommand> commands) {
        int cx = spawn.x();
        int cy = spawn.y();
        int hw = 24, hh = 40;
        float r = 1.0f, g = 0.85f, b = 0.2f;
        addLine(commands, cx, cy - hh, cx + hw, cy, r, g, b);
        addLine(commands, cx + hw, cy, cx, cy + hh, r, g, b);
        addLine(commands, cx, cy + hh, cx - hw, cy, r, g, b);
        addLine(commands, cx - hw, cy, cx, cy - hh, r, g, b);
    }

    private void addLine(List<GLCommand> commands, int x1, int y1, int x2, int y2,
            float r, float g, float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, x2, y2, 0, 0));
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(RENDER_PRIORITY);
    }

    @Override
    public boolean isPersistent() {
        // Ring must persist while flash animation is playing
        return state == State.ENTERED;
    }

    @Override
    public boolean shouldStayActiveWhenRemembered() {
        return state == State.ENTERED;
    }

    // --- Package-visible accessors for testing ---

    /** Returns the current mapping frame index (for verifying animation state). */
    int getMappingFrame() {
        return mappingFrame;
    }

    /** Returns true if the ring is in formation (mapping_frame < 8, collision disabled). */
    boolean isForming() {
        return state == State.MAIN && mappingFrame < COLLISION_FRAME_THRESHOLD;
    }

    /** Returns true if the ring is in the main state (formation or idle). */
    boolean isMainState() {
        return state == State.MAIN;
    }

    /** ROM bit 6 at object offset $38: enables the Super Emerald palette script. */
    boolean isSuperEmeraldRing() {
        ensureInitialized();
        return superEmeraldRing;
    }

    int getPaletteStepForTest() {
        return paletteStep;
    }

    int[] getLastAppliedPaletteWordsForTest() {
        return lastAppliedPaletteWords == null ? null : lastAppliedPaletteWords.clone();
    }

}
