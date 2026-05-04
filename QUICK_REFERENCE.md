# 🚀 Quick Start - Mobile OTP Authentication

## 📱 What Changed?
- ❌ Email + Password authentication
- ✅ Mobile Number + OTP authentication (two-step)

---

## 🔄 Authentication Flows

### Login
```
Step 1: Send OTP to mobile
├─ GET: 10-digit mobile number from user
├─ POST: /api/auth/send-login-otp
└─ Response: { "success": true, "expiresInSeconds": 300 }

Step 2: Verify OTP
├─ GET: 6-digit OTP from user
├─ POST: /api/auth/verify-login-otp
├─ Response: { "token": "...", "userId": 1, "name": "...", "mobileNumber": "..." }
└─ SAVE: Store token + mobile in SessionManager
```

### Registration
```
Step 1: Send OTP to mobile
├─ GET: Name + 10-digit mobile from user
├─ POST: /api/auth/send-register-otp
└─ Response: { "success": true, "expiresInSeconds": 300 }

Step 2: Verify OTP & Create Account
├─ GET: 6-digit OTP from user
├─ POST: /api/auth/verify-register-otp
├─ Response: { "token": "...", "userId": 1, "name": "...", "mobileNumber": "..." }
└─ SAVE: Store token + mobile in SessionManager → Auto-login
```

---

## 🗂️ Key Files

| What | Where |
|------|-------|
| Models | `data/model/Models.kt` |
| API | `data/api/ApiService.kt` |
| Session | `utils/SessionManager.kt` |
| Login UI | `ui/login/LoginFragment.kt` |
| Register UI | `ui/login/RegisterFragment.kt` |
| Login Layout | `res/layout/fragment_login.xml` |
| Register Layout | `res/layout/fragment_register.xml` |

---

## ⚙️ Backend Endpoints Needed

```kotlin
POST /api/auth/send-login-otp
Request:  { "mobileNumber": "9876543210" }
Response: { "success": true, "message": "OTP sent", "expiresInSeconds": 300 }

POST /api/auth/verify-login-otp
Request:  { "mobileNumber": "9876543210", "otp": "123456" }
Response: { "token": "...", "userId": 1, "name": "John", "mobileNumber": "9876543210" }

POST /api/auth/send-register-otp
Request:  { "mobileNumber": "9876543210" }
Response: { "success": true, "message": "OTP sent", "expiresInSeconds": 300 }

POST /api/auth/verify-register-otp
Request:  { "mobileNumber": "9876543210", "otp": "123456" }
Response: { "token": "...", "userId": 1, "name": "New User", "mobileNumber": "9876543210" }
```

---

## 💾 SessionManager API

### New Methods
```kotlin
SessionManager.getMobileNumber()  // Returns "9876543210"
SessionManager.saveSession(
    token = "jwt...",
    userId = 1L,
    name = "John",
    mobileNumber = "9876543210"
)
```

### Old Methods (REMOVED)
```kotlin
SessionManager.getEmail()  // ❌ REMOVED - Use getMobileNumber() instead
```

---

## 📋 Validation Rules (Client-Side)

### Mobile Number
- ✅ Exactly 10 digits
- ✅ No letters or special characters
- ✅ Regex: `^[0-9]{10}$`

### OTP
- ✅ Exactly 6 digits
- ✅ No letters or special characters
- ✅ User enters after OTP is sent

---

## 🎯 Code Examples

### Sending OTP in Fragment
```kotlin
lifecycleScope.launch {
    try {
        val response = RetrofitClient.api.sendLoginOtp(
            SendOtpRequest("9876543210")
        )
        if (response.body()?.success == true) {
            otpSent = true  // Update state
            updateUIState() // Show OTP input
        }
    } catch (e: Exception) {
        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
    }
}
```

### Verifying OTP in Fragment
```kotlin
lifecycleScope.launch {
    try {
        val response = RetrofitClient.api.verifyLoginOtp(
            VerifyOtpRequest("9876543210", "123456")
        )
        val loginResponse = response.body()
        if (loginResponse != null) {
            SessionManager.saveSession(
                loginResponse.token,
                loginResponse.userId,
                loginResponse.name,
                loginResponse.mobileNumber
            )
            // Navigate to map
        }
    } catch (e: Exception) {
        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
    }
}
```

---

## 🧪 Quick Testing

### Test Scenario 1: Valid Login
1. Enter: `9876543210`
2. Click: "Send OTP"
3. Enter: `123456` (backend-provided OTP)
4. Click: "Verify OTP"
5. Expected: ✅ Navigate to map screen

### Test Scenario 2: Invalid Mobile
1. Enter: `123456` (only 6 digits)
2. Click: "Send OTP"
3. Expected: ❌ "Please enter a valid 10-digit mobile number"

### Test Scenario 3: Invalid OTP
1. Enter mobile, send OTP
2. Enter: `111111` (wrong OTP)
3. Click: "Verify OTP"
4. Expected: ❌ "Invalid OTP"

---

## 🚨 Important Notes

1. **Mobile Format:** Always 10 digits, no special chars
2. **OTP Format:** Always 6 digits, numeric only
3. **State Management:** Uses `otpSent` flag to track flow stage
4. **Screen Rotation:** State persisted via `onSaveInstanceState`
5. **Auto-Login:** If already authenticated, skip to map
6. **SessionManager:** All session data persisted in SharedPreferences
7. **Error Handling:** User-friendly error messages shown as Toast

---

## 📞 Still Need Help?

See detailed guides:
- **Implementation Details:** [OTP_MIGRATION_GUIDE.md](./OTP_MIGRATION_GUIDE.md)
- **Complete Summary:** [MIGRATION_SUMMARY.md](./MIGRATION_SUMMARY.md)
- **AI Agent Guidance:** [AGENTS.md](./AGENTS.md)

---

**Last Updated:** May 4, 2026  
**Status:** ✅ Ready for integration with backend OTP endpoints


