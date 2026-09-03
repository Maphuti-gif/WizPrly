package com.maphutimoviousteffo.wizprly.shared

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
