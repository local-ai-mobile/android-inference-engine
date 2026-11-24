# Android Inference Engine: On-Device AI Runtime for Android

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=26)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.localai/android-inference-engine.svg)](https://central.sonatype.com/)
[![Build Status](https://github.com/local-ai-mobile/android-inference-engine/actions/workflows/build-test.yml/badge.svg)](https://github.com/local-ai-mobile/android-inference-engine/actions)

A standalone, production-ready **android on-device ai** inference runtime designed for high-performance mobile edge intelligence. Seamlessly integrates **llama.cpp android** GGUF execution, Google **litert npu acceleration**, and Google **gemini nano android aicore** system acceleration behind a unified, lightweight Kotlin Coroutines API.

Engineered for **mobile llm inference**, this library provides automated **android hardware profiling**, Arm **kleidiai android** micro-kernel acceleration, self-calibrating tokens-per-second (TPS) prediction, and **multi-token prediction android** (MTP) speculative decoding.

---

## Architecture Overview

```
+-----------------------------------------------------------------------------------+
|                        io.github.localai.inference.api                            |
|             (LocalInference, InferenceEngine, EngineOptions, InferenceChunk)      |
+-----------------------------------------------------------------------------------+
                                          │
                  ┌───────────────────────┼───────────────────────┐
                  ▼                       ▼                       ▼
+-----------------------------+ +--------------------+ +----------------------------+
|        LlamaCppEngine       | |    LiteRTEngine    | |        AiCoreEngine        |
| • GGUF universal runtime    | | • Play Services    | | • Android 14+ AICore       |
| • Maven: llamacpp-kotlin    | |   unbundled        | | • Zero APK bloat (0 MB)    |
| • 32-bit RAM guard (1.1GB)  | | • 3-Tier fallback  | | • OS download progress     |
| • Perf core thread scaling  | | • MTP speculative  | | • GeminiNanoAgentEngine    |
| • Context cleanup reflection| | • Vision pre-proc  | | • GeminiNanoTaskApis       |
+-----------------------------+ +--------------------+ +----------------------------+
                  │                       │                       │
                  └───────────────────────┼───────────────────────┘
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  ▼                                               ▼
+------------------------------------+  +-------------------------------------------+
|          Hardware Profiler         |  |         Dynamic Router & Telemetry        |
| • Arm KleidiAI / DotProd / I8MM    |  | • Magic byte detection (GGUF vs TFL3)     |
| • big.LITTLE core frequency parsing|  | • 6-Family Tokenizer Chat Templates       |
| • GPU Vendor (Adreno, Mali)        |  | • Context Window Manager & Think-stripping|
| • Device Tiering & TPS Prediction  |  | • Thermal throttling & Battery power-gate |
+------------------------------------+  +-------------------------------------------+
```

---

## Key Features

1. **Tri-Engine Unified Runtime**:
   - **llama.cpp (GGUF universal)**: High-speed quantized LLM execution (`gguf kotlin`) with ARM NEON and Vulkan GPU offloading.
   - **LiteRT (Play Services NPU & GPU)**: Direct hardware acceleration with 3-tier delegate fallback (System GPU -> System CPU -> Bundled).
   - **Gemini Nano (Android AICore)**: Zero-byte APK bloat, binding directly to Android 14+ on-device system models with hardware NPU efficiency.

2. **Hardware Profiling & Silicon Detection**:
   - Inspects `/proc/cpuinfo` for ARM instructions (`dotprod`, `i8mm`, `neon`, `fp16`, `sve`).
   - Automatically labels **KleidiAI** optimized kernels (`KleidiAI (NEON+I8MM)`, `KleidiAI (Arm DotProd)`).
   - Detects Qualcomm Adreno and ARM Mali GPU architectures.
   - Parses big.LITTLE core topologies and dynamically allocates compute threads.

3. **Multi-Token Prediction (MTP) & Speculative Decoding**:
   - Speculative decoding support enabling $1.5\times$ to $3\times$ faster token generation on supported graphs.

4. **Multi-Family Tokenizer Chat Templating**:
   - Out-of-the-box templating for 6 model families: `Llama 3`, `ChatML` (Qwen, DeepSeek, SmolLM), `Gemma`, `Phi`, `Mistral/Llama-2`, and `Generic`.
   - Automated `<think>...</think>` scratchpad stripping and document retention pinning during context window overflows.

5. **Resource Management & System Telemetry**:
   - **Dynamic Thermal Throttling**: Automatically reduces CPU thread count and GPU layers under moderate and severe heat while preserving user preference.
   - **Battery Power Gating**: Gates intensive inference when battery < 15% (unplugged) or OS Battery Saver is enabled.
   - **Memory Limiter Kill (MLK) Guard**: Intercepts `ComponentCallbacks2.onTrimMemory` to perform tiered cache reclamation before low-memory killer intervention.
   - **Model Hibernation**: Watchdog releasing inactive native models after 5 minutes of inactivity.

---

## Installation

Add the dependency to your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.localai:android-inference-engine:1.0.0")
}
```

---

## Quickstart Guide

### 1. Hardware Profiling & Silicon Detection

```kotlin
import io.github.localai.inference.api.LocalInference
import io.github.localai.inference.hardware.PerformanceMode

// Detect hardware silicon capabilities
val silicon = LocalInference.detectSilicon(context)
println("Engine: ${silicon.engineLabel}")
println("KleidiAI Supported: ${silicon.hasKleidiAi}")
println("GPU Delegate Ready: ${silicon.gpuDelegateReady}")

// Compute optimal hardware profile
val profile = LocalInference.profileHardware(context, PerformanceMode.BALANCED)
println("Device Tier: ${profile.tier}")
println("Recommended GPU Layers: ${profile.recommendedGpuLayers}")
println("Recommended Thread Count: ${profile.nThreads}")
```

### 2. Loading and Streaming LLM Generation

```kotlin
import io.github.localai.inference.api.*

// 1. Resolve engine automatically via magic byte or model ID routing
val engineType = LocalInference.resolveEngineType(
    modelId = "meta-llama/Llama-3.2-1B-Instruct",
    modelPath = "/sdcard/Download/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
)

// 2. Instantiate engine
val engine = LocalInference.createEngine(context, engineType)

// 3. Load model weights
val isLoaded = engine.loadModel(
    modelPath = "/sdcard/Download/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
    modelId = "meta-llama/Llama-3.2-1B-Instruct",
    options = EngineOptions(useGpu = true, threadCount = 4)
)

if (isLoaded) {
    // 4. Stream response tokens
    val messages = listOf(
        InferenceChatMessage(role = ChatRole.SYSTEM, text = "You are a helpful assistant."),
        InferenceChatMessage(role = ChatRole.USER, text = "What are the advantages of on-device AI?")
    )
    val prompt = LocalInference.buildBudgetedPrompt("meta-llama/Llama-3.2-1B-Instruct", null, messages)

    engine.generateResponses(prompt).collect { token ->
        print(token)
    }

    // 5. Unload model when done
    engine.unloadModel()
}
```

### 3. Zero-Byte Gemini Nano AICore Quick Tasks

```kotlin
import io.github.localai.inference.api.LocalInference

// Turnkey summarization using system Gemini Nano
val summary = LocalInference.summarize(context, longArticleText)
println("Summary: $summary")

// Proofreading
val correctedText = LocalInference.proofread(context, "They is going to the park.")
println("Corrected: $correctedText")

// Tone rewriting
val formalText = LocalInference.rewrite(context, "hey whats up", style = "formal")
println("Rewritten: $formalText")
```

---

## Configuration Options

| Option | Type | Default | Description |
|---|---|---|---|
| `useNpu` | `Boolean` | `true` | Enable NPU hardware acceleration delegates |
| `useGpu` | `Boolean` | `true` | Enable GPU offloading (Adreno OpenCL / Mali Vulkan) |
| `threadCount` | `Int` | `4` | Number of compute threads for matrix operations |
| `temperature` | `Float` | `0.7f` | Sampling temperature |
| `topK` | `Int` | `40` | Top-K sampling cutoff |
| `topP` | `Float` | `0.95f` | Nucleus sampling probability threshold |
| `repeatPenalty` | `Float` | `1.1f` | Repetition penalty factor |
| `useMtp` | `Boolean` | `false` | Enable multi-token prediction speculative decoding |
| `maxTokens` | `Int` | `1024` | Maximum completion token budget |

---

## License

```
Copyright 2026 The local-ai-mobile Authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
