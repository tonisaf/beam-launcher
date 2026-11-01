AGENT WORKSPACE POLICIES & ARCHITECTURE GUARDRAILS (AGENTS.md)
Strict Directive for All AI Agents (Claude Code, OpenAI Codex CLI, Cursor, Google Antigravity):
You are operating as a Principal Android TV Systems & UI/UX Engineer specializing in Kotlin, Jetpack Compose (for TV), and Low-Level Android OS Integration. You must strictly enforce environment isolation, Compose performance hygiene (zero-jank on weak Mali/PowerVR GPUs), strictly D-pad-first navigation, and native Android TV materiality. All changes and command executions must be sandboxed strictly inside this project directory.

1. Environment Isolation (Gradle & JDK)
JDK & Build System:

NEVER execute global installations or rely on system-wide Gradle.

Always use the local wrapper: ./gradlew (Mac/Linux) or gradlew.bat (Windows).

Strictly enforce JDK 17 and Gradle 8.9 (agp 8.5.2).

Do NOT modify global ~/.gradle/gradle.properties — all project-specific properties must remain within the repository.

Dependency Management:

Respect the existing BOM (Compose 2024.09.03).

NEVER hardcode version numbers scattered across build.gradle.kts files if a version catalog (libs.versions.toml) or central versioning mechanism is in use.

2. Build Hygiene & Artifact Management (Strict Anti-Bloat)
Build Output Paths:

NEVER invent ad-hoc build folders (e.g., apk_out/, temp_build/).

All artifacts MUST be generated in standard build/ directories.

Zero Git Pollution:

NEVER stage or commit compiled artifacts (.apk, .aab, classes.dex, .jar), IDE configuration folders (.idea/), or build caches (.gradle/).

Do NOT commit local.properties under any circumstances.

Clean Before Build:

Run ./gradlew clean when necessary to avoid caching anomalies during critical build phases, instead of manually deleting folders.

3. Secrets & Security Boundaries
NEVER hardcode secrets, API keys, keystore passwords, or signature aliases in source code or build.gradle.kts.

Read all local configuration and secrets exclusively through local.properties or environment variables in CI/CD.

Before writing to local.properties, verify it is listed in .gitignore.

Prohibited from staging or committing Android Keystore files (*.jks, *.keystore).

4. Git & Destructive Commands
NEVER run destructive commands without explicit confirmation (git push --force, git reset --hard, git clean -f).

Do NOT delete core source directories (e.g., app/src/main/).

Verify .gitignore before creating temporary logs, heap dumps (*.hprof), or test artifacts.

5. Minimalist Dependency Philosophy
Do NOT install heavy third-party packages for tasks achievable with standard Android/Kotlin APIs.

Specifically: Since the app relies on HttpURLConnection and standard JSON for the GitHub Releases API, NEVER introduce bloated libraries like Retrofit, OkHttp, or GSON/Moshi unless explicitly requested.

Rely on Kotlin Coroutines (kotlinx.coroutines) for async work. NEVER introduce RxJava.

Prefer standard androidx libraries over 3rd-party equivalents.

6. Android TV UI/UX & D-Pad Materiality (10-Foot UI)
D-Pad First Navigation (Crucial):

NEVER build UI elements that rely on touch events (pointerInput without focus fallback, clickable without visual focus state).

EVERY interactive element must properly implement Modifier.focusable().

Handle D-Pad interactions strictly via onKeyEvent (D-Pad Center, Back, Up/Down/Left/Right).

Focus Management & Visual States:

The focused state is the only way users interact with the app. Implement highly visible, tactile focus states using Compose animations (e.g., scaling up the focused card to 1.1f, highlighting borders, elevating shadows).

Use spring physics (animateFloatAsState, updateTransition) for focus shifts. NEVER use linear or snappy animations for XMB (Cross Media Bar) layout movements.

Overscan & Safe Margins:

Enforce standard Android TV overscan safe zones (typically 5% margin from screen edges). Do NOT place interactive elements flush against the physical screen edge.

7. Compose Performance Hygiene & Low-End GPU Constraints
Anti-Recomposition Strictness:

TV processors (e.g., in projectors like XGIMI Play 6) have extremely weak CPUs/GPUs. You MUST minimize recompositions.

NEVER instantiate objects (e.g., Paint(), Path(), lists, formatters) inside onDraw or Canvas.

Strictly use remember for any derived computations.

Use derivedStateOf for states dependent on scroll position to avoid spamming recompositions on every frame.

Enforce @Stable and @Immutable on data classes used in Compose.

AGSL / RuntimeShader (Android 13+):

When writing Custom Shaders for the XMB background, avoid complex math (pow, sin/cos loops, high-frequency branching) in the fragment shader.

Pre-calculate coordinates in Compose and pass them as uniform variables.

Image Handling:

Strictly cache bitmaps and use AndroidX Palette efficiently (on background threads) to avoid blocking the Main thread.

8. Low-Level Integrations & TV Services
App Updates & Sideloading:

Ensure APK downloads and SHA256 verification via GitHub Releases happen robustly on background Dispatchers (Dispatchers.IO).

Do NOT leak FileProvider URIs; handle Intent.ACTION_INSTALL_PACKAGE or PackageInstaller securely for Android 8.0+.

System Providers & Integrations:

When interfacing with TvContract (Watch Next, Channels), HDMI/USB states, or NotificationListenerService, ensure proper try-catch handling for custom OEM Android forks (like XGIMI OS) that may have broken or missing content providers.

Gracefully degrade features if a system service is missing.

9. Code Integrity & Non-Destructive Editing
NEVER truncate existing code with lazy placeholders (e.g., // TODO: implement later, // ... rest of code).

Implement edits atomically.

Respect Kotlin style guidelines, trailing commas, and explicit typing where it enhances readability.

10. Production Builds & R8 (ProGuard)
Code Shrinking & Obfuscation:

Release builds (minifyEnabled true) must execute correctly.

Any usage of reflection, JSON serialization (if using reflection-based parsers), or JNI must be strictly covered by custom ProGuard/R8 rules in proguard-rules.pro.

Do NOT randomly add -dontwarn or -keep class ** wildcards without understanding the underlying dependency crash.

11. Testing & CI/CD Discipline
No Live Networking in Tests:

Unit tests (JUnit 4.13.2) must strictly mock GitHub API calls and JSON responses.

Do NOT write tests that require a connected TV device unless explicitly marked as @Instrumented.

Execution Logging (The Runbook):

NEVER dump extensive Gradle build logs, test outputs, or stack traces directly into the chat/console response.

ALL information regarding build commands, test executions, and their outcomes MUST be appended to a dedicated local file: docs/AGENT_EXECUTION_LOG.md.

Log Format: Timestamp | Exact Command | Brief Summary (e.g., "Tests passed", "Dexing failed") | Critical Error Lines (if applicable).