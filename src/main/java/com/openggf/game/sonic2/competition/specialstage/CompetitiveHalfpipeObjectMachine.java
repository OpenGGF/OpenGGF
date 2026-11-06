package com.openggf.game.sonic2.competition.specialstage;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.game.sonic2.competition.Sonic2CompetitiveSpecialStageAdapter.Events;
import com.openggf.game.sonic2.specialstage.CompetitiveSpecialStagePlayerAccess;
import com.openggf.game.sonic2.specialstage.Sonic2PerspectiveData;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageBomb;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageObject;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStagePlayer;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageRing;
import com.openggf.physics.TrigLookupTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Native special-stage Object_RAM ownership, live execution and DisplaySprite queues. */
public final class CompetitiveHalfpipeObjectMachine {
    public enum Kind { PLAYER, HUD, BANNER, COUNTERS, PLAYER_SHADOW, TAIL, HIDDEN, WIN_COUNTERS,
        ITEM, ITEM_SHADOW, MARKER, RAINBOW, WINGS, HAND, TIMER, LETTER, BANNER_PART, SPILL_OWNER, SPILL }
    public static final class Node {
        final Kind kind;
        int routine, owner = -1, child = -1, x, y, angle, mapping, timer, base, frame, palette, nativeArt;
        boolean kill, down, visible;
        Sonic2SpecialStageObject item;
        CompetitiveHalfpipeSpill spill;
        public Node(Kind kind) {
            this.kind = Objects.requireNonNull(kind, "kind");
            nativeArt = switch (kind) {
                case PLAYER -> 0x22E5;
                case HUD -> 0x1FA;
                case BANNER, BANNER_PART -> 0x38A;
                case COUNTERS, WIN_COUNTERS -> 0x41FA;
                case PLAYER_SHADOW, ITEM_SHADOW -> 0x623C;
                case TAIL -> 0x4316;
                case HIDDEN, LETTER -> 0x41A2;
                case RAINBOW, SPILL -> 0x6322;
                case WINGS, HAND -> 0x21A2;
                default -> 0;
            };
            palette = nativeArt >>> 13 & 3;
        }
        public Kind kind() { return kind; }
        public int owner() { return owner; }
        public int child() { return child; }
        public int routine() { return routine; }
    }
    public record NodeState(Kind kind, int routine, int owner, int child, int x, int y, int angle,
                            int mapping, int timer, int base, int frame, int palette, int nativeArt,
                            boolean kill, boolean down, boolean visible, Object item,
                            CompetitiveHalfpipeSpill.State spill) { }
    public record Display(int slot, int bucket) { }
    public record State(int stage, CompetitiveHalfpipeObjectRam.State<NodeState> ram,
                        CompetitiveHalfpipeObjectStream.State stream,
                        CompetitiveHalfpipeCheckpoint.State checkpoint,
                        boolean noCheckpoint, boolean rainbow, boolean started, boolean complete,
                        List<Display> display, int addressRegister2, List<Integer> inactiveArtWords) {
        public State {
            display = List.copyOf(display); inactiveArtWords = List.copyOf(inactiveArtWords);
            if (inactiveArtWords.size() != 128) throw new IllegalArgumentException("128 native SST art words required");
        }
    }
    private final CompetitiveHalfpipeRomData data;
    private final CompetitiveSpecialStagePlayerAccess.Pair players;
    private final AudioManager audio;
    private final Events events;
    private final Sonic2PerspectiveData perspective;
    private final CompetitiveHalfpipeObjectRam<Node> ram = new CompetitiveHalfpipeObjectRam<>();
    private final CompetitiveHalfpipeObjectStream stream;
    private final CompetitiveHalfpipeCheckpoint checkpoint;
    private final List<Display> display = new ArrayList<>();
    // FixBugs=0's failed wing allocation still writes art_tile(a1/a2).
    // ID-zero cells retain these words without becoming allocated objects.
    private final int[] inactiveArtWords = new int[128];
    private int addressRegister2;
    private CompetitiveHalfpipeTrack track;
    private int stage = -1, perspectiveFrame;
    private boolean noCheckpoint, rainbow, started, complete;

