package org.jboss.ext.slides;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
import io.vertx.reactivex.ext.web.templ.freemarker.FreeMarkerTemplateEngine;

// not high performance on images, for teachers, it is sufficient.
public class SlideShowHandler implements Handler<RoutingContext> {

    private final static Logger logger = LoggerFactory.getLogger(SlideShowHandler.class);

    private final static String SLIDE_META = "slide.json";
    private final static String SLIDE_TEMPLATE = "slide.ftl";

    public static final void install(Router router, FreeMarkerTemplateEngine templateEngine, JsonObject config) {
        SlideShowHandler handler = new SlideShowHandler(templateEngine, config == null ? new JsonObject() : config);
        router.get("/slides/:slide/").order(-1).handler(handler);
        router.get("/slides/:slide").handler(handler);
        router.get("/slides/:slide/*").handler(handler);
    }

    private final JsonObject config;
    private final FreeMarkerTemplateEngine templateEngine;

    private SlideShowHandler(FreeMarkerTemplateEngine templateEngine, JsonObject config) {
        this.templateEngine = templateEngine;
        this.config = config;
    }

    @Override
    public void handle(RoutingContext ctx) {
        final String slide = ctx.request().getParam("slide");
        if (slide == null || slide.length() == 0) {
            ctx.fail(400);
            return;
        }
        final String path = ctx.normalisedPath();
        String redirect = "/slides/" + slide + "/";
        if (path.endsWith("/slides/" + slide)) {
            String pathPrefix = path.substring(0, path.indexOf("/slides"));
            ctx.response().setStatusCode(302).putHeader("Location", pathPrefix + redirect).end();
            return;
        }

        Vertx vertx = ctx.vertx();
        final String slidesRootDir = System.getProperty("slides.root.dir",
                System.getenv().getOrDefault("slides.root.dir", config.getString("slides.root.dir", "slides")));
        final String slideZipRoot = System.getProperty("slides.zip.root.dir",
                System.getenv().getOrDefault("slides.zip.root.dir", config.getString("slides.zip.root.dir", "slides_zip")));
        final String slideZipExplodedRoot = System.getProperty("slides.zip.exploded.dir",
                System.getenv().getOrDefault("slides.zip.exploded.dir", config.getString("slides.zip.exploded.dir", Paths.get(System.getProperty("java.tmp.dir"), "slides_exploded").toString())));

        final LocalMap<String, String> slideDirMap = vertx.sharedData().getLocalMap("slides_Dir");
        final Single<String> dirInCache = Single.fromCallable(() -> {
            return slideDirMap.getOrDefault(slide, "");
        });

        // slide.root.dir/slide directory, vertx file resolver takes care about the classpath by default
        final String slidePath = slidesRootDir + "/" + slide;
        Single<String> slideInDir = vertx.fileSystem().rxExists(slidePath).map(e -> e ? slidePath : "");

        // slide.zip.dir/slide.zip file
        final String zipPath = slideZipRoot + "/" + slide + ".zip";
        Single<String> slideZip = vertx.fileSystem().rxExists(zipPath).map(e -> e ? zipPath : "");

        final LocalMap<String, String> unzippedSlides = vertx.sharedData().getLocalMap("unzipped_slides");

        dirInCache.flatMap(s ->  s.equals("") ? slideInDir : Single.just(s))
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
                    String targetDir = Paths.get(slideZipExplodedRoot, slide).toString();
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
            final String requestedFile = path.substring(path.indexOf(redirect) + redirect.length());
            if (requestedFile == null || requestedFile.equals("") || requestedFile.equals(SLIDE_TEMPLATE)) {
                final String metaFile = dir + File.separator + SLIDE_META;
                responseSingle = vertx.fileSystem().rxExists(metaFile).flatMap(e -> {
                    if (e) {
                        return vertx.fileSystem().rxReadFile(metaFile).map(b -> new JsonObject(b.getDelegate()));
                    }
                    return Single.just(new JsonObject().put("title", "Slides"));
                 }).flatMap(c -> {
                     final String tempFile = dir + File.separator + SLIDE_TEMPLATE;
                     return vertx.fileSystem().rxExists(tempFile).flatMap(e -> {
                         if (e) {
                             // using specific template
                             return templateEngine.rxRender(c, tempFile);
                         }
                         return templateEngine.rxRender(c, "templates/slide.ftl");
                     });
                 });
            } else {
                // try to read the file, no cache for slides.
                final String filePath = dir.toString() + File.separator + requestedFile;
                if (filePath.endsWith(".svg")) {
                    ctx.response().putHeader("Content-Type", "image/svg+xml");
                }
                responseSingle = vertx.fileSystem().rxExists(filePath).flatMap(e -> {
                    if (e)
                        return ctx.vertx().fileSystem().rxReadFile(filePath);
                    return Single.just(Buffer.buffer());
                });
            }

            responseSingle.subscribe(responseNotClosed(ctx, sb -> {
                if (sb.toString().equals("")) { // slide is empty or file under the directory is not found
                    ctx.next();
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
