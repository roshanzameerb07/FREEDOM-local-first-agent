# Security Policy

## Supported version

The current hackathon branch is an Alpha / prototype release. Security hardening is still in progress.

## Reporting a vulnerability

Please do not publish exploitable security details in a public GitHub issue.

For a private report, use GitHub's repository security advisory/private vulnerability reporting mechanism when available to the repository.

Include:
- affected component or file
- reproduction steps
- expected and observed behavior
- impact assessment
- relevant logs or screenshots with secrets removed

Do not include passwords, private keys, model credentials, personal data, or other secrets in a report.

## Security design principles

FREEDOM treats model output as untrusted input. The model must not determine authenticated identity, organization scope, permissions, or raw SQL execution. Imported local models are copied into app-private storage and checked for SHA-256 integrity locally.
