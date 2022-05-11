package com.shanhy.spring.ws.reactor;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.ws.transport.http.HttpTransportConstants;
import org.springframework.ws.wsdl.WsdlDefinition;
import org.springframework.xml.xpath.XPathExpression;
import org.springframework.xml.xpath.XPathExpressionFactory;
import org.w3c.dom.Document;
import reactor.core.publisher.Mono;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.util.HashMap;
import java.util.Map;

/**
 * Reactor 模式的 WsdlDefinitionHandlerAdapter 重写
 *
 * @author shanhy
 * @date 2022-05-10 13:02
 */
public class ReactorWsdlDefinitionHandlerAdapter extends ReactorLocationTransformerObjectSupport implements ReactorHandlerAdapter, InitializingBean {

    /**
     * Default XPath expression used for extracting all {@code location} attributes from the WSDL definition.
     */
    public static final String DEFAULT_LOCATION_EXPRESSION = "//@location";

    /**
     * Default XPath expression used for extracting all {@code schemaLocation} attributes from the WSDL definition.
     */
    public static final String DEFAULT_SCHEMA_LOCATION_EXPRESSION = "//@schemaLocation";

    private static final String CONTENT_TYPE = "text/xml";

    private Map<String, String> expressionNamespaces = new HashMap<String, String>();

    private String locationExpression = DEFAULT_LOCATION_EXPRESSION;

    private String schemaLocationExpression = DEFAULT_SCHEMA_LOCATION_EXPRESSION;

    private XPathExpression locationXPathExpression;

    private XPathExpression schemaLocationXPathExpression;

    private boolean transformLocations = false;

    private boolean transformSchemaLocations = false;

    /**
     * Sets the XPath expression used for extracting the {@code location} attributes from the WSDL 1.1 definition.
     *
     * <p>Defaults to {@code DEFAULT_LOCATION_EXPRESSION}.
     */
    public void setLocationExpression(String locationExpression) {
        this.locationExpression = locationExpression;
    }

    /**
     * Sets the XPath expression used for extracting the {@code schemaLocation} attributes from the WSDL 1.1 definition.
     *
     * <p>Defaults to {@code DEFAULT_SCHEMA_LOCATION_EXPRESSION}.
     */
    public void setSchemaLocationExpression(String schemaLocationExpression) {
        this.schemaLocationExpression = schemaLocationExpression;
    }

    /**
     * Sets whether relative address locations in the WSDL are to be transformed using the request URI of the incoming
     * {@code ServerHttpRequest}. Defaults to {@code false}.
     */
    public void setTransformLocations(boolean transformLocations) {
        this.transformLocations = transformLocations;
    }

    /**
     * Sets whether relative address schema locations in the WSDL are to be transformed using the request URI of the
     * incoming {@code ServerHttpRequest}. Defaults to {@code false}.
     */
    public void setTransformSchemaLocations(boolean transformSchemaLocations) {
        this.transformSchemaLocations = transformSchemaLocations;
    }

    @Override
    public long getLastModified(ServerHttpRequest request, Object handler) {
        Source definitionSource = ((WsdlDefinition) handler).getSource();
        return LastModifiedHelper.getLastModified(definitionSource);
    }

    @Override
    public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response, Object handler)
            throws Exception {
        if (HttpTransportConstants.METHOD_GET.equals(request.getMethodValue())) {
            WsdlDefinition definition = (WsdlDefinition) handler;

            Transformer transformer = createTransformer();
            Source definitionSource = definition.getSource();

            if (transformLocations || transformSchemaLocations) {
                DOMResult domResult = new DOMResult();
                transformer.transform(definitionSource, domResult);
                Document definitionDocument = (Document) domResult.getNode();
                if (transformLocations) {
                    transformLocations(definitionDocument, request);
                }
                if (transformSchemaLocations) {
                    transformSchemaLocations(definitionDocument, request);
                }
                definitionSource = new DOMSource(definitionDocument);
            }
            response.getHeaders().setContentType(MediaType.parseMediaType(CONTENT_TYPE));

            DataBuffer dataBuffer = response.bufferFactory().allocateBuffer();
            StreamResult responseResult = new StreamResult(dataBuffer.asOutputStream()); // reactor 方式
            transformer.transform(definitionSource, responseResult);

            return response.writeWith(Mono.just(dataBuffer));
        } else {
            response.setStatusCode(HttpStatus.METHOD_NOT_ALLOWED);
        }
        return Mono.empty();
    }

    @Override
    public boolean supports(Object handler) {
        return handler instanceof WsdlDefinition;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        locationXPathExpression =
                XPathExpressionFactory.createXPathExpression(locationExpression, expressionNamespaces);
        schemaLocationXPathExpression =
                XPathExpressionFactory.createXPathExpression(schemaLocationExpression, expressionNamespaces);
    }

    /**
     * Transforms all {@code location} attributes to reflect the server name given {@code ServerHttpRequest}.
     * Determines the suitable attributes by evaluating the defined XPath expression, and delegates to
     * {@code transformLocation} to do the transformation for all attributes that match.
     *
     * <p>This method is only called when the {@code transformLocations} property is true.
     *
     * @see #setLocationExpression(String)
     * @see #setTransformLocations(boolean)
     * @see #transformLocation(String, ServerHttpRequest)
     */
    protected void transformLocations(Document definitionDocument, ServerHttpRequest request) throws Exception {
        transformLocations(locationXPathExpression, definitionDocument, request);
    }

    /**
     * Transforms all {@code schemaLocation} attributes to reflect the server name given {@code ServerHttpRequest}.
     * Determines the suitable attributes by evaluating the defined XPath expression, and delegates to
     * {@code transformLocation} to do the transformation for all attributes that match.
     *
     * <p>This method is only called when the {@code transformSchemaLocations} property is true.
     *
     * @see #setSchemaLocationExpression(String)
     * @see #setTransformSchemaLocations(boolean)
     * @see #transformLocation(String, ServerHttpRequest)
     */
    protected void transformSchemaLocations(Document definitionDocument, ServerHttpRequest request) throws Exception {
        transformLocations(schemaLocationXPathExpression, definitionDocument, request);
    }

}
