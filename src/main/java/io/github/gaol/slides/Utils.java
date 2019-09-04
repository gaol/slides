package io.github.gaol.slides;

import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class Utils {

    /**
     * Gets the configured String by it's key, default to the System's property, which defaults to the specified default value.
     *
     * @param config the config which to get by the key, not-null
     * @param key the key to search for the property
     * @param dft the default value if nothing specified
     * @return the configured value
     */
    static String configString(JsonObject config, String key, String dft) {
        return config.getString(key, System.getProperty(key, dft));
    }

    /**
     * Unzip the file to target directory.
     *
     * @param vertx the vertx instance
     * @param zipFile the source zip file location
     * @param targetDir the target directory
     * @param override override or not
     * @return the Single to unzip the file with the target directory as the result
     */
    static Single<String> unzipFile(Vertx vertx, final String zipFile, final String targetDir, boolean override) {
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
                            if (!newFile.exists()) {
                                newFile.createNewFile();
                            }
                            if (!newFile.getCanonicalPath().startsWith(dir)) {
                                newFile.delete();
                                throw new IOException("Cannot unzip file to outside of target directory: " + dir);
                            }
                            try (FileOutputStream fos = new FileOutputStream(newFile)) {
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

}
