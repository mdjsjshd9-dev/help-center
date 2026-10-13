package com.screenguide.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val reqCapture = 1001
    private val reqPerms = 1002
    private var permsAsked = false

    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var sizeBar: SeekBar
    private lateinit var alphaBar: SeekBar
    private lateinit var textBar: SeekBar
    private lateinit var colorSpin: Spinner
    private lateinit var themeSpin: Spinner
    private lateinit var langSpin: Spinner
    private lateinit var autoSendBox: CheckBox

    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val pad: Int get() = (16 * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cfg = Prefs.load(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Screen Guide"
            textSize = 26f
        })

        addLabel(root, "Gemini API key (aistudio.google.com se free milti hai):")
        keyInput = EditText(this).apply {
            hint = "AIza..."
            setText(cfg.apiKey)
            setSingleLine(true)
        }
        root.addView(keyInput, LinearLayout.LayoutParams(match, wrap))

        addLabel(root, "Model name:")
        modelInput = EditText(this).apply {
            setText(cfg.model)
            setSingleLine(true)
        }
        root.addView(modelInput, LinearLayout.LayoutParams(match, wrap))

        root.addView(TextView(this).apply {
            text = "\nCustomize"
            textSize = 20f
        })

        sizeBar = addSeek(root, "Bubble size", "dp", 40, 100, cfg.bubbleDp)
        alphaBar = addSeek(root, "Bubble opacity", "%", 30, 100, cfg.alpha)
        textBar = addSeek(root, "Jawab ka text size", "sp", 12, 22, cfg.textSp)

        addLabel(root, "Bubble color:")
        colorSpin = addSpinner(root, Prefs.COLORS.map { it.first }, cfg.colorIdx)

        addLabel(root, "Panel theme:")
        themeSpin = addSpinner(root, listOf("Dark", "Light"), if (cfg.dark) 0 else 1)

        addLabel(root, "Voice language:")
        langSpin = addSpinner(root, Prefs.LANGS.map { it.first }, cfg.langIdx)

        autoSendBox = CheckBox(this).apply {
            text = "Voice se bolne ke baad automatically bhej do"
            isChecked = cfg.autoSend
        }
        root.addView(autoSendBox, LinearLayout.LayoutParams(match, wrap))

        root.addView(Button(this).apply {
            text = "Start"
            setOnClickListener { onStartClicked() }
        })
        root.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener { stopService(Intent(this@MainActivity, ScreenService::class.java)) }
        })

        root.addView(TextView(this).apply {
            setPadding(0, pad, 0, 0)
            text = "Kaise use kare:\n" +
                "1. Start dabao, permissions do (overlay, microphone, notification).\n" +
                "2. Screen share dialog me 'Entire screen' / 'Poori screen' chuno.\n" +
                "3. Screen par 'AI' bubble aayega. Bubble dabao, sawal likho ya 🎤 dabakar bolo.\n" +
                "4. Settings badalni ho to Stop karo, badlo, phir Start dabao."
        })

        setContentView(ScrollView(this).also { it.addView(root) })
    }

    private fun addLabel(root: LinearLayout, t: String) {
        root.addView(TextView(this).apply {
            text = t
            setPadding(0, pad / 2, 0, 0)
        })
    }

    private fun addSpinner(root: LinearLayout, items: List<String>, sel: Int): Spinner {
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        sp.setSelection(sel.coerceIn(0, items.lastIndex))
        root.addView(sp, LinearLayout.LayoutParams(match, wrap))
        return sp
    }

    private fun addSeek(
        root: LinearLayout, title: String, unit: String, lo: Int, hi: Int, init: Int
    ): SeekBar {
        val start = init.coerceIn(lo, hi)
        val tv = TextView(this)
        tv.text = "$title: $start $unit"
        tv.setPadding(0, pad / 2, 0, 0)
        val sb = SeekBar(this)
        sb.max = hi - lo
        sb.progress = start - lo
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tv.text = "$title: ${p + lo} $unit"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(tv)
        root.addView(sb, LinearLayout.LayoutParams(match, wrap))
        return sb
    }

    private fun collectConfig() = Config(
        apiKey = keyInput.text.toString().trim(),
        model = modelInput.text.toString().trim().ifEmpty { "gemini-2.5-flash" },
        bubbleDp = sizeBar.progress + 40,
        colorIdx = colorSpin.selectedItemPosition,
        alpha = alphaBar.progress + 30,
        dark = themeSpin.selectedItemPosition == 0,
        textSp = textBar.progress + 12,
        langIdx = langSpin.selectedItemPosition,
        autoSend = autoSendBox.isChecked
    )

    private fun onStartClicked() {
        val cfg = collectConfig()
        if (cfg.apiKey.isEmpty()) {
            Toast.makeText(this, "Pehle API key daalo", Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.save(this, cfg)

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "'Display over other apps' ON karo, phir wapas aakar Start dabao",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }

        val need = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isNotEmpty() && !permsAsked) {
            permsAsked = true
            requestPermissions(need.toTypedArray(), reqPerms)
            return
        }
        launchCapture()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == reqPerms) launchCapture()
    }

    private fun launchCapture() {
        // purani service (agar chal rahi ho) band karo taaki nayi settings lagein
        stopService(Intent(this, ScreenService::class.java))
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), reqCapture)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != reqCapture) return
        if (resultCode == RESULT_OK && data != null) {
            val i = Intent(this, ScreenService::class.java)
                .putExtra("code", resultCode)
                .putExtra("data", data)
            startForegroundService(i)
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "Screen capture permission nahi mili", Toast.LENGTH_SHORT).show()
        }
    }
}
