package com.maphutimoviousteffo.wizprly.data

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val avatar: String? = null,
    val online: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)