package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.BoxObjectInstance;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;

import com.openggf.audio.GameSound;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * HTZ Smashable Ground (Object 0x2F) - Breakable rock platform that progressively crumbles.
 *
 * Based on Obj2F in the Sonic 2 disassembly (s2.asm lines 48958-49150).
 *
 * Behavior:
 * - Acts as a solid platform that players can stand on
 * - Normal mode (subtype bit 7 = 0): Only breaks when player rolling AND standing on top
 * - Jump-breakable mode (subtype bit 7 = 1): A rolling landing ignores the solidity-plane gate
 * - When broken, spawns fragment objects that fly apart
 * - Players are released airborne when the ground breaks
 * - Plays SLOW_SMASH sound effect (0xCB)
 * - Awards points via chain bonus system (100/200/500/1000/10000 based on the shared chain)
 * - Uses level patterns (ArtTile_ArtKos_LevelArt), not dedicated object art
 */
public class SmashableGroundObjectInstance extends BoxObjectInstance
        implements SolidObjectProvider, RewindRecreatable, ObjectVisualFacing.NonDirectional {
    // Obj2F owns ground fragments and score popups; breakup velocities do not face a body.

    private static final Logger LOGGER = Logger.getLogger(SmashableGroundObjectInstance.class.getName());

    // From disassembly: move.b #$10,width_pixels(a0) (16 pixels half-width)
    private static final int HALF_WIDTH = 0x10;  // 16 pixels

    // Y-radius values per destruction state from Obj2F_Properties:
    // dc.b $24, 0  ; state 0: y_radius = 36, frame 0
    // dc.b $20, 2  ; state 2: y_radius = 32, frame 2
    // dc.b $18, 4  ; state 4: y_radius = 24, frame 4
    // dc.b $10, 6  ; state 6: y_radius = 16, frame 6
    // dc.b   8, 8  ; state 8: y_radius =  8, frame 8
    private static final int[] Y_RADIUS_BY_STATE = { 0x24, 0x20, 0x18, 0x10, 0x08 };
    private static final int[] FRAME_BY_STATE = { 0, 2, 4, 6, 8 };

    private static final int FRAGMENT_PIECE_MASK = 0x0F;
    private static final int FRAGMENT_FRAME_SHIFT = 4;
    private static final int FRAGMENT_FRAME_MASK = 0x0F;

    private int destructionState;  // 0, 2, 4, 6, or 8
    private int yRadius;
    private int mappingFrame;
    private boolean jumpBreakable;
    private boolean broken;
    private int savedChainCounter;  // objoff_38 - saved counter at frame start

    private boolean persistenceChecked;

    public SmashableGroundObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name, HALF_WIDTH, 0x20, 0.6f, 0.5f, 0.3f, false);

        // Parse subtype:
        // ROM: andi.w #$1E,d0 - extracts bits 1-4, forcing even values (0,2,4,6,8,...)
        // Bit 7 (0x80): jump-breakable mode
        int subtype = spawn.subtype();
        this.jumpBreakable = (subtype & 0x80) != 0;
        this.destructionState = (subtype & 0x1E);  // ROM mask: bits 1-4, even values only

        // Validate and clamp state
        int stateIndex = destructionState / 2;
        if (stateIndex < 0 || stateIndex >= Y_RADIUS_BY_STATE.length) {
            stateIndex = 0;
            this.destructionState = 0;
        }

        // ROM: move.b #$20,y_radius(a0) - always 32 pixels regardless of state
        // The Y_RADIUS_BY_STATE table values are read but immediately overwritten
        this.yRadius = 0x20;  // Always 32 pixels per ROM line 48671
        this.mappingFrame = FRAME_BY_STATE[stateIndex];
        this.broken = false;
        this.savedChainCounter = 0;
    }

    @Override
    public SmashableGroundObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SmashableGroundObjectInstance(ctx.spawn(), "SmashableGround");
    }

    private void ensureInitialized() {
        if (persistenceChecked) {
            return;
        }
        persistenceChecked = true;
        // Check persistence: if already broken (remembered), stay destroyed
        ObjectManager objectManager = services().objectManager();
        if (objectManager != null && objectManager.isRemembered(spawn)) {
            this.broken = true;
            setDestroyed(true);
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        ensureInitialized();
        if (broken) {
            return;
        }

        // ROM: move.w (Chain_Bonus_counter).w,objoff_38(a0)
        // Save global chain counter at frame start to restore when breaking
        // Obj2F_Main (s2.asm:49161-49164): save before SolidObject can reset it.
        // KiS2 Obj2F_Main (docs/kis2disasm/s2.asm:52395-52398) also saves before solids.
        savedChainCounter = Sonic2SmashableChain.policy(services()).word();
        var players = Sonic2SmashableChain.players(services());
        var animations = new java.util.IdentityHashMap<PlayableEntity, Integer>();
        for (var actor : players) animations.put(actor, actor.getAnimationId());
        // Obj2F_Main (s2.asm:49161-49180) runs both solids before testing
        // standing_mask. Preserve that boundary around the shared-word restore.
        var batch = services().solidExecution().resolveSolidNowAll();
        AbstractPlayableSprite breaker = null;
        for (var actor : players) {
            var result = batch.perPlayer().get(actor);
            if (result == null || !result.standingNow()) continue;
            if (animations.get(actor) == Sonic2AnimationIds.ROLL.id()
                    && (jumpBreakable || (((AbstractPlayableSprite) actor).getTopSolidBit() & 0xFF) == 0x0E)) {
                breaker = (AbstractPlayableSprite) actor; break;
            }
            actor.setTopSolidBit((byte) 0x0C); actor.setLrbSolidBit((byte) 0x0D);
        }
        if (breaker != null) {
            for (var actor : players) {
                var result = batch.perPlayer().get(actor);
                if (result != null && result.standingNow())
                    Sonic2SmashableChain.release(services(), (AbstractPlayableSprite) actor,
                            animations.get(actor) == Sonic2AnimationIds.ROLL.id(), false);
            }
            breakOneLayer(breaker, vIntRunCount);
        }


    }

    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }

    @Override
    public SolidObjectParams getSolidParams() {
        // From disassembly: width_pixels = $10, use halfWidth + 11 for x check
        return SolidObjectParams.of(HALF_WIDTH + 11, yRadius, yRadius + 1);
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // Block is not solid once broken
        return !broken;
    }

    private void breakOneLayer(AbstractPlayableSprite player, int vIntRunCount) {
        if (broken) {
            return;
        }

        // Mark as broken immediately - the entire object is destroyed
        broken = true;

        // ROM: move.w objoff_38(a0),(Chain_Bonus_counter).w
        // Restore saved chain counter before processing break
        // loc_234A4 (s2.asm:49277-49292): restore the saved word before breaking.
        // KiS2 loc_234A4 (docs/kis2disasm/s2.asm:52511-52526) restores before points too.
        Sonic2SmashableChain.policy(services()).restoreWord(savedChainCounter);

        ObjectManager objectManager = services().objectManager();
        // Spawn fragments for ALL pieces of the current frame
        // In the ROM, BreakObjectToPieces creates fragments for every piece
        var firstFragment = spawnFragments();

        // Play slow smash sound effect (played by BreakObjectToPieces in ROM)
        services().playSfx(GameSound.SLOW_SMASH);

        // Award chain bonus points
        awardChainBonus();

        // Mark as broken in persistence table
        ObjectLifetimeOps.markSpawnRemembered(objectManager, spawn);

        // loc_234A4 falls through into Obj2F_Fragment after the popup attempt
        // (s2.asm:49277-49300); a replacement at the current slot is not rescheduled.
        Sonic2BreakIntoPieces.runFirstFragment(services(), firstFragment, vIntRunCount, player);
        setDestroyed(true);
        LOGGER.fine(() -> String.format("Smashable ground at (%d,%d) destroyed (state %d)",
                spawn.x(), spawn.y(), destructionState));
    }

    private Sonic2BreakPieceObjectInstance spawnFragments() {
        // loc_234A4 increments mapping_frame, then indexes its velocity table
        // by the OLD frame *4 (s2.asm:49277-49292). No renderer owns admission.
        int oldFrame = mappingFrame;
        mappingFrame = (mappingFrame + 1) & 0xFF;
        var pieces = Sonic2BreakIntoPieces.readPieces(services(), 0x236FA,
                mappingFrame, 0x234F2 + oldFrame * 4);
        boolean onScreen = isWithinSolidContactBounds();
        return Sonic2BreakIntoPieces.replaceParent(this, services(), pieces.size(), index ->
                new Sonic2BreakPieceObjectInstance(spawn, pieces.get(index),
                        Sonic2ObjectArtKeys.SMASHABLE_GROUND, 0x18, HALF_WIDTH,
                        index == 0 ? yRadius : 0, true, true, 4, onScreen));
    }

    private void awardChainBonus() {
        Sonic2SmashableChain.award(services(), spawn.x(), spawn.y());
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (broken) {
            return;
        }

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            super.appendRenderCommands(commands);
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.SMASHABLE_GROUND);
        if (renderer == null || !renderer.isReady()) {
            super.appendRenderCommands(commands);
            return;
        }

        // Obj2F_Init selects the even intact ROM frame (s2.asm:49151-49157).
        int frameIndex = mappingFrame;
        renderer.drawFrameIndex(frameIndex, spawn.x(), spawn.y(), false, false);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    protected boolean usesCustomRenderHeight() {
        // Obj2F_Init: move.b #$20,y_radius / bset #render_flags.explicit_height
        // (s2.asm:49158-49159); BuildSprites tests y_radius (s2.asm:30580-30593).
        return true;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return yRadius;
    }

    @Override
    protected int getHalfWidth() {
        return HALF_WIDTH;
    }

    @Override
    protected int getHalfHeight() {
        return yRadius;
    }

    /**
     * Fragment object that flies apart when the smashable ground breaks.
     * These are simple falling objects with initial velocity that despawn when off-screen.
     */
    public static class SmashableGroundFragmentInstance extends AbstractObjectInstance
            implements RewindRecreatable {

        private static final int GRAVITY = 0x18;  // From disassembly: addi.w #$18,y_vel(a0)

        private int currentX;
        private int currentY;
        private int subX;  // 8.8 fixed point
        private int subY;  // 8.8 fixed point
        private int velX;  // 8.8 fixed point
        private int velY;  // 8.8 fixed point
        private int frameIndex;
        private int pieceIndex;
        private final SpriteMappingPiece piece;
        private final PatternSpriteRenderer renderer;
        private final List<SpriteMappingPiece> pieceList;

        public SmashableGroundFragmentInstance(int x, int y, int velX, int velY,
                                               SpriteMappingPiece piece, PatternSpriteRenderer renderer) {
            this(x, y, velX, velY, piece, renderer, 0, 0);
        }

        public SmashableGroundFragmentInstance(int x, int y, int velX, int velY,
                                               SpriteMappingPiece piece, PatternSpriteRenderer renderer,
                                               int frameIndex, int pieceIndex) {
            this(fragmentSpawn(x, y, frameIndex, pieceIndex), velX, velY, piece, renderer,
                    frameIndex, pieceIndex);
        }

        public SmashableGroundFragmentInstance(ObjectSpawn spawn) {
            this(spawn, null);
        }

        private SmashableGroundFragmentInstance(ObjectSpawn spawn, ObjectRenderManager renderManager) {
            this(spawn, 0, 0, fragmentPiece(renderManager, decodeFrameIndex(spawn), decodePieceIndex(spawn)),
                    fragmentRenderer(renderManager), decodeFrameIndex(spawn), decodePieceIndex(spawn));
        }

        private SmashableGroundFragmentInstance(ObjectSpawn spawn, int velX, int velY,
                                                SpriteMappingPiece piece, PatternSpriteRenderer renderer,
                                                int frameIndex, int pieceIndex) {
            super(spawn, "SmashFragment");
            this.currentX = spawn.x();
            this.currentY = spawn.y();
            this.subX = spawn.x() << 8;
            this.subY = spawn.y() << 8;
            this.velX = velX;
            this.velY = velY;
            this.frameIndex = frameIndex;
            this.pieceIndex = pieceIndex;
            this.piece = piece;
            this.renderer = renderer;
            this.pieceList = piece != null ? List.of(piece) : List.of();
        }

        @Override
        public SmashableGroundFragmentInstance recreateForRewind(RewindRecreateContext ctx) {
            ObjectRenderManager renderManager = ctx.objectServices() != null
                    ? ctx.objectServices().renderManager()
                    : null;
            return new SmashableGroundFragmentInstance(ctx.spawn(), renderManager);
        }

        private static ObjectSpawn fragmentSpawn(int x, int y, int frameIndex, int pieceIndex) {
            int subtype = (pieceIndex & FRAGMENT_PIECE_MASK)
                    | ((frameIndex & FRAGMENT_FRAME_MASK) << FRAGMENT_FRAME_SHIFT);
            return new ObjectSpawn(x, y, 0x2F, subtype, 0, false, 0);
        }

        private static int decodePieceIndex(ObjectSpawn spawn) {
            return spawn.subtype() & FRAGMENT_PIECE_MASK;
        }

        private static int decodeFrameIndex(ObjectSpawn spawn) {
            return (spawn.subtype() >> FRAGMENT_FRAME_SHIFT) & FRAGMENT_FRAME_MASK;
        }

        private static PatternSpriteRenderer fragmentRenderer(ObjectRenderManager renderManager) {
            return renderManager != null
                    ? renderManager.getRenderer(Sonic2ObjectArtKeys.SMASHABLE_GROUND)
                    : null;
        }

        private static SpriteMappingPiece fragmentPiece(ObjectRenderManager renderManager, int frameIndex,
                                                        int pieceIndex) {
            if (renderManager == null) {
                return null;
            }
            ObjectSpriteSheet sheet = renderManager.getSheet(Sonic2ObjectArtKeys.SMASHABLE_GROUND);
            if (sheet == null || frameIndex < 0 || frameIndex >= sheet.getFrameCount()) {
                return null;
            }
            SpriteMappingFrame frame = sheet.getFrame(frameIndex);
            if (frame == null || frame.pieces() == null || pieceIndex < 0
                    || pieceIndex >= frame.pieces().size()) {
                return null;
            }
            return frame.pieces().get(pieceIndex);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity playerEntity) {
            AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
            if (isDestroyed()) {
                return;
            }

            // Apply gravity
            velY += GRAVITY;

            // Update position (8.8 fixed point)
            subX += velX;
            subY += velY;
            currentX = subX >> 8;
            currentY = subY >> 8;

            // Check if off-screen (destroy if too far below camera)
            int cameraY = services().camera().getY();
            int screenHeight = 224;  // Standard MD screen height
            if (currentY > cameraY + screenHeight + 64) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed() || renderer == null || !renderer.isReady() || pieceList.isEmpty()) {
                return;
            }
            renderer.drawPieces(pieceList, currentX, currentY, false, false);
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(4);
        }
    }
}
