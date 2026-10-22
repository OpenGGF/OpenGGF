package com.openggf.game.sonic1.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/** Object $48: one anchor, four links, and one harmful ball, each in its own native SST slot. */
public class GHZBossWreckingBall extends AbstractBossChild implements RewindRecreatable {
    // GBall_PosData, shipped S1 REV01 ROM $1835E. Includes the anchor itself.
    private static final int[] CHAIN_OFFSETS = {0, 0x10, 0x20, 0x30, 0x40, 0x60};
    private static final int TOTAL_ELEMENTS = 6;
    private boolean initialized;
    private int elementCount = 1;
    private NativeComponent[] components = new NativeComponent[TOTAL_ELEMENTS - 1];
    private final int[] chainExtension = new int[TOTAL_ELEMENTS];
    private boolean chainFullyExtended;
    private int verticalOffset;
    private int angle = 0x4080;
    private int swingParam = -0x200;
    private boolean swingForward;
    private int anchorX;
    private int anchorY;
    private int anchorFrame;
    private int anchorFrameCounter;
    private boolean parentDefeated;
    private boolean displayThisPass;
    private int explosionFrame;
    private int explosionDuration = -1;

    public GHZBossWreckingBall(AbstractBossInstance parent) {
        super(parent, "GHZBallAnchor", 6, Sonic1ObjectIds.BOSS_BALL);
        // BGHZ_MakeBall copies the ship's scratch BossX/BossY words, before
        // BGHZ_ShipUpdate publishes this pass's sine-displaced display position.
        setPosition((parent.getState().xFixed >>> 16) & 0xFFFF,
                (parent.getState().yFixed >>> 16) & 0xFFFF);
        updateDynamicSpawn();
    }

    @Override
    protected boolean tracksViaChildComponents() { return false; }

    @Override
    public int getOnScreenHalfWidth() { return parentDefeated && explosionDuration >= 0 ? 12 : 8; }

    @Override
    protected void onRemovedFromObjectManager() {
        super.onRemovedFromObjectManager();
        if (parent instanceof Sonic1GHZBossInstance boss) boss.forgetWreckingBall(this);
    }

    @Override
    public ObjectSpawn getSpawn() {
        ObjectSpawn nativeSpawn = super.getSpawn();
        return parentDefeated ? explosionSpawn(nativeSpawn) : nativeSpawn;
    }

    private static ObjectSpawn explosionSpawn(ObjectSpawn original) {
        return new ObjectSpawn(original.x(), original.y(), Sonic1ObjectIds.EXPLOSION,
                original.subtype(), original.renderFlags(), original.respawnTracked(), original.rawYWord());
    }

    @Override
    public GHZBossWreckingBall recreateForRewind(RewindRecreateContext ctx) {
        Sonic1GHZBossInstance boss = findBoss(ctx);
        if (boss == null) return null;
        GHZBossWreckingBall restored = new GHZBossWreckingBall(boss);
        boss.adoptWreckingBallForRewind(restored);
        return restored;
    }

    private static Sonic1GHZBossInstance findBoss(RewindRecreateContext ctx) {
        if (ctx == null || ctx.objectServices() == null || ctx.objectServices().objectManager() == null) return null;
        for (ObjectInstance object : ctx.objectServices().objectManager().getActiveObjects()) {
            if (object instanceof Sonic1GHZBossInstance boss && !boss.isDestroyed()) return boss;
        }
        return null;
    }

