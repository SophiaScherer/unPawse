package com.example.unpawse.data

/**
 * Stands in for Room's transaction over the fake DAOs: if the block throws, or [failCommit] makes
 * the commit itself fail, every store is rolled back to its [checkpoints].
 */
internal class FakeTransactor(private val checkpoints: List<() -> () -> Unit>) : Transactor {

    var failCommit = false

    override suspend fun inTransaction(block: suspend () -> Unit) {
        val rollbacks = checkpoints.map { it() }
        try {
            block()
            if (failCommit) throw IllegalStateException("simulated commit failure")
        } catch (e: Throwable) {
            rollbacks.forEach { it() }
            throw e
        }
    }
}
