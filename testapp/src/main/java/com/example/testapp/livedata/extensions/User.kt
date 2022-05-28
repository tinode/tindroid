package com.example.testapp.livedata.extensions

import com.example.testapp.client.models.User

/** Updates a collection of users by more fresh value of [users]. */
internal fun Collection<User>.updateUsers(users: Map<String, User>) = map { user -> users[user.id] ?: user }
