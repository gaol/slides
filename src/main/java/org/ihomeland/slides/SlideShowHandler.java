package org.ihomeland.slides;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
 * This Handler
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
    public static final void install(Router router, TemplateEngine templateEngine, JsonObject config) {
        SlideShowHandler handler = new SlideShowHandler(templateEngine, config == null ? new JsonObject() : config);
        router.get(handler.slideUrlPath + ":slide/").handler(handler);
        router.get(handler.slideUrlPath + ":slide").handler(handler);
        router.get(handler.slideUrlPath + ":slide/*").handler(handler);
        router.get(getWebJarsRootPath(config) + "*").handler(StaticHandler.create().setWebRoot(config.getString("webroot", StaticHandler.DEFAULT_WEB_ROOT)));
    }

    private static String getWebJarsRootPath(JsonObject config) {
        return config.getString("webroot.path", "/");
    }

    private final JsonObject config;
    private final TemplateEngine templateEngine;
    private final String slideUrlPath;
    private final String explodedDir;

    private SlideShowHandler(TemplateEngine templateEngine, JsonObject config) {
        this.templateEngine = templateEngine;
        this.config = config;
        String slidePath = config.getString("slides.path", "/slides");
        if (!slidePath.endsWith("/")) {
            slidePath = slidePath + "/";
        }
        this.slideUrlPath = slidePath;
        this.explodedDir = config.getString("exploded.dir", Paths.get(System.getProperty("java.io.tmpdir"), "vertx-slides-exploded", UUID.randomUUID().toString()).toString());
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
        final String slidesRootDir = config.getString("slides.root.dir", "slides");
        final String slideZipRoot = config.getString("slides.zip.root.dir", "slides_zip");
        final LocalMap<String, String> slideDirMap = vertx.sharedData().getLocalMap("slides_Dir");
        final LocalMap<String, String> unzippedSlides = vertx.sharedData().getLocalMap("unzipped_slides");
        final LocalMap<String, String> explodedDirMap = vertx.sharedData().getLocalMap("exploded_dir_map");
        if (!explodedDirMap.containsKey("explodedDir")) {
            explodedDirMap.put("explodedDir", this.explodedDir);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> this.deleteExploded(vertx)));
        }

        final Single<String> dirInCache = Single.fromCallable(() -> {
            return slideDirMap.getOrDefault(slide, "");
        });

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
                    return unzipFile(vertx, s, targetDir, true)
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
            Single<Buffer> responseSingle = null;
            final AtomicBoolean found = new AtomicBoolean(false);
            final String requestedFile = path.substring(path.indexOf(redirect) + redirect.length());
            if (requestedFile == null || requestedFile.equals("") || requestedFile.equals(SLIDE_TEMPLATE)) {
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
                final String filePath = dir.toString() + File.separator + requestedFile;
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
            }), e -> {
                ctx.fail(e);
            });
        }, e -> {
            ctx.fail(e);
        });
    }

    private void deleteExploded(Vertx vertx) {
        logger.info("Trying to delete the tempoary exploded zip directory: " + this.explodedDir);
        vertx.fileSystem().deleteRecursiveBlocking(this.explodedDir, true);
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

    public static Single<String> unzipFile(Vertx vertx, final String zipFile, final String targetDir, boolean override) {
        return vertx.fileSystem().rxExists(targetDir).flatMap(e -> {
            if (!e) {
                return vertx.fileSystem().rxMkdirs(targetDir).toSingleDefault(targetDir);
            }
            return Single.just(targetDir);
        }).flatMapMaybe(dir -> {
            return vertx.<String>rxExecuteBlocking(h -> {
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));) {
                    ZipEntry ze = zis.getNextEntry();
                    byte[] buffer = new byte[4029];
                    while (ze != null) {
                        String fn = ze.getName();
                        File newFile = new File(dir + File.separator + fn);
                        if (override || !newFile.exists()) {
                            try (FileOutputStream fos = new FileOutputStream(newFile);) {
                                int len;
                                while ((len = zis.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                        }
                        ze = zis.getNextEntry();
                    }
                    zis.closeEntry();
                    h.complete(targetDir);
                } catch (IOException ioe) {
                    h.fail(ioe);
                }
            });
        }).toSingle(targetDir);
    }

    protected <T> Consumer<T> responseNotClosed(final RoutingContext ctx, Consumer<T> con) {
        return new Consumer<T>() {
            @Override
            public void accept(T t) throws Exception {
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
            }
        };
    }

}