package org.snrkl.nfcbusinesscard.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import org.snrkl.nfcbusinesscard.R

/**
 * Shows the third-party attribution notice.
 *
 * The app bundles several Apache 2.0 libraries, and that licence requires their
 * attribution notices to travel with anything that redistributes their code —
 * which an APK does. Keeping the notice only in the repository would satisfy
 * someone reading the source and nobody who installed the app.
 *
 * The text is read from `res/raw/notice.txt`, a copy of the repository's NOTICE
 * file, so there is one wording to maintain rather than two.
 */
@Composable
internal fun LicencesDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val notice = remember {
        runCatching {
            context.resources.openRawResource(R.raw.notice)
                .bufferedReader()
                .use { it.readText() }
        }.getOrElse { "Attribution notice unavailable." }
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_licences)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = notice,
                    // Monospaced because the notice is laid out in aligned
                    // columns; a proportional face turns it into ragged soup.
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_cancel))
            }
        },
    )
}
