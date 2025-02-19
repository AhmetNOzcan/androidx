/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmMultifileClass
@file:JvmName("RoomDatabaseKt")

package androidx.room

import androidx.annotation.RestrictTo
import androidx.room.concurrent.CloseBarrier
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.contains as containsCommon
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Base class for all Room databases. All classes that are annotated with [Database] must
 * extend this class.
 *
 * RoomDatabase provides direct access to the underlying database implementation but you should
 * prefer using [Dao] classes.
 *
 * @see Database
 */
actual abstract class RoomDatabase {

    private lateinit var connectionManager: RoomConnectionManager
    private lateinit var coroutineScope: CoroutineScope

    private val typeConverters: MutableMap<KClass<*>, Any> = mutableMapOf()

    /**
     * The invalidation tracker for this database.
     *
     * You can use the invalidation tracker to get notified when certain tables in the database
     * are modified.
     *
     * @return The invalidation tracker for the database.
     */
    actual val invalidationTracker: InvalidationTracker
        get() = internalTracker

    private lateinit var internalTracker: InvalidationTracker

    /**
     * A barrier that prevents the database from closing while the [InvalidationTracker] is using
     * the database asynchronously.
     *
     * @return The barrier for [close].
     */
    internal actual val closeBarrier = CloseBarrier(::onClosed)

    /**
     * Called by Room when it is initialized.
     *
     * @param configuration The database configuration.
     * @throws IllegalArgumentException if initialization fails.
     */
    internal actual fun init(configuration: DatabaseConfiguration) {
        connectionManager = createConnectionManager(configuration)
        internalTracker = createInvalidationTracker()
        val parentJob = checkNotNull(configuration.queryCoroutineContext)[Job]
        coroutineScope = CoroutineScope(
            configuration.queryCoroutineContext + SupervisorJob(parentJob)
        )
        validateAutoMigrations(configuration)
        validateTypeConverters(configuration)
    }

    /**
     * Creates a connection manager to manage database connection. Note that this method
     * is called when the [RoomDatabase] is initialized.
     *
     * @param configuration The database configuration
     * @return A new connection manager
     */
    internal actual fun createConnectionManager(
        configuration: DatabaseConfiguration
    ): RoomConnectionManager = RoomConnectionManager(
        configuration = configuration,
        sqliteDriver = checkNotNull(configuration.sqliteDriver),
        openDelegate = createOpenDelegate() as RoomOpenDelegate,
        callbacks = configuration.callbacks ?: emptyList()
    )

    /**
     * Creates a delegate to configure and initialize the database when it is being opened.
     * An implementation of this function is generated by the Room processor. Note that this method
     * is called when the [RoomDatabase] is initialized.
     *
     * @return A new delegate to be used while opening the database
     * @throws NotImplementedError by default
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected actual open fun createOpenDelegate(): RoomOpenDelegateMarker {
        throw NotImplementedError()
    }

    /**
     * Creates the invalidation tracker
     *
     * An implementation of this function is generated by the Room processor. Note that this method
     * is called when the [RoomDatabase] is initialized.
     *
     * @return A new invalidation tracker.
     */
    protected actual abstract fun createInvalidationTracker(): InvalidationTracker

    internal actual fun getCoroutineScope(): CoroutineScope {
        return coroutineScope
    }

    /**
     * Returns a Set of required [AutoMigrationSpec] classes.
     *
     * An implementation of this function is generated by the Room processor. Note that this method
     * is called when the [RoomDatabase] is initialized.
     *
     * @return Creates a set that will include the classes of all required auto migration specs for
     * this database.
     * @throws NotImplementedError by default
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    actual open fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
        throw NotImplementedError()
    }

    /**
     * Returns a list of automatic [Migration]s that have been generated.
     *
     * An implementation of this function is generated by the Room processor. Note that this method
     * is called when the [RoomDatabase] is initialized.
     *
     * @param autoMigrationSpecs the provided specs needed by certain migrations.
     * @return A list of migration instances each of which is a generated 'auto migration'.
     * @throws NotImplementedError by default
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    actual open fun createAutoMigrations(
        autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>
    ): List<Migration> {
        throw NotImplementedError()
    }

    /**
     * Gets the instance of the given type converter class.
     *
     * This method should only be called by the generated DAO implementations.
     *
     * @param klass The Type Converter class.
     * @param T The type of the expected Type Converter subclass.
     * @return An instance of T.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Suppress("UNCHECKED_CAST")
    actual fun <T : Any> getTypeConverter(klass: KClass<T>): T {
        return typeConverters[klass] as T
    }

    /**
     * Adds a provided type converter to be used in the database DAOs.
     *
     * @param kclass the class of the type converter
     * @param converter an instance of the converter
     */
    internal actual fun addTypeConverter(kclass: KClass<*>, converter: Any) {
        typeConverters[kclass] = converter
    }

    /**
     * Returns a Map of String -> List&lt;KClass&gt; where each entry has the `key` as the DAO name
     * and `value` as the list of type converter classes that are necessary for the database to
     * function.
     *
     * An implementation of this function is generated by the Room processor. Note that this method
     * is called when the [RoomDatabase] is initialized.
     *
     * @return A map that will include all required type converters for this database.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected actual open fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
        throw NotImplementedError()
    }

    /**
     * Property delegate of [getRequiredTypeConverterClasses] for common ext functionality.
     */
    internal actual val requiredTypeConverterClasses: Map<KClass<*>, List<KClass<*>>>
        get() = getRequiredTypeConverterClasses()

    /**
     * Initialize invalidation tracker. Note that this method is called when the [RoomDatabase] is
     * initialized and opens a database connection.
     *
     * @param connection The database connection.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    protected actual fun internalInitInvalidationTracker(connection: SQLiteConnection) {
        invalidationTracker.internalInit(connection)
    }

    /**
     * Closes the database.
     *
     * Once a [RoomDatabase] is closed it should no longer be used.
     */
    actual fun close() {
        closeBarrier.close()
    }

    private fun onClosed() {
        coroutineScope.cancel()
        invalidationTracker.stop()
        connectionManager.close()
    }

    /**
     * Use a connection to perform database operations.
     *
     * This function is for internal access to the pool, it is an unconfined coroutine function to
     * be used by Room generated code paths. For the public version see [useReaderConnection] and
     * [useWriterConnection].
     */
    internal actual suspend fun <R> useConnection(
        isReadOnly: Boolean,
        block: suspend (Transactor) -> R
    ): R {
        return connectionManager.useConnection(isReadOnly, block)
    }

    /**
     * Journal modes for SQLite database.
     *
     * @see Builder#setJournalMode
     */
    actual enum class JournalMode {
        /**
         * Truncate journal mode.
         */
        TRUNCATE,

        /**
         * Write-Ahead Logging mode.
         */
        WRITE_AHEAD_LOGGING;
    }

    /**
     * Builder for [RoomDatabase].
     *
     * @param T The type of the abstract database class.
     * @param klass The abstract database class.
     * @param name The name of the database or NULL for an in-memory database.
     * @param factory The lambda calling `initializeImpl()` on the abstract database class which
     * returns the generated database implementation.
     */
    actual class Builder<T : RoomDatabase>
    @PublishedApi internal constructor(
        private val klass: KClass<T>,
        private val name: String?,
        private val factory: (() -> T)
    ) {

        private var driver: SQLiteDriver? = null
        private val callbacks = mutableListOf<Callback>()
        private val typeConverters: MutableList<Any> = mutableListOf()
        private var journalMode: JournalMode = JournalMode.WRITE_AHEAD_LOGGING
        private var queryCoroutineContext: CoroutineContext? = null

        /**
         * Migrations, mapped by from-to pairs.
         */
        private val migrationContainer: MigrationContainer = MigrationContainer()

        /**
         * Versions that don't require migrations, configured via
         * [fallbackToDestructiveMigrationFrom].
         */
        private var migrationsNotRequiredFrom: MutableSet<Int> = mutableSetOf()

        /**
         * Keeps track of [Migration.startVersion]s and [Migration.endVersion]s added in
         * [addMigrations] for later validation that makes those versions don't
         * match any versions passed to [fallbackToDestructiveMigrationFrom].
         */
        private val migrationStartAndEndVersions = mutableSetOf<Int>()

        private val autoMigrationSpecs: MutableList<AutoMigrationSpec> = mutableListOf()

        private var requireMigration: Boolean = true
        private var allowDestructiveMigrationOnDowngrade = false
        private var allowDestructiveMigrationForAllTables = false

        /**
         * Sets the [SQLiteDriver] implementation to be used by Room to open database connections.
         * For example, an instance of [androidx.sqlite.driver.NativeSQLiteDriver] or
         * [androidx.sqlite.driver.bundled.BundledSQLiteDriver].
         *
         * @param driver The driver
         * @return This builder instance.
         */
        actual fun setDriver(driver: SQLiteDriver): Builder<T> = apply {
            this.driver = driver
        }

        /**
         * Adds a migration to the builder.
         *
         * Each [Migration] has a start and end versions and Room runs these migrations to bring the
         * database to the latest version.
         *
         * A migration can handle more than 1 version (e.g. if you have a faster path to choose when
         * going from version 3 to 5 without going to version 4). If Room opens a database at
         * version 3 and latest version is >= 5, Room will use the migration object that can migrate
         * from 3 to 5 instead of 3 to 4 and 4 to 5.
         *
         * @param migrations The migration objects that modify the database schema with the
         * necessary changes for a version change.
         * @return This builder instance.
         */
        actual fun addMigrations(vararg migrations: Migration) = apply {
            for (migration in migrations) {
                migrationStartAndEndVersions.add(migration.startVersion)
                migrationStartAndEndVersions.add(migration.endVersion)
            }
            migrationContainer.addMigrations(migrations.toList())
        }

        /**
         * Adds an auto migration spec instance to the builder.
         *
         * @param autoMigrationSpec The auto migration object that is annotated with
         * [ProvidedAutoMigrationSpec] and is declared in an [AutoMigration] annotation.
         * @return This builder instance.
         */
        actual fun addAutoMigrationSpec(autoMigrationSpec: AutoMigrationSpec) = apply {
            this.autoMigrationSpecs.add(autoMigrationSpec)
        }

        /**
         * Allows Room to destructively recreate database tables if [Migration]s that would
         * migrate old database schemas to the latest schema version are not found.
         *
         * When the database version on the device does not match the latest schema version, Room
         * runs necessary [Migration]s on the database. If it cannot find the set of [Migration]s
         * that will bring the database to the current version, it will throw an
         * [IllegalStateException]. You can call this method to change this behavior to re-create
         * the database tables instead of crashing.
         *
         * To let Room fallback to destructive migration only during a schema downgrade then use
         * [fallbackToDestructiveMigrationOnDowngrade].
         *
         * @param dropAllTables Set to `true` if all tables should be dropped during destructive
         * migration including those not managed by Room. Recommended value is `true` as otherwise
         * Room could leave obsolete data when table names or existence changes between versions.
         * @return This builder instance.
         */
        actual fun fallbackToDestructiveMigration(dropAllTables: Boolean) = apply {
            this.requireMigration = false
            this.allowDestructiveMigrationOnDowngrade = true
            this.allowDestructiveMigrationForAllTables = dropAllTables
        }

        /**
         * Allows Room to destructively recreate database tables if [Migration]s are not
         * available when downgrading to old schema versions.
         *
         * For details, see [Builder.fallbackToDestructiveMigration].
         *
         * @param dropAllTables Set to `true` if all tables should be dropped during destructive
         * migration including those not managed by Room. Recommended value is `true` as otherwise
         * Room could leave obsolete data when table names or existence changes between versions.
         * @return This builder instance.
         */
        actual fun fallbackToDestructiveMigrationOnDowngrade(dropAllTables: Boolean) = apply {
            this.requireMigration = true
            this.allowDestructiveMigrationOnDowngrade = true
            this.allowDestructiveMigrationForAllTables = dropAllTables
        }

        /**
         * Informs Room that it is allowed to destructively recreate database tables from specific
         * starting schema versions.
         *
         * This functionality is the same [fallbackToDestructiveMigration], except that this method
         * allows the specification of a set of schema versions for which destructive recreation is
         * allowed.
         *
         * Using this method is preferable to [fallbackToDestructiveMigration] if you want
         * to allow destructive migrations from some schema versions while still taking advantage
         * of exceptions being thrown due to unintentionally missing migrations.
         *
         * Note: No versions passed to this method may also exist as either starting or ending
         * versions in the [Migration]s provided via [addMigrations]. If a
         * version passed to this method is found as a starting or ending version in a Migration, an
         * exception will be thrown.
         *
         * @param dropAllTables Set to `true` if all tables should be dropped during destructive
         * migration including those not managed by Room. Recommended value is `true` as otherwise
         * Room could leave obsolete data when table names or existence changes between versions.
         * @param startVersions The set of schema versions from which Room should use a destructive
         * migration.
         * @return This builder instance.
         */
        actual fun fallbackToDestructiveMigrationFrom(
            dropAllTables: Boolean,
            vararg startVersions: Int
        ) = apply {
            for (startVersion in startVersions) {
                this.migrationsNotRequiredFrom.add(startVersion)
            }
            this.allowDestructiveMigrationForAllTables = dropAllTables
        }

        /**
         * Adds a type converter instance to the builder.
         *
         * @param typeConverter The converter instance that is annotated with
         * [ProvidedTypeConverter].
         * @return This builder instance.
         */
        actual fun addTypeConverter(typeConverter: Any) = apply {
            this.typeConverters.add(typeConverter)
        }

        /**
         * Sets the journal mode for this database.
         *
         * The value is ignored if the builder is for an 'in-memory database'. The journal mode
         * should be consistent across multiple instances of [RoomDatabase] for a single SQLite
         * database file.
         *
         * The default value is [JournalMode.WRITE_AHEAD_LOGGING].
         *
         * @param journalMode The journal mode.
         * @return This builder instance.
         */
        actual fun setJournalMode(journalMode: JournalMode) = apply {
            this.journalMode = journalMode
        }

        /**
         * Sets the [CoroutineContext] that will be used to execute all asynchronous queries and
         * tasks, such as `Flow` emissions and [InvalidationTracker] notifications.
         *
         * If no [CoroutineDispatcher] is present in the [context] then this function will throw
         * an [IllegalArgumentException]
         *
         * If no context is provided, then Room will default to `Dispatchers.IO`.
         *
         * @param context The context
         * @return This [Builder] instance
         * @throws IllegalArgumentException if the [context] has no [CoroutineDispatcher]
         */
        actual fun setQueryCoroutineContext(context: CoroutineContext) = apply {
            require(context[ContinuationInterceptor] != null) {
                "It is required that the coroutine context contain a dispatcher."
            }
            this.queryCoroutineContext = context
        }

        /**
         * Adds a [Callback] to this database.
         *
         * @param callback The callback.
         * @return This builder instance.
         */
        actual fun addCallback(callback: Callback) = apply {
            this.callbacks.add(callback)
        }

        /**
         * Creates the database and initializes it.
         *
         * @return A new database instance.
         * @throws IllegalArgumentException if the builder was misconfigured.
         */
        actual fun build(): T {
            requireNotNull(driver) {
                "Cannot create a RoomDatabase without providing a SQLiteDriver via setDriver()."
            }

            validateMigrationsNotRequired(migrationStartAndEndVersions, migrationsNotRequiredFrom)

            val configuration = DatabaseConfiguration(
                name = name,
                migrationContainer = migrationContainer,
                callbacks = callbacks,
                journalMode = journalMode,
                requireMigration = requireMigration,
                allowDestructiveMigrationOnDowngrade = allowDestructiveMigrationOnDowngrade,
                migrationNotRequiredFrom = migrationsNotRequiredFrom,
                typeConverters = typeConverters,
                autoMigrationSpecs = autoMigrationSpecs,
                allowDestructiveMigrationForAllTables = allowDestructiveMigrationForAllTables,
                sqliteDriver = driver,
                queryCoroutineContext = queryCoroutineContext ?: Dispatchers.IO,
            )
            val db = factory.invoke()
            db.init(configuration)
            return db
        }
    }

    /**
     * A container to hold migrations. It also allows querying its contents to find migrations
     * between two versions.
     */
    actual class MigrationContainer {
        private val migrations = mutableMapOf<Int, MutableMap<Int, Migration>>()

        /**
         * Returns the map of available migrations where the key is the start version of the
         * migration, and the value is a map of (end version -> Migration).
         *
         * @return Map of migrations keyed by the start version
         */
        actual fun getMigrations(): Map<Int, Map<Int, Migration>> {
            return migrations
        }

        /**
         * Adds the given migrations to the list of available migrations. If 2 migrations have the
         * same start-end versions, the latter migration overrides the previous one.
         *
         * @param migrations List of available migrations.
         */
        actual fun addMigrations(migrations: List<Migration>) {
            migrations.forEach(::addMigration)
        }

        /**
         * Add a [Migration] to the container. If the container already has a migration with the
         * same start-end versions then it will be overwritten.
         *
         * @param migration the migration to add.
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        actual fun addMigration(migration: Migration) {
            val start = migration.startVersion
            val end = migration.endVersion
            val targetMap = migrations.getOrPut(start) { mutableMapOf() }
            targetMap[end] = migration
        }

        /**
         * Indicates if the given migration is contained within the [MigrationContainer] based
         * on its start-end versions.
         *
         * @param startVersion Start version of the migration.
         * @param endVersion End version of the migration
         * @return True if it contains a migration with the same start-end version, false otherwise.
         */
        actual fun contains(startVersion: Int, endVersion: Int): Boolean {
            return this.containsCommon(startVersion, endVersion)
        }

        /**
         * Returns a pair corresponding to an entry in the map of available migrations whose key
         * is [migrationStart] and its sorted keys in ascending order.
         */
        internal actual fun getSortedNodes(
            migrationStart: Int
        ): Pair<Map<Int, Migration>, Iterable<Int>>? {
            val targetNodes = migrations[migrationStart] ?: return null
            return targetNodes to targetNodes.keys.sorted()
        }

        /**
         * Returns a pair corresponding to an entry in the map of available migrations whose key
         * is [migrationStart] and its sorted keys in descending order.
         */
        internal actual fun getSortedDescendingNodes(
            migrationStart: Int
        ): Pair<Map<Int, Migration>, Iterable<Int>>? {
            val targetNodes = migrations[migrationStart] ?: return null
            return targetNodes to targetNodes.keys.sortedDescending()
        }
    }

    /**
     * Callback for [RoomDatabase]
     */
    actual abstract class Callback {
        /**
         * Called when the database is created for the first time.
         *
         * This function called after all the tables are created.
         *
         * @param connection The database connection.
         */
        actual open fun onCreate(connection: SQLiteConnection) {}

        /**
         * Called after the database was destructively migrated.
         *
         * @param connection The database connection.
         */
        actual open fun onDestructiveMigration(connection: SQLiteConnection) {}

        /**
         * Called when the database has been opened.
         *
         * @param connection The database connection.
         */
        actual open fun onOpen(connection: SQLiteConnection) {}
    }
}
