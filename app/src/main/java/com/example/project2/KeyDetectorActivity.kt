package com.example.project2

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.project2.CardDetection.Companion.detectRectOtsu
import com.example.project2.Utils.Companion.checkCamPermission
import com.example.project2.Utils.Companion.mat2bitmap
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream

class KeyDetectorActivity: CardBaseActivity() {

    private var button: Button? = null
    private var lastKey: Mat? = null

    fun getCardKey(frame:Mat) : Mat? {
        val rects = detectRectOtsu(frame, drawBoundingBoxes = true)

        // Whole card and the key
        if (rects.size == 2){
            // The smaller rect is the key
            val key = rects.minBy { it.area() }
            val subframe = frame.submat(key)

            return subframe
        }
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_key_detector)

        button = findViewById<Button>(R.id.process_button)
        button?.setOnClickListener {
            // Pass the frame to the next activity
            try {
                // Conver last detected key to bitmap
                val stream = ByteArrayOutputStream()
                val frameBitmap = mat2bitmap(lastKey!!)
                frameBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) // or JPEG, WebP
                val byteArray = stream.toByteArray()

                // Pass as intent (byte array) to the next activity
                val intent = Intent(this, DetectedKeyActivity::class.java)
                intent.putExtra("frame", byteArray)
                startActivity(intent)
            }
            catch (e: Exception){
                Log.e(TAG, "Error passing frame to next activity", e)
            }

        }

        // init camera
        initCamera()

        if (checkCamPermission(this)) {
            Log.d(TAG, "Permissions granted")
            mOpenCvCameraView.setCameraPermissionGranted()

            // Init Opencv
            if (OpenCVLoader.initLocal()) {
                Log.i(TAG, "OpenCV loaded successfully")
            } else {
                Log.e(TAG, "OpenCV initialization failed!")
                Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show()
                return
            }

        } else {
            // request permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val frame = inputFrame.rgba()
        val key = getCardKey(frame)
        if (key != null){
            lastKey = key
        }

        // TODO: Process a frame and return it probably in a coroutine
        // TODO: Pass the frame to the next activity
        // TODO: Display an image in the activity
        // TODO: Study and write functions for image processing, such as filtering and enhancements, etc.

        return frame
    }
}