# BleX Architecture Decision Records (ADRs)

**Document Version:** 1.0  
**Last Updated:** 16th of April 2026  
**Status:** Living Document  
**Classification:** Internal

---

## What are ADRs?

Architecture Decision Records document significant architectural decisions made during the BleX project lifecycle. Each ADR captures the context, decision, and consequences of a choice.

---

## ADR-001: Embedded MQTT Broker on Android

**Status:** Accepted  
**Date:** 2024-Q2  
**Deciders:** System Architecture Team

### Context
Traditional IoT systems require external MQTT brokers (Mosquitto, HiveMQ, AWS IoT Core). This adds infrastructure cost, deployment complexity, and internet dependency.

### Decision
Embed Moquette MQTT broker directly inside the Android application, running as part of the foreground service.

### Rationale
- **Self-Contained**: No external dependencies for basic operation
- **Offline Capability**: Works in airgapped environments
- **Lower Latency**: No cloud round-trip (<10ms vs. 200-500ms)
- **Cost Savings**: No broker hosting fees
- **Privacy**: Data stays on-premises by default

### Consequences
**Positive:**
- Simplified deployment (single APK)
- Works without internet
- Reduced operational costs
- Better data sovereignty

**Negative:**
- Tablet becomes single point of failure
- Limited to ~50 concurrent scanner connections
- Requires tablet to stay powered
- More complex Android app

**Mitigation:**
- Implement watchdog for auto-restart
- Support multiple tablets per site (future)
- Battery optimization recommendations

### Alternatives Considered
1. **External Mosquitto on Server**: Rejected due to infrastructure requirement
2. **Cloud MQTT (AWS IoT Core)**: Rejected due to internet dependency and cost
3. **HiveMQ Embedded**: Rejected due to larger footprint (~10MB vs. 2MB)

---

## ADR-002: Zone-Based Tracking (Not GPS/Trilateration)

**Status:** Accepted  
**Date:** 2024-Q1  
**Deciders:** Product & Engineering Teams

### Context
Asset tracking can be implemented via:
1. GPS (outdoor only, power-hungry)
2. BLE Trilateration (3+ scanners, complex math, unreliable indoors)
3. Zone-based (strongest RSSI = current zone)

### Decision
Use zone-based tracking where each scanner is assigned to a logical zone. The zone with the strongest RSSI wins.

### Rationale
- **Indoor Friendly**: GPS doesn't work indoors
- **Simple Algorithm**: No complex trilateration calculations
- **Reliable**: RSSI comparison is more stable than distance estimation
- **Privacy**: Room-level accuracy (not precise XY coordinates)
- **Scalable**: Easy to add zones

### Consequences
**Positive:**
- Works indoors reliably
- Simple to implement and debug
- Low computational overhead
- Privacy-friendly (GDPR compliant)

**Negative:**
- Room-level accuracy only (not sub-meter)
- Requires at least 1 scanner per zone
- RSSI can fluctuate (mitigated by dwell time filter)

**Trade-offs Accepted:**
- "In Zone A" vs. "At coordinates (X, Y)" is sufficient for 90% of use cases
- Customers can add more scanners for finer granularity

