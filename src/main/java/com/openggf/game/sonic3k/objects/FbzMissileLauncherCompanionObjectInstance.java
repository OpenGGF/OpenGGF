package com.openggf.game.sonic3k.objects;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.*;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;
/**
 * Allocated-after-current solid companion for negative-subtype FBZ launcher.
 */
public final class FbzMissileLauncherCompanionObjectInstance
    extends AbstractObjectInstance
    implements SolidObjectProvider, RewindRecreatable {
  private static final int[] OFFSETS = {-0x18, 2, -8, -4, 8, 4, 0x18, -2};
  private FbzMissileLauncherObjectInstance parent;
  private int familySlot = -1, anchorX;
  private boolean detonated;
  FbzMissileLauncherCompanionObjectInstance(
      ObjectSpawn s, FbzMissileLauncherObjectInstance p) {
    super(s, "FBZMissileLauncherCompanion");
    parent = p;
    familySlot = p == null ? -1 : p.getSlotIndex();
    anchorX = p == null ? s.x() : p.getX();
  }
  public void update(int f, PlayableEntity p) {
    if (!detonated && parent != null && parent.liveImpacts() == 0) {
      detonated = true;
      int member = 0;
      for (int attempt = 0; attempt < 4; attempt++) {
        int x = spawn.x() + OFFSETS[member * 2],
            y = spawn.y() + OFFSETS[member * 2 + 1];
        com.openggf.level.objects.ExplosionObjectInstance made =
            spawnAfterCurrentSibling(
                ()
                    -> new com.openggf.level.objects.ExplosionObjectInstance(
                        0, x, y, services().renderManager()));
        if (!made.isDestroyed())
          member++;
      }
      services().playSfx(Sonic3kSfx.TUBE_LAUNCHER.id);
      anchorX = 0x7F00;
      updateDynamicSpawn(anchorX, spawn.y());
    }
    coarseXCull(anchorX, 0x280);
  }
  static int[] explosionOffsets() { return OFFSETS.clone(); }
  FbzMissileLauncherObjectInstance parentMember() { return parent; }
  int familySlot() { return familySlot; }
  public SolidObjectParams getSolidParams() {
    return new SolidObjectParams(0x2B, 8, 9);
  }
  public SolidRoutineProfile getSolidRoutineProfile() {
    return SolidRoutineProfile.fullSolid(false);
  }
  public int getPriorityBucket() { return 2; }
  public boolean isHighPriority() { return true; }
  public FbzMissileLauncherCompanionObjectInstance
  recreateForRewind(RewindRecreateContext c) {
    var r = new FbzMissileLauncherCompanionObjectInstance(c.spawn(), null);
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
  public void appendRenderCommands(List<GLCommand> c) {
    PatternSpriteRenderer r =
        getRenderer(Sonic3kObjectArtKeys.FBZ_MISSILE_LAUNCHER_COMPANION);
    if (r != null && r.isReady())
      r.drawFrameIndex(6, getX(), getY(), false, false);
  }
}
