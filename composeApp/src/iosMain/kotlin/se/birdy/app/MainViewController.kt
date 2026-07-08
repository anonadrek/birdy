package se.birdy.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

private val graph by lazy { buildIosAppGraph() }

@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController { App(graph) }
