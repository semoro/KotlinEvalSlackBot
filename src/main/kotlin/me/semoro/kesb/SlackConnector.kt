package me.semoro.kesb

import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.set
import com.google.gson.*
import com.squareup.okhttp.FormEncodingBuilder
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import kotlinx.websocket.gson.withGsonConsumer
import kotlinx.websocket.gson.withGsonProducer
import kotlinx.websocket.newWebSocket
import kotlinx.websocket.open
import rx.Observer
import rx.lang.kotlin.PublishSubject


/**
 * Created by Semoro on 14.09.16.
 * ©XCodersTeam, 2016
 */


val apiUrl = "https://slack.com/api"
val postMessageRequestBuilder = Request.Builder().url("$apiUrl/chat.postMessage")
val updateMessageRequestBuilder = Request.Builder().url("$apiUrl/chat.update")


object SlackConnector {
    val gson = Gson()
    val parser = JsonParser()
    val okhttpclient = OkHttpClient()
    lateinit var selfID: String
    lateinit var selfName: String
    lateinit var token: String
    lateinit var tokenApi: String

    data class MessageId(val ts: String, val channel: String)


    fun fillMessageBodyBuilder(processingState: ProcessingState, text: String, channel: String): FormEncodingBuilder {
        val encodingBuilder = FormEncodingBuilder()
        encodingBuilder.add("token", tokenApi)
        encodingBuilder.add("text", text)
        encodingBuilder.add("channel", channel)
        encodingBuilder.add("parse", "full")
        val attachments = JsonArray()
        val statusAttachment = JsonObject()
        statusAttachment["text"] = processingState.name
        statusAttachment["color"] = "#0f0"
        attachments.add(statusAttachment)
        encodingBuilder.add("attachments", gson.toJson(attachments))
        return encodingBuilder
    }

    fun postMessage(processingState: ProcessingState, text: String, channel: String): MessageId? {
        val bodyBuilder = fillMessageBodyBuilder(processingState, text, channel)
        bodyBuilder.add("username", selfName)
        val response = okhttpclient.newCall(postMessageRequestBuilder
                .post(bodyBuilder.build())
                .build()).execute()
        val resultObject = parser.parse(response.body().string())
        if (resultObject["ok"].asBoolean)
            return MessageId(resultObject["ts"].asString, channel)
        return null
    }

    fun updateMessage(processingState: ProcessingState, text: String, messageId: MessageId): MessageId? {
        val bodyBuilder = fillMessageBodyBuilder(processingState, text, messageId.channel)
        bodyBuilder.add("ts", messageId.ts)
        println("Sent update")
        val response = okhttpclient.newCall(updateMessageRequestBuilder
                .post(bodyBuilder.build())
                .build()).execute()

        println(response.body().string())
        val resultObject = parser.parse(response.body().string())

        if (resultObject["ok"].asBoolean)
            return MessageId(resultObject["ts"].asString, messageId.channel)
        return null
    }

    @JvmStatic
    fun main(args: Array<String>) {
        token = args[0]
        tokenApi = args[1]
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
        val startResponse = parser.parse(startResponseText)
        if (startResponse["ok"].asBoolean) {
            selfName = startResponse["self"]["name"].asString
            selfID = startResponse["self"]["id"].asString
            val ws = okhttpclient.newWebSocket(startResponse["url"].asString)
            ws.withGsonConsumer(consumer, gson)
            ws.withGsonProducer(commandsToSend, gson).open()
            while (true) {
                Thread.sleep(500)
            }
        }
    }


    val commandsToSend = PublishSubject<JsonElement>()


    fun processEvent(t: JsonObject) {
        println("Event $t")
        when (t.get("type").asString) {
            "message" -> {
                val text = t.get("text").asString
                if (text.contains(selfID)) {
                    val channel = t.get("channel").asString

                    val codeRegex = "```(.*?)```".toRegex(setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
                    val code = codeRegex.findAll(text)
                            .flatMap { it.groupValues[1].splitToSequence("\n") }
                            .map(String::trim)
                            .filterNot { it.length == 0 }.toList()
                    if (code.size > 0) {
                        val msg = postMessage(ProcessingState.Queued, "", channel)!!
                        val task = evaluate(code.toTypedArray())
                        task.future.thenAccept {
                            val result = it.result.joinToString("\n")
                            updateMessage(it.processingState, "```$result\n```", msg)
                        }
                    }
                }
            }
            else -> {
                return
            }
        }
    }


    val consumer = object : Observer<JsonObject> {
        override fun onError(e: Throwable) {

        }

        override fun onNext(t: JsonObject) {
            if (t.has("type"))
                processEvent(t)
        }

        override fun onCompleted() {

        }
    }
}


