package com.benmorrisrains.breakthebricks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.AbilityInventory
import com.benmorrisrains.breakthebricks.platform.GameSettings
import com.benmorrisrains.breakthebricks.platform.Setting

/** The first screen: title, best points, the player's rank once it exists, Play, Levels and Settings. */
@Composable
fun HomeScreen(
    bestPoints: Long,
    rank: RankProgress?,
    onPlay: () -> Unit,
    onLevels: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MenuColumn(modifier) {
        MenuTitle("BREAK\nTHE\nBRICKS", NeonPalette.Cyan, 40.sp)
        if (bestPoints > 0L) MenuLabel("BEST ${formatPoints(bestPoints)}")
        RankPanel(rank)
        Spacer(Modifier.height(16.dp))
        NeonButton("PLAY", onPlay, color = NeonPalette.Lime)
        NeonButton("LEVELS", onLevels, color = NeonPalette.Yellow)
        NeonButton("SETTINGS", onSettings, color = NeonPalette.Purple)
    }
}

/**
 * Over the paused game. It covers the whole field and swallows touches, so nothing behind it
 * can aim or fire. With [boostOffers], it also offers one power-up for this game for watching
 * an ad; [adMissed] says the last ad didn't pay out. With [adFree] (the one-time unlock) the
 * power-up comes without the ad.
 */
@Composable
fun PauseMenu(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onSettings: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    warning: String? = null,
    boostOffers: List<Ability> = emptyList(),
    onBoost: (Ability) -> Unit = {},
    adMissed: Boolean = false,
    adFree: Boolean = false,
) {
    val blocker = remember { MutableInteractionSource() }
    MenuColumn(
        modifier
            .background(NeonPalette.Background.copy(alpha = 0.88f))
            .clickable(interactionSource = blocker, indication = null, onClick = {}),
    ) {
        // The ad offers get their own page so the menu still fits a short phone.
        var choosingBoost by remember { mutableStateOf(false) }
        if (choosingBoost && boostOffers.isNotEmpty()) {
            MenuTitle("POWER-UP", NeonPalette.Yellow)
            MenuLabel(if (adFree) BOOST_HEADING_FREE else BOOST_HEADING, NeonPalette.Ink)
            MenuLabel(BOOST_TERMS, NeonPalette.Cyan, 12.sp)
            if (adMissed) MenuLabel(BOOST_AD_MISSED, NeonPalette.Orange, 12.sp)
            for (ability in boostOffers) {
                NeonButton(boostButtonText(ability, adFree), { onBoost(ability) }, color = NeonPalette.Yellow)
            }
            NeonButton("BACK", { choosingBoost = false }, color = NeonPalette.Cyan)
        } else {
            MenuTitle("PAUSED")
            if (warning != null) MenuLabel(warning, NeonPalette.Orange)
            NeonButton("RESUME", onResume, color = NeonPalette.Lime)
            if (boostOffers.isNotEmpty()) {
                NeonButton(if (adFree) BOOST_MENU_BUTTON_FREE else BOOST_MENU_BUTTON, { choosingBoost = true }, color = NeonPalette.Yellow)
            }
            NeonButton("RESTART", onRestart, color = NeonPalette.Orange)
            NeonButton("SETTINGS", onSettings, color = NeonPalette.Purple)
            NeonButton("HOME", onHome, color = NeonPalette.Cyan)
        }
    }
}

/**
 * After a game: points, the round reached, the best, rank progress (with a banner when the game
 * took the player up a rank), Play again and Home. After
 * a level it also shows pass or fail with the stars, Next level once passed, and Levels.
 * With [showsAbilityPreviews], abilities the game didn't reach are shown off as locked, with
 * their preview clips.
 */