### Alternatives Considered
1. **GPS**: Rejected (doesn't work indoors)
2. **BLE Trilateration**: Rejected (unreliable, requires 3+ scanners per area)
3. **UWB (Ultra-Wideband)**: Deferred to future (expensive hardware, Android 12+ only)

---

## ADR-003: Foreground Service Architecture

**Status:** Accepted  
**Date:** 2024-Q1  
**Deciders:** Android Team

### Context
Android aggressively kills background services to save battery. BleX needs to run 24/7 for continuous tracking.

### Decision
Run all core components (BLE scanner, MQTT broker, bridge) in a single foreground service with persistent notification.

### Rationale
- **Process Priority**: Foreground services are protected from being killed
- **Auto-Restart**: System restarts service on crash (START_STICKY)
- **User Visibility**: Notification shows system is running (builds trust)
- **Battery Optimization Bypass**: Recommended to users for 24/7 operation

### Consequences
**Positive:**
- Reliable 24/7 operation
- Survives low memory conditions
- Clear to users that app is active

**Negative:**
- Persistent notification cannot be hidden (Android requirement)
- Uses more battery than background service
- Notification occupies status bar space

**Mitigation:**
- Make notification informative (scan count, MQTT status)
- Allow users to minimize notification importance (still visible)
- Recommend keeping tablet plugged in

### Alternatives Considered
1. **Background Service**: Rejected (killed by Android Doze mode)
2. **WorkManager**: Rejected (not suitable for continuous operation)
3. **Separate App + System Service**: Rejected (requires root/system permissions)

---

## ADR-004: Kotlin + Jetpack Compose (Not Java + XML Views)

**Status:** Accepted  
**Date:** 2024-Q1  
**Deciders:** Android Team

### Context
Android UI can be built with:
1. Java + XML Views (legacy)
2. Kotlin + XML Views (hybrid)
3. Kotlin + Jetpack Compose (modern)

### Decision
Use Kotlin as primary language with Jetpack Compose for UI, Material 3 design system.

### Rationale
- **Modern Stack**: Compose is Google's recommended approach
- **Less Boilerplate**: 40-50% less code vs. XML Views
- **Type Safety**: Compile-time null safety
- **Coroutines**: First-class async support
- **Reactive**: StateFlow/MutableState for real-time UI updates
- **Material 3**: Latest design system with dynamic theming

### Consequences
**Positive:**
- Faster development (less boilerplate)
- Better maintainability
- Automatic UI updates via State
- Modern developer experience

**Negative:**
- Steeper learning curve for XML developers
- Slightly larger APK (~2MB for Compose runtime)
- Requires Android 12+ (API 31) minimum

**Trade-offs Accepted:**
- Targeting Android 12+ is acceptable (95%+ of enterprise tablets)
- 2MB APK increase is negligible on modern devices

### Alternatives Considered
1. **XML Views**: Rejected (legacy, verbose)
2. **React Native**: Rejected (adds JavaScript layer, larger bundle)
3. **Flutter**: Rejected (different tech stack, not native)

---

## ADR-005: FastAPI + PostgreSQL (Backend)

**Status:** Accepted  
**Date:** 2024-Q1  
**Deciders:** Backend Team

### Context
Backend API can be built with various stacks:
- Python: Flask, Django, FastAPI
- Node.js: Express, NestJS
- Java: Spring Boot
- Go: Gin, Echo

### Decision
Use FastAPI (Python) with PostgreSQL database and SQLAlchemy ORM.

### Rationale
**FastAPI:**
- Async by default (handles 1000s of concurrent connections)
- Automatic OpenAPI documentation
- Pydantic validation (type-safe)
- Fast development (Python simplicity)
- Modern Python 3.12+ features

**PostgreSQL:**
- ACID transactions (data integrity)
- Complex queries (JOINs, aggregations)
- Time-series optimization
- Proven reliability
- Open source

### Consequences
**Positive:**
- High performance (async)
- Self-documenting API (/docs endpoint)
- Type safety via Pydantic
- Rapid prototyping
- Strong ecosystem

**Negative:**
- Python GIL (mitigated by async I/O)
- Less performant than Go/Rust (acceptable for our scale)
- Database migrations require Alembic

**Performance:**
- Target: 1000 req/sec per instance
- Actual: 500-800 req/sec (sufficient)
- Scalability: Horizontal (add more instances)

### Alternatives Considered
1. **Django**: Rejected (heavier, more opinionated)
2. **Node.js/Express**: Rejected (prefer Python for ML integrations)
3. **Go**: Rejected (team expertise in Python)
4. **MongoDB**: Rejected (prefer relational for complex queries)

---

## ADR-006: Auto-Provisioning via UDP + HTTP

**Status:** Accepted  
**Date:** 2024-Q2  
**Deciders:** IoT Team

### Context
Manually configuring 50+ scanners with WiFi credentials and MQTT broker IP is time-consuming and error-prone.

### Decision
Implement auto-provisioning where:
1. Scanner boots → connects to "setup" WiFi
2. Broadcasts UDP heartbeat with MAC address
3. Tablet detects scanner → shows in UI
4. User taps "Provision" → sends WiFi + MQTT config via HTTP POST
5. Scanner reboots → connects to site WiFi

### Rationale
- **Zero-Touch**: No SSH or terminal access needed
- **User-Friendly**: Simple UI workflow
- **Scalable**: Provision 100 scanners in bulk
- **Fast**: 2 minutes per scanner

### Consequences
**Positive:**
- 90% reduction in deployment time
- No technical knowledge required
- Works with firewalled networks

**Negative:**
- Insecure (HTTP on setup network)
- Requires temporary "setup" WiFi
- Scanner must be physically accessible

**Security Mitigation:**
- Setup WiFi is isolated (no internet)
- Short provisioning window (5 minutes)
- MAC address verification
- Manual user approval

### Alternatives Considered
1. **Bluetooth LE Provisioning**: Rejected (requires tablet to be in BLE range)
2. **NFC Tap-to-Provision**: Deferred (requires NFC-enabled scanners)
3. **QR Code Scanning**: Rejected (requires camera, awkward for bulk)
4. **Manual SSH**: Rejected (not user-friendly)

---

## ADR-007: MQTT QoS 1 (At-Least-Once)

**Status:** Accepted  
**Date:** 2024-Q1  
**Deciders:** Architecture Team

### Context
MQTT supports three Quality of Service levels:
- QoS 0: At most once (fire-and-forget)
- QoS 1: At least once (acknowledged)
- QoS 2: Exactly once (4-way handshake)

### Decision
Use QoS 1 for all beacon scan data and movement events.

### Rationale
- **Reliability**: Message acknowledged by broker
- **Performance**: Lower overhead than QoS 2
- **Acceptable Duplicates**: Deduplication handled at application layer

**Why Not QoS 0?**
- Too risky for asset tracking (data loss)

**Why Not QoS 2?**
- Overkill for beacon data (duplicates are acceptable)
- 4x overhead vs. QoS 1
- Slower (round-trip delays)

### Consequences
**Positive:**
- No data loss on network hiccups
- Good balance of reliability and performance

**Negative:**
- Possible duplicates (rare: <0.5%)
- Slightly higher bandwidth than QoS 0

**Duplicate Handling:**
- Application-level deduplication via timestamp + MAC cache
- Duplicates within 1 second are filtered out

### Alternatives Considered
1. **QoS 0**: Rejected (too risky)
2. **QoS 2**: Rejected (overkill)
3. **Mixed QoS**: Rejected (complexity not justified)

---

## ADR-008: Single Async MQTT Client (Not Recreate)

**Status:** Accepted  
**Date:** 2024-Q3  
**Deciders:** Android Team

### Context
Early versions recreated `MqttAsyncClient` on every disconnect, causing reconnection storms and battery drain.

### Decision
Reuse a single `MqttAsyncClient` instance throughout app lifecycle. Rely on Paho's built-in auto-reconnect.

### Rationale
- **Prevent Reconnection Storms**: Creating new clients causes rapid connect attempts
- **Resource Efficiency**: Reusing connections saves memory/CPU
- **Built-in Retry**: Paho has exponential backoff

**Implementation:**
- `AtomicBoolean isConnecting` guard prevents concurrent connects
- Only create new client if null or permanently failed
- Trust Paho's `automaticReconnect = true`

### Consequences
**Positive:**
- No more reconnection storms
- Better battery life
- More stable connections

**Negative:**
- Slightly more complex connection logic
- Must handle edge cases (null client)

**Metrics After Fix:**
- Reconnection attempts: 50/hour → 2/hour
- Battery drain: 15%/hour → 8%/hour

### Alternatives Considered
1. **Recreate on Every Disconnect**: Rejected (causes storms)
2. **Manual Reconnect Loop**: Rejected (duplicates Paho logic)
3. **AlarmManager for Periodic Reconnect**: Rejected (not needed with auto-reconnect)

---

## ADR-009: Beacon Whitelist (Not Blacklist)

**Status:** Accepted  
**Date:** 2024-Q2  
**Deciders:** Product Team

### Context
BLE scanners detect all nearby beacons, including:
- Registered assets (should track)
- Employee phones (should NOT track)
- Other IoT devices (irrelevant)

### Decision
Implement whitelist approach: only process beacons registered in `mst_asset` table. Unknown MACs are ignored.

### Rationale
- **Privacy**: Don't track personal devices
- **Performance**: Filters out 90% of irrelevant data
- **Intentional Tracking**: Explicit registration required
- **GDPR Friendly**: Only track devices with consent

### Consequences
**Positive:**
- 90% reduction in processed data
- Clear privacy compliance
- Reduced bandwidth/storage

**Negative:**
- Assets must be registered before tracking starts
- Manual registration step required

**User Workflow:**
1. Purchase BLE beacons
2. Attach to assets
3. Register MAC addresses in BleX app
4. System starts tracking automatically

### Alternatives Considered
1. **Blacklist**: Rejected (impossible to list all personal devices)
2. **Track Everything**: Rejected (privacy concerns, wasted resources)
3. **UUID-Based Filtering**: Considered (still requires registration)

---

## ADR-010: Dwell Time Filter (10 Seconds)

**Status:** Accepted  
**Date:** 2024-Q2  
**Deciders:** Algorithm Team

### Context
RSSI fluctuates due to multipath interference, causing false zone transitions.

### Decision
Only emit movement event if beacon stays in new zone for 10+ seconds (configurable).

### Rationale
- **Reduce False Positives**: Beacon passing through zone boundary
- **Stable Tracking**: Only record meaningful movements
- **Configurable**: Users can adjust based on their environment

**Algorithm:**
```
IF zone changed:
    IF dwell_time > threshold:
        EMIT movement_event
    ELSE:
        IGNORE (transient signal)
```

### Consequences
**Positive:**
- 80% reduction in false movements
- More meaningful movement logs
- Better user experience

**Negative:**
- 10-second delay in detecting real movements
- Very fast movements might be missed

**Trade-offs Accepted:**
- 10-second latency is acceptable for asset tracking (not real-time navigation)
- Can be reduced to 5s for time-critical scenarios

### Alternatives Considered
1. **No Dwell Filter**: Rejected (too many false positives)
2. **Kalman Filter**: Implemented but optional (adds complexity)
3. **5-Second Threshold**: Rejected (still too many false positives)

---

## Future ADRs

Decisions pending or deferred:

- **ADR-011**: ML-Based Movement Prediction (Research phase)
- **ADR-012**: Multi-Tablet Mesh Architecture (Design phase)
- **ADR-013**: UWB Integration (Hardware evaluation)
- **ADR-014**: Certificate Pinning for API (Security review)
- **ADR-015**: WebSocket vs. REST for Real-Time Updates (TBD)

---

## ADR Template

Use this template for new ADRs:

```markdown
## ADR-XXX: Title

**Status:** [Proposed | Accepted | Deprecated | Superseded]  
**Date:** YYYY-MM-DD  
**Deciders:** [Team/Person]

### Context
[Describe the situation and why a decision is needed]

### Decision
[State the decision clearly]

### Rationale
[Explain why this decision was made]

### Consequences
**Positive:**
- [Benefit 1]
- [Benefit 2]

**Negative:**
- [Trade-off 1]
- [Trade-off 2]

### Alternatives Considered
1. **Option A**: [Why rejected]
2. **Option B**: [Why rejected]
```

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Apr 2026 | Akshat Jain | Initial 10 ADRs |

**Next Review Date**: May 2026  
**Living Document**: Update as new decisions are made

---

*This document is part of the BleX technical documentation suite.*