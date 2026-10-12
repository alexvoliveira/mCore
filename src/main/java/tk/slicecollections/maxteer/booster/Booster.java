package tk.slicecollections.maxteer.booster;

import tk.slicecollections.maxteer.database.Database;
import tk.slicecollections.maxteer.player.Profile;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Maxter
 */
public class Booster {

  private static final long NETWORK_CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(3);
  private static final int MAX_NETWORK_CACHE_ENTRIES = 64;
  private static final ConcurrentMap<String, CachedNetworkBooster> CACHE = new ConcurrentHashMap<>();

  private double multiplier;
  private long hours;

  public Booster(double multiplier, long hours) {
    this.multiplier = multiplier;
    this.hours = hours;
  }

  public void gc() {
    this.multiplier = 0.0D;
    this.hours = 0;
  }

  public double getMultiplier() {
    return this.multiplier;
  }

  public long getHours() {
    return this.hours;
  }

  @Override
  public String toString() {
    return this.multiplier + ":" + this.hours;
  }

  public static Booster parseBooster(String toParse) {
    String[] splitted = toParse.split(":");
    if (splitted.length < 2) {
      return null;
    }

    return new Booster(Double.parseDouble(splitted[0]), Long.parseLong(splitted[1]));
  }

  public enum BoosterType {
    PRIVATE,
    NETWORK
  }

  public static void setupBoosters() {
    Database.getInstance().setupBoosters();
  }

  public static boolean setNetworkBooster(String id, Profile profile, Booster booster) {
    if (id == null || id.isEmpty()) {
      return false;
    }

    NetworkBooster current = getNetworkBooster(id);
    if (current != null) {
      return false;
    }

    profile.getBoostersContainer().removeBooster(BoosterType.NETWORK, booster);
    NetworkBooster created = new NetworkBooster(profile.getName(), booster.getMultiplier(),
      System.currentTimeMillis() + TimeUnit.HOURS.toMillis(booster.getHours()));
    Database.getInstance().setBooster(id, created.getBooster(), created.getMultiplier(), created.getExpires());
    cache(id, created);
    return true;
  }

  public static NetworkBooster getNetworkBooster(String id) {
    if (id == null || id.isEmpty()) {
      return null;
    }

    long nowNanos = System.nanoTime();
    long nowMillis = System.currentTimeMillis();
    CachedNetworkBooster cached = CACHE.get(id);
    if (cached != null) {
      if (cached.expiresAtNanos - nowNanos > 0L
          && (cached.booster == null || cached.booster.getExpires() > nowMillis)) {
        return copy(cached.booster);
      }
      CACHE.remove(id, cached);
    }

    NetworkBooster loaded = Database.getInstance().getBooster(id);
    if (loaded != null && loaded.getExpires() <= nowMillis) {
      loaded = null;
    }

    // Cache negative lookups briefly too, avoiding repeated SQL when no booster is active.
    cache(id, loaded);
    return copy(loaded);
  }

  private static void cache(String id, NetworkBooster booster) {
    CACHE.put(id, new CachedNetworkBooster(copy(booster), System.nanoTime() + NETWORK_CACHE_TTL_NANOS));
    if (CACHE.size() <= MAX_NETWORK_CACHE_ENTRIES) {
      return;
    }

    long now = System.nanoTime();
    CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos - now <= 0L);
    while (CACHE.size() > MAX_NETWORK_CACHE_ENTRIES) {
      boolean removed = false;
      for (String key : CACHE.keySet()) {
        if (!key.equals(id) && CACHE.remove(key) != null) {
          removed = true;
          break;
        }
      }
      if (!removed) {
        break;
      }
    }
  }

  private static NetworkBooster copy(NetworkBooster booster) {
    return booster == null ? null
      : new NetworkBooster(booster.getBooster(), booster.getMultiplier(), booster.getExpires());
  }

  private static final class CachedNetworkBooster {
    private final NetworkBooster booster;
    private final long expiresAtNanos;

    private CachedNetworkBooster(NetworkBooster booster, long expiresAtNanos) {
      this.booster = booster;
      this.expiresAtNanos = expiresAtNanos;
    }
  }
}
