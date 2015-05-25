/*
 * Generalized Language Modeling Toolkit (GLMTK)
 *
 * Copyright (C) 2015 Lukas Schmelzeisen
 *
 * GLMTK is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * GLMTK is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * GLMTK. If not, see <http://www.gnu.org/licenses/>.
 *
 * See the AUTHORS file for contributors.
 */

package de.glmtk.executables;

import static de.glmtk.common.Output.OUTPUT;
import static de.glmtk.util.LoggingHelper.LOGGING_HELPER;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.glmtk.Constants;
import de.glmtk.common.Output.Phase;
import de.glmtk.common.Output.Progress;
import de.glmtk.logging.Logger;
import de.glmtk.options.DoubleOption;
import de.glmtk.options.IntegerOption;
import de.glmtk.options.custom.CorpusOption;
import de.glmtk.util.NioUtils;
import de.glmtk.util.PrintUtils;
import de.glmtk.util.StringUtils;

public class GlmtkExpSetupExecutable extends Executable {
    private static final Logger LOGGER = Logger.get(GlmtkExpSetupExecutable.class);

    public static void main(String[] args) {
        new GlmtkExpSetupExecutable().run(args);
    }

    private CorpusOption optionCorpus;
    private DoubleOption optionTrainingProb;
    private IntegerOption optionNGramLength;
    private IntegerOption optionNumNGrams;

    private Path corpus = null;
    private Path workingDir = null;
    private Double trainingProb = null;
    private Integer ngramLength = null;
    private Integer numNGrams = null;

    @Override
    protected String getExecutableName() {
        return "glmtk-exp-setup";
    }

    @Override
    protected void registerOptions() {
        optionCorpus = new CorpusOption("c", "corpus",
                "Give corpus and maybe working directory.").suffix(".datasets");
        optionTrainingProb = new DoubleOption("p", "training-prob",
                "Probability with which lines go into training, "
                        + "other lines go into held-out. Default: 0.8.").defaultValue(
                0.8).requireProbability();
        optionNGramLength = new IntegerOption("n", "ngram-length",
                "Lenghts for which n-grams should be selected. "
                        + "If zero no n-grams will be selected. Default: 10.").defaultValue(
                                10).requirePositive().requireNotZero();
        optionNumNGrams = new IntegerOption("N", "num-ngrams",
                "Number of n-gram sequences to select. Default: 5000").defaultValue(
                        5000).requirePositive().requireNotZero();

        optionManager.register(optionCorpus, optionTrainingProb,
                optionNGramLength, optionNumNGrams);
    }

    @Override
    protected String getHelpHeader() {
        return "Splits the given corpus into training and test files.";
    }

    @Override
    protected String getHelpFooter() {
        return null;
    }

    @Override
    protected void parseOptions(String[] args) throws Exception {
        super.parseOptions(args);

        corpus = optionCorpus.getCorpus();
        workingDir = optionCorpus.getWorkingDir();

        trainingProb = optionTrainingProb.getDouble();
        ngramLength = optionNGramLength.getInt();
        numNGrams = optionNumNGrams.getInt();
    }

    @Override
    protected void configureLogging() {
        super.configureLogging();

        LOGGING_HELPER.addFileAppender(
                workingDir.resolve(Constants.LOCAL_LOG_FILE_NAME), "FileLocal",
                true);
    }

    @Override
    protected void exec() throws Exception {
        logFields();

        Path trainingFile = workingDir.resolve("training");
        Path heldoutFile = workingDir.resolve("heldout");

        Random rand = new Random();

        List<String> ngramCandidates = new ArrayList<>();

        long corpusFileSize = NioUtils.calcFileSize(corpus);

        String message = "Experimental setup";
        OUTPUT.beginPhases(message + "...");
        OUTPUT.setPhase(Phase.SPLITTING_CORPUS);
        Progress progress = OUTPUT.newProgress(NioUtils.calcNumberOfLines(corpus));
        try (BufferedReader reader = Files.newBufferedReader(corpus,
                Constants.CHARSET);
                BufferedWriter trainingWriter = Files.newBufferedWriter(
                        trainingFile, Constants.CHARSET);
                BufferedWriter heldoutWriter = Files.newBufferedWriter(
                        heldoutFile, Constants.CHARSET)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (rand.nextDouble() <= trainingProb)
                    trainingWriter.append(line).append('\n');
                else {
                    heldoutWriter.append(line).append('\n');
                    if (ngramLength != 0
                            && StringUtils.split(line, ' ').size() >= ngramLength)
                        ngramCandidates.add(line);
                }
                progress.increase(1);
            }
        }

        if (ngramLength != 0)
            selectNGrams(ngramCandidates);

        OUTPUT.endPhases(String.format("%s done (%s)", message,
                PrintUtils.humanReadableByteCount(corpusFileSize)));
    }

    private void selectNGrams(List<String> ngramCandidates) throws IOException {
        if (ngramCandidates.size() < numNGrams) {
            OUTPUT.printError(String.format(
                    "Not enough available NGram sequences that are longer than requested size. Size = %d, Have = %d, Need = %d",
                    ngramLength, ngramCandidates.size(), numNGrams));
            return;
        }

        OUTPUT.setPhase(Phase.SELECTING_NGRAMS);
        Progress progress = OUTPUT.newProgress(numNGrams);

        Random rand = new Random();

        List<BufferedWriter> ngramWriters = new ArrayList<>(ngramLength);
        for (int i = 1; i != ngramLength + 1; ++i) {
            Path ngramFile = workingDir.resolve("ngram" + i);
            ngramWriters.add(Files.newBufferedWriter(ngramFile,
                    Constants.CHARSET));
        }

        for (int i = 0; i != numNGrams; ++i) {
            int candidateIndex = rand.nextInt(ngramCandidates.size());
            String ngram = ngramCandidates.remove(candidateIndex);
            List<String> ngramWords = StringUtils.split(ngram, ' ');
            int firstWordIndex = rand.nextInt(ngramWords.size() - ngramLength
                    + 1);
            ngramWords = ngramWords.subList(firstWordIndex, firstWordIndex
                    + ngramLength);
            //            for (int j = ngramWords.size(); j != ngramLength; --j)
            //                ngramWords.remove(ngramWords.size() - 1);

            for (int j = ngramLength - 1; j != -1; --j) {
                ngramWriters.get(j).append(StringUtils.join(ngramWords, ' ')).append(
                        '\n');
                ngramWords.remove(ngramWords.size() - 1);
            }

            progress.increase(1);
        }

        for (BufferedWriter writer : ngramWriters)
            writer.close();
    }

    private void logFields() {
        LOGGER.debug("%s %s", getExecutableName(), StringUtils.repeat("-",
                80 - getExecutableName().length()));
        LOGGER.debug("Corpus:            %s", corpus);
        LOGGER.debug("WorkingDir:        %s", workingDir);
        LOGGER.debug("ProbTraining:      %f", trainingProb);
        LOGGER.debug("MaxNGramLength:    %d", ngramLength);
        LOGGER.debug("NumNGramSequences: %d", numNGrams);
    }
}
