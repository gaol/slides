/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ihomeland.slides;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;

import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.templ.freemarker.FreeMarkerTemplateEngine;

/**
 * This is the default Verticle to deploy which starts a HTTP server.
 *
 */
public class SlidesVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(SlidesVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        JsonObject config = config();
        JsonObject httpConfig = config.getJsonObject("http.server.options", new JsonObject());
        HttpServerOptions httpOptions = new HttpServerOptions(httpConfig).setPort(8080);
        if (System.getProperty("http.server.host") != null) {
            httpOptions.setHost(System.getProperty("http.server.host"));
        }
        if (System.getProperty("http.server.port") != null) {
            httpOptions.setPort(Integer.getInteger("http.server.port", httpOptions.getPort()));
        }
        HttpServer httpServer = vertx.createHttpServer(httpOptions);
        Router slidesRouter = Router.router(vertx);
        SlideShowHandler.install(slidesRouter, FreeMarkerTemplateEngine.create(vertx), config.getJsonObject("slides.config", new JsonObject()));
        httpServer.requestHandler(slidesRouter).rxListen(httpOptions.getPort()).subscribe(s -> {
            logger.info("HTTP server is running on port: " + s.actualPort());
            startFuture.complete();
        }, e -> {
            logger.error("Failed to start HTTP server.", e);
            startFuture.fail(e);
        });
    }

}
