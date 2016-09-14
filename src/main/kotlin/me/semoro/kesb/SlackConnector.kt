package me.semoro.kesb

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import kotlinx.websocket.gson.withGsonConsumer
import kotlinx.websocket.gson.withGsonProducer
import kotlinx.websocket.newWebSocket
import kotlinx.websocket.open
import me.semoro.kesb.SlackConnector.gson
import me.semoro.kesb.SlackConnector.nextId
import me.semoro.kesb.SlackConnector.selfName
import rx.Observer
import rx.lang.kotlin.PublishSubject


/**
 * Created by Semoro on 14.09.16.
 * ©XCodersTeam, 2016
 */
object SlackConnector {
    val gson = Gson()
    val okhttpclient = OkHttpClient()
    val selfName = "U2BL4SBUJ"

    data class StartResponse(var ok: Boolean = false, var url: String)

    var nextId = 1

    @JvmStatic
    fun main(args: Array<String>) {
        val token = args[0]
        val startRequest = Request.Builder()
                .url("https://slack.com/api/rtm.start" +
                        "?token=$token" +
                        "&simple_latest=true" +
                        "&no_unreads=true")
                .get().build()

        val startResponseText = okhttpclient
                .newCall(startRequest)
                .execute()
                .body()
                .string()
        println(startResponseText)
        val startResponse = gson.fromJson<StartResponse>(startResponseText)


        val ws = okhttpclient.newWebSocket(startResponse.url)

        ws.withGsonConsumer(consumer, gson)
        ws.withGsonProducer(commandsToSend, gson).open()
        while (true) {
            Thread.sleep(500)
        }
    }


    val commandsToSend = PublishSubject<JsonElement>()

    fun sendCommand(a: Any) {
        commandsToSend.onNext(gson.toJsonTree(a))
    }

    fun processEvent(t: JsonObject) {
        println("Event $t")
        when (t.get("type").asString) {
            "message" -> {
                val text = t.get("text").asString
                if (text.contains(selfName)) {
                    val channel = t.get("channel").asString
                    val id = nextId++
                    sendCommand(Message(id, channel, "Queued"))
                }
            }
            else -> {
                return
            }
        }
    }

    fun processReplay(t: JsonObject) {
        println("Replay $t")
    }

    val consumer = object : Observer<JsonObject> {
        override fun onError(e: Throwable) {

        }

        override fun onNext(t: JsonObject) {
            if (t.has("type"))
                processEvent(t)
            else
                processReplay(t)
        }

        override fun onCompleted() {

        }
    }
}


data class Message(val id: Int, val channel: String, val text: String, val type: String = "message")
