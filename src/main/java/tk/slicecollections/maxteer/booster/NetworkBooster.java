package tk.slicecollections.maxteer.booster;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Maxter
 */
@Getter
@AllArgsConstructor
public class NetworkBooster {

  private String booster;
  private double multiplier;
  private long expires;

  public void gc() {
    this.booster = null;
    this.multiplier = 0.0D;
    this.expires = 0L;
  }

}
