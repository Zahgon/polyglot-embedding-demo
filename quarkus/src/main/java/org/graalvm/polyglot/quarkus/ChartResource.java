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
package org.graalvm.polyglot.quarkus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

@Path("/chart")
public class ChartResource {

    private static final String IMAGE_SVG = "image/svg+xml";
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

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({IMAGE_SVG, MediaType.TEXT_PLAIN})
    public Response render(ChartRequest request) {
        ChartRequest validated;
        try {
            validated = validate(request);
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }

        try (Context context = Context.newBuilder("js").
                        allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT).allowListAccess(true).build()).
                        build()) {
            context.eval(D3);
            context.eval(BAR_CHART);
            Value renderBarChart = context.getBindings("js").getMember("renderBarChart");
            String svg = renderBarChart.execute(validated.title(), validated.xLabel(), validated.yLabel(),
                                                validated.x(), validated.y(),
                                                validated.width(), validated.height()).asString();
            return Response.ok(svg, IMAGE_SVG).build();
        } catch (PolyglotException exception) {
            StringBuilder message = new StringBuilder("Unable to render chart due to ");
            message.append(exception.getMessage());
            for (PolyglotException.StackFrame frame : exception.getPolyglotStackTrace()) {
                message.append("\nat ").append(frame);
            }
            return badRequest(message.toString());
        }
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                        .type(MediaType.TEXT_PLAIN)
                        .entity(message)
                        .build();
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
        try (InputStream stream = ChartResource.class.getClassLoader().getResourceAsStream(resourceName)) {
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
