package com.example.bglib

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import com.example.bglib.imgproc.CardDetection.Companion.detectRectOtsu
import com.example.bglib.imgproc.CardDetection.Companion.getBoundingBoxes
import com.example.bglib.imgproc.TextDetection.Companion.detectTextMLKit
import com.example.bglib.imgproc.TextDetection.Companion.getRotationCompensation
import com.example.bglib.imgproc.Utils.Companion.getLargestRect
import com.example.bglib.classes.convertMLKitTextToParcelable
import com.example.bglib.demos.CardBaseActivity
import com.example.bglib.demos.DetectedTextBlockActivity
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

class TestActivity : CardBaseActivity()  {

    val context = this
    val TAG = "Test::Activity"

    protected lateinit var button: Button
    protected lateinit var lastText: Text
    protected lateinit var lastFrame: Mat

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        button = findViewById<Button>(R.id.button)
        button.setOnClickListener {
            // saveFrame(lastFrame, this)
            Log.d(TAG, lastText.text)

            val intent = Intent(context, DetectedTextBlockActivity::class.java)
            try{
                intent.putExtra("cardText", convertMLKitTextToParcelable(lastText))
            }
            catch (e: Exception){
                Log.e(TAG, "Error putting cards in intent", e)
            }
            context.startActivity(intent)

        }

    }


    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val frame = inputFrame.rgba()

        val rects = detectRectOtsu(frame, drawBoundingBoxes = true)
        val bbs = getBoundingBoxes(frame, rects)
        val cardRect = getLargestRect(bbs)

        var cardText = Text("No text detected", listOf(1))
        if (cardRect != null) {
            val rotation = getRotationCompensation(CAMERA_ID, this, false)
            CoroutineScope(Dispatchers.Default).launch {
                val subframe = Mat(frame, cardRect)
                val deferredText = async {
                    val textDeferred = CompletableDeferred<Text>()
                    detectTextMLKit(subframe, rotation, TextRecognition.getClient(
                        TextRecognizerOptions.DEFAULT_OPTIONS)) { detectedText ->
                        textDeferred.complete(detectedText) // Complete the CompletableDeferred with the detected text
                    }
                    textDeferred.await() // Wait for the CompletableDeferred to be completed
                }
                cardText = deferredText.await()
                lastText = cardText
            }
        }



        lastFrame = frame
        return frame
    }
}