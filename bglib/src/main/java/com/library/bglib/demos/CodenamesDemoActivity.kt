package com.library.bglib.demos

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Button
import com.library.bglib.R
import com.library.bglib.imgproc.ModelInterpreter
import com.library.bglib.imgproc.cards2grid
import com.library.bglib.imgproc.detectRectCanny
import com.library.bglib.imgproc.detectRectOtsu
import com.library.bglib.imgproc.detectTextMLKit
import com.library.bglib.imgproc.getBoundingBoxes
import com.library.bglib.imgproc.getRotationCompensation
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Rect

/************************************************
 * Demo activity for binary classification      *
 * of cards.                                    *
 * Showcases the use of model interpreter,      *
 * as well as rectangle detection,              *
 * ocr, card parcelable and grid alignment.     *
 ***********************************************/
class CodenamesDemoActivity : CardBaseActivity() {
    val context = this
    val TAG = "Example::Activity"

    // Get parameters from HomeActivity
    private val cardDetectMethod: String by lazy { intent.extras?.getString("cardDetectMethod", "") ?: "" }
    private val rows: Int by lazy { intent.extras?.getInt("rows", 1) ?: 1 }
    private val cols: Int by lazy { intent.extras?.getInt("cols", 1) ?: 1 }
    private var numberOfCards: Int = 0

    private var mediaPlayer: MediaPlayer? = null

    private lateinit var modelInterpreter : ModelInterpreter

    lateinit var button: Button

    private var latestRects: List<Rect>? = null
    private var latestFrame: Mat? = null
    private var activityStarted = false

    private fun logRectsInfo(rects: List<Rect>, frame: Mat) {
        Log.d(TAG, "Number of rectangles: ${rects.size}")
        Log.d(TAG, "Screen Dimensions: ${frame.size().width}x${frame.size().height}")
        for (rect in rects) {
            Log.d(TAG, "x: ${rect.x}, y: ${rect.y}, width: ${rect.width}, height: ${rect.height}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        numberOfCards = rows * cols

        button = findViewById<Button>(R.id.button)
        button.setOnClickListener {
            logRectsInfo(latestRects ?: emptyList(), latestFrame ?: Mat())
        }

        try {
            modelInterpreter = ModelInterpreter(this, "binary_classifier_model.tflite")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model interpreter", e)
        }

        // Init media player for sound effects
        mediaPlayer = MediaPlayer.create(this, R.raw.r2d2_beep)
        mediaPlayer?.setOnPreparedListener { /* TODO */ }

    }



    // Detect number of cards
    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val frame = inputFrame.rgba()
        var rectangles = mutableListOf<MatOfPoint2f>()
        rectangles = when (cardDetectMethod) {
            "Otsu" -> detectRectOtsu(frame)
            "Canny" -> detectRectCanny(frame)
            else -> mutableListOf<MatOfPoint2f>()
        }
        val boundingBoxes = getBoundingBoxes(frame, rectangles)

        if (boundingBoxes.size == numberOfCards && !activityStarted) {
            // Check if rects are cards
            try {
                boundingBoxes.forEachIndexed{
                        index, rect ->
                    val subframe = Mat(frame, rect)
                    val inputData = modelInterpreter.preprocessMat(subframe)
                    val output = modelInterpreter.predict(inputData)
                    val isCard = output[0] <= 0.5 // Class 0 is card, class 1 is not a card
                    Log.d(TAG, "For rect $index is card: $isCard")

                    if (!isCard) return frame
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during rectangle classification", e)
            }


            Log.d(TAG, "All cards detected $numberOfCards")
            activityStarted = true

            // Play sound indicating that all cards are detected
            mediaPlayer?.start()

            // Align cards into the grid
            val grid = ArrayList(cards2grid(boundingBoxes, rows, cols))
            Log.d(TAG, "Grid: $grid")

            // Once all the cards are detected, get rotation and start the OCR
            val rotation = getRotationCompensation(CAMERA_ID, this, false)
            CoroutineScope(Dispatchers.Default).launch {
                grid.forEachIndexed { index, card ->
                    val subframe = Mat(frame, card.boundingBox)
                    val deferredText = async {
                        val textDeferred = CompletableDeferred<String>()
                        detectTextMLKit(
                            subframe,
                            rotation,
                            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        ) { detectedText ->
                            Log.d(TAG, "For rect $index the text is $detectedText")
                            textDeferred.complete(detectedText.text) // Complete the CompletableDeferred with the detected text
                        }
                        textDeferred.await() // Wait for the CompletableDeferred to be completed
                    }
                    card.text = deferredText.await()
                }

                val gridTexts = grid.map { it.text }
                Log.d(TAG, "Texts: $gridTexts")

                for (card in grid){
                    Log.d(TAG, "Card: ${card.text} pos: ${card.GridPosition}")
                }

                withContext(Dispatchers.Main) {
                    val intent = Intent(context, DetectedCardsActivity::class.java)
                    intent.putExtra("numberOfCards", numberOfCards)
                    intent.putExtra("cols", cols)
                    intent.putExtra("rows", rows)
                    try {
                        intent.putParcelableArrayListExtra("cards", grid)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error putting cards in intent", e)
                    }
                    context.startActivity(intent)
                }
            }



        }

        latestRects = boundingBoxes
        latestFrame = frame

        return frame
    }
}