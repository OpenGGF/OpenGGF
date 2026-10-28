package echoes;

import com.openggf.control.*;
import com.openggf.game.mode.*;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.*;

/** Persistent attempt history deliberately has no rewind adapter. Checkpoints own only the world. */
public final class RoomSession implements GameplayFrameController {
    public enum Command { ECHO, UNDO, RESET, EXIT, HINT }
    public record Sample(PlayerInputState input, int x, int y) { }
    public record Recording(List<Sample> samples) {
        public Recording { samples = List.copyOf(samples); }
        public Sample at(int frame) { return samples.get(Math.min(frame, samples.size() - 1)); }
        public PlayerInputState input(int frame) { return frame < samples.size() ? samples.get(frame).input() : PlayerInputState.neutral(); }
    }
    public static final int REWIND_HOLD_FRAMES = 12;
    private final EchoProgress progress;
    private int rewindHold, loopLabel, clearAge, paradoxSlot=-1, paradoxAge;
    private boolean hintVisible, newBest, hubReturn;
    private String hubCursor="1-1";
    private final Map<String, RoomDefinition> rooms;
    public RoomSession(EchoProgress progress, Map<String, RoomDefinition> rooms) {
        this.progress = Objects.requireNonNull(progress);
        this.rooms = Map.copyOf(rooms);
        room = definition("1-1");
        hint = room.hint();
    }
    public RoomDefinition definition(String id) {
        return Objects.requireNonNull(rooms.get(id), "Unknown room: " + id);
    }
    public boolean available(String id) { return rooms.containsKey(id); }
    public EchoProgress progress() { return progress; }
    public int rewindHold() { return rewindHold; }
    public int loopLabel() { return loopLabel; }
    public int clearAge() { return clearAge; }
    public int paradoxSlot() { return paradoxSlot; }
    public int paradoxAge() { return paradoxAge; }
    public boolean hintVisible() { return hintVisible; }
    public boolean newBest() { return newBest; }
    public int clearStars() { return 1|(lastClearEchoes<=room.par()?2:0)|(lastClearShards>=3?4:0); }
    public String hubCursor() { return hubCursor; }
    public boolean consumeHubReturn() { boolean result=hubReturn; hubReturn=false;return result; }
    private void returnToHub() {
        hubReturn=true; titleRequested=true;
        int world=room.id().charAt(0)-'0',number=room.id().charAt(2)-'0';
        if(number<4) number++;else if(world<3) { world++;number=1; }
        hubCursor=world+"-"+number;
    }
    private RoomDefinition room;
    private String hint;
    public String hint() { return hint; }
    private final List<Recording> recordings = new ArrayList<>();
    private final List<Sample> attempt = new ArrayList<>();
    private CourseCheckpoint checkpoint;
    private Object visit;
    private Command pending;
    private PlayerInputState input = PlayerInputState.neutral();
    private final boolean[] fractured = new boolean[5];
    private final int[] fractureX = new int[5], fractureY = new int[5], fractureFlash = new int[5];
    private int frame, loops;
    private boolean started, cleared, titleRequested, initialized;
    private final EchoLooks looks = new EchoLooks();
    private final Set<Integer> shards = new HashSet<>();
    private final Map<String, Boolean> channels = new HashMap<>();
    private int lastClearFrames, lastClearEchoes, lastClearShards;
    public RoomDefinition room() { return room; }
    public List<Recording> recordings() { return List.copyOf(recordings); }
    public int frame() { return frame; }
    public int loopCount() { return loops; }
    public int remainingFrames() { return room.loopFrames() - frame; }
    public boolean cleared() { return cleared; }
    public boolean fractured(int slot) { return fractured[slot]; }
    public int fractureFlash(int slot) { return fractureFlash[slot]; }
    public int fractureX(int slot) { return fractureX[slot]; }
    public int fractureY(int slot) { return fractureY[slot]; }
    public int lastClearFrames() { return lastClearFrames; }
    public int lastClearEchoes() { return lastClearEchoes; }
    public int lastClearShards() { return lastClearShards; }
    public void select(RoomDefinition room) {
        this.room = Objects.requireNonNull(room); hint=room.hint(); recordings.clear(); checkpoint = null; visit = null;
        rewindHold=0; loopLabel=0; clearAge=0; paradoxAge=0; hintVisible=false; newBest=false;
        loops = 0; initialized = false; cleared = false; titleRequested = false; pending = null; resetAttempt();
    }
    public void command(Command command) { pending = Objects.requireNonNull(command); }
    public boolean channel(String key) { return channels.getOrDefault(key, false); }
    public boolean allChannels(String keys) {
        return keys.isEmpty() || Arrays.stream(keys.split("\\+")).allMatch(this::channel);
    }
    void channel(String key, boolean active) { channels.merge(key, active, Boolean::logicalOr); }
    void shard(int index) { shards.add(index); }
    void clear() {
        if (cleared) return;
        cleared = true; clearAge=0; lastClearFrames = frame+1; lastClearEchoes = recordings.size(); lastClearShards = shards.size();
        newBest=progress.record(room,lastClearEchoes,lastClearFrames,lastClearShards);
    }
    public boolean active(AbstractPlayableSprite body, ObjectServices services) {
        if (body == services.camera().getFocusedSprite()) return !body.getDead();
        int i = services.spriteManager().getSidekicks().indexOf(body);
        return i >= 0 && i < recordings.size() && !fractured[i] && !body.getDead();
    }
    @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) { return true; }
    @Override public boolean allowsDebugRewind() { return false; }
    @Override public GameplayFrameDisposition beforeFrame(CourseControl course, LogicalInputSnapshot row) {
        progress.tick();
        var services = course.objectServices();
        services.levelManager().setForceHudSuppressed(true);
        Object level = services.levelManager().getCurrentLevel();
        if (visit != level) { checkpoint = null; initialized = false; visit = level; }
        course.finishInitialPresentation();
        if (!course.presentationReady()) { course.advanceEntryPresentation(); return GameplayFrameDisposition.HOLD; }
        if (!initialized) {
            configure(services);
            initialized = true;
            checkpoint = course.capture();
            feed(services);
        }
        if (pending != null) {
            Command command = pending; pending = null;
            if(cleared && command != Command.EXIT) return GameplayFrameDisposition.HOLD;
            switch (command) {
                case EXIT -> { returnToHub(); return GameplayFrameDisposition.HOLD; }
                case HINT -> { hintVisible=!hintVisible; }
                case UNDO -> { if (!recordings.isEmpty()) recordings.removeLast(); restore(course); }
                case RESET -> { recordings.clear(); loops = 0; restore(course); }
                case ECHO -> { if(!cleared&&rewindHold==0) { commit(); beginRewind(services); } }
            }
            return GameplayFrameDisposition.HOLD;
        }
        if (rewindHold > 0) {
            if (--rewindHold == 0) { restore(course); loopLabel=42; }
            return GameplayFrameDisposition.HOLD;
        }
        if(loopLabel>0) loopLabel--;
        if(paradoxAge>0) paradoxAge--;
        if (cleared) {
            clearAge++;
            if(clearAge==12||clearAge==30||clearAge==48) {
                int slot=(clearAge-12)/18;
                if((clearStars()&1<<slot)!=0) services.playSfx(slot==2?0xA1:0xCE);
            }
            return GameplayFrameDisposition.HOLD;
        }
        input = row.player1();
        if (input.heldMask() != 0) started = true;
        if (!started) return GameplayFrameDisposition.HOLD;
        channels.clear();
        for (var object : services.objectManager().getActiveObjects())
            if (object instanceof EchoObject echo) echo.samplePlate();
        feed(services);
        looks.refresh(services, frame);
        return GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
    }
    private void configure(ObjectServices services) {
        var camera = services.camera();
        camera.setMinX((short) room.left()); camera.setMinXTarget((short) room.left());
        camera.setMaxX((short) (room.right() - 384)); camera.setMaxXTarget((short) (room.right() - 384));
        camera.setMinY((short) room.top()); camera.setMinYTarget((short) room.top());
        camera.setMaxY((short) room.bottom()); camera.setMaxYTarget((short) room.bottom());
        camera.setX((short) Math.clamp(camera.getX(), room.left(), room.right() - 384));
        camera.setY((short) Math.clamp(camera.getY(), room.top(), room.bottom()));
        var main = camera.getFocusedSprite();
        main.setControlLocked(false);
        NativePositionOps.writeXPosResetSubpixel(main, room.spawnX());
        NativePositionOps.writeYPosResetSubpixel(main, room.spawnY());
        main.updateSensors(main.getX(), main.getY());
        var initial = main.captureRewindState();
        var sprites = services.spriteManager();
        // The contribution API intentionally scopes launch teams to authored mod zones.
        // A stock-terrain patch constructs its room-local roster through the same registered factory.
        for (var old : List.copyOf(sprites.getSidekicks())) sprites.removeSprite(old.getCode());
        var definition = services.gameModule().getPlayableCharacterRegistry().find(com.openggf.game.CharacterKey.SONIC).orElseThrow();
        for (int slot = 0; slot < 5; slot++) {
            var body = definition.spriteFactory().create("sonic_p" + (slot + 2), main.getX(), main.getY());
            body.setCpuControlled(true);
            sprites.addSprite(body, "sonic");
        }
        var bodies = sprites.getSidekicks();
        for (var body : bodies) {
            body.restoreRewindState(initial);
            body.setCpuControlled(true);
            body.setCpuController(new EchoController(body, main));
        }
        services.levelManager().refreshPlayableSpriteArt();
        looks.reset();
    }
    private void resetAttempt() {
        frame = 0; started = false; cleared = false; attempt.clear(); shards.clear(); channels.clear();
        Arrays.fill(fractured, false); Arrays.fill(fractureFlash, 0);
    }
    private void commit() {
        if (!attempt.isEmpty() && recordings.size() < room.echoCap()) recordings.add(new Recording(attempt));
        loops++;
    }
    private void beginRewind(ObjectServices services) {
        rewindHold=REWIND_HOLD_FRAMES;
        services.playSfx(0xBC); // S2 spindash release: short rising snap, leaving music running.
    }
    private void restore(CourseControl course) {
        rewindHold=0; paradoxAge=0;
        course.restore(checkpoint);
        resetAttempt();
        feed(course.objectServices());
    }
    private void feed(ObjectServices services) {
        var bodies = services.spriteManager().getSidekicks();
        for (int i = 0; i < bodies.size(); i++) {
            var body = bodies.get(i);
            boolean active = i < recordings.size() && !fractured[i];
            ((EchoController) body.getCpuController()).feed(active ? recordings.get(i).input(frame) : PlayerInputState.neutral(), active);
            body.setHidden(!active);
            // Inactive bodies retain their registered roster slot but cannot touch any room object.
            if (!active) {
                NativePositionOps.writeXPosResetSubpixel(body, room.left());
                NativePositionOps.writeYPosResetSubpixel(body, room.top() - 512);
            }
        }
    }
    @Override public void afterTick(CourseControl course, boolean advanced) {
        if (!advanced) return;
        var services = course.objectServices();
        var main = services.camera().getFocusedSprite();
        attempt.add(new Sample(input, main.getCentreX(), main.getCentreY()));
        var bodies = services.spriteManager().getSidekicks();
        for (int i = 0; i < recordings.size(); i++) {
            var body = bodies.get(i);
            var expected = recordings.get(i).at(frame);
            long dx = body.getCentreX() - expected.x(), dy = body.getCentreY() - expected.y();
            if (!fractured[i] && (body.getDead() || dx * dx + dy * dy > 48L * 48)) {
                fractured[i] = true; fractureX[i] = body.getCentreX(); fractureY[i] = body.getCentreY(); fractureFlash[i] = 18;
                paradoxSlot=i; paradoxAge=75; services.playSfx(0xC6); // Ring scatter: an unmistakable broken run.
            }
        }
        for (int i = 0; i < 5; i++) if (fractureFlash[i] > 0) fractureFlash[i]--;
        frame++;
        if (main.getDead()) { loops++; beginRewind(services); }
        else if (!cleared && frame >= room.loopFrames()) { commit(); beginRewind(services); }
        else feed(services);
    }
    @Override public boolean consumeTitleRequest() { boolean value = titleRequested; titleRequested = false; return value; }
}
