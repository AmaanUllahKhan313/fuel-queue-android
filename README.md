# ⛽ Fuel Queue — Android App

Real-time fuel station crowd tracker Android app built with Kotlin + XML layouts.

---

## ✅ Prerequisites

| Tool             | Version  |
|------------------|----------|
| Android Studio   | Hedgehog (2023.1.1) or newer |
| Android SDK      | API 24+ (Android 7.0+) |
| Java             | 17       |
| Spring Backend   | Running on localhost:8080 |

---

## 🚀 Setup in Android Studio

1. Open Android Studio → **File → Open** → select `fuel-queue-android` folder
2. Wait for Gradle sync (~2–3 min first time)
3. **Add your Google Maps API Key:**
   - Open `app/build.gradle`
   - Replace `YOUR_GOOGLE_MAPS_API_KEY` with a real key from [console.cloud.google.com](https://console.cloud.google.com)
   - Enable **Maps SDK for Android** in your project
4. Start the Spring Boot backend first (runs on port 8080)
5. Run the app on **emulator** or physical device

> **Emulator users:** Backend URL is `http://10.0.2.2:8080/` (maps to your host localhost)
> **Physical device:** Change `BASE_URL` in `app/build.gradle` to your machine's LAN IP e.g. `http://192.168.1.5:8080/`

---

## 📱 App Screens

| Screen          | Description                                              |
|-----------------|----------------------------------------------------------|
| **Login**       | Email/password login → JWT stored in SharedPreferences   |
| **Register**    | Create account with name, email, password                |
| **Map**         | Google Map with colored markers per crowd level          |
| **Station List**| RecyclerView list with crowd badge, distance, wait time  |
| **Detail**      | Live crowd level, user count, wait estimate, auto-refresh|

---

## 🗺️ Map Marker Colors

| Color  | Crowd Level | Meaning          |
|--------|-------------|------------------|
| 🟢 Green  | LOW     | 0–2 vehicles — go now! |
| 🟡 Yellow | MEDIUM  | 3–6 vehicles — short wait |
| 🔴 Red    | HIGH    | 7+ vehicles — long queue |

---

## 📡 How GPS Tracking Works

- After login, a **foreground service** (`GpsTrackerService`) starts automatically
- It sends your GPS coordinates to the backend **every 10 seconds**
- The backend checks if you're inside any station's **80 m geofence**
- If yes, your presence is recorded and used to calculate crowd level
- Your data expires after **30 seconds** of no ping — so leaving the station clears your count automatically
- A persistent notification shows while tracking is active

---

## 📁 Project Structure

```
app/src/main/
├── java/com/fuelqueue/
│   ├── ui/
│   │   ├── MainActivity.kt              ← Single Activity host
│   │   ├── login/
│   │   │   ├── LoginFragment.kt
│   │   │   └── RegisterFragment.kt
│   │   ├── map/
│   │   │   ├── MapFragment.kt           ← Google Map with markers
│   │   │   └── MapFragmentDirections.kt
│   │   ├── list/
│   │   │   ├── StationListFragment.kt   ← RecyclerView list
│   │   │   └── StationAdapter.kt
│   │   └── detail/
│   │       └── StationDetailFragment.kt ← Live crowd detail
│   ├── service/
│   │   └── GpsTrackerService.kt         ← Background GPS ping service
│   ├── data/
│   │   ├── api/
│   │   │   ├── ApiService.kt            ← Retrofit interface
│   │   │   └── RetrofitClient.kt        ← OkHttp + JWT header
│   │   └── model/
│   │       └── Models.kt                ← Data classes
│   └── utils/
│       ├── SessionManager.kt            ← JWT + user info storage
│       └── CrowdUtils.kt               ← Color/label/emoji helpers
├── res/
│   ├── layout/
│   │   ├── activity_main.xml
│   │   ├── fragment_login.xml
│   │   ├── fragment_register.xml
│   │   ├── fragment_map.xml
│   │   ├── fragment_station_list.xml
│   │   ├── fragment_station_detail.xml
│   │   └── item_station.xml             ← RecyclerView row card
│   ├── navigation/nav_graph.xml         ← All screen routes
│   ├── menu/bottom_nav_menu.xml
│   └── values/{colors, strings, themes}
└── AndroidManifest.xml
```

---

## 🔑 Google Maps API Key (Required for Map screen)

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Create a project → Enable **Maps SDK for Android**
3. Create an API key → restrict it to your app package `com.fuelqueue`
4. Paste it in `app/build.gradle`:
   ```groovy
   manifestPlaceholders = [MAPS_API_KEY: "AIzaSy...your_key_here"]
   ```

> Without a Maps key the **List** and **Detail** screens will still work. Only the Map screen needs it.
