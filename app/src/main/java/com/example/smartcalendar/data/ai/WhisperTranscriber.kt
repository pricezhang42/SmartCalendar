package com.example.smartcalendar.data.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.smartcalendar.BuildConfig
import com.example.smartcalendar.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records audio and transcribes via the SmartCalendar backend's /speech/transcribe endpoint.
 * Records raw PCM, converts to WAV, and uploads. The backend proxies to OpenAI Whisper.
 *
 * Lifecycle: create once per Fragment, call release() in onDestroyView.
 */
class WhisperTranscriber(
    private val context: Context,
    private val baseUrl: String = BuildConfig.BACKEND_URL
) {

    companion object {
        private const val TAG = "WhisperTranscriber"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    sealed class State {
        object Idle : State()
        object Recording : State()
        object Transcribing : State()
        data class Result(val text: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val audioBytes = ByteArrayOutputStream()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    fun isRecording(): Boolean = _state.value is State.Recording

    fun resetState() {
        _state.value = State.Idle
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = State.Error("Microphone permission not granted")
            return
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufferSize, SAMPLE_RATE * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _state.value = State.Error("Failed to initialize audio recorder")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioBytes.reset()
            audioRecord?.startRecording()
            _state.value = State.Recording

            recordingJob = scope.launch {
                val buffer = ByteArray(2048)
                while (isActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        synchronized(audioBytes) {
                            audioBytes.write(buffer, 0, read)
                        }
                    }
                }
            }

            Log.d(TAG, "Recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _state.value = State.Error("Failed to start recording: ${e.message}")
            audioRecord?.release()
            audioRecord = null
        }
    }

    suspend fun stopAndTranscribe(): String? {
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord?.release()
        audioRecord = null

        val pcmData: ByteArray
        synchronized(audioBytes) {
            pcmData = audioBytes.toByteArray()
            audioBytes.reset()
        }

        if (pcmData.isEmpty()) {
            _state.value = State.Error("No audio recorded")
            return null
        }

        val durationSecs = pcmData.size / (SAMPLE_RATE * 2)
        Log.d(TAG, "Recorded ${pcmData.size} bytes (~${durationSecs}s)")

        if (baseUrl.isEmpty()) {
            _state.value = State.Error("Backend URL not configured")
            return null
        }

        val token = runCatching { SupabaseClient.auth.currentSessionOrNull()?.accessToken }.getOrNull()
        if (token.isNullOrBlank()) {
            _state.value = State.Error("Not signed in")
            return null
        }

        _state.value = State.Transcribing

        return try {
            val wavData = pcmToWav(pcmData, SAMPLE_RATE, 1, 16)

            val response = withContext(Dispatchers.IO) {
                httpClient.submitFormWithBinaryData(
                    url = "$baseUrl/speech/transcribe",
                    formData = formData {
                        append("File", wavData, Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                            append(HttpHeaders.ContentType, "audio/wav")
                        })
                    }
                ) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }

            val body = response.bodyAsText()
            Log.d(TAG, "Backend transcribe response: ${response.status} $body")

            if (!response.status.isSuccess()) {
                val msg = when (response.status.value) {
                    401 -> "Not authorized. Please sign in again."
                    429 -> "Daily quota reached. Try again tomorrow."
                    else -> "Backend transcribe error: ${response.status}"
                }
                _state.value = State.Error(msg)
                return null
            }

            val result = json.decodeFromString<WhisperResponse>(body)
            val text = result.text.trim()

            if (text.isBlank()) {
                _state.value = State.Error("No speech detected")
                null
            } else {
                Log.d(TAG, "Transcription: $text")
                _state.value = State.Result(text)
                text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            _state.value = State.Error("Transcription failed: ${e.message}")
            null
        }
    }

    /**
     * Convert raw PCM bytes to WAV format by prepending a 44-byte WAV header.
     */
    private fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            // RIFF header
            put("RIFF".toByteArray())
            putInt(totalSize)
            put("WAVE".toByteArray())
            // fmt chunk
            put("fmt ".toByteArray())
            putInt(16) // chunk size
            putShort(1) // PCM format
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            // data chunk
            put("data".toByteArray())
            putInt(dataSize)
        }

        return header.array() + pcmData
    }

    fun cancelRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null
        audioBytes.reset()
        _state.value = State.Idle
    }

    fun release() {
        cancelRecording()
        scope.cancel()
        httpClient.close()
    }

    @Serializable
    private data class WhisperResponse(val text: String = "")
}
