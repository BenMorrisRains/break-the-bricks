package com.benmorrisrains.breakthebricks.engine

/**
 * Levels 11 to 30, the paid levels, in the [LevelFile] format like [FreeLevels]. They open only
 * once the player has bought or unlocked them; the UI's LevelProgress asks. Every one has more
 * to break than level 10 (fuller rows, heavier starting bricks, rows worth later rounds), and
 * they get heavier as they go. Levels 12, 16, 21 and 26 each lend one power-up for the level,
 * on a layout it suits, to show it off. Seeds and star rounds are tuned against the test bot
 * as in FreeLevelsTest; see PaidLevelsTest.
 */
object PaidLevels {
    const val FIRST = FreeLevels.COUNT + 1
    const val COUNT = 20

    val all: List<Level> by lazy { LevelFile.parse(TEXT, first = FIRST) }

    operator fun get(number: Int): Level = all[number - FIRST]

    const val TEXT = """
# Break The Bricks: paid levels 11 to 30.
# Same rules as the free levels; `power` lends a power-up for one shot in that level.

level 11
name Twin Towers
seed 9333
rows 13
bricks 5 7
ahead 8
stars 17 14
row . 9 . . . 9 .
row . 8 . . . 8 .
row 3 . . 3 . . 3

level 12
name Heavy Hitter
seed 1250
rows 12
bricks 5 7
ahead 10
power big_ball
stars 16 13
row 4 4 4 . 4 4 4
row . . . . . . .
row 2 2 . . . 2 2

level 13
name Zigzag
seed 17156
rows 12
bricks 6 7
ahead 9
stars 16 13
row 6 . . . . . 6
row . 5 . . . 5 .
row . . 3 . 3 . .

level 14
name Overtime
seed 9031
rows 12
bricks 6 7
ahead 9
stars 17 14
row 6 6 . 6 . 6 6
row . 4 . 4 . 4 .
row 2 . . 2 . . 2

level 15
name Crossfire
seed 1515
rows 13
bricks 6 7
ahead 9
stars 18 15
row 6 . . 6 . . 6
row . . 4 . 4 . .
row 2 . . . . . 2

level 16
name Scatter
seed 25385
rows 12
bricks 6 7
ahead 10
power splitter
stars 18 14
row 7 . . 7 . . 7
row . . 6 . 6 . .
row . 3 . . . 3 .

level 17
name Picket Fence
seed 2021
rows 12
bricks 5 7
ahead 10
stars 17 14
row 10 . 10 . 10 . 10
row . 7 . 7 . 7 .
row 3 . . 3 . . 3

level 18
name Gatekeeper
seed 1718
rows 13
bricks 5 7
ahead 10
stars 18 15
row 7 7 7 . 7 7 7
row . . . . . . .
row 2 . 2 . 2 . 2

level 19
name Diamond
seed 10249
rows 13
bricks 5 7
ahead 10
stars 18 15
row . . . 10 . . .
row . . 7 . 7 . .
row . 3 . . . 3 .

level 20
name Bunker
seed 25979
rows 14
bricks 5 7
ahead 9
stars 19 16
row 9 9 9 . 9 9 9
row 5 . . . . . 5
row 2 . . . . . 2

level 21
name Core Sample
seed 17963
rows 13
bricks 6 7
ahead 10
power drill
stars 18 15
row . 8 . 8 . 8 .
row . 7 . 7 . 7 .
row . 5 . 5 . 5 .

level 22
name Staircase
seed 17759
rows 13
bricks 5 7
ahead 11
stars 18 15
row 8 8 . . . . .
row 6 6 6 . . . .
row 2 2 . . . . .

level 23
name Hourglass
seed 25576
rows 14
bricks 5 7
ahead 10
stars 20 16
row 8 7 6 . 6 7 8
row . . 5 . 5 . .
row . . . 4 . . .

level 24
name Maze Runner
seed 26585
rows 12
bricks 5 7
ahead 12
stars 18 15
row 13 . 13 13 13 . 13
row . . . . 9 . .
row 4 . . . . . 4

level 25
name Long Haul
seed 2552
rows 14
bricks 6 7
ahead 10
stars 20 17
row 11 . . 11 . . 11
row . 8 . . . 8 .
row 3 . . 3 . . 3

level 26
name Cluster Bomb
seed 2653
rows 14
bricks 6 7
ahead 9
power grenade
stars 20 16
row 12 12 . . . 12 12
row 12 12 . . . 12 12
row . . 5 5 5 . .

level 27
name Avalanche
seed 42527
rows 12
bricks 5 7
ahead 12
stars 19 16
row 14 14 . 14 . 14 14
row 10 . 10 . 10 . 10
row . 3 . . . 3 .

level 28
name Teeth
seed 34106
rows 13
bricks 6 7
ahead 11
stars 20 17
row 15 . 15 . 15 . 15
row 10 . 10 . 10 . 10
row . 4 . . . 4 .

level 29
name Iron Curtain
seed 42330
rows 13
bricks 5 7
ahead 12
stars 19 16
row 11 11 11 . 11 11 11
row . . . . . . .
row 3 . 3 . 3 . 3

level 30
name Final Brick
seed 3054
rows 13
bricks 5 7
ahead 12
stars 19 16
row 13 13 13 . 13 13 13
row . . . . . . .
row 3 . 3 . 3 . 3
"""
}

/** Every level in order: the free ones, then the paid ones. */
object Levels {
    val all: List<Level> by lazy { FreeLevels.all + PaidLevels.all }

    operator fun get(number: Int): Level = all[number - 1]
}
