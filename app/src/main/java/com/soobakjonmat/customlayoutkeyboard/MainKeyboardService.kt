package com.soobakjonmat.customlayoutkeyboard
import android.view.inputmethod.InputMethodManager
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.core.view.setPadding
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.soobakjonmat.customlayoutkeyboard.layout.*
import kotlin.math.absoluteValue
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import android.view.MenuItem
import android.widget.PopupMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log


class MainKeyboardService : InputMethodService() {
    private lateinit var keyboardRoot: FrameLayout
    lateinit var mainKeyboardView: LinearLayout
    lateinit var phoneNumKeyboardView: LinearLayout

    private val languageMode = mapOf("Special" to 0, "English" to 1, "Korean" to 2)
    private lateinit var standardLayouts: Array<KeyboardLayout>
    private lateinit var phoneNumberLayout: PhoneNumberLayout

    var rapidTextDeleteInterval: Long = 200 // in milliseconds
    val gestureMinDist = 120

    private val numbers = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val numBtnSubTexts = arrayOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")")
    val subTextLetterList = listOf(
        arrayOf("", "", "", "\\", "|", "[", "]", "{", "}"),
        arrayOf("", "", "`", "~", "-", "_", ":", ";", "\"", "'"),
        arrayOf("", "<", ">", "=", "+", "/", "?")
    )
    private val combinedNums = mutableListOf<SpannableString>()
    lateinit var numberRow: LinearLayout

    var subtextColor = 0

    private lateinit var numBtns: Array<Button>
    private lateinit var numBtnPreviewPopupArr: Array<PopupWindow>
    private lateinit var specialKeyBtn: Button
    private lateinit var commaBtn: Button
    private lateinit var spacebarBtn: Button
    private lateinit var fullStopBtn: Button
    private lateinit var returnKeyBtn: ImageButton

    var capsLockMode0Image: VectorDrawableCompat? = null
    var capsLockMode1Image: VectorDrawableCompat? = null
    var capsLockMode2Image: VectorDrawableCompat? = null
    var backspaceImage: VectorDrawableCompat? = null
    private var returnKeyImageSearch: VectorDrawableCompat? = null
    private var returnKeyImageDone: VectorDrawableCompat? = null
    private var returnKeyImageForward: VectorDrawableCompat? = null
    private var returnKeyImageReturn: VectorDrawableCompat? = null
    private var returnKeyImageTab: VectorDrawableCompat? = null

    var currReturnKeyImage: VectorDrawableCompat? = null

    private val searchIconActionList = arrayOf(
        EditorInfo.IME_ACTION_SEARCH,
        EditorInfo.IME_ACTION_GO,
    )
    private val doneIconActionList = arrayOf(
        EditorInfo.IME_ACTION_DONE
    )
    private val returnIconActionList = arrayOf(
        EditorInfo.IME_ACTION_SEND,
        EditorInfo.IME_ACTION_NONE,
        EditorInfo.IME_ACTION_UNSPECIFIED,
    )
    private val tabIconActionList = arrayOf(
        EditorInfo.IME_ACTION_NEXT
    )
    var currIMEOptions = 0

    private var mode = languageMode["English"]!!
    private var lastDownSpacebarX = 0f
    private var lastCursorPos = 0

    private lateinit var vibrator: Vibrator

    private val settingsKeyList = arrayOf("settings_long_click_delete_speed", "settings_keyboard_height")
    private val myReceiver = MyReceiver()
    // todo word suggestion

    private fun addDictionaryButton() {
        val dictionaryBtn = Button(ContextThemeWrapper(this, R.style.Theme_ControlBtn))
        dictionaryBtn.text = "Dictionary"
        dictionaryBtn.setOnClickListener {
            val parentLayout = dictionaryBtn.parent as ViewGroup
            val index = parentLayout.indexOfChild(dictionaryBtn)

            // Create a horizontal layout to hold the text field, search button, and back button
            val horizontalLayout = LinearLayout(ContextThemeWrapper(this, R.style.Theme_ControlBtn))
            horizontalLayout.orientation = LinearLayout.HORIZONTAL

            // Create a text field
            val textField = EditText(ContextThemeWrapper(this, R.style.Theme_ControlBtn))
            textField.hint = "Search Dictionary"
            textField.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            textField.inputType = InputType.TYPE_CLASS_TEXT // Set input type explicitly
            textField.requestFocus() // Ensure the textField gets focus

            // Create a search button
            val searchButton = Button(ContextThemeWrapper(this, R.style.Theme_ControlBtn))
            searchButton.text = "Search"
            searchButton.setOnClickListener {
                val searchTerm = textField.text.toString()
                if (searchTerm.isNotEmpty()) {
                    // Perform API call and display response in the popup
                    fetchDictionaryData(searchTerm, horizontalLayout)
                } else {
                    Toast.makeText(this, "Please enter a search term", Toast.LENGTH_SHORT).show()
                }
            }

            // Create a back button
            val backButton = Button(ContextThemeWrapper(this, R.style.Theme_ControlBtn))
            backButton.text = "Back"
            backButton.setOnClickListener {
                // Remove the text field, search button, and back button
                parentLayout.removeView(horizontalLayout)
                // Add back the Dictionary button at the top
                parentLayout.addView(dictionaryBtn, 0)
            }

            // Add the text field, search button, and back button to the horizontal layout
            horizontalLayout.addView(textField)
            horizontalLayout.addView(searchButton)
            horizontalLayout.addView(backButton)

            // Replace the Dictionary button with the horizontal layout
            parentLayout.removeView(dictionaryBtn)
            parentLayout.addView(horizontalLayout, index)

            // Show the keyboard for the textField
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(textField, InputMethodManager.SHOW_IMPLICIT)
        }

        // Add the Dictionary button at the top of the mainKeyboardView
        mainKeyboardView.addView(dictionaryBtn, 0)
    }

    private fun fetchDictionaryData(query: String, horizontalLayout: LinearLayout) {
        val url = "https://api.dictionaryapi.dev/api/v2/entries/en/$query"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = URL(url).readText()
                Log.d("DictionaryAPI", "Response: $response") // Log the response

                // Process the fetched data and display it above the search bar
                withContext(Dispatchers.Main) {
                    processDictionaryData(response, query, horizontalLayout)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("response","Error fetching data")
            }
        }
    }

    private fun processDictionaryData(data: String, query: String, horizontalLayout: LinearLayout) {
        try {
            val jsonArray = JSONArray(data)
            if (jsonArray.length() > 0) {
                val jsonObject = jsonArray.getJSONObject(0)
                val word = jsonObject.getString("word")
                val definitionArray = jsonObject.getJSONArray("meanings")

                val definitions = StringBuilder()
                for (i in 0 until definitionArray.length()) {
                    val meaningObj = definitionArray.getJSONObject(i)
                    val partOfSpeech = meaningObj.getString("partOfSpeech")
                    val definitionsArray = meaningObj.getJSONArray("definitions")

                    definitions.append("$partOfSpeech:\n")
                    for (j in 0 until definitionsArray.length()) {
                        val definitionObj = definitionsArray.getJSONObject(j)
                        val definition = definitionObj.getString("definition")
                        definitions.append(" - $definition\n")
                    }
                    definitions.append("\n")
                }

                // Log the processed data
                Log.d("DictionaryAPI", "Processed Data: $definitions")

                // Clear previous views in horizontal layout
                horizontalLayout.removeAllViews()

                // Add the back button
                val backButton = Button(this)
                backButton.text = "Back"
                backButton.setOnClickListener {
                    // Clear the horizontal layout and show the search UI again
                    horizontalLayout.removeAllViews()
                    addDictionaryButton()
                }
                horizontalLayout.addView(backButton)

                // Display the definitions above the search bar
                val textView = TextView(this)
                textView.text = definitions.toString()
                textView.gravity = Gravity.CENTER_HORIZONTAL
                horizontalLayout.addView(textView) // Add the text view after the back button
            } else {
                Toast.makeText(this, "No definitions found for the word", Toast.LENGTH_SHORT).show()
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            Toast.makeText(this, "Error processing data", Toast.LENGTH_SHORT).show()
        }
    }





    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()

        mainKeyboardView = layoutInflater.inflate(R.layout.main_keyboardview, null) as LinearLayout
        phoneNumKeyboardView = layoutInflater.inflate(R.layout.phone_number_keyboardview, null) as LinearLayout

        specialKeyBtn = mainKeyboardView.findViewById(R.id.special_key)
        commaBtn = mainKeyboardView.findViewById(R.id.comma)
        spacebarBtn = mainKeyboardView.findViewById(R.id.spacebar)
        fullStopBtn = mainKeyboardView.findViewById(R.id.full_stop)
        returnKeyBtn = mainKeyboardView.findViewById(R.id.return_key)

        val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibrator = vm.defaultVibrator

        capsLockMode0Image = VectorDrawableCompat.create(resources, R.drawable.caps_lock_mode_0, null)
        capsLockMode1Image = VectorDrawableCompat.create(resources, R.drawable.caps_lock_mode_1, null)
        capsLockMode2Image = VectorDrawableCompat.create(resources, R.drawable.caps_lock_mode_2, null)
        backspaceImage = VectorDrawableCompat.create(resources, R.drawable.ic_outline_backspace_24, null)
        returnKeyImageSearch = VectorDrawableCompat.create(resources, R.drawable.ic_outline_search_24, null)
        returnKeyImageDone = VectorDrawableCompat.create(resources, R.drawable.ic_outline_done_24, null)
        returnKeyImageReturn = VectorDrawableCompat.create(resources, R.drawable.ic_outline_keyboard_return_24, null)
        returnKeyImageTab = VectorDrawableCompat.create(resources, R.drawable.ic_outline_keyboard_tab_24, null)
        returnKeyImageForward = VectorDrawableCompat.create(resources, R.drawable.ic_outline_arrow_forward_24, null)

        // number buttons
        numberRow = LinearLayout(mainKeyboardView.context)
        numberRow.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        numberRow.orientation = LinearLayout.HORIZONTAL
        numBtns = Array(numbers.size) { Button(ContextThemeWrapper(this, R.style.Theme_LetterBtn)) }
        numBtnPreviewPopupArr = Array(numbers.size) { PopupWindow(ContextThemeWrapper(this, R.style.Theme_TransparentBackground)) }

        subtextColor = if (isDarkTheme()) {
            getColor(R.color.dark_theme_subtext)
        } else {
            getColor(R.color.light_theme_subtext)
        }

        for (i in numbers.indices) {
            numBtns[i].layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            numBtns[i].setPadding(0)

            combinedNums.add(SpannableString(numBtnSubTexts[i] + "\n" + numbers[i]))

            if (numBtnSubTexts[i].isNotEmpty()) {
                combinedNums[i].setSpan(ForegroundColorSpan(subtextColor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            combinedNums[i].setSpan(RelativeSizeSpan(resources.getFloat(R.dimen.text_scale)), combinedNums[i].length-1, combinedNums[i].length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            numBtns[i].text = combinedNums[i]


            val gestureDetector = GestureDetector(this, NumBtnGestureListener(i))
            numBtns[i].setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    numBtnPreviewPopupArr[i].dismiss()
                    (numBtnPreviewPopupArr[i].contentView as TextView).text = numbers[i]
                }
                gestureDetector.onTouchEvent(event)
            }

            numberRow.addView(numBtns[i])

            // key preview popup
            numBtnPreviewPopupArr[i].isTouchable = false
            numBtnPreviewPopupArr[i].contentView = TextView(ContextThemeWrapper(this, R.style.Theme_PreviewPopupTextView))
            (numBtnPreviewPopupArr[i].contentView as TextView).background = ResourcesCompat.getDrawable(resources, R.drawable.preview_popup_background, ContextThemeWrapper(this, R.style.Theme_PreviewPopupTextView).theme)
            (numBtnPreviewPopupArr[i].contentView as TextView).text = numbers[i]
            (numBtnPreviewPopupArr[i].contentView as TextView).elevation = 8f
            (numBtnPreviewPopupArr[i].contentView as TextView).setPadding(resources.getInteger(R.integer.english_preview_popup_text_padding), 0, 0, 0)
            (numBtnPreviewPopupArr[i].contentView as TextView).setTextSize(TypedValue.COMPLEX_UNIT_SP, resources.getFloat(R.dimen.preview_popup_text_size))
            numBtnPreviewPopupArr[i].setBackgroundDrawable(null)
        }

        // special key
        specialKeyBtn.setOnClickListener {
            vibrate()
            if (mode != 0) {
                mode = 0
                changeLayout()
                specialKeyBtn.text = getString(R.string.special_key_text_english)
            } else {
                mode = 1
                changeLayout()
                specialKeyBtn.text = getString(R.string.special_key_text_special_key)
            }
        }

        // spacebar
        spacebarBtn.setOnTouchListener { btn, motionEvent ->
            val action = motionEvent.action
            if (action == MotionEvent.ACTION_DOWN) {
                btn.isPressed = true
                vibrate()
                lastDownSpacebarX = motionEvent.rawX
            } else if (action == MotionEvent.ACTION_MOVE) {
                // todo show transition between layout on top. Use popup window
            } else if (action == MotionEvent.ACTION_UP) {
                btn.isPressed = false
                // on scroll keyboard
                if ((lastDownSpacebarX - motionEvent.rawX).absoluteValue > gestureMinDist) {
                    when (mode) {
                        0 -> {
                            specialKeyBtn.text = getString(R.string.special_key_text_special_key)
                            mode = 1
                        }
                        1 -> {
                            mode = 2
                        }
                        2 -> {
                            mode = 1
                        }
                    }
                    changeLayout()
                }
                // on click
                else {
                    resetAndFinishComposing()
                    currentInputConnection.commitText(" ", 1)
                }
            }
            return@setOnTouchListener true
        }

        // comma
        commaBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, resources.getFloat(R.dimen.default_text_size))
        commaBtn.setOnClickListener {
            vibrate()
            resetAndFinishComposing()
            currentInputConnection.commitText(",", 1)
        }
        // full stop
        fullStopBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, resources.getFloat(R.dimen.default_text_size))
        fullStopBtn.setOnClickListener {
            vibrate()
            resetAndFinishComposing()
            currentInputConnection.commitText(".", 1)
        }
        // return key
        returnKeyBtn.setOnClickListener {
            vibrate()
            resetAndFinishComposing()
            if ((currIMEOptions and EditorInfo.IME_MASK_ACTION) in returnIconActionList ||
                (currIMEOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == EditorInfo.IME_FLAG_NO_ENTER_ACTION) {
                currentInputConnection.commitText("\n", 1)
            } else {
                currentInputConnection.performEditorAction(currIMEOptions and EditorInfo.IME_MASK_ACTION)
            }
        }

        standardLayouts = arrayOf(
            SpecialKeyLayout(this),
            EnglishLayout(this),
            KoreanLayout(this)
        )
        for (layout in standardLayouts) {
            layout.init()
        }
        phoneNumberLayout = PhoneNumberLayout(this)
        phoneNumberLayout.init()

        // init layout
        changeLayout()
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        currIMEOptions = currentInputEditorInfo.imeOptions

        // change return key button image
        when (currIMEOptions and EditorInfo.IME_MASK_ACTION) {
            in searchIconActionList -> {
                currReturnKeyImage = returnKeyImageSearch
            }
            in doneIconActionList -> {
                currReturnKeyImage = returnKeyImageDone
            }
            in returnIconActionList -> {
                currReturnKeyImage = returnKeyImageReturn
            }
            in tabIconActionList -> {
                currReturnKeyImage = returnKeyImageTab
            }
            else -> {
                currReturnKeyImage = returnKeyImageForward
            }
        }
        if ((currIMEOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == EditorInfo.IME_FLAG_NO_ENTER_ACTION) {
            currReturnKeyImage = returnKeyImageReturn
        }
        returnKeyBtn.setImageDrawable(currReturnKeyImage)

        keyboardRoot = if (mainKeyboardView.parent != null) {
            mainKeyboardView.parent as FrameLayout
        } else {
            phoneNumKeyboardView.parent as FrameLayout
        }
        // if inputType is phone number
        if (editorInfo?.inputType?.and(InputType.TYPE_MASK_CLASS) == (InputType.TYPE_CLASS_PHONE or InputType.TYPE_CLASS_NUMBER)) {
            if (keyboardRoot.children.contains(mainKeyboardView)) {
                keyboardRoot.removeView(mainKeyboardView)
                phoneNumberLayout.updateReturnKeyImage()
                setInputView(phoneNumKeyboardView)
            }
        } else {
            if (keyboardRoot.children.contains(phoneNumKeyboardView)) {
                keyboardRoot.removeView(phoneNumKeyboardView)
                setInputView(mainKeyboardView)
            }
        }

        // call onUpdateCursorAnchorInfo() whenever cursor/anchor position is changed
        currentInputConnection.requestCursorUpdates(InputConnection.CURSOR_UPDATE_MONITOR)
        // set subtext color according to whether dark mode is turned on or not
        updateSubtextColor()
    }

    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo) {
        val currPos = cursorAnchorInfo.selectionStart
        if ((currPos != lastCursorPos + 1 || !(standardLayouts[languageMode["Korean"]!!] as KoreanLayout).hangulAssembler.cursorMovedBySystem) && currPos != lastCursorPos) {
            resetAndFinishComposing()
        }
        lastCursorPos = currPos
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        resetAndFinishComposing()
    }

    private fun isDarkTheme(): Boolean {
        if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
            return true
        }
        return false
    }

    private fun updateSubtextColor() {
        subtextColor = if (isDarkTheme()) {
            if (subtextColor == getColor(R.color.dark_theme_subtext)) {
                return
            } else {
                getColor(R.color.dark_theme_subtext)
            }
        } else {
            if (subtextColor == getColor(R.color.dark_theme_subtext)) {
                getColor(R.color.light_theme_subtext)
            } else {
                return
            }
        }
        for (i in numbers.indices) {
            if (numBtnSubTexts[i].isNotEmpty()) {
                combinedNums[i].setSpan(
                    ForegroundColorSpan(subtextColor),
                    0,
                    1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            numBtns[i].text = combinedNums[i]
        }
        (standardLayouts[languageMode["English"]!!] as LanguageLayout).updateSubtextColor()
        (standardLayouts[languageMode["Korean"]!!] as LanguageLayout).updateSubtextColor()
    }

    fun deleteByWord(direction: Int): Boolean {
        resetAndFinishComposing()
        vibrate()
        var count = 1
        when (direction) {
            -1 -> {
                val firstBefore = currentInputConnection.getTextBeforeCursor(1, 0)
                if (firstBefore.isNullOrEmpty()) {
                    return false
                } else if (firstBefore == "\n") {
                    this.currentInputConnection.deleteSurroundingText(1, 0)
                    return true
                }
                while (true) {
                    val text = currentInputConnection.getTextBeforeCursor(count, 0)
                    if (text?.first() == '\n') {
                        count--
                        break
                    }
                    if (text?.first() == ' ' || text?.length != count) {
                        break
                    }
                    count++
                }
                this.currentInputConnection.deleteSurroundingText(count, 0)
                return true
            }
            1 -> {
                val firstAfter = currentInputConnection.getTextAfterCursor(1, 0)
                if (firstAfter.isNullOrEmpty()) {
                    return false
                } else if (firstAfter == "\n") {
                    this.currentInputConnection.deleteSurroundingText(0, 1)
                    return true
                }
                while (true) {
                    val text = currentInputConnection.getTextAfterCursor(count, 0)
                    if (text?.last() == '\n') {
                        count--
                        break
                    }
                    if (text?.last() == ' ' || text?.length != count) {
                        break
                    }
                    count++
                }
                this.currentInputConnection.deleteSurroundingText(0, count)
                return true
            }
            else -> {
                throw Exception("Wrong deleteByWord parameter. Direction can be only either 1 or -1.")
            }
        }
    }

    private fun changeLayout() {
        resetAndFinishComposing()
        if (mainKeyboardView.childCount > 1) { // check if this is initial layout insert call
            // delete remove every row except control row
            for (i in 0 until mainKeyboardView.childCount-1) {
                mainKeyboardView.removeViewAt(0)
            }
        }
        standardLayouts[mode].insertLayout()
        when (mode) {
            // special key layout
            languageMode["Special"]!! -> {
                spacebarBtn.text = getString(R.string.spacebar_text_special_key)
            }
            // english layout
            languageMode["English"]!! -> {
                spacebarBtn.text = getString(R.string.spacebar_text_english)
            }
            // korean layout
            languageMode["Korean"]!! -> {
                spacebarBtn.text = getString(R.string.spacebar_text_korean)
            }
        }
    }

    fun resetAndFinishComposing() {
        if (mode == languageMode["Korean"]) {
            (standardLayouts[languageMode["Korean"]!!] as KoreanLayout).hangulAssembler.reset()
        }
        if (currentInputConnection != null) {
            currentInputConnection.finishComposingText()
        }
    }

    fun vibrate() {
        vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun changeKeyboardSettings(intent: Intent?) {
        when (intent?.getStringExtra("key")) {
            settingsKeyList[0] -> {
                rapidTextDeleteInterval = intent.getLongExtra("value", rapidTextDeleteInterval)
            }
            settingsKeyList[1] -> {
                mainKeyboardView.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    intent.getIntExtra("value", mainKeyboardView.height)
                )
                phoneNumKeyboardView.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    intent.getIntExtra("value", mainKeyboardView.height)
                )
            }
        }
    }

    override fun onCreateInputView(): View {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SEND)
        registerReceiver(myReceiver, filter)
        addDictionaryButton() // Call the method to add the Dictionary button

        return mainKeyboardView
    }

    inner class MyReceiver : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            changeKeyboardSettings(p1)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(myReceiver)
        super.onDestroy()
    }

    inner class NumBtnGestureListener(private val i: Int) : GestureDetector.OnGestureListener {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            numBtns[i].isPressed = false
            numBtnPreviewPopupArr[i].dismiss()
            resetAndFinishComposing()
            currentInputConnection.commitText(numbers[i], 1)
            return true
        }

        override fun onDown(event: MotionEvent): Boolean {
            numBtns[i].isPressed = true
            val loc = IntArray(2)
            numBtns[i].getLocationInWindow(loc)
            numBtnPreviewPopupArr[i].showAtLocation(numBtns[i], Gravity.NO_GRAVITY, 0, 0)
            numBtnPreviewPopupArr[i].update(loc[0], loc[1]-128, 128, 128, false)
            vibrate()
            return true
        }

        override fun onFling(p0: MotionEvent, p1: MotionEvent, p2: Float, p3: Float): Boolean {
            numBtns[i].isPressed = false
            numBtnPreviewPopupArr[i].dismiss()
            if (p0.rawX - p1.rawX > gestureMinDist) {
                deleteByWord(-1)
                return true
            }
            else if (p1.rawX - p0.rawX > gestureMinDist) {
                deleteByWord(1)
                return true
            }
            return false
        }

        override fun onScroll(p0: MotionEvent, p1: MotionEvent, p2: Float, p3: Float): Boolean {
            numBtns[i].isPressed = false
            numBtnPreviewPopupArr[i].dismiss()
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            (numBtnPreviewPopupArr[i].contentView as TextView).text = numBtnSubTexts[i]
            vibrate()
            resetAndFinishComposing()
            currentInputConnection.commitText(numBtnSubTexts[i], 1)
        }

        override fun onShowPress(p0: MotionEvent) {}
    }
}
