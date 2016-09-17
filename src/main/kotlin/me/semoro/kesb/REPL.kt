package me.semoro.kesb

import mu.KLogging
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.*
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


class CompilerTask(val input: Array<String>, val onRun: (CompilerTask) -> Unit, val onEnd: (CompilerTask) -> Unit) : Runnable {
    init {
        logger.trace { "Queued code execution" }
        executor.execute(this)
    }

    companion object : KLogging()

    override fun run() {
        try {
            val process = startCompiler()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine()
            processingState = ProcessingState.Running
            onRun.invoke(this)
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            for (i in input) {
                writer.write(i)
                writer.newLine()
                writer.flush()
            }
            writer.write(":quit")
            writer.newLine()
            writer.flush()
            val startTime = System.currentTimeMillis()
            var isNotTimeout = process.waitFor(Config.timeout, TimeUnit.MILLISECONDS)
            while (reader.ready()) {
                result.add(reader.readLine())
            }
            if (!isNotTimeout)
                isNotTimeout = pkill(process)
            processingState = if (isNotTimeout) ProcessingState.Finished else ProcessingState.Timeout
            result = result.subList(result.indexOfFirst { it.startsWith(">>>") }.let { if (it == -1) 0 else it },
                    result.indexOfLast { it.startsWith(">>>") && it.contains(":quit") }.let { if (it == -1) result.size else it })
            val runTime = System.currentTimeMillis() - startTime
            logger.info { "Finished execution in $runTime ms, state: $processingState, result lines: ${result.size}" }
            onEnd.invoke(this)
        } catch (e: Throwable) {
            if (e is ProcessKillFailedException) {
                logger.error { e }
                System.exit(1)
            } else {
                logger.warn { e }
            }
        }

    }

    var processingState = ProcessingState.Queued
    var result: MutableList<String> = LinkedList()
}

val executor = Executors.newScheduledThreadPool(Config.workers)!!

fun startCompiler(): Process {
    return executeCommand("kotlinc/bin/kotlinc")
}

class ProcessKillFailedException(message: String) : Exception(message) {

}

private fun executeCommand(cmd: String): Process {
    return ProcessBuilder().command(cmd.split(" ")).start()
}

fun pkill(p: Process): Boolean {
    getPidOfProcess(p).let { pid ->
        val isExists = executeCommand("ps -o pid= -p $pid").waitFor()
        if (isExists == 0) {
            val pkillResult = executeCommand("pkill -15 -P $pid").waitFor()
            if (pkillResult != 0)
                throw ProcessKillFailedException("Could not pkill process with $pid, should restart container")
            return false
        } else
            return true
    }
}

fun getPidOfProcess(p: Process): Long {
    return if (p.javaClass.name == "java.lang.UNIXProcess") {
        val f = p.javaClass.getDeclaredField("pid")
        f.isAccessible = true
        val pid = f.getLong(p)
        f.isAccessible = false
        pid
    } else
        throw ProcessKillFailedException("Not a UNIXProcess")
}

fun evaluate(input: Array<String>, onRun: (CompilerTask) -> Unit, onEnd: (CompilerTask) -> Unit) {
    CompilerTask(input, onRun, onEnd)
}