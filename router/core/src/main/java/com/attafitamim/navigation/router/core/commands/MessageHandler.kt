package com.attafitamim.navigation.router.core.commands

fun interface MessageHandler {
    fun post(runnable: Runnable)
}
