# Auto-Login & Location Request Guide

> How the Fuel Queue app handles automatic login and location permissions on app restart

---

## 🎯 Overview

The app now implements a **seamless auto-login flow** that:

1. **First-time users**: Show OTP login → request location → start GPS tracking → navigate to map
2. **Returning users**: Automatically skip login → navigate to map (if consent already given) → continue GPS tracking
3. **No redundant dialogs**: Location permission only asked once per app install (unless user revokes it)

---

## 🔄 App Flow Architecture

### Flow Diagram

```
App Launch
  ↓
MainActivity.onCreate()
  ├─ SessionManager.init()
  ├─ Set up Navigation
  └─ Setup Bottom Navigation
       ↓
   NavHost navigates to startDestination (LoginFragment)
       ↓
   LoginFragment.onViewCreated()
       ├─ Check: Is user logged in?
       │   ├─ YES → Request Location + Navigate to Map ✅
       │   └─ NO  → Show Login/Register UI
       │
       ├─ User chooses: Send OTP
       │   └─ Enter phone number (10 digits)
       │
       ├─ Backend responds with OTP
       │   └─ Show OTP input field
       │
       ├─ User enters OTP (6 digits)
       │   └─ Backend verifies → returns JWT + user info
       │
       ├─ SaveSession() + RequestLocation()
       │   └─ Check location permission
       │       ├─ Already granted → Start GPS immediately ✅
       │       └─ Not granted → Show permission dialog
       │
       └─ Once location handled → Navigate to Map
```

---

## 📱 Component Responsibilities

### 1. **MainActivity** (`ui/MainActivity.kt`)
- ✅ Initialize SessionManager on app start
- ✅ Set up navigation controller & bottom nav
- ✅ Provide `requestLocationAndStartTracking()` method for fragments
- ✅ Handle location permission responses (dialog dismissed → grant/deny)
- ❌ **No longer** auto-requests location in onCreate (LoginFragment handles it)

### 2. **LoginFragment** (`ui/login/LoginFragment.kt`)
- ✅ Detect if user already logged in on onViewCreated()
- ✅ If logged in → call `MainActivity.requestLocationAndStartTracking()`
- ✅ Wait 100ms for location setup to start
- ✅ Navigate to map (GPS already requested in background)
- ✅ If not logged in → show mobile number + OTP fields

**Key Code:**
```kotlin
if (SessionManager.isLoggedIn()) {
    // Request location in background (doesn't block navigation)
    (requireActivity() as MainActivity).requestLocationAndStartTracking()
    // Navigate after short delay to allow permission setup
    view.postDelayed({
        if (isAdded) {
            findNavController().navigate(R.id.action_login_to_map)
        }
    }, 100)
    return
}
```

### 3. **RegisterFragment** (`ui/login/RegisterFragment.kt`)
- ✅ Show name + mobile number input
- ✅ Send OTP to phone
- ✅ Verify OTP from user
- ✅ On success: SaveSession() + RequestLocation() + Navigate to Map
- ✅ **Only called on first-time registration**

### 4. **SessionManager** (`utils/SessionManager.kt`)
- ✅ Store: JWT token, userId, name, phone number
- ✅ Check: `isLoggedIn()` → token exists?
- ✅ Retrieve: Token for all API requests (via OkHttp interceptor)

---

## 🔐 Permission & Session Flow

### Scenario 1: First-Time User (Not Logged In)
```
1. App launches
2. LoginFragment shows UI
3. User enters phone → Send OTP
4. User enters 6-digit OTP → Verify
5. Backend returns JWT + user info
6. SessionManager.saveSession(token, userId, name, phone)
7. MainActivity.requestLocationAndStartTracking() called
   ├─ Check: Permission already granted?
   │   └─ NO → Show permission dialog to user
   ├─ User grants permission
   └─ Start GpsTrackerService
8. LoginFragment navigates to Map
9. User can see stations and crowd levels
```

### Scenario 2: Returning User (Already Logged In)
```
1. App launches
2. LoginFragment.onViewCreated() detects session
3. MainActivity.requestLocationAndStartTracking() called
   ├─ Check: Permission already granted?
   │   └─ YES → Start GpsTrackerService immediately ✅ NO DIALOG
   │   └─ NO → Show permission dialog (first time permission was revoked)
4. LoginFragment navigates to Map (after 100ms)
5. User sees map instantly, GPS tracking active
```

### Scenario 3: User Revoked Permission (Edge Case)
```
1. App launches
2. LoginFragment detects session
3. MainActivity.requestLocationAndStartTracking() called
   ├─ Permission was revoked (e.g., via Android settings)
   └─ Show permission dialog again
4. If granted → Start GPS
5. If denied → Show toast "Location needed for crowd levels"
6. Map still navigates (but crowd data might be limited)
```

---

## 🛡️ Permission Check Details

### In MainActivity.requestLocationAndStartTracking()
```kotlin
fun requestLocationAndStartTracking() {
    when {
        // Already have permission — start immediately (no dialog)
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED -> {
            startGpsTracking()  // ✅ No UI interaction
        }
        // Permission not granted — show dialog
        else -> {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))  // ✅ Android system dialog appears
        }
    }
}
```

