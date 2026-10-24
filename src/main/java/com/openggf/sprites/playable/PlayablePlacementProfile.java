package com.openggf.sprites.playable;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.*;
import com.openggf.game.session.EngineServices;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.*;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;

/** Immutable stock standing geometry, captured once through the native donor factory without ticks. */
public record PlayablePlacementProfile(Offset leftFoot, Offset rightFoot, Offset leftHead, Offset rightHead,
        int xRadius, int yRadius, int topSolidBit, int lrbSolidBit, int layoutYMask) {
    public record Offset(short x, short y) {}

    public static PlayablePlacementProfile capture(GameModule donor, LevelManager level, String character, int path) {
        if (path < 0 || path > 1) throw new IllegalArgumentException("Player path is 0 or 1");
        var key = CharacterKey.parsePersisted(character);
        var definition = donor.getPlayableCharacterRegistry().find(key).orElseThrow(() -> new IllegalArgumentException("Unknown player donor"));
        // The shared registry contains all stock bodies; native donor availability is narrower.
        // A constructible S1 Tails body, for example, is not an S1-supported performer.
        var capabilities = donor.getDonorCapabilities();
        if (!key.isBuiltin() || capabilities == null || !capabilities.getPlayableCharacters().contains(definition.behavesLike()))
            throw new IllegalArgumentException("Native donor does not provide character " + character);
        var services = EngineServices.current();
        var audio = AudioManager.createSilent();
        try {
            var context = new PlayableRuntimeContext(donor, new Camera(services.configuration()), level,
                    new CollisionSystem(new TerrainCollisionManager()), new WaterSystem(),
                    new SpriteManager(services.configuration()), new GameStateManager(), new TimerManager(), audio);
            var actor = context.construct(() -> CharacterConstructionScope.call(key,
                    () -> CharacterConstructionScope.validateFactoryResult(key, definition.spriteFactory().create(character,0,0))));
            actor.setTopSolidBit((byte) (path == 0 ? 12 : 14));
            actor.setLrbSolidBit((byte) (path == 0 ? 13 : 15));
            if (actor.getTopSolidBit() != (path == 0 ? 12 : 14) || actor.getLrbSolidBit() != (path == 0 ? 13 : 15))
                throw new IllegalArgumentException("Donor does not support this player collision path");
            var feet = actor.getGroundSensors(); var head = actor.getCeilingSensors();
            return new PlayablePlacementProfile(offset(feet[0]),offset(feet[1]),offset(head[0]),offset(head[1]),
                    actor.getXRadius(),actor.getYRadius(),actor.getTopSolidBit(),actor.getLrbSolidBit(),
                    donor.getRules().collision().defaultCollisionLayoutYMask());
        } finally { audio.getBackend().destroy(); }
    }
    private static Offset offset(Sensor sensor) { return new Offset(sensor.getX(),sensor.getY()); }
}
