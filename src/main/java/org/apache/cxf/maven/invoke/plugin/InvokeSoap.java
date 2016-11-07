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
import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.handler.MessageContext;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;

import org.xml.sax.SAXException;

import org.apache.cxf.feature.LoggingFeature;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static org.apache.cxf.maven.invoke.plugin.XmlUtil.document;

/**
 * Invoke SOAP service.
 */
@Mojo(name = "invoke-soap", defaultPhase = LifecyclePhase.NONE)
public final class InvokeSoap extends AbstractMojo {

    @Parameter(property = "cxf.invoke.endpoint", required = false)
    private String endpoint;

    @Parameter(property = "cxf.invoke.headers", required = false)
    private XmlString[] headers;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    private MojoExecution mojoExecution;

    @Parameter(property = "cxf.invoke.namespace", required = true)
    private String namespace;

    @Parameter(property = "cxf.invoke.operation", required = true)
    private String operation;

    @Component
    private MavenPluginManager pluginManager;

    @Parameter(property = "cxf.invoke.port", required = false)
    private String portName;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    @Parameter(property = "cxf.invoke.properties")
    private final Map<String, String> properties = new HashMap<>();

    @Parameter(property = "cxf.invoke.repeatInterval", required = false, defaultValue = "5000")
    private int repeatInterval;

    @Parameter(property = "cxf.invoke.repeatUntil", required = false)
    private String repeatUntil;

    @Parameter(property = "cxf.invoke.request", required = true)
    private XmlString[] request;

    @Parameter(property = "cxf.invoke.request.path", required = true, defaultValue = "${project.build.directory}")
    private File requestPath;

    @Parameter(property = "cxf.invoke.service", required = true)
    private String serviceName;

    private Transformer transformer;

    @Parameter(property = "cxf.invoke.wsdl", required = true)
    private URI wsdl;

    public InvokeSoap() {
        try {
            final TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformer = transformerFactory.newTransformer();
        } catch (TransformerConfigurationException | TransformerFactoryConfigurationError e) {
            throw new IllegalStateException("Unable to use JAXP API", e);
        }
    }

    static Source createRequest(final String request) throws IOException, SAXException {
        return new DOMSource(XmlUtil.parse(request));
    }

    @Override
    public void execute() throws MojoExecutionException {
        boolean first = true;
        Document response;
        do {
            if (!first) {
                try {
                    Thread.sleep(repeatInterval);
                } catch (final InterruptedException e) {
                    return;
                }
            }
            first = false;

            response = invokeService();

            extractProperties(response);
        } while (shouldRepeat(response));
    }

    QName determinePort(final Service service) throws MojoExecutionException {
        if (portName != null) {
            return new QName(namespace, portName);
        }

        final Iterator<QName> ports = service.getPorts();

        if (ports.hasNext()) {
            final QName port = ports.next();

            if (ports.hasNext()) {
                throw new MojoExecutionException("Found more than one port type defined in specified WSDL `" + wsdl
                        + "`, please specify which one to use with `portName` configuration option");
            }
            return port;
        } else {
            throw new MojoExecutionException(
                    "Given WSDL `" + wsdl + "` does not specify any port types, please specify one to use with "
                            + "`portName` configuration option");
        }
    }

    void extractProperties(final Document response) throws MojoExecutionException {
        if (properties.isEmpty()) {
            return;
        }

        final Map<String, Object> values = properties.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> {
                    try {
                        final XPathExpression expression = XmlUtil.xpathExpression(e.getValue());

                        return expression.evaluate(response, XPathConstants.STRING);
                    } catch (final XPathExpressionException ex) {
                        throw new IllegalArgumentException("Unable to get property " + e.getKey()
                                + " from XML using XPath expression `" + e.getValue() + "`", ex);
                    }
                }));

        final Properties projectProperties = project.getProperties();
        projectProperties.putAll(values);
    }

    Document invokeService() throws MojoExecutionException {
        final Service service;
        try {
            if (getLog().isDebugEnabled()) {
                service = Service.create(wsdl.toURL(), new QName(namespace, serviceName), new LoggingFeature());
            } else {
                service = Service.create(wsdl.toURL(), new QName(namespace, serviceName));
            }
        } catch (final MalformedURLException e) {
            throw new MojoExecutionException("Unable to convert `" + wsdl + "` to URL", e);
        }

        if (headers != null) {
            service.setHandlerResolver(new HeadersHandlerResolver(headers));
        }

        final QName port = determinePort(service);

        final Dispatch<Source> dispatch = service.createDispatch(port, Source.class, Service.Mode.PAYLOAD);

        final File executionDir = new File(requestPath, mojoExecution.getExecutionId());
        if (!executionDir.exists()) {
            executionDir.mkdirs();
        }

        final Source soapRequest;
        try {
            soapRequest = createRequest(request[0].getValue());
        } catch (final IOException | SAXException e) {
            throw new MojoExecutionException("Unable to process request", e);
        }

        final File requestFile = new File(executionDir, "request.xml");
        try {

            transformer.transform(soapRequest, new StreamResult(requestFile));
        } catch (final TransformerException e) {
            throw new MojoExecutionException("Unable to store request XML to file `" + requestFile + "`", e);
        }

        final Map<String, Object> requestContext = dispatch.getRequestContext();
        requestContext.put(MessageContext.WSDL_OPERATION, new QName(namespace, operation));

        if (endpoint != null) {
            requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint);
        }

        final Source soapResponse = dispatch.invoke(soapRequest);

        final Document soapResponseDocument = document();
        try {
            transformer.transform(soapResponse, new DOMResult(soapResponseDocument));
        } catch (final TransformerException e) {
            throw new MojoExecutionException("Unable to transform response source XML to DOM document", e);
        }

        final File responseFile = new File(executionDir, "response.xml");
        try {

            transformer.transform(new DOMSource(soapResponseDocument), new StreamResult(responseFile));
        } catch (final TransformerException e) {
            throw new MojoExecutionException("Unable to store request XML to file `" + requestFile + "`", e);
        }

        return soapResponseDocument;
    }

    boolean shouldRepeat(final Document response) throws MojoExecutionException {
        if (repeatUntil == null) {
            return false;
        }

        try {
            return (boolean) XmlUtil.xpathExpression(repeatUntil).evaluate(response, XPathConstants.BOOLEAN);
        } catch (final XPathExpressionException e) {
            throw new MojoExecutionException("Unable to evaluate repeatUntil XPath expression `" + repeatUntil + "`",
                    e);
        }
    }
}
