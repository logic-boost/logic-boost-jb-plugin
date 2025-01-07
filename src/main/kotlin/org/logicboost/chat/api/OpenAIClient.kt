package org.logicboost.chat.api

import com.intellij.openapi.diagnostic.Logger
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.models.ChatCompletionCreateParams
import com.openai.models.ChatCompletionFunctionCallOption
import com.openai.models.ChatCompletionMessageParam
import com.openai.models.ChatCompletionUserMessageParam
import com.openai.models.FunctionParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class OpenAIClientWrapper private constructor(
    private val client: OpenAIClient,
    private val config: Configuration
) : AutoCloseable {

    private val logger = Logger.getInstance(OpenAIClientWrapper::class.java)
    private val interruptFlag = AtomicBoolean(false)

    data class Configuration(
        val apiKey: String,
        val endpoint: String = DEFAULT_ENDPOINT,
        val model: String,
        val systemPrompt: String,
        val temperature: Double = 0.0,
        val maxTokens: Long = 8000,
        val topP: Double = 1.0,
        val functions: List<FunctionDefinition> = emptyList(),
        val functionCall: FunctionCallOption = FunctionCallOption.Auto
    ) {
        fun isValid(): Boolean = apiKey.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank()

        companion object {
            const val DEFAULT_ENDPOINT = "https://api.openai.com/v1"
        }
    }

    sealed interface FunctionCallOption {
        data object Auto : FunctionCallOption
        data object None : FunctionCallOption
        data class Forced(val name: String) : FunctionCallOption
    }

    data class FunctionDefinition(
        val name: String,
        val description: String,
        val parameters: Map<String, Any>
    )

    fun interrupt() {
        logger.info("Interrupting current request")
        interruptFlag.set(true)
    }

    fun resetInterrupt() {
        interruptFlag.set(false)
    }

    private fun createCompletionParams(
        message: String,
        temperature: Double?,
        maxTokens: Long?,
        topP: Double?
    ): ChatCompletionCreateParams {
        val builder = ChatCompletionCreateParams.builder()
            .model(config.model)
            .messages(
                listOf(
                    ChatCompletionMessageParam.ofChatCompletionUserMessageParam(
                        ChatCompletionUserMessageParam.builder()
                            .role(ChatCompletionUserMessageParam.Role.of("system"))
                            .content(ChatCompletionUserMessageParam.Content.ofTextContent(config.systemPrompt))
                            .build()
                    ),
                    ChatCompletionMessageParam.ofChatCompletionUserMessageParam(
                        ChatCompletionUserMessageParam.builder()
                            .role(ChatCompletionUserMessageParam.Role.USER)
                            .content(ChatCompletionUserMessageParam.Content.ofTextContent(message))
                            .build()
                    )
                )
            )
            .temperature(temperature ?: config.temperature)
            .maxTokens(maxTokens ?: config.maxTokens)
            .topP(topP ?: config.topP)

        if (config.functions.isNotEmpty()) {
            builder.functions(buildFunctionsParam())
            buildFunctionCallParam(config.functionCall)?.let { builder.functionCall(it) }
        }

        return builder.build()
    }

    private fun buildFunctionCallParam(
        functionCall: FunctionCallOption
    ): ChatCompletionFunctionCallOption? = when (functionCall) {
        is FunctionCallOption.Forced -> ChatCompletionFunctionCallOption.builder()
            .name(functionCall.name)
            .build()

        else -> null
    }

    private fun buildFunctionsParam(): List<ChatCompletionCreateParams.Function> =
        config.functions.map { function ->
            ChatCompletionCreateParams.Function.builder()
                .name(function.name)
                .description(function.description)
                .parameters(
                    FunctionParameters.builder()
                        .putAllAdditionalProperties(
                            function.parameters.mapValues { JsonValue.from(it.value) }
                        )
                        .build()
                )
                .build()
        }

    fun streamCompletion(
        message: String,
        temperature: Double? = null,
        maxTokens: Long? = null,
        topP: Double? = null
    ): Flow<String> = callbackFlow {
        logger.info("Creating streaming completion request")
        val isClosed = AtomicBoolean(false)
        interruptFlag.set(false)

        try {
            val params = createCompletionParams(message, temperature, maxTokens, topP)
            logger.info("Sending streaming request to OpenAI")

            val streamResponse = client.chat().completions().createStreaming(params)
            val stream = streamResponse.stream()

            stream.forEach { chatCompletion ->
                if (interruptFlag.get()) {
                    logger.info("Request interrupted")
                    close()
                    return@forEach
                }

                if (!isClosed.get()) {
                    chatCompletion.choices().forEach { choice ->
                        val content = choice.delta().content().orElse(null)
                        val functionArgs = choice.delta().functionCall().orElse(null)?.arguments()?.orElse(null)

                        when {
                            content?.isNotEmpty() == true -> {
                                logger.info("Emitting stream content: $content")
                                trySend(content).isSuccess
                            }

                            functionArgs != null -> {
                                logger.info("Emitting function args: $functionArgs")
                                trySend(functionArgs).isSuccess
                            }
                        }
                    }
                }
            }

            close()
        } catch (e: Exception) {
            logger.error("Error during streaming completion", e)
            close(e)
        }

        awaitClose {
            isClosed.set(true)
            logger.info("Streaming completion closed")
        }
    }

    suspend fun getSingleCompletion(
        message: String,
        temperature: Double? = null,
        maxTokens: Long? = null,
        topP: Double? = null
    ): String = withContext(Dispatchers.IO) {
        logger.info("Creating single completion request")

        val params = createCompletionParams(message, temperature, maxTokens, topP)
        logger.info("Sending single completion request to OpenAI")

        try {
            val chatCompletion = client.chat().completions().create(params)
            val choice = chatCompletion.choices().firstOrNull()
                ?: throw Exception("No choices returned in the response.")

            choice.message().content().orElse(null)
                ?: choice.message().functionCall().orElse(null)?.arguments()
                ?: throw Exception("No content or function call found in the response.")
        } catch (e: Exception) {
            logger.info("Error during single completion request: ${e.message}")
            throw e
        }
    }

    override fun close() {
        try {
            logger.info("Closing OpenAIClientWrapper")
            interrupt()
            (client as? AutoCloseable)?.close()
            logger.info("OpenAIClientWrapper closed successfully")
        } catch (e: Exception) {
            logger.error("Error while closing OpenAIClientWrapper", e)
            throw e
        }
    }

    companion object {
        fun create(config: Configuration): OpenAIClientWrapper {
            require(config.isValid()) { "Invalid configuration provided" }

            val client = OpenAIOkHttpClient.builder()
                .apiKey(config.apiKey)
                .baseUrl(config.endpoint)
                .build()

            return OpenAIClientWrapper(client, config)
        }
    }
}
