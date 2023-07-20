package com.mutkuensert.downloader

/*Copyright 2022 Mustafa Utku Ensert

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.FileOutputStream
import java.io.OutputStream

private const val TAG = "Downloader"
private const val DOWNLOADER_NOTIF_CHANNEL_ID: String = "downloader_notification_channel"
private const val PROGRESS_MAX = 100

/**
 * @property initActivityResultLauncher Initialize activityResultLauncher
 * in a Fragment or Activity.
 *
 * @property setFileFormatExtractor Set if the file format isn't the part after the last dot in the url.
 *
 * @property setFileNameExtractor Set if the name of the file is not between the last slash and the first dot after the last slash.
 *
 * @property setFileFormat Set the file format if it is certain. It should be set to null if format extractor is wanted to work.
 *
 * @property notificationBuilder can be edited.
 */
open class Downloader private constructor(
    private val scope: CoroutineScope,
    private val context: Context,
    private var areNotificationsActive: Boolean,
) {
    private var fileFormat: String? = null
    private var fileFormatExtractor: (url: String) -> String = { it.substringAfterLast(".") }
    private var fileNameExtractor: (url: String) -> String =
        { it.substringAfterLast("/").substringBefore(".") }
    private var startForResult: ActivityResultLauncher<Intent>? = null
    private var notificationId: Int = Random(System.nanoTime()).nextInt()
    private var body: ResponseBody? = null
    private var currentFileName = "file"

    val notificationCompat = NotificationManagerCompat.from(context)
    val notificationBuilder =
        NotificationCompat.Builder(context, DOWNLOADER_NOTIF_CHANNEL_ID).apply {
            setContentTitle("Downloader")
            setSmallIcon(R.drawable.download)
            priority = NotificationCompat.PRIORITY_LOW
            setAutoCancel(true)
        }

    init {
        createNotificationChannelIfActive()
    }

    /**
     * @property context is mandatory. If not set, NullPointerException will be thrown.
     *
     * @property scope is mandatory. If not set, NullPointerException will be thrown
     */
    class Builder {
        private var areNotificationsActive = false
        private var scope: CoroutineScope? = null
        private var context: Context? = null

        /**
         * Default is false.
         */
        fun setNotificationsActive(isActive: Boolean): Builder {
            this.areNotificationsActive = isActive
            return this
        }

        fun scope(scope: CoroutineScope): Builder {
            this.scope = scope
            return this
        }

        fun context(context: Context): Builder {
            this.context = context
            return this
        }

        fun build(): Downloader {
            return Downloader(
                scope = scope!!,
                context = context!!,
                areNotificationsActive = areNotificationsActive
            )
        }
    }

    private fun createEmptyFileIntentAndStartLauncher(format: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)

            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(format)

            putExtra(Intent.EXTRA_TITLE, currentFileName)
        }

        startForResult?.launch(intent)
    }

    private fun createNotificationChannelIfActive() {
        if (areNotificationsActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Downloader Notification Channel"
            val descriptionText = "Shows download progress."
            val importance =
                NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(DOWNLOADER_NOTIF_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun downloadUrl(url: String) {
        Log.d(TAG, "$url is going to be downloaded.")

        scope.launch {
            val request = Request.Builder()
                .url(url)
                .build()

            val response = OkHttpClient().newCall(request).execute()

            when {
                response.isSuccessful && response.body != null -> {
                    body = response.body
                    val format = fileFormat ?: fileFormatExtractor.invoke(url)
                    currentFileName = fileNameExtractor.invoke(url)
                    createEmptyFileIntentAndStartLauncher(format)
                }

                response.isSuccessful && response.body == null -> {
                    Log.e(TAG, "Response body is null")
                }

                else -> {
                    Log.e(TAG, "Response is not successful.")
                }
            }
        }
    }

    fun initActivityResultLauncher(activityResultLauncher: Downloader.() -> ActivityResultLauncher<Intent>?) {
        startForResult = activityResultLauncher.invoke(this)
    }

    @SuppressLint("MissingPermission")
    private fun notifyIfPermissionIsGrantedAndNotificationsActive(
        id: Int,
        notification: Notification
    ) {
        if (areNotificationsActive && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationCompat.notify(id, notification)
        }
    }

    open fun onDownloadComplete() {
        notificationBuilder.setContentText("Downloaded File: $currentFileName")
            .setProgress(0, 0, false)

        notifyIfPermissionIsGrantedAndNotificationsActive(
            notificationId,
            notificationBuilder.build()
        )
    }

    open fun onDownloadStart() {
        notificationBuilder.setProgress(PROGRESS_MAX, 0, false)
        notificationBuilder.setContentText("Downloading $currentFileName")
        notifyIfPermissionIsGrantedAndNotificationsActive(
            notificationId,
            notificationBuilder.build()
        )
    }

    fun setFileFormat(type: String?) {
        fileFormat = type
    }

    /**
     * Default is url.substringAfterLast(".")
     */
    fun setFileFormatExtractor(extractor: (url: String) -> String) {
        fileFormatExtractor = extractor
    }

    /**
     * Default is url.substringAfterLast("/").substringBeforeLast(".")
     */
    fun setFileNameExtractor(extractor: (url: String) -> String) {
        fileNameExtractor = extractor
    }

    private fun setNotificationBuilderIntent(uri: Uri) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            uri
        ).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        notificationBuilder.setContentIntent(pendingIntent)
    }

    fun writeToFile(uri: Uri) {
        setNotificationBuilderIntent(uri)

        scope.launch {
            var outputStream: OutputStream? = null

            try {
                notificationId = Random(System.nanoTime()).nextInt()
                onDownloadStart()

                context.contentResolver.openFileDescriptor(uri, "wt")
                    ?.use { parcelFileDescriptor ->
                        outputStream = FileOutputStream(parcelFileDescriptor.fileDescriptor)

                        val buff = ByteArray(1024)
                        var read: Int
                        var bytesCopied: Long = 0
                        val stream = body!!.byteStream()
                        val contentLength = body!!.contentLength()
                        var previousTimeMillis = System.currentTimeMillis()

                        while (stream.read(buff, 0, buff.size).also { read = it } > -1) {
                            outputStream!!.write(buff, 0, read)
                            bytesCopied += read
                            val progressCurrent =
                                ((bytesCopied * PROGRESS_MAX) / contentLength).toInt()

                            if ((System.currentTimeMillis() - previousTimeMillis) > 1000) {
                                notificationBuilder.setProgress(
                                    PROGRESS_MAX,
                                    progressCurrent,
                                    false
                                )
                                notifyIfPermissionIsGrantedAndNotificationsActive(
                                    notificationId,
                                    notificationBuilder.build()
                                )
                                previousTimeMillis = System.currentTimeMillis()
                            }
                        }
                    }

                onDownloadComplete()
            } catch (error: Throwable) {
                Log.e(TAG, error.stackTraceToString())
            } finally {
                body?.close()
                outputStream?.close()
            }
        }
    }
}