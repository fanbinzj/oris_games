package com.orisgames.dino.storage

interface HighScoreStorage {
    fun load(): Int
    fun save(score: Int)
}

class InMemoryHighScoreStorage : HighScoreStorage {
    private var best = 0

    override fun load(): Int = best

    override fun save(score: Int) {
        best = score
    }
}
