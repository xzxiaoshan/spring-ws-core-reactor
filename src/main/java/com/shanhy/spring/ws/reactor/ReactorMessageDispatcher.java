package com.shanhy.spring.ws.reactor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.FrameworkServlet;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.support.DefaultStrategiesHelper;
import org.springframework.ws.support.WebUtils;
import org.springframework.ws.transport.WebServiceMessageReceiver;
import org.springframework.ws.transport.http.HttpTransportConstants;
import org.springframework.ws.transport.http.WebServiceMessageReceiverHandlerAdapter;
import org.springframework.ws.transport.http.WsdlDefinitionHandlerAdapter;
import org.springframework.ws.transport.http.XsdSchemaHandlerAdapter;
import org.springframework.ws.wsdl.WsdlDefinition;
import org.springframework.xml.xsd.XsdSchema;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 重写 MessageDispatcher
 *
 * @author shanhy
 * @date 2022-05-10 13:24
 */
public class ReactorMessageDispatcher implements ApplicationContextAware {

    private final Log logger = LogFactory.getLog(getClass());

    private final String webServiceUrl;

    /**
     * Well-known name for the {@link WebServiceMessageFactory} bean in the bean factory for this namespace.
     */
    public static final String DEFAULT_MESSAGE_FACTORY_BEAN_NAME = "messageFactory";

    /**
     * Well-known name for the {@link WebServiceMessageReceiver} object in the bean factory for this namespace.
     */
    public static final String DEFAULT_MESSAGE_RECEIVER_BEAN_NAME = "messageReceiver";

    /**
     * Well-known name for the {@link WebServiceMessageReceiverHandlerAdapter} object in the bean factory for this
     * namespace.
     */
    public static final String DEFAULT_MESSAGE_RECEIVER_HANDLER_ADAPTER_BEAN_NAME =
            "reactorMessageReceiverHandlerAdapter";

    /**
     * Well-known name for the {@link WsdlDefinitionHandlerAdapter} object in the bean factory for this namespace.
     */
    public static final String DEFAULT_WSDL_DEFINITION_HANDLER_ADAPTER_BEAN_NAME =
            "reactorWsdlDefinitionHandlerAdapter";

    /**
     * Well-known name for the {@link XsdSchemaHandlerAdapter} object in the bean factory for this namespace.
     */
    public static final String DEFAULT_XSD_SCHEMA_HANDLER_ADAPTER_BEAN_NAME = "reactorXsdSchemaHandlerAdapter";

    /**
     * Suffix of a WSDL request uri.
     */
    private static final String WSDL_SUFFIX_NAME = ".wsdl";

    /**
     * Suffix of a XSD request uri.
     */
    private static final String XSD_SUFFIX_NAME = ".xsd";

    private final DefaultStrategiesHelper defaultStrategiesHelper;

    private String messageFactoryBeanName = DEFAULT_MESSAGE_FACTORY_BEAN_NAME;

    private String messageReceiverHandlerAdapterBeanName = DEFAULT_MESSAGE_RECEIVER_HANDLER_ADAPTER_BEAN_NAME;

    /**
     * The {@link WebServiceMessageReceiverHandlerAdapter} used by this servlet.
     */
    private ReactorWebServiceMessageReceiverHandlerAdapter reactorMessageReceiverHandlerAdapter;

    private String wsdlDefinitionHandlerAdapterBeanName = DEFAULT_WSDL_DEFINITION_HANDLER_ADAPTER_BEAN_NAME;

    /**
     * The {@link ReactorWsdlDefinitionHandlerAdapter} used by this servlet.
     */
    private ReactorWsdlDefinitionHandlerAdapter reactorWsdlDefinitionHandlerAdapter;

    private String xsdSchemaHandlerAdapterBeanName = DEFAULT_XSD_SCHEMA_HANDLER_ADAPTER_BEAN_NAME;

    /**
     * The {@link XsdSchemaHandlerAdapter} used by this servlet.
     */
    private ReactorXsdSchemaHandlerAdapter reactorXsdSchemaHandlerAdapter;

