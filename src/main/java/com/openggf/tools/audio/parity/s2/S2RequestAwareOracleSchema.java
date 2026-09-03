package com.openggf.tools.audio.parity.s2;

/** Fixed, unbound identities for the request-aware bounded S2 candidate. */
final class S2RequestAwareOracleSchema {
    public static final String PAYLOAD_SCHEMA = "openggf.s2-oracle-audio-raw.v2";
    public static final String SOURCE_SCHEMA = "openggf.s2-complete-run-audio-raw.v3";
    public static final String REQUEST_TRANSFER_SCHEMA =
            "openggf.s2-preconsumption-request-transfer.v1";
    public static final String S2_REV01_SHA1 = "8bca5dcef1af3e00098666fd892dc1c2a76333f9";
    public static final String BK2_SHA256 =
            "e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5";
    public static final String SERVICE_MANIFEST_SHA256 =
            "ef8f8103c38d70e41cb09cb29751f56815a0401709dc509071aa514d614813a0";
    public static final String INVENTORY_DIGEST_DOMAIN = "compact-json-lf-v1";
    public static final String BODY_DIGEST_DOMAIN = "bounded-jsonl-body-bytes-v1";
    public static final String TERMINAL_STATE_DIGEST_DOMAIN = "decoded-z80-state-bytes-v1";
    public static final String PAYLOAD_PREFIX_DIGEST_DOMAIN =
            "bounded-jsonl-before-cutoff-bytes-v1";
    public static final int FIRST_ROW = 10_150;
    public static final int EXCLUSIVE_END = 10_900;
    public static final int SOURCE_FIRST_ROW = 769;
    public static final int SOURCE_EXCLUSIVE_END = 259_590;
    public static final int STATE_BYTES = 0x2000;
    public static final int REQUEST_PC = 0x10d6;
    /** The fixed pre-execution instruction at {@link #REQUEST_PC}: move.b D0,$09(A1,D1.w). */
    public static final String REQUEST_OPCODE = "13801009";
    public static final int REQUEST_MARKER_TOKEN = 24;
    public static final int REQUEST_SERVICE_MARKER_TOKEN = 25;
    public static final int MAX_LINE_BYTES = 1 << 20;

    /**
     * One published bounded candidate: the recording it was cut from and the
     * exact movie rows it covers. Every field is an identity the strict reader
     * matches exactly, so widening to another interval or another recording
     * stays a pinned match rather than a relaxed one.
     */
    record Window(String name, String bk2Sha256, int firstRow, int exclusiveEnd,
            int sourceFirstRow, int sourceExclusiveEnd) {
        Window {
            if (firstRow < 0 || exclusiveEnd <= firstRow) {
                throw new IllegalArgumentException("window is not a valid interval");
            }
        }
    }

    /** The originally published EHZ-reload window, and the default everywhere. */
    static final Window CONTROL = new Window("w10150-10900", BK2_SHA256,
            FIRST_ROW, EXCLUSIVE_END, SOURCE_FIRST_ROW, SOURCE_EXCLUSIVE_END);

    private S2RequestAwareOracleSchema() {
    }
}
