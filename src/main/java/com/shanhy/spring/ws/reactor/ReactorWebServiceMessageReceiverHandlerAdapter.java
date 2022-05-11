package com.shanhy.spring.ws.reactor;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.ws.InvalidXmlException;
import org.springframework.ws.transport.WebServiceMessageReceiver;
import org.springframework.ws.transport.http.HttpTransportConstants;
import org.springframework.ws.transport.support.WebServiceMessageReceiverObjectSupport;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 重写 WebServiceMessageReceiverHandlerAdapter
 *
 * @author shanhy
 * @date 2022-05-10 13:54
 */
public class ReactorWebServiceMessageReceiverHandlerAdapter extends WebServiceMessageReceiverObjectSupport
        implements ReactorHandlerAdapter {

    @Override
    public long getLastModified(ServerHttpRequest request, Object handler) {
        return -1L;
    }

    @Override
    public Mono<Void> handle(ServerHttpRequest request,
                             ServerHttpResponse response,
                             Object handler) throws Exception {
        if (HttpTransportConstants.METHOD_POST.equals(request.getMethodValue())) {
            return request.getBody().collect(ServerHttpConnection.InputStreamCollector::new,
                            (t, dataBuffer) -> t.collectInputStream(dataBuffer.asInputStream()))
                    .flatMap(inputStreamCollector -> {
                        try {
                            DataBuffer dataBuffer = response.bufferFactory().allocateBuffer();
                            InputStream requestInputStream = inputStreamCollector.getInputStream();
                            OutputStream responseOutputStream = dataBuffer.asOutputStream();

                            ServerHttpConnection connection = new ServerHttpConnection(request, response,
                                    requestInputStream, responseOutputStream);
                            handleConnection(connection, (WebServiceMessageReceiver) handler);

                            return response.writeWith(Mono.just(dataBuffer));
                        } catch (InvalidXmlException ex) {
                            handleInvalidXmlException(request, response, handler, ex);
                            return Mono.empty();
                        } catch (Exception e) {
                            return Mono.error(new RuntimeException(e));
                        }
                    });
        } else {
            handleNonPostMethod(request, response, handler);
            return Mono.empty();
        }
    }

    @Override
    public boolean supports(Object handler) {
        return handler instanceof WebServiceMessageReceiver;
    }

    /**
     * Template method that is invoked when the request method is not {@code POST}.
     *
     * <p>Default implementation set the response status to 405: Method Not Allowed. Can be overridden in subclasses.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler current handler
     */
    protected void handleNonPostMethod(ServerHttpRequest request,
                                       ServerHttpResponse response,
                                       Object handler) {
        response.setStatusCode(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * Template method that is invoked when parsing the request results in a {@link InvalidXmlException}.
     *
     * <p>Default implementation set the response status to 400: Bad Request. Can be overridden in subclasses.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler current handler
     * @param ex the invalid XML exception that resulted in this method being called
     */
    protected void handleInvalidXmlException(ServerHttpRequest request,
                                             ServerHttpResponse response,
                                             Object handler,
                                             InvalidXmlException ex) {
        response.setStatusCode(HttpStatus.BAD_REQUEST);
    }

}
