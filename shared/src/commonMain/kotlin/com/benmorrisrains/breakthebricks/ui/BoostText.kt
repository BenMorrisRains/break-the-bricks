package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.GameEngine

/** The pause menu button that opens the ad power-ups. */
const val BOOST_MENU_BUTTON = "POWER-UP (WATCH AD)"

/** The same button for an owner of the one-time unlock, who gets it without the ad. */
const val BOOST_MENU_BUTTON_FREE = "POWER-UP (FREE)"

/** The pause menu's heading over the ad power-ups. */
const val BOOST_HEADING = "WATCH AN AD FOR A POWER-UP"

/** The heading for an owner of the one-time unlock. */
const val BOOST_HEADING_FREE = "PICK A FREE POWER-UP"

/** Under the heading: an ad power-up lasts this game and never joins the wager inventory. */
const val BOOST_TERMS = "THIS GAME ONLY. CAN'T BE STAKED."

/** The same in a level, where every power-up acts on one shot only. */
const val BOOST_TERMS_LEVEL = "ONE SHOT, THIS LEVEL ONLY. CAN'T BE STAKED."

/** The terms for [engine]'s game. */
fun boostTerms(engine: GameEngine): String = if (engine.singleUsePowerUps) BOOST_TERMS_LEVEL else BOOST_TERMS

/** Shown when the last ad didn't pay out. */
const val BOOST_AD_MISSED = "NO AD RIGHT NOW. TRY AGAIN SOON."

/** A pause menu button: "AD: BIG BALL", or "FREE: BIG BALL" for an owner of the unlock. */
fun boostButtonText(ability: Ability, adFree: Boolean = false): String =
    "${if (adFree) "FREE" else "AD"}: ${abilityName(ability.id)}"
