package com.openggf.tools.nativewindows;

import com.openggf.Engine;
import com.openggf.tools.NativeModMemberAudit;
import com.openggf.tools.NativeModRegistrationProbe;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;
import java.nio.file.Path;

/** Experimental Windows bundle entry point, originating in the 2026-10-08
 * friends ZIP task. Inputs: the generated member contract and packaged mods
 * beside the executable. Native-only; audits before creator execution. Normal
 * engine/native builds never compile this source or install its substitution.
 */
public final class ExperimentalNativeEngine {
    public static void main(String[] args) throws Exception {
        if (System.getProperty("org.graalvm.nativeimage.imagecode") == null) {
            throw new IllegalStateException("Use the experimental native executable");
        }
        Path root = Path.of("").toAbsolutePath();
        System.setProperty("org.lwjgl.librarypath", root.toString());
        NativeModMemberAudit.verify(root.resolve("native-mod-members.tsv"));
        if (args.length == 1 && args[0].equals("--audit")) return;
        if (args.length == 2 && args[0].equals("--check-engine")) {
            com.openggf.ExperimentalEngineBootCheck.verify(args[1]);
            return;
        }
        if (args.length >= 1 && args[0].equals("--check-mods")) {
            String[] registration = new String[args.length];
            registration[0] = root.resolve("mods").toString();
            System.arraycopy(args, 1, registration, 1, args.length - 1);
            NativeModRegistrationProbe.main(registration);
            return;
        }
        if (args.length != 0) throw new IllegalArgumentException("Expected: [--audit | --check-mods [owner-id]]");
        Engine.main(args);
    }
}

/** Redirect only this capability field. Native detection, trust, snapshots,
 * owner boundaries and all other engine behavior retain their original paths.
 */
@TargetClass(value = Engine.class, onlyWith = ExperimentalNativeFeature.Enabled.class)
final class TargetEngineCapability {
    @Alias
    @InjectAccessors(ExperimentalCapabilityAccess.class)
    private final boolean compiledModsSupported = false;
}

final class ExperimentalCapabilityAccess {
    static boolean get(TargetEngineCapability receiver) { return true; }
    // The original constructor stores !isNativeImage(); this image's capability
    // is an immutable build choice verified by the hosted feature, not that store.
    static void set(TargetEngineCapability receiver, boolean originalValue) { }
}