**Why this approach:**
- `ContextCompat.checkSelfPermission()` doesn't trigger any dialog
- Only if permission is NOT granted, we use `ActivityResultContracts.RequestMultiplePermissions()` which shows the dialog
- This means returning users with granted permission get instant access (no dialog)

---

## 📍 GPS Tracking Lifecycle

### When GPS Starts
1. **First-time login**: User completes OTP verification → `requestLocationAndStartTracking()` → permission dialog appears → GPS runs
2. **App restart (logged in)**: LoginFragment detects session → `requestLocationAndStartTracking()` → if permission already granted, GPS starts instantly
3. **Already in app**: User navigates between screens → GPS already running in foreground service

### When GPS Stops
- `onDestroy()` or `onStop()` of GpsTrackerService
- User explicitly revokes location permission
- User logs out (logout should call `MainActivity.stopGpsTracking()`)

---

## 🚀 Development Notes

### Adding Logout Functionality
If you add a logout feature, remember to:
1. Clear session: `SessionManager.clearSession()`
2. Stop GPS: `MainActivity.stopGpsTracking()`
3. Navigate back to login screen

```kotlin
fun logout() {
    SessionManager.clearSession()
    (context as MainActivity).stopGpsTracking()
    findNavController().navigate(R.id.action_any_to_login)  // Add this action if needed
}
```

### Testing the Auto-Login
```bash
# Build and run
./gradlew installDebug

# Test Flow 1: First Time
# - Open app → See login screen
# - Enter OTP → Grant permission → See map

# Test Flow 2: Restart App (Permission Already Granted)
# - Close app completely
# - Reopen → See map immediately (no permission dialog) ✅ NO LOGIN SCREEN

# Test Flow 3: Revoke Permission
# - Android Settings → Apps → Fuel Queue → Permissions → Disable Location
# - Reopen app → Permission dialog should appear again
```

### To Force Clear Session (For Testing)
Add a debug menu or use Android Studio's Device File Explorer:
```bash
# Via adb
adb shell
su
rm -r /data/data/com.fuelqueue/shared_prefs/fuel_queue_session.xml

# Or let user logout (to be implemented)
```

---

## 🐛 Troubleshooting

### Issue: Login screen still appears after session exists
**Cause:** SessionManager not initialized properly  
**Fix:** Ensure `SessionManager.init(applicationContext)` is called in `MainActivity.onCreate()`

### Issue: GPS tracking not starting on app restart
**Cause:** Location permission not granted  
**Fix:** 
- Check Android settings: Settings → Fuel Queue → Permissions → Location → Allow
- Or trigger permission dialog again by restarting app

### Issue: Permission dialog appears every time app opens
**Cause:** Permission was denied by user  
**Fix:** 
- Ask user to grant permission in settings manually
- Or implement a "Retry" button to ask permission again

### Issue: Map appears but crowd levels not updating
**Cause:** GPS ping failed despite service running  
**Fix:**
- Ensure backend `/api/gps/ping` endpoint is working
- Check network connectivity
- Verify JWT token is valid

---

## 📊 State Transitions

```
[Init] 
  ├─ No Session + Permission NOT Granted
  │   └─→ [LoginUI] → [OtpInputUI] → [PermissionDialog] → [GPSRunning] → [MapScreen] ✅
  │
  ├─ No Session + Permission GRANTED (unlikely on first install)
  │   └─→ [LoginUI] → [OtpInputUI] → [MapScreen] → [GPSRunning] ✅
  │
  ├─ Session EXISTS + Permission NOT Granted
  │   └─→ [PermissionDialog] → [GPSRunning] → [MapScreen] ✅ (instant, no login)
  │
  └─ Session EXISTS + Permission GRANTED
      └─→ [MapScreen] + [GPSRunning] ✅ (instant, no dialogs)
```

---

## 🔄 Request Deduplication

The app avoids asking for permission multiple times:

| Scenario | MainActivity.onCreate() | LoginFragment.onViewCreated() | Result |
|----------|--------------------------|-------------------------------|--------|
| First-time user | _(skipped if not logged in)_ | Shows login UI | ✅ Login shown |
| User completes OTP | _(already init)_ | Calls requestLocation() | ✅ Location asked |
| Logged-in restart | _(skipped)_ | Calls requestLocation() | ✅ Single request |
| After grant permission | _(skipped)_ | GPS starts immediately | ✅ No retry |

---

## 📚 Related Files

- `MainActivity.kt` — Entry point, permission launcher, GPS service control
- `LoginFragment.kt` — Auto-login logic, auto-request location
- `RegisterFragment.kt` — Registration flow, post-reg location request  
- `SessionManager.kt` — Token + user info persistence
- `GpsTrackerService.kt` — Background GPS pinging
- `nav_graph.xml` — Navigation routes

---

## ✅ Checklist for New Developers

- [ ] Understand `SessionManager.isLoggedIn()` checks JWT token existence
- [ ] Know that `MainActivity.requestLocationAndStartTracking()` is non-blocking
- [ ] Realize that returning users with permission get instant map access
- [ ] Remember to add GPS tracking calls after login/register
- [ ] Test scenarios: (1) First time, (2) Restart, (3) Permission revoked
- [ ] Don't duplicate location requests (call once per login attempt)


