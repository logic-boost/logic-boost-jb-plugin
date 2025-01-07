package org.logicboost.chat.utils

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.logicboost.chat.actions.CommentAction.CommentResponse
import org.logicboost.chat.actions.ChangesAction.CodeChange

/**
 * Utility class for parsing JSON responses from LLM.
 */
object JsonParserUtils {

    private val logger = Logger.getInstance(JsonParserUtils::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses a JSON response into a [CommentResponse].
     * If standard parsing fails, attempts to extract JSON from Markdown code fences.
     */
    fun parseCommentResponse(response: String): String {
        logger.info("Parsing comment response: $response")

        try {
            return json.decodeFromString<CommentResponse>(response).commentedCode
        } catch (ex: Exception) {
            logger.debug("Failed to parse initial response, attempting to extract JSON from code fences.", ex)
        }

        //Extract JSON object enclosed within ```json and ```
        val regex = Regex("```json\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL)
        val matchResult = regex.find(response)

        if (matchResult != null && matchResult.groupValues.size > 1) {
            val extractedJson = matchResult.groupValues[1]
            val sanitized = sanitizeJsonString(extractedJson)

            // 3) Attempt to parse the sanitized JSON
            return try {
                json.decodeFromString<CommentResponse>(sanitized).commentedCode
            } catch (ex2: Exception) {
                logger.error("Failed to parse JSON extracted from code fences. Final JSON:\n$sanitized", ex2)
                throw IllegalStateException("Invalid response format from LLM: ${ex2.message}", ex2)
            }
        } else {
            logger.warn("No JSON code block found in response, returning raw text.")
            return response.trim()
        }
    }

    /**
     * Parses a JSON response into a list of [CodeChange] objects.
     */
    fun parseCodeChanges(response: String): List<CodeChange> {
        logger.info("Parsing code changes response: $response")
        return try {
            json.decodeFromString(ListSerializer(CodeChange.serializer()), response)
        } catch (e: Exception) {
            logger.info("Attempting to extract JSON array from response")
            extractJsonFromResponse(response)
        }
    }

    /**
     * Extracts JSON array from a response that might contain additional text.
     */
    private fun extractJsonFromResponse(response: String): List<CodeChange> {
        val startIndex = response.indexOf('[')
        val endIndex = response.lastIndexOf(']')
        if (startIndex != -1 && endIndex != -1) {
            val jsonString = response.substring(startIndex, endIndex + 1)
            return json.decodeFromString(ListSerializer(CodeChange.serializer()), jsonString)
        }
        throw IllegalStateException("Could not find valid JSON array in response")
    }

    /**
     * Removes common code fence markers and ensures typical JSON escapes.
     */
    fun sanitizeJsonString(jsonStr: String): String {
        return jsonStr
            .replace("```json", "")
            .replace("```", "")
    }
}
