package com.benmorrisrains.breakthebricks.engine

/** Things the UI reacts to with sound, haptics and effects. Collected each step, drained by the UI. */
sealed interface GameEvent {
    data class BrickHit(val column: Int, val row: Int, val hitsLeft: Long) : GameEvent
    data class BrickBroken(val column: Int, val row: Int, val points: Long) : GameEvent
    data object WallBounce : GameEvent
    data class BallLanded(val x: Float, val isFirst: Boolean) : GameEvent
    data class RoundStarted(val round: Int, val ballCount: Int) : GameEvent
    data class AbilityTriggered(val abilityId: String, val column: Int, val row: Int) : GameEvent
    data class AbilityEarned(val abilityId: String, val round: Int) : GameEvent
    /**
     * A shot was fired with a copy of a limited power-up, which is now used up. [boosted] when
     * that copy came from [GameEngine.boost] (an ad), so it was never the player's to bank.
     */
    data class PowerUpUsed(val abilityId: String, val boosted: Boolean = false) : GameEvent
    data class GameOver(val round: Int, val points: Long) : GameEvent
    /** Every brick of a level was broken; [round] is the round that cleared the board. */
    data class LevelCompleted(val round: Int, val points: Long) : GameEvent
}
