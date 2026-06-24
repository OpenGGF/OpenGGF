package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K Obj $96 - TurboSpiker (HCZ).
 *
 * <p>Implements the HCZ crab badnik's full ROM state flow:
 * patrol toward the player, periodically pause-turn at subtype-defined
 * intervals, back away and launch its shell when the player approaches from the
 * facing side, and optionally hide behind a waterfall overlay on Y-flipped
 * placements before emerging in a burst of splash particles.
 *
 * <p>ROM reference: {@code Obj_TurboSpiker} (sonic3k.asm:183861-184226).
 */
public final class TurboSpikerBadnikInstance extends AbstractS3kBadnikInstance {

    private static final int COLLISION_SIZE_INDEX = 0x1A;      // ObjDat_TurboSpiker flags $1A
    private static final int PRIORITY_BUCKET_NORMAL = 5;       // ObjDat_TurboSpiker priority $280
    private static final int PRIORITY_BUCKET_WATERFALL = 3;    // ObjDat3_87EDA priority $180

    private static final int DETECT_RANGE = 0x60;
    private static final int INITIAL_TRACK_SPEED = 0x80;
    private static final int RETREAT_SPEED = 0x200;

    private static final int FLOOR_MIN_DIST = -1;
    private static final int FLOOR_MAX_DIST = 0x0C;
    private static final int Y_RADIUS = 0x0F;

    private static final int TURN_DELAY = 0x0F;
    private static final int WATERFALL_EMERGE_DELAY = 3;
    private static final int WATERFALL_PRIORITY_DELAY = 0x0F;

    private static final int WALK_ANIM_DELAY = 5;
    private static final int SHELLLESS_ANIM_DELAY = 1;
    private static final int[] WALK_FRAMES = {0, 1, 2};

    private static final int SHELL_OFFSET_X = 4;
    private static final int SHELL_LAUNCH_SPEED_X = 0x100;
    private static final int SHELL_LAUNCH_SPEED_Y = -0x400;
    private static final int SHELL_COLLISION_FLAGS = 0x9E;
    private static final int SHELL_FRAME = 3;
    private static final int SHELL_TRAIL_FRAME = 4;
    private static final int SHELL_PRIORITY_BUCKET = 5;
    private static final int SHELL_OFF_SCREEN_MARGIN = 160;

    private static final int[] SHELL_DRIP_FRAMES = {5, 5, 5, 6, 7};
    private static final int[] WATER_SPLASH_FRAMES = {8, 9, 10, 11, 12, 13};
    private static final int PARTICLE_KIND_SHELL_DRIP = 0;
    private static final int PARTICLE_KIND_WATER_SPLASH = 1;
    private static final int[] WATER_SPLASH_OFFSETS_X = {4, -6, 6, -8, 8};
    private static final int[] WATER_SPLASH_OFFSETS_Y = {-8, 0, 0, 0, 0};

    private enum State {
        HIDDEN_WAIT,
        EMERGE_DELAY,
        EMERGE_WATERFALL,
        PATROL,
        TURN_PAUSE,
        LAUNCH_PREP,
        SHELLLESS_RUN
    }

    private static final ObjectPlayerParticipationPolicy TARGET_PARTICIPATION =
            ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS;

    private record TargetSelection(AbstractPlayableSprite player, int distance) {
    }

    private boolean hiddenVariant;
    private int turnResetTimer;

    private State state;
    private State resumeState;
    private int stateTimer;
    private int turnTimer;
    private int currentPriorityBucket = PRIORITY_BUCKET_NORMAL;

    private boolean initialized;
    private boolean waterfallOverlayVisible;
    private int animIndex;
    private int animTimer;
    private TurboSpikerShellChild shellChild;

