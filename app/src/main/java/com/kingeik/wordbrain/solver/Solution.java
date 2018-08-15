package com.kingeik.wordbrain.solver;

class Solution {

    Solution previousSolution;
    String foundWord;
    int[][] solutionPath;
    char[][] lettersBeforeSolution;

    private boolean invalid = false;

    public Solution(String foundWord, int[][] solutionPath, char[][] lettersBeforeSolution, Solution previousSolution) {
        this.previousSolution = previousSolution;
        this.foundWord = foundWord;

        int height = solutionPath.length;
        int width = solutionPath[0].length;

        this.solutionPath = new int[height][width];
        this.lettersBeforeSolution = new char[height][width];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                this.solutionPath[i][j] = solutionPath[i][j];
                this.lettersBeforeSolution[i][j] = lettersBeforeSolution[i][j];
            }
        }

    }

    public void setSolutionInvalid() {
        this.invalid = true;
    }

    public boolean isInvalid() {
        return invalid || (previousSolution != null && previousSolution.isInvalid());
    }
}
