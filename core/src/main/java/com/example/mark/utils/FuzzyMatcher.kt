package com.example.mark.utils

import kotlin.math.min

object FuzzyMatcher {

    /**
     * Bounded Levenshtein distance. Returns the distance, or [limit] + 1 if it
     * exceeds the limit.
     */
    fun levenshtein(s1: String, s2: String, limit: Int = 2): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val n = s1.length
        val m = s2.length

        if (kotlin.math.abs(n - m) > limit) return limit + 1

        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)

        for (i in 1..n) {
            curr[0] = i
            var minInRow = curr[0]
            for (j in 1..m) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                minInRow = min(minInRow, curr[j])
            }
            if (minInRow > limit) return limit + 1
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[m]
    }
}
