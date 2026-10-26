package com.example.unpawse.data

/**
 * Runs a group of Room writes as one all-or-nothing unit. An interface rather than a direct
 * `withTransaction` call so the JVM tests can stand in for Room with the fake DAOs.
 */
fun interface Transactor {
    suspend fun inTransaction(block: suspend () -> Unit)
}
