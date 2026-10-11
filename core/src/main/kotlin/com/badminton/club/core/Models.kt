package com.badminton.club.core

import kotlinx.serialization.Serializable

const val DEFAULT_RATING = 1000.0
const val DEFAULT_COURTS = 2
const val MIN_COURTS = 1
const val MAX_COURTS = 12
val BOARDS = listOf("open_single", "womens_single", "open_double", "womens_double")
const val DEFAULT_BOARD = "open_double"

@Serializable
data class Club(
    val id: String,
    val name: String,
    val createdAt: String = "",
)

@Serializable
data class Member(
    val id: String,
    val name: String,
    val gender: String? = null,
    val ratings: Map<String, Double> = BOARDS.associateWith { DEFAULT_RATING },
    val createdAt: String = "",
)

@Serializable
data class Match(
    val id: String,
    val date: String,
    val seq: Int,
    val court: Int,
    val board: String = DEFAULT_BOARD,
    val teamA: List<String> = emptyList(),
    val teamB: List<String> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val ratingsBefore: Map<String, Double> = emptyMap(),
    val ratingsAfter: Map<String, Double>? = null,
    val scoreA: Int? = null,
    val scoreB: Int? = null,
    val winner: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val departed: Map<String, String> = emptyMap(),
)

@Serializable
data class Settings(
    val courts: Int = DEFAULT_COURTS,
    val nightStarts: Map<String, String> = emptyMap(),
)

data class PlayerStats(
    val played: Int,
    val rest: Double,
)

data class Suggestion(
    val teamA: List<String>,
    val teamB: List<String>,
    val balance: Double,
    val stats: Map<String, PlayerStats>,
    val board: String,
)
