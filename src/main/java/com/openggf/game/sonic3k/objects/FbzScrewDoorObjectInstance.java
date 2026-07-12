package com.openggf.game.sonic3k.objects;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.*;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;
/** Locked-on Obj_FBZScrewDoor ($7A), sonic3k.asm $3BBEA-$3BD5C. */
public final class FbzScrewDoorObjectInstance extends AbstractObjectInstance
    implements SolidObjectProvider, SpawnRewindRecreatable {
  private static final int[][] SIZE = {{8, 0x20}, {8, 0x20}, {0x20, 8},
                                       {0x20, 8}, {0x40, 8}, {0x40, 8}};
  private static final int[][] FRAMES = {{0, 3, 2, 1},   {0, 1, 2, 3},
                                         {4, 5, 6, 7},   {4, 7, 6, 5},
                                         {8, 9, 10, 11}, {8, 11, 10, 9}};
  @RewindTransient(reason = "constructor subtype decode")
  private final int animation, trigger, width, height;
  @RewindTransient(reason = "constructor subtype decode")
  private final boolean horizontal, negative, legacy;
  private int displacement, mappingFrame, animStep = 1, animTimer = 1;
  private boolean opening, sfxPlayed, initialized, legacyRestored;
  public FbzScrewDoorObjectInstance(ObjectSpawn s) {
    super(s, "FBZScrewDoor");
    animation = (s.subtype() >>> 4) & 7;
    int row = Math.min(animation, SIZE.length - 1);
    width = SIZE[row][0];
    height = SIZE[row][1];
    trigger = s.subtype() & 0xF;
    horizontal = (s.subtype() & 0x20) != 0;
    negative = (s.subtype() & 0x10) != 0;
    legacy = (s.subtype() & 0x80) != 0;
    mappingFrame = FRAMES[Math.min(animation, 5)][0];
  }
  public void update(int f, PlayableEntity p) {
    if (!initialized) {
      initialized = true;
      if (legacy && services().objectManager() != null &&
          services().objectManager().isSpawnStateBitSet(spawn, 0)) {
        displacement = 0x80;
        legacyRestored = true;
      }
    }
    if (!opening && displacement < 0x80) {
      if (!legacy && Sonic3kLevelTriggerManager.testAny(trigger)) {
        opening = true;
        sfxPlayed = true;
        services().playSfx(Sonic3kSfx.DOOR_OPEN.id);
      } else if (legacy && p != null) {
        int dy = (short)(p.getCentreY() - spawn.y());
        int dx = (short)(p.getCentreX() - spawn.x());
        if (dy >= 0x20 && dy < 0x60 &&
            (((spawn.renderFlags() & 1) == 0 && dx >= 0x40) ||
             ((spawn.renderFlags() & 1) != 0 && dx <= -0x40))) {
          opening = true;
          if (services().objectManager() != null)
            services().objectManager().setSpawnStateBit(spawn, 0);
        }
      }
    }
    if (opening && displacement < 0x80) {
      displacement++;
      advanceAnimation();
    }
    coarseCull();
  }
  private void advanceAnimation() {
    if (animTimer-- == 0) {
      int[] seq = FRAMES[Math.min(animation, 5)];
      mappingFrame = seq[animStep];
      animStep = (animStep + 1) & 3;
      animTimer = 1;
    }
  }
  private void coarseCull() { coarseXCull(spawn.x(), 0x280); }
  public int getX() {
    if (legacyRestored)
      return spawn.x();
    int d = horizontal ? (negative ? -displacement : displacement) : 0;
    return spawn.x() + (horizontal ? (animation == 2 ? d / 2 : d) : 0);
  }
  public int getY() {
    if (legacyRestored)
      return spawn.y() + 0x40;
    int d = !horizontal ? (negative ? -displacement : displacement) : 0;
    return spawn.y() + (!horizontal ? d / 2 : 0);
  }
  int animationIndex() { return animation; }
  int mappingFrame() { return mappingFrame; }
  int triggerIndex() { return trigger; }
  int nativeWidth() { return width; }
  int nativeHeight() { return height; }
  boolean horizontalMode() { return horizontal; }
  boolean negativeDirection() { return negative; }
  public int getPriorityBucket() { return 5; }
  public SolidObjectParams getSolidParams() {
    return new SolidObjectParams(width + 0xB, height, height + 1);
  }
  public SolidRoutineProfile getSolidRoutineProfile() {
    return SolidRoutineProfile.fullSolid(false);
  }
  public int getOutOfRangeReferenceX() { return spawn.x(); }
  public void appendRenderCommands(List<GLCommand> c) {
    PatternSpriteRenderer r = getRenderer(Sonic3kObjectArtKeys.FBZ_SCREW_DOOR);
    if (r != null && r.isReady())
      r.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
  }
}
