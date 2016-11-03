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
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.handler.MessageContext;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.configuration.PlexusConfiguration;

/**
 * Invoke SOAP service.
 */
@Mojo(name = "invoke-soap", defaultPhase = LifecyclePhase.NONE)
public final class InvokeSoap extends AbstractMojo {

    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    private MojoExecution mojoExecution;

    @Parameter(property = "cxf.invoke.namespace", required = true)
    private String namespace;

    @Parameter(property = "cxf.invoke.operation", required = true)
    private String operation;

    @Parameter(property = "cxf.invoke.port", required = true)
    private String portName;

    @Parameter(property = "cxf.invoke.request", required = true)
    private PlexusConfiguration request;

    @Parameter(property = "cxf.invoke.request.path", required = true, defaultValue = "${project.build.directory}")
    private File requestPath;

    @Parameter(property = "cxf.invoke.service", required = true)
    private String serviceName;

    private Transformer transformer;

    @Parameter(property = "cxf.invoke.wsdl", required = true)
    private URI wsdl;

    public InvokeSoap() {
        try {
            transformer = TransformerFactory.newInstance().newTransformer();
        } catch (TransformerConfigurationException | TransformerFactoryConfigurationError e) {
            throw new IllegalStateException("Unable to use JAXP API", e);
        }
    }

    static Source createRequest(final PlexusConfiguration configuration, final File file) throws XMLStreamException {
        final PlexusConfiguration soapRequest = configuration.getChild(0);

        PlexusConfigurationWriter.writeConfigurationTo(soapRequest, file);

        return new StreamSource(file);
    }

    @Override
    public void execute() throws MojoExecutionException {
        final Service service;
        try {
            service = Service.create(wsdl.toURL(), new QName(namespace, serviceName));
        } catch (final MalformedURLException e) {
            throw new MojoExecutionException("Unable to convert `" + wsdl + "` to URL", e);
        }

        final Dispatch<Source> dispatch = service.createDispatch(new QName(namespace, portName), Source.class,
                Service.Mode.PAYLOAD);

        final File executionDir = new File(requestPath, mojoExecution.getExecutionId());

        Source soapRequest;
        final File requestFile = new File(executionDir, "request.xml");
        try {
            soapRequest = createRequest(request, requestFile);
        } catch (final XMLStreamException e) {
            throw new MojoExecutionException("Unable to process request", e);
        }

        log("SOAP request", requestFile);

        dispatch.getRequestContext().put(MessageContext.WSDL_OPERATION, new QName(namespace, operation));

        final Source soapResponse = dispatch.invoke(soapRequest);

        final File responseFile = new File(executionDir, "response.xml");
        try {
            transformer.transform(soapResponse, new StreamResult(responseFile));
        } catch (final TransformerException e) {
            throw new MojoExecutionException(
                    "Unable to serialise SOAP response `" + soapResponse + "` to file `" + responseFile + "`", e);
        }

        log("SOAP response", responseFile);
    }

    private void log(final String type, final File source) throws MojoExecutionException {
        final Log log = getLog();
        if (log.isDebugEnabled()) {
            final StringWriter out = new StringWriter();
            out.write(type);
            out.write(": ");

            try {
                Files.lines(Paths.get(source.getCanonicalPath())).forEach(out::write);
            } catch (final IOException e) {
                throw new MojoExecutionException("Unable to serialise " + type + " for log", e);
            }

            log.debug(out.toString());
        }
    }
}
