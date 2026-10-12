package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PaymentRequest
import com.example.data.model.UltronAdmins
import com.example.data.model.UltronBadges
import com.example.data.model.UserProfile
import com.example.ui.UltronViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AchievementsScreen(
    profile: UserProfile
) {
    val unlockedSet = remember(profile.unlockedBadges) { profile.unlockedBadges.toSet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // XP & Level Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Level ${profile.level} Scholar",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${profile.xp} Total XP • ${(profile.xpForNextLevel - profile.xp).coerceAtLeast(0)} XP to Level ${profile.level + 1}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Achievements",
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { profile.xpProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
                Text(
                    text = "XP Rewards: Task +10 XP • Focus +20 XP • Quiz +25 XP • Flashcard Deck +25 XP • Daily Streak +5 XP",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Streak & 7-Day Activity Calendar Card
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Streak",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Current Streak: ${profile.currentStreak} Days",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "Longest: ${profile.longestStreak} Days",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 7-Day Local Timezone Calendar Strip
                val daysList = remember(profile.currentStreak) {
                    val cal = Calendar.getInstance()
                    val fmt = SimpleDateFormat("EEE", Locale.US)
                    (6 downTo 0).map { offset ->
                        val c = cal.clone() as Calendar
                        c.add(Calendar.DAY_OF_YEAR, -offset)
                        val label = fmt.format(c.time)
                        val isActive = offset < profile.currentStreak
                        label to isActive
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    daysList.forEach { (dayLabel, active) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.Star,
                                    contentDescription = dayLabel,
                                    tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(dayLabel, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Comprehensive Study Statistics Grid
        Text("Study Performance Statistics", style = MaterialTheme.typography.titleLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 2
        ) {
            StatMetricCard("Study Sessions", "${profile.totalStudySessions}", "Questions: ${profile.questionsAnswered}", Modifier.weight(1f))
            StatMetricCard("Quizzes Completed", "${profile.quizzesCompleted}", "Adaptive MCQs", Modifier.weight(1f))
            StatMetricCard("Focus Sessions", "${profile.focusSessions}", "${profile.studyMinutes} mins focused", Modifier.weight(1f))
            StatMetricCard("Flashcards Mastered", "${profile.flashcardsMastered}", "${profile.flashcardsReviewed} reviewed", Modifier.weight(1f))
            StatMetricCard("Tasks Completed", "${profile.tasksCompleted}", "Syllabus Topics: ${profile.syllabusTopicsCompleted}", Modifier.weight(1f))
            StatMetricCard("Homework Sessions", "${profile.homeworkSessions}", "Step-by-step OCR", Modifier.weight(1f))
        }

        // All 11 Official Ultron Badges Collection
        Text(
            text = "Badge Collection (${unlockedSet.size}/${UltronBadges.ALL_BADGES.size} Unlocked)",
            style = MaterialTheme.typography.titleLarge
        )
        UltronBadges.ALL_BADGES.forEach { badge ->
            val isUnlocked = unlockedSet.contains(badge.id)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUnlocked) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isUnlocked) Icons.Default.Verified else Icons.Default.Lock,
                                contentDescription = badge.title,
                                tint = if (isUnlocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = badge.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = badge.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = if (isUnlocked) "UNLOCKED\n+${badge.xpReward} XP" else "+${badge.xpReward} XP",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMetricCard(
    label: String,
    primaryValue: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(primaryValue, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SubscriptionScreen(
    viewModel: UltronViewModel,
    profile: UserProfile,
    paymentHistory: List<PaymentRequest>
) {
    val context = LocalContext.current
    var selectedPlanForPayment by remember { mutableStateOf("PRO") }
    var selectedAmount by remember { mutableIntStateOf(299) }
    var utrInput by remember { mutableStateOf("") }
    var copyConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Current Plan & Monthly Reset Telemetry Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current Plan: ${profile.effectivePlan}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = profile.subscriptionStatus,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = "Chats: ${profile.chatsUsed} used (${profile.chatsRemainingDisplay} left) • Images: ${profile.imagesUsed} used (${profile.imagesRemainingDisplay} left)",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (profile.subscriptionExpiryDate.isNotBlank()) {
                        "Valid from ${profile.subscriptionStartDate} to ${profile.subscriptionExpiryDate} • Monthly usage resets on the 1st of each month."
                    } else {
                        "Monthly usage bucket: ${profile.usageMonth} (Automatically resets every month)."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Plan Comparison Cards
        Text("Choose Your Ultron Study Plan", style = MaterialTheme.typography.titleLarge)

        PlanOptionCard(
            name = "FREE",
            priceHeadline = "₹0 / month",
            subPrice = "Always Free Starter Plan",
            features = listOf(
                "50 Gemini Study Chats / month",
                "10 Image / OCR Uploads / month",
                "Core Flashcards, Quizzes, Tasks & Focus Timer",
                "All 11 Study Badges & Streak Tracking"
            ),
            isCurrent = profile.effectivePlan == "FREE",
            isSelected = false,
            onSelect = null
        )

        PlanOptionCard(
            name = "BASIC",
            priceHeadline = "₹99 introductory price",
            subPrice = "Then ₹199 / month",
            features = listOf(
                "200 Gemini Study Chats / month",
                "50 Image / OCR Uploads / month",
                "Expanded Syllabus Focus Mode & Homework Assist",
                "Achievements, Badges & Offline Sync"
            ),
            isCurrent = profile.effectivePlan == "BASIC",
            isSelected = selectedPlanForPayment == "BASIC",
            onSelect = {
                selectedPlanForPayment = "BASIC"
                selectedAmount = 99
            }
        )

        PlanOptionCard(
            name = "PRO",
            priceHeadline = "₹299 introductory price",
            subPrice = "Then ₹399 / month",
            features = listOf(
                "Unlimited Gemini Study Chats & High-Thinking Mode",
                "Unlimited Image / OCR Scans & Syllabus Pages",
                "Full Science Lab, Diagram & Veo 3.1 Video Generation",
                "Priority Collaborative Notebooks & Live Voice"
            ),
            isCurrent = profile.effectivePlan == "PRO",
            isSelected = selectedPlanForPayment == "PRO",
            onSelect = {
                selectedPlanForPayment = "PRO"
                selectedAmount = 299
            }
        )

        // Manual UPI Payment Checkout Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manual UPI Payment Verification", style = MaterialTheme.typography.titleLarge)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedPlanForPayment == "BASIC" && selectedAmount == 99,
                        onClick = {
                            selectedPlanForPayment = "BASIC"
                            selectedAmount = 99
                        },
                        label = { Text("Basic Intro ₹99") }
                    )
                    FilterChip(
                        selected = selectedPlanForPayment == "BASIC" && selectedAmount == 199,
                        onClick = {
                            selectedPlanForPayment = "BASIC"
                            selectedAmount = 199
                        },
                        label = { Text("Basic Renewal ₹199") }
                    )
                    FilterChip(
                        selected = selectedPlanForPayment == "PRO" && selectedAmount == 299,
                        onClick = {
                            selectedPlanForPayment = "PRO"
                            selectedAmount = 299
                        },
                        label = { Text("Pro Intro ₹299") }
                    )
                    FilterChip(
                        selected = selectedPlanForPayment == "PRO" && selectedAmount == 399,
                        onClick = {
                            selectedPlanForPayment = "PRO"
                            selectedAmount = 399
                        },
                        label = { Text("Pro Renewal ₹399") }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Selected Plan: $selectedPlanForPayment • Amount to Pay: ₹$selectedAmount",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Official UPI ID:", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = UltronAdmins.OFFICIAL_UPI_ID,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Ultron UPI ID", UltronAdmins.OFFICIAL_UPI_ID))
                                    copyConfirmation = true
                                },
                                modifier = Modifier.testTag("copy_upi_id_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy UPI ID", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (copyConfirmation) "Copied!" else "Copy UPI ID")
                            }
                        }
                        Text(
                            text = "Step 1: Copy the UPI ID above and pay ₹$selectedAmount using any UPI app (GPay, PhonePe, Paytm, BHIM).\nStep 2: Enter your 12-digit UPI Reference / UTR ID below.\nStep 3: Submit for verification. Both official Ultron admins are notified and your plan activates as soon as verified!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = utrInput,
                    onValueChange = { utrInput = it },
                    label = { Text("Enter Payment ID / 12-Digit UTR Number") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upi_utr_input"),
                    singleLine = true
                )

                Button(
                    onClick = {
                        viewModel.submitManualUpiPayment(
                            selectedPlan = selectedPlanForPayment,
                            amount = selectedAmount,
                            utr = utrInput
                        )
                        utrInput = ""
                    },
                    enabled = utrInput.trim().length >= 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_utr_payment_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Submit UTR for Admin Verification (Status: PENDING)")
                }
            }
        }

        // Student's Payment Request History
        if (paymentHistory.isNotEmpty()) {
            Text("Your Payment Verification History", style = MaterialTheme.typography.titleLarge)
            paymentHistory.forEach { req ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${req.selectedPlan} Plan • ₹${req.amount}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (req.status) {
                                    "APPROVED" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                    "REJECTED" -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = req.status,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text("Request ID: #${req.id.take(8)} • UTR: ${req.utr}", style = MaterialTheme.typography.bodyMedium)
                        if (req.processedByAdmin.isNotBlank()) {
                            Text(
                                text = "Processed by: ${req.processedByAdmin} • Note: ${req.adminNote}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanOptionCard(
    name: String,
    priceHeadline: String,
    subPrice: String,
    features: List<String>,
    isCurrent: Boolean,
    isSelected: Boolean,
    onSelect: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(priceHeadline, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(subPrice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isCurrent) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiary) {
                        Text(
                            text = "CURRENT",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else if (onSelect != null) {
                    Button(
                        onClick = onSelect,
                        modifier = Modifier.testTag("select_plan_${name.lowercase()}")
                    ) {
                        Text(if (isSelected) "Selected" else "Select $name")
                    }
                }
            }
            features.forEach { f ->
                Text("• $f", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
