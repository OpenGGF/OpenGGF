package com.openggf.game.soniccd.objects;
import java.util.function.IntConsumer;
/** Native increasing physical RunObjects walk, one live read/dispatch per occurrence.
 * Bounds permit the old explicitly partial DEV dynamic continuation as well as the full pass.
 * N1ecc5f3a Object Functions.RunObjects; no captured list, scheduler, source or lifecycle owner. */
public final class CdPhysicalObjectWalk {
 private CdPhysicalObjectWalk(){}
 public static void run(int first,int end,IntConsumer original){if(first<0||end>128||first>end)throw new IllegalArgumentException("Physical object range");java.util.Objects.requireNonNull(original);for(int whole=first;whole<end;whole++)original.accept(whole);}
}
