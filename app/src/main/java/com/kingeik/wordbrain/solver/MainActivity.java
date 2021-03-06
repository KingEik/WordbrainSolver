package com.kingeik.wordbrain.solver;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity {

    private static int ACTIVITY_CHOOSE_FILE = 1;

    public static final String validChars = "abcdefghijklmnopqrstuvwxyzäöüß";
    public static final char placeholder = '_';

    LinearLayout baseLayout;
    public static List<String> wordlist;

    Thread wordlistLoader = null, problemSolver = null;
    boolean continueSolving, autoAdvance;
    Problem lastProblem;
    List<Solution> latestSolutions;
    Solution shownSolution;
    int currentSolutionIndex, currentWordIndex, solvedWordCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        baseLayout = findViewById(R.id.baseLayout);

        renderUI(false);
    }

    private void renderUIOnUIThread(final boolean lockInputs) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                renderUI(lockInputs);
            }
        });
    }

    private void renderUI(final boolean lockInputs) {

        File wordlist = new File(getFilesDir(), "words.bin");
        baseLayout.removeAllViews();

        if (!wordlist.exists()) {
            // show the user a way to download wordlist
            TextView tv = new TextView(this);
            tv.setText(R.string.ui_need_wordlist);
            baseLayout.addView(tv);

            tv = new TextView(this);
            tv.setText(R.string.ui_dictcc_explanation);
            baseLayout.addView(tv);

            Button btn = new Button(this);
            btn.setText(R.string.ui_download);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    btnDownloadClick();
                }
            });
            baseLayout.addView(btn);

            tv = new TextView(this);
            tv.setText(R.string.ui_after_download);
            baseLayout.addView(tv);

            btn = new Button(this);
            btn.setText(R.string.ui_add_wordlist);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    btnAddClick();
                }
            });
            baseLayout.addView(btn);
        } else if (latestSolutions != null) {
            // we got some results, show them!

            List<Solution> allSolutions = latestSolutions;

            // local variables to prevent interference from other threads
            final int solvedWordCount = this.solvedWordCount;
            final List<Solution> shownSolutions = new ArrayList<>();
            final long startTime = Calendar.getInstance().getTimeInMillis();

            for (Solution s : allSolutions) {
                if (!s.isInvalid()) {
                    shownSolutions.add(s);
                }
            }

            if (autoAdvance && currentWordIndex < solvedWordCount - 1) {
                currentWordIndex++;
            }

            autoAdvance = (currentWordIndex >= solvedWordCount);

            currentSolutionIndex = Math.min(shownSolutions.size() - 1, currentSolutionIndex);
            currentWordIndex = Math.min(solvedWordCount - 1, currentWordIndex);

            TextView tv = new TextView(this);
            tv.setText(String.format(getString(R.string.ui_got_results), shownSolutions.size(), solvedWordCount));
            baseLayout.addView(tv);

            Button btn;
            if (shownSolutions.size() > 0) {
                LinearLayout ll = new LinearLayout(this);
                ll.setOrientation(LinearLayout.HORIZONTAL);

                btn = new Button(this);
                btn.setText("<<");
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (currentSolutionIndex > 0) {
                            currentSolutionIndex--;
                            renderUIOnUIThread(false);
                        }
                    }
                });
                ll.addView(btn);

                btn = new Button(this);
                btn.setText("<");
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (currentWordIndex > 0) {
                            currentWordIndex--;
                            renderUIOnUIThread(false);
                        }
                    }
                });
                ll.addView(btn);

                btn = new Button(this);
                btn.setText(">");
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (currentWordIndex < solvedWordCount - 1) {
                            currentWordIndex++;
                            renderUIOnUIThread(false);
                        }
                    }
                });
                ll.addView(btn);

                btn = new Button(this);
                btn.setText(">>");
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (currentSolutionIndex < shownSolutions.size() - 1) {
                            currentSolutionIndex++;
                            renderUIOnUIThread(false);
                        }
                    }
                });
                ll.addView(btn);

                baseLayout.addView(ll);

                tv = new TextView(this);
                tv.setText(getString(R.string.ui_results_solution) + ": " + (currentSolutionIndex + 1) + ", " + getString(R.string.ui_results_word) + ": " + (currentWordIndex + 1));
                baseLayout.addView(tv);

                Solution currentSolution = shownSolutions.get(currentSolutionIndex);
                for (int i = 1; i < solvedWordCount - currentWordIndex; i++) {
                    currentSolution = currentSolution.previousSolution;
                }

                shownSolution = currentSolution;

                int height = currentSolution.lettersBeforeSolution.length;
                int width = currentSolution.lettersBeforeSolution[0].length;

                for (int row = 0; row < height; row++) {
                    ll = new LinearLayout(this);
                    ll.setOrientation(LinearLayout.HORIZONTAL);
                    for (int col = 0; col < width; col++) {
                        tv = new TextView(this);
                        int solutionPath = currentSolution.solutionPath[row][col];
                        tv.setText(currentSolution.lettersBeforeSolution[row][col] + "\n" + (solutionPath != -1 ? (solutionPath + 1) : ""));
                        tv.setTypeface(Typeface.MONOSPACE);
                        ll.addView(tv);
                    }
                    baseLayout.addView(ll);
                }

                tv = new TextView(this);
                tv.setText(currentSolution.foundWord);
                baseLayout.addView(tv);

                ll = new LinearLayout(this);
                ll.setOrientation(LinearLayout.HORIZONTAL);

                btn = new Button(this);
                btn.setText(R.string.ui_results_correct);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // add correct logic

                        // stop user from hitting button right after UI update
                        long now = Calendar.getInstance().getTimeInMillis();
                        if (lockInputs && (now - startTime) < 300)
                            return;

                        for (Solution s : shownSolutions) {
                            for (int i = 1; i < solvedWordCount - currentWordIndex; i++) {
                                s = s.previousSolution;
                            }
                            if (!shownSolution.foundWord.equals(s.foundWord))
                                s.setSolutionInvalid();
                        }

                        lastProblem.setHintForWord(currentWordIndex, shownSolution.foundWord);

                        SharedPreferences sp = MainActivity.this.getApplicationContext().getSharedPreferences("lastProblem", MODE_PRIVATE);
                        SharedPreferences.Editor edit = sp.edit();
                        edit.putString("lengths", lastProblem.getLengthString());
                        edit.apply();

                        currentWordIndex++;

                        if (currentWordIndex >= lastProblem.getWordCount()) {
                            // exit on last word correct
                            latestSolutions = null;
                        }

                        renderUIOnUIThread(false);
                    }
                });
                ll.addView(btn);

                btn = new Button(this);
                btn.setText(R.string.ui_results_wrong);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // add wrong logic

                        // stop user from hitting button right after UI update
                        long now = Calendar.getInstance().getTimeInMillis();
                        if (lockInputs && (now - startTime) < 300)
                            return;

                        for (Solution s : shownSolutions) {
                            for (int i = 1; i < solvedWordCount - currentWordIndex; i++) {
                                s = s.previousSolution;
                            }
                            if (shownSolution.foundWord.equals(s.foundWord))
                                s.setSolutionInvalid();
                        }
                        renderUIOnUIThread(false);
                    }
                });
                ll.addView(btn);

                baseLayout.addView(ll);
            }

            btn = new Button(this);
            btn.setText(R.string.ui_results_leave);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // stop all solving (if any)
                    continueSolving = false;
                    for (Solution s : shownSolutions) {
                        s.setSolutionInvalid();
                    }

                    lastProblem = null;
                    latestSolutions = null;
                    currentSolutionIndex = 0;
                    currentWordIndex = 0;
                    renderUIOnUIThread(false);
                }
            });
            baseLayout.addView(btn);

        } else {
            // we got a wordlist
            // read it, user inputs problem to solve

            if (MainActivity.wordlist == null && this.wordlistLoader == null) {
                this.wordlistLoader = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        loadWordlist();
                    }
                });
                this.wordlistLoader.start();
            }

            TextView tv = new TextView(this);
            tv.setText(R.string.ui_got_wordlist);
            baseLayout.addView(tv);

            SharedPreferences sp = MainActivity.this.getApplicationContext().getSharedPreferences("lastProblem", MODE_PRIVATE);
            String lastProblem = sp.getString("problem", "");
            String lastLengths = sp.getString("lengths", "");

            final EditText problemEdit = new EditText(this);
            problemEdit.setInputType(EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            problemEdit.setSingleLine(false);
            problemEdit.setHint(R.string.ui_hint_problem);
            problemEdit.setTypeface(Typeface.MONOSPACE);
            problemEdit.setText(lastProblem);
            baseLayout.addView(problemEdit);

            final EditText lengthsEdit = new EditText(this);
            lengthsEdit.setInputType(EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            lengthsEdit.setSingleLine(true);
            lengthsEdit.setHint(R.string.ui_hint_lengths);
            lengthsEdit.setText(lastLengths);
            baseLayout.addView(lengthsEdit);

            Button btn = new Button(this);
            btn.setText(R.string.ui_ok);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (MainActivity.wordlist == null || MainActivity.this.wordlistLoader != null) {
                        showToast(R.string.ui_wait_for_wordlist, false);
                        return;
                    }

                    if (MainActivity.this.problemSolver != null) {
                        showToast(R.string.ui_already_running, false);
                        return;
                    }

                    continueSolving = true;
                    final String problem = problemEdit.getText().toString();
                    final String lengths = lengthsEdit.getText().toString();

                    SharedPreferences sp = MainActivity.this.getApplicationContext().getSharedPreferences("lastProblem", MODE_PRIVATE);
                    SharedPreferences.Editor edit = sp.edit();
                    edit.putString("problem", problem);
                    edit.putString("lengths", lengths);
                    edit.apply();

                    MainActivity.this.problemSolver = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            solveProblem(problem, lengths);
                        }
                    });
                    MainActivity.this.problemSolver.start();
                }
            });
            baseLayout.addView(btn);
        }

    }

    private void btnDownloadClick() {
        String url = getString(R.string.url_dictcc_download);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        startActivity(i);
    }

    private void btnAddClick() {
        /*if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED && Build.VERSION.SDK_INT >= 23) {
            Toast.makeText(this, R.string.ui_toast_permission, Toast.LENGTH_LONG).show();
            requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE }, 0);
            return;
        }*/

        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("text/plain");
        Intent intent = Intent.createChooser(chooseFile, getString(R.string.ui_add_wordlist));
        startActivityForResult(intent, ACTIVITY_CHOOSE_FILE);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v("onActivityResult", "gotResult");
        if (resultCode != RESULT_OK)
            return;
        Log.v("onActivityResult", "result ok");
        if (requestCode == ACTIVITY_CHOOSE_FILE)
        {
            Log.v("onActivityResult", "was choose file");
            final Uri uri = data.getData();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    buildWordlist(uri);
                }
            }).start();
        }
    }

    private void loadWordlist() {
        wordlist = new ArrayList<>();
        try {
            Scanner sc = new Scanner(new File(getFilesDir(), "words.bin"));
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                wordlist.add(line.trim());
            }
            if (sc.ioException() != null)
                throw sc.ioException();
        } catch (Exception e) {
            Log.e("loadWordlist", "Something went wrong while reading!", e);
            showToast(R.string.ui_toast_error_read, true);
            wordlist = null;
            this.wordlistLoader = null;
            return;
        }
        showToast(String.format(getString(R.string.ui_lines_processed), wordlist.size()), false);
        this.wordlistLoader = null;
    }

    private void buildWordlist(@NonNull Uri sourceFile) {

        Log.v("buildWordlist", "starting, file is " + sourceFile.toString());

        List<String> words = new ArrayList<>(1000000);

        int readLines = 0;
        try {
            Scanner sc = new Scanner(getContentResolver().openInputStream(sourceFile));
            String lastAdded = "";
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (readLines++ % 10000 == 0) {
                    final int _read = readLines - 1;
                    showToast(String.format(getString(R.string.ui_lines_processed), _read), false);
                    Log.v("buildWordlist", "read " + readLines + " lines");
                }

                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }

                line = line.split("\t")[0].toLowerCase();

                String word = line.split(" ")[0];

                boolean valid = true;
                for (char c : word.toCharArray()) {
                    if (!validChars.contains(new String(new char[] { c }))) {
                        valid = false;
                        break;
                    }
                }
                if (valid && !word.equals(lastAdded)) { // we assume a sorted dictionary to avoid expensive .contains on a big list
                    words.add(word);
                    lastAdded = word;
                }
            }
            if (sc.ioException() != null)
                throw sc.ioException();
        } catch (Exception e) {
            Log.e("buildWordlist", "Something went wrong while reading!", e);
            showToast(R.string.ui_toast_error_read, true);
            return;
        }


        /*String file;

        try {
            InputStream in = getContentResolver().openInputStream(sourceFile);

            int byteLength;
            byte[] buffer = new byte[1024 * 128];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while ((byteLength = in.read(buffer)) != -1) {
                byte[] copy = Arrays.copyOf(buffer, byteLength);
                out.write(copy, 0, copy.length);
            }
            file = out.toString();

            out.close();
            in.close();
        } catch (Exception e) {
            Log.e("buildWordlist", "Something went wrong while reading!", e);
            Toast.makeText(this, R.string.ui_toast_error_read, Toast.LENGTH_LONG).show();
            return;
        }

        Log.v("buildWordlist", "file read");
        int readLines = 0;

        while (file.contains("\n")) {

            String[] split = file.split("\n", 2);
            String line = split[0];
            file = split[1];

            if (readLines++ % 10 == 0)
                Log.v("buildWordlist", "read " + readLines + " lines");

            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }

            line = line.split("\t")[0].toLowerCase();

            String word = line.split(" ")[0];

            boolean valid = true;
            for (char c : word.toCharArray()) {
                if (!validChars.contains(new String(new char[] { c }))) {
                    valid = false;
                    break;
                }
            }
            if (valid && !words.contains(word))
                words.add(word);
        }*/

        Log.v("buildWordlist", "Got " + words.size() + " distinct words");

        File wordlist = new File(getFilesDir(), "words.bin");

        try {
            FileOutputStream out = new FileOutputStream(wordlist, false);
            out.write(TextUtils.join("\n", words).getBytes());
            out.close();
        } catch (Exception e) {
            Log.e("buildWordlist", "Something went wrong while writing!", e);
            showToast(R.string.ui_toast_error_write, true);
            return;
        }

        Log.v("buildWordlist", "wordlist written!");

        MainActivity.wordlist = words;

        showToast(R.string.ui_toast_success, true);
        renderUIOnUIThread(true);
    }

    private void solveProblem(String problemString, String lenghts) {
        Log.v("solveProblem", problemString + "\n" + lenghts);

        if (wordlist == null || this.wordlistLoader != null) {
            Log.w("solveProblem", "No complete wordlist given!");
            this.problemSolver = null;
            return;
        }

        Problem problem;
        try {
            problem = new Problem(this, problemString, lenghts);
        } catch (Exception e) {
            showToast(e.getMessage(), false);
            this.problemSolver = null;
            return;
        }
        Log.v("solveProblem", "H:" + problem.getMatrix().getHeigth() + ";W:" + problem.getMatrix().getWidth());

        lastProblem = problem;
        currentSolutionIndex = 0;
        currentWordIndex = 0;
        showToast(R.string.ui_toast_start_solving, false);

        // TODO: actual solving process
        // + filter wordlist to contain only potential useful words
        // + start a thread for every starting letter
        // - + walk over the problem tree narrowing down possibilities from the wordlist
        // - + check wordlengths to break early
        // - + wait for threads to finish
        // - + apply "gravity" for fields with changes
        // + repeat with the smaller fields which resulted from found words (all at the same time)
        // - display results

        int length = problem.buildProblemWordlist(); // this call is for debug purposes, .buildWordWordlist() would also call this (once)
        Log.v("solveProblem", "Problem wordlist has " + length + " / " + wordlist.size() + " words");

        for (int i = 0; i < problem.getWordCount() && continueSolving; i++) {
            length = problem.buildWordWordlist();
            Log.v("solveProblem", "Word wordlist has " + length + " words");

            boolean started = problem.startSolvers();

            if (!started) {
                Log.e("solveProblem", "Could not start solvers!");
                break;
            }

            problem.waitForSolvers();

            if (continueSolving)
                if (!problem.isWordSolved(i)) {
                    showToast(String.format(getString(R.string.ui_toast_error_word_not_found), i+1), true);
                    Log.w("solveProblem", "Could not solve word " + (i+1));
                    break;
                } else {
                    // show (only) intermediate results to the user
                    solvedWordCount = i + 1;
                    if (i < problem.getWordCount() - 1) {
                        latestSolutions = problem.getLatestResults();
                        Log.v("solveProblem", "Got " + latestSolutions.size() + " intermediate results (word " + (i+1) + ")!");
                        renderUIOnUIThread(true);
                    }
                }
        }

        // END OF SOLVING

        if (continueSolving) {
            latestSolutions = problem.getFinalResults();
            Log.v("solveProblem", "Got " + latestSolutions.size() + " results!");

            // show results
            renderUIOnUIThread(true);
        }

        this.problemSolver = null;
    }

    private void showToast(final int resId, final boolean durationLong) {
        showToast(getString(resId), durationLong);
    }

    private void showToast(final String text, final boolean durationLong) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, (durationLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT)).show();
            }
        });
    }
}
