package com.openggf.net.master;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** External configuration for the master/browser server. */
public record MasterConfig(
        Integer port,
        String tlsCertPath,
        String tlsKeyPath,
        boolean plaintextForTest,
        String dbPath,
        Integer adminPort,
        String adminToken,
        long establishedAgeHours,
        int establishedCleanRounds,
        long trustedAgeDays,
        int trustedCleanRounds,
        int identityPowBits,
        int attackModePowBits,
        boolean attackMode,
        int maxRoomsPerIdentity,
        int maxRoomsPerIp,
        long roomHeartbeatTimeoutSeconds,
        int browserPageSize,
        long identityGcInactiveDays,
        int newIdentityCacheSize,
        long newIdentityCacheTtlMinutes) {

    public MasterConfig {
        port = port == null ? 27_900 : port;
        dbPath = blankToDefault(dbPath, "master-identities.db");
        adminPort = adminPort == null ? 27_901 : adminPort;
        adminToken = adminToken == null ? "" : adminToken;
        establishedAgeHours = establishedAgeHours <= 0 ? 48 : establishedAgeHours;
        establishedCleanRounds = establishedCleanRounds <= 0 ? 10 : establishedCleanRounds;
        trustedAgeDays = trustedAgeDays <= 0 ? 14 : trustedAgeDays;
        trustedCleanRounds = trustedCleanRounds <= 0 ? 50 : trustedCleanRounds;
        identityPowBits = identityPowBits <= 0 ? 20 : identityPowBits;
        attackModePowBits = attackModePowBits <= 0 ? 22 : attackModePowBits;
        maxRoomsPerIdentity = maxRoomsPerIdentity <= 0 ? 2 : maxRoomsPerIdentity;
        maxRoomsPerIp = maxRoomsPerIp <= 0 ? 4 : maxRoomsPerIp;
        roomHeartbeatTimeoutSeconds = roomHeartbeatTimeoutSeconds <= 0
                ? 30 : roomHeartbeatTimeoutSeconds;
        browserPageSize = browserPageSize <= 0 ? 20 : browserPageSize;
        identityGcInactiveDays = identityGcInactiveDays <= 0 ? 30 : identityGcInactiveDays;
        newIdentityCacheSize = newIdentityCacheSize <= 0 ? 10_000 : newIdentityCacheSize;
        newIdentityCacheTtlMinutes = newIdentityCacheTtlMinutes <= 0
                ? 60 : newIdentityCacheTtlMinutes;
    }

    public static MasterConfig defaults() {
        return new MasterConfig(null, null, null, false, null, null, null,
                0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0);
    }

    public static MasterConfig load(Path yamlFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper.readValue(Files.readAllBytes(yamlFile), MasterConfig.class);
    }

    public TrustLadder.Thresholds thresholds() {
        return new TrustLadder.Thresholds(establishedAgeHours * 3_600_000L,
                establishedCleanRounds, trustedAgeDays * 24 * 3_600_000L,
                trustedCleanRounds);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
