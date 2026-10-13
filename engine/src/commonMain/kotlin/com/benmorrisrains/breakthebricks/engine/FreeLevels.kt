package com.benmorrisrains.breakthebricks.engine

/**
 * Levels 1 to 10, free for everyone. Written in the [LevelFile] format so the same text could
 * ship as a data file; levels 11 and up are paid and come later. Star points are tuned against
 * a simple test bot (see FreeLevelsTest): it passes every level, and 3 stars needs more than it
 * scores. Random aiming fails every level often (Ben found the first version too easy).
 */
object FreeLevels {
    const val COUNT = 10

    val all: List<Level> by lazy { LevelFile.parse(TEXT) }

    operator fun get(number: Int): Level = all[number - 1]

    const val TEXT = """
# Break The Bricks: free levels 1 to 10.
# Each level starts with bricks low enough that they must be cleared before the goal;
# later levels add more bricks per row and rows worth what later rounds are worth.

level 1
name Warm Up
seed 101
goal 6
bricks 2 3
stars 22 29
row . . . . . . .
row . . . . . . .
row . 2 . 2 . 2 .
row 1 . 1 . 1 . 1

level 2
name Two by Two
seed 202
goal 7
bricks 1 3
stars 28 37
row . . . . . . .
row . . . . . . .
row 2 2 . . . 2 2
row . 2 2 . 2 2 .

level 3
name Doorstep
seed 303
goal 8
bricks 2 4
ahead 2
stars 61 80
row . . 2 2 2 . .
row . . . . . . .
row . . . . . . .
row 3 . . 3 . . 3

level 4
name Checkers
seed 404
goal 9
bricks 2 4
ahead 2
stars 100 130
row 2 . 2 . 2 . 2
row . 2 . 2 . 2 .
row 3 . 3 . 3 . 3

level 5
name Pull the Pin
seed 505
goal 10
bricks 2 4
ahead 2
stars 145 190
row . . . . . . .
row . . . . . . .
row . 3 3 3 3 3 .
row 2 . . . . . 2

level 6
name Brick Wall
seed 606
goal 11
bricks 4 6
ahead 3
stars 290 380
row 3 3 3 . 3 3 3
row . . . . . . .
row . . . . . . .
row 2 . 2 . 2 . 2

level 7
name Pillars
seed 707
goal 11
bricks 3 6
ahead 3
stars 310 405
row 4 . . 4 . . 4
row 3 . . 3 . . 3
row 2 . . 2 . . 2

level 8
name Funnel
seed 808
goal 12
bricks 4 6
ahead 4
stars 600 790
row 5 4 . . . 4 5
row . 3 . . . 3 .
row . . 2 . 2 . .
row 2 . . . . . 2

level 9
name Fortress
seed 909
goal 12
bricks 3 6
ahead 4
stars 640 830
row . 6 6 6 6 6 .
row . 4 . . . 4 .
row 4 . . . . . 4

level 10
name Last Free Level
seed 1010
goal 13
bricks 3 6
ahead 4
stars 900 1180
row 8 . 8 . 8 . 8
row . 6 . 6 . 6 .
row 4 . . 4 . . 4
"""
}
