package com.benmorrisrains.breakthebricks.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.benmorrisrains.breakthebricks.engine.Abilities
import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.AbilityInventory
import com.benmorrisrains.breakthebricks.engine.Wagering
import com.benmorrisrains.breakthebricks.platform.KeyValueStore

/**
 * The saved ability inventory and whether the free first game has been played. Every change is
 * written to [store] at once, so the inventory survives restarts and a quit mid-game can't
 * bring back a stake.
 */
class AbilityBank(private val store: KeyValueStore, val abilities: List<Ability> = Abilities.earnable) {
    var inventory: AbilityInventory by mutableStateOf(load())
        private set

    var firstGamePlayed: Boolean by mutableStateOf(store.getLong(FIRST_GAME_KEY, 0L) != 0L)
        private set

    /** True when the next free-play game must stake one of [stakeable]. */
    val stakeRequired: Boolean get() = Wagering.stakeRequired(firstGamePlayed, inventory)

    val stakeable: List<Ability> get() = Wagering.stakeable(inventory, abilities)

    fun save(inventory: AbilityInventory) {
        if (inventory == this.inventory) return
        abilities.forEach { store.putLong(countKey(it.id), inventory.count(it.id).toLong()) }
        this.inventory = inventory
    }

    fun markFirstGamePlayed() {
        if (firstGamePlayed) return
        store.putLong(FIRST_GAME_KEY, 1L)
        firstGamePlayed = true
    }

    private fun load(): AbilityInventory = abilities.fold(AbilityInventory()) { inventory, ability ->
        inventory.add(ability.id, store.getLong(countKey(ability.id), 0L).toInt())
    }

    companion object {
        const val FIRST_GAME_KEY = "wager_first_game_played"
        fun countKey(id: String) = "inventory_$id"
    }
}
