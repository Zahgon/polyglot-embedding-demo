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
package org.graalvm.polyglot.micronaut;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.serde.annotation.Serdeable;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

@Controller("/chart")
public final class ChartController {

    private static final int MAX_POINTS = 200;
    private static final int DEFAULT_WIDTH = 320;
    private static final int DEFAULT_HEIGHT = 80;
    private static final int MIN_WIDTH = 16;
    private static final int MAX_WIDTH = 2048;
    private static final int MIN_HEIGHT = 16;
    private static final int MAX_HEIGHT = 1024;
    private static final String DEFAULT_COLOR = "#2563eb";
    private static final Pattern SAFE_COLOR = Pattern.compile("#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?");
    private static final String SCRIPT_D3_PATH = loadScript("js/d3-path.min.js");
    private static final String SCRIPT_D3_SHAPE = loadScript("js/d3-shape.min.js");
    private static final String SCRIPT_SPARKLINE = loadScript("js/sparkline.js");

    @Post(consumes = MediaType.APPLICATION_JSON, produces = {MediaType.IMAGE_SVG, MediaType.TEXT_PLAIN})
    public HttpResponse<String> render(@Body ChartRequest request) {
        ChartRequest validatedRequest;
        try {
            validatedRequest = validate(request);
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }

        try (Context context = Context.newBuilder("js").build()) {
            context.eval("js", SCRIPT_D3_PATH);
            context.eval("js", SCRIPT_D3_SHAPE);
            context.eval("js", SCRIPT_SPARKLINE);
            Value renderSparkline = context.getBindings("js").getMember("renderSparkline");
            String svg = renderSparkline.execute(validatedRequest.toJson()).asString();
            return HttpResponse.ok(svg).contentType(MediaType.IMAGE_SVG_TYPE);
        } catch (PolyglotException exception) {
            StringBuilder message = new StringBuilder("Unable to render chart due to ");
            message.append(exception.getMessage());
            for (PolyglotException.StackFrame frame : exception.getPolyglotStackTrace()) {
                message.append("\nat ").append(frame);
            }
            return badRequest(message.toString());
        }
    }

    private static HttpResponse<String> badRequest(String message) {
        return HttpResponse.badRequest(message).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static ChartRequest validate(ChartRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must contain chart input JSON.");
        }
        if (request.values() == null || request.values().size() < 2 || request.values().size() > MAX_POINTS) {
            throw new IllegalArgumentException("values must contain between 2 and " + MAX_POINTS + " numbers.");
        }

        List<Double> values = new ArrayList<>(request.values().size());
        for (Double value : request.values()) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("values must contain only finite numbers.");
            }
            values.add(value);
        }

        int width = boundedDimension(request.width(), DEFAULT_WIDTH, MIN_WIDTH, MAX_WIDTH, "width");
        int height = boundedDimension(request.height(), DEFAULT_HEIGHT, MIN_HEIGHT, MAX_HEIGHT, "height");
        String color = validateColor(request.color());
        return new ChartRequest(values, width, height, color);
    }

    private static int boundedDimension(Integer requested, int defaultValue, int min, int max, String name) {
        int value = requested == null ? defaultValue : requested;
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    private static String validateColor(String requested) {
        if (requested == null || requested.isBlank()) {
            return DEFAULT_COLOR;
        }
        if (!SAFE_COLOR.matcher(requested).matches()) {
            throw new IllegalArgumentException("color must be a hex color such as #2563eb.");
        }
        return requested;
    }

    private static String loadScript(String resourceName) {
        try (InputStream stream = ChartController.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalStateException("Missing JavaScript resource: " + resourceName);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read JavaScript resource: " + resourceName, exception);
        }
    }

    @Serdeable
    public record ChartRequest(List<Double> values, Integer width, Integer height, String color) {

        String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{\"values\":[");
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append(values.get(i));
            }
            json.append("],\"width\":").append(width);
            json.append(",\"height\":").append(height);
            json.append(",\"color\":\"").append(color).append("\"}");
            return json.toString();
        }
    }
}