    /** GBall_Main returns without running GBall_Base; construction/reconstruction allocates nothing. */
    private void initializeComponents() {
        initialized = true;
        ObjectManager manager = services().objectManager();
        if (manager == null || getSlotIndex() < 0) return;
        for (int index = 1; index < TOTAL_ELEMENTS; index++) {
            NativeComponent component = new NativeComponent(parent, index, currentX, currentY);
            // FindNextFreeObj restarts at the root for each child, scanning live occupancy.
            manager.addDynamicObjectAfterSlot(component, getSlotIndex());
            if (component.getSlotIndex() < 0 || component.isDestroyed()) {
                // FixBugs=0: exhausted FindNextFreeObj leaves a1=v_lvlobjend.
                // GBall_MakeBall writes to non-object (SMPS) RAM, NOT the last
                // successful link. No live ball exists after partial allocation.
                break;
            }
            components[index - 1] = component;
            elementCount++;
        }
        if (elementCount == TOTAL_ELEMENTS) {
            components[TOTAL_ELEMENTS - 2].makeBall();
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // ObjectManager dispatches this in its own slot, even below the ship.
        if (!beginUpdate(vIntRunCount)) return;
        if (!initialized) {
            initializeComponents();
            return;
        }
        displayThisPass = true;
        if (parentDefeated) {
            updateExplosion();
            return;
        }
        if (chainFullyExtended) {
            updateSwing();
        } else {
            // GBall_Base tests the last distance AFTER increment. Routine 4
            // dispatches next pass, and requires ship routine EXACTLY 6.
            for (int index = 0; index < elementCount; index++) {
                if (chainExtension[index] != CHAIN_OFFSETS[index]) {
                    chainExtension[index] = (chainExtension[index] + 1) & 0xFF;
                }
            }
            if (chainExtension[elementCount - 1] == CHAIN_OFFSETS[elementCount - 1]
                    && parent.getState().routineSecondary == 6) chainFullyExtended = true;
            if (verticalOffset != 0x20) verticalOffset = (verticalOffset + 1) & 0xFFFF;
        }
        // GBall_UpdateBase: obAniFrame +=32, toggle mapping on carry (every eight passes).
        anchorFrameCounter += 0x20;
        if (anchorFrameCounter > 0xFF) {
            anchorFrameCounter &= 0xFF;
            anchorFrame ^= 1;
        }
        anchorX = parent.getX();
        anchorY = (parent.getY() + verticalOffset) & 0xFFFF;
        parentDefeated = (parent.getState().renderFlags & 0x80) != 0;
        calculateChainPositions();
        updateDynamicSpawn();
    }

    private void updateSwing() {
        // GBall_Move uses signed word speed, word angle and equality at the limits.
        swingParam = (short) (swingParam + (swingForward ? -8 : 8));
        angle = (angle + swingParam) & 0xFFFF;
        if (swingParam == (swingForward ? -0x200 : 0x200)) swingForward = !swingForward;
    }

    private void calculateChainPositions() {
        int sine = TrigLookupTable.sinHex((angle >>> 8) & 0xFF);
        int cosine = TrigLookupTable.cosHex((angle >>> 8) & 0xFF);
        currentX = anchorX;
        currentY = anchorY;
        for (int index = 1; index < elementCount; index++) {
            NativeComponent component = components[index - 1];
            if (component == null || component.isDestroyed()) continue;
            component.positionFromAnchor(chainExtension[index],
                    (anchorX + ((cosine * chainExtension[index]) >> 8)) & 0xFFFF,
                    (anchorY + ((sine * chainExtension[index]) >> 8)) & 0xFFFF);
        }
    }

    /** Obj3F converts in place, preserving this same SST slot for five eight-pass frames. */
    private void updateExplosion() {
        if (explosionDuration < 0) {
            explosionDuration = 7;
            displayThisPass = false;
            services().playSfx(Sonic1Sfx.BOSS_EXPLOSION.id);
            return; // Expl_Main jumps to QueueSound2; no animation fallthrough.
        }
        if (--explosionDuration < 0) {
            explosionDuration = 7;
            if (++explosionFrame == 5) setDestroyed(true);
        }
    }

    @Override
    public int getPriorityBucket() { return parentDefeated && explosionDuration >= 0 ? 1 : 6; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || !displayThisPass || services().renderManager() == null) return;
        if (parentDefeated && explosionDuration >= 0) {
            drawExplosion(services().renderManager(), currentX, currentY, explosionFrame);
            return;
        }
        PatternSpriteRenderer renderer = services().renderManager().getRenderer(ObjectArtKeys.BOSS_WEAPONS);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(anchorFrame, currentX, currentY,
                    (parent.getState().renderFlags & 1) != 0, false);
        }
    }

    private static void drawExplosion(ObjectRenderManager renderManager, int x, int y, int frame) {
        var renderer = renderManager.getBossExplosionRenderer();
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(frame, x, y, false, false);
    }

    /** Routine 6 link or routine 8 ball; the ship reference survives anchor deletion. */
    public static final class NativeComponent extends AbstractBossChild
            implements TouchResponseProvider, RewindRecreatable {
        private int chainIndex;
        private boolean ball;
        private int distance;
        private int ballFrame = 1;
        private boolean parentDefeated;
        private boolean convertedToExplosion;
        private boolean displayThisPass;
        private int explosionFrame;
        private int explosionDuration = -1;

        private NativeComponent(AbstractBossInstance parent, int index, int x, int y) {
            super(parent, "GHZBallComponent", 6, Sonic1ObjectIds.BOSS_BALL);
            chainIndex = index;
            setPosition(x, y);
            updateDynamicSpawn();
        }

        private void makeBall() {
            ball = true;
            priority = 5;
        }

        private void positionFromAnchor(int radius, int x, int y) {
            distance = radius;
            setPosition(x, y);
            updateDynamicSpawn();
        }

        @Override
        protected boolean tracksViaChildComponents() { return false; }

        @Override
        public int getOnScreenHalfWidth() { return convertedToExplosion && explosionDuration >= 0 ? 12 : 8; }

        @Override
        public ObjectSpawn getSpawn() {
            ObjectSpawn nativeSpawn = super.getSpawn();
            return convertedToExplosion ? explosionSpawn(nativeSpawn) : nativeSpawn;
        }

        @Override
        public NativeComponent recreateForRewind(RewindRecreateContext ctx) {
            Sonic1GHZBossInstance boss = findBoss(ctx);
            return boss == null ? null : new NativeComponent(boss, 0, ctx.spawn().x(), ctx.spawn().y());
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (!beginUpdate(vIntRunCount)) return;
            displayThisPass = true;
            if (convertedToExplosion) {
                if (explosionDuration < 0) {
                    explosionDuration = 7;
                    displayThisPass = false;
                    services().playSfx(Sonic1Sfx.BOSS_EXPLOSION.id);
                    return;
                }
                if (--explosionDuration < 0) {
                    explosionDuration = 7;
                    if (++explosionFrame == 5) setDestroyed(true);
                }
                return;
            }
            if (ball) ballFrame = ballFrame == 0 ? 1 : 0;
            if ((parent.getState().renderFlags & 0x80) == 0) return;
            parentDefeated = true;
            if (!ball) {
                convertedToExplosion = true;
                return;
            }
            if ((vIntRunCount & 7) == 0) spawnDefeatExplosion();
            // BGHZ_BossGenericTimer aliases GBall_LinkDist: early defeat uses
            // the CURRENT radius, not a freshly seeded constant $60.
            distance = (distance - 1) & 0xFF;
            if ((byte) distance < 0) convertedToExplosion = true;
        }

        private void spawnDefeatExplosion() {
            ObjectManager manager = services().objectManager();
            if (manager == null) return;
            // BossDefeated allocates BEFORE RandomNumber; full RAM consumes no RNG.
            manager.createDynamicObjectWithReservedSlot(() -> {
                int random = services().rng().nextWord();
                int x = (currentX + ((random & 0xFF) >> 2) - 0x20) & 0xFFFF;
                int y = (currentY + (((random >>> 8) & 0xFF) >> 3)) & 0xFFFF;
                return new S1BossExplosion(x, y, services().renderManager());
            });
        }

        @Override
        public int getCollisionFlags() { return ball && !parentDefeated && !isDestroyed() ? 0x81 : 0; }

        @Override
        public int getCollisionProperty() { return 0; }

        @Override
        public int getPriorityBucket() { return convertedToExplosion && explosionDuration >= 0 ? 1 : ball ? 5 : 6; }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed() || !displayThisPass || services().renderManager() == null) return;
            if (convertedToExplosion && explosionDuration >= 0) {
                drawExplosion(services().renderManager(), currentX, currentY, explosionFrame);
                return;
            }
            PatternSpriteRenderer renderer = services().renderManager().getRenderer(
                    ball ? ObjectArtKeys.BOSS_BALL : ObjectArtKeys.SWING_GHZ);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(ball ? ballFrame : 1, currentX, currentY, false, false, ball ? -1 : 0);
            }
        }
    }

}
