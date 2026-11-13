package com.benchy.X.engine

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

enum class FileThreatCategory {
    ZIP_BOMB,
    MALICIOUS_PAYLOAD,
    SPOOFED_EXTENSION,
    EICAR_TEST,
    SUSPICIOUS_APK_OR_SCRIPT
}

data class FlaggedFile(
    val id: String,
    val fileName: String,
    val filePath: String,
    val contentUri: Uri?,
    val sizeBytes: Long,
    val riskTier: RiskTier,
    val category: FileThreatCategory,
    val reasons: List<String>
)

data class FileScanProgress(
    val scannedFiles: Int,
    val totalFiles: Int,
    val currentFileName: String,
    val flaggedSoFar: Int,
    val directoriesTraversed: Int = 0
)

data class FileScanResult(
    val scannedAtMillis: Long,
    val totalFilesInspected: Int,
    val directoriesTraversed: Int = 0,
    val permissionGranted: Boolean,
    val crawlScopeLabel: String = "App Storage",
    val flaggedFiles: List<FlaggedFile>
)

private data class CrawlQuotaRoot(
    val dir: File,
    val maxFiles: Int,
    val maxDepth: Int
)

private data class MediaStoreCandidate(
    val uri: Uri,
    val displayName: String,
    val relativePath: String,
    val sizeBytes: Long
)

object FileSecurityAnalyzer {

    private const val EICAR_SIGNATURE =
        "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*"

