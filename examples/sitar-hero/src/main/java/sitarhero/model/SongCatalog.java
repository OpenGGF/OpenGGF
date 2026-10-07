package sitarhero.model;

import sitarhero.catalogue.Sonic1Catalogue;
import sitarhero.catalogue.Sonic2Catalogue;
import sitarhero.catalogue.Sonic3kCatalogue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Full musical forms from supplied ROMs; short title/countdown cues are not tour songs. */
public final class SongCatalog {
    private SongCatalog() { }
    public record TempoAnchor(double beat, int serviceFrame) { }
    public record MusicalForm(double introBeats, double loopBeats, double endBeats) { }

    public static List<SongArrangement> arrangements() {
        var forms = new ArrayList<SongArrangement>();
        forms.addAll(Sonic1Catalogue.all().stream()
                .filter(form -> !List.of("s1-title", "s1-continue").contains(form.id())).toList());
        forms.addAll(Sonic2Catalogue.all());
        forms.addAll(Sonic3kCatalogue.all().stream()
                .filter(form -> !List.of("s3-title", "s3k-title", "s3-knuckles", "s3k-knuckles").contains(form.id())).toList());
        return List.copyOf(forms);
    }
    public static Optional<SongArrangement> arrangement(String id) {
        return arrangements().stream().filter(form -> form.id().equals(id)).findFirst();
    }
    public static List<TempoAnchor> tempoAnchors(String id) {
        if ("s1-credits".equals(id)) return Sonic1Catalogue.tempoAnchors(id).stream()
                .map(anchor -> new TempoAnchor(anchor.beat(), anchor.serviceFrame())).toList();
        if ("credits-s2".equals(id)) return List.of(new TempoAnchor(0, 0),
                new TempoAnchor(36, 921), new TempoAnchor(172, 4492),
                new TempoAnchor(196, 5211), new TempoAnchor(256, 7083),
                new TempoAnchor(7977.0 / 24, 9526));
        double divisor = Sonic3kCatalogue.tempoAnchorBeatDivisor(id);
        return Sonic3kCatalogue.tempoAnchors(id).stream()
                .map(anchor -> new TempoAnchor(anchor.beat() / divisor, anchor.serviceFrame())).toList();
    }
    public static MusicalForm musicalForm(String id) {
        var form = arrangement(id).orElseThrow(() -> new IllegalArgumentException("No authored form for " + id));
        if (form.game().equals("s3k")) {
            var exact = Sonic3kCatalogue.nativeForm(id);
            double unit = exact.unitsPerBeat();
            return new MusicalForm(exact.introUnits() / unit, exact.loopUnits() / unit, exact.endUnits() / unit);
        }
        return new MusicalForm(form.introBeats(), form.loopFrames() == 0 ? 0 : form.loopBeats(),
                form.loopFrames() == 0 ? form.loopBeats() : 0);
    }
    public static List<SongSpec> all() {
        return arrangements().stream().map(SongArrangement::song).toList();
    }
    public static List<SongSpec> available(List<String> installedGames) {
        return all().stream().filter(song -> installedGames.contains(song.game())).toList();
    }
}
