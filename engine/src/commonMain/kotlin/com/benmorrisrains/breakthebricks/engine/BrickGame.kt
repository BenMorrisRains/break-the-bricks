package com.benmorrisrains.breakthebricks.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The game itself: rounds, the brick grid, scoring and the fixed-step loop.
 * Collision math lives behind [physics]; abilities plug in through [Ability].
 *
 * A round goes Aiming -> Firing -> Returning -> Advancing -> Aiming, or ends in GameOver
 * (a brick reached the floor row) or LevelComplete (a level's last row came and every brick is broken).
 * Nothing moves while Aiming, so a game depends only on [seed] and the angle and ability fired
 * each round, never on frame timing.
 */
class BrickGame(
    override val config: GameConfig = GameConfig(),
    val seed: Long,
    private val physics: BallPhysics,
    /** Abilities active from round 1, such as a staked one. */
    abilities: List<Ability> = emptyList(),
    /** Abilities this game earns on reaching their [Ability.earnedAtRound]. */
    private val earnable: List<Ability> = Abilities.earnable,
    /** Bricks on the board before round 1, top row first, placed just below round 1's new row. */
    layout: List<List<Long?>> = emptyList(),
    /** For a level, the rounds that bring a new row; then the board must be cleared. Null for an endless game. */
    override val levelRows: Int? = null,
) : GameEngine {
    private val random = SeededRandom(seed)

    /** Abilities (split directions, grenade blasts) draw from their own stream, so they never change the rows that come. */
    private val abilityRandom = SeededRandom(seed xor ABILITY_STREAM)
    private val events = mutableListOf<GameEvent>()
    private val ballList = mutableListOf<Ball>()
    private val abilityList = abilities.toMutableList()

    override val board = Board(config.columns, config.rows)
    override val balls: List<Ball> get() = ballList
    override val activeAbilities: List<Ability> get() = abilityList
    private var selectedId: String? = null
    override val selectedAbility: Ability?
        get() = abilityList.firstOrNull { it.id == selectedId } ?: abilityList.firstOrNull()

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

    /** The ability each round's shot used (null for none), alongside [firedAngles] for replays. */
    val firedAbilities: List<String?> get() = firedAbilityIds
    private val firedAbilityIds = mutableListOf<String?>()

    /** The ability the shot in flight uses, fixed when it was fired. */
    private var shotAbility: Ability? = null

    /**
     * One-game boosts, each as the 0-based index into [firedAngles] of the shot it first acted
     * on and the ability's id. Replaying the same angles and boosts on the same seed reproduces the game.
     */
    val boosts: List<Pair<Int, String>> get() = boostLog
    private val boostLog = mutableListOf<Pair<Int, String>>()
    private val pendingBoosts = mutableListOf<Ability>()

    /** Fixed steps run since the game began. */
    var steps = 0L
        private set

    private var accumulator = 0f
    private var stepsInPhase = 0
    private var ballsLaunched = 0
    private var firstLandingX: Float? = null
    private var firstBallHasHit = false
    private var currentBall: Ball? = null

    /** A split the first ball asked for during its current step: extra balls and their spread. */
    private var pendingSplit: Pair<Int, Float>? = null

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
        override val config: GameConfig get() = this@BrickGame.config
        override fun damage(column: Int, row: Int, hits: Long) = damageBrick(column, row, hits)
        override fun breakBrick(column: Int, row: Int) {
            val brick = board[column, row] ?: return
            damageBrick(column, row, brick.hits)
        }
        override fun randomInt(bound: Int): Int = abilityRandom.nextInt(bound)
        override fun splitFirstBall(extraBalls: Int, maxTurnRadians: Float) {
            require(extraBalls >= 0 && maxTurnRadians >= 0f)
            pendingSplit = extraBalls to maxTurnRadians
        }
    }

    init {
        require(levelRows == null || levelRows >= 1)
        // Row 0 is round 1's new row and the floor row ends the game, so a layout fits in between.
        require(layout.size <= config.rows - 2) { "a layout has at most ${config.rows - 2} rows" }
        // A level's layout is the whole starting board; extra rank rows are for endless games.
        require(layout.isEmpty() || config.startingRows == 1) { "a layout game can't also have extra starting rows" }
        // Higher ranks start with extra rows, worth what round 1 is worth, already on the board.
        repeat(config.startingRows - 1) {
            spawnRow(config.rowValues.valueFor(1))
            dropBricks()
        }
        layout.forEachIndexed { index, cells ->
            require(cells.size == config.columns) { "a layout row has ${config.columns} cells" }
            cells.forEachIndexed { column, hits ->
                if (hits != null) board[column, index + 1] = Brick(column = column, row = index + 1, hits = hits)
            }
        }
        startRound()
    }

    override fun aim(angleRadians: Float) {
        if (phase != RoundPhase.Aiming) return
        aimAngle = clampAim(angleRadians)
    }

    override fun selectAbility(abilityId: String) {
        if (phase != RoundPhase.Aiming) return
        if (abilityList.any { it.id == abilityId }) selectedId = abilityId
    }

    override fun boost(ability: Ability): Boolean {
        if (phase.isFinished) return false
        if (abilityList.any { it.id == ability.id } || pendingBoosts.any { it.id == ability.id }) return false
        pendingBoosts += ability
        return true
    }

    override fun fire() {
        if (phase != RoundPhase.Aiming) return
        // A boost waits for a shot so it always starts on a first ball that hasn't moved yet.
        // The player chose it, so it is also picked for this shot.
        for (ability in pendingBoosts) {
            boostLog += fired.size to ability.id
            if (abilityList.none { it.id == ability.id }) abilityList += ability
            selectedId = ability.id
        }
        pendingBoosts.clear()
        fired += aimAngle
        shotAbility = selectedAbility
        firedAbilityIds += shotAbility?.id
        val vx = cos(aimAngle) * config.ballSpeed
        val vy = -sin(aimAngle) * config.ballSpeed
        for (ball in ballList) {
            ball.x = launchX
            ball.y = floorY
            ball.vx = vx
            ball.vy = vy
            ball.state = BallState.Waiting
            resetEffects(ball)
        }
        ballsLaunched = 0
        firstLandingX = null
        firstBallHasHit = false
        stepsInPhase = 0
        phase = RoundPhase.Firing
    }

    override fun advance(elapsedSeconds: Float) {
        if (phase.isFinished || elapsedSeconds <= 0f) return
        // Cap the catch-up after a long pause so one frame never runs thousands of steps.
        accumulator += min(elapsedSeconds, MAX_FRAME_SECONDS)
        while (accumulator >= GameConfig.STEP_SECONDS) {
            accumulator -= GameConfig.STEP_SECONDS
            step()
        }
    }

    /** Runs exactly one fixed physics step. Does nothing while Aiming or once the game is finished. */
    fun step() {
        when (phase) {
            RoundPhase.Aiming, RoundPhase.GameOver, RoundPhase.LevelComplete -> return
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
            launch(ballList[ballsLaunched])
            ballsLaunched++
            if (ballsLaunched == ballCount) phase = RoundPhase.Returning
        }
        // A split adds balls to the end of the list mid-loop; they start moving next step.
        val moving = ballList.size
        for (i in 0 until moving) {
            val ball = ballList[i]
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

    /** Sends [ball] off, letting the shot's ability change the round's first ball before it moves. */
    private fun launch(ball: Ball) {
        if (ball === ballList.first()) shotAbility?.onFirstLaunch(abilityContext, ball)
        // A bigger ball starts higher, resting on the floor, and clear of the walls.
        val radius = ball.radius(config)
        ball.x = ball.x.coerceIn(radius, config.columns - radius)
        ball.y = config.rows - radius
        ball.state = BallState.Flying
    }

    private fun moveFlying(ball: Ball) {
        currentBall = ball
        val landed = physics.step(ball, board, config, GameConfig.STEP_SECONDS, contacts)
        currentBall = null
        pendingSplit?.let { (extraBalls, spread) ->
            pendingSplit = null
            // A ball that landed in the same step has nowhere left to split to.
            if (!landed) split(ball, extraBalls, spread)
        }
        if (!landed) return
        resetEffects(ball)
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

    /**
     * Adds [extraBalls] normal balls at [ball] and sends them and [ball] off at the same speed in
     * seeded random directions up to [maxTurn] radians either side of its heading.
     */
    private fun split(ball: Ball, extraBalls: Int, maxTurn: Float) {
        val speed = sqrt(ball.vx * ball.vx + ball.vy * ball.vy)
        if (speed == 0f) return
        val heading = atan2(ball.vy, ball.vx)
        // One slice of [-maxTurn, maxTurn] per ball, in a seeded random order, and a seeded
        // random angle inside each slice, so the balls scatter yet never bunch up.
        val count = extraBalls + 1
        val slices = IntArray(count) { it }
        for (i in count - 1 downTo 1) {
            val j = abilityRandom.nextInt(i + 1)
            val swap = slices[i]
            slices[i] = slices[j]
            slices[j] = swap
        }
        val sliceWidth = 2 * maxTurn / count
        val newBalls = List(extraBalls) { Ball(ball.x, ball.y, state = BallState.Flying) }
        for ((index, target) in (listOf(ball) + newBalls).withIndex()) {
            val turn = -maxTurn + sliceWidth * (slices[index] + abilityRandom.nextFloat())
            aimBall(target, heading + turn, speed)
        }
        ballList += newBalls
    }

    /**
     * Points [ball] along [angle] at [speed]. An angle flatter than [GameConfig.minAimAngle] is
     * steepened to it, so no ball skims back and forth forever.
     */
    private fun aimBall(ball: Ball, angle: Float, speed: Float) {
        var vx = cos(angle) * speed
        var vy = sin(angle) * speed
        val minVy = sin(config.minAimAngle) * speed
        if (abs(vy) < minVy) {
            vy = if (vy > 0f) minVy else -minVy
            vx = (if (vx < 0f) -1f else 1f) * cos(config.minAimAngle) * speed
        }
        ball.vx = vx
        ball.vy = vy
    }

    /** Ability effects last one flight. */
    private fun resetEffects(ball: Ball) {
        ball.size = 1f
        ball.pierce = 0
        ball.insideBricks.clear()
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
            shotAbility?.let { ability ->
                events += GameEvent.AbilityTriggered(ability.id, column, row)
                ability.onFirstHit(abilityContext, column, row)
            }
        }
        val ball = currentBall
        if (ball != null && ball === ballList.first()) {
            shotAbility?.onFirstBallHit(abilityContext, ball, column, row)
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
        if (levelRows != null && round >= levelRows && board.bricks().none()) {
            // A level is won only once its last row has come and every brick is broken.
            phase = RoundPhase.LevelComplete
            events += GameEvent.LevelCompleted(round, points)
            return
        }
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
        // Balls split off last round are gone; the new round's ball joins.
        while (ballList.size > ballCount) ballList.removeAt(ballList.lastIndex)
        while (ballList.size < ballCount) ballList += Ball(launchX, floorY)
        for (ball in ballList) {
            ball.x = launchX
            ball.y = floorY
            ball.vx = 0f
            ball.vy = 0f
            ball.state = BallState.Waiting
            resetEffects(ball)
        }
        // A level's rows stop after its last row round; the rest is clearing the board.
        if (levelRows == null || round <= levelRows) spawnRow(config.rowValues.valueFor(round))
        stepsInPhase = 0
        phase = RoundPhase.Aiming
        events += GameEvent.RoundStarted(round, ballCount)
        earnAbilities()
    }

    /**
     * Reaching an ability's round earns it and keeps it active (selectable) for the rest of the game.
     * Earning one doesn't change which ability is picked.
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

    /** Fills row 0 with bricks worth [value] in seeded random columns. */
    private fun spawnRow(value: Long) {
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

        /** Mixed into the seed for the abilities' random stream. */
        private const val ABILITY_STREAM = 0x5EED5B117L
    }
}
