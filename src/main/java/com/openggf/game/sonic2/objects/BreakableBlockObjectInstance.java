package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.BoxObjectInstance;

import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.PlayableEntity;

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
import java.util.Objects;
import java.util.logging.Logger;

/**
 * CPZ Breakable Block (Object 0x32) - Metal blocks that shatter when Sonic rolls into them.
 *
 * Based on Obj32 in the Sonic 2 disassembly (s2.asm lines 49153-49344).
 *
 * Behavior:
 * - Acts as a solid platform that players can stand on
 * - Only breaks when a player standing on it is rolling (spin attack)
 * - When broken, the ROM mapping supplies four CPZ or six HTZ pieces
 * - Player bounces upward when block breaks
 * - Plays SLOW_SMASH sound effect (0xCB)
 */
public class BreakableBlockObjectInstance extends BoxObjectInstance
        implements SolidObjectProvider, RewindRecreatable, ObjectVisualFacing.NonDirectional, com.openggf.level.objects.homing.HomingTargetSource {
    // Obj32 owns a block, debris and score popup; debris velocity is not body facing.

    private static final Logger LOGGER = Logger.getLogger(BreakableBlockObjectInstance.class.getName());

    // From disassembly:
    // - CPZ: move.b #$10,width_pixels(a0) (32px wide)
    // - HTZ: move.b #$18,width_pixels(a0) (48px wide)
    private static final int CPZ_HALF_WIDTH = 0x10;  // 16 pixels
    private static final int HTZ_HALF_WIDTH = 0x18;  // 24 pixels
    private static final int HALF_HEIGHT = 0x10;     // 16 pixels

    // Mapping frame indices
    private static final int FRAME_INTACT = 0;

    private int halfWidth;
    private boolean broken;
    private boolean initialized;

    public BreakableBlockObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name, resolveHalfWidth(), HALF_HEIGHT, 0.6f, 0.6f, 0.8f, false);
        this.halfWidth = resolveHalfWidth();
        this.broken = false;
    }

    @Override
    public BreakableBlockObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new BreakableBlockObjectInstance(ctx.spawn(), getName());
    }

    private int savedChainCounter; // objoff_38, retained across the solid call and rewind

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;

        // Check persistence: if already broken, stay broken
        ObjectManager objectManager = services().objectManager();
        if (objectManager != null && objectManager.isRemembered(spawn)) {
            this.broken = true;
            setDestroyed(true);
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        ensureInitialized();
        if (broken) return;
        // Obj32_Main (s2.asm:49354-49358): save before the solid landing reset.
        // KiS2 Obj32_Main (docs/kis2disasm/s2.asm:52585-52588) has the same save.
        savedChainCounter = Sonic2SmashableChain.policy(services()).word();
        var players = Sonic2SmashableChain.players(services());
        var animations = new java.util.IdentityHashMap<PlayableEntity, Integer>();
        for (var actor : players) animations.put(actor, actor.getAnimationId());
        // Obj32_Main (s2.asm:49354-49368) returns from BOTH character solids
        // before Obj32_SupportingSomeone chooses a break. A callback during P1
        // would let P2's later landing reset the newly awarded chain word.
        var batch = services().solidExecution().resolveSolidNowAll();
        AbstractPlayableSprite breaker = null;
        for (var actor : players) {
            var result = batch.perPlayer().get(actor);
            if (result != null && result.standingNow()
                    && animations.get(actor) == Sonic2AnimationIds.ROLL.id()) {
                breaker = (AbstractPlayableSprite) actor; break;
            }
        }
        if (breaker == null) return;
        for (var actor : players) {
            var result = batch.perPlayer().get(actor);
            if (result != null && result.standingNow())
                Sonic2SmashableChain.release(services(), (AbstractPlayableSprite) actor,
                        animations.get(actor) == Sonic2AnimationIds.ROLL.id(), true);
        }
        destroyBlock(vIntRunCount);
    }

    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }

    @Override
    public SolidObjectParams getSolidParams() {
        // From disassembly: width_pixels = $10 (CPZ) / $18 (HTZ)
        // SolidObject routine uses: halfWidth + 11 for x check, halfHeight for y check
        return SolidObjectParams.of(halfWidth + 11, HALF_HEIGHT, HALF_HEIGHT + 1);
    }

    @Override
    public boolean usesInclusiveRightEdge() {
        // Obj32 calls the ordinary SolidObject helper. Its X gate branches out
        // only on `bhi`, so relX == 2*d1 remains a zero-distance side contact
        // (docs/s2disasm/s2.asm:35347-35354). HTZ1 reaches that exact right
        // edge on the roll-landing frame; retaining the contact publishes the
        // native Status_Push bit for the following SAnim_Push timer gate.
        return true;
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        // Block is not solid once broken. ROM keeps the block fully solid before it
        // breaks (Obj32_Main always invokes SolidObject); a rolling player moving
        // upward hits the underside as a ceiling collision, not a break.
        return !broken;
    }

    private void destroyBlock(int vIntRunCount) {
        if (broken) {
            return;
        }

        broken = true;
        // Obj32_Destroy (s2.asm:49426-49431) restores before fragments/points.
        // KiS2 Obj32_Destroy (docs/kis2disasm/s2.asm:52657-52662) has the same restore.
        Sonic2SmashableChain.policy(services()).restoreWord(savedChainCounter);
        // Mark as broken in persistence table (stays broken on respawn/revisit)
        ObjectManager objectManager = services().objectManager();
        ObjectLifetimeOps.markSpawnRemembered(objectManager, spawn);

        // Spawn fragment objects
        var firstFragment = spawnFragments();

        // Play slow smash sound effect
        services().playSfx(GameSound.SLOW_SMASH);

        Sonic2SmashableChain.award(services(), spawn.x(), spawn.y());

        // Obj32_Destroy falls through into the current-slot fragment
        // after its popup attempt (s2.asm:49426-49439).
        Sonic2BreakIntoPieces.runFirstFragment(services(), firstFragment, vIntRunCount, null);

        // Mark this object as destroyed so it stops rendering/updating
        setDestroyed(true);

        LOGGER.fine(() -> String.format("Breakable block at (%d,%d) broken by player", spawn.x(), spawn.y()));
    }

    private Sonic2BreakPieceObjectInstance spawnFragments() {
        // Obj32_Init selects CPZ only; every other zone retains the HTZ mapping
        // and velocity table (s2.asm:49337-49352).
        boolean cpz = halfWidth == CPZ_HALF_WIDTH;
        var pieces = Sonic2BreakIntoPieces.readPieces(services(), cpz ? 0x23886 : 0x23852,
                0, cpz ? 0x23698 : 0x23680);
        boolean onScreen = isWithinSolidContactBounds();
        return Sonic2BreakIntoPieces.replaceParent(this, services(), pieces.size(), index ->
                new Sonic2BreakPieceObjectInstance(spawn, pieces.get(index),
                        Sonic2ObjectArtKeys.BREAKABLE_BLOCK, 0x18, halfWidth, 0,
                        false, false, 4, onScreen));
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

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.BREAKABLE_BLOCK);
        if (renderer == null || !renderer.isReady()) {
            super.appendRenderCommands(commands);
            return;
        }

        renderer.drawFrameIndex(FRAME_INTACT, spawn.x(), spawn.y(), false, false);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public int getOnScreenHalfWidth() {
        // Obj32_Init supplies width_pixels (s2.asm:49337-49352).
        // BreakObjectToPieces copies it to every piece (s2.asm:29743-29758).
        return halfWidth;
    }

    @Override
    protected int getHalfWidth() {
        return halfWidth;
    }

    @Override
    protected int getHalfHeight() {
        return HALF_HEIGHT;
    }

    private static int resolveHalfWidth() {
        // Use construction context ThreadLocal since this is called during super() args
        var ctx = constructionContext();
        if (ctx == null || ctx.currentLevel() == null) {
            return CPZ_HALF_WIDTH;
        }
        int zoneId = ctx.currentLevel().getZoneIndex();
        return zoneId == Sonic2Constants.ZONE_CHEMICAL_PLANT
                ? CPZ_HALF_WIDTH
                : HTZ_HALF_WIDTH;
    }

    /**
     * Inner class for the fragment objects that fly apart when the block breaks.
     * These are simple falling objects with initial velocity that despawn when off-screen.
     */
    public static class BreakableBlockFragmentInstance extends AbstractObjectInstance implements RewindRecreatable {

        private static final int GRAVITY = 0x18;  // From disassembly: addi.w #$18,y_vel(a0)

        private int currentX;
        private int currentY;
        private int subX;  // 8.8 fixed point
        private int subY;  // 8.8 fixed point
        private int velX;  // 8.8 fixed point
        private int velY;  // 8.8 fixed point
        private FragmentArt fragmentArt;
        private int frameIndex;
        private final ObjectRenderManager renderManager;

        /** The art key identifies the current ROM-backed renderer for this immutable piece. */
        public BreakableBlockFragmentInstance(int x, int y, int velX, int velY, SpriteMappingPiece piece,
                                              String artKey) {
            super(new ObjectSpawn(x, y, 0x32, 0, 0, false, 0), "BlockFragment");
            this.currentX = x;
            this.currentY = y;
            this.subX = x << 8;
            this.subY = y << 8;
            this.velX = velX;
            this.velY = velY;
            this.fragmentArt = piece != null
                    ? new FragmentArt(Objects.requireNonNull(artKey, "artKey"), piece) : null;
            this.frameIndex = -1;
            this.renderManager = null;
        }

        public BreakableBlockFragmentInstance(int x, int y, int velX, int velY, int frameIndex,
                                              ObjectRenderManager renderManager) {
            super(new ObjectSpawn(x, y, 0x32, 0, 0, false, 0), "BlockFragment");
            this.currentX = x;
            this.currentY = y;
            this.subX = x << 8;
            this.subY = y << 8;
            this.velX = velX;
            this.velY = velY;
            this.fragmentArt = renderManager != null
                    ? new FragmentArt(Sonic2ObjectArtKeys.BREAKABLE_BLOCK, null) : null;
            this.frameIndex = frameIndex;
            this.renderManager = renderManager;
        }

        public BreakableBlockFragmentInstance(int x, int y, int velX, int velY) {
            this(x, y, velX, velY, 0, null);
        }

        @Override
        public BreakableBlockFragmentInstance recreateForRewind(RewindRecreateContext ctx) {
            ObjectRenderManager manager = ctx.objectServices() != null
                    ? ctx.objectServices().renderManager()
                    : null;
            return new BreakableBlockFragmentInstance(ctx.spawn().x(), ctx.spawn().y(), 0, 0, 0, manager);
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
            if (currentY > cameraY + screenHeight + 32) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }

            if (fragmentArt == null) {
                return;
            }
            ObjectRenderManager manager = renderManager != null ? renderManager : services().renderManager();
            PatternSpriteRenderer renderer = manager != null ? manager.getRenderer(fragmentArt.artKey()) : null;
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            if (fragmentArt.piece() != null) {
                renderer.drawPieces(List.of(fragmentArt.piece()), currentX, currentY, false, false);
            } else {
                renderer.drawFrameIndex(frameIndex, currentX, currentY, false, false);
            }
        }

        private record FragmentArt(String artKey, SpriteMappingPiece piece) {}

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(4);
        }
    }
    /** Homing: a CPZ block broken by landing on it in a roll, so the attack aims at its top. */
    @Override public com.openggf.level.objects.homing.HomingTarget homingTarget(com.openggf.game.PlayableEntity player) {
        return broken ? null : com.openggf.level.objects.HomingTargets.onTop(this,
                com.openggf.level.objects.homing.HomingTargetKind.BREAKABLE, player);
    }
}
