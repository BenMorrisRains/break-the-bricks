package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.GrenadeBall
import com.benmorrisrains.breakthebricks.engine.SeededRandom
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A palette slot for an effect; the canvas turns it into a color with [NeonPalette.tint]. */
enum class Tint { Ink, Cyan, Lime, Yellow, Orange, Magenta, Purple }

/** Brick tints from lowest to highest value, matching [NeonPalette.BrickTiers]. */
val BrickTints = listOf(Tint.Cyan, Tint.Lime, Tint.Yellow, Tint.Orange, Tint.Magenta)

/**
 * Which of [tiers] color steps a brick worth [hits] gets when the newest row is worth
 * [roundValue]: the toughest bricks on the board always get the top step.
 */
fun brickTier(hits: Long, roundValue: Long, tiers: Int): Int {
    if (roundValue <= 1L) return 0
    val ratio = (hits.toDouble() / roundValue.toDouble()).coerceIn(0.0, 1.0)
    return (ratio * (tiers - 1)).toInt()
}

/**
 * The color of ball [index] of [total] when a round fires [ballCount]: after a split the engine
 * adds the new balls past [ballCount], so the three balls of the split are the first ball and
 * those. They get magenta, cyan and lime so they stand out from the white balls (Ben's playtest).
 * Null for a normal white ball.
 */
fun splitBallTint(index: Int, ballCount: Int, total: Int): Tint? = when {
    total <= ballCount -> null
    index == 0 -> Tint.Magenta
    index >= ballCount -> SplitTints[(index - ballCount) % SplitTints.size]
    else -> null
}

private val SplitTints = listOf(Tint.Cyan, Tint.Lime)

/** How a particle is drawn: a square neon pixel, or a streak along its motion (shrapnel). */
enum class ParticleShape { Pixel, Streak }

/** One spark of a break or blast. Positions and speeds are in cells and cells per second. */
class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val size: Float,
    val life: Float,
    val tint: Tint,
    val shape: ParticleShape,
) {
    var age = 0f
        internal set

    /** 1 when spawned, fading to 0 at the end of its life. */
    val fade: Float get() = (1f - age / life).coerceIn(0f, 1f)
}

/** A ring that grows out of a grenade blast. [radius] is in cells. */
class Shockwave(val x: Float, val y: Float, val life: Float, val maxRadius: Float) {
    var age = 0f
        internal set

    val progress: Float get() = (age / life).coerceIn(0f, 1f)

    /** Fast at first, easing out. */
    val radius: Float get() = maxRadius * (1f - (1f - progress) * (1f - progress))
}

/**
 * The brick a grenade hit, flashing white in its cell for a moment before it shatters into
 * shards. The engine has already removed it, so the canvas draws this in its place.
 */
class BrickGhost(val column: Int, val row: Int, val life: Float, val tint: Tint) {
    var age = 0f
        internal set

    val fade: Float get() = (1f - age / life).coerceIn(0f, 1f)
}

/**
 * Everything the canvas draws on top of the board for a moment: neon pixels when a brick breaks,
 * and the grenade blast (flash, shockwave ring, shrapnel, the hit brick flashing white then
 * shattering, and a short screen shake). Fed each frame with the drained [GameEvent]s.
 *
 * Purely visual: it never touches game state. Its sparks come from its own [SeededRandom], so the
 * engine's random sequence is untouched and the same events always look the same.
 */
class CanvasEffects(seed: Long = 0x7E11L) {
    private val random = SeededRandom(seed)
    private val pendingShards = mutableListOf<BrickGhost>()

    val particles = mutableListOf<Particle>()
    val shockwaves = mutableListOf<Shockwave>()
    val ghosts = mutableListOf<BrickGhost>()

    /** Seconds of effects played so far; drives flickers such as the grenade's fuse spark. */
    var time = 0f
        private set

    /** Brightness of the full-field blast flash, 1 right after a grenade explodes. */
    var flash = 0f
        private set

    private var shakeLeft = 0f

    /** True while the screen shakes after a blast. */
    val isShaking: Boolean get() = shakeLeft > 0f

    /**
     * Moves every effect on by [elapsedSeconds] and starts new ones for [events]. [roundValue] is
     * the newest row's value, used to color break sparks like the brick that broke.
     */
    fun update(elapsedSeconds: Float, events: List<GameEvent>, roundValue: Long) {
        step(elapsedSeconds)
        // A grenade's own blast covers its brick, so that brick gets no plain break burst.
        val blasted = events.filterIsInstance<GameEvent.AbilityTriggered>()
            .filter { it.abilityId == GrenadeBall.id }
            .map { it.column to it.row }
            .toSet()
        for (event in events) {
            when (event) {
                is GameEvent.BrickBroken -> if ((event.column to event.row) !in blasted) {
                    val tier = brickTier(event.points, roundValue, BrickTints.size)
                    breakBurst(event.column, event.row, BrickTints[tier])
                }
                is GameEvent.AbilityTriggered -> if (event.abilityId == GrenadeBall.id) {
                    grenadeBlast(event.column, event.row)
                }
                else -> Unit
            }
        }
    }

    /** Screen shake offset in cells for this frame: (0, 0) when still. */
    fun shakeX(): Float = if (shakeLeft <= 0f) 0f else shakeAmount() * sin(time * 91f)

    fun shakeY(): Float = if (shakeLeft <= 0f) 0f else shakeAmount() * cos(time * 67f)

    private fun shakeAmount(): Float = SHAKE_CELLS * (shakeLeft / SHAKE_SECONDS)

    private fun step(dt: Float) {
        if (dt <= 0f) return
        time += dt
        flash = (flash - dt / FLASH_SECONDS).coerceAtLeast(0f)
        shakeLeft = (shakeLeft - dt).coerceAtLeast(0f)

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.age += dt
            if (p.age >= p.life) {
                iterator.remove()
                continue
            }
            p.vy += GRAVITY * dt
            val drag = 1f - DRAG * dt
            p.vx *= drag
            p.vy *= drag
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        shockwaves.forEach { it.age += dt }
        shockwaves.removeAll { it.age >= it.life }

        for (ghost in ghosts) ghost.age += dt
        val done = ghosts.filter { it.age >= it.life }
        if (done.isNotEmpty()) {
            ghosts.removeAll(done)
            pendingShards += done
        }
        for (ghost in pendingShards) shatter(ghost)
        pendingShards.clear()
    }

    /** A few neon pixels in the brick's color, bursting out and falling. */
    private fun breakBurst(column: Int, row: Int, tint: Tint) {
        val cx = column + 0.5f
        val cy = row + 0.5f
        repeat(BREAK_PIXELS) {
            val angle = random.nextFloat() * TWO_PI
            val speed = 1.4f + random.nextFloat() * 2.2f
            spawn(
                Particle(
                    x = cx + (random.nextFloat() - 0.5f) * 0.6f,
                    y = cy + (random.nextFloat() - 0.5f) * 0.6f,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 1f,
                    size = 0.07f + random.nextFloat() * 0.06f,
                    life = 0.35f + random.nextFloat() * 0.25f,
                    tint = tint,
                    shape = ParticleShape.Pixel,
                ),
            )
        }
    }

    /** The grenade going off on the brick at [column], [row]. */
    private fun grenadeBlast(column: Int, row: Int) {
        val cx = column + 0.5f
        val cy = row + 0.5f
        flash = 1f
        shakeLeft = SHAKE_SECONDS
        shockwaves += Shockwave(cx, cy, life = 0.45f, maxRadius = 1.9f)
        shockwaves += Shockwave(cx, cy, life = 0.3f, maxRadius = 1.1f)
        ghosts += BrickGhost(column, row, life = GHOST_SECONDS, tint = Tint.Ink)
        repeat(SHRAPNEL) { i ->
            // Spread evenly with some jitter, so no side of the blast looks empty.
            val angle = (i + random.nextFloat() * 0.8f) * TWO_PI / SHRAPNEL
            val speed = 5f + random.nextFloat() * 4f
            spawn(
                Particle(
                    x = cx,
                    y = cy,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    size = 0.05f + random.nextFloat() * 0.04f,
                    life = 0.3f + random.nextFloat() * 0.25f,
                    tint = if (i % 3 == 0) Tint.Yellow else Tint.Orange,
                    shape = ParticleShape.Streak,
                ),
            )
        }
    }

    /** The white brick breaking into chunky shards once its flash is over. */
    private fun shatter(ghost: BrickGhost) {
        repeat(SHARDS) {
            val angle = random.nextFloat() * TWO_PI
            val speed = 1.5f + random.nextFloat() * 2.5f
            spawn(
                Particle(
                    x = ghost.column + 0.15f + random.nextFloat() * 0.7f,
                    y = ghost.row + 0.15f + random.nextFloat() * 0.7f,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 1.5f,
                    size = 0.12f + random.nextFloat() * 0.1f,
                    life = 0.45f + random.nextFloat() * 0.25f,
                    tint = if (it % 2 == 0) Tint.Ink else Tint.Orange,
                    shape = ParticleShape.Pixel,
                ),
            )
        }
    }

    /** Late in a run dozens of bricks can break in one volley; past the cap, new sparks are skipped. */
    private fun spawn(particle: Particle) {
        if (particles.size < MAX_PARTICLES) particles += particle
    }

    companion object {
        const val MAX_PARTICLES = 600
        const val BREAK_PIXELS = 8
        const val SHRAPNEL = 24
        const val SHARDS = 10
        const val FLASH_SECONDS = 0.18f
        const val GHOST_SECONDS = 0.09f
        const val SHAKE_SECONDS = 0.3f
        const val SHAKE_CELLS = 0.12f
        private const val GRAVITY = 9f
        private const val DRAG = 2.5f
        private const val TWO_PI = (2 * PI).toFloat()
    }
}