    public CompetitiveHalfpipeObjectMachine(CompetitiveHalfpipeRomData data,
            CompetitiveSpecialStagePlayerAccess.Pair players, AudioManager audio, Events events) {
        this.data = Objects.requireNonNull(data, "data");
        this.players = Objects.requireNonNull(players, "players");
        this.audio = Objects.requireNonNull(audio, "audio");
        this.events = Objects.requireNonNull(events, "events");
        perspective = CompetitiveHalfpipeItems.perspective(data);
        stream = new CompetitiveHalfpipeObjectStream(data);
        checkpoint = new CompetitiveHalfpipeCheckpoint(data);
    }

    public void load(int stage) {
        ram.clear(); java.util.Arrays.fill(inactiveArtWords, 0); addressRegister2 = 0;
        display.clear(); stream.load(stage); this.stage = stage;
        noCheckpoint = rainbow = started = complete = false;
        checkpoint.restore(new CompetitiveHalfpipeCheckpoint(data).capture());
        for (int slot = 0; slot < 5; slot++) {
            Kind kind = switch (slot) { case 0, 1 -> Kind.PLAYER; case 2 -> Kind.HUD;
                case 3 -> Kind.BANNER; default -> Kind.COUNTERS; };
            Node node = new Node(kind); node.owner = slot;
            if (kind == Kind.PLAYER) node.nativeArt = slot == 0 ? 0x22E5 : 0x4300;
            ram.fixed(slot, node);
        }
    }

    /** CreateRingsToGoText is unconditional in GM_Special, even though VS keeps all eleven hidden. */
    public void initializeHiddenMessages() {
        Node numeric = new Node(Kind.HIDDEN); numeric.nativeArt = 0x41FA; numeric.routine = 0x0E; numeric.y = 0x38;
        if (ram.allocate(numeric) < 0) return;
        int cursor = address(data.rom(), 0x35604);
        for (int section = 0; section < 3; section++) {
            int index = 0;
            while (true) {
                int frame = data.rom().readU8(cursor++);
                if (frame == 0xFF) break;
                Node node = new Node(Kind.HIDDEN); node.mapping = frame; node.y = 0x38;
                node.routine = 0x10 + section * 2;
                node.x = section == 0 ? 0x68 + index * 8
                        : data.rom().readU16BE(address(data.rom(), 0x35612) + (section == 2 ? 5 : index) * 2);
                node.base = node.x;
                if (ram.allocate(node) < 0) return;
                index++;
            }
        }
    }

    public void prepareSegment(CompetitiveHalfpipeTrack track) {
        this.track = Objects.requireNonNull(track, "track");
        int segment = track.segment();
        if (segment >= data.segments(stage).size()) return;
        stream.step(segment, track.drawingIndex(),
                track.segmentLength(data.segments(stage).get(segment).geometry()), ram,
                new CompetitiveHalfpipeObjectStream.Factory<>() {
                    @Override public Node item(boolean bomb, int depth, int angle) {
                        Node node = new Node(Kind.ITEM);
                        node.item = CompetitiveHalfpipeItems.create(data, bomb, depth, angle);
                        return node;
                    }
                    @Override public Node marker(int marker) {
                        Node node = new Node(Kind.MARKER); node.base = marker;
                        if (marker == 0xFC) noCheckpoint = true;
                        return node;
                    }
                });
    }

    /** SSHurt_Animation attempts this once, only at timer $08; failure preserves both ring banks. */
    public int requestSpill(int owner) {
        if (players.player(owner).getRings() == 0) return -1;
        Node node = new Node(Kind.SPILL_OWNER); node.owner = owner;
        return ram.allocate(node);
    }

    public void runPass(CompetitiveHalfpipeTrack track, int perspectiveFrame, IntConsumer actorPass) {
        this.track = Objects.requireNonNull(track, "track"); this.perspectiveFrame = perspectiveFrame;
        display.clear();
        ram.runPass((slot, node) -> tick(slot, node, actorPass));
        updateCheckpointView();
    }

