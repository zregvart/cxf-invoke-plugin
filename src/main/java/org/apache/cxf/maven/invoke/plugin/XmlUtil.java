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

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public final class XmlUtil {

    private static final DocumentBuilder DOCUMENT_BUILDER;

    private static final XPath XPATH;

    static {
        final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        try {
            DOCUMENT_BUILDER = documentBuilderFactory.newDocumentBuilder();
        } catch (final ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }

        final XPathFactory xPathFactory = XPathFactory.newInstance();
        XPATH = xPathFactory.newXPath();
    }

    private XmlUtil() {
        // utility class
    }

    public static Node parse(final String xml) throws SAXException, IOException {
        final InputSource inputSource = new InputSource();
        inputSource.setCharacterStream(new StringReader(xml));

        final Document document = DOCUMENT_BUILDER.parse(inputSource);

        return document.getDocumentElement();
    }

    public static Source sourceFrom(final String xml) {
        return new StreamSource(new StringReader(xml));
    }

    public static XPathExpression xpathExpression(final String expression) throws XPathExpressionException {
        return XPATH.compile(expression);
    }

    static Document document() {
        return DOCUMENT_BUILDER.newDocument();
    }
}
