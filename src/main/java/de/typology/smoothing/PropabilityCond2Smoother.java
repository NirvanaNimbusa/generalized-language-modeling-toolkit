package de.typology.smoothing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import de.typology.patterns.PatternElem;

public class PropabilityCond2Smoother extends DiscountSmoother {

    public PropabilityCond2Smoother(
            Path absoluteDir,
            Path continuationDir,
            String delimiter,
            Double absoluteDiscount) throws IOException {
        super(absoluteDir, continuationDir, delimiter, absoluteDiscount);
    }

    @Override
    protected double calcProbabilityCond2(
            List<String> reqSequence,
            List<String> condSequence) {
        return propabilityCond2(reqSequence,
                condSequence.subList(1, condSequence.size()));
    }

    protected double propabilityCond2(
            List<String> reqSequence,
            List<String> condSequence) {
        debugP2(reqSequence, condSequence);

        List<String> sequence = getSequence2(reqSequence, condSequence);
        List<String> history = getHistory2(reqSequence, condSequence);

        double sequenceCount = getContinuation(sequence).getOnePlusCount();
        double historyCount = getContinuation(history).getOnePlusCount();

        debugSequenceHistory(sequence, history, sequenceCount, historyCount);

        if (sequenceCount == 0) {
            return calcResultSequenceCount0(reqSequence, condSequence);
        } else {
            return calcResult(reqSequence, condSequence, sequence, history,
                    sequenceCount, historyCount);
        }
    }

    @Override
    protected double calcResultSequenceCount0(
            List<String> reqSequence,
            List<String> condSequence) {
        return calcProbabilityCond2(reqSequence, condSequence);
    }

    protected final List<String> getSequence2(
            List<String> reqSequence,
            List<String> condSequence) {
        int n = reqSequence.size() + condSequence.size();

        List<String> sequence = new ArrayList<String>(n);
        sequence.add(PatternElem.SKIPPED_WORD);
        sequence.addAll(condSequence);
        sequence.addAll(reqSequence);

        return sequence;
    }

    protected final List<String> getHistory2(
            List<String> reqSequence,
            List<String> condSequence) {
        int n = reqSequence.size() + condSequence.size();

        List<String> history = new ArrayList<String>(n);
        history.add(PatternElem.SKIPPED_WORD);
        history.addAll(condSequence);
        for (int i = 0; i != reqSequence.size(); ++i) {
            history.add(PatternElem.SKIPPED_WORD);
        }

        return history;
    }

    protected final void debugP2(
            List<String> reqSequence,
            List<String> condSequence) {
        if (DEBUG) {
            System.out.print("    P2( " + reqSequence + " | " + condSequence
                    + " )");
        }
    }

}
