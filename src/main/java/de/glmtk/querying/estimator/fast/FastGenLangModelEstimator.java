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

import java.util.Set;

import de.glmtk.common.NGram;


public class FastGenLangModelEstimator extends FastGenLangModelAbsEstimator {
    @Override
    protected double calcProbability(NGram sequence,
                                     NGram history,
                                     int recDepth) {
        NGram fullHistory = getFullHistory(sequence, history);
        long denominator = cache.getCount(fullHistory);
        if (denominator == 0.0) {
            double result = 0;
            Set<NGram> diffHistories =
                history.getDifferentiatedNGrams(backoffMode);
            for (NGram diffHistory : diffHistories) {
                result += probability(sequence, diffHistory, recDepth);
            }
            result /= diffHistories.size();
            return result;
        }

        NGram fullSequence = getFullSequence(sequence, history);
        long numerator = cache.getCount(fullSequence);
        if (history.isEmptyOrOnlySkips()) {
            return (double) numerator / denominator;
        }

        double discount = cache.getDiscount(fullSequence);
        double gamma = cache.getGammaHigh(history) / denominator;

        double alpha = Math.max(numerator - discount, 0.0) / denominator;
        double beta = 0;
        Set<NGram> differentiatedHistories =
            history.getDifferentiatedNGrams(backoffMode);
        for (NGram differentiatedHistory : differentiatedHistories) {
            beta += probabilityLower(sequence, differentiatedHistory, recDepth);
        }
        beta /= differentiatedHistories.size();

        return alpha + gamma * beta;
    }

    public final double probabilityLower(NGram sequence,
                                         NGram history,
                                         int recDepth) {
        logTrace(recDepth, "%s#probabilityLower(%s,%s)",
            getClass().getSimpleName(), sequence, history);
        ++recDepth;

        double result = calcProbabilityLower(sequence, history, recDepth);
        logTrace(recDepth, "result = %f", result);

        return result;
    }

    protected double calcProbabilityLower(NGram sequence,
                                          NGram history,
                                          int recDepth) {
        NGram fullHistory = getFullHistory(sequence, history);
        long denominator =
            cache.getCount(WSKP_NGRAM.concat(fullHistory.convertSkpToWskp()));
        if (denominator == 0.0) {
            double result = 0;
            Set<NGram> diffHistories =
                history.getDifferentiatedNGrams(backoffMode);
            for (NGram diffHistory : diffHistories) {
                result += probabilityLower(sequence, diffHistory, recDepth);
            }
            result /= diffHistories.size();
            return result;
        }

        NGram fullSequence = getFullSequence(sequence, history);
        long numerator =
            cache.getCount(WSKP_NGRAM.concat(fullSequence.convertSkpToWskp()));
        if (history.isEmptyOrOnlySkips()) {
            return (double) numerator / denominator;
        }

        double discount = cache.getDiscount(fullSequence);
        double gamma = cache.getGammaLow(history) / denominator;

        double alpha = Math.max(numerator - discount, 0.0) / denominator;
        double beta = 0;
        Set<NGram> differentiatedHistories =
            history.getDifferentiatedNGrams(backoffMode);
        for (NGram differentiatedHistory : differentiatedHistories) {
            beta += probabilityLower(sequence, differentiatedHistory, recDepth);
        }
        beta /= differentiatedHistories.size();

        return alpha + gamma * beta;
    }
}
