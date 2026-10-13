package com.benmorrisrains.breakthebricks.ui

/**
 * Text on a brick. Fibonacci values outgrow a cell fast, so from 10,000 up they shorten:
 * 12K, 832K, 1.2M, 35B. At most four digits plus a suffix.
 */
fun formatBrickValue(value: Long): String {
    if (value < 10_000) return value.toString()
    val suffixes = "KMBTQ"
    var scaled = value.toDouble()
    var index = -1
    while (scaled >= 1000.0 && index < suffixes.length - 1) {
        scaled /= 1000.0
        index++
    }
    val suffix = suffixes[index]
    if (scaled >= 10.0) return "${scaled.toLong()}$suffix"
    val tenths = (scaled * 10).toLong()
    return if (tenths % 10 == 0L) "${tenths / 10}$suffix" else "${tenths / 10}.${tenths % 10}$suffix"
}

/** Brick number font size as a fraction of the cell, shrinking for longer labels. */
fun brickFontFraction(label: String): Float = minOf(0.34f, 1.1f / label.length.coerceAtLeast(1))

/** Score with thousands separators: 1179 -> "1,179". */
fun formatPoints(points: Long): String {
    val digits = points.toString().removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (points < 0) "-$grouped" else grouped
}

/**
 * HUD round text: "ROUND 7" in an endless game. In a level with 8 rows, "ROW 3/8" while rows
 * still come, then "CLEAR IT!" once only breaking the rest is left.
 */
fun roundLabel(round: Int, levelRows: Int?): String = when {
    levelRows == null -> "ROUND $round"
    round <= levelRows -> "ROW $round/$levelRows"
    else -> "CLEAR IT!"
}
