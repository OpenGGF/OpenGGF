package com.openggf.tools.nativewindows;

import com.openggf.Engine;
import java.lang.reflect.Modifier;
import java.util.function.BooleanSupplier;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/** Fail-closed hosted checks for the 2026-10-08 experimental Windows build.
 * Uses the pinned builder's actual option, never a creator-reported capability.
 * No production POM or runtime property enables this experiment.
 */
public final class ExperimentalNativeFeature implements Feature {
    public static final class Enabled implements BooleanSupplier {
        public boolean getAsBoolean() {
            return Boolean.getBoolean("openggf.experimental.native.mods");
        }
    }

    @Override public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            if (!new Enabled().getAsBoolean()) throw new IllegalStateException("Experimental build marker required");
            var field = Engine.class.getDeclaredField("compiledModsSupported");
            if (field.getType() != boolean.class || !Modifier.isFinal(field.getModifiers())
                    || Modifier.isStatic(field.getModifiers())) {
                throw new IllegalStateException("Engine capability field changed; requalify the substitution");
            }
            Class<?> options = Class.forName("com.oracle.svm.core.hub.RuntimeClassLoading$Options");
            var optionField = options.getField("RuntimeClassLoading");
            Object key = optionField.get(null);
            // The pinned builder declares a public shared.option key type. Use
            // that declaration, avoiding both a stale package and the private
            // anonymous subclass of the actual key instance.
            Class<?> keyType = optionField.getType();
            if (!Boolean.TRUE.equals(keyType.getMethod("getValue").invoke(key))) {
                throw new IllegalStateException("RuntimeClassLoading must be enabled");
            }
            RuntimeReflection.register(Engine.class.getDeclaredMethod("initializeExternalContentAtBoot"));
            RuntimeReflection.register(Engine.class.getDeclaredField("modRuntime"));
            System.out.println("PASS experimental engine capability: RuntimeClassLoading enabled; isolated field substitution");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Pinned GraalVM/engine capability contract changed", failure);
        }
    }
}
