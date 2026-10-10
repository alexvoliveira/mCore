package tk.slicecollections.maxteer.database.data.container;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import tk.slicecollections.maxteer.booster.Booster;
import tk.slicecollections.maxteer.booster.Booster.BoosterType;
import tk.slicecollections.maxteer.database.data.DataContainer;
import tk.slicecollections.maxteer.database.data.interfaces.AbstractContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Maxter
 */
@SuppressWarnings("unchecked")
public class BoostersContainer extends AbstractContainer {

  public BoostersContainer(DataContainer dataContainer) {
    super(dataContainer);
    JSONObject boosters = this.dataContainer.getAsJsonObject();
    boolean changed = false;
    if (!(boosters.get("0") instanceof JSONArray)) {
      boosters.put("0", new JSONArray());
      changed = true;
    }
    if (!(boosters.get("1") instanceof JSONArray)) {
      boosters.put("1", new JSONArray());
      changed = true;
    }
    if (!(boosters.get("2") instanceof String)) {
      boosters.put("2", "none");
      changed = true;
    }
    if (changed) {
      this.dataContainer.set(boosters.toString());
    }
    boosters.clear();
  }

  public boolean enable(Booster booster) {
    if (this.getEnabled() != null) {
      return false;
    }

    this.removeBooster(BoosterType.PRIVATE, booster);
    JSONObject boosters = this.dataContainer.getAsJsonObject();
    boosters.put("2", booster.getMultiplier() + ":" + (System.currentTimeMillis() + TimeUnit.HOURS.toMillis(booster.getHours())));
    this.dataContainer.set(boosters.toString());
    boosters.clear();
    return true;
  }

  public void addBooster(BoosterType type, double multiplier, long hours) {
    JSONObject boosters = this.dataContainer.getAsJsonObject();
    ((JSONArray) boosters.get(String.valueOf(type.ordinal()))).add(multiplier + ":" + hours);
    this.dataContainer.set(boosters.toString());
    boosters.clear();
  }

  public void removeBooster(BoosterType type, Booster booster) {
    JSONObject boosters = this.dataContainer.getAsJsonObject();
    ((JSONArray) boosters.get(String.valueOf(type.ordinal()))).remove(booster.getMultiplier() + ":" + booster.getHours());
    this.dataContainer.set(boosters.toString());
    boosters.clear();
  }

  public List<Booster> getBoosters(BoosterType type) {
    List<Booster> list = new ArrayList<>();
    JSONArray boosters = (JSONArray) this.dataContainer.getAsJsonObject().get(String.valueOf(type.ordinal()));
    for (Object obj : boosters) {
      if (obj instanceof String) {
        list.add(Booster.parseBooster((String) obj));
      }
    }

    return list;
  }

  public String getEnabled() {
    JSONObject boosters = this.dataContainer.getAsJsonObject();
    Object raw = boosters.get("2");
    if (!(raw instanceof String) || "none".equals(raw)) {
      boosters.clear();
      return null;
    }

    String[] splitted = ((String) raw).split(":");
    try {
      if (splitted.length == 2) {
        double multiplier = Double.parseDouble(splitted[0]);
        long expires = Long.parseLong(splitted[1]);
        if (Double.isFinite(multiplier) && multiplier > 0 && expires > System.currentTimeMillis()) {
          boosters.clear();
          return multiplier + ":" + expires;
        }
      }
    } catch (NumberFormatException ex) {
      // Invalid persisted booster data is reset below; it must not crash player interactions.
    }

    boosters.put("2", "none");
    this.dataContainer.set(boosters.toString());
    boosters.clear();
    return null;
  }
}