    private String messageReceiverBeanName = DEFAULT_MESSAGE_RECEIVER_BEAN_NAME;

    /**
     * The {@link WebServiceMessageReceiver} used by this servlet.
     */
    private WebServiceMessageReceiver messageReceiver;

    /**
     * Keys are bean names, values are {@link WsdlDefinition WsdlDefinitions}.
     */
    private Map<String, WsdlDefinition> wsdlDefinitions;

    private Map<String, XsdSchema> xsdSchemas;

    private boolean transformWsdlLocations = false;

    private boolean transformSchemaLocations = false;

    /**
     * Public constructor, necessary for some Web application servers.
     */
    public ReactorMessageDispatcher() {
        // Url默认值/webservice
        this("/webservice");
    }

    /**
     * Constructor to support programmatic configuration of the Servlet with the specified
     * web application context. This constructor is useful in Servlet 3.0+ environments
     * where instance-based registration of servlets is possible through the
     * {@code ServletContext#addServlet} API.
     * <p>Using this constructor indicates that the following properties / init-params
     * will be ignored:
     *
     * <p>The given web application context may or may not yet be {@linkplain
     * org.springframework.web.context.ConfigurableWebApplicationContext#refresh() refreshed}.
     * If it has <strong>not</strong> already been refreshed (the recommended approach), then
     * the following will occur:
     * <ul>
     * <li>If the given context does not already have a {@linkplain
     * org.springframework.web.context.ConfigurableWebApplicationContext#setParent parent},
     * the root application context will be set as the parent.</li>
     * <li>If the given context has not already been assigned an {@linkplain
     * org.springframework.web.context.ConfigurableWebApplicationContext#setId id}, one
     * will be assigned to it</li>
     * <li>{@code ServletContext} and {@code ServletConfig} objects will be delegated to
     * the application context</li>
     * {@link org.springframework.web.context.ConfigurableWebApplicationContext}</li>
     * </ul>
     * If the context has already been refreshed, none of the above will occur, under the
     * assumption that the user has performed these actions (or not) per their specific
     * needs.
     * <p>See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
     *
     * @see FrameworkServlet#FrameworkServlet(WebApplicationContext)
     * @see org.springframework.web.WebApplicationInitializer
     */
    public ReactorMessageDispatcher(String webServiceUrl) {
        this.webServiceUrl = webServiceUrl;
        defaultStrategiesHelper = new DefaultStrategiesHelper(ReactorMessageDispatcher.class);
    }

    /**
     * Returns the bean name used to lookup a {@link WebServiceMessageFactory}.
     */
    public String getMessageFactoryBeanName() {
        return messageFactoryBeanName;
    }

    /**
     * Sets the bean name used to lookup a {@link WebServiceMessageFactory}. Defaults to {@link
     * #DEFAULT_MESSAGE_FACTORY_BEAN_NAME}.
     */
    public void setMessageFactoryBeanName(String messageFactoryBeanName) {
        this.messageFactoryBeanName = messageFactoryBeanName;
    }

    /**
     * Returns the bean name used to lookup a {@link WebServiceMessageReceiver}.
     */
    public String getMessageReceiverBeanName() {
        return messageReceiverBeanName;
    }

    /**
     * Sets the bean name used to lookup a {@link WebServiceMessageReceiver}. Defaults to {@link
     * #DEFAULT_MESSAGE_RECEIVER_BEAN_NAME}.
     */
    public void setMessageReceiverBeanName(String messageReceiverBeanName) {
        this.messageReceiverBeanName = messageReceiverBeanName;
    }

    /**
     * Indicates whether relative address locations in the WSDL are to be transformed using the request URI of the
     * incoming {@link ServerHttpRequest}.
     */
    public boolean isTransformWsdlLocations() {
        return transformWsdlLocations;
    }

