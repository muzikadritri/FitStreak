// Minimale Stellvertreter für die Room-Annotationen, damit Entities und DAO-Interfaces
// im Desktop-Prüfwerkzeug kompilieren. Die echte Room-Prüfung macht der Android-Build (KSP).
package androidx.room

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
annotation class Entity(val tableName: String = "", val indices: Array<Index> = [], val primaryKeys: Array<String> = [])

@Target(allowedTargets = [])
annotation class Index(vararg val value: String, val unique: Boolean = false)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class PrimaryKey(val autoGenerate: Boolean = false)

@Target(AnnotationTarget.CLASS)
annotation class Dao

@Target(AnnotationTarget.FUNCTION)
annotation class Query(val value: String)

@Target(AnnotationTarget.FUNCTION)
annotation class Insert(val onConflict: Int = OnConflictStrategy.ABORT)

@Target(AnnotationTarget.FUNCTION)
annotation class Update(val onConflict: Int = OnConflictStrategy.ABORT)

@Target(AnnotationTarget.FUNCTION)
annotation class Delete

@Target(AnnotationTarget.CLASS)
annotation class Database(val entities: Array<KClass<*>> = [], val version: Int = 1, val exportSchema: Boolean = true)

object OnConflictStrategy {
    const val NONE = 0
    const val REPLACE = 1
    const val ABORT = 3
    const val IGNORE = 5
}
