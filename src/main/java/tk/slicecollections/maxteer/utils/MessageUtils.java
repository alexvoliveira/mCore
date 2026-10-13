package tk.slicecollections.maxteer.utils;

import java.util.Locale;

/** Consistent, lightweight chat formatting for mCore messages. */
public final class MessageUtils {
  private static final String PREFIX = "§7[§a§lMCORE§7] §7➜ ";

  private MessageUtils() {
  }

  public static String info(String message) {
    return PREFIX + "§7" + message;
  }

  public static String success(String keyword, String message) {
    return PREFIX + "§a§l" + normalize(keyword) + " §7" + message;
  }

  public static String error(String keyword, String message) {
    return PREFIX + "§c§l" + normalize(keyword) + " §7" + message;
  }

  public static String critical(String keyword, String message) {
    return PREFIX + "§4§l" + normalize(keyword) + " §7" + message;
  }

  private static String normalize(String keyword) {
    return keyword == null ? "" : keyword.toUpperCase(Locale.ROOT);
  }
}