    private void tick(int slot, Node node, IntConsumer actorPass) {
        switch (node.kind) {
            case PLAYER -> {
                if (node.routine == 0) {
                    node.routine = 2;
                    Node shadow = new Node(Kind.PLAYER_SHADOW); shadow.owner = slot;
                    ram.fixed(slot + 5, shadow);
                    if (slot == 1) { Node tail = new Node(Kind.TAIL); tail.owner = 1; tail.frame = 1; ram.fixed(7, tail); }
                } else actorPass.accept(slot);
                if (players.player(slot).dplcLoadRuns()) show(slot, players.player(slot).getPriority());
            }
            case HUD -> {
                // Obj5E_Init (s2.asm:9515-9566) returns before its first display.
                if (node.routine == 0) node.routine = 1; else show(slot, 0);
            }
            case COUNTERS -> {
                if (node.routine == 0) {
                    // Obj87_Init (s2.asm:9778-9848): allocate the win HUD and
                    // return; the VS counter is displayed on its next pass.
                    node.routine = 2; ram.allocate(new Node(Kind.WIN_COUNTERS)); return;
                }
                node.x = players.player(0).getRings(); node.y = players.player(1).getRings(); show(slot, 0);
            }
            case PLAYER_SHADOW -> {
                addressRegister2 = shadowPoseEnd(players.player(node.owner));
                node.nativeArt = data.rom().readU16BE(addressRegister2 - 8);
                show(slot, 4);
            }
            case TAIL -> {
                var player = players.player(1);
                if (player.shouldRenderTailsTails() && player.tailsTailsDplcLoadRuns()) {
                    int frame = player.getTailsTailsMappingFrame();
                    if (node.frame != frame) {
                        node.frame = frame;
                        addressRegister2 = address(data.rom(), 0x345FA) + data.rom().readU16BE(address(data.rom(), 0x345FA) + (0x24 + frame) * 2) + 2;
                    }
                    show(slot, player.getPriority() - 1);
                }
            }
            case HIDDEN -> {
                // VS keeps SS_TriggerRingsToGo set. MoveAndFlash still updates
                // its hidden word positions using the initial zero BCD bank.
                if (node.routine == 0x12) node.x = node.base - 8;
            }
            case WIN_COUNTERS -> {
                node.x = checkpoint.wins(0); node.y = checkpoint.wins(1);
                if ((node.x | node.y) != 0) show(slot, 0);
            }
            case BANNER -> banner(slot, node);
            case LETTER -> letter(slot, node);
            case BANNER_PART -> fly(slot, node);
            case ITEM -> item(slot, node);
            case ITEM_SHADOW -> shadow(slot, node);
            case MARKER -> marker(slot, node);
            case RAINBOW -> rainbow(slot, node);
            case WINGS -> { if (--node.timer == 0) clearSlot(slot); else show(slot, 1); }
            case HAND -> hand(slot, node);
            case TIMER -> timer(slot, node);
            case SPILL_OWNER -> spillOwner(slot, node);
            case SPILL -> { node.spill.tick(); if (node.spill.removed()) clearSlot(slot); else show(slot, node.spill.priority()); }
        }
    }

    private void banner(int slot, Node node) {
        if (node.routine == 0) {
            phrase(slot, 8); node.x = 0x80; node.y = -0x40; node.routine = 2;
        }
        if (node.routine == 2) {
            if (++node.y == 0x48) { node.routine = 4; node.timer = 15; }
            show(slot, 1);
        } else if (node.routine == 4) {
            if (--node.timer != 0) { show(slot, 1); return; }
            int mapping = address(data.rom(), 0x7240) + data.rom().readU16BE(address(data.rom(), 0x7244));
            addressRegister2 = mapping;
            for (int i = 0; i < 7; i++) {
                Node part = new Node(Kind.BANNER_PART); part.mapping = i + 2; part.x = node.x; part.y = node.y;
                part.angle = TrigLookupTable.calcAngle((short) data.rom().readS16BE(mapping + 8 + i * 10), (short) -0x28);
                if (ram.allocate(part) >= 0) addressRegister2 = mapping + (i + 1) * 10;
            }
            node.routine = 10; node.timer = 30;
        } else if (node.routine == 10 && --node.timer < 0) {
            phrase(slot, 10); started = true; clearSlot(slot);
        }
    }

