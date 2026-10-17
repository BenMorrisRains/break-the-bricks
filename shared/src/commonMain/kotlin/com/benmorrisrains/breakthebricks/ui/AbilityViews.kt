package com.benmorrisrains.breakthebricks.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.GameEvent

/** Width of one icon slot in the HUD tray; big enough to tap when picking an ability. */
private val TraySlot = 36.dp

/**
 * The ability showcase for one game: canvas effects, the earning card and the HUD tray's pulses,
 * all fed from the same drained events. [GameScreen] holds one per engine.
 */
class AbilityShowcase {
    val effects = CanvasEffects()
    val cards = EarnedCards()
    val pulses = AbilityPulses()

    /** Where the tray sits on screen, in root pixels, so a card can drop into its slot. */
    internal var trayLeft = 0f
    internal var trayCenterY = 0f
    internal var traySlotPx = 0f

    /** Center of the earning card's overlay, in root pixels. */
    internal var cardCenter = Offset.Zero

    /** True while an earning card holds the game still. */
    val holdsPlay: Boolean get() = cards.holdsPlay

    fun update(elapsedSeconds: Float, events: List<GameEvent>, engine: GameEngine) {
        val roundValue = engine.config.rowValues.valueFor(engine.round.coerceAtLeast(1))
        effects.update(elapsedSeconds, events, roundValue)
        cards.update(elapsedSeconds, events)
        pulses.update(elapsedSeconds, events)
    }

    /** Center of the tray slot [abilityId] lands in, in root pixels. */
    internal fun slotCenter(engine: GameEngine, abilityId: String): Offset {
        val index = trayIds(engine).indexOf(abilityId).coerceAtLeast(0)
        return Offset(trayLeft + traySlotPx * (index + 0.5f), trayCenterY)
    }
}

/**
 * The tray's slots: one per active ability. An earned ability is active as soon as its card
 * appears, so its slot is already there, empty until the card lands in it.
 */
internal fun trayIds(engine: GameEngine): List<String> = engine.activeAbilities.map { it.id }.distinct()

/**
 * Whether the tray shows a pick: with more than one ability active, or in a level (where each
 * power-up has one shot and none is picked until tapped), the one the next shot uses is ringed
 * and the player can tap another while aiming.
 */
internal fun trayPicks(engine: GameEngine): Boolean = engine.singleUsePowerUps || trayIds(engine).size > 1

/**
 * A tap on [abilityId]'s tray slot. It picks that power-up for the next shot; in a level a tap
 * on the picked one unpicks it, and a spent one does nothing.
 */
internal fun tapTraySlot(engine: GameEngine, abilityId: String) {
    when {
        abilityId in engine.spentAbilities -> Unit
        engine.singleUsePowerUps && engine.selectedAbility?.id == abilityId -> engine.unselectAbility()
        else -> engine.selectAbility(abilityId)
    }
}

/** A power-up whose one shot in this level is used and isn't the shot in flight. */
internal fun isSpent(engine: GameEngine, abilityId: String): Boolean =
    abilityId in engine.spentAbilities && engine.selectedAbility?.id != abilityId

/**
 * Active abilities as small glowing icons by the score; each pulses when it fires. With more
 * than one, the picked one is ringed and tapping another while aiming picks it for the next shot.
 * In a level a tap arms or disarms a power-up, and a spent one stays dim.
 */
@Composable
internal fun AbilityTray(engine: GameEngine, showcase: AbilityShowcase, frame: LongState, modifier: Modifier = Modifier) {
    val ids by remember(engine) { derivedStateOf { frame.longValue; trayIds(engine) } }
    if (ids.isEmpty()) return
    Row(
        modifier
            .width(TraySlot * ids.size)
            .height(TraySlot)
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInRoot()
                showcase.trayLeft = origin.x
                showcase.trayCenterY = origin.y + coordinates.size.height / 2f
                showcase.traySlotPx = coordinates.size.width.toFloat() / ids.size
            },
    ) {
        for (id in ids) {
            val slot = if (trayPicks(engine)) {
                Modifier.clickable(role = Role.Button, onClickLabel = "Use ${abilityName(id)}") { tapTraySlot(engine, id) }
            } else {
                Modifier
            }
            // Abilities not picked for the next shot stay in full color but fade back; spent ones fade further.
            val faded = Modifier.graphicsLayer {
                frame.longValue
                alpha = when {
                    isSpent(engine, id) -> 0.2f
                    trayPicks(engine) && engine.selectedAbility?.id != id -> 0.45f
                    else -> 1f
                }
            }
            Canvas(slot.then(faded).size(TraySlot)) {
                frame.longValue
                if (!showcase.cards.hasLanded(id)) return@Canvas
                val picking = trayPicks(engine)
                val picked = !picking || engine.selectedAbility?.id == id
                val pulse = showcase.pulses.pulse(id)
                val center = Offset(size.width / 2, size.height / 2)
                if (picking && picked) {
                    drawCircle(
                        NeonPalette.Yellow,
                        radius = size.minDimension * 0.47f,
                        center = center,
                        style = Stroke(width = size.minDimension * 0.07f),
                    )
                }
                drawCircle(
                    NeonPalette.Purple.copy(alpha = (if (picked) 0.22f else 0.08f) + 0.5f * pulse),
                    radius = size.minDimension * (0.4f + 0.2f * pulse),
                    center = center,
                    blendMode = BlendMode.Plus,
                )
                drawAbilityIcon(id, center, size.minDimension * (0.7f + 0.35f * pulse))
            }
        }
    }
}

/**
 * The earning card (plan doc): it flips in over the game with the ability's name, its looping
 * preview and "Active for the rest of this game" (plus how to switch to it when another ability
 * is already picked), holds, then shrinks into the ability's HUD
 * tray slot. While it shows it swallows touches; a tap sends it to the tray at once.
 */
@Composable
internal fun EarnedCardOverlay(engine: GameEngine, showcase: AbilityShowcase, frame: LongState, modifier: Modifier = Modifier) {
    val card by remember(showcase) { derivedStateOf { frame.longValue; showcase.cards.current } }
    val shown = card ?: return
    val blocker = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInRoot()
                showcase.cardCenter = Offset(origin.x + coordinates.size.width / 2f, origin.y + coordinates.size.height / 2f)
            }
            .drawBehind {
                frame.longValue
                drawRect(NeonPalette.Background.copy(alpha = 0.6f * shown.flip * (1f - shown.drop)))
            }
            .clickable(interactionSource = blocker, indication = null) { showcase.cards.skip() },
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(10.dp)
        Column(
            Modifier
                .width(230.dp)
                .graphicsLayer {
                    frame.longValue
                    val drop = shown.drop
                    rotationY = 90f * (1f - shown.flip)
                    cameraDistance = 14f * density
                    val scale = 1f - 0.9f * drop
                    scaleX = scale
                    scaleY = scale
                    val target = showcase.slotCenter(engine, shown.abilityId)
                    translationX = (target.x - showcase.cardCenter.x) * drop
                    translationY = (target.y - showcase.cardCenter.y) * drop
                    alpha = if (drop > 0.8f) (1f - drop) / 0.2f else 1f
                }
                .background(NeonPalette.Background, shape)
                .border(3.dp, NeonPalette.Purple, shape)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText("ABILITY EARNED", style = arcadeStyle(NeonPalette.Yellow, 14.sp, TextAlign.Center))
            AbilityPreview(
                shown.abilityId,
                Modifier
                    .size(140.dp)
                    .border(1.dp, NeonPalette.Purple.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
            )
            BasicText(abilityName(shown.abilityId), style = arcadeStyle(NeonPalette.Orange, 20.sp, TextAlign.Center))
            BasicText(
                if (engine.singleUsePowerUps) "YOURS FOR ONE SHOT\nIN THIS LEVEL" else "ACTIVE FOR THE REST\nOF THIS GAME",
                style = arcadeStyle(NeonPalette.Cyan, 11.sp, TextAlign.Center),
            )
            // Earning a second ability doesn't change the pick, so say how to switch to it.
            if (trayPicks(engine)) {
                BasicText(
                    "TAP ITS ICON BY THE SCORE\nTO USE IT NEXT SHOT",
                    style = arcadeStyle(NeonPalette.Yellow, 10.sp, TextAlign.Center),
                )
            }
        }
    }
}

/** An ability's short preview clip, looping for as long as it is on screen. */
@Composable
fun AbilityPreview(abilityId: String, modifier: Modifier = Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(abilityId) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
    }
    Canvas(modifier) { drawAbilityPreview(abilityId, time.floatValue) }
}

/**
 * Abilities still to earn, shown off as silhouettes with their unlock round ("REACH ROUND 5")
 * beside their preview clip. Draws nothing when [abilities] is empty.
 */
@Composable
fun LockedAbilities(abilities: List<Ability>, modifier: Modifier = Modifier, title: String = "NEXT ABILITY") {
    if (abilities.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MenuLabel(title, NeonPalette.Purple, 12.sp)
        for (ability in abilities) {
            val shape = RoundedCornerShape(6.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .border(2.dp, NeonPalette.Dim, shape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Canvas(Modifier.size(40.dp)) {
                    drawAbilityIcon(ability.id, Offset(size.width / 2, size.height / 2), size.minDimension, silhouette = true)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(abilityName(ability.id), style = arcadeStyle(NeonPalette.Ink, 13.sp))
                    BasicText("REACH ROUND ${ability.earnedAtRound}", style = arcadeStyle(NeonPalette.Yellow, 11.sp))
                }
                AbilityPreview(ability.id, Modifier.size(64.dp))
            }
        }
    }
}
