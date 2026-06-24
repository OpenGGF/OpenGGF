package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.GameRng;
import com.openggf.graphics.GLCommand;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Object 0xB9 - ICZ snow pile.
 *
 * <p>ROM reference: {@code Obj_ICZSnowPile} at sonic3k.asm:189802-190133.
 * Low subtype values select the ROM's raw dispatch table offsets:
 * {@code $00} slows/breaks on both native players, {@code $08} launches Sonic
 * and spawns two pieces, {@code $10} launches Sonic, spawns four pieces, and
 * optionally starts LBZ when bit 7 is set, {@code $18} is the snowdust emitter.
 */
public class IczSnowPileObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    private static final int OBJECT_ID = Sonic3kObjectIds.ICZ_SNOW_PILE;
    private static final int DRAW_PALETTE = 2;
    private static final int PRIORITY_BUCKET = 1; // ObjDat3 priority $80.

    // ObjDat3_8B814 / 820 / 82C.
    private static final int FRAME_BREAKABLE_PILE = 0x20;
    private static final int FRAME_SMALL_LAUNCH_PILE = 0x21;
    private static final int FRAME_LARGE_LAUNCH_PILE = 0x22;
    private static final int FRAME_SNOWDUST = 0x0B;

    // Check_PlayerInRange boxes: {left, width, top, height}.
    private static final int[] BREAK_BOX = {-0x28, 0x50, -0x18, 0x20};
    private static final int[] LAUNCH_BOX = {-0x08, 0x10, -0x10, 0x20};

    // sub_8B598: break if |x_vel| >= $600; otherwise ground_vel = x_vel / 2.
    private static final int BREAK_X_SPEED = 0x0600;
    private static final int LAUNCH_SPEED = 0x0800;
    private static final int NEXT_ZONE_LBZ = 6; // StartNewLevel #$0600.
    private static final int NEXT_ACT_1 = 0;
    private static final int LIGHT_GRAVITY = 0x20; // MoveSprite_LightGravity.
    private static final int SNOWDUST_SPAWN_PERIOD = 8;
    private static final int SNOWDUST_MAX_COUNT = 0x3C;
    private static final int[] SNOWDUST_RANDOM_FRAMES = {0x0B, 0x10, 0x0B, 0x0B};
    private static final int[] SNOWDUST_PRIORITIES = {1, 7, 1, 1}; // word_8B718: $80/$380.

    private static final int[][] BREAK_CHILD_OFFSETS = {
            {-0x08, -0x08},
            { 0x08, -0x08},
            {-0x18,  0x00},
            {-0x08,  0x00},
            { 0x08,  0x00},
            { 0x18,  0x00}
    };
    private static final int[][] SMALL_LAUNCH_CHILD_OFFSETS = {
            {0x00, -0x08},
            {0x00,  0x08}
    };
    private static final int[][] LARGE_LAUNCH_CHILD_OFFSETS = {
            {-0x08, -0x08},
            { 0x00,  0x08},
            { 0x08, -0x08},
            { 0x08,  0x08}
    };
    private static final int[][] CHILD_VELOCITIES = {
            {-0x0300, -0x0200},
            {-0x0400, -0x0300},
            {-0x0400, -0x0280},
            {-0x0280, -0x0200},
            {-0x0200, -0x0180},
            {-0x0200, -0x0100}
    };

    private int x;
    private int y;
    private final int variant;
    private final boolean startsNextLevel;
    private final boolean hFlip;
    private boolean destroyedByTrigger;
    private int snowdustTimer;
    private int activeSnowdustCount;
    private boolean snowdustStopped;

    public IczSnowPileObjectInstance(ObjectSpawn spawn) {
        super(spawn, "ICZSnowPile");
        this.x = spawn.x();
        this.y = spawn.y();
        this.variant = (spawn.subtype() & 0x7F);
        this.startsNextLevel = (spawn.subtype() & 0x80) != 0;
        this.hFlip = (spawn.renderFlags() & 0x01) != 0;
    }

    @Override
    public IczSnowPileObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new IczSnowPileObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (isDestroyed()) {
            return;
        }

        switch (variant) {
            case 0x08 -> updateLaunchPile(player, SMALL_LAUNCH_CHILD_OFFSETS, false);
            case 0x10 -> updateLaunchPile(player, LARGE_LAUNCH_CHILD_OFFSETS, startsNextLevel);
            case 0x18 -> updateSnowdustEmitter();
            default -> updateBreakablePile(player);
        }
    }

    private void updateBreakablePile(PlayableEntity mainPlayer) {
        for (PlayableEntity candidate : nativePlayersP2ThenP1(mainPlayer)) {
            if (!isInRange(candidate, BREAK_BOX)) {
                continue;
            }
            if (shouldBreak(candidate)) {
                breakPile(candidate, BREAK_CHILD_OFFSETS);
                return;
            }
            if (!candidate.getAir()) {
                candidate.setGSpeed((short) (candidate.getXSpeed() >> 1));
            }
        }
    }

    private void updateLaunchPile(PlayableEntity mainPlayer, int[][] childOffsets, boolean requestNextLevel) {
        if (!isInRange(mainPlayer, LAUNCH_BOX)) {
            return;
        }

        mainPlayer.setXSpeed((short) LAUNCH_SPEED);
        mainPlayer.setGSpeed((short) LAUNCH_SPEED);
        breakPile(mainPlayer, childOffsets);
        if (requestNextLevel) {
            services().requestZoneAndAct(NEXT_ZONE_LBZ, NEXT_ACT_1, true);
        }
    }

    private boolean shouldBreak(PlayableEntity player) {
        if (player.getAnimationId() == Sonic3kAnimationIds.SPINDASH.id()) {
            return true;
        }
        if (player.getAir()) {
            return false;
        }
        return Math.abs(player.getXSpeed()) >= BREAK_X_SPEED;
    }

    private void breakPile(PlayableEntity player, int[][] childOffsets) {
        destroyedByTrigger = true;
        boolean playerFacingLeft = player != null && player.getDirection() == Direction.LEFT;
        for (SnowPileDebrisSpec spec : buildDebrisSpecs(x, y, childOffsets, playerFacingLeft)) {
            spawnChild(() -> new SnowPileDebris(spec));
        }
        setDestroyed(true);
    }

    private void updateSnowdustEmitter() {
        if (snowdustStopped) {
            setDestroyed(true);
            return;
        }

        Camera camera = tryCamera();
        if (camera != null) {
            x = camera.getX();
            y = camera.getY() - 8;
        }

        if (--snowdustTimer >= 0) {
            return;
        }
        snowdustTimer = SNOWDUST_SPAWN_PERIOD;
        if (activeSnowdustCount >= SNOWDUST_MAX_COUNT) {
            return;
        }

        SnowdustSpec spec = buildSnowdustSpec(camera);
        if (spec == null) {
            return;
        }
        activeSnowdustCount++;
        spawnFreeChild(() -> new SnowdustParticle(this, spec));
    }

    private Camera tryCamera() {
        ObjectServices services = tryServices();
        return services != null ? services.camera() : null;
    }

    private SnowdustSpec buildSnowdustSpec(Camera camera) {
        ObjectServices services = tryServices();
        GameRng rng = services != null ? services.rng() : null;
        int randomX = rng != null ? rng.nextWord() & 0x01FF : 0;
        if (randomX >= 0x0140) {
            randomX = (randomX & 0x003F) << 2;
        }
        int cameraX = camera != null ? camera.getX() : x;
        int particleX = cameraX + randomX;
        if (particleX < 0x0380) {
            return null;
        }

        int randomMeta = rng != null ? rng.nextWord() : 0;
        int frameIndex = randomMeta & 0x03;
        int xVel = (randomMeta & 0x003C) - 0x20;
        int cameraY = camera != null ? camera.getY() : y;
        return new SnowdustSpec(
                particleX,
                cameraY - 4,
                SNOWDUST_RANDOM_FRAMES[frameIndex],
                SNOWDUST_PRIORITIES[frameIndex],
                xVel,
                0x0100);
    }

    private void onSnowdustExpired() {
        if (activeSnowdustCount > 0) {
            activeSnowdustCount--;
        }
    }

    public boolean isSnowdustEmitter() {
        return variant == 0x18;
    }

    public void stopSnowdustEmitter() {
        if (!isSnowdustEmitter()) {
            return;
        }
        snowdustStopped = true;
        setDestroyed(true);
    }

    private List<PlayableEntity> nativePlayersP2ThenP1(PlayableEntity mainPlayer) {
        List<PlayableEntity> players = new ArrayList<>(2);
        ObjectServices services = tryServices();
        if (services != null) {
            // Native P2 (sidekick) is processed before P1, matching the ROM's
            // player loop order. Routed through the shared ObjectPlayerQuery
            // contract instead of a raw services.sidekicks() iteration.
            PlayableEntity nativeP2 = ObjectPlayerQuery.from(services).nativeP2OrNull();
            if (nativeP2 != null && !nativeP2.getDead()) {
                players.add(nativeP2);
            }
        }
        if (mainPlayer != null && !mainPlayer.getDead()) {
            players.add(mainPlayer);
        }
        return players;
    }

    private boolean isInRange(PlayableEntity player, int[] box) {
        if (player == null || player.getDead()) {
            return false;
        }
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();
        int minX = x + box[0];
        int maxX = minX + box[1];
        int minY = y + box[2];
        int maxY = minY + box[3];
        return playerX >= minX && playerX < maxX && playerY >= minY && playerY < maxY;
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
    public boolean isHighPriority() {
        return variant == 0x08 || variant == 0x10;
    }

    @Override
    public boolean isPersistent() {
        return variant == 0x18;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY_BUCKET);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (destroyedByTrigger || isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(artKey());
        if (renderer != null) {
            renderer.drawFrameIndex(mappingFrame(), x, y, hFlip, false, DRAW_PALETTE);
        }
    }

    private String artKey() {
        return variant == 0x18 ? Sonic3kObjectArtKeys.ICZ_PLATFORMS : Sonic3kObjectArtKeys.ICZ_PLATFORMS_MISC2;
    }

    private int mappingFrame() {
        return switch (variant) {
            case 0x08 -> FRAME_SMALL_LAUNCH_PILE;
            case 0x10 -> FRAME_LARGE_LAUNCH_PILE;
            case 0x18 -> FRAME_SNOWDUST;
            default -> FRAME_BREAKABLE_PILE;
        };
    }

    static List<SnowPileDebrisSpec> debrisSpecsForTesting(int x, int y, int[][] offsets, boolean playerFacingLeft) {
        return buildDebrisSpecs(x, y, offsets, playerFacingLeft);
    }

    private static List<SnowPileDebrisSpec> buildDebrisSpecs(int x, int y, int[][] offsets, boolean playerFacingLeft) {
        List<SnowPileDebrisSpec> specs = new ArrayList<>(offsets.length);
        for (int i = 0; i < offsets.length; i++) {
            int[] offset = offsets[i];
            int[] velocity = CHILD_VELOCITIES[i];
            int xVel = playerFacingLeft ? -velocity[0] : velocity[0];
            specs.add(new SnowPileDebrisSpec(i * 2, x + offset[0], y + offset[1], xVel, velocity[1], playerFacingLeft));
        }
        return List.copyOf(specs);
    }

    public record SnowPileDebrisSpec(int subtype, int x, int y, int xVel, int yVel, boolean hFlip) {
    }

    public static final class SnowPileDebris extends AbstractObjectInstance {
        private final SubpixelMotion.State motion;
        private final boolean hFlip;

        private SnowPileDebris(SnowPileDebrisSpec spec) {
            super(new ObjectSpawn(spec.x(), spec.y(), OBJECT_ID, spec.subtype(), 0, false, spec.y()),
                    "ICZSnowPileDebris");
            this.motion = new SubpixelMotion.State(spec.x(), spec.y(), 0, 0, spec.xVel(), spec.yVel());
            this.hFlip = spec.hFlip();
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            SubpixelMotion.objectFallXY(motion, LIGHT_GRAVITY);
            if (!isOnScreen()) {
                setDestroyed(true);
            }
        }

        @Override
        public int getX() {
            return motion.x;
        }

        @Override
        public int getY() {
            return motion.y;
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY_BUCKET);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ICZ_PLATFORMS);
            if (renderer != null) {
                renderer.drawFrameIndex(9, motion.x, motion.y, hFlip, false, DRAW_PALETTE);
            }
        }
    }

    private record SnowdustSpec(int x, int y, int mappingFrame, int priorityBucket, int xVel, int yVel) {
    }

    private static final class SnowdustParticle extends AbstractObjectInstance {
        @RewindTransient(reason = "structural parent link used only to mirror Hyudoro_count on particle expiry")
        private final IczSnowPileObjectInstance parent;
        private final SubpixelMotion.State motion;
        private final int mappingFrame;
        private final int priorityBucket;
        private boolean enteredScreen;
        private boolean flickerBit;
        private boolean drawThisFrame = true;

        private SnowdustParticle(IczSnowPileObjectInstance parent, SnowdustSpec spec) {
            super(new ObjectSpawn(spec.x(), spec.y(), OBJECT_ID, 0, 0, false, spec.y()), "ICZSnowdustParticle");
            this.parent = parent;
            this.motion = new SubpixelMotion.State(spec.x(), spec.y(), 0, 0, spec.xVel(), spec.yVel());
            this.mappingFrame = spec.mappingFrame();
            this.priorityBucket = spec.priorityBucket();
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            SubpixelMotion.moveSprite2(motion);
            boolean onScreen = isOnScreen();
            if (!enteredScreen) {
                if (onScreen) {
                    enteredScreen = true;
                    drawThisFrame = false;
                } else {
                    drawThisFrame = true;
                }
                return;
            }

            if (!onScreen) {
                expire();
                return;
            }
            drawThisFrame = !flickerBit;
            flickerBit = !flickerBit;
        }

        private void expire() {
            parent.onSnowdustExpired();
            setDestroyed(true);
        }

        @Override
        public int getX() {
            return motion.x;
        }

        @Override
        public int getY() {
            return motion.y;
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(priorityBucket);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (!drawThisFrame) {
                return;
            }
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ICZ_PLATFORMS);
            if (renderer != null) {
                renderer.drawFrameIndex(mappingFrame, motion.x, motion.y, false, false, DRAW_PALETTE);
            }
        }
    }
}
