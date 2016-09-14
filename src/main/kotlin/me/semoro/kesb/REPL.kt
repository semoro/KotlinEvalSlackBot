package me.semoro.kesb

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque

/**
 * Created by Semoro on 14.09.16.
 * ©XCodersTeam, 2016
 */

enum class ProcessingState {
    Queued, Running, Finished, Timeout
}

val timeot = 1000

class CompilerTask(val input: Array<String>) : Runnable {
    init {
        executor.execute(this)
    }

    override fun run() {
        process = startCompiler()
        val stream = BufferedWriter(OutputStreamWriter(process.outputStream))
        for (i in input) {
            stream.write(i)
            stream.newLine()
            stream.flush()
        }
        stream.write(":quit")
        stream.newLine()
        stream.flush()
        processingState = ProcessingState.Running
        val timeoutAt = System.currentTimeMillis() + timeot
        while (true) {
            if(!process.isAlive){
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
        BufferedReader(InputStreamReader(process.inputStream)).lines().forEach { result.add(it) }
        result.removeFirst()
        result.removeFirst()
        result.removeLast()
        future.complete(this)
    }

    lateinit var process: Process
    val future = CompletableFuture<CompilerTask>()
    var processingState = ProcessingState.Queued
    var result = LinkedList<String>()
}

val taskStack = LinkedBlockingDeque<CompilerTask>()
val executor = Executors.newScheduledThreadPool(1)!!

fun startCompiler(): Process {
    val builder = ProcessBuilder().command("kotlinc/bin/kotlinc")
    return builder.start()
}

fun evaluate(input: Array<String>): CompilerTask {
    val task = CompilerTask(input)
    taskStack.add(task)
    return task
}