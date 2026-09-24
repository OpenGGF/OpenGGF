package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.Direction;

import java.util.List;

/** Obj_KnuxFinalBossCrane / loc_7CA3A..loc_7CC26, the placed SSZ2 $B2 ship. */
public final class SszCraneShip extends AbstractObjectInstance implements SpawnRewindRecreatable, SszCranePose {
    private int code;
    private int xFixed;
    private int yFixed;
    private int xVelocity;
    private int yVelocity;
    private int flags;
    private int timer;
    private boolean flipX;
    private boolean visible;
    private int priority;
    private final SszRuntimeArtRequest craneArt = new SszRuntimeArtRequest();

    public SszCraneShip(ObjectSpawn spawn) {
        super(spawn, "KnuxFinalBossCrane");
        xFixed = spawn.x() << 16;
        yFixed = spawn.y() << 16;
    }

    @Override public void update(int clock, PlayableEntity ignored) {
        visible = false;
        var state = S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
        var player = services().spriteManager().getMainPlayable();
        if (state == null || player == null) return;
        craneArt.service(services());
        switch (code) {
            case 0 -> {
                services().camera().setMaxX(services().camera().getMinX());
                SszRuntimeArtRequest.loadShipPlc(services());
                code = 0x7CA3A;
            }
            case 0x7CA3A -> {
                if (!player.getAir()) return;
                code = 0x7CAAA;
                xVelocity = -0x80;
                yVelocity = 0xC0; // Swing_Setup1; $38 bit 0 starts clear.
                spawnFreeChild(() -> new SongFadeTransitionInstance(90, Sonic3kMusic.BOSS.id));
                spawnChild(() -> new SszCraneShipDecoration(child(0, -0x1C, 0), this));
                spawnChild(() -> new SszCraneClaw(child(0, 0x23, 0), this));
                spawnChild(() -> new SszCraneShipDecoration(child(0x1E, 0, 1), this));
                craneArt.submit(services(), 0x1607D8, 0x4A7);
                visible = true;
            }
            case 0x7CAAA -> {
                swingAndMove();
                if (getX() < 0x120) {
                    code = 0x7CAD2;
                    xVelocity = 0;
                    state.setCutsceneFlag(0);
                }
                visible = true;
            }
            case 0x7CAD2, 0x7CAE6, 0x7CAF8 -> {
                // These code pointers fall through their remaining flag tests.
                if (code == 0x7CAD2 && state.cutsceneFlag(1)) {
                    code = 0x7CAE6;
                    xVelocity = -0x80;
                }
                if (code != 0x7CAF8 && state.cutsceneFlag(2)) {
                    code = 0x7CAF8;
                    xVelocity = 0;
                }
                if (state.cutsceneFlag(3)) {
                    code = 0x7CB28;
                    xVelocity = 0x80;
                    flipX = true;
                    player.setDirection(Direction.RIGHT);
                }
                swingAndMove();
                visible = true;
            }
            case 0x7CB28 -> {
                swingAndMove();
                if (getX() >= 0x120) {
                    code = 0x7CB64;
                    xVelocity = 0;
                    timer = 59;
                    spawnFreeChild(() -> new SongFadeTransitionInstance(90, Sonic3kMusic.FINAL_BOSS.id));
                }
                visible = true;
            }
            case 0x7CB64 -> {
                timer = (short) (timer - 1);
                if (timer == 0) {
                    code = 0x7CBA4;
                    services().camera().setScrollLocked(true);
                    spawnFreeChild(() -> new SszCraneCameraPan(child(0, 0, 0)));
                    var boss = spawnFreeChild(() -> new SszMechaSonicObjectInstance(child(0, 0, 0)));
                    // Failed AllocateObject leaves a1 at the last searched SST.
                    state.setCarriedObjectSlot(boss == null
                            ? services().objectManager().getLastDynamicSlotExclusive() - 1 : boss.getSlotIndex());
                }
                swingAndMove();
                visible = true;
            }
            case 0x7CBA4, 0x7CBCE -> {
                var boss = occupant(state.carriedObjectSlot());
                if (code == 0x7CBA4 && inRange(boss, 0x78)) {
                    code = 0x7CBCE;
                    spawnFreeChild(() -> new SszCranePlayerRelease(child(0, 0, 0)));
                    state.setCutsceneFlag(5);
                }
                // loc_7CBA4 falls through into loc_7CBCE even before the first range succeeds.
                swingAndMove();
                if (inRange(boss, 0x30)) {
                    code = 0x8488A; // Wait_Draw
                    timer = 0x3F;
                    priority = 1; // move.w #$80,priority(a0)
                    spawnChild(() -> new DezMinibossExplosionController(getX(), getY(), 0));
                }
                visible = true;
            }
            case 0x8488A -> {
                timer = (short) (timer - 1);
                if (timer >= 0) { visible = true; break; }
                for (int index = 0; index < 4; index++) {
                    int subtype = index * 2;
                    if (spawnChild(() -> new SszCraneShipDebris(child(0, 0, subtype))) == null) break;
                }
                flags |= 0x30;
                visible = true; // Wait_Draw still draws after Obj_Wait calls loc_7CC26.
                code = -1; // Go_Delete_Sprite retires on the following pass.
            }
            case -1 -> ObjectLifetimeOps.deleteNoRespawn(this);
            default -> throw new IllegalStateException("Unknown SSZ crane code " + code);
        }
        updateDynamicSpawn(getX(), getY());
    }

    private ObjectSpawn child(int dx, int dy, int subtype) {
        return new ObjectSpawn(getX() + dx, getY() + dy, 0, subtype, 0, false, 0);
    }

    private AbstractObjectInstance occupant(int slot) {
        for (var object : services().objectManager().getActiveObjects()) {
            if (object instanceof AbstractObjectInstance candidate
                    && candidate.getSlotIndex() == slot && !candidate.isDestroyed()) return candidate;
        }
        return null;
    }

    private boolean inRange(AbstractObjectInstance other, int height) {
        // Check_InMyRange uses signed word bounds, with the upper edge excluded.
        int x = other == null ? 0 : (short) other.getX();
        int y = other == null ? 0 : (short) other.getY();
        return x >= (short) (getX() - 0x20) && x < (short) (getX() + 0x20)
                && y >= (short) getY() && y < (short) (getY() + height);
    }

    private void swingAndMove() {
        // Swing_UpAndDown: the limit pass applies the reversed acceleration too.
        int acceleration = 0x10;
        int velocity = yVelocity;
        int limit = 0xC0;
        if ((flags & 1) == 0) {
            acceleration = -acceleration;
            velocity = (short) (velocity + acceleration);
            limit = -limit;
            if (velocity > limit) {
                yVelocity = velocity;
                move();
                return;
            }
            flags |= 1;
            acceleration = -acceleration;
            limit = -limit;
        }
        velocity = (short) (velocity + acceleration);
        if (velocity >= limit) {
            flags &= ~1;
            velocity = (short) (velocity - acceleration);
        }
        yVelocity = velocity;
        move();
    }

    private void move() { xFixed += xVelocity << 8; yFixed += yVelocity << 8; }
    int xVelocity() { return xVelocity; }
    boolean parentFlag(int bit) { return (flags & (1 << bit)) != 0; }
    @Override public boolean craneFlipped() { return flipX; }
    @Override public boolean craneRetiring() { return code == -1; }
    @Override public int getX() { return xFixed >>> 16; }
    @Override public int getY() { return yFixed >>> 16; }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return priority; }
    @Override public int getOnScreenHalfWidth() { return 0x1C; }
    @Override public int getOnScreenHalfHeight() { return 0x20; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (visible && renderer != null && renderer.isReady()) renderer.drawFrameIndex(0xA, getX(), getY(), flipX, false);
    }
}