    public TurboSpikerBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "TurboSpiker",
                Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER, COLLISION_SIZE_INDEX, PRIORITY_BUCKET_NORMAL);
        this.hiddenVariant = (spawn.renderFlags() & 0x02) != 0;
        this.state = hiddenVariant ? State.HIDDEN_WAIT : State.PATROL;
        this.turnTimer = (spawn.subtype() & 0xFF) << 1;
        this.turnResetTimer = this.turnTimer << 1;
        this.mappingFrame = WALK_FRAMES[0];
        this.waterfallOverlayVisible = hiddenVariant;
    }

    @Override
    protected void updateMovement(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed() || !isOnScreenX()) {
            return;
        }

        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (!initialized) {
            initialize(player);
            return;
        }

        switch (state) {
            case HIDDEN_WAIT -> updateHiddenWait(player);
            case EMERGE_DELAY -> updateEmergeDelay();
            case EMERGE_WATERFALL -> updateEmergeWaterfall();
            case PATROL -> updatePatrol(player);
            case TURN_PAUSE -> updateTurnPause();
            case LAUNCH_PREP -> updateLaunchPrep();
            case SHELLLESS_RUN -> updateShelllessRun();
        }
    }

    @Override
    public int getPriorityBucket() {
        return currentPriorityBucket;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean hFlip = !facingLeft;
        if (shellChild != null && shellChild.isAttached()) {
            renderer.drawFrameIndex(SHELL_FRAME,
                    currentX + adjustedOffsetX(SHELL_OFFSET_X),
                    currentY,
                    hFlip, false);
        }
        renderer.drawFrameIndex(mappingFrame, getRenderAnchorX(), getRenderAnchorY(), hFlip, false);
    }

    private void initialize(AbstractPlayableSprite player) {
        trackInitialFacing(player);
        shellChild = spawnChild(() -> new TurboSpikerShellChild(this));
        if (hiddenVariant) {
            spawnChild(() -> new TurboSpikerWaterfallOverlayChild(this));
        }
        initialized = true;
    }

    private void updateHiddenWait(AbstractPlayableSprite mainPlayer) {
        TargetSelection target = findNearestTarget(mainPlayer);
        if (target.player() == null || target.distance() >= DETECT_RANGE) {
            return;
        }

        waterfallOverlayVisible = false;
        state = State.EMERGE_DELAY;
        stateTimer = WATERFALL_EMERGE_DELAY;
        spawnWaterSplashBurst();
    }

    private void updateEmergeDelay() {
        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        state = State.EMERGE_WATERFALL;
        stateTimer = WATERFALL_PRIORITY_DELAY;
        currentPriorityBucket = PRIORITY_BUCKET_WATERFALL;
    }

    private void updateEmergeWaterfall() {
        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        state = State.PATROL;
        currentPriorityBucket = PRIORITY_BUCKET_NORMAL;
    }

    private void updatePatrol(AbstractPlayableSprite mainPlayer) {
        TargetSelection target = findNearestTarget(mainPlayer);
        if (shouldLaunchShell(target)) {
            enterLaunchPrep();
            return;
        }

        animateWalking(WALK_ANIM_DELAY);
        moveWithVelocity();
        if (!snapToFloorOrPause(State.PATROL)) {
            return;
        }

        turnTimer--;
        if (turnTimer < 0) {
            enterTurnPause(State.PATROL);
        }
    }

    private void updateTurnPause() {
        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        state = resumeState;
        xVelocity = -xVelocity;
        facingLeft = !facingLeft;
        turnTimer = turnResetTimer;
        currentPriorityBucket = PRIORITY_BUCKET_NORMAL;
    }

    private void updateLaunchPrep() {
        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        state = State.SHELLLESS_RUN;
        if (shellChild != null) {
            shellChild.launch();
        }
    }

    private void updateShelllessRun() {
        animateWalking(SHELLLESS_ANIM_DELAY);
        moveWithVelocity();
        snapToFloorOrPause(State.SHELLLESS_RUN);
    }

    private void enterLaunchPrep() {
        state = State.LAUNCH_PREP;
        stateTimer = TURN_DELAY;
        facingLeft = !facingLeft;
        xVelocity = facingLeft ? -RETREAT_SPEED : RETREAT_SPEED;
    }

    private void enterTurnPause(State previousState) {
        if (state == State.TURN_PAUSE) {
            return;
        }
        resumeState = previousState;
        state = State.TURN_PAUSE;
        stateTimer = TURN_DELAY;
    }

    private boolean snapToFloorOrPause(State previousState) {
        int probeX = currentX + (xVelocity >> 8);
        TerrainCheckResult floor;
        try {
            floor = ObjectTerrainUtils.checkFloorDist(probeX, currentY, Y_RADIUS);
        } catch (IllegalStateException e) {
            return true;
        }
        if (!floor.foundSurface() || floor.distance() < FLOOR_MIN_DIST || floor.distance() >= FLOOR_MAX_DIST) {
            enterTurnPause(previousState);
            return false;
        }
        currentY += floor.distance();
        return true;
    }

    private void animateWalking(int delay) {
        animTimer--;
        if (animTimer >= 0) {
            return;
        }
        animIndex = (animIndex + 1) % WALK_FRAMES.length;
        mappingFrame = WALK_FRAMES[animIndex];
        animTimer = delay;
    }

    private void trackInitialFacing(AbstractPlayableSprite mainPlayer) {
        AbstractPlayableSprite target = findNearestTarget(mainPlayer).player();
        if (target == null) {
            xVelocity = facingLeft ? -INITIAL_TRACK_SPEED : INITIAL_TRACK_SPEED;
            return;
        }

        if (romSignedXDelta(target) >= 0) {
            facingLeft = true;
            xVelocity = -INITIAL_TRACK_SPEED;
        } else {
            facingLeft = false;
            xVelocity = INITIAL_TRACK_SPEED;
        }
    }

    private boolean shouldLaunchShell(TargetSelection target) {
        AbstractPlayableSprite player = target.player();
        if (player == null) {
            return false;
        }
        if (target.distance() >= DETECT_RANGE) {
            return false;
        }

        int directionCode = romSignedXDelta(player) >= 0 ? 0 : 2;
        if (!facingLeft) {
            directionCode -= 2;
        }
        return directionCode == 0;
    }

    private TargetSelection findNearestTarget(AbstractPlayableSprite mainPlayer) {
        ObjectServices svc = tryServices();
        ObjectPlayerQuery query = new ObjectPlayerQuery(
                () -> mainPlayer,
                () -> svc != null ? svc.playerQuery().sidekicks() : List.of());
        ObjectPlayerQuery.NearestPlayerX nearest = query.nearestByRomX(
                TARGET_PARTICIPATION,
                currentX,
                TurboSpikerBadnikInstance::isLivePlayable);
        return new TargetSelection((AbstractPlayableSprite) nearest.player(), nearest.distance());
    }

    private static boolean isLivePlayable(PlayableEntity player) {
        return player instanceof AbstractPlayableSprite sprite && !sprite.getDead();
    }

    private int romSignedXDelta(AbstractPlayableSprite target) {
        return (short) ((currentX - target.getCentreX()) & 0xFFFF);
    }

    private boolean shouldShowWaterfallOverlay() {
        return waterfallOverlayVisible && !isDestroyed();
    }

    private int adjustedOffsetX(int baseOffset) {
        return adjustedOffsetX(baseOffset, facingLeft);
    }

    private static int adjustedOffsetX(int baseOffset, boolean facingLeft) {
        return facingLeft ? baseOffset : -baseOffset;
    }

    private void spawnWaterSplashBurst() {
        services().playSfx(Sonic3kSfx.SPLASH.id);
        for (int i = 0; i < WATER_SPLASH_OFFSETS_X.length; i++) {
            int index = i;
            spawnChild(() -> new TurboSpikerWaterSplashParticle(
                    this,
                    currentX + adjustedOffsetX(WATER_SPLASH_OFFSETS_X[index]),
                    currentY + WATER_SPLASH_OFFSETS_Y[index],
                    false));
        }
    }

    private static final class TurboSpikerShellChild extends AbstractObjectInstance
            implements TouchResponseProvider, RewindRecreatable {
        private TurboSpikerBadnikInstance parent;

        private int currentX;
        private int currentY;
        private int xVelocity;
        private int yVelocity;
        private int xSubpixel;
        private int ySubpixel;
        private boolean attached = true;
        private boolean facingLeft;
        private TurboSpikerTrailEmitter trailEmitter;

        TurboSpikerShellChild(TurboSpikerBadnikInstance parent) {
            super(parent.getSpawn(), "TurboSpikerShell");
            this.parent = parent;
            this.facingLeft = parent.badnikFacingLeft();
            this.currentX = parent.getX() + adjustedOffsetX(SHELL_OFFSET_X, facingLeft);
            this.currentY = parent.getY();
        }

        @Override
        public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
            TurboSpikerBadnikInstance liveParent = findLiveTurboSpikerParent(ctx);
            return liveParent != null ? new TurboSpikerShellChild(liveParent) : null;
        }

        void launch() {
            if (!attached || parent.isDestroyed()) {
                return;
            }
            attached = false;
            facingLeft = parent.badnikFacingLeft();
            currentX = parent.getX() + adjustedOffsetX(SHELL_OFFSET_X, facingLeft);
            currentY = parent.getY();
            xVelocity = facingLeft ? -SHELL_LAUNCH_SPEED_X : SHELL_LAUNCH_SPEED_X;
            yVelocity = SHELL_LAUNCH_SPEED_Y;
            trailEmitter = spawnChild(() -> new TurboSpikerTrailEmitter(this));
            services().playSfx(Sonic3kSfx.FLOOR_LAUNCHER.id);
        }

        boolean isAttached() {
            return attached;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (attached) {
                if (parent.isDestroyed()) {
                    setDestroyed(true);
                    return;
                }
                facingLeft = parent.badnikFacingLeft();
                currentX = parent.getX() + adjustedOffsetX(SHELL_OFFSET_X, facingLeft);
                currentY = parent.getY();
                return;
            }

            int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
            int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
            xPos24 += xVelocity;
            yPos24 += yVelocity;
            currentX = xPos24 >> 8;
            currentY = yPos24 >> 8;
            xSubpixel = xPos24 & 0xFF;
            ySubpixel = yPos24 & 0xFF;

            if (!isOnScreen(SHELL_OFF_SCREEN_MARGIN)) {
                setDestroyed(true);
            }
        }

        @Override
        public int getCollisionFlags() {
            if (isDestroyed()) {
                return 0;
            }
            return SHELL_COLLISION_FLAGS;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public ObjectSpawn getSpawn() {
            return buildSpawnAt(currentX, currentY);
        }

        @Override
        public int getX() {
            return currentX;
        }

        @Override
        public int getY() {
            return currentY;
        }

        @Override
        public int getPriorityBucket() {
            return SHELL_PRIORITY_BUCKET;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (attached) {
                return;
            }
            ObjectRenderManager renderManager = services().renderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(SHELL_FRAME, currentX, currentY, !facingLeft, false);
        }

        private static TurboSpikerBadnikInstance findLiveTurboSpikerParent(RewindRecreateContext ctx) {
            if (ctx == null || ctx.objectServices() == null || ctx.objectServices().objectManager() == null) {
                return null;
            }
            for (ObjectInstance instance : ctx.objectServices().objectManager().getActiveObjects()) {
                if (instance instanceof TurboSpikerBadnikInstance turboSpiker && !turboSpiker.isDestroyed()) {
                    return turboSpiker;
                }
            }
            return null;
        }
    }

    private static final class TurboSpikerTrailEmitter extends AbstractObjectInstance {

        private static final int SPAWN_INTERVAL_MASK = 0x03;
        private static final int OFFSET_X = -4;
        private static final int OFFSET_Y = 0x14;
        private final TurboSpikerShellChild shell;
        private int mappingFrame = SHELL_TRAIL_FRAME;

        TurboSpikerTrailEmitter(TurboSpikerShellChild shell) {
            super(shell.getSpawn(), "TurboSpikerTrailEmitter");
            this.shell = shell;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (shell.isDestroyed()) {
                setDestroyed(true);
                return;
            }

            if ((frameCounter & SPAWN_INTERVAL_MASK) == 0) {
                int xJitter = ((frameCounter >> 2) & 7) - 3;
                int yJitter = ((frameCounter >> 3) & 7) - 3;
                spawnChild(() -> new TurboSpikerShellDripParticle(
                        getX() + xJitter,
                        getY() + yJitter + 4,
                        shell.getSpawn()));
            }

            mappingFrame ^= 1;
        }

        @Override
        public ObjectSpawn getSpawn() {
            return buildSpawnAt(getX(), getY());
        }

        @Override
        public int getX() {
            return shell.getX() + adjustedOffsetX(OFFSET_X, shell.facingLeft);
        }

        @Override
        public int getY() {
            return shell.getY() + OFFSET_Y;
        }

        @Override
        public int getPriorityBucket() {
            return 4;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if ((mappingFrame & 1) == 0) {
                return;
            }
            ObjectRenderManager renderManager = services().renderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(SHELL_TRAIL_FRAME, getX(), getY(), !shell.facingLeft, false);
        }
    }

    private static final class TurboSpikerShellDripParticle extends TurboSpikerAnimatedParticle {
        TurboSpikerShellDripParticle(int x, int y, ObjectSpawn ownerSpawn) {
            this(TurboSpikerAnimatedParticle.particleSpawn(
                    ownerSpawn, x, y, PARTICLE_KIND_SHELL_DRIP, false));
        }

        private TurboSpikerShellDripParticle(ObjectSpawn spawn) {
            super(spawn, "TurboSpikerShellDrip", spawn.x(), spawn.y(), SHELL_DRIP_FRAMES, 1, 5, false);
        }
    }

    private static final class TurboSpikerWaterSplashParticle extends TurboSpikerAnimatedParticle {
        TurboSpikerWaterSplashParticle(TurboSpikerBadnikInstance parent, int x, int y, boolean playSound) {
            this(TurboSpikerAnimatedParticle.particleSpawn(
                    parent.getSpawn(), x, y, PARTICLE_KIND_WATER_SPLASH, playSound));
        }

        private TurboSpikerWaterSplashParticle(ObjectSpawn spawn) {
            super(spawn, "TurboSpikerWaterSplash", spawn.x(), spawn.y(), WATER_SPLASH_FRAMES, 1, 4,
                    (spawn.renderFlags() & 1) != 0);
        }
    }

    private static class TurboSpikerAnimatedParticle extends AbstractObjectInstance implements SpawnRewindRecreatable {

        private int currentX;
        private int currentY;
        private final int[] frames;
        private int frameDelay;
        private int priorityBucket;
        private boolean playSound;

        private int frameIndex;
        private int frameTimer;
        private int mappingFrame;
        private boolean soundPlayed;

        private TurboSpikerAnimatedParticle(ObjectSpawn spawn) {
            this(spawn,
                    particleName(spawn),
                    spawn.x(),
                    spawn.y(),
                    particleFrames(spawn),
                    1,
                    particlePriority(spawn),
                    (spawn.renderFlags() & 1) != 0);
        }

        TurboSpikerAnimatedParticle(ObjectSpawn ownerSpawn, String name, int x, int y,
                int[] frames, int frameDelay, int priorityBucket, boolean playSound) {
            super(ownerSpawn, name);
            this.currentX = x;
            this.currentY = y;
            this.frames = frames;
            this.frameDelay = frameDelay;
            this.priorityBucket = priorityBucket;
            this.mappingFrame = frames[0];
            this.playSound = playSound;
        }

        private static ObjectSpawn particleSpawn(ObjectSpawn ownerSpawn, int x, int y, int particleKind,
                boolean playSound) {
            int objectId = ownerSpawn == null ? 0 : ownerSpawn.objectId();
            return new ObjectSpawn(x, y, objectId, particleKind, playSound ? 1 : 0, false, y);
        }

        private static String particleName(ObjectSpawn spawn) {
            return (spawn.subtype() & 0xFF) == PARTICLE_KIND_WATER_SPLASH
                    ? "TurboSpikerWaterSplash"
                    : "TurboSpikerShellDrip";
        }

        private static int[] particleFrames(ObjectSpawn spawn) {
            return (spawn.subtype() & 0xFF) == PARTICLE_KIND_WATER_SPLASH
                    ? WATER_SPLASH_FRAMES
                    : SHELL_DRIP_FRAMES;
        }

        private static int particlePriority(ObjectSpawn spawn) {
            return (spawn.subtype() & 0xFF) == PARTICLE_KIND_WATER_SPLASH ? 4 : 5;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (playSound && !soundPlayed) {
                services().playSfx(Sonic3kSfx.SPLASH.id);
                soundPlayed = true;
            }
            frameTimer--;
            if (frameTimer >= 0) {
                return;
            }
            frameTimer = frameDelay;
            frameIndex++;
            if (frameIndex >= frames.length) {
                setDestroyed(true);
                return;
            }
            mappingFrame = frames[frameIndex];
        }

        @Override
        public ObjectSpawn getSpawn() {
            return buildSpawnAt(currentX, currentY);
        }

        @Override
        public int getX() {
            return currentX;
        }

        @Override
        public int getY() {
            return currentY;
        }

        @Override
        public int getPriorityBucket() {
            return priorityBucket;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = services().renderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
        }
    }

    private static final class TurboSpikerWaterfallOverlayChild extends AbstractObjectInstance {
        private final TurboSpikerBadnikInstance parent;

        TurboSpikerWaterfallOverlayChild(TurboSpikerBadnikInstance parent) {
            super(parent.getSpawn(), "TurboSpikerWaterfallOverlay");
            this.parent = parent;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (!parent.shouldShowWaterfallOverlay()) {
                setDestroyed(true);
            }
        }

        @Override
        public ObjectSpawn getSpawn() {
            return buildSpawnAt(parent.getX(), parent.getY());
        }

        @Override
        public int getX() {
            return parent.getX();
        }

        @Override
        public int getY() {
            return parent.getY();
        }

        @Override
        public int getPriorityBucket() {
            return PRIORITY_BUCKET_WATERFALL;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = services().renderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER_HIDDEN);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(0, parent.getX(), parent.getY(), false, false);
        }
    }
}
