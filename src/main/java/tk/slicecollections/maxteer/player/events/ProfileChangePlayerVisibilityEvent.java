package tk.slicecollections.maxteer.player.events;

import lombok.Getter;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.player.enums.PlayerVisibility;

/**
 * @author Maxter
 */
public class ProfileChangePlayerVisibilityEvent extends Event {

  @Getter
  private Profile profile;
  @Getter
  private PlayerVisibility playerVisibility;
  
  public ProfileChangePlayerVisibilityEvent(Profile profile) {
    this.profile = profile;
    this.playerVisibility = profile.getPreferencesContainer().getPlayerVisibility();
  }
  
  public Player getPlayer() {
    return this.profile.getPlayer();
  }
  
  @Override
  public HandlerList getHandlers() {
    return HANDLER_LIST;
  }
  
  private static final HandlerList HANDLER_LIST = new HandlerList();

  public static HandlerList getHandlerList() {
    return HANDLER_LIST;
  }
}
