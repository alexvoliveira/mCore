package tk.slicecollections.maxteer.database.cache;

import java.util.Locale;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Bounded, thread-safe cache for player role/name lookups.
 */
public class RoleCache {

  private static final long CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);
  private static final int MAX_CACHE_ENTRIES = 10000;
  private static final ConcurrentMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

  public static void setCache(String playerName, String role, String realName) {
    if (playerName == null || playerName.isEmpty()) {
      return;
    }

    String key = normalize(playerName);
    CACHE.put(key, new CacheEntry(
      System.currentTimeMillis() + CACHE_TTL_MILLIS, role, realName
    ));
    enforceLimit(key);
  }

  public static String get(String playerName) {
    CacheEntry entry = getValidEntry(playerName);
    return entry == null ? null : entry.role + " : " + entry.realName;
  }

  public static boolean isPresent(String playerName) {
    return getValidEntry(playerName) != null;
  }

  public static void invalidate(String playerName) {
    if (playerName != null && !playerName.isEmpty()) {
      CACHE.remove(normalize(playerName));
    }
  }

  public static TimerTask clearCache() {
    return new TimerTask() {
      @Override
      public void run() {
        long now = System.currentTimeMillis();
        CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis <= now);
      }
    };
  }

  private static CacheEntry getValidEntry(String playerName) {
    if (playerName == null || playerName.isEmpty()) {
      return null;
    }

    String key = normalize(playerName);
    CacheEntry entry = CACHE.get(key);
    if (entry != null && entry.expiresAtMillis <= System.currentTimeMillis()) {
      CACHE.remove(key, entry);
      return null;
    }
    return entry;
  }

  private static String normalize(String playerName) {
    return playerName.toLowerCase(Locale.ROOT);
  }

  private static void enforceLimit(String newestKey) {
    if (CACHE.size() <= MAX_CACHE_ENTRIES) {
      return;
    }

    long now = System.currentTimeMillis();
    CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis <= now);

    while (CACHE.size() > MAX_CACHE_ENTRIES) {
      boolean removed = false;
      for (String key : CACHE.keySet()) {
        if (!key.equals(newestKey) && CACHE.remove(key) != null) {
          removed = true;
          break;
        }
      }
      if (!removed) {
        break;
      }
    }
  }

  private static final class CacheEntry {
    private final long expiresAtMillis;
    private final String role;
    private final String realName;

    private CacheEntry(long expiresAtMillis, String role, String realName) {
      this.expiresAtMillis = expiresAtMillis;
      this.role = role;
      this.realName = realName;
    }
  }
}
