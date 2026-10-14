package com.openggf.level.rings;

/** Engine-only admission of bounded temporary pickups; native hurt-spill lifetime is untouched. */
public final class LostRingMutatorAccess {
    private LostRingMutatorAccess() { }
    public static LostRingObjectInstance spawn(int x,int y,int xVelocity,int yVelocity,int phase,int lifetime,int pickupDelayTicks) {
        var caller=com.openggf.util.EngineCallerAccess.callerOutside(LostRingMutatorAccess.class);
        if(caller.getClassLoader()!=LostRingMutatorAccess.class.getClassLoader())
            throw new SecurityException("Temporary pickup admission belongs to the native runtime");
        return LostRingObjectInstance.temporaryPickup(x,y,xVelocity,yVelocity,phase,lifetime,pickupDelayTicks);
    }
}
