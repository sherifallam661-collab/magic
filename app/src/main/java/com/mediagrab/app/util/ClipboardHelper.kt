package com.mediagrab.app.util

import android.content.ClipboardManager
import android.content.Context
import android.util.Patterns
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getValidUrlFromClipboard(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null
        if (!clipboard.hasPrimaryClip()) return null
        val item = clipboard.primaryClip?.getItemAt(0) ?: return null
        val text = item.coerceToText(context)?.toString()?.trim() ?: return null
        return extractFirstUrl(text)
    }

    fun isValidHttpUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        return Patterns.WEB_URL.matcher(trimmed).matches()
    }

    fun extractFirstUrl(text: String): String? {
        val matcher = Patterns.WEB_URL.matcher(text)
        while (matcher.find()) {
            val candidate = matcher.group()
            if (candidate.startsWith("http://", ignoreCase = true) ||
                candidate.startsWith("https://", ignoreCase = true)
            ) {
                return candidate
            }
        }
        return null
    }
}
