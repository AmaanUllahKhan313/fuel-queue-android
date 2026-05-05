# Mobile OTP Authentication Migration Guide

## Overview
This document outlines the changes made to migrate from email-based authentication to mobile number-based OTP authentication in the Fuel Queue Android application.

---

## 🔄 Changes Summary

### 1. **Data Models** (`app/src/main/java/com/fuelqueue/data/model/Models.kt`)

#### Added Models:
```kotlin
data class SendOtpRequest(
    val mobileNumber: String
)

data class VerifyOtpRequest(
    val mobileNumber: String,
    val otp: String
)

data class OtpResponse(
    val success: Boolean,
    val message: String?,
    val expiresInSeconds: Int?
)
```

#### Updated LoginResponse:
```kotlin
data class LoginResponse(
    val token: String,
    val userId: Long,
    val name: String,
    val mobileNumber: String  // ← Changed from email to mobileNumber
)
```

**Old Models Deprecated (Kept for Backward Compatibility):**
- `LoginRequest` (email, password)
- `RegisterRequest` (email, password, name)

---

### 2. **API Service** (`app/src/main/java/com/fuelqueue/data/api/ApiService.kt`)

#### New OTP Endpoints:
```kotlin
// Login Flow
@POST("api/auth/send-login-otp")
suspend fun sendLoginOtp(@Body request: SendOtpRequest): Response<OtpResponse>

@POST("api/auth/verify-login-otp")
suspend fun verifyLoginOtp(@Body request: VerifyOtpRequest): Response<LoginResponse>

// Registration Flow
@POST("api/auth/send-register-otp")
suspend fun sendRegisterOtp(@Body request: SendOtpRequest): Response<OtpResponse>

@POST("api/auth/verify-register-otp")
suspend fun verifyRegisterOtp(@Body request: VerifyOtpRequest): Response<LoginResponse>
```

---

### 3. **Session Manager** (`app/src/main/java/com/fuelqueue/utils/SessionManager.kt`)

#### Changes:
- Replaced `KEY_EMAIL` with `KEY_MOBILE`
- Updated `saveSession()` signature: `String email` → `String mobileNumber`
- Added `getMobileNumber()` method (replaces old `getEmail()`)
- Removed `getEmail()` method

```kotlin
// Before
fun getEmail(): String = prefs.getString(KEY_EMAIL, "") ?: ""

// After
fun getMobileNumber(): String = prefs.getString(KEY_MOBILE, "") ?: ""
```

---

### 4. **Login Fragment** (`app/src/main/java/com/fuelqueue/ui/login/LoginFragment.kt`)

#### New Features:
- **Two-Step OTP Flow**: Send OTP → Verify OTP
- **State Management**: Tracks `otpSent` state and `currentMobileNumber`
- **Mobile Number Validation**: Ensures 10-digit format
- **OTP Validation**: Ensures 6-digit format

#### Flow:
1. User enters 10-digit mobile number
2. Clicks "Send OTP" → OTP sent to backend
3. System reveals OTP input field
4. User enters 6-digit OTP
5. Clicks "Verify OTP" → Authentication successful

#### Key Methods:
```kotlin
sendOtp()          // Step 1: Send OTP to mobile number
verifyOtp()        // Step 2: Verify OTP and authenticate
updateUIState()    // Update UI based on otpSent state
```

---

### 5. **Register Fragment** (`app/src/main/java/com/fuelqueue/ui/login/RegisterFragment.kt`)

#### New Features:
- **Two-Step Registration**: Send OTP → Verify OTP
- **Email Field Hidden**: Removed email input (unused in OTP flow)
- **Name + Mobile Number**: Only required fields
- **State Persistence**: Handles screen rotation via `onSaveInstanceState`

#### Flow:
1. User enters name and 10-digit mobile number
2. Clicks "Send OTP" → OTP sent to backend
3. System reveals OTP input field
4. User enters 6-digit OTP
5. Clicks "Verify OTP" → Account created and auto-logged in

#### Key Methods:
```kotlin
sendOtp()          // Step 1: Send OTP to mobile number
verifyOtp()        // Step 2: Verify OTP and complete registration
updateUIState()    // Update UI based on otpSent state
```

---

### 6. **Login Layout** (`app/src/main/res/layout/fragment_login.xml`)

#### Changes:
- **et_email field**: Now accepts phone input (10-digit mobile number)
- **et_password field**: Now accepts numeric OTP (initially hidden)
- Input type changed:
  - `et_email`: `textEmailAddress` → `phone`
  - `et_password`: `textPassword` → `number`
- Button text: "Login" → Dynamic ("Send OTP" / "Verify OTP")

---

### 7. **Register Layout** (`app/src/main/res/layout/fragment_register.xml`)

#### Changes:
- **et_email field**: Hidden (not used in OTP flow)
- **et_password field**: Used for mobile number input (`phone` type)
- **et_confirm_password field**: Used for OTP input (initially hidden, `number` type)
- Button text: "Create Account" → "Send OTP" (then "Verify OTP")
- Description updated: "...email required..." → Removed

---

## 🔌 Backend API Contract

The backend must implement these endpoints:

