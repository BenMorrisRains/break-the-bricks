package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.WagerOutcome

/** The name players see for an ability: "GRENADE" for the grenade ball. */
fun abilityName(ability: Ability): String = ability.id.uppercase()

/** A wager screen card: "GRENADE x3". */
fun stakeCardText(ability: Ability, copies: Int): String = "${abilityName(ability)} x$copies"

/** What staking [ability] asks of the player: "REACH ROUND 10 TO KEEP IT, +1". */
fun stakeGoalText(ability: Ability): String = "REACH ROUND ${ability.wagerTargetRound} TO KEEP IT, +1"

/** The pause menu's reminder while a wager is open, or null when nothing is at stake. */
fun quitWarning(stake: Ability?, outcome: WagerOutcome): String? =
    if (stake != null && outcome == WagerOutcome.Open) "QUITTING LOSES YOUR ${abilityName(stake)}" else null

/** One line about the wager on the results screen; [won] picks its color. */
data class WagerLine(val text: String, val won: Boolean)

/** The wager lines for the results screen: kept or lost, then anything banked. Empty when nothing changed. */
fun wagerLines(result: GameResult): List<WagerLine> {
    val lines = mutableListOf<WagerLine>()
    val stake = result.stake
    if (stake != null) {
        val name = abilityName(stake)
        val left = "$name x${result.stakeCopiesLeft}"
        when (result.wagerOutcome) {
            WagerOutcome.Won -> lines += WagerLine("$name KEPT +1  ($left)", won = true)
            WagerOutcome.Lost, WagerOutcome.Open -> lines += WagerLine("$name LOST  ($left)", won = false)
            WagerOutcome.Unstaked -> Unit
        }
    }
    result.banked.forEach { lines += WagerLine("${abilityName(it)} BANKED +1", won = true) }
    return lines
}
