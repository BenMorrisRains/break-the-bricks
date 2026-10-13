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
 * can aim or fire.
 */
@Composable
fun PauseMenu(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onSettings: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocker = remember { MutableInteractionSource() }
    MenuColumn(
        modifier
            .background(NeonPalette.Background.copy(alpha = 0.88f))
            .clickable(interactionSource = blocker, indication = null, onClick = {}),
    ) {
        MenuTitle("PAUSED")
        NeonButton("RESUME", onResume, color = NeonPalette.Lime)
        NeonButton("RESTART", onRestart, color = NeonPalette.Orange)
        NeonButton("SETTINGS", onSettings, color = NeonPalette.Purple)
        NeonButton("HOME", onHome, color = NeonPalette.Cyan)
    }
}

/**
 * After a game: points, the round reached, the best, rank progress, Play again and Home. After
 * a level it also shows pass or fail with the stars, Next level once passed, and Levels.
 * Abilities the game didn't reach are shown off as locked, with their preview clips.
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
            if (!result.passed) MenuLabel("SURVIVE ${level.goalRounds} ROUNDS TO PASS", NeonPalette.Cyan)
        }
        MenuLabel(formatPoints(result.points), NeonPalette.Ink, 44.sp)
        MenuLabel("POINTS", NeonPalette.Purple)
        if (result.isNewBest) MenuLabel("NEW BEST!", NeonPalette.Yellow, 20.sp)
        MenuLabel(roundLabel(result.round, level?.goalRounds), NeonPalette.Cyan, 18.sp)
        MenuLabel("BEST ${formatPoints(result.bestPoints)}")
        RankPanel(rank)
        LockedAbilities(lockedAbilities(result.round))
        Spacer(Modifier.height(16.dp))
        if (onNextLevel != null) NeonButton("NEXT LEVEL", onNextLevel, color = NeonPalette.Lime)
        NeonButton(if (level == null) "PLAY AGAIN" else "RETRY", onPlayAgain, color = if (onNextLevel == null) NeonPalette.Lime else NeonPalette.Orange)
        if (onLevels != null) NeonButton("LEVELS", onLevels, color = NeonPalette.Yellow)
        NeonButton("HOME", onHome, color = NeonPalette.Cyan)
    }
}

/** Music, sound effects and haptics toggles. Each change is saved and applied at once. */
@Composable
fun SettingsScreen(settings: GameSettings, onBack: () -> Unit, modifier: Modifier = Modifier) {
    MenuColumn(modifier) {
        MenuTitle("SETTINGS", NeonPalette.Purple)
        SettingToggle(settings, Setting.Music, "MUSIC")
        SettingToggle(settings, Setting.SoundEffects, "SOUND")
        SettingToggle(settings, Setting.Haptics, "VIBRATION")
        Spacer(Modifier.height(16.dp))
        NeonButton("BACK", onBack, color = NeonPalette.Cyan)
    }
}

@Composable
private fun SettingToggle(settings: GameSettings, setting: Setting, label: String) {
    var on by remember(settings, setting) { mutableStateOf(settings.isOn(setting)) }
    NeonToggle(label, on, onChange = { settings.set(setting, it); on = it })
}
