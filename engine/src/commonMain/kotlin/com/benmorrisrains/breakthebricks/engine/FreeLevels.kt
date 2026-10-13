package com.benmorrisrains.breakthebricks.engine

/**
 * Levels 1 to 10, free for everyone. Written in the [LevelFile] format so the same text could
 * ship as a data file; levels 11 and up are [PaidLevels]. Star rounds are tuned against
 * a simple test bot (see FreeLevelsTest): it clears every level, and 3 stars needs a clear at
 * least as fast as its. Random aiming fails every level often (Ben found the first version too easy).
 */
object FreeLevels {
    const val COUNT = 10

    val all: List<Level> by lazy { LevelFile.parse(TEXT) }

    operator fun get(number: Int): Level = all[number - 1]

    const val TEXT = """
# Break The Bricks: free levels 1 to 10.
# New rows come for the first `rows` rounds; then break every brick to pass.
# Later levels start with more bricks and rows worth what later rounds are worth.

level 1
name Warm Up
seed 101
rows 6
bricks 2 3
stars 8 6
row . . . . . . .
row . . . . . . .
row . 2 . 2 . 2 .
row 1 . 1 . 1 . 1

level 2
name Two by Two
seed 202
rows 7
bricks 1 3
stars 9 7
row . . . . . . .
row . . . . . . .
row 2 2 . . . 2 2
row . 2 2 . 2 2 .

level 3
name Doorstep
seed 303
rows 8
bricks 2 4
ahead 2
stars 11 8
row . . 2 2 2 . .
row . . . . . . .
row . . . . . . .
row 3 . . 3 . . 3

level 4
name Checkers
seed 404
rows 9
bricks 2 4
ahead 2
stars 12 9
row 2 . 2 . 2 . 2
row . 2 . 2 . 2 .
row 3 . 3 . 3 . 3

level 5
name Pull the Pin
seed 505
rows 10
bricks 2 4
ahead 1
stars 12 10
row . . . . . . .
row . . . . . . .
row . 3 3 3 3 3 .
row 2 . . . . . 2

level 6
name Brick Wall
seed 606
rows 10
bricks 3 5
ahead 1
stars 13 10
row 3 3 3 . 3 3 3
row . . . . . . .
row . . . . . . .
row 2 . 2 . 2 . 2

level 7
name Pillars
seed 707
rows 10
bricks 3 5
ahead 1
stars 13 10
row 4 . . 4 . . 4
row 3 . . 3 . . 3
row 2 . . 2 . . 2

level 8
name Funnel
seed 808
rows 11
bricks 3 5
ahead 2
stars 14 11
row 5 4 . . . 4 5
row . 3 . . . 3 .
row . . 2 . 2 . .
row 2 . . . . . 2

level 9
name Fortress
seed 909
rows 11
bricks 3 6
ahead 2
stars 15 12
row . 6 6 6 6 6 .
row . 4 . . . 4 .
row 4 . . . . . 4

level 10
name Last Free Level
seed 1010
rows 12
bricks 3 6
ahead 2
stars 17 14
row 8 . 8 . 8 . 8
row . 6 . 6 . 6 .
row 4 . . 4 . . 4
"""
}
