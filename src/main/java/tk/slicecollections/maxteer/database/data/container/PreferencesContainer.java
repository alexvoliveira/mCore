package tk.slicecollections.maxteer.database.data.container;

import org.json.simple.JSONObject;
import tk.slicecollections.maxteer.database.data.DataContainer;
import tk.slicecollections.maxteer.database.data.interfaces.AbstractContainer;
import tk.slicecollections.maxteer.player.enums.BloodAndGore;
import tk.slicecollections.maxteer.player.enums.PlayerVisibility;
import tk.slicecollections.maxteer.player.enums.PrivateMessages;
import tk.slicecollections.maxteer.player.enums.ProtectionLobby;

/**
 * Preferences are stored as ordinals in a JSON object. Older or malformed rows
 * are normalized to the existing default (ordinal zero) instead of crashing
 * player login or menu interactions.
 */
@SuppressWarnings("unchecked")
public class PreferencesContainer extends AbstractContainer {

  private static final String[] KEYS = {"pv", "pm", "bg", "pl"};

  public PreferencesContainer(DataContainer dataContainer) {
    super(dataContainer);
    JSONObject preferences = this.dataContainer.getAsJsonObject();
    boolean changed = false;
    for (String key : KEYS) {
      Object value = preferences.get(key);
      if (!(value instanceof Number)) {
        preferences.put(key, 0L);
        changed = true;
      } else {
        Number number = (Number) value;
        long ordinal = number.longValue();
        if (ordinal < 0 || ordinal > 1 || number.doubleValue() != ordinal) {
          preferences.put(key, 0L);
          changed = true;
        }
      }
    }
    if (changed) {
      this.dataContainer.set(preferences.toString());
    }
    preferences.clear();
  }

  private static long ordinal(JSONObject preferences, String key) {
    Object value = preferences.get(key);
    if (value instanceof Number) {
      Number number = (Number) value;
      long ordinal = number.longValue();
      if (ordinal >= 0 && ordinal <= 1 && number.doubleValue() == ordinal) {
        return ordinal;
      }
    }
    return 0L;
  }

  public void changePlayerVisibility() {
    JSONObject preferences = this.dataContainer.getAsJsonObject();
    preferences.put("pv", PlayerVisibility.getByOrdinal(ordinal(preferences, "pv")).next().ordinal());
    this.dataContainer.set(preferences.toString());
    preferences.clear();
  }

  public void changePrivateMessages() {
    JSONObject preferences = this.dataContainer.getAsJsonObject();
    preferences.put("pm", PrivateMessages.getByOrdinal(ordinal(preferences, "pm")).next().ordinal());
    this.dataContainer.set(preferences.toString());
    preferences.clear();
  }

  public void changeBloodAndGore() {
    JSONObject preferences = this.dataContainer.getAsJsonObject();
    preferences.put("bg", BloodAndGore.getByOrdinal(ordinal(preferences, "bg")).next().ordinal());
    this.dataContainer.set(preferences.toString());
    preferences.clear();
  }

  public void changeProtectionLobby() {
    JSONObject preferences = this.dataContainer.getAsJsonObject();
    preferences.put("pl", ProtectionLobby.getByOrdinal(ordinal(preferences, "pl")).next().ordinal());
    this.dataContainer.set(preferences.toString());
    preferences.clear();
  }

  public PlayerVisibility getPlayerVisibility() {
    return PlayerVisibility.getByOrdinal(ordinal(this.dataContainer.getAsJsonObject(), "pv"));
  }

  public PrivateMessages getPrivateMessages() {
    return PrivateMessages.getByOrdinal(ordinal(this.dataContainer.getAsJsonObject(), "pm"));
  }

  public BloodAndGore getBloodAndGore() {
    return BloodAndGore.getByOrdinal(ordinal(this.dataContainer.getAsJsonObject(), "bg"));
  }

  public ProtectionLobby getProtectionLobby() {
    return ProtectionLobby.getByOrdinal(ordinal(this.dataContainer.getAsJsonObject(), "pl"));
  }
}
