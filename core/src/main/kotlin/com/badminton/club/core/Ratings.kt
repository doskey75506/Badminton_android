package com.badminton.club.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

const val K_FACTOR = 32.0

fun expectedScore(ratingA: Double, ratingB: Double): Double =
    1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0))

fun marginMultiplier(scoreA: Int, scoreB: Int, ratingA: Double, ratingB: Double): Double {
    val pointDiff = abs(scoreA - scoreB)
    val eloDiff = abs(ratingA - ratingB)
    val mult = ln((pointDiff + 1).toDouble()) * (2.2 / (eloDiff * 0.001 + 2.2))
    return max(1.0, min(2.0, mult))
}

class RatingException(message: String) : IllegalArgumentException(message)

fun applyResult(
    ratingsBefore: Map<String, Double>,
    teamA: List<String>,
    teamB: List<String>,
    scoreA: Int,
    scoreB: Int,
): Map<String, Double> {
    if (scoreA == scoreB) throw RatingException("tie")
    if (min(scoreA, scoreB) < 0 || max(scoreA, scoreB) > 30) throw RatingException("range")
    if (teamA.isEmpty() || teamB.isEmpty()) throw RatingException("empty")

    val ratingA = teamA.sumOf { ratingsBefore[it] ?: DEFAULT_RATING }
    val ratingB = teamB.sumOf { ratingsBefore[it] ?: DEFAULT_RATING }
    val expA = expectedScore(ratingA, ratingB)
    val actualA = if (scoreA > scoreB) 1.0 else 0.0
    val mult = marginMultiplier(scoreA, scoreB, ratingA, ratingB)

    val deltaA = K_FACTOR * (actualA - expA) * mult
    val ratingsAfter = mutableMapOf<String, Double>()
    for (p in teamA) {
        ratingsAfter[p] = round1((ratingsBefore[p] ?: DEFAULT_RATING) + deltaA)
    }
    for (p in teamB) {
        ratingsAfter[p] = round1((ratingsBefore[p] ?: DEFAULT_RATING) - deltaA)
    }
    for (p in ratingsAfter.keys) {
        ratingsAfter[p] = max(0.0, ratingsAfter[p]!!)
    }
    return ratingsAfter
}

private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
