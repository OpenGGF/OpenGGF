package com.openggf.game.rules;

import com.openggf.game.CollisionModel;

public record GameRules(
        PlayerMovementRules playerMovement,
        PlayerCapabilityRules playerCapability,
        CollisionRules collision,
        PlayerAnimationRules playerAnimation,
        CameraRules camera,
        RingRules ring,
        ObjectInteractionRules objectInteraction,
        SidekickCpuRules sidekickCpu,
        PowerUpRules powerUp,
        DrowningBubbleRules drowningBubble) {

    public static final GameRules SONIC_1 = new GameRules(
            new PlayerMovementRules(
                    true,
                    true,
                    false,
                    (short) 0,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    false,
                    true,
                    false,
                    false,
                    false,
                    false,
                    true,
                    false
            ),
            new PlayerCapabilityRules(
                    false,
                    null,
                    false,
                    false,
                    false,
                    false,
                    false,
                    null
            ),
            new CollisionRules(
                    CollisionModel.UNIFIED,
                    true,
                    false,
                    false,
                    true,
                    new AirCollisionRules(false, false, false, false),
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    false,
                    true,
                    0x07FF
            ),
            new PlayerAnimationRules(
                    false,
                    false,
                    false
            ),
            new CameraRules(
                    (short) 0,
                    true,
                    16,
                    true,
                    false,
                    false
            ),
            new RingRules(
                    3,
                    0,
                    false,
                    6,
                    6,
                    true,
                    false
            ),
            new ObjectInteractionRules(
                    false,
                    true,
                    true,
                    false,
                    false,
                    false, // sidekickDespawnUsesInteractCodeWordChange (S3K-only)
                    false,
                    false,
                    true,
                    true,
                    false,
                    false,
                    false
            ),
            new SidekickCpuRules(
                    16,
                    16384,
                    0,
                    false,
                    false,
                    false,
                    true,
                    0,
                    false,
                    192,
                    300,
                    12,
                    1,
                    32,
                    1024,
                    false,
                    false,
                    false,
                    true
            ),
            new PowerUpRules(
                    0,
                    6,
                    8,
                    1,
                    false,
                    false,
                    -1,
                    -1
            ),
            new DrowningBubbleRules(
                    60,
                    0,
                    false,
                    -136
            )
    );

    public static final GameRules SONIC_2 = new GameRules(
            new PlayerMovementRules(
                    false,
                    false,
                    true,
                    (short) 0,
                    false,
                    false,
                    true,
                    false,
                    true,
                    true,
                    true,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    false,
                    false,
                    false,
                    true
            ),
            new PlayerCapabilityRules(
                    true,
                    new short[]{0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00},
                    false,
                    false,
                    false,
                    false,
                    false,
                    new short[]{0x0B00, 0x0B80, 0x0C00, 0x0C80, 0x0D00, 0x0D80, 0x0E00, 0x0E80, 0x0F00}
            ),
            new CollisionRules(
                    CollisionModel.DUAL_PATH,
                    true,
                    false,
                    true,
                    false,
                    new AirCollisionRules(false, false, false, false),
                    true,
                    true,
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    false,
                    true,
                    0x07FF
            ),
            new PlayerAnimationRules(
                    true,
                    false,
                    true
            ),
            new CameraRules(
                    (short) 120,
                    false,
                    16,
                    false,
                    false,
                    true
            ),
            new RingRules(
                    7,
                    0,
                    true,
                    6,
                    6,
                    false,
                    false
            ),
            new ObjectInteractionRules(
                    false,
                    false,
                    true,
                    true,
                    false,
                    false, // sidekickDespawnUsesInteractCodeWordChange (S3K-only)
                    true,
                    false,
                    true,
                    false,
                    false,
                    true,
                    true
            ),
            new SidekickCpuRules(
                    16,
                    16384,
                    0,
                    false,
                    false,
                    true,
                    true,
                    210,
                    false,
                    192,
                    300,
                    12,
                    1,
                    32,
                    1024,
                    false,
                    false,
                    true,
                    false
            ),
            new PowerUpRules(
                    1,
                    134,
                    136,
                    1,
                    true,
                    true,
                    132,
                    133
            ),
            new DrowningBubbleRules(
                    0,
                    8,
                    true,
                    -136
            )
    );

    public static final GameRules SONIC_3K = new GameRules(
            new PlayerMovementRules(
                    false,
                    false,
                    true,
                    (short) 256,
                    true,
                    true,
                    false,
                    true,
                    true,
                    true,
                    false,
                    true,
                    true,
                    false,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    false,
                    false
            ),
            new PlayerCapabilityRules(
                    true,
                    new short[]{0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00},
                    true,
                    true,
                    true,
                    true,
                    true,
                    new short[]{0x0B00, 0x0B80, 0x0C00, 0x0C80, 0x0D00, 0x0D80, 0x0E00, 0x0E80, 0x0F00}
            ),
            new CollisionRules(
                    CollisionModel.DUAL_PATH,
                    true,
                    true,
                    false,
                    true,
                    new AirCollisionRules(true, true, true, true),
                    false,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    false,
                    true,
                    false,
                    0x0FFF
            ),
            new PlayerAnimationRules(
                    true,
                    true,
                    true
            ),
            new CameraRules(
                    (short) 120,
                    false,
                    24,
                    false,
                    true,
                    true
            ),
            new RingRules(
                    7,
                    4,
                    true,
                    6,
                    6,
                    false,
                    true
            ),
            new ObjectInteractionRules(
                    true,
                    false,
                    false,
                    false,
                    true,
                    true, // sidekickDespawnUsesInteractCodeWordChange (S3K sub_13EFC word compare)
                    true,
                    true,
                    true,
                    false,
                    true,
                    false,
                    false
            ),
            new SidekickCpuRules(
                    48,
                    32512,
                    32,
                    true,
                    true,
                    true,
                    false,
                    128,
                    true,
                    192,
                    300,
                    12,
                    1,
                    32,
                    1024,
                    true,
                    true,
                    true,
                    true
            ),
            new PowerUpRules(
                    0,
                    100,
                    102,
                    8,
                    false,
                    true,
                    98,
                    99
            ),
            new DrowningBubbleRules(
                    60,
                    8,
                    true,
                    -256
            )
    );
}
