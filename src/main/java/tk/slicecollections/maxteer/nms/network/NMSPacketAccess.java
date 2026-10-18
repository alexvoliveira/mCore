package tk.slicecollections.maxteer.nms.network;

import java.lang.reflect.Field;

/**
 * Small reflection helper for fields in the version-specific 1.8_R3 packets.
 */
public final class NMSPacketAccess {

  private NMSPacketAccess() {}

  public static Object get(Object target, Class<?> fieldType, int index) {
    try {
      return findField(target, fieldType, index).get(target);
    } catch (IllegalAccessException ex) {
      throw new IllegalStateException("Could not read a native packet field.", ex);
    }
  }

  public static void set(Object target, Class<?> fieldType, int index, Object value) {
    try {
      findField(target, fieldType, index).set(target, value);
    } catch (IllegalAccessException ex) {
      throw new IllegalStateException("Could not write a native packet field.", ex);
    }
  }

  private static Field findField(Object target, Class<?> fieldType, int index) {
    if (target == null || fieldType == null || index < 0) {
      throw new IllegalArgumentException("A packet, field type and non-negative index are required.");
    }

    int remaining = index;
    for (Class<?> current = target.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        if (fieldType.equals(field.getType()) || fieldType.isAssignableFrom(field.getType())) {
          if (remaining-- == 0) {
            field.setAccessible(true);
            return field;
          }
        }
      }
    }

    throw new IllegalArgumentException("No field of type " + fieldType.getName()
        + " at index " + index + " in " + target.getClass().getName() + ".");
  }
}