    private void phrase(int current, int id) {
        // PrintWord failure returns to PrintPhrase, which still consumes the
        // remaining phrase IDs. The final a2 value is always the ROM terminator end.
        int pointer = address(data.rom(), 0x35DDE) + data.rom().readU16BE(address(data.rom(), 0x35DDE) + id);
        while (data.rom().readU8(pointer++) < 0x80) { }
        addressRegister2 = pointer;
        for (var letter : CompetitiveHalfpipeRenderer.decodePhrase(data, id)) {
            Node node = new Node(Kind.LETTER); node.x = letter.x(); node.y = letter.y();
            node.mapping = letter.mappingFrame(); node.timer = 70; node.palette = 2;
            if (ram.allocateAfter(current, node) < 0) return;
        }
    }
    private void letter(int slot, Node node) {
        if (node.routine == 0) {
            if (--node.timer == 0) {
                node.routine = 8;
                node.angle = TrigLookupTable.calcAngle((short) (node.x - 0x80), (short) (node.y - 0x70));
            }
            show(slot, 1);
        } else fly(slot, node);
    }
    private void fly(int slot, Node node) {
        node.x = (short) (node.x + (TrigLookupTable.cosHex(node.angle) * 8 >> 8));
        node.y = (short) (node.y + (TrigLookupTable.sinHex(node.angle) * 8 >> 8));
        if (node.x < 0 || node.x > 256 || node.y < 0) clearSlot(slot); else show(slot, 1);
    }

    private void item(int slot, Node node) {
        var item = node.item;
        if (node.routine == 0) {
            node.routine = 2; node.base = 3;
            if ((item.getAngle() & 0x80) == 0) {
                Node shadow = new Node(Kind.ITEM_SHADOW); shadow.owner = slot;
                int angle = item.getAngle();
                shadow.base = angle <= 0x10 || angle > 0x70 ? 2 : angle <= 0x30 || angle > 0x50 ? 1 : 0;
                shadow.down = angle <= 0x30;
                node.child = ram.allocateAfter(slot, shadow);
            }
        }
        // Effect priority is checked before the native depth decrement, and
        // collection enters that effect routine only on the following pass.
        if (item.getState() != Sonic2SpecialStageObject.State.ACTIVE && item.getDepth() < 4) node.base = 1;
        CompetitiveHalfpipeItems.advance(item, perspectiveFrame, perspective, track.flipped(), track.drawingIndex() == 4);
        if (item.shouldRemove()) { detachShadow(node); clearSlot(slot); return; }
        projectShadow(node);
        if ((item.isRing() || item.isOnScreen()) && item.isCollidable()) {
            // Obj61_TestCollision loads a2 before testing either human's routine
            // or angle; a miss still leaves MainCharacter in this register.
            addressRegister2 = 0xB000;
            int first = players.player(0).getSSZPos() < players.player(1).getSSZPos() ? 0 : 1;
            if (!touch(node, first)) touch(node, 1 - first);
        }
        CompetitiveHalfpipeItems.afterTouch(item);
        node.nativeArt = item.isRing() ? (item.getAnimIndex() == 10 ? 0x477F : 0x6322)
                : item.getAnimIndex() == 10 ? 0x42B5 : 0x238A;
        if (item.isOnScreen() || item.getAnimIndex() == 10) show(slot, node.base);
    }
    private boolean touch(Node node, int owner) {
        var player = players.player(owner); var item = node.item;
        if (player.getRoutine() != Sonic2SpecialStagePlayer.RoutineState.NORMAL || player.isHurt()) return false;
        int radius = item.isRing() ? 10 : 8;
        if (((player.getAngle() - item.getAngle() + radius) & 255) >= radius * 2) return false;
        if (item instanceof Sonic2SpecialStageRing ring && ring.collect()) {
            players.collectRing(owner); events.collectedRing(owner); audio.playSfx(GameSound.RING);
        } else if (item instanceof Sonic2SpecialStageBomb bomb && bomb.explode()) {
            player.triggerHit(); audio.playSfx(GameSound.SLOW_SMASH);
        } else return false;
        detachShadow(node); return true;
    }
    private void detachShadow(Node parent) {
        if (parent.child >= 0) {
            Node child = ram.at(parent.child); if (child != null) child.kill = true;
            parent.child = -1;
        }
    }
    private void projectShadow(Node parent) {
        if (parent.child < 0 || !parent.item.isOnScreen()) return;
        Node child = ram.at(parent.child); if (child == null) return;
        var item = parent.item; var entry = perspective.getEntry(perspectiveFrame, item.getDepth());
        if (entry == null) return;
        int cosine = TrigLookupTable.cosHex(item.getAngle()), sine = TrigLookupTable.sinHex(item.getAngle());
        child.x = (short) (item.getScreenX() - (cosine * entry.xRadius >> 8)
                + (cosine * (entry.xRadius + (entry.xRadius >>> 2)) >> 8));
        child.y = (short) (item.getScreenY() - (sine * entry.yRadius >> 8)
                + (sine * (entry.yRadius + (entry.yRadius >> 2)) >> 8));
    }
    private void shadow(int slot, Node node) {
        if (node.kill) { clearSlot(slot); return; }
        Node parent = ram.at(node.owner);
        if (parent == null || parent.item == null || !parent.item.isOnScreen()) return;
        int size = 10 - parent.item.getAnimIndex(); if (size == 10) size = 9;
        node.mapping = size * 3 + node.base;
        node.nativeArt = 0x6000 + switch (node.base) { case 0 -> 0x23C; case 1 -> 0x262; default -> 0x29C; };
        show(slot, 5);
    }

