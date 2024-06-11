package org.rivchain.cuplink.util

import java.util.concurrent.ExecutionException
import java.util.concurrent.Future


interface ListenableFuture<T> : Future<T> {
    fun addListener(listener: Listener<T>?)
    interface Listener<T> {
        fun onSuccess(result: T)
        fun onFailure(e: ExecutionException?)
    }
}