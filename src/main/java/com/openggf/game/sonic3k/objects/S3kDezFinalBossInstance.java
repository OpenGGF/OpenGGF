package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.GameOverExit;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreateObjectLinks;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * Dynamic {@code Obj_DEZ3_Boss} owner for {@code $1700}.
 *
 * <p>The ROM actor is a coordinator for the run-in, arena shrink, laser body,
 * post-defeat chase and route exit (sonic3k.asm:170933-173053). This class keeps
 * those phases in one rewind-recreatable graph; the children represent the fixed
 * laser/core/crane actors rather than flattening their independently mutable state.
 */
public final class S3kDezFinalBossInstance extends AbstractBossInstance
        implements SpawnRewindRecreatable {
    public static final int GRAPH_SIZE = 8;
    private static final int HIT_COUNT = 8;
    private static final int COLLISION_SIZE = 0x0F;

    static final int RUN_IN = 0;
    static final int APPROACH = 2;
    static final int WAIT_RELEASE = 4;
    static final int DESCEND = 6;
    static final int FIGHT = 8;
    static final int ARENA_SHRINK = 10;
    static final int CHASE = 12;
    static final int EXIT = 14;

    private int timer;
    private int angle;
    private boolean graphCreated;
    private boolean playersPrepared;
    private boolean exitRequested;

    public S3kDezFinalBossInstance(ObjectSpawn spawn) {
        super(spawn, "DEZ3Boss");
    }

    @Override
    protected void initializeBossState() {
        state.x = spawn.x();
        state.y = spawn.y();
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
        state.hitCount = HIT_COUNT;
        state.routine = RUN_IN;
    }

    @Override
    protected void updateBossLogic(int vIntRunCount, PlayableEntity player) {
        ensureGraph();
        if (!playersPrepared) preparePlayers(player);
        switch (state.routine) {
            case RUN_IN -> runIn(player);
            case APPROACH -> approach(player);
            case WAIT_RELEASE -> waitRelease(player);
            case DESCEND -> descend();
            case FIGHT -> fight(vIntRunCount, player);
            case ARENA_SHRINK -> shrinkArena();
            case CHASE -> chase(player);
            case EXIT -> exit();
            default -> throw new IllegalStateException("DEZ3 boss phase " + state.routine);
        }
        S3kDezZoneRuntimeState dez = state();
        dez.setAct3BackgroundWord(0x02, state.x);
        dez.setAct3BackgroundWord(0x04, state.y);
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
    }

    private void preparePlayers(PlayableEntity player) {
        playersPrepared = true;
        placeRunInPlayer(player, 0x30, state().playerCharacter() == PlayerCharacter.TAILS_ALONE);
        placeRunInPlayer(nativeP2(), 0x10, true);
    }

    private static void placeRunInPlayer(PlayableEntity player, int x, boolean tails) {
        if (player == null) return;
        NativePositionOps.writeXPosResetSubpixel((AbstractPlayableSprite) player, x);
        NativePositionOps.writeYPosPreserveSubpixel(player, 0xCD + (tails ? 4 : 0));
        player.setXSpeed((short) 0x600);
        player.setGSpeed((short) 0x600);
        if (player instanceof AbstractPlayableSprite sprite) {
            ObjectControlState.nativeBit7FullControl().applyTo(sprite);
        }
    }

    private void runIn(PlayableEntity player) {
        advancePlayers(player, 6);
        int leadX = player == null ? 0 : player.getCentreX() & 0xFFFF;
        if (leadX < (services().camera().getX() & 0xFFFF) + 0x98) return;
        state.routine = APPROACH;
        services().playMusic(Sonic3kMusic.FINAL_BOSS.id);
    }

    private void approach(PlayableEntity player) {
        advancePlayers(player, 6);
        if (player == null || (player.getCentreX() & 0xFFFF) < 0x360) return;
        stopPlayers(player);
        state.routine = WAIT_RELEASE;
        timer = 0x20;
    }

    private void waitRelease(PlayableEntity player) {
        if (--timer >= 0) return;
        releasePlayers(player);
        state.routine = DESCEND;
        timer = 0xBF;
        state.yVel = -0x80;
        state().raiseEventsFg5();
    }

    private void descend() {
        state.y += state.yVel >> 8;
        if (--timer >= 0) return;
        state.routine = FIGHT;
        state.xVel = 1;
        state.yVel = 0;
        services().gameState().setScreenShakeActive(true);
    }

    private void fight(int vIntRunCount, PlayableEntity player) {
        state.x += state.xVel;
        angle = (angle + 2) & 0x7F;
        state.y = 0x128 - (int) Math.round(Math.sin(angle * Math.PI / 64.0) * 16);
        if ((vIntRunCount & 0x3F) == 0) {
            services().playSfx(Sonic3kSfx.LASER.id);
            int targetX = player == null ? state.x - 1 : player.getCentreX() & 0xFFFF;
            int direction = targetX < state.x ? -1 : 1;
            spawnFreeChild(() -> new LaserHazard(state.x, state.y + 0x18, direction));
        }
        int target = player == null ? state.x : player.getCentreX() & 0xFFFF;
        if (state.x > target + 0x80) state.xVel = -1;
        else if (state.x < target - 0x80) state.xVel = 1;
    }

    private void shrinkArena() {
        state().setAct3BackgroundWord(0x00, 0x6C0);
        state.routine = CHASE;
        state.xVel = 0x500;
        timer = 0x100;
    }

    private void chase(PlayableEntity player) {
        state.x += state.xVel >> 8;
        state.y = (services().camera().getY() & 0xFFFF) + 0x50
                + (int) Math.round(Math.sin((angle += 4) * Math.PI / 128.0) * 8);
        services().camera().setMinX(services().camera().getX());
        if ((player != null && (player.getCentreX() & 0xFFFF) > (state.x + 0x180))
                || --timer <= 0) {
            state().setAct3BackgroundWord(0x00, 0);
            state.routine = EXIT;
            timer = 0x60;
        }
    }

    private void exit() {
        if (exitRequested || --timer > 0) return;
        exitRequested = true;
        services().requestSessionSave(com.openggf.game.save.SaveReason.PROGRESSION_SAVE);
        PlayerCharacter character = state().playerCharacter();
        if ((character == PlayerCharacter.SONIC_AND_TAILS
                || character == PlayerCharacter.SONIC_ALONE)
                && services().gameState().getEmeraldCount() == 7) {
            services().requestZoneAndAct(Sonic3kZoneIds.ZONE_DDZ, 0, true);
        } else if (character != PlayerCharacter.KNUCKLES) {
            services().requestZoneAndAct(Sonic3kZoneIds.ZONE_INTRO_ENDING, 1, true);
        } else {
            // loc_8041E writes GameMode_SegaScreen. The engine's existing title-screen
            // transition port owns the fade and mode boundary for this equivalent path.
            services().levelManager().requestGameOverExit(GameOverExit.TITLE_SCREEN);
        }
        ObjectLifetimeOps.destroyLatched(this);
    }

    private void advancePlayers(PlayableEntity main, int pixels) {
        if (main instanceof AbstractPlayableSprite sprite)
            NativePositionOps.addXPosPreserveSubpixel(sprite, pixels);
        if (nativeP2() instanceof AbstractPlayableSprite sidekick)
            NativePositionOps.addXPosPreserveSubpixel(sidekick, pixels);
    }

    private void stopPlayers(PlayableEntity main) {
        if (main != null) { main.setXSpeed((short) 0); main.setGSpeed((short) 0); }
        PlayableEntity sidekick = nativeP2();
        if (sidekick != null) {
            sidekick.setXSpeed((short) 0); sidekick.setGSpeed((short) 0);
        }
    }

    private void releasePlayers(PlayableEntity main) {
        if (main != null) releasePlayer(main);
        releasePlayer(nativeP2());
    }

    private static void releasePlayer(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            ObjectControlState.none().applyTo(sprite);
        }
    }

    private PlayableEntity nativeP2() {
        return services().playerQuery().nativeP2OrNull();
    }

    private void ensureGraph() {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager != null && renderManager.getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_MISC);
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_MASTER_EMERALD);
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_DEBRIS);
        }
        if (graphCreated) return;
        for (int role = 0; role < GRAPH_SIZE; role++) {
            int childRole = role;
            Component child = spawnChild(() -> new Component(this, childRole));
            if (child != null && !child.isDestroyed()) childComponents.add(child);
        }
        graphCreated = true;
    }

    @Override
    protected void recreateConstructionChildrenForRewind() {
        ensureGraph();
    }

    private S3kDezZoneRuntimeState state() {
        if (services().zoneRuntimeState() instanceof S3kDezZoneRuntimeState dez) return dez;
        throw new IllegalStateException("DEZ3 boss requires its runtime state");
    }

    @Override protected int getInitialHitCount() { return HIT_COUNT; }
    @Override protected int getCollisionSizeIndex() { return COLLISION_SIZE; }
    @Override protected int getBossHitSfxId() { return Sonic3kSfx.BOSS_HIT.id; }
    @Override protected int getBossExplosionSfxId() { return Sonic3kSfx.EXPLODE.id; }
    @Override protected boolean usesDefeatSequencer() { return false; }
    @Override protected void onHitTaken(int remainingHits) { }
    @Override protected void onDefeatStarted() {
        stopLevelTimerOnBossDefeat();
        state.routine = ARENA_SHRINK;
    }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return 4; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = renderer(Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_MISC);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(
                state.routine >= CHASE ? 20 : 0, state.x, state.y, false, false, 1);
    }

    private PatternSpriteRenderer renderer(String key) {
        ObjectRenderManager manager = tryServices() == null ? null : services().renderManager();
        return manager == null ? null : manager.getRenderer(key);
    }

    int phase() { return state.routine; }
    void setPhaseForTesting(int phase, int timer) { state.routine = phase; this.timer = timer; }

    static final class Component extends AbstractBossChild implements RewindRecreatable {
        private int role;
        private int localAngle;

        Component(S3kDezFinalBossInstance parent, int role) {
            super(parent, "DEZ3BossComponent" + role, role < 4 ? 4 : 3,
                    Sonic3kObjectIds.DEZ_END_BOSS);
            this.role = role;
            localAngle = role * 0x20;
        }

        @Override public Component recreateForRewind(RewindRecreateContext context) {
            return RewindRecreateObjectLinks.nearestObject(
                    context, S3kDezFinalBossInstance.class, true, 0x400)
                    .map(parent -> new Component(parent, context.spawn().subtype())).orElse(null);
        }

        @Override public void syncPositionWithParent() {
            if (!(parent instanceof S3kDezFinalBossInstance boss) || parent.isDestroyed()) return;
            if (role == 6 && boss.phase() >= CHASE) {
                currentX = boss.getX() - 0x50;
                currentY = (boss.services().camera().getY() & 0xFFFF) + 0xCF;
                return;
            }
            localAngle = (localAngle + (role < 4 ? 2 : 4)) & 0xFF;
            int radius = role < 4 ? 0x28 : 0x50;
            currentX = boss.getX() + (int) Math.round(Math.cos(localAngle * Math.PI / 128.0) * radius);
            currentY = boss.getY() + (int) Math.round(Math.sin(localAngle * Math.PI / 128.0) * radius);
        }

        @Override public void update(int vIntRunCount, PlayableEntity player) {
            if (!shouldUpdate(vIntRunCount)) return;
            syncPositionWithParent();
            updateDynamicSpawn();
        }

        @Override public void appendRenderCommands(List<GLCommand> commands) {
            if (!(parent instanceof S3kDezFinalBossInstance boss)) return;
            if (role == 6 && boss.phase() < CHASE) return;
            String key = role == 6 ? Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_MASTER_EMERALD
                    : role == 7 && boss.phase() >= ARENA_SHRINK
                    ? Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_DEBRIS
                    : Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_MISC;
            PatternSpriteRenderer renderer = boss.renderer(key);
            if (renderer != null && renderer.isReady())
                renderer.drawFrameIndex(role == 6 ? 0 : role == 7 ? 1 : role + 1,
                        currentX, currentY, false, false, 1);
        }
    }

    /** The damaging laser/fireball pass created by {@code loc_807BC-loc_809F4}. */
    static final class LaserHazard extends AbstractObjectInstance
            implements TouchResponseProvider, SpawnRewindRecreatable {
        private static final int COLLISION_FLAGS = 0xAC;
        private int xFixed;
        private int yFixed;
        private int xVelocity;
        private int age;

        LaserHazard(int x, int y, int direction) {
            this(new ObjectSpawn(x, y, Sonic3kObjectIds.DEZ_END_BOSS,
                    direction < 0 ? 0 : 1, 0, false, -1));
        }

        public LaserHazard(ObjectSpawn spawn) {
            super(spawn, "DEZ3BossLaser");
            xFixed = spawn.x() << 16;
            yFixed = spawn.y() << 16;
            xVelocity = (spawn.subtype() == 0 ? -1 : 1) * 0x300;
        }

        @Override public void update(int vIntRunCount, PlayableEntity player) {
            xFixed += xVelocity << 8;
            age++;
            yFixed += (int) Math.round(Math.sin(age * Math.PI / 16.0) * 0x1800);
            int cameraX = services().camera().getX() & 0xFFFF;
            int width = services().camera().getWidth() & 0xFFFF;
            if (age > 0xC0 || getX() < cameraX - 0x100 || getX() > cameraX + width + 0x100)
                ObjectLifetimeOps.destroyLatched(this);
        }

        @Override public int getX() { return xFixed >> 16; }
        @Override public int getY() { return yFixed >> 16; }
        @Override public int getCollisionFlags() { return COLLISION_FLAGS; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x280); }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_MISC);
            if (renderer != null && renderer.isReady())
                renderer.drawFrameIndex(0x1A + ((age >>> 2) & 1), getX(), getY(),
                        xVelocity < 0, false, 1);
        }
    }
}
