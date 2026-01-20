package com.example.personalcloud

import android.content.Context

class ServerManager(private val context: Context) {

    fun isServerRunning(): Boolean {
        return ServerService.isRunning
    }

    fun getServerStatus(): String {
        return if (isServerRunning()) {
            "Server is running"
        } else {
            "Server is stopped"
        }
    }
}