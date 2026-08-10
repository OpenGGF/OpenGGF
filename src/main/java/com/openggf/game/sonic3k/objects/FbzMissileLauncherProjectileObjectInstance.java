package com.openggf.game.sonic3k.objects;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.*;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.*;
import java.util.List;
/**
 * Ballistic launcher missile, replacing its own slot with Obj_Explosion on
 * impact.
 */
public final class FbzMissileLauncherProjectileObjectInstance
    extends AbstractObjectInstance
    implements TouchResponseProvider, RewindRecreatable {
  private FbzMissileLauncherObjectInstance parent;
  private int familySlot = -1, x, y, xFixed, yFixed, yVel = -0x600, xDelta;
  private boolean damaging, pendingImpact, converted;
  FbzMissileLauncherProjectileObjectInstance(ObjectSpawn s,
                                             FbzMissileLauncherObjectInstance p,
                                             int xv) {
    super(s, "FBZMissileLauncherProjectile");
    parent = p;
    familySlot = p == null ? -1 : p.getSlotIndex();
    x = s.x();
    y = s.y();
    xFixed = x << 8;
    yFixed = y << 8;
    xDelta = xv;
  }
  public void update(int vIntRunCount, PlayableEntity p) {
    if (converted)
      return;
    if (pendingImpact) {
      convertToExplosion();
      return;
    }
    yFixed += yVel;
    y = yFixed >> 8;
    boolean rising = yVel < 0;
    if (rising) {
      yVel += 0x18;
      if (yVel >= 0)
        damaging = true;
    } else {
      yVel += 0x10;
      if ((spawn.subtype() & 0x80) != 0 && parent != null &&
          parent.targeting() && y > parent.getY() - 0x44) {
        y = parent.getY() - 0x44;
        yFixed = y << 8;
        scheduleImpact();
        // loc_3C716 decrements the companion's live-impact byte on the
        // detection frame, before this projectile converts next callback.
        parent.missileImpacted();
      } else if ((spawn.subtype() & 0x80) == 0 || parent == null ||
          !parent.targeting()) {
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(x, y, 0xC);
        if (floor.foundSurface() && floor.distance() < 0) {
          y += floor.distance();
          yFixed = y << 8;
          scheduleImpact();
        }
      }
    }
    if (Math.abs(yVel) < 0x1D0)
      xFixed += xDelta;
    x = xFixed >> 8;
    updateDynamicSpawn(x, y);
  }
  void impact() { scheduleImpact(); }
  private void scheduleImpact() {
    if (pendingImpact || converted)
      return;
    pendingImpact = true;
  }
  private void convertToExplosion() {
    if (converted)
      return;
    pendingImpact = false;
    converted = true;
    damaging = false;
    y += 4;
    yFixed = y << 8;
    services().playSfx(Sonic3kSfx.EXPLODE.id);
    int slot = ObjectLifetimeOps.detachSlotForTransfer(this);
    ObjectLifetimeOps.deleteNoRespawn(this);
    ObjectLifetimeOps.addReplacementAtTransferredSlot(
        services().objectManager(),
        new com.openggf.level.objects.ExplosionObjectInstance(
            0, x, y, services().renderManager()),
        slot);
  }
  public int getCollisionFlags() { return damaging ? 0x9E : 0; }
  public int getCollisionProperty() { return 0; }
  public int getX() { return x; }
  public int getY() { return y; }
  int horizontalDelta() { return xDelta; }
  FbzMissileLauncherObjectInstance parentMember() { return parent; }
  int familySlot() { return familySlot; }
  public FbzMissileLauncherProjectileObjectInstance
  recreateForRewind(RewindRecreateContext c) {
    var r = new FbzMissileLauncherProjectileObjectInstance(c.spawn(), null, 0);
    if (c.state() != null && c.state().compactGenericState() != null)
      GenericFieldCapturer.restoreObjectSubclassScalarsCompact(
          r, c.state().compactGenericState());
    return r;
  }
  protected void afterRewindRestoreSettled() {
    if (parent == null && services().objectManager() != null)
      for (ObjectInstance o : services().objectManager().getActiveObjects())
        if (o instanceof FbzMissileLauncherObjectInstance p &&
            p.getSlotIndex() == familySlot) {
          parent = p;
          break;
        }
  }
  public int getPriorityBucket() { return 1; }
  public boolean isHighPriority() { return true; }
  public void appendRenderCommands(List<GLCommand> c) {
    PatternSpriteRenderer r =
        getRenderer(Sonic3kObjectArtKeys.FBZ_MISSILE_LAUNCHER);
    if (r != null && r.isReady())
      r.drawFrameIndex(damaging ? 3 : 2, x, y, false, false);
  }
}
