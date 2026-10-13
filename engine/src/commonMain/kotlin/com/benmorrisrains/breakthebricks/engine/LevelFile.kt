package com.benmorrisrains.breakthebricks.engine

/**
 * The level file format: plain text, one block per level, so new levels ship as data.
 *
 * ```
 * # Comments run from # to the end of the line; blank lines are ignored.
 * level 1               # starts a block; levels are numbered 1, 2, 3, ...
 * name Warm Up          # the rest of the line
 * seed 101              # fixes the starting rows and every new row
 * rows 6                # rounds that bring a new row; then break every brick to pass
 * bricks 1 2            # bricks per new row, min and max
 * stars 9 7             # clear within 9 rounds for 2 stars, within 7 for 3 (any clear earns 1)
 * ahead 2               # optional: new rows are worth what they'd be 2 rounds later
 * row . . . 1 . . .     # optional starting bricks, top row first, one value or . per column
 * ```
 */
object LevelFile {
    fun parse(text: String, columns: Int = GameConfig().columns): List<Level> {
        val levels = mutableListOf<Level>()
        var block: Block? = null

        fun finish() {
            val done = block ?: return
            levels += done.build()
            block = null
        }

        text.lineSequence().forEachIndexed { index, raw ->
            val lineNumber = index + 1
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            val key = line.substringBefore(' ')
            val rest = line.substringAfter(' ', "").trim()
            fun fail(message: String): Nothing = throw LevelFormatException(lineNumber, message)
            fun ints(count: Int): List<Long> {
                val parts = rest.split(WHITESPACE).filter { it.isNotEmpty() }
                if (parts.size != count) fail("'$key' takes $count number${if (count > 1) "s" else ""}")
                return parts.map { it.toLongOrNull() ?: fail("'$it' is not a number") }
            }

            if (key == "level") {
                finish()
                val number = ints(1).single().toInt()
                if (number != levels.size + 1) fail("expected level ${levels.size + 1}, found level $number")
                block = Block(number, lineNumber)
                return@forEachIndexed
            }
            val current = block ?: fail("'$key' comes before the first 'level' line")
            when (key) {
                "name" -> current.name = rest.ifEmpty { fail("'name' needs a name") }
                "seed" -> current.seed = ints(1).single()
                "rows" -> current.rows = ints(1).single().toInt()
                "bricks" -> current.bricks = ints(2).let { (min, max) ->
                    if (min < 1 || max < min || max > columns) fail("bricks must be 1 <= min <= max <= $columns")
                    min.toInt() to max.toInt()
                }
                "stars" -> current.stars = ints(2).let { (two, three) ->
                    if (three < 1 || three > two) fail("stars are rounds for 2 then 3 stars, 0 < three <= two")
                    two.toInt() to three.toInt()
                }
                "ahead" -> current.ahead = ints(1).single().toInt().also { if (it < 0) fail("ahead can't be negative") }
                "row" -> {
                    val cells = rest.split(WHITESPACE).filter { it.isNotEmpty() }
                    if (cells.size != columns) fail("a row has $columns cells, found ${cells.size}")
                    current.layout += cells.map { cell ->
                        if (cell == ".") null else cell.toLongOrNull()?.takeIf { it >= 1 } ?: fail("'$cell' is not a brick value or .")
                    }
                }
                else -> fail("unknown key '$key'")
            }
        }
        finish()
        return levels
    }

    private val WHITESPACE = Regex("\\s+")

    private class Block(val number: Int, val line: Int) {
        var name: String? = null
        var seed: Long? = null
        var rows: Int? = null
        var bricks: Pair<Int, Int>? = null
        var stars: Pair<Int, Int>? = null
        var ahead = 0
        val layout = mutableListOf<List<Long?>>()

        fun build(): Level {
            fun missing(key: String): Nothing = throw LevelFormatException(line, "level $number has no '$key'")
            val rows = rows ?: missing("rows")
            if (rows < 1) throw LevelFormatException(line, "level $number: rows must be at least 1")
            val (min, max) = bricks ?: missing("bricks")
            val (two, three) = stars ?: missing("stars")
            return Level(
                number = number,
                name = name ?: missing("name"),
                seed = seed ?: missing("seed"),
                rowCount = rows,
                minBricksPerRow = min,
                maxBricksPerRow = max,
                twoStarRounds = two,
                threeStarRounds = three,
                layout = layout.toList(),
                valuesAhead = ahead,
            )
        }
    }
}

class LevelFormatException(val line: Int, message: String) : IllegalArgumentException("line $line: $message")
