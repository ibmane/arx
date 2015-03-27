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

package org.deidentifier.arx.gui.view.impl.menu;

import org.deidentifier.arx.gui.model.ModelDPresenceCriterion;
import org.deidentifier.arx.gui.resources.Resources;
import org.deidentifier.arx.gui.view.SWTUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;

/**
 * A view on a d-presence criterion.
 *
 * @author Fabian Prasser
 */
public class EditorCriterionDPresence extends EditorCriterion<ModelDPresenceCriterion>{

    /** View */
    private Scale                   sliderDMin;

    /** View */
    private Scale                   sliderDMax;

    /** View */
    private Label                   labelDMin;

    /** View */
    private Label                   labelDMax;

    /**
     * Creates a new instance.
     *
     * @param parent
     * @param model
     */
    public EditorCriterionDPresence(final Composite parent, 
                                  final ModelDPresenceCriterion model) {
        super(parent, model);
    }

    /**
     * Updates the "dMax" label and tooltip text.
     *
     * @param text
     */
    private void updateDMaxLabel(String text) {
        labelDMax.setText(text);
        labelDMax.setToolTipText(text);
    }

    /**
     * Updates the "dMin" label and tooltip text.
     *
     * @param text
     */
    private void updateDMinLabel(String text) {
        labelDMin.setText(text);
        labelDMin.setToolTipText(text);
    }
    
    /**
     * Build
     * @param parent
     * @return
     */
    protected Composite build(Composite parent) {

        // Create input group
        final Composite group = new Composite(parent, SWT.NONE);
        group.setLayoutData(SWTUtil.createFillHorizontallyGridData());
        final GridLayout groupInputGridLayout = new GridLayout();
        groupInputGridLayout.numColumns = 6;
        group.setLayout(groupInputGridLayout);

        // Create dmin slider
        final Label zLabel = new Label(group, SWT.NONE);
        zLabel.setText(Resources.getMessage("CriterionDefinitionView.50")); //$NON-NLS-1$

        labelDMin = new Label(group, SWT.BORDER | SWT.CENTER);
        final GridData d9 = new GridData();
        d9.minimumWidth = LABEL_WIDTH;
        d9.widthHint = LABEL_WIDTH;
        labelDMin.setLayoutData(d9);
        updateDMinLabel("0"); //$NON-NLS-1$

        sliderDMin = new Scale(group, SWT.HORIZONTAL);
        final GridData d6 = SWTUtil.createFillHorizontallyGridData();
        d6.horizontalSpan = 1;
        sliderDMin.setLayoutData(d6);
        sliderDMin.setMaximum(SWTUtil.SLIDER_MAX);
        sliderDMin.setMinimum(0);
        sliderDMin.setSelection(0);
        sliderDMin.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent arg0) {
                model.setDmin(SWTUtil.sliderToDouble(0, 1, sliderDMin.getSelection()));
                String dmin = String.valueOf(model.getDmin());
                updateDMinLabel(dmin);
                
                if (model.getDmin() > model.getDmax()) {
                    model.setDmax(model.getDmin());
                    String dmax = String.valueOf(model.getDmin());
                    updateDMaxLabel(dmax);
                    sliderDMax.setSelection(sliderDMin.getSelection());
                }
            }
        });

        // Create dax slider
        final Label z2Label = new Label(group, SWT.NONE);
        z2Label.setText(Resources.getMessage("CriterionDefinitionView.51")); //$NON-NLS-1$

        labelDMax = new Label(group, SWT.BORDER | SWT.CENTER);
        final GridData d91 = new GridData();
        d91.minimumWidth = LABEL_WIDTH;
        d91.widthHint = LABEL_WIDTH;
        labelDMax.setLayoutData(d91);
        updateDMaxLabel("0"); //$NON-NLS-1$

        sliderDMax = new Scale(group, SWT.HORIZONTAL);
        final GridData d62 = SWTUtil.createFillHorizontallyGridData();
        d62.horizontalSpan = 1;
        sliderDMax.setLayoutData(d62);
        sliderDMax.setMaximum(SWTUtil.SLIDER_MAX);
        sliderDMax.setMinimum(0);
        sliderDMax.setSelection(0);
        sliderDMax.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent arg0) {
                model.setDmax(SWTUtil.sliderToDouble(0, 1, sliderDMax.getSelection()));
                String dmax = String.valueOf(model.getDmax());
                updateDMaxLabel(dmax);
                
                if (model.getDmax() < model.getDmin()) {
                    model.setDmin(model.getDmax());
                    String dmin = String.valueOf(model.getDmax());
                    updateDMinLabel(dmin);
                    sliderDMin.setSelection(sliderDMax.getSelection());
                }
            }
        });
        
        return group;
    }

    /**
     * Parses the input
     */
    protected void parse(ModelDPresenceCriterion model) {
        updateDMinLabel(String.valueOf(model.getDmin()));
        sliderDMin.setSelection(SWTUtil.doubleToSlider(0, 1d, model.getDmin()));
        updateDMaxLabel(String.valueOf(model.getDmax()));
        sliderDMax.setSelection(SWTUtil.doubleToSlider(0, 1d, model.getDmax()));
    }
}
