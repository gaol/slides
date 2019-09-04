package io.github.gaol.slides;

import java.io.File;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.vertx.core.http.impl.MimeMapping;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import io.vertx.reactivex.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.shareddata.LocalMap;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * This Handler takes care of how to access the Slides application.
 */
public class SlideShowHandler implements Handler<RoutingContext> {

    private final static Logger logger = LoggerFactory.getLogger(SlideShowHandler.class);

    private final static String SLIDE_META = "slide.json";
    private final static String SLIDE_TEMPLATE = "slide.ftl";

    /**
     * Installs the SlideShowHandler to a vertx-web @doc{Router} with specified path prefix, default to '/slides'.
     *
     * <p>
     *     This will install a StaticHandler as well to take advantage of reveal.js presentation kits.
     * </p>
     * <p>
     *     The dependent project needs to use vertx-maven-plugin to package the jar to be able to use the webjars in vertx way
     * </p>
     * @param router The vertx-web Router to install to.
     * @param templateEngine The TemplateEngine used to render the slide page, default to FreeMarkerTemplateEngine.
     * @param config The JsonObject configuration used to configure where to find/serve the slide page.
     */
    static void install(Router router, TemplateEngine templateEngine, JsonObject config) {
        SlideShowHandler handler = new SlideShowHandler(templateEngine, config == null ? new JsonObject() : config);
        router.get(handler.slideUrlPath + ":slide/").handler(handler);
        router.get(handler.slideUrlPath + ":slide").handler(handler);
        router.get(handler.slideUrlPath + ":slide/*").handler(handler);
        router.get(getWebJarsRootPath(config) + "*").handler(StaticHandler.create().setDirectoryListing(true).setWebRoot(Utils.configString(config, "webroot", StaticHandler.DEFAULT_WEB_ROOT)));
    }

    private static String getWebJarsRootPath(JsonObject config) {
        return Utils.configString(config, "webroot.path", "/");
    }

    private final JsonObject config;
    private final TemplateEngine templateEngine;
    private final String slideUrlPath;
    private final String explodedDir;

    private SlideShowHandler(TemplateEngine templateEngine, JsonObject config) {
        this.templateEngine = templateEngine;
        this.config = config;
        this.slideUrlPath = slidePath(config);
        this.explodedDir = Utils.configString(config, "exploded.dir", Paths.get(System.getProperty("java.io.tmpdir"), "vertx-slides-exploded", UUID.randomUUID().toString()).toString());
    }

    static String slidePath(JsonObject config) {
        String slidePath = Utils.configString(config, "slides.path", "/slides");
        if (!slidePath.endsWith("/")) {
            slidePath = slidePath + "/";
        }
        return slidePath;
    }

    private static String slideRootDir(JsonObject config) {
        return Utils.configString(config, "slides.root.dir", "slides");
    }

    @Override
    public void handle(RoutingContext ctx) {
        final String slide = ctx.request().getParam("slide");
        if (slide == null || slide.length() == 0) {
            ctx.fail(400);
            return;
        }
        final String path = ctx.normalisedPath();
        String redirect = slideUrlPath + slide + "/";
        if (path.endsWith(slideUrlPath + slide)) {
            String pathPrefix = path.substring(0, path.indexOf(slideUrlPath));
            ctx.response().setStatusCode(302).putHeader("Location", pathPrefix + redirect).end();
            return;
        }

        Vertx vertx = ctx.vertx();
        final String slidesRootDir = slideRootDir(config);
        final String slideZipRoot = Utils.configString(config, "slides.zip.root.dir", "slides_zip");

        final LocalMap<String, String> slideDirMap = vertx.sharedData().getLocalMap("slides_Dir");
        final LocalMap<String, String> unzippedSlides = vertx.sharedData().getLocalMap("unzipped_slides");
        final LocalMap<String, String> explodedDirMap = vertx.sharedData().getLocalMap("exploded_dir_map");
        if (!explodedDirMap.containsKey("explodedDir")) {
            explodedDirMap.put("explodedDir", this.explodedDir);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> this.deleteExploded(vertx)));
        }

        final Single<String> dirInCache = Single.fromCallable(() -> slideDirMap.getOrDefault(slide, ""));

        // slide.root.dir/slide directory, vertx file resolver takes care about the classpath by default
        final String slidePath = slidesRootDir + "/" + slide;
        Single<String> slideInDir = vertx.fileSystem().rxExists(slidePath).map(e -> e ? slidePath : "");

        // slide.zip.dir/slide.zip file
        final String zipPath = slideZipRoot + "/" + slide + ".zip";
        Single<String> slideZip = vertx.fileSystem().rxExists(zipPath).map(e -> e ? zipPath : "");

        dirInCache
        .flatMap(s ->  s.equals("") ? slideInDir : Single.just(s))
        .flatMap(s ->  s.equals("") ? slideZip : Single.just(s))
        .flatMap(s -> {
            if (logger.isTraceEnabled()) {
                logger.trace("Slide zip file or directory: {}", s);
            }
            return vertx.fileSystem().rxProps(s).flatMap(p -> {
                if (p.isRegularFile() && s.endsWith(".zip")) {
                    if (unzippedSlides.containsKey(s)) {
                        return Single.just(unzippedSlides.get(s));
                    }
                    String targetDir = Paths.get(this.explodedDir, slide).toString();
                    return Utils.unzipFile(vertx, s, targetDir, true)
                        .flatMap(dir -> {
                            unzippedSlides.put(s, dir);
                            return Single.just(dir);
                        });
                }
                return Single.just(s);
            });
        })
        .subscribe(dir -> {
            if (dir.equals("")) {
                ctx.next();
                return;
            }
            slideDirMap.putIfAbsent(slide, dir);
            Single<Buffer> responseSingle;
            final AtomicBoolean found = new AtomicBoolean(false);
            final String requestedFile = path.substring(path.indexOf(redirect) + redirect.length());
            if (requestedFile.equals("") || requestedFile.equals(SLIDE_TEMPLATE)) {
                final String metaFile = dir + File.separator + SLIDE_META;
                responseSingle = vertx.fileSystem().rxExists(metaFile).flatMap(e -> {
                    if (e) {
                        return vertx.fileSystem().rxReadFile(metaFile).map(b -> new JsonObject(b.getDelegate())).map(meta -> defaultMeta().mergeIn(meta, 2));
                    }
                    return Single.just(defaultMeta());
                 }).flatMap(c -> {
                     final String tempFile = dir + File.separator + SLIDE_TEMPLATE;
                     return vertx.fileSystem().rxExists(tempFile).flatMap(e -> {
                         found.set(true);
                         ctx.response().putHeader("Content-Type", MimeMapping.getMimeTypeForExtension("html"));
                         if (e) {
                             // using specific template
                             return templateEngine.rxRender(c, tempFile);
                         }
                         return templateEngine.rxRender(c, "templates/slide.ftl");
                     });
                 });
            } else {
                final String filePath = dir + File.separator + requestedFile;
                responseSingle = vertx.fileSystem().rxExists(filePath).flatMap(e -> {
                    found.set(e);
                    if (e) {
                        String contentType = MimeMapping.getMimeTypeForFilename(filePath);
                        if (contentType != null) {
                            if (contentType.startsWith("text")) {
                                ctx.response().putHeader("Content-Type", contentType + ";charset=UTF-8");
                            } else {
                                ctx.response().putHeader("Content-Type", contentType);
                            }
                        }
                        return ctx.vertx().fileSystem().rxReadFile(filePath);
                    }
                    return Single.just(Buffer.buffer());
                });
            }

            responseSingle.subscribe(responseNotClosed(ctx, sb -> {
                if (!found.get()) {
                    ctx.fail(404);
                    return;
                }
                ctx.response().end(sb);
            }), ctx::fail);
        }, ctx::fail);
    }

    private void deleteExploded(Vertx vertx) {
        if (vertx.fileSystem().existsBlocking(this.explodedDir)) {
            logger.info("Delete the tempoary exploded zip directory: " + this.explodedDir);
            vertx.fileSystem().deleteRecursiveBlocking(this.explodedDir, true);
        }
    }

    private JsonObject defaultMeta() {
        String rootPath = getWebJarsRootPath(config);
        if (rootPath.endsWith("/")) {
            rootPath = rootPath.substring(0, rootPath.length() - 1);
        }
        return new JsonObject()
                .put("title", "Slides")
                .put("webRootPath", rootPath)
                .put("reveal", new JsonObject()
                        .put("theme", "moon")
                        .put("controls", "true")
                        .put("progress", "true")
                        .put("history", "true")
                        .put("center", "true")
                        .put("hash", "true")
                        .put("loop", "false")
                        .put("layout", "bottom-right")
                        .put("transition", "slide")
                        .put("highlight", "monokai")
                );
    }

    private <T> Consumer<T> responseNotClosed(final RoutingContext ctx, Consumer<T> con) {
        return t -> {
            if (ctx.response().closed() || ctx.response().ended()) {
                logger.warn("Response has been closed.");
            } else {
                Exception theE = null;
                try {
                    con.accept(t);
                } catch (Exception e) {
                    theE = e;
                    throw e;
                } finally {
                    if (!ctx.response().ended()) {
                        if (theE != null) {
                            ctx.fail(theE);
                        } else {
                            ctx.response().end();
                        }
                    }
                }
            }
        };
    }

}