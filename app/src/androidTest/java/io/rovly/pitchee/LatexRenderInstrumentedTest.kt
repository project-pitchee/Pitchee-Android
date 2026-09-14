package io.rovly.pitchee

import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LatexRenderInstrumentedTest {
    @Test
    fun rendersCompositeScoreFormula() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val markwon = Markwon.builder(context)
            .usePlugin(JLatexMathPlugin.create(18f))
            .build()
        val textView = TextView(context)
        val formula = """
            $$
            base = 100 \times \left(0.50S_r + 0.20N_r + 0.15F_r + 0.15S_rN_rF_r\right)
            $$
        """.trimIndent()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            markwon.setMarkdown(textView, formula)
        }

        assertTrue(textView.text.isNotEmpty())
    }
}
