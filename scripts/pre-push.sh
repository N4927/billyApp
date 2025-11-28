#!/bin/sh

# ==============================================================================
#  CORPORATE QUALITY GATE - PRE-PUSH HOOK
# ==============================================================================
#  "If it hurts, do it more often." - Jez Humble
#
#  This hook acts as the first line of defense against broken builds.
#  It enforces strict quality standards BEFORE code leaves your machine.
#
#  CHECKS PERFORMED:
#  1. Static Analysis (Linting via Spotless/KtLint)
#  2. Unit Tests (Debug variant)
#
#  BYPASS:
#  In emergency situations (e.g., hotfix), use: `git push --no-verify`
# ==============================================================================

echo "\n🚀 STARTING PRE-PUSH QUALITY GATE..."
echo "========================================"

# 1. LINT CHECK (Spotless)
# We run spotlessCheck to verify formatting without modifying files.
# If this fails, run `./gradlew spotlessApply` to fix it automatically.
echo "\n🔍 [1/2] Running Static Analysis (Spotless)..."
./gradlew spotlessCheck --daemon

if [ $? -ne 0 ]; then
    echo "\n❌ LINT CHECK FAILED!"
    echo "   Run './gradlew spotlessApply' to fix formatting issues automatically."
    echo "========================================"
    exit 1
fi

# 2. UNIT TESTS
# We run the shared module tests. This is the core logic.
# Running 'testDebugUnitTest' covers the Android/JVM side of KMP.
echo "\n🧪 [2/2] Running Unit Tests (Shared Module)..."
./gradlew :shared:testDebugUnitTest --daemon

if [ $? -ne 0 ]; then
    echo "\n❌ UNIT TESTS FAILED!"
    echo "   Please fix the failing tests before pushing."
    echo "========================================"
    exit 1
fi

echo "\n✅ QUALITY GATE PASSED. PUSHING CODE..."
echo "========================================"
exit 0
