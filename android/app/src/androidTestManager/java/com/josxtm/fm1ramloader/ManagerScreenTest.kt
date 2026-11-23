package com.josxtm.fm1ramloader

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class ManagerScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ManagerActivity>()

    @Test fun catalogueAndNavigation() {
        rule.waitUntil(15_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText("Buscar firmware, autor o función"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("FM1 Manager To USB").assertIsDisplayed()
        rule.onNodeWithText("Descargables").assertIsDisplayed()
        rule.onNodeWithText("Descargas").performClick()
        rule.onNodeWithText("Pausar").assertIsDisplayed()
        rule.onNodeWithText("MicroSD").performClick()
        rule.onNodeWithText("Recargar índice").assertIsDisplayed()
        rule.onNodeWithText("Actualizaciones").performScrollTo().performClick()
        rule.onNodeWithText("Actualizar selección").assertIsDisplayed()
    }

    @Test fun progressOnlyOffersEjectionAfterPublication() {
        val progress = mutableStateOf(CopyProgress(CopyPhase.COPYING, "Fixture 2.0", 1, 2, 512, 1024,
            fileWritten = 256, fileBytes = 512))
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent { MaterialTheme { CopyBanner(progress.value, progress.value.phase != CopyPhase.DONE, {}, {}) } }
        }
        rule.onNodeWithText("No desconectes la microSD", substring = true).assertIsDisplayed()
        rule.onNodeWithText("50 %", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Almacenamiento").assertDoesNotExist()
        rule.runOnIdle { progress.value = progress.value.copy(phase = CopyPhase.INDEX_VERIFY) }
        rule.onNodeWithText("Verificando índice", substring = true).assertIsDisplayed()
        rule.runOnIdle { progress.value = progress.value.copy(phase = CopyPhase.DONE) }
        rule.onNodeWithText("Copia y verificación finalizadas", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Almacenamiento").assertIsDisplayed()
    }

    @Test fun missingImageUsesPlaceholder() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent { MaterialTheme { CardPhoto(null, "Firmware", Modifier) } }
        }
        rule.onNodeWithText("Imagen no disponible").assertIsDisplayed()
    }
}