    /** Obj5A_Init (s2.asm:71526-71555) allocates seven independent live rainbow cells. */
    private void marker(int slot, Node node) {
        if (noCheckpoint) { noCheckpoint = false; clearSlot(slot); return; }
        if (track.previousFrame() != 0x14) { checkpointPhase(CompetitiveHalfpipeCheckpoint.Phase.WAITING); return; }
        rainbow = true; checkpointPhase(CompetitiveHalfpipeCheckpoint.Phase.RAINBOW);
        for (int base = 6; base >= 0; base--) {
            Node child = new Node(Kind.RAINBOW); child.base = base; child.mapping = -1; child.palette = 3;
            ram.allocate(child);
        }
        clearSlot(slot);
    }
    private void rainbow(int slot, Node node) {
        if (track.drawingIndex() == 4) {
            node.mapping = (byte) data.rom().readU8(address(data.rom(), 0x357F4) + node.frame++);
            if (node.mapping < 0) {
                if (node.x == 0xE8) compare(slot, node); else clearSlot(slot);
                return;
            }
            int position = address(data.rom(), 0x35846) + node.base * 2 + node.timer;
            node.x = data.rom().readU8(position); node.y = data.rom().readU8(position + 1); node.timer += 14;
        }
        if (node.mapping >= 0) show(slot, 5);
    }
    private void compare(int slot, Node node) {
        rainbow = false; audio.playSfx(GameSound.CHECKPOINT);
        var old = checkpoint.capture(); int sector = old.sector() + 1;
        int comparison = Short.compare((short) players.player(0).getRings(), (short) players.player(1).getRings());
        int winner = Integer.signum(comparison); int phrase = winner == 0 ? 16 : winner > 0 ? 12 : 20;
        checkpoint.restore(new CompetitiveHalfpipeCheckpoint.State(winner == 0 ? CompetitiveHalfpipeCheckpoint.Phase.TIE_MESSAGE
                : CompetitiveHalfpipeCheckpoint.Phase.WINNER_HAND, sector, 10, 70, phrase,
                winner == 0 ? 0 : winner > 0 ? 1 : 2, 28, false, List.of(),
                old.p1Wins() + (winner > 0 ? 1 : 0), old.p2Wins() + (winner < 0 ? 1 : 0)));
        if (winner == 0) {
            phrase(slot, phrase);
            if (sector == 3) archive(); else events.tiedSector(sector - 1);
            Node timer = new Node(Kind.TIMER); timer.nativeArt = node.nativeArt; timer.timer = 70; timer.routine = sector == 3 ? 12 : 10;
            ram.replace(slot, timer);
        } else {
            Node wings = new Node(Kind.WINGS); wings.mapping = 20; wings.x = 128; wings.y = 28;
            wings.timer = 70; wings.palette = 1; wings.nativeArt = 0x21A2;
            int a1 = 0xCFC0;
            int wingsSlot = ram.allocate(wings);
            if (wingsSlot >= 0) {
                addressRegister2 = 0xB000 + wingsSlot * 0x40;
                Node hand = new Node(Kind.HAND); hand.mapping = 21; hand.x = 128; hand.y = 28;
                hand.timer = 70; hand.palette = 1; hand.nativeArt = 0x21A2;
                int handSlot = ram.allocate(hand);
                if (handSlot >= 0) a1 = 0xB000 + handSlot * 0x40;
            }
            // Shipped FixBugs=0 executes both additions even if either
            // allocation failed (loc_358F6..loc_3593A). a1 then points at
            // the excluded CFC0 guard. a2 can still name Sonic from an
            // earlier size-eight item test, so its art word changes too.
            int paletteBias = winner < 0 ? 0x2000 : 0;
            addArtWord(a1, paletteBias); addArtWord(addressRegister2, paletteBias);
            phrase(slot, phrase); clearSlot(slot);
        }
    }
    private void hand(int slot, Node node) {
        if (!node.down) { if (--node.y <= 24) { node.y = 25; node.down = true; } }
        else if (++node.y >= 32) { node.y = 31; node.down = false; }
        if (--node.timer != 0) { show(slot, 0); return; }
        archive();
        if (checkpoint.sector() == 3) {
            audio.fadeOutMusic(); node.routine = 12; node.timer = 48;
            Node timer = new Node(Kind.TIMER); timer.nativeArt = node.nativeArt; timer.routine = 12; timer.timer = 48; ram.replace(slot, timer);
            checkpointPhase(CompetitiveHalfpipeCheckpoint.Phase.FINAL_FADE);
        } else { phrase(slot, 10); clearSlot(slot); checkpointPhase(CompetitiveHalfpipeCheckpoint.Phase.IDLE); }
    }
    private void timer(int slot, Node node) {
        if (--node.timer != 0) return;
        if (node.routine == 10) {
            phrase(slot, 10); clearSlot(slot); checkpointPhase(CompetitiveHalfpipeCheckpoint.Phase.IDLE);
        } else {
            ram.clear(); java.util.Arrays.fill(inactiveArtWords, 0); display.clear(); players.clearPlayers(); complete = true; rainbow = false;
            checkpointPhase(CompetitiveHalfpipeCheckpoint.Phase.COMPLETE);
        }
    }
    private void archive() {
        events.archivedSector(checkpoint.sector() - 1, players.player(0).getRings(), players.player(1).getRings());
        players.resetRings();
    }

