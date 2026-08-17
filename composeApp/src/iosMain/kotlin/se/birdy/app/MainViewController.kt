package se.birdy.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import se.birdy.app.notifications.installIosNotificationLifecycle

private val graph by lazy {
    buildIosAppGraph().also {
        AppGraphHolderIos.current = it
        installIosNotificationLifecycle(it)
    }
}

@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController { App(graph) }
