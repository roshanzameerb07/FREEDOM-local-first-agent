# Contributing to FREEDOM

## Development rules

FREEDOM is a local-first Android framework. Contributions should preserve the core security boundary:

Model output
→ FreedomQuery
→ Validation
→ Authorization
→ Deterministic execution
→ Room / SQLite

Do not introduce:
- raw model-generated SQL execution
- model-controlled organization or user identity
- model-controlled authorization
- operational truth stored only in model prompts or model state
- cloud inference as an undocumented hard dependency

## Pull requests

Before opening a pull request:
1. Explain the architectural or product change.
2. Add or update tests for behavior that changed.
3. Run unit tests and Android Lint.
4. Run an Android build.
5. Update README/documentation when installation, architecture, limitations, or user-visible behavior changes.

Recommended local commands:

    ./gradlew testDebugUnitTest
    ./gradlew lintDebug
    ./gradlew assembleDebug

On Windows:

    gradlew.bat testDebugUnitTest
    gradlew.bat lintDebug
    gradlew.bat assembleDebug

## Model artifacts

Do not commit large .litertlm model binaries. The repository intentionally excludes them from Git.
