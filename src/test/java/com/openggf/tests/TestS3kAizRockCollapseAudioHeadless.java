package com.openggf.tests;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.objects.AizLrzRockObjectInstance;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kAizRockCollapseAudioHeadless {
    private static final int HUD_X = 6517;
    private static final int HUD_Y = 933;

    private final AudioManager audio = AudioManager.getInstance();

    @AfterEach
    void tearDown() {
        audio.setChipWriteObserver(null);
        audio.resetState();
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void tailsAirRollBreaksAizRockAndPresentsTheCollapseTail() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        audio.resetState();

        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)
                .build();
        Tails tails = assertInstanceOf(Tails.class, fixture.sprite());

        // The debug HUD reports top-left coordinates, not the ROM centre.
        tails.setX((short) HUD_X);
        tails.setY((short) HUD_Y);
        tails.setXSpeed((short) 0);
        tails.setYSpeed((short) 0);
        tails.setGSpeed((short) 0);
        tails.setAir(true);
        tails.setRolling(true);
        tails.setRollingJump(true);
        tails.setAnimationId(Sonic3kAnimationIds.ROLL.id());
        tails.setJumping(false);
        tails.setControlLocked(false);
        tails.setObjectControlled(false);
        tails.setObjectMappingFrameControl(false);
        fixture.camera().updatePosition(true);
        tails.updateSensors(tails.getX(), tails.getY());
        GameServices.level().getObjectManager().reset(fixture.camera().getX());

        audio.stopMusic();
        int[] presentedFrame = { -1 };
        List<YmKeyWrite> keyWrites = new ArrayList<>();
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                if (port == 0 && register == 0x28
                        && presentedFrame[0] >= 0) {
                    keyWrites.add(new YmKeyWrite(presentedFrame[0], value));
                }
            }

            @Override
            public void onPsgWrite(int value) {
            }
        });
        try (LiveCaptureAudioHandle capture =
                     AudioManagerTestDiagnostics.attachPresentationCapture(
                             audio, audio.presentationFrameRate())) {
            short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
            List<Long> packetEnergy = new ArrayList<>();
            int breakFrame = -1;
            int springFrame = -1;

            for (int frame = 0; frame < 180; frame++) {
                presentedFrame[0] = frame;
                fixture.stepFrame(false, false, false, false, false);
                int stereoFrames = capture.drainPresentationFrame(packet);
                packetEnergy.add(sumAbsolute(packet, stereoFrames * 2));

                boolean rockPresent = GameServices.level().getObjectManager()
                        .getActiveObjects().stream()
                        .filter(AizLrzRockObjectInstance.class::isInstance)
                        .map(AizLrzRockObjectInstance.class::cast)
                        .anyMatch(rock -> rock.getSpawn() != null
                                && rock.getSpawn().x() == 0x1980
                                && rock.getSpawn().y() == 0x0424);
                if (!rockPresent && breakFrame < 0) {
                    breakFrame = frame;
                }
                if (breakFrame >= 0 && tails.getYSpeed() <= -0x900
                        && springFrame < 0) {
                    springFrame = frame;
                }
            }

            assertTrue(breakFrame >= 0,
                    "Tails should break the ROM AIZ rock from the supplied air-roll position");
            assertTrue(springFrame > breakFrame,
                    "Tails should later hit the spring below the broken rock");

            int tailEnd = Math.min(packetEnergy.size(), breakFrame + 28);
            List<Long> collapseWindow = packetEnergy.subList(breakFrame, tailEnd);
            assertFalse(collapseWindow.isEmpty());
            List<Integer> collapseKeyOffFrames = terminalKeyOffFrames(
                    keyWrites, breakFrame, springFrame);
            assertTrue(collapseKeyOffFrames.equals(List.of(
                            breakFrame + 18, breakFrame + 19,
                            breakFrame + 20)),
                    "the real AIZ request must preserve the three staggered "
                            + "cfStopTrack key-offs: breakFrame=" + breakFrame
                            + " keyOffFrames=" + collapseKeyOffFrames);
            for (int frame = breakFrame + 21; frame <= breakFrame + 24;
                    frame++) {
                assertTrue(packetEnergy.get(frame) > 0,
                        "the final presentation PCM must retain the audible "
                                + "post-key-off Collapse decay at frame "
                                + frame + ": " + collapseWindow);
            }
        }
    }

    private static long sumAbsolute(short[] samples, int length) {
        long sum = 0;
        for (int i = 0; i < length; i++) {
            sum += Math.abs((int) samples[i]);
        }
        return sum;
    }

    private static List<Integer> terminalKeyOffFrames(
            List<YmKeyWrite> writes, int breakFrame, int springFrame) {
        boolean[] keyed = new boolean[6];
        List<Integer> terminals = new ArrayList<>();
        for (YmKeyWrite write : writes) {
            if (write.frame < breakFrame || write.frame >= springFrame) {
                continue;
            }
            int encodedChannel = write.value & 7;
            int channel = encodedChannel >= 4
                    ? encodedChannel - 1 : encodedChannel;
            if (channel < 0 || channel >= keyed.length) {
                continue;
            }
            boolean nextKeyed = (write.value & 0xF0) != 0;
            if (!nextKeyed && keyed[channel]
                    && (write.value == 2 || write.value == 4
                    || write.value == 5)) {
                terminals.add(write.frame);
            }
            keyed[channel] = nextKeyed;
        }
        return terminals;
    }

    private record YmKeyWrite(int frame, int value) {
    }
}
