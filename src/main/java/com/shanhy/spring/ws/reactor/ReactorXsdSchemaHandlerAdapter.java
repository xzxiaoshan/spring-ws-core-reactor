package com.shanhy.spring.ws.reactor;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.ws.transport.http.HttpTransportConstants;
import org.springframework.xml.xpath.XPathExpression;
import org.springframework.xml.xpath.XPathExpressionFactory;
import org.springframework.xml.xsd.XsdSchema;
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
 * 重写 XsdSchemaHandlerAdapter
 *
 * @author shanhy
 * @date 2022-05-10 14:18
 */
public class ReactorXsdSchemaHandlerAdapter extends ReactorLocationTransformerObjectSupport
        implements ReactorHandlerAdapter, InitializingBean {

    /**
     * Default XPath expression used for extracting all {@code schemaLocation} attributes from the WSDL definition.
     */
    public static final String DEFAULT_SCHEMA_LOCATION_EXPRESSION = "//@schemaLocation";

    private static final String CONTENT_TYPE = "text/xml";

    private Map<String, String> expressionNamespaces = new HashMap<String, String>();

    private String schemaLocationExpression = DEFAULT_SCHEMA_LOCATION_EXPRESSION;

    private XPathExpression schemaLocationXPathExpression;

    private boolean transformSchemaLocations = false;

    /**
     * Sets the XPath expression used for extracting the {@code schemaLocation} attributes from the WSDL 1.1 definition.
     *
     * <p>Defaults to {@code DEFAULT_SCHEMA_LOCATION_EXPRESSION}.
     */
    public void setSchemaLocationExpression(String schemaLocationExpression) {
        this.schemaLocationExpression = schemaLocationExpression;
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
        Source schemaSource = ((XsdSchema) handler).getSource();
        return LastModifiedHelper.getLastModified(schemaSource);
    }

    @Override
    public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response, Object handler)
            throws Exception {
        if (HttpTransportConstants.METHOD_GET.equals(request.getMethodValue())) {
            Transformer transformer = createTransformer();
            Source schemaSource = getSchemaSource((XsdSchema) handler);

            if (transformSchemaLocations) {
                DOMResult domResult = new DOMResult();
                transformer.transform(schemaSource, domResult);
                Document schemaDocument = (Document) domResult.getNode();
                transformSchemaLocations(schemaDocument, request);
                schemaSource = new DOMSource(schemaDocument);
            }

            response.getHeaders().setContentType(MediaType.parseMediaType(CONTENT_TYPE));

            DataBuffer dataBuffer = response.bufferFactory().allocateBuffer();
            StreamResult responseResult = new StreamResult(dataBuffer.asOutputStream()); // reactor 方式
            transformer.transform(schemaSource, responseResult);

            return response.writeWith(Mono.just(dataBuffer));
        } else {
            response.setStatusCode(HttpStatus.METHOD_NOT_ALLOWED);
        }
        return Mono.empty();
    }

    @Override
    public boolean supports(Object handler) {
        return handler instanceof XsdSchema;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        schemaLocationXPathExpression =
                XPathExpressionFactory.createXPathExpression(schemaLocationExpression, expressionNamespaces);
    }

    /**
     * Returns the {@link Source} of the given schema. Allows for post-processing and transformation of the schema in
     * sub-classes.
     *
     * <p>Default implementation simply returns {@link XsdSchema#getSource()}.
     *
     * @param schema the schema
     * @return the source of the given schema
     * @throws Exception in case of errors
     */
    protected Source getSchemaSource(XsdSchema schema) throws Exception {
        return schema.getSource();
    }

    /**
     * Transforms all {@code schemaLocation} attributes to reflect the server name given {@code ServerHttpRequest}.
     * Determines the suitable attributes by evaluating the defined XPath expression, and delegates to {@code
     * transformLocation} to do the transformation for all attributes that match.
     *
     * <p>This method is only called when the {@code transformSchemaLocations} property is true.
     *
     * @see #setSchemaLocationExpression(String)
     */
    protected void transformSchemaLocations(Document definitionDocument, ServerHttpRequest request) throws Exception {
        transformLocations(schemaLocationXPathExpression, definitionDocument, request);
    }


}