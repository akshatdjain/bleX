# BleX Security Remediation Log (BleX-1-1 / SIGP-2241)

## Committed secrets removed from HEAD
| Secret | Where it was | Replacement |
|--------|-------------|-------------|
| Postgres password `LYzxeJ2xrSKfzM2f` | docker-compose / database.py | env var, no fallback (done in prior commit) |
| MQTT password `1234` | Pi config.py, scanner_boot.py, sage.py, master/docker-compose.yml | `/etc/blex/blex.env` (Pi), `${MQTT_PASSWORD}` (docker) |
| MQTT username `tab` | same as above | `${MQTT_USERNAME}` / env |
| Redis password `1234` | Pi & master config.py | `REDIS_PASSWORD` env, no fallback |
| JWT secret `blex-dev-secret-change-in-prod-please` | api/auth.py | `SECRET_KEY` env, fail-fast |
| Android keystore `blex1234` | android/app/build.gradle.kts | gitignored `android/keystore.properties` |
| Hotspot PSK `setup@1234` | android HotspotTab.kt | BuildConfig / settings |

## Rotation status
- [ ] New values generated and stored in AWS Secrets Manager `blex/dev/config` (Manendra)
- [ ] Pi `/etc/blex/blex.env` updated with rotated values
- [ ] Cloud API `.env` updated with rotated SECRET_KEY + DB password
- [ ] Android keystore.properties distributed to signing machine

## Git history scrub decision
**Decision: `git filter-repo`** to remove the secret strings from all history.
Rationale: commit history (SAGE, watchdog, security fixes) has high value; the repo is small
enough that filter-repo is fast and surgical. A fresh remote would discard useful history.

### Execution (coordinated with Manendra — DevOps)
1. All developers push/stash outstanding work.
2. `git filter-repo --replace-text replacements.txt` where replacements.txt maps each secret string to `***REMOVED***`.
3. Force-push all branches.
4. Every developer re-clones (old clones are poisoned).
5. Open PRs are recreated/rebased.

## Prevention
- gitleaks pre-commit hooks (tracked separately in BleX-1-8 / SIGP-2248).
