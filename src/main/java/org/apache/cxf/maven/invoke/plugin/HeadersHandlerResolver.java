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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.HandlerResolver;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.PortInfo;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import org.w3c.dom.Node;

import org.xml.sax.SAXException;

final class HeadersHandlerResolver implements HandlerResolver {

    public class HeaderHandler implements SOAPHandler<SOAPMessageContext> {

        @Override
        public void close(final MessageContext context) {
            // noop
        }

        @Override
        public Set<QName> getHeaders() {
            return Collections.emptySet();
        }

        @Override
        public boolean handleFault(final SOAPMessageContext context) {
            return true;
        }

        @Override
        public boolean handleMessage(final SOAPMessageContext context) {
            if ((headers == null) || (headers.length == 0)) {
                return true;
            }

            final SOAPMessage soapMessage = context.getMessage();
            final SOAPPart soapPart = soapMessage.getSOAPPart();
            final SOAPHeader soapHeader;
            try {
                final SOAPEnvelope soapEnvelope = soapPart.getEnvelope();

                soapHeader = Optional.ofNullable(soapEnvelope.getHeader()).orElseGet(() -> {
                    try {
                        return soapEnvelope.addHeader();
                    } catch (final SOAPException e) {
                        throw new IllegalStateException("Unable to add SOAP header", e);
                    }
                });
            } catch (final SOAPException e) {
                throw new IllegalStateException("Unable to create SOAP header", e);
            }

            for (final XmlString header : headers) {
                Node node;
                try {
                    node = XmlUtil.parse(header.getValue());
                } catch (SAXException | IOException e) {
                    throw new IllegalArgumentException("Unable to parse headers XML: `" + headers + "`", e);
                }

                final Node headersNode = soapHeader.getOwnerDocument().importNode(node, true);

                soapHeader.appendChild(headersNode);
            }

            return true;
        }

    }

    private final XmlString[] headers;

    HeadersHandlerResolver(final XmlString[] headers) {
        this.headers = headers;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<Handler> getHandlerChain(final PortInfo portInfo) {
        return Arrays.asList(new HeaderHandler());
    }

}
