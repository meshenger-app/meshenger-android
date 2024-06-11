package org.rivchain.cuplink.util

import java.util.LinkedList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


class SettableFuture<T> : ListenableFuture<T> {
    private val listeners: MutableList<ListenableFuture.Listener<T>?> = LinkedList()
    private var completed = false
    private var canceled = false

    @Volatile
    private var result: T? = null

    @Volatile
    private var exception: Throwable? = null

    constructor()
    constructor(value: T) {
        result = value
        completed = true
    }

    constructor(throwable: Throwable?) {
        exception = throwable
        completed = true
    }

    @Synchronized
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        if (!completed && !canceled) {
            canceled = true
            return true
        }
        return false
    }

    @Synchronized
    override fun isCancelled(): Boolean {
        return canceled
    }

    @Synchronized
    override fun isDone(): Boolean {
        return completed
    }

    fun set(result: T): Boolean {
        synchronized(this) {
            if (completed || canceled) return false
            this.result = result
            completed = true
            (this as Object).notifyAll()
        }
        notifyAllListeners()
        return true
    }

    fun setException(throwable: Throwable?): Boolean {
        synchronized(this) {
            if (completed || canceled) return false
            exception = throwable
            completed = true
            (this as Object).notifyAll()
        }
        notifyAllListeners()
        return true
    }

    fun deferTo(other: ListenableFuture<T>) {
        other.addListener(object : ListenableFuture.Listener<T> {
            override fun onSuccess(result: T) {
                this@SettableFuture.set(result)
            }

            override fun onFailure(e: ExecutionException?) {
                setException(e!!.cause)
            }
        })
    }

    @Synchronized
    @Throws(InterruptedException::class, ExecutionException::class)
    override fun get(): T? {
        while (!completed) (this as Object).wait()
        return if (exception != null) throw ExecutionException(exception) else result
    }

    @Synchronized
    @Throws(
        InterruptedException::class,
        ExecutionException::class,
        TimeoutException::class
    )
    override fun get(timeout: Long, unit: TimeUnit): T? {
        val startTime = System.currentTimeMillis()
        while (!completed && System.currentTimeMillis() - startTime < unit.toMillis(timeout)) {
            (this as Object).wait(unit.toMillis(timeout))
        }
        return if (!completed) throw TimeoutException() else get()
    }

    override fun addListener(listener: ListenableFuture.Listener<T>?) {
        synchronized(this) {
            listeners.add(listener)
            if (!completed) return
        }
        notifyListener(listener)
    }

    private fun notifyAllListeners() {
        var localListeners: List<ListenableFuture.Listener<T>?>
        synchronized(this) {
            localListeners =
                LinkedList(
                    listeners
                )
        }
        for (listener in localListeners) {
            notifyListener(listener)
        }
    }

    private fun notifyListener(listener: ListenableFuture.Listener<T>?) {
        if (exception != null) listener!!.onFailure(ExecutionException(exception)) else result?.let {
            listener!!.onSuccess(
                it
            )
        }
    }
}