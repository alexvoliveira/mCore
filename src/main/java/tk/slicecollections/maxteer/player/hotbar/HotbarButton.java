package tk.slicecollections.maxteer.player.hotbar;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Maxter
 */
@AllArgsConstructor
public class HotbarButton {
  
  private int slot;
  @Getter
  private HotbarAction action;
  @Getter
  private String icon;
  
  public int getSlot() {
    return this.slot - 1;
  }
  
}
