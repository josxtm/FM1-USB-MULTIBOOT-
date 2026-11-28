// SPDX-License-Identifier: GPL-3.0-only
package com.josxtm.fm1screen.protocol

/** The most recent absolute state plus all unapplied relative encoder steps. */
class PanelUpdates {
    private var pending: PanelState? = null

    @Synchronized fun offer(state: PanelState): Boolean {
        val previous = pending
        pending = when {
            previous == null || previous.encoderSteps.all { it == 0 } -> state
            else -> state.copy(encoderSteps = state.encoderSteps.mapIndexed { index, step ->
                // The dial presentation uses a 20-step rotation.
                (previous.encoderSteps[index] + step) % 20
            })
        }
        return previous == null
    }

    @Synchronized fun take(): PanelState? = pending.also { pending = null }
    @Synchronized fun clear() { pending = null }
}
