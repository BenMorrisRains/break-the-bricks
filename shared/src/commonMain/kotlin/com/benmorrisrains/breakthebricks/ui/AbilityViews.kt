package com.benmorrisrains.breakthebricks.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.GameEvent
import kotlin.math.abs

/** Space from one carousel icon to the next. */
private val CarouselSpacing = 76.dp

/** The middle carousel icon's size; the others shrink with distance. */
private val CarouselIcon = 58.dp

/** Height of the carousel strip under the field: the icon plus its name. */
val CarouselHeight = 92.dp

/**
 * The ability showcase for one game: canvas effects, the earning card, the carousel's pulses and
 * its turning, all fed from the same drained events. [GameScreen] holds one per engine.
 */
class AbilityShowcase {
    val effects = CanvasEffects()
    val cards = EarnedCards()
    val pulses = AbilityPulses()
    val wheel = CarouselWheel()

    /** Where the carousel's middle sits on screen, in root pixels, so a card can drop into its slot. */
    internal var carouselCenter = Offset.Zero
    internal var carouselSpacingPx = 0f

    /** Center of the earning card's overlay, in root pixels. */
    internal var cardCenter = Offset.Zero

    /** True while an earning card holds the game still. */
    val holdsPlay: Boolean get() = cards.holdsPlay

    fun update(elapsedSeconds: Float, events: List<GameEvent>, engine: GameEngine) {
        val roundValue = engine.config.rowValues.valueFor(engine.round.coerceAtLeast(1))
        effects.update(elapsedSeconds, events, roundValue)
        cards.update(elapsedSeconds, events)
        pulses.update(elapsedSeconds, events)
        // A newly earned power-up turns to the middle so its card lands in view; it isn't picked.
        for (event in events) {
            if (event is GameEvent.AbilityEarned) {
                val ids = trayIds(engine)
                wheel.rotateTo(ids.indexOf(event.abilityId).coerceAtLeast(0), ids.size)
            }
        }
        wheel.update(elapsedSeconds)
    }

    /** Center of the carousel slot [abilityId] sits in, in root pixels. */
    internal fun slotCenter(engine: GameEngine, abilityId: String): Offset {
        val ids = trayIds(engine)
        val offset = wheel.offsetOf(ids.indexOf(abilityId).coerceAtLeast(0), ids.size)
        return Offset(carouselCenter.x + offset * carouselSpacingPx, carouselCenter.y)
    }
}

/**
 * The carousel's icons: one per active ability. An earned ability is active as soon as its card
 * appears, so its slot is already there, empty until the card lands in it.
 */
internal fun trayIds(engine: GameEngine): List<String> = engine.activeAbilities.map { it.id }.distinct()

/**
 * Whether the carousel shows a pick: with limited power-ups (every real game, where none is
 * picked until tapped) or more than one ability active, the one the next shot uses is ringed
 * and the player can tap another while aiming.
 */
internal fun trayPicks(engine: GameEngine): Boolean = engine.singleUsePowerUps || trayIds(engine).size > 1

/**
 * A tap on [abilityId]'s carousel icon. It picks that power-up for the next shot; with limited
 * power-ups a tap on the picked one unpicks it, and one with no copies left does nothing.
 */
internal fun tapTraySlot(engine: GameEngine, abilityId: String) {
    when {
        abilityId in engine.spentAbilities -> Unit
        engine.singleUsePowerUps && engine.selectedAbility?.id == abilityId -> engine.unselectAbility()
        else -> engine.selectAbility(abilityId)
    }
}

/** A power-up with no copies left that isn't the shot in flight. */
internal fun isSpent(engine: GameEngine, abilityId: String): Boolean =
    abilityId in engine.spentAbilities && engine.selectedAbility?.id != abilityId

/** "x3": copies left of [abilityId], or null when power-ups aren't limited. */
internal fun copiesLabel(engine: GameEngine, abilityId: String): String? = engine.copiesLeft(abilityId)?.let { "x$it" }

/**
 * The power-ups as a turning carousel under the launch line (Ben, 2026-10-17), each icon with its
 * copies left ("x3") over it. Dragging turns it; a tap on the middle icon picks it for the next
 * shot or, if picked, unpicks it, and a tap on a side icon turns it to the middle and picks it.
 * The picked one is ringed in yellow; one with no copies left stays dim. It keeps its height when
 * empty, so the field doesn't jump when the first power-up is earned.
 */
@Composable
internal fun PowerUpCarousel(engine: GameEngine, showcase: AbilityShowcase, frame: LongState, modifier: Modifier = Modifier) {
    val ids by remember(engine) { derivedStateOf { frame.longValue; trayIds(engine) } }
    // A badge shows once the icon's earning card has landed in its slot.
    val labels by remember(engine) {
        derivedStateOf { frame.longValue; ids.map { if (showcase.cards.hasLanded(it)) copiesLabel(engine, it) else null } }
    }
    val centerName by remember(engine) {
        derivedStateOf { frame.longValue; ids.getOrNull(showcase.wheel.centerIndex(ids.size))?.let(::abilityName) }
    }
    val wheel = showcase.wheel
    val density = LocalDensity.current
    val spacingPx = with(density) { CarouselSpacing.toPx() }
    Box(
        modifier
            .fillMaxWidth()
            .height(CarouselHeight)
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInRoot()
                showcase.carouselCenter = Offset(origin.x + coordinates.size.width / 2f, origin.y + with(density) { (CarouselIcon / 2 + 6.dp).toPx() })
                showcase.carouselSpacingPx = spacingPx
            }
            .pointerInput(engine) {
                detectHorizontalDragGestures(
                    onDragEnd = { wheel.release() },
                    onDragCancel = { wheel.release() },
                ) { change, dragAmount ->
                    change.consume()
                    wheel.drag(dragAmount / spacingPx)
                }
            }
            .pointerInput(engine) {
                detectTapGestures { tap ->
                    val count = trayIds(engine).size
                    val index = wheel.indexAt((tap.x - size.width / 2f) / spacingPx, count) ?: return@detectTapGestures
                    val id = trayIds(engine)[index]
                    if (index == wheel.centerIndex(count)) {
                        tapTraySlot(engine, id)
                    } else {
                        wheel.rotateTo(index, count)
                        if (engine.selectedAbility?.id != id) tapTraySlot(engine, id)
                    }
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        for ((index, id) in ids.withIndex()) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(CarouselIcon)
                    .graphicsLayer {
                        frame.longValue
                        val offset = wheel.offsetOf(index, ids.size)
                        val distance = abs(offset)
                        translationX = offset * spacingPx
                        val scale = (1f - 0.22f * distance).coerceAtLeast(0.5f)
                        scaleX = scale
                        scaleY = scale
                        // Icons fade toward the ends of the strip; ones not picked fade back; spent ones further.
                        val reach = (1f - (distance - 1.5f).coerceAtLeast(0f)).coerceIn(0f, 1f)
                        alpha = reach * when {
                            isSpent(engine, id) -> 0.25f
                            trayPicks(engine) && engine.selectedAbility?.id != id -> 0.6f
                            else -> 1f
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    frame.longValue
                    if (!showcase.cards.hasLanded(id)) return@Canvas
                    val picking = trayPicks(engine)
                    val picked = !picking || engine.selectedAbility?.id == id
                    val pulse = showcase.pulses.pulse(id)
                    val center = Offset(size.width / 2, size.height / 2)
                    drawCircle(
                        if (picking && picked) NeonPalette.Yellow else NeonPalette.Purple.copy(alpha = 0.6f),
                        radius = size.minDimension * 0.47f,
                        center = center,
                        style = Stroke(width = size.minDimension * (if (picking && picked) 0.07f else 0.04f)),
                    )
                    drawCircle(
                        NeonPalette.Purple.copy(alpha = (if (picked) 0.22f else 0.08f) + 0.5f * pulse),
                        radius = size.minDimension * (0.4f + 0.2f * pulse),
                        center = center,
                        blendMode = BlendMode.Plus,
                    )
                    drawAbilityIcon(id, center, size.minDimension * (0.62f + 0.3f * pulse))
                }
                val label = labels.getOrNull(index)
                if (label != null) {
                    BasicText(
                        label,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(NeonPalette.Background.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 3.dp),
                        style = arcadeStyle(NeonPalette.Ink, 14.sp),
                    )
                }
            }
        }
        centerName?.let { name ->
            BasicText(
                name,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                style = arcadeStyle(NeonPalette.Cyan, 11.sp, TextAlign.Center),
            )
        }
    }
}

/**
 * The earning card (plan doc): it flips in over the game with the ability's name, its looping
 * preview and what the copy is good for (plus how to pick it), holds, then shrinks into the
 * ability's carousel slot. While it shows it swallows touches; a tap sends it to the tray at once.
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
                when {
                    engine.levelRows != null -> "+1 COPY: ONE SHOT\nIN THIS LEVEL"
                    engine.singleUsePowerUps -> "+1 COPY: ONE SHOT\nSAVED TO YOUR STASH"
                    else -> "ACTIVE FOR THE REST\nOF THIS GAME"
                },
                style = arcadeStyle(NeonPalette.Cyan, 11.sp, TextAlign.Center),
            )
            // Earning a second ability doesn't change the pick, so say how to switch to it.
            if (trayPicks(engine)) {
                BasicText(
                    "TAP IT BELOW THE LINE\nTO USE IT NEXT SHOT",
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