    /** Obj5B_Init (s2.asm:71375-71461): debit once, then allocate each decorative particle. */
    private void spillOwner(int slot, Node owner) {
        int lost = players.loseRings(owner.owner);
        if (lost == 0) { clearSlot(slot); return; }
        events.hitBomb(owner.owner);
        var player = players.player(owner.owner);
        int successful = 0;
        int angleTable = (lost & 1) == 0 ? address(data.rom(), 0x353EA) : address(data.rom(), 0x353F4);
        for (int attempt = 0; attempt < lost; attempt++) {
            int target = attempt == 0 ? slot : ram.findFree();
            if (target < 0) continue;
            var velocity = CompetitiveHalfpipeSpill.velocity(data, lost, successful++, player.getAngle());
            Node particle = new Node(Kind.SPILL);
            particle.spill = new CompetitiveHalfpipeSpill(data,
                    player.getXPos() + track.offsetX(), player.getYPos() + track.offsetY(), velocity.x(), velocity.y());
            ram.replace(target, particle);
        }
        addressRegister2 = angleTable + successful;
    }

    /** Raw art_tile words remain addressable in ID-zero cells, including guard slot 127. */
    public int nativeArtWord(int slot) {
        Objects.checkIndex(slot, 128);
        Node node = ram.at(slot); return node == null ? inactiveArtWords[slot] : node.nativeArt;
    }
    public int addressRegister2() { return addressRegister2; }
    private void addArtWord(int address, int value) {
        int offset = address - 0xB000;
        // Fixed Obj63 passes dominate earlier counter interiors. Dynamic
        // routines leave only ROM addresses or aligned object bases here.
        if (offset < 0 || offset >= 0x2000 || (offset & 0x3F) != 0) return;
        int slot = offset >>> 6; Node node = ram.at(slot);
        int word = (nativeArtWord(slot) + value) & 0xFFFF;
        if (node == null) inactiveArtWords[slot] = word;
        else { node.nativeArt = word; node.palette = word >>> 13 & 3; }
    }
    private int shadowPoseEnd(Sonic2SpecialStagePlayer player) {
        int angle = player.getAngle() & 255;
        if (player.getRoutine() != Sonic2SpecialStagePlayer.RoutineState.NORMAL) {
            int z = player.getSSZPos() & 0xFFFF;
            if (z == 0) return addressRegister2;
            int index = (short) ((Math.abs((short) player.getSSXPos()) << 7) / z);
            angle = data.rom().readU8(address(data.rom(), 0x33D32) + index);
            if (player.getSSXPos() >= 0) angle = -(angle - 0x80) & 255;
            if (player.getSSYPos() < 0) angle = -angle & 255;
        }
        return address(data.rom(), 0x3417A) + (((angle - 0x10) & 255) >>> 5) * 8 + 8;
    }

    private void clearSlot(int slot) {
        ram.clear(slot); inactiveArtWords[slot] = 0;
    }

    private void show(int slot, int bucket) { display.add(new Display(slot, Math.max(0, bucket))); }
    private void checkpointPhase(CompetitiveHalfpipeCheckpoint.Phase phase) {
        var old = checkpoint.capture();
        checkpoint.restore(new CompetitiveHalfpipeCheckpoint.State(phase, old.sector(), old.rainbowStep(), old.timer(),
                old.phraseId(), old.winner(), old.handY(), old.handMovingDown(), old.rainbow(), old.p1Wins(), old.p2Wins()));
    }
    private void updateCheckpointView() {
        var old = checkpoint.capture(); var visible = new ArrayList<CompetitiveHalfpipeRenderer.Rainbow>();
        int timer = old.timer(), handY = old.handY(); boolean down = old.handMovingDown();
        for (int slot = 0; slot < 128; slot++) {
            Node node = ram.at(slot); if (node == null) continue;
            if (node.kind == Kind.RAINBOW && node.mapping >= 0)
                visible.add(new CompetitiveHalfpipeRenderer.Rainbow(node.x, node.y, node.mapping));
            if (node.kind == Kind.HAND) { timer = node.timer; handY = node.y; down = node.down; }
            else if (node.kind == Kind.TIMER) timer = node.timer;
        }
        checkpoint.restore(new CompetitiveHalfpipeCheckpoint.State(old.phase(), old.sector(), old.rainbowStep(), timer,
                old.phraseId(), old.winner(), handY, down, visible, old.p1Wins(), old.p2Wins()));
    }

