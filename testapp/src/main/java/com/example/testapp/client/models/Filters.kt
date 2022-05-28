package com.example.testapp.client.models

import com.example.testapp.client.api.models.AndFilterObject
import com.example.testapp.client.api.models.AutocompleteFilterObject
import com.example.testapp.client.api.models.ContainsFilterObject
import com.example.testapp.client.api.models.DistinctFilterObject
import com.example.testapp.client.api.models.EqualsFilterObject
import com.example.testapp.client.api.models.ExistsFilterObject
import com.example.testapp.client.api.models.FilterObject
import com.example.testapp.client.api.models.GreaterThanFilterObject
import com.example.testapp.client.api.models.GreaterThanOrEqualsFilterObject
import com.example.testapp.client.api.models.InFilterObject
import com.example.testapp.client.api.models.LessThanFilterObject
import com.example.testapp.client.api.models.LessThanOrEqualsFilterObject
import com.example.testapp.client.api.models.NeutralFilterObject
import com.example.testapp.client.api.models.NorFilterObject
import com.example.testapp.client.api.models.NotEqualsFilterObject
import com.example.testapp.client.api.models.NotExistsFilterObject
import com.example.testapp.client.api.models.NotInFilterObject
import com.example.testapp.client.api.models.OrFilterObject

/**
 * Stream supports a limited set of filters for querying channels, users and members.
 * The example below shows how to filter for channels of type messaging where the current
 * user is a member
 *
 * @code
 * val filter = Filters.and(
 *     Filters.eq("type", "messaging"),
 *     Filters.`in`("members", listOf(user.id))
 * )
 *
 */
public object Filters {

    @JvmStatic
    public fun neutral(): FilterObject = NeutralFilterObject

    @JvmStatic
    public fun exists(fieldName: String): FilterObject = ExistsFilterObject(fieldName)

    @JvmStatic
    public fun notExists(fieldName: String): FilterObject = NotExistsFilterObject(fieldName)

    @JvmStatic
    public fun contains(fieldName: String, value: Any): FilterObject = ContainsFilterObject(fieldName, value)

    @JvmStatic
    public fun and(vararg filters: FilterObject): FilterObject = AndFilterObject(filters.toSet())

    @JvmStatic
    public fun or(vararg filters: FilterObject): FilterObject = OrFilterObject(filters.toSet())

    @JvmStatic
    public fun nor(vararg filters: FilterObject): FilterObject = NorFilterObject(filters.toSet())

    @JvmStatic
    public fun eq(fieldName: String, value: Any): FilterObject = EqualsFilterObject(fieldName, value)

    @JvmStatic
    public fun ne(fieldName: String, value: Any): FilterObject = NotEqualsFilterObject(fieldName, value)

    @JvmStatic
    public fun greaterThan(fieldName: String, value: Any): FilterObject = GreaterThanFilterObject(fieldName, value)

    @JvmStatic
    public fun greaterThanEquals(fieldName: String, value: Any): FilterObject = GreaterThanOrEqualsFilterObject(fieldName, value)

    @JvmStatic
    public fun lessThan(fieldName: String, value: Any): FilterObject = LessThanFilterObject(fieldName, value)

    @JvmStatic
    public fun lessThanEquals(fieldName: String, value: Any): FilterObject = LessThanOrEqualsFilterObject(fieldName, value)

    @JvmStatic
    public fun `in`(fieldName: String, vararg values: String): FilterObject = InFilterObject(fieldName, values.toSet())

    @JvmStatic
    public fun `in`(fieldName: String, values: List<Any>): FilterObject = InFilterObject(fieldName, values.toSet())

    @JvmStatic
    public fun `in`(fieldName: String, vararg values: Number): FilterObject = InFilterObject(fieldName, values.toSet())

    @JvmStatic
    public fun nin(fieldName: String, vararg values: String): FilterObject = NotInFilterObject(fieldName, values.toSet())

    @JvmStatic
    public fun nin(fieldName: String, values: List<Any>): FilterObject = NotInFilterObject(fieldName, values.toSet())

    @JvmStatic
    public fun nin(fieldName: String, vararg values: Number): FilterObject = NotInFilterObject(fieldName, values.toSet())

    @JvmStatic
    public fun autocomplete(fieldName: String, value: String): FilterObject = AutocompleteFilterObject(fieldName, value)

    @JvmStatic
    public fun distinct(memberIds: List<String>): FilterObject = DistinctFilterObject(memberIds.toSet())
}
