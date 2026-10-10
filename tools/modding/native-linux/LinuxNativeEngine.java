package com.openggf.tools.nativelinux;

/** Linux experimental entry point. Shares the isolated Windows capability and
 * mandatory member audit; production native entry points remain unchanged.
 * Origin: 2026-10-09 Linux friends build.
 */
public final class LinuxNativeEngine {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--check-gameplay")) {
            if (System.getProperty("org.graalvm.nativeimage.imagecode") == null)
                throw new IllegalStateException("Use the experimental native executable");
            System.setProperty("org.lwjgl.librarypath", java.nio.file.Path.of("").toAbsolutePath().toString());
            com.openggf.tools.NativeModMemberAudit.verify(java.nio.file.Path.of("native-mod-members.tsv"));
            NativeGameplayCheck.main(java.util.Arrays.copyOfRange(args, 1, args.length));
        } else {
            com.openggf.tools.nativewindows.ExperimentalNativeEngine.main(args);
        }
    }
}