    public CompetitiveHalfpipeRenderFrame renderFrame() {
        var draws = new ArrayList<CompetitiveHalfpipeRenderFrame.Draw>();
        for (Display entry : display) {
            Node node = ram.at(entry.slot()); if (node == null) continue;
            CompetitiveHalfpipeRenderFrame.Type type;
            try { type = switch (node.kind) {
                case BANNER_PART -> CompetitiveHalfpipeRenderFrame.Type.BANNER;
                case LETTER -> CompetitiveHalfpipeRenderFrame.Type.MESSAGE;
                default -> CompetitiveHalfpipeRenderFrame.Type.valueOf(node.kind.name());
            }; }
            catch (IllegalArgumentException hidden) { continue; }
            int x = node.x, y = node.y, mapping = node.mapping, palette = node.palette;
            int art = switch (node.kind) {
                case ITEM_SHADOW -> switch (node.base) { case 0 -> 0x23C; case 1 -> 0x262; default -> 0x29C; };
                case RAINBOW, SPILL -> 0x322;
                case BANNER, BANNER_PART -> 0x38A;
                default -> 0x1A2;
            };
            if (node.kind == Kind.PLAYER || node.kind == Kind.TAIL) {
                art = node.nativeArt; palette = node.nativeArt >>> 13 & 3;
            } else if (node.kind == Kind.ITEM) {
                var item = node.item;
                boolean effect = item.getAnimIndex() == 10;
                x = item.getScreenX(); y = item.getScreenY();
                if (item instanceof Sonic2SpecialStageRing ring) {
                    mapping = ring.getMappingFrame(); art = effect ? 0x77F : 0x322; palette = effect ? 2 : 3;
                } else if (item instanceof Sonic2SpecialStageBomb bomb) {
                    mapping = bomb.getMappingFrame(); art = effect ? 0x2B5 : 0x38A; palette = effect ? 2 : 1;
                }
            } else if (node.kind == Kind.SPILL) {
                x = node.spill.x(); y = node.spill.y(); mapping = node.spill.mappingFrame(); palette = 3;
            } else if (node.kind == Kind.ITEM_SHADOW) palette = 3;
            draws.add(new CompetitiveHalfpipeRenderFrame.Draw(type, entry.slot(), entry.bucket(), node.owner,
                    x, y, mapping, art, palette, node.down && node.kind == Kind.ITEM_SHADOW, node.item, node.spill));
        }
        // DisplaySprite appends within each priority list. BuildSprites consumes lists from zero upwards.
        draws.sort(java.util.Comparator.comparingInt(CompetitiveHalfpipeRenderFrame.Draw::bucket));
        return new CompetitiveHalfpipeRenderFrame(draws);
    }
    public boolean started() { return started; }
    public boolean complete() { return complete; }
    public boolean rainbowActive() { return rainbow; }
    public CompetitiveHalfpipeCheckpoint checkpoint() { return checkpoint; }
    public CompetitiveHalfpipeObjectRam<Node> ram() { return ram; }
    public CompetitiveHalfpipeObjectStream stream() { return stream; }
    public List<Sonic2SpecialStageObject> objects() {
        var objects = new ArrayList<Sonic2SpecialStageObject>();
        for (int i = 0; i < 128; i++) { Node node = ram.at(i); if (node != null && node.item != null) objects.add(node.item); }
        return List.copyOf(objects);
    }
    public State capture() { return new State(stage, ram.capture(this::encode), stream.capture(), checkpoint.capture(),
            noCheckpoint, rainbow, started, complete, display, addressRegister2,
            java.util.Arrays.stream(inactiveArtWords).boxed().toList()); }
    public void restore(State state) {
        stage = state.stage(); ram.restore(state.ram(), this::decode); stream.restore(state.stream());
        checkpoint.restore(state.checkpoint()); noCheckpoint = state.noCheckpoint(); rainbow = state.rainbow();
        started = state.started(); complete = state.complete(); display.clear(); display.addAll(state.display());
        addressRegister2 = state.addressRegister2();
        for (int slot = 0; slot < 128; slot++) inactiveArtWords[slot] = state.inactiveArtWords().get(slot);
    }
    private NodeState encode(Node node) {
        return new NodeState(node.kind, node.routine, node.owner, node.child, node.x, node.y, node.angle,
                node.mapping, node.timer, node.base, node.frame, node.palette, node.nativeArt, node.kill, node.down, node.visible,
                node.item == null ? null : CompetitiveHalfpipeItems.capture(node.item),
                node.spill == null ? null : node.spill.capture());
    }
    private Node decode(NodeState state) {
        Node node = new Node(state.kind()); node.routine = state.routine(); node.owner = state.owner(); node.child = state.child();
        node.x = state.x(); node.y = state.y(); node.angle = state.angle(); node.mapping = state.mapping(); node.timer = state.timer();
        node.base = state.base(); node.frame = state.frame(); node.palette = state.palette(); node.nativeArt = state.nativeArt(); node.kill = state.kill();
        node.down = state.down(); node.visible = state.visible();
        if (state.item() != null) node.item = CompetitiveHalfpipeItems.restore(data, state.item());
        if (state.spill() != null) { node.spill = new CompetitiveHalfpipeSpill(data, 0, 0, 0, 0); node.spill.restore(state.spill()); }
        return node;
    }
}
