package com.budgetpace.app.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.budgetpace.app.core.designsystem.theme.bpColors

@Composable
fun OnboardingRoute(
    onComplete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.bpColors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Spend at the speed you planned.",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.bpColors.textPrimary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Automatically capture supported bank transactions, categorize them in one tap, and see whether you're spending too fast.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.bpColors.textSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "Continue with Google",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(
                onClick = onComplete
            ) {
                Text(
                    text = "Skip for now",
                    color = MaterialTheme.bpColors.textSecondary
                )
            }
        }
    }
}
