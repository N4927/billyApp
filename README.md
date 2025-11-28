# BillySDK (SpotMi KMM)

![Build Status](https://img.shields.io/github/actions/workflow/status/billyapp/new_kmm/quality_gate.yml?branch=main&style=flat-square&label=Quality%20Gate)
![Coverage](https://raw.githubusercontent.com/billyapp/new_kmm/badges/coverage.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-7f52ff?logo=kotlin&style=flat-square)
![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-lightgrey?style=flat-square)
![License](https://img.shields.io/badge/License-Proprietary-red?style=flat-square)

---

## 📖 Table of Contents

1.  [Executive Summary](#-executive-summary)
2.  [System Architecture](#-system-architecture)
3.  [Technology Stack](#-technology-stack)
4.  [Getting Started](#-getting-started)
5.  [Integration Guide](#-integration-guide)
6.  [Development Standards](#-development-standards)
7.  [Command Cheat Sheet](#-command-cheat-sheet)
8.  [Security & Compliance](#-security--compliance)
9.  [Project Structure](#-project-structure)
10. [Support & Contact](#-support--contact)

---

## 📋 Executive Summary

**BillySDK** represents the core intellectual property of the Billy application ecosystem. It is a high-performance, cross-platform software development kit (SDK) engineered to unify the **Proximity** and **Identity** business logic across mobile platforms.

By leveraging **Kotlin Multiplatform (KMP)**, we have consolidated critical logic into a single, robust, and testable artifact. This strategic decision reduces time-to-market for new features, eliminates logic fragmentation between iOS and Android teams, and ensures a consistent user experience.

### Key Capabilities
*   **Proximity Engine**: Manages BLE identifier ingestion, queuing, and batch processing.
*   **Identity Management**: Handles secure user authentication, token lifecycle, and session persistence.
*   **Offline Synchronization**: Robust data syncing capabilities with conflict resolution.
*   **Security Core**: Centralized cryptographic operations and secure storage abstraction.

---

## 🏗️ System Architecture

The SDK is architected as a **Modular Monolith** following **Clean Architecture** and **Domain-Driven Design (DDD)** principles. This ensures that business rules are isolated from external frameworks, making the system resilient to change.

### High-Level Design

```mermaid
graph TD
    subgraph "Mobile Clients"
        iOS[iOS Application]
        Android[Android Application]
    end

    subgraph "BillySDK (Shared Core)"
        Facade[Public API Facade]
        
        subgraph "Domain Layer (Business Rules)"
            Entities[Core Entities]
            UseCases[Interactors]
            Ports[Interface Adapters]
        end
        
        subgraph "Infrastructure Layer"
            Net[Networking (Ktor)]
            DB[Persistence (SQLDelight)]
            Sec[Security (Keychain/Keystore)]
        end
    end

    iOS --> Facade
    Android --> Facade
    Facade --> UseCases
    UseCases --> Entities
    UseCases --> Ports
    Net -.->|Implements| Ports
    DB -.->|Implements| Ports
    Sec -.->|Implements| Ports
```

### Design Decisions

1.  **Single Source of Truth**: All business logic resides in the `commonMain` source set. Platform-specific code is strictly limited to hardware interfaces (Bluetooth, Crypto, Storage).
2.  **Fail-Fast Initialization**: The SDK enforces strict initialization patterns. It will throw runtime exceptions if dependencies (like Keychain wrappers) are not correctly injected at startup, preventing undefined states.
3.  **Interface-Based Dependency Injection**: We use the "Ports and Adapters" pattern. The Core defines *what* it needs (e.g., `TokenStorage`), and the Platform provides *how* to do it.

---

## 🛠️ Technology Stack

We utilize a modern, stable, and enterprise-ready stack.

| Category | Technology | Version | Justification |
| :--- | :--- | :--- | :--- |
| **Language** | Kotlin | 2.0.0 | Latest K2 Compiler for performance and stability. |
| **Network** | Ktor Client | 2.3.12 | Asynchronous, multiplatform HTTP client. |
| **Database** | SQLDelight | 2.0.2 | Type-safe SQL generation; verifies queries at compile time. |
| **Concurrency** | Coroutines | 1.8.1 | Efficient background processing without callback hell. |
| **Serialization** | Kotlinx | 1.7.0 | High-performance JSON parsing. |
| **Testing** | Kotlin Test | - | Unified testing framework for all platforms. |

---

## 🚀 Getting Started

Follow these steps to set up your development environment.

### 1. Prerequisites
*   **JDK 17**: Required for the Gradle Daemon. We recommend [Eclipse Temurin](https://adoptium.net/).
*   **Android Studio**: Koala (2024.1.1) or newer.
*   **Xcode**: Version 15.0+ (Required for iOS compilation).
*   **CocoaPods**: (Optional) If integrating via Pods.

### 2. Installation
Clone the repository and initialize the project:

```bash
git clone https://github.com/billyapp/new_kmm.git
cd new_kmm
./gradlew clean build
```

### 3. IDE Configuration
*   **Android Studio**: Open the root folder. Wait for Gradle Sync to complete. Select `shared` configuration to run tests.
*   **Xcode**: Open the `iosApp` folder (if present) or generate the XCFramework manually via `./gradlew :shared:assembleBillySDKXCFramework`.

### 4. Local Quality Gate (Git Hooks)
We enforce strict quality standards locally. The pre-push hook is **automatically installed** when you:
*   Sync the project in Android Studio.
*   Run `./gradlew build`.
*   Run `./gradlew clean`.

**Zero Configuration Required**: Just clone and build. The environment self-heals.

*The hook runs linting and tests automatically before every push.*

---

## 🔌 Integration Guide

### Android Integration
The SDK is delivered as a standard Android Archive (AAR).

1.  **Add Dependency**:
    In your app-level `build.gradle.kts`:
    ```kotlin
    implementation(project(":shared"))
    ```

2.  **Usage**:
    The SDK components are available immediately. Ensure you pass the Application Context to the SQL Driver if modifying the database initialization logic.

### iOS Integration
The SDK is delivered as a static XCFramework.

1.  **Build Framework**:
    Run the following command to generate the artifact:
    ```bash
    ./gradlew :shared:assembleBillySDKXCFramework
    ```
    Output location: `shared/build/XCFrameworks/release/BillySDK.xcframework`

2.  **Embed in Xcode**:
    Drag and drop the `.xcframework` into your Xcode project's "Frameworks, Libraries, and Embedded Content" section.

3.  **Initialization (Mandatory)**:
    You must inject the platform-specific Keychain implementation at app launch.

    ```swift
    import BillySDK

    @main
    struct BillyApp: App {
        init() {
            // 1. Instantiate your Keychain wrapper
            let secureStorage = IOSKeychainStorage() 
            
            // 2. Initialize the SDK
            BillySDK.shared.initialize(storage: secureStorage)
        }
    }
    ```

---

## 📏 Development Standards

We enforce strict quality gates to maintain codebase integrity.

### Code Style
*   **Formatting**: We use **Spotless** with **KtLint**.
*   **Rule**: "Gold Standard" documentation. Every public class and method must have KDoc explaining *why* it exists.
*   **Check**: `./gradlew :shared:spotlessCheck`
*   **Fix**: `./gradlew :shared:spotlessApply`

### Testing Strategy
*   **Unit Tests**: Business logic must be tested in `commonTest`.
*   **Coverage**: We currently maintain **100% Line Coverage** on Domain logic (Target: >80%).
*   **Command**: `./gradlew :shared:testDebugUnitTest`

### Versioning
We follow **Semantic Versioning (SemVer)**:
*   `MAJOR`: Breaking API changes.
*   `MINOR`: New features (backward compatible).
*   `PATCH`: Bug fixes.

---

## 💻 Command Cheat Sheet

Reference for common Gradle tasks used in this project.

### Build & Verify
*   **Build All**: `./gradlew build`
    *   *Compiles everything and runs checks.*
*   **Clean**: `./gradlew clean`
    *   *Removes build artifacts.*
*   **Run Unit Tests**: `./gradlew :shared:test`
    *   *Executes tests in the shared module.*

### Code Quality
*   **Install Git Hooks**: `./gradlew installGitHooks`
    *   *Sets up the local pre-push quality gate.*
*   **Fix Code Style**: `./gradlew :shared:spotlessApply`
    *   *Auto-formats Kotlin code (imports, spacing).*
*   **Check Code Style**: `./gradlew :shared:spotlessCheck`
    *   *Fails if code style violations are found.*

### iOS Specific
*   **Generate XCFramework**: `./gradlew :shared:assembleBillySDKXCFramework`
    *   *Builds the framework for iOS integration.*

---

## 🔒 Security & Compliance

*   **Token Storage**: Access and Refresh tokens are never stored in plain text. The SDK delegates storage to the host app's secure enclave (EncryptedSharedPreferences on Android, Keychain on iOS).
*   **Network Security**: All traffic is encrypted via TLS 1.2+.
*   **Data Privacy**: No PII (Personally Identifiable Information) is persisted locally unless explicitly required for the "Resolved User" cache, which is ephemeral.

---

## 📂 Project Structure

A quick map to navigate the codebase:

```text
/new_kmm
├── build.gradle.kts          # Global build configuration
├── settings.gradle.kts       # Module inclusion
├── gradle/libs.versions.toml # Dependency Version Catalog
└── shared/                   # THE CORE SDK
    ├── build.gradle.kts      # SDK-specific build config
    └── src/
        ├── commonMain/       # 🧠 BRAIN: Shared Business Logic
        │   ├── core/         # Service Facades (BillyCore)
        │   ├── data/         # Implementation (Network, DB)
        │   ├── domain/       # Pure Business Rules (Models)
        │   └── cache/        # Database Schema
        ├── androidMain/      # 🤖 Android Drivers
        ├── iosMain/          # 🍎 iOS Drivers
        └── commonTest/       # 🧪 Unit Tests
```

---

## 📞 Support & Contact

For technical support, integration assistance, or to report security vulnerabilities:

*   **Technical Lead**: Andrea Zorzi
*   **Documentation**: [Internal Wiki Link]
*   **Issue Tracker**: [Jira/GitHub Issues Link]

---

**Copyright © 2025 Billy App Inc.**
*Confidential & Proprietary. All rights reserved.*