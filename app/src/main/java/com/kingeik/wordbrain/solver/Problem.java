package com.kingeik.wordbrain.solver;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

class Problem {

    private Context context;

    private LetterMatrix matrix;
    private Word[] words;

    private int minWordLength, maxWordLength;
    private List<Integer> lengths;
    private boolean isSolved;

    private List<String> problemWordlist;
    private List<String> wordWordlist;

    private List<Thread> solvers;

    public Problem(Context c, String problem, String lengths) {
        this.context = c;

        matrix = new LetterMatrix(context, problem);

        String[] wordsString = lengths.toLowerCase().split(",");
        words = new Word[wordsString.length];

        int wordNumber = 0;
        int chars = 0;
        minWordLength = matrix.getTotalChars();
        maxWordLength = 0;
        this.lengths = new ArrayList<>(words.length);
        isSolved = true;
        for (String word : wordsString) {
            words[wordNumber] = new Word(context, this, word);
            int l = words[wordNumber].length();
            if (!words[wordNumber].isSolved()) {
                isSolved = false;
                if (!this.lengths.contains(l))
                    this.lengths.add(l);
                if (l < minWordLength)
                    minWordLength = l;
                if (l > maxWordLength)
                    maxWordLength = l;
            }
            chars += l;
            wordNumber++;
        }

        if (isSolved) {
            minWordLength = 0;
            maxWordLength = 0;
        }

        if (chars != matrix.getTotalChars()) {
            throw new IllegalArgumentException(context.getString(R.string.ui_toast_unsolvable_chars_mismatch));
        }
    }

    public LetterMatrix getMatrix() {
        return matrix;
    }

    public int getWordCount() {
        return words.length;
    }

    public int getSolvedWordCount() {
        int lastDoneWord = -1;
        for (int i = words.length - 1; i >= 0; i--) {
            if (words[i].isSolved()) {
                lastDoneWord = i;
                break;
            }
        }
        return ++lastDoneWord;
    }

    public boolean isWordSolved(int index) {
        return index >= 0 && index < words.length && words[index].isSolved();
    }

    public List<Solution> getFinalResults() {
        return words[words.length - 1].getPossibleSolutions();
    }

    public List<Solution> getLatestResults() {
        int lastDoneWord = -1;
        for (int i = words.length - 1; i >= 0; i--) {
            if (words[i].isSolved()) {
                lastDoneWord = i;
                break;
            }
        }
        return getResultsForWord(lastDoneWord);
    }

    public List<Solution> getResultsForWord(int index) {
        if (index >= 0 && index < words.length) {
            return words[index].getPossibleSolutions();
        }
        return null;
    }

    private void refreshWordLengths() {
        isSolved = false;
        minWordLength = matrix.getTotalChars();
        maxWordLength = 0;
        lengths = new ArrayList<>(words.length);

        for (Word word : words) {
            if (word.isSolved()) continue;
            int l = word.length();
            if (!lengths.contains(l))
                lengths.add(l);
            if (l < minWordLength)
                minWordLength = l;
            if (l > maxWordLength)
                maxWordLength = l;
        }

        if (minWordLength > maxWordLength) {
            minWordLength = 0;
            maxWordLength = 0;
            isSolved = true;
        }
    }

    public int buildProblemWordlist() {
        refreshWordLengths();
        List<String> oldWordlist;
        if (problemWordlist == null) {
            oldWordlist = MainActivity.wordlist;
        } else {
            oldWordlist = problemWordlist;
        }

        problemWordlist = new ArrayList<>();
        if (isSolved) return 0;

        boolean filterChars = (MainActivity.validChars.length() != matrix.getUniqueCharacterCount());

        for (String w : oldWordlist) {
            int l = w.length();
            if (/*l >= minWordLength && l <= maxWordLength &&*/ lengths.contains(l)) {
                if (filterChars) {
                    boolean isValid = true;
                    for (Character c : w.toCharArray()) {
                        if (!matrix.contains(c)) {
                            isValid = false;
                            break;
                        }
                    }
                    if (!isValid) continue;
                }
                problemWordlist.add(w);
            }
        }
        return problemWordlist.size();
    }

    public int buildWordWordlist() {
        int lastDoneWord = -1;
        for (int i = words.length - 1; i >= 0; i--) {
            if (words[i].isSolved()) {
                lastDoneWord = i;
                break;
            }
        }
        lastDoneWord++;
        if (lastDoneWord == words.length) {
            return 0;
        }
        if (problemWordlist == null) {
            buildProblemWordlist();
        }

        wordWordlist = new ArrayList<>();
        boolean filterChars = (MainActivity.validChars.length() != matrix.getUniqueCharacterCount());
        int length = words[lastDoneWord].length();

        if (words[lastDoneWord].isFullyHinted()) {
            wordWordlist.add(words[lastDoneWord].getHintedStart());
        } else {
            for (String w : problemWordlist) {
                int l = w.length();
                if (length == l) {
                    if (filterChars) {
                        boolean isValid = true;
                        for (Character c : w.toCharArray()) {
                            if (!matrix.contains(c)) {
                                isValid = false;
                                break;
                            }
                        }
                        if (!isValid) continue;
                    }
                    wordWordlist.add(w);
                }
            }
        }
        return wordWordlist.size();
    }