@Composable
fun ResultsScreen(
    result: GameResult,
    rank: RankProgress?,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    onNextLevel: (() -> Unit)? = null,
    onLevels: (() -> Unit)? = null,
    showsAbilityPreviews: Boolean = true,
) {
    val level = result.level
    MenuColumn(modifier) {
        when {
            level == null -> MenuTitle("GAME OVER")
            result.passed -> MenuTitle("LEVEL ${level.number} CLEAR", NeonPalette.Lime)
            else -> MenuTitle("LEVEL ${level.number} FAILED")
        }
        if (level != null) {
            MenuLabel(starText(result.stars), NeonPalette.Yellow, 32.sp)
            if (!result.passed) MenuLabel("BREAK EVERY BRICK TO PASS", NeonPalette.Cyan)
        }
        MenuLabel(formatPoints(result.points), NeonPalette.Ink, 44.sp)
        MenuLabel("POINTS", NeonPalette.Purple)
        if (result.isNewBest) MenuLabel("NEW BEST!", NeonPalette.Yellow, 20.sp)
        val roundText = if (result.passed) "CLEARED IN ${result.round} ROUNDS" else "ROUND ${result.round}"
        MenuLabel(roundText, NeonPalette.Cyan, 18.sp)
        for (line in wagerLines(result)) {
            MenuLabel(line.text, if (line.won) NeonPalette.Lime else NeonPalette.Orange, 16.sp)
        }
        MenuLabel("BEST ${formatPoints(result.bestPoints)}")
        result.rankUp?.let { MenuLabel("RANK UP!  RANK ${it.to}", NeonPalette.Lime, 22.sp) }
        RankPanel(rank)
        if (showsAbilityPreviews) LockedAbilities(lockedAbilities(result.round))
        Spacer(Modifier.height(16.dp))
        if (onNextLevel != null) NeonButton("NEXT LEVEL", onNextLevel, color = NeonPalette.Lime)
        NeonButton(if (level == null) "PLAY AGAIN" else "RETRY", onPlayAgain, color = if (onNextLevel == null) NeonPalette.Lime else NeonPalette.Orange)
        if (onLevels != null) NeonButton("LEVELS", onLevels, color = NeonPalette.Yellow)
        NeonButton("HOME", onHome, color = NeonPalette.Cyan)
    }
}

/**
 * Before a free-play game once the first one is played: each ability the player holds, with its
 * count and the round to reach. Tapping one stakes a copy and starts the game with it active.
 * Card animations come with the ability showcase (T11).
 */
@Composable
fun WagerScreen(
    stakeable: List<Ability>,
    inventory: AbilityInventory,
    onStake: (Ability) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MenuColumn(modifier) {
        MenuTitle("WAGER", NeonPalette.Yellow)
        MenuLabel("STAKE AN ABILITY TO PLAY", NeonPalette.Ink)
        for (ability in stakeable) {
            NeonButton(stakeCardText(ability, inventory.count(ability.id)), { onStake(ability) }, color = NeonPalette.Lime)
            MenuLabel(stakeGoalText(ability), NeonPalette.Cyan, 12.sp)
        }
        MenuLabel("LOSE AND THE STAKED COPY IS GONE")
        Spacer(Modifier.height(8.dp))
        NeonButton("BACK", onBack, color = NeonPalette.Cyan)
    }
}

/**
 * Music, sound effects and haptics toggles. Each change is saved and applied at once. With an
 * [unlock], it also offers the one-time purchase and restores an earlier one.
 */
@Composable
fun SettingsScreen(
    settings: GameSettings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    unlock: FullUnlock? = null,
    onBuy: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    MenuColumn(modifier) {
        MenuTitle("SETTINGS", NeonPalette.Purple)
        SettingToggle(settings, Setting.Music, "MUSIC")
        SettingToggle(settings, Setting.SoundEffects, "SOUND")
        SettingToggle(settings, Setting.Haptics, "VIBRATION")
        if (unlock != null) {
            Spacer(Modifier.height(8.dp))
            if (unlock.owned) {
                MenuLabel(UNLOCK_OWNED, NeonPalette.Lime)
            } else {
                NeonButton(unlockButtonText(unlock.price), { if (!unlock.busy) onBuy() }, color = NeonPalette.Lime)
                MenuLabel(UNLOCK_TERMS, NeonPalette.Lime, 12.sp)
            }
            NeonButton("RESTORE PURCHASE", { if (!unlock.busy) onRestore() }, color = NeonPalette.Yellow)
            unlock.notice?.let { MenuLabel(it, NeonPalette.Orange, 12.sp) }
        }
        Spacer(Modifier.height(16.dp))
        NeonButton("BACK", onBack, color = NeonPalette.Cyan)
    }
}

@Composable
private fun SettingToggle(settings: GameSettings, setting: Setting, label: String) {
    var on by remember(settings, setting) { mutableStateOf(settings.isOn(setting)) }
    NeonToggle(label, on, onChange = { settings.set(setting, it); on = it })
}
