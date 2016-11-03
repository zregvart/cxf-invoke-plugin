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

import java.io.File;
import java.io.IOException;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamResult;

import org.codehaus.plexus.configuration.PlexusConfiguration;

final class PlexusConfigurationWriter {

    private PlexusConfigurationWriter() {
        // utility class
    }

    static void writeConfigurationTo(final PlexusConfiguration configuration, final File requestPath) {
        final File parent = requestPath.getParentFile();

        if (!parent.exists()) {
            parent.mkdirs();
        }

        if (!requestPath.exists()) {
            try {
                requestPath.createNewFile();
            } catch (final IOException e) {
                throw new IllegalStateException("Unable to create file at path `" + requestPath + "`", e);
            }
        }

        final XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();

        final StreamResult result = new StreamResult(requestPath);
        XMLStreamWriter writer;
        try {
            writer = outputFactory.createXMLStreamWriter(result);
        } catch (final XMLStreamException e) {
            throw new IllegalStateException("Unable to create XMLStreamWriter with file at path `" + requestPath + "`",
                    e);
        }

        try {
            writer.writeStartDocument("UTF-8", "1.0");

            writeElement(writer, configuration);

            writer.writeEndDocument();
        } catch (final XMLStreamException e) {
            throw new IllegalStateException("Unable to create XML document in file at path `" + requestPath + "`", e);
        }
    }

    static void writeElement(final XMLStreamWriter writer, final PlexusConfiguration configuration)
            throws XMLStreamException {
        writer.writeStartElement(configuration.getName());

        for (final String attribute : configuration.getAttributeNames()) {
            writer.writeAttribute(attribute, configuration.getAttribute(attribute));
        }

        final String value = configuration.getValue();
        if ((value != null) && !value.isEmpty()) {
            writer.writeCharacters(value);
        }

        for (final PlexusConfiguration child : configuration.getChildren()) {
            writeElement(writer, child);
        }

        writer.writeEndElement();
    }

}
