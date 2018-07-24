package com.kingeik.wordbrain.solver;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class Word {

    private Lock lock = new ReentrantLock();

    private Context context;
    private Problem parent;
    private String rawWord;

    private int length = 0;
    private boolean hasHints;
    private boolean isSolved;

    private List<Solution> possibleSolutions;

    public Word(Context c, Problem p, String word) {
        this.context = c;
        this.parent = p;
        this.rawWord = word.toLowerCase();

        try {
            length = Integer.parseInt(word);
            hasHints = false;
            isSolved = false;
        } catch (Exception e) {
            length = word.length();
            hasHints = true;

            char[] letters = word.toCharArray();
            for (char letter : letters) {
                if (!parent.getMatrix().contains(letter) && letter != MainActivity.placeholder) {
                    throw new IllegalArgumentException(context.getString(R.string.ui_toast_unsolvable_chars_mismatch));
                }
            }

            isSolved = !word.contains("" + MainActivity.placeholder);
        }

        if (length <= 0) {
            throw new IllegalArgumentException(context.getString(R.string.ui_toast_unsolvable_invalid_length));
        }

        possibleSolutions = new ArrayList<>();
    }

    public int length() {
        return length;
    }

    public boolean isSolved() {
        return possibleSolutions.size() > 0;
    }

    public int foundSolutionCount() {
        return possibleSolutions.size();
    }

    public List<Solution> getPossibleSolutions() {
        return possibleSolutions;
    }

    public boolean match(String testWord) {
        if (testWord.length() > length)
            return false;

        if (hasHints) {
            char[] testChars = testWord.toLowerCase().toCharArray();
            char[] wordChars = rawWord.toCharArray();
            for (int i = 0; i < testChars.length && i < wordChars.length; i++) {
                if (wordChars[i] != MainActivity.placeholder && wordChars[i] != testChars[i])
                    return false;
            }
        }

        return true;
    }

    public boolean matchExact(String testWord) {
        return testWord.length() == length && match(testWord);
    }

    public char getLetterHint(int pos) {
        if (pos < 0 || pos >= length)
            return ' ';

        if (!hasHints)
            return MainActivity.placeholder;

        return rawWord.charAt(pos);
    }

    public boolean isFullyHinted() {
        return isSolved;
    }

    public boolean isHinted() {
        return hasHints;
    }

    public String getHintedStart() {
        if (!hasHints)
            return "";

        if (isSolved)
            return rawWord;

        int firstPlaceholder = rawWord.indexOf(MainActivity.placeholder);

        return rawWord.substring(0, firstPlaceholder);
    }

    public void addSolution(Solution solution) {
        this.lock.lock();
        try {
            this.possibleSolutions.add(solution);
        } finally {
            lock.unlock();
        }
    }

}
