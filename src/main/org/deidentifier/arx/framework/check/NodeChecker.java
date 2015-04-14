/*
 * ARX: Powerful Data Anonymization
 * Copyright 2012 - 2015 Florian Kohlmayer, Fabian Prasser
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.deidentifier.arx.framework.check;

import org.deidentifier.arx.ARXConfiguration;
import org.deidentifier.arx.ARXConfiguration.ARXConfigurationInternal;
import org.deidentifier.arx.aggregates.MicroaggregateFunction;
import org.deidentifier.arx.framework.check.StateMachine.Transition;
import org.deidentifier.arx.framework.check.distribution.IntArrayDictionary;
import org.deidentifier.arx.framework.check.groupify.HashGroupify;
import org.deidentifier.arx.framework.check.groupify.IHashGroupify;
import org.deidentifier.arx.framework.check.history.History;
import org.deidentifier.arx.framework.data.Data;
import org.deidentifier.arx.framework.data.DataManager;
import org.deidentifier.arx.framework.lattice.Lattice;
import org.deidentifier.arx.framework.lattice.Node;
import org.deidentifier.arx.metric.InformationLoss;
import org.deidentifier.arx.metric.InformationLossWithBound;
import org.deidentifier.arx.metric.Metric;

/**
 * This class orchestrates the process of checking a node for k-anonymity.
 *
 * @author Fabian Prasser
 * @author Florian Kohlmayer
 */
public class NodeChecker implements INodeChecker {

    /** The config. */
    private final ARXConfigurationInternal config;

    /** The data. */
    private final Data             dataGH;
    
    /** The microaggregate data. */
    private final Data                        bufferOT;
        /** The microaggregate functions. */
    private final MicroaggregateFunction[] functionsMA;
    /** The microaggregate start index. */
    private final int                         startMA;

    /** The current hash groupify. */
    protected IHashGroupify        currentGroupify;

    /** The history. */
    protected History              history;

    /** The last hash groupify. */
    protected IHashGroupify        lastGroupify;

    /** The metric. */
    protected Metric<?>            metric;

    /** The state machine. */
    protected StateMachine         stateMachine;

    /** The data transformer. */
    protected Transformer          transformer;

    /**
     * Creates a new NodeChecker instance.
     * 
     * @param manager
     *            The manager
     * @param metric
     *            The metric
     * @param config
     *            The anonymization configuration
     * @param historyMaxSize
     *            The history max size
     * @param snapshotSizeDataset
     *            The history threshold
     * @param snapshotSizeSnapshot
     *            The history threshold replacement
     */
    public NodeChecker(final DataManager manager, final Metric<?> metric, final ARXConfigurationInternal config, final int historyMaxSize, final double snapshotSizeDataset, final double snapshotSizeSnapshot) {

        // Initialize all operators
        this.metric = metric;
        this.config = config;
        this.dataGH = manager.getDataQI();
        // Make copy of the microaggregation columns for the output transformation
        this.functionsMA = manager.getMicroaggregationFunctions();
        this.bufferOT = manager.getDataMA().clone();

        
        int initialSize = (int) (manager.getDataQI().getDataLength() * 0.01d);
        IntArrayDictionary dictionaryDistributionValue;
        IntArrayDictionary dictionaryDistributionFreq;
        if ((config.getRequirements() & ARXConfiguration.REQUIREMENT_DISTRIBUTION) != 0) {
            dictionaryDistributionValue = new IntArrayDictionary(initialSize);
            dictionaryDistributionFreq = new IntArrayDictionary(initialSize);
        } else {
            // Just to allow byte code instrumentation
            dictionaryDistributionValue = new IntArrayDictionary(0);
            dictionaryDistributionFreq = new IntArrayDictionary(0);
        }
        int[][] attributeWithDistribution = manager.getDataDI();

        startMA = manager.getMAStartIndex();

        this.history = new History(manager.getDataQI().getArray().length,
                                   historyMaxSize,
                                   snapshotSizeDataset,
                                   snapshotSizeSnapshot,
                                   config,
                                   dictionaryDistributionValue,
                                   dictionaryDistributionFreq);
        
        this.stateMachine = new StateMachine(history);
        this.currentGroupify = new HashGroupify(initialSize, config);
        this.lastGroupify = new HashGroupify(initialSize, config);
        this.transformer = new Transformer(manager.getDataQI().getArray(),
                                           manager.getHierarchies(),
                                           attributeWithDistribution,
                                           config,
                                           dictionaryDistributionValue,
                                           dictionaryDistributionFreq);
    }

