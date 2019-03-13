package peergos.server.net;

import com.sun.net.httpserver.*;

import java.io.*;
import java.util.*;

public class ResponseHeaderHandler implements HttpHandler {

    private final Map<String, String> responseHeaders;
    private final HttpHandler handler;

    public ResponseHeaderHandler(Map<String, String> responseHeaders, HttpHandler handler) {
        this.responseHeaders = responseHeaders;
        this.handler = handler;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        for (Map.Entry<String, String> entry: responseHeaders.entrySet())
            httpExchange.getResponseHeaders().set(entry.getKey(), entry.getValue());
        handler.handle(httpExchange);
    }
}
