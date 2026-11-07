package com.openggf.game.soniccd.objects.r4;

import com.openggf.game.soniccd.CdNativeFile;
import java.util.Set;

/**
 * Finite *descriptive* dispatch/reuse obligations, never runtime source admission.
 * Historical object_index_{1a,1b,1c_1d,2a,2b,2c_2d,3}.asm at native pin in ports.
 * NativeNull means objects/null.asm:3-5 (ID byte only), not DeleteObject or absent data.
 */
public final class CdR4EcologyManifest {
    private CdR4EcologyManifest() { }
    public enum Handler { TEST_SPAWN_POINTS, SPIKES, TONBO, AMENBO, TAGA_TAGA, YAGO, ANIMAL,
        FALL_SPIKES, MACE, SPIKE_BALL, CIRCLE_SPIKE_BALL, NATIVE_NULL }
    public static final Set<CdNativeFile> PROGRAMMES=Set.of(CdNativeFile.R41A,CdNativeFile.R41B,CdNativeFile.R41C,CdNativeFile.R41D,
        CdNativeFile.R42A,CdNativeFile.R42B,CdNativeFile.R42C,CdNativeFile.R42D,CdNativeFile.R43C,CdNativeFile.R43D);
    public static Handler handler(CdNativeFile programme,int id){
        if(!PROGRAMMES.contains(programme))throw new IllegalArgumentException("Not a normal R4 programme");
        boolean third=programme==CdNativeFile.R43C||programme==CdNativeFile.R43D;
        boolean a1=programme==CdNativeFile.R41A;
        boolean a2=programme==CdNativeFile.R42A;
        boolean future1=programme==CdNativeFile.R41C||programme==CdNativeFile.R41D;
        boolean future2=programme==CdNativeFile.R42C||programme==CdNativeFile.R42D;
        return switch(id){
            case 6->Handler.TEST_SPAWN_POINTS;
            case 0x22->third?Handler.NATIVE_NULL:Handler.SPIKES;
            case 0x2b->Handler.TONBO;
            case 0x2c->third?Handler.NATIVE_NULL:Handler.AMENBO;
            case 0x2d->third?Handler.NATIVE_NULL:Handler.TAGA_TAGA;
            case 0x2e->third?Handler.NATIVE_NULL:Handler.YAGO;
            case 0x2f->Handler.ANIMAL;
            case 0x32->a1?Handler.FALL_SPIKES:Handler.NATIVE_NULL;
            case 0x36->a1||a2||future2?Handler.MACE:Handler.NATIVE_NULL;
            case 0x37->a2||future2?Handler.SPIKE_BALL:Handler.NATIVE_NULL;
            case 0x38->future1||future2?Handler.CIRCLE_SPIKE_BALL:Handler.NATIVE_NULL;
            default->throw new IllegalArgumentException("Outside ecology lane ID: "+id);
        };
    }
    /** Data obligations for Root's shared-family profile migration; no cloned bodies or fake adapters. */
    public enum Reuse {
        SPRING, MOVING_SPRING, RING, LOST_RING, BOULDER, CHECKPOINT, EXPLOSION,
        FLOWER, POINTS, MONITOR_TIME, MONITOR_ITEM, TIME_POST, ROBOT_GENERATOR, PROJECTOR, POWERUP
    }
}
