package com.example.ui.screens

import android.widget.Toast
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel
import com.example.util.GoogleSignInHelper
import com.example.util.SoundSystem
import com.example.util.AppSignatureHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

enum class AuthMode { SIGN_IN, REGISTER }

@Composable
fun LoginScreen(
    viewModel: GameViewModel,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    var isSigningIn by remember { mutableStateOf(false) }
    var showError10Dialog by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val profile by viewModel.profile.collectAsState()
    val isFarsi = profile.language == "FA"

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            onNavigateToHome()
        }
    }

    // Email/Password States
    var authMode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var localValidationErrorMessage by remember { mutableStateOf("") }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningIn = true
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                viewModel.signInWithGoogle(idToken) { success, errorMsg ->
                    isSigningIn = false
                    if (success) {
                        Toast.makeText(context, if (isFarsi) "ورود موفقیت‌آمیز بود! خوش آمدید، ${account.displayName}" else "Sign-in successful! Welcome, ${account.displayName}", Toast.LENGTH_SHORT).show()
                        onNavigateToHome()
                    } else {
                        val isConfigError = errorMsg?.contains("Json conversion failed", ignoreCase = true) == true || 
                                           errorMsg?.contains("internal error", ignoreCase = true) == true ||
                                           errorMsg?.contains("network", ignoreCase = true) == true
                        if (isConfigError) {
                            showError10Dialog = true
                        } else {
                            Toast.makeText(context, if (isFarsi) "ورود ناموفق بود: $errorMsg" else "Login failed: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                isSigningIn = false
                Toast.makeText(context, if (isFarsi) "امکان دریافت اطلاعات ورود از گوگل وجود ندارد." else "Failed to retrieve Google Identity credentials.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            isSigningIn = false
            val isHelpNeeded = if (e is ApiException) {
                e.statusCode == 10 || e.statusCode == 7 || e.statusCode == 8 || e.statusCode == 20 || e.statusCode == 12500 || e.statusCode == 12501 || e.statusCode == 12502
            } else {
                e.message?.contains("10:") == true || 
                e.localizedMessage?.contains("10:") == true || 
                e.localizedMessage?.contains("Json conversion failed", ignoreCase = true) == true ||
                e.localizedMessage?.contains("network", ignoreCase = true) == true ||
                e.localizedMessage?.contains("connection", ignoreCase = true) == true ||
                e.localizedMessage?.contains("timeout", ignoreCase = true) == true
            }
            if (isHelpNeeded) {
                showError10Dialog = true
            } else {
                Toast.makeText(context, if (isFarsi) "خطا در احراز هویت گوگل: ${e.localizedMessage}" else "Google authentication failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Local Verification Helpers
    fun isValidEmail(target: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches()
    }

    fun handleEmailAuthSubmit() {
        SoundSystem.playMenuClick()
        localValidationErrorMessage = ""

        if (email.isBlank() || password.isBlank()) {
            localValidationErrorMessage = if (isFarsi) "لطفاً تمام فیلدها را پر کنید." else "Please fill in all fields."
            return
        }

        if (!isValidEmail(email)) {
            localValidationErrorMessage = if (isFarsi) "فرمت آدرس ایمیل نامعتبر است." else "Email format is invalid."
            return
        }

        if (password.length < 6) {
            localValidationErrorMessage = if (isFarsi) "رمز عبور باید حداقل ۶ کاراکتر باشد." else "Password must be at least 6 characters."
            return
        }

        isSigningIn = true

        if (authMode == AuthMode.SIGN_IN) {
            viewModel.signInWithEmail(email, password) { success, errorMsg ->
                isSigningIn = false
                if (success) {
                    Toast.makeText(context, if (isFarsi) "ورود با موفقیت انجام شد!" else "Logged in successfully!", Toast.LENGTH_SHORT).show()
                    onNavigateToHome()
                } else {
                    localValidationErrorMessage = errorMsg ?: (if (isFarsi) "ورود ناموفق بود." else "Authentication failed.")
                }
            }
        } else {
            viewModel.signUpWithEmail(email, password) { success, errorMsg ->
                isSigningIn = false
                if (success) {
                    Toast.makeText(context, if (isFarsi) "ثبت‌نام با موفقیت انجام شد!" else "Registered successfully!", Toast.LENGTH_SHORT).show()
                    onNavigateToHome()
                } else {
                    localValidationErrorMessage = errorMsg ?: (if (isFarsi) "ثبت‌نام ناموفق بود." else "Registration failed.")
                }
            }
        }
    }

    FrostedGlassBackground(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 450.dp)
            ) {
                // Large Glowing Logo Header
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "INFINITE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = AccentIndigoLight,
                        letterSpacing = 6.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "TIC TAC TOE",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        style = LocalTextStyle.current.copy(
                            brush = Brush.horizontalGradient(
                                colors = listOf(AccentIndigoLight, AccentFuchsiaLight)
                            )
                        ),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "THE ULTIMATE ONLINE STRATEGY DUEL",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Authentication Dialog Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    border = BorderStroke(1.dp, NeonBorder),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header or sliding animation switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val tabModifier = Modifier.weight(1f).height(40.dp)
                            val selectedTabColors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            )
                            val unselectedTabColors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White.copy(alpha = 0.5f)
                            )

                            Button(
                                onClick = {
                                    SoundSystem.playMenuClick()
                                    authMode = AuthMode.SIGN_IN
                                    localValidationErrorMessage = ""
                                },
                                colors = if (authMode == AuthMode.SIGN_IN) selectedTabColors else unselectedTabColors,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = tabModifier
                            ) {
                                Text(
                                    text = if (isFarsi) "ورود" else "SIGN IN",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            Button(
                                onClick = {
                                    SoundSystem.playMenuClick()
                                    authMode = AuthMode.REGISTER
                                    localValidationErrorMessage = ""
                                },
                                colors = if (authMode == AuthMode.REGISTER) selectedTabColors else unselectedTabColors,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = tabModifier
                            ) {
                                Text(
                                    text = if (isFarsi) "ثبت‌نام" else "REGISTER",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Text(
                            text = if (authMode == AuthMode.SIGN_IN) {
                                if (isFarsi) "ورود با ایمیل و رمز عبور" else "SIGN IN WITH CREDENTIALS"
                            } else {
                                if (isFarsi) "ایجاد حساب کاربری با ایمیل" else "CREATE ACCOUNT VIA EMAIL"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )

                        // Email Field
                        OutlinedTextField(
                            value = email,
                            onValueChange = { 
                                email = it 
                                localValidationErrorMessage = ""
                            },
                            label = { Text(if (isFarsi) "آدرس ایمیل" else "Email Address") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = AccentIndigoLight) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentIndigoLight,
                                unfocusedBorderColor = NeonBorder,
                                focusedLabelColor = AccentIndigoLight,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = AccentIndigoLight
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("email_input")
                        )

                        // Password Field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { 
                                password = it 
                                localValidationErrorMessage = ""
                            },
                            label = { Text(if (isFarsi) "رمز عبور" else "Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = AccentIndigoLight) },
                            trailingIcon = {
                                TextButton(
                                    onClick = { isPasswordVisible = !isPasswordVisible },
                                    colors = ButtonDefaults.textButtonColors(contentColor = AccentIndigoLight)
                                ) {
                                    Text(
                                        text = if (isPasswordVisible) {
                                            if (isFarsi) "مخفی" else "HIDE"
                                        } else {
                                            if (isFarsi) "نمایش" else "SHOW"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentIndigoLight,
                                unfocusedBorderColor = NeonBorder,
                                focusedLabelColor = AccentIndigoLight,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = AccentIndigoLight
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input")
                        )

                        if (localValidationErrorMessage.isNotEmpty()) {
                            Text(
                                text = localValidationErrorMessage,
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            )
                        }

                        // Submit Button
                        Button(
                            onClick = { handleEmailAuthSubmit() },
                            enabled = !isSigningIn,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentIndigoLight),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag(if (authMode == AuthMode.SIGN_IN) "login_submit_button" else "register_submit_button")
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    text = if (authMode == AuthMode.SIGN_IN) {
                                        if (isFarsi) "ورود به بازی" else "Login"
                                    } else {
                                        if (isFarsi) "ثبت‌نام و عضویت" else "Register"
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        // Forgot Password Link (Only in Sign In Mode)
                        if (authMode == AuthMode.SIGN_IN) {
                            TextButton(
                                onClick = { 
                                    SoundSystem.playMenuClick()
                                    showForgotPasswordDialog = true 
                                },
                                modifier = Modifier.testTag("forgot_password_button")
                            ) {
                                Text(
                                    text = if (isFarsi) "رمز عبور خود را فراموش کرده‌اید؟" else "Forgot Password?",
                                    color = AccentIndigoLight,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.15f))
                    Text(
                        text = if (isFarsi) "یا" else "OR",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.15f))
                }

                // Google & Guest alternate logins box
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            color = PrimaryCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        // Gmail Google Sign In Button
                        Button(
                            onClick = {
                                SoundSystem.playMenuClick()
                                val client = GoogleSignInHelper.getGoogleSignInClient(context)
                                googleSignInLauncher.launch(client.signInIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("google_login_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "G",
                                    color = Color(0xFFEA4335),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = if (isFarsi) "ورود با حساب گوگل" else "Sign in with Google",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Offline/Guest Entrance
                        var isSigningInGuest by remember { mutableStateOf(false) }
                        if (isSigningInGuest) {
                            CircularProgressIndicator(
                                color = PrimaryCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Button(
                                onClick = {
                                    SoundSystem.playMenuClick()
                                    isSigningInGuest = true
                                    viewModel.signInAnonymously { _, _ ->
                                        isSigningInGuest = false
                                        onNavigateToHome()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CarbonSurfaceUp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("guest_login_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (isFarsi) "ورود به عنوان مهمان" else "Skip & Play as Guest",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isFarsi) "پشتیبانی کامل از رمزنگاری امن پایگاه داده Firebase" else "Real-time encryption by Google Firebase Identity services.",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        var forgotEmail by remember { mutableStateOf("") }
        var isSendingReset by remember { mutableStateOf(false) }
        var resetSuccessOrError by remember { mutableStateOf("") }
        var resetIsSuccessToken by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            containerColor = DeepSlateBg,
            title = {
                Text(
                    text = if (isFarsi) "بازیابی رمز عبور" else "Reset Password",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isFarsi) "آدرس ایمیل خود را وارد کنید تا لینک بازیابی ارسال شود." else "Enter your registered email address to receive a secure password reset link.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = { 
                            forgotEmail = it 
                            resetSuccessOrError = ""
                        },
                        label = { Text(if (isFarsi) "ایمیل" else "Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = AccentIndigoLight) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentIndigoLight,
                            unfocusedBorderColor = NeonBorder,
                            focusedLabelColor = AccentIndigoLight,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = AccentIndigoLight
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("forgot_password_email_input")
                    )

                    if (resetSuccessOrError.isNotEmpty()) {
                        Text(
                            text = resetSuccessOrError,
                            color = if (resetIsSuccessToken) Color(0xFF10B981) else Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        SoundSystem.playMenuClick()
                        if (forgotEmail.isBlank() || !isValidEmail(forgotEmail)) {
                            resetSuccessOrError = if (isFarsi) "لطفاً یک آدرس ایمیل معتبر وارد کنید." else "Please enter a valid email address."
                            return@Button
                        }

                        isSendingReset = true
                        viewModel.sendPasswordResetEmail(forgotEmail) { success, errorMsg ->
                            isSendingReset = false
                            if (success) {
                                resetIsSuccessToken = true
                                resetSuccessOrError = if (isFarsi) "لینک بازیابی رمز عبور با موفقیت ارسال شد. ایمیل خود را بررسی کنید." else "Password reset link sent! Check your inbox."
                            } else {
                                resetIsSuccessToken = false
                                resetSuccessOrError = errorMsg ?: (if (isFarsi) "ارسال لینک بازیابی ناموفق بود." else "Failed to send reset link.")
                            }
                        }
                    },
                    enabled = !isSendingReset,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigoLight),
                    modifier = Modifier.testTag("send_reset_link_button")
                ) {
                    if (isSendingReset) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text(if (isFarsi) "ارسال لینک" else "Send link", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text(if (isFarsi) "بستن" else "Close", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }

    // Google Sign in / VPN connection error code 10 alert dialogue
    if (showError10Dialog) {
        val currentProfile by viewModel.profile.collectAsState()
        val isFarsiLang = currentProfile.language == "FA"
        AlertDialog(
            onDismissRequest = { showError10Dialog = false },
            containerColor = CarbonSurface,
            title = {
                Text(
                    text = if (isFarsiLang) "برقراری ارتباط ناموفق بود" else "Network / Google Connection Failed",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                val sha1 = remember { AppSignatureHelper.getSha1Fingerprint(context) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (isFarsiLang) {
                            "⚠️ خطای ورود به سیستم گوگل (کد ۱۰ یا خطا در شبکه)\n\n" +
                            "این خطا زمانی رخ می‌دهد که سرویس‌های گوگل نتوانند ارتباط برقرار کنند. دو دلیل اصلی برای آن وجود دارد:\n\n" +
                            "۱. 🔴 اتصال فیلترشکن (VPN) قطع است:\n" +
                            "به دلیل تحریم‌های شرکت گوگل علیه ایران، در زمان خاموش بودن فیلترشکن، گوگل اجازه احراز هویت را نداده و خطای تنظیمات (Developer Error) بازمی‌گرداند. لطفاً فیلترشکن خود را روشن کنید تا وارد شوید.\n\n" +
                            "۲. تنظیم نبودن اثر انگشت SHA-1 در پنل فایربیس:\n" +
                            "اگر فیلترشکن شما روشن است اما باز هم این خطا را می‌بینید، احتمالاً شناسه‌ی SHA-1 زیر در کنسول Firebase اضافه نشده است.\n\n" +
                            "اثر انگشت SHA-1 این برنامه:"
                        } else {
                            "⚠️ Google Sign-In Fails (Code 10 / Connection Blocked)\n\n" +
                            "This issue usually occurs for one of two reasons:\n\n" +
                            "1. 🔴 VPN is OFF / Network Sanctions:\n" +
                            "Due to Google's restrictions on Iranian IPs or network censorship, Google blocks authorization when your VPN is OFF. Please turn ON your VPN/Proxy to login.\n\n" +
                            "2. Missing SHA-1 Configuration:\n" +
                            "If your VPN is already ON but you still see this, the SHA-1 fingerprint of the app is not registered in your Firebase Console.\n\n" +
                            "Device's SHA-1 Fingerprint:"
                        },
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = sha1,
                            color = PrimaryCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("SHA1 Fingerprint", sha1)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, if (isFarsiLang) "اثر انگشت SHA-1 کپی شد!" else "SHA-1 fingerprint copied!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CarbonSurfaceUp),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isFarsiLang) "کپی کردن کد SHA-1 به حافظه" else "Copy SHA-1 to Clipboard", color = Color.White, fontSize = 12.sp)
                    }

                    Text(
                        text = if (isFarsiLang) {
                            "روش ثبت اثر انگشت در کنسول فایربیس (تنها در صورتی که با وجود روشن بودن فیلترشکن همچنان خطا می‌بینید):\n" +
                            "1️⃣ وارد Firebase Console شده و به تنظیمات پروژه (Settings) بروید.\n" +
                            "2️⃣ به بخش Your apps اسکرول کنید.\n" +
                            "3️⃣ دکمه Add fingerprint را بزنید و این کد SHA-1 را پیست کنید.\n" +
                            "• نیازی به آپدیت کردن فایل google-services نیست، تغییرات بلافاصله اثرگذار خواهد بود.\n\n" +
                            "تا آن زمان، می‌توانید از گزینه 'بازی به عنوان مهمان' یا ورود آفلاین (Guest) استفاده کنید."
                        } else {
                            "To authorize and fix this in Firebase (Only if the error persists even with active VPN):\n" +
                            "1️⃣ Go to Firebase Console > Project Settings.\n" +
                            "2️⃣ Scroll down to 'Your apps' > Android app.\n" +
                            "3️⃣ Click 'Add fingerprint' and paste this SHA-1 key.\n" +
                            "• No need to download a new config file; changes apply instantly!\n\n" +
                            "In the meantime, press 'Play as Guest' to play local games offline."
                        },
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showError10Dialog = false
                        onNavigateToHome()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Text(if (isFarsiLang) "بازی به عنوان مهمان" else "Play as Guest", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showError10Dialog = false }) {
                    Text(if (isFarsiLang) "بستن" else "Close", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }
}
