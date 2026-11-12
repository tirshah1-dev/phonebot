package com.phonebot.app

/** استخراج آیدی/نام‌کاربری از متن (اعلان‌ها یا محتوای صفحه). */
object IdExtractor {
    private val patterns = listOf(
        Regex("(?<![\\w.@])@([A-Za-z][A-Za-z0-9_.]{2,31})"),
        Regex("t\\.me/([A-Za-z][A-Za-z0-9_]{3,31})"),
        Regex("instagram\\.com/([A-Za-z0-9_.]{2,30})")
    )

    fun extract(text: String): List<String> {
        val found = sortedSetOf<String>()
        for (p in patterns) {
            for (m in p.findAll(text)) {
                found.add(m.groupValues[1].trimEnd('.'))
            }
        }
        return found.toList()
    }
}
