package org.snrkl.nfcbusinesscard.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Builds a [ViewModelProvider.Factory] from a lambda.
 *
 * The app hand-wires its dependencies rather than using a DI framework (see
 * AppContainer), so view models need a factory each. This removes the ceremony
 * without pulling in a processor.
 */
internal fun <T : ViewModel> viewModelFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
