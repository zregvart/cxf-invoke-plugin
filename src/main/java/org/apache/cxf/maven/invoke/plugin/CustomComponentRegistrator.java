/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.maven.invoke.plugin;

import java.util.stream.Collectors;

import static java.util.Arrays.stream;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.configurator.BasicComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ComponentConfigurator;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;

@Component(role = ComponentConfigurator.class, hint = "basic")
public final class CustomComponentRegistrator extends BasicComponentConfigurator implements Initializable {

    private static final class ConfigurationConverterImplementation implements ConfigurationConverter {
        static String asXml(final PlexusConfiguration configuration) {
            final StringBuilder value = new StringBuilder();

            value.append('<');
            value.append(configuration.getName());

            final String attributes = stream(configuration.getAttributeNames())
                    .map(a -> a + "=\"" + configuration.getAttribute(a) + "\"").collect(Collectors.joining(" "));

            if (!attributes.isEmpty()) {
                value.append(' ');
                value.append(attributes);
            }

            value.append('>');

            for (final PlexusConfiguration child : configuration.getChildren()) {
                value.append(asXml(child));
            }

            final String configurationValue = configuration.getValue();
            if ((configurationValue != null) && !configurationValue.isEmpty()) {
                value.append(configurationValue);
            }

            value.append("</");
            value.append(configuration.getName());
            value.append('>');

            return value.toString();
        }

        @Override
        public boolean canConvert(@SuppressWarnings("rawtypes") final Class type) {
            return XmlString.class.isAssignableFrom(type);
        }

        @Override
        public Object fromConfiguration(final ConverterLookup converterLookup, final PlexusConfiguration configuration,
                @SuppressWarnings("rawtypes") final Class type, @SuppressWarnings("rawtypes") final Class baseType,
                final ClassLoader classLoader, final ExpressionEvaluator expressionEvaluator)
                throws ComponentConfigurationException {
            return fromConfiguration(configuration, expressionEvaluator);
        }

        @Override
        public Object fromConfiguration(final ConverterLookup converterLookup, final PlexusConfiguration configuration,
                @SuppressWarnings("rawtypes") final Class type, @SuppressWarnings("rawtypes") final Class baseType,
                final ClassLoader classLoader, final ExpressionEvaluator expressionEvaluator,
                final ConfigurationListener listener) throws ComponentConfigurationException {
            return fromConfiguration(configuration, expressionEvaluator);
        }

        Object fromConfiguration(final PlexusConfiguration configuration, final ExpressionEvaluator expressionEvaluator)
                throws ComponentConfigurationException {
            final String xmlStringValue = asXml(configuration);

            final String literalStringValue;
            try {
                literalStringValue = String.valueOf(expressionEvaluator.evaluate(xmlStringValue));
            } catch (final ExpressionEvaluationException e) {
                throw new ComponentConfigurationException(configuration, "Unable to evaluate expression", e);
            }

            return new XmlString(literalStringValue);
        }
    }

    @Override
    public void initialize() throws InitializationException {
        converterLookup.registerConverter(new ConfigurationConverterImplementation());
    }
}
