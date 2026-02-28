package com.pdfliteai.ui

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.pdfliteai.settings.SettingsViewModel

private enum class OnboardMode { CHOICE, MANUAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit
) {
    val vm: SettingsViewModel = viewModel()
    val context = LocalContext.current
    val cfg = LocalConfiguration.current

    val legalUrl = "https://pandeysatyam.com/pdfliteai-legal/index.html"

    var mode by remember { mutableStateOf(OnboardMode.CHOICE) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var gender by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }

    var genderExpanded by remember { mutableStateOf(false) }
    val genders = remember { listOf("Male", "Female", "Non-binary", "Prefer not to say") }

    var agreed by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var attempted by remember { mutableStateOf(false) }

    fun openLegal() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(legalUrl)))
        }
    }

    fun requireTermsOrError(): Boolean {
        if (!agreed) {
            err = "Please accept Terms & Privacy to continue."
            return false
        }
        return true
    }

    // ---- Google Sign-In ----
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
    }
    val gClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res: ActivityResult ->
        val data = res.data ?: return@rememberLauncherForActivityResult
        err = null
        runCatching {
            val acct = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            val gEmail = acct?.email.orEmpty()
            val gName = acct?.displayName.orEmpty().ifBlank { "User" }
            if (gEmail.isBlank()) {
                err = "Google sign-in failed: email not available."
                return@rememberLauncherForActivityResult
            }
            vm.completeOnboardingGoogle(gName, gEmail)
            onDone()
        }.onFailure {
            err = "Google sign-in failed."
        }
    }

    // ---- Validation ----
    val nameOk = name.trim().isNotBlank()
    val emailOk = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passOk = password.trim().length >= 8
    val genderOk = gender.trim().isNotBlank()
    val cityOk = city.trim().isNotBlank()
    val stateOk = state.trim().isNotBlank()

    fun validateManualAndContinue() {
        attempted = true
        err = null

        if (!requireTermsOrError()) return
        if (!nameOk) { err = "Name is required."; return }
        if (!emailOk) { err = "Enter a valid email."; return }
        if (!passOk) { err = "Password must be at least 8 characters."; return }
        if (!genderOk) { err = "Gender is required."; return }
        if (!cityOk) { err = "City is required."; return }
        if (!stateOk) { err = "State is required."; return }

        vm.completeOnboardingManual(
            name.trim(),
            email.trim(),
            gender.trim(),
            city.trim(),
            state.trim()
        )
        onDone()
    }

    // ---- Background: Aurora + scrims ----
    val verticalScrim = Brush.verticalGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.55f),
            Color.Black.copy(alpha = 0.18f),
            Color.Black.copy(alpha = 0.82f)
        )
    )
    val vignette = Brush.radialGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.30f),
            Color.Black.copy(alpha = 0.84f)
        )
    )

    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Black.copy(alpha = 0.25f),
        unfocusedContainerColor = Color.Black.copy(alpha = 0.18f),
        disabledContainerColor = Color.Black.copy(alpha = 0.14f),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White.copy(alpha = 0.6f),
        focusedBorderColor = Color.White.copy(alpha = 0.22f),
        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
        disabledBorderColor = Color.White.copy(alpha = 0.08f),
        cursorColor = MaterialTheme.colorScheme.primary
    )

    // Terms line (clickable)
    val termsLine = remember {
        buildAnnotatedString {
            append("I agree to ")
            pushStringAnnotation(tag = "URL", annotation = legalUrl)
            pushStyle(
                SpanStyle(
                    color = Color(0xFF9AD1FF),
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline
                )
            )
            append("Terms & Privacy")
            pop()
            pop()
            append(".")
        }
    }

    // Layout sizing
    val screenH = cfg.screenHeightDp.dp
    val cardMaxH = (screenH - 260.dp).coerceAtLeast(340.dp)

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = com.pdfliteai.R.drawable.aurora_header),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(verticalScrim))
        Box(Modifier.fillMaxSize().background(vignette))

        // ✅ Bottom pinned Terms (full width, centered)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.Black.copy(alpha = 0.42f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                Spacer(Modifier.width(8.dp))
                ClickableText(
                    text = termsLine,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.92f)),
                    onClick = { offset ->
                        termsLine.getStringAnnotations("URL", offset, offset)
                            .firstOrNull()
                            ?.let { openLegal() }
                    }
                )
            }
        }

        // ✅ Center content (options in middle)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
                .padding(bottom = 110.dp),
            contentAlignment = Alignment.Center
        ) {
            val innerScroll = rememberScrollState()

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ✅ Icon: use image directly, rounded corners only (no fit/padding box)
                Image(
                    painter = painterResource(id = com.pdfliteai.R.drawable.pdfliteaiicon_playstore),
                    contentDescription = "App icon",
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp)),
                    contentScale = ContentScale.Crop
                )

                Text(
                    "PDFLite AI",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )

                err?.let {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF2A1111),
                        border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(12.dp),
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = cardMaxH),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Black.copy(alpha = 0.46f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    shadowElevation = 14.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(innerScroll),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        AnimatedContent(
                            targetState = mode,
                            transitionSpec = {
                                ContentTransform(
                                    targetContentEnter = fadeIn(tween(160)) + slideInHorizontally(tween(160)) { it / 10 },
                                    initialContentExit = fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { -it / 10 }
                                )
                            },
                            label = "mode"
                        ) { st ->
                            when (st) {
                                OnboardMode.CHOICE -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            "Choose sign-in method",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                        Text(
                                            "Google is recommended. Manual sign-in is available.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.70f)
                                        )

                                        Button(
                                            onClick = {
                                                err = null
                                                if (!requireTermsOrError()) return@Button
                                                googleLauncher.launch(gClient.signInIntent)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp),
                                            shape = RoundedCornerShape(18.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                                contentColor = Color.Black
                                            ),
                                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp, pressedElevation = 2.dp)
                                        ) { Text("Sign in with Google") }

                                        Button(
                                            onClick = {
                                                err = null
                                                attempted = false
                                                mode = OnboardMode.MANUAL
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp),
                                            shape = RoundedCornerShape(18.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White.copy(alpha = 0.10f),
                                                contentColor = Color.White
                                            ),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                                        ) { Text("Sign in Manually") }
                                    }
                                }

                                OnboardMode.MANUAL -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Manual sign-in",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(
                                                onClick = { mode = OnboardMode.CHOICE },
                                                colors = ButtonDefaults.textButtonColors(
                                                    contentColor = Color.White.copy(alpha = 0.85f)
                                                )
                                            ) { Text("Back") }
                                        }

                                        OutlinedTextField(
                                            value = name,
                                            onValueChange = { name = it },
                                            label = { Text("Name") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = tfColors,
                                            isError = attempted && !nameOk,
                                            supportingText = {
                                                if (attempted && !nameOk) Text("Name is required.")
                                            }
                                        )

                                        OutlinedTextField(
                                            value = email,
                                            onValueChange = { email = it },
                                            label = { Text("Email") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = tfColors,
                                            isError = attempted && !emailOk,
                                            supportingText = {
                                                if (attempted && !emailOk) Text("Enter a valid email.")
                                            }
                                        )

                                        OutlinedTextField(
                                            value = password,
                                            onValueChange = { password = it },
                                            label = { Text("Password") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = tfColors,
                                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                    Icon(
                                                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                        contentDescription = if (passwordVisible) "Hide" else "Show",
                                                        tint = Color.White.copy(alpha = 0.85f)
                                                    )
                                                }
                                            },
                                            isError = attempted && !passOk,
                                            supportingText = {
                                                if (attempted && !passOk) Text("Min 8 characters required.")
                                            }
                                        )

                                        ExposedDropdownMenuBox(
                                            expanded = genderExpanded,
                                            onExpandedChange = { genderExpanded = !genderExpanded }
                                        ) {
                                            OutlinedTextField(
                                                value = gender,
                                                onValueChange = { },
                                                readOnly = true,
                                                label = { Text("Gender") },
                                                modifier = Modifier
                                                    .menuAnchor()
                                                    .fillMaxWidth(),
                                                colors = tfColors,
                                                isError = attempted && !genderOk,
                                                supportingText = {
                                                    if (attempted && !genderOk) Text("Gender is required.")
                                                }
                                            )
                                            ExposedDropdownMenu(
                                                expanded = genderExpanded,
                                                onDismissRequest = { genderExpanded = false }
                                            ) {
                                                genders.forEach { opt ->
                                                    DropdownMenuItem(
                                                        text = { Text(opt) },
                                                        onClick = {
                                                            gender = opt
                                                            genderExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedTextField(
                                                value = city,
                                                onValueChange = { city = it },
                                                label = { Text("City") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                colors = tfColors,
                                                isError = attempted && !cityOk,
                                                supportingText = { if (attempted && !cityOk) Text("Required") }
                                            )
                                            OutlinedTextField(
                                                value = state,
                                                onValueChange = { state = it },
                                                label = { Text("State") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                colors = tfColors,
                                                isError = attempted && !stateOk,
                                                supportingText = { if (attempted && !stateOk) Text("Required") }
                                            )
                                        }

                                        Button(
                                            onClick = { validateManualAndContinue() },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp),
                                            shape = RoundedCornerShape(18.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                                contentColor = Color.Black
                                            ),
                                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp, pressedElevation = 2.dp)
                                        ) { Text("Continue") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}