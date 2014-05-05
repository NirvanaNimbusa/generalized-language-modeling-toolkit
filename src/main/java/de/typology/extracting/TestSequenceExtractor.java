package de.typology.extracting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.typology.patterns.Pattern;

/**
 * This class extracts all sequences that are needed for computing the
 * Kneser-Ney smoothed values for a set of given test sequences.
 */
public class TestSequenceExtractor {

    private Path absoluteDirectory;

    private Path continuationDirectory;

    private Path outputDirectory;

    private String delimiter;

    private int modelLength;

    private int numberOfCores;

    private Set<String> sequences;

    public TestSequenceExtractor(
            InputStream input,
            Path absoluteDirectory,
            Path continuationDirectory,
            Path outputDirectory,
            String delimiter,
            int modelLength,
            int numberOfCores) throws IOException {
        this.absoluteDirectory = absoluteDirectory;
        this.continuationDirectory = continuationDirectory;
        this.outputDirectory = outputDirectory;
        this.delimiter = delimiter;
        this.modelLength = modelLength;
        this.numberOfCores = numberOfCores;

        Files.createDirectory(outputDirectory);

        sequences = new HashSet<String>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sequences.add(line);
            }
        }
    }

    public void extractAbsoluteSequences() throws IOException,
            InterruptedException {
        ExecutorService executorService =
                Executors.newFixedThreadPool(numberOfCores);

        List<Pattern> patterns =
                Pattern.getGlmForSmoothingPatterns(modelLength);

        Path outputBaseDirectory =
                outputDirectory.resolve(absoluteDirectory.getFileName());
        Files.createDirectory(outputBaseDirectory);

        for (Pattern pattern : patterns) {
            Path inputDirectory = absoluteDirectory.resolve(pattern.toString());
            Path outputDirectory =
                    outputBaseDirectory.resolve(pattern.toString());

            SequenceExtractorTask sequenceExtractorTask =
                    new SequenceExtractorTask(inputDirectory, outputDirectory,
                            sequences, pattern, delimiter);
            executorService.execute(sequenceExtractorTask);
        }

        executorService.shutdown();
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }

    public void extractContinuationSequences() throws IOException,
            InterruptedException {
        ExecutorService executorService =
                Executors.newFixedThreadPool(numberOfCores);

        Path outputBaseDirectory =
                outputDirectory.resolve(continuationDirectory.getFileName());
        Files.createDirectory(outputBaseDirectory);

        try (DirectoryStream<Path> continuationFiles =
                Files.newDirectoryStream(continuationDirectory)) {
            for (Path inputDirectory : continuationFiles) {
                String patternLabel = inputDirectory.getFileName().toString();
                if (patternLabel.endsWith("-split")) {
                    continue;
                }

                Pattern pattern = new Pattern(patternLabel);
                Path outputDirectory =
                        outputBaseDirectory.resolve(patternLabel);

                SequenceExtractorTask sequenceExtractorTask =
                        new SequenceExtractorTask(inputDirectory,
                                outputDirectory, sequences, pattern, delimiter);
                executorService.execute(sequenceExtractorTask);
            }
        }

        executorService.shutdown();
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }

}