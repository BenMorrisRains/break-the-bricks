package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.Ball
import com.benmorrisrains.breakthebricks.engine.BallState
import com.benmorrisrains.breakthebricks.engine.Board
import com.benmorrisrains.breakthebricks.engine.Brick
import com.benmorrisrains.breakthebricks.engine.GameConfig
import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import com.benmorrisrains.breakthebricks.engine.SeededRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * A stand-in [GameEngine] so the canvas and aim input can be seen and played before the real
 * engine (T2) and physics (T3) land. Its physics is deliberately rough: one axis flip per brick
 * hit, no corner handling. Task T6 replaces it with the real engine. After a game over it starts
 * a fresh sample board on its own, so the preview never gets stuck.
 */
class PreviewGameEngine(
    override val config: GameConfig = GameConfig(),
    seed: Long = 2026,
    private val startRound: Int = 8,
) : GameEngine {
    private val random = SeededRandom(seed)
    override val board = Board(config.columns, config.rows)
    private val ballList = mutableListOf<Ball>()
    override val balls: List<Ball> get() = ballList
    override var phase = RoundPhase.Aiming
        private set
    override var round = startRound
        private set
    override val ballCount: Int get() = round
    override var points = 0L
        private set
    override var launchX = config.columns / 2f
        private set
    override var aimAngle = (PI / 2).toFloat()
        private set
    override val activeAbilities: List<Ability> = emptyList()

    private val events = mutableListOf<GameEvent>()
    private var accumulator = 0f
    private var fireClock = 0f
    private var launched = 0
    private var nextLaunchX: Float? = null
    private var gameOverClock = 0f

    init {
        newGame()
    }

    override fun aim(angleRadians: Float) {
        if (phase != RoundPhase.Aiming) return
        aimAngle = angleRadians.coerceIn(config.minAimAngle, PI.toFloat() - config.minAimAngle)
    }

    override fun fire() {
        if (phase != RoundPhase.Aiming) return
        ballList.clear()
        repeat(ballCount) { ballList += Ball(launchX, config.rows - config.ballRadius) }
        launched = 0
        fireClock = 0f
        nextLaunchX = null
        phase = RoundPhase.Firing
    }

    override fun advance(elapsedSeconds: Float) {
        accumulator += elapsedSeconds.coerceIn(0f, MAX_ADVANCE_SECONDS)
        while (accumulator >= GameConfig.STEP_SECONDS) {
            accumulator -= GameConfig.STEP_SECONDS
            step()
        }
    }

    override fun drainEvents(): List<GameEvent> = events.toList().also { events.clear() }

    private fun step() {
        when (phase) {
            RoundPhase.Firing -> {
                launchDue()
                moveBalls()
            }
            RoundPhase.Returning -> moveBalls()
            RoundPhase.GameOver -> {
                gameOverClock += GameConfig.STEP_SECONDS
                if (gameOverClock >= RESTART_SECONDS) newGame()
            }
            RoundPhase.Aiming, RoundPhase.Advancing -> Unit
        }
    }

    private fun launchDue() {
        fireClock += GameConfig.STEP_SECONDS
        while (launched < ballList.size && fireClock >= launched * config.fireInterval) {
            ballList[launched].apply {
                vx = cos(aimAngle) * config.ballSpeed
                vy = -sin(aimAngle) * config.ballSpeed
                state = BallState.Flying
            }
            launched++
        }
        if (launched == ballList.size) phase = RoundPhase.Returning
    }

    private fun moveBalls() {
        val dt = GameConfig.STEP_SECONDS / SUBSTEPS
        for (ball in ballList) {
            repeat(SUBSTEPS) { if (ball.state == BallState.Flying) moveBall(ball, dt) }
        }
        if (phase == RoundPhase.Returning && ballList.all { it.state == BallState.Landed }) endRound()
    }

    private fun moveBall(ball: Ball, dt: Float) {
        val r = config.ballRadius
        // Horizontal then vertical, probing the cell at the ball's leading edge on each axis.
        ball.x += ball.vx * dt
        val hitX = brickAt(ball.x + if (ball.vx > 0) r else -r, ball.y)
        if (hitX != null) {
            ball.x -= ball.vx * dt
            ball.vx = -ball.vx
            hit(hitX)
        } else if (ball.x < r || ball.x > config.columns - r) {
            ball.x = ball.x.coerceIn(r, config.columns - r)
            ball.vx = if (ball.x <= r) abs(ball.vx) else -abs(ball.vx)
            events += GameEvent.WallBounce
        }

        ball.y += ball.vy * dt
        val hitY = brickAt(ball.x, ball.y + if (ball.vy > 0) r else -r)
        if (hitY != null) {
            ball.y -= ball.vy * dt
            ball.vy = -ball.vy
            if (hitY !== hitX) hit(hitY)
        } else if (ball.y < r) {
            ball.y = r
            ball.vy = abs(ball.vy)
            events += GameEvent.WallBounce
        } else if (ball.y >= config.rows - r && ball.vy > 0) {
            land(ball)
        }
    }

    private fun brickAt(x: Float, y: Float): Brick? = board[floor(x).toInt(), floor(y).toInt()]

    private fun hit(brick: Brick) {
        brick.hits -= 1
        points += 1
        events += GameEvent.BrickHit(brick.column, brick.row, brick.hits)
        if (brick.hits <= 0) {
            board[brick.column, brick.row] = null
            events += GameEvent.BrickBroken(brick.column, brick.row, brick.startingHits)
        }
    }

    private fun land(ball: Ball) {
        val first = nextLaunchX == null
        val x = nextLaunchX ?: ball.x.coerceIn(config.ballRadius, config.columns - config.ballRadius)
        nextLaunchX = x
        events += GameEvent.BallLanded(ball.x, first)
        // The rest gather at the first ball's spot.
        ball.x = x
        ball.y = config.rows - config.ballRadius
        ball.vx = 0f
        ball.vy = 0f
        ball.state = BallState.Landed
    }

    private fun endRound() {
        launchX = nextLaunchX ?: launchX
        ballList.clear()
        val bottomRow = config.rows - 1
        for (row in bottomRow - 1 downTo 0) {
            for (column in 0 until config.columns) {
                val brick = board[column, row] ?: continue
                board[column, row] = null
                brick.row = row + 1
                board[column, row + 1] = brick
            }
        }
        if ((0 until config.columns).any { board[it, bottomRow] != null }) {
            phase = RoundPhase.GameOver
            gameOverClock = 0f
            events += GameEvent.GameOver(round, points)
            return
        }
        round++
        spawnRow(0, config.rowValues.valueFor(round))
        phase = RoundPhase.Aiming
        events += GameEvent.RoundStarted(round, ballCount)
    }

    private fun newGame() {
        board.clear()
        ballList.clear()
        round = startRound
        points = 0
        launchX = config.columns / 2f
        aimAngle = (PI / 2).toFloat()
        for (row in 0 until SAMPLE_ROWS) {
            spawnRow(row, config.rowValues.valueFor((startRound - row).coerceAtLeast(1)))
        }
        phase = RoundPhase.Aiming
        events += GameEvent.RoundStarted(round, ballCount)
    }

    private fun spawnRow(row: Int, value: Long) {
        val count = random.nextIntInclusive(config.minBricksPerRow, config.maxBricksPerRow)
        val columns = (0 until config.columns).toMutableList()
        repeat(count) {
            val column = columns.removeAt(random.nextInt(columns.size))
            board[column, row] = Brick(column, row, value)
        }
    }

    private companion object {
        const val SUBSTEPS = 2
        const val SAMPLE_ROWS = 4
        const val RESTART_SECONDS = 2.5f
        const val MAX_ADVANCE_SECONDS = 0.25f
    }
}
