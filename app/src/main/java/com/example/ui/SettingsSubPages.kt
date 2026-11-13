package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.network.FirebaseUpdateManager
import com.example.network.UpdateStatus
import com.example.data.DatabaseProvider
import com.example.util.CsvBackupHelper
import com.example.util.CurrencyFormatter
import com.example.util.MoneyMath
import com.example.util.UpiQrCodeHelper
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// 1. LanguageSubPage
// ==========================================
@Composable
fun LanguageSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = translate("Select which language of display you want to apply globally.", language),
                fontSize = 11.sp,
                color = Color.DarkGray
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val langs = listOf(
                    "English" to "English (Default)",
                    "Tamil" to "தமிழ் (Tamil)",
                    "Hindi" to "हिन्दी (Hindi)",
                    "Telugu" to "తెలుగు (Telugu)"
                )

                langs.forEach { (code, label) ->
                    val isSel = language == code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSel) ColorSlateDark.copy(alpha = 0.05f) else Color.White,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSel) ColorSlateDark else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.setLanguage(code) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ColorSlateDark
                        )

                        RadioButton(
                            selected = isSel,
                            onClick = { viewModel.setLanguage(code) },
                            colors = RadioButtonDefaults.colors(selectedColor = ColorSlateDark)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. FontSizeSubPage
// ==========================================
@Composable
fun FontSizeSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    fontSizeScale: Float,
    fontStyleKey: String = "SANS_SERIF"
) {
    val isDark = appColors.isDark
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val cardBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val textColor = if (isDark) Color.White else ColorSlateDark
    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color.DarkGray
    val activeFontFamily = com.example.ui.theme.getAppFontFamily(fontStyleKey)

    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Section 0: 3 Pro Color Theme Palettes
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = translate("Pro Color Theme Palettes", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = appColors.primaryAccent
                )
                Text(
                    text = translate("Choose a field-tested color theme optimized for readability, contrast, and battery life.", language),
                    fontSize = 11.sp,
                    color = subTextColor
                )

                data class ThemeOptionSpec(
                    val key: String,
                    val title: String,
                    val subtitle: String,
                    val swatches: List<Pair<String, Color>>
                )

                val themeOptions = listOf(
                    ThemeOptionSpec(
                        key = "Fintech Emerald",
                        title = "Option 1: \"Fintech Emerald & Royal Slate\" 🏆 (Recommended)",
                        subtitle = "Inspired by Stripe & Mercury Bank — modern, clean, trustworthy, and ultra-crisp.",
                        swatches = listOf(
                            "Header" to Color(0xFF0F172A),
                            "Collect" to Color(0xFF059669),
                            "Disburse" to Color(0xFF2563EB),
                            "Profit" to Color(0xFF4F46E5),
                            "Deduct" to Color(0xFF9333EA),
                            "Alert" to Color(0xFFDC2626)
                        )
                    ),
                    ThemeOptionSpec(
                        key = "Sunlight Contrast",
                        title = "Option 2: \"Outdoor Sunlight High-Contrast\" ☀️ (Best for Direct Sun & Glare)",
                        subtitle = "Engineered for maximum legibility outdoors on a bike in bright afternoon sun.",
                        swatches = listOf(
                            "Header" to Color(0xFF020617),
                            "Collect" to Color(0xFF15803D),
                            "Disburse" to Color(0xFF1D4ED8),
                            "Profit" to Color(0xFFB45309),
                            "Deduct" to Color(0xFF7E22CE),
                            "Alert" to Color(0xFFB91C1C)
                        )
                    ),
                    ThemeOptionSpec(
                        key = "Midnight AMOLED",
                        title = "Option 3: \"Midnight AMOLED / Stealth OLED\" 🌙 (Night & Battery Saver)",
                        subtitle = "Saves OLED battery and looks sleek during evening/Sunday eve collections.",
                        swatches = listOf(
                            "Header" to Color(0xFF090D16),
                            "Card" to Color(0xFF1E293B),
                            "Collect" to Color(0xFF10B981),
                            "Disburse" to Color(0xFF38BDF8),
                            "Profit" to Color(0xFFFBBF24),
                            "Deduct" to Color(0xFFC084FC)
                        )
                    )
                )

                themeOptions.forEach { spec ->
                    val isSel = selectedTheme.equals(spec.key, ignoreCase = true) ||
                        (spec.key == "Fintech Emerald" && (selectedTheme == "Emerald Green" || selectedTheme == "Royal Blue"))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSel) appColors.primaryAccent.copy(alpha = 0.08f) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = if (isSel) 2.dp else 1.dp,
                                color = if (isSel) appColors.primaryAccent else cardBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.setSelectedTheme(spec.key) }
                            .padding(12.dp)
                            .testTag("theme_palette_${spec.key.lowercase(Locale.US).replace(" ", "_")}"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = spec.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = textColor
                                )
                                Text(
                                    text = spec.subtitle,
                                    fontSize = 10.5.sp,
                                    color = subTextColor,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            RadioButton(
                                selected = isSel,
                                onClick = { viewModel.setSelectedTheme(spec.key) },
                                colors = RadioButtonDefaults.colors(selectedColor = appColors.primaryAccent)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            spec.swatches.forEach { (roleName, swatchColor) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .background(
                                            if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .border(1.dp, cardBorder, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(swatchColor, CircleShape)
                                    )
                                    Text(
                                        text = roleName,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 1: Font Style Choice
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = translate("Font Style (Typeface Family)", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = appColors.primaryAccent
                )
                Text(
                    text = translate("Select an in-built font family style to apply across all app screens, titles, ledgers and receipts.", language),
                    fontSize = 11.sp,
                    color = subTextColor
                )

                val fontStyles = listOf(
                    "SANS_SERIF" to ("Sans-Serif (Standard & Clean)" to "Clean, modern high-legibility geometric font"),
                    "SERIF" to ("Serif (Classic & Editorial)" to "Traditional, elegant serif letterforms"),
                    "MONOSPACE" to ("Monospace (Digital & Ledger)" to "Fixed-width digits for crisp accounting figures"),
                    "CURSIVE" to ("Cursive (Stylish Script)" to "Flowing decorative display typography"),
                    "DEFAULT" to ("System Default" to "Native Android OS default system typeface")
                )

                fontStyles.forEach { (key, pair) ->
                    val (label, desc) = pair
                    val isSel = fontStyleKey.equals(key, ignoreCase = true)
                    val styleFontFamily = com.example.ui.theme.getAppFontFamily(key)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSel) appColors.primaryAccent.copy(alpha = 0.08f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = if (isSel) 2.dp else 1.dp,
                                color = if (isSel) appColors.primaryAccent else cardBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.setFontStyleKey(key) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = translate(label, language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = styleFontFamily,
                                color = textColor
                            )
                            Text(
                                text = "Sample: MD Finance ₹1,25,000 — $desc",
                                fontSize = 10.sp,
                                fontFamily = styleFontFamily,
                                color = subTextColor,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        RadioButton(
                            selected = isSel,
                            onClick = { viewModel.setFontStyleKey(key) },
                            colors = RadioButtonDefaults.colors(selectedColor = appColors.primaryAccent)
                        )
                    }
                }
            }
        }

        // Section 2: Font Size Scale
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = translate("Font Size Scale", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = appColors.primaryAccent
                )
                Text(
                    text = translate("Select text typography scale setting to fit comfortably on small screens.", language),
                    fontSize = 11.sp,
                    color = subTextColor
                )

                val sizes = listOf(
                    "Tiny" to ("Tiny / Extra Compact (0.75x)" to 0.75f),
                    "Compact" to ("Small / Compact (0.90x)" to 0.90f),
                    "Regular" to ("Regular / Comfort (1.00x)" to 1.00f),
                    "Normal" to ("Normal / Standard (1.15x)" to 1.15f),
                    "Medium" to ("Medium / Readable (1.25x)" to 1.25f),
                    "Large" to ("Large / Enlarged (1.35x)" to 1.35f),
                    "Extra Large" to ("Extra Large / XL (1.50x)" to 1.50f),
                    "Huge" to ("Huge / High Visibility (1.65x)" to 1.65f),
                    "Gigantic" to ("Gigantic / Maximum Zoom (1.80x)" to 1.80f)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sizes.forEach { (_, pair) ->
                        val (label, targetScale) = pair
                        val isSel = kotlin.math.abs(fontSizeScale - targetScale) < 0.051f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isSel) appColors.primaryAccent.copy(alpha = 0.08f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (isSel) 2.dp else 1.dp,
                                    color = if (isSel) appColors.primaryAccent else cardBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.setFontSizeScale(targetScale) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = translate(label, language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = activeFontFamily,
                                color = textColor
                            )

                            RadioButton(
                                selected = isSel,
                                onClick = { viewModel.setFontSizeScale(targetScale) },
                                colors = RadioButtonDefaults.colors(selectedColor = appColors.primaryAccent)
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Live Preview Across Display Places
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = translate("Typography Live Preview Across Display Places", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = appColors.primaryAccent
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Display Place 1: App Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = translate("Header Title", language),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = subTextColor
                            )
                            Text(
                                text = "MD Finance App",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                fontFamily = activeFontFamily,
                                color = textColor
                            )
                        }

                        HorizontalDivider(color = cardBorder.copy(alpha = 0.5f))

                        // Display Place 2: Financial Currency Metric
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = translate("Dashboard Collection", language),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = subTextColor
                            )
                            Text(
                                text = "₹ 1,25,000.00",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                fontFamily = activeFontFamily,
                                color = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
                            )
                        }

                        HorizontalDivider(color = cardBorder.copy(alpha = 0.5f))

                        // Display Place 3: Borrower List Item
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = translate("Customer List Item", language),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = subTextColor
                            )
                            Text(
                                text = "S. Murugan (Route #101)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = activeFontFamily,
                                color = textColor
                            )
                            Text(
                                text = translate("Week 12 • Paid ₹1,000", language),
                                fontSize = 11.sp,
                                fontFamily = activeFontFamily,
                                color = subTextColor
                            )
                        }

                        HorizontalDivider(color = cardBorder.copy(alpha = 0.5f))

                        // Display Place 4: Badges & Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFF22C55E).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "ACTIVE LOAN",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    fontFamily = activeFontFamily,
                                    color = Color(0xFF15803D),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = translate("Collect Instalment", language),
                                    fontSize = 11.sp,
                                    fontFamily = activeFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. ResetSubPage
// ==========================================
@Composable
fun ResetSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    context: Context
) {
    var showDiag by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = translate("Emergency ledger control resets. This resets local databases to clear space.", language),
                fontSize = 11.sp,
                color = Color.DarkGray
            )

            Button(
                onClick = { showDiag = true },
                colors = ButtonDefaults.buttonColors(containerColor = ColorLossRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = translate("RESET LOCAL LEDGER DATABASE", language),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showDiag) {
        AlertDialog(
            onDismissRequest = { showDiag = false },
            title = {
                Text(
                    text = translate("⚠️ Absolute Reset Warning", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ColorLossRed
                )
            },
            text = {
                Text(
                    text = translate("Are you absolutely sure you want to completely erase the local database? All your clients, loan entries and weekly transaction logs will be permanently deleted from this device. Please create a CSV backup first.", language),
                    fontSize = 12.sp,
                    color = Color.Black
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiag = false
                        viewModel.clearAllLocalData(
                            context = context,
                            onSuccess = {
                                Toast.makeText(context, translate("All data successfully deleted!", language), Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, "${translate("Delete failed:", language)} $err", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorLossRed)
                ) {
                    Text(translate("Wipe All Data", language), fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDiag = false },
                    border = BorderStroke(1.dp, ColorSlateDark)
                ) {
                    Text(translate("Cancel", language), color = ColorSlateDark)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// ==========================================
// 4. SIMSubPage
// ==========================================
@Composable
fun SIMSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    simSelection: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = translate("Select which SIM Card is default on your physical Android slot for delivery reminders.", language),
                fontSize = 11.sp,
                color = Color.DarkGray
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val slots = listOf(
                    "Ask Always" to "Default Slot (Ask Always)",
                    "SIM 1" to "SIM card Slot 1",
                    "SIM 2" to "SIM card Slot 2"
                )

                slots.forEach { (slotVal, label) ->
                    val isSel = simSelection == slotVal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSel) ColorSlateDark.copy(alpha = 0.05f) else Color.White,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSel) ColorSlateDark else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.setSimSelection(slotVal) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = translate(label, language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ColorSlateDark
                        )

                        RadioButton(
                            selected = isSel,
                            onClick = { viewModel.setSimSelection(slotVal) },
                            colors = RadioButtonDefaults.colors(selectedColor = ColorSlateDark)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. BusinessUpiSubPage
// ==========================================
@Composable
fun BusinessUpiSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    businessName: String,
    upiId: String,
    upiLink: String,
    qrImageUri: String,
    qrPickerLauncher: ActivityResultLauncher<String>
) {
    var showQrCameraScanner by remember { mutableStateOf(false) }

    if (showQrCameraScanner) {
        QrCameraScannerDialog(
            onDismiss = { showQrCameraScanner = false },
            onQrCodeScanned = { rawPayload ->
                showQrCameraScanner = false
                viewModel.saveScannedQrCodePayload(rawPayload)
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = translate("Customize billing identities & business notification footer settings.", language),
                fontSize = 11.sp,
                color = Color.DarkGray
            )

            // Business Name
            OutlinedTextField(
                value = businessName,
                onValueChange = { viewModel.setBusinessName(it) },
                label = { Text("Business Name Signal Title (SMS footer)", fontSize = 11.sp) },
                placeholder = { Text("Example: MD Finance", fontSize = 11.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = Color.Black),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black,
                    focusedBorderColor = ColorSlateDark,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            // Merchant UPI ID
            OutlinedTextField(
                value = upiId,
                onValueChange = { viewModel.setUpiId(it) },
                label = { Text("Merchant UPI ID (Auto-filled when you scan QR code)", fontSize = 11.sp) },
                placeholder = { Text("Example: 9440736893@ptyes", fontSize = 11.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = Color.Black),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black,
                    focusedBorderColor = ColorSlateDark,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            HorizontalDivider(color = Color(0xFFE2E8F0))

            // UPI QR Code Live Camera Scanner Section
            val context = LocalContext.current
            val savedQrBitmap = remember(qrImageUri) {
                UpiQrCodeHelper.loadSavedQrBitmap(context)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = translate("Scan UPI QR Code for Statements", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ColorSlateDark
                    )
                    Text(
                        text = translate("Open camera and scan your GPay / PhonePe / Paytm QR code. We extract the QR data, auto-fill your UPI ID, and paste the clean QR code directly onto customer statements.", language),
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showQrCameraScanner = true },
                        colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("scan_upi_qr_button")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (savedQrBitmap != null) translate("Rescan UPI QR Code", language) else translate("Scan UPI QR Code", language),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Direct QR Code Preview (No background box or wrapper)
                if (savedQrBitmap != null) {
                    Image(
                        bitmap = savedQrBitmap.asImageBitmap(),
                        contentDescription = "Scanned UPI QR Code",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(84.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = "QR Placeholder",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 6. CollectionGroupsSubPage
// ==========================================
@Composable
fun CollectionGroupsSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    collectionGroups: List<String>,
    customGroupName: String,
    onCustomGroupNameChange: (String) -> Unit,
    onRenameClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val showOverdueDots by viewModel.showOverdueDots.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF7ED), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFF97316), CircleShape))
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFEF4444), CircleShape))
                    }
                    Column {
                        Text(
                            text = translate("Non-Payment Dots Under Route No", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ColorSlateDark
                        )
                        Text(
                            text = translate("Show red & orange non-payment dots under customer route number", language),
                            fontSize = 10.sp,
                            color = Color.DarkGray
                        )
                    }
                }
                Switch(
                    checked = showOverdueDots,
                    onCheckedChange = { viewModel.setShowOverdueDots(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFF97316)
                    ),
                    modifier = Modifier.testTag("groups_overdue_dots_switch")
                )
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Text(
                text = translate("Configure active route days or groups for collection cycles.", language),
                fontSize = 11.sp,
                color = Color.DarkGray
            )

            // Add Group Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customGroupName,
                    onValueChange = onCustomGroupNameChange,
                    placeholder = { Text("Add Group Name e.g. Tuesday / செவ்வாய்", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = ColorSlateDark,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = {
                        val trimmed = customGroupName.trim()
                        if (trimmed.isNotBlank()) {
                            if (collectionGroups.contains(trimmed)) {
                                // Already contains
                            } else {
                                val newList = collectionGroups.toMutableList().apply { add(trimmed) }
                                viewModel.updateCollectionGroups(newList)
                                onCustomGroupNameChange("")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text("Add", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // Reorder and list area
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                collectionGroups.forEachIndexed { index, group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = group,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ColorSlateDark,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Move Up
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val newList = collectionGroups.toMutableList().apply {
                                            val temp = get(index)
                                            set(index, get(index - 1))
                                            set(index - 1, temp)
                                        }
                                        viewModel.updateCollectionGroups(newList)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move Up",
                                    tint = if (index > 0) ColorSlateDark else Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Move Down
                            IconButton(
                                onClick = {
                                    if (index < collectionGroups.size - 1) {
                                        val newList = collectionGroups.toMutableList().apply {
                                            val temp = get(index)
                                            set(index, get(index + 1))
                                            set(index + 1, temp)
                                        }
                                        viewModel.updateCollectionGroups(newList)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move Down",
                                    tint = if (index < collectionGroups.size - 1) ColorSlateDark else Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Rename
                            IconButton(
                                onClick = { onRenameClick(group) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename group",
                                    tint = ColorSlateDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Delete
                            IconButton(
                                onClick = { onDeleteClick(group) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete group",
                                    tint = ColorLossRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. SystemUpdateSubPage
// ==========================================
@Composable
fun SystemUpdateSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    context: Context
) {
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsStateWithLifecycle()
    val forceUpdateEnabled by viewModel.forceUpdateEnabled.collectAsStateWithLifecycle()
    val pauseUpdatesEnabled by viewModel.pauseUpdatesEnabled.collectAsStateWithLifecycle()

    // Observe AppUpdateManager update states
    val updateStatus by FirebaseUpdateManager.updateStatus.collectAsStateWithLifecycle()
    val latestVersionCodeState by FirebaseUpdateManager.latestVersionCode.collectAsStateWithLifecycle()
    val updateErrorState by FirebaseUpdateManager.updateError.collectAsStateWithLifecycle()
    val latestVersionCode = latestVersionCodeState
    val latestVersionName = "Build $latestVersionCode" 
    val updateError = updateErrorState

    val currentVersionCode = try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            pInfo.versionCode.toLong()
        }
    } catch (e: Exception) {
        1L
    }

    val currentVersionName = try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Version Info & Update Status Box
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth().testTag("version_metadata_box")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = translate("Application Version Details", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = translate("Current details of the installed application package", language),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = appColors.primaryAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Grid layout for Version Code & Name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Version Name box
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = translate("Version Name", language),
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentVersionName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.primaryAccent
                            )
                        }
                    }

                    // Version Code box
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = translate("Version Code", language),
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "#$currentVersionCode",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.primaryAccent
                            )
                        }
                    }
                }

                // Dynamic Live Update Status Panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    when (updateStatus) {
                        UpdateStatus.IDLE -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = translate("Standby / Idle", language),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.DarkGray
                                )
                            }
                        }
                        UpdateStatus.CHECKING -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = appColors.primaryAccent,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = translate("Checking for new update on Cloud...", language),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = appColors.primaryAccent
                                )
                            }
                        }
                        UpdateStatus.DOWNLOADING -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = translate("Downloading update version v$latestVersionCode... Please wait.", language),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFD97706)
                                )
                            }
                        }
                        UpdateStatus.DOWNLOADED -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = translate("New update fully downloaded & ready!", language),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF047857)
                                    )
                                }
                                Button(
                                    onClick = { FirebaseUpdateManager.triggerInstall(context, latestVersionCode) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = translate("INSTALL NOW", language),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        UpdateStatus.UPDATE_AVAILABLE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = translate("New Version v$latestVersionCode is available!", language),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB)
                                    )
                                }
                            }
                        }
                        UpdateStatus.UP_TO_DATE -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = translate("App is fully up-to-date (v$currentVersionCode)", language),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857)
                                )
                            }
                        }
                        UpdateStatus.FAILED -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.Red,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = translate("Error checking/downloading updates.", language),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Red
                                    )
                                }
                                if (!updateError.isNullOrEmpty()) {
                                    Text(
                                        text = updateError,
                                        fontSize = 11.sp,
                                        color = Color.Red
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Manual Check Button Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = translate("Check for Updates Manually", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Black
                )
                Text(
                    text = translate("Instantly query Firebase RTDB for any newer APK releases and start automated installation with secure rollback backup.", language),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Button(
                    onClick = {
                        Toast.makeText(context, "Contacting Firebase Realtime Database...", Toast.LENGTH_SHORT).show()
                        FirebaseUpdateManager.checkForCloudUpdates(context, manualCheck = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("check_updates_manual_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Check",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = translate("CHECK FOR UPDATES NOW", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
                
                Button(
                    onClick = {
                        FirebaseUpdateManager.deleteDownloadedUpdate(context)
                        FirebaseUpdateManager.checkForCloudUpdates(context, manualCheck = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = translate("DELETE DOWNLOAD & RE-CHECK", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Settings Toggles Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = translate("OTA Auto-Update Parameters", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Black
                )

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Toggle 1: Auto-Update Enabled
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = translate("Auto Download & Install", language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Text(
                            text = translate("Periodically checks and initiates background download when updates exist.", language),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = autoUpdateEnabled,
                        onCheckedChange = { viewModel.setAutoUpdateEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = appColors.primaryAccent
                        ),
                        modifier = Modifier.testTag("auto_update_enabled_switch")
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Toggle 2: Force Updates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = translate("Force Updates", language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Text(
                            text = translate("Aggressively request installation immediately upon new release detection.", language),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = forceUpdateEnabled,
                        onCheckedChange = { viewModel.setForceUpdateEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = appColors.primaryAccent
                        ),
                        modifier = Modifier.testTag("force_update_enabled_switch")
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Toggle 3: Pause All Updates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = translate("Pause All Updates", language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Text(
                            text = translate("Temporarily halt background checks and automated OTA downloads.", language),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = pauseUpdatesEnabled,
                        onCheckedChange = { viewModel.setPauseUpdatesEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = appColors.primaryAccent
                        ),
                        modifier = Modifier.testTag("pause_updates_enabled_switch")
                    )
                }
            }
        }
    }
}

// ==========================================
// 8. GoogleContactsSyncSubPage
// ==========================================
@Composable
fun GoogleContactsSyncSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    context: Context
) {
    val syncEnabled by viewModel.googleContactsSyncEnabled.collectAsStateWithLifecycle()
    val selectedAccount by viewModel.googleContactsSelectedAccount.collectAsStateWithLifecycle()
    val accountsList by viewModel.googleContactsAccountsList.collectAsStateWithLifecycle()

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val writeOk = permissions[Manifest.permission.WRITE_CONTACTS] == true
        val readOk = permissions[Manifest.permission.READ_CONTACTS] == true
        permissionGranted = writeOk && readOk
        if (permissionGranted) {
            viewModel.fetchGoogleAccounts()
            Toast.makeText(context, "Contacts permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissions denied. Cannot sync contacts.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (permissionGranted) {
            viewModel.fetchGoogleAccounts()
        }
    }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrBlank()) {
                viewModel.setGoogleContactsSelectedAccount(accountName)
                viewModel.setGoogleContactsSyncEnabled(true)
                Toast.makeText(context, "Account selected: $accountName. Contacts sync enabled!", Toast.LENGTH_LONG).show()
            }
        }
    }

    val triggerAccountPicker = {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.accounts.AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    arrayOf("com.google"),
                    null,
                    null,
                    null,
                    null
                )
            } else {
                @Suppress("DEPRECATION")
                android.accounts.AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    arrayOf("com.google"),
                    false,
                    null,
                    null,
                    null,
                    null
                )
            }
            accountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening Google account picker: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    var showDropdown by remember { mutableStateOf(false) }
    var manualAccountEmail by remember { mutableStateOf(selectedAccount) }

    // Sync progress tracking states
    var isSyncingAll by remember { mutableStateOf(false) }
    var syncProgressCurrent by remember { mutableStateOf(0) }
    var syncProgressTotal by remember { mutableStateOf(0) }
    var syncResultMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Permission Status Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (permissionGranted) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (permissionGranted) Color(0xFFBBF7D0) else Color(0xFFFCA5A5)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (permissionGranted) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = "Permission Status",
                    tint = if (permissionGranted) Color(0xFF16A34A) else Color(0xFFDC2626),
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (permissionGranted) translate("Contacts Permission Active", language) else translate("Contacts Permission Required", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (permissionGranted) Color(0xFF15803D) else Color(0xFF991B1B)
                    )
                    Text(
                        text = if (permissionGranted) translate("The app has secure system access to update your contact books.", language) else translate("Please grant read/write permissions to synchronize client details directly to your account contacts.", language),
                        fontSize = 12.sp,
                        color = if (permissionGranted) Color(0xFF166534) else Color(0xFF7F1D1D)
                    )
                }
                if (!permissionGranted) {
                    Button(
                        onClick = {
                            launcher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.WRITE_CONTACTS
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("request_contacts_permission_btn")
                    ) {
                        Text(
                            text = translate("GRANT", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Feature Toggle Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = translate("Enable Contacts Sync", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                        Text(
                            text = translate("When enabled, creating or editing client mobile numbers automatically pushes correct details to Google Contacts.", language),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = syncEnabled,
                        onCheckedChange = {
                            if (!permissionGranted && it) {
                                Toast.makeText(context, "Grant permissions first to enable syncing.", Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.setGoogleContactsSyncEnabled(it)
                                if (it && selectedAccount.isBlank()) {
                                    triggerAccountPicker()
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = appColors.primaryAccent
                        ),
                        modifier = Modifier.testTag("contacts_sync_enabled_switch")
                    )
                }

                if (syncEnabled) {
                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    Text(
                        text = translate("Google Sync Account", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (selectedAccount.isNotBlank()) selectedAccount else translate("No Google Account Selected", language),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedAccount.isNotBlank()) appColors.primaryAccent else Color.Gray
                            )

                            Button(
                                onClick = { triggerAccountPicker() },
                                colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("select_google_account_popup_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContactPage,
                                    contentDescription = "Google Accounts Popup",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = translate("CHOOSE GOOGLE ACCOUNT VIA POP-UP", language),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Manual Email input fallback if needed
                    OutlinedTextField(
                        value = manualAccountEmail,
                        onValueChange = {
                            manualAccountEmail = it
                            viewModel.setGoogleContactsSelectedAccount(it)
                        },
                        label = { Text(translate("Or Enter Google Account Manually", language)) },
                        placeholder = { Text("e.g. name@gmail.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("google_account_manual_input")
                    )
                }
            }
        }

        // Full Sync Command Card
        if (syncEnabled && selectedAccount.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = translate("Manual Full Database Sync", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.Black
                    )
                    Text(
                        text = translate("Sync all existing clients with active phone numbers directly to Google Contacts now. This resolves any unsynced names or newly updated telephone entries.", language),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    if (isSyncingAll) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val progress = if (syncProgressTotal > 0) syncProgressCurrent.toFloat() / syncProgressTotal else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = appColors.primaryAccent,
                                trackColor = Color(0xFFE2E8F0)
                            )
                            Text(
                                text = "Syncing contact $syncProgressCurrent of $syncProgressTotal...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.primaryAccent
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                isSyncingAll = true
                                syncResultMessage = null
                                viewModel.syncAllBorrowersToGoogleContacts(
                                    onProgress = { current, total ->
                                        syncProgressCurrent = current
                                        syncProgressTotal = total
                                    },
                                    onComplete = { success, msg ->
                                        isSyncingAll = false
                                        syncResultMessage = msg
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("sync_all_contacts_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Now",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = translate("SYNC ALL BORROWERS NOW", language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }

                    syncResultMessage?.let { msg ->
                        Text(
                            text = msg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (msg.contains("success", ignoreCase = true) || msg.contains("synced", ignoreCase = true)) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. TemplatesSubPage & Interactive Message Format Studio
// ==========================================
data class MessageBubbleTag(
    val token: String,
    val displayLabel: String,
    val altTokens: List<String> = emptyList()
)

val MESSAGE_BUBBLE_TAGS = listOf(
    MessageBubbleTag(token = "{customer}", displayLabel = "Name", altTokens = listOf("{name}")),
    MessageBubbleTag(token = "{amount}", displayLabel = "Amount", altTokens = listOf("{p_amount}")),
    MessageBubbleTag(token = "{balance}", displayLabel = "Balance", altTokens = listOf("{outstanding}")),
    MessageBubbleTag(token = "{inst_amt}", displayLabel = "Instalment Amt", altTokens = listOf("{weekly}")),
    MessageBubbleTag(token = "{date}", displayLabel = "Date"),
    MessageBubbleTag(token = "{business}", displayLabel = "Business Name"),
    MessageBubbleTag(token = "{upi}", displayLabel = "UPI ID"),
    MessageBubbleTag(token = "{phone}", displayLabel = "Phone Number", altTokens = listOf("{phone_number}"))
)

val STATEMENT_BUBBLE_TAGS = listOf(
    MessageBubbleTag(token = "{business}", displayLabel = "Business Name"),
    MessageBubbleTag(token = "{customer}", displayLabel = "Customer Name", altTokens = listOf("{name}")),
    MessageBubbleTag(token = "{phone}", displayLabel = "Phone Number", altTokens = listOf("{phone_number}")),
    MessageBubbleTag(token = "{day}", displayLabel = "Collection Day"),
    MessageBubbleTag(token = "{city}", displayLabel = "City"),
    MessageBubbleTag(token = "{route}", displayLabel = "Route No"),
    MessageBubbleTag(token = "{start_date}", displayLabel = "Dispersal Date"),
    MessageBubbleTag(token = "{loan_amt}", displayLabel = "Principal Amt"),
    MessageBubbleTag(token = "{interest}", displayLabel = "Interest Amt"),
    MessageBubbleTag(token = "{total_due}", displayLabel = "Total Contract"),
    MessageBubbleTag(token = "{inst_amt}", displayLabel = "Weekly Instalment", altTokens = listOf("{weekly}")),
    MessageBubbleTag(token = "{paid_amt}", displayLabel = "Amt Paid", altTokens = listOf("{amount}", "{p_amount}")),
    MessageBubbleTag(token = "{balance}", displayLabel = "Outstanding", altTokens = listOf("{outstanding}")),
    MessageBubbleTag(token = "{weeks}", displayLabel = "Total Weeks"),
    MessageBubbleTag(token = "{date}", displayLabel = "Today Date"),
    MessageBubbleTag(token = "{upi}", displayLabel = "UPI ID")
)

/**
 * VisualTransformation that hides raw `{` and `}` brackets inside the editor and renders
 * each wildcard token as a clean highlighted bubble pill (e.g. ` Name `, ` Phone Number `).
 */
class CleanBubbleVisualTransformation(private val appColors: AppThemeColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val isDark = appColors.isDark
        val bubbleBg = if (isDark) Color(0xFF312E81) else Color(0xFFE0E7FF)
        val bubbleText = if (isDark) Color(0xFFC7D2FE) else Color(0xFF4F46E5)

        val tokenLabelMap = linkedMapOf(
            "{customer}" to " Name ",
            "{name}" to " Name ",
            "{amount}" to " Amount ",
            "{p_amount}" to " Amount ",
            "{paid_amt}" to " Amt Paid ",
            "{balance}" to " Balance ",
            "{outstanding}" to " Balance ",
            "{inst_amt}" to " Instalment Amt ",
            "{weekly}" to " Instalment Amt ",
            "{loan_amt}" to " Principal Amt ",
            "{interest}" to " Interest Amt ",
            "{total_due}" to " Total Contract ",
            "{start_date}" to " Dispersal Date ",
            "{weeks}" to " Total Weeks ",
            "{day}" to " Collection Day ",
            "{city}" to " City ",
            "{route}" to " Route No ",
            "{date}" to " Date ",
            "{business}" to " Business Name ",
            "{upi}" to " UPI ID ",
            "{phone}" to " Phone Number ",
            "{phone_number}" to " Phone Number ",
            "{upi_link}" to " Phone Number "
        )

        data class TokenMatch(val start: Int, val end: Int, val replacement: String)
        val matches = mutableListOf<TokenMatch>()

        for ((token, label) in tokenLabelMap) {
            var idx = raw.indexOf(token)
            while (idx != -1) {
                val endIdx = idx + token.length
                if (matches.none { idx < it.end && endIdx > it.start }) {
                    matches.add(TokenMatch(idx, endIdx, label))
                }
                idx = raw.indexOf(token, idx + 1)
            }
        }
        matches.sortBy { it.start }

        if (matches.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder()
        val origToTrans = IntArray(raw.length + 1)
        val transToOrigList = mutableListOf<Int>()

        var rawCursor = 0
        var transCursor = 0

        for (m in matches) {
            while (rawCursor < m.start) {
                origToTrans[rawCursor] = transCursor
                transToOrigList.add(rawCursor)
                builder.append(raw[rawCursor])
                rawCursor++
                transCursor++
            }
            val spanStart = transCursor
            for (i in m.start until m.end) {
                origToTrans[i] = spanStart
            }
            for (ch in m.replacement) {
                transToOrigList.add(m.start)
                builder.append(ch)
                transCursor++
            }
            builder.addStyle(
                style = SpanStyle(
                    color = bubbleText,
                    background = bubbleBg,
                    fontWeight = FontWeight.ExtraBold
                ),
                start = spanStart,
                end = transCursor
            )
            rawCursor = m.end
        }

        while (rawCursor < raw.length) {
            origToTrans[rawCursor] = transCursor
            transToOrigList.add(rawCursor)
            builder.append(raw[rawCursor])
            rawCursor++
            transCursor++
        }
        origToTrans[raw.length] = transCursor
        transToOrigList.add(raw.length)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return origToTrans[offset.coerceIn(0, raw.length)]
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (transToOrigList.isEmpty()) return 0
                return transToOrigList[offset.coerceIn(0, transToOrigList.lastIndex)]
            }
        }

        return TransformedText(builder.toAnnotatedString(), offsetMapping)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveMessageBubbleEditor(
    value: String,
    onValueChange: (String) -> Unit,
    language: String,
    appColors: AppThemeColors,
    textColor: Color,
    subTextColor: Color,
    cardBorder: Color,
    modifier: Modifier = Modifier,
    bubbleTags: List<MessageBubbleTag> = MESSAGE_BUBBLE_TAGS
) {
    // Normalize legacy tokens so the canonical bubble tags govern them cleanly
    val normalizedValue = remember(value) {
        value
            .replace("{name}", "{customer}")
            .replace("{upi_link}", "{phone}")
            .replace("{phone_number}", "{phone}")
    }
    var textFieldValueState by remember {
        mutableStateOf(TextFieldValue(text = normalizedValue, selection = TextRange(normalizedValue.length)))
    }

    LaunchedEffect(normalizedValue) {
        if (textFieldValueState.text != normalizedValue) {
            val safeCursor = textFieldValueState.selection.start.coerceIn(0, normalizedValue.length)
            textFieldValueState = TextFieldValue(
                text = normalizedValue,
                selection = TextRange(safeCursor)
            )
        }
    }

    val currentText = textFieldValueState.text

    // Available bubbles (NOT currently inside the text box) vs Selected bubbles (currently inside the text box)
    val availableBubbles = remember(currentText, bubbleTags) {
        bubbleTags.filter { tag ->
            !currentText.contains(tag.token) && tag.altTokens.none { currentText.contains(it) }
        }
    }
    val activeBubbles = remember(currentText, bubbleTags) {
        bubbleTags.filter { tag ->
            currentText.contains(tag.token) || tag.altTokens.any { currentText.contains(it) }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. TOP BUBBLE TRAY: Available bubbles above the text box (tap to insert at cursor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (appColors.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                    RoundedCornerShape(10.dp)
                )
                .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = translate("Tap a Bubble to Insert at Cursor:", language),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.primaryAccent
                )
                Text(
                    text = "${availableBubbles.size} available",
                    fontSize = 10.sp,
                    color = subTextColor
                )
            }

            if (availableBubbles.isEmpty()) {
                Text(
                    text = translate("All bubbles are in the message. Tap any bubble in the Selected Bubbles list below to remove it.", language),
                    fontSize = 11.sp,
                    color = subTextColor
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableBubbles.forEach { tag ->
                        Surface(
                            onClick = {
                                val raw = textFieldValueState.text
                                val cursor = textFieldValueState.selection.start.coerceIn(0, raw.length)
                                val prefix = raw.substring(0, cursor)
                                val suffix = raw.substring(cursor)
                                val needsLeadingSpace = prefix.isNotEmpty() && !prefix.last().isWhitespace()
                                val needsTrailingSpace = suffix.isNotEmpty() && !suffix.first().isWhitespace() && suffix.first() !in listOf('.', ',', '!', '?', ':')
                                val inserted = buildString {
                                    if (needsLeadingSpace) append(" ")
                                    append(tag.token)
                                    if (needsTrailingSpace) append(" ")
                                }
                                val newText = prefix + inserted + suffix
                                val newCursor = (prefix.length + inserted.length).coerceIn(0, newText.length)
                                textFieldValueState = TextFieldValue(
                                    text = newText,
                                    selection = TextRange(newCursor)
                                )
                                onValueChange(newText)
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = appColors.primaryAccent.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, appColors.primaryAccent.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = appColors.primaryAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = tag.displayLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = appColors.primaryAccent
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. FORMAT EDIT BOX: Tapping inside a bubble in the text box moves the cursor only (does NOT remove the bubble)
        OutlinedTextField(
            value = textFieldValueState,
            onValueChange = { newValue ->
                textFieldValueState = newValue
                if (newValue.text != normalizedValue) {
                    onValueChange(newValue.text)
                }
            },
            label = {
                Text(
                    text = translate("Message Format Box (Press Enter for a new line)", language),
                    fontSize = 11.sp
                )
            },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedLabelColor = appColors.primaryAccent,
                unfocusedLabelColor = subTextColor,
                focusedBorderColor = appColors.primaryAccent,
                unfocusedBorderColor = cardBorder
            ),
            visualTransformation = remember(appColors) { CleanBubbleVisualTransformation(appColors) },
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Backspace || event.nativeKeyEvent.keyCode == 67)) {
                        val selection = textFieldValueState.selection
                        if (selection.collapsed) {
                            val cursor = selection.start
                            val text = textFieldValueState.text
                            for (tag in bubbleTags) {
                                for (wildcard in (listOf(tag.token) + tag.altTokens)) {
                                    var index = text.indexOf(wildcard)
                                    while (index != -1) {
                                        val start = index
                                        val end = index + wildcard.length
                                        if (cursor in (start + 1)..end) {
                                            val newText = text.substring(0, start) + text.substring(end)
                                            textFieldValueState = TextFieldValue(text = newText, selection = TextRange(start))
                                            onValueChange(newText)
                                            return@onPreviewKeyEvent true
                                        }
                                        index = text.indexOf(wildcard, index + 1)
                                    }
                                }
                            }
                        }
                    }
                    false
                },
            minLines = 4,
            maxLines = 8
        )

        // 3. SELECTED BUBBLES LIST BELOW TEXT BOX (Tap one or multiple bubbles here to remove them from the message)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (appColors.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                    RoundedCornerShape(10.dp)
                )
                .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = translate("Selected Bubbles in Message (Tap below to remove):", language),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = "${activeBubbles.size} selected",
                    fontSize = 10.sp,
                    color = subTextColor
                )
            }
            if (activeBubbles.isEmpty()) {
                Text(
                    text = translate("No bubbles currently selected in message.", language),
                    fontSize = 10.5.sp,
                    color = subTextColor
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeBubbles.forEach { tag ->
                        Surface(
                            onClick = {
                                var updated = textFieldValueState.text
                                (listOf(tag.token) + tag.altTokens).forEach { tok ->
                                    updated = updated.replace(tok, "")
                                }
                                updated = updated
                                    .split("\n")
                                    .joinToString("\n") { line -> line.replace("  ", " ").trim() }
                                val newCursor = textFieldValueState.selection.start.coerceIn(0, updated.length)
                                textFieldValueState = TextFieldValue(text = updated, selection = TextRange(newCursor))
                                onValueChange(updated)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (appColors.isDark) Color(0xFF312E81) else Color(0xFFE0E7FF),
                            border = BorderStroke(1.dp, appColors.primaryAccent.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = tag.displayLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (appColors.isDark) Color(0xFFC7D2FE) else Color(0xFF4F46E5)
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove ${tag.displayLabel}",
                                    tint = if (appColors.isDark) Color(0xFFC7D2FE) else Color(0xFF4F46E5),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. LINE-BY-LINE STRUCTURE BREAKDOWN: Explicitly shows which text is on Line 1, Line 2, Line 3 vs wrapped continuation
        val templateLines = remember(currentText) {
            currentText.split("\n")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (appColors.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                    RoundedCornerShape(10.dp)
                )
                .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = translate("Line-by-Line Structure (New Line vs Wrap Continuation):", language),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.primaryAccent
                )
                Text(
                    text = "${templateLines.size} ${if (templateLines.size == 1) "Line" else "Lines"}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = subTextColor
                )
            }
            templateLines.forEachIndexed { idx, rawLine ->
                val readableLine = remember(rawLine, bubbleTags) {
                    var s = rawLine
                    bubbleTags.forEach { tag ->
                        s = s.replace(tag.token, "[${tag.displayLabel}]")
                        tag.altTokens.forEach { alt -> s = s.replace(alt, "[${tag.displayLabel}]") }
                    }
                    s
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (appColors.isDark) Color(0xFF1E293B) else Color.White,
                            RoundedCornerShape(6.dp)
                        )
                        .border(1.dp, cardBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(appColors.primaryAccent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Line ${idx + 1}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = appColors.primaryAccent
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = readableLine.ifBlank { "(Blank line)" },
                            fontSize = 11.5.sp,
                            color = if (readableLine.isBlank()) subTextColor else textColor,
                            lineHeight = 16.sp
                        )
                        if (readableLine.length > 36) {
                            Text(
                                text = "↳ Single line (wraps automatically if long)",
                                fontSize = 9.sp,
                                color = subTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TemplatesSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    smsPaused: Boolean,
    currentTemplateLang: String,
    smsNewLoanTemplate: String,
    smsPaymentTemplate: String,
    smsReminderTemplate: String,
    whatsappReminderTemplate: String
) {
    var showCustomerSmsSettings by rememberSaveable { mutableStateOf(false) }
    var showMessageFormatList by rememberSaveable { mutableStateOf(false) }
    var selectedMessagePatternIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    val collectionGroups by viewModel.collectionGroups.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val allLoanCycles by viewModel.allLoanCycles.collectAsStateWithLifecycle()
    val customAppName by viewModel.customAppName.collectAsStateWithLifecycle()
    val upiId by viewModel.upiId.collectAsStateWithLifecycle()

    val daysList = remember(collectionGroups) {
        getOrderedDaysList(collectionGroups).filter {
            !it.equals("Friday", ignoreCase = true) && !it.equals("Home", ignoreCase = true)
        }
    }
    var selectedSmsDay by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(daysList) {
        if (selectedSmsDay.isBlank() && daysList.isNotEmpty()) {
            selectedSmsDay = daysList.firstOrNull { it.equals("Monday", ignoreCase = true) } ?: daysList.first()
        }
    }

    val isDark = appColors.isDark
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val cardBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val textColor = if (isDark) Color.White else ColorSlateDark
    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color.DarkGray

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (showCustomerSmsSettings) {
                // Header of Customer-wise SMS Settings
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { showCustomerSmsSettings = false }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = translate("Customer Wise SMS Settings", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = textColor
                    )
                }

                HorizontalDivider(color = cardBorder)

                // Day Selection Tab Row (Horizontal Scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    daysList.forEach { day ->
                        val isActive = day.trim().equals(selectedSmsDay.trim(), ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isActive) appColors.primaryAccent else (if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isActive) appColors.primaryAccent else cardBorder,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedSmsDay = day }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = translate(day, language).uppercase(java.util.Locale.getDefault()),
                                color = if (isActive) Color.White else (if (isDark) Color.White else Color.Black),
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                val filteredCustomers = remember(allCustomers, selectedSmsDay) {
                    allCustomers.filter {
                        it.collectionDay.trim().equals(selectedSmsDay.trim(), ignoreCase = true) &&
                            it.phone.trim().isNotEmpty()
                    }.sortedBy { it.customOrder }
                }

                if (filteredCustomers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = translate("No customers registered for this day.", language),
                            fontSize = 13.sp,
                            color = subTextColor
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        filteredCustomers.forEach { customer ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${customer.customOrder}. ${customer.name}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (!customer.city.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = customer.city,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF334155),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    viewModel.updateCustomerSmsSettings(
                                                        customer,
                                                        smsWeekly = !customer.smsWeeklyReminder,
                                                        smsConfirmation = customer.smsConfirmationOfEntry
                                                    )
                                                }
                                        ) {
                                            Checkbox(
                                                checked = customer.smsWeeklyReminder,
                                                onCheckedChange = { isChecked ->
                                                    viewModel.updateCustomerSmsSettings(
                                                        customer,
                                                        smsWeekly = isChecked,
                                                        smsConfirmation = customer.smsConfirmationOfEntry
                                                    )
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = appColors.primaryAccent)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = translate("Weekly Reminder", language),
                                                fontSize = 11.sp,
                                                color = subTextColor
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    viewModel.updateCustomerSmsSettings(
                                                        customer,
                                                        smsWeekly = customer.smsWeeklyReminder,
                                                        smsConfirmation = !customer.smsConfirmationOfEntry
                                                    )
                                                }
                                        ) {
                                            Checkbox(
                                                checked = customer.smsConfirmationOfEntry,
                                                onCheckedChange = { isChecked ->
                                                    viewModel.updateCustomerSmsSettings(
                                                        customer,
                                                        smsWeekly = customer.smsWeeklyReminder,
                                                        smsConfirmation = isChecked
                                                    )
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = appColors.primaryAccent)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = translate("Entry Confirmation", language),
                                                fontSize = 11.sp,
                                                color = subTextColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (showMessageFormatList && selectedMessagePatternIndex != null) {
                // =========================================================
                // SPLIT LIVE PREVIEW & CURSOR-AWARE BUBBLE EDITOR
                // =========================================================
                val patternIdx = selectedMessagePatternIndex!!
                val patternTitle = when (patternIdx) {
                    0 -> "1. New Loan Confirmation SMS"
                    1 -> "2. Entry Confirmation SMS"
                    2 -> "3. Weekly SMS Reminder"
                    else -> "4. Weekly WhatsApp Reminder"
                }
                val currentTemplateValue = when (patternIdx) {
                    0 -> smsNewLoanTemplate
                    1 -> smsPaymentTemplate
                    2 -> smsReminderTemplate
                    else -> whatsappReminderTemplate
                }
                val onUpdateTemplate: (String) -> Unit = { updated ->
                    when (patternIdx) {
                        0 -> viewModel.setSmsNewLoanTemplate(updated)
                        1 -> viewModel.setSmsPaymentTemplate(updated)
                        2 -> viewModel.setSmsReminderTemplate(updated)
                        else -> viewModel.setWhatsappReminderTemplate(updated)
                    }
                }

                // Header with Back to Pattern List
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { selectedMessagePatternIndex = null }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Message Formats",
                            tint = textColor
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = translate(patternTitle, language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = textColor
                        )
                        Text(
                            text = if (currentTemplateLang == "English") {
                                "Language: English (Auto-applies format to Tamil, Hindi & Telugu)"
                            } else {
                                "Language: $currentTemplateLang"
                            },
                            fontSize = 10.5.sp,
                            color = appColors.primaryAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider(color = cardBorder)

                // Language selector strip
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    val languages = listOf("English", "Tamil", "Hindi", "Telugu")
                    languages.forEach { langItem ->
                        val isSelected = langItem == currentTemplateLang
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) appColors.primaryAccent else (if (isDark) Color(0xFF0F172A) else Color.White),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) appColors.primaryAccent else cardBorder,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { viewModel.setTemplateLanguage(langItem) }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = translate(langItem, language),
                                color = if (isSelected) Color.White else textColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // ---------------------------------------------------------
                // TOP HALF: Day & Customer Dropdowns + Live Message Preview
                // ---------------------------------------------------------
                val defaultPreviewDay = remember(daysList) {
                    daysList.firstOrNull { it.equals("Monday", ignoreCase = true) }
                        ?: daysList.firstOrNull()
                        ?: "Monday"
                }
                var previewDay by rememberSaveable { mutableStateOf(defaultPreviewDay) }
                var dayDropdownExpanded by remember { mutableStateOf(false) }
                var customerDropdownExpanded by remember { mutableStateOf(false) }

                val dayCustomers = remember(allCustomers, previewDay) {
                    allCustomers
                        .filter { it.collectionDay.trim().equals(previewDay.trim(), ignoreCase = true) }
                        .sortedBy { it.customOrder }
                }

                var selectedPreviewCustomerId by rememberSaveable(previewDay) {
                    mutableStateOf(dayCustomers.firstOrNull()?.id ?: -1)
                }

                LaunchedEffect(dayCustomers) {
                    if (dayCustomers.isNotEmpty() && dayCustomers.none { it.id == selectedPreviewCustomerId }) {
                        selectedPreviewCustomerId = dayCustomers.first().id
                    }
                }

                val previewCustomer = remember(dayCustomers, selectedPreviewCustomerId) {
                    dayCustomers.find { it.id == selectedPreviewCustomerId } ?: dayCustomers.firstOrNull()
                }
                val previewLoan = remember(allLoanCycles, previewCustomer) {
                    if (previewCustomer == null) null
                    else allLoanCycles.filter { it.customerId == previewCustomer.id && it.status == "ACTIVE" }
                        .maxByOrNull { it.id }
                        ?: allLoanCycles.filter { it.customerId == previewCustomer.id }.maxByOrNull { it.id }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF0F9FF)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, appColors.primaryAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Compute Live Formatted Message Preview for the selected Customer
                        val livePreviewText = remember(
                            currentTemplateValue,
                            previewCustomer,
                            previewLoan,
                            customAppName,
                            upiId,
                            patternIdx
                        ) {
                            val cName = previewCustomer?.name ?: "Muneeswaran"
                            val cPhone = previewCustomer?.phone?.ifBlank { "9876543210" } ?: "9876543210"
                            val lTotal = if (previewLoan != null) (previewLoan.loanAmount + previewLoan.interestAmount) else 10000.0
                            val lPaid = previewLoan?.paidAmount ?: 2000.0
                            val lBal = maxOf(0.0, lTotal - lPaid)
                            val lInst = previewLoan?.weeklyAmount ?: 1000.0
                            val sampleAmt = if (patternIdx == 0) (previewLoan?.loanAmount ?: 10000.0) else lInst
                            val bName = customAppName.ifBlank { "MD Finance" }
                            val uId = upiId.ifBlank { "9440736893@ptyes" }
                            val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

                            currentTemplateValue
                                .replace("{customer}", cName)
                                .replace("{name}", cName)
                                .replace("{amount}", sampleAmt.toLong().toString())
                                .replace("{p_amount}", sampleAmt.toLong().toString())
                                .replace("{balance}", lBal.toLong().toString())
                                .replace("{outstanding}", lBal.toLong().toString())
                                .replace("{inst_amt}", lInst.toLong().toString())
                                .replace("{weekly}", lInst.toLong().toString())
                                .replace("{week}", "1")
                                .replace("{date}", todayStr)
                                .replace("{business}", bName)
                                .replace("{upi}", uId)
                                .replace("{phone}", cPhone)
                                .replace("{phone_number}", cPhone)
                                .replace("{upi_link}", cPhone)
                        }

                        val previewLines = remember(livePreviewText) {
                            livePreviewText.split("\n")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = translate("Live Message Preview", language),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = appColors.primaryAccent
                            )
                            Text(
                                text = "${previewLines.size} ${if (previewLines.size == 1) "Line" else "Lines"} (L1–L${previewLines.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = subTextColor
                            )
                        }

                        // Two Dropdowns side-by-side: 1. Day Dropdown, 2. Customer Dropdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Dropdown 1: Collection Day
                            Box(modifier = Modifier.weight(0.45f)) {
                                Surface(
                                    onClick = { dayDropdownExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = cardBg,
                                    border = BorderStroke(1.dp, cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Day", fontSize = 9.sp, color = subTextColor)
                                            Text(
                                                text = previewDay,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Day",
                                            tint = textColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = dayDropdownExpanded,
                                    onDismissRequest = { dayDropdownExpanded = false }
                                ) {
                                    daysList.forEach { d ->
                                        DropdownMenuItem(
                                            text = { Text(d, fontWeight = if (d == previewDay) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                previewDay = d
                                                dayDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Dropdown 2: Customer from selected Day
                            Box(modifier = Modifier.weight(0.55f)) {
                                Surface(
                                    onClick = { if (dayCustomers.isNotEmpty()) customerDropdownExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = cardBg,
                                    border = BorderStroke(1.dp, cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Customer", fontSize = 9.sp, color = subTextColor)
                                            Text(
                                                text = previewCustomer?.let { "${it.customOrder}. ${it.name}" } ?: "Sample Customer",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Customer",
                                            tint = textColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = customerDropdownExpanded,
                                    onDismissRequest = { customerDropdownExpanded = false }
                                ) {
                                    dayCustomers.forEach { cust ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${cust.customOrder}. ${cust.name}",
                                                    fontWeight = if (cust.id == previewCustomer?.id) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                selectedPreviewCustomerId = cust.id
                                                customerDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Numbered Line-by-Line Preview Box so wrapped lines are never confused with new lines
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cardBg, RoundedCornerShape(10.dp))
                                .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (livePreviewText.isBlank()) {
                                Text(
                                    text = "(Empty message format)",
                                    fontSize = 13.sp,
                                    color = subTextColor
                                )
                            } else {
                                previewLines.forEachIndexed { idx, lineStr ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    appColors.primaryAccent.copy(alpha = 0.14f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "L${idx + 1}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = appColors.primaryAccent
                                            )
                                        }
                                        Text(
                                            text = lineStr.ifBlank { " " },
                                            fontSize = 13.sp,
                                            color = textColor,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (idx < previewLines.lastIndex) {
                                        HorizontalDivider(
                                            color = cardBorder.copy(alpha = 0.5f),
                                            thickness = 0.5.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------------------------------------------------
                // BOTTOM HALF: Bubble Tray Above Format Box + Selected Bubbles List Below
                // ---------------------------------------------------------
                InteractiveMessageBubbleEditor(
                    value = currentTemplateValue,
                    onValueChange = onUpdateTemplate,
                    language = language,
                    appColors = appColors,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    cardBorder = cardBorder
                )
            } else if (showMessageFormatList) {
                // =========================================================
                // MESSAGE FORMAT PATTERN LIST (4 MESSAGE TYPES)
                // =========================================================
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { showMessageFormatList = false }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = translate("Select Message Format Type", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = textColor
                    )
                }

                HorizontalDivider(color = cardBorder)

                Text(
                    text = translate("Tap any message type below to open the Live Customer Preview and Bubble Format Editor:", language),
                    fontSize = 11.sp,
                    color = subTextColor
                )

                val patternCards = listOf(
                    Triple(0, "1. New Loan Confirmation SMS", smsNewLoanTemplate),
                    Triple(1, "2. Entry Confirmation SMS", smsPaymentTemplate),
                    Triple(2, "3. Weekly SMS Reminder", smsReminderTemplate),
                    Triple(3, "4. Weekly WhatsApp Reminder", whatsappReminderTemplate)
                )

                patternCards.forEach { (idx, title, rawTemplate) ->
                    val readableSnippet = remember(rawTemplate) {
                        var s = rawTemplate
                        MESSAGE_BUBBLE_TAGS.forEach { tag ->
                            s = s.replace(tag.token, "[${tag.displayLabel}]")
                            tag.altTokens.forEach { alt -> s = s.replace(alt, "[${tag.displayLabel}]") }
                        }
                        s
                    }
                    Card(
                        onClick = { selectedMessagePatternIndex = idx },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = translate(title, language),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = appColors.primaryAccent
                                )
                                Text(
                                    text = readableSnippet,
                                    fontSize = 11.5.sp,
                                    color = subTextColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Edit Format",
                                tint = appColors.primaryAccent
                            )
                        }
                    }
                }
            } else {
                // =========================================================
                // MAIN MESSAGES & REMINDERS HOME VIEW
                // =========================================================
                // Outbound SMS Controls Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                        .border(1.dp, cardBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = translate("Outbound SMS Sending", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (smsPaused) ColorLossRed else textColor
                        )
                        Text(
                            text = translate("Pause or enable outbound confirmation and draft SMS.", language),
                            fontSize = 10.sp,
                            color = subTextColor
                        )
                    }

                    Switch(
                        checked = !smsPaused,
                        onCheckedChange = { viewModel.setSmsPaused(!it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = appColors.primaryAccent,
                            checkedTrackColor = appColors.primaryAccent.copy(alpha = 0.4f),
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color.LightGray.copy(alpha = 0.4f)
                        )
                    )
                }

                // Customer Wise SMS Settings Button
                Button(
                    onClick = { showCustomerSmsSettings = true },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Customer Wise SMS Settings",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = translate("Customer Wise SMS Settings", language),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }

                // Single "Message Format" Button (replaces cluttered static boxes)
                Button(
                    onClick = {
                        selectedMessagePatternIndex = null
                        showMessageFormatList = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("message_format_studio_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "Message Format",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = translate("Message Format (4 Message Types & Live Preview)", language),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }

                // Statement Template Studio Shortcut Button right below SMS Templates
                Button(
                    onClick = { viewModel.setSettingsSubPage("statement_template") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("statement_template_studio_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Statement Template",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = translate("Statement Template (Virtual Format & Bubbles)", language),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// 9B. StatementTemplateSubPage (Virtual Statement Format Preview + Bubble Editor)
// ==========================================
@Composable
fun StatementTemplateSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors
) {
    val collectionGroups by viewModel.collectionGroups.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val allLoanCycles by viewModel.allLoanCycles.collectAsStateWithLifecycle()
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
    val businessName by viewModel.businessName.collectAsStateWithLifecycle()
    val upiId by viewModel.upiId.collectAsStateWithLifecycle()

    val headerTitleTemplate by viewModel.statementHeaderTitle.collectAsStateWithLifecycle()
    val headerSubtitleTemplate by viewModel.statementHeaderSubtitle.collectAsStateWithLifecycle()
    val customerBoxTemplate by viewModel.statementCustomerBoxTemplate.collectAsStateWithLifecycle()
    val loanBoxTemplate by viewModel.statementLoanBoxTemplate.collectAsStateWithLifecycle()
    val footerTemplate by viewModel.statementFooterTemplate.collectAsStateWithLifecycle()
    val tableColumnsCsv by viewModel.statementTableColumns.collectAsStateWithLifecycle()
    val activeTableColumns = remember(tableColumnsCsv) {
        com.example.util.StatementGenerator.parseStatementTableColumns(tableColumnsCsv)
    }

    val daysList = remember(collectionGroups) {
        getOrderedDaysList(collectionGroups).filter {
            !it.equals("Friday", ignoreCase = true) && !it.equals("Home", ignoreCase = true)
        }
    }

    var previewDay by rememberSaveable { mutableStateOf("Monday") }
    var previewCustomerId by rememberSaveable { mutableStateOf<Int?>(null) }
    var dayDropdownExpanded by remember { mutableStateOf(false) }
    var customerDropdownExpanded by remember { mutableStateOf(false) }
    var activeSectionIndex by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(daysList) {
        if (daysList.isNotEmpty() && daysList.none { it.equals(previewDay, ignoreCase = true) }) {
            previewDay = daysList.firstOrNull { it.equals("Monday", ignoreCase = true) } ?: daysList.first()
        }
    }

    val customersInSelectedDay = remember(allCustomers, previewDay) {
        allCustomers.filter {
            it.collectionDay.trim().equals(previewDay.trim(), ignoreCase = true)
        }.sortedBy { it.customOrder }
    }

    LaunchedEffect(customersInSelectedDay) {
        if (customersInSelectedDay.isNotEmpty() && customersInSelectedDay.none { it.id == previewCustomerId }) {
            previewCustomerId = customersInSelectedDay.first().id
        } else if (customersInSelectedDay.isEmpty()) {
            previewCustomerId = null
        }
    }

    val selectedPreviewCustomer = remember(customersInSelectedDay, previewCustomerId) {
        customersInSelectedDay.firstOrNull { it.id == previewCustomerId } ?: customersInSelectedDay.firstOrNull()
    }

    val selectedPreviewLoan = remember(allLoanCycles, selectedPreviewCustomer) {
        if (selectedPreviewCustomer == null) null
        else {
            val custLoans = allLoanCycles.filter { it.customerId == selectedPreviewCustomer.id && it.status.uppercase() != "DELETED" }
            custLoans.firstOrNull { it.status == "ACTIVE" } ?: custLoans.lastOrNull()
        }
    }

    val selectedPreviewPayments = remember(allPayments, selectedPreviewLoan) {
        if (selectedPreviewLoan == null) emptyList()
        else allPayments.filter { it.loanCycleId == selectedPreviewLoan.id && it.status.uppercase() != "DELETED" }
            .sortedBy { it.weekNumber }
    }

    val previewName = selectedPreviewCustomer?.name ?: "Ramesh Kumar (Demo)"
    val previewPhone = selectedPreviewCustomer?.phone?.takeIf { it.isNotBlank() } ?: "9876543210"
    val previewCity = selectedPreviewCustomer?.city?.takeIf { it.isNotBlank() } ?: "Madurai"
    val previewRoute = selectedPreviewCustomer?.customOrder ?: 1

    val resolvedHeaderTitle = remember(headerTitleTemplate, businessName, previewName, previewPhone, previewDay, previewCity, previewRoute, selectedPreviewLoan, selectedPreviewPayments, upiId) {
        com.example.util.StatementGenerator.resolveStatementTemplate(
            headerTitleTemplate, businessName, previewName, previewPhone, previewDay, previewCity, previewRoute, selectedPreviewLoan, selectedPreviewPayments, upiId
        ).ifBlank { "MD Finance" }
    }
    val resolvedHeaderSubtitle = remember(headerSubtitleTemplate, businessName, previewName, previewPhone, previewDay, previewCity, previewRoute, selectedPreviewLoan, selectedPreviewPayments, upiId) {
        com.example.util.StatementGenerator.resolveStatementTemplate(
            headerSubtitleTemplate, businessName, previewName, previewPhone, previewDay, previewCity, previewRoute, selectedPreviewLoan, selectedPreviewPayments, upiId
        )
    }
    val resolvedCustomerBoxLines = remember(customerBoxTemplate, businessName, previewName, previewPhone, previewDay, previewCity, previewRoute, selectedPreviewLoan, selectedPreviewPayments, upiId) {
        com.example.util.StatementGenerator.resolveStatementTemplate(
            customerBoxTemplate, businessName, previewName, previewPhone, previewDay, previewCity, previewRoute, selectedPreviewLoan, selectedPreviewPayments, upiId
        ).lines().filter { it.isNotBlank() }
    }
    val resolvedLoanBoxLines = remember(loanBoxTemplate, businessName, previewName, previewPhone, previewDay, previewCity, previewRoute, selectedPreviewLoan, selectedPreviewPayments, upiId) {
        com.example.util.StatementGenerator.resolveStatementTemplate(
            loanBoxTemplate, businessName, previewName, previewPhone, previewDay, previewCity, previewRoute, selectedPreviewLoan, selectedPreviewPayments, upiId
        ).lines().filter { it.isNotBlank() }
    }
    val resolvedFooterLines = remember(footerTemplate, businessName, previewName, previewPhone, previewDay, previewCity, previewRoute, selectedPreviewLoan, selectedPreviewPayments, upiId) {
        com.example.util.StatementGenerator.resolveStatementTemplate(
            footerTemplate, businessName, previewName, previewPhone, previewDay, previewCity, previewRoute, selectedPreviewLoan, selectedPreviewPayments, upiId
        ).lines().filter { it.isNotBlank() }
    }

    val isDark = appColors.isDark
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val cardBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val textColor = if (isDark) Color.White else ColorSlateDark
    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color.DarkGray

    val sectionTitles = listOf(
        "1. Header Heading (Default: MD Finance)",
        "2. Header Sub-Heading",
        "3. Customer Details Box",
        "4. Loan Summary Placeholders",
        "5. Transaction Box (Add, Remove & Reorder Columns)",
        "6. Bottom Footer Format"
    )
    val currentSectionValue = when (activeSectionIndex) {
        0 -> headerTitleTemplate
        1 -> headerSubtitleTemplate
        2 -> customerBoxTemplate
        3 -> loanBoxTemplate
        else -> footerTemplate
    }
    val onUpdateSectionValue: (String) -> Unit = { updated ->
        when (activeSectionIndex) {
            0 -> viewModel.setStatementHeaderTitle(updated)
            1 -> viewModel.setStatementHeaderSubtitle(updated)
            2 -> viewModel.setStatementCustomerBoxTemplate(updated)
            3 -> viewModel.setStatementLoanBoxTemplate(updated)
            5 -> viewModel.setStatementFooterTemplate(updated)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header + Reset Default Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = translate("Statement Template Studio", language),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = textColor
                    )
                    Text(
                        text = translate("Tap any section in the Virtual Statement Preview or choose below to edit placeholders & bubbles", language),
                        fontSize = 11.sp,
                        color = subTextColor
                    )
                }
                TextButton(
                    onClick = { viewModel.resetStatementTemplatesToDefault() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Default",
                        tint = appColors.primaryAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = translate("Reset", language),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.primaryAccent
                    )
                }
            }

            HorizontalDivider(color = cardBorder)

            // PARALLEL DAY & CUSTOMER DEMO FILTER ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Day Dropdown (Defaults to Monday)
                Box(modifier = Modifier.weight(0.45f)) {
                    OutlinedButton(
                        onClick = { dayDropdownExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, appColors.primaryAccent.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = translate(previewDay, language),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = appColors.primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = dayDropdownExpanded,
                        onDismissRequest = { dayDropdownExpanded = false }
                    ) {
                        daysList.forEach { d ->
                            DropdownMenuItem(
                                text = { Text(translate(d, language), fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    previewDay = d
                                    dayDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Customer Dropdown (Defaults to first customer in selected day)
                Box(modifier = Modifier.weight(0.55f)) {
                    OutlinedButton(
                        onClick = { customerDropdownExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, appColors.primaryAccent.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedPreviewCustomer?.let { "${it.customOrder}. ${it.name}" } ?: previewName,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = appColors.primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = customerDropdownExpanded,
                        onDismissRequest = { customerDropdownExpanded = false }
                    ) {
                        if (customersInSelectedDay.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Ramesh Kumar (Demo)") },
                                onClick = { customerDropdownExpanded = false }
                            )
                        } else {
                            customersInSelectedDay.forEach { cust ->
                                DropdownMenuItem(
                                    text = { Text("${cust.customOrder}. ${cust.name}") },
                                    onClick = {
                                        previewCustomerId = cust.id
                                        customerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // VIRTUAL STATEMENT IMAGE FORMAT PREVIEW (INTERACTIVE)
            // =========================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("virtual_statement_preview_card")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. Dark Header Banner (Tap to edit Header Heading or Sub-Heading + Top-Right Scan and Pay QR)
                    val previewContext = LocalContext.current
                    val qrImageUri by viewModel.qrImageUri.collectAsStateWithLifecycle()
                    val previewQrBitmap = remember(qrImageUri) {
                        UpiQrCodeHelper.loadSavedQrBitmap(previewContext)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .padding(vertical = 12.dp, horizontal = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 68.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (activeSectionIndex == 0) Color(0xFF4F46E5).copy(alpha = 0.35f) else Color.Transparent
                                    )
                                    .border(
                                        width = if (activeSectionIndex == 0) 1.dp else 0.dp,
                                        color = if (activeSectionIndex == 0) Color(0xFF818CF8) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { activeSectionIndex = 0 }
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = resolvedHeaderTitle.uppercase(),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (activeSectionIndex == 1) Color(0xFF4F46E5).copy(alpha = 0.35f) else Color.Transparent
                                    )
                                    .border(
                                        width = if (activeSectionIndex == 1) 1.dp else 0.dp,
                                        color = if (activeSectionIndex == 1) Color(0xFF818CF8) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { activeSectionIndex = 1 }
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = resolvedHeaderSubtitle.uppercase(),
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Text(
                                text = "Generated on: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())}",
                                color = Color(0xFF64748B),
                                fontSize = 9.sp
                            )
                        }

                        // Top-Right Corner: Pasted QR Code directly (no background card or label)
                        if (previewQrBitmap != null) {
                            Image(
                                bitmap = previewQrBitmap.asImageBitmap(),
                                contentDescription = "Statement QR Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(54.dp)
                                    .align(Alignment.CenterEnd)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "QR Preview",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(48.dp)
                                    .align(Alignment.CenterEnd)
                            )
                        }
                    }

                    // 2. Side-by-Side Customer Details Box & Loan Summary Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Left: Customer Details Box (Section 2)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .border(
                                    width = if (activeSectionIndex == 2) 2.dp else 1.dp,
                                    color = if (activeSectionIndex == 2) Color(0xFF4F46E5) else Color(0xFF94A3B8),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { activeSectionIndex = 2 }
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "CUSTOMER DETAILS",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF64748B)
                            )
                            resolvedCustomerBoxLines.forEachIndexed { idx, line ->
                                Text(
                                    text = line,
                                    fontSize = if (idx == 0) 10.5.sp else 9.5.sp,
                                    fontWeight = if (idx == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (idx == 0) Color(0xFF0F172A) else Color(0xFF334155),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Right: Loan Summary Box (Section 3)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .border(
                                    width = if (activeSectionIndex == 3) 2.dp else 1.dp,
                                    color = if (activeSectionIndex == 3) Color(0xFF4F46E5) else Color(0xFF94A3B8),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { activeSectionIndex = 3 }
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "LOAN SUMMARY",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF64748B)
                            )
                            resolvedLoanBoxLines.forEach { line ->
                                val lower = line.lowercase(Locale.getDefault())
                                val lineColor = when {
                                    lower.contains("paid") -> Color(0xFF16A34A)
                                    lower.contains("outstanding") || lower.contains("balance") -> Color(0xFFDC2626)
                                    else -> Color(0xFF0F172A)
                                }
                                Text(
                                    text = line,
                                    fontSize = 9.5.sp,
                                    fontWeight = if (lower.contains("paid") || lower.contains("outstanding") || lower.contains("balance") || lower.contains("total")) FontWeight.Bold else FontWeight.Medium,
                                    color = lineColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 3. Interactive Transaction Table Preview (Section 4 — Tap to Edit & Reorder Columns)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (activeSectionIndex == 4) 2.dp else 1.dp,
                                color = if (activeSectionIndex == 4) Color(0xFF4F46E5) else Color(0xFFCBD5E1),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { activeSectionIndex = 4 }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            activeTableColumns.forEachIndexed { colIdx, colKey ->
                                val align = when {
                                    colKey == "AMOUNT" || colIdx == activeTableColumns.lastIndex -> TextAlign.End
                                    colIdx == 0 -> TextAlign.Start
                                    else -> TextAlign.Center
                                }
                                Text(
                                    text = com.example.util.StatementGenerator.getColumnHeaderLabel(colKey),
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = align,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        val demoPayments = if (selectedPreviewPayments.isNotEmpty()) {
                            selectedPreviewPayments.take(2)
                        } else {
                            val now = System.currentTimeMillis()
                            listOf(
                                com.example.data.WeeklyPayment(
                                    id = 1,
                                    loanCycleId = 1,
                                    amountPaid = 1000.0,
                                    paymentDate = now - 7L * 24 * 3600 * 1000,
                                    weekNumber = 1,
                                    notes = "Cash",
                                    upiTxnId = null
                                ),
                                com.example.data.WeeklyPayment(
                                    id = 2,
                                    loanCycleId = 1,
                                    amountPaid = 1000.0,
                                    paymentDate = now,
                                    weekNumber = 2,
                                    notes = "UPI",
                                    upiTxnId = "UPI123"
                                )
                            )
                        }

                        demoPayments.forEachIndexed { rowIdx, payment ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (rowIdx % 2 == 0) Color.White else Color(0xFFF1F5F9))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                activeTableColumns.forEachIndexed { colIdx, colKey ->
                                    val cellText = com.example.util.StatementGenerator.formatPaymentCellForColumn(colKey, payment, rowIdx)
                                    val align = when {
                                        colKey == "AMOUNT" || colIdx == activeTableColumns.lastIndex -> TextAlign.End
                                        colIdx == 0 -> TextAlign.Start
                                        else -> TextAlign.Center
                                    }
                                    val cellColor = when (colKey) {
                                        "AMOUNT" -> Color(0xFF16A34A)
                                        "MODE" -> if (cellText == "UPI") Color(0xFF2563EB) else Color(0xFF1E293B)
                                        else -> Color(0xFF1E293B)
                                    }
                                    Text(
                                        text = cellText,
                                        fontSize = 8.5.sp,
                                        fontWeight = if (colKey == "AMOUNT" || colKey == "WEEK_NO") FontWeight.Bold else FontWeight.Medium,
                                        color = cellColor,
                                        textAlign = align,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // 4. Bottom Footer Format Preview (Section 5)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (activeSectionIndex == 5) Color(0xFFE0E7FF) else Color.Transparent
                            )
                            .border(
                                width = if (activeSectionIndex == 5) 1.5.dp else 1.dp,
                                color = if (activeSectionIndex == 5) Color(0xFF4F46E5) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { activeSectionIndex = 5 }
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        resolvedFooterLines.forEachIndexed { idx, line ->
                            Text(
                                text = line,
                                fontSize = if (idx == 0) 11.sp else 9.5.sp,
                                fontWeight = if (idx == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (idx == 0) Color(0xFF4F46E5) else Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            text = "ACCOUNT STATUS: ${selectedPreviewLoan?.status ?: "ACTIVE"}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFEA580C)
                        )
                    }
                }
            }

            // =========================================================================
            // SECTION SELECTOR CHIPS + INTERACTIVE BUBBLE / COLUMN EDITOR
            // =========================================================================
            Text(
                text = translate("Select Statement Section to Edit:", language),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val chipLabels = listOf(
                    "1. Header Heading",
                    "2. Sub-Heading",
                    "3. Customer Box",
                    "4. Loan Placeholders",
                    "5. Transaction Box",
                    "6. Bottom Footer"
                )
                chipLabels.forEachIndexed { idx, label ->
                    val selected = activeSectionIndex == idx
                    Surface(
                        onClick = { activeSectionIndex = idx },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) appColors.primaryAccent else (if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)),
                        border = BorderStroke(1.dp, if (selected) appColors.primaryAccent else cardBorder)
                    ) {
                        Text(
                            text = translate(label, language),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (selected) Color.White else textColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Text(
                text = translate(sectionTitles.getOrElse(activeSectionIndex) { sectionTitles.first() }, language),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = appColors.primaryAccent
            )

            if (activeSectionIndex == 4) {
                TransactionTableColumnsEditor(
                    activeColumns = activeTableColumns,
                    onUpdateColumns = { updatedList ->
                        viewModel.setStatementTableColumns(updatedList.joinToString(","))
                    },
                    language = language,
                    appColors = appColors,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    cardBorder = cardBorder
                )
            } else {
                InteractiveMessageBubbleEditor(
                    value = currentSectionValue,
                    onValueChange = onUpdateSectionValue,
                    language = language,
                    appColors = appColors,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    cardBorder = cardBorder,
                    bubbleTags = STATEMENT_BUBBLE_TAGS
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionTableColumnsEditor(
    activeColumns: List<String>,
    onUpdateColumns: (List<String>) -> Unit,
    language: String,
    appColors: AppThemeColors,
    textColor: Color,
    subTextColor: Color,
    cardBorder: Color
) {
    val allColumns = com.example.util.StatementGenerator.ALL_AVAILABLE_TABLE_COLUMNS
    val availableToAdd = remember(activeColumns) {
        allColumns.filter { (key, _) -> key !in activeColumns }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Active Columns List (Reorder with Up/Down arrows or Remove)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (appColors.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                    RoundedCornerShape(10.dp)
                )
                .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = translate("Active Transaction Box Columns (Left → Right Order):", language),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor
                )
                Text(
                    text = "${activeColumns.size} columns",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.primaryAccent
                )
            }

            activeColumns.forEachIndexed { index, colKey ->
                val displayName = allColumns.firstOrNull { it.first == colKey }?.second
                    ?: com.example.util.StatementGenerator.getColumnHeaderLabel(colKey)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (appColors.isDark) Color(0xFF1E293B) else Color.White,
                            RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, cardBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(appColors.primaryAccent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "#${index + 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = appColors.primaryAccent
                            )
                        }
                        Column {
                            Text(
                                text = displayName,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = "Header: ${com.example.util.StatementGenerator.getColumnHeaderLabel(colKey)}",
                                fontSize = 10.sp,
                                color = subTextColor
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Move Up (Left in Table)
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val mutable = activeColumns.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(index - 1, item)
                                    onUpdateColumns(mutable)
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Move Column Left/Up",
                                tint = if (index > 0) appColors.primaryAccent else subTextColor.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Move Down (Right in Table)
                        IconButton(
                            onClick = {
                                if (index < activeColumns.lastIndex) {
                                    val mutable = activeColumns.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(index + 1, item)
                                    onUpdateColumns(mutable)
                                }
                            },
                            enabled = index < activeColumns.lastIndex,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Move Column Right/Down",
                                tint = if (index < activeColumns.lastIndex) appColors.primaryAccent else subTextColor.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Remove Column (keep at least 1 column)
                        IconButton(
                            onClick = {
                                if (activeColumns.size > 1) {
                                    onUpdateColumns(activeColumns.filterNot { it == colKey })
                                }
                            },
                            enabled = activeColumns.size > 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Column",
                                tint = if (activeColumns.size > 1) Color(0xFFDC2626) else subTextColor.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Available Columns to Add
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (appColors.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                    RoundedCornerShape(10.dp)
                )
                .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = translate("Available Columns to Add (Tap to Include in Statement):", language),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.primaryAccent
            )
            if (availableToAdd.isEmpty()) {
                Text(
                    text = translate("All available transaction columns are currently active.", language),
                    fontSize = 11.sp,
                    color = subTextColor
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableToAdd.forEach { (colKey, label) ->
                        Surface(
                            onClick = {
                                onUpdateColumns(activeColumns + colKey)
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = appColors.primaryAccent.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, appColors.primaryAccent.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = appColors.primaryAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = appColors.primaryAccent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 10. BackupSubPage
// ==========================================
@Composable
fun BackupSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    collectionGroups: List<String>,
    context: Context,
    currentUserRole: String = "ADMIN"
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var groupToImportInto by rememberSaveable { mutableStateOf<String?>(null) }

    val msgAllDataDeleted = translate("All data successfully deleted!", language)
    val msgDeleteFailed = translate("Delete failed:", language)
    val msgSuccessTail = translate("successfully imported!", language)
    val msgImportFailedHead = translate("Import failed:", language)
    val msgAllDaysLedger = translate("All days ledger", language)

    // CSV / Excel (.xlsx) File picker launcher
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val targetGroup = groupToImportInto
        if (uri != null && targetGroup != null) {
            viewModel.importCsvGroupBackupFromUri(
                context = context,
                uri = uri,
                groupName = targetGroup,
                onSuccess = {
                    val displayGroupName = if (targetGroup == "ALL") msgAllDaysLedger else targetGroup
                    Toast.makeText(context, "$displayGroupName $msgSuccessTail", Toast.LENGTH_LONG).show()
                },
                onError = { err ->
                    Toast.makeText(context, "$msgImportFailedHead $err", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = {
                Text(
                    text = translate("⚠️ Delete All Local Data?", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Red
                )
            },
            text = {
                Text(
                    text = translate("WARNING: This will completely delete all clients, loan records, edits, and transaction histories from this local device. This is irreversible. You can import your saved backups later to restore.", language),
                    fontSize = 12.sp,
                    color = Color.Black
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        viewModel.clearAllLocalData(
                            context = context,
                            onSuccess = {
                                Toast.makeText(context, msgAllDataDeleted, Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, "$msgDeleteFailed $err", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                ) {
                    Text(translate("Delete All Data", language), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteAllDialog = false },
                    border = BorderStroke(1.dp, ColorSlateDark)
                ) {
                    Text(translate("Cancel", language), color = ColorSlateDark)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Dynamic sheets fix (excluding Friday per AGENTS.md)
    val defaultGroups = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Saturday", "Sunday mrg", "Sunday eve")
    val dynamicGroups = remember(collectionGroups) {
        val sanitizedIncoming = collectionGroups.map { it.trim() }
            .filter {
                it.isNotBlank() &&
                !it.equals("Friday", ignoreCase = true) &&
                !it.equals("Sunday", ignoreCase = true) &&
                !it.equals("Sunday Morning", ignoreCase = true) &&
                !it.equals("Sunday Evening", ignoreCase = true)
            }

        // Append default groups first to safeguard structural layout precedence rules
        (defaultGroups + sanitizedIncoming).distinctBy { it.lowercase(Locale.US) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Security Banner Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                     imageVector = Icons.Default.CloudOff,
                     contentDescription = "Offline Secure Icon",
                     tint = appColors.primaryAccent,
                     modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = translate("Fully Offline Local Backup Engine", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ColorSlateDark
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = translate("All data is loaded locally using standard CSV or Excel (.xlsx) formats. Supports both unified multi-day sheets and individual day-group files.", language),
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Universal Full Backup & Restore Card
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.primaryAccent.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, appColors.primaryAccent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = translate("Universal Full Ledger CSV / Excel Backup", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )

                Text(
                    text = if (currentUserRole == "USER")
                        translate("Export all active days' customers, loans, and weekly payments in a single unified CSV spreadsheet template cleanly.", language)
                    else
                        translate("Export or import all active days' customers, loans, and weekly payments from a unified CSV or multi-sheet Excel (.xlsx) workbook.", language),
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    lineHeight = 15.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Export ALL CSV Button
                    OutlinedButton(
                        onClick = { viewModel.exportCsvGroupBackup(context, "ALL") },
                        border = BorderStroke(1.5.dp, appColors.primaryAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = (if (currentUserRole == "USER") Modifier.fillMaxWidth() else Modifier.weight(1f))
                            .testTag("export_all_days_csv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export All icon",
                            tint = appColors.primaryAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = translate("Export All Days", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = appColors.primaryAccent
                        )
                    }

                    if (currentUserRole != "USER") {
                        // Import ALL CSV Button
                        Button(
                            onClick = {
                                groupToImportInto = "ALL"
                                csvPickerLauncher.launch("*/*")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_all_days_csv_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = "Import All icon",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = translate("Import All Days", language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Day-by-Day Group CSV / Excel Backup & Restore Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = translate("Day Group CSV / Excel Backups", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )
                Text(
                    text = translate("Export or import customer ledgers for a specific collection day group (excluding Friday).", language),
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    lineHeight = 15.sp
                )
                dynamicGroups.forEach { dayGroup ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = translate(dayGroup, language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = ColorSlateDark,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.exportCsvGroupBackup(context, dayGroup) },
                                border = BorderStroke(1.dp, appColors.primaryAccent),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = translate("Export", language),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appColors.primaryAccent
                                )
                            }
                            if (currentUserRole != "USER") {
                                Button(
                                    onClick = {
                                        groupToImportInto = dayGroup
                                        csvPickerLauncher.launch("*/*")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = translate("Import", language),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Firebase Cloud Sync Card
        val syncPaused by viewModel.syncPaused.collectAsStateWithLifecycle()
        val firebaseSyncStatus by viewModel.firebaseSyncStatus.collectAsStateWithLifecycle()

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = translate("Cloud Firestore Real-time Sync", language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )

                Text(
                    text = translate("Synchronize your ledger entries, customers, loans, payments, and audits with Cloud Firestore ((default) database). Any legacy RTDB customer branches are cleared automatically.", language),
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    lineHeight = 15.sp
                )

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = translate("Cloud Integration Status", language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ColorSlateDark
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            val dotColor = when {
                                syncPaused -> Color.Gray
                                firebaseSyncStatus.contains("Error", ignoreCase = true) || firebaseSyncStatus.contains("Failed", ignoreCase = true) -> Color.Red
                                firebaseSyncStatus.contains("Synced", ignoreCase = true) -> Color(0xFF10B981)
                                else -> Color(0xFFF59E0B)
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color = dotColor, shape = CircleShape)
                            )
                            val statusTextColor = when {
                                syncPaused -> Color.Gray
                                firebaseSyncStatus.contains("Error", ignoreCase = true) || firebaseSyncStatus.contains("Failed", ignoreCase = true) -> Color.Red
                                firebaseSyncStatus.contains("Synced", ignoreCase = true) -> Color(0xFF047857)
                                else -> Color(0xFFB45309)
                            }
                            Text(
                                text = translate(firebaseSyncStatus, language),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusTextColor
                            )
                        }
                    }

                    Switch(
                        checked = !syncPaused,
                        onCheckedChange = { isChecked ->
                            viewModel.setSyncPaused(!isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = appColors.primaryAccent,
                            checkedTrackColor = appColors.primaryAccent.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("firebase_sync_pause_resume_switch")
                    )
                }

                if (!syncPaused) {
                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Manual Synchronize Now
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (currentUserRole == "USER") Arrangement.Center else Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.startFirebaseSyncListening() },
                            border = BorderStroke(1.5.dp, appColors.primaryAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = if (currentUserRole == "USER") Modifier.fillMaxWidth() else Modifier.weight(1f)
                        ) {
                            Text(
                                text = translate("Fetch from Cloud", language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = appColors.primaryAccent
                            )
                        }

                        if (currentUserRole != "USER") {
                            Button(
                                onClick = { viewModel.forceUploadToFirebaseCloud() },
                                colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = translate("Upload to Cloud", language),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 11. AutomationSubPage
// ==========================================
@Composable
fun AutomationSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    smsPaused: Boolean,
    smsReaderPaused: Boolean,
    autoEntryPassing: Boolean,
    upiLinkSharing: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Outbound SMS Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = translate("Outbound SMS Sending", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (smsPaused) ColorLossRed else ColorSlateDark
                    )
                    Text(
                        text = translate("Pause or enable outbound confirmation and draft SMS.", language),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                Switch(
                    checked = !smsPaused,
                    onCheckedChange = { viewModel.setSmsPaused(!it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = ColorSlateDark, checkedTrackColor = ColorSlateDark.copy(alpha = 0.4f))
                )
            }
        }
    }
}

// ==========================================
// 12. BadDebtsSubPage (NPA Quarantine Hub)
// ==========================================
@Composable
fun BadDebtsSubPage(
    language: String,
    viewModel: FinanceViewModel,
    appColors: AppThemeColors,
    context: Context
) {
    val badDebtCustomers by viewModel.badDebtCustomers.collectAsStateWithLifecycle()
    val allLoanCycles by viewModel.allLoanCycles.collectAsStateWithLifecycle()
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
    val collectionGroups by viewModel.collectionGroups.collectAsStateWithLifecycle()
    val currentUserRole by viewModel.currentUserRole.collectAsStateWithLifecycle()

    val validDayTabs = remember(collectionGroups) {
        val defaultDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Saturday", "Sunday mrg", "Sunday eve")
        val dynamic = collectionGroups.map { it.trim() }.filter {
            it.isNotBlank() &&
                !it.equals("Friday", ignoreCase = true) &&
                !it.equals("Sunday", ignoreCase = true) &&
                !it.equals("Home", ignoreCase = true)
        }
        listOf("All") + (defaultDays + dynamic).distinctBy { it.lowercase(Locale.US) }
    }

    var selectedDayFilter by rememberSaveable { mutableStateOf("All") }

    val filteredCustomers = remember(badDebtCustomers, selectedDayFilter) {
        if (selectedDayFilter.equals("All", ignoreCase = true)) {
            badDebtCustomers.sortedWith(compareBy<com.example.data.Customer> { it.collectionDay }.thenBy { it.customOrder })
        } else {
            badDebtCustomers.filter {
                it.collectionDay.trim().equals(selectedDayFilter.trim(), ignoreCase = true)
            }.sortedBy { it.customOrder }
        }
    }

    // Calculate Principal Loss, Interest Loss, and Total Loss across filtered Bad Debt customers
    val lossMetrics = remember(filteredCustomers, allLoanCycles, allPayments) {
        var totalPrincipalLoss = 0.0
        var totalInterestLoss = 0.0
        var totalLoss = 0.0

        val customerIdSet = filteredCustomers.map { it.id }.toSet()
        val relevantLoans = allLoanCycles.filter {
            it.customerId in customerIdSet &&
                it.status.uppercase(Locale.US) != "DELETED"
        }

        relevantLoans.forEach { loan ->
            val livePaid = allPayments
                .filter { it.loanCycleId == loan.id && it.status.uppercase(Locale.US) != "DELETED" }
                .sumOf { it.amountPaid }
            val (pLoss, iLoss, tLoss) = MoneyMath.calculateBadDebtLossBreakdown(
                principalAmount = loan.loanAmount,
                interestAmount = loan.interestAmount,
                paidAmount = livePaid
            )
            totalPrincipalLoss = MoneyMath.add(totalPrincipalLoss, pLoss)
            totalInterestLoss = MoneyMath.add(totalInterestLoss, iLoss)
            totalLoss = MoneyMath.add(totalLoss, tLoss)
        }

        Triple(totalPrincipalLoss, totalInterestLoss, totalLoss)
    }

    val (principalLoss, interestLoss, totalCombinedLoss) = lossMetrics

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Same Day-Group Navigation Bar as Home (All, Monday, Tuesday, Wednesday, Thursday, Saturday, Sunday mrg, Sunday eve)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            validDayTabs.forEach { dayTab ->
                val isSelected = selectedDayFilter.equals(dayTab, ignoreCase = true)
                val countForDay = remember(badDebtCustomers, dayTab) {
                    if (dayTab.equals("All", ignoreCase = true)) badDebtCustomers.size
                    else badDebtCustomers.count { it.collectionDay.trim().equals(dayTab.trim(), ignoreCase = true) }
                }
                Surface(
                    onClick = { selectedDayFilter = dayTab },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFFDC2626) else appColors.cardBg,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) Color(0xFFDC2626) else appColors.cardBorder
                    ),
                    modifier = Modifier.testTag("bad_debt_day_tab_${dayTab.lowercase(Locale.US).replace(" ", "_")}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = translate(dayTab, language),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else appColors.textPrimary
                        )
                        if (countForDay > 0) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) Color.White.copy(alpha = 0.22f) else Color(0xFFFEE2E2),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$countForDay",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) Color.White else Color(0xFFDC2626)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Mini Loss Dashboard (Principal Loss, Interest Loss, Total Loss)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bad_debts_mini_dashboard")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReportProblem,
                            contentDescription = null,
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "NPA QUARANTINE LOSS DASHBOARD (${selectedDayFilter.uppercase(Locale.US)})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "${filteredCustomers.size} Account(s)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFCA5A5)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Principal Loss Box
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = translate("Principal Loss", language),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹${CurrencyFormatter.format(principalLoss)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFF87171)
                        )
                    }

                    // Interest Loss Box
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = translate("Interest Loss", language),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹${CurrencyFormatter.format(interestLoss)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFBBF24)
                        )
                    }

                    // Total Loss Box
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF7F1D1D).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = translate("Total Loss", language),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFECACA)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹${CurrencyFormatter.format(totalCombinedLoss)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 3. Quarantined Customer Cards List
        if (filteredCustomers.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = appColors.cardBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, appColors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = translate("No Bad Debt Accounts in $selectedDayFilter", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = appColors.textPrimary
                    )
                    Text(
                        text = translate("Quarantined NPA accounts moved from Customer Dashboard 3-dots will appear here.", language),
                        fontSize = 11.sp,
                        color = appColors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            filteredCustomers.forEach { customer ->
                val customerLoans = remember(allLoanCycles, customer.id) {
                    allLoanCycles.filter {
                        it.customerId == customer.id && it.status.uppercase(Locale.US) != "DELETED"
                    }
                }
                var custPrincipalLoss = 0.0
                var custInterestLoss = 0.0
                var custTotalLoss = 0.0
                var custTotalPaid = 0.0

                customerLoans.forEach { loan ->
                    val paid = allPayments
                        .filter { it.loanCycleId == loan.id && it.status.uppercase(Locale.US) != "DELETED" }
                        .sumOf { it.amountPaid }
                    custTotalPaid = MoneyMath.add(custTotalPaid, paid)
                    val (pL, iL, tL) = MoneyMath.calculateBadDebtLossBreakdown(
                        principalAmount = loan.loanAmount,
                        interestAmount = loan.interestAmount,
                        paidAmount = paid
                    )
                    custPrincipalLoss = MoneyMath.add(custPrincipalLoss, pL)
                    custInterestLoss = MoneyMath.add(custInterestLoss, iL)
                    custTotalLoss = MoneyMath.add(custTotalLoss, tL)
                }

                Card(
                    onClick = { viewModel.navigateTo(Screen.CustomerDetail(customer.id)) },
                    colors = CardDefaults.cardColors(containerColor = appColors.cardBg),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bad_debt_customer_card_${customer.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = customer.name,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = appColors.textPrimary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFFEF2F2), RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFFDC2626).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "SUSPENDED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFDC2626)
                                        )
                                    }
                                }
                                Text(
                                    text = "Day: ${customer.collectionDay} • Ph: ${customer.phone.ifBlank { "N/A" }} • Loans: ${customerLoans.size}",
                                    fontSize = 11.sp,
                                    color = appColors.textSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            if (currentUserRole != "USER") {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.reviveCustomerFromBadDebts(customer.id) {
                                            Toast.makeText(
                                                context,
                                                "${customer.name} revived to ${customer.collectionDay}!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    border = BorderStroke(1.dp, Color(0xFF059669)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("revive_customer_btn_${customer.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = "Revive Customer",
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = translate("Revive", language),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = appColors.cardBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Principal Loss", fontSize = 10.sp, color = appColors.textSecondary)
                                Text(
                                    text = "₹${CurrencyFormatter.format(custPrincipalLoss)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Interest Loss", fontSize = 10.sp, color = appColors.textSecondary)
                                Text(
                                    text = "₹${CurrencyFormatter.format(custInterestLoss)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Recovered", fontSize = 10.sp, color = appColors.textSecondary)
                                Text(
                                    text = "₹${CurrencyFormatter.format(custTotalPaid)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Unpaid Loss", fontSize = 10.sp, color = appColors.textSecondary)
                                Text(
                                    text = "₹${CurrencyFormatter.format(custTotalLoss)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
