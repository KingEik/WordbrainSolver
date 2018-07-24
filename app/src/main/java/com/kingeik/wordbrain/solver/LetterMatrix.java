package com.kingeik.wordbrain.solver;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

class LetterMatrix {

    private char[][] originalMatrix;
    private List<Solution> foundSolutions;

    private List<Character> characters;
    private char[][] letters;
    private int width, heigth;
    private int[][] inSolutionIndex;
    private int currentSolutionIndex;

    public LetterMatrix(Context context, String problem) {
        String[] lines = problem.toLowerCase().split("\n");

        width = lines[0].length();
        heigth = lines.length;

        letters = new char[heigth][width];
        resetSolution();

        int lineNumber = 0;
        for (String line : lines) {
            if (line.length() != width) {
                throw new IllegalArgumentException(context.getString(R.string.ui_toast_inconsistent_line_length));
            }
            letters[lineNumber] = line.toCharArray();
            lineNumber++;
        }

        characters = new ArrayList<>();
        for (char letter : problem.replaceAll("\n", "").toCharArray()) {
            if (!characters.contains(letter)) {
                characters.add(letter);
                if (!MainActivity.validChars.contains("" + letter)) {
                    throw new IllegalArgumentException(context.getString(R.string.ui_toast_unsolvabe_invalid_chars));
                }
            }
        }

        this.originalMatrix = new char[heigth][width];
        int row = 0;
        for (char[] line : this.letters) {
            int col = 0;
            for (char c : line) {
                this.originalMatrix[row][col] = c;
                col++;
            }
            row++;
        }

        this.foundSolutions = new ArrayList<>();
    }

    // copy constructor
    private LetterMatrix(char[][] letters, char[][] originalMatrix, List<Solution> foundSolutions) {
        width = letters[0].length;
        heigth = letters.length;
        this.letters = new char[heigth][width];
        resetSolution();
        characters = new ArrayList<>();
        int row = 0;
        for (char[] line : letters) {
            int col = 0;
            for (char c : line) {
                this.letters[row][col] = c;
                if (!characters.contains(c) && MainActivity.validChars.contains("" + c)) {
                    characters.add(c);
                }
                col++;
            }
            row++;
        }

        this.originalMatrix = originalMatrix; // should work cause it's never going to be modified (shallow copy)

        this.foundSolutions = new ArrayList<>(foundSolutions);
    }

    public LetterMatrix(Solution solution) {
        width = solution.lettersBeforeSolution[0].length;
        heigth = solution.lettersBeforeSolution.length;
        this.letters = new char[heigth][width];
        resetSolution();
        characters = new ArrayList<>();
        int row = 0;
        for (char[] line : solution.lettersBeforeSolution) {
            int col = 0;
            for (char c : line) {
                this.letters[row][col] = c;
                if (!characters.contains(c) && MainActivity.validChars.contains("" + c)) {
                    characters.add(c);
                }
                col++;
            }
            row++;
        }

        this.originalMatrix = solution.lettersBeforeSolution; // should work cause it's never going to be modified (shallow copy)

        this.foundSolutions = new ArrayList<>();

        this.gravity(solution);
    }

    public void resetSolution() {
        currentSolutionIndex = 0;
        inSolutionIndex = new int[heigth][width];

        for (int i = 0; i < heigth; i++) {
            for (int j = 0; j < width; j++) {
                inSolutionIndex[i][j] = -1;
            }
        }
    }

    public boolean canUseForSolution(int row, int col) {
        return row >= 0 && row < heigth && col >= 0 && col < width && inSolutionIndex[row][col] == -1;
    }

    public boolean addSolutionLetter(int row, int col) {
        if (canUseForSolution(row, col)) {
            inSolutionIndex[row][col] = currentSolutionIndex;
            currentSolutionIndex++;
            return true;
        }
        return false;
    }

    public int removeLastSolutionLetter() {
        if (currentSolutionIndex == 0)
            return -1;
        for (int i = 0; i < heigth; i++) {
            for (int j = 0; j < width; j++) {
                if (inSolutionIndex[i][j] == currentSolutionIndex - 1) {
                    inSolutionIndex[i][j] = -1;
                    currentSolutionIndex--;
                    return currentSolutionIndex;
                }
            }
        }
        return -1;
    }

    public int getCurrentSolutionEndRow() {
        if (currentSolutionIndex == 0)
            return -1;
        for (int i = 0; i < heigth; i++) {
            for (int j = 0; j < width; j++) {
                if (inSolutionIndex[i][j] == currentSolutionIndex - 1) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int getCurrentSolutionEndCol() {
        if (currentSolutionIndex == 0)
            return -1;
        for (int i = 0; i < heigth; i++) {
            for (int j = 0; j < width; j++) {
                if (inSolutionIndex[i][j] == currentSolutionIndex - 1) {
                    return j;
                }
            }
        }
        return -1;
    }

    public char getLetterAt(int row, int col) {
        if (row >= 0 && row < heigth && col >= 0 && col < width)
            return letters[row][col];
        return ' ';
    }

    public Solution getCurrentSolution() {
        Solution previous = null;
        if (foundSolutions.size() > 0) {
            previous = foundSolutions.get(foundSolutions.size() - 1);
        }
        return new Solution(this.getCurrentSolutionWord(), inSolutionIndex, letters, previous);
    }

    public String getCurrentSolutionWord() {
        char[] str = new char[currentSolutionIndex];

        for (int i = 0; i < heigth; i++) {
            for (int j = 0; j < width; j++) {
                if (inSolutionIndex[i][j] != -1) {
                    str[inSolutionIndex[i][j]] = letters[i][j];
                }
            }
        }

        return new String(str);
    }

    public void gravity() {
        this.gravity(this.getCurrentSolution());
    }

    public void gravity(Solution solution) {
        this.foundSolutions.add(solution);

        for (int col = 0; col < this.width; col++) {
            for (int row = 0; row < this.heigth; row++) {
                if (solution.solutionPath[row][col] != -1) {
                    this.letters[row][col] = ' ';
                }
            }
        }

        for (int col = 0; col < this.width; col++) {
            for (int row = this.heigth - 2; row >= 0; row--) {
                if (this.letters[row][col] == ' ')
                    continue;
                int i = 0;
                while (row + i + 1 < heigth && this.letters[row + i + 1][col] == ' ') {
                    i++;
                }
                if (i != 0) {
                    this.letters[row + i][col] = this.letters[row][col];
                    this.letters[row][col] = ' ';
                }
            }
        }

        resetSolution();
    }

    public LetterMatrix getCopy() {
        return new LetterMatrix(letters, originalMatrix, foundSolutions);
    }

    public int getWidth() {
        return width;
    }

    public int getHeigth() {
        return heigth;
    }

    public int getTotalChars() {
        return width * heigth;
    }

    public boolean contains(Character c) {
        return characters.contains(c);
    }

    public int getUniqueCharacterCount() {
        return characters.size();
    }

}
