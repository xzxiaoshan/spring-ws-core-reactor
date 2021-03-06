package com.shanhy.spring.ws.reactor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.xml.transform.TransformerObjectSupport;
import org.springframework.xml.xpath.XPathExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.util.List;

/**
 * 基于 Reactor 模式的重构，重写 LocationTransformerObjectSupport
 *
 * @author shanhy
 * @date 2022-05-10 12:49
 */
public abstract class ReactorLocationTransformerObjectSupport extends TransformerObjectSupport {

    /**
     * Logger available to subclasses.
     */
    private final Log logger = LogFactory.getLog(getClass());

    /**
     * Transforms the locations of the given definition document using the given XPath expression.
     *
     * @param xPathExpression the XPath expression
     * @param definitionDocument the definition document
     * @param request the request, used to determine the location to transform to
     */
    protected void transformLocations(XPathExpression xPathExpression,
                                      Document definitionDocument,
                                      ServerHttpRequest request) {
        Assert.notNull(xPathExpression, "'xPathExpression' must not be null");
        Assert.notNull(definitionDocument, "'definitionDocument' must not be null");
        Assert.notNull(request, "'request' must not be null");

        List<Node> locationNodes = xPathExpression.evaluateAsNodeList(definitionDocument);
        for (Node locationNode : locationNodes) {
            if (locationNode instanceof Attr) {
                Attr location = (Attr) locationNode;
                if (StringUtils.hasLength(location.getValue())) {
                    String newLocation = transformLocation(location.getValue(), request);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Transforming [" + location.getValue() + "] to [" + newLocation + "]");
                    }
                    location.setValue(newLocation);
                }
            }
        }
    }

    /**
     * Transform the given location string to reflect the given request. If the given location is a full url, the
     * scheme, server name, and port are changed. If it is a relative url, the scheme, server name, and port are
     * prepended. Can be overridden in subclasses to change this behavior.
     *
     * <p>For instance, if the location attribute defined in the WSDL is {@code http://localhost:8080/context
     * /services/myService},
     * and the request URI for the WSDL is {@code http://example.com:80/context/myService.wsdl}, the location
     * will be changed to {@code http://example.com:80/context/services/myService}.
     *
     * <p>If the location attribute defined in the WSDL is {@code /services/myService}, and the request URI for the
     * WSDL is {@code http://example.com:8080/context/myService.wsdl}, the location will be changed to
     * {@code http://example.com:8080/context/services/myService}.
     *
     * <p>This method is only called when the {@code transformLocations} property is true.
     */
    protected String transformLocation(String location, ServerHttpRequest request) {

        StringBuilder url = new StringBuilder(request.getURI().getScheme());
        url.append("://").append(request.getURI().getHost()).append(':').append(request.getURI().getPort());
        if (location.startsWith("/")) {
            // a relative path, prepend the context path
            url.append(request.getPath().contextPath().value()).append(location);
            return url.toString();
        } else {
            int idx = location.indexOf("://");
            if (idx != -1) {
                // a full url
                idx = location.indexOf('/', idx + 3);
                if (idx != -1) {
                    String path = location.substring(idx);
                    url.append(path);
                    return url.toString();
                }
            }
        }
        // unknown location, return the original
        return location;
    }
}
