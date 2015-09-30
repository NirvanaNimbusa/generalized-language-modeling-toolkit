/*
 * Generalized Language Modeling Toolkit (GLMTK)
 *
 * Copyright (C) 2014-2015 Lukas Schmelzeisen, Rene Pickhardt
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

package de.glmtk.querying.estimator.fraction;

import static de.glmtk.common.NGram.WSKP_NGRAM;

import de.glmtk.common.NGram;


public class ContinuationMaximumLikelihoodEstimator extends FractionEstimator {
    @Override
    public boolean isDefined(NGram sequence,
                             NGram history,
                             int recDepth) {
        return WSKP_NGRAM
            .concat(getFullHistory(sequence, history).convertSkpToWskp())
            .seen(cache);
    }

    @Override
    protected double calcNumerator(NGram sequence,
                                   NGram history,
                                   int recDepth) {
        NGram contFullSequence = WSKP_NGRAM
            .concat(getFullSequence(sequence, history).convertSkpToWskp());
        long contFullSequenceCount = cache.getCount(contFullSequence);
        logTrace(recDepth, "contFullSequence = %s (%d)", contFullSequence,
            contFullSequenceCount);
        return contFullSequenceCount;
    }

    @Override
    protected double calcDenominator(NGram sequence,
                                     NGram history,
                                     int recDepth) {
        NGram contFullHistory = WSKP_NGRAM
            .concat(getFullHistory(sequence, history).convertSkpToWskp());
        long contFullHistoryCount = cache.getCount(contFullHistory);
        logTrace(recDepth, "contFullHistory = %s (%d)", contFullHistory,
            contFullHistoryCount);
        return contFullHistoryCount;
    }
}
