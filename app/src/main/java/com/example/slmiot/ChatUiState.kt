package com.example.slmiot

import androidx.compose.runtime.toMutableStateList

const val USER_PREFIX = "user"
const val MODEL_PREFIX = "model"

interface UiState {
    val messages: List<ChatMessage>

    /** Creates a new loading message. */
    fun createLoadingMessage()

    /**
     * Appends the specified text to the message with the specified ID.
     * THe underlying implementations may split the re-use messages or create new ones. The method
     * always returns the ID of the message used.
     * @param done - indicates whether the model has finished generating the message.
     */
    fun appendMessage(text: String, done: Boolean = false)

    /** Creates a new message with the specified text and author. */
    fun addMessage(text: String, author: String)

    /** Clear all messages. */
    fun clearMessages()

    /** Formats a messages from the user into the prompt format of the model. */
    fun formatPrompt(text:String) : String
}

/**
 * An implementation of [UiState] to be used with the Gemma model.
 */
class GemmaUiState(
    messages: List<ChatMessage> = emptyList()
) : UiState {
    private val START_TURN = "<start_of_turn>"
    private val END_TURN = "<end_of_turn>"

    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()
    override val messages: List<ChatMessage> = _messages.asReversed()
    private var _currentMessageId = ""

    override fun createLoadingMessage() {
        val chatMessage = ChatMessage(author = MODEL_PREFIX, isLoading = true)
        _messages.add(chatMessage)
        _currentMessageId = chatMessage.id
    }

    override fun appendMessage(text: String, done: Boolean) {
        val newText =  text.replace(END_TURN, "")
        val index = _messages.indexOfFirst { it.id == _currentMessageId }
        if (index != -1) {
            val newMessage =  _messages[index].rawMessage + newText
            _messages[index] = _messages[index].copy(rawMessage = newMessage, isLoading = false)
        }
    }

    override fun addMessage(text: String, author: String) {
        val chatMessage = ChatMessage(
            rawMessage = text,
            author = author
        )
        _messages.add(chatMessage)
        _currentMessageId = chatMessage.id
    }

    override fun clearMessages() {
        _messages.clear()
    }

    override fun formatPrompt(text: String): String {
        return "$START_TURN$USER_PREFIX\n$text$END_TURN$START_TURN$MODEL_PREFIX"
    }
}