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

package androidx.camera.integration.core

import android.content.Context
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraX
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.camera.core.internal.CameraUseCaseAdapter
import androidx.camera.testing.CameraUtil
import androidx.camera.testing.LabTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertWithMessage
import com.google.mlkit.vision.barcode.Barcode.FORMAT_QR_CODE
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/*  The integration-test is for MLKit vision barcode component with CameraX ImageAnalysis use case.
    The test is for lab device test. For the local test, mark the @LabTestRule.LabTestFrontCamera,
    and @LabTestRule.LabTestRearCamera, or enable "setprop log.tag.rearCameraE2E DEBUG" or
    "setprop log.tag.frontCameraE2E DEBUG" using 'adb shell'.
*/
@LargeTest
@RunWith(Parameterized::class)
class MLKitBarcodeTest(
    private val resolution: Size
) {

    @get:Rule
    val cameraRule = CameraUtil.grantCameraPermissionAndPreTest()

    @get:Rule
    val labTest: LabTestRule = LabTestRule()

    companion object {
        private const val DETECT_TIMEOUT = 5_000L
        private const val TAG = "MLKitVisionTest"
        private val size480p = Size(640, 480)
        private val size720p = Size(1280, 720)
        @JvmStatic
        @Parameterized.Parameters
        fun data() = listOf(size480p, size720p)
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var camera: CameraUseCaseAdapter
    // For MK Kit Barcode scanner
    private lateinit var barcodeScanner: BarcodeScanner
    private var imageResolution: Size = resolution
    private var imageRotation: Int = Surface.ROTATION_0
    private var targetRotation: Int = Surface.ROTATION_0

    @Before
    fun setup() {
        CameraX.initialize(context, Camera2Config.defaultConfig()).get(10, TimeUnit.SECONDS)

        barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(FORMAT_QR_CODE).build()
        )
    }

    @After
    fun tearDown(): Unit = runBlocking {
        if (::camera.isInitialized) {
            // TODO: The removeUseCases() call might be removed after clarifying the
            // abortCaptures() issue in b/162314023
            withContext(Dispatchers.Main) {
                camera.removeUseCases(camera.useCases)
            }
        }
        CameraX.shutdown().get(10, TimeUnit.SECONDS)

        if (::barcodeScanner.isInitialized) {
            barcodeScanner.close()
        }
    }

    @LabTestRule.LabTestFrontCamera
    @Test
    fun barcodeDetectViaFontCamera() {
        val imageAnalysis = initImageAnalysis(CameraSelector.LENS_FACING_FRONT)

        camera = CameraUtil.createCameraAndAttachUseCase(
            context,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            imageAnalysis
        )
        assertBarcodeDetect(imageAnalysis)
    }

    @LabTestRule.LabTestRearCamera
    @Test
    fun barcodeDetectViaRearCamera() {
        val imageAnalysis = initImageAnalysis(CameraSelector.LENS_FACING_BACK)

        camera = CameraUtil.createCameraAndAttachUseCase(
            context,
            CameraSelector.DEFAULT_BACK_CAMERA,
            imageAnalysis
        )
        assertBarcodeDetect(imageAnalysis)
    }

    private fun assertBarcodeDetect(imageAnalysis: ImageAnalysis) {
        val latchForBarcodeDetect = CountDownLatch(2)

        imageAnalysis.setAnalyzer(
            CameraXExecutors.ioExecutor()
        ) { imageProxy ->
            imageResolution = Size(imageProxy.image!!.width, imageProxy.image!!.height)
            imageRotation = imageProxy.imageInfo.rotationDegrees
            barcodeScanner.process(
                InputImage.fromMediaImage(
                    imageProxy.image!!,
                    imageProxy.imageInfo.rotationDegrees
                )
            )
                .addOnSuccessListener { barcodes ->
                    barcodes.forEach {
                        if ("Hi, CamX!" == it.displayValue) {
                            latchForBarcodeDetect.countDown()
                        }
                        Log.d(TAG, "barcode display value: {${it.displayValue}} ")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "processImage onFailure: $exception")
                }
                // When the image is from CameraX analysis use case, must call image.close() on
                // received images when finished using them. Otherwise, new images may not be
                // received or the camera may stall.
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }

        // Verify it is the CameraX lab test environment and can detect qr-code.
        assertWithMessage(
            "Fail to detect qrcode, target resolution: $resolution, " +
                "image resolution: $imageResolution, " +
                "target rotation: $targetRotation, " +
                "image rotation: $imageRotation "
        ).that(latchForBarcodeDetect.await(DETECT_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue()
    }

    private fun initImageAnalysis(lensFacing: Int): ImageAnalysis {
        val sensorOrientation = CameraUtil.getSensorOrientation(lensFacing)
        val isRotateNeeded = sensorOrientation!! % 180 != 0
        Log.d(TAG, "Sensor Orientation: $sensorOrientation, lensFacing: $lensFacing")
        targetRotation = if (isRotateNeeded) Surface.ROTATION_90 else Surface.ROTATION_0

        return ImageAnalysis.Builder()
            .setTargetName("ImageAnalysis")
            .setTargetResolution(resolution)
            .setTargetRotation(targetRotation)
            .build()
    }
}
