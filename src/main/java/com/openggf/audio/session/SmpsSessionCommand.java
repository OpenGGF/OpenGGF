package com.openggf.audio.session;

import java.util.Objects;

public sealed interface SmpsSessionCommand {
    record AdmitSfx(PreparedSmpsSfxProgram program)
            implements SmpsSessionCommand {
        public AdmitSfx {
            Objects.requireNonNull(program, "program");
        }
    }

    record StopMusic() implements SmpsSessionCommand {
    }

    record StopAllSfx() implements SmpsSessionCommand {
    }

    record PushOverride(PreparedSmpsMusicActivation activation)
            implements SmpsSessionCommand {
        public PushOverride {
            Objects.requireNonNull(activation, "activation");
        }
    }

    record RestoreOverride() implements SmpsSessionCommand {
    }

    record EndOverride(int musicId) implements SmpsSessionCommand {
    }

    record FadeMusic(int steps, int delay) implements SmpsSessionCommand {
    }

    record SetSpeedMultiplier(int multiplier) implements SmpsSessionCommand {
    }

    record SetSpeedShoes(boolean enabled) implements SmpsSessionCommand {
    }

    record ChangeMusicTempo(int dividingTiming) implements SmpsSessionCommand {
    }

    record ResetRingAlternation(boolean ringLeft)
            implements SmpsSessionCommand {
    }

    record HardReset() implements SmpsSessionCommand {
    }
}