    private val archiveExtensions = setOf("zip", "jar", "cbz", "apk", "docx", "xlsx", "pptx")
    private val nestedBombArchiveExtensions = setOf(".zip", ".gz", ".7z", ".rar", ".bz2", ".xz")
    private val safeDocMediaExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "pdf", "mp3", "mp4", "mkv", "wav", "txt", "csv", "json"
    )
    private val executableExtensions = setOf(
        "exe", "dll", "bat", "cmd", "vbs", "ps1", "sh", "dex", "odex", "elf", "so", "hta", "js", "jar"
    )
    private val riskyScriptTokens = listOf(
        "rm -rf", "chmod 777", "chmod +x", "curl ", "wget ", "dalvikvm",
        "pm install", "su -c", "/system/bin/sh", "eval(", "base64 -d",
        "nc -e", "/dev/tcp/", "powershell -enc", "invoke-expression",
        "wscript.shell", "mshta ", "fromcharcode("
    )
    private val ignoredDirNames = setOf(".thumbnails", ".trash", "cache", "code_cache", ".android_secure")

    fun hasFileScanPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun scanDeviceFilesWithProgress(
        context: Context,
        minDurationMs: Long = 5_000L,
        onProgress: suspend (FileScanProgress) -> Unit
    ): FileScanResult {
        val startMs = System.currentTimeMillis()
        val hasPerm = hasFileScanPermission(context)

        val discovered = LinkedHashMap<String, File>()
        val visitedCanonicalDirs = HashSet<String>()
        var dirsTraversed = 0

        val roots = buildPriorityCrawlRoots(context, hasPerm)
        for (quotaRoot in roots) {
            if (discovered.size >= 1500) break
            val addedDirs = crawlFileSystemRoot(
                root = quotaRoot.dir,
                maxDepth = quotaRoot.maxDepth,
                targetCap = (discovered.size + quotaRoot.maxFiles).coerceAtMost(1500),
                visitedDirs = visitedCanonicalDirs,
                out = discovered
            )
            dirsTraversed += addedDirs
        }

        // Scoped-storage fallback: query MediaStore for downloadable/accessible payloads if full permission not granted
        val mediaCandidates = if (!hasPerm) {
            queryAccessibleMediaStoreCandidates(context, discovered.keys)
        } else {
            emptyList()
        }

        val candidates = discovered.values.toList()
        val total = (candidates.size + mediaCandidates.size).coerceAtLeast(1)
        val perFileDelay = (minDurationMs / total).coerceIn(3L, 90L)
        val flagged = mutableListOf<FlaggedFile>()

        candidates.forEachIndexed { index, file ->
            onProgress(
                FileScanProgress(
                    scannedFiles = index + 1,
                    totalFiles = candidates.size + mediaCandidates.size,
                    currentFileName = file.name,
                    flaggedSoFar = flagged.size,
                    directoriesTraversed = dirsTraversed
                )
            )
            inspectLocalFile(file)?.let { flagged += it }
            delay(perFileDelay)
        }

        mediaCandidates.forEachIndexed { mIdx, mc ->
            onProgress(
                FileScanProgress(
                    scannedFiles = candidates.size + mIdx + 1,
                    totalFiles = candidates.size + mediaCandidates.size,
                    currentFileName = mc.displayName,
                    flaggedSoFar = flagged.size,
                    directoriesTraversed = dirsTraversed
                )
            )
            inspectSafStream(context, mc.uri, "${mc.relativePath}${mc.displayName}", mc.sizeBytes)?.let {
                flagged += it
            }
            delay(perFileDelay)
        }

        val elapsed = System.currentTimeMillis() - startMs
        if (elapsed < minDurationMs) {
            delay(minDurationMs - elapsed)
        }

        val scopeLabel = when {
            hasPerm -> "Full Device File System ($dirsTraversed dirs)"
            mediaCandidates.isNotEmpty() -> "App + Scoped MediaStore ($dirsTraversed dirs)"
            else -> "App-Accessible Storage ($dirsTraversed dirs)"
        }

        return FileScanResult(
            scannedAtMillis = System.currentTimeMillis(),
            totalFilesInspected = candidates.size + mediaCandidates.size,
            directoriesTraversed = dirsTraversed,
            permissionGranted = hasPerm,
            crawlScopeLabel = scopeLabel,
            flaggedFiles = flagged.sortedWith(
                compareBy<FlaggedFile> { it.riskTier.severityOrder }.thenBy { it.fileName.lowercase() }
            )
        )
    }

    suspend fun scanSafFolderWithProgress(
        context: Context,
        treeUri: Uri,
        onProgress: suspend (FileScanProgress) -> Unit
    ): FileScanResult {
        val (docEntries, dirsCount) = collectSafFiles(context, treeUri, maxCount = 500)
        val flagged = mutableListOf<FlaggedFile>()

        docEntries.forEachIndexed { idx, (uri, name, sizeBytes) ->
            onProgress(
                FileScanProgress(
                    scannedFiles = idx + 1,
                    totalFiles = docEntries.size,
                    currentFileName = name,
                    flaggedSoFar = flagged.size,
                    directoriesTraversed = dirsCount
                )
            )
            inspectSafStream(context, uri, name, sizeBytes)?.let { flagged += it }
            delay(18L)
        }

        return FileScanResult(
            scannedAtMillis = System.currentTimeMillis(),
            totalFilesInspected = docEntries.size,
            directoriesTraversed = dirsCount,
            permissionGranted = true,
            crawlScopeLabel = "SAF Folder Crawl ($dirsCount dirs)",
            flaggedFiles = flagged.sortedWith(
                compareBy<FlaggedFile> { it.riskTier.severityOrder }.thenBy { it.fileName.lowercase() }
            )
        )
    }

    private fun buildPriorityCrawlRoots(context: Context, hasPerm: Boolean): List<CrawlQuotaRoot> {
        val list = mutableListOf<CrawlQuotaRoot>()
        if (hasPerm) {
            val extRoot = Environment.getExternalStorageDirectory()
            list += CrawlQuotaRoot(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), 350, 5)
            list += CrawlQuotaRoot(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), 250, 5)
            listOf(
                File(extRoot, "Android/media"),
                File(extRoot, "WhatsApp/Media"),
                File(extRoot, "Telegram"),
                File(extRoot, "Bluetooth")
            ).forEach { dir ->
                list += CrawlQuotaRoot(dir, 150, 4)
            }
            list += CrawlQuotaRoot(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), 150, 4)
            list += CrawlQuotaRoot(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), 100, 3)
            list += CrawlQuotaRoot(extRoot, 500, 6)
        }
        context.getExternalFilesDir(null)?.let { list += CrawlQuotaRoot(it, 150, 4) }
        list += CrawlQuotaRoot(context.filesDir, 150, 4)
        list += CrawlQuotaRoot(context.cacheDir, 100, 3)
        return list.filter { it.dir.exists() && it.dir.canRead() }
    }

    /**
     * Breadth-first bounded directory crawler with canonical-path symlink-loop protection.
     */
    private fun crawlFileSystemRoot(
        root: File,
        maxDepth: Int,
        targetCap: Int,
        visitedDirs: HashSet<String>,
        out: LinkedHashMap<String, File>
    ): Int {
        var dirsVisited = 0
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.addLast(root to 0)

        while (queue.isNotEmpty() && out.size < targetCap) {
            val (dir, depth) = queue.removeFirst()
            if (depth > maxDepth) continue
            val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (!visitedDirs.add(canonical)) continue

            // Skip restricted Android app private sandboxes when crawling /sdcard root
            val normalized = canonical.replace('\\', '/')
            if (normalized.endsWith("/Android/data") || normalized.endsWith("/Android/obb")) continue

            dirsVisited++
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (out.size >= targetCap) break
                val name = child.name
                if (name.startsWith(".tmp")) continue
                if (child.isFile) {
                    out[child.absolutePath] = child
                } else if (child.isDirectory && child.canRead()) {
                    if (name.lowercase(Locale.US) !in ignoredDirNames) {
                        queue.addLast(child to (depth + 1))
                    }
                }
            }
        }
        return dirsVisited
    }

    private fun queryAccessibleMediaStoreCandidates(
        context: Context,
        alreadyIndexedPaths: Set<String>
    ): List<MediaStoreCandidate> {
        val candidates = mutableListOf<MediaStoreCandidate>()
        val targetExts = archiveExtensions + executableExtensions + setOf("txt", "gz", "gzip")
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA
        )
        runCatching {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                while (cursor.moveToNext() && candidates.size < 180) {
                    val name = cursor.getString(nameCol) ?: continue
                    val ext = name.lowercase(Locale.US).substringAfterLast('.', "")
                    if (ext !in targetExts && !hasSpoofedDoubleExtension(name.lowercase(Locale.US))) continue
                    val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                    if (path != null && path in alreadyIndexedPaths) continue
                    val id = cursor.getLong(idCol)
                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val itemUri = ContentUris.withAppendedId(uri, id)
                    candidates += MediaStoreCandidate(
                        uri = itemUri,
                        displayName = name,
                        relativePath = path?.substringBeforeLast('/')?.plus("/") ?: "MediaStore/",
                        sizeBytes = size
                    )
                }
            }
        }
        return candidates
    }

    fun inspectLocalFile(file: File): FlaggedFile? {
        if (!file.exists() || !file.isFile || !file.canRead()) return null
        val name = file.name
        val lowerName = name.lowercase(Locale.US)
        val ext = lowerName.substringAfterLast('.', "")
        val reasons = mutableListOf<String>()
        var category = FileThreatCategory.MALICIOUS_PAYLOAD
        var riskScore = 0

        // 1. Double / Spoofed extensions (e.g. invoice.pdf.apk, photo.jpg.exe)
        if (hasSpoofedDoubleExtension(lowerName)) {
            reasons += "Spoofed double extension disguising executable payload"
            category = FileThreatCategory.SPOOFED_EXTENSION
            riskScore += 5
        }

        // 2. Read header (16 KB) & tail (4 KB) safely without buffering large files
        val fileLen = file.length()
        val headerBuf = ByteArray(16_384)
        val headerRead = runCatching {
            FileInputStream(file).use { it.read(headerBuf) }
        }.getOrDefault(0).coerceAtLeast(0)
        val headerSlice = if (headerRead > 0) headerBuf.copyOf(headerRead) else ByteArray(0)

        val tailSlice = if (fileLen > headerRead + 32L && fileLen <= 32L * 1024L * 1024L) {
            runCatching {
                val tailLen = minOf(4096L, fileLen - headerRead).toInt()
                val buf = ByteArray(tailLen)
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(fileLen - tailLen)
                    raf.readFully(buf)
                }
                buf
            }.getOrDefault(ByteArray(0))
        } else {
            ByteArray(0)
        }

        checkMagicAndTextHeuristics(ext, lowerName, fileLen, headerSlice, tailSlice, reasons) { cat, pts ->
            category = cat
            riskScore += pts
        }

        // 3. Zip Bomb, Nested Archive Bomb & Zip-Slip Path Traversal inspection (zero disk extraction)
        val isZipHeader = headerRead >= 4 &&
            headerBuf[0] == 0x50.toByte() && headerBuf[1] == 0x4B.toByte() &&
            headerBuf[2] == 0x03.toByte() && headerBuf[3] == 0x04.toByte()
        if (ext in archiveExtensions || isZipHeader) {
            inspectZipFileSafely(file, reasons)?.let { (zipCategory, zipScore) ->
                category = zipCategory
                riskScore += zipScore
            }
        } else if (ext == "gz" || ext == "gzip" ||
            (headerRead >= 2 && headerBuf[0] == 0x1F.toByte() && headerBuf[1] == 0x8B.toByte())
        ) {
            inspectGzipBombSafely(file, reasons)?.let { gzScore ->
                category = FileThreatCategory.ZIP_BOMB
                riskScore += gzScore
            }
        }

        if (ext == "apk") {
            reasons += "Standalone APK package stored on device — verify publisher signature before installing"
            if (category == FileThreatCategory.MALICIOUS_PAYLOAD) {
                category = FileThreatCategory.SUSPICIOUS_APK_OR_SCRIPT
            }
            riskScore += 3
        } else if (ext in executableExtensions && riskScore < 3) {
            reasons += "Executable binary or script (.$ext) stored in user directory"
            category = FileThreatCategory.SUSPICIOUS_APK_OR_SCRIPT
            riskScore += 3
        }

        if (reasons.isEmpty() || riskScore < 2) return null

        val tier = when {
            riskScore >= 5 -> RiskTier.HIGH
            riskScore >= 3 -> RiskTier.MEDIUM
            else -> RiskTier.LOW
        }
        return FlaggedFile(
            id = file.absolutePath,
            fileName = name,
            filePath = file.absolutePath,
            contentUri = null,
            sizeBytes = fileLen,
            riskTier = tier,
            category = category,
            reasons = reasons.distinct()
        )
    }

    private fun inspectSafStream(
        context: Context,
        uri: Uri,
        name: String,
        sizeBytes: Long
    ): FlaggedFile? {
        val fileNameOnly = name.substringAfterLast('/')
        val lowerName = fileNameOnly.lowercase(Locale.US)
        val ext = lowerName.substringAfterLast('.', "")
        val reasons = mutableListOf<String>()
        var category = FileThreatCategory.MALICIOUS_PAYLOAD
        var riskScore = 0

        if (hasSpoofedDoubleExtension(lowerName)) {
            reasons += "Spoofed double extension disguising executable payload"
            category = FileThreatCategory.SPOOFED_EXTENSION
            riskScore += 5
        }

        val header = ByteArray(16_384)
        val headerRead = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: 0
        }.getOrDefault(0).coerceAtLeast(0)
        val slice = if (headerRead > 0) header.copyOf(headerRead) else ByteArray(0)

        checkMagicAndTextHeuristics(ext, lowerName, sizeBytes, slice, ByteArray(0), reasons) { cat, pts ->
            category = cat
            riskScore += pts
        }

        val isZipHeader = headerRead >= 4 &&
            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() && header[3] == 0x04.toByte()

        if (ext in archiveExtensions || isZipHeader) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ZipInputStream(stream).use { zis ->
                        var count = 0
                        var totalDeclared = 0L
                        var nestedArchives = 0
                        var zipSlipDetected = false
                        while (count < 10_500) {
                            val entry = zis.nextEntry ?: break
                            count++
                            if (entry.name.contains("../") || entry.name.contains("..\\")) {
                                zipSlipDetected = true
                            }
                            val uncomp = entry.size.coerceAtLeast(0L)
                            totalDeclared += uncomp
                            val eLower = entry.name.lowercase(Locale.US)
                            if (nestedBombArchiveExtensions.any { eLower.endsWith(it) }) {
                                nestedArchives++
                            }
                            if (entry.compressedSize > 0 && uncomp / entry.compressedSize >= 100L) {
                                reasons += "Extreme compression ratio detected inside archive (${uncomp / entry.compressedSize}:1)"
                                category = FileThreatCategory.ZIP_BOMB
                                riskScore += 6
                                break
                            }
                        }
                        if (zipSlipDetected) {
                            reasons += "Zip-Slip path traversal entry (../) detected inside archive"
                            category = FileThreatCategory.MALICIOUS_PAYLOAD
                            riskScore += 6
                        }
                        if (count >= 8000 || nestedArchives >= 8 || totalDeclared > 2L * 1024 * 1024 * 1024) {
                            reasons += "Zip bomb characteristics ($count entries, $nestedArchives nested archives)"
                            category = FileThreatCategory.ZIP_BOMB
                            riskScore += 6
                        }
                    }
                }
            }
        }

        if (ext == "apk") {
            reasons += "Standalone APK installer found in crawled storage"
            category = FileThreatCategory.SUSPICIOUS_APK_OR_SCRIPT
            riskScore += 3
        } else if (ext in executableExtensions && riskScore < 3) {
            reasons += "Executable binary or script (.$ext) detected"
            category = FileThreatCategory.SUSPICIOUS_APK_OR_SCRIPT
            riskScore += 3
        }

        if (reasons.isEmpty() || riskScore < 2) return null
        val tier = when {
            riskScore >= 5 -> RiskTier.HIGH
            riskScore >= 3 -> RiskTier.MEDIUM
            else -> RiskTier.LOW
        }
        return FlaggedFile(
            id = uri.toString(),
            fileName = fileNameOnly,
            filePath = name,
            contentUri = uri,
            sizeBytes = sizeBytes,
            riskTier = tier,
            category = category,
            reasons = reasons.distinct()
        )
    }

    private fun checkMagicAndTextHeuristics(
        ext: String,
        lowerName: String,
        fileSize: Long,
        headerBytes: ByteArray,
        tailBytes: ByteArray,
        reasons: MutableList<String>,
        onHit: (FileThreatCategory, Int) -> Unit
    ) {
        if (headerBytes.isEmpty()) return
        val headText = runCatching { headerBytes.toString(Charsets.UTF_8) }.getOrDefault("")
        val tailText = if (tailBytes.isNotEmpty()) {
            runCatching { tailBytes.toString(Charsets.UTF_8) }.getOrDefault("")
        } else {
            ""
        }
        val combinedText = if (tailText.isNotEmpty()) "$headText\n$tailText" else headText

        if (combinedText.contains(EICAR_SIGNATURE)) {
            reasons += "EICAR standard anti-malware test signature detected"
            onHit(FileThreatCategory.EICAR_TEST, 6)
        }

        val isMzPe = headerBytes.size >= 2 && headerBytes[0] == 'M'.code.toByte() && headerBytes[1] == 'Z'.code.toByte()
        val isElf = headerBytes.size >= 4 && headerBytes[0] == 0x7F.toByte() &&
            headerBytes[1] == 'E'.code.toByte() && headerBytes[2] == 'L'.code.toByte() && headerBytes[3] == 'F'.code.toByte()
        val isDex = headerBytes.size >= 4 && headerBytes[0] == 'd'.code.toByte() &&
            headerBytes[1] == 'e'.code.toByte() && headerBytes[2] == 'x'.code.toByte() && headerBytes[3] == '\n'.code.toByte()
        val isShebang = headerBytes.size >= 2 && headerBytes[0] == '#'.code.toByte() && headerBytes[1] == '!'.code.toByte()

        if (ext in safeDocMediaExtensions && (isMzPe || isElf || isDex || isShebang)) {
            reasons += "Magic-byte spoofing: .$ext file actually contains executable binary/script code"
            onHit(FileThreatCategory.SPOOFED_EXTENSION, 6)
        }

        val scriptExtensions = setOf("sh", "bat", "ps1", "cmd", "vbs", "js", "hta", "html", "htm")
        if ((ext in scriptExtensions || isShebang) && fileSize <= 1024_000L) {
            val lowerText = combinedText.lowercase(Locale.US)
            val hitTokens = riskyScriptTokens.filter { lowerText.contains(it) }
            if (hitTokens.isNotEmpty()) {
                reasons += "Payload contains privileged or obfuscated commands: ${hitTokens.joinToString(", ")}"
                onHit(FileThreatCategory.MALICIOUS_PAYLOAD, 5)
            }
        }

        if (isDex && ext != "dex") {
            reasons += "Hidden Dalvik bytecode payload (.dex) disguised as .$ext"
            onHit(FileThreatCategory.MALICIOUS_PAYLOAD, 6)
        }
        if (lowerName.contains("bomb") && ext in archiveExtensions) {
            reasons += "Archive filename matches zip-bomb pattern; central directory inspected"
        }
    }

    private fun inspectZipFileSafely(
        file: File,
        reasons: MutableList<String>
    ): Pair<FileThreatCategory, Int>? {
        return runCatching {
            ZipFile(file).use { zf ->
                var entryCount = 0
                var totalUncompressed = 0L
                var maxRatio = 0.0
                var nestedArchives = 0
                var nestedBombDetected = false
                var zipSlipDetected = false

                val enumEntries = zf.entries()
                while (enumEntries.hasMoreElements()) {
                    val entry = enumEntries.nextElement()
                    entryCount++
                    if (entry.name.contains("../") || entry.name.contains("..\\")) {
                        zipSlipDetected = true
                    }
                    val uncomp = entry.size.coerceAtLeast(0L)
                    val comp = entry.compressedSize.coerceAtLeast(0L)
                    totalUncompressed += uncomp
                    if (comp > 0) {
                        val ratio = uncomp.toDouble() / comp.toDouble()
                        if (ratio > maxRatio) maxRatio = ratio
                    }
                    val eLower = entry.name.lowercase(Locale.US)
                    if (nestedBombArchiveExtensions.any { eLower.endsWith(it) }) {
                        nestedArchives++
                        // Shallow 1-level inspection of embedded small zip entries (<= 2 MB compressed)
                        if (eLower.endsWith(".zip") && !nestedBombDetected && comp in 1L..(2L * 1024 * 1024)) {
                            nestedBombDetected = checkNestedZipEntryForBomb(zf, entry)
                        }
                    }
                    if (entryCount > 20_000) break
                }

                val fileRatio = if (file.length() > 0L) {
                    totalUncompressed.toDouble() / file.length().toDouble()
                } else {
                    0.0
                }
                val worstRatio = maxOf(maxRatio, fileRatio)
                var score = 0
                var cat = FileThreatCategory.ZIP_BOMB

                if (zipSlipDetected) {
                    reasons += "Zip-Slip path traversal payload (../) found inside archive"
                    cat = FileThreatCategory.MALICIOUS_PAYLOAD
                    score += 6
                }

                if (nestedBombDetected) {
                    reasons += "Multi-layer recursive zip bomb detected inside nested archive entry"
                    score += 6
                }

                if (worstRatio >= 100.0 && totalUncompressed >= 50L * 1024L * 1024L) {
                    reasons += String.format(
                        Locale.US,
                        "Zip Bomb detected: %.0f:1 compression ratio expands to %.1f GB",
                        worstRatio,
                        totalUncompressed / (1024.0 * 1024.0 * 1024.0)
                    )
                    score += 6
                } else if (worstRatio >= 250.0) {
                    reasons += String.format(
                        Locale.US,
                        "Extreme archive compression ratio (%.0f:1) characteristic of decompression bomb",
                        worstRatio
                    )
                    score += 5
                }

                if (entryCount >= 8_000) {
                    reasons += "Archive flood bomb: contains $entryCount+ entries"
                    score += 5
                }
                if (nestedArchives >= 8) {
                    reasons += "Recursive archive bomb: contains $nestedArchives nested archives"
                    score += 5
                }
                if (totalUncompressed > 2L * 1024L * 1024L * 1024L && file.length() < 100L * 1024L * 1024L) {
                    reasons += "Uncompressed archive payload exceeds 2 GB from small container"
                    score += 6
                }

                if (score > 0) cat to score else null
            }
        }.getOrNull()
    }

    private fun checkNestedZipEntryForBomb(zf: ZipFile, outerEntry: java.util.zip.ZipEntry): Boolean {
        return runCatching {
            val bytes = zf.getInputStream(outerEntry).use { it.readBytes() }
            val maxProbeDecompressed = 16L * 1024L * 1024L
            val scratch = ByteArray(8192)
            ZipInputStream(ByteArrayInputStream(bytes)).use { nestedZis ->
                var nestedCount = 0
                var totalDecompressed = 0L
                while (nestedCount < 5_000) {
                    val inner = nestedZis.nextEntry ?: break
                    nestedCount++
                    val declaredUncomp = inner.size
                    val declaredComp = inner.compressedSize
                    if (declaredComp > 0 && declaredUncomp / declaredComp >= 150L) {
                        return@runCatching true
                    }
                    while (totalDecompressed < maxProbeDecompressed) {
                        val n = nestedZis.read(scratch)
                        if (n <= 0) break
                        totalDecompressed += n
                    }
                    nestedZis.closeEntry()
                    val postUncomp = maxOf(declaredUncomp, inner.size, totalDecompressed)
                    val postComp = maxOf(declaredComp, inner.compressedSize)
                    if (postComp > 0 && inner.size > 0 && inner.size / postComp >= 150L) {
                        return@runCatching true
                    }
                    if (totalDecompressed >= maxProbeDecompressed &&
                        bytes.isNotEmpty() &&
                        (totalDecompressed / bytes.size.toDouble()) >= 80.0
                    ) {
                        return@runCatching true
                    }
                    if (postUncomp >= 100L * 1024L * 1024L) {
                        return@runCatching true
                    }
                }
                nestedCount >= 4000
            }
        }.getOrDefault(false)
    }

    private fun inspectGzipBombSafely(file: File, reasons: MutableList<String>): Int? {
        if (file.length() < 18L) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(file.length() - 4L)
                val b = ByteArray(4)
                raf.readFully(b)
                val isize = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val ratio = isize.toDouble() / file.length().toDouble()
                if (ratio >= 100.0 && isize >= 50L * 1024L * 1024L) {
                    reasons += String.format(
                        Locale.US,
                        "GZIP Bomb detected: %.0f:1 expansion ratio (%.1f MB uncompressed)",
                        ratio,
                        isize / (1024.0 * 1024.0)
                    )
                    6
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun hasSpoofedDoubleExtension(lowerName: String): Boolean {
        val parts = lowerName.split('.')
        if (parts.size < 3) return false
        val finalExt = parts.last()
        val prevExt = parts[parts.size - 2]
        return prevExt in safeDocMediaExtensions && (finalExt == "apk" || finalExt in executableExtensions)
    }

    private fun collectSafFiles(
        context: Context,
        treeUri: Uri,
        maxCount: Int
    ): Pair<List<Triple<Uri, String, Long>>, Int> {
        val results = mutableListOf<Triple<Uri, String, Long>>()
        var dirsCount = 0
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<String>()
        queue.add(rootDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        while (queue.isNotEmpty() && results.size < maxCount) {
            val docId = queue.removeFirst()
            dirsCount++
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            runCatching {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext() && results.size < maxCount) {
                        val childId = cursor.getString(0) ?: continue
                        val name = cursor.getString(1) ?: "file"
                        val mime = cursor.getString(2) ?: ""
                        val size = cursor.getLong(3)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.addLast(childId)
                        } else {
                            val itemUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            results += Triple(itemUri, name, size)
                        }
                    }
                }
            }
        }
        return results to dirsCount
    }
}
