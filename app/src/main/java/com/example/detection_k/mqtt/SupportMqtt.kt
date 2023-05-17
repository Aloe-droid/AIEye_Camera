package com.example.detection_k.mqtt

import org.eclipse.paho.client.mqttv3.MqttClient

class SupportMqtt {
    private var mqttClient: MqttClient? = null

    companion object {
        private var instance: SupportMqtt? = null

        @Synchronized
        fun getInstance(): SupportMqtt {
            if (instance == null) {
                instance = SupportMqtt()
            }
            return instance!!
        }
    }

    fun getMqttClient(): MqttClient? {
        return mqttClient
    }

    fun setMqttClient(mqttClient: MqttClient) {
        this.mqttClient = mqttClient
    }
}