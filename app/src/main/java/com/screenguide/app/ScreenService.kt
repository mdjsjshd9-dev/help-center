package com.screenguide.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs

class ScreenService : Service() {

    companion object {
        const val ACTION_STOP = "com.screenguide.app.STOP"
        private const val CHANNEL_ID = "screenguide"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var wm: WindowManager
    private lateinit var cfg: Config

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var heldImage: Image? = null

    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var answerView: TextView? = null
    private var inputView: EditText? = null
    private var micBtn: Button? = null
    private var panelShown = false
    private var busy = false
    private var destroyed = false

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    private val history = mutableListOf<GeminiClient.Turn>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection != null) return START_NOT_STICKY

        cfg = Prefs.load(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()

        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = readData(intent)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                handler.post { if (!destroyed) stopSelf() }
            }
        }, handler)

        setupCapture()
        createBubble()
        buildPanel()
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun readData(i: Intent?): Intent? =
        if (Build.VERSION.SDK_INT >= 33) i?.getParcelableExtra("data", Intent::class.java)
        else i?.getParcelableExtra("data")

    @Suppress("DEPRECATION")
    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Screen Guide", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, ScreenService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Guide chal raha hai")
            .setContentText("'AI' bubble dabao aur sawal pucho")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .build()

        if (Build.VERSION.SDK_INT >= 30) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(1, n, type)
        } else if (Build.VERSION.SDK_INT == 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    // ---------- Screen capture ----------

    private fun setupCapture() {
        val dm = resources.displayMetrics
        val scale = 0.7f
        val w = (dm.widthPixels * scale).toInt()
        val h = (dm.heightPixels * scale).toInt()

        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        reader.setOnImageAvailableListener({ r ->
            val img = try { r.acquireLatestImage() } catch (e: Exception) { null }
            if (img != null) {
                heldImage?.close()
                heldImage = img
            }
        }, handler)
        imageReader = reader

        virtualDisplay = projection!!.createVirtualDisplay(
            "ScreenGuide", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )
    }

    private fun grabBitmap(): Bitmap? {
        val img = heldImage ?: return null
        return try {
            val plane = img.planes[0]
            val buffer = plane.buffer
            buffer.rewind()
            val pixelStride = plane.pixelStride
            val rowPadding = plane.rowStride - pixelStride * img.width
            val full = Bitmap.createBitmap(
                img.width + rowPadding / pixelStride, img.height, Bitmap.Config.ARGB_8888
            )
            full.copyPixelsFromBuffer(buffer)
            Bitmap.createBitmap(full, 0, 0, img.width, img.height)
        } catch (e: Exception) {
            null
        }
    }

    private fun toBase64Jpeg(bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // ---------- Overlay UI ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun overlayType() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun createBubble() {
        val size = dp(cfg.bubbleDp)
        val b = TextView(this).apply {
            text = "AI"
            setTextColor(Color.WHITE)
            textSize = cfg.bubbleDp * 0.28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            alpha = cfg.alpha / 100f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cfg.bubbleColor)
            }
        }
        val lp = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(8)
        lp.y = dp(220)

        b.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) moved = true
                        if (moved) {
                            lp.x = sx + dx
                            lp.y = sy + dy
                            wm.updateViewLayout(b, lp)
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) togglePanel()
                }
                return true
            }
        })
        wm.addView(b, lp)
        bubble = b
    }

    private fun makeBtn(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    private fun buildPanel() {
        val fg = if (cfg.dark) Color.WHITE else 0xFF212121.toInt()
        val hintColor = if (cfg.dark) 0xFF999999.toInt() else 0xFF777777.toInt()
        val bgColor = if (cfg.dark) 0xEE1E1E1E.toInt() else 0xEEFFFFFF.toInt()
        val match = LinearLayout.LayoutParams.MATCH_PARENT
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT

        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(16).toFloat()
            }
        }

        val ans = TextView(this).apply {
            setTextColor(fg)
            textSize = cfg.textSp.toFloat()
            text = "Namaste! Screen par kuch bhi ho, sawal likho ya 🎤 dabakar bolo."
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(ans) }
        p.addView(scroll, LinearLayout.LayoutParams(match, dp(240)))

        val input = EditText(this).apply {
            hint = "Sawal likho..."
            setTextColor(fg)
            setHintTextColor(hintColor)
            maxLines = 4
        }
        val mic = makeBtn("🎤") { toggleMic() }
        val inRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        inRow.addView(input, LinearLayout.LayoutParams(0, wrap, 1f))
        inRow.addView(mic, LinearLayout.LayoutParams(wrap, wrap))
        p.addView(inRow, LinearLayout.LayoutParams(match, wrap))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(makeBtn("Bhejo") { ask() }, LinearLayout.LayoutParams(0, wrap, 1f))
        row.addView(makeBtn("Clear") {
            history.clear()
            ans.text = "Chat clear ho gayi."
        }, LinearLayout.LayoutParams(0, wrap, 1f))
        row.addView(makeBtn("Chhupao") { hidePanel() }, LinearLayout.LayoutParams(0, wrap, 1f))
        row.addView(makeBtn("Band") { stopSelf() }, LinearLayout.LayoutParams(0, wrap, 1f))
        p.addView(row, LinearLayout.LayoutParams(match, wrap))

        val lp = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = dp(8)
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

        panel = p
        panelLp = lp
        answerView = ans
        inputView = input
        micBtn = mic
    }

    private fun showPanel() {
        if (!panelShown && panel != null) {
            wm.addView(panel, panelLp)
            panelShown = true
        }
    }

    private fun hidePanel() {
        if (listening) cancelListening()
        if (panelShown && panel != null) {
            try { wm.removeView(panel) } catch (_: Exception) {}
            panelShown = false
        }
    }

    private fun togglePanel() {
        if (panelShown) hidePanel() else showPanel()
    }

    // ---------- Voice input ----------

    private fun toggleMic() {
        if (listening) {
            recognizer?.stopListening()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            answerView?.text = "Mic permission nahi hai. App kholkar Start dabao aur Microphone allow karo."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            answerView?.text = "Is phone par speech recognition nahi mila. Google app / Speech Services install aur update karo."
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(newListener())
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, cfg.voiceLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        listening = true
        micBtn?.text = "⏹"
        answerView?.text = "Sun raha hu... boliye"
        r.startListening(i)
    }

    private fun finishListening() {
        listening = false
        micBtn?.text = "🎤"
        val r = recognizer
        recognizer = null
        handler.post { try { r?.destroy() } catch (_: Exception) {} }
    }

    private fun cancelListening() {
        try { recognizer?.cancel() } catch (_: Exception) {}
        finishListening()
    }

    private fun newListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrEmpty()) inputView?.setText(t)
        }

        override fun onResults(results: Bundle?) {
            val t = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            finishListening()
            if (t.isEmpty()) {
                answerView?.text = "Kuch sunai nahi diya, dobara 🎤 dabao."
                return
            }
            inputView?.setText(t)
            inputView?.setSelection(t.length)
            if (cfg.autoSend) ask() else answerView?.text = "Sawal: $t\n\n'Bhejo' dabao."
        }

        override fun onError(error: Int) {
            finishListening()
            answerView?.text = when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Mic permission nahi hai. App kholkar Start dabao aur Microphone allow karo."
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "Kuch sunai nahi diya, dobara 🎤 dabao."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Voice ke liye network problem hai. Internet check karo."
                else -> "Voice error ($error). Dobara try karo."
            }
        }
    }

    // ---------- Ask Gemini ----------

    private fun ask() {
        if (busy) return
        val q = inputView?.text?.toString()?.trim().orEmpty()
        if (q.isEmpty()) return

        val c = Prefs.load(this)
        if (c.apiKey.isEmpty()) {
            answerView?.text = "API key nahi mili. App kholkar key daalo."
            return
        }

        busy = true
        inputView?.setText("")

        // Panel aur bubble chhupao taaki screenshot me na aaye
        hidePanel()
        bubble?.visibility = View.INVISIBLE

        handler.postDelayed({
            val bmp = grabBitmap()
            bubble?.visibility = View.VISIBLE
            answerView?.text = "Tum: $q\n\nGemini soch raha hai..."
            showPanel()

            worker.execute {
                try {
                    val b64 = bmp?.let { toBase64Jpeg(it) }
                    val reply = GeminiClient.ask(c.apiKey, c.model, history.toList(), q, b64)
                    handler.post {
                        history.add(GeminiClient.Turn("user", q))
                        history.add(GeminiClient.Turn("model", reply))
                        while (history.size > 12) history.removeAt(0)
                        answerView?.text = "Tum: $q\n\n$reply"
                        busy = false
                    }
                } catch (e: Exception) {
                    handler.post {
                        answerView?.text = "Error: ${e.message}"
                        busy = false
                    }
                }
            }
        }, 450)
    }

    override fun onDestroy() {
        destroyed = true
        hidePanel()
        try { recognizer?.destroy() } catch (_: Exception) {}
        try { bubble?.let { wm.removeView(it) } } catch (_: Exception) {}
        heldImage?.close()
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        worker.shutdownNow()
        super.onDestroy()
    }
}
