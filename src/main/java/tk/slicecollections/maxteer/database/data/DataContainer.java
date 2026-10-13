package tk.slicecollections.maxteer.database.data;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import tk.slicecollections.maxteer.database.data.interfaces.AbstractContainer;
import tk.slicecollections.maxteer.reflection.Accessors;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Maxter
 */
public class DataContainer {

  private Object value;
  private boolean updated;

  public DataContainer(Object value) {
    this.value = value;
  }

  public synchronized void gc() {
    this.value = null;
    if (this.containerMap != null) {
      this.containerMap.values().forEach(AbstractContainer::gc);
      this.containerMap.clear();
      this.containerMap = null;
    }
  }

  public synchronized void set(Object value) {
    if (this.value == null || !this.value.equals(value)) {
      this.setUpdated(true);
    }
    this.value = value;
  }

  public synchronized void setUpdated(boolean updated) {
    this.updated = updated;
  }

  public synchronized void addInt(int amount) {
    this.set(getAsInt() + amount);
  }

  public synchronized void addLong(long amount) {
    this.set(getAsLong() + amount);
  }

  public synchronized void addDouble(double amount) {
    this.set(getAsDouble() + amount);
  }

  public synchronized void removeInt(int amount) {
    this.set(getAsInt() - amount);
  }

  public synchronized void removeLong(long amount) {
    this.set(getAsLong() - amount);
  }

  public synchronized void removeDouble(double amount) {
    this.set(getAsDouble() - amount);
  }

  public synchronized Object get() {
    return this.value;
  }

  public int getAsInt() {
    return Integer.parseInt(this.getAsString());
  }

  public long getAsLong() {
    return Long.parseLong(this.getAsString());
  }

  public double getAsDouble() {
    return Double.parseDouble(this.getAsString());
  }

  public synchronized String getAsString() {
    return this.value.toString();
  }

  public boolean getAsBoolean() {
    return Boolean.parseBoolean(this.getAsString());
  }

  public JSONObject getAsJsonObject() {
    try {
      return (JSONObject) new JSONParser().parse(this.getAsString());
    } catch (Exception ex) {
      throw new IllegalArgumentException("\"" + value + "\" is not a JsonObject: ", ex);
    }
  }

  public JSONArray getAsJsonArray() {
    try {
      return (JSONArray) new JSONParser().parse(this.getAsString());
    } catch (Exception ex) {
      throw new IllegalArgumentException("\"" + value + "\" is not a JsonArray: ", ex);
    }
  }

  public synchronized boolean isUpdated() {
    return this.updated;
  }

  private Map<Class<? extends AbstractContainer>, AbstractContainer> containerMap = new HashMap<>();

  @SuppressWarnings("unchecked")
  public synchronized <T extends AbstractContainer> T getContainer(Class<T> containerClass) {
    if (!this.containerMap.containsKey(containerClass)) {
      this.containerMap.put(containerClass, Accessors.getConstructor(containerClass, DataContainer.class).newInstance(this));
    }

    return (T) this.containerMap.get(containerClass);
  }
}
