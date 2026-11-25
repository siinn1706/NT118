package vn.uit.healthos.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * Checks the 5 to 6 migration against the exported schemas without a device: every statement it runs is recorded and
 * compared with what Room expects version 6 to look like. Room re-validates the result when the database opens.
 */
class AppDatabaseMigrationTest {

    private val json = Json
    private val schemaDir = File("schemas/vn.uit.healthos.core.data.db.AppDatabase")

    private fun schema(version: Int): JsonObject =
        json.parseToJsonElement(File(schemaDir, "$version.json").readText()).jsonObject["database"]!!.jsonObject

    private fun JsonObject.entities(): Map<String, JsonObject> =
        this["entities"]!!.jsonArray.associate { e -> e.jsonObject["tableName"]!!.jsonPrimitive.content to e.jsonObject }

    private fun JsonObject.createSql(): String =
        this["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", this["tableName"]!!.jsonPrimitive.content)

    private fun JsonObject.indexSql(): List<String> {
        val table = this["tableName"]!!.jsonPrimitive.content
        return this["indices"]?.jsonArray.orEmpty().map { it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table) }
    }

    private fun JsonObject.columns(): Set<String> =
        this["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }.toSet()

    /** Runs the generated migration against a fake database that only records the SQL it receives. */
    private fun recordMigration(from: Int, to: Int): List<String> {
        val migration = Class.forName("vn.uit.healthos.core.data.db.AppDatabase_AutoMigration_${from}_${to}_Impl")
            .getDeclaredConstructor()
            .apply { isAccessible = true }
            .newInstance() as Migration
        assertEquals(from, migration.startVersion)
        assertEquals(to, migration.endVersion)

        val executed = mutableListOf<String>()
        val statement = proxy<SQLiteStatement> { method, _ ->
            when (method) {
                "step" -> false
                else -> null
            }
        }
        val connection = proxy<SQLiteConnection> { method, args ->
            when (method) {
                "prepare" -> statement.also { executed += args!![0] as String }
                "inTransaction" -> false
                else -> null
            }
        }
        val supportDb = proxy<SupportSQLiteDatabase> { method, args ->
            when (method) {
                "execSQL" -> executed += args!![0] as String
                "inTransaction" -> false
            }
            null
        }
        try {
            migration.migrate(connection)
        } catch (e: UnsupportedOperationException) {
            migration.migrate(supportDb)
        } catch (e: NotImplementedError) {
            migration.migrate(supportDb)
        }
        return executed.map { it.trim() }
    }

    private inline fun <reified T> proxy(crossinline handler: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            when (method.name) {
                "toString" -> "Fake${T::class.java.simpleName}"
                "hashCode" -> 0
                "equals" -> false
                else -> handler(method.name, args) ?: defaultFor(method.returnType)
            }
        } as T

    private fun defaultFor(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        else -> null
    }

    @Test
    fun `5 to 6 creates the new tables as exported and adds the outbox owner without dropping anything`() {
        val v5 = schema(5).entities()
        val v6 = schema(6).entities()
        val executed = recordMigration(5, 6)

        assertTrue("no table may be dropped: $executed", executed.none { it.startsWith("DROP TABLE", ignoreCase = true) })

        val newTables = v6.keys - v5.keys
        assertEquals(setOf("dm_messages", "leaderboard_cache"), newTables)
        newTables.forEach { table ->
            val entity = v6.getValue(table)
            assertTrue("missing CREATE for $table in $executed", entity.createSql() in executed)
            entity.indexSql().forEach { sql -> assertTrue("missing index $sql", sql in executed) }
        }

        val added = v6.getValue("outbox").columns() - v5.getValue("outbox").columns()
        assertEquals(setOf("owner_user_id"), added)
        assertTrue(
            "outbox.owner_user_id must be added in place: $executed",
            executed.any { it.startsWith("ALTER TABLE `outbox` ADD COLUMN `owner_user_id` TEXT", ignoreCase = true) },
        )

        // Tables that did not change are left alone, so the rows in them survive.
        val untouched = v5.keys - "outbox"
        executed.forEach { sql ->
            untouched.forEach { table -> assertTrue("$table must not be rewritten: $sql", !sql.contains("`$table`")) }
        }
    }

    @Test
    fun `6 to 7 only creates the snapshot cache table as exported`() {
        val v6 = schema(6).entities()
        val v7 = schema(7).entities()
        val executed = recordMigration(6, 7)

        assertEquals(setOf("snapshot_cache"), v7.keys - v6.keys)
        val entity = v7.getValue("snapshot_cache")
        assertTrue("missing CREATE for snapshot_cache in $executed", entity.createSql() in executed)

        executed.forEach { sql ->
            v6.keys.forEach { table -> assertTrue("$table must not be touched: $sql", !sql.contains("`$table`")) }
        }
    }
}
