package com.ross.livemedia.utils

import android.util.Log

class Logger(private val tag: String) {
    fun info(message: String) {
        Log.i(tag, message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }

    fun debug(message: String) {
        Log.d(tag, message)
    }

    fun warn(message: String) {
        Log.w(tag, message)
    }
}
