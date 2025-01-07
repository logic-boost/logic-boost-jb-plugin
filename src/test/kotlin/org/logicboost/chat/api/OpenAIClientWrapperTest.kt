package org.logicboost.chat.api

import com.openai.client.OpenAIClient
import com.openai.models.ChatCompletion
import com.openai.models.ChatCompletionMessage
import com.openai.models.Completion
import com.openai.services.blocking.ChatService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Constructor
import java.util.concurrent.atomic.AtomicBoolean

class OpenAIClientWrapperTest {

    private lateinit var validConfig: OpenAIClientWrapper.Configuration
    private lateinit var invalidConfig: OpenAIClientWrapper.Configuration

    @Before
    fun setUp() {
        validConfig = OpenAIClientWrapper.Configuration(
            apiKey = "valid-api-key",
            endpoint = "https://api.openai.com/v1",
            model = "gpt-4o-mini",
            systemPrompt = "You are a helpful assistant"
        )
        invalidConfig = OpenAIClientWrapper.Configuration(
            apiKey = "",
            endpoint = "",
            model = "",
            systemPrompt = ""
        )
    }

    /**
     * Helper function to instantiate OpenAIClientWrapper using reflection.
     */
    private fun createWrapper(
        clientMock: OpenAIClient,
        config: OpenAIClientWrapper.Configuration
    ): OpenAIClientWrapper {
        val constructor: Constructor<OpenAIClientWrapper> =
            OpenAIClientWrapper::class.java.getDeclaredConstructor(
                OpenAIClient::class.java,
                OpenAIClientWrapper.Configuration::class.java
            )
        constructor.isAccessible = true
        return constructor.newInstance(clientMock, config)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create should throw when configuration is invalid`() {
        OpenAIClientWrapper.create(invalidConfig)
    }

    @Test
    fun `create should return OpenAIClientWrapper when configuration is valid`() {
        val wrapper = OpenAIClientWrapper.create(validConfig)
        assertNotNull(wrapper)
        wrapper.close()
    }

    @Test
    fun `interrupt sets interruptFlag to true`() {
        val clientMock = mockk<OpenAIClient>(relaxed = true)
        val wrapper = createWrapper(clientMock, validConfig)

        // Access the private interruptFlag field via reflection
        val interruptField = OpenAIClientWrapper::class.java.getDeclaredField("interruptFlag")
        interruptField.isAccessible = true
        val flag = interruptField.get(wrapper) as AtomicBoolean

        // Check initial state
        assertFalse("Initially, interruptFlag should be false", flag.get())

        // Call interrupt()
        wrapper.interrupt()

        // Validate new state
        assertTrue("After interrupt(), interruptFlag should be true", flag.get())

        wrapper.close()
    }

    @Test
    fun `resetInterrupt sets interruptFlag to false`() {
        val clientMock = mockk<OpenAIClient>(relaxed = true)
        val wrapper = createWrapper(clientMock, validConfig)

        // Access the private interruptFlag field via reflection
        val interruptField = OpenAIClientWrapper::class.java.getDeclaredField("interruptFlag")
        interruptField.isAccessible = true
        val flag = interruptField.get(wrapper) as AtomicBoolean

        // Set it to true first
        wrapper.interrupt()
        assertTrue("After interrupt(), interruptFlag should be true", flag.get())

        // Now reset
        wrapper.resetInterrupt()
        assertFalse("After resetInterrupt(), interruptFlag should be false", flag.get())

        wrapper.close()
    }

    @Test
    fun `getSingleCompletion returns expected content`() = runBlocking {
        // Arrange
        val clientMock = mockk<OpenAIClient>()
        val chatCompletionMock = mockk<ChatCompletion>()
        val choiceMock = mockk<ChatCompletion.Choice>()
        val messageMock = mockk<ChatCompletionMessage>()
        val chatServiceMock = mockk<ChatService>()
        val chatCompletionServiceMock = mockk<com.openai.services.blocking.chat.CompletionService>()

        // Set up all required mocks
        every { clientMock.chat() } returns chatServiceMock
        every { chatServiceMock.completions() } returns chatCompletionServiceMock
        every { chatCompletionServiceMock.create(any()) } returns chatCompletionMock
        every { chatCompletionMock.choices() } returns listOf(choiceMock)
        every { choiceMock.message() } returns messageMock  // Mock the message() call
        every { messageMock.content() } returns java.util.Optional.of("Hello from AI!")  // Mock content() to return Optional

        val wrapper = createWrapper(clientMock, validConfig)

        // Act
        val result = wrapper.getSingleCompletion("Test prompt")

        // Assert
        assertEquals("Hello from AI!", result)
        verify(exactly = 1) { chatCompletionServiceMock.create(any()) }

        wrapper.close()
    }


    @Test
    fun `getSingleCompletion throws exception when no choices returned`() = runBlocking {
        // Arrange
        val clientMock = mockk<OpenAIClient>(relaxed = true)
        val completionMock = mockk<Completion>(relaxed = true)

        // Simulate empty choices
        every { clientMock.completions().create(any()) } returns completionMock
        every { completionMock.choices() } returns emptyList()

        val wrapper = createWrapper(clientMock, validConfig)

        // Act and Assert
        try {
            wrapper.getSingleCompletion("Test prompt")
            fail("Expected an exception when no choices are returned.")
        } catch (e: Exception) {
            assertTrue(e.message.orEmpty().contains("No choices returned in the response"))
        } finally {
            wrapper.close()
        }
    }

    @Test
    fun `close calls interrupt and closes underlying client if AutoCloseable`() {
        val clientMock = mockk<OpenAIClient>(relaxed = true)

        val wrapper = createWrapper(clientMock, validConfig)
        wrapper.close()

        // The `close()` method calls `interrupt()`.
        val interruptField = OpenAIClientWrapper::class.java.getDeclaredField("interruptFlag")
        interruptField.isAccessible = true
        val flag = interruptField.get(wrapper) as AtomicBoolean
        assertTrue("interruptFlag should be set to true on close", flag.get())

    }

}
