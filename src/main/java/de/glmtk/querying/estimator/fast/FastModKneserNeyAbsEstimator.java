/*
 * Generalized Language Modeling Toolkit (GLMTK)
 *
 * Copyright (C) 2015 Lukas Schmelzeisen, Rene Pickhardt
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

package de.glmtk.querying.estimator.fast;

import static de.glmtk.common.NGram.WSKP_NGRAM;

import java.util.HashMap;
import java.util.Map;

import de.glmtk.cache.OldCache;
import de.glmtk.common.BackoffMode;
import de.glmtk.common.NGram;
import de.glmtk.common.Pattern;
import de.glmtk.counts.Counts;
import de.glmtk.counts.Discounts;
import de.glmtk.counts.NGramTimes;
import de.glmtk.querying.estimator.AbstractEstimator;

public class FastModKneserNeyAbsEstimator extends AbstractEstimator {
    protected BackoffMode backoffMode;
    private Map<Pattern, Discounts> discounts;

    public FastModKneserNeyAbsEstimator() {
        setBackoffMode(BackoffMode.DEL);
        discounts = new HashMap<>();
    }

    public void setBackoffMode(BackoffMode backoffMode) {
        if (backoffMode != BackoffMode.DEL && backoffMode != BackoffMode.SKP)
            throw new IllegalArgumentException(
                    "Illegal BackoffMode for this class.");
        this.backoffMode = backoffMode;
    }

    @Override
    public void setCache(OldCache cache) {
        super.setCache(cache);
        discounts = new HashMap<>();
    }

    @Override
    protected double calcProbability(NGram sequence,
                                     NGram history,
                                     int recDepth) {
        long denominator = cache.getCount(getFullHistory(sequence, history));
        if (denominator == 0.0)
            return probability(sequence, history.backoff(backoffMode), recDepth);

        NGram fullSequence = getFullSequence(sequence, history);
        long numerator = cache.getCount(fullSequence);
        if (history.isEmptyOrOnlySkips())
            return (double) numerator / denominator;

        Discounts d = getDiscounts(fullSequence.getPattern(), recDepth);
        double discount = d.getForCount(cache.getCount(fullSequence));

        Counts c = cache.getContinuation(history.concat(WSKP_NGRAM));
        double gamma = (d.getOne() * c.getOneCount() + d.getTwo()
                * c.getTwoCount() + d.getThree() * c.getThreePlusCount())
                / denominator;

        NGram backoffHistory = history.backoffUntilSeen(backoffMode, cache);
        double alpha = Math.max(numerator - discount, 0.0) / denominator;
        double beta = probability(sequence, backoffHistory, recDepth);

        return alpha + gamma * beta;
    }

    protected Discounts getDiscounts(Pattern pattern,
                                     @SuppressWarnings("unused") int recDepth) {
        Discounts result = discounts.get(pattern);
        if (result != null)
            return result;

        NGramTimes n = cache.getNGramTimes(pattern);
        double y = (double) n.getOneCount()
                / (n.getOneCount() + n.getTwoCount());
        result = new Discounts(1.0f - 2.0f * y * n.getTwoCount()
                / n.getOneCount(), 2.0f - 3.0f * y * n.getThreeCount()
                / n.getTwoCount(), 3.0f - 4.0f * y * n.getFourCount()
                / n.getThreeCount());

        discounts.put(pattern, result);
        return result;
    }

}
