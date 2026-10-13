package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.Ability

/** The pause menu button that opens the ad power-ups. */
const val BOOST_MENU_BUTTON = "POWER-UP (WATCH AD)"

/** The pause menu's heading over the ad power-ups. */
const val BOOST_HEADING = "WATCH AN AD FOR A POWER-UP"

/** Under the heading: an ad power-up lasts this game and never joins the wager inventory. */
const val BOOST_TERMS = "THIS GAME ONLY. CAN'T BE STAKED."

/** Shown when the last ad didn't pay out. */
const val BOOST_AD_MISSED = "NO AD RIGHT NOW. TRY AGAIN SOON."

/** A pause menu button: "AD: BIG BALL". */
fun boostButtonText(ability: Ability): String = "AD: ${abilityName(ability.id)}"
