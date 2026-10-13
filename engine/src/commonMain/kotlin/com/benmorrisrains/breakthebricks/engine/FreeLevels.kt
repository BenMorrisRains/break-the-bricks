package com.benmorrisrains.breakthebricks.engine

/**
 * Levels 1 to 10, free for everyone. Written in the [LevelFile] format so the same text could
 * ship as a data file; levels 11 and up are paid and come later. Star points are tuned against
 * a simple test bot (see FreeLevelsTest): it passes every level, and 3 stars needs more than it scores.
 */
object FreeLevels {
    const val COUNT = 10

    val all: List<Level> by lazy { LevelFile.parse(TEXT) }

    operator fun get(number: Int): Level = all[number - 1]

    const val TEXT = """
# Break The Bricks: free levels 1 to 10.
# Fewer, smaller rows early on; longer goals and starting bricks later.

level 1
name Warm Up
seed 101
goal 5
bricks 1 2
stars 8 10

level 2
name Two by Two
seed 202
goal 6
bricks 1 3
stars 11 15

level 3
name Doorstep
seed 303
goal 7
bricks 1 3
stars 17 23
row . . 1 1 1 . .

level 4
name Checkers
seed 404
goal 8
bricks 2 3
stars 28 37
row 1 . 1 . 1 . 1
row . 1 . 1 . 1 .

level 5
name Pull the Pin
seed 505
goal 9
bricks 2 4
stars 46 62

level 6
name Brick Wall
seed 606
goal 10
bricks 2 4
stars 70 92
row 2 2 2 . 2 2 2

level 7
name Pillars
seed 707
goal 10
bricks 2 5
stars 82 108
row 3 . . 3 . . 3
row 2 . . 2 . . 2

level 8
name Funnel
seed 808
goal 11
bricks 2 5
stars 115 150
row 4 3 . . . 3 4
row . 2 . . . 2 .

level 9
name Fortress
seed 909
goal 12
bricks 3 5
stars 190 250
row . 5 5 5 5 5 .
row . 3 . . . 3 .

level 10
name Last Free Level
seed 1010
goal 13
bricks 3 6
stars 275 360
row 6 . 6 . 6 . 6
row . 4 . 4 . 4 .
"""
}