    public boolean startSolvers() {
        if (solvers != null)
            return false;

        int lastDoneWord = -1;
        for (int i = words.length - 1; i >= 0; i--) {
            if (words[i].isSolved()) {
                lastDoneWord = i;
                break;
            }
        }

        if (lastDoneWord == words.length - 1) {
            return false;
        }

        int foundSolutionCount = (lastDoneWord == -1 ? 1 : words[lastDoneWord].foundSolutionCount());
        int height = matrix.getHeigth();
        //int width = matrix.getWidth();
        int total = matrix.getTotalChars();
        int numSolvers = total * foundSolutionCount;
        solvers = new ArrayList<>(numSolvers);

        Log.v("startSolvers", "Will start " + numSolvers + " threads...");
        int skipped = 0;

        for (int i = 0; i < numSolvers; i++) {

            int solutionId = i / total;
            final int startCol = (i % total) / height;
            final int startRow = (i % total) % height;

            LetterMatrix source;

            if (lastDoneWord == -1) {
                source = this.matrix.getCopy();
            } else {
                source = new LetterMatrix(words[lastDoneWord].getPossibleSolutions().get(solutionId));
                if (source.isBasedOnInvalid()) {
                    i += total - 1;
                    skipped++;
                    continue;
                }
            }

            final LetterMatrix workingMatrix = source;
            final Word word = words[lastDoneWord + 1];

            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    solverMain(word, workingMatrix, startRow, startCol);
                }
            });
            t.start();

            solvers.add(t);
        }

        if (skipped > 0) {
            Log.v("startSolvers", "Skipped creating " + (skipped * total) + " threads");
        }

        return true;
    }

    public void waitForSolvers() {
        for (Thread t : solvers) {
            boolean tryAgain = true;
            while (tryAgain)
                try {
                    tryAgain = false;
                    t.join();
                } catch (Exception e) {
                    Log.e("waitForSolvers", "Couldn't wait...?", e);
                    tryAgain = true;
                }
        }

        solvers = null;
    }

    private void solverMain(Word word, LetterMatrix letterMatrix, int startRow, int startCol) {

        // TODO: problem solving
        // + get copy of matrix to work with
        // + check if next tile is valid for searched word (we start at tile -1 so we validate the actual start)
        // + check next tile against wordlist (keep an internal wordlist so we can check faster)
        // - + yes: advance to next tile
        // - + no: try with next tile clockwise
        // - - + no possible tile found: go one level back and try next tile clockwise (this mechanism may be easiest with a recursive algorithm)
        // + if we hit the word length and have a valid word:
        // (- - apply gravity)
        // - + submit solution
        // - + go back to start (exclude already taken paths ^^)
        // + if no paths left: terminate

        letterMatrix.addSolutionLetter(startRow, startCol);
        recursionStep(word, letterMatrix); // work on a copy so we don't accidentally mess up other threads data
    }

    private void recursionStep(Word word, LetterMatrix matrix) {
        if (matrix.isBasedOnInvalid())
            return;

        String wordSoFar = matrix.getCurrentSolutionWord();

        //Log.v("recursionStep", wordSoFar);

        if (!word.match(wordSoFar))
            return;

        boolean gotWords = false;
        for (String w : wordWordlist) {
            if (w.startsWith(wordSoFar)) {
                gotWords = true;
                break;
            }
        }
        if (!gotWords) {
            return;
        }

        if (word.matchExact(wordSoFar)) {
            boolean exactMatch = false;
            for (String w : wordWordlist) {
                if (w.equals(wordSoFar)) {
                    exactMatch = true;
                    break;
                }
            }
            if (!exactMatch)
                return;

            word.addSolution(matrix.getCurrentSolution());
            return;
        }

        // find next possible tile

        int curRow = matrix.getCurrentSolutionEndRow();
        int curCol = matrix.getCurrentSolutionEndCol();

        for (int deltaRow = -1; deltaRow <= 1; deltaRow++) {
            for (int deltaCol = -1; deltaCol <= 1; deltaCol++) {
                if (deltaRow == 0 && deltaCol == 0)
                    continue;
                if (!matrix.canUseForSolution(curRow + deltaRow, curCol + deltaCol))
                    continue;
                char hint = word.getLetterHint(wordSoFar.length());
                if (hint == MainActivity.placeholder || hint == matrix.getLetterAt(curRow + deltaRow, curCol + deltaCol)) {
                    matrix.addSolutionLetter(curRow + deltaRow, curCol + deltaCol);
                    recursionStep(word, matrix);
                    matrix.removeLastSolutionLetter();
                }
                if (matrix.isBasedOnInvalid())
                    return;
            }
        }

    }
}
