package com.openggf.sprites.playable;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameModule;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.LevelState;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.timer.TimerManager;
import com.openggf.sprites.managers.SpriteManager;

final class PlayableSpriteRuntimeServices {
        private PlayableSpriteRuntimeServices() {
        }

        private static NativeCompetitionRoster competitionRoster(AbstractPlayableSprite sprite) {
                LevelManager owner = levelOrNull(sprite);
                return owner != null ? NativeCompetitionRoster.from(owner.getWorldSession()) : null;
        }
        static Camera camera(AbstractPlayableSprite sprite) {
                NativeCompetitionRoster roster = competitionRoster(sprite);
                int slot = roster != null ? roster.slotOf(sprite) : -1;
                return slot >= 0 ? roster.camera(slot)
                                : sprite.runtimeServices != null ? sprite.runtimeServices.camera : GameServices.camera();
        }
        static LevelManager level(AbstractPlayableSprite sprite) { return sprite.runtimeServices != null ? sprite.runtimeServices.level : GameServices.level(); }
        static LevelManager levelOrNull(AbstractPlayableSprite sprite) { return sprite.runtimeServices != null ? sprite.runtimeServices.level : GameServices.levelOrNull(); }
        static GameModule currentOrBootstrapGameModule(AbstractPlayableSprite sprite) { return sprite.runtimeServices != null ? sprite.runtimeServices.module : GameServices.currentOrBootstrapGameModule(); }
        static CrossGameFeatureProvider crossGameFeatures(AbstractPlayableSprite sprite) { return GameServices.crossGameFeatures(); }
        /**
         * Level events belong to the active world session — nothing outside one
         * can have a boss fight running — so this deliberately does not fall
         * back to the bootstrap module.
         */
        static LevelEventProvider levelEventsOrNull(AbstractPlayableSprite sprite) {
                if (sprite.runtimeServices != null) {
                        var world = sprite.runtimeServices.world;
                        return world == null ? null : world.getWorldSession().getGameModule().getLevelEventProvider();
                }
                WorldSession world = SessionManager.getCurrentWorldSession();
                GameModule module = world != null ? world.getGameModule() : null;
                return module != null ? module.getLevelEventProvider() : null;
        }
        static boolean bossOwnsMusic(AbstractPlayableSprite sprite) {
                return levelEventsOrNull(sprite) instanceof com.openggf.game.AbstractLevelEventManager events
                                && events.isBossActive();
        }
        static LevelState levelState(LevelManager levelManager) {
                return levelManager != null ? levelManager.getLevelGamestate() : null;
        }
        static LevelState levelState(AbstractPlayableSprite sprite) {
                NativeCompetitionRoster roster = competitionRoster(sprite);
                int slot = roster != null ? roster.slotOf(sprite) : -1;
                return slot >= 0 ? roster.levelState(slot) : levelState(levelOrNull(sprite));
        }
        static int resolveAnimationId(GameModule module, CanonicalAnimation animation) {
                return module != null ? module.resolveAnimationId(animation) : -1;
        }
        static TimerManager timers(AbstractPlayableSprite sprite) { return sprite.runtimeServices != null ? sprite.runtimeServices.timers : GameServices.timers(); }
        static GameStateManager gameState(AbstractPlayableSprite sprite) {
                NativeCompetitionRoster roster = competitionRoster(sprite);
                int slot = roster != null ? roster.slotOf(sprite) : -1;
                return slot >= 0 ? roster.gameState(slot)
                                : sprite.runtimeServices != null ? sprite.runtimeServices.gameState : GameServices.gameState();
        }
        static GameStateManager gameStateOrNull(AbstractPlayableSprite sprite) {
                NativeCompetitionRoster roster = competitionRoster(sprite);
                int slot = roster != null ? roster.slotOf(sprite) : -1;
                return slot >= 0 ? roster.gameState(slot)
                                : sprite.runtimeServices != null ? sprite.runtimeServices.gameState : GameServices.gameStateOrNull();
        }
        static CollisionSystem collision(AbstractPlayableSprite sprite) { return sprite.runtimeServices != null ? sprite.runtimeServices.collision : GameServices.collision(); }
        static CollisionSystem collisionOrNull(AbstractPlayableSprite sprite) { return sprite.runtimeServices != null ? sprite.runtimeServices.collision : GameServices.collisionOrNull(); }
        static AudioManager audio(AbstractPlayableSprite sprite) { return sprite.runtimeServices != null ? sprite.runtimeServices.audio : GameServices.audio(); }
        static GameRng rng(AbstractPlayableSprite sprite) { return sprite.runtimeServices != null ? sprite.runtimeServices.rng : GameServices.rng(); }
        static GameRng rngOrNull(AbstractPlayableSprite sprite) { return sprite.runtimeServices != null ? sprite.runtimeServices.rng : GameServices.rngOrNull(); }
        static WaterSystem water(AbstractPlayableSprite sprite) { return sprite.runtimeServices != null ? sprite.runtimeServices.water : GameServices.water(); }
        static SpriteManager spritesOrNull(AbstractPlayableSprite sprite) { return sprite.runtimeServices != null ? sprite.runtimeServices.sprites : GameServices.spritesOrNull(); }
        static int gameplayFrameCounter(AbstractPlayableSprite sprite) {
                SpriteManager sprites = spritesOrNull(sprite);
                return sprites != null ? sprites.getFrameCounter() : 0;
        }
}
