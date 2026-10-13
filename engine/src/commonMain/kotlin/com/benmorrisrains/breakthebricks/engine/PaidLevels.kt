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
# Same rules as the free levels; `power` lends a power-up for that level from the first shot.

level 11
name Twin Towers
seed 1414
rows 13
bricks 4 7
ahead 1
stars 17 14
row . 12 . . . 12 .
row . 10 . . . 10 .
row 4 . . 4 . . 4

level 12
name Heavy Hitter
seed 1218
rows 12
bricks 4 7
ahead 2
power big_ball
stars 17 14
row 9 9 9 . 9 9 9
row . . . . . . .
row 4 4 . . . 4 4

level 13
name Zigzag
seed 1318
rows 12
bricks 5 7
ahead 2
stars 17 14
row 10 . . . . . 10
row . 8 . . . 8 .
row . . 5 . 5 . .

level 14
name Overtime
seed 1112
rows 12
bricks 5 6
ahead 2
stars 18 15
row 10 10 . 10 . 10 10
row . 7 . 7 . 7 .
row 4 . . 4 . . 4

level 15
name Crossfire
seed 1515
rows 13
bricks 5 7
ahead 1
stars 19 16
row 12 . . 12 . . 12
row . . 9 . 9 . .
row 5 . . . . . 5

level 16
name Scatter
seed 1619
rows 12
bricks 6 7
ahead 2
power splitter
stars 18 15
row 12 . . 12 . . 12
row . . 10 . 10 . .
row . 5 . . . 5 .

level 17
name Picket Fence
seed 2021
rows 12
bricks 4 6
ahead 3
stars 19 16
row 16 . 16 . 16 . 16
row . 12 . 12 . 12 .
row 5 . . 5 . . 5

level 18
name Gatekeeper
seed 1718
rows 13
bricks 4 6
ahead 2
stars 19 16
row 14 14 14 . 14 14 14
row . . . . . . .
row 5 . 5 . 5 . 5

level 19
name Diamond
seed 2330
rows 13
bricks 4 7
ahead 2
stars 20 17
row . . . 20 . . .
row . . 14 . 14 . .
row . 6 . . . 6 .

level 20
name Bunker
seed 2222
rows 14
bricks 4 7
ahead 1
stars 20 17
row 18 18 18 . 18 18 18
row 10 . . . . . 10
row 5 . . . . . 5

level 21
name Core Sample
seed 2121
rows 13
bricks 5 7
ahead 2
power drill
stars 18 15
row . 16 . 16 . 16 .
row . 14 . 14 . 14 .
row . 10 . 10 . 10 .

level 22
name Staircase
seed 1921
rows 13
bricks 4 7
ahead 2
stars 20 17
row 16 16 . . . . .
row 12 12 12 . . . .
row 4 4 . . . . .

level 23
name Hourglass
seed 1819
rows 14
bricks 4 6
ahead 1
stars 19 16
row 14 12 10 . 10 12 14
row . . 8 . 8 . .
row . . . 6 . . .

level 24
name Maze Runner
seed 2828
rows 12
bricks 4 7
ahead 3
stars 18 15
row 26 . 26 26 26 . 26
row . . . . 18 . .
row 8 . . . . . 8

level 25
name Long Haul
seed 2552
rows 14
bricks 5 7
ahead 1
stars 20 17
row 22 . . 22 . . 22
row . 16 . . . 16 .
row 6 . . 6 . . 6

level 26
name Cluster Bomb
seed 2634
rows 14
bricks 6 7
ahead 3
power grenade
stars 17 14
row 20 20 . . . 20 20
row 20 20 . . . 20 20
row . . 8 8 8 . .

level 27
name Avalanche
seed 2932
rows 12
bricks 4 6
ahead 3
stars 20 17
row 24 24 . 24 . 24 24
row 16 . 16 . 16 . 16
row . 5 . . . 5 .

level 28
name Teeth
seed 2430
rows 13
bricks 5 7
ahead 2
stars 20 17
row 20 . 20 . 20 . 20
row 14 . 14 . 14 . 14
row . 6 . . . 6 .

level 29
name Iron Curtain
seed 2735
rows 13
bricks 4 6
ahead 3
stars 19 16
row 22 22 22 . 22 22 22
row . . . . . . .
row 6 . 6 . 6 . 6

level 30
name Final Brick
seed 3054
rows 13
bricks 4 6
ahead 3
stars 20 17
row 26 26 26 . 26 26 26
row . . . . . . .
row 6 . 6 . 6 . 6
"""
}

/** Every level in order: the free ones, then the paid ones. */
object Levels {
    val all: List<Level> by lazy { FreeLevels.all + PaidLevels.all }

    operator fun get(number: Int): Level = all[number - 1]
}
