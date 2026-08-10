package com.openggf.game.sonic3k.objects;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;
/** Locked-on Obj_FBZPiston ($7D), sonic3k.asm $3C244-$3C326. */
public final class FbzPistonObjectInstance extends AbstractObjectInstance
    implements SolidObjectProvider, SpawnRewindRecreatable {
  private final int travel, cull;
  private int displacement;
  private boolean retracting;
  public FbzPistonObjectInstance(ObjectSpawn s) {
    super(s, "FBZPiston");
    travel = s.subtype() << 3;
    cull = (travel & 0xFF80) + 0x200;
  }
  public void update(int vIntRunCount, PlayableEntity p) {
    displacement += retracting ? -2 : 2;
    if (displacement >= travel) {
      displacement = travel;
      retracting = true;
    }
    if (displacement <= 0) {
      displacement = 0;
      retracting = false;
    }
  }
  int travel() { return travel; }
  int cullWidth() { return cull; }
  public int getX() { return spawn.x() - displacement; }
  public int getPriorityBucket() { return 5; }
  public SolidObjectParams getSolidParams() {
    return new SolidObjectParams(0x2B, 0x20, 0x21);
  }
  public SolidRoutineProfile getSolidRoutineProfile() {
    return SolidRoutineProfile.fullSolid(false);
  }
  public boolean usesCustomOutOfRangeCheck() { return true; }
  public boolean isCustomOutOfRange(int cameraX) {
    return isCoarseXOutOfRange(spawn.x(), cameraX, cull);
  }
  public void appendRenderCommands(List<GLCommand> c) {
    PatternSpriteRenderer r = getRenderer(Sonic3kObjectArtKeys.FBZ_PISTON);
    if (r != null && r.isReady())
      r.drawFrameIndex(0, getX(), spawn.y(), false, false);
  }
}
