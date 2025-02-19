/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.foundation.text.selection

import androidx.compose.foundation.PlatformMagnifierFactory
import androidx.compose.foundation.contextmenu.ContextMenuScope
import androidx.compose.foundation.contextmenu.ContextMenuState
import androidx.compose.foundation.contextmenu.close
import androidx.compose.foundation.isPlatformMagnifierSupported
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.text.TextContextMenuItems
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize

internal actual val PointerEvent.isShiftPressed: Boolean
    get() = false

// We use composed{} to read a local, but don't provide inspector info because the underlying
// magnifier modifier provides more meaningful inspector info.
internal actual fun Modifier.textFieldMagnifier(manager: TextFieldSelectionManager): Modifier {
    // Avoid tracking animation state on older Android versions that don't support magnifiers.
    if (!isPlatformMagnifierSupported()) {
        return this
    }

    return composed {
        val density = LocalDensity.current
        var magnifierSize by remember { mutableStateOf(IntSize.Zero) }
        animatedSelectionMagnifier(
            magnifierCenter = {
                calculateSelectionMagnifierCenterAndroid(manager, magnifierSize)
            },
            platformMagnifier = { center ->
                Modifier.magnifier(
                    sourceCenter = { center() },
                    onSizeChanged = { size ->
                        magnifierSize = with(density) {
                            IntSize(size.width.roundToPx(), size.height.roundToPx())
                        }
                    },
                    useTextDefault = true,
                    platformMagnifierFactory = PlatformMagnifierFactory.getForCurrentPlatform()
                )
            }
        )
    }
}

@ReadOnlyComposable
@Composable
internal fun TextFieldSelectionManager.contextMenuBuilder(
    contextMenuState: ContextMenuState
): ContextMenuScope.() -> Unit {
    val cutString = TextContextMenuItems.Cut.resolvedString()
    val copyString = TextContextMenuItems.Copy.resolvedString()
    val pasteString = TextContextMenuItems.Paste.resolvedString()
    val selectAllString = TextContextMenuItems.SelectAll.resolvedString()
    return {
        val isPassword = visualTransformation is PasswordVisualTransformation
        val hasSelection = !value.selection.collapsed
        item(
            label = cutString,
            enabled = hasSelection && editable && !isPassword,
            onClick = {
                cut()
                contextMenuState.close()
            },
        )
        item(
            label = copyString,
            enabled = hasSelection && !isPassword,
            onClick = {
                copy(cancelSelection = false)
                contextMenuState.close()
            },
        )
        item(
            label = pasteString,
            enabled = editable && clipboardManager?.hasText() == true,
            onClick = {
                paste()
                contextMenuState.close()
            },
        )
        item(
            label = selectAllString,
            enabled = value.selection.length != value.text.length,
            onClick = {
                selectAll()
                contextMenuState.close()
            },
        )
    }
}
