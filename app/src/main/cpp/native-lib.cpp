#include <jni.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fcntl.h>
#include <fstream>
#include <numeric>
#include <random>
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

namespace {

using Clock = std::chrono::steady_clock;

// Portable standard C++17 SHA-256 implementation (no vendor/ISA lock-in)
constexpr uint32_t kSha256K[64] = {
    0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u, 0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
    0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u, 0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
    0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu, 0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
    0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u, 0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
    0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u, 0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
    0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u, 0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
    0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u, 0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
    0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u, 0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u
};

inline uint32_t rotr(uint32_t x, uint32_t n) {
    return (x >> n) | (x << (32u - n));
}

void sha256Transform(uint32_t state[8], const uint8_t block[64]) {
    uint32_t w[64];
    for (int i = 0; i < 16; ++i) {
        w[i] = (static_cast<uint32_t>(block[i * 4]) << 24u) |
               (static_cast<uint32_t>(block[i * 4 + 1]) << 16u) |
               (static_cast<uint32_t>(block[i * 4 + 2]) << 8u) |
               static_cast<uint32_t>(block[i * 4 + 3]);
    }
    for (int i = 16; i < 64; ++i) {
        uint32_t s0 = rotr(w[i - 15], 7u) ^ rotr(w[i - 15], 18u) ^ (w[i - 15] >> 3u);
        uint32_t s1 = rotr(w[i - 2], 17u) ^ rotr(w[i - 2], 19u) ^ (w[i - 2] >> 10u);
        w[i] = w[i - 16] + s0 + w[i - 7] + s1;
    }

    uint32_t a = state[0], b = state[1], c = state[2], d = state[3];
    uint32_t e = state[4], f = state[5], g = state[6], h = state[7];

    for (int i = 0; i < 64; ++i) {
        uint32_t S1 = rotr(e, 6u) ^ rotr(e, 11u) ^ rotr(e, 25u);
        uint32_t ch = (e & f) ^ ((~e) & g);
        uint32_t temp1 = h + S1 + ch + kSha256K[i] + w[i];
        uint32_t S0 = rotr(a, 2u) ^ rotr(a, 13u) ^ rotr(a, 22u);
        uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        uint32_t temp2 = S0 + maj;

        h = g; g = f; f = e; e = d + temp1;
        d = c; c = b; b = a; a = temp1 + temp2;
    }

    state[0] += a; state[1] += b; state[2] += c; state[3] += d;
    state[4] += e; state[5] += f; state[6] += g; state[7] += h;
}

struct CpuWorkerResult {
    double fpMflops = 0.0;
    double shaMbPerSec = 0.0;
    double intMops = 0.0;
    uint64_t checksum = 0;
};

CpuWorkerResult runCpuCoreWorkload(int durationMs, uint32_t seed) {
    const auto start = Clock::now();
    const auto deadline = start + std::chrono::milliseconds(std::max(100, durationMs));

    // Phase 1: Floating-point fused ops & trig (approx 35% of budget)
    const auto fpDeadline = start + std::chrono::milliseconds(std::max(30, (durationMs * 35) / 100));
    double fpAcc = 1.0001 + (seed % 17) * 0.001;
    double x = 0.523598775;
    uint64_t fpOps = 0;
    while (Clock::now() < fpDeadline) {
        for (int i = 0; i < 4096; ++i) {
            fpAcc = fpAcc * 1.0000013 + std::sin(x) * std::cos(fpAcc);
            x += 0.00017;
            if (fpAcc > 1e6 || fpAcc < -1e6) fpAcc = 1.0001;
        }
        fpOps += 4096ULL * 6ULL;
    }
    const double fpSeconds = std::max(0.001, std::chrono::duration<double>(Clock::now() - start).count());

    // Phase 2: Integer prime sieve + ALU mix (approx 30% of budget)
    const auto intStart = Clock::now();
    const auto intDeadline = start + std::chrono::milliseconds(std::max(60, (durationMs * 65) / 100));
    uint64_t intOps = 0;
    uint64_t intHash = 0x9e3779b97f4a7c15ULL ^ seed;
    std::vector<uint8_t> sieve(8192, 1);
    while (Clock::now() < intDeadline) {
        std::fill(sieve.begin(), sieve.end(), 1);
        for (size_t p = 2; p * p < sieve.size(); ++p) {
            if (sieve[p]) {
                for (size_t m = p * p; m < sieve.size(); m += p) {
                    sieve[m] = 0;
                    intOps += 2;
                }
            }
        }
        for (size_t i = 2; i < sieve.size(); ++i) {
            if (sieve[i]) {
                intHash ^= (intHash << 7u) + i + (intHash >> 3u);
            }
        }
        intOps += sieve.size() * 4ULL;
    }
    const double intSeconds = std::max(0.001, std::chrono::duration<double>(Clock::now() - intStart).count());

    // Phase 3: SHA-256 hashing throughput (remaining 35% of budget)
    const auto shaStart = Clock::now();
    std::array<uint8_t, 64> block{};
    for (size_t i = 0; i < block.size(); ++i) {
        block[i] = static_cast<uint8_t>((i * 31u + seed) & 0xFFu);
    }
    uint32_t state[8] = {
        0x6a09e667u, 0xbb67ae85u, 0x3c6ef372u, 0xa54ff53au,
        0x510e527fu, 0x9b05688cu, 0x1f83d9abu, 0x5be0cd19u
    };
    uint64_t bytesHashed = 0;
    while (Clock::now() < deadline) {
        for (int i = 0; i < 256; ++i) {
            sha256Transform(state, block.data());
            block[0] ^= static_cast<uint8_t>(state[0] & 0xFFu);
        }
        bytesHashed += 256ULL * 64ULL;
    }
    const double shaSeconds = std::max(0.001, std::chrono::duration<double>(Clock::now() - shaStart).count());

    CpuWorkerResult res;
    res.fpMflops = (static_cast<double>(fpOps) / 1e6) / fpSeconds;
    res.intMops = (static_cast<double>(intOps) / 1e6) / intSeconds;
    res.shaMbPerSec = (static_cast<double>(bytesHashed) / (1024.0 * 1024.0)) / shaSeconds;
    res.checksum = static_cast<uint64_t>(fpAcc * 1000.0) ^ intHash ^ state[0];
    return res;
}

} // namespace

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_benchy_X_engine_NativeBridge_runCpuSingleCoreBenchmark(
    JNIEnv* env,
    jobject /* thiz */,
    jint durationMs
) {
    const auto r = runCpuCoreWorkload(static_cast<int>(durationMs), 42u);
    // Normalized single-core score tuned so modern ARM64 phones score ~1200-3200
    const double score = (r.fpMflops * 0.55) + (r.intMops * 0.35) + (r.shaMbPerSec * 4.5);

    jdouble out[4] = { score, r.fpMflops, r.shaMbPerSec, r.intMops };
    jdoubleArray arr = env->NewDoubleArray(4);
    env->SetDoubleArrayRegion(arr, 0, 4, out);
    return arr;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_benchy_X_engine_NativeBridge_runCpuMultiCoreBenchmark(
    JNIEnv* env,
    jobject /* thiz */,
    jint threadCount,
    jint durationMs
) {
    const int threads = std::max(1, std::min(32, static_cast<int>(threadCount)));
    std::vector<std::thread> pool;
    std::vector<CpuWorkerResult> results(threads);
    const auto t0 = Clock::now();

    for (int i = 0; i < threads; ++i) {
        pool.emplace_back([&, i]() {
            results[i] = runCpuCoreWorkload(static_cast<int>(durationMs), static_cast<uint32_t>(i * 97 + 13));
        });
    }
    for (auto& t : pool) {
        t.join();
    }

    const double elapsedMs = std::chrono::duration<double, std::milli>(Clock::now() - t0).count();
    double sumFp = 0.0, sumSha = 0.0, sumInt = 0.0;
    for (const auto& r : results) {
        sumFp += r.fpMflops;
        sumSha += r.shaMbPerSec;
        sumInt += r.intMops;
    }
    const double multiScore = (sumFp * 0.50) + (sumInt * 0.30) + (sumSha * 4.2);

    jdouble out[4] = { multiScore, sumFp, sumSha, elapsedMs };
    jdoubleArray arr = env->NewDoubleArray(4);
    env->SetDoubleArrayRegion(arr, 0, 4, out);
    return arr;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_benchy_X_engine_NativeBridge_runRamBenchmark(
    JNIEnv* env,
    jobject /* thiz */,
    jint bufferMb
) {
    const size_t clampedMb = static_cast<size_t>(std::max(8, std::min(128, static_cast<int>(bufferMb))));
    const size_t totalBytes = clampedMb * 1024ULL * 1024ULL;
    const size_t count = totalBytes / sizeof(uint64_t);

    std::vector<uint64_t> src(count, 0xA5A5A5A55A5A5A5AULL);
    std::vector<uint64_t> dst(count, 0ULL);

    // Warmup touch
    for (size_t i = 0; i < count; i += 512) {
        src[i] = static_cast<uint64_t>(i);
    }

    // Sequential Write + Read Bandwidth
    constexpr int kPasses = 4;
    const auto w0 = Clock::now();
    volatile uint64_t sink = 0;
    for (int p = 0; p < kPasses; ++p) {
        std::memcpy(dst.data(), src.data(), totalBytes);
        sink ^= dst[(p * 1021) % count];
    }
    const double copySeconds = std::max(0.0001, std::chrono::duration<double>(Clock::now() - w0).count());
    const double copyMbPerSec = (static_cast<double>(clampedMb) * kPasses) / copySeconds;

    // Sequential Sum Read Bandwidth
    const auto r0 = Clock::now();
    uint64_t acc = 0;
    for (int p = 0; p < kPasses; ++p) {
        const uint64_t* ptr = dst.data();
        for (size_t i = 0; i < count; i += 8) {
            acc += ptr[i] + ptr[i + 1] + ptr[i + 2] + ptr[i + 3];
        }
    }
    sink ^= acc;
    const double readSeconds = std::max(0.0001, std::chrono::duration<double>(Clock::now() - r0).count());
    const double readMbPerSec = (static_cast<double>(clampedMb) * kPasses) / readSeconds;

    // Pointer-chasing random latency benchmark (8MB working set to bypass L1/L2 cache)
    const size_t latCount = (8ULL * 1024ULL * 1024ULL) / sizeof(uint32_t);
    std::vector<uint32_t> ring(latCount);
    std::iota(ring.begin(), ring.end(), 0u);
    std::mt19937 rng(1337u);
    for (size_t i = latCount - 1; i > 0; --i) {
        size_t j = rng() % (i + 1);
        std::swap(ring[i], ring[j]);
    }
    // Convert permutation to single cycle
    std::vector<uint32_t> nextIdx(latCount);
    for (size_t i = 0; i < latCount; ++i) {
        nextIdx[ring[i]] = ring[(i + 1) % latCount];
    }

    constexpr uint64_t kHops = 500000ULL;
    uint32_t idx = 0;
    const auto l0 = Clock::now();
    for (uint64_t h = 0; h < kHops; ++h) {
        idx = nextIdx[idx];
    }
    sink ^= idx;
    const double latNs = std::chrono::duration<double, std::nano>(Clock::now() - l0).count() / static_cast<double>(kHops);

    jdouble out[3] = { readMbPerSec, copyMbPerSec, latNs };
    jdoubleArray arr = env->NewDoubleArray(3);
    env->SetDoubleArrayRegion(arr, 0, 3, out);
    return arr;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_benchy_X_engine_NativeBridge_runStorageBenchmark(
    JNIEnv* env,
    jobject /* thiz */,
    jstring cacheDirPath,
    jint fileSizeMb,
    jint randomOps
) {
    const char* rawPath = env->GetStringUTFChars(cacheDirPath, nullptr);
    std::string testFile = std::string(rawPath ? rawPath : "/data/local/tmp") + "/benchy_io_probe.bin";
    if (rawPath) env->ReleaseStringUTFChars(cacheDirPath, rawPath);

    const size_t mb = static_cast<size_t>(std::max(8, std::min(128, static_cast<int>(fileSizeMb))));
    const size_t totalBytes = mb * 1024ULL * 1024ULL;
    const size_t chunkSize = 256ULL * 1024ULL;
    std::vector<uint8_t> chunk(chunkSize);
    for (size_t i = 0; i < chunkSize; ++i) {
        chunk[i] = static_cast<uint8_t>((i * 17u + 3u) & 0xFFu);
    }

    double seqWriteMbPerSec = 0.0;
    double seqReadMbPerSec = 0.0;
    double rand4kReadIops = 0.0;
    double rand4kWriteIops = 0.0;

    int fd = ::open(testFile.c_str(), O_RDWR | O_CREAT | O_TRUNC, 0600);
    if (fd >= 0) {
        // Sequential Write with fsync
        const auto w0 = Clock::now();
        size_t written = 0;
        while (written < totalBytes) {
            ssize_t n = ::write(fd, chunk.data(), chunkSize);
            if (n <= 0) break;
            written += static_cast<size_t>(n);
        }
        ::fsync(fd);
        const double wSec = std::max(0.0005, std::chrono::duration<double>(Clock::now() - w0).count());
        seqWriteMbPerSec = (static_cast<double>(written) / (1024.0 * 1024.0)) / wSec;

        // Sequential Read from start
        ::lseek(fd, 0, SEEK_SET);
        const auto r0 = Clock::now();
        size_t readBytes = 0;
        while (readBytes < written) {
            ssize_t n = ::read(fd, chunk.data(), chunkSize);
            if (n <= 0) break;
            readBytes += static_cast<size_t>(n);
        }
        const double rSec = std::max(0.0005, std::chrono::duration<double>(Clock::now() - r0).count());
        seqReadMbPerSec = (static_cast<double>(readBytes) / (1024.0 * 1024.0)) / rSec;

        // Random 4K Reads
        const size_t blockSize = 4096ULL;
        const size_t maxBlocks = std::max<size_t>(1, written / blockSize);
        const int ops = std::max(100, std::min(4000, static_cast<int>(randomOps)));
        std::mt19937 rng(2026u);
        std::vector<uint8_t> block4k(blockSize, 0x5Cu);

        const auto rr0 = Clock::now();
        for (int i = 0; i < ops; ++i) {
            off_t off = static_cast<off_t>((rng() % maxBlocks) * blockSize);
            ::pread(fd, block4k.data(), blockSize, off);
        }
        const double rrSec = std::max(0.0005, std::chrono::duration<double>(Clock::now() - rr0).count());
        rand4kReadIops = static_cast<double>(ops) / rrSec;

        // Random 4K Writes (with periodic fdatasync for realistic flash sync overhead)
        const int wOps = std::max(60, ops / 4);
        const auto rw0 = Clock::now();
        for (int i = 0; i < wOps; ++i) {
            off_t off = static_cast<off_t>((rng() % maxBlocks) * blockSize);
            block4k[0] = static_cast<uint8_t>(i & 0xFF);
            ::pwrite(fd, block4k.data(), blockSize, off);
            if ((i & 7) == 7) {
                ::fdatasync(fd);
            }
        }
        ::fdatasync(fd);
        const double rwSec = std::max(0.0005, std::chrono::duration<double>(Clock::now() - rw0).count());
        rand4kWriteIops = static_cast<double>(wOps) / rwSec;

        ::close(fd);
        ::unlink(testFile.c_str());
    }

    jdouble out[4] = { seqWriteMbPerSec, seqReadMbPerSec, rand4kReadIops, rand4kWriteIops };
    jdoubleArray arr = env->NewDoubleArray(4);
    env->SetDoubleArrayRegion(arr, 0, 4, out);
    return arr;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_benchy_X_engine_NativeBridge_readCpuFrequenciesKhz(
    JNIEnv* env,
    jobject /* thiz */,
    jint coreCount
) {
    const int cores = std::max(1, std::min(32, static_cast<int>(coreCount)));
    std::vector<jint> freqs(cores * 2, 0);

    for (int i = 0; i < cores; ++i) {
        std::string curPath = "/sys/devices/system/cpu/cpu" + std::to_string(i) + "/cpufreq/scaling_cur_freq";
        std::string maxPath = "/sys/devices/system/cpu/cpu" + std::to_string(i) + "/cpufreq/cpuinfo_max_freq";
        std::ifstream curFile(curPath);
        int curVal = 0;
        if (curFile.is_open()) curFile >> curVal;
        std::ifstream maxFile(maxPath);
        int maxVal = 0;
        if (maxFile.is_open()) maxFile >> maxVal;
        freqs[i * 2] = curVal;
        freqs[i * 2 + 1] = maxVal;
    }

    jintArray arr = env->NewIntArray(cores * 2);
    env->SetIntArrayRegion(arr, 0, cores * 2, freqs.data());
    return arr;
}
