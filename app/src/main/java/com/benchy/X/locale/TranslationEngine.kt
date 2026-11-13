package com.benchy.X.locale

import android.content.Context
import com.benchy.X.data.BenchyDatabase
import com.benchy.X.data.LanguageMode
import com.benchy.X.data.TranslationCacheEntity
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class TranslationModelState {
    data object Native : TranslationModelState()
    data class Downloading(val languageName: String) : TranslationModelState()
    data class Ready(val mlKitLanguageCode: String, val languageName: String) : TranslationModelState()
    data class ErrorFallback(val languageName: String) : TranslationModelState()
}

class TranslationEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val cacheDao = BenchyDatabase.getInstance(appContext).translationCacheDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val memoryCache = ConcurrentHashMap<String, String>()
    private var activeTranslator: Translator? = null
    private var activeTargetCode: String? = null

    private val _state = MutableStateFlow<TranslationModelState>(TranslationModelState.Native)
    val state: StateFlow<TranslationModelState> = _state.asStateFlow()

    fun syncForLanguageMode(mode: LanguageMode) {
        if (!LocaleManagerHelper.requiresMlKitTranslation(mode)) {
            _state.value = TranslationModelState.Native
            return
        }

        val sysLocale = LocaleManagerHelper.getSystemLocale()
        val displayName = LocaleManagerHelper.getDetectedSystemLanguageName()
        val mlKitTarget = TranslateLanguage.fromLanguageTag(sysLocale.toLanguageTag())
            ?: TranslateLanguage.fromLanguageTag(sysLocale.language)

        if (mlKitTarget == null || mlKitTarget == TranslateLanguage.ENGLISH) {
            _state.value = TranslationModelState.Native
            return
        }

        scope.launch {
            prepareTargetModel(mlKitTarget, displayName)
        }
    }

    private suspend fun prepareTargetModel(mlKitTarget: String, displayName: String) {
        mutex.withLock {
            // Load persisted translations for this language into memory cache immediately
            val persisted = runCatching { cacheDao.getAllForLanguage(mlKitTarget) }.getOrDefault(emptyList())
            persisted.forEach { entry ->
                memoryCache["$mlKitTarget::${entry.sourceText}"] = entry.translatedText
            }

            if (activeTargetCode == mlKitTarget && _state.value is TranslationModelState.Ready) {
                return
            }

            activeTranslator?.close()
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(mlKitTarget)
                .build()
            val translator = Translation.getClient(options)
            activeTranslator = translator
            activeTargetCode = mlKitTarget

            val model = TranslateRemoteModel.Builder(mlKitTarget).build()
            val isDownloaded = runCatching {
                RemoteModelManager.getInstance().isModelDownloaded(model).awaitTask()
            }.getOrDefault(false)

            if (!isDownloaded) {
                _state.value = TranslationModelState.Downloading(displayName)
            }

            val downloadResult = runCatching {
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitTask()
            }

            _state.value = if (downloadResult.isSuccess) {
                TranslationModelState.Ready(mlKitTarget, displayName)
            } else {
                TranslationModelState.ErrorFallback(displayName)
            }
        }
    }

    fun getCachedOrNull(targetCode: String, englishText: String): String? {
        return memoryCache["$targetCode::$englishText"]
    }

    suspend fun translateOrFallback(englishText: String): String {
        if (englishText.isBlank()) return englishText
        val currentState = _state.value
        if (currentState !is TranslationModelState.Ready) {
            return englishText
        }
        val targetCode = currentState.mlKitLanguageCode
        val cacheKey = "$targetCode::$englishText"
        memoryCache[cacheKey]?.let { return it }

        val roomCached = runCatching { cacheDao.getTranslation(targetCode, englishText) }.getOrNull()
        if (roomCached != null) {
            memoryCache[cacheKey] = roomCached.translatedText
            return roomCached.translatedText
        }

        val translator = activeTranslator ?: return englishText
        return runCatching {
            val translated = translator.translate(englishText).awaitTask()
            if (translated.isNotBlank()) {
                memoryCache[cacheKey] = translated
                cacheDao.upsert(
                    TranslationCacheEntity(
                        targetLang = targetCode,
                        sourceText = englishText,
                        translatedText = translated,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
                translated
            } else {
                englishText
            }
        }.getOrDefault(englishText)
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TranslationEngine? = null

        fun getInstance(context: Context): TranslationEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TranslationEngine(context).also { INSTANCE = it }
            }
        }
    }
}
