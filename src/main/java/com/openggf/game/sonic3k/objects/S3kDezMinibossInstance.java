package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreateObjectLinks;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;

import java.io.IOException;
import java.util.List;

/**
 * S&amp;K object $A6 — Death Egg act 1 gravity-platform miniboss and act-change carrier.
 *
 * <p>This ports the owning graph and phase boundaries from {@code Obj_DEZMiniboss}
 * ({@code sonic3k.asm:167750-169630}). The ROM keeps the root alive after the eighth hit:
 * it raises {@code Events_fg_4}, finishes the results flow, then carries player one into
 * act 2. Consequently this object deliberately does not use the generic boss deletion
 * sequencer.
 */
public final class S3kDezMinibossInstance extends AbstractBossInstance
        implements SpawnRewindRecreatable {
    static final int CHILD_COUNT = 9; // one hit proxy plus Child6_Simple's eight arms
    private static final int HIT_COUNT = 8;
    private static final int COLLISION_SIZE = 0x15;
    private static final int PRIORITY_BUCKET = 5; // ObjDat priority $280
    private static final int ARENA_MIN_X = 0x3680;
    private static final int ARENA_MAX_X = 0x36C0;
    private static final int ARENA_Y = 0x028C;
    private static final int INITIAL_WAIT = 0x7F;
    private static final int DEFEAT_WAIT = 0x5F;
    private static final int RESULTS_WAIT = 0x1F;
    private static final int TRANSITION_LAND_Y = 0x03AC;

    static final int PHASE_ENTRY = 0;
    static final int PHASE_FIGHT = 2;
    static final int PHASE_DEFEAT_WAIT = 4;
    static final int PHASE_RESULTS = 6;
    static final int PHASE_TRANSITION_DROP = 8;
    static final int PHASE_ACT2_WAIT = 10;
    static final int PHASE_DONE = 12;

    private int timer;
    private int angle;
    private int angularVelocity;
    private int mappingFrame;
    private boolean graphCreated;
    private boolean foregroundRaised;
    private boolean transitionRaised;
    private boolean paletteOneLoaded;
    private boolean paletteTwoLoaded;

    public S3kDezMinibossInstance(ObjectSpawn spawn) {
        super(spawn, "DEZMiniboss");
    }

    @Override
    protected void initializeBossState() {
        state.x = spawn.x();
        state.y = spawn.y();
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
        state.hitCount = HIT_COUNT;
        state.routine = PHASE_ENTRY;
        timer = INITIAL_WAIT;
        angle = 0x80;
        angularVelocity = 0x20;
        mappingFrame = 0;
    }

    @Override
    protected void updateBossLogic(int vIntRunCount, PlayableEntity player) {
        ensurePresentationAndGraph();
        switch (state.routine) {
            case PHASE_ENTRY -> updateEntry();
            case PHASE_FIGHT -> updateFight(vIntRunCount, player);
            case PHASE_DEFEAT_WAIT -> updateDefeatWait(vIntRunCount);
            case PHASE_RESULTS -> updateResults();
            case PHASE_TRANSITION_DROP -> updateTransitionDrop(player);
            case PHASE_ACT2_WAIT -> updateAct2Wait();
            case PHASE_DONE -> ObjectLifetimeOps.destroyLatched(this);
            default -> throw new IllegalStateException("DEZ miniboss phase " + state.routine);
        }
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
    }

    private void updateEntry() {
        lockArena();
        if (--timer >= 0) {
            return;
        }
        state.routine = PHASE_FIGHT;
        services().playMusic(Sonic3kMusic.MINIBOSS.id);
    }

    private void updateFight(int vIntRunCount, PlayableEntity player) {
        lockArena();
        // loc_7E0A6 / sub_7EB8E: hover horizontally toward the nearest player while
        // the two articulated halves advance around the root's angle accumulator.
        int targetX = player == null ? state.x : player.getCentreX();
        if ((vIntRunCount & 0x1F) == 0) {
            state.xVel = targetX < state.x ? -0x80 : 0x80;
        }
        state.x = Math.max(ARENA_MIN_X, Math.min(ARENA_MAX_X, state.x + (state.xVel >> 8)));
        angle = (angle + angularVelocity) & 0xFFFF;
        mappingFrame = 1 + ((angle >>> 13) & 3);
        if ((vIntRunCount & 0x3F) == 0) {
            services().playSfx(Sonic3kSfx.WAVE_HOVER.id);
        }
    }

    private void updateDefeatWait(int vIntRunCount) {
        lockArena();
        mappingFrame = 0x15 + ((vIntRunCount >>> 2) & 1);
        if (!foregroundRaised) {
            dezState().raiseEventsFg4();
            foregroundRaised = true;
        }
        if ((vIntRunCount & 7) == 0) {
            spawnDefeatExplosion();
        }
        if (--timer >= 0) {
            return;
        }
        state.routine = PHASE_RESULTS;
        timer = RESULTS_WAIT;
        state.xVel = -0x80;
        state.yVel = 0x40;
    }

    private void updateResults() {
        state.x += state.xVel >> 8;
        state.y += state.yVel >> 8;
        if (--timer >= 0) {
            return;
        }
        state.routine = PHASE_TRANSITION_DROP;
        state.yVel = 0x100;
    }

    private void updateTransitionDrop(PlayableEntity player) {
        if (!transitionRaised) {
            // loc_7E342: the surviving chain wakes DEZ2 stage 0 and opens the vertical
            // camera span. The foreground handler consumes the event on its next pass.
            dezState().raiseEventsFg4();
            services().camera().setMinYTarget((short) 0);
            services().camera().setMaxYTarget((short) 0x2000);
            transitionRaised = true;
        }
        state.y += state.yVel >> 8;
        state.yVel += 0x38;
        if (player != null && player.getCentreY() >= 0x360 && state.y < player.getCentreY()) {
            state.x = player.getCentreX();
            state.y = player.getCentreY();
        }
        if (state.y < TRANSITION_LAND_Y) {
            return;
        }
        state.y = TRANSITION_LAND_Y;
        state.yVel = 0;
        state.routine = PHASE_ACT2_WAIT;
        timer = (2 * 60) - 1;
    }

    private void updateAct2Wait() {
        if (--timer >= 0) {
            return;
        }
        loadPalette(Sonic3kConstants.PAL_DEZ_MINIBOSS_2_ADDR, 2,
                "s3k.dez.miniboss.transition");
        paletteTwoLoaded = true;
        state.routine = PHASE_DONE;
    }

    private void lockArena() {
        services().camera().setMinX((short) ARENA_MIN_X);
        services().camera().setMaxX((short) ARENA_MAX_X);
        services().camera().setMinY((short) ARENA_Y);
        services().camera().setMaxY((short) ARENA_Y);
    }

    private void ensurePresentationAndGraph() {
        if (!paletteOneLoaded) {
            loadPalette(Sonic3kConstants.PAL_DEZ_MINIBOSS_1_ADDR, 1, "s3k.dez.miniboss");
            paletteOneLoaded = true;
        }
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager != null && renderManager.getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.DEZ_MINIBOSS);
            provider.ensureBossExplosionArtLoaded();
        }
        if (!graphCreated) {
            for (int role = 0; role < CHILD_COUNT; role++) {
                int childRole = role;
                Component child = spawnChild(() -> new Component(this, childRole));
                if (child != null && !child.isDestroyed()) {
                    childComponents.add(child);
                }
            }
            graphCreated = true;
        }
    }

    @Override
    protected void recreateConstructionChildrenForRewind() {
        ensurePresentationAndGraph();
    }

    private void loadPalette(int address, int line, String owner) {
        try {
            var rom = services().rom();
            if (rom == null) {
                return;
            }
            byte[] bytes = rom.readBytes(address, 32);
            S3kPaletteWriteSupport.applyLine(services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(), services().graphicsManager(), owner,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, line, bytes);
        } catch (IOException failure) {
            throw new IllegalStateException("DEZ miniboss palette at $" + Integer.toHexString(address), failure);
        }
    }

    private S3kDezZoneRuntimeState dezState() {
        if (services().zoneRuntimeState() instanceof S3kDezZoneRuntimeState state) {
            return state;
        }
        throw new IllegalStateException("DEZ miniboss requires S3kDezZoneRuntimeState");
    }

    @Override protected int getInitialHitCount() { return HIT_COUNT; }
    @Override protected int getCollisionSizeIndex() { return COLLISION_SIZE; }
    @Override protected int getBossHitSfxId() { return Sonic3kSfx.BOSS_HIT.id; }
    @Override protected int getBossExplosionSfxId() { return Sonic3kSfx.EXPLODE.id; }
    @Override protected boolean usesDefeatSequencer() { return false; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }

    @Override
    protected void onHitTaken(int remainingHits) {
        angularVelocity = remainingHits < 4 ? 0x100 : 0x80;
    }

    @Override
    protected void onDefeatStarted() {
        stopLevelTimerOnBossDefeat();
        state.routine = PHASE_DEFEAT_WAIT;
        state.invulnerable = false;
        state.invulnerabilityTimer = 0;
        timer = DEFEAT_WAIT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer();
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, state.x, state.y, false, false, 1);
        }
    }

    private PatternSpriteRenderer getRenderer() {
        ObjectRenderManager manager = tryServices() == null ? null : services().renderManager();
        return manager == null ? null : manager.getRenderer(Sonic3kObjectArtKeys.DEZ_MINIBOSS);
    }

    int angle() { return angle; }
    int phase() { return state.routine; }

    /** Fixed encounter components from ChildObjDat_7EF8E/7EF96. */
    static final class Component extends AbstractBossChild implements RewindRecreatable {
        private int role;

        Component(S3kDezMinibossInstance parent, int role) {
            super(parent, "DEZMinibossComponent" + role, role == 0 ? 5 : 3,
                    Sonic3kObjectIds.DEZ_MINIBOSS);
            this.role = role;
        }

        @Override
        public Component recreateForRewind(RewindRecreateContext ctx) {
            return RewindRecreateObjectLinks.nearestObject(
                            ctx, S3kDezMinibossInstance.class, true, 0x200)
                    .map(parent -> new Component(parent, ctx.spawn().subtype()))
                    .orElse(null);
        }

        @Override
        public void syncPositionWithParent() {
            if (!(parent instanceof S3kDezMinibossInstance boss) || parent.isDestroyed()) {
                return;
            }
            if (role == 0) {
                currentX = boss.getX();
                currentY = boss.getY() - 4;
                return;
            }
            int phase = (boss.angle() >>> 8) + ((role - 1) * 0x20);
            currentX = boss.getX() + ((int) Math.round(Math.cos(phase * Math.PI / 128.0) * 32));
            currentY = boss.getY() + ((int) Math.round(Math.sin(phase * Math.PI / 128.0) * 12));
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (!shouldUpdate(vIntRunCount)) {
                return;
            }
            syncPositionWithParent();
            updateDynamicSpawn();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (!(parent instanceof S3kDezMinibossInstance boss)) {
                return;
            }
            PatternSpriteRenderer renderer = boss.getRenderer();
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            int frame = role == 0 ? 2 : 0x15 + ((role - 1) & 2) / 2;
            renderer.drawFrameIndex(frame, currentX, currentY, false, false, 1);
        }
    }
}
