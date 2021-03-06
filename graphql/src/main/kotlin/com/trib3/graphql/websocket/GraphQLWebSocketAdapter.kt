package com.trib3.graphql.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.eclipse.jetty.websocket.api.WebSocketAdapter

private val log = KotlinLogging.logger {}

/**
 * [WebSocketAdapter] implementation that bridges incoming WebSocket events into
 * a coroutine [Channel] to be handled by a consumer, and provides access
 * to the [remote] for sending messages to the client.
 */
open class GraphQLWebSocketAdapter(
    val channel: Channel<OperationMessage<*>>,
    val objectMapper: ObjectMapper,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : WebSocketAdapter(), CoroutineScope by CoroutineScope(dispatcher) {
    val objectWriter = objectMapper.writerWithDefaultPrettyPrinter()!!

    companion object {
        private val CLIENT_SOURCED_MESSAGES = listOf(
            OperationType.GQL_CONNECTION_INIT,
            OperationType.GQL_START,
            OperationType.GQL_STOP,
            OperationType.GQL_CONNECTION_TERMINATE
        )
    }

    /**
     * Parse incoming messages as [OperationMessage]s, then send them to the channel consumer
     */
    override fun onWebSocketText(message: String) = runBlocking {
        try {
            val operation = objectMapper.readValue<OperationMessage<*>>(message)
            // Only send client->server messages downstream, otherwise queue an error to be sent back
            if (operation.type in CLIENT_SOURCED_MESSAGES) {
                channel.send(operation)
            } else {
                channel.send(
                    OperationMessage(
                        OperationType.GQL_ERROR,
                        operation.id,
                        "Invalid message `$message`"
                    )
                )
            }
        } catch (error: Throwable) {
            // Don't kill the socket because of a bad message, just queue an error to be sent back
            log.error("Error parsing message: ${error.message}", error)
            channel.send(
                OperationMessage(
                    OperationType.GQL_ERROR,
                    null,
                    "Invalid message `$message`"
                )
            )
        }
    }

    /**
     * Notify channel that the stream is finished
     */
    override fun onWebSocketClose(statusCode: Int, reason: String?) {
        val msg = "WebSocket close $statusCode $reason"
        log.debug(msg)
        super.onWebSocketClose(statusCode, reason)
        channel.close()
        cancel(msg)
    }

    /**
     * Just log the error, and rely on the [onWebSocketClose] callback to clean up
     */
    override fun onWebSocketError(cause: Throwable) {
        log.error("WebSocket error ${cause.message}", cause)
    }

    /**
     * Convenience method for writing an [OperationMessage] back to the client in json format
     * Must be called from the Subscriber's observation context
     */
    internal fun sendMessage(message: OperationMessage<*>) {
        remote?.sendString(objectWriter.writeValueAsString(message))
    }

    /**
     * Convenience method for writing the components of an [OperationMessage] back to the client in json format
     * Must be called from the Subscriber's observation context
     */
    internal fun <T : Any> sendMessage(type: OperationType<T>, id: String?, payload: T? = null) {
        sendMessage(OperationMessage(type, id, payload))
    }
}
