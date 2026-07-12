package com.openggf.game.sonic3k.objects;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;
/** Locked-on Obj_FBZPropeller ($7C), sonic3k.asm $3C1B6-$3C208. */
public final class FbzPropellerObjectInstance extends AbstractObjectInstance
    implements TouchResponseProvider, SpawnRewindRecreatable {
  private static final int[] FLAGS = {0xB6, 0, 0xB6, 0xB7};
  private int frame, flags = 0xB6;
  public FbzPropellerObjectInstance(ObjectSpawn s) { super(s, "FBZPropeller"); }
  public void update(int f, PlayableEntity p) {
    frame = (frame + 1) & 3;
    flags = FLAGS[frame];
    coarseXCull(spawn.x(), 0x280);
  }
  int mappingFrame() { return frame; }
  int[] collisionCycle() { return FLAGS.clone(); }
  public int getCollisionFlags() { return flags; }
  public int getCollisionProperty() { return 0; }
  public int getPriorityBucket() { return 5; }
  public void appendRenderCommands(List<GLCommand> c) {
    PatternSpriteRenderer r = getRenderer(Sonic3kObjectArtKeys.FBZ_PROPELLER);
    if (r != null && r.isReady())
      r.drawFrameIndex(frame, spawn.x(), spawn.y(), false, false);
  }
}
