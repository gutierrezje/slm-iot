package com.example.slmiot

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import kotlin.math.max

class ChatViewModel(
    private var inferenceModel: InferenceModel
) : ViewModel() {

    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(inferenceModel.uiState)
    val uiState: StateFlow<UiState> =_uiState.asStateFlow()

    private val _tokensRemaining = MutableStateFlow(-1)
    val tokensRemaining: StateFlow<Int> = _tokensRemaining.asStateFlow()

    private val _textInputEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val isTextInputEnabled: StateFlow<Boolean> = _textInputEnabled.asStateFlow()

    private val _mqttMessages = MutableSharedFlow<List<String>>()
    val mqttMessages: SharedFlow<List<String>> = _mqttMessages.asSharedFlow()

    private var mqttManager: MqttManager? = null

    fun initializeMqtt(context: Context,
                       serverUri: String,
                       clientId: String,
                       topic: String) {
        mqttManager = MqttManager(context, serverUri, clientId, topic)
            .apply {
                onMessageReceived = { message ->
                    viewModelScope.launch(Dispatchers.IO) {
                        _mqttMessages.emit(listOf(message))
                        processMqttMessage(message)
                    }
                }
                connect()
            }
    }

    private fun processMqttMessage(message: String) {
        val chatMessage = ChatMessage(
            message,
            "Received from MQTT: $message",
            "MQTT",
            isLoading = false
        )
            //_uiState.value.addMessage(chatMessage.message, chatMessage.author)
        sendMessageSilently("This is a sensor reading from the outside world. " +
                "Extract the temperature and humidity from this ${chatMessage.message}")
    }

    fun resetInferenceModel(newModel: InferenceModel) {
        inferenceModel = newModel
        _uiState.value = inferenceModel.uiState
    }

    fun sendMessage(userMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.addMessage(userMessage, USER_PREFIX)
            _uiState.value.createLoadingMessage()
            setInputEnabled(false)
            try {
                val asyncInference =  inferenceModel.generateResponseAsync(userMessage, { partialResult, done ->
                    _uiState.value.appendMessage(partialResult, done)
                    if (done) {
                        setInputEnabled(true)  // Re-enable text input
                    } else {
                        // Reduce current token count (estimate only). sizeInTokens() will be used
                        // when computation is done
                        _tokensRemaining.update { max(0, it - 1) }
                    }
                })
                // Once the inference is done, recompute the remaining size in tokens
                asyncInference.addListener({
                    viewModelScope.launch(Dispatchers.IO) {
                        recomputeSizeInTokens(userMessage)
                    }
                }, Dispatchers.Main.asExecutor())
            } catch (e: Exception) {
                _uiState.value.addMessage(e.localizedMessage ?: "Unknown Error", MODEL_PREFIX)
                setInputEnabled(true)
            }
        }
    }

    fun sendMessageSilently(userMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            setInputEnabled(false)
            try {
                val asyncInference =  inferenceModel.generateResponseAsync(userMessage, { partialResult, done ->
                    _uiState.value.appendMessage("Hi there!", done)
                    if (done) {
                        setInputEnabled(true)  // Re-enable text input
                    } else {
                        // Reduce current token count (estimate only). sizeInTokens() will be used
                        // when computation is done
                        _tokensRemaining.update { max(0, it - 1) }
                    }
                })
                // Once the inference is done, recompute the remaining size in tokens
                asyncInference.addListener({
                    viewModelScope.launch(Dispatchers.IO) {
                        recomputeSizeInTokens(userMessage)
                    }
                }, Dispatchers.Main.asExecutor())
            } catch (e: Exception) {
                _uiState.value.addMessage(e.localizedMessage ?: "Unknown Error", MODEL_PREFIX)
                setInputEnabled(true)
            }
        }
    }

    private fun setInputEnabled(isEnabled: Boolean) {
        _textInputEnabled.value = isEnabled
    }

    fun recomputeSizeInTokens(message: String) {
        val remainingTokens = inferenceModel.estimateTokensRemaining(message)
        _tokensRemaining.value = remainingTokens
    }

    override fun onCleared() {
        super.onCleared()
        mqttManager?.disconnect()
    }

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val inferenceModel = InferenceModel.getInstance(context)
                return ChatViewModel(inferenceModel) as T
            }
        }
    }
}