    @Override
    public TransformedData applyAndSetProperties(final Node transformation) {

        // Apply transition and groupify
        currentGroupify = transformer.apply(0L, transformation.getTransformation(), currentGroupify);
        currentGroupify.analyze(transformation, true);
        if (!currentGroupify.isAnonymous() && !config.isSuppressionAlwaysEnabled()) {
            currentGroupify.resetSuppression();
        }

        // Determine information loss
        // TODO: This may already be known
        InformationLoss<?> loss = transformation.getInformationLoss();
        if (loss == null) {
            loss = metric.getInformationLoss(transformation, currentGroupify).getInformationLoss();
        }
        
        // Microaggregate
        // Important: has to be done before marking outliers!
        if (config.isMicroaggregation()) {
            currentGroupify.microaggregate(transformer.getBuffer(), bufferOT, startMA, functionsMA);
        }
        
        // Find outliers
        if (config.getAbsoluteMaxOutliers() != 0 || !currentGroupify.isAnonymous()) {
            currentGroupify.markOutliers(transformer.getBuffer());
        }
        
       
        
        // Set properties
        Lattice lattice = new Lattice(new Node[][]{{transformation}}, 0);
        lattice.setChecked(transformation, new Result(currentGroupify.isAnonymous(), 
                                                      currentGroupify.isKAnonymous(),
                                                      loss,
                                                      null));
        
        // Return the buffer
        return new TransformedData(getBuffer(), bufferOT, currentGroupify.getGroupStatistics());
    }

    @Override
    public INodeChecker.Result check(final Node node) {
        return check(node, false);
    }

    @Override
    public INodeChecker.Result check(final Node node, final boolean forceMeasureInfoLoss) {
        
        // If the result is already know, simply return it
        if (node.getData() != null && node.getData() instanceof INodeChecker.Result) {
            return (INodeChecker.Result)node.getData();
        }

        // Store snapshot from last check
        if (stateMachine.getLastNode() != null) {
            history.store(stateMachine.getLastNode(), currentGroupify, stateMachine.getLastTransition().snapshot);
        }

        // Transition
        final Transition transition = stateMachine.transition(node);

        // Switch groupifies
        final IHashGroupify temp = lastGroupify;
        lastGroupify = currentGroupify;
        currentGroupify = temp;

        // Apply transition
        switch (transition.type) {
        case UNOPTIMIZED:
            currentGroupify = transformer.apply(transition.projection, node.getTransformation(), currentGroupify);
            break;
        case ROLLUP:
            currentGroupify = transformer.applyRollup(transition.projection, node.getTransformation(), lastGroupify, currentGroupify);
            break;
        case SNAPSHOT:
            currentGroupify = transformer.applySnapshot(transition.projection, node.getTransformation(), currentGroupify, transition.snapshot);
            break;
        }
        
        // We are done with transforming and adding
        currentGroupify.analyze(node, forceMeasureInfoLoss);
        if (forceMeasureInfoLoss && !currentGroupify.isAnonymous() && !config.isSuppressionAlwaysEnabled()) {
            currentGroupify.resetSuppression();
        }
        
        // Compute information loss and lower bound
        InformationLossWithBound<?> result = (currentGroupify.isAnonymous() || forceMeasureInfoLoss) ?
                                          metric.getInformationLoss(node, currentGroupify) : null;
        InformationLoss<?> loss = result != null ? result.getInformationLoss() : null;
        InformationLoss<?> bound = result != null ? result.getLowerBound() : metric.getLowerBound(node, currentGroupify);
        
        // Return result;
        return new INodeChecker.Result(currentGroupify.isAnonymous(), 
                                       currentGroupify.isKAnonymous(),
                                       loss,
                                       bound);
    }

    @Override
    public Data getBuffer() {
        return new Data(transformer.getBuffer(), dataGH.getHeader(), dataGH.getMap(), dataGH.getDictionary());
    }

    @Override
    public ARXConfigurationInternal getConfiguration() {
        return config;
    }

    @Override
    @Deprecated
    public Data getData() {
        return dataGH;
    }

    @Override
    public IHashGroupify getGroupify() {
        return currentGroupify;
    }

    /**
     * Returns the checkers history, if any.
     *
     * @return
     */
    public History getHistory() {
        return history;
    }

    @Override
    @Deprecated
    public double getInformationLoss(final Node node) {
        throw new UnsupportedOperationException("Not implemented!");
    }

    @Override
    public Metric<?> getMetric() {
        return metric;
    }
}
