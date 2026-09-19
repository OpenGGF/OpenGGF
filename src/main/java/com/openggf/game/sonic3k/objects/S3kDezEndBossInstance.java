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
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectRenderManager;
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
 * S&amp;K object $A7 — Death Egg act 2 gravity-ball end boss.
 *
 * <p>Owned ROM graph: {@code Obj_DEZEndBoss}, {@code ChildObjDat_7FC8C..7FCCE}
 * ({@code sonic3k.asm:169634-170932}). The encounter alternates hover and three ball-release
 * attacks, accepts eight returned-ball strikes, clears reverse gravity on defeat, performs the
 * arena redraw/music handoff, and requests the special {@code $1700} boss act.
 */
public final class S3kDezEndBossInstance extends AbstractBossInstance
        implements SpawnRewindRecreatable {
    static final int INITIAL_GRAPH_SIZE = 3;
    private static final int HIT_COUNT = 8;
    private static final int COLLISION_SIZE = 0x16;
    private static final int PRIORITY_BUCKET = 4;
    private static final int MIN_X = 0x3488;
    private static final int MAX_X = 0x3598;
    private static final int ENTRY_TIMER = 0xBF;
    private static final int HOVER_TIMER = 0xB3;
    private static final int RELEASE_TIMER = 0x3F;
    private static final int POST_FALL_TIMER = 0x3F;

    static final int PHASE_ENTRY = 0;
    static final int PHASE_HOVER = 2;
    static final int PHASE_RELEASE = 4;
    static final int PHASE_RECOVER = 6;
    static final int PHASE_DEFEAT_FALL = 8;
    static final int PHASE_DEFEAT_WAIT = 10;
    static final int PHASE_CAMERA_EXIT = 12;
    static final int PHASE_TRANSITION = 14;

    private int timer;
    private int attackCount;
    private int mappingFrame;
    private int hoverAngle;
    private boolean graphCreated;
    private boolean paletteLoaded;
    private boolean eventRaised;
    private boolean transitionRequested;

    public S3kDezEndBossInstance(ObjectSpawn spawn) {
        super(spawn, "DEZEndBoss");
    }

    @Override
    protected void initializeBossState() {
        state.x = spawn.x();
        state.y = spawn.y();
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
        state.hitCount = HIT_COUNT;
        state.routine = PHASE_ENTRY;
        state.yVel = 0x100;
        timer = ENTRY_TIMER;
    }

    @Override
    protected void updateBossLogic(int vIntRunCount, PlayableEntity player) {
        ensurePresentationAndGraph();
        switch (state.routine) {
            case PHASE_ENTRY -> updateEntry();
            case PHASE_HOVER -> updateHover(vIntRunCount, player);
            case PHASE_RELEASE -> updateRelease(vIntRunCount);
            case PHASE_RECOVER -> updateRecover();
            case PHASE_DEFEAT_FALL -> updateDefeatFall(vIntRunCount);
            case PHASE_DEFEAT_WAIT -> updateDefeatWait(vIntRunCount);
            case PHASE_CAMERA_EXIT -> updateCameraExit();
            case PHASE_TRANSITION -> updateTransition(player);
            default -> throw new IllegalStateException("DEZ end boss phase " + state.routine);
        }
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
    }

    private void updateEntry() {
        state.y += state.yVel >> 8;
        if (--timer >= 0) {
            return;
        }
        state.routine = PHASE_HOVER;
        timer = HOVER_TIMER;
        state.xVel = 0x100;
        services().playMusic(Sonic3kMusic.BOSS.id);
    }

    private void updateHover(int vIntRunCount, PlayableEntity player) {
        hoverAngle = (hoverAngle + 2) & 0xFF;
        state.y += (int) Math.round(Math.sin(hoverAngle * Math.PI / 128.0));
        state.x += state.xVel >> 8;
        if (state.x <= MIN_X || state.x >= MAX_X) {
            state.x = Math.max(MIN_X, Math.min(MAX_X, state.x));
            state.xVel = -state.xVel;
        }
        if ((vIntRunCount & 0x3F) == 0) {
            services().playSfx(Sonic3kSfx.WAVE_HOVER.id);
        }
        if (--timer >= 0) {
            return;
        }
        if (attackCount >= 3) {
            timer = 0x0F;
            return;
        }
        attackCount++;
        state.routine = PHASE_RELEASE;
        timer = RELEASE_TIMER;
        mappingFrame = 1;
        for (int role = 3; role < 5; role++) {
            int childRole = role;
            Component child = spawnChild(() -> new Component(this, childRole));
            if (child != null && !child.isDestroyed()) childComponents.add(child);
        }
    }

    private void updateRelease(int vIntRunCount) {
        mappingFrame = (vIntRunCount >>> 2) & 2;
        if (--timer >= 0) {
            return;
        }
        state.routine = PHASE_RECOVER;
        timer = 0x1F;
    }

    private void updateRecover() {
        if (--timer >= 0) {
            return;
        }
        state.routine = PHASE_HOVER;
        timer = HOVER_TIMER;
        mappingFrame = 0;
    }

    private void updateDefeatFall(int vIntRunCount) {
        state.yVel += 0x38;
        state.y += state.yVel >> 8;
        if ((vIntRunCount & 7) == 0) spawnDefeatExplosion();
        if (state.y < 0x318) {
            return;
        }
        state.y = 0x318;
        state.yVel = 0;
        state.routine = PHASE_DEFEAT_WAIT;
        timer = POST_FALL_TIMER;
        services().fadeOutMusic();
    }

    private void updateDefeatWait(int vIntRunCount) {
        if ((vIntRunCount & 7) == 0) spawnDefeatExplosion();
        if (!eventRaised && timer == 0x30) {
            dezState().raiseEventsFg4();
            eventRaised = true;
        }
        if (--timer > 0) {
            return;
        }
        services().playMusic(Sonic3kMusic.DEZ2.id);
        dezState().setCameraStoredMaxX(0x3620);
        services().camera().setMaxXTarget((short) 0x3620);
        state.routine = PHASE_CAMERA_EXIT;
    }

    private void updateCameraExit() {
        services().camera().setMinX(services().camera().getX());
        if ((services().camera().getX() & 0xFFFF) < 0x3620) {
            return;
        }
        services().camera().setMaxX((short) ((services().camera().getMaxX() & 0xFFFF) + 0x40));
        state.routine = PHASE_TRANSITION;
    }

    private void updateTransition(PlayableEntity player) {
        if (transitionRequested || player == null) {
            return;
        }
        int threshold = (services().camera().getX() & 0xFFFF) + 0x160;
        if ((player.getCentreX() & 0xFFFF) < threshold) {
            return;
        }
        transitionRequested = true;
        services().requestSessionSave(com.openggf.game.save.SaveReason.PROGRESSION_SAVE);
        services().requestZoneAndAct(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 0, true);
        setDestroyed(true);
    }

    private void ensurePresentationAndGraph() {
        if (!paletteLoaded) {
            loadPalette();
            paletteLoaded = true;
        }
        ObjectRenderManager manager = services().renderManager();
        if (manager != null && manager.getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.DEZ_END_BOSS);
            provider.ensureBossExplosionArtLoaded();
        }
        if (graphCreated) return;
        for (int role = 0; role < INITIAL_GRAPH_SIZE; role++) {
            int childRole = role;
            Component child = spawnChild(() -> new Component(this, childRole));
            if (child != null && !child.isDestroyed()) childComponents.add(child);
        }
        graphCreated = true;
    }

    private void loadPalette() {
        try {
            var rom = services().rom();
            if (rom == null) return;
            byte[] bytes = rom.readBytes(Sonic3kConstants.PAL_DEZ_END_BOSS_ADDR, 32);
            S3kPaletteWriteSupport.applyLine(services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(), services().graphicsManager(), "s3k.dez.endboss",
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 1, bytes);
        } catch (IOException failure) {
            throw new IllegalStateException("DEZ end-boss palette", failure);
        }
    }

    private S3kDezZoneRuntimeState dezState() {
        if (services().zoneRuntimeState() instanceof S3kDezZoneRuntimeState state) return state;
        throw new IllegalStateException("DEZ end boss requires S3kDezZoneRuntimeState");
    }

    @Override protected int getInitialHitCount() { return HIT_COUNT; }
    @Override protected int getCollisionSizeIndex() { return COLLISION_SIZE; }
    @Override protected int getBossHitSfxId() { return Sonic3kSfx.BOSS_HIT.id; }
    @Override protected int getBossExplosionSfxId() { return Sonic3kSfx.EXPLODE.id; }
    @Override protected boolean usesDefeatSequencer() { return false; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override protected void onHitTaken(int remainingHits) { }

    @Override
    protected void onDefeatStarted() {
        stopLevelTimerOnBossDefeat();
        services().gameState().setReverseGravityActive(false);
        state.routine = PHASE_DEFEAT_FALL;
        state.xVel = 0;
        state.yVel = 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = renderer();
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndex(mappingFrame, state.x, state.y, false, false, 1);
    }

    private PatternSpriteRenderer renderer() {
        ObjectRenderManager manager = tryServices() == null ? null : services().renderManager();
        return manager == null ? null : manager.getRenderer(Sonic3kObjectArtKeys.DEZ_END_BOSS);
    }

    int phase() { return state.routine; }
    void setPhaseTimerForTesting(int phase, int timer) { state.routine = phase; this.timer = timer; }

    static final class Component extends AbstractBossChild implements RewindRecreatable {
        private int role;
        private int localAngle;

        Component(S3kDezEndBossInstance parent, int role) {
            super(parent, "DEZEndBossComponent" + role, role < 2 ? 4 : 3,
                    Sonic3kObjectIds.DEZ_END_BOSS);
            this.role = role;
            this.localAngle = role * 0x40;
        }

        @Override
        public Component recreateForRewind(RewindRecreateContext ctx) {
            return RewindRecreateObjectLinks.nearestObject(
                            ctx, S3kDezEndBossInstance.class, true, 0x300)
                    .map(parent -> new Component(parent, ctx.spawn().subtype()))
                    .orElse(null);
        }

        @Override
        public void syncPositionWithParent() {
            if (!(parent instanceof S3kDezEndBossInstance boss) || parent.isDestroyed()) return;
            if (role == 0) {
                currentX = boss.getX(); currentY = boss.getY() + 0x14;
            } else if (role == 1) {
                currentX = boss.getX(); currentY = boss.getY() + 0x0C;
            } else if (role == 2) {
                localAngle = (localAngle + 4) & 0xFF;
                currentX = boss.getX() + (int) Math.round(Math.cos(localAngle * Math.PI / 128.0) * 48);
                currentY = boss.getY() + (int) Math.round(Math.sin(localAngle * Math.PI / 128.0) * 48);
            } else {
                currentX = boss.getX() + (role == 3 ? -0x18 : 0x18);
                currentY = boss.getY() + 0x18 + Math.min(0x40, localAngle++);
            }
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (!shouldUpdate(vIntRunCount)) return;
            syncPositionWithParent();
            updateDynamicSpawn();
            if (role >= 3 && localAngle > 0x80) setDestroyed(true);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (!(parent instanceof S3kDezEndBossInstance boss)) return;
            PatternSpriteRenderer renderer = boss.renderer();
            if (renderer == null || !renderer.isReady()) return;
            int frame = switch (role) { case 0 -> 0x14; case 1 -> 0x16; case 2 -> 4; default -> 0x11; };
            renderer.drawFrameIndex(frame, currentX, currentY, false, false, 1);
        }
    }
}
