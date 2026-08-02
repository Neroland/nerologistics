package za.co.neroland.nerologistics.world;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.telemetry.NeroLogisticsTelemetry;

/**
 * Crash-proof loading + last-known-good backups for NeroLogistics {@link SavedData} (ported from
 * Nerospace's {@code SavedDataRecovery}, MC-NEROSPACE-H).
 *
 * <p>Vanilla's {@code SavedDataStorage.computeIfAbsent} reads the {@code data/<id>.dat} file on first
 * access and lets any {@code IOException} (corrupt/unreadable disk, truncated file) propagate
 * unchecked — and because {@code ShipmentState} is fetched from the per-tick shipment driver, one bad
 * file would crash the server tick loop on every tick. This helper is the single accessor all
 * NeroLogistics saved-data managers route through, with a three-step recovery ladder:</p>
 *
 * <ol>
 *   <li><b>Primary:</b> vanilla storage ({@code computeIfAbsent}).</li>
 *   <li><b>Backup:</b> on a read failure, restore from the manager's NeroLogistics-written
 *       last-known-good file ({@code data/<namespace>_<path>_backup.dat} in the same dimension
 *       folder), so state rolls back to the last backup instead of being lost.</li>
 *   <li><b>Fresh:</b> if the backup is missing/unreadable too, start with a fresh instance —
 *       degraded but playable, never a hard crash.</li>
 * </ol>

 * <p>Whichever recovery step wins is installed into the storage cache via {@code set} (so
 * subsequent ticks hit the cache instead of re-reading the bad file) and marked dirty (so a clean
 * primary file is rewritten at the next level save). The failure is logged once and reported as a
 * handled (non-fatal) telemetry event through the existing scrubbed pipeline.</p>
 *
 * <p><b>Backup writing</b> piggybacks on {@link #get}: at most every {@value #DIRTY_INTERVAL_MS} ms
 * while the manager is dirty (state changed) or every {@value #IDLE_INTERVAL_MS} ms otherwise, the
 * manager is re-encoded with its own {@link SavedDataType#codec() codec}; the file is only rewritten
 * when the content actually changed (hash compare), via write-temp-then-atomic-rename so a crash
 * mid-write can never corrupt the backup itself. Best-effort by design: a backup failure only logs
 * at debug and never affects gameplay.</p>
 *
 * <p><b>Privacy (POPIA/GDPR):</b> the backup holds exactly the same fields as the vanilla primary
 * file, inside the same world save. Shipment state carries no player identifiers; the erased-owner
 * tombstone list (see {@code RocketCargoPortBlockEntity}) holds only UUIDs whose erasure was
 * requested, and is covered by {@link #backupNow} being invoked right after an erasure request so
 * the backup never lags behind an erasure.</p>
 */
public final class SavedDataRecovery {

    /** While dirty (state changed since the last vanilla save), refresh the backup at most this often. */
    private static final long DIRTY_INTERVAL_MS = 5_000L;
    /** Re-check interval when not dirty (still hash-compared, so unchanged state writes nothing). */
    private static final long IDLE_INTERVAL_MS = 5L * 60_000L;

    /** Server-thread only (all call sites are server-tick/command paths), so plain maps suffice. */
    private static final Map<String, Long> lastAttemptMs = new HashMap<>();
    private static final Map<String, Integer> lastWrittenHash = new HashMap<>();

    private SavedDataRecovery() {
    }

    /**
     * Fetches {@code type} from {@code level}'s data storage, recovering via backup-then-fresh if the
     * primary file cannot be read, and opportunistically refreshing this manager's backup file.
     *
     * @param name stable non-identifying label for logs/telemetry and the backup file name
     *             (e.g. {@code "nerologistics:shipments"})
     */
    public static <T extends SavedData> T get(ServerLevel level, SavedDataType<T> type,
                                              Supplier<T> fallback, String name) {
        T instance;
        try {
            instance = level.getDataStorage().computeIfAbsent(type);
        } catch (Exception e) {
            instance = recover(level, type, fallback, name, e);
        }
        if (instance != null) { // ecj nullness: computeIfAbsent's generic return is unannotated
            maybeBackup(level, type, instance, name, false);
        }
        return instance;
    }

    /**
     * Forces an immediate backup refresh (bypassing the interval throttle, still hash-compared).
     * Called by the data-erasure hook so anonymisation reaches the backup file right away.
     */
    public static <T extends SavedData> void backupNow(ServerLevel level, SavedDataType<T> type,
                                                       T instance, String name) {
        maybeBackup(level, type, instance, name, true);
    }

    /** Drop the per-server throttle/hash caches (called from the server-stopped reset hook). */
    public static void clearAll() {
        lastAttemptMs.clear();
        lastWrittenHash.clear();
    }

    // --- Recovery -----------------------------------------------------------

    private static <T extends SavedData> T recover(ServerLevel level, SavedDataType<T> type,
                                                   Supplier<T> fallback, String name, Exception failure) {
        T restored = readBackup(level, type, name, failure);
        boolean fromBackup = restored != null;
        T instance;
        if (restored != null) {
            instance = restored;
        } else {
            T fresh = fallback.get();
            if (fresh == null) {
                throw new IllegalStateException("SavedData fallback supplier returned null for " + name);
            }
            instance = fresh;
        }
        try {
            // Install into the storage cache (stops the per-tick re-read of the bad file) and mark
            // dirty so the next level save replaces the corrupt primary with a clean one.
            level.getDataStorage().set(type, instance);
            instance.setDirty();
        } catch (Exception inner) {
            failure.addSuppressed(inner);
        }
        NeroLogisticsCommon.LOGGER.error(
                "[NeroLogistics] Could not read saved data '{}' (corrupt or unreadable file); {}. "
                        + "A clean file will be rewritten at the next save.",
                name,
                fromBackup ? "restored the last-known-good backup" : "no usable backup — starting with fresh data",
                failure);
        NeroLogisticsTelemetry.captureHandledException(failure);
        return instance;
    }

    private static <T extends SavedData> T readBackup(ServerLevel level, SavedDataType<T> type,
                                                      String name, Exception primaryFailure) {
        Path file = backupFile(level, name);
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            Optional<T> parsed = tag.read("data", type.codec());
            return parsed.orElse(null);
        } catch (Exception e) {
            primaryFailure.addSuppressed(e); // backup unreadable too (e.g. same failing disk)
            return null;
        }
    }

    // --- Backup writing -----------------------------------------------------

    private static <T extends SavedData> void maybeBackup(ServerLevel level, SavedDataType<T> type,
                                                          T instance, String name, boolean force) {
        String key = level.dimension() + "|" + name;
        long now = System.currentTimeMillis();
        if (!force) {
            Long last = lastAttemptMs.get(key);
            long interval = instance.isDirty() ? DIRTY_INTERVAL_MS : IDLE_INTERVAL_MS;
            if (last != null && now - last < interval) {
                return;
            }
        }
        lastAttemptMs.put(key, now);
        try {
            CompoundTag tag = new CompoundTag();
            tag.store("data", type.codec(), instance);
            int hash = tag.hashCode();
            Integer previous = lastWrittenHash.get(key);
            if (previous != null && previous == hash) {
                return; // unchanged since the last written backup
            }
            Path file = backupFile(level, name);
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            NbtIo.writeCompressed(tag, tmp);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            lastWrittenHash.put(key, hash);
        } catch (Exception e) {
            // Best-effort: a backup failure must never affect gameplay, and on a genuinely failing
            // disk this would fire repeatedly — keep it at debug.
            NeroLogisticsCommon.LOGGER.debug("[NeroLogistics] Could not write saved-data backup '{}'", name, e);
        }
    }

    /** {@code <dimension folder>/data/<namespace>_<path>_backup.dat} — colon-free for Windows. */
    private static Path backupFile(ServerLevel level, String name) {
        Path dimensionRoot = DimensionType.getStorageFolder(
                level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT));
        String fileName = name.replace(':', '_').replace('/', '_') + "_backup.dat";
        return dimensionRoot.resolve("data").resolve(fileName);
    }
}
