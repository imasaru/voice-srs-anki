# Local security checks

Use gitleaks 8.30.1 and pre-commit from the repository root:

```sh
gitleaks git . --config=.gitleaks.toml --redact=100 --log-opts=public-clean
pre-commit run --all-files
```

The pre-commit secret hook checks staged changes; use the separate history scan too. Other hooks check private keys, merge markers, YAML and added files larger than 1 MiB. Hook installation is optional and is not performed by these commands. No secret baseline or blanket allowlist is used.

To audit debug runtime dependencies with OSV-Scanner 2.7.0:

```sh
./gradlew :app:dependencies --configuration debugRuntimeClasspath --write-locks --init-script tools/security/locks.init.gradle --no-daemon
test -s app/gradle.lockfile
osv-scanner scan source --lockfile=app/gradle.lockfile
```

The generated ignored lockfile is a runtime inventory, not canonical dependency pinning. It does not cover all build plugins, tests, release-only configurations or vendored code. The CI configuration checksum-verifies pinned scanner binaries and preserves scanner failures.

Keep local configuration, credentials, scan reports, generated artifacts and personal data out of version control. Review fixtures, documentation, source history, dependencies and redistribution rights before sharing code. A successful secret scan is not a privacy, license or complete security audit.
