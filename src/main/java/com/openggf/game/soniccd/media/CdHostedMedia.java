package com.openggf.game.soniccd.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.internal.HostedGameMedia;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.CdZone;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.game.soniccd.release.CdRetailReleases;
import com.openggf.configuration.SonicConfigurationService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Sonic CD's media: the user's USA disc and stage names. The launcher lists and boots the game through
 * this ({@link #probe}, {@link #openConfiguredMedia}); the capture tools open it the same way. The disc is
 * found by {@link CdDiscLocator}: {@code -Dsoniccd.media.root=<dir holding USA/*.cue or USA/*.chd>} for tests
 * and tools, then the {@code roms.soniccd} key (the sheet's or CHD's path), then the ROM folder ({@code roms.directory}) and its
 * sub-folders.
 *
 * <p>A trace names its stage by {@code stage_file} (the recorder writes the stage program, e.g.
 * {@code R11B}), which carries act and period; the CLI accepts that name, or a zone's initials
 * ({@code ppz}, {@code ccz}, ..., {@code mmz}) with a one-based act index in the zone's stage order
 * (1-4 act 1 Present, Past, Good Future, Bad Future; 5-8 act 2; 9-10 act 3 Good and Bad Future).
 */
public final class CdHostedMedia implements HostedGameMedia {
    /** The property the Sonic CD disc tests read. */
    public static final String MEDIA_ROOT_PROPERTY = "soniccd.media.root";

    @Override public String displayName() { return "Sonic CD"; }
    @Override public String menuLabel() { return "Sonic CD"; }

    @Override
    public Availability probe(SonicConfigurationService config) {
        CdDiscLocator.Result found = CdDiscLocator.locate(config);
        return new Availability(found.found(), found.detail());
    }

    /**
     * Opens the located USA disc and verifies it whole: every track's content must equal the USA
     * release's record ({@link CdRetailReleases#USA}, Redump 29188). Another release, a damaged or
     * replaced track, or a missing disc is refused with an {@link IOException} saying which.
     */
    @Override
    public GameDataSource openConfiguredMedia(SonicConfigurationService config) throws IOException {
        CdDiscLocator.Result found = CdDiscLocator.locate(config);
        if (!found.found()) throw new IOException(found.detail());
        return openVerified(found.disc().orElseThrow());
    }

    /** Accepts a CUE sheet or a CHD only when its decoded content equals the USA release's record. */
    static CdDiscDataSource openVerified(Path disc) throws IOException {
        CdDiscDataSource source = CdDiscDataSource.open(disc);
        try {
            var identity = source.assets().identity();
            var release = CdRetailReleases.find(identity);
            if (release.isEmpty() || release.get() != CdRetailReleases.USA) {
                throw new IOException(disc.getFileName() + " is not an unmodified Sonic CD (USA) disc: "
                        + (release.map(r -> "it matches the " + r.variant() + " release").orElse(
                        "its tracks differ from the USA release (a damaged, replaced or re-encoded track)"))
                        + ". OpenGGF plays the USA release (Redump 29188).");
            }
            return source;
        } catch (IOException refused) {
            try {
                source.close();
            } catch (IOException closeFailure) {
                refused.addSuppressed(closeFailure);
            }
            throw refused;
        }
    }

    /**
     * The first {@code .cue} under {@code <root>/USA}, else the first {@code .chd} there: the layout the tests
     * and tools name with the property.
     */
    static Path propertyDisc(String root) throws IOException {
        Path usa = Path.of(root, "USA");
        if (!Files.isDirectory(usa)) {
            throw new IOException("No USA directory under " + MEDIA_ROOT_PROPERTY + "=" + root);
        }
        List<Path> images;
        try (Stream<Path> listed = Files.list(usa)) {
            images = listed.filter(CdDiscLocator::isDiscImageName).sorted().toList();
        }
        return images.stream().filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).findFirst()
                .or(() -> images.stream().findFirst())
                .orElseThrow(() -> new IOException("No USA .cue or .chd under " + usa));
    }

    /**
     * The stage was reached by New Game ({@code NewGame}/{@code RunR1}, IPX.asm:162-174), the campaign
     * route, so a warp continues into the new period ({@code RunLevel}, IPX.asm:555-573). The retail
     * stage-select route returns to the title after one stage (IPX.asm:726-727), which is not a run.
     * Only act 1 Present has a New Game entry.
     */
    @Override
    public void declareRunPastTrace(GameModule module) {
        var scenes = module.getGameService(com.openggf.game.soniccd.program.CdSceneCoordinator.class);
        if (scenes == null || !(GameServices.level().getCurrentLevel() instanceof com.openggf.game.soniccd.level.CdLevel level)
                || level.payload() != CdStagePayload.R11A) {
            throw new IllegalStateException("A clip past the trace's last row starts from Palmtree Panic act 1 Present (New Game)");
        }
        scenes.titleExited(com.openggf.game.soniccd.program.CdProgramFlow.EXIT_NEW_GAME);
    }

    @Override
    public Level levelOfTrace(Path traceDir) throws IOException {
        Path metadata = traceDir.resolve("metadata.json");
        JsonNode root = new ObjectMapper().readTree(metadata.toFile());
        JsonNode stage = root.get("stage_file");
        if (stage == null || stage.asText().isBlank()) {
            throw new IOException(metadata + " names no stage_file; a Sonic CD trace records its stage program");
        }
        return level(payload(stage.asText()));
    }

    @Override
    public Level levelNamed(String zone, int oneBasedAct) {
        String text = zone.trim().toUpperCase(Locale.ROOT);
        for (CdStagePayload payload : CdStagePayload.values()) {
            if (payload.name().equals(text)) return level(payload);
        }
        CdZone named = null;
        if (text.matches("\\d+")) {
            int index = Integer.parseInt(text);
            if (index < CdZone.values().length) named = CdZone.values()[index];
        } else {
            for (CdZone candidate : CdZone.values()) {
                if (initials(candidate).equals(text)) named = candidate;
            }
        }
        if (named == null) {
            throw new IllegalArgumentException("Unknown Sonic CD zone '" + zone
                    + "' (use a stage such as R11B, or ppz, ccz, ttz, qqz, wwz, ssz, mmz with --act)");
        }
        int act = oneBasedAct - 1;
        int count = stagesIn(named);
        if (act < 0 || act >= count) {
            throw new IllegalArgumentException("Sonic CD " + initials(named).toLowerCase(Locale.ROOT)
                    + " has stages 1-" + count + " (1-4 act 1 Present, Past, Good Future, Bad Future; 5-8 act 2; "
                    + "9-10 act 3 Good, Bad Future)");
        }
        return new Level(named.ordinal(), act);
    }

    private static CdStagePayload payload(String name) throws IOException {
        try {
            return CdStagePayload.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IOException("Unknown Sonic CD stage_file '" + name + "'");
        }
    }

    private static Level level(CdStagePayload payload) {
        return new Level(SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
    }

    private static int stagesIn(CdZone zone) {
        int count = 0;
        for (CdStagePayload p : CdStagePayload.values()) if (p.stage().zone() == zone) count++;
        return count;
    }

    /** {@code PALMTREE_PANIC} is {@code PPZ}. */
    static String initials(CdZone zone) {
        StringBuilder out = new StringBuilder();
        for (String word : zone.name().split("_")) out.append(word.charAt(0));
        return out.append('Z').toString();
    }
}
