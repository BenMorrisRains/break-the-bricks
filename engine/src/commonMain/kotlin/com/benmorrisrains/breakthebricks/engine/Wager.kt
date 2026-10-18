package com.benmorrisrains.breakthebricks.engine

/**
 * The player's banked abilities: how many copies of each, by [Ability.id]. Copies never expire
 * and an ability holds at most [MAX_COPIES] (Ben, 2026-10-12). Abilities are earned in play,
 * never sold.
 */
data class AbilityInventory(val copies: Map<String, Int> = emptyMap()) {
    fun count(id: String): Int = copies[id] ?: 0

    val isEmpty: Boolean get() = copies.values.none { it > 0 }

    /** Adds [count] copies, stopping at [MAX_COPIES]. */
    fun add(id: String, count: Int = 1): AbilityInventory = withCount(id, count(id) + count)

    /** Takes one copy out; a missing ability stays at zero. */
    fun remove(id: String): AbilityInventory = withCount(id, count(id) - 1)

    private fun withCount(id: String, count: Int): AbilityInventory {
        val clamped = count.coerceIn(0, MAX_COPIES)
        return AbilityInventory(if (clamped == 0) copies - id else copies + (id to clamped))
    }

    companion object {
        const val MAX_COPIES = 9
    }
}

/** How a game's wager stands. */
enum class WagerOutcome {
    /** No stake: the first game, an empty inventory, or a level. */
    Unstaked,
    /** Staked and the target round not reached yet. */
    Open,
    /** The target round was reached: the stake came back with 1 more copy. */
    Won,
    /** The game ended or was quit before the target round: the stake is gone. */
    Lost,
}

/**
 * One game's wager, settled against the inventory as the game's events arrive. Ben's rules:
 * - [start] takes the staked copy out the moment the game starts, so quitting can't dodge a loss.
 * - Reaching the stake's [Ability.wagerTargetRound] wins it at once: the stake back plus 1 copy.
 * - Ending or quitting before that loses it; the copy taken at the start stays gone.
 * - Every ability earned in the game banks 1 copy straight away, the staked one too: the staked
 *   copy sits out the game, so the earned one is the player's to use (Ben, 2026-10-17: power-ups
 *   are limited, so a stake no longer plays for free all game).
 * - A copy used on a shot leaves the inventory (the app does that on [GameEvent.PowerUpUsed]).
 *
 * Pure and deterministic: the same events always settle the same way.
 */
class WagerGame(val stake: Ability? = null) {
    var outcome: WagerOutcome = if (stake == null) WagerOutcome.Unstaked else WagerOutcome.Open
        private set

    /** Ability ids banked by earning during this game, in order. */
    val banked: List<String> get() = bankedIds
    private val bankedIds = mutableListOf<String>()

    /** Takes the staked copy out of [inventory]. Call once, as the game starts. */
    fun start(inventory: AbilityInventory): AbilityInventory {
        val stake = stake ?: return inventory
        require(inventory.count(stake.id) > 0) { "No ${stake.id} copy to stake" }
        return inventory.remove(stake.id)
    }

    /** Applies one engine event and returns the inventory after it. */
    fun onEvent(event: GameEvent, inventory: AbilityInventory): AbilityInventory = when (event) {
        is GameEvent.AbilityEarned -> {
            bankedIds += event.abilityId
            inventory.add(event.abilityId)
        }
        is GameEvent.RoundStarted -> {
            val stake = stake
            if (outcome == WagerOutcome.Open && stake != null && event.round >= stake.wagerTargetRound) {
                outcome = WagerOutcome.Won
                inventory.add(stake.id, 2)
            } else {
                inventory
            }
        }
        is GameEvent.GameOver, is GameEvent.LevelCompleted -> {
            abandon()
            inventory
        }
        else -> inventory
    }

    /** The game was quit or restarted before it ended; an open wager is lost. */
    fun abandon() {
        if (outcome == WagerOutcome.Open) outcome = WagerOutcome.Lost
    }
}

/** When a game must be staked, and with what. */
object Wagering {
    /**
     * True when the next free-play game must stake a copy: every game after the first, as long
     * as the inventory holds something. An empty inventory plays a free, unstaked game.
     */
    fun stakeRequired(firstGamePlayed: Boolean, inventory: AbilityInventory): Boolean =
        firstGamePlayed && !inventory.isEmpty

    /** The abilities the player holds at least one copy of, in [abilities] order. */
    fun stakeable(inventory: AbilityInventory, abilities: List<Ability> = Abilities.earnable): List<Ability> =
        abilities.filter { inventory.count(it.id) > 0 }
}
