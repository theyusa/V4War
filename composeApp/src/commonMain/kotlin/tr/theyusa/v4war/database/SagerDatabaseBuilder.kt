package tr.theyusa.v4war.database

/**
 * The object wrapper is must.
 * Otherwise, "[MissingType]: Element 'tr.theyusa.v4war.database.SagerDatabase' references a type that is not present"
 */
internal expect object SagerDatabaseProvider {
    fun create(): SagerDatabase
}
