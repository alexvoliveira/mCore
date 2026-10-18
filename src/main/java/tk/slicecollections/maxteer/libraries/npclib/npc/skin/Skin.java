package tk.slicecollections.maxteer.libraries.npclib.npc.skin;

import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.libraries.profile.Mojang;
import tk.slicecollections.maxteer.libraries.profile.InvalidMojangException;
import tk.slicecollections.maxteer.nms.NMS;

/**
 * @author Maxter
 */
public class Skin {

  private String value, signature;

  private Skin(String value, String signature) {
    this.value = value;
    this.signature = signature;
  }

  private Skin fetch(String name) {
    if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) {
      return this;
    }

    try {
      String id = Mojang.getUUID(name);
      if (id != null) {
        String property = Mojang.getSkinProperty(id);
        if (property != null) {
          String[] parts = property.split(" : ", 3);
          if (parts.length == 3 && "textures".equals(parts[0])
              && !parts[1].isEmpty() && !parts[2].isEmpty()) {
            this.value = parts[1];
            this.signature = parts[2];
          }
        }
      }
    } catch (InvalidMojangException e) {
      NMS.LOGGER.warning("Could not fetch skin for " + name + ": " + e.getMessage());
    }
    return this;
  }

  public String getValue() {
    return this.value;
  }

  public String getSignature() {
    return this.signature;
  }

  public boolean isValid() {
    return this.value != null && !this.value.isEmpty()
        && this.signature != null && !this.signature.isEmpty();
  }

  public void apply(SkinnableEntity entity) {
    if (entity != null && this.isValid()) {
      NMS.setValueAndSignature(entity.getEntity(), this.value, this.signature);
    }
  }

  /** Applies a signed texture to a real player and uses the existing 1.8_R3 refresh. */
  public void apply(Player player) {
    if (player == null || !this.isValid()) {
      throw new IllegalArgumentException("A valid signed skin and player are required.");
    }
    NMS.setValueAndSignature(player, this.value, this.signature);
    NMS.refreshPlayer(player);
  }

  public static Skin fromName(String name) {
    return new Skin(null, null).fetch(name);
  }

  public static Skin fromData(String value, String signature) {
    return new Skin(value, signature);
  }
}
