package me.semoro.kesb

/**
 * Created by Semoro on 14.09.16.
 * ©XCodersTeam, 2016
 */

enum class ProcessingState {
    Queued, Running, Finished, Timeout
}

fun startCompiler(): Process {
    val builder = ProcessBuilder().command("kotlinc/bin/kotlinc")
    return builder.start()
}

