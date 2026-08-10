package com.openggf.game.sonic3k.objects;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;
/** Locked-on Obj_FBZWallMissile ($E0), sonic3k.asm $3C7EC-$3C904. */
public final class FbzWallMissileObjectInstance
    extends AbstractObjectInstance implements SpawnRewindRecreatable {
  private final int interval;
  private int timer, muzzleTimer, frame;
  private boolean muzzle;
  public FbzWallMissileObjectInstance(ObjectSpawn s) {
    super(s, "FBZWallMissile");
    interval = s.subtype() << 2;
    timer = interval;
  }
  public void update(int vIntRunCount, PlayableEntity p) {
    if (muzzle) {
      if (--muzzleTimer < 0) {
        muzzleTimer = 7;
        if (--frame == 0)
          muzzle = false;
      }
      coarseCull();
      return;
    }
    // ROM loc_3C828 gates the countdown on render_flags bit 7 from
    // Render_Sprites. Obj_FBZWallMissile sets width_pixels=$10 and
    // height_pixels=4; the renderer's right/bottom edges are exclusive.
    if (isWithinRenderSpriteBounds(0x10, 4) && --timer < 0) {
      timer = interval;
      short xv = (short)(((spawn.renderFlags() & 1) != 0) ? 0x400 : -0x400);
      FbzWallMissileProjectileObjectInstance made = spawnAfterCurrentSibling(
          ()
              -> new FbzWallMissileProjectileObjectInstance(
                  new ObjectSpawn(spawn.x(), spawn.y(), spawn.objectId(),
                                  spawn.subtype(), spawn.renderFlags(), false,
                                  spawn.rawYWord()),
                  xv));
      if (!made.isDestroyed())
        services().playSfx(Sonic3kSfx.LEVEL_PROJECTILE.id);
      muzzle = true;
      frame = 7;
      muzzleTimer = 0x1E;
    }
    coarseCull();
  }
  private void coarseCull() { coarseXCull(spawn.x(), 0x280); }
  int launchInterval() { return interval; }
  public int getPriorityBucket() { return 5; }
  public void appendRenderCommands(List<GLCommand> c) {
    PatternSpriteRenderer r =
        getRenderer(Sonic3kObjectArtKeys.FBZ_WALL_MISSILE);
    if (r != null && r.isReady())
      r.drawFrameIndex(frame, spawn.x(), spawn.y(), false, false);
  }
}
