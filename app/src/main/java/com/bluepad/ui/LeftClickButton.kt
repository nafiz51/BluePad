package com.bluepad.ui

import android.content.Context

/** Touch holds the left button; accessibility/keyboard activation sends a complete click. */
class LeftClickButton @JvmOverloads constructor(context: Context, press: () -> Unit = {}, release: () -> Unit = {}) : MouseClickButton(context,press,release)
