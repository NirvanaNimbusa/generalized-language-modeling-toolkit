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

package de.glmtk.querying.estimator.fast;

import static de.glmtk.common.NGram.SKP_NGRAM;
import static de.glmtk.common.NGram.WSKP_NGRAM;
import de.glmtk.common.NGram;
import de.glmtk.counts.Counts;
import de.glmtk.counts.Discount;

public class FastModifiedKneserNeyEstimator extends FastModifiedKneserNeyAbsEstimator {
    @Override
    protected double calcProbability(NGram sequence,
                                     NGram history,
                                     int recDepth) {
        double denominator = countCache.getAbsolute(history.concat(SKP_NGRAM));

        if (history.isEmptyOrOnlySkips()) {
            if (denominator == 0.0)
                return (double) countCache.getAbsolute(sequence.get(0))
                        / countCache.getNumWords();

            double numerator = countCache.getAbsolute(history.concat(sequence));
            return numerator / denominator;
        }

        double discount;
        double gamma = 0.0;
        {
            Discount d = getDiscounts(history.getPattern(), recDepth);
            long abs = countCache.getAbsolute(history);
            if (abs == 0)
                discount = 0.0;
            else if (abs == 1)
                discount = d.getOne();
            else if (abs == 2)
                discount = d.getTwo();
            else
                discount = d.getThree();

            if (denominator != 0) {
                Counts c = countCache.getContinuation(history.concat(NGram.WSKP_NGRAM));
                gamma = (d.getOne() * c.getOneCount() + d.getTwo()
                        * c.getTwoCount() + d.getThree()
                        * c.getThreePlusCount())
                        / denominator;
            }
        }

        double alpha;
        if (denominator == 0.0)
            alpha = (double) countCache.getAbsolute(sequence.get(0))
                    / countCache.getNumWords();
        else {
            double numerator = countCache.getAbsolute(history.concat(sequence));
            if (numerator > discount)
                numerator -= discount;
            else
                numerator = 0.0;

            alpha = numerator / denominator;
        }

        NGram backoffHistory = history.backoffUntilSeen(backoffMode, countCache);
        double beta = probabilityLower(sequence, backoffHistory, recDepth);

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
        double denominator = countCache.getContinuation(
                WSKP_NGRAM.concat(history.concat(SKP_NGRAM).convertSkpToWskp())).getOnePlusCount();

        if (history.isEmptyOrOnlySkips()) {
            if (denominator == 0.0)
                return (double) countCache.getAbsolute(sequence.get(0))
                        / countCache.getNumWords();

            double numerator = countCache.getContinuation(
                    WSKP_NGRAM.concat(history.concat(sequence)).convertSkpToWskp()).getOnePlusCount();
            return numerator / denominator;
        }

        double discount;
        double gamma = 0.0;
        {
            Discount d = getDiscounts(history.getPattern(), recDepth);
            long abs = countCache.getAbsolute(history);
            if (abs == 0)
                discount = 0.0;
            else if (abs == 1)
                discount = d.getOne();
            else if (abs == 2)
                discount = d.getTwo();
            else
                discount = d.getThree();

            if (denominator != 0) {
                Counts c = countCache.getContinuation(history.concat(NGram.WSKP_NGRAM));
                gamma = (d.getOne() * c.getOneCount() + d.getTwo()
                        * c.getTwoCount() + d.getThree()
                        * c.getThreePlusCount())
                        / denominator;
            }
        }

        double alpha;
        if (denominator == 0.0)
            alpha = (double) countCache.getAbsolute(sequence.get(0))
                    / countCache.getNumWords();
        else {
            double numerator = countCache.getContinuation(
                    WSKP_NGRAM.concat(history.concat(sequence).convertSkpToWskp())).getOnePlusCount();
            if (numerator > discount)
                numerator -= discount;
            else
                numerator = 0.0;

            alpha = numerator / denominator;
        }

        NGram backoffHistory = history.backoffUntilSeen(backoffMode, countCache);
        double beta = probabilityLower(sequence, backoffHistory, recDepth);

        return alpha + gamma * beta;
    }
}