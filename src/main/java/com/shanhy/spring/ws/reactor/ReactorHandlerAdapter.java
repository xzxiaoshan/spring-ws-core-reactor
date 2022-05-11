package com.shanhy.spring.ws.reactor;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;

/**
 * 重写 HandlerAdapter
 *
 * @author shanhy
 * @date 2022-05-10 13:04
 */
public interface ReactorHandlerAdapter {

    /**
     * Given a handler instance, return whether or not this {@code HandlerAdapter}
     * can support it. Typical HandlerAdapters will base the decision on the handler
     * type. HandlerAdapters will usually only support one handler type each.
     * <p>A typical implementation:
     * <p>{@code
     * return (handler instanceof MyHandler);
     * }
     * @param handler the handler object to check
     * @return whether or not this object can use the given handler
     */
    boolean supports(Object handler);

    /**
     * Use the given handler to handle this request.
     * The workflow that is required may vary widely.
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler the handler to use. This object must have previously been passed
     * to the {@code supports} method of this interface, which must have
     * returned {@code true}.
     * @throws Exception in case of errors
     * @return a ModelAndView object with the name of the view and the required
     * model data, or {@code null} if the request has been handled directly
     */
    @Nullable
    Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response, Object handler) throws Exception;

    /**
     * Same contract as for HttpServlet's {@code getLastModified} method.
     * Can simply return -1 if there's no support in the handler class.
     * @param request current HTTP request
     * @param handler the handler to use
     * @return the lastModified value for the given handler
     * @see org.springframework.web.servlet.mvc.LastModified#getLastModified
     */
    long getLastModified(ServerHttpRequest request, Object handler);

}
