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

package androidx.wear.watchface.editor

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.complications.ComplicationDataSourceInfo
import androidx.wear.complications.data.ComplicationData
import androidx.wear.watchface.RenderParameters
import androidx.wear.watchface.client.ComplicationSlotState
import androidx.wear.watchface.client.HeadlessWatchFaceClient
import androidx.wear.watchface.client.WatchFaceId
import androidx.wear.watchface.style.UserStyle
import androidx.wear.watchface.style.UserStyleSchema
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * [ListenableFuture]-based compatibility wrapper around [EditorSession]'s suspending methods.
 */
public class ListenableEditorSession(
    private val wrappedEditorSession: EditorSession
) : EditorSession {
    public companion object {
        /**
         * Constructs a [ListenableFuture] for a [ListenableEditorSession] for an on watch face
         * editor. This registers an activity result handler and so it must be called during an
         * Activity or Fragment initialization path.
         *
         * If watch face editor takes more than 4s to create a watch face, returned future will be
         * resolved with [TimeoutCancellationException] exception.
         *
         * @param activity The [ComponentActivity] associated with the EditorSession.
         * @param editIntent [Intent] sent by SysUI to launch the editing session.
         */
        @SuppressWarnings("ExecutorRegistration")
        @JvmStatic
        @UiThread
        public fun listenableCreateOnWatchEditorSession(
            activity: ComponentActivity,
            editIntent: Intent
        ): ListenableFuture<ListenableEditorSession?> {
            val result = ResolvableFuture.create<ListenableEditorSession?>()
            val coroutineScope =
                CoroutineScope(object : CoroutineDispatcher() {
                    override fun dispatch(context: CoroutineContext, block: Runnable) {
                        block.run()
                    }
                })
            coroutineScope.launch {
                try {
                    result.set(
                        ListenableEditorSession(
                            EditorSession.createOnWatchEditorSession(activity, editIntent)
                        )
                    )
                } catch (e: Exception) {
                    result.setException(e)
                }
            }
            return result
        }

        /**
         * Constructs an [ListenableEditorSession] for a remote watch face editor.
         *
         * @param activity The [ComponentActivity] associated with the EditorSession.
         * @param editIntent [Intent] sent by SysUI to launch the editing session.
         * @param headlessWatchFaceClient The [HeadlessWatchFaceClient] that backs the constructed
         * EditorSession.
         */
        @JvmStatic
        @RequiresApi(27)
        @UiThread
        public fun createHeadlessEditorSession(
            activity: ComponentActivity,
            editIntent: Intent,
            headlessWatchFaceClient: HeadlessWatchFaceClient
        ): ListenableEditorSession = ListenableEditorSession(
            EditorSession.createHeadlessEditorSession(
                activity,
                editIntent,
                headlessWatchFaceClient
            )
        )
    }

    private fun getCoroutineScope(): CoroutineScope =
        (wrappedEditorSession as BaseEditorSession).coroutineScope

    override val watchFaceComponentName: ComponentName = wrappedEditorSession.watchFaceComponentName

    @get:RequiresApi(Build.VERSION_CODES.R)
    @RequiresApi(Build.VERSION_CODES.R)
    override val watchFaceId: WatchFaceId = wrappedEditorSession.watchFaceId

    override var userStyle: UserStyle by wrappedEditorSession::userStyle

    override val previewReferenceTimeMillis: Long = wrappedEditorSession.previewReferenceTimeMillis

    override val userStyleSchema: UserStyleSchema by wrappedEditorSession::userStyleSchema

    override val complicationSlotsState: Map<Int, ComplicationSlotState> by
    wrappedEditorSession::complicationSlotsState

    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("isCommitChangesOnClose")
    override var commitChangesOnClose: Boolean by wrappedEditorSession::commitChangesOnClose

    /** [ListenableFuture] wrapper around [EditorSession.getComplicationsPreviewData]. */
    public fun getListenableComplicationPreviewData():
        ListenableFuture<Map<Int, ComplicationData>> {
            val future = ResolvableFuture.create<Map<Int, ComplicationData>>()
            getCoroutineScope().launch {
                try {
                    future.set(wrappedEditorSession.getComplicationsPreviewData())
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }

    /** [ListenableFuture] wrapper around [EditorSession.getComplicationsDataSourceInfo]. */
    public fun getListenableComplicationsProviderInfo():
        ListenableFuture<Map<Int, ComplicationDataSourceInfo?>> {
            val future = ResolvableFuture.create<Map<Int, ComplicationDataSourceInfo?>>()
            getCoroutineScope().launch {
                try {
                    future.set(wrappedEditorSession.getComplicationsDataSourceInfo())
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }

    override suspend fun getComplicationsPreviewData(): Map<Int, ComplicationData> =
        wrappedEditorSession.getComplicationsPreviewData()

    override suspend fun getComplicationsDataSourceInfo(): Map<Int, ComplicationDataSourceInfo?> =
        wrappedEditorSession.getComplicationsDataSourceInfo()

    @get:SuppressWarnings("AutoBoxing")
    override val backgroundComplicationSlotId: Int? by
    wrappedEditorSession::backgroundComplicationSlotId

    @SuppressWarnings("AutoBoxing")
    override fun getComplicationSlotIdAt(x: Int, y: Int): Int? =
        wrappedEditorSession.getComplicationSlotIdAt(x, y)

    override fun renderWatchFaceToBitmap(
        renderParameters: RenderParameters,
        calendarTimeMillis: Long,
        slotIdToComplicationData: Map<Int, ComplicationData>?
    ): Bitmap = wrappedEditorSession.renderWatchFaceToBitmap(
        renderParameters,
        calendarTimeMillis,
        slotIdToComplicationData
    )

    /** [ListenableFuture] wrapper around [EditorSession.openComplicationDataSourceChooser]. */
    public fun listenableOpenComplicationDataSourceChooser(
        complicationSlotId: Int
    ): ListenableFuture<ChosenComplicationDataSource?> {
        val future = ResolvableFuture.create<ChosenComplicationDataSource?>()
        getCoroutineScope().launch {
            try {
                future.set(
                    wrappedEditorSession.openComplicationDataSourceChooser(complicationSlotId)
                )
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override suspend fun openComplicationDataSourceChooser(complicationSlotId: Int):
        ChosenComplicationDataSource? =
            wrappedEditorSession.openComplicationDataSourceChooser(complicationSlotId)

    override fun close() {
        wrappedEditorSession.close()
    }
}
