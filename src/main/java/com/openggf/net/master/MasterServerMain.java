package com.openggf.net.master;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/** Engine-free master entry point. Usage: --config master.yaml --data ./master-data */
public final class MasterServerMain {
    private MasterServerMain() { }

    public static void main(String[] args) throws Exception {
        Path configPath = Path.of(argValue(args, "--config", "master.yaml"));
        Path dataDir = Path.of(argValue(args, "--data", "master-data"));
        MasterServer server = MasterServer.start(MasterConfig.load(configPath), dataDir);
        System.getLogger(MasterServerMain.class.getName()).log(System.Logger.Level.INFO,
                "master listening on port " + server.port()
                        + " (admin " + server.adminPort() + ")");
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        new CountDownLatch(1).await();
    }

    private static String argValue(String[] args, String flag, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        return fallback;
    }
}
