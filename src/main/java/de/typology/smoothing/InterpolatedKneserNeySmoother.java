package de.typology.smoothing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.typology.counting.Counter;
import de.typology.patterns.Pattern;
import de.typology.patterns.PatternElem;
import de.typology.utils.StringUtils;

public class InterpolatedKneserNeySmoother extends Smoother {

    private Map<Pattern, Map<Integer, Integer>> nGramTimesCountCache =
            new HashMap<Pattern, Map<Integer, Integer>>();

    private Map<Pattern, Double> discountCache = new HashMap<Pattern, Double>();

    public InterpolatedKneserNeySmoother(
            Path absoluteDir,
            Path continuationDir,
            String delimiter) throws IOException {
        super(absoluteDir, continuationDir, delimiter);
    }

    @Override
    protected double propabilityCond(
            List<String> reqSequence,
            List<String> condSequence) {
        int n = reqSequence.size() + condSequence.size() - 1;

        List<String> sequence = new ArrayList<String>(n);
        sequence.addAll(condSequence);
        sequence.addAll(reqSequence);

        List<String> history = new ArrayList<String>(n);
        history.addAll(condSequence);
        for (int i = 0; i != reqSequence.size(); ++i) {
            history.add(PatternElem.SKIPPED_WORD);
        }

        double numerator =
                Math.max(
                        getAbsolute(sequence) - discount(getPattern(sequence)),
                        0.);
        double denomintator = getAbsolute(sequence);

        double lambda = lambda_high(history);

        System.out.print("P( ");
        for (String reqWord : reqSequence) {
            System.out.print(reqWord + " ");
        }
        System.out.print("| ");
        for (String condWord : condSequence) {
            System.out.print(condWord + " ");
        }
        System.out.println(")");

        System.out.print("sequence = ");
        for (String word : sequence) {
            System.out.print(word + " ");
        }
        System.out.print("; history = ");
        for (String historyWord : history) {
            System.out.print(historyWord + " ");
        }
        System.out.println(";");

        return 0;
    }

    private double propabilityCond2(
            List<String> reqSequence,
            List<String> condSequence) {
        return 0;
    }

    /**
     * @return The total number of n-grams with {@code pattern} which appear
     *         exactly {@code times} often in the training data.
     */
    private int nGramTimesCount(Pattern pattern, int times) {
        Map<Integer, Integer> patternCache = nGramTimesCountCache.get(pattern);
        if (patternCache == null) {
            patternCache = new HashMap<Integer, Integer>();
            nGramTimesCountCache.put(pattern, patternCache);
        }

        Integer count = patternCache.get(times);
        if (count == null) {
            count = 0;
            for (int absoluteCount : absoluteCounts.get(pattern).values()) {
                if (absoluteCount == times) {
                    ++count;
                }
            }
            patternCache.put(times, count);
        }

        return count;
    }

    /**
     * @return The discount value for n-grams with {@code pattern}.
     */
    private double discount(Pattern pattern) {
        Double discount = discountCache.get(pattern);
        if (discount == null) {
            double n_1 = nGramTimesCount(pattern, 1);
            double n_2 = nGramTimesCount(pattern, 2);
            discount = n_1 / (n_1 + 2. * n_2);
            discountCache.put(pattern, discount);
        }

        return discount;
    }

    private double lambda_high(List<String> history) {
        // TODO: correct to use discount of history and not of sequence?
        double d = discount(getPattern(history));
        double n = getContinuation(history).getOneCount();
        double c = getAbsolute(history);

        return d * n / c;
    }

    private double lambda_mid(List<String> history) {
        return 0;
    }

    private Pattern getPattern(List<String> sequence) {
        List<PatternElem> patternElems =
                new ArrayList<PatternElem>(sequence.size());
        for (String word : sequence) {
            if (word.equals(PatternElem.SKIPPED_WORD)) {
                patternElems.add(PatternElem.SKP);
            } else {
                patternElems.add(PatternElem.CNT);
            }
        }
        return new Pattern(patternElems);
    }

    private int getAbsolute(List<String> sequence) {
        Pattern pattern = getPattern(sequence);
        String string = StringUtils.join(sequence, " ");
        return absoluteCounts.get(pattern).get(string);
    }

    private Counter getContinuation(List<String> sequence) {
        Pattern pattern = getPattern(sequence);
        String string = StringUtils.join(sequence, " ");
        return continuationCounts.get(pattern).get(string);
    }

}
