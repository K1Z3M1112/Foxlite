/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.perf

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTabOrCustomTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.MiddlewareContext
import org.mozilla.fenix.utils.Settings

/**
 * Enforces "RAM Saver Mode".
 *
 * When [Settings.ramSaverModeEnabled] is on, only the currently selected tab is allowed
 * to keep a live engine session (i.e. an active GeckoView content process). As soon as a
 * tab stops being the selected tab its engine session is unlinked, which frees the RAM
 * and CPU that tab was using. Nothing is deleted: the tab's state (URL, scroll position,
 * form data, navigation history) is untouched, so the next time the user taps that tab it
 * transparently gets a fresh engine session and reloads — the same recovery path Fenix
 * already uses after a low-memory kill or process death.
 *
 * Tabs that are actively playing audio/video are left alone so switching away from a tab
 * doesn't kill music/video playback.
 */
class RamSaverMiddleware(
    private val settings: Settings,
) : Middleware<BrowserState, BrowserAction> {

    private var lastSelectedTabId: String? = null

    override fun invoke(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        next(action)

        if (!settings.ramSaverModeEnabled) {
            return
        }

        when (action) {
            is TabListAction.SelectTabAction -> {
                unloadEverythingExcept(context, keepTabId = action.tabId)
            }
            is TabListAction.AddTabAction,
            is TabListAction.AddMultipleTabsAction,
            is TabListAction.RestoreAction,
            -> {
                // Newly added/restored tabs shouldn't get a free engine session either
                // unless they immediately became the selected tab.
                unloadEverythingExcept(context, keepTabId = context.state.selectedTabId)
            }
            else -> {
                // no-op
            }
        }
    }

    private fun unloadEverythingExcept(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        keepTabId: String?,
    ) {
        lastSelectedTabId = keepTabId

        context.state.tabs.forEach { tab ->
            if (tab.id == keepTabId) return@forEach
            if (tab.engineState.engineSession == null) return@forEach

            val isPlayingMedia = tab.mediaSessionState?.playbackState ==
                MediaSession.PlaybackState.PLAYING

            if (!isPlayingMedia) {
                context.store.dispatch(EngineAction.UnlinkEngineSessionAction(tab.id))
            }
        }

        // Custom tabs (opened from other apps) are intentionally left alone: they're
        // short-lived and unlinking them tends to be more disruptive than helpful.
    }
}
