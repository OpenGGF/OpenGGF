package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * ROM {@code loc_85CA4} (sonic3k.asm:180484-180554) as allocated by {@code loc_63D1A} for the
 * Hidden Palace Knuckles fight: {@code boss_saved_mus = mus_Knuckles}, {@code $2E = 2*60} and
 * {@code $34 = loc_63DD4}.
 *
 * <p>Bit 0 of {@code $27} plays the music once the wait word underflows. Bit 1 locks the
 * camera Y range to {@code _unkFAB0}/{@code _unkFAB2} ({@code $380}) and bit 2 the X range to
 * {@code _unkFAB4}/{@code _unkFAB6} ({@code $10E0}); until then the minimum (or, approaching
 * from below/right, the maximum) follows the camera. Bits 6 and 7 were copied from the Knuckles
 * object's {@code Check_CameraInRange}. When all three are set, {@code loc_63DD4} sets
 * {@code _unkFAB8} bit 0 and the object deletes itself.
 */
public final class HpzKnucklesBossMusicObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int APPROACH_FROM_BELOW_SLACK = 0x60;

    private boolean fromBelow;
    private boolean fromRight;
    private int waitWord = 2 * 60;
    private int bits;

    public HpzKnucklesBossMusicObjectInstance(boolean fromBelow, boolean fromRight) {
        super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "HpzKnucklesBossMusic");
        this.fromBelow = fromBelow;
        this.fromRight = fromRight;
    }

    @Override
    public HpzKnucklesBossMusicObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzKnucklesBossMusicObjectInstance(false, false);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        var camera = services().camera();
        if ((bits & 1) == 0) {
            waitWord = (short) (waitWord - 1);
            if (waitWord < 0) {
                services().playMusic(Sonic3kMusic.KNUCKLES.id);
                bits |= 1;
            }
        }
        int lockY = CutsceneKnucklesHpzInstance.LOCK_Y;
        int lockX = CutsceneKnucklesHpzInstance.LOCK_X;
        if ((bits & 2) == 0) {
            int cameraY = camera.getY() & 0xFFFF;
            if (!fromBelow) {
                if (cameraY >= lockY) {
                    lockY(camera, lockY);
                } else {
                    camera.setMinY((short) cameraY);
                }
            } else if (cameraY <= lockY + APPROACH_FROM_BELOW_SLACK) {
                lockY(camera, lockY);
            }
        }
        if ((bits & 4) == 0) {
            int cameraX = camera.getX() & 0xFFFF;
            if (!fromRight) {
                if (cameraX >= lockX) {
                    lockX(camera, lockX);
                } else {
                    camera.setMinX((short) cameraX);
                }
            } else if (cameraX <= lockX) {
                lockX(camera, lockX);
            } else {
                camera.setMaxX((short) cameraX);
            }
        }
        if ((bits & 7) != 7) {
            return;
        }
        // loc_63DD4: bset #0,(_unkFAB8).w / Go_Delete_Sprite
        HpzZoneRuntimeState hpz = HpzKnucklesCutsceneSupport.hpz(services());
        if (hpz != null) {
            hpz.setKnucklesCutsceneFlag(HpzKnucklesCutsceneSupport.FLAG_CAMERA_READY);
        }
        ObjectLifetimeOps.expireDynamic(this);
    }

    /** {@code loc_85CF2}: {@code Camera_min_Y_pos = _unkFAB0}, {@code Camera_target_max_Y_pos = _unkFAB2}. */
    private void lockY(com.openggf.camera.Camera camera, int lock) {
        bits |= 2;
        camera.setMinY((short) lock);
        camera.setMaxYTarget((short) lock);
    }

    /** {@code loc_85D36}. */
    private void lockX(com.openggf.camera.Camera camera, int lock) {
        bits |= 4;
        camera.setMinX((short) lock);
        camera.setMaxX((short) lock);
    }

    public int bitsForTest() { return bits; }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
