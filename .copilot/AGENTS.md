# AGENTS.md — Fuel Queue Android

> Guidance for AI coding agents working on this codebase

## 🏗️ Architecture Overview

**Single-Activity Navigation Pattern** → `MainActivity` hosts a `NavHostFragment` that switches between 5 screens via Jetpack Navigation.

**Data Layer:** Retrofit client with OkHttp interceptor auto-injects JWT `Bearer` token from `SessionManager` into all requests. Base URL is `http://10.0.2.2:8080/` (emulator) or configurable for physical devices.

**Background Service:** `GpsTrackerService` (foreground) pings backend every 10 seconds with GPS coords. Geofence logic lives server-side (80m radius).

**Session State:** `SessionManager` (Kotlin singleton) wraps SharedPreferences for JWT token + user metadata. Initialized in `FuelQueueApplication.onCreate()`.

---

## 🔑 Key File Reference

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Activity host + GPS permission/service control |
| `data/api/RetrofitClient.kt` | Singleton Retrofit builder w/ JWT interceptor |
| `data/api/ApiService.kt` | Retrofit interface (auth, stations, GPS ping endpoints) |
| `data/model/Models.kt` | Data classes for API contracts |
| `utils/SessionManager.kt` | SharedPreferences wrapper for token + user info |
| `utils/CrowdUtils.kt` | When adding crowd-related UI → use these color/emoji/label helpers |
| `service/GpsTrackerService.kt` | Background GPS tracking (lifecycle: onCreate→startForeground→locationCallback loop) |
| `ui/login/LoginFragment.kt` | Auth pattern example (lifecycleScope + error handling) |
| `ui/detail/StationDetailFragment.kt` | Auto-refresh pattern (15s loop with job cancellation on pause) |

---

## 🛠️ Developer Workflows

### Build + Run
```bash
# Sync Gradle (do this after dependency changes)
./gradlew clean build

# Run on emulator (must have Android Virtual Device running)
./gradlew installDebug

# Real backend required: Spring app must be running on localhost:8080
```

### Emulator Network → Host Localhost
- Emulator backend URL: `http://10.0.2.2:8080/` (hardcoded in `build.gradle` → `buildConfigField BASE_URL`)
- Physical device: edit `build.gradle` `manifestPlaceholders` to your LAN IP (e.g., `http://192.168.1.5:8080/`)

### GPS Simulator (Emulator)
- Android Studio Extended Controls → **Location** tab → enter manually or load GPX file

### Google Maps API Key
- Required for map screen only. Add to `build.gradle`:
  ```groovy
  manifestPlaceholders = [MAPS_API_KEY: "AIzaSy..."]
  ```

---

## 🎯 Code Patterns — Use These

### Fragment + ViewBinding
```kotlin
class MyFragment : Fragment() {
    private var _binding: FragmentMyBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(...): View {
        _binding = FragmentMyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
```
**Why:** Null-safe, no findViewById, automatic cleanup.

### API Call with Coroutines
```kotlin
lifecycleScope.launch {
    try {
        val response = RetrofitClient.api.someEndpoint(params)
        if (response.isSuccessful && response.body() != null) {
            updateUI(response.body()!!)
        } else {
            Toast.makeText(requireContext(), "Error", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
```
**Key:** Always use `lifecycleScope.launch` (cancels on destroy), check both `isSuccessful` + `body() != null`.

### Auto-Refresh Pattern (Detail Screen)
```kotlin
private var refreshJob: Job? = null

override fun onResume() {
    super.onResume()
    refreshJob = lifecycleScope.launch {
        while (isActive) {
            delay(15_000)  // 15 seconds
            loadData()
        }
    }
}

override fun onPause() {
    refreshJob?.cancel()
    super.onPause()
}
```
**Key:** Cancel on pause, check `isActive` inside loop.

### Station Crowd Display
```kotlin
// Always use CrowdUtils for consistency
binding.tvCrowdLabel.text = CrowdUtils.getLabel(crowdLevel)
binding.tvCrowdLabel.setTextColor(CrowdUtils.getColor(crowdLevel))
binding.tvCrowdEmoji.text = CrowdUtils.getEmoji(crowdLevel)
binding.tvAdvice.text = CrowdUtils.getAdvice(crowdLevel)
```
**Location:** `utils/CrowdUtils.kt` defines colors (green/yellow/red), emojis, labels, advice. Don't hardcode.

