/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.ui.geometry

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

// Masks everything but the sign bit
internal const val UnsignedFloatMask = 0x7fffffffL
internal const val DualUnsignedFloatMask = 0x7fffffff_7fffffffL

// Any value greater than this is a NaN
internal const val FloatInfinityBase = 0x7f800000L
internal const val DualFloatInfinityBase = 0x7f800000_7f800000L

// Same as Offset/Size.Unspecified.packedValue, but avoids a getstatic
internal const val UnspecifiedPackedFloats = 0x7fc00000_7fc00000L // NaN_NaN

// 0x80000000_80000000UL.toLong() but expressed as a const value
// Mask for the sign bit of the two floats packed in a long
internal const val DualFloatSignBit = -0x7fffffff_80000000L
// Set the highest bit of each 32 bit chunk in a 64 bit word
internal const val Uint64High32 = -0x7fffffff_80000000L

// This function exists so we do *not* inline the throw. It keeps
// the call site much smaller and since it's the slow path anyway,
// we don't mind the extra function call
internal fun throwIllegalStateException(message: String) {
    throw IllegalStateException(message)
}

// Like Kotlin's require() but without the .toString() call
@OptIn(ExperimentalContracts::class)
internal inline fun checkPrecondition(value: Boolean, lazyMessage: () -> String) {
    contract {
        returns() implies value
    }
    if (!value) {
        throwIllegalStateException(lazyMessage())
    }
}
