package local.superopt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Moves the player-data compression and atomic file replace off the server thread.
 *
 * <p>Vanilla builds the NBT tag and creates the temporary file on the server thread; only the
 * {@code NbtIo.writeCompressed} and {@code Util.safeReplaceFile} calls are handed to a single
 * background thread, which keeps writes for the same player ordered. A bounded queue plus a
 * synchronous fallback keeps the failure and shutdown paths safe: if anything goes wrong, the
 * original write is performed inline before the save call returns.
 */
public final class AsyncPlayerSave {
    private static final int MAX_QUEUE = 512;
    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);
    private static final AtomicInteger QUEUED = new AtomicInteger();
    private static final AtomicInteger FAILED = new AtomicInteger();

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SuperOpt-PlayerSave");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private AsyncPlayerSave() {
    }

    /** Called on the server thread from the redirected {@code NbtIo.writeCompressed} call. */
    public static void capture(CompoundTag tag, Path tempFile) {
        PENDING.set(new Pending(tag, tempFile));
    }

    /** Called on the server thread from the redirected {@code Util.safeReplaceFile} call. */
    public static void submit(Path finalFile, Path tempFile, Path backupFile) {
        Pending pending = take();
        if (pending == null || !ViewCapConfig.isAsyncPlayerSaveEnabled() || SHUTDOWN.get()
                || QUEUED.get() >= MAX_QUEUE) {
            runSynchronously(pending, finalFile, tempFile, backupFile);
            return;
        }
        QUEUED.incrementAndGet();
        try {
            EXECUTOR.execute(() -> {
                try {
                    writeAndReplace(pending, finalFile, tempFile, backupFile);
                } catch (Throwable throwable) {
                    FAILED.incrementAndGet();
                    SuperOpt.LOGGER.warn("Async player save failed for {}", finalFile, throwable);
                } finally {
                    QUEUED.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException rejected) {
            QUEUED.decrementAndGet();
            runSynchronously(pending, finalFile, tempFile, backupFile);
        }
    }

    /** Drops a captured tag that was never submitted (defensive, e.g. if vanilla aborted the save). */
    public static void discard() {
        PENDING.remove();
    }

    public static boolean hasPending() {
        return PENDING.get() != null;
    }

    public static int queued() {
        return QUEUED.get();
    }

    public static int failed() {
        return FAILED.get();
    }

    private static Pending take() {
        Pending pending = PENDING.get();
        PENDING.remove();
        return pending;
    }

    private record Pending(CompoundTag tag, Path tempFile) {
    }

    private static void writeAndReplace(Pending pending, Path finalFile, Path tempFile, Path backupFile)
            throws IOException {
        NbtIo.writeCompressed(pending.tag(), pending.tempFile());
        Util.safeReplaceFile(finalFile, tempFile, backupFile);
    }

    private static void runSynchronously(Pending pending, Path finalFile, Path tempFile, Path backupFile) {
        try {
            if (pending != null) {
                NbtIo.writeCompressed(pending.tag(), pending.tempFile());
            }
            Util.safeReplaceFile(finalFile, tempFile, backupFile);
        } catch (IOException exception) {
            SuperOpt.LOGGER.warn("Synchronous player save failed for {}", finalFile, exception);
        }
    }

    /** Blocks until every queued save has been written. Called once during shutdown. */
    public static void drain() {
        PENDING.remove();
        if (!SHUTDOWN.compareAndSet(false, true)) {
            return;
        }
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(60, TimeUnit.SECONDS)) {
                SuperOpt.LOGGER.warn("Timed out waiting for {} queued player saves, {} already failed",
                        QUEUED.get(), FAILED.get());
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            EXECUTOR.shutdownNow();
        }
    }
}
