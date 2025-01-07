package org.logicboost.chat.api

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.logicboost.chat.settings.ChatSettings
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DuckDuckGoSearch private constructor(
    private val client: HttpClient,
    private val openAIWrapper: OpenAIClientWrapper
) {
    private val logger = Logger.getInstance(DuckDuckGoSearch::class.java)

    @Serializable
    data class SearchResponse(
        val abstract: String,
        val abstractText: String,
        val abstractSource: String,
        val abstractURL: String,
        val relatedTopics: List<RelatedTopic> = emptyList()
    )

    @Serializable
    data class RelatedTopic(
        val text: String,
        val firstURL: String
    )

    companion object {
        private const val DUCKDUCKGO_API_URL = "https://api.duckduckgo.com/"
        private val searchFunction = OpenAIClientWrapper.FunctionDefinition(
            name = "search",
            description = "Search the internet using DuckDuckGo",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf(
                        "type" to "string",
                        "description" to "The search query to execute"
                    )
                ),
                "required" to listOf("query")
            )
        )

        fun create(): DuckDuckGoSearch? {
            val settings = ChatSettings.getInstance()
            val llmConfig = settings.getSmartLLMConfig() ?: return null

            if (!llmConfig.isEnabled || !llmConfig.forSmartFunctions) {
                return null
            }

            val openAIConfig = OpenAIClientWrapper.Configuration(
                apiKey = llmConfig.apiKey,
                endpoint = llmConfig.apiEndpoint,
                model = llmConfig.model,
                systemPrompt = llmConfig.systemPrompt,
                temperature = llmConfig.temperature,
                maxTokens = llmConfig.maxTokens.toLong(),
                topP = llmConfig.topP,
                functions = listOf(searchFunction),
                functionCall = OpenAIClientWrapper.FunctionCallOption.Forced("search")
            )

            val httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()

            return DuckDuckGoSearch(
                client = httpClient,
                openAIWrapper = OpenAIClientWrapper.create(openAIConfig)
            )
        }
    }

    suspend fun search(query: String): SearchResponse = withContext(Dispatchers.IO) {
        try {
            logger.info("Executing DuckDuckGo search for query: $query")

            val encodedQuery = URI(DUCKDUCKGO_API_URL)
                .resolve("?q=${query.replace(" ", "+")}&format=json")
                .toString()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(encodedQuery))
                .header("User-Agent", "LogicBoostSearch/1.0")
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                throw Exception("Failed to get search results. Status: ${response.statusCode()}")
            }

            Json.decodeFromString<SearchResponse>(response.body())
        } catch (e: Exception) {
            logger.error("Error during DuckDuckGo search", e)
            throw e
        }
    }

    suspend fun processUserQuery(userInput: String): String {
        return try {
            // Get the search query from OpenAI
            val searchQuery = openAIWrapper.getSingleCompletion(userInput)

            // Execute the search
            val searchResults = search(searchQuery)

            // Format and return the results
            buildString {
                appendLine("Search Results for: $searchQuery")
                appendLine("\nMain Result:")
                appendLine(searchResults.abstractText)

                if (searchResults.relatedTopics.isNotEmpty()) {
                    appendLine("\nRelated Topics:")
                    searchResults.relatedTopics.take(3).forEach { topic ->
                        appendLine("- ${topic.text}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing user query", e)
            "Sorry, I encountered an error while searching: ${e.message}"
        }
    }

    fun close() {
        try {
            openAIWrapper.close()
        } catch (e: Exception) {
            logger.error("Error closing DuckDuckGo search", e)
            throw e
        }
    }
}
