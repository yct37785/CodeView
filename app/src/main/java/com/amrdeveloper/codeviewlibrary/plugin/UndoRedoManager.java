package com.amrdeveloper.codeviewlibrary.plugin;

import android.text.Editable;
import android.text.Selection;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.widget.TextView;

import java.util.LinkedList;

// Module taken from: https://stackoverflow.com/questions/14777593/android-textwatcher-saving-batches-of-similar-changes-for-undo-redo
public class UndoRedoManager {

    private final TextView textView;
    private final EditHistory editHistory;
    private final TextChangeWatcher textChangeWatcher;

    private boolean isUndoOrRedo = false;

    public UndoRedoManager(TextView textView) {
        this.textView = textView;
        editHistory = new EditHistory();
        textChangeWatcher = new TextChangeWatcher();
    }

    public void undo() {
        textChangeWatcher.pushToEditHistory();
        EditNode edit = editHistory.getPrevious();
        if (edit == null) return;

        Editable text = textView.getEditableText();
//        int start = edit.start;
//        int end = start + (edit.after != null ? edit.after.length() : 0);

        isUndoOrRedo = true;
        text.replace(0, text.length(), edit.before);
        isUndoOrRedo = false;

        UnderlineSpan[] underlineSpans = text.getSpans(0, text.length(), UnderlineSpan.class);
        for (Object span : underlineSpans) text.removeSpan(span);

//        Selection.setSelection(text, edit.before == null ? start : (start + edit.before.length()));
    }

    public void redo() {
        EditNode edit = editHistory.getNext();
        if (edit == null) return;

        Editable text = textView.getEditableText();
//        int start = edit.start;
//        int end = start + (edit.before != null ? edit.before.length() : 0);

        isUndoOrRedo = true;
        text.replace(0, text.length(), edit.after);
        isUndoOrRedo = false;

        UnderlineSpan[] underlineSpans = text.getSpans(0, text.length(), UnderlineSpan.class);
        for (Object span : underlineSpans) text.removeSpan(span);

//        Selection.setSelection(text, edit.after == null ? start : (start + edit.after.length()));
    }

    public void connect() {
        textView.addTextChangedListener(textChangeWatcher);
    }

    public void disconnect() {
        textView.removeTextChangedListener(textChangeWatcher);
    }

    public void setMaxHistorySize(int maxSize) {
        editHistory.setMaxHistorySize(maxSize);
    }

    public void clearHistory() {
        editHistory.clear();
    }

    public boolean canUndo() {
        return editHistory.position > 0;
    }

    public boolean canRedo() {
        return editHistory.position < editHistory.historyList.size();
    }

    private static final class EditHistory {

        private int position = 0;
        private int maxHistorySize = -1;

        private final LinkedList<EditNode> historyList = new LinkedList<>();

        private void clear() {
            position = 0;
            historyList.clear();
        }

        private void add(EditNode item) {
            while (historyList.size() > position) historyList.removeLast();
            historyList.add(item);
            position++;
            if (maxHistorySize >= 0) trimHistory();
        }

        private void setMaxHistorySize(int maxHistorySize) {
            this.maxHistorySize = maxHistorySize;
            if (this.maxHistorySize >= 0) trimHistory();
        }

        private void trimHistory() {
            while (historyList.size() > maxHistorySize) {
                historyList.removeFirst();
                position--;
            }

            if (position < 0) position = 0;
        }

        private EditNode getCurrent() {
            if (position == 0) return null;
            return historyList.get(position - 1);
        }

        private EditNode getPrevious() {
            if (position == 0) return null;
            position--;
            return historyList.get(position);
        }

        private EditNode getNext() {
            if (position >= historyList.size()) return null;
            EditNode item = historyList.get(position);
            position++;
            return item;
        }
    }

    private static final class EditNode {

        private int start;
        private CharSequence before;
        private CharSequence after;

        public EditNode(int start, CharSequence before, CharSequence after) {
            this.start = start;
            this.before = before;
            this.after = after;
        }

        public void setAfter(CharSequence after) {
            this.after = after;
        }

        public void printDetails() {
            Log.d("CodeView", "Node: '" + before + "' -> '" + after + "'");
        }
    }

    private enum ActionType {
        INSERT, DELETE, PASTE, NOT_DEF;
    }

    /*
    * TextWatcher defines change not by current key press but rather a sequence. We will utilize
    * TextWatcher's logic and batch sequence into per edit action.
     */
    private final class TextChangeWatcher implements TextWatcher {

        EditNode toSaveNode = null;
        private CharSequence full_beforeChange;
        private CharSequence full_afterChange;
        private CharSequence beforeChange;
        private boolean isFirstAction = true;

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            if (isUndoOrRedo) return;
            full_beforeChange = s.subSequence(0, s.length());
            beforeChange = s.subSequence(start, start + count);
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (isUndoOrRedo) return;
            full_afterChange = s.subSequence(0, s.length());
            CharSequence afterChange = s.subSequence(start, start + count);
//            Log.d("CodeView", "'" + beforeChange + "' -> '" + afterChange + "'");
            boolean isNewSequence = false;
            // on keypress
            if (Math.abs(afterChange.length() - beforeChange.length()) == 1) {
                isNewSequence = !isFirstAction && beforeChange.length() == 0;
            } else {    // batch change
                isNewSequence = true;
            }
            isFirstAction = false;
            saveBatch(start, isNewSequence);
        }

        // save current batch to to-be-saved EditNode
        private void saveBatch(int start, boolean saveCurrentBatch) {
            if (saveCurrentBatch) {
                pushToEditHistory();
            }
            if (toSaveNode == null) {
                toSaveNode = new EditNode(start, full_beforeChange, full_afterChange);
            }
            // always save the latest afterChange
            toSaveNode.setAfter(full_afterChange);
        }

        public void pushToEditHistory() {
            if (toSaveNode != null) {
                editHistory.add(toSaveNode);
                // set for new edit action
                toSaveNode = null;
            }
        }

        public void afterTextChanged(Editable s) {

        }
    }
}
