package dev.surco

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.surco.i18n.S
import dev.surco.slsk.SlskState
import dev.surco.ui.SurcoRoot
import dev.surco.ui.theme.SurcoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Abre Soulseek sin cuenta (pantalla de inicio de sesión) y pasa por los distintos estados. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SoulseekScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun settle() = repeat(5) { Thread.sleep(200); compose.waitForIdle() }

    @Test
    fun loginFlowDoesNotCrash() {
        val c = ApplicationProvider.getApplicationContext<SurcoApp>().container
        c.prefs.update { it.copy(treeUri = "content://fake/tree", slskUser = "", slskPass = "", language = "en") }
        compose.setContent { SurcoTheme { SurcoRoot(container = c, openPlayerSignal = 0) } }
        settle()

        compose.onNodeWithTag("tab_search").performClick()
        settle()
        compose.onNodeWithContentDescription(S.downloadWithSoulseek).performClick()
        settle()
        compose.onRoot().captureRoboImage("build/screens/20_soulseek_login.png")

        // Escribe usuario y contraseña (sin pulsar «Conectar»: en CI no hay que tocar la red real).
        compose.onNodeWithText(S.user).performTextInput("surco_demo")
        compose.onNodeWithText(S.password).performTextInput("secreto")
        settle()

        // Simula lo que pasa al conectar: cuenta guardada → conectando → error de contraseña → conectado.
        c.prefs.update { it.copy(slskUser = "surco_demo", slskPass = "secreto") }
        c.soulseek.client.debugSet(SlskState.Connecting, emptyList(), emptyList())
        settle()
        compose.onRoot().captureRoboImage("build/screens/21_soulseek_conectando.png")
        c.soulseek.client.debugSet(SlskState.Failed(S.loginError("INVALIDPASS", null)), emptyList(), emptyList())
        settle()
        compose.onRoot().captureRoboImage("build/screens/22_soulseek_error.png")
        c.soulseek.client.debugSet(SlskState.Online("surco_demo", 2234), emptyList(), emptyList())
        settle()
        compose.onRoot().captureRoboImage("build/screens/23_soulseek_conectado.png")
        c.soulseek.client.debugSet(SlskState.Failed(S.slskReconnecting, retrying = true), emptyList(), emptyList())
        settle()
        c.soulseek.client.debugSet(SlskState.Offline, emptyList(), emptyList())
        settle()
    }
}
