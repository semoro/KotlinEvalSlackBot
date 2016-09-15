package me.semoro.kesb

/**
 * Created by Semoro on 15.09.16.
 * ©XCodersTeam, 2016
 */

object Config {
    private val env = System.getenv()

    val timeout = env["EVAL_TIMEOUT"]?.toLong() ?: 5000L
    val token = env["API_TOKEN"]!!
    val workers = env["WORKERS"]?.toInt() ?: 1
}