package com.openggf.game.sonic3k.objects;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;
/** Locked-on Obj_FBZPlatformBlocks ($7E), sonic3k.asm $3C34C-$3C412. */
public final class FbzPlatformBlocksObjectInstance
    extends AbstractObjectInstance
    implements SolidObjectProvider, SpawnRewindRecreatable {
  private static final int[] WIDTH = {0x10, 0x20, 0x30, 0x40};
  private final int frame, width, travel;
  private int offset;
  public FbzPlatformBlocksObjectInstance(ObjectSpawn s) {
    super(s, "FBZPlatformBlocks");
    frame = (s.subtype() >>> 4) & 7;
    width = WIDTH[Math.min(frame, 3)];
    travel = (s.subtype() & 0xF) << 4;
  }
  public void update(int vIntRunCount, PlayableEntity p) {
    if (travel != 0 && p != null) {
      int dy = (short)(p.getCentreY() - spawn.y());
      if (dy >= 0x20 && offset < travel)
        offset = Math.min(travel, offset + 8);
      else if (dy < -0x28 && offset > 0)
        offset = Math.max(0, offset - 8);
    }
    coarseXCull(spawn.x(), 0x280);
  }
  int mappingFrame() { return frame; }
  int nativeWidth() { return width; }
  int travel() { return travel; }
  public int getX() {
    int d = (spawn.renderFlags() & 1) != 0 ? -offset : offset;
    return spawn.x() + d;
  }
  public int getPriorityBucket() { return 5; }
  public SolidObjectParams getSolidParams() {
    return new SolidObjectParams(width + 0xB, 0x10, 0x11);
  }
  public SolidRoutineProfile getSolidRoutineProfile() {
    return SolidRoutineProfile.fullSolid(false);
  }
  public void appendRenderCommands(List<GLCommand> c) {
    PatternSpriteRenderer r =
        getRenderer(Sonic3kObjectArtKeys.FBZ_PLATFORM_BLOCKS);
    if (r != null && r.isReady())
      r.drawFrameIndex(frame, getX(), spawn.y(), false, false);
  }
}
