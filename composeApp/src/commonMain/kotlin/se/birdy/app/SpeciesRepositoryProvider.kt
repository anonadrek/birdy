package se.birdy.app

import se.birdy.content.SpeciesRepository

expect object SpeciesRepositoryProvider {
    fun get(): SpeciesRepository
}
