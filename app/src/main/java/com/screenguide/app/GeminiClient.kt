package com.screenguide.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {

    data class Turn(val role: String, val text: String)

    private const val SYSTEM_PROMPT =
        "Tum ek screen guide assistant ho. User ki screen ka screenshot tumhe diya jata hai. " +
        "Screenshot dekhkar user ke sawal ka jawab Hinglish me do aur step-by-step guide karo " +
        "(video editing, coding, apps, settings, kuch bhi). Batao ki kaunsa button ya option " +
        "screen par kahan hai aur kya dabana hai. Jawab chhota, clear aur practical rakho. " +
        "Agar code chahiye to pura working code do."

    fun ask(
        apiKey: String,
        model: String,
        history: List<Turn>,
        prompt: String,
        imageB64: String?
    ): String {
        val contents = JSONArray()
        for (t in history) {
            contents.put(
                JSONObject()
                    .put("role", t.role)
                    .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
            )
        }
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        if (imageB64 != null) {
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", "image/jpeg").put("data", imageB64)
                )
            )
        }
        contents.put(JSONObject().put("role", "user").put("parts", parts))

        val body = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
            )
            .put("contents", contents)

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                val msg = try {
                    JSONObject(text).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    text
                }
                throw RuntimeException("Gemini error $code: $msg")
            }

            val cands = JSONObject(text).optJSONArray("candidates")
            val sb = StringBuilder()
            if (cands != null && cands.length() > 0) {
                val ps = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                if (ps != null) {
                    for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text"))
                }
            }
            return if (sb.isEmpty()) "Gemini ne koi jawab nahi diya (shayad blocked)." else sb.toString().trim()
        } finally {
            conn.disconnect()
        }
    }
}
