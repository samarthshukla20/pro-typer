
package com.samarthshukla.protyper;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

public class BackspaceAwareEditText extends AppCompatEditText {

    private boolean backspaceEnabled = true;
    private boolean internalChange = false;
    private boolean intentionalProgrammaticChange = false;

    private String previousText = "";
    private int previousSelectionStart = 0;
    private int previousSelectionEnd = 0;

    public BackspaceAwareEditText(@NonNull Context context) {
        super(context);
        init();
    }

    public BackspaceAwareEditText(
            @NonNull Context context,
            @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
        init();
    }

    public BackspaceAwareEditText(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        previousText = getText() == null ? "" : getText().toString();

        addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after
            ) {
                if (internalChange) return;

                previousText = s == null ? "" : s.toString();

                int selectionStart = getSelectionStart();
                int selectionEnd = getSelectionEnd();

                previousSelectionStart = selectionStart >= 0
                        ? selectionStart : previousText.length();

                previousSelectionEnd = selectionEnd >= 0
                        ? selectionEnd : previousSelectionStart;
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count
            ) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (internalChange ||
                        intentionalProgrammaticChange ||
                        backspaceEnabled ||
                        editable == null) {
                    return;
                }

                String currentText = editable.toString();

                // Fallback for keyboards that bypass InputConnection deletion.
                if (currentText.length() < previousText.length()) {
                    restorePreviousText();
                } else {
                    rememberCurrentState(currentText);
                }
            }
        });
    }

    private void rememberCurrentState(String text) {
        previousText = text;

        int selectionStart = getSelectionStart();
        int selectionEnd = getSelectionEnd();

        previousSelectionStart = selectionStart >= 0
                ? selectionStart : text.length();

        previousSelectionEnd = selectionEnd >= 0
                ? selectionEnd : previousSelectionStart;
    }

    private void restorePreviousText() {
        internalChange = true;

        try {
            setText(previousText);

            int length = previousText.length();

            int start = Math.max(
                    0, Math.min(previousSelectionStart, length)
            );

            int end = Math.max(
                    0, Math.min(previousSelectionEnd, length)
            );

            setSelection(Math.min(start, end), Math.max(start, end));

        } finally {
            internalChange = false;
        }
    }

    // Use this instead of setText("") for intentional game clearing.
    public void clearText() {
        intentionalProgrammaticChange = true;

        try {
            setText("");
            setSelection(0);
        } finally {
            intentionalProgrammaticChange = false;
            rememberCurrentState("");
        }
    }

    public void setBackspaceEnabled(boolean enabled) {
        backspaceEnabled = enabled;

        if (getText() != null) {
            rememberCurrentState(getText().toString());
        }
    }

    public boolean isBackspaceEnabled() {
        return backspaceEnabled;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection target = super.onCreateInputConnection(outAttrs);

        if (target == null) return null;

        return new InputConnectionWrapper(target, false) {

            @Override
            public boolean deleteSurroundingText(
                    int beforeLength, int afterLength
            ) {
                if (!backspaceEnabled && beforeLength > 0) {
                    return true;
                }

                return super.deleteSurroundingText(
                        beforeLength, afterLength
                );
            }

            @Override
            public boolean deleteSurroundingTextInCodePoints(
                    int beforeLength, int afterLength
            ) {
                if (!backspaceEnabled && beforeLength > 0) {
                    return true;
                }

                return super.deleteSurroundingTextInCodePoints(
                        beforeLength, afterLength
                );
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                if (!backspaceEnabled &&
                        event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                    return true;
                }

                return super.sendKeyEvent(event);
            }
        };
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!backspaceEnabled &&
                keyCode == KeyEvent.KEYCODE_DEL) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!backspaceEnabled &&
                keyCode == KeyEvent.KEYCODE_DEL) {
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }
}