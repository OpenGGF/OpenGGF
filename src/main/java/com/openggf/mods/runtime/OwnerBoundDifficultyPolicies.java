package com.openggf.mods.runtime;

import com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy;
import com.openggf.game.sonic3k.difficulty.BlueSphereLayoutPolicy;
import com.openggf.game.sonic3k.difficulty.GumballSpringPolicy;
import com.openggf.game.sonic3k.difficulty.S3kDifficultyRules;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;

/** Admission inside the verified owner boundary, before native consumers see a policy value. */
final class OwnerBoundDifficultyPolicies {
    private static final Method AIZ_FOLLOWUP = method(AizMinibossAttackPolicy.class,
            "followupDelayTicks", int.class);
    private static final Method BLUE_LAYOUT = method(BlueSphereLayoutPolicy.class,
            "transformLayout", int.class, byte[].class);
    private static final Method GUMBALL_SPRINGS = method(GumballSpringPolicy.class, "springOffsets");

    private OwnerBoundDifficultyPolicies() { }

    /** The caller must run this entire operation inside its existing fault boundary. */
    static Object invoke(Object delegate, Method method, Object[] args, Function<Object[], Object> callback) {
        if (delegate instanceof BlueSphereLayoutPolicy && signatureMatches(method, BLUE_LAYOUT)) {
            // Save the actual offered native bytes before creator code can write
            // them. A second copy is offered, so retained creator inputs cannot
            // mutate the native caller's board or redefine the trusted original.
            byte[] trustedOriginal = ((byte[]) args[1]).clone();
            Object[] offeredArgs = args.clone();
            offeredArgs[1] = trustedOriginal.clone();
            return S3kDifficultyRules.blueSphereLayout(trustedOriginal,
                    (byte[]) callback.apply(offeredArgs));
        }
        Object result = callback.apply(args);
        if (delegate instanceof AizMinibossAttackPolicy && signatureMatches(method, AIZ_FOLLOWUP)) {
            return S3kDifficultyRules.followupDelayTicks((Integer) result);
        }
        if (delegate instanceof GumballSpringPolicy && signatureMatches(method, GUMBALL_SPRINGS)) {
            return S3kDifficultyRules.gumballSpringOffsets((int[]) result);
        }
        return result;
    }

    private static boolean signatureMatches(Method invocation, Method policy) {
        // Proxy supplies the first interface's Method for duplicate signatures.
        // The actual delegate's policy contract still owns those exact operations,
        // even when its stable proxy was first published under another interface.
        return invocation.getName().equals(policy.getName())
                && invocation.getReturnType() == policy.getReturnType()
                && Arrays.equals(invocation.getParameterTypes(), policy.getParameterTypes());
    }

    private static Method method(Class<?> contract, String name, Class<?>... parameters) {
        try {
            return contract.getMethod(name, parameters);
        } catch (NoSuchMethodException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }
}
