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

package de.glmtk.querying.estimator.iterative;

import static de.glmtk.common.NGram.SKP_NGRAM;
import static de.glmtk.common.NGram.WSKP_NGRAM;
import de.glmtk.common.BackoffMode;
import de.glmtk.common.NGram;
import de.glmtk.common.PatternElem;
import de.glmtk.counts.Counts;
import de.glmtk.counts.Discounts;
import de.glmtk.util.BinomDiamond;
import de.glmtk.util.BinomDiamondNode;

public class IterativeGenLangModelEstimator extends IterativeModKneserNeyEstimator {
    public static class GlmNode extends BinomDiamondNode<GlmNode> {
        private NGram history = null;
        private long absoluteCount = 0;
        private long continuationCount = 0;
        private double gammaNumerator = 0.0;
        private double absoluteFactor = 0.0;
        private double continuationFactor = 0.0;
    }

    public IterativeGenLangModelEstimator() {
        super();
        setBackoffMode(BackoffMode.SKP);
    }

    @Override
    public void setBackoffMode(BackoffMode backoffMode) {
        this.backoffMode = backoffMode;
    }

    public double probability(NGram sequence,
                              NGram history,
                              BinomDiamond<GlmNode> glmDiamond) {
        return calcProbability(sequence, history, 1, glmDiamond);
    }

    @Override
    protected double calcProbability(NGram sequence,
                                     NGram history,
                                     int recDepth) {
        BinomDiamond<GlmNode> glmDiamond = buildGlmDiamond(history);
        return calcProbability(sequence, history, recDepth, glmDiamond);
    }

    protected double calcProbability(NGram sequence,
                                     NGram history,
                                     @SuppressWarnings("unused") int recDepth,
                                     BinomDiamond<GlmNode> glmDiamond) {
        if (history.isEmpty())
            return (double) cache.getAbsolute(sequence) / cache.getNumWords();

        double prob = 0.0;
        for (GlmNode node : glmDiamond) {
            NGram fullSequence = getFullSequence(sequence, node.history);
            if (node.absoluteFactor != 0) {
                double absAlpha = calcAlpha(fullSequence, true,
                        !node.isBottom());
                prob += absAlpha * node.absoluteFactor;
            }
            if (node.continuationFactor != 0) {
                double contAlpha = calcAlpha(fullSequence, false,
                        !node.isBottom());
                prob += contAlpha * node.continuationFactor;
            }
        }

        return prob;
    }

    public BinomDiamond<GlmNode> buildGlmDiamond(NGram history) {
        int order = history.size();
        if (order == 0)
            return null;
        BinomDiamond<GlmNode> diamond = new BinomDiamond<>(order, GlmNode.class);

        for (GlmNode node : diamond.inOrder()) {
            NGram hist = history.applyIntPattern(~node.getIndex(), order);
            node.history = hist;
            node.absoluteCount = cache.getAbsolute(hist.concat(SKP_NGRAM));
            node.continuationCount = cache.getContinuation(
                    WSKP_NGRAM.concat(hist.convertSkpToWskp()).concat(
                            WSKP_NGRAM)).getOnePlusCount();
            node.gammaNumerator = calcGammaNumerator(hist);

            double coeff = calcCoefficient(node.getLevel(), diamond.order());

            if (node.absoluteCount == 0)
                node.absoluteFactor = 0.0;
            else if (node.isTop())
                node.absoluteFactor = coeff / node.absoluteCount;
            else {
                node.absoluteFactor = calcAbsoluteFactor(diamond.getTop(), node);
                node.absoluteFactor *= coeff / node.absoluteCount;
            }

            if (node.continuationCount == 0 || node.isTop())
                node.continuationFactor = 0;
            else {
                node.continuationFactor = calcContinuationFactor(
                        diamond.getTop(), node, true);
                node.continuationFactor *= coeff / node.continuationCount;
            }
        }

        return diamond;
    }

    private double calcCoefficient(int level,
                                   int order) {
        int result = 1;
        for (int i = 0; i != level; ++i)
            result *= (order - i);
        return 1.0 / result;
    }

    private int calcAbsoluteFactor(GlmNode ancestor,
                                   GlmNode node) {
        if (ancestor.absoluteCount != 0)
            return 0;

        if (ancestor.getLevel() == node.getLevel() - 1)
            return 1;

        int numUnseenPaths = 0;
        for (int i = 0; i != ancestor.numChilds(); ++i) {
            GlmNode child = ancestor.getChild(i);
            if (child.isAncestorOf(node))
                numUnseenPaths += calcAbsoluteFactor(child, node);
        }

        return numUnseenPaths;
    }

    private double calcContinuationFactor(GlmNode ancestor,
                                          GlmNode node,
                                          boolean absolute) {
        boolean last = ancestor.getLevel() == node.getLevel() - 1;
        double mult;
        if (absolute)
            if (ancestor.absoluteCount == 0)
                if (last)
                    mult = node.absoluteFactor == 0 ? 1.0 : 0.0;
                else
                    mult = 1.0;
            else {
                mult = ancestor.gammaNumerator / ancestor.absoluteCount;
                absolute = false;
            }
        else if (ancestor.continuationCount == 0)
            mult = 1.0;
        else
            mult = ancestor.gammaNumerator / ancestor.continuationCount;

        if (last)
            return mult;

        double sum = 0;
        for (int i = 0; i != ancestor.numChilds(); ++i) {
            GlmNode child = ancestor.getChild(i);
            if (child.isAncestorOf(node))
                sum += calcContinuationFactor(child, node, absolute);
        }

        return mult * sum;
    }

    private double calcGammaNumerator(NGram history) {
        Discounts discount = calcDiscounts(history.getPattern().concat(
                PatternElem.CNT));
        Counts contCount = cache.getContinuation(history.concat(WSKP_NGRAM));

        return discount.getOne() * contCount.getOneCount() + discount.getTwo()
                * contCount.getTwoCount() + discount.getThree()
                * contCount.getThreePlusCount();
    }
}