    /**
     * Sets whether relative address locations in the WSDL are to be transformed using the request URI of the incoming
     * {@link ServerHttpRequest}. Defaults to {@code false}.
     */
    public void setTransformWsdlLocations(boolean transformWsdlLocations) {
        this.transformWsdlLocations = transformWsdlLocations;
    }

    /**
     * Indicates whether relative address locations in the XSD are to be transformed using the request URI of the
     * incoming {@link ServerHttpRequest}.
     */
    public boolean isTransformSchemaLocations() {
        return transformSchemaLocations;
    }

    /**
     * Sets whether relative address locations in the XSD are to be transformed using the request URI of the incoming
     * {@link ServerHttpRequest}. Defaults to {@code false}.
     */
    public void setTransformSchemaLocations(boolean transformSchemaLocations) {
        this.transformSchemaLocations = transformSchemaLocations;
    }

    /**
     * Returns the bean name used to lookup a {@link WebServiceMessageReceiverHandlerAdapter}.
     */
    public String getMessageReceiverHandlerAdapterBeanName() {
        return messageReceiverHandlerAdapterBeanName;
    }

    /**
     * Sets the bean name used to lookup a {@link WebServiceMessageReceiverHandlerAdapter}. Defaults to {@link
     * #DEFAULT_MESSAGE_RECEIVER_HANDLER_ADAPTER_BEAN_NAME}.
     */
    public void setMessageReceiverHandlerAdapterBeanName(String messageReceiverHandlerAdapterBeanName) {
        this.messageReceiverHandlerAdapterBeanName = messageReceiverHandlerAdapterBeanName;
    }

    /**
     * Returns the bean name used to lookup a {@link WsdlDefinitionHandlerAdapter}.
     */
    public String getWsdlDefinitionHandlerAdapterBeanName() {
        return wsdlDefinitionHandlerAdapterBeanName;
    }

    /**
     * Sets the bean name used to lookup a {@link WsdlDefinitionHandlerAdapter}. Defaults to {@link
     * #DEFAULT_WSDL_DEFINITION_HANDLER_ADAPTER_BEAN_NAME}.
     */
    public void setWsdlDefinitionHandlerAdapterBeanName(String wsdlDefinitionHandlerAdapterBeanName) {
        this.wsdlDefinitionHandlerAdapterBeanName = wsdlDefinitionHandlerAdapterBeanName;
    }

    /**
     * Returns the bean name used to lookup a {@link XsdSchemaHandlerAdapter}.
     */
    public String getXsdSchemaHandlerAdapterBeanName() {
        return xsdSchemaHandlerAdapterBeanName;
    }

    /**
     * Sets the bean name used to lookup a {@link XsdSchemaHandlerAdapter}. Defaults to {@link
     * #DEFAULT_XSD_SCHEMA_HANDLER_ADAPTER_BEAN_NAME}.
     */
    public void setXsdSchemaHandlerAdapterBeanName(String xsdSchemaHandlerAdapterBeanName) {
        this.xsdSchemaHandlerAdapterBeanName = xsdSchemaHandlerAdapterBeanName;
    }

    public Mono<Void> doService(ServerHttpRequest request, ServerHttpResponse response)
            throws Exception {
        WsdlDefinition definition = getWsdlDefinition(request);
        if (definition != null) {
            return reactorWsdlDefinitionHandlerAdapter.handle(request, response, definition);
        }
        XsdSchema schema = getXsdSchema(request);
        if (schema != null) {
            return reactorXsdSchemaHandlerAdapter.handle(request, response, schema);
        }
        return reactorMessageReceiverHandlerAdapter.handle(request, response, messageReceiver);
    }

    /**
     * This implementation calls {@link #initStrategies}.
     */
    protected void onRefresh(ApplicationContext context) {
        initStrategies(context);
    }

