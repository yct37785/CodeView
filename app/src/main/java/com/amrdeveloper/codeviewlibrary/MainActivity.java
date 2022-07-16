package com.amrdeveloper.codeviewlibrary;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.amrdeveloper.codeview.Keyword;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import com.amrdeveloper.codeview.Code;
import com.amrdeveloper.codeview.CodeView;
import com.amrdeveloper.codeviewlibrary.plugin.CommentManager;
import com.amrdeveloper.codeviewlibrary.plugin.SourcePositionListener;
import com.amrdeveloper.codeviewlibrary.plugin.UndoRedoManager;
import com.amrdeveloper.codeviewlibrary.syntax.ThemeName;
import com.amrdeveloper.codeviewlibrary.syntax.LanguageName;
import com.amrdeveloper.codeviewlibrary.syntax.LanguageManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private final int AUTO_COMPLETE_BOUNCE_MS = 100;

    private CodeView codeView;
    private LanguageManager languageManager;
    private CommentManager commentManager;
    private UndoRedoManager undoRedoManager;

    private TextView languageNameText;
    private TextView sourcePositionText;

    private final TextChangeWatcher textChangeWatcher = new TextChangeWatcher();

    private LanguageName currentLanguage = LanguageName.PYTHON;
    private ThemeName currentTheme = ThemeName.MONOKAI;

    private final boolean useModernAutoCompleteAdapter = true;

    private int cursor_line, cursor_col;
    private String currentCode = "";
    private ScheduledExecutorService autoCompleteService = Executors.newSingleThreadScheduledExecutor();
    private Future<?> autoCompleteFuture = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        if (!Python.isStarted()) {
//            Python.start(new AndroidPlatform(this));
//        }

        configCodeView();
        configCodeViewPlugins();
        Log.d("CodeView", "CodeView onCreate");
    }

    private void configCodeView() {
        codeView = findViewById(R.id.codeView);

        // turn off auto caps
        codeView.setRawInputType(InputType.TYPE_TEXT_VARIATION_NORMAL | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        // Change default font to JetBrains Mono font
        Typeface jetBrainsMono = ResourcesCompat.getFont(this, R.font.jetbrains_mono_medium);
        codeView.setTypeface(jetBrainsMono);

        // Setup Line number feature
        codeView.setEnableLineNumber(true);
        codeView.setLineNumberTextColor(Color.GRAY);
        codeView.setLineNumberTextSize(25f);

        // Setup Auto indenting feature
        codeView.setTabLength(4);
        codeView.setEnableAutoIndentation(true);

        // Setup the language and theme with SyntaxManager helper class
        languageManager = new LanguageManager(this, codeView);
        languageManager.applyTheme(currentLanguage, currentTheme);

        // Setup auto pair complete
        final Map<Character, Character> pairCompleteMap = new HashMap<>();
        pairCompleteMap.put('{', '}');
        pairCompleteMap.put('[', ']');
        pairCompleteMap.put('(', ')');
        pairCompleteMap.put('<', '>');
        pairCompleteMap.put('"', '"');
        pairCompleteMap.put('\'', '\'');

        codeView.setPairCompleteMap(pairCompleteMap);
        codeView.enablePairComplete(true);
        codeView.enablePairCompleteCenterCursor(true);

        // listener for text change
        codeView.addTextChangedListener(textChangeWatcher);

        // Setup the auto complete and auto indenting for the current language
//        configLanguageAutoComplete();
        configLanguageAutoIndentation();
    }

    private void configLanguageAutoComplete() {
        if (useModernAutoCompleteAdapter) {
            // Load the code list (keywords and snippets) for the current language
            List<Code> codeList = languageManager.getLanguageCodeList(currentLanguage);

            // Use CodeViewAdapter or custom one
            CustomCodeViewAdapter adapter = new CustomCodeViewAdapter(this, codeList);

            // Add the odeViewAdapter to the CodeView
            codeView.setAdapter(adapter);
        } else {
            String[] languageKeywords = languageManager.getLanguageKeywords(currentLanguage);

            // Custom list item xml layout
            final int layoutId = R.layout.list_item_suggestion;

            // TextView id to put suggestion on it
            final int viewId = R.id.suggestItemTextView;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, layoutId, viewId, languageKeywords);

            // Add the ArrayAdapter to the CodeView
            codeView.setAdapter(adapter);
        }
    }

    private void configLanguageAutoIndentation() {
        codeView.setIndentationStarts(languageManager.getLanguageIndentationStarts(currentLanguage));
        codeView.setIndentationEnds(languageManager.getLanguageIndentationEnds(currentLanguage));
    }

    private void configCodeViewPlugins() {
        commentManager = new CommentManager(codeView);
        configCommentInfo();

        undoRedoManager = new UndoRedoManager(codeView);
        undoRedoManager.connect();

        languageNameText = findViewById(R.id.language_name_txt);
        configLanguageName();

        sourcePositionText = findViewById(R.id.source_position_txt);
        sourcePositionText.setText(getString(R.string.source_position, 0, 0));
        configSourcePositionListener();
    }

    private void configCommentInfo() {
        commentManager.setCommentStart(languageManager.getCommentStart(currentLanguage));
        commentManager.setCommendEnd(languageManager.getCommentEnd(currentLanguage));
    }

    private void configLanguageName() {
        languageNameText.setText(currentLanguage.name().toLowerCase());
    }

    private void configSourcePositionListener() {
        SourcePositionListener sourcePositionListener = new SourcePositionListener(codeView);
        sourcePositionListener.setOnPositionChanged((line, column) -> {
            sourcePositionText.setText(getString(R.string.source_position, line, column));
            cursor_line = line;
            cursor_col = column;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int menuItemId = item.getItemId();
        final int menuGroupId = item.getGroupId();

        if (menuGroupId == R.id.group_languages) changeTheEditorLanguage(menuItemId);
        else if (menuGroupId == R.id.group_themes) changeTheEditorTheme(menuItemId);
        else if (menuItemId == R.id.findMenu) launchEditorButtonSheet();
        else if (menuItemId == R.id.comment) commentManager.commentSelected();
        else if (menuItemId == R.id.un_comment) commentManager.unCommentSelected();
        else if (menuItemId == R.id.clearText) codeView.setText("");
        else if (menuItemId == R.id.undo) undoRedoManager.undo();
        else if (menuItemId == R.id.redo) undoRedoManager.redo();

        return super.onOptionsItemSelected(item);
    }

    private void changeTheEditorLanguage(int languageId) {
        final LanguageName oldLanguage = currentLanguage;
        if (languageId == R.id.language_java) currentLanguage = LanguageName.JAVA;
        else if (languageId == R.id.language_python) currentLanguage = LanguageName.PYTHON;
        else if(languageId == R.id.language_go) currentLanguage = LanguageName.GO_LANG;

        if (currentLanguage != oldLanguage) {
            languageManager.applyTheme(currentLanguage, currentTheme);
            configLanguageName();
            configLanguageAutoComplete();
            configLanguageAutoIndentation();
            configCommentInfo();
        }
    }
    
    private void changeTheEditorTheme(int themeId) {
        final ThemeName oldTheme = currentTheme;
        if (themeId == R.id.theme_monokia) currentTheme = ThemeName.MONOKAI;
        else if (themeId == R.id.theme_noctics) currentTheme = ThemeName.NOCTIS_WHITE;
        else if(themeId == R.id.theme_five_color) currentTheme = ThemeName.FIVE_COLOR;
        else if(themeId == R.id.theme_orange_box) currentTheme = ThemeName.ORANGE_BOX;

        if (currentTheme != oldTheme) {
            languageManager.applyTheme(currentLanguage, currentTheme);
        }
    }

    private void launchEditorButtonSheet() {
        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.bottom_sheet_dialog);
        dialog.getWindow().setDimAmount(0f);

        final EditText searchEdit = dialog.findViewById(R.id.search_edit);
        final EditText replacementEdit = dialog.findViewById(R.id.replacement_edit);

        final ImageButton findPrevAction = dialog.findViewById(R.id.find_prev_action);
        final ImageButton findNextAction = dialog.findViewById(R.id.find_next_action);
        final ImageButton replacementAction = dialog.findViewById(R.id.replace_action);

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                String text = editable.toString().trim();
                if (text.isEmpty()) codeView.clearMatches();
                codeView.findMatches(Pattern.quote(text));
            }
        });

        findPrevAction.setOnClickListener(v -> {
            codeView.findPrevMatch();
        });

        findNextAction.setOnClickListener(v -> {
            codeView.findNextMatch();
        });

        replacementAction.setOnClickListener(v -> {
            String regex = searchEdit.getText().toString();
            String replacement = replacementEdit.getText().toString();
            codeView.replaceAllMatches(regex, replacement);
        });

        dialog.setOnDismissListener(c -> codeView.clearMatches());
        dialog.show();
    }

    private final class TextChangeWatcher implements TextWatcher {

        private Runnable runAutoComplete = () -> {
//            Log.d("CodeView", "Cursor pos: " + cursor_line + ", " + cursor_col);
            Log.d("CodeView", currentCode);
        };

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            currentCode = s.toString();
            if (autoCompleteFuture != null) {
                autoCompleteFuture.cancel(true);
            }
            autoCompleteFuture = autoCompleteService.schedule(runAutoComplete, AUTO_COMPLETE_BOUNCE_MS,
                    TimeUnit.MILLISECONDS);
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}