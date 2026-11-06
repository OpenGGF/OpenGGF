package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneKeys;
import com.openggf.control.PhysicalGamepad;
import com.openggf.control.PhysicalInput;
import com.openggf.mods.ui.PixelCanvas;
import com.openggf.net.protocol.QuickChatPhrase;
import com.openggf.net.chat.EnglishQuickChat;
import openggf.timeattack.ui.ViewInput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Canned messages only. Race input stays independent of gameplay movement, jump and pause. */
public final class QuickChatChooser {
    private enum Activation { LIST, RADIAL }

    private final QuickChatRadialMenu radialMenu = new QuickChatRadialMenu();
    private boolean open;
    private Activation activation = Activation.LIST;
    private int selected;
    private int controllerId = -1;
    private boolean clickHeld, triggerHeld;

    public boolean isOpen() { return open; }
    public boolean isRadial() { return open && activation == Activation.RADIAL; }
    public void open() { open = true; selected = 0; activation = Activation.LIST; }
    public void close() { open = false; }

    /** The opening tick establishes the primary pad's edges, including an already-held trigger. */
    public void openController(PhysicalInput input) {
        PhysicalGamepad pad = primaryPad(input);
        baseline(pad);
        open = true;
        activation = Activation.RADIAL;
        selected = pad == null ? -1 : radialSelection(pad, -1);
    }

    /** Loading or leaving a run invalidates the old pad baseline. */
    public void resetRaceInput() { close(); controllerId = -1; }

    /**
     * Race controls use only the right stick and trigger. Always sample, even while chat is
     * unavailable, so a held control cannot become a new press after retry or a mode change.
     */
    public void updateController(PhysicalInput input, boolean allowed, Consumer<QuickChatPhrase> send) {
        PhysicalGamepad pad = input.gamepad(controllerId);
        if (pad == null) {
            if (controllerId != -1 && isRadial()) close();
            baseline(primaryPad(input));
            if (!allowed) close();
            return; // Connecting or replacing a pad establishes its baseline.
        }
        boolean click = pad.buttonDown(PhysicalGamepad.BUTTON_RIGHT_THUMB);
        boolean trigger = pad.axis(PhysicalGamepad.AXIS_RIGHT_TRIGGER) > (triggerHeld ? 0.30f : 0.55f);
        boolean clicked = click && !clickHeld;
        boolean sent = trigger && !triggerHeld;
        clickHeld = click;
        triggerHeld = trigger;
        if (!allowed) { close(); return; }
        if (clicked) {
            if (open) close();
            else openController(input);
            return; // An opening click never also sends from a simultaneous trigger.
        }
        if (!isRadial()) return;
        selected = radialSelection(pad, selected);
        if (sent && selected >= 0) choose(selected, send);
    }

    private PhysicalGamepad primaryPad(PhysicalInput input) {
        PhysicalGamepad pad = input.gamepad(controllerId);
        return pad != null ? pad : input.gamepads().isEmpty() ? null : input.gamepads().getFirst();
    }

    private void baseline(PhysicalGamepad pad) {
        boolean samePad = pad != null && pad.deviceId() == controllerId;
        clickHeld = pad != null && pad.buttonDown(PhysicalGamepad.BUTTON_RIGHT_THUMB);
        triggerHeld = pad != null && pad.axis(PhysicalGamepad.AXIS_RIGHT_TRIGGER)
                > (samePad && triggerHeld ? 0.30f : 0.55f);
        controllerId = pad == null ? -1 : pad.deviceId();
    }

    private static int radialSelection(PhysicalGamepad pad, int previous) {
        float x = pad.axis(PhysicalGamepad.AXIS_RIGHT_X);
        float y = pad.axis(PhysicalGamepad.AXIS_RIGHT_Y);
        if (Math.hypot(x, y) <= (previous >= 0 ? 0.30f : 0.55f)) return -1;
        double angle = Math.atan2(x, -y);
        double sector = Math.PI / 4;
        if (previous >= 0 && Math.abs(Math.IEEEremainder(angle - previous * sector, Math.PI * 2))
                <= sector / 2 + Math.toRadians(6)) return previous;
        return Math.floorMod((int) Math.floor(angle / sector + 0.5), QuickChatPhrase.values().length);
    }

    public String raceHint(String keyboardToggle) {
        return keyboardToggle + (controllerId == -1 ? "" : " / RS CLICK") + " QUICK CHAT";
    }

    /** The lobby list accepts menu directions/confirm; either presentation accepts digits and Back. */
    public void update(ViewInput input, Consumer<QuickChatPhrase> send) {
        if (!open) return;
        if (input.back()) { updateController(input.physicalInput(), false, send); return; }
        if (number(input::keyPressed, send)) return;
        updateController(input.physicalInput(), true, send);
        if (!open || isRadial()) return;
        if (input.up()) selected = Math.floorMod(selected - 1, QuickChatPhrase.values().length);
        if (input.down()) selected = (selected + 1) % QuickChatPhrase.values().length;
        if (input.accept()) choose(selected, send);
    }

    /** Returns whether a number edge selected a phrase, keeping vote/retry handling separate. */
    public boolean number(java.util.function.IntPredicate pressed, Consumer<QuickChatPhrase> send) {
        if (!open) return false;
        for (int i = 0; i < QuickChatPhrase.values().length; i++) {
            if (pressed.test(SceneKeys.DIGIT_1 + i)) {
                choose(i, send);
                return true;
            }
        }
        return false;
    }

    private void choose(int index, Consumer<QuickChatPhrase> send) {
        send.accept(QuickChatPhrase.values()[index]);
        close();
    }

    /** Title and help rows sit above the numbered catalogue rows. */
    private static final int HEADER_LINES = 2;

    /** Measured name-font rows preserve catalogue punctuation and leave gameplay visible. */
    public HudTextLayout.Panel layout(int width, int height, String zoneName, String closeLabel) {
        return layoutWithHelp(width, height, zoneName, "QUICK CHAT",
                closeLabel.isEmpty() ? "1-8 SEND" : "1-8 SEND  " + closeLabel + " CLOSE");
    }

    HudTextLayout.Panel raceLayout(int width, int height, String zoneName, String keyboardToggle) {
        return layout(width, height, zoneName, keyboardToggle);
    }

    private HudTextLayout.Panel layoutWithHelp(int width, int height, String zoneName, String title, String help) {
        List<HudTextLayout.Line> lines = new ArrayList<>();
        lines.add(new HudTextLayout.Line(title, HudTextLayout.ACCENT));
        lines.add(new HudTextLayout.Line(help, HudTextLayout.MUTED));
        QuickChatPhrase[] phrases = QuickChatPhrase.values();
        for (int i = 0; i < phrases.length; i++) {
            int key = selected == i ? HudTextLayout.GOLD : HudTextLayout.ACCENT;
            lines.add(HudTextLayout.Line.named((i + 1) + " ",
                    EnglishQuickChat.text(phrases[i], zoneName), HudTextLayout.WHITE, "", key));
        }
        int panelHeight = 4 + lines.stream().mapToInt(HudTextLayout.Line::height).sum();
        return HudTextLayout.panelAt(4, Math.max(4, (height - panelHeight) / 2), width - 8,
                lines, HudTextLayout.ACCENT);
    }

    /** The focused row swaps the cyan edge for gold beside its key number, clear of the wording. */
    public void draw(PixelCanvas canvas, String zoneName, String closeLabel) {
        if (isRadial()) {
            radialMenu.draw(canvas, selected < 0 ? null : QuickChatPhrase.values()[selected], zoneName, true, closeLabel);
            return;
        }
        draw(canvas, layout(canvas.width(), canvas.height(), zoneName, closeLabel));
    }

    public void drawRace(PixelCanvas canvas, String zoneName, String keyboardToggle) {
        if (isRadial()) {
            radialMenu.draw(canvas, selected < 0 ? null : QuickChatPhrase.values()[selected], zoneName, false, "");
            return;
        }
        draw(canvas, raceLayout(canvas.width(), canvas.height(), zoneName, keyboardToggle));
    }

    private void draw(PixelCanvas canvas, HudTextLayout.Panel panel) {
        HudTextLayout.drawPanel(canvas, panel);
        int rowTop = panel.y() + 2 + HEADER_LINES * HudTextLayout.LINE_HEIGHT
                + selected * HudTextLayout.NAME_LINE_HEIGHT;
        canvas.fill(panel.x(), rowTop, 2, HudTextLayout.NAME_LINE_HEIGHT, HudTextLayout.GOLD);
        canvas.fill(panel.x() + 3, rowTop + 4, 1, 5, HudTextLayout.GOLD);
    }
}
