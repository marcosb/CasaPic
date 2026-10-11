package com.metitem.casapic

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform