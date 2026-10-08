package eggsky.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import eggsky.Game;
import eggsky.Mode;
import eggsky.world.Planet;
import eggsky.world.PlanetSpec;
import eggsky.world.Species;
import eggsky.world.StarSystem;
import java.util.ArrayList;
import java.util.List;

/** Browse visited planets and their surveys without moving the player or awarding discoveries. */
public final class JournalMode implements Mode {
    private record Entry(StarSystem system, PlanetSpec spec, String key) { }
    private final Mode back;
    private final List<Entry> planets = new ArrayList<>();
    private int planetRow;
    private int row;
    private int kind;
    private int age;
    private Planet survey;
    private String portraitId;
    private SceneSprite portrait;

    public JournalMode(Mode back) { this.back = back; }
    @Override public boolean live() { return false; }
    @Override public void enter(Game g) {
        planets.clear();
        for (String key : g.player.visitedPlanets) {
            String[] parts = key.split(":");
            try {
                StarSystem system = g.galaxy.system(Long.parseLong(parts[0]));
                int index = Integer.parseInt(parts[1]);
                if (system != null && index >= 0 && index < system.planetCount) {
                    planets.add(new Entry(system, system.planets(g.usableBiomes).get(index), key));
                }
            } catch (RuntimeException ignored) {
                // Ignore malformed legacy visit entries without damaging the save.
            }
        }
    }
    private List<Species> species() {
        return survey == null ? List.of() : survey.allSpecies().stream().filter(s -> s.kind == kind).toList();
    }
    @Override public void update(Game g) {
        age++;
        if (g.in.backPressed || g.in.menuPressed && !g.in.confirmPressed) {
            if (survey != null) {
                survey = null;
                portrait = null;
                portraitId = null;
            } else g.setMode(back);
            return;
        }
        if (survey != null && (g.in.leftPressed || g.in.rightPressed)) {
            kind = Math.floorMod(kind + (g.in.rightPressed ? 1 : -1), 3);
            row = 0;
        }
        int size = survey == null ? planets.size() : species().size();
        int delta = g.in.downRepeat ? 1 : g.in.upRepeat ? -1 : -g.in.wheel;
        if (size > 0 && delta != 0) {
            if (survey == null) planetRow = Math.floorMod(planetRow + delta, size);
            else row = Math.floorMod(row + delta, size);
        }
        if (survey == null && !planets.isEmpty() && g.in.confirmPressed && age > 2) {
            Entry e = planets.get(planetRow);
            Planet current = g.planet();
            survey = current != null && current.key.equals(e.key) ? current : new Planet(e.spec, e.system,
                    g.art.rom(e.spec.biome.game()).levelKit(e.spec.biome.zone(), e.spec.biome.act()), g.art, false);
            row = 0;
            kind = 0;
        }
        if (survey != null && !species().isEmpty()) {
            Species s = species().get(row);
            if (!s.id.equals(portraitId)) {
                portraitId = s.id;
                portrait = null;
                if (g.player.discovered.contains(s.id)) {
                    if (s.body != null) {
                        var set = g.art.creature(s.body, survey.palette, s.body.key() + "@journal:" + survey.spec.biome.key());
                        if (set != null) {
                            SceneSprite raw = set.frame(s.body.frames()[0]);
                            if (raw != null) portrait = new SceneSprite(s.recolor.apply(raw.image()), raw.originX(), raw.originY());
                        }
                    } else if (s.sprites != null && s.sprites.length > 0) portrait = s.sprites[0];
                }
            }
        }
    }
    private static String shortText(String text, int length) {
        return text.length() <= length ? text : text.substring(0, length - 3) + "...";
    }
    @Override public void draw(Game g, SceneCanvas c) {
        c.fill(0, 0, g.width, g.height, 0xFF080C20);
        Ui.panel(c, 6, 6, g.width - 12, g.height - 12);
        Font f = g.font;
        f.draw(c, "DISCOVERY JOURNAL", 16, 16, Ui.GOLD);
        if (planets.isEmpty()) {
            f.draw(c, "Land on a planet to start your journal.", 16, 45, Ui.GREY);
        } else if (survey == null) {
            f.right(c, planets.size() + " PLANETS", g.width - 16, 16, Ui.CYAN);
            int start = Math.max(0, planetRow - 9);
            for (int i = start; i < Math.min(planets.size(), start + 10); i++) {
                Entry e = planets.get(i);
                int y = 36 + (i - start) * 15;
                if (i == planetRow) Ui.focus(c, 12, y - 2, g.width - 24, 13, g.ticks);
                f.draw(c, shortText(g.player.names.getOrDefault(e.key, e.spec.name), 25), 18, y, Ui.WHITE);
                f.draw(c, shortText(e.system.name, 22), 180, y, Ui.DIM);
            }
            Entry e = planets.get(planetRow);
            f.draw(c, shortText(e.spec.summary() + " / " + e.spec.stormName(), 60), 16, 190, Ui.CYAN);
        } else {
            f.right(c, shortText(g.player.names.getOrDefault(survey.key, survey.spec.name), 30), g.width - 16, 16, Ui.CYAN);
            String[] kinds = {"FAUNA", "FLORA", "MINERALS"};
            for (int k = 0; k < 3; k++) {
                int filter = k;
                List<Species> group = survey.allSpecies().stream().filter(s -> s.kind == filter).toList();
                long known = group.stream().filter(s -> g.player.discovered.contains(s.id)).count();
                f.draw(c, kinds[k] + " " + known + "/" + group.size(), 16 + k * 125, 34, k == kind ? Ui.GOLD : Ui.DIM);
            }
            List<Species> list = species();
            int start = Math.max(0, row - 9);
            for (int i = start; i < Math.min(list.size(), start + 10); i++) {
                Species s = list.get(i);
                int y = 52 + (i - start) * 14;
                if (i == row) Ui.focus(c, 12, y - 2, 166, 12, g.ticks);
                f.draw(c, g.player.discovered.contains(s.id) ? shortText(s.name, 26) : "??? UNDISCOVERED", 16, y, Ui.WHITE);
            }
            if (list.isEmpty()) f.draw(c, "No species in this category.", 16, 60, Ui.DIM);
            else drawCard(g, c, list.get(row));
        }
        f.draw(c, survey == null ? "A/ENTER: SURVEY   B/X: BACK" : "LEFT/RIGHT: CATEGORY   B/X: PLANETS", 16, 205, Ui.GREY);
    }
    private void drawCard(Game g, SceneCanvas c, Species s) {
        Font f = g.font;
        if (!g.player.discovered.contains(s.id)) {
            f.draw(c, "UNKNOWN SPECIES", 190, 60, Ui.GOLD);
            f.draw(c, "Use the analysis visor", 190, 80, Ui.GREY);
            f.draw(c, "to catalogue it.", 190, 90, Ui.GREY);
            return;
        }
        if (portrait != null) {
            float scale = Math.min(2, Math.min(150f / portrait.width(), 55f / portrait.height()));
            c.draw(portrait, 282 - portrait.width() * scale / 2 + portrait.originX() * scale,
                    54 + portrait.originY() * scale, SceneDraw.plain().withScale(scale));
        }
        List<String> details = new ArrayList<>();
        details.add(s.name);
        details.add(s.kindName());
        if (s.body != null) {
            details.add(s.temperamentName() + " / " + s.diet);
            details.add(s.note);
        } else {
            details.add("Yields: " + g.catalog.name(s.yield));
            details.add("Typical yield: " + s.yieldCount);
        }
        int y = 118;
        for (String detail : details) {
            for (String line : Ui.wrap(detail, 30)) {
                if (y > 188) break;
                f.draw(c, line, 190, y, y == 118 ? Ui.GOLD : Ui.GREY);
                y += 10;
            }
        }
    }
}
