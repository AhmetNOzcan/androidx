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

package androidx.privacysandbox.ads.adservices.measurement

import android.adservices.common.AdServicesPermissions
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.InputEvent
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresExtension
import androidx.annotation.RequiresPermission
import androidx.annotation.RestrictTo
import androidx.privacysandbox.ads.adservices.common.ExperimentalFeatures
import androidx.privacysandbox.ads.adservices.internal.asAdServicesOutcomeReceiver
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@RestrictTo(RestrictTo.Scope.LIBRARY)
@SuppressLint("ClassVerificationFailure", "NewApi")
@RequiresExtension(extension = Build.VERSION_CODES.R, version = 11)
class MeasurementManagerApi30Ext11Impl(context: Context) : MeasurementManager() {
    private val mMeasurementManager: android.adservices.measurement.MeasurementManager =
        android.adservices.measurement.MeasurementManager.get(context)

    @DoNotInline
    override suspend fun deleteRegistrations(deletionRequest: DeletionRequest) {
        suspendCancellableCoroutine<Any> { continuation ->
            mMeasurementManager.deleteRegistrations(
                deletionRequest.convertToAdServices(),
                Runnable::run,
                continuation.asAdServicesOutcomeReceiver()
            )
        }
    }

    @DoNotInline
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    override suspend fun registerSource(attributionSource: Uri, inputEvent: InputEvent?) {
        suspendCancellableCoroutine<Any> { continuation ->
            mMeasurementManager.registerSource(
                attributionSource,
                inputEvent,
                Runnable::run,
                continuation.asAdServicesOutcomeReceiver()
            )
        }
    }

    @DoNotInline
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    override suspend fun registerTrigger(trigger: Uri) {
        suspendCancellableCoroutine<Any> { continuation ->
            mMeasurementManager.registerTrigger(
                trigger,
                Runnable::run,
                continuation.asAdServicesOutcomeReceiver())
        }
    }

    @DoNotInline
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    override suspend fun registerWebSource(request: WebSourceRegistrationRequest) {
        suspendCancellableCoroutine<Any> { continuation ->
            mMeasurementManager.registerWebSource(
                request.convertToAdServices(),
                Runnable::run,
                continuation.asAdServicesOutcomeReceiver())
        }
    }

    @DoNotInline
    @ExperimentalFeatures.RegisterSourceOptIn
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    override suspend fun registerSource(
        request: SourceRegistrationRequest
    ): Unit = coroutineScope {
        request.registrationUris.forEach { uri ->
            launch {
                suspendCancellableCoroutine<Any> { continuation ->
                    mMeasurementManager.registerSource(
                        uri,
                        request.inputEvent,
                        Runnable::run,
                        continuation.asAdServicesOutcomeReceiver()
                    )
                }
            }
        }
    }

    @DoNotInline
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    override suspend fun registerWebTrigger(request: WebTriggerRegistrationRequest) {
        suspendCancellableCoroutine<Any> { continuation ->
            mMeasurementManager.registerWebTrigger(
                request.convertToAdServices(),
                Runnable::run,
                continuation.asAdServicesOutcomeReceiver())
        }
    }

    @DoNotInline
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION)
    override suspend fun getMeasurementApiStatus(): Int = suspendCancellableCoroutine {
            continuation ->
        mMeasurementManager.getMeasurementApiStatus(
            Runnable::run,
            continuation.asAdServicesOutcomeReceiver())
    }
}