---

## 🔗 Integration Points

### Authentication Flow
1. User enters email + password → `LoginFragment.doLogin()`
2. `RetrofitClient.api.login(LoginRequest)` → backend returns JWT
3. `SessionManager.saveSession(token, userId, name, email)` stores in SharedPreferences
4. All future requests auto-include `Authorization: Bearer {token}` via OkHttp interceptor

### GPS Tracking Flow
1. After login, `MainActivity.requestLocationAndStartTracking()` checks permission + starts `GpsTrackerService`
2. Service runs `FusedLocationProviderClient.requestLocationUpdates()` every 10 seconds
3. Each location triggers `LocationPing` POST to `/api/gps/ping`
4. Backend checks if user inside any station's 80m geofence, updates crowd level
5. Response includes `CrowdStatus` (optional) if at a station

### Data Refresh
- **Map Screen:** Polls `/api/stations` once on load, should cache; GPS ping responses include crowd updates
- **List Screen:** Queries `/api/stations` or `/api/stations/nearby` (with user's lat/lng)
- **Detail Screen:** Auto-refreshes `/api/stations/{id}/crowd` every 15 seconds (see `StationDetailFragment`)

---

## ⚠️ Critical Patterns & Gotchas

### Permission Checks (Before GPS)
```kotlin
val hasPermission = ContextCompat.checkSelfPermission(
    context, Manifest.permission.ACCESS_FINE_LOCATION
) == PackageManager.PERMISSION_GRANTED
```
**Gotcha:** Missing checks → service crashes. Always guard location operations.

### Navigation Routes in `nav_graph.xml`
- Login/Register fragments auto-hide bottom nav (see `MainActivity` destination listener)
- Use `findNavController().navigate(R.id.action_name_to_name)` to route between screens
- Pass arguments via Bundle or safe args plugin

### Session Check on App Open
- `LoginFragment.onViewCreated()` checks `SessionManager.isLoggedIn()` → skip to map if already authenticated
- Prevents re-login loop after rotation

### Coroutine Cleanup
- Always use `lifecycleScope` (not global scope) so jobs cancel on Fragment destroy
- GPS service uses `scope = CoroutineScope(Dispatchers.IO + SupervisorJob())` + cancel in `onDestroy()`

### Retrofit Base URL (Must Match Backend)
- Emulator: `http://10.0.2.2:8080/`
- Physical: `http://[YOUR_LAN_IP]:8080/`
- Change in `build.gradle` `buildConfigField`, not in code

---

## 📊 Data Models

- **Station:** Full station info + distance + crowd level (list/map display)
- **CrowdStatus:** Live crowd + user count + wait estimate (detail screen refresh)
- **LocationPing:** User GPS coords + speed → sent every 10s to `/api/gps/ping`
- **LoginResponse:** Contains JWT token + userId + name (store in SessionManager immediately)

---

## 🚨 Common Tasks for AI Agents

| Task | Approach |
|------|----------|
| Add new station field | Update `Models.kt` data class + API endpoint |
| Add new screen | Add fragment + layout XML + route in `nav_graph.xml` + menu item in `bottom_nav_menu.xml` |
| Update crowd display | Modify `CrowdUtils.kt` (colors/labels/advice) + UI binding calls |
| Add API endpoint | Add method to `ApiService` interface + call via `RetrofitClient.api` + handle in `lifecycleScope.launch` |
| Background sync | Extend `GpsTrackerService` or create new service + permission checks + foreground notification |

---

## 🔍 Build System Notes

- **Gradle:** `build.gradle` configurable (API key, BASE_URL). Always run `./gradlew clean build` after changes
- **Java 17 / Kotlin 1.9.22** required
- **AndroidX + Jetpack** (Navigation, LiveData, Lifecycle, Coroutines)
- **Data Binding:** Enabled in `buildFeatures`, use `binding.view` not `findViewById()`

