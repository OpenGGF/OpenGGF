package openggf.racing.server.master;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import openggf.racing.server.config.SecretFile;

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
        long newIdentityCacheTtlMinutes,
        int maxRecordingBytes,
        long uploadDeadlineSeconds,
        long verifiedUploadDeadlineSeconds,
        long recordingRetentionDays,
        long verdictGraceMillis,
        int spotCheckTopTimes,
        long cheatBanDays,
        String verifierRegistrationToken,
        long verifierStaleSeconds,
        long verifierLeaseSeconds,
        String publicBaseUrl,
        long maxRecordingStorageBytes,
        long recordingUploadMinIntervalMillis,
        String chatModeLimit) {
    private static final long MIN_VERIFIER_STALE_SECONDS = 120;

    public MasterConfig {
        chatModeLimit = chatModeLimit == null ? "QUICK_CHAT" : chatModeLimit;
        if (com.openggf.net.protocol.ChatMode.parse(chatModeLimit) == null) {
            throw new IllegalArgumentException("chatModeLimit must be FULL_CHAT, QUICK_CHAT or OFF");
        }
        port = port == null ? 27_900 : port;
        dbPath = blankToDefault(dbPath, "master-identities.db");
        adminPort = adminPort == null ? 27_901 : adminPort;
        adminToken = adminToken == null ? "" : adminToken;
        if (!adminToken.isEmpty()) SecretFile.requireToken(adminToken);
        if (verifierRegistrationToken != null && !verifierRegistrationToken.isEmpty()) {
            SecretFile.requireToken(verifierRegistrationToken);
        }
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
        maxRecordingBytes = maxRecordingBytes <= 0 ? 65_536 : maxRecordingBytes;
        uploadDeadlineSeconds = uploadDeadlineSeconds <= 0 ? 180 : uploadDeadlineSeconds;
        verifiedUploadDeadlineSeconds = verifiedUploadDeadlineSeconds <= 0
                ? 15 : verifiedUploadDeadlineSeconds;
        recordingRetentionDays = recordingRetentionDays <= 0 ? 3 : recordingRetentionDays;
        verdictGraceMillis = verdictGraceMillis <= 0 ? 10_000 : verdictGraceMillis;
        spotCheckTopTimes = spotCheckTopTimes <= 0 ? 1 : spotCheckTopTimes;
        verifierStaleSeconds = verifierStaleSeconds <= 0 ? 120 : verifierStaleSeconds;
        if (verifierStaleSeconds < MIN_VERIFIER_STALE_SECONDS) {
            throw new IllegalArgumentException("verifierStaleSeconds must be at least "
                    + MIN_VERIFIER_STALE_SECONDS);
        }
        verifierLeaseSeconds = verifierLeaseSeconds <= 0 ? 300 : verifierLeaseSeconds;
        publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl;
        maxRecordingStorageBytes = maxRecordingStorageBytes <= 0
                ? 512L * 1024 * 1024 : maxRecordingStorageBytes;
        recordingUploadMinIntervalMillis = recordingUploadMinIntervalMillis <= 0
                ? 1_000 : recordingUploadMinIntervalMillis;
    }

    /** Source compatibility for existing server configuration callers. */
    public MasterConfig(Integer port,
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
                        long newIdentityCacheTtlMinutes,
                        int maxRecordingBytes,
                        long uploadDeadlineSeconds,
                        long verifiedUploadDeadlineSeconds,
                        long recordingRetentionDays,
                        long verdictGraceMillis,
                        int spotCheckTopTimes,
                        long cheatBanDays,
                        String verifierRegistrationToken,
                        long verifierStaleSeconds,
                        long verifierLeaseSeconds,
                        String publicBaseUrl,
                        long maxRecordingStorageBytes,
                        long recordingUploadMinIntervalMillis) {
        this(port, tlsCertPath, tlsKeyPath, plaintextForTest, dbPath, adminPort, adminToken,
                establishedAgeHours, establishedCleanRounds, trustedAgeDays, trustedCleanRounds,
                identityPowBits, attackModePowBits, attackMode, maxRoomsPerIdentity, maxRoomsPerIp,
                roomHeartbeatTimeoutSeconds, browserPageSize, identityGcInactiveDays,
                newIdentityCacheSize, newIdentityCacheTtlMinutes, maxRecordingBytes,
                uploadDeadlineSeconds, verifiedUploadDeadlineSeconds, recordingRetentionDays,
                verdictGraceMillis, spotCheckTopTimes, cheatBanDays, verifierRegistrationToken,
                verifierStaleSeconds, verifierLeaseSeconds, publicBaseUrl, maxRecordingStorageBytes,
                recordingUploadMinIntervalMillis, null);
    }

    /** Compatibility constructor for phase-3 call sites and configurations. */
    public MasterConfig(Integer port, String tlsCertPath, String tlsKeyPath,
                        boolean plaintextForTest, String dbPath, Integer adminPort,
                        String adminToken, long establishedAgeHours,
                        int establishedCleanRounds, long trustedAgeDays,
                        int trustedCleanRounds, int identityPowBits,
                        int attackModePowBits, boolean attackMode,
                        int maxRoomsPerIdentity, int maxRoomsPerIp,
                        long roomHeartbeatTimeoutSeconds, int browserPageSize,
                        long identityGcInactiveDays, int newIdentityCacheSize,
                        long newIdentityCacheTtlMinutes) {
        this(port, tlsCertPath, tlsKeyPath, plaintextForTest, dbPath, adminPort,
                adminToken, establishedAgeHours, establishedCleanRounds,
                trustedAgeDays, trustedCleanRounds, identityPowBits,
                attackModePowBits, attackMode, maxRoomsPerIdentity, maxRoomsPerIp,
                roomHeartbeatTimeoutSeconds, browserPageSize, identityGcInactiveDays,
                newIdentityCacheSize, newIdentityCacheTtlMinutes,
                0, 0, 0, 0, 0, 0, 0, null, 0, 0, "", 0, 0);
    }

    /** The requested relay mode restricted by the public server's configured maximum. */
    public String relayChatMode(String requested) {
        var choice = com.openggf.net.protocol.ChatMode.parse(requested);
        if (choice == null) {
            throw new IllegalArgumentException("unknown room chat mode");
        }
        return choice.intersect(com.openggf.net.protocol.ChatMode.parse(chatModeLimit)).name();
    }

    public static MasterConfig defaults() {
        return new MasterConfig(null, null, null, false, null, null, null,
                0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, null, 0, 0, "", 0, 0);
    }

    public static MasterConfig load(Path yamlFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        JsonNode root;
        try {
            root = mapper.readTree(Files.readAllBytes(yamlFile));
        } catch (JsonProcessingException malformed) {
            // YAML errors include source snippets (and sometimes nested parser snippets).
            // Keep coordinates, but never retain a cause that can echo literal credentials.
            JsonLocation location = malformed.getLocation();
            String position = location == null ? "" : " at line " + location.getLineNr()
                    + ", column " + location.getColumnNr();
            throw new IOException("invalid master YAML" + position);
        }
        if (!(root instanceof ObjectNode config)) {
            throw new IOException("master configuration must be a YAML mapping");
        }
        resolveSecretFile(config, "adminToken", yamlFile);
        resolveSecretFile(config, "verifierRegistrationToken", yamlFile);
        return mapper.treeToValue(config, MasterConfig.class);
    }

    private static void resolveSecretFile(ObjectNode config, String name, Path yamlFile)
            throws IOException {
        String fileName = name + "File";
        JsonNode file = config.remove(fileName);
        if (file == null) return;
        if (config.has(name)) {
            throw new IOException("configure either " + name + " or " + fileName + ", not both");
        }
        if (!file.isTextual() || file.asText().isBlank()) {
            throw new IOException(fileName + " must be a nonempty path");
        }
        Path secret = yamlFile.toAbsolutePath().getParent().resolve(file.asText());
        config.put(name, SecretFile.read(secret));
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
