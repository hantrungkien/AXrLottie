package com.aghajari.rlottie;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;

import com.aghajari.rlottie.network.AXrFileExtension;
import com.aghajari.rlottie.network.JsonFileExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * Lottie cache manager
 */
public class AXrLottieCacheManager {

    private static final String TAG = AXrLottieCacheManager.class.getSimpleName();

    File networkCacheDir, localCacheDir;

    public AXrLottieCacheManager(File networkCacheDir, File localCacheDir) {
        this.networkCacheDir = networkCacheDir;
        this.localCacheDir = localCacheDir;
    }

    public void clear() {
        clear(getLocalCacheParent());
        clear(getNetworkCacheParent());
    }

    private void clear(File parentDir) {
        if (parentDir.exists()) {
            File[] files = parentDir.listFiles();
            if (files != null && files.length > 0) {
                for (File file : parentDir.listFiles()) {
                    file.delete();
                }
            }
            parentDir.delete();
        }
    }

    /**
     * Returns null if the animation doesn't exist in the cache.
     */
    @Nullable
    @WorkerThread
    public File fetchURLFromCache(String url) {
        Pair<AXrFileExtension, File> cacheResult = fetch(url);
        if (cacheResult == null) {
            return null;
        }
        File f = cacheResult.second;
        if (f == null || !f.exists()) return null;
        return f;
    }

    public File fetchLocalFromCache(final String json, final String name) {
        File f = new File(getLocalCacheParent(), name + ".cache");
        if (f.exists()) return f;
        return writeLocalCache(json, name);
    }

    private File writeLocalCache(final String json, final String name) {
        try {
            File f2 = new File(getLocalCacheParent(), name + ".cache");
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(f2));
            outputStreamWriter.write(json);
            outputStreamWriter.close();

            return f2;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * If the animation doesn't exist in the cache, null will be returned.
     * <p>
     * Once the animation is successfully parsed, {@link #loadTempFile(String)} must be
     * called to move the file from a temporary location to its permanent cache location so it can
     * be used in the future.
     */
    @Nullable
    @WorkerThread
    private Pair<AXrFileExtension, File> fetch(String url) {
        File cachedFile = null;
        for (AXrFileExtension extension : AXrLottie.getSupportedFileExtensions().values()) {
            File file = new File(getNetworkCacheParent(), filenameForUrl(url, extension, false));
            if (file.exists()) {
                cachedFile = file;
                break;
            }
        }
        if (cachedFile == null) {
            return null;
        }

        AXrFileExtension extension = AXrLottie.getSupportedFileExtensions().get(cachedFile.getAbsolutePath().substring(cachedFile.getAbsolutePath().lastIndexOf(".")).toLowerCase());
        if (extension == null)
            extension = new AXrFileExtension(cachedFile.getAbsolutePath().substring(cachedFile.getAbsolutePath().lastIndexOf("."))) {
            };

        return new Pair<>(extension, cachedFile);
    }

    /**
     * Writes an InputStream from a network response to a temporary file. If the file successfully parses
     * to an composition, {@link #loadTempFile(String)}  should be called to move the file
     * to its final location for future cache hits.
     */
    public File writeTempCacheFile(String url, InputStream stream, AXrFileExtension extension) throws IOException {
        String fileName = filenameForUrl(url, extension, true);
        File file = new File(getNetworkCacheParent(), fileName);
        try {
            OutputStream output = new FileOutputStream(file);
            //noinspection TryFinallyCanBeTryWithResources
            try {
                byte[] buffer = new byte[1024];
                int read;

                while ((read = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }

                output.flush();
            } catch (Exception e) {
                Log.e(TAG, "writeTempCacheFile: ", e);
            } finally {
                output.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "writeTempCacheFile: ", e);
        } finally {
            stream.close();
        }
        return file;
    }

    /**
     * If the file created by {@link #writeTempCacheFile(String, InputStream, AXrFileExtension)} was successfully parsed,
     * this should be called to remove the temporary part of its name which will allow it to be a cache hit in the future.
     */
    public File loadTempFile(String url) {
        String fileName = filenameForUrl(url, JsonFileExtension.JSON, true);
        File file = new File(getNetworkCacheParent(), fileName);
        String newFileName = file.getAbsolutePath().replace(".temp", "");
        File newFile = new File(newFileName);
        file.renameTo(newFile);
        return newFile;
    }

    /**
     * Returns the cache file for the given url if it exists.
     * Returns null if neither exist.
     */
    public File getCachedFile(String url, AXrFileExtension extension, boolean isTemp) {
        return new File(getNetworkCacheParent(), filenameForUrl(url, extension, isTemp));
    }

    public File getNetworkCacheParent() {
        File file = networkCacheDir;
        if (file.isFile()) {
            file.delete();
        }
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public File getLocalCacheParent() {
        File file = localCacheDir;
        if (file.isFile()) {
            file.delete();
        }
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    private String filenameForUrl(String url, AXrFileExtension extension, boolean isTemp) {
        return "lottie_cache_" + url.replaceAll("\\W+", "") + (isTemp ? extension.tempExtension() : extension.extension);
    }
}