package se.birdy.app.photo

expect object PhotoStorageProvider {
    fun get(): PhotoStorage
}
