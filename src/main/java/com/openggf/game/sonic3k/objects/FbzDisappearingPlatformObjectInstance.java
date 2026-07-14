package com.openggf.game.sonic3k.objects;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import java.util.List;
/** Locked-on Obj_FBZDisappearingPlatform ($79), sonic3k.asm $3BADE-$3BB98. */
public final class FbzDisappearingPlatformObjectInstance
    extends AbstractObjectInstance
    implements SolidObjectProvider, SolidObjectListener,
               SpawnRewindRecreatable {
  private static final int[] PHASE_MASKS = {0x7F, 0xFF, 0x1FF, 0x3FF};
  private static final int[] SOLID_DURATIONS = {0x3C, 0x78, 0xB4, 0xF0};
  private static final ObjectPlayerQuery
      .PlayerVisitor<FbzDisappearingPlatformObjectInstance> DETACH =
      (platform, player) -> platform.detachRider(player);
  private final int mask, offset, animation;
  private final FbzParticipantStateTable riders =
      new FbzParticipantStateTable(1);
  private int state, frame = 2, timer, scriptStep;
  public FbzDisappearingPlatformObjectInstance(ObjectSpawn s) {
    super(s, "FBZDisappearingPlatform");
    mask = PHASE_MASKS[(s.subtype() >>> 2) & 3];
    offset = ((mask + 1) >>> 4) * ((s.subtype() >>> 4) & 0xF);
    animation = s.subtype() & 3;
  }
  public void update(int counter, PlayableEntity p) {
    if (state == 0) {
      if (((counter + offset) & mask) != 0) {
        coarseCull(spawn.x());
        return;
      }
      state = 1;
      scriptStep = 0;
      frame = 1;
      timer = 5;
    } else if (timer-- == 0) {
      if (scriptStep == 0) {
        scriptStep = 1;
        frame = 0;
        timer = SOLID_DURATIONS[animation];
      } else if (scriptStep == 1) {
        scriptStep = 2;
        frame = 1;
        timer = 5;
      } else {
        state = 0;
        scriptStep = 0;
        frame = 2;
        timer = 0;
      }
    }
    if (frame != 0)
      services().playerQuery().visitPlayers(
          ObjectPlayerParticipationPolicy
              .MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED,
          this, DETACH);
    coarseCull(spawn.x());
  }
  private void coarseCull(int anchor) { coarseXCull(anchor, 0x280); }
  private void detachRider(PlayableEntity player) {
    int slot = riders.slot(player);
    if (!riders.flag(slot, 0))
      return;
    riders.flag(slot, 0, false);
    if (player instanceof
        com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
      sprite.setOnObject(false);
      sprite.setAir(true);
    }
  }
  public void onSolidContact(PlayableEntity player, SolidContact contact,
                             int frameCounter) {
    riders.flag(riders.slot(player), 0, contact.standing());
  }
  public void onSolidContactCleared(PlayableEntity player, int frameCounter) {
    riders.flag(riders.slot(player), 0, false);
  }
  int phaseMask() { return mask; }
  int phaseOffset() { return offset; }
  int animationIndex() { return animation; }
  int mappingFrame() { return frame; }
  public int getPriorityBucket() { return 5; }
  public SolidObjectParams getSolidParams() {
    return new SolidObjectParams(0x1B, 0x11, 0x11);
  }
  public boolean isSolidFor(PlayableEntity p) { return frame == 0; }
  public SolidRoutineProfile getSolidRoutineProfile() {
    return SolidRoutineProfile.topSolid(false);
  }
  public void appendRenderCommands(List<GLCommand> c) {
    PatternSpriteRenderer r =
        getRenderer(Sonic3kObjectArtKeys.FBZ_DISAPPEARING_PLATFORM);
    if (r != null && r.isReady())
      r.drawFrameIndex(frame, spawn.x(), spawn.y(), false, false);
  }
}
