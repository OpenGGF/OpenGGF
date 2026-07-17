package com.openggf.game.sonic3k.objects;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;
/** Locked-on Obj_FBZMissileLauncher ($7F), sonic3k.asm $3C44E-$3C78C. */
public final class FbzMissileLauncherObjectInstance
    extends AbstractObjectInstance implements SpawnRewindRecreatable {
  private static final int[] X_TRAJECTORY = {0x100, 0xE0, 0x120, 0x140,
                                             0x100, 0xE0, 0xC0,  0xE0};
  private final int interval, burst, phase;
  private final boolean companion;
  private boolean initialized, armed, cooling, targeting;
  private int burstRemaining, timer, trajectoryIndex, liveImpacts;
  public FbzMissileLauncherObjectInstance(ObjectSpawn s) {
    super(s, "FBZMissileLauncher");
    interval = (((s.subtype() & 0xC) + 4) << 2);
    burst = (s.subtype() & 3) + 1;
    phase = s.subtype() & 0x70;
    companion = (s.subtype() & 0x80) != 0;
    targeting = companion;
    liveImpacts = companion ? 5 : 0;
  }
  public void update(int frame, PlayableEntity p) {
    if (!initialized) {
      initialized = true;
      if (companion)
        spawnAfterCurrentSibling(
            ()
                -> new FbzMissileLauncherCompanionObjectInstance(
                    new ObjectSpawn(spawn.x() + 0x30, spawn.y() - 0x30,
                                    spawn.objectId(), spawn.subtype(),
                                    spawn.renderFlags(), false,
                                    spawn.rawYWord()),
                    this));
    }
    if (cooling) {
      if (--timer < 0) {
        cooling = false;
        armed = false;
      }
      coarseCull();
      return;
    }
    if (!armed) {
      // loc_3C534 reads the low byte of (Level_frame_counter+1), not the
      // free-running VBla counter used to execute ObjectManager callbacks.
      // Keeping these clock domains separate preserves the native projectile
      // burst phase across trace bootstrap, lag, and seamless transitions.
      int levelFrame = resolveLevelFrameCounter(frame);
      if (((levelFrame + phase) & 0xFF) != 0 || !isOnScreen(0x20)) {
        coarseCull();
        return;
      }
      armed = true;
      burstRemaining = burst;
      timer = 0;
    }
    if (--timer < 0) {
      timer = interval;
      int xv = X_TRAJECTORY[trajectoryIndex & 7];
      FbzMissileLauncherProjectileObjectInstance made =
          spawnAfterCurrentSibling(
              ()
                  -> new FbzMissileLauncherProjectileObjectInstance(
                      new ObjectSpawn(
                          spawn.x(), spawn.y() + 2, spawn.objectId(),
                          targeting ? (spawn.subtype() | 0x80)
                                    : (spawn.subtype() & 0x7F),
                          spawn.renderFlags(), false, spawn.rawYWord()),
                      this, xv));
      if (!made.isDestroyed()) {
        trajectoryIndex++;
        services().playSfx(Sonic3kSfx.LEVEL_PROJECTILE.id);
      }
      if (--burstRemaining == 0) {
        cooling = true;
        timer = 7;
      }
    }
    coarseCull();
  }
  private int resolveLevelFrameCounter(int fallbackFrameCounter) {
    ObjectServices objectServices = tryServices();
    return objectServices != null && objectServices.levelManager() != null
        ? objectServices.levelManager().getFrameCounter() + 1
        : fallbackFrameCounter;
  }
  private void coarseCull() { coarseXCull(spawn.x(), 0x280); }
  void missileImpacted() {
    if (liveImpacts > 0)
      liveImpacts--;
    if (liveImpacts == 0)
      targeting = false;
  }
  boolean targeting() { return targeting; }
  int liveImpacts() { return liveImpacts; }
  int launchInterval() { return interval; }
  int burstCount() { return burst; }
  int phaseOffset() { return phase; }
  boolean hasCompanion() { return companion; }
  static int[] horizontalTrajectoryTable() { return X_TRAJECTORY.clone(); }
  public int getPriorityBucket() { return 1; }
  public boolean isHighPriority() { return true; }
  public void appendRenderCommands(List<GLCommand> c) {
    PatternSpriteRenderer r =
        getRenderer(Sonic3kObjectArtKeys.FBZ_MISSILE_LAUNCHER);
    if (r != null && r.isReady())
      r.drawFrameIndex(armed ? 1 : 0, spawn.x(), spawn.y(), false, false);
  }
}
