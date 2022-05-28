package com.example.testapp.client.api.models

import android.util.Log
import com.example.testapp.client.models.Channel
import java.lang.ClassCastException
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

/**
 * Filter object that specifies requests for backend queries.
 */
public sealed class FilterObject {
    override fun equals(other: Any?): Boolean {
        return if (other is FilterObject) {
            this.toMap() == other.toMap()
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return toMap().hashCode()
    }
}

public data class AndFilterObject internal constructor(val filterObjects: Set<FilterObject>) : FilterObject()
public data class OrFilterObject internal constructor(val filterObjects: Set<FilterObject>) : FilterObject()
public data class NorFilterObject internal constructor(val filterObjects: Set<FilterObject>) : FilterObject()
public data class ContainsFilterObject internal constructor(val fieldName: String, val value: Any) : FilterObject()
public data class AutocompleteFilterObject internal constructor(val fieldName: String, val value: String) : FilterObject()
public data class ExistsFilterObject internal constructor(val fieldName: String) : FilterObject()
public data class NotExistsFilterObject internal constructor(val fieldName: String) : FilterObject()
public data class EqualsFilterObject internal constructor(val fieldName: String, val value: Any) : FilterObject()
public data class NotEqualsFilterObject internal constructor(val fieldName: String, val value: Any) : FilterObject()
public data class GreaterThanFilterObject internal constructor(val fieldName: String, val value: Any) : FilterObject()
public data class GreaterThanOrEqualsFilterObject internal constructor(val fieldName: String, val value: Any) : FilterObject()
public data class LessThanFilterObject internal constructor(val fieldName: String, val value: Any) : FilterObject()
public data class LessThanOrEqualsFilterObject internal constructor(val fieldName: String, val value: Any) : FilterObject()
public data class InFilterObject internal constructor(val fieldName: String, val values: Set<Any>) : FilterObject()
public data class NotInFilterObject internal constructor(val fieldName: String, val values: Set<Any>) : FilterObject()
public data class DistinctFilterObject internal constructor(val memberIds: Set<String>) : FilterObject()
public object NeutralFilterObject : FilterObject()


internal fun FilterObject.toMap(): Map<String, Any> = when (this) {
    is AndFilterObject -> mapOf(KEY_AND to this.filterObjects.map(FilterObject::toMap))
    is OrFilterObject -> mapOf(KEY_OR to this.filterObjects.map(FilterObject::toMap))
    is NorFilterObject -> mapOf(KEY_NOR to this.filterObjects.map(FilterObject::toMap))
    is ExistsFilterObject -> mapOf(this.fieldName to mapOf(KEY_EXIST to true))
    is NotExistsFilterObject -> mapOf(this.fieldName to mapOf(KEY_EXIST to false))
    is EqualsFilterObject -> mapOf(this.fieldName to this.value)
    is NotEqualsFilterObject -> mapOf(this.fieldName to mapOf(KEY_NOT_EQUALS to this.value))
    is ContainsFilterObject -> mapOf(this.fieldName to mapOf(KEY_CONTAINS to this.value))
    is GreaterThanFilterObject -> mapOf(this.fieldName to mapOf(KEY_GREATER_THAN to this.value))
    is GreaterThanOrEqualsFilterObject -> mapOf(this.fieldName to mapOf(KEY_GREATER_THAN_OR_EQUALS to this.value))
    is LessThanFilterObject -> mapOf(this.fieldName to mapOf(KEY_LESS_THAN to this.value))
    is LessThanOrEqualsFilterObject -> mapOf(this.fieldName to mapOf(KEY_LESS_THAN_OR_EQUALS to this.value))
    is InFilterObject -> mapOf(this.fieldName to mapOf(KEY_IN to this.values))
    is NotInFilterObject -> mapOf(this.fieldName to mapOf(KEY_NOT_IN to this.values))
    is AutocompleteFilterObject -> mapOf(this.fieldName to mapOf(KEY_AUTOCOMPLETE to this.value))
    is DistinctFilterObject -> mapOf(KEY_DISTINCT to true, KEY_MEMBERS to this.memberIds)
    is NeutralFilterObject -> emptyMap<String, String>()
}

private const val KEY_EXIST: String = "\$exists"
private const val KEY_CONTAINS: String = "\$contains"
private const val KEY_AND: String = "\$and"
private const val KEY_OR: String = "\$or"
private const val KEY_NOR: String = "\$nor"
private const val KEY_NOT_EQUALS: String = "\$ne"
private const val KEY_GREATER_THAN: String = "\$gt"
private const val KEY_GREATER_THAN_OR_EQUALS: String = "\$gte"
private const val KEY_LESS_THAN: String = "\$lt"
private const val KEY_LESS_THAN_OR_EQUALS: String = "\$lte"
private const val KEY_IN: String = "\$in"
private const val KEY_NOT_IN: String = "\$nin"
private const val KEY_AUTOCOMPLETE: String = "\$autocomplete"
private const val KEY_DISTINCT: String = "distinct"
private const val KEY_MEMBERS: String = "members"

internal fun <T> Collection<T>.filter(filterObject: FilterObject): List<T> =
    filter { filterObject.filter(it) }

@Suppress("UNCHECKED_CAST")
internal fun <T> FilterObject.filter(t: T): Boolean = try {
    when (this) {
        is AndFilterObject -> filterObjects.all { it.filter(t) }
        is OrFilterObject -> filterObjects.any { it.filter(t) }
        is NorFilterObject -> filterObjects.none { it.filter(t) }
        is ContainsFilterObject -> t?.getMemberProperty(fieldName, List::class)
            ?.contains(value) ?: false
        is AutocompleteFilterObject -> t?.getMemberProperty(fieldName, String::class)
            ?.contains(value) ?: false
        is ExistsFilterObject -> t?.getMemberProperty(fieldName, Any::class) != null
        is NotExistsFilterObject -> t?.getMemberProperty(fieldName, Any::class) == null
        is EqualsFilterObject -> value == t?.getMemberProperty(fieldName, value::class)
        is NotEqualsFilterObject -> value != t?.getMemberProperty(fieldName, value::class)
        is GreaterThanFilterObject ->
            compare(
                t?.getMemberProperty(fieldName, value::class) as? Comparable<Any>,
                value as? Comparable<Any>
            ) { it > 0 }
        is GreaterThanOrEqualsFilterObject ->
            compare(
                t?.getMemberProperty(fieldName, value::class) as? Comparable<Any>,
                value as? Comparable<Any>
            ) { it >= 0 }
        is LessThanFilterObject ->
            compare(
                t?.getMemberProperty(fieldName, value::class) as? Comparable<Any>,
                value as? Comparable<Any>
            ) { it < 0 }
        is LessThanOrEqualsFilterObject ->
            compare(
                t?.getMemberProperty(fieldName, value::class) as? Comparable<Any>,
                value as? Comparable<Any>
            ) { it <= 0 }
        is InFilterObject -> {
            val fieldValue = t?.getMemberProperty(fieldName, Any::class)
            if (fieldValue is List<*>) {
                values.any(fieldValue::contains)
            } else {
                values.contains(fieldValue)
            }
        }
        is NotInFilterObject -> {
            val fieldValue = t?.getMemberProperty(fieldName, Any::class)
            if (fieldValue is List<*>) {
                values.none(fieldValue::contains)
            } else {
                !values.contains(fieldValue)
            }
        }
        is DistinctFilterObject -> (t as? Channel)?.let { channel ->
            channel.id.startsWith("!members") &&
                    channel.members.size == memberIds.size &&
                    channel.members.map { it.user.id }.containsAll(memberIds)
        } ?: false
        NeutralFilterObject -> true
    }
} catch (e: ClassCastException) {
    Log.e(this::class.simpleName, e.message, e)
    false
}

private fun <T : Any> Any.getMemberProperty(name: String, clazz: KClass<out T>): T? =
    name.let { fieldName ->
        this::class.memberProperties.firstOrNull { it.name == fieldName }?.getter?.call(this)
            ?.cast(clazz)
    }

private fun <T : Any> Any.cast(clazz: KClass<out T>): T = clazz.javaObjectType.cast(this)!!

private fun <T : Comparable<T>> compare(a: T?, b: T?, compareFun: (Int) -> Boolean): Boolean =
    a?.let { notNullA ->
        b?.let { notNullB ->
            compareFun(notNullA.compareTo(notNullB))
        }
    } ?: false