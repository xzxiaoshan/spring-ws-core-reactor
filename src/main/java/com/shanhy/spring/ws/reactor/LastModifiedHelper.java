package com.shanhy.spring.ws.reactor;

import org.springframework.util.StringUtils;
import org.springframework.xml.transform.TraxUtils;
import org.w3c.dom.Document;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * 最后修改时间 Helper
 *
 * @author shanhy
 * @date 2022-05-10 13:02
 */
public class LastModifiedHelper {

    private LastModifiedHelper() {
    }

    /**
     * Returns the last modified date of the given {@link Source}.
     *
     * @param source the source
     * @return the last modified date, as a long
     */
    public static long getLastModified(Source source) {
        if (source instanceof DOMSource) {
            Document document = TraxUtils.getDocument((DOMSource) source);
            return document != null ? getLastModified(document.getDocumentURI()) : -1;
        } else {
            return getLastModified(source.getSystemId());
        }
    }

    private static long getLastModified(String systemId) {
        if (StringUtils.hasText(systemId)) {
            try {
                URI systemIdUri = new URI(systemId);
                if ("file".equals(systemIdUri.getScheme())) {
                    File documentFile = new File(systemIdUri);
                    if (documentFile.exists()) {
                        return documentFile.lastModified();
                    }
                }
            } catch (URISyntaxException e) {
                // ignore
            }
        }
        return -1;
    }
}
