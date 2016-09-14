package me.semoro.kesb

import rx.Observable
import rx.Scheduler
import rx.schedulers.Schedulers


/**
 * Created by Semoro on 14.09.16.
 * ©XCodersTeam, 2016
 */
object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        val process = startCompiler()

        val reader = process.inputStream.bufferedReader()
        val writer = process.outputStream.bufferedWriter()


        println(reader.readLine())
        println(reader.readLine())
        writer.write("println(\"test\")")
        writer.newLine()
        println(reader.readLine())
    }
}