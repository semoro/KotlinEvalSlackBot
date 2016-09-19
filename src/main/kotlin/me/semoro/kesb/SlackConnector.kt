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
import mu.KLogger
import mu.KLogging
import org.apache.commons.lang3.StringEscapeUtils
import rx.Observer
import rx.lang.kotlin.PublishSubject


/**
 * Created by Semoro on 14.09.16.
 * ©XCodersTeam, 2016
 */


val apiUrl = "https://slack.com/api"
val postMessageRequestBuilder = Request.Builder().url("$apiUrl/chat.postMessage")
val updateMessageRequestBuilder = Request.Builder().url("$apiUrl/chat.update")
val getMessageRequestBuilderChannel = Request.Builder().url("$apiUrl/channels.history")
val getMessageRequestBuilderIm = Request.Builder().url("$apiUrl/im.history")
val getMessageRequestBuilderGroup = Request.Builder().url("$apiUrl/im.history")

val stateColors = mapOf(
        ProcessingState.Queued to "#439FE0",
        ProcessingState.Running to "warning",
        ProcessingState.Finished to "good",
        ProcessingState.Timeout to "danger"
)

object SlackConnector : KLogging() {


    override val logger: KLogger = logger()
    val gson = Gson()
    val parser = JsonParser()
    val okhttpclient = OkHttpClient()
    lateinit var selfID: String
    lateinit var selfName: String
    lateinit var token: String

    data class MessageId(val ts: String, val channel: String)


    fun fillMessageBodyBuilder(processingState: ProcessingState, text: String, channel: String): FormEncodingBuilder {
        val encodingBuilder = FormEncodingBuilder()
        encodingBuilder.add("token", token)
        encodingBuilder.add("text", "")
        encodingBuilder.add("channel", channel)
        encodingBuilder.add("parse", "full")
        val attachments = JsonArray()
        val statusAttachment = JsonObject()
        statusAttachment["text"] = processingState.name
        statusAttachment["color"] = stateColors[processingState]
        attachments.add(statusAttachment)

        val resultAttachment = JsonObject()
        resultAttachment["text"] = text
        val markdownIn = JsonArray()
        markdownIn.add("text")
        resultAttachment["mrkdwn_in"] = markdownIn
        attachments.add(resultAttachment)

        encodingBuilder.add("attachments", gson.toJson(attachments))
        return encodingBuilder
    }

    fun getMessageRequestBuilder(channel: String): Request.Builder? {
        return when (channel[0]) {
            'C' -> getMessageRequestBuilderChannel
            'G' -> getMessageRequestBuilderGroup
            'D' -> getMessageRequestBuilderIm
            else -> return null
        }
    }

    fun getMessage(ts: String, channel: String): MessageId? {
        val encodingBuilder = FormEncodingBuilder()
        encodingBuilder.add("token", token)
        encodingBuilder.add("oldest", ts)
        encodingBuilder.add("count", "1")
        encodingBuilder.add("channel", channel)

        val response = okhttpclient.newCall(getMessageRequestBuilder(channel)!!
                .post(encodingBuilder.build())
                .build()).execute()
        val resultObject = parser.parse(response.body().string())
        if (resultObject["ok"].asBoolean)
            return MessageId(resultObject["messages"][0]["ts"].asString, channel)
        return null
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

    fun updateMessage(processingState: ProcessingState, text: String, messageId: MessageId): Boolean {
        val bodyBuilder = fillMessageBodyBuilder(processingState, text, messageId.channel)
        bodyBuilder.add("ts", messageId.ts)
        val response = okhttpclient.newCall(updateMessageRequestBuilder
                .post(bodyBuilder.build())
                .build()).execute()
        val resultObject = parser.parse(response.body().string()) as? JsonObject
        return resultObject?.get("ok")?.asBoolean ?: false
    }

    @JvmStatic
    fun main(args: Array<String>) {
        token = Config.token
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

        val startResponse = parser.parse(startResponseText)
        logger.info { "Got start response, status: ${startResponse["ok"]}" }
        if (startResponse["ok"].asBoolean) {
            selfName = startResponse["self"]["name"].asString
            selfID = startResponse["self"]["id"].asString
            val ws = okhttpclient.newWebSocket(startResponse["url"].asString)
            ws.withGsonConsumer(consumer, gson)
            ws.withGsonProducer(commandsToSend, gson)
            ws.open().closeSubject.toBlocking().toFuture().get()
        } else {
            logger.error { "Got non-ok start response: $startResponse" }
        }
    }


    val commandsToSend = PublishSubject<JsonElement>()

    fun codeFromMessageIfShouldExecute(text: String): List<String>? {
        if (selfID !in text)
            return null
        logger.debug { "Should execute code in message" }
        val codeRegex = "```(.*?)```".toRegex(setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
        val code = codeRegex.findAll(text)
                .flatMap { it.groupValues[1].splitToSequence("\n") }
                .filter(String::isNotBlank)
                .map(StringEscapeUtils::unescapeHtml4)
                .toList()
        logger.trace { "Parsed code: $code" }
        if (code.size > 0)
            return code
        else
            return null
    }


    fun processMessageChanged(t: JsonObject, channel: String) {
        if (t.has("message"))
            processMessageChanged(t["message"].asJsonObject, channel)
        else {
            val text = t["text"].asString
            codeFromMessageIfShouldExecute(text)?.let {
                code ->
                val msg = getMessage(t["ts"].asString, channel)!!
                updateMessage(ProcessingState.Queued, "", msg)
                evaluate(code.toTypedArray(), {
                    updateMessage(it.processingState, "", msg)
                }, {
                    val result = it.result.joinToString("\n")
                    updateMessage(it.processingState, "```$result\n```", msg)
                })
            }
        }
    }

    fun processMessage(t: JsonObject, channel: String) {
        if (t.has("message"))
            processMessage(t["message"].asJsonObject, channel)
        else {
            val text = t["text"].asString
            codeFromMessageIfShouldExecute(text)?.let {
                code ->
                val msg = postMessage(ProcessingState.Queued, "", channel)!!
                evaluate(code.toTypedArray(), {
                    updateMessage(it.processingState, "", msg)
                }, {
                    val result = it.result.joinToString("\n")
                    updateMessage(it.processingState, "```$result\n```", msg)
                })
            }
        }
    }


    fun processEvent(t: JsonObject) {
        when (t.get("type").asString) {
            "message" -> {
                logger.trace { "Got new message" }
                val channel = t["channel"].asString
                if (t.has("subtype")) {
                    if (t["subtype"].asString == "message_changed")
                        processMessageChanged(t, channel)
                } else
                    processMessage(t, channel)
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
            try {
                if (t.has("type"))
                    processEvent(t)
            } catch (throwable: Throwable) {
                logger.error("Exception while processing event", throwable)
            }
        }

        override fun onCompleted() {

        }
    }
}


