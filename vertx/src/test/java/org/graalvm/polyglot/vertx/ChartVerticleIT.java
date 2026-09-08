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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// Execute the same tests but in packaged mode.
class ChartVerticleIT extends ChartVerticleTest {

    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);

    private Process runner;

    @Override
    protected int start() {
        Path jar = Path.of(System.getProperty("runner.jar.path", ""));
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Runnable artifact not found: " + jar);
        }

        int port = freePort();
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        for (String argument : System.getProperty("runner.jvm.args", "").trim().split("\\s+")) {
            if (!argument.isBlank()) {
                command.add(argument);
            }
        }
        command.add("-Dhttp.port=" + port);
        command.add("-jar");
        command.add(jar.toString());

        try {
            runner = new ProcessBuilder(command).inheritIO().start();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start " + jar, exception);
        }
        awaitPort(port);
        return port;
    }

    @Override
    protected void stop() {
        if (runner != null && runner.isAlive()) {
            runner.destroy();
            try {
                if (!runner.waitFor(30, TimeUnit.SECONDS)) {
                    runner.destroyForcibly();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                runner.destroyForcibly();
            }
        }
    }

    private void awaitPort(int port) {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (runner != null && !runner.isAlive()) {
                throw new IllegalStateException("Runnable artifact exited with " + runner.exitValue() + " before accepting requests.");
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("localhost", port), 1000);
                return;
            } catch (IOException notYet) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
        }
        stop();
        throw new IllegalStateException("Runnable artifact did not accept requests within " + STARTUP_TIMEOUT);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot reserve a port for the runnable artifact.", exception);
        }
    }
}
