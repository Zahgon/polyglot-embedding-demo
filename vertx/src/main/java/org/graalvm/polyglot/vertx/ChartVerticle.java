/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.polyglot.vertx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * Serves {@code POST /chart} on Vert.x Web. The request body is validated on the event loop and
 * the SVG is rendered by the bundled JavaScript on a worker thread, because evaluating guest code
 * through the Polyglot API blocks.
 */
public class ChartVerticle extends VerticleBase {

    public static final int DEFAULT_PORT = 8080;

    private static final String CHART_PATH = "/chart";
    private static final String IMAGE_SVG = "image/svg+xml";
    private static final String TEXT_PLAIN = "text/plain";
    private static final String APPLICATION_JSON = "application/json";
    private static final String CHART_ALLOW = "HEAD, POST, OPTIONS";
    private static final int MAX_BARS = 100;
    private static final int DEFAULT_WIDTH = 480;
    private static final int DEFAULT_HEIGHT = 320;
    private static final int MIN_WIDTH = 240;
    private static final int MAX_WIDTH = 2048;
    private static final int MIN_HEIGHT = 180;
    private static final int MAX_HEIGHT = 1024;
    private static final int MAX_TEXT_LENGTH = 80;
    private static final Source D3 = Source.newBuilder("js", loadScript("js/d3.v7.min.js"), "d3.v7.min.js").buildLiteral();
    private static final Source BAR_CHART = Source.newBuilder("js", loadScript("js/barchart.js"), "barchart.js").buildLiteral();
    private static final ObjectMapper MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final int port;
    private HttpServer server;

    public ChartVerticle() {
        this(DEFAULT_PORT);
    }

    public ChartVerticle(int port) {
        this.port = port;
    }

    @Override
    public Future<?> start() {
        Router router = Router.router(vertx);
        router.route().handler(this::stripNegotiationHints);
        // A request that states no media type at all is read as the media type the endpoint
        // declares, rather than being refused, which is what the service this one replaces did.
        router.post(CHART_PATH).handler(routingContext -> {
            if (routingContext.request().getHeader(HttpHeaders.CONTENT_TYPE) == null) {
                routingContext.request().headers().set(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
            }
            routingContext.next();
        });
        router.post(CHART_PATH)
                        .consumes(APPLICATION_JSON)
                        .produces(IMAGE_SVG)
                        .produces(TEXT_PLAIN)
                        .handler(BodyHandler.create(false))
                        .handler(this::render);
        // The service this one replaces answers OPTIONS on the resource itself rather than letting
        // it fall through to the method-not-allowed path, so the route is declared explicitly here.
        router.options(CHART_PATH).handler(ChartVerticle::describeAllowedMethods);
        // A body over the router's limit is refused with the status alone, as before, rather than
        // with the status message the default failure handler would write as the body.
        router.errorHandler(413, routingContext -> routingContext.response().setStatusCode(413).end());
        return vertx.createHttpServer().requestHandler(router).listen(port).onSuccess(started -> server = started);
    }

    /**
     * The port the HTTP server is bound to, which is the actual port when the verticle was
     * configured with {@code 0} to pick a free one.
     */
    public int port() {
        return server == null ? port : server.actualPort();
    }

    /**
     * Drops the negotiation hints the router volunteers on a rejected request. The service this one
     * replaces answers those rejections with headers and no body, so advertising the allowed
     * methods or the accepted media type here would be a new response header, not a migrated one.
     */
    private void stripNegotiationHints(RoutingContext routingContext) {
        routingContext.addHeadersEndHandler(written -> {
            HttpServerResponse response = routingContext.response();
            if (response.getStatusCode() == 405) {
                response.headers().remove(HttpHeaders.ALLOW);
            } else if (response.getStatusCode() == 415) {
                response.headers().remove(HttpHeaders.ACCEPT);
            }
        });
        routingContext.next();
    }

    /**
     * Answers {@code OPTIONS /chart} with the methods the resource accepts and no body.
     */
    private static void describeAllowedMethods(RoutingContext routingContext) {
        routingContext.response().putHeader(HttpHeaders.ALLOW, CHART_ALLOW).end();
    }

    private void render(RoutingContext routingContext) {
        ChartRequest parsed;
        try {
            parsed = parse(routingContext.body().buffer());
        } catch (IOException exception) {
            // A body the JSON reader cannot map is rejected by the framework before the resource
            // is reached, so no message is produced for it.
            routingContext.response().setStatusCode(400).end();
            return;
        }

        ChartRequest validated;
        try {
            validated = validate(parsed);
        } catch (IllegalArgumentException exception) {
            badRequest(routingContext, exception.getMessage());
            return;
        }

        routingContext.vertx().<String> executeBlocking(() -> renderSvg(validated)).onComplete(rendered -> {
            if (rendered.succeeded()) {
                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, IMAGE_SVG).end(rendered.result());
            } else if (rendered.cause() instanceof PolyglotException exception) {
                StringBuilder message = new StringBuilder("Unable to render chart due to ");
                message.append(exception.getMessage());
                for (PolyglotException.StackFrame frame : exception.getPolyglotStackTrace()) {
                    message.append("\nat ").append(frame);
                }
                badRequest(routingContext, message.toString());
            } else {
                routingContext.fail(rendered.cause());
            }
        });
    }

    private static String renderSvg(ChartRequest validated) {
        try (Context context = Context.newBuilder("js").
                        allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT).allowListAccess(true).build()).
                        build()) {
            context.eval(D3);
            context.eval(BAR_CHART);
            Value renderBarChart = context.getBindings("js").getMember("renderBarChart");
            return renderBarChart.execute(validated.title(), validated.xLabel(), validated.yLabel(),
                                          validated.x(), validated.y(),
                                          validated.width(), validated.height()).asString();
        }
    }

    private static ChartRequest parse(Buffer body) throws IOException {
        if (body == null || body.length() == 0) {
            return null;
        }
        return MAPPER.readValue(body.getBytes(), ChartRequest.class);
    }

    private static void badRequest(RoutingContext routingContext, String message) {
        routingContext.response()
                        .setStatusCode(400)
                        .putHeader(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
                        .end(message);
    }

    private static ChartRequest validate(ChartRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must contain chart input JSON.");
        }
        if (request.x() == null || request.y() == null || request.x().isEmpty() || request.x().size() > MAX_BARS || request.x().size() != request.y().size()) {
            throw new IllegalArgumentException("x and y must contain the same number of entries, between 1 and " + MAX_BARS + ".");
        }

        List<String> x = new ArrayList<>(request.x().size());
        for (String label : request.x()) {
            x.add(validateText(label, "x value", "Bar"));
        }

        List<Double> y = new ArrayList<>(request.y().size());
        for (Double value : request.y()) {
            if (value == null || !Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("y values must be finite, non-negative numbers.");
            }
            y.add(value);
        }

        String title = validateText(request.title(), "title", "Bar chart");
        String xLabel = validateText(request.xLabel(), "xLabel", "Category");
        String yLabel = validateText(request.yLabel(), "yLabel", "Value");
        int width = boundedDimension(request.width(), DEFAULT_WIDTH, MIN_WIDTH, MAX_WIDTH, "width");
        int height = boundedDimension(request.height(), DEFAULT_HEIGHT, MIN_HEIGHT, MAX_HEIGHT, "height");
        return new ChartRequest(title, xLabel, yLabel, x, y, width, height);
    }

    private static int boundedDimension(Integer requested, int defaultValue, int min, int max, String name) {
        int value = requested == null ? defaultValue : requested;
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    private static String validateText(String requested, String name, String defaultValue) {
        String text = requested == null || requested.isBlank() ? defaultValue : requested.trim();
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(name + " must be at most " + MAX_TEXT_LENGTH + " characters.");
        }
        return text;
    }

    private static String loadScript(String resourceName) {
        try (InputStream stream = ChartVerticle.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalStateException("Missing JavaScript resource: " + resourceName);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read JavaScript resource: " + resourceName, exception);
        }
    }

    public record ChartRequest(String title, String xLabel, String yLabel, List<String> x, List<Double> y, Integer width, Integer height) {
    }

}
