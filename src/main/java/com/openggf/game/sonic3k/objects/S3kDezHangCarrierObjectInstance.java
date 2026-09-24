package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/** SKL {@code $4C}, {@code Obj_DEZHangCarrier} (sonic3k.asm:92885-93035). */
public final class S3kDezHangCarrierObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private int xFixed, yFixed;
    private int xVelocity, yVelocity;
    private int travelFrames;
    private int p1Cooldown, p2Cooldown;
    private boolean p1Captured, p2Captured;
    private int phase;

    public S3kDezHangCarrierObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZHangCarrier");
        xFixed = spawn.x() << 16;
        yFixed = spawn.y() << 16;
        travelFrames = (spawn.subtype() & 0xFF) << 2;
    }

    @Override public void update(int vIntRunCount, PlayableEntity ignored) {
        if (p1Captured && phase == 0) phase = 1;
        if (phase == 1) {
            move();
            yVelocity = (short) (yVelocity - 8);
            if (tryServices() != null && services().levelManager() != null) {
                int distance = ObjectTerrainUtils.checkCeilingDist(getX(), getY(), 0x14).distance();
                if (distance < 0) {
                    yFixed -= distance << 16;
                    yVelocity = 0;
                    xVelocity = (spawn.renderFlags() & 1) == 0 ? 0x200 : -0x200;
                    phase = 2;
                }
            }
        } else if (phase == 2 && travelFrames != 0) {
            travelFrames--;
            move();
        }
        processPlayers();
    }

    private void move() { xFixed += (short) xVelocity << 8; yFixed += (short) yVelocity << 8; }

    private void processPlayers() {
        if (tryServices() == null || services().playerQuery() == null) return;
        List<PlayableEntity> players = services().playerQuery().playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2);
        for (int i = 0; i < players.size(); i++) if (players.get(i) instanceof AbstractPlayableSprite p) process(p, i != 0);
    }

    void process(AbstractPlayableSprite player, boolean p2) {
        boolean captured = p2 ? p2Captured : p1Captured;
        int cooldown = p2 ? p2Cooldown : p1Cooldown;
        if (captured) {
            if (!isOnScreen(0) || player.isJumpJustPressed()) {
                release(player, p2, player.isJumpJustPressed());
                return;
            }
            NativePositionOps.writeXPosPreserveSubpixel(player, getX());
            NativePositionOps.writeYPosPreserveSubpixel(player, getY() + 0x28);
            if ((levelFrame() & 0xF) == 0 && tryServices() != null) services().playSfx(Sonic3kSfx.RISING.id);
            return;
        }
        if (cooldown != 0) {
            if (p2) p2Cooldown--; else p1Cooldown--;
            return;
        }
        int dx = (player.getCentreX() - getX() + 0x10) & 0xFFFF;
        int dy = (player.getCentreY() - getY() - 0x28) & 0xFFFF;
        if (dx >= 0x20 || dy >= 0x18 || player.isObjectControlled()) return;
        player.setXSpeed((short) 0); player.setYSpeed((short) 0); player.setGSpeed((short) 0);
        NativePositionOps.writeXPosPreserveSubpixel(player, getX());
        NativePositionOps.writeYPosPreserveSubpixel(player, getY() + 0x28);
        player.setAnimationId(0x14); player.setAir(false);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        if (p2) p2Captured = true; else p1Captured = true;
        if (tryServices() != null) services().playSfx(Sonic3kSfx.SWITCH.id);
    }

    private void release(AbstractPlayableSprite p, boolean p2, boolean jump) {
        ObjectControlState.none().applyTo(p);
        if (jump) {
            if (p.isLeftPressed()) p.setXSpeed((short) -0x200);
            if (p.isRightPressed()) p.setXSpeed((short) 0x200);
            p.setYSpeed((short) -0x380); p.setAir(true); p.setJumping(true);
            p.applyRollingRadii(false); p.setAnimationId(2); p.setRolling(true);
            p.setRollingJump(false); p.setFlipAngle(0);
        }
        if (p2) { p2Captured = false; p2Cooldown = jump && !(p.isLeftPressed() || p.isRightPressed()) ? 18 : 60; }
        else { p1Captured = false; p1Cooldown = jump && !(p.isLeftPressed() || p.isRightPressed()) ? 18 : 60; }
    }

    private int levelFrame() { return services().levelManager() == null ? 0 : services().levelManager().getFrameCounter(); }
    @Override public int getX() { return xFixed >> 16; }
    @Override public int getY() { return yFixed >> 16; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x80); }
    @Override public int getOnScreenHalfWidth() { return 0x18; }
    @Override public int getOnScreenHalfHeight() { return 0x14; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer r = getRenderer(Sonic3kObjectArtKeys.DEZ_HANG_CARRIER);
        if (r != null && r.isReady()) r.drawFrameIndex(0, getX(), getY(), (spawn.renderFlags() & 1) != 0, false);
    }
    boolean capturedForTest(boolean p2) { return p2 ? p2Captured : p1Captured; }
    int travelFramesForTest() { return travelFrames; }
}
