package tk.slicecollections.maxteer.libraries;

import com.google.common.base.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;

/** Parses both legacy CraftBukkit package versions and modern Bukkit version strings. */
public class MinecraftVersion {

  private static final Pattern VERSION_PATTERN =
    Pattern.compile("(?i)^v?(\\d+)[._](\\d+)[._]R?(\\d+)(?:[-+].*)?$");
  private static final Pattern LEGACY_PACKAGE_PATTERN =
    Pattern.compile("(?i)(?:^|\\.)v(\\d+_\\d+_R\\d+)$");

  private final int major;
  private final int minor;
  private final int build;
  private final int compareId;
  private final boolean legacyPackageFormat;

  public MinecraftVersion(Server server) {
    this(extractVersion(server));
  }

  /**
   * Accepts both legacy package forms (for example, v1_8_R3) and regular
   * Minecraft versions (for example, 1.21.1 or 1.21.1-R0.1-SNAPSHOT).
   */
  public MinecraftVersion(String version) {
    String value = version == null ? "" : version.trim();
    Matcher matcher = VERSION_PATTERN.matcher(value);
    if (!matcher.matches()) {
      throw new IllegalStateException("Unsupported Minecraft version format: " + value);
    }

    this.major = parseInt(matcher.group(1));
    this.minor = parseInt(matcher.group(2));
    this.build = parseInt(matcher.group(3));
    this.compareId = parseInt(this.major + "" + this.minor + "" + this.build);
    this.legacyPackageFormat = value.matches("(?i)^v?\\d+[_\\.]\\d+[_\\.]R\\d+$");
  }

  /** Legacy NMS-package constructor retained for source compatibility. */
  public MinecraftVersion(int major, int minor, int build) {
    this.major = major;
    this.minor = minor;
    this.build = build;
    this.compareId = parseInt(major + "" + minor + "" + build);
    this.legacyPackageFormat = true;
  }

  public boolean lowerThan(MinecraftVersion version) {
    return this.compareId <= version.getCompareId();
  }

  public boolean newerThan(MinecraftVersion version) {
    return this.compareId >= version.getCompareId();
  }

  public boolean inRange(MinecraftVersion latest, MinecraftVersion olded) {
    return this.compareId <= latest.getCompareId() && this.compareId >= olded.getCompareId();
  }

  public int getMajor() {
    return this.major;
  }

  public int getMinor() {
    return this.minor;
  }

  public int getBuild() {
    return this.build;
  }

  public int getCompareId() {
    return this.compareId;
  }

  /** Returns the original NMS-style label only for legacy package formats. */
  public String getVersion() {
    return legacyPackageFormat
      ? String.format("v%s_%s_R%s", this.major, this.minor, this.build)
      : String.format("%s.%s.%s", this.major, this.minor, this.build);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MinecraftVersion)) {
      return false;
    }

    MinecraftVersion other = (MinecraftVersion) obj;
    return this.major == other.major && this.minor == other.minor && this.build == other.build;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.major, this.minor, this.build);
  }

  @Override
  public String toString() {
    return getVersion();
  }

  private static String extractVersion(Server server) {
    String packageName = server.getClass().getPackage().getName();
    Matcher matcher = LEGACY_PACKAGE_PATTERN.matcher(packageName);
    if (matcher.find()) {
      return "v" + matcher.group(1);
    }

    String bukkitVersion = server.getBukkitVersion();
    if (bukkitVersion == null || bukkitVersion.trim().isEmpty()) {
      throw new IllegalStateException("The Bukkit server did not provide a version string.");
    }
    return bukkitVersion;
  }

  private static MinecraftVersion currentVersion;

  public static MinecraftVersion getCurrentVersion() {
    if (currentVersion == null) {
      currentVersion = new MinecraftVersion(Bukkit.getServer());
    }

    return currentVersion;
  }
}
