package com.openggf.game.sonic3k.objects;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.*;
import java.util.List;
/**
 * Locked-on Obj_FBZSpinningPole ($7B), sonic3k.asm $3BD9A-$3C19A. Player
 * mappings/DPLCs are owned by the playable sprite.
 */
public final class FbzSpinningPoleObjectInstance
    extends AbstractObjectInstance implements SpawnRewindRecreatable {
  private static final int[] FRAMES = {0x5C, 0x5C, 0x5D, 0x5D, 0x5D, 0x5E,
                                       0x5E, 0x5E, 0x5F, 0x5F, 0x60, 0x60,
                                       0x60, 0x61, 0x61, 0x61};
  private static final int[] XOFF = {0x18,  0x18, 4,     4,     4,     -0xA,
                                     -0xA,  -0xA, -0x18, -0x18, -0x18, -0x18,
                                     -0x18, 0xA,  0xA,   0xA};
  private static final ObjectPlayerQuery
      .PlayerVisitor<FbzSpinningPoleObjectInstance> VISIT = (pole, e) -> {
    if (e instanceof AbstractPlayableSprite p)
      pole.updatePlayer(p, pole.players.slot(p));
  };
  private final int height;
  private final FbzParticipantStateTable players =
      new FbzParticipantStateTable(3); // held,cooldown,angle
  public FbzSpinningPoleObjectInstance(ObjectSpawn s) {
    super(s, "FBZSpinningPole");
    height = s.subtype() << 3;
  }
  public void update(int vIntRunCount, PlayableEntity p) {
    services().playerQuery().visitPlayers(
        ObjectPlayerParticipationPolicy
            .MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED,
        this, VISIT);
    PlayableEntity leader = services().playerQuery().mainPlayerOrNull();
    if (leader != null && players.flag(players.slot(leader), 0) &&
        services().camera() != null)
      services().camera().requestForcedScroll(spawn.x(), leader.getCentreY());
    coarseXCull(spawn.x(), 0x280);
  }
  private void updatePlayer(AbstractPlayableSprite p, int i) {
    if (!players.flag(i, 0)) {
      int cd = players.get(i, 1);
      if (cd > 0) {
        players.set(i, 1, cd - 1);
        return;
      }
      int dx = (short)(p.getCentreX() - spawn.x()),
          dy = spawn.y() - p.getCentreY() + 0x10;
      if (dx < -0xC || dx >= 0xC || dy < 0 || dy >= height || p.isDebugMode() ||
          p.isHurt() || p.getDead() || p.isObjectControlled())
        return;
      p.setXSpeed((short)0);
      p.setYSpeed((short)0);
      p.setGSpeed((short)0);
      // loc_3C0DC clears render_flags bits 0-1 before sub_3C010 reads
      // the horizontal flip to apply the initial angle offset.
      p.setRenderFlips(false, false);
      int nativeY = p.getCentreY();
      int oldYRadius = p.getYRadius();
      boolean wasRolling = p.getRolling();
      p.setRolling(false);
      p.applyStandingRadii(false);
      if (wasRolling)
        nativeY += oldYRadius - p.getYRadius();
      NativePositionOps.writeYPosPreserveSubpixel(p, nativeY);
      NativePositionOps.writeXPosPreserveSubpixel(p, spawn.x());
      if (spawn.y() < p.getCentreY())
        NativePositionOps.writeYPosPreserveSubpixel(p, spawn.y());
      p.setAir(true);
      p.setDirection(Direction.RIGHT);
      p.setAnimationId(0);
      p.setObjectMappingFrameControl(true);
      ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(
          p);
      players.set(i, 2, 0xE0);
      players.flag(i, 0, true);
      position(p, i);
      return;
    }
    if (p.isDebugMode() || p.isHurt() || p.getDead()) {
      cleanup(p, i);
      return;
    }
    if (p.isLogicalJumpPressActive()) {
      launch(p, i);
      return;
    }
    if (p.isUpPressed() && p.getCentreY() > spawn.y() - height)
      NativePositionOps.addYPosPreserveSubpixel(p, -1);
    if (p.isDownPressed() && p.getCentreY() < spawn.y())
      NativePositionOps.addYPosPreserveSubpixel(p, 1);
    players.set(i, 2, (players.get(i, 2) + 8) & 0xFF);
    position(p, i);
  }
  private void position(AbstractPlayableSprite p, int i) {
    int angle = players.get(i, 2) & 0xFF, idx = angle >>> 4;
    p.setHighPriority((byte)angle >= 0);
    p.setMappingFrame(FRAMES[idx]);
    int x = XOFF[idx];
    if (p.getRenderHFlip())
      x = -x;
    NativePositionOps.writeXPosPreserveSubpixel(p, spawn.x() + x);
  }
  private void launch(AbstractPlayableSprite p, int i) {
    p.setXSpeed((short)(p.isLeftPressed() ? -0x1000 : 0x1000));
    p.setYSpeed((short)-0x100);
    p.setAnimationId(2);
    int nativeY = p.getCentreY();
    p.setRolling(true);
    p.applyRollingRadii(false);
    NativePositionOps.writeYPosPreserveSubpixel(p, nativeY);
    cleanup(p, i);
  }
  private void cleanup(AbstractPlayableSprite p, int i) {
    players.flag(i, 0, false);
    players.set(i, 1, 0x1E);
    p.setAir(true);
    p.setJumping(true);
    p.setRollingJump(false);
    p.setFlipAngle(0);
    p.setSpindash(false);
    p.setDoubleJumpFlag(0);
    p.setObjectMappingFrameControl(false);
    p.setHighPriority(false);
    ObjectControlState.none().applyTo(p);
  }
  private boolean anyHeld() {
    for (int i = 0; i < players.size(); i++)
      if (players.flag(i, 0))
        return true;
    return false;
  }
  int poleHeight() { return height; }
  public void appendRenderCommands(List<GLCommand> c) {}
}
