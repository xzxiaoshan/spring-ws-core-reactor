package com.shanhy.spring.ws.reactor;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.transport.AbstractReceiverConnection;
import org.springframework.ws.transport.EndpointAwareWebServiceConnection;
import org.springframework.ws.transport.FaultAwareWebServiceConnection;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPConstants;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;

/**
 * 新增实现
 *
 * @author shanhy
 * @date 2022-05-10 14:03
 */
public class ServerHttpConnection extends AbstractReceiverConnection
        implements EndpointAwareWebServiceConnection, FaultAwareWebServiceConnection {

    private final ServerHttpRequest request;

    private final ServerHttpResponse response;

    private final DataBuffer responseDataBuffer;

    private boolean statusCodeSet = false;

    private final InputStream requestInputStream;

    private final OutputStream responseOutputStream;

    /**
     * Constructs a new servlet connection with the given {@code HttpServletRequest} and
     * {@code HttpServletResponse}.
     *
     * @param request ServerHttpRequest
     * @param response ServerHttpResponse
     * @param requestInputStream InputStream
     * @param responseOutputStream OutputStream
     */
    protected ServerHttpConnection(ServerHttpRequest request, ServerHttpResponse response,
                                   InputStream requestInputStream, OutputStream responseOutputStream) {
        this.request = request;
        this.response = response;
        this.responseDataBuffer = response.bufferFactory().allocateBuffer();
        this.requestInputStream = requestInputStream;
        this.responseOutputStream = responseOutputStream;
    }

    /**
     * Returns the {@code HttpServletRequest} for this connection.
     */
    public ServerHttpRequest getRequest() {
        return request;
    }

    /**
     * Returns the {@code HttpServletResponse} for this connection.
     */
    public ServerHttpResponse getResponse() {
        return response;
    }

    @Override
    public void endpointNotFound() {
        getResponse().setStatusCode(HttpStatus.NOT_FOUND);
        statusCodeSet = true;
    }

    /*
     * Errors
     */

    @Override
    public boolean hasError() throws IOException {
        return false;
    }

    @Override
    public String getErrorMessage() throws IOException {
        return null;
    }

    /*
     * URI
     */

    @Override
    public URI getUri() throws URISyntaxException {
        return new URI(request.getURI().getScheme(), null, request.getURI().getHost(),
                request.getURI().getPort(), request.getURI().getPath(),
                request.getURI().getQuery(), null);
    }

    /*
     * Receiving request
     */

    @Override
    public Iterator<String> getRequestHeaderNames() throws IOException {
        return getRequest().getHeaders().keySet().iterator();
    }

    @Override
    public Iterator<String> getRequestHeaders(String name) throws IOException {
        List<String> list = request.getHeaders().get(name);
        return list == null ? null : list.iterator();
    }

    @Override
    protected InputStream getRequestInputStream() throws IOException {
        return requestInputStream;
    }

    /*
     * Sending response
     */

    @Override
    public void addResponseHeader(String name, String value) throws IOException {
        getResponse().getHeaders().add(name, value);
    }

    @Override
    protected OutputStream getResponseOutputStream() throws IOException {
        return this.responseOutputStream;
    }

    @Override
    protected void onSendAfterWrite(WebServiceMessage message) throws IOException {
        statusCodeSet = true;
    }

    @Override
    public void onClose() throws IOException {
        if (!statusCodeSet) {
            getResponse().setStatusCode(HttpStatus.ACCEPTED);
        }
    }

    /*
     * Faults
     */

    @Override
    public boolean hasFault() throws IOException {
        return false;
    }

    @Override
    @Deprecated
    public void setFault(boolean fault) throws IOException {
        if (fault) {
            getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        } else {
            getResponse().setStatusCode(HttpStatus.OK);
        }
        statusCodeSet = true;
    }

    @Override
    public void setFaultCode(QName faultCode) throws IOException {
        if (faultCode != null) {
            if (SOAPConstants.SOAP_SENDER_FAULT.equals(faultCode)) {
                getResponse()
                        .setStatusCode(HttpStatus.BAD_REQUEST);
            } else {
                getResponse()
                        .setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            getResponse().setStatusCode(HttpStatus.OK);
        }
        statusCodeSet = true;
    }

    static class InputStreamCollector {

        private InputStream is;

        public void collectInputStream(InputStream is) {
            if (this.is == null) this.is = is;
            this.is = new SequenceInputStream(this.is, is);
        }

        public InputStream getInputStream() {
            return this.is;
        }
    }
}
