package com.openggf.level.objects;

import com.openggf.game.internal.GlideLandingDustPolicy;
import com.openggf.sprites.playable.PlayableArchetype;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.physics.Direction;
import com.openggf.game.PlayableEntity;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.util.List;

/**
 * Skid dust effect object (Object $08, routine 6 / animation 3).
 * Created when Sonic starts skidding (braking at speed >= 0x400).
 * Uses skid frames from the shared dust/splash art data.
 *
 * In the original game, the skid dust object is spawned at the player's feet
 * when skidding begins. It plays through its animation frames and then
 * destroys itself. Unlike spindash dust, it does NOT follow the player.
 *
 * Skid animation frames (from Sonic 2 disassembly obj08.asm):
 * Obj08Ani_Skid: dc.b 3,$11,$12,$13,$14,$FC
 * - Frame delay: 3 (4 game ticks per frame)
 * - Frames: 0x11, 0x12, 0x13, 0x14
 * - End action: $FC (routine increment - animation ends)
 */
public class SkidDustObjectInstance extends AbstractObjectInstance implements SpawnServicesRewindRecreatable {
    // Skid animation frames from obj08.asm Obj08Ani_Skid
    private static final int[] SKID_FRAMES = { 0x11, 0x12, 0x13, 0x14 };
    private static final int FRAME_DELAY = 3; // 4 game ticks per frame
    private static final int DELETE_ROUTINE_DELAY = 1;
    private static final int NULL_ANIMATION = -2;

    // Frame 0x15 (21) has the DPLC that loads skid dust tiles.
    // Frames 0x11-0x14 have empty DPLCs and reuse tiles from frame 0x15.
    private static final int PRELOAD_DPLC_FRAME = 0x15;

    private PlayerSpriteRenderer renderer;
    /**
     * Code of the player whose dust object spawned this puff. S3K loc_18D1C copies
     * the owning dust object's art_tile (sonic3k.asm:34166), so a follower's puff
     * draws from that follower's dust bank; a recreated puff must keep its owner.
     */
    private String ownerCode;
    private int animTimer;
    private int frameIndex;
    private int deleteRoutineDelay = -1;
    private boolean facingLeft;
    private boolean dplcPreloaded = false;
    private boolean glideSlidePuff;

    /**
     * Creates a skid dust object at the specified position.
     *
     * @param x          World X coordinate (player's X position)
     * @param y          World Y coordinate (player's feet Y position)
     * @param renderer   The dust/splash art renderer
     * @param facingLeft Whether the player was facing left when skidding started
     */
    public SkidDustObjectInstance(int x, int y, PlayerSpriteRenderer renderer, boolean facingLeft) {
        super(new ObjectSpawn(x, y, 0x08, 0, facingLeft ? 1 : 0, false, 0), "SkidDust");
        this.renderer = renderer;
        this.animTimer = FRAME_DELAY;
        this.frameIndex = 0;
        this.facingLeft = facingLeft;
    }

    public SkidDustObjectInstance(ObjectSpawn spawn, ObjectServices services) {
        this(spawn.x(), spawn.y(), null, (spawn.renderFlags() & 1) != 0);
    }

    private static PlayerSpriteRenderer findRewindRenderer(ObjectServices services) {
        PlayerSpriteRenderer renderer = null;
        if (services != null && services.spriteManager() != null) {
            for (Sprite sprite : services.spriteManager().getAllSprites()) {
                if (sprite instanceof AbstractPlayableSprite playable
                        && playable.getSpindashDustController() != null) {
                    renderer = playable.getSpindashDustController().getRenderer();
                    if (renderer != null && !playable.isCpuControlled()) {
                        break;
                    }
                }
            }
        }
        return renderer;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (deleteRoutineDelay >= 0) {
            if (deleteRoutineDelay-- == 0) {
                ObjectLifetimeOps.expireDynamic(this);
            }
            return;
        }

        if (frameIndex == NULL_ANIMATION) {
            return;
        }
        if (glideSlidePuff) {
            // Obj08_MdSkidDust (docs/kis2disasm/s2.asm:45884-45898) and
            // loc_18C84 (sonic3k.asm:34098-34113) switch to the blank looping
            // animation when drowning art takes over. The slot stays allocated.
            AbstractPlayableSprite owner = dustOwner(tryServices(), ownerCode);
            if (owner != null && !GlideLandingDustPolicy.hasAirForDust(owner)) {
                frameIndex = NULL_ANIMATION;
                return;
            }
        }

        // Decrement animation timer
        animTimer--;
        if (animTimer < 0) {
            animTimer = FRAME_DELAY;
            frameIndex++;

            // ROM animation command $FC increments Obj08 to routine 4; the
            // object remains allocated until that delete routine is reached on
            // a later RunObjects pass (docs/s2disasm/s2.asm:42660-42664,
            // 42846-42847).
            if (frameIndex >= SKID_FRAMES.length) {
                deleteRoutineDelay = glideSlidePuff ? 0 : DELETE_ROUTINE_DELAY;
            }
        }
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        // ROM Obj08_SkidDust tails through loc_1DEE0 ->
        // Obj08_LoadDustOrSplashArt -> rts, and the allocated puff itself runs
        // Obj08_Main -> Obj08_Display -> DisplaySprite
        // (docs/s2disasm/s2.asm:42796-42800, 42821-42851). Neither path calls
        // MarkObjGone, so the puff's lifetime is owned entirely by its
        // animation counter ($FC -> routine 4 -> DeleteObject); it must survive
        // the camera scrolling away from where it was dropped. The S3K dash
        // dust draws the same way (docs/skdisasm/sonic3k.asm:34063, 34108).
        return true;
    }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return false;
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState() {
        return super.captureRewindState().withObjectSubclassExtra(
                new SkidDustRewindExtra(animTimer, frameIndex, deleteRoutineDelay, dplcPreloaded, ownerCode, glideSlidePuff));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot) {
        super.restoreRewindState(snapshot);
        if (snapshot.objectSubclassExtra() instanceof SkidDustRewindExtra extra) {
            animTimer = extra.animTimer();
            frameIndex = extra.frameIndex();
            deleteRoutineDelay = extra.deleteRoutineDelay();
            dplcPreloaded = extra.dplcPreloaded();
            ownerCode = extra.ownerCode();
            glideSlidePuff = extra.glideSlidePuff();
            PlayerSpriteRenderer ownerRenderer = ownerDustRenderer(tryServices(), ownerCode);
            if (ownerRenderer != null) {
                renderer = ownerRenderer;
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PlayerSpriteRenderer activeRenderer = resolveRenderer();
        if (isDestroyed() || activeRenderer == null) {
            return;
        }

        // ROM: Frames 0x11-0x14 have empty DPLCs and reuse tiles loaded by frame 0x15.
        // We must "draw" frame 0x15 first to trigger its DPLC load (it has no mapping
        // pieces, so nothing visible is drawn), then the animation frames will work.
        if (!dplcPreloaded) {
            activeRenderer.drawFrame(PRELOAD_DPLC_FRAME, spawn.x(), spawn.y(), facingLeft, false);
            dplcPreloaded = true;
        }

        if (frameIndex >= 0 && frameIndex < SKID_FRAMES.length) {
            int mappingFrame = SKID_FRAMES[frameIndex];
            activeRenderer.drawFrame(mappingFrame, spawn.x(), spawn.y(), facingLeft, false);
        }
    }

    // Obj08_Display (s2.asm:42797-42801) displays the stored mapping_frame after AnimateSprite.
    // Obj08_SkidDust (s2.asm:42843-42854) copies mappings/art/parent to the actual allocated child.
    // Observe that frame and parent only; never run the ordinary draw-time DPLC preload here.
    final ObjectDrawInternalAccess.SkidDustDisplayPose nativeDisplayPose() {
        int frame=frameIndex>=0 && frameIndex<SKID_FRAMES.length ? SKID_FRAMES[frameIndex] : -1;
        return new ObjectDrawInternalAccess.SkidDustDisplayPose(frame,ownerCode,frameIndex,animTimer);
    }

    private PlayerSpriteRenderer resolveRenderer() {
        if (renderer == null) {
            renderer = ownerDustRenderer(tryServices(), ownerCode);
        }
        if (renderer == null) {
            renderer = findRewindRenderer(tryServices());
        }
        return renderer;
    }

    private static PlayerSpriteRenderer ownerDustRenderer(ObjectServices services, String ownerCode) {
        AbstractPlayableSprite owner = dustOwner(services, ownerCode);
        return owner == null ? null : playerDustRenderer(owner);
    }

    private static AbstractPlayableSprite dustOwner(ObjectServices services, String ownerCode) {
        if (ownerCode == null || services == null || services.spriteManager() == null) {
            return null;
        }
        return services.spriteManager().getSprite(ownerCode) instanceof AbstractPlayableSprite owner ? owner : null;
    }

    private static PlayerSpriteRenderer playerDustRenderer(AbstractPlayableSprite player) {
        var dustManager = player.getSpindashDustController();
        return dustManager == null ? null : dustManager.getRenderer();
    }

    /**
     * Creates a skid dust object for when the player starts skidding.
     * The dust is spawned at the player's feet position.
     *
     * @param player The player sprite
     * @return A new skid dust object, or null if renderer is not available
     */
    public static SkidDustObjectInstance create(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite aps)) {
            return null;
        }
        PlayerSpriteRenderer renderer = playerDustRenderer(aps);
        return renderer == null ? null : create(aps, renderer, false);
    }

    private static SkidDustObjectInstance create(AbstractPlayableSprite player, PlayerSpriteRenderer renderer,
                                                  boolean glideSlidePuff) {
        // Both emitters add $10 for Stop or 6 for glide slide, less 4 for the
        // second fixed Dust object (sonic3k.asm:34120-34158;
        // docs/kis2disasm/s2.asm:45910-45955).
        int dustX = player.getCentreX();
        int dustOffset = glideSlidePuff ? 6 : 16;
        if (PlayableArchetype.isTails(player)) {
            dustOffset -= 4;
        }
        // S3K loc_18D14 negates the complete foot offset after Tails's
        // four-pixel adjustment. The spawned puff stays at that world position;
        // later gravity changes must not move an already detached effect.
        ObjectServices owner = constructionContext();
        var gameState = owner != null ? owner.gameState() : player.currentGameStateOrNull();
        if (gameState != null && gameState.isReverseGravityActive()) {
            dustOffset = -dustOffset;
        }
        int dustY = player.getCentreY() + dustOffset;
        // loc_18D1C (sonic3k.asm:34157-34176) and Obj08_SkidDust
        // (docs/kis2disasm/s2.asm:45957-45969) clear the detached puff's flip.
        // Its first animation tick is on the next dynamic object pass.
        boolean facingLeft = !glideSlidePuff && player.getDirection() == Direction.LEFT;
        SkidDustObjectInstance dust = new SkidDustObjectInstance(dustX, dustY, renderer, facingLeft);
        dust.ownerCode = player.getCode();
        dust.glideSlidePuff = glideSlidePuff;
        if (glideSlidePuff) {
            dust.animTimer = 0;
            dust.frameIndex = -1;
        }
        return dust;
    }

    /**
     * Spawns a skid dust object at the player's position.
     * Convenience method that creates and adds the dust to the object manager.
     *
     * @param player The player sprite that started skidding
     */
    public static void spawn(PlayableEntity player) {
        spawn(player, false);
    }

    public static void spawnFromGlideSlide(PlayableEntity player) {
        spawn(player, true);
    }

    private static void spawn(PlayableEntity player, boolean glideSlidePuff) {
        if (ObjectConstructionContext.isProbeConstruction() || !(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        PlayerSpriteRenderer renderer = playerDustRenderer(sprite);
        if (renderer == null && !glideSlidePuff) {
            return;
        }
        ObjectServices owner = constructionContext();
        // Ordinary fixed player dust runs outside an object callback. Its
        // existing player-runtime level is the explicit bootstrap in that path.
        var level = owner == null ? sprite.currentLevelManagerIfAvailable() : null;
        ObjectManager manager = owner != null ? owner.objectManager()
                : level != null ? level.getObjectManager() : null;
        if (manager != null) {
            // AllocateObject starts at the first dynamic slot
            // (sonic3k.asm:37951-37965; docs/kis2disasm/s2.asm:35314-35331).
            manager.createDynamicObject(() -> create(sprite, renderer, glideSlidePuff));
        }
    }

    private record SkidDustRewindExtra(
            int animTimer,
            int frameIndex,
            int deleteRoutineDelay,
            boolean dplcPreloaded,
            String ownerCode,
            boolean glideSlidePuff
    ) implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {
    }

    /**
     * Skid dust bucket: S2 Obj08 {@code move.b #1,priority(a0)} (s2.asm:42725),
     * S3K Obj_DashDust {@code move.w #$80,priority(a0)} (sonic3k.asm:34011).
     */
    private static final int DUST_PRIORITY_BUCKET = RenderPriority.bucket(1);

    @Override
    public int getPriorityBucket() {
        return DUST_PRIORITY_BUCKET;
    }
}
