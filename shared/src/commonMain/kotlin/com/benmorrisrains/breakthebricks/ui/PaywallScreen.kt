package com.benmorrisrains.breakthebricks.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benmorrisrains.breakthebricks.engine.Level

/**
 * Before a paid level that isn't open yet: the one-time unlock with its price, an ad to open
 * just this level, and restore for a purchase made before. The ad button says it is an ad, and
 * the game stays playable without it (store rules for rewarded ads).
 */
@Composable
fun PaywallScreen(
    level: Level,
    unlock: FullUnlock,
    access: LevelAccess,
    onBuy: () -> Unit,
    onWatchAd: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MenuColumn(modifier) {
        MenuTitle("LEVEL ${level.number}", NeonPalette.Yellow)
        MenuLabel(level.name.uppercase(), NeonPalette.Ink)
        MenuLabel("LEVELS 1-${LevelAccess.FREE_LEVELS} ARE FREE. THIS ONE IS PAID.", NeonPalette.Cyan, 12.sp)
        Spacer(Modifier.height(8.dp))
        NeonButton(unlockButtonText(unlock.price), { if (!unlock.busy) onBuy() }, color = NeonPalette.Lime)
        MenuLabel(UNLOCK_TERMS, NeonPalette.Lime, 12.sp)
        NeonButton(levelAdButtonText(level.number), { if (!access.adShowing) onWatchAd() }, color = NeonPalette.Yellow)
        if (access.adMissed) MenuLabel(LEVEL_AD_MISSED, NeonPalette.Orange, 12.sp)
        unlock.notice?.let { MenuLabel(it, NeonPalette.Orange, 12.sp) }
        NeonButton("RESTORE PURCHASE", { if (!unlock.busy) onRestore() }, color = NeonPalette.Purple)
        Spacer(Modifier.height(8.dp))
        NeonButton("BACK", onBack, color = NeonPalette.Cyan)
    }
}
