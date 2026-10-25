package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneClipboard;
import com.openggf.mods.scene.SceneMouse;
import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.RoomAccessPolicy;
import com.openggf.net.protocol.RoomInviteCode;
import com.openggf.net.protocol.RoomPassword;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.TextEntry;
import openggf.timeattack.ui.ViewInput;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Scene-thread ownership of an explicit disclosure, a clipboard gesture and owner controls. */
public final class LobbyInviteController implements AutoCloseable {
    private static final long COPY_INTENT_MILLIS = 4_500;
    private static final long REQUEST_MILLIS = 10_000;

    private record Scope(String instance, long access, long invite, RoomAccessPolicy policy, boolean allowed, boolean manual) {
        boolean sameAccess(Scope other) {
            return Objects.equals(instance, other.instance) && access == other.access
                    && policy.equals(other.policy) && allowed == other.allowed && manual == other.manual;
        }
    }

    private sealed interface Pending permits Idle, Fetching, Copying, Changing { }
    private record Idle() implements Pending { }
    private record Fetching(Scope scope, long disclosureId, boolean copy, long requestedAt) implements Pending { }
    private record Copying(Scope scope, SceneClipboard.Write write) implements Pending { }
    private record Changing(Scope scope, InviteCodeView.Action action, long requestedAt) implements Pending { }

    private final MultiplayerRaceCoordinator coordinator;
    private final ClientRaceSession session;
    private final boolean owner;
    private final Supplier<String> manualInvite;
    private final Consumer<MenuCue> cues;
    private final LongSupplier clock;
    private InviteCodeView view;
    private TextEntry password;
    private Pending pending = new Idle();
    private Scope observed;
    private String status = "";
    private boolean closed;

    public LobbyInviteController(MultiplayerRaceCoordinator coordinator, boolean owner, Supplier<String> manualInvite,
                                 Consumer<MenuCue> cues, LongSupplier clock) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.session = coordinator.session();
        this.owner = owner;
        this.manualInvite = Objects.requireNonNull(manualInvite, "manualInvite");
        this.cues = Objects.requireNonNull(cues, "cues");
        this.clock = Objects.requireNonNull(clock, "clock");
        observed = scope();
        coordinator.setAccessListener(this::onControl);
    }

    public void open() {
        if (closed) return;
        hideDisclosure();
        if (!(pending instanceof Changing)) status = "";
        view = new InviteCodeView(this::snapshot, this::action, cues, session::hubNowEstimateMillis);
    }

    public boolean isOpen() { return view != null; }
    public boolean isEditingText() { return password != null; }

    public void update(ViewInput input, SceneMouse mouse, SceneClipboard clipboard) {
        if (closed || view == null) return;
        poll(clipboard);
        if (password != null) {
            switch (password.update(input)) {
                case ACCEPTED -> {
                    try (RoomPassword secret = RoomPassword.of(password.text())) {
                        password.clear();
                        password = null;
                        pending = new Changing(scope(), InviteCodeView.Action.CHANGE_PASSWORD, clock.getAsLong());
                        status = "Changing password...";
                        coordinator.changePassword(secret);
                    } catch (IllegalArgumentException invalid) {
                        password.withPrompt("Use 15 to 128 characters. Nothing is trimmed.");
                    }
                }
                case CANCELLED -> { password.clear(); password = null; }
                case NONE -> { }
            }
        } else {
            view.update(input, mouse);
            if (view.closed()) {
                hideDisclosure();
                view = null;
            }
        }
        poll(clipboard);
    }

    public void poll(SceneClipboard clipboard) {
        if (closed) return;
        Scope current = scope();
        if (!current.sameAccess(observed)) {
            if (view != null) view.hide();
            session.invalidateInvite();
            if (!(pending instanceof Changing)) pending = new Idle();
            clearPassword();
        } else if (current.invite() != observed.invite() && !(pending instanceof Fetching)) {
            if (view != null) view.hide();
            session.invalidateInvite();
            if (pending instanceof Copying) pending = new Idle();
        }
        observed = current;
        long now = clock.getAsLong();
        switch (pending) {
            case Fetching fetching -> {
                long limit = fetching.copy() ? COPY_INTENT_MILLIS : REQUEST_MILLIS;
                if (view == null || !current.allowed() || !current.sameAccess(fetching.scope())
                        || now - fetching.requestedAt() >= limit) {
                    hideDisclosure();
                    status = "Invite unavailable. Try again.";
                    break;
                }
                if (fetching.copy() && fetching.disclosureId() == -1 && manual()) {
                    copy(clipboard, manualInvite.get(), current);
                    break;
                }
                ControlMessage.RoomInvite invite = session.invite();
                if (invite != null && invite.disclosureId() == fetching.disclosureId()
                        && invite.inviteEpoch() == current.invite()) {
                    if (fetching.copy()) copy(clipboard, RoomInviteCode.parse(invite.code()).display(), current);
                    else { pending = new Idle(); status = ""; }
                }
            }
            case Copying copying -> {
                if (view == null || !current.equals(copying.scope()) || !current.allowed()) {
                    hideDisclosure();
                } else if (copying.write().result() != SceneClipboard.Result.PENDING) {
                    status = copying.write().result() == SceneClipboard.Result.SUCCESS ? "Copied" : "Copy unavailable";
                    pending = new Idle();
                }
            }
            case Changing changing -> {
                if (now - changing.requestedAt() >= REQUEST_MILLIS) {
                    pending = new Idle();
                    status = "Change not confirmed. Try again.";
                }
            }
            case Idle ignored -> { }
        }
        if (session.invite() == null && !(pending instanceof Fetching) && view != null
                && !manual()) view.hide();
    }

    private Scope scope() {
        RoomAccessPolicy policy = session.accessPolicy();
        boolean admitted = session.localSlot() >= 0 && session.kickReason() == null;
        return new Scope(session.instanceId(), session.accessEpoch(), session.inviteEpoch(), policy,
                admitted && (owner || policy.membersCanInvite()), manual());
    }

    private InviteCodeView.Snapshot snapshot() {
        Scope scope = scope();
        boolean manual = manual();
        ControlMessage.RoomInvite invite = manual ? null : session.invite();
        String code = manual ? manualInvite.get() : invite == null ? null : RoomInviteCode.parse(invite.code()).display();
        long expires = manual ? Long.MAX_VALUE : invite == null ? 0 : invite.expiresAtMillis();
        return new InviteCodeView.Snapshot(code, expires, scope.invite(), scope.allowed(), owner,
                scope.policy().membersCanInvite(), status, manual, scope.policy().passwordRequired());
    }

    private boolean manual() { return owner && manualInvite.get() != null; }

    private void action(InviteCodeView.Action action) {
        if (action == InviteCodeView.Action.HIDE) { hideDisclosure(); return; }
        if (pending instanceof Changing || !scope().allowed()) return;
        switch (action) {
            case FETCH, COPY -> {
                if (manual()) {
                    // The clipboard is called only by poll(), inside the owning scene's update.
                    if (action == InviteCodeView.Action.COPY) {
                        pending = new Fetching(scope(), -1, true, clock.getAsLong());
                    }
                } else {
                    ControlMessage.RoomInviteRequest request = coordinator.requestInvite();
                    pending = new Fetching(scope(), request.disclosureId(), action == InviteCodeView.Action.COPY,
                            clock.getAsLong());
                    status = "Fetching invite...";
                }
            }
            case ROTATE, TOGGLE_SHARING -> {
                if (!owner || manual()) return;
                hideDisclosure();
                pending = new Changing(scope(), action, clock.getAsLong());
                status = "Updating invite...";
                if (action == InviteCodeView.Action.ROTATE) coordinator.rotateInvite();
                else coordinator.setMemberInvites(!session.accessPolicy().membersCanInvite());
            }
            case CHANGE_PASSWORD -> {
                if (owner && session.accessPolicy().passwordRequired()) {
                    hideDisclosure();
                    password = TextEntry.password("NEW ROOM PASSWORD");
                }
            }
            case HIDE -> { }
        }
    }

    private void copy(SceneClipboard clipboard, String value, Scope scope) {
        pending = new Copying(scope, clipboard.writeText(value));
    }

    private void onControl(ControlMessage message) {
        if (message instanceof ControlMessage.RoomInviteRejected rejected && !(pending instanceof Idle)) {
            hideDisclosure();
            pending = new Idle();
            status = rejected.retryAfterMillis() > 0 ? "Invite unavailable. Try again later." : "Invite unavailable";
        } else if (pending instanceof Changing changing) {
            Scope current = scope();
            boolean acknowledged = switch (changing.action()) {
                case CHANGE_PASSWORD -> message instanceof ControlMessage.RoomPasswordChanged changed
                        && changed.accessEpoch() > changing.scope().access();
                case ROTATE -> controlAcknowledged(message, changing, current);
                case TOGGLE_SHARING -> controlAcknowledged(message, changing, current)
                        && current.policy().membersCanInvite() != changing.scope().policy().membersCanInvite();
                default -> false;
            };
            if (acknowledged) {
                pending = new Idle();
                status = changing.action() == InviteCodeView.Action.CHANGE_PASSWORD ? "Password changed" : "Invite updated";
            }
        }
    }

    private static boolean controlAcknowledged(ControlMessage message, Changing changing, Scope current) {
        return message instanceof ControlMessage.RoomInvite reply && reply.disclosureId() == 0
                && Objects.equals(reply.instanceId(), current.instance())
                && reply.accessEpoch() == current.access() && reply.inviteEpoch() == current.invite()
                && current.invite() > changing.scope().invite();
    }

    private void hideDisclosure() {
        session.invalidateInvite();
        if (view != null) view.hide();
        if (!(pending instanceof Changing)) pending = new Idle();
    }

    private void clearPassword() {
        if (password != null) password.clear();
        password = null;
    }

    public void dismiss() {
        focusLost();
        view = null;
    }

    public void focusLost() {
        hideDisclosure();
        clearPassword();
    }

    public void draw(SceneCanvas canvas) {
        if (password != null) password.draw(canvas);
        else if (view != null) view.draw(canvas);
    }

    @Override
    public void close() {
        if (closed) return;
        focusLost();
        pending = new Idle();
        view = null;
        closed = true;
        coordinator.setAccessListener(message -> { });
    }
}
