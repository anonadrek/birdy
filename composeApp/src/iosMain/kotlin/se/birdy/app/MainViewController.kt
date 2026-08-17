package se.birdy.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

private val graph by lazy { buildIosAppGraph().also { AppGraphHolderIos.current = it } }

@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController { App(graph) }