    protected long getLastModified(ServerHttpRequest request) {
        WsdlDefinition definition = getWsdlDefinition(request);
        if (definition != null) {
            return reactorWsdlDefinitionHandlerAdapter.getLastModified(request, definition);
        }
        XsdSchema schema = getXsdSchema(request);
        if (schema != null) {
            return reactorXsdSchemaHandlerAdapter.getLastModified(request, schema);
        }
        return reactorMessageReceiverHandlerAdapter.getLastModified(request, messageReceiver);
    }

    /**
     * Returns the {@link WebServiceMessageReceiver} used by this servlet.
     */
    protected WebServiceMessageReceiver getMessageReceiver() {
        return messageReceiver;
    }

    /**
     * Determines the {@link WsdlDefinition} for a given request, or {@code null} if none is found.
     *
     * <p>Default implementation checks whether the request method is {@code GET}, whether the request uri ends with
     * {@code ".wsdl"}, and if there is a {@code WsdlDefinition} with the same name as the filename in the
     * request uri.
     *
     * @param request the {@code ServerHttpRequest}
     * @return a definition, or {@code null}
     */
    protected WsdlDefinition getWsdlDefinition(ServerHttpRequest request) {
        if (HttpTransportConstants.METHOD_GET.equals(request.getMethodValue()) &&
                request.getURI().getPath().endsWith(WSDL_SUFFIX_NAME)) {
            String fileName = WebUtils.extractFilenameFromUrlPath(request.getURI().getPath());
            return wsdlDefinitions.get(fileName);
        } else {
            return null;
        }
    }

    /**
     * Determines the {@link XsdSchema} for a given request, or {@code null} if none is found.
     *
     * <p>Default implementation checks whether the request method is {@code GET}, whether the request uri ends with
     * {@code ".xsd"}, and if there is a {@code XsdSchema} with the same name as the filename in the request
     * uri.
     *
     * @param request the {@code ServerHttpRequest}
     * @return a schema, or {@code null}
     */
    protected XsdSchema getXsdSchema(ServerHttpRequest request) {
        if (HttpTransportConstants.METHOD_GET.equals(request.getMethodValue()) &&
                request.getURI().getPath().endsWith(XSD_SUFFIX_NAME)) {
            String fileName = WebUtils.extractFilenameFromUrlPath(request.getURI().getPath());
            return xsdSchemas.get(fileName);
        } else {
            return null;
        }
    }

    /**
     * Initialize the strategy objects that this servlet uses.
     * <p>May be overridden in subclasses in order to initialize further strategy objects.
     */
    protected void initStrategies(ApplicationContext context) {
        initMessageReceiverHandlerAdapter(context);
        initWsdlDefinitionHandlerAdapter(context);
        initXsdSchemaHandlerAdapter(context);
        initMessageReceiver(context);
        initWsdlDefinitions(context);
        initXsdSchemas(context);
    }


    private void initMessageReceiverHandlerAdapter(ApplicationContext context) {
        try {
            try {
                reactorMessageReceiverHandlerAdapter = context.getBean(getMessageReceiverHandlerAdapterBeanName(),
                        ReactorWebServiceMessageReceiverHandlerAdapter.class);
            } catch (NoSuchBeanDefinitionException ignored) {
                reactorMessageReceiverHandlerAdapter = new ReactorWebServiceMessageReceiverHandlerAdapter();
            }
            initWebServiceMessageFactory(context);
            reactorMessageReceiverHandlerAdapter.afterPropertiesSet();
        } catch (Exception ex) {
            throw new BeanInitializationException("Could not initialize WebServiceMessageReceiverHandlerAdapter", ex);
        }
    }

    private void initWebServiceMessageFactory(ApplicationContext context) {
        WebServiceMessageFactory messageFactory;
        try {
            messageFactory = context.getBean(getMessageFactoryBeanName(), WebServiceMessageFactory.class);
        } catch (NoSuchBeanDefinitionException ignored) {
            messageFactory = defaultStrategiesHelper
                    .getDefaultStrategy(WebServiceMessageFactory.class, context);
            if (logger.isDebugEnabled()) {
                logger.debug("No WebServiceMessageFactory found in servlet '" + getBeanName() + "': using default");
            }
        }
        reactorMessageReceiverHandlerAdapter.setMessageFactory(messageFactory);
    }

