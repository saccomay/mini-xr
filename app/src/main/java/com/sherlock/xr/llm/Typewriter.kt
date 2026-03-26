package com.sherlock.xr.llm

import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.*

class Typewriter(private val tv: TextView, private val scroll: ScrollView) {
    private var job: Job? = null

    fun start(full: String, cps: Int = 45) {
        cancel()
        tv.text = ""
        job = CoroutineScope(Dispatchers.Main.immediate).launch {
            val delayMs = (1000f / cps).toLong().coerceAtLeast(8)
            for (ch in full) {
                tv.append(ch.toString())
                // auto scroll to bottom
                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                delay(delayMs)
            }
        }
    }

    fun cancel() { job?.cancel(); job = null }
}
