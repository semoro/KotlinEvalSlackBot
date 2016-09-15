package me.semoro.kesb

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Created by Semoro on 14.09.16.
 * ©XCodersTeam, 2016
 */

enum class ProcessingState {
    Queued, Running, Finished, Timeout
}

val timeout = 5000L

class CompilerTask(val input: Array<String>, val onRun: (CompilerTask) -> Unit, val onEnd: (CompilerTask) -> Unit) : Runnable {
    init {
        executor.execute(this)
    }

    override fun run() {
        try {
            val process = startCompiler()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine()
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            for (i in input) {
                writer.write(i)
                writer.newLine()
                writer.flush()
            }
            writer.write(":quit")
            writer.newLine()
            writer.flush()
            processingState = ProcessingState.Running
            onRun.invoke(this)
            val isNotTimeout = process.waitFor(timeout, TimeUnit.MILLISECONDS)
            while (reader.ready()) {
                result.add(reader.readLine())
            }
            if (!isNotTimeout)
                process.destroy()
            processingState = if (isNotTimeout) ProcessingState.Finished else ProcessingState.Timeout
            result = result.subList(result.indexOfFirst { it.startsWith(">>>") }, result.indexOfLast { it.contains(":quit") })
            onEnd.invoke(this)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

    }

    var processingState = ProcessingState.Queued
    var result: MutableList<String> = LinkedList()
}

val executor = Executors.newScheduledThreadPool(1)!!

fun startCompiler(): Process {
    val builder = ProcessBuilder().command("kotlinc/bin/kotlinc")
    return builder.start()
}

fun evaluate(input: Array<String>, onRun: (CompilerTask) -> Unit, onEnd: (CompilerTask) -> Unit) {
    CompilerTask(input, onRun, onEnd)
}