package com.benmorrisrains.breakthebricks.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The game itself: rounds, the brick grid, scoring and the fixed-step loop.
 * Collision math lives behind [physics]; abilities plug in through [Ability].
 *
 * A round goes Aiming -> Firing -> Returning -> Advancing -> Aiming, or ends in GameOver.
 * Nothing moves while Aiming, so a game depends only on [seed] and the angle fired each round,
 * never on frame timing.
 */
class BrickGame(
    override val config: GameConfig = GameConfig(),
    val seed: Long,
    private val physics: BallPhysics,
    /** Abilities active from round 1, such as a staked one. */
    abilities: List<Ability> = emptyList(),
    /** Abilities this game earns on reaching their [Ability.earnedAtRound]. */
    private val earnable: List<Ability> = Abilities.earnable,
) : GameEngine {
    private val random = SeededRandom(seed)
    private val events = mutableListOf<GameEvent>()
    private val ballList = mutableListOf<Ball>()
    private val abilityList = abilities.toMutableList()

    override val board = Board(config.columns, config.rows)
    override val balls: List<Ball> get() = ballList
    override val activeAbilities: List<Ability> get() = abilityList

    override var phase = RoundPhase.Aiming
        private set
    override var round = 0
        private set
    override var ballCount = 0
        private set
    override var points = 0L
        private set
    override var launchX = config.columns / 2f
        private set
    override var aimAngle = (PI / 2).toFloat()
        private set

    /** Angles fired so far, one per round. Replaying them on the same seed reproduces the game. */
    val firedAngles: List<Float> get() = fired
    private val fired = mutableListOf<Float>()

    /** Fixed steps run since the game began. */
    var steps = 0L
        private set

    private var accumulator = 0f
    private var stepsInPhase = 0
    private var ballsLaunched = 0
    private var firstLandingX: Float? = null
    private var firstBallHasHit = false
    private var currentBall: Ball? = null

    private val floorY get() = config.rows - config.ballRadius
    private val fireIntervalSteps = max(1, (config.fireInterval * GameConfig.STEPS_PER_SECOND).roundToInt())
    private val advanceSteps = max(1, (config.advanceSeconds * GameConfig.STEPS_PER_SECOND).roundToInt())
    private val returnSpeedPerStep = config.ballSpeed * GameConfig.STEP_SECONDS

    private val contacts = object : BallContacts {
        override fun brickHit(column: Int, row: Int) = onBrickHit(column, row)
        override fun wallBounce() {
            events += GameEvent.WallBounce
        }
    }

    private val abilityContext = object : AbilityContext {
        override val board: Board get() = this@BrickGame.board
        override fun damage(column: Int, row: Int, hits: Long) = damageBrick(column, row, hits)
        override fun breakBrick(column: Int, row: Int) {
            val brick = board[column, row] ?: return
            damageBrick(column, row, brick.hits)
        }
    }

    init {
        startRound()
    }

    override fun aim(angleRadians: Float) {
        if (phase != RoundPhase.Aiming) return
        aimAngle = clampAim(angleRadians)
    }

    override fun fire() {
        if (phase != RoundPhase.Aiming) return
        fired += aimAngle
        val vx = cos(aimAngle) * config.ballSpeed
        val vy = -sin(aimAngle) * config.ballSpeed
        for (ball in ballList) {
            ball.x = launchX
            ball.y = floorY
            ball.vx = vx
            ball.vy = vy
            ball.state = BallState.Waiting
        }
        ballsLaunched = 0
        firstLandingX = null
        firstBallHasHit = false
        stepsInPhase = 0
        phase = RoundPhase.Firing
    }

    override fun advance(elapsedSeconds: Float) {
        if (phase == RoundPhase.GameOver || elapsedSeconds <= 0f) return
        // Cap the catch-up after a long pause so one frame never runs thousands of steps.
        accumulator += min(elapsedSeconds, MAX_FRAME_SECONDS)
        while (accumulator >= GameConfig.STEP_SECONDS) {
            accumulator -= GameConfig.STEP_SECONDS
            step()
        }
    }

    /** Runs exactly one fixed physics step. Does nothing while Aiming or after GameOver. */
    fun step() {
        when (phase) {
            RoundPhase.Aiming, RoundPhase.GameOver -> return
            RoundPhase.Firing, RoundPhase.Returning -> stepBalls()
            RoundPhase.Advancing -> if (stepsInPhase + 1 >= advanceSteps) startRound()
        }
        steps++
        stepsInPhase++
    }

    override fun drainEvents(): List<GameEvent> {
        val drained = events.toList()
        events.clear()
        return drained
    }

    private fun stepBalls() {
        if (phase == RoundPhase.Firing && stepsInPhase % fireIntervalSteps == 0) {
            ballList[ballsLaunched].state = BallState.Flying
            ballsLaunched++
            if (ballsLaunched == ballList.size) phase = RoundPhase.Returning
        }
        for (ball in ballList) {
            when (ball.state) {
                BallState.Flying -> moveFlying(ball)
                BallState.Landed -> slideToLaunch(ball)
                BallState.Waiting -> Unit
            }
        }
        if (phase == RoundPhase.Returning && ballList.all { it.state == BallState.Landed && it.x == firstLandingX }) {
            endRound()
        }
    }

    private fun moveFlying(ball: Ball) {
        currentBall = ball
        val landed = physics.step(ball, board, config, GameConfig.STEP_SECONDS, contacts)
        currentBall = null
        if (!landed) return
        val x = ball.x.coerceIn(config.ballRadius, config.columns - config.ballRadius)
        ball.x = x
        ball.y = floorY
        ball.vx = 0f
        ball.vy = 0f
        ball.state = BallState.Landed
        val isFirst = firstLandingX == null
        if (isFirst) firstLandingX = x
        events += GameEvent.BallLanded(x, isFirst)
    }

    /** Landed balls roll along the floor to where the first ball landed. */
    private fun slideToLaunch(ball: Ball) {
        val target = firstLandingX ?: return
        val gap = target - ball.x
        ball.x = if (abs(gap) <= returnSpeedPerStep) target else ball.x + if (gap > 0) returnSpeedPerStep else -returnSpeedPerStep
    }

    private fun onBrickHit(column: Int, row: Int) {
        if (board[column, row] == null) return
        damageBrick(column, row, 1)
        if (currentBall === ballList.first() && !firstBallHasHit) {
            firstBallHasHit = true
            for (ability in abilityList) {
                events += GameEvent.AbilityTriggered(ability.id, column, row)
                ability.onFirstHit(abilityContext, column, row)
            }
        }
    }

    /** Takes up to [hits] off a brick, scoring one point per hit taken. */
    private fun damageBrick(column: Int, row: Int, hits: Long) {
        val brick = board[column, row] ?: return
        val taken = min(hits, brick.hits)
        if (taken <= 0) return
        brick.hits -= taken
        points += taken
        events += GameEvent.BrickHit(column, row, brick.hits)
        if (brick.hits == 0L) {
            board[column, row] = null
            events += GameEvent.BrickBroken(column, row, taken)
        }
    }

    private fun endRound() {
        launchX = firstLandingX ?: launchX
        stepsInPhase = -1 // step() counts this step, so Advancing starts at 0.
        phase = RoundPhase.Advancing
        // The drop happens now so the UI can animate it during Advancing.
        dropBricks()
        if (board.bricks().any { it.row == config.rows - 1 }) {
            phase = RoundPhase.GameOver
            events += GameEvent.GameOver(round, points)
        }
    }

    private fun startRound() {
        round++
        ballCount = round
        while (ballList.size < ballCount) ballList += Ball(launchX, floorY)
        for (ball in ballList) {
            ball.x = launchX
            ball.y = floorY
            ball.vx = 0f
            ball.vy = 0f
            ball.state = BallState.Waiting
        }
        spawnRow()
        stepsInPhase = 0
        phase = RoundPhase.Aiming
        events += GameEvent.RoundStarted(round, ballCount)
        earnAbilities()
    }

    /**
     * Reaching an ability's round earns it and keeps it active for the rest of the game.
     * An ability that is already active (a staked copy) still reports the earned copy but
     * doesn't fire twice.
     */
    private fun earnAbilities() {
        for (ability in earnable) {
            if (ability.earnedAtRound != round) continue
            if (abilityList.none { it.id == ability.id }) abilityList += ability
            events += GameEvent.AbilityEarned(ability.id, round)
        }
    }

    /** Moves every brick down one row, bottom row first. */
    private fun dropBricks() {
        for (row in config.rows - 2 downTo 0) {
            for (column in 0 until config.columns) {
                val brick = board[column, row] ?: continue
                board[column, row] = null
                brick.row = row + 1
                board[column, row + 1] = brick
            }
        }
    }

    /** Fills row 0 with this round's bricks in seeded random columns. */
    private fun spawnRow() {
        val value = config.rowValues.valueFor(round)
        val count = random.nextIntInclusive(config.minBricksPerRow, config.maxBricksPerRow)
        val columns = IntArray(config.columns) { it }
        // Partial Fisher-Yates: the first [count] entries become the chosen columns.
        for (i in 0 until count) {
            val j = i + random.nextInt(config.columns - i)
            val swap = columns[i]
            columns[i] = columns[j]
            columns[j] = swap
        }
        for (i in 0 until count) {
            val column = columns[i]
            board[column, 0] = Brick(column = column, row = 0, hits = value)
        }
    }

    private fun clampAim(angle: Float): Float =
        angle.coerceIn(config.minAimAngle, PI.toFloat() - config.minAimAngle)

    companion object {
        /** Longest frame [advance] will catch up on; anything longer is treated as this much. */
        const val MAX_FRAME_SECONDS = 0.25f
    }
}
