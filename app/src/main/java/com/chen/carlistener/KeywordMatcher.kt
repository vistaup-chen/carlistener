package com.chen.carlistener

import android.content.Context

/**
 * 关键字匹配工具 — 短信和通知共用
 */
object KeywordMatcher {

    /**
     * 检查文本是否包含任一关键字（不区分大小写）
     */
    fun containsKeyword(context: Context, text: String): Boolean {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val keywords = prefs.getString(MainActivity.KEY_KEYWORDS, MainActivity.DEFAULT_KEYWORDS)
        if (keywords.isNullOrEmpty()) return false

        return keywords.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { text.contains(it, ignoreCase = true) }
    }
}
