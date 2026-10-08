package sitarhero;

import com.openggf.control.PhysicalGamepad;
import com.openggf.control.PhysicalInput;
import com.openggf.control.PhysicalInputEvent;
import com.openggf.mods.scene.*;
import sitarhero.chart.ChartCurator;
import sitarhero.controls.*;
import sitarhero.model.*;
import sitarhero.ui.SitarUi;
import sitarhero.ui.HighwayView;
import sitarhero.net.OnlineMatch;
import sitarhero.story.CareerStory;
import static sitarhero.ui.SitarUi.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Character → role → world journey or open setlist → finite performance → results.
 * Debug capture commands: perform:SONG:ROLE:PERFORMER, autoplay, pause, resume,
 * retry, settings, menu. Autoplay is a labelled tool-only performance, never a menu mode.
 */
public final class SitarScene implements ModScene, DebuggableScene {
    public enum Screen { TITLE, CHARACTERS, ROLES, DIFFICULTY, TOURS, WORLDS, STORY, SONGS, LOADING, PLAY, PAUSED, RESULTS, SETTINGS, CALIBRATION, RECORDS, HELP, ONLINE, LOBBY, ERROR }
    public enum Mode { CAREER, QUICK_PLAY, PRACTICE, LOCAL_COOP, LOCAL_VERSUS, ONLINE_COOP, ONLINE_VERSUS }
    private SceneContext context;
    private List<String> games = List.of();
    private List<Roster> roster = List.of();
    private List<SongSpec> songs = List.of();
    private final Map<String, PerformerArt> actors = new HashMap<>();
    private final ControlSettings settings = new ControlSettings();
    private final MappedControls controls = new MappedControls(settings);
    private final ControlSettings settings2 = new ControlSettings(1);
    private final MappedControls controls2 = new MappedControls(settings2);
    private final PlayerProfile profile = new PlayerProfile();
    private final CareerJournal career = new CareerJournal();
    private List<CareerTour> tours = List.of();
    private CareerTour careerTour;
    private CareerWorld careerWorld;
    private CareerStory.Scene story;
    private int storyLine;
    private Screen storyReturn = Screen.SONGS;
    private boolean careerRoleReturn;
    private final HighwayView highway = new HighwayView(), highway2 = new HighwayView();
    private Screen screen = Screen.TITLE;
    private Screen settingsReturn = Screen.TITLE;
    private Mode mode = Mode.CAREER;
    private Difficulty difficulty = Difficulty.MEDIUM;
    private Roster performer2 = Roster.ROBOTNIK;
    private boolean choosingSecond;
    private boolean settingsSecond;
    private RhythmSession session2;
    private PerformanceResult result, result2;
    private boolean saved;
    private boolean saveFailed;
    private OnlineMatch online;
    private String onlineAddress = "127.0.0.1:34917";
    private boolean editingAddress;
    private int preparedRound;
    private long sentProgress;
    private int selected;
    private Roster performer = Roster.SONIC;
    private Role role = Role.SITAR;
    private SongSpec song;
    private ScenePreparedMusic prepared;
    private SceneMusicPreparation preparation;
    private boolean preparingPart;
    private List<SceneMusicPart> selectedParts = List.of();
    private Chart preparedChart;
    private SceneMusicPlayer player;
    private RhythmSession session;
    private SceneBackdrop backdrop;
    private SceneImage foreground;
    private long loadingTick;
    private long underruns;
    private long best;
    private boolean autoplay;
    private boolean settingsDrums;
    private boolean settingsPad;
    private boolean capturingBinding;
    private boolean calibratePending;
    private boolean mouseAccepted;
    private final long[] navigationRepeat = {-1, -1, -1, -1};
    private String notice = "";
    private String error = "";
    private final List<Long> calibration = new ArrayList<>();
    private long lastCalibrationNote = -1;
    private long eofNanos = Long.MIN_VALUE;
    private long pauseNanos;

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
        if (roster.isEmpty() || songs.isEmpty()) { error = "Supply a supported Sonic ROM"; screen = Screen.ERROR; return; }
        performer = roster.get(0); song = songs.get(0);
        actor(performer);
        stage(song);
        ctx.audio().stopMusic();
    }

    private PerformerArt actor(Roster who) {
        return actors.computeIfAbsent(who.id(), ignored -> new PerformerArt(context.art().rom(who.game(games)), who.id()));
    }
    private void stage(SongSpec source) {
        SceneRomArt rom = context.art().rom(source.game());
        SongSpec scenery = rom.hasZonePictures(source.zone(), source.act()) ? source
                : SongCatalog.available(List.of(source.game())).stream()
                .filter(candidate -> rom.hasZonePictures(candidate.zone(), candidate.act()))
                .findFirst().orElse(source);
        backdrop = rom.zoneBackdrop(scenery.zone(), scenery.act());
        foreground = null;
        List<SceneLevelStage> stages = rom.levelStages(scenery.zone(), scenery.act(), context.width(), 90, 24);
        if (!stages.isEmpty()) {
            SceneLevelStage stage = stages.get(0);
            foreground = rom.levelForeground(scenery.zone(), scenery.act(), stage.x(), stage.floorY() - 180,
                    context.width(), context.height());
        }
    }
    private void load(boolean calibrationMode) {
        stop(); calibratePending = calibrationMode;
        screen = Screen.LOADING; loadingTick = context.ticks(); selected = 0; notice = "";
        preparingPart = false; selectedParts = List.of(); preparedChart = null;
    }
    private void prepare() {
        try {
            SongSpec source = calibratePending ? songs.stream()
                    .filter(candidate -> candidate.game().equals(song.game()) && candidate.availableRoles().contains(Role.BONGOS))
                    .findFirst().orElseGet(() -> songs.stream().filter(candidate -> candidate.availableRoles().contains(Role.BONGOS)).findFirst().orElseThrow()) : song;
            Role preparedRole = calibratePending ? Role.BONGOS : role;
            if (preparation == null) {
                stage(source); actor(performer);
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
            player = context.music().start(prepared, selectedParts, prepared.sampleRate() * 3);
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
                if (context.keyPressed(SceneKeys.TAB)) role = Role.values()[(role.ordinal() + 1) % Role.values().length];
                int direction = left() ? -1 : right() ? 1 : 0;
                if (direction != 0) difficulty = Difficulty.values()[Math.floorMod(difficulty.ordinal() + direction, Difficulty.values().length)];
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
                else if (online != null && !online.host()) { notice = "The host chooses the next song"; }
                else { stop(); returnToSongs(); }
            } else if (context.keyPressed(SceneKeys.ESCAPE)) resume();
            return;
        }
        if (screen == Screen.ERROR) {
            mouseAccepted = context.mouse().leftPressed() && context.mouse().over(12, 55, context.width() - 24, 136);
            if (accept() || back()) {
                if (roster.isEmpty() || songs.isEmpty()) ctx.exitToMasterTitle();
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
                if (careerRoleReturn && !song.availableRoles().contains(choice)) { notice = "This ROM song has no " + choice.label() + " part"; return; }
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

    private List<String> titleItems() {
        return List.of("Career tour", "Quick play", "Practice", "Local co-op", "Local score duel", "Direct-connect", "Records", "How to play", "Settings", "Play stock Sonic");
    }
    private boolean local() { return mode == Mode.LOCAL_COOP || mode == Mode.LOCAL_VERSUS; }
    private void home() {
        stop(); if (online != null) online.close(); online = null; songs = SongCatalog.available(games);
        screen = Screen.TITLE; selected = 0; autoplay = false; choosingSecond = false; notice = "";
        careerRoleReturn = false;
    }

    private List<SongSpec> setlistSongs() {
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
            mouseAccepted = context.mouse().leftPressed() && context.mouse().over(12, 125, context.width() - 24, 66);
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
            if (!career.unlocked(careerTour, replay)) { notice = "Clear each main act in the previous world"; return; }
            careerWorld = replay; openStory(replay.id() + "-intro", Screen.WORLDS); return;
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
                if (!career.unlocked(careerTour, choice)) { notice = "Clear each main act in the previous world"; return; }
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
        if (selected == 3) { editingAddress = true; return; }
        if (selected == 4) { home(); return; }
        try {
            int separator = onlineAddress.lastIndexOf(':');
            String address = separator < 0 ? onlineAddress : onlineAddress.substring(0, separator);
            int port = separator < 0 ? 34917 : Integer.parseInt(onlineAddress.substring(separator + 1));
            boolean host = selected != 2;
            mode = selected == 1 ? Mode.ONLINE_VERSUS : Mode.ONLINE_COOP;
            online = new OnlineMatch(host ? context.network().host(port) : context.network().connect(address, port), host, games);
            preparedRound = 0; screen = Screen.LOBBY; selected = 0;
        } catch (RuntimeException failure) { notice = "Connection unavailable: " + failure.getMessage(); }
    }
    private void drawOnline(SceneCanvas c) {
        SitarUi.text(c, "DIRECT-CONNECT", 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, c.width() - 24, 136);
        if (screen == Screen.LOBBY) {
            SitarUi.wrapped(c, online == null ? "No connection" : online.status(), 24, 69, c.width() - 48, CYAN);
            if (online != null && online.choice() != null) {
                OnlineMatch.Choice choice = online.choice();
                SitarUi.text(c, choice.song() + " / " + choice.difficulty().label(), 24, 104, GOLD);
                SitarUi.text(c, choice.role().label(), 24, 122, CREAM);
            }
            SitarUi.wrapped(c, online != null && online.host() ? "ENTER: choose song and performer after your peer connects" : "ENTER: choose performer, then confirm the host's offered song to ready up", 24, 152, c.width() - 48, CREAM);
            SitarUi.footer(c, "Each player needs their own matching ROM and mod build", "ENTER / A: READY / ESC / B: DISCONNECT");
        } else {
            List<String> items = List.of("Host co-op", "Host score duel", "Join host", "Address / " + onlineAddress, "Title");
            for (int i = 0; i < items.size(); i++) SitarUi.choice(c, SitarUi.fit(items.get(i), c.width() - 75), 18, 62 + i * 24, c.width() - 36, 22, selected == i);
            SitarUi.footer(c, notice.isEmpty() ? "IP ADDRESS:PORT / LAN OR FORWARDED TCP PORT" : notice,
                    editingAddress ? "TYPE / BACKSPACE / ; FOR COLON / ENTER: SAVE" : "ENTER / A: SELECT / ESC / B: TITLE");
        }
    }
    private void titleMenu() {
        menuMove(titleItems().size()); mouseChoices(titleItems().size(), 7);
        if (back()) { context.exitToMasterTitle(); return; }
        if (!accept()) return;
        if (selected < 5) {
            mode = Mode.values()[selected]; screen = Screen.CHARACTERS; selected = roster.indexOf(performer); choosingSecond = false;
            careerRoleReturn = false;
        } else if (selected == 5) { screen = Screen.ONLINE; selected = 0; }
        else if (selected == 6) { screen = Screen.RECORDS; selected = 0; }
        else if (selected == 7) { screen = Screen.HELP; selected = 0; }
        else if (selected == 8) { settingsReturn = Screen.TITLE; screen = Screen.SETTINGS; selected = 0; }
        else context.exitToGameTitle();
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
    private int listFirst(int count, int visible) { return Math.max(0, Math.min(count - visible, selected - visible / 2)); }

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
        player.setPartAudible(session.partAudible() || session2 != null && session2.partAudible());
        boolean ended = session2 == null ? session.failed() : mode == Mode.LOCAL_COOP ? session.rock() + session2.rock() == 0 : session.failed() && session2.failed();
        if (online != null) {
            long timestamp = input.timestampNanos();
            if (timestamp - sentProgress >= 250_000_000L) { online.progress(session, now, false); sentProgress = timestamp; }
            if (mode == Mode.ONLINE_COOP) ended = session.rock() + online.remoteRock() == 0;
        }
        if (ended || player.finished() && session.finished() && (session2 == null || session2.finished())) finish();
    }

    private PerformerArt performanceArtist(RhythmSession active) {
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
        if (online != null) { online.requestPause(); notice = "Pause requested for both players"; return; }
        pauseLocal();
    }
    private void pauseLocal() {
        pauseNanos = context.physicalInput().timestampNanos();
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
        if (!autoplay && !session.failed() && (mode == Mode.CAREER || mode == Mode.QUICK_PLAY)) {
            profile.record(result);
            saved = context.storage().write("profile.txt", profile.encode()); saveFailed = !saved;
            best = Math.max(best, session.score());
            if (difficulty == Difficulty.MEDIUM) saveFailed |= !context.storage().write(recordName(), Long.toString(best));
        }
        if (career.record(result, mode == Mode.CAREER && !autoplay && session.finished())) saveCareer();
        screen = Screen.RESULTS; selected = 0;
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
    }
    private void retry() {
        if (online != null) {
            if (!online.host()) { screen = Screen.LOBBY; notice = "Wait for the host to offer a rematch"; return; }
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
            if (context.keyPressed(SceneKeys.ESCAPE)) { capturingBinding = false; return; }
            for (PhysicalInputEvent event : context.physicalInput().events()) {
                PhysicalBinding binding = PhysicalBinding.capture(event, settingsPad);
                if (binding != null) {
                    settings.bind(settingsDrums, settingsPad, selected, binding); capturingBinding = false;
                    saveSettings(); return;
                }
            }
            return;
        }
        int previous = selected;
        menuMove(19);
        if (settingsDrums && selected >= 5 && selected <= 7) selected = selected < previous ? 4 : 8;
        if (context.keyPressed(SceneKeys.TAB)) settingsPad = !settingsPad;
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
        if (accept()) {
            if (selected < 10) capturingBinding = true;
            else if (selected == 12) settings.lefty(!settings.lefty());
            else if (selected == 13) { load(true); return; }
            else if (selected == 14) { settingsDrums = !settingsDrums; }
            else if (selected == 15) settingsSecond = !settingsSecond;
            else if (selected == 16) settings.reducedFlashes(!settings.reducedFlashes());
            else if (selected == 18) settings.defaults();
        }
        if (context.keyPressed(SceneKeys.DELETE) && selected < 10)
            settings.bind(settingsDrums, settingsPad, selected, new PhysicalBinding(settingsPad ? 'B' : 'K', 0, -1, 1));
        if (back()) {
            saveSettings();
            screen = settingsReturn; selected = 0;
        }
    }
    private List<Integer> settingsRows() {
        var rows = new ArrayList<Integer>();
        for (int i = 0; i < 19; i++) if (!settingsDrums || i < 5 || i > 7) rows.add(i);
        return rows;
    }
    private ControlSettings activeSettings() { return settingsSecond ? settings2 : settings; }
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

    @Override public void draw(SceneContext ctx, SceneCanvas c) {
        c.clear(0x143C74);
        if (backdrop != null) c.drawBackdrop(backdrop, Math.max(0, Math.min(backdrop.image().height() - c.height(), 128)), 0, ctx.ticks());
        if (foreground != null) c.draw(foreground, 0, 0);
        if (screen == Screen.PLAY || screen == Screen.PAUSED) { drawPerformance(c); if (screen == Screen.PAUSED) drawPause(c); return; }
        if (screen == Screen.CALIBRATION) { drawCalibration(c); return; }
        int step = screen == Screen.CHARACTERS ? 1 : screen == Screen.ROLES ? 2 : screen == Screen.DIFFICULTY ? 3 : screen == Screen.SONGS ? 4 : 0;
        SitarUi.page(c, step, mode == Mode.CAREER ? "TOUR" : "ARCADE");
        if (screen == Screen.TITLE) { drawTitle(c); return; }
        if (screen == Screen.RECORDS) { drawRecords(c); return; }
        if (screen == Screen.HELP) { drawHelp(c); return; }
        if (screen == Screen.TOURS || screen == Screen.WORLDS) { drawCareerBoard(c); return; }
        if (screen == Screen.STORY) { drawStory(c); return; }
        if (screen == Screen.ONLINE || screen == Screen.LOBBY) { drawOnline(c); return; }
        if (screen == Screen.SETTINGS) { drawSettings(c); return; }
        if (screen == Screen.LOADING) {
            SitarUi.panel(c, 24, 52, c.width() - 48, 136);
            center(c, "GET READY", 63, GOLD);
            SitarUi.center(c, song.label().toUpperCase(java.util.Locale.ROOT), 24, 88, c.width() - 48, CREAM);
            SitarUi.center(c, performer.label() + " / " + shortRole(), 24, 103, c.width() - 48, CYAN);
            actor(performer).draw(c, c.width() / 2, 143, ctx.ticks(), shortRole(), false);
            int progress = preparation == null ? 0 : (preparingPart ? 50 : 0) + preparation.progressPercent() / 2;
            c.fill(48, 174, c.width() - 96, 5, 0xFF132038);
            c.fill(48, 174, (c.width() - 96) * progress / 100, 5, CYAN);
            SitarUi.footer(c, (preparingPart ? "Preparing your musical part " : "Preparing ROM music ") + progress + "%", "Back cancels / Three-second count-in when ready");
            return;
        }
        if (screen == Screen.ERROR) {
            SitarUi.panel(c, 12, 52, c.width() - 24, 136);
            SitarUi.label(c, "UNABLE TO START", 24, 62, c.width() - 48, GOLD);
            SitarUi.wrapped(c, error, 24, 86, c.width() - 48, CREAM);
            SitarUi.footer(c, "Your selection is preserved", "ENTER / A: RETURN"); return;
        }
        if (screen == Screen.RESULTS) { drawResults(c); return; }
        drawChoices(c);
    }

    private void drawTitle(SceneCanvas c) {
        SitarUi.text(c, "WORLD TOUR", 12, 42, GOLD);
        int width = c.width() - 156;
        SitarUi.panel(c, 12, 55, width, 136);
        drawList(c, titleItems(), 7);
        int x = c.width() - 140;
        SitarUi.panel(c, x, 55, 128, 136);
        SitarUi.center(c, "TAKE THE STAGE", x, 67, 128, CYAN);
        actor(performer).draw(c, x + 64, 127, context.ticks(), "Sitar", false);
        SitarUi.center(c, songs.size() + " SONGS / 4 LEVELS", x, 160, 128, GOLD);
        SitarUi.center(c, profile.clears() + " CLEARS", x, 178, 128, CREAM);
        SitarUi.footer(c, "ARROWS / DPAD / MOUSE: CHOOSE", "ENTER / A: SELECT / ESC / B: MASTER TITLE");
    }
    private void drawList(SceneCanvas c, List<String> labels, int visible) {
        int first = listFirst(labels.size(), visible), rowHeight = 126 / visible;
        int width = c.width() - 168;
        for (int row = 0; row < Math.min(visible, labels.size()); row++)
            SitarUi.choice(c, labels.get(first + row), 18, 60 + row * rowHeight, width, rowHeight - 1, selected == first + row);
        if (labels.size() > visible) SitarUi.text(c, (selected + 1) + "/" + labels.size(), c.width() - 188, 181, DIM);
    }
    private void drawRecords(SceneCanvas c) {
        SongSpec item = songs.get(Math.min(selected, songs.size() - 1));
        SitarUi.text(c, "RECORDS / " + shortRole() + " / " + difficulty.label(), 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, c.width() - 24, 136);
        SitarUi.text(c, item.label(), 24, 68, CREAM);
        PerformanceResult record = profile.best(item.id(), role.name(), difficulty.name()).orElse(null);
        if (record == null) SitarUi.text(c, "NO CLEAR YET / PLAY CAREER OR QUICK PLAY", 24, 96, DIM);
        else {
            SitarUi.text(c, "BEST SCORE " + record.score(), 24, 94, GOLD);
            SitarUi.text(c, profile.bestStars(item.id(), role.name(), difficulty.name()).orElse(record).stars() + " STARS / SCORE " + Math.round(record.accuracy() * 100) + "%", 24, 116, CYAN);
            SitarUi.text(c, "BEST STREAK " + record.bestStreak() + (record.fullCombo() ? " / FULL COMBO" : ""), 24, 138, CREAM);
        }
        SitarUi.text(c, "MAIN ACT CLEARS " + tours.stream().mapToInt(career::cleared).sum() + "/" + tours.stream().mapToInt(t -> t.worlds().stream().mapToInt(w -> w.requiredSongIds().size()).sum()).sum(), 24, 166, GOLD);
        SitarUi.footer(c, "UP / DOWN: SONG / LEFT / RIGHT: DIFFICULTY", "TAB: INSTRUMENT / ESC / B: TITLE");
    }
    private void drawHelp(SceneCanvas c) {
        SitarUi.text(c, "HOW TO PLAY", 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, c.width() - 24, 136);
        SitarUi.wrapped(c, role.drums() ? "BONGOS: press each coloured pad when it reaches the line. The gold bar is the kick pedal. New presses strike; holding does not." :
                "MELODIC: hold the matching frets and strum at the line. Hold long tails. White centres can be hammered on after a successful chain.", 24, 65, c.width() - 48, CREAM);
        SitarUi.wrapped(c, "Complete bright phrases to charge Star Power. Activate at half a meter for double score. Misses lower ROCK and mute your part.", 24, 107, c.width() - 48, GOLD);
        SitarUi.wrapped(c, "Career: clear each world's main acts. Tour progress follows any instrument or level. Practice never saves. Co-op shares survival; duels compare scores.", 24, 149, c.width() - 48, CYAN);
        SitarUi.footer(c, "REMAP CONTROLS AND CALIBRATE BOTH PLAYERS IN SETTINGS", "ESC / B: TITLE");
    }
    private void drawChoices(SceneCanvas c) {
        String heading = screen == Screen.CHARACTERS ? "CHOOSE PLAYER " + (choosingSecond ? "2" : "1") + " PERFORMER" : screen == Screen.ROLES ? "CHOOSE YOUR INSTRUMENT" : screen == Screen.DIFFICULTY ? "CHOOSE YOUR DIFFICULTY" : "CHOOSE YOUR SONG";
        SitarUi.text(c, heading, 12, 42, GOLD);
        int paneWidth = c.width() - 156;
        SitarUi.panel(c, 12, 55, paneWidth, 136);
        if (screen == Screen.CHARACTERS) drawList(c, roster.stream().map(Roster::label).toList(), 7);
        else if (screen == Screen.ROLES) drawList(c, java.util.Arrays.stream(Role.values()).map(Role::label).toList(), 4);
        else if (screen == Screen.DIFFICULTY) drawList(c, java.util.Arrays.stream(Difficulty.values()).map(Difficulty::label).toList(), 4);
        else {
            List<SongSpec> choices = setlistSongs();
            int first = listFirst(choices.size(), 4);
            for (int row = 0; row < Math.min(4, choices.size()); row++) {
                int i = first + row; SongSpec choice = choices.get(i); int y = 60 + row * 31;
                boolean supported = choice.availableRoles().contains(role);
                SitarUi.choice(c, !supported ? "CHANGE PART / " + choice.label() : choice.label(), 18, y, paneWidth - 12, 29, selected == i);
                String detail = choice.game().toUpperCase(java.util.Locale.ROOT) + " / " + choice.durationFrames() / 60 + " SEC";
                if (mode == Mode.CAREER) detail = (careerWorld.requiredSongIds().contains(choice.id()) ? "MAIN ACT" : "SIDE GIG")
                        + (career.cleared(choice.id()) ? " / CLEARED" : "") + " / " + choice.durationFrames() / 60 + " SEC";
                SitarUi.text(c, SitarUi.fit(detail, paneWidth - 36), 39, y + 21, DIM);
            }
            if (choices.size() > 4) SitarUi.text(c, (selected + 1) + "/" + choices.size(), 18, 184, CYAN);
        }
        Roster preview = screen == Screen.CHARACTERS ? roster.get(selected) : performer;
        Role previewRole = screen == Screen.ROLES ? Role.values()[selected] : role;
        int previewX = c.width() - 140;
        SitarUi.panel(c, previewX, 55, 128, 136);
        SitarUi.center(c, modeLabel(), previewX, 63, 128, CYAN);
        SitarUi.center(c, SitarUi.fit(preview.label(), 116), previewX, 78, 128, CREAM);
        c.fill(previewX + 16, 149, 96, 3, GOLD);
        actor(preview).draw(c, previewX + 64, 121, context.ticks(), previewRole.label().split(" / ")[0], false);
        SitarUi.center(c, previewRole.drums() ? "4 PADS + KICK" : difficulty.lanes() + " FRETS + STRUM", previewX, 164, 128, GOLD);
        if (screen == Screen.SONGS && mode == Mode.CAREER) {
            SitarUi.frame(c, previewX + 8, 171, 112, 18, CYAN);
            SitarUi.center(c, "PART & LEVEL", previewX, 177, 128, CREAM);
        } else SitarUi.center(c, difficulty.label(), previewX, 178, 128, DIM);
        SitarUi.footer(c, notice.isEmpty() ? performer.label() + " / " + shortRole() + " / " + difficulty.label() : notice,
                screen == Screen.SONGS && mode == Mode.CAREER ? "ENTER / A: PLAY / I / X: PART & LEVEL / ESC / B: WORLD BOARD" : "ENTER / A: SELECT / TAB: SETTINGS / ESC / B: BACK");
    }

    private void drawCareerBoard(SceneCanvas c) {
        boolean tourMenu = screen == Screen.TOURS;
        SitarUi.text(c, tourMenu ? "CHOOSE YOUR JOURNEY" : SitarUi.fit(careerTour.title(), c.width() - 24), 12, 42, GOLD);
        int pane = c.width() - 156, x = c.width() - 140;
        SitarUi.panel(c, 12, 55, pane, 136); SitarUi.panel(c, x, 55, 128, 136);
        if (tourMenu) {
            for (int i = 0; i < tours.size(); i++) {
                CareerTour tour = tours.get(i);
                SitarUi.choice(c, tour.title(), 18, 60 + i * 40, pane - 12, 36, selected == i);
                SitarUi.text(c, career.cleared(tour) + " MAIN ACTS / " + (career.complete(tour) ? "COMPLETE" : tour.worlds().size() + " WORLDS"), 39, 86 + i * 40, DIM);
            }
            CareerTour preview = tours.get(selected);
            SitarUi.center(c, preview.game().toUpperCase(java.util.Locale.ROOT), x, 65, 128, CYAN);
            actor(performer).draw(c, x + 64, 118, context.ticks(), shortRole(), false);
            SitarUi.wrapped(c, preview.subtitle(), x + 10, 148, 108, CREAM);
        } else {
            int first = listFirst(careerTour.worlds().size(), 5);
            for (int row = 0; row < Math.min(5, careerTour.worlds().size()); row++) {
                int i = first + row; CareerWorld world = careerTour.worlds().get(i); int y = 60 + row * 24;
                boolean unlocked = career.unlocked(careerTour, world);
                SitarUi.choice(c, unlocked ? world.title() : "LOCKED / " + world.title(), 18, y, pane - 12, 14, selected == i);
                SitarUi.text(c, (i + 1) + " / " + career.cleared(world) + "/" + world.requiredSongIds().size() + " MAIN ACTS" + (career.complete(world) ? " / CLEAR" : ""), 39, y + 15, DIM);
            }
            SitarUi.text(c, (selected + 1) + "/" + careerTour.worlds().size() + " WORLDS", 18, 184, CYAN);
            CareerWorld preview = careerTour.worlds().get(selected);
            SitarUi.center(c, "TOUR ROUTE", x, 65, 128, GOLD);
            SitarUi.center(c, preview.sideSongIds().size() + " SIDE GIGS", x, 80, 128, DIM);
            for (int i = 0; i < careerTour.worlds().size(); i++) {
                int column = i % 3, row = i / 3, nx = x + 20 + column * 34, ny = 91 + row * 15;
                int color = career.complete(careerTour.worlds().get(i)) ? GOLD : career.unlocked(careerTour, careerTour.worlds().get(i)) ? CYAN : 0xFF365E95;
                if (column < 2 && i + 1 < careerTour.worlds().size()) c.fill(nx + 10, ny + 4, 25, 1, DIM);
                if (i + 3 < careerTour.worlds().size()) c.fill(nx + 4, ny + 8, 1, 8, DIM);
                c.fill(nx, ny, 9, 9, color);
                if (i == selected) SitarUi.frame(c, nx - 2, ny - 2, 13, 13, CREAM);
            }
            SitarUi.frame(c, x + 8, 171, 112, 12, CYAN);
            SitarUi.center(c, "REPLAY SCENE", x, 174, 128, CREAM);
            if (career.complete(careerTour)) {
                SitarUi.frame(c, x + 8, 184, 112, 12, GOLD); SitarUi.center(c, "TOUR FINALE", x, 187, 128, GOLD);
            }
        }
        SitarUi.footer(c, notice.isEmpty() ? performer.label() + " / " + shortRole() + " / " + difficulty.label() : notice,
                tourMenu ? "ENTER / A: TOUR / ESC / B: LEVEL" : career.complete(careerTour)
                ? "A: GIG / R Y: SCENE / F X: FINALE / B: TOURS" : "ENTER / A: GIG / R / Y: REPLAY SCENE / ESC / B: TOURS");
    }
    private void drawStory(SceneCanvas c) {
        CareerStory.Line line = story.lines().get(storyLine);
        SitarUi.text(c, SitarUi.fit(story.title(), c.width() - 80), 12, 42, GOLD);
        SitarUi.text(c, (storyLine + 1) + "/" + story.lines().size(), c.width() - 48, 42, DIM);
        Roster speaker = line.speaker();
        if (speaker != null) actor(speaker).draw(c, c.width() / 2, 90, context.ticks(), shortRole(), false);
        SitarUi.panel(c, 12, 123, c.width() - 24, 68);
        SitarUi.text(c, speaker == null ? "BACKSTAGE" : speaker.label() + (line.action() ? " / STAGE BUSINESS" : ""), 24, 130, GOLD);
        SitarUi.wrapped(c, line.text(), 24, 145, c.width() - 48, line.action() ? CYAN : CREAM);
        SitarUi.footer(c, "Robotnik's world tour / " + performer.label() + " on " + shortRole(),
                "ENTER / A / CLICK: NEXT / ESC / B: SKIP / R ON BOARD: REPLAY");
    }

    private void drawPerformance(SceneCanvas c) {
        long position = player != null ? player.samplePosition() : session.position();
        int lanes = role.drums() ? 4 : difficulty.lanes();
        c.fill(0, 0, c.width(), 35, 0xF00B2457); c.fill(0, 34, c.width(), 1, GOLD);
        SitarUi.text(c, SitarUi.fit(song.label(), c.width() - 130), 8, 6, GOLD);
        SitarUi.text(c, shortRole() + " / " + difficulty.label(), 8, 22, CREAM);
        SitarUi.text(c, modeLabel(), c.width() - 113, 22, CYAN);
        if (session2 == null) {
            highway.draw(c, session, position, prepared.sampleRate(), settings, role.drums(), lanes,
                    c.width() / 2 - 98, 53, 196, 130, context.ticks());
            SitarUi.panel(c, 7, 47, 78, 134);
            SitarUi.text(c, "SCORE", 14, 55, CREAM);
            SitarUi.text(c, Long.toString(session.score()), 14, 69, GOLD);
            SitarUi.text(c, "X" + session.multiplier(), 14, 85, CYAN);
            SitarUi.text(c, "ROCK", 14, 102, CREAM); meter(c, 13, 114, 63, session.rock(), 0xFF6CDA66);
            SitarUi.text(c, "STAR POWER", 14, 135, CREAM); meter(c, 13, 147, 63, session.starCharge(), CYAN);
            SitarUi.text(c, session.starCharge() >= .5 ? "READY!" : "BUILD IT", 14, 166, GOLD);
            int x = c.width() - 85;
            actor(performer).draw(c, x + 38, 128, context.ticks(), shortRole(), screen == Screen.PLAY);
            SitarUi.wrapped(c, performer.label(), x + 5, 158, 75, CREAM);
            if (mode == Mode.PRACTICE) SitarUi.text(c, "NO FAIL", x + 5, 180, CYAN);
            if (online != null) SitarUi.text(c, "PEER " + online.remoteScore(), x + 5, 180, CYAN);
        } else {
            int half = c.width() / 2;
            drawPlayerHud(c, session, performer, 0, half);
            drawPlayerHud(c, session2, performer2, half, half);
            drawLocalPerformer(c, session, 0, half);
            drawLocalPerformer(c, session2, half, half);
            highway.draw(c, session, position, prepared.sampleRate(), settings, role.drums(), lanes, 12, 77, half - 84, 107, context.ticks());
            highway2.draw(c, session2, position, prepared.sampleRate(), settings2, role.drums(), lanes, half + 12, 77, half - 84, 107, context.ticks());
            c.fill(half - 1, 37, 2, 158, 0xFF365E95);
        }
        if (position < 0) center(c, Integer.toString((int) ((-position + prepared.sampleRate() - 1) / prepared.sampleRate())), 111, GOLD);
        if (autoplay) SitarUi.text(c, "DEMO", 14, 185, CYAN);
        double progress = Math.max(0, Math.min(1, position / (double) prepared.lengthSamples()));
        c.fill(0, 197, c.width(), 2, INK); c.fill(0, 197, (int) (c.width() * progress), 2, GOLD);
        SitarUi.footer(c, session2 == null ? "STREAK " + session.streak() + " / BEST " + session.bestStreak() : role.drums() ? "P1: ASDF + SPACE / P2: JKL; + P" : "P1: ASDFG / P2: JKL;' / REMAP IN SETTINGS",
                mode == Mode.PRACTICE ? "PRACTICE / RECORDS DISABLED / ESC: PAUSE" : "ESC / START: PAUSE / STAR POWER DOUBLES SCORE");
    }
    private void drawPlayerHud(SceneCanvas c, RhythmSession who, Roster actor, int x, int width) {
        SitarUi.text(c, SitarUi.fit(actor.label(), width - 20), x + 10, 41, CREAM);
        SitarUi.text(c, "SCORE " + who.score() + "  X" + who.multiplier(), x + 10, 53, GOLD);
        meter(c, x + 10, 66, width / 2 - 15, who.rock(), who.failed() ? 0xFFFF5969 : 0xFF6CDA66);
        meter(c, x + width / 2 + 5, 66, width / 2 - 15, who.starCharge(), CYAN);
    }
    private void drawLocalPerformer(SceneCanvas c, RhythmSession active, int x, int width) {
        c.clip(x + width - 72, 77, 72, 109);
        performanceArtist(active).draw(c, x + width - 36, 130, context.ticks(), shortRole(), screen == Screen.PLAY);
        c.unclip();
    }
    private String modeLabel() {
        return switch (mode) { case CAREER -> "CAREER"; case QUICK_PLAY -> "QUICK PLAY";
            case PRACTICE -> "PRACTICE"; case LOCAL_COOP, ONLINE_COOP -> "CO-OP"; default -> "SCORE DUEL"; };
    }
    private static void meter(SceneCanvas c, int x, int y, int width, double value, int color) {
        c.fill(x, y, width, 9, INK); SitarUi.frame(c, x, y, width, 9, DIM);
        c.fill(x + 2, y + 2, (int) ((width - 4) * value), 5, color);
    }

    private void drawPause(SceneCanvas c) {
        c.fill(0, 0, c.width(), 224, 0xB8000B28);
        int x = (c.width() - 248) / 2;
        SitarUi.panel(c, x, 48, 248, 144);
        title(c, "PAUSED", 59);
        for (int i = 0; i < 3; i++) SitarUi.choice(c, List.of("Resume", "Retry", "Song select").get(i), x + 10, 83 + i * 26, 228, 22, selected == i);
        SitarUi.wrapped(c, notice, x + 12, 166, 224, GOLD);
        SitarUi.footer(c, "Performance clock and score are paused", "ENTER / A: SELECT    ESC: RESUME");
    }
    private void drawResults(SceneCanvas c) {
        int width = c.width() - 156;
        boolean failed = result.failed();
        String heading = failed ? "PERFORMANCE FAILED" : mode == Mode.PRACTICE ? "PRACTICE COMPLETE" : mode == Mode.CAREER && careerTour != null && career.complete(careerTour) ? "TOUR COMPLETE!" : "STAGE CLEAR!";
        if (mode == Mode.LOCAL_VERSUS) heading = session.score() == session2.score() ? "DRAW!" : session.score() > session2.score() ? "PLAYER 1 WINS!" : "PLAYER 2 WINS!";
        if (mode == Mode.ONLINE_VERSUS) heading = !online.remoteFinished() ? "WAITING FOR PEER RESULT" : session.score() == online.remoteScore() ? "DRAW!" : session.score() > online.remoteScore() ? "YOU WIN!" : "PEER WINS!";
        SitarUi.text(c, heading, 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, width, 136);
        SitarUi.text(c, SitarUi.fit(song.label().toUpperCase(java.util.Locale.ROOT) + " / " + shortRole().toUpperCase(java.util.Locale.ROOT), width - 20), 22, 63, CREAM);
        SitarUi.label(c, "SCORE " + session.score(), 22, 78, width - 20, GOLD);
        SitarUi.text(c, result.stars() + " STARS / " + Math.round(result.accuracy() * 100) + "% / " + (result.fullCombo() ? "FULL COMBO" : result.grade()), 22, 96, CYAN);
        SitarUi.text(c, "BEST " + best + " / STREAK " + session.bestStreak(), 22, 109, DIM);
        for (int i = 0; i < 4; i++) SitarUi.choice(c, List.of("Retry", "Choose song", mode == Mode.CAREER ? "Continue tour" : "Choose performer", "Title").get(i), 18, 123 + i * 16, width - 12, 15, selected == i);
        int x = c.width() - 140;
        SitarUi.panel(c, x, 55, 128, 136);
        SitarUi.center(c, performer.label(), x, 66, 128, CREAM);
        actor(performer).draw(c, x + 64, 129, context.ticks(), shortRole(), false);
        if (result2 != null) {
            SitarUi.center(c, "P2 SCORE " + result2.score(), x, 164, 128, GOLD);
            SitarUi.center(c, Math.round(result2.accuracy() * 100) + "% / " + result2.stars() + " STARS", x, 178, 128, CYAN);
        } else if (online != null) SitarUi.center(c, "PEER " + online.remoteScore(), x, 164, 128, GOLD);
        else SitarUi.center(c, failed ? "TRY AGAIN!" : "GREAT SHOW!", x, 164, 128, GOLD);
        String recordNotice = saveFailed ? "SAVE FAILED / RECORDS REMAIN IN MEMORY" : autoplay ? "DEMO / RECORD NOT SAVED"
                : saved ? "RECORD SAVED / " + profile.clears() + " CLEARS" : "PRACTICE / MULTIPLAYER / FAILED: NO SOLO RECORD";
        SitarUi.footer(c, recordNotice, mode == Mode.CAREER ? failed ? CareerStory.retryQuip(performer) : CareerStory.successQuip(performer)
                : "ENTER / A: SELECT / ESC / B: SONG SELECT");
    }
    private void drawSettings(SceneCanvas c) {
        ControlSettings settings = activeSettings();
        SitarUi.text(c, "P" + (settingsSecond ? "2" : "1") + " / " + (settingsDrums ? "BONGOS" : "FRETS") + " / " + (settingsPad ? "GAMEPAD" : "KEYBOARD"), 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, c.width() - 24, 136);
        List<Integer> rows = settingsRows();
        int first = Math.max(0, Math.min(rows.size() - 8, rows.indexOf(selected) - 3));
        for (int row = first; row < Math.min(rows.size(), first + 8); row++) {
            int i = rows.get(row), y = 60 + (row - first) * 16;
            if (selected == i) {
                c.fill(18, y, c.width() - 36, 15, 0xFF173B6C);
                SitarUi.frame(c, 18, y, c.width() - 36, 15, CYAN);
                c.fill(19, y + 1, 3, 13, GOLD);
            }
            String name = i < 10 ? settings.actionName(i, settingsDrums)
                    : switch (i) { case 10 -> "INPUT OFFSET"; case 11 -> "DISPLAY OFFSET";
                        case 12 -> "LEFTY FLIP"; case 13 -> "TAP CALIBRATION"; case 14 -> "SWITCH INSTRUMENT PROFILE";
                        case 15 -> "EDIT PLAYER"; case 16 -> "REDUCED FLASHES"; case 17 -> "HIGHWAY SPEED"; default -> "RESTORE DEFAULTS"; };
            String value = i < 10 ? settings.binding(settingsDrums, settingsPad, i).label()
                    : switch (i) { case 10 -> settings.inputOffsetMs() + " MS"; case 11 -> settings.displayOffsetMs() + " MS";
                        case 12 -> settings.lefty() ? "ON" : "OFF"; case 15 -> settingsSecond ? "P2" : "P1";
                        case 16 -> settings.reducedFlashes() ? "ON" : "OFF"; case 17 -> settings.scrollSpeed() + "%"; default -> ">"; };
            SitarUi.text(c, name, 29, y + 4, selected == i ? GOLD : CREAM);
            SitarUi.text(c, value, c.width() - 28 - SitarUi.width(value, 1), y + 4, CYAN);
        }
        SitarUi.footer(c, capturingBinding ? "Press a key, button or move an axis / ESC cancels" : "ENTER / A: REMAP    TAB: DEVICE    LEFT / RIGHT: ADJUST",
                capturingBinding ? "Waiting for your new binding..." : "DELETE: UNBIND    ESC / B: SAVE AND RETURN");
    }
    private void drawCalibration(SceneCanvas c) {
        SitarUi.page(c, 0);
        SitarUi.text(c, "TAP CALIBRATION", 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, c.width() - 24, 136);
        SitarUi.wrapped(c, "Listen to the ROM drum hits. Tap " + activeSettings().binding(true, false, 4).label() + " / your PAD A on the beat. Use at least 8 taps. The median sets your input offset.", 24, 66, c.width() - 48, CREAM);
        center(c, calibration.size() + " TAPS", 111, GOLD);
        int fill = Math.min(8, calibration.size());
        for (int i = 0; i < 8; i++) {
            int x = c.width() / 2 - 77 + i * 20;
            c.fill(x, 137, 14, 8, i < fill ? CYAN : INK);
            SitarUi.frame(c, x, 137, 14, 8, DIM);
        }
        SitarUi.center(c, calibration.size() >= 8 ? "READY TO SAVE" : "KEEP TAPPING", 0, 165, c.width(), CYAN);
        SitarUi.footer(c, activeSettings().binding(true, false, 4).label() + " / YOUR PAD A: TAP WITH THE DRUMS", "ENTER: SAVE OFFSET AFTER 8 TAPS    ESC / B: CANCEL");
    }
    private String shortRole() { return role.label().split(" / ")[0]; }
    private static void title(SceneCanvas c, String text, int y) {
        center(c, text, y + 2, INK); center(c, text, y, GOLD);
    }
    private static void center(SceneCanvas c, String text, int y, int color) { c.text(text, (c.width() - c.textWidth(text)) / 2, y, color); }

    @Override public void exit(SceneContext ctx) { stop(); if (online != null) online.close(); ctx.storage().write("settings.txt", settings.encode()); ctx.storage().write("settings-p2.txt", settings2.encode()); ctx.storage().write("career.txt", career.encode()); }
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
