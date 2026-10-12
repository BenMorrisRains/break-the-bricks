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

    /** Centre height of a ball resting on the floor. */
    fun restingY(config: GameConfig): Float = config.rows - config.ballRadius

    /**
     * Moves [ball] for [dt] seconds, bouncing off the walls, the ceiling and bricks, and reports
     * each contact to [contacts]. A brick is solid while it is on [board] with hits above 0, and the
     * board is read again after every contact. When one contact touches two bricks at once, both
     * are reported, ordered by row and then column, before the ball reflects once.
     *
     * Returns true when the ball lands; it then rests at [restingY] with zero velocity. Physics never
     * changes the board or [Ball.state].
     */
    override fun step(ball: Ball, board: Board, config: GameConfig, dt: Float, contacts: BallContacts): Boolean {
        require(dt >= 0f) { "dt must not be negative" }
        val sweep = Sweep(
            board = board,
            radius = config.ballRadius.toDouble(),
            x = ball.x.toDouble(),
            y = ball.y.toDouble(),
            vx = ball.vx.toDouble(),
            vy = ball.vy.toDouble(),
        )
        val landed = sweep.run(dt.toDouble(), contacts)
        ball.x = sweep.x.toFloat()
        ball.y = sweep.y.toFloat()
        ball.vx = sweep.vx.toFloat()
        ball.vy = sweep.vy.toFloat()
        return landed
    }

    private enum class Surface { Wall, Floor, Brick }

    /**
     * One possible contact: [time] from now, the axis normal (nx, ny) pointing out of the surface,
     * and for a brick its [cell] index (row * columns + column, so cell order is row then column).
     */
    private class Contact(val time: Double, val nx: Int, val ny: Int, val surface: Surface, val cell: Int = -1)

    private class Sweep(
        val board: Board,
        val radius: Double,
        var x: Double,
        var y: Double,
        var vx: Double,
        var vy: Double,
    ) {
        private val width = board.columns.toDouble()
        private val floorY = board.rows - radius

        /** Returns true when the ball lands. */
        fun run(seconds: Double, contacts: BallContacts): Boolean {
            var remaining = seconds
            var contactsLeft = MAX_CONTACTS_PER_STEP
            while (remaining > 0.0 && (vx != 0.0 || vy != 0.0)) {
                if (contactsLeft-- == 0) return false
                val found = ArrayList<Contact>()
                findBoundaryContacts(remaining, found)
                findBrickContacts(remaining, found)
                if (found.isEmpty()) {
                    x += vx * remaining
                    y += vy * remaining
                    return false
                }

                val first = found.minOf { it.time }
                val window = SAME_MOMENT / sqrt(vx * vx + vy * vy)
                val now = found.filter { it.time <= first + window }
                x += vx * first
                y += vy * first
                remaining -= first

                now.filter { it.surface == Surface.Brick }
                    .map { it.cell }
                    .distinct()
                    .sorted()
                    .forEach { contacts.brickHit(it % board.columns, it / board.columns) }
                if (now.any { it.surface == Surface.Wall }) contacts.wallBounce()

                if (now.any { it.surface == Surface.Floor }) {
                    x = x.coerceIn(radius, width - radius)
                    y = floorY
                    vx = 0.0
                    vy = 0.0
                    return true
                }
                val flipX = now.any { it.nx * vx < 0.0 }
                val flipY = now.any { it.ny * vy < 0.0 }
                if (flipX) vx = -vx
                if (flipY) vy = -vy
            }
            return false
        }

        /** Left and right walls, ceiling and floor are planes the ball centre stays `radius` away from. */
        private fun findBoundaryContacts(remaining: Double, found: MutableList<Contact>) {
            if (vx < 0.0) addPlane((radius - x) / vx, 1, 0, Surface.Wall, remaining, found)
            if (vx > 0.0) addPlane((width - radius - x) / vx, -1, 0, Surface.Wall, remaining, found)
            if (vy < 0.0) addPlane((radius - y) / vy, 0, 1, Surface.Wall, remaining, found)
            if (vy > 0.0) addPlane((floorY - y) / vy, 0, -1, Surface.Floor, remaining, found)
        }

        private fun addPlane(time: Double, nx: Int, ny: Int, surface: Surface, remaining: Double, found: MutableList<Contact>) {
            // A ball already past a boundary is caught at once rather than escaping the field.
            val t = max(0.0, time)
            if (t <= remaining) found += Contact(t, nx, ny, surface)
        }

        /** Tests only the cells the ball can reach during [remaining]. */
        private fun findBrickContacts(remaining: Double, found: MutableList<Contact>) {
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
                    if (brick.hits > 0) sweepBrick(row * board.columns + column, column.toDouble(), row.toDouble(), remaining, found)
                }
            }
        }

        private fun sweepBrick(cell: Int, left: Double, top: Double, remaining: Double, found: MutableList<Contact>) {
            val right = left + 1.0
            val bottom = top + 1.0

            // Faces, grown by the radius. The ball must start on the outside of the face.
            if (vy < 0.0) addFace(bottom + radius, y, vy, x, vx, left, right, 0, 1, cell, remaining, found)
            if (vy > 0.0) addFace(top - radius, y, vy, x, vx, left, right, 0, -1, cell, remaining, found)
            if (vx > 0.0) addFace(left - radius, x, vx, y, vy, top, bottom, -1, 0, cell, remaining, found)
            if (vx < 0.0) addFace(right + radius, x, vx, y, vy, top, bottom, 1, 0, cell, remaining, found)

            // Rounded corners: the ball centre meets a circle of the ball's radius around each corner.
            addCorner(left, top, -1, -1, cell, remaining, found)
            addCorner(right, top, 1, -1, cell, remaining, found)
            addCorner(left, bottom, -1, 1, cell, remaining, found)
            addCorner(right, bottom, 1, 1, cell, remaining, found)
        }

        /**
         * A face at `along = face`, spanning [from]..[to] on the other axis. [along] and [speed] are the
         * ball's position and velocity across the face; [across] and [drift] are along it.
         */
        private fun addFace(
            face: Double, along: Double, speed: Double, across: Double, drift: Double,
            from: Double, to: Double, nx: Int, ny: Int, cell: Int, remaining: Double, found: MutableList<Contact>,
        ) {
            val outsideBy = if (speed < 0.0) along - face else face - along
            if (outsideBy < -SLOP) return
            val t = max(0.0, (face - along) / speed)
            if (t > remaining) return
            val hitAt = across + drift * t
            if (hitAt in from..to) found += Contact(t, nx, ny, Surface.Brick, cell)
        }

        /**
         * The corner at ([cornerX], [cornerY]) only owns contacts in its own quadrant (signs [sx], [sy]);
         * the faces own the rest.
         */
        private fun addCorner(
            cornerX: Double, cornerY: Double, sx: Int, sy: Int, cell: Int, remaining: Double, found: MutableList<Contact>,
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
                found += Contact(t, sx, 0, Surface.Brick, cell)
            } else {
                found += Contact(t, 0, sy, Surface.Brick, cell)
            }
        }
    }
}
