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

package org.deidentifier.arx.examples;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import org.deidentifier.arx.ARXAnonymizer;
import org.deidentifier.arx.ARXConfiguration;
import org.deidentifier.arx.ARXResult;
import org.deidentifier.arx.AttributeType;
import org.deidentifier.arx.AttributeType.Hierarchy;
import org.deidentifier.arx.AttributeType.Hierarchy.DefaultHierarchy;
import org.deidentifier.arx.AttributeType.Microaggregation;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.Data.DefaultData;
import org.deidentifier.arx.DataType;
import org.deidentifier.arx.aggregates.MicroaggregateFunction;
import org.deidentifier.arx.aggregates.MicroaggregateFunction.MicroAggregateFunctionBuilder;
import org.deidentifier.arx.criteria.KAnonymity;

/**
 * This class implements an example of how to compute summary statistics
 *
 * @author Fabian Prasser
 * @author Florian Kohlmayer
 */
public class Example31 extends Example {
    
    /**
     * Entry point.
     * 
     * @param args
     *            the arguments
     */
    public static void main(final String[] args) {
        
        // Define data
        DefaultData data = Data.create();
        data.add("age", "gender", "zipcode", "date");
        data.add("45", "female", "81675", "01.01.1982");
        data.add("34", "male", "81667", "11.05.1982");
        data.add("NULL", "male", "81925", "31.08.1982");
        data.add("70", "female", "81931", "02.07.1982");
        data.add("34", "female", "NULL", "05.01.1982");
        data.add("70", "male", "81931", "24.03.1982");
        data.add("45", "male", "81931", "NULL");
        
        data.getDefinition().setDataType("age", DataType.INTEGER);
        data.getDefinition().setDataType("zipcode", DataType.DECIMAL);
        data.getDefinition().setDataType("date", DataType.DATE);
        
        final DefaultHierarchy gender = Hierarchy.create();
        gender.add("male", "*");
        gender.add("female", "*");
        
        // Only excerpts for readability
        final DefaultHierarchy zipcode = Hierarchy.create();
        zipcode.add("81667", "8166*", "816**", "81***", "8****", "*****");
        zipcode.add("81675", "8167*", "816**", "81***", "8****", "*****");
        zipcode.add("81925", "8192*", "819**", "81***", "8****", "*****");
        zipcode.add("81931", "8193*", "819**", "81***", "8****", "*****");
        zipcode.add("NULL", "NULL", "NULL", "NULL", "NULL", "*****");
        
        // Create microaggregation for attribute age
        MicroaggregateFunction<Long> function = new MicroAggregateFunctionBuilder<Long>(DataType.INTEGER).createArithmeticMeanFunction();
        AttributeType ageType = Microaggregation.create(function);
        
        data.getDefinition().setAttributeType("age", ageType);
        data.getDefinition().setAttributeType("gender", gender);
        data.getDefinition().setAttributeType("zipcode", zipcode);
        
        // Create an instance of the anonymizer
        final ARXAnonymizer anonymizer = new ARXAnonymizer();
        final ARXConfiguration config = ARXConfiguration.create();
        config.addCriterion(new KAnonymity(2));
        config.setMaxOutliers(0.5d);
        try {
            final ARXResult result = anonymizer.anonymize(data, config);
            
            // Print info
            printResult(result, data);
            
            // Process results
            System.out.println(" - Transformed data:");
            final Iterator<String[]> transformed = result.getOutput(false)
                                                         .iterator();
            while (transformed.hasNext()) {
                System.out.print("   ");
                System.out.println(Arrays.toString(transformed.next()));
            }
            
        } catch (final IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        
    }
}