package com.soobakjonmat.customlayoutkeyboard.layout

import android.annotation.SuppressLint
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding
import androidx.core.view.size
import com.soobakjonmat.customlayoutkeyboard.MainKeyboardService
import com.soobakjonmat.customlayoutkeyboard.R

class EnglishLayout(mainKeyboardService: MainKeyboardService) : LanguageLayout(mainKeyboardService) {

    @SuppressLint("ClickableViewAccessibility")
    override fun init() {
        super.init()

        letterList = listOf(
            arrayOf("q", "w", "e", "r", "t", "y", "u", "i", "o"),
            arrayOf("a", "s", "d", "f", "g", "h", "j", "k", "l","p"),
            arrayOf("z", "x", "c", "v", "b", "n", "m")
        )
        rowList = Array(letterList.size) { LinearLayout(mainKeyboardView.context) }

        for (i in letterList.indices) {
            // initialise btnList, combinedLetterList, previewPopupList
            btnList.add(Array(letterList[i].size) { Button(ContextThemeWrapper(mainKeyboardView.context, R.style.Theme_LetterBtn)) })
            combinedLetterList.add(Array(letterList[i].size) { SpannableString("") })
            previewPopupList.add(Array(letterList[i].size) { PopupWindow(ContextThemeWrapper(mainKeyboardView.context, R.style.Theme_TransparentBackground)) })
            // set linear layout attributes
            rowList[i].layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            rowList[i].orientation = LinearLayout.HORIZONTAL
            // create letter buttons and set attributes
            for (j in letterList[i].indices) {
                val text = SpannableString(mainKeyboardService.subTextLetterList[i][j] + "\n" + letterList[i][j])
                if (mainKeyboardService.subTextLetterList[i][j].isNotEmpty()) {
                    // set subtext size
                    text.setSpan(
                        ForegroundColorSpan(mainKeyboardService.subtextColor),
                        0,
                        1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                // set text size
                text.setSpan(
                    RelativeSizeSpan(resources.getFloat(R.dimen.text_scale)),
                    text.length - 1,
                    text.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                // set font
                text.setSpan(
                    TypefaceSpan("Arial"),
                    0,
                    text.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                combinedLetterList[i][j] = text
                btnList[i][j].text = text
                btnList[i][j].layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
                btnList[i][j].isAllCaps = false
                btnList[i][j].setPadding(0)

                val gestureDetector = GestureDetector(mainKeyboardService, EnglishGestureListener(i, j))
                btnList[i][j].setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        previewPopupList[i][j].dismiss()
                        (previewPopupList[i][j].contentView as TextView).text = letterList[i][j]
                    }
                    gestureDetector.onTouchEvent(event)
                }
                // add buttons to linear layouts
                rowList[i].addView(btnList[i][j])

                // key preview popup
                previewPopupList[i][j].isTouchable = false
                previewPopupList[i][j].contentView = TextView(ContextThemeWrapper(mainKeyboardView.context, R.style.Theme_PreviewPopupTextView))
                (previewPopupList[i][j].contentView as TextView).background = ResourcesCompat.getDrawable(resources, R.drawable.preview_popup_background, ContextThemeWrapper(mainKeyboardView.context, R.style.Theme_PreviewPopupTextView).theme)
                (previewPopupList[i][j].contentView as TextView).text = letterList[i][j]
                (previewPopupList[i][j].contentView as TextView).elevation = 8f
                (previewPopupList[i][j].contentView as TextView).setPadding(resources.getInteger(R.integer.english_preview_popup_text_padding), 0, 0, 0)
                (previewPopupList[i][j].contentView as TextView).setTextSize(TypedValue.COMPLEX_UNIT_SP, resources.getFloat(R.dimen.preview_popup_text_size))
                previewPopupList[i][j].setBackgroundDrawable(null)
            }
        }

        capsLockBtn.setOnClickListener {
            mainKeyboardService.vibrate()
            when (capsLockMode) {
                0 -> {
                    setToUppercase()
                    capsLockMode = 1
                    capsLockBtn.setImageDrawable(capsLockMode1Image)
                    for (i in letterList.indices) {
                        for (j in letterList[i].indices) {
                            (previewPopupList[i][j].contentView as TextView).text = letterList[i][j].uppercase()
                        }
                    }
                }
                1 -> {
                    capsLockMode = 2
                    capsLockBtn.setImageDrawable(capsLockMode2Image)
                }
                2 -> {
                    setToLowercase()
                    capsLockMode = 0
                    capsLockBtn.setImageDrawable(capsLockMode0Image)
                    for (i in letterList.indices) {
                        for (j in letterList[i].indices) {
                            (previewPopupList[i][j].contentView as TextView).text = letterList[i][j].lowercase()
                        }
                    }
                }
            }
        }
        rowList[rowList.size-1].addView(capsLockBtn, 0)

        // set backspaceBtn attributes
        backspaceBtn.setImageDrawable(mainKeyboardService.backspaceImage)
        backspaceBtn.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            resources.getFloat(R.dimen.backspace_weight)
        )
        backspaceBtn.setOnClickListener {
            mainKeyboardService.vibrate()
            if (mainKeyboardService.currentInputConnection.getSelectedText(0).isNullOrEmpty()) {
                // no selection, so delete previous character
                mainKeyboardService.currentInputConnection.deleteSurroundingText(1, 0)
            } else {
                // delete the selection
                mainKeyboardService.currentInputConnection.commitText("", 1)
            }
        }
        rowList[rowList.size-1].addView(backspaceBtn, rowList[rowList.size-1].size)
    }


    override fun setToUppercase() {
        for (i in letterList.indices) {
            for (j in letterList[i].indices) {
                btnList[i][j].isAllCaps = true
            }
        }
    }

    override fun setToLowercase() {
        for (i in letterList.indices) {
            for (j in letterList[i].indices) {
                btnList[i][j].isAllCaps = false
            }
        }
    }

    private inner class EnglishGestureListener(i: Int, j: Int) : LanguageGestureListener(i, j) {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            super.onSingleTapUp(event)
            if (capsLockMode == 0) {
                mainKeyboardService.currentInputConnection.commitText(letterList[i][j], 1)
            } else {
                if (capsLockMode == 1) {
                    capsLockMode = 0
                    setToLowercase()
                    capsLockBtn.setImageDrawable(capsLockMode0Image)
                    for (i in letterList.indices) {
                        for (j in letterList[i].indices) {
                            (previewPopupList[i][j].contentView as TextView).text = letterList[i][j].lowercase()
                        }
                    }
                }
                mainKeyboardService.currentInputConnection.commitText(letterList[i][j].uppercase(), 1)
            }
            return true
        }
    }
}