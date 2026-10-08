package sitarhero;

import com.openggf.control.PhysicalGamepad;
import com.openggf.control.PhysicalInput;
import com.openggf.control.PhysicalInputEvent;
import com.openggf.mods.scene.*;
import sitarhero.audio.HouseAudio;
import sitarhero.audio.HouseAudio.Cue;
import sitarhero.chart.ChartCurator;
import sitarhero.controls.*;
import sitarhero.model.*;
import sitarhero.net.OnlineMatch;
import sitarhero.stage.ConcertStage;
import sitarhero.stage.PerformerArt;
import sitarhero.stage.TitleStage;
import sitarhero.story.CareerStory;
import sitarhero.ui.HighwayView;
import sitarhero.ui.Motion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole game as one scene: the rules half. {@link #update} reads input and changes
 * state; {@link SitarScreens} draws that state and never changes it.
 *
 * <p>Flow: title → performer → instrument → difficulty → tour/world board or open setlist
 * → loading → count-in and performance → results. Settings, records, help and
 * direct-connect hang off the title. Every screen change happens on the tick its input
 * arrives; transitions, glides and sounds are layered on afterwards in
 * {@link #present}, so they never delay input or touch the music clock.
 *
 * <p>Debug capture commands ({@link #debugJump}): perform:SONG:ROLE:PERFORMER[:DIFFICULTY],
 * practice:..., local:SONG:ROLE:PERFORMER:DIFFICULTY:coop|versus, autoplay, pause, resume,
 * retry, settings, menu/title, career, quick, records, help. Autoplay is a labelled
 * tool-only performance, never a menu mode, and saves nothing.
 */
public final class SitarScene implements ModScene, DebuggableScene {
    public enum Screen { TITLE, CHARACTERS, ROLES, DIFFICULTY, TOURS, WORLDS, STORY, SONGS, LOADING, PLAY, PAUSED, RESULTS, SETTINGS, CALIBRATION, RECORDS, HELP, ONLINE, LOBBY, ERROR }
    public enum Mode { CAREER, QUICK_PLAY, PRACTICE, LOCAL_COOP, LOCAL_VERSUS, ONLINE_COOP, ONLINE_VERSUS }

    /** Title menu rows: shared by mouse hit-testing here and drawing in {@link SitarScreens}. */
    static final int TITLE_X = 12, TITLE_TOP = 58, TITLE_WIDTH = 168, TITLE_ROW = 13;

    // Fields are package-private so SitarScreens can read them; only this class writes them.
    SceneContext context;
    List<String> games = List.of();
    List<Roster> roster = List.of();
    List<SongSpec> songs = List.of();
    final Map<String, PerformerArt> actors = new HashMap<>();
    final ControlSettings settings = new ControlSettings();
    final MappedControls controls = new MappedControls(settings);
    final ControlSettings settings2 = new ControlSettings(1);
    final MappedControls controls2 = new MappedControls(settings2);
    final PlayerProfile profile = new PlayerProfile();
    final CareerJournal career = new CareerJournal();
    List<CareerTour> tours = List.of();
    CareerTour careerTour;
    CareerWorld careerWorld;
    CareerStory.Scene story;
    int storyLine;
    Screen storyReturn = Screen.SONGS;
    boolean careerRoleReturn;
    final HighwayView highway = new HighwayView(), highway2 = new HighwayView();
    Screen screen = Screen.TITLE;
    Screen settingsReturn = Screen.TITLE;
    Mode mode = Mode.CAREER;
    Difficulty difficulty = Difficulty.MEDIUM;
    Roster performer2 = Roster.ROBOTNIK;
    boolean choosingSecond;
    boolean settingsSecond;
    RhythmSession session2;
    PerformanceResult result, result2;
    boolean saved, newRecord;
    boolean saveFailed;
    OnlineMatch online;
    String onlineAddress = "127.0.0.1:34917";
    boolean editingAddress;
    int preparedRound;
    long sentProgress;
    int selected;
    Roster performer = Roster.SONIC;
    Role role = Role.SITAR;
    SongSpec song;
    ScenePreparedMusic prepared;
    SceneMusicPreparation preparation;
    boolean preparingPart;
    List<SceneMusicPart> selectedParts = List.of();
    Chart preparedChart;
    SceneMusicPlayer player;
    sitarhero.audio.PerformanceAudio performanceAudio;
    RhythmSession session;
    long loadingTick;
    long underruns;
    long best;
    boolean autoplay;
    boolean settingsDrums;
    boolean settingsPad;
    boolean capturingBinding;
    boolean calibratePending;
    boolean mouseAccepted;
    final long[] navigationRepeat = {-1, -1, -1, -1};
    String notice = "";
    String error = "";
    final List<Long> calibration = new ArrayList<>();
    long lastCalibrationNote = -1;
    long eofNanos = Long.MIN_VALUE;
    long pauseNanos;

    // Presentation: the venue behind every screen, the title tableau and the house sounds.
    ConcertStage venue;
    TitleStage title;
    int titleVisits;
    /** Title venues and band members already decoded; separate from {@link #actors} so rehearsal never touches play. */
    final Map<String, ConcertStage> titleVenues = new HashMap<>();
    final Map<String, PerformerArt> band = new HashMap<>();
    HouseAudio house;
    final SitarScreens screens = new SitarScreens(this);
    /** Tick the current screen opened, the screen before it, and whether Back opened it.
     *  The cursor glides in sixteenths of a row so the highlight moves smoothly. */
    long screenTick;
    Screen previousScreen = Screen.TITLE;
    boolean enteredBack;
    long storyLineTick;
    final Motion.Glide cursor = new Motion.Glide(Motion.GLIDE_TICKS);
    final Motion.Glide loadingBar = new Motion.Glide(12);
    /** The venue behind the current screen, and the one it is crossfading from. */
    ConcertStage shownStage, fadingStage;
    long stageTick = Long.MIN_VALUE / 2;

    @Override public void enter(SceneContext ctx) {
        context = ctx;
        games = ctx.art().availableGames();
        roster = Roster.availablePerformers(games);
        songs = SongCatalog.available(games);
        tours = CareerTours.available(songs);
        settings.read(ctx.storage().read("settings.txt").orElse(""));
        settings2.read(ctx.storage().read("settings-p2.txt").orElse(""));
        profile.read(ctx.storage().read("profile.txt").orElse(""));
        career.read(ctx.storage().read("career.txt").orElse(""));
        onlineAddress = ctx.storage().read("online-address.txt").filter(t -> t.length() <= 128).orElse(onlineAddress);
        // House cues use the running game's driver, whichever ROM supplies a song or scenery.
        SceneRomArt running = ctx.art().rom();
        house = new HouseAudio(ctx.audio(), running == null ? null : running.gameId());
        ctx.audio().stopMusic();
        if (roster.isEmpty() || songs.isEmpty()) { error = "Supply a supported Sonic ROM"; screen = Screen.ERROR; return; }
        performer = roster.get(0); song = songs.get(0);
        actor(performer);
        stage(song);
        title = TitleStage.build(ctx.art(), games, performer, titleVisits, ctx.width(), ctx.height(), titleVenues, band);
    }

    PerformerArt actor(Roster who) {
        return actors.computeIfAbsent(who.id(), ignored -> new PerformerArt(context.art().rom(who.game(games)), who.id()));
    }
    /** The song's own act when its ROM pictures it, else another pictured act from that same ROM. */
    private void stage(SongSpec source) {
        SceneRomArt rom = context.art().rom(source.game());
        SongSpec scenery = rom.hasZonePictures(source.zone(), source.act()) ? source
                : SongCatalog.available(List.of(source.game())).stream()
                .filter(candidate -> rom.hasZonePictures(candidate.zone(), candidate.act()))
                .findFirst().orElse(source);
        venue = ConcertStage.build(rom, scenery.zone(), scenery.act(), context.width(), context.height(), 0);
    }
    private void load(boolean calibrationMode) {
        stop(); calibratePending = calibrationMode;
        // Show the song's own venue from the first loading frame (calibration picks its song later).
        if (!calibrationMode) stage(song);
        screen = Screen.LOADING; loadingTick = context.ticks(); selected = 0; notice = "";
        preparingPart = false; selectedParts = List.of(); preparedChart = null;
        house.startGig(context.ticks(), calibrationMode ? Cue.CONFIRM : Cue.START);
    }
    private void prepare() {
        try {
            SongSpec source = calibratePending ? songs.stream()
                    .filter(candidate -> candidate.game().equals(song.game()) && candidate.availableRoles().contains(Role.BONGOS))
                    .findFirst().orElseGet(() -> songs.stream().filter(candidate -> candidate.availableRoles().contains(Role.BONGOS)).findFirst().orElseThrow()) : song;
            Role preparedRole = calibratePending ? Role.BONGOS : role;
            if (preparation == null) {
                if (calibratePending) stage(source);
                actor(performer);
                preparation = context.music().prepareAsync(source.game(), source.musicId(), calibratePending ? 1800 : source.durationFrames());
            }
            if (!preparationReady()) return;
            if (!preparingPart) {
                prepared = preparation.prepared();
                preparedChart = ChartCurator.curate(source, preparedRole, prepared, difficulty);
                if (preparedChart.notes().isEmpty()) throw new IllegalStateException("Selected musical part has no chart");
                selectedParts = calibratePending ? List.of(new SceneMusicPart(0, 0, 0, false)) : ChartCurator.audioParts(source, preparedRole, prepared);
                preparation = context.music().preparePartAsync(prepared, selectedParts);
                preparingPart = true;
                if (!preparationReady()) return;
            }
            preparation.prepared(); preparation = null;
            Chart chart = preparedChart;
            if (!calibratePending) {
            session = new RhythmSession(chart, role.drums(), prepared.sampleRate(), mode == Mode.PRACTICE || mode == Mode.LOCAL_COOP || mode == Mode.ONLINE_COOP);
            session2 = local() ? new RhythmSession(chart, role.drums(), prepared.sampleRate(), mode == Mode.LOCAL_COOP) : null;
            highway.reset(); highway2.reset(); result = null; result2 = null; saved = false; saveFailed = false;
            }
            // The song's PCM replaces the driver's output, so silence the driver first.
            house.beforePlayback();
            player = context.music().start(prepared, selectedParts, prepared.sampleRate() * 3);
            if (!calibratePending) performanceAudio = new sitarhero.audio.PerformanceAudio(player,prepared,chart,selectedParts,role,
                    local(),mode==Mode.LOCAL_COOP || mode==Mode.ONLINE_COOP);
            controls.reset(context.physicalInput(), role.drums());
            controls2.reset(context.physicalInput(), role.drums());
            underruns = player.underrunCount();
            if (!calibratePending) {
            best = profile.best(song.id(), role.name(), difficulty.name()).map(PerformanceResult::score).orElse(0L);
            if (difficulty == Difficulty.MEDIUM) best = Math.max(best, parseLong(context.storage().read(recordName()).orElse("0")));
            }
            calibration.clear(); lastCalibrationNote = -1;
            eofNanos = Long.MIN_VALUE;
            if (online != null && !calibratePending) {
                player.pause(); online.ready(chart, prepared.sampleRate()); screen = Screen.LOBBY;
            } else screen = calibratePending ? Screen.CALIBRATION : Screen.PLAY;
        } catch (RuntimeException failure) {
            stop(); error = "Song preparation failed: " + failure.getMessage(); screen = Screen.ERROR;
        }
    }

    private boolean preparationReady() {
        return switch (preparation.state()) {
            case READY -> true;
            case PREPARING -> false;
            case FAILED -> throw new IllegalStateException(preparation.error());
            case CANCELLED -> throw new IllegalStateException("Music preparation cancelled");
        };
    }

    @Override public void update(SceneContext ctx) {
        Screen before = screen;
        int beforeSelected = selected, beforeLine = storyLine;
        boolean beforeSecond = choosingSecond, backInput = back();
        step(ctx);
        present(before, beforeSelected, beforeSecond, beforeLine, backInput);
    }

    /**
     * Presentation bookkeeping after the rules ran: when a screen or selection changed,
     * start its transition and pick the cue; keep the menu theme playing on menus.
     */
    private void present(Screen before, int beforeSelected, boolean beforeSecond, int beforeLine, boolean backInput) {
        long now = context.ticks();
        if (screen != before || choosingSecond != beforeSecond) {
            previousScreen = before; screenTick = now; enteredBack = backInput;
            cursor.snap(selected * 16);
            if (screen == Screen.ERROR) house.cue(Cue.DENIED);
            else if (screen != Screen.LOADING && screen != Screen.RESULTS && !performing(screen))
                house.cue(backInput ? Cue.BACK : Cue.CONFIRM);
        } else if (selected != beforeSelected) {
            cursor.moveTo(selected * 16, now);
            house.cue(Cue.MOVE);
        } else if (storyLine != beforeLine) {
            storyLineTick = now;
            house.cue(Cue.MOVE);
        }
        if (screen == Screen.LOADING) {
            if (before != Screen.LOADING) loadingBar.snap(0);
            loadingBar.moveTo(loadingProgress(), now);
        }
        if (screen == Screen.RESULTS && result != null && result.cleared()) tally(now - screenTick);
        if (screen == Screen.TITLE && title != null) title.update(now);
        ConcertStage shown = backdropStage();
        if (shown != shownStage) { fadingStage = shownStage; shownStage = shown; stageTick = now; }
        if (player == null && !performing(screen) && screen != Screen.LOADING && screen != Screen.RESULTS) house.menuTheme();
        house.tick(now, player != null);
    }

    /** Ticks after results open before the score starts counting, and how long it counts. */
    static final int TALLY_DELAY = 30, TALLY_TICKS = 60;

    /** The results score counts up as Sonic's act-clear tally does: a click every 4 ticks, then the register. */
    private void tally(long age) {
        long counting = age - TALLY_DELAY;
        if (counting >= 0 && counting < TALLY_TICKS && counting % 4 == 0) house.cue(Cue.TALLY);
        else if (counting == TALLY_TICKS) house.cue(Cue.REGISTER);
    }

    /**
     * The venue behind a screen: until a song or world is chosen, the menus keep the title's
     * venue, so leaving the title never cuts the scenery; afterwards, the song's own act.
     */
    ConcertStage backdropStage() {
        boolean titleVenue = switch (screen) {
            case TITLE, CHARACTERS, ROLES, DIFFICULTY, TOURS, RECORDS, HELP, SETTINGS, ONLINE, LOBBY, ERROR -> true;
            default -> false;
        };
        return titleVenue && title != null ? title.stage() : venue;
    }

    private static boolean performing(Screen screen) {
        return screen == Screen.PLAY || screen == Screen.PAUSED || screen == Screen.CALIBRATION;
    }

    private void step(SceneContext ctx) {
        mouseAccepted = false;
        if (online != null) {
            online.tick(ctx.physicalInput().timestampNanos());
            if (!online.error().isEmpty()) {
                error = online.error(); stop(); online.close(); online = null; screen = Screen.ERROR; return;
            }
            if (online.connected()) songs = SongCatalog.available(online.commonGames());
            if (!online.host() && online.round() > preparedRound && screen != Screen.CHARACTERS) {
                stop(); screen = Screen.LOBBY;
            }
            if (player != null && online.startDue()) {
                online.started(); player.resume(); screen = Screen.PLAY;
                controls.reset(ctx.physicalInput(), role.drums()); sentProgress = 0;
            }
            if (player != null && online.ready()) {
                if (online.paused() && screen == Screen.PLAY) pauseLocal();
                else if (!online.paused() && screen == Screen.PAUSED) resumeLocal();
            }
        }
        if (screen == Screen.TITLE) { titleMenu(); return; }
        if (screen == Screen.RECORDS || screen == Screen.HELP) {
            menuMove(songs.size());
            if (screen == Screen.RECORDS) {
                Role before = role; Difficulty level = difficulty;
                if (context.keyPressed(SceneKeys.TAB)) role = Role.values()[(role.ordinal() + 1) % Role.values().length];
                int direction = left() ? -1 : right() ? 1 : 0;
                if (direction != 0) difficulty = Difficulty.values()[Math.floorMod(difficulty.ordinal() + direction, Difficulty.values().length)];
                if (role != before || difficulty != level) house.cue(Cue.MOVE);
            }
            if (back()) home(); return;
        }
        if (screen == Screen.ONLINE || screen == Screen.LOBBY) { onlineMenu(); return; }
        if (screen == Screen.LOADING) {
            if (back()) {
                stop();
                if (!calibratePending && online != null && !online.host()) preparedRound = Math.max(0, online.round() - 1);
                if (calibratePending) { screen = Screen.SETTINGS; selected = 13; }
                else returnToSongs();
            } else if (ctx.ticks() > loadingTick + 1) prepare();
            return;
        }
        if (screen == Screen.PLAY) { performance(); return; }
        if (screen == Screen.CALIBRATION) { calibrate(); return; }
        if (screen == Screen.SETTINGS) { settings(); return; }
        if (screen == Screen.PAUSED) {
            menuMove(3); mouseRows(3, (context.width() - 248) / 2 + 10, 83, 228, 26);
            if (accept()) {
                if (selected == 0) resume();
                else if (selected == 1) retry();
                else if (online != null && !online.host()) { notice = "The host chooses the next song"; house.cue(Cue.DENIED); }
                else { stop(); returnToSongs(); }
            } else if (context.keyPressed(SceneKeys.ESCAPE)) resume();
            return;
        }
        if (screen == Screen.ERROR) {
            mouseAccepted = context.mouse().leftPressed() && context.mouse().over(12, 55, context.width() - 24, 136);
            if (accept() || back()) {
                if (roster.isEmpty() || songs.isEmpty()) leave(false);
                else home();
            }
            return;
        }
        if (context.keyPressed(SceneKeys.TAB)) { settingsReturn = screen; screen = Screen.SETTINGS; selected = 0; return; }
        if (screen == Screen.TOURS || screen == Screen.WORLDS || screen == Screen.STORY) { careerMenu(); return; }
        if (screen == Screen.RESULTS) {
            menuMove(4); mouseRows(4, 18, 123, context.width() - 168, 16);
            if (accept()) {
                if (selected == 0) retry();
                else if (selected == 1) returnToSongs();
                else if (selected == 2) {
                    if (mode == Mode.CAREER) continueTour();
                    else { screen = Screen.CHARACTERS; selected = roster.indexOf(performer); }
                } else home();
            } else if (back()) returnToSongs();
            return;
        }
        if (screen == Screen.SONGS && mode == Mode.CAREER && (context.keyPressed(SceneKeys.I) || padPressed(PhysicalGamepad.BUTTON_X)
                || context.mouse().leftPressed() && context.mouse().over(context.width() - 132, 171, 112, 18))) {
            song = setlistSongs().get(selected); careerRoleReturn = true;
            screen = Screen.ROLES; selected = role.ordinal(); notice = "Tour progress follows you across instruments"; return;
        }
        int count = screen == Screen.CHARACTERS ? roster.size() : screen == Screen.ROLES ? Role.values().length
                : screen == Screen.DIFFICULTY ? Difficulty.values().length : setlistSongs().size();
        menuMove(count);
        mouseChoices(count, screen == Screen.SONGS ? 4 : screen == Screen.CHARACTERS ? 7 : 4);
        if (accept()) {
            if (screen == Screen.CHARACTERS) {
                if (choosingSecond) { performer2 = roster.get(selected); actor(performer2); choosingSecond = false; screen = Screen.ROLES; selected = role.ordinal(); }
                else {
                    performer = roster.get(selected); actor(performer);
                    if (local()) { choosingSecond = true; selected = roster.indexOf(performer2); }
                    else if (online != null && !online.host()) { screen = Screen.LOBBY; selected = 0; }
                    else { screen = Screen.ROLES; selected = role.ordinal(); }
                }
            } else if (screen == Screen.ROLES) {
                Role choice = Role.values()[selected];
                if (careerRoleReturn && !song.availableRoles().contains(choice)) {
                    notice = "This ROM song has no " + choice.label() + " part"; house.cue(Cue.DENIED); return;
                }
                role = choice; screen = Screen.DIFFICULTY; selected = difficulty.ordinal();
            }
            else if (screen == Screen.DIFFICULTY) {
                difficulty = Difficulty.values()[selected];
                if (mode == Mode.CAREER && !careerRoleReturn) { screen = Screen.TOURS; selected = Math.max(0, tours.indexOf(careerTour)); }
                else { careerRoleReturn = false; returnToSongs(); }
            }
            else {
                SongSpec choice = setlistSongs().get(selected);
                if (!choice.availableRoles().contains(role)) {
                    notice = "This ROM song has no " + shortRole() + " part";
                    house.cue(Cue.DENIED);
                    if (mode == Mode.CAREER) {
                        song = choice; careerRoleReturn = true; screen = Screen.ROLES;
                        selected = choice.availableRoles().getFirst().ordinal();
                    }
                    return;
                }
                song = choice;
                if (online != null) { online.offer(song, role, difficulty, mode == Mode.ONLINE_COOP); preparedRound = online.round(); }
                load(false);
            }
        } else if (back()) {
            if (screen == Screen.SONGS && mode == Mode.CAREER) openWorldBoard(false);
            else if (screen == Screen.SONGS) { screen = Screen.DIFFICULTY; selected = difficulty.ordinal(); }
            else if (screen == Screen.DIFFICULTY) { screen = Screen.ROLES; selected = role.ordinal(); }
            else if (screen == Screen.ROLES && careerRoleReturn) { careerRoleReturn = false; returnToSongs(); }
            else if (screen == Screen.ROLES) { screen = Screen.CHARACTERS; selected = roster.indexOf(performer); }
            else if (choosingSecond) { choosingSecond = false; selected = roster.indexOf(performer); }
            else home();
        }
    }

    List<String> titleItems() {
        return List.of("Career tour", "Quick play", "Practice", "Local co-op", "Local duel", "Direct-connect", "Records", "How to play", "Settings", "Stock Sonic");
    }
    /** One line per title item, shown in the footer while it is selected. */
    String titleHint(int item) {
        return switch (item) {
            case 0 -> "Tour every installed game; main acts open the next world";
            case 1 -> "Any song and part; best scores and stars are saved";
            case 2 -> "No fail and no records: learn a song at your own pace";
            case 3 -> "Two players share a song; the band fails only if both do";
            case 4 -> "Two players share a song; the higher score wins";
            case 5 -> "Co-op or duel with a peer over LAN or a forwarded port";
            case 6 -> "Best scores and stars per song, part and difficulty";
            case 7 -> "Frets, strums, bongos, Star Power and the rock meter";
            case 8 -> "Remap controls, calibrate timing, reduce flashes";
            default -> "Leave Sitar Hero for the game's own title screen";
        };
    }
    /** Loading progress, 0-100: the full mix is the first half, your part the second. */
    int loadingProgress() {
        return preparation == null ? (preparingPart ? 100 : 0) : (preparingPart ? 50 : 0) + preparation.progressPercent() / 2;
    }
    boolean local() { return mode == Mode.LOCAL_COOP || mode == Mode.LOCAL_VERSUS; }
    private void home() {
        stop(); if (online != null) online.close(); online = null; songs = SongCatalog.available(games);
        screen = Screen.TITLE; selected = 0; autoplay = false; choosingSecond = false; notice = "";
        careerRoleReturn = false;
        // Each return to the title stages the band on the next installed venue.
        titleVisits++;
        if (!roster.isEmpty()) title = TitleStage.build(context.art(), games, performer, titleVisits, context.width(), context.height(), titleVenues, band);
    }
    /** Fades the house music with the host's fade to black, then leaves the scene. */
    private void leave(boolean toGameTitle) {
        house.leaving();
        if (toGameTitle) context.exitToGameTitle(); else context.exitToMasterTitle();
    }

    List<SongSpec> setlistSongs() {
        if (mode != Mode.CAREER || careerWorld == null) return songs;
        var setlist = new ArrayList<SongSpec>();
        for (String id : careerWorld.requiredSongIds()) setlist.add(songs.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow());
        for (String id : careerWorld.sideSongIds()) songs.stream().filter(candidate -> candidate.id().equals(id)).findFirst().ifPresent(setlist::add);
        return List.copyOf(setlist);
    }
    private void returnToSongs() {
        screen = online != null && !online.host() ? Screen.LOBBY : Screen.SONGS;
        selected = Math.max(0, setlistSongs().indexOf(song)); notice = "";
    }
    private void openWorldBoard(boolean next) {
        screen = Screen.WORLDS; notice = "";
        selected = Math.max(0, careerTour.worlds().indexOf(careerWorld));
        if (next) selected = firstUnclearedWorld();
    }
    private int firstUnclearedWorld() {
        for (int i = 0; i < careerTour.worlds().size(); i++) if (!career.complete(careerTour.worlds().get(i))) return i;
        return careerTour.worlds().size() - 1;
    }
    private void continueTour() {
        if (careerTour == null) { screen = Screen.TOURS; selected = 0; return; }
        openWorldBoard(true);
        if (career.complete(careerTour) && !career.seen(careerTour.id() + "-outro"))
            openStory(careerTour.id() + "-outro", Screen.WORLDS);
    }
    private void openStory(String id, Screen destination) {
        story = CareerStory.scene(id, performer, games); storyLine = 0; storyReturn = destination; screen = Screen.STORY;
        notice = "";
    }
    private void closeStory() {
        career.markSeen(story.id()); saveCareer(); screen = storyReturn;
        if (screen == Screen.SONGS) selected = 0;
        else selected = Math.max(0, careerTour.worlds().indexOf(careerWorld));
    }
    private void saveCareer() {
        if (!context.storage().write("career.txt", career.encode())) {
            saveFailed = true; notice = "Tour progress remains in memory; storage could not save";
        }
    }
    private void careerMenu() {
        if (screen == Screen.STORY) {
            mouseAccepted = context.mouse().leftPressed() && context.mouse().over(12, SitarScreens.STORY_TOP, context.width() - 24, SitarScreens.STORY_HEIGHT);
            if (back()) closeStory();
            else if (accept() && ++storyLine >= story.lines().size()) closeStory();
            return;
        }
        int count = screen == Screen.TOURS ? tours.size() : careerTour.worlds().size();
        menuMove(count);
        mouseRows(count, 18, 60, context.width() - 168, screen == Screen.TOURS ? 40 : 24,
                screen == Screen.TOURS ? 3 : 5);
        if (screen == Screen.WORLDS && (context.keyPressed(SceneKeys.R) || padPressed(PhysicalGamepad.BUTTON_Y)
                || context.mouse().leftPressed() && context.mouse().over(context.width() - 132, 171, 112, 12))) {
            CareerWorld replay = careerTour.worlds().get(selected);
            if (!career.unlocked(careerTour, replay)) { notice = "Clear each main act in the previous world"; house.cue(Cue.DENIED); return; }
            careerWorld = replay; stage(setlistSongs().getFirst()); openStory(replay.id() + "-intro", Screen.WORLDS); return;
        }
        if (screen == Screen.WORLDS && career.complete(careerTour) && (context.keyPressed(SceneKeys.F) || padPressed(PhysicalGamepad.BUTTON_X)
                || context.mouse().leftPressed() && context.mouse().over(context.width() - 132, 184, 112, 12))) {
            openStory(careerTour.id() + "-outro", Screen.WORLDS); return;
        }
        if (accept()) {
            if (screen == Screen.TOURS) {
                careerTour = tours.get(selected); careerWorld = careerTour.worlds().get(firstUnclearedWorld()); openWorldBoard(false);
                song = setlistSongs().getFirst(); stage(song);
                if (career.complete(careerTour) && !career.seen(careerTour.id() + "-outro")) openStory(careerTour.id() + "-outro", Screen.WORLDS);
            } else {
                CareerWorld choice = careerTour.worlds().get(selected);
                if (!career.unlocked(careerTour, choice)) { notice = "Clear each main act in the previous world"; house.cue(Cue.DENIED); return; }
                careerWorld = choice; song = setlistSongs().getFirst(); stage(song);
                if (!career.seen(choice.id() + "-intro")) openStory(choice.id() + "-intro", Screen.SONGS);
                else { returnToSongs(); selected = 0; }
            }
        } else if (back()) {
            if (screen == Screen.WORLDS) { screen = Screen.TOURS; selected = tours.indexOf(careerTour); }
            else { screen = Screen.DIFFICULTY; selected = difficulty.ordinal(); }
        }
    }

    private void onlineMenu() {
        if (screen == Screen.LOBBY) {
            mouseAccepted = context.mouse().leftPressed() && context.mouse().over(18, 149, context.width() - 36, 32);
            if (back()) { home(); return; }
            if (online == null || !online.connected() || !accept()) return;
            if (online.host() && player == null) { screen = Screen.CHARACTERS; selected = roster.indexOf(performer); }
            else if (!online.host() && online.choice() != null && online.round() > preparedRound) {
                OnlineMatch.Choice choice = online.choice();
                song = songs.stream().filter(s -> s.id().equals(choice.song())).findFirst().orElseThrow();
                role = choice.role(); difficulty = choice.difficulty(); mode = choice.coop() ? Mode.ONLINE_COOP : Mode.ONLINE_VERSUS;
                preparedRound = online.round(); load(false);
            } else if (!online.host() && online.round() == 0) { screen = Screen.CHARACTERS; selected = roster.indexOf(performer); }
            return;
        }
        if (editingAddress) {
            if (back()) { editingAddress = false; return; }
            if (context.keyPressed(SceneKeys.BACKSPACE) && !onlineAddress.isEmpty()) onlineAddress = onlineAddress.substring(0, onlineAddress.length() - 1);
            mouseAccepted = context.mouse().leftPressed() && context.mouse().over(18, 134, context.width() - 36, 22);
            if (accept()) { editingAddress = false; context.storage().write("online-address.txt", onlineAddress); return; }
            for (int code = SceneKeys.A; code <= SceneKeys.Z; code++) if (context.keyPressed(code) && onlineAddress.length() < 128) onlineAddress += Character.toLowerCase((char) code);
            for (int code = SceneKeys.DIGIT_0; code <= SceneKeys.DIGIT_9; code++) if (context.keyPressed(code) && onlineAddress.length() < 128) onlineAddress += (char) code;
            if (context.keyPressed(SceneKeys.PERIOD) && onlineAddress.length() < 128) onlineAddress += ".";
            if (context.keyPressed(SceneKeys.SEMICOLON) && onlineAddress.length() < 128) onlineAddress += ":";
            return;
        }
        menuMove(5); mouseRows(5, 18, 62, context.width() - 36, 24);
        if (back()) { home(); return; }
        if (!accept()) return;
        if (selected == 3) { editingAddress = true; house.cue(Cue.CONFIRM); return; }
        if (selected == 4) { home(); return; }
        try {
            int separator = onlineAddress.lastIndexOf(':');
            String address = separator < 0 ? onlineAddress : onlineAddress.substring(0, separator);
            int port = separator < 0 ? 34917 : Integer.parseInt(onlineAddress.substring(separator + 1));
            boolean host = selected != 2;
            mode = selected == 1 ? Mode.ONLINE_VERSUS : Mode.ONLINE_COOP;
            online = new OnlineMatch(host ? context.network().host(port) : context.network().connect(address, port), host, games);
            preparedRound = 0; screen = Screen.LOBBY; selected = 0;
        } catch (RuntimeException failure) { notice = "Connection unavailable: " + failure.getMessage(); house.cue(Cue.DENIED); }
    }
    private void titleMenu() {
        int count = titleItems().size();
        menuMove(count); mouseRows(count, TITLE_X, TITLE_TOP, TITLE_WIDTH, TITLE_ROW, count);
        if (back()) { leave(false); return; }
        if (!accept()) return;
        if (selected < 5) {
            mode = Mode.values()[selected]; screen = Screen.CHARACTERS; selected = roster.indexOf(performer); choosingSecond = false;
            careerRoleReturn = false;
        } else if (selected == 5) { screen = Screen.ONLINE; selected = 0; }
        else if (selected == 6) { screen = Screen.RECORDS; selected = 0; }
        else if (selected == 7) { screen = Screen.HELP; selected = 0; }
        else if (selected == 8) { settingsReturn = Screen.TITLE; screen = Screen.SETTINGS; selected = 0; }
        else leave(true);
    }

    /** Visible list coordinates are also used for mouse navigation. */
    private void mouseChoices(int count, int visible) {
        SceneMouse mouse = context.mouse();
        int rowHeight = 126 / visible;
        if (mouse.wheel() != 0) { selected = Math.floorMod(selected - mouse.wheel(), count); return; }
        int first = listFirst(count, visible);
        for (int row = 0; row < Math.min(visible, count); row++)
            if (mouse.over(18, 60 + row * rowHeight, context.width() - 168, rowHeight) && (mouse.moved() || mouse.leftPressed()))
                selected = first + row;
        mouseAccepted = mouse.leftPressed() && mouse.over(18, 60, context.width() - 168, Math.min(visible, count) * rowHeight);
    }
    private void mouseRows(int count, int x, int y, int width, int height) {
        SceneMouse mouse = context.mouse();
        for (int row = 0; row < count; row++) {
            if (mouse.over(x, y + row * height, width, height - 1) && (mouse.moved() || mouse.leftPressed())) {
                selected = row; mouseAccepted = mouse.leftPressed();
            }
        }
    }
    private void mouseRows(int count, int x, int y, int width, int height, int visible) {
        SceneMouse mouse = context.mouse();
        if (mouse.wheel() != 0) { selected = Math.floorMod(selected - mouse.wheel(), count); return; }
        int first = listFirst(count, visible);
        for (int row = 0; row < Math.min(visible, count); row++)
            if (mouse.over(x, y + row * height, width, height - 1) && (mouse.moved() || mouse.leftPressed())) {
                selected = first + row; mouseAccepted = mouse.leftPressed();
            }
    }
    int listFirst(int count, int visible) { return Math.max(0, Math.min(count - visible, selected - visible / 2)); }

    private void performance() {
        PhysicalInput input = context.physicalInput();
        long now = player.samplePosition();
        if (player.finished() && eofNanos == Long.MIN_VALUE) eofNanos = input.timestampNanos();
        if (eofNanos != Long.MIN_VALUE) now = postrollPosition(input.timestampNanos());
        if (input.droppedEvents() != 0 || player.paused() || player.underrunCount() != underruns) {
            notice = input.droppedEvents() != 0 ? "Input interrupted" : "Audio interrupted";
            pause(); underruns = player.underrunCount(); return;
        }
        if (autoplay) {
            while (session.nextNote() < session.chart().notes().size()) {
                ChartNote note = session.chart().notes().get(session.nextNote());
                if (note.onset() > now) break;
                performanceInput(session, note.onset(), note.lanes(), role.drums() ? note.lanes() : 0,
                        !role.drums(), session.starCharge() >= .5, false);
            }
            if (context.keyPressed(SceneKeys.ESCAPE)) { pause(); return; }
        } else {
            for (MappedControls.Frame frame : controls.accept(input)) {
                if (frame.pause()) { pause(); return; }
                long at = (eofNanos != Long.MIN_VALUE && frame.timestamp() >= eofNanos
                        ? postrollPosition(frame.timestamp()) : player.samplePositionAt(frame.timestamp()))
                        - (long) settings.inputOffsetMs() * prepared.sampleRate() / 1000;
                performanceInput(session, at, frame.frets(), frame.directPressed(), frame.strum(), frame.power(), frame.whammy() > .2f);
                player.setWhammy(role.drums() ? 0 : frame.whammy());
            }
        }
        if (session2 != null) {
            if (autoplay) {
                while (session2.nextNote() < session2.chart().notes().size()) {
                    ChartNote note = session2.chart().notes().get(session2.nextNote()); if (note.onset() > now) break;
                    performanceInput(session2, note.onset(), note.lanes(), role.drums() ? note.lanes() : 0, !role.drums(), session2.starCharge() >= .5, false);
                }
            } else for (MappedControls.Frame frame : controls2.accept(input)) {
                if (frame.pause()) { pause(); return; }
                long at = (eofNanos != Long.MIN_VALUE && frame.timestamp() >= eofNanos ? postrollPosition(frame.timestamp()) : player.samplePositionAt(frame.timestamp()))
                        - (long) settings2.inputOffsetMs() * prepared.sampleRate() / 1000;
                performanceInput(session2, at, frame.frets(), frame.directPressed(), frame.strum(), frame.power(), frame.whammy() > .2f);
            }
            performanceAdvance(session2, now - (long) settings2.inputOffsetMs() * prepared.sampleRate() / 1000);
            highway2.observe(session2, context.ticks());
        }
        performanceAdvance(session, now - (long) settings.inputOffsetMs() * prepared.sampleRate() / 1000);
        highway.observe(session, context.ticks());
        performanceAudio.update(session,session2,online,now);
        boolean ended = session2 == null ? session.failed() : mode == Mode.LOCAL_COOP ? session.rock() + session2.rock() == 0 : session.failed() && session2.failed();
        if (online != null) {
            long timestamp = input.timestampNanos();
            if (timestamp - sentProgress >= 250_000_000L) { online.progress(session, now, false); sentProgress = timestamp; }
            if (mode == Mode.ONLINE_COOP) ended = session.rock() + online.remoteRock() == 0;
        }
        if (ended || player.finished() && session.finished() && (session2 == null || session2.finished())) finish();
    }

    PerformerArt performanceArtist(RhythmSession active) {
        if (active != session2 || session2 == null) return actor(performer);
        return actors.computeIfAbsent("p2-" + performer2.id(),
                ignored -> new PerformerArt(context.art().rom(performer2.game(games)), performer2.id()));
    }
    private void performanceInput(RhythmSession active, long at, int frets, int direct,
                                  boolean strum, boolean power, boolean whammy) {
        int first = active.nextNote();
        active.input(at, frets, direct, strum, power, whammy);
        animateJudgments(active, first);
    }
    private void performanceAdvance(RhythmSession active, long at) {
        int first = active.nextNote(); active.advance(at); animateJudgments(active, first);
    }
    private void animateJudgments(RhythmSession active, int first) {
        for (int index = first; index < active.nextNote(); index++) {
            // RhythmSession's status1 is a successful note-on; status2 is a miss.
            if (active.status(index) == 1)
                performanceArtist(active).notePlayed(role, active.chart().notes().get(index).lanes(), context.ticks());
        }
    }

    // A bounded silent judgment tail preserves calibrated late strikes. The ROM audio
    // remains finite; only this final input window uses the same monotonic input clock.
    private long postrollPosition(long timestamp) {
        return prepared.lengthSamples() + Math.max(0, timestamp - eofNanos) * prepared.sampleRate() / 1_000_000_000L;
    }

    private void pause() {
        if (online != null) {
            if (performanceAudio != null) performanceAudio.update(session,session2,online,player.samplePosition());
            online.requestPause(); notice = "Pause requested for both players"; return;
        }
        pauseLocal();
    }
    private void pauseLocal() {
        pauseNanos = context.physicalInput().timestampNanos();
        // Apply earlier judgments from this input batch before audio can resume.
        if (performanceAudio != null) performanceAudio.update(session,session2,online,player.samplePosition());
        if(online!=null)online.discardCues();
        player.pause(); screen = Screen.PAUSED; selected = 0;
    }
    private void resume() {
        if (online != null) { online.requestResume(); notice = "Pause released; waiting for peer if still paused"; return; }
        resumeLocal();
    }
    private void resumeLocal() {
        player.resume();
        if (player.paused()) { notice = "Speaker unavailable; restore audio and retry"; return; }
        if (eofNanos != Long.MIN_VALUE) eofNanos += Math.max(0, context.physicalInput().timestampNanos() - pauseNanos);
        controls.reset(context.physicalInput(), role.drums());
        controls2.reset(context.physicalInput(), role.drums());
        underruns = player.underrunCount(); screen = Screen.PLAY; notice = "";
    }
    private void finish() {
        if (online != null) online.progress(session, session.position(), true);
        stop();
        boolean teamLost = mode == Mode.LOCAL_COOP && session.rock() + session2.rock() == 0
                || mode == Mode.ONLINE_COOP && session.rock() + online.remoteRock() == 0;
        result = snapshotResult(session, teamLost);
        result2 = session2 == null ? null : snapshotResult(session2, teamLost);
        newRecord = false;
        if (!autoplay && !session.failed() && (mode == Mode.CAREER || mode == Mode.QUICK_PLAY)) {
            newRecord = session.score() > best;
            profile.record(result);
            saved = context.storage().write("profile.txt", profile.encode()); saveFailed = !saved;
            best = Math.max(best, session.score());
            if (difficulty == Difficulty.MEDIUM) saveFailed |= !context.storage().write(recordName(), Long.toString(best));
        }
        if (career.record(result, mode == Mode.CAREER && !autoplay && session.finished())) saveCareer();
        screen = Screen.RESULTS; selected = 0;
        house.results(result.cleared() || result2 != null && result2.cleared());
    }
    private PerformanceResult snapshotResult(RhythmSession who, boolean teamLost) {
        if (!teamLost) return PerformanceResult.from(song.id(), role.name(), difficulty.name(), who);
        return new PerformanceResult(song.id(), role.name(), difficulty.name(), who.score(), who.hits(),
                who.chart().notes().size(), who.bestStreak(), true, false);
    }
    private String recordName() { return song.id() + "." + role.name().toLowerCase(java.util.Locale.ROOT) + ".txt"; }
    private static long parseLong(String text) { try { return Math.max(0, Long.parseLong(text.trim())); } catch (NumberFormatException ignored) { return 0; } }
    private void stop() {
        if (preparation != null) { preparation.cancel(); preparation = null; }
        if (player != null) { player.stop(); player = null; }
        performanceAudio = null;
    }
    private void retry() {
        if (online != null) {
            if (!online.host()) { screen = Screen.LOBBY; notice = "Wait for the host to offer a rematch"; house.cue(Cue.DENIED); return; }
            online.offer(song, role, difficulty, mode == Mode.ONLINE_COOP); preparedRound = online.round();
        }
        load(false);
    }

    private void calibrate() {
        for (PhysicalInputEvent event : context.physicalInput().events()) {
            if (!event.pressed() || !calibrationTap(event)) continue;
            long at = player.samplePositionAt(event.timestampNanos());
            SceneNoteEvent nearest = null;
            for (SceneNoteEvent note : prepared.notes()) {
                if (note.kind() != SceneNoteEvent.Kind.DAC) continue;
                if (nearest == null || Math.abs(note.onsetSamples() - at) < Math.abs(nearest.onsetSamples() - at)) nearest = note;
            }
            if (nearest != null && nearest.onsetSamples() != lastCalibrationNote
                    && Math.abs(at - nearest.onsetSamples()) < prepared.sampleRate() / 4) {
                calibration.add(at - nearest.onsetSamples()); lastCalibrationNote = nearest.onsetSamples();
                if (calibration.size() > 24) calibration.remove(0);
            }
        }
        if (context.keyPressed(SceneKeys.ENTER) && calibration.size() >= 8) {
            List<Long> sorted = calibration.stream().sorted().toList();
            long median = sorted.get(sorted.size() / 2);
            activeSettings().inputOffsetMs((int) (median * 1000 / prepared.sampleRate()));
            saveSettings();
            stop(); screen = Screen.SETTINGS; selected = 10; notice = "Calibration saved";
        } else if (back() || player.finished()) { stop(); screen = Screen.SETTINGS; selected = 13; }
    }

    private boolean calibrationTap(PhysicalInputEvent event) {
        ControlSettings setting = activeSettings();
        var keyboard = setting.binding(true, false, 4);
        var pad = setting.binding(true, true, 4);
        return event.kind() == PhysicalInputEvent.Kind.KEY && event.code() == keyboard.code()
                || event.kind() == PhysicalInputEvent.Kind.BUTTON && event.deviceId() == pad.device()
                && event.code() == PhysicalGamepad.BUTTON_A;
    }

    private void settings() {
        ControlSettings settings = activeSettings();
        if (capturingBinding) {
            if (context.keyPressed(SceneKeys.ESCAPE)) { capturingBinding = false; house.cue(Cue.BACK); return; }
            for (PhysicalInputEvent event : context.physicalInput().events()) {
                PhysicalBinding binding = null;
                if (!settingsPad && event.kind() == PhysicalInputEvent.Kind.KEY && event.pressed())
                    binding = new PhysicalBinding('K', -1, event.code(), 1);
                if (settingsPad && event.kind() == PhysicalInputEvent.Kind.BUTTON && event.pressed())
                    binding = new PhysicalBinding('B', event.deviceId(), event.code(), 1);
                if (settingsPad && event.kind() == PhysicalInputEvent.Kind.AXIS && Math.abs(event.value()) > .7f
                        && !(event.code() >= 4 && event.value() < 0))
                    binding = new PhysicalBinding('A', event.deviceId(), event.code(), event.value() < 0 ? -1 : 1);
                if (binding != null) {
                    settings.bind(settingsDrums, settingsPad, selected, binding); capturingBinding = false;
                    saveSettings(); house.cue(Cue.CONFIRM); return;
                }
            }
            return;
        }
        int previous = selected;
        menuMove(19);
        if (settingsDrums && selected >= 5 && selected <= 7) selected = selected < previous ? 4 : 8;
        if (context.keyPressed(SceneKeys.TAB)) { settingsPad = !settingsPad; house.cue(Cue.MOVE); }
        var rows = settingsRows();
        int first = Math.max(0, Math.min(rows.size() - 8, rows.indexOf(selected) - 3));
        SceneMouse mouse = context.mouse();
        if (mouse.wheel() != 0) { selected = rows.get(Math.floorMod(rows.indexOf(selected) - mouse.wheel(), rows.size())); return; }
        for (int row = first; row < Math.min(rows.size(), first + 8); row++) {
            if (mouse.over(18, 60 + (row - first) * 16, context.width() - 36, 15) && (mouse.moved() || mouse.leftPressed())) {
                selected = rows.get(row); mouseAccepted = mouse.leftPressed();
            }
        }
        int direction = left() ? -1 : right() ? 1 : 0;
        if (selected == 10 && direction != 0) settings.inputOffsetMs(settings.inputOffsetMs() + direction * 5);
        if (selected == 11 && direction != 0) settings.displayOffsetMs(settings.displayOffsetMs() + direction * 5);
        if (selected == 12 && direction != 0) settings.lefty(!settings.lefty());
        if (selected == 15 && direction != 0) settingsSecond = !settingsSecond;
        if (selected == 16 && direction != 0) settings.reducedFlashes(!settings.reducedFlashes());
        if (selected == 17 && direction != 0) settings.scrollSpeed(settings.scrollSpeed() + direction * 10);
        if (direction != 0 && (selected >= 10 && selected <= 12 || selected >= 15 && selected <= 17)) house.cue(Cue.MOVE);
        if (accept()) {
            house.cue(Cue.CONFIRM);
            if (selected < 10) capturingBinding = true;
            else if (selected == 12) settings.lefty(!settings.lefty());
            else if (selected == 13) { load(true); return; }
            else if (selected == 14) { settingsDrums = !settingsDrums; }
            else if (selected == 15) settingsSecond = !settingsSecond;
            else if (selected == 16) settings.reducedFlashes(!settings.reducedFlashes());
            else if (selected == 18) settings.defaults();
        }
        if (context.keyPressed(SceneKeys.DELETE) && selected < 10) {
            settings.bind(settingsDrums, settingsPad, selected, new PhysicalBinding(settingsPad ? 'B' : 'K', 0, -1, 1));
            house.cue(Cue.BACK);
        }
        if (back()) {
            saveSettings();
            screen = settingsReturn; selected = 0;
        }
    }
    List<Integer> settingsRows() {
        var rows = new ArrayList<Integer>();
        for (int i = 0; i < 19; i++) if (!settingsDrums || i < 5 || i > 7) rows.add(i);
        return rows;
    }
    ControlSettings activeSettings() { return settingsSecond ? settings2 : settings; }
    private void saveSettings() {
        if (!context.storage().write(settingsSecond ? "settings-p2.txt" : "settings.txt", activeSettings().encode()))
            notice = "Settings could not be saved; check storage";
    }

    private boolean padPressed(int button) {
        return context.physicalInput().events().stream().anyMatch(event -> event.kind() == PhysicalInputEvent.Kind.BUTTON
                && event.code() == button && event.pressed());
    }
    private boolean accept() { return mouseAccepted || context.keyPressed(SceneKeys.ENTER) || padPressed(PhysicalGamepad.BUTTON_A)
            || padPressed(PhysicalGamepad.BUTTON_START); }
    private boolean back() { return context.mouse().rightPressed() || context.keyPressed(SceneKeys.ESCAPE) || padPressed(PhysicalGamepad.BUTTON_B); }
    private boolean navigationPulse(int slot, int key, int padButton) {
        boolean edge = context.keyPressed(key) || padPressed(padButton);
        boolean held = context.keyDown(key) || context.physicalInput().gamepads().stream().anyMatch(pad -> pad.buttonDown(padButton));
        if (!held && !edge) { navigationRepeat[slot] = -1; return false; }
        if (edge || navigationRepeat[slot] < 0 || context.ticks() >= navigationRepeat[slot]) {
            navigationRepeat[slot] = context.ticks() + (edge || navigationRepeat[slot] < 0 ? 18 : 6);
            return true;
        }
        return false;
    }
    private boolean left() { return navigationPulse(2, SceneKeys.LEFT, PhysicalGamepad.BUTTON_DPAD_LEFT); }
    private boolean right() { return navigationPulse(3, SceneKeys.RIGHT, PhysicalGamepad.BUTTON_DPAD_RIGHT); }
    private void menuMove(int count) {
        if (count <= 0) return;
        boolean up = navigationPulse(0, SceneKeys.UP, PhysicalGamepad.BUTTON_DPAD_UP);
        boolean down = navigationPulse(1, SceneKeys.DOWN, PhysicalGamepad.BUTTON_DPAD_DOWN);
        if (up != down) selected = Math.floorMod(selected + (up ? -1 : 1), count);
    }

    @Override public void draw(SceneContext ctx, SceneCanvas c) { screens.draw(c, ctx.ticks()); }

    String shortRole() { return role.instrument(); }
    String modeLabel() {
        return switch (mode) { case CAREER -> "CAREER"; case QUICK_PLAY -> "QUICK PLAY";
            case PRACTICE -> "PRACTICE"; case LOCAL_COOP, ONLINE_COOP -> "CO-OP"; default -> "SCORE DUEL"; };
    }
    boolean reducedFlashes() { return settings.reducedFlashes(); }

    @Override public void exit(SceneContext ctx) {
        stop(); if (online != null) online.close();
        if (house != null) house.dispose();
        ctx.storage().write("settings.txt", settings.encode()); ctx.storage().write("settings-p2.txt", settings2.encode()); ctx.storage().write("career.txt", career.encode());
    }
    public String screen() { return screen.name(); }
    public String mode() { return mode.name(); }
    public long score() { return session == null ? 0 : session.score(); }
    public int hits() { return session == null ? 0 : session.hits(); }
    public int chartNotes() { return session == null ? 0 : session.chart().notes().size(); }
    public String performerId() { return performer.id(); }
    public List<String> availableSongs() { return songs.stream().map(SongSpec::id).toList(); }
    public List<String> availablePerformers() { return roster.stream().map(Roster::id).toList(); }
    public String error() { return error; }

    @Override public boolean debugJump(String command) {
        if (context == null) return false;
        if (command.startsWith("perform:") || command.startsWith("practice:") || command.startsWith("local:")) {
            String[] fields = command.split(":"); if (fields.length < 4 || fields.length > 6) return false;
            SongSpec source = songs.stream().filter(s -> s.id().equals(fields[1])).findFirst().orElse(null);
            Roster who = roster.stream().filter(p -> p.id().equals(fields[3])).findFirst().orElse(null);
            Role chosen;
            try { chosen = Role.valueOf(fields[2].toUpperCase(java.util.Locale.ROOT)); } catch (IllegalArgumentException ignored) { return false; }
            if (source == null || who == null) return false;
            Difficulty level = Difficulty.MEDIUM;
            try { if (fields.length >= 5) level = Difficulty.valueOf(fields[4].toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return false; }
            if (fields[0].equals("local") && (fields.length != 6 || !List.of("coop", "versus").contains(fields[5]))) return false;
            mode = fields[0].equals("practice") ? Mode.PRACTICE : fields[0].equals("local")
                    ? fields[5].equals("coop") ? Mode.LOCAL_COOP : Mode.LOCAL_VERSUS : Mode.QUICK_PLAY;
            song = source; role = chosen; performer = who; difficulty = level; autoplay = false; load(false); return true;
        }
        return switch (command) {
            case "autoplay" -> { autoplay = true; yield true; }
            case "pause" -> { if (player == null) yield false; pause(); yield true; }
            case "resume" -> { if (screen != Screen.PAUSED) yield false; resume(); yield true; }
            case "retry" -> { retry(); yield true; }
            case "settings" -> { stop(); settingsReturn = Screen.CHARACTERS; screen = Screen.SETTINGS; selected = 0; yield true; }
            case "menu", "title" -> { home(); yield true; }
            case "career" -> { stop(); mode = Mode.CAREER; careerRoleReturn = false; screen = Screen.CHARACTERS; selected = 0; autoplay = false; yield true; }
            case "quick" -> { stop(); mode = Mode.QUICK_PLAY; screen = Screen.CHARACTERS; selected = 0; autoplay = false; yield true; }
            case "records" -> { stop(); screen = Screen.RECORDS; selected = 0; yield true; }
            case "help" -> { stop(); screen = Screen.HELP; selected = 0; yield true; }
            default -> false;
        };
    }
}
