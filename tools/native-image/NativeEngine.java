package com.openggf.tools.nativeimage;

import com.openggf.Engine;
import com.openggf.tools.NativeModMemberAudit;
import com.openggf.tools.NativeModRegistrationProbe;
import java.nio.file.Path;

/** Default native launcher with mandatory retention audit and bounded qualification commands. */
public final class NativeEngine {
    public static void main(String[] args) throws Exception {
        if (System.getProperty("org.graalvm.nativeimage.imagecode") == null) {
            throw new IllegalStateException("Use the native executable");
        }
        Path root = Path.of(org.graalvm.nativeimage.ProcessProperties.getExecutableName()).toAbsolutePath().getParent();
        if (System.getProperty("org.lwjgl.librarypath") == null)
            System.setProperty("org.lwjgl.librarypath", root.toString());
        if (System.getProperty("openggf.installDir") == null)
            System.setProperty("openggf.installDir", root.toString());
        try (var input = NativeEngine.class.getResourceAsStream("/native-mod-members.tsv")) {
            if (input == null) throw new IllegalStateException("Native retention contract missing");
            NativeModMemberAudit.verify(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList());
        }
        if (args.length == 2 && args[0].equals("--audit")) {
            NativeModMemberAudit.verify(Path.of(args[1]));
            return;
        }
        if (args.length == 1 && args[0].equals("--audit")) return;
        if (args.length >= 1 && args[0].equals("--check-gameplay")) {
            com.openggf.tools.nativelinux.NativeGameplayCheck.main(
                    java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
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
        if (args.length != 0) throw new IllegalArgumentException(
                "Expected: [--audit | --check-mods [owner-id] | --check-engine owner-id | --check-gameplay ...]");
        Engine.main(args);
    }
}
