package com.benmorrisrains.breakthebricks.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The real [BallPhysics]: ball movement and collisions against the walls, the ceiling, the floor
 * and the brick grid.
 *
 * World units are cells (see [GameConfig]); each brick fills the unit square of its cell. The
 * ball is a circle, so its centre is swept as a ray against each brick grown by the ball radius:
 * four flat faces plus four rounded corners. A step finds the earliest contact, moves the ball
 * there, reflects it and sweeps the time that is left. Nothing is sampled, so no speed can tunnel
 * through a brick or slip between two touching bricks, and only cells inside the swept path are
 * tested.
 *
 * Reflections only flip vx or vy, so speed never changes:
 * - a face flips the velocity component that points into it;
 * - a corner reflects off one of its two edge normals, the one the ball pushes into hardest,
 *   which always sends the ball away from the corner;
 * - contacts at the same moment (two bricks, or a brick and a wall) each count once and the ball
 *   reflects once, flipping each axis at most once.
 *
 * Math runs in Double inside a step and is stored back into the Float ball, the same way on every
 * platform, so a seed plus inputs always replays the same game.
 */
object GridPhysics : BallPhysics {
    /** Contacts per step before the rest of the step is dropped. Only degenerate setups get here. */
    private const val MAX_CONTACTS_PER_STEP = 32

    /** Contacts within this many cells of travel of each other happen at the same moment. */
    private const val SAME_MOMENT = 1e-9

    /** How far (cells) rounding may leave a ball past a surface it is touching. */
    private const val SLOP = 1e-5

    /** Contacts a sweep has room for before it grows; a moment rarely has more than three. */
    private const val INITIAL_CAPACITY = 8

    /** Centre height of a normal-sized ball resting on the floor. */
    fun restingY(config: GameConfig): Float = config.rows - config.ballRadius

    /**
     * Moves [ball] for [dt] seconds, bouncing off the walls, the ceiling and bricks, and reports
     * each contact to [contacts]. A brick is solid while it is on [board] with hits above 0, and the
     * board is read again after every contact. When one contact touches two bricks at once, both
     * are reported, ordered by row and then column, before the ball reflects once.
     *
     * A ball with [Ball.pierce] left passes through the bricks it hits, using up 1 per brick, as
     * long as it has enough left for every brick in that contact; otherwise it bounces as usual.
     * The bricks it passes through stay see-through to it until it is clear of all of them, so each
     * is hit once and a ball that runs out inside a stack of bricks can't be trapped bouncing there.
     * A [Ball.ghost] ball passes through any number of bricks this way and stops being a ghost once
     * it is clear of them. A ball that [Ball.exitsAtWall] stops at the first side wall
     * or ceiling it meets and reports [BallContacts.exited] instead of bouncing.
     *
     * Returns true when the ball lands; it then rests at [restingY] with zero velocity. Physics never
     * changes the board or [Ball.state].
     */
    override fun step(ball: Ball, board: Board, config: GameConfig, dt: Float, contacts: BallContacts): Boolean {
        require(dt >= 0f) { "dt must not be negative" }
        // Each ball keeps its own sweep, so a step allocates nothing and two games never share one.
        val sweep = ball.gridSweep ?: Sweep().also { ball.gridSweep = it }
        sweep.begin(ball, board, ball.radius(config).toDouble())
        val landed = sweep.run(dt.toDouble(), contacts)
        ball.x = sweep.x.toFloat()
        ball.y = sweep.y.toFloat()
        ball.vx = sweep.vx.toFloat()
        ball.vy = sweep.vy.toFloat()
        ball.pierce = sweep.pierce
        ball.ghost = sweep.ghost
        return landed
    }

    private const val WALL = 0
    private const val FLOOR = 1
    private const val BRICK = 2
    private const val CEILING = 3

    /**
     * One ball's step through the field. It is reused from step to step, and keeps the contacts
     * it finds in flat arrays rather than objects.
     */
    internal class Sweep {
        private lateinit var ball: Ball
        private lateinit var board: Board
        private var radius = 0.0
        var x = 0.0
            private set
        var y = 0.0
            private set
        var vx = 0.0
            private set
        var vy = 0.0
            private set
        var pierce = 0
            private set
        var ghost = false
            private set
        private var exitsAtWall = false
        private var width = 0.0
        private var floorY = 0.0

        /**
         * The contacts at the earliest moment found so far: [count] of them, each with its time,
         * axis normal (nx, ny) pointing out of the surface, surface ([WALL], [CEILING], [FLOOR] or [BRICK]) and,
         * for a brick, its cell index (row * columns + column, so cell order is row then column).
         */
        private var count = 0
        private var times = DoubleArray(INITIAL_CAPACITY)
        private var normalsX = IntArray(INITIAL_CAPACITY)
        private var normalsY = IntArray(INITIAL_CAPACITY)
        private var surfaces = IntArray(INITIAL_CAPACITY)
        private var cells = IntArray(INITIAL_CAPACITY)
        private var first = 0.0
        private var window = 0.0

        /** Bricks hit at one moment, distinct and in cell order. */
        private var bricksHit = IntArray(INITIAL_CAPACITY)

        /** Cells of bricks the ball has passed through; see-through until it is clear of them all. */
        private val inside: MutableSet<Int> get() = ball.insideBricks

        fun begin(ball: Ball, board: Board, radius: Double) {
            this.ball = ball
            this.board = board
            this.radius = radius
            x = ball.x.toDouble()
            y = ball.y.toDouble()
            vx = ball.vx.toDouble()
            vy = ball.vy.toDouble()
            pierce = ball.pierce
            ghost = ball.ghost
            exitsAtWall = ball.exitsAtWall
            width = board.columns.toDouble()
            floorY = board.rows - radius
        }

        /** Returns true when the ball lands. */
        fun run(seconds: Double, contacts: BallContacts): Boolean {
            var remaining = seconds
            var contactsLeft = MAX_CONTACTS_PER_STEP
            while (remaining > 0.0 && (vx != 0.0 || vy != 0.0)) {
                if (contactsLeft-- == 0) return false
                leaveBricks()
                count = 0
                window = SAME_MOMENT / sqrt(vx * vx + vy * vy)
                findBoundaryContacts(remaining)
                findBrickContacts(remaining)
                if (count == 0) {
                    x += vx * remaining
                    y += vy * remaining
                    return false
                }

                x += vx * first
                y += vy * first
                remaining -= first

                val hitCount = collectBricksHit()
                if (hitCount > 0) {
                    // Hits are reported with the ball at the contact point, before it reflects.
                    ball.x = x.toFloat()
                    ball.y = y.toFloat()
                }
                for (i in 0 until hitCount) contacts.brickHit(bricksHit[i] % board.columns, bricksHit[i] / board.columns)
                if (exitsAtWall && (anyOn(WALL) || anyOn(CEILING))) {
                    vx = 0.0
                    vy = 0.0
                    contacts.exited()
                    return false
                }
                if (anyOn(WALL) || anyOn(CEILING)) contacts.wallBounce()
                val passThrough = hitCount > 0 && (ghost || pierce >= hitCount)
                if (passThrough) {
                    if (!ghost) pierce -= hitCount
                    for (i in 0 until hitCount) inside += bricksHit[i]
                }

                if (anyOn(FLOOR)) {
                    x = x.coerceIn(radius, width - radius)
                    y = floorY
                    vx = 0.0
                    vy = 0.0
                    return true
                }
                // A ball passing through bricks only bounces off the walls it meets at that moment.
                var flipX = false
                var flipY = false
                for (i in 0 until count) {
                    if (passThrough && surfaces[i] == BRICK) continue
                    if (normalsX[i] * vx < 0.0) flipX = true
                    if (normalsY[i] * vy < 0.0) flipY = true
                }
                if (flipX) vx = -vx
                if (flipY) vy = -vy
            }
            return false
        }

        /**
         * Records a contact [time] from now, keeping only those within [SAME_MOMENT] of the
         * earliest. A contact later than that window can never come back into it, since the
         * earliest time only moves earlier.
         */
        private fun add(time: Double, nx: Int, ny: Int, surface: Int, cell: Int) {
            if (count > 0 && time > first + window) return
            if (count == 0 || time < first) {
                first = time
                // Drop the contacts the new earliest one leaves behind, keeping the rest in order.
                var kept = 0
                for (i in 0 until count) {
                    if (times[i] <= first + window) {
                        times[kept] = times[i]
                        normalsX[kept] = normalsX[i]
                        normalsY[kept] = normalsY[i]
                        surfaces[kept] = surfaces[i]
                        cells[kept] = cells[i]
                        kept++
                    }
                }
                count = kept
            }
            if (count == times.size) grow()
            times[count] = time
            normalsX[count] = nx
            normalsY[count] = ny
            surfaces[count] = surface
            cells[count] = cell
            count++
        }

        private fun grow() {
            val size = times.size * 2
            times = times.copyOf(size)
            normalsX = normalsX.copyOf(size)
            normalsY = normalsY.copyOf(size)
            surfaces = surfaces.copyOf(size)
            cells = cells.copyOf(size)
        }

        private fun anyOn(surface: Int): Boolean {
            for (i in 0 until count) if (surfaces[i] == surface) return true
            return false
        }

        /** Fills [bricksHit] with the distinct brick cells of this moment, in cell order. */
        private fun collectBricksHit(): Int {
            if (bricksHit.size < count) bricksHit = IntArray(times.size)
            var hits = 0
            for (i in 0 until count) {
                if (surfaces[i] != BRICK) continue
                val cell = cells[i]
                // Insertion sort: there are only ever a few.
                var at = hits
                while (at > 0 && bricksHit[at - 1] > cell) at--
                if (at > 0 && bricksHit[at - 1] == cell) continue
                for (j in hits downTo at + 1) bricksHit[j] = bricksHit[j - 1]
                bricksHit[at] = cell
                hits++
            }
            return hits
        }

        /** Left and right walls, ceiling and floor are planes the ball centre stays `radius` away from. */
        private fun findBoundaryContacts(remaining: Double) {
            if (vx < 0.0) addPlane((radius - x) / vx, 1, 0, WALL, remaining)
            if (vx > 0.0) addPlane((width - radius - x) / vx, -1, 0, WALL, remaining)
            if (vy < 0.0) addPlane((radius - y) / vy, 0, 1, CEILING, remaining)
            if (vy > 0.0) addPlane((floorY - y) / vy, 0, -1, FLOOR, remaining)
        }

        private fun addPlane(time: Double, nx: Int, ny: Int, surface: Int, remaining: Double) {
            // A ball already past a boundary is caught at once rather than escaping the field.
            val t = max(0.0, time)
            if (t <= remaining) add(t, nx, ny, surface, -1)
        }

        /** Tests only the cells the ball can reach during [remaining]. */
        private fun findBrickContacts(remaining: Double) {
            val endX = x + vx * remaining
            val endY = y + vy * remaining
            val reach = radius + SLOP
            val firstColumn = max(0, floor(min(x, endX) - reach).toInt())
            val lastColumn = min(board.columns - 1, floor(max(x, endX) + reach).toInt())
            val firstRow = max(0, floor(min(y, endY) - reach).toInt())
            val lastRow = min(board.rows - 1, floor(max(y, endY) + reach).toInt())
            for (row in firstRow..lastRow) {
                for (column in firstColumn..lastColumn) {
                    val brick = board[column, row] ?: continue
                    if (brick.hits > 0 && row * board.columns + column !in inside) {
                        sweepBrick(row * board.columns + column, column.toDouble(), row.toDouble(), remaining)
                    }
                }
            }
        }

        /** Once the ball is clear of every brick it passed through, they are solid to it again. */
        private fun leaveBricks() {
            if (inside.isEmpty()) return
            val reach = radius + SLOP
            val stillIn = inside.any { cell ->
                val column = cell % board.columns
                val row = cell / board.columns
                val dx = x - x.coerceIn(column.toDouble(), column + 1.0)
                val dy = y - y.coerceIn(row.toDouble(), row + 1.0)
                board[column, row] != null && dx * dx + dy * dy < reach * reach
            }
            if (!stillIn) {
                inside.clear()
                // A ghost is solid again once it is through.
                ghost = false
            }
        }

        private fun sweepBrick(cell: Int, left: Double, top: Double, remaining: Double) {
            val right = left + 1.0
            val bottom = top + 1.0

            // Faces, grown by the radius. The ball must start on the outside of the face.
            if (vy < 0.0) addFace(bottom + radius, y, vy, x, vx, left, right, 0, 1, cell, remaining)
            if (vy > 0.0) addFace(top - radius, y, vy, x, vx, left, right, 0, -1, cell, remaining)
            if (vx > 0.0) addFace(left - radius, x, vx, y, vy, top, bottom, -1, 0, cell, remaining)
            if (vx < 0.0) addFace(right + radius, x, vx, y, vy, top, bottom, 1, 0, cell, remaining)

            // Rounded corners: the ball centre meets a circle of the ball's radius around each corner.
            addCorner(left, top, -1, -1, cell, remaining)
            addCorner(right, top, 1, -1, cell, remaining)
            addCorner(left, bottom, -1, 1, cell, remaining)
            addCorner(right, bottom, 1, 1, cell, remaining)
        }

        /**
         * A face at `along = face`, spanning [from]..[to] on the other axis. [along] and [speed] are the
         * ball's position and velocity across the face; [across] and [drift] are along it.
         */
        private fun addFace(
            face: Double, along: Double, speed: Double, across: Double, drift: Double,
            from: Double, to: Double, nx: Int, ny: Int, cell: Int, remaining: Double,
        ) {
            val outsideBy = if (speed < 0.0) along - face else face - along
            if (outsideBy < -SLOP) return
            val t = max(0.0, (face - along) / speed)
            if (t > remaining) return
            val hitAt = across + drift * t
            if (hitAt in from..to) add(t, nx, ny, BRICK, cell)
        }

        /**
         * The corner at ([cornerX], [cornerY]) only owns contacts in its own quadrant (signs [sx], [sy]);
         * the faces own the rest.
         */
        private fun addCorner(
            cornerX: Double, cornerY: Double, sx: Int, sy: Int, cell: Int, remaining: Double,
        ) {
            val dx = x - cornerX
            val dy = y - cornerY
            val a = vx * vx + vy * vy
            val b = 2.0 * (dx * vx + dy * vy)
            if (b >= 0.0) return // Moving away from the corner, so it can't get closer.
            val c = dx * dx + dy * dy - radius * radius
            val discriminant = b * b - 4.0 * a * c
            if (discriminant < 0.0) return
            val t = if (c <= 0.0) 0.0 else (-b - sqrt(discriminant)) / (2.0 * a)
            if (t > remaining) return
            val px = dx + vx * t
            val py = dy + vy * t
            if (px * sx <= 0.0 || py * sy <= 0.0) return
            // Reflect off the edge normal the ball pushes into hardest. Since the ball is moving into
            // the corner, flipping that component alone always turns it away.
            val pushX = vx * px
            val pushY = vy * py
            if (pushX <= pushY) {
                add(t, sx, 0, BRICK, cell)
            } else {
                add(t, 0, sy, BRICK, cell)
            }
        }
    }
}
