package me.semoro.kesb

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Created by Semoro on 14.09.16.
 * ©XCodersTeam, 2016
 */

enum class ProcessingState {
    Queued, Running, Finished, Timeout
}

val timeout = 5000

class CompilerTask(val input: Array<String>) : Runnable {
    init {
        executor.execute(this)
    }

    override fun run() {
        process = startCompiler()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        result.add(reader.readLine())
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
        val timeoutAt = System.currentTimeMillis() + timeout
        while (true) {
            if (!process.isAlive) {
                processingState = ProcessingState.Finished
                break
            }
            if (System.currentTimeMillis() > timeoutAt) {
                process.destroy()
                processingState = ProcessingState.Timeout
                break
            }
            Thread.sleep(50)
        }
        reader.lines().forEach {
            println(it)
            result.add(it)
        }
        result.removeLast()
        future.complete(this)
    }

    lateinit var process: Process
    val future = CompletableFuture<CompilerTask>()
    var processingState = ProcessingState.Queued
    var result = LinkedList<String>()
}

val executor = Executors.newScheduledThreadPool(1)!!

fun startCompiler(): Process {
    val builder = ProcessBuilder().command("kotlinc/bin/kotlinc")
    return builder.start()
}

fun evaluate(input: Array<String>): CompilerTask {
    return CompilerTask(input)
}