package com.openggf.game.sonic1.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.objects.boss.BossExplosionObjectInstance;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Base class for the 5 standard Sonic 1 Eggman-ship bosses (GHZ, LZ, MZ, SLZ, SYZ).
 *
 * These bosses share the same Eggman ship sprite with independently dispatched face and flame parts,
 * and use identical helper methods for fixed-point movement, on-screen checks,
 * native component allocation, defeat explosion spawning, and camera-expand escape.
 *
 * The FZ boss is NOT included — it uses a completely different sprite set (Map_SEgg)
 * and has unique combat mechanics.
 *
 * Extracted methods (byte-identical across all 5 bosses in the ROM):
 * <ul>
 *   <li>{@link #bossMove()} — BossMove subroutine (applies velocity to fixed-point position)</li>
 *   <li>{@link #isBossOnScreen()} — camera-relative bounds check</li>
 *   <li>{@link #renderEggmanShip()} — ship body rendering</li>
 *   <li>{@link #runDefeatExplosions(int)} — countdown timer + explosion spawning</li>
 *   <li>{@link #runCameraExpandEscape(int)} — camera unlock + boundary expansion + off-screen destroy</li>
 * </ul>
 */
public abstract class AbstractS1EggmanBossInstance extends AbstractBossInstance {

    /** Face animation state — set by subclass in updateFaceAnimation(). */
    protected int faceAnim;

    /** Flame animation state — set by subclass in updateFlameAnimation(). */
    protected int flameAnim;

    private boolean nativeComponentsInitialized;
    private boolean shipRenderedLastPass;
    private boolean shipDisplayedThisPass;

    protected AbstractS1EggmanBossInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    /**
     * The S1 Eggman-ship bosses are never unloaded by the generic {@code out_of_range}
     * window: their main routine ends with {@code jmp (DisplaySprite).l} (e.g.
     * docs/s1disasm/_incObj/75, 76 Boss - SYZ Main and Blocks.asm:84), which only
     * displays the sprite — it never calls {@code MarkObjGone} / the {@code out_of_range}
     * delete macro. The boss owns its entire lifecycle: it self-deletes from its escape
     * routine once {@code v_limitright2} reaches {@code boss_*_end} and it has left the
     * screen ({@link #runCameraExpandEscape(int)}). Without this, the boss is culled
     * off-screen mid-escape and stops running {@code addq.w #2,(v_limitright2)}, freezing
     * the right-boundary camera scroll short of {@code boss_*_end} (SYZ3 trace: camera
     * stuck at 0x2CA4 instead of expanding to boss_syz_end 0x2D40). The GHZ boss happens
     * to reach its end boundary just before the cull window, so it was already correct;
     * this makes all five bosses match the ROM no-cull behavior.
     */
    @Override
    public boolean isPersistent() {
        return true;
    }

    // ========================================================================
    // Shared utility methods — byte-identical across all 5 S1 Eggman bosses
    // ========================================================================

    /**
     * BossMove subroutine — applies velocity to fixed-point position.
     * ROM: sonic.asm:6692 (BossMove)
     * <pre>
     *   objoff_30 += sign_extend(obVelX) << 8
     *   objoff_38 += sign_extend(obVelY) << 8
     * </pre>
     */
    protected void bossMove() {
        state.xFixed += (state.xVel << 8);
        state.yFixed += (state.yVel << 8);
    }

    /** Native escape routines read the previous post-camera BuildSprites bit. */
    protected boolean isBossOnScreen() {
        return shipRenderedLastPass;
    }

    @Override
    public void refreshPostCameraRenderState() {
        if (!shipDisplayedThisPass) return;
        shipDisplayedThisPass = false;
        Camera camera = services().camera();
        int sx = (short) (state.x - camera.getX());
        int sy = (short) (state.y - camera.getY());
        shipRenderedLastPass = sx >= -32 && sx < camera.getWidth() + 32
                && sy >= -32 && sy < camera.getHeight() + 32;
    }

    @Override
    public int getCollisionFlags() {
        return nativeComponentsInitialized ? super.getCollisionFlags() : 0;
    }

    /** Native tables allocate the face, flame, then optional weapon during routine 0. */
    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!nativeComponentsInitialized) {
            nativeComponentsInitialized = true;
            if (spawnVisualComponent(S1EggmanVisualComponent.FACE)
                    && spawnVisualComponent(S1EggmanVisualComponent.FLAME)) {
                if (getAccessoryMappingFrame() >= 0) {
                    spawnVisualComponent(S1EggmanVisualComponent.ACCESSORY);
                } else {
                    initializeNativeWeapon();
                }
            }
        }
        super.update(vIntRunCount, player);
        shipDisplayedThisPass = !isDestroyed();
    }

    private boolean spawnVisualComponent(int component) {
        S1EggmanVisualComponent child = spawnChild(() -> new S1EggmanVisualComponent(this, component));
        return !child.isDestroyed();
    }

    protected void initializeNativeWeapon() {
    }

    protected int getAccessoryMappingFrame() {
        return -1;
    }

    protected abstract void updateFaceAnimation(AbstractPlayableSprite player);

    protected abstract void updateFlameAnimation();

    protected abstract int getNativeEscapeRoutine();

    int selectComponentAnimation(int component, PlayableEntity player) {
        if (component == S1EggmanVisualComponent.FACE) {
            updateFaceAnimation(player instanceof AbstractPlayableSprite sprite ? sprite : null);
            return faceAnim;
        }
        updateFlameAnimation();
        return flameAnim;
    }

    /** The ship, face and flame are independently dispatched native SST objects. */
    protected void renderEggmanShip() {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.EGGMAN);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, state.x, state.y, (state.renderFlags & 1) != 0, false);
        }
    }

    /** S1 BossDefeated: FindFreeObj precedes RNG; explosions are biased downward. */
    @Override
    protected void spawnDefeatExplosion() {
        var manager = services().objectManager();
        if (manager == null) return;
        manager.createDynamicObjectWithReservedSlot(() -> {
            int random = services().rng().nextWord();
            int x = (state.x + ((random & 0xFF) >> 2) - 0x20) & 0xFFFF;
            int y = (state.y + (((random >>> 8) & 0xFF) >> 3)) & 0xFFFF;
            return new S1BossExplosion(x, y, services().renderManager());
        });
    }

    /**
     * Run defeat explosions: decrement timer, spawn explosion every 8 frames.
     * ROM: BossDefeated subroutine — used by GHZ, MZ, SLZ, SYZ in their defeat wait states.
     *
     * @param frameCounter current frame counter (checked with & 7)
     * @return true if the timer has expired (caller should transition to next state)
     */
    protected boolean runDefeatExplosions(int frameCounter, int[] timer) {
        timer[0]--;
        if (timer[0] < 0) {
            return true; // Timer expired
        }
        // Spawn explosions every 8 frames
        if ((frameCounter & 7) == 0) {
            spawnDefeatExplosion();
        }
        return false;
    }

    /**
     * Run camera-expand escape: expand right boundary by 2 per frame until endBoundary,
     * then check if boss is off-screen and destroy it.
     * ROM: Common escape pattern in GHZ, MZ, SLZ, SYZ bosses.
     *
     * @param endBoundary the target right camera boundary (e.g. boss_ghz_end)
     * @return true if the boss was destroyed (off-screen), false otherwise
     */
    protected boolean runCameraExpandEscape(int endBoundary) {
        Camera camera = services().camera();

        // Unfreeze camera during escape
        if (camera.getFrozen()) {
            camera.setFrozen(false);
        }

        int rightBoundary = camera.getMaxX() & 0xFFFF;

        if (rightBoundary >= endBoundary) {
            // Boundary reached — check if off-screen
            if (!isBossOnScreen()) {
                setDestroyed(true);
                return true;
            }
        } else {
            // ROM: addq.w #2,(v_limitright2).w — expand right boundary
            camera.setMaxX((short) (rightBoundary + 2));
        }

        return false;
    }

    // ========================================================================
    // Default rendering — independently dispatched components draw themselves
    // ========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        renderEggmanShip();
    }

    @Override
    protected int getBossHitSfxId() {
        return Sonic1Sfx.HIT_BOSS.id;
    }

    @Override
    protected int getBossExplosionSfxId() {
        return Sonic1Sfx.BOSS_EXPLOSION.id;
    }

    /** Sonic 1 boss explosions are Obj3F: {@code move.b #1,obPriority(a0)}. */
    @Override
    protected int getBossExplosionPriorityBucket() {
        return BossExplosionObjectInstance.S1_PRIORITY_BUCKET;
    }

    @Override
    protected int getBossExplosionObjectId() {
        return Sonic1ObjectIds.EXPLOSION;
    }
}
