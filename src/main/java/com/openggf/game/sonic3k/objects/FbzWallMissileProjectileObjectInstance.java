package com.openggf.game.sonic3k.objects;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;
/** Parentless horizontal child of Obj_FBZWallMissile. */
public final class FbzWallMissileProjectileObjectInstance
    extends AbstractObjectInstance
    implements TouchResponseProvider, SpawnRewindRecreatable {
  private int x, y, xFixed, velocity, frame;
  FbzWallMissileProjectileObjectInstance(ObjectSpawn s, short velocity) {
    super(s, "FBZWallMissileProjectile");
    x = s.x();
    y = s.y();
    xFixed = x << 8;
    this.velocity = velocity;
  }
  public FbzWallMissileProjectileObjectInstance(ObjectSpawn s) {
    this(s, (short)(((s.renderFlags() & 1) != 0) ? 0x400 : -0x400));
  }
  public void update(int counter, PlayableEntity p) {
    if (!isOnScreen(0x20)) {
      setDestroyed(true);
      return;
    }
    if ((counter & 3) == 0)
      frame ^= 1;
    xFixed += velocity;
    x = xFixed >> 8;
    updateDynamicSpawn(x, y);
  }
  int mappingFrame() { return frame; }
  public int getCollisionFlags() { return 0x9B; }
  public int getCollisionProperty() { return 0; }
  public int getX() { return x; }
  public int getY() { return y; }
  public int getPriorityBucket() { return 6; }
  public void appendRenderCommands(List<GLCommand> c) {
    PatternSpriteRenderer r =
        getRenderer(Sonic3kObjectArtKeys.FBZ_WALL_MISSILE);
    if (r != null && r.isReady())
      r.drawFrameIndex(frame, x, y, false, false);
  }
}
