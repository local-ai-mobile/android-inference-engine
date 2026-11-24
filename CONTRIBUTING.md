# Contributing to local-ai-mobile

Thank you for your interest in contributing to the **local-ai-mobile** open-source ecosystem! Our mission is to provide Android developers with high-performance, 100% offline, on-device AI tools and autonomous agent runtimes.

We welcome contributions of all types: bug fixes, performance improvements, new on-device MCP tools, new hardware acceleration delegates, documentation enhancements, and unit tests.

---

## Code of Conduct

We are committed to providing a welcoming, inclusive, and harassment-free environment for all contributors. Please treat fellow community members with respect, empathy, and constructive feedback.

---

## Getting Started

1. **Prerequisites**:
   - Android Studio Ladybug (2024.2.1+) or newer
   - JDK 17 or JDK 21/22
   - Android SDK (API Level 26 minimum, API 35/36 compile target)
   - Git & GitHub CLI (`gh`)

2. **Repository Structure**:
   - `android-inference-engine`: Native llama.cpp GGUF, LiteRT NPU/GPU delegates, and Gemini Nano AICore runtimes.
   - `android-mcp-tools`: Model Context Protocol 2024-11-05 implementation, 9 system tools, and inter-app IPC.
   - `android-agent-workflow`: Plan-Execute-Observe-Verify autonomous loop, context distillation, and 3-tier coordinator.
   - `.github`: Organization profile, architecture blueprint, issue templates, and community assets.

3. **Development Workflow**:
   - Fork the repository you wish to contribute to.
   - Create a feature branch: `git checkout -b feature/your-feature-name`
   - Make your changes following our code style and architecture patterns.
   - Write or update automated unit/integration tests (using Robolectric and JUnit).
   - Verify that all tests pass: `./gradlew test`
   - Commit your changes with clear, descriptive commit messages:
     ```bash
     git commit -m "feat(mcp): add sqlite local database inspector tool"
     ```
   - Push to your fork: `git push origin feature/your-feature-name`
   - Open a Pull Request targeting the `main` branch.

---

## Architectural Principles & Coding Guidelines

1. **Zero Cloud Dependencies**:
   - All core features must operate completely offline without requiring cloud API keys, network pings, or external telemetry.
2. **Minimal Change & High Cohesion**:
   - Avoid bloated dependencies. Prefer pure Kotlin/Java and official AndroidX libraries.
3. **Hardware Safety**:
   - Always verify thermal and battery constraints before initiating intensive workloads.
4. **Sandboxed Security**:
   - MCP tools that access the filesystem must strictly validate canonical paths to prevent path traversal attacks.
5. **Test Coverage**:
   - Every bug fix must include a reproducing test. Every new feature must include Robolectric or JUnit unit tests covering normal execution and edge cases.

---

## Reporting Issues & Requesting Features

- To report bugs, please use our [Bug Report Template](https://github.com/local-ai-mobile/.github/issues/new?template=bug_report.md).
- To propose new features or MCP tools, please use our [Feature Request Template](https://github.com/local-ai-mobile/.github/issues/new?template=feature_request.md).
- For security vulnerabilities, please refer to [SECURITY.md](./SECURITY.md) and do NOT file public issues.

---

## License

By contributing to `local-ai-mobile`, you agree that your contributions will be licensed under the [Apache License, Version 2.0](./LICENSE).