    private void initWsdlDefinitionHandlerAdapter(ApplicationContext context) {
        try {
            try {
                reactorWsdlDefinitionHandlerAdapter =
                        context.getBean(getWsdlDefinitionHandlerAdapterBeanName(),
                                ReactorWsdlDefinitionHandlerAdapter.class);

            } catch (NoSuchBeanDefinitionException ignored) {
                reactorWsdlDefinitionHandlerAdapter = new ReactorWsdlDefinitionHandlerAdapter();
            }
            reactorWsdlDefinitionHandlerAdapter.setTransformLocations(isTransformWsdlLocations());
            reactorWsdlDefinitionHandlerAdapter.setTransformSchemaLocations(isTransformSchemaLocations());
            reactorWsdlDefinitionHandlerAdapter.afterPropertiesSet();
        } catch (Exception ex) {
            throw new BeanInitializationException("Could not initialize WsdlDefinitionHandlerAdapter", ex);
        }
    }

    private void initXsdSchemaHandlerAdapter(ApplicationContext context) {
        try {
            try {
                reactorXsdSchemaHandlerAdapter = context
                        .getBean(getXsdSchemaHandlerAdapterBeanName(), ReactorXsdSchemaHandlerAdapter.class);

            } catch (NoSuchBeanDefinitionException ignored) {
                reactorXsdSchemaHandlerAdapter = new ReactorXsdSchemaHandlerAdapter();
            }
            reactorXsdSchemaHandlerAdapter.setTransformSchemaLocations(isTransformSchemaLocations());
            reactorXsdSchemaHandlerAdapter.afterPropertiesSet();
        } catch (Exception ex) {
            throw new BeanInitializationException("Could not initialize XsdSchemaHandlerAdapter", ex);
        }
    }

    private void initMessageReceiver(ApplicationContext context) {
        try {
            messageReceiver = context.getBean(getMessageReceiverBeanName(), WebServiceMessageReceiver.class);
        } catch (NoSuchBeanDefinitionException ex) {
            messageReceiver = defaultStrategiesHelper
                    .getDefaultStrategy(WebServiceMessageReceiver.class, context);
            if (messageReceiver instanceof BeanNameAware) {
                ((BeanNameAware) messageReceiver).setBeanName(getBeanName());
            }
            if (logger.isDebugEnabled()) {
                logger.debug("No MessageDispatcher found in servlet '" + getBeanName() + "': using default");
            }
        }
    }

    private void initWsdlDefinitions(ApplicationContext context) {
        wsdlDefinitions = BeanFactoryUtils
                .beansOfTypeIncludingAncestors(context, WsdlDefinition.class, true, false);
        if (logger.isDebugEnabled()) {
            for (Map.Entry<String, WsdlDefinition> entry : wsdlDefinitions.entrySet()) {
                String beanName = entry.getKey();
                WsdlDefinition definition = entry.getValue();
                logger.debug("Published [" + definition + "] as " + beanName + WSDL_SUFFIX_NAME);
            }
        }
    }

    private void initXsdSchemas(ApplicationContext context) {
        xsdSchemas = BeanFactoryUtils
                .beansOfTypeIncludingAncestors(context, XsdSchema.class, true, false);
        if (logger.isDebugEnabled()) {
            for (Map.Entry<String, XsdSchema> entry : xsdSchemas.entrySet()) {
                String beanName = entry.getKey();
                XsdSchema schema = entry.getValue();
                logger.debug("Published [" + schema + "] as " + beanName + XSD_SUFFIX_NAME);
            }
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        onRefresh(applicationContext);
    }

    private String getBeanName() {
        String clsName = ClassUtils.getShortName(ReactorMessageDispatcher.class);
        clsName = String.valueOf(clsName.charAt(0)).toLowerCase().concat(clsName.substring(1));
        return clsName;
    }

}
