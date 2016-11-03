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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class PlexusConfigurationWriterTest {

    @Rule
    public TemporaryFolder output = new TemporaryFolder();

    @Test
    public void shouldWalkFromRootAndAcrossChildrensSiblings() throws IOException {
        final PlexusConfiguration root = configuration("root");
        final DefaultPlexusConfiguration child1 = configuration("child1");
        final DefaultPlexusConfiguration child2 = configuration("child2");
        final DefaultPlexusConfiguration child3 = configuration("child3");
        final DefaultPlexusConfiguration grandson11 = configuration("grandson11", "11");
        final DefaultPlexusConfiguration grandson21 = configuration("grandson21", "21");
        final DefaultPlexusConfiguration grandson22 = configuration("grandson22", "22", "a22", "v22");
        final DefaultPlexusConfiguration grandson23 = configuration("grandson23", "23");

        root.addChild(child1);
        child1.addChild(grandson11);

        root.addChild(child2);
        child2.addChild(grandson21);
        child2.addChild(grandson22);
        child2.addChild(grandson23);

        root.addChild(child3);

        final File outputFile = output.newFile();

        PlexusConfigurationWriter.writeConfigurationTo(root, outputFile);

        final List<String> lines = Files.readAllLines(Paths.get(outputFile.getCanonicalPath()));

        assertThat(lines).containsExactly("<?xml version='1.0' encoding='UTF-8'?>"//
                + "<root>"//
                + "<child1><grandson11>11</grandson11>"//
                + "</child1>"//
                + "<child2>"//
                + "<grandson21>21</grandson21>"//
                + "<grandson22 a22=\"v22\">22</grandson22>"//
                + "<grandson23>23</grandson23>"//
                + "</child2>"//
                + "<child3/></root>");
    }

    private DefaultPlexusConfiguration configuration(final String name) {
        return new DefaultPlexusConfiguration(name) {
            @Override
            public String toString() {
                return name;
            }
        };
    }

    private DefaultPlexusConfiguration configuration(final String name, final String value,
            final String... attributes) {
        final DefaultPlexusConfiguration configuration = configuration(name);

        configuration.setValue(value);

        for (int i = 0; i < attributes.length; i++) {
            configuration.setAttribute(attributes[i], attributes[++i]);
        }

        return configuration;
    }
}
