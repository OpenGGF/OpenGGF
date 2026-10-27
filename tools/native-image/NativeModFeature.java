package com.openggf.tools.nativeimage;

import com.openggf.Engine;
import java.lang.reflect.Modifier;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/** Build-time checks for the default native runtime-loading contract. */
public final class NativeModFeature implements Feature {
    @Override public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            var field = Engine.class.getDeclaredField("compiledModsSupported");
            if (field.getType() != boolean.class || !Modifier.isFinal(field.getModifiers())
                    || Modifier.isStatic(field.getModifiers())) {
                throw new IllegalStateException("Engine capability field changed; requalify the native build");
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
            System.out.println("PASS native build contract: RuntimeClassLoading enabled");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Pinned GraalVM/engine capability contract changed", failure);
        }
    }
}
