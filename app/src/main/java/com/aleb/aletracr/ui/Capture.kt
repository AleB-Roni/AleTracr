package com.aleb.aletracr.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aleb.aletracr.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Speech-to-text via Android's built-in speech recognizer (Google), German.
 * Returns a function that opens the "Sprich jetzt" dialog; the recognised text goes to [onText].
 */
@Composable
fun rememberSpeechInput(
    prompt: String = "Was hast du gegessen?",
    onError: (String) -> Unit = {},
    onText: (String) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) onText(text)
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            onError("Keine Spracherkennung auf diesem Gerät gefunden (Google-App installieren).")
        }
    }
}

/**
 * Takes a photo with the camera app, then loads it downscaled as JPEG bytes.
 * Returns a function that opens the camera.
 */
@Composable
fun rememberPhotoCapture(onPhoto: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pending
        if (ok && uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { ImageUtils.loadScaledJpeg(context, uri) }
                if (bytes != null) onPhoto(bytes)
            }
        }
    }
    return {
        val uri = ImageUtils.newPhotoUri(context)
        pending = uri
        camera.launch(uri)
    }
}