### **Login OTP Flow**
```
POST /api/auth/send-login-otp
Body: { "mobileNumber": "9876543210" }
Response: { "success": true, "message": "OTP sent", "expiresInSeconds": 300 }

POST /api/auth/verify-login-otp
Body: { "mobileNumber": "9876543210", "otp": "123456" }
Response: { 
  "token": "eyJhbGc...", 
  "userId": 1, 
  "name": "John Doe",
  "mobileNumber": "9876543210"
}
```

### **Registration OTP Flow**
```
POST /api/auth/send-register-otp
Body: { "mobileNumber": "9876543210" }
Response: { "success": true, "message": "OTP sent", "expiresInSeconds": 300 }

POST /api/auth/verify-register-otp
Body: { "mobileNumber": "9876543210", "otp": "123456" }
Response: { 
  "token": "eyJhbGc...", 
  "userId": 1, 
  "name": "New User",
  "mobileNumber": "9876543210"
}
```

---

## ✅ Testing Checklist

- [ ] **Login Flow**
  - [ ] Enter valid 10-digit mobile number
  - [ ] Verify "Send OTP" button enabled
  - [ ] Confirm OTP input field appears after sending
  - [ ] Enter valid 6-digit OTP
  - [ ] Verify successful authentication and navigation to map
  - [ ] Test invalid mobile number format (< 10 digits)
  - [ ] Test invalid OTP format (< 6 digits)

- [ ] **Registration Flow**
  - [ ] Enter name and valid 10-digit mobile number
  - [ ] Verify "Send OTP" button enabled
  - [ ] Confirm OTP input field appears after sending
  - [ ] Enter valid 6-digit OTP
  - [ ] Verify account creation and auto-login
  - [ ] Test mobile number already registered scenario
  - [ ] Test screen rotation (state persistence)

- [ ] **Session Management**
  - [ ] Verify `SessionManager.getMobileNumber()` returns correct value
  - [ ] Verify JWT token stored correctly
  - [ ] Verify logout clears mobile number from SharedPreferences
  - [ ] Verify auto-login skips to map if already authenticated

- [ ] **Error Handling**
  - [ ] Network timeout during OTP send
  - [ ] Invalid OTP attempts
  - [ ] Mobile number not found (login)
  - [ ] Mobile number already registered (registration)

---

## 🚀 Migration Steps

### For Backend Team:
1. Create new OTP endpoints (see API Contract above)
2. Implement OTP generation and verification logic
3. Update `LoginResponse` to include `mobileNumber` field
4. Add mobile number validation (10 digits)
5. Deprecate old email-based endpoints (if needed)

### For Frontend Team (Already Done):
1. ✅ Updated data models
2. ✅ Updated API service interface
3. ✅ Updated session manager
4. ✅ Rewrote login fragment with OTP flow
5. ✅ Rewrote register fragment with OTP flow
6. ✅ Updated layouts for mobile + OTP inputs

### For QA Team:
1. Test all scenarios in "Testing Checklist" above
2. Verify error messages are user-friendly
3. Test on various Android versions (minSdk: 24)
4. Test on different screen sizes

---

## 📝 Example Usage in Code

### Before (Email-based):
```kotlin
// Old way (deprecated)
val response = RetrofitClient.api.login(LoginRequest("user@example.com", "password123"))
SessionManager.saveSession(token, userId, name, email = "user@example.com")
```

### After (OTP-based):
```kotlin
// Step 1: Send OTP
val sendResponse = RetrofitClient.api.sendLoginOtp(SendOtpRequest("9876543210"))

// Step 2: Verify OTP
val verifyResponse = RetrofitClient.api.verifyLoginOtp(
    VerifyOtpRequest("9876543210", "123456")
)
SessionManager.saveSession(token, userId, name, mobileNumber = "9876543210")

// Retrieve later:
val userMobil = SessionManager.getMobileNumber() // "9876543210"
```

---

## 🔒 Security Considerations

1. **OTP Expiration**: Backend should expire OTP after 5 minutes
2. **Attempt Limiting**: Limit OTP verification attempts (e.g., max 3 attempts)
3. **Mobile Number Validation**: Verify mobile number format on both client & backend
4. **JWT Token**: Ensure JWT is stored securely in SharedPreferences
5. **HTTPS**: Use HTTPS for all OTP endpoints (no sensitive data in query params)

---

## 🔄 Backward Compatibility

Old models are kept but deprecated:
- `LoginRequest` - No longer used
- `RegisterRequest` - No longer used
- `MessageResponse` - No longer used (replaced with `OtpResponse`)

Old `SessionManager` methods removed:
- `getEmail()` - Use `getMobileNumber()` instead

---

## 📞 Support

For integration issues, refer to:
- **API Models**: `app/src/main/java/com/fuelqueue/data/model/Models.kt`
- **API Service**: `app/src/main/java/com/fuelqueue/data/api/ApiService.kt`
- **Login Implementation**: `app/src/main/java/com/fuelqueue/ui/login/LoginFragment.kt`
- **Register Implementation**: `app/src/main/java/com/fuelqueue/ui/login/RegisterFragment.kt`

---

## 📅 Version History

**v1.0 - Mobile OTP Authentication** (Current)
- ✅ Email-based auth → OTP-based auth
- ✅ Two-step login flow
- ✅ Two-step registration flow
- ✅ SessionManager updated for mobile storage


