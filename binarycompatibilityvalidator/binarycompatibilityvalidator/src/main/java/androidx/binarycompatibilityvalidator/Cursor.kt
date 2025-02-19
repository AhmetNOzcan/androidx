/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.binarycompatibilityvalidator

class Cursor private constructor(
    private val lines: List<String>,
    private var rowIndex: Int = 0,
    private var columnIndex: Int = 0
) {
    constructor(text: String) : this(text.split("\n"))
    val currentLine: String
        get() = lines[rowIndex].slice(columnIndex until lines[rowIndex].length)
    fun hasNext() = rowIndex < (lines.size - 1)
    fun nextLine() {
        rowIndex++
        columnIndex = 0
    }

    fun parseSymbol(
        pattern: String,
        peek: Boolean = false,
        skipInlineWhitespace: Boolean = true
    ): String? {
        val match = Regex(pattern).find(currentLine)
        return match?.value?.also {
            if (!peek) {
                val offset = it.length + currentLine.indexOf(it)
                columnIndex += offset
                if (skipInlineWhitespace) {
                    skipInlineWhitespace()
                }
            }
        }
    }

    fun parseValidIdentifier(peek: Boolean = false): String? =
        parseSymbol("^[a-zA-Z_][a-zA-Z0-9_]+", peek)

    fun parseWord(peek: Boolean = false): String? = parseSymbol("[a-zA-Z]+", peek)

    fun copy() = Cursor(lines, rowIndex, columnIndex)

    internal fun skipInlineWhitespace() {
        while (currentLine.firstOrNull()?.isWhitespace() == true) {
            columnIndex++
        }
    }
}
