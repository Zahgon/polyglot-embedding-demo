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

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

@Controller("/js")
public final class JSController {

    @Post(consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
    public HttpResponse<String> evaluate(@Body String script) {
        if (script == null || script.isBlank()) {
            return HttpResponse.badRequest("Request body must contain JavaScript source.")
                    .contentType(MediaType.TEXT_PLAIN_TYPE);
        }

        try (Context context = Context.newBuilder("js")
                .allowAllAccess(false)
                .build()) {
            Value result = context.eval("js", script);
            return HttpResponse.ok(asString(result))
                    .contentType(MediaType.TEXT_PLAIN_TYPE);
        } catch (PolyglotException exception) {
            return HttpResponse.badRequest(exception.getMessage())
                    .contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    private static String asString(Value value) {
        if (value == null || value.isNull()) {
            return "null";
        } else if (value.isString()) {
            return value.asString();
        } else if (value.isBoolean()) {
            return Boolean.toString(value.asBoolean());
        } else if (value.isNumber()) {
            if (value.fitsInLong()) {
                return Long.toString(value.asLong());
            } else if (value.fitsInDouble()) {
                return Double.toString(value.asDouble());
            }
        }
        return value.toString();
    }
}
