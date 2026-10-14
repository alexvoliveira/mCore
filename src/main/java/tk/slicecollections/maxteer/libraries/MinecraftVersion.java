package tk.slicecollections.maxteer.libraries;

import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minecraft server version parsed from a Bukkit version or legacy package name.
 */
public final class MinecraftVersion {

    private static final Pattern LEGACY = Pattern.compile("^v?(\\d+)[._](\\d+)[._]R(\\d+)$");
    private static final Pattern SEMANTIC = Pattern.compile("^(?:v)?(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private final int major;
    private final int minor;
    private final int build;
    private final int compareId;

    public MinecraftVersion(Server server) {
        this(extractVersion(server));
    }

    public MinecraftVersion(String version) {
        int[] numbers = parseVersion(version);
        this.major = numbers[0];
        this.minor = numbers[1];
        this.build = numbers[2];
        this.compareId = Integer.parseInt(Integer.toString(major) + minor + build);
    }

    public MinecraftVersion(int major, int minor, int build) {
        this.major = major;
        this.minor = minor;
        this.build = build;
        this.compareId = Integer.parseInt(Integer.toString(major) + minor + build);
    }

    public boolean lowerThan(MinecraftVersion version) {
        return compareTo(version) <= 0;
    }

    public boolean newerThan(MinecraftVersion version) {
        return compareTo(version) >= 0;
    }

    public boolean inRange(MinecraftVersion latest, MinecraftVersion olded) {
        return compareTo(latest) <= 0 && compareTo(olded) >= 0;
    }

    private int compareTo(MinecraftVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        if (result == 0) result = Integer.compare(build, other.build);
        return result;
    }

    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public int getBuild() { return build; }
    public int getCompareId() { return compareId; }

    /**
     * Retains the legacy package-style representation used by existing callers.
     * For Minecraft 1.21.1 this returns v1_21_R1.
     */
    public String getVersion() {
        return String.format("v%s_%s_R%s", major, minor, build);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MinecraftVersion)) return false;
        MinecraftVersion other = (MinecraftVersion) obj;
        return major == other.major && minor == other.minor && build == other.build;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, build);
    }

    @Override
    public String toString() {
        return getVersion();
    }

    private static String extractVersion(Server server) {
        String packageName = server.getClass().getPackage().getName();
        String[] packageParts = packageName.split("\\.");
        if (packageParts.length > 3 && LEGACY.matcher(packageParts[3]).matches()) {
            return packageParts[3];
        }

        String bukkitVersion = server.getBukkitVersion();
        if (bukkitVersion != null && SEMANTIC.matcher(bukkitVersion).find()) {
            return bukkitVersion;
        }
        throw new IllegalStateException("Cannot determine Minecraft version from server: " + packageName);
    }

    private static int[] parseVersion(String version) {
        if (version == null) {
            throw new IllegalStateException("Minecraft version cannot be null");
        }

        String candidate = version.trim();
        Matcher legacy = LEGACY.matcher(candidate);
        if (legacy.matches()) {
            return new int[] {
                Integer.parseInt(legacy.group(1)),
                Integer.parseInt(legacy.group(2)),
                Integer.parseInt(legacy.group(3))
            };
        }

        Matcher semantic = SEMANTIC.matcher(candidate);
        if (semantic.find()) {
            return new int[] {
                Integer.parseInt(semantic.group(1)),
                Integer.parseInt(semantic.group(2)),
                semantic.group(3) == null ? 0 : Integer.parseInt(semantic.group(3))
            };
        }

        throw new IllegalStateException("Unsupported Minecraft server version: " + version);
    }

    private static MinecraftVersion currentVersion;

    public static MinecraftVersion getCurrentVersion() {
        if (currentVersion == null) {
            currentVersion = new MinecraftVersion(Bukkit.getServer());
        }
        return currentVersion;
    }
}
