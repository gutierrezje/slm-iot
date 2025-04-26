package com.example.slmiot

import android.content.Context
import android.health.connect.datatypes.units.Temperature
import android.util.Log
import com.google.gson.Gson
import info.mqtt.android.service.MqttAndroidClient

import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException

private const val TAG = "MqttManager"
private const val USER = "admin"
private const val PASS = "Password1"

data class SensorData(val humidity: Float, val temperature: Float)

class MqttManager(
    private val context: Context,
    private val serverUri: String,
    private val clientId: String,
    private val topic: String,
    private val gson: Gson = Gson()
) {
    private var mqttClient: MqttAndroidClient? = null

    var onMessageReceived: ((String) -> Unit)? = null

    fun connect() {
        mqttClient = MqttAndroidClient(context, serverUri, clientId)
        mqttClient?.setCallback(object: MqttCallback {
            override fun messageArrived(topic: String?, message: org.eclipse.paho.client.mqttv3.MqttMessage?) {
                val payload = message?.payload?.decodeToString()
                payload?.let {
                    Log.d(TAG, "Received: $it from $topic")
                    try {
                        val data = gson.fromJson(it, SensorData::class.java)
                        onMessageReceived?.invoke(data.toString())
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed parsing message: ${e.message}")
                    }
                }
            }

            override fun connectionLost(cause: Throwable?) {
                Log.w(TAG, "Connection lost: ${cause?.message}")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Log.d(TAG, "Delivery complete")
            }
        })

        val options = getMqttConnectionOptions()
        try {
            mqttClient?.connect(options, null, object: IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Connected")
                    publish("Android", "Hello from Android!")
                    subscribe(topic)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.i(TAG, "Connection Failed: ${exception?.message}")
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "Error connecting: ${e.message}")
        }


    }

    private fun getMqttConnectionOptions(): MqttConnectOptions {
        val options = MqttConnectOptions()
        options.userName = USER
        options.password = PASS.toCharArray()
        options.isAutomaticReconnect = true
        options.isCleanSession = false
        options.connectionTimeout = 60
        return options
    }

    fun subscribe(topic: String, qos: Int = 1) {
        try {
            mqttClient?.subscribe(topic, qos, null,
                object: IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "Subscribed to $topic")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "Subscription failed: ${exception?.message}")
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing: ${e.message}")
        }
    }

    fun publish(topic: String, msg: String, qos: Int = 1) {
        try {
            val message = org.eclipse.paho.client.mqttv3.MqttMessage()
            message.payload = msg.toByteArray()
            message.qos = qos
            mqttClient?.publish(topic, message, null, object: IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Published to $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Publish failed: ${exception?.message}")
                }
            })
        }
        catch (e: Exception) {
            Log.e(TAG, "Error publishing: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()?.apply {
                actionCallback = object: IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "Disconnected")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "Disconnect failed: ${exception?.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        }
    }
}