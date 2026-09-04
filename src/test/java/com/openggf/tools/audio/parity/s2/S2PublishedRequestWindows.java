package com.openggf.tools.audio.parity.s2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * The committed request-aware S2 candidates: one bounded window each, every
 * one an exact identity the strict reader matches rather than a relaxation.
 * The first entry is the originally published EHZ-reload window; the rest
 * widen coverage along the same complete run and onto a second recording.
 * All of them are comparison-only reference data and hydrate no engine state.
 */
final class S2PublishedRequestWindows {
    private S2PublishedRequestWindows() {
    }

    static final String COMPLETE_RUN_BK2_SHA256 =
            "e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5";
    static final String CPZ_LEVEL_SELECT_BK2_SHA256 =
            "7e28cf822d5dbbe64646965cd857f264bf51d3349075af94db1a818cac7311e4";

    /**
     * One published candidate: where its payload lives, what it must hash to,
     * the exact window it covers, and how many pre-consumption request
     * transfers the extractor observed inside it.
     */
    record Published(String name, String resource, String payloadGzSha256,
            String payloadRawSha256, int requestTransfers,
            S2RequestAwareOracleSchema.Window window) {

        Path expand(Path directory) throws IOException {
            Path expanded = directory.resolve(name + ".oracle-raw-v2.jsonl");
            if (Files.exists(expanded)) {
                return expanded;
            }
            try (InputStream input = new GZIPInputStream(open());
                 OutputStream output = Files.newOutputStream(expanded)) {
                input.transferTo(output);
            }
            return expanded;
        }

        InputStream open() {
            InputStream stream = S2PublishedRequestWindows.class
                    .getResourceAsStream(resource);
            if (stream == null) {
                throw new IllegalStateException(
                        "committed S2 request window is absent: " + resource);
            }
            return stream;
        }
    }

    private static Published complete(String name, String gz, String raw,
            int transfers, int firstRow, int exclusiveEnd) {
        return new Published(name,
                "/audio/parity/s2/" + name + ".raw-v2.jsonl.gz", gz, raw, transfers,
                new S2RequestAwareOracleSchema.Window(name, COMPLETE_RUN_BK2_SHA256,
                        firstRow, exclusiveEnd,
                        S2RequestAwareOracleSchema.SOURCE_FIRST_ROW,
                        S2RequestAwareOracleSchema.SOURCE_EXCLUSIVE_END));
    }

    /** The original window, whose reader defaults it also is. */
    static final Published CONTROL = complete("s2-request-window-w10150-10900",
            "be8ab87f45499fcf5db0aee5613d699f56d79d5d6a8ffacbbfbe21592ab95c15",
            "a7d56fe71674d9f4a9307e6fb6078f7832409bb310916e808faf28b1e9426c2c",
            25, 10_150, 10_900);

    /** The 750 rows after the original window, same segment and music epoch. */
    static final Published EHZ1_CONTINUATION = complete(
            "s2-request-window-w10900-11650",
            "9f55167012138b45799bac5da0d802a5fe9bfb94cbc8d23047a5406a7cda95e0",
            "b24c6f9144d8f4fe85316cf7d2a22020b916ec208280ac8001ab18876d3d9137",
            52, 10_900, 11_650);

    /** The next contiguous 750 rows, carrying coverage to movie row 12400. */
    static final Published EHZ1_CONTINUATION_TWO = complete(
            "s2-request-window-w11650-12400",
            "d23d19d6374905da5781224470711cf218be632b0601bf9db8b16e272b8cbe76",
            "04e9d7e1feb53cd5a2012bcab5813bce6262a9b7ef3bacd93565f8a12008bab0",
            27, 11_650, 12_400);

    /** The rows spanning the EHZ1 exit into the second special stage. */
    static final Published SPECIAL_STAGE_TRANSITION = complete(
            "s2-request-window-w13650-14400",
            "d465abe64b2bee433aa458ab93dd4b7068479f22fd7ea4007700dfc758b55330",
            "457e9870c381fef32dd37abfbcf2fe04fa041cfdf3548e62e3e1e789a438c871",
            5, 13_650, 14_400);

    /** A different route: the CPZ level load of the CPZ level-select recording. */
    static final Published CPZ_LOAD = new Published(
            "s2-request-window-cpz-w2700-3450",
            "/audio/parity/s2/s2-request-window-cpz-w2700-3450.raw-v2.jsonl.gz",
            "564325db4133c5baf232f91ee922460bc733bf2cf3bb49b6432b7ac4f938e25b",
            "f1e068cba2fbd342eed5d1d7d784ac8bdecd72a0d6ed58db68aad0893c0d2cbf",
            33,
            new S2RequestAwareOracleSchema.Window("s2-request-window-cpz-w2700-3450",
                    CPZ_LEVEL_SELECT_BK2_SHA256, 2_700, 3_450, 2_700, 3_450));

    /** Every committed candidate, original window first. */
    static final List<Published> ALL = List.of(CONTROL, EHZ1_CONTINUATION,
            EHZ1_CONTINUATION_TWO, SPECIAL_STAGE_TRANSITION, CPZ_LOAD);

    /**
     * The candidates the engine-side request oracle can drive today: those cut
     * from the committed complete run, which the run-chain harness replays.
     */
    static final List<Published> COMPLETE_RUN_WINDOWS = List.of(CONTROL,
            EHZ1_CONTINUATION, EHZ1_CONTINUATION_TWO, SPECIAL_STAGE_TRANSITION);
}
