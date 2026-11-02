package tk.slicecollections.maxteer.servers;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ServerPingTest {

  @Test
  public void extractsTextAndExtraFromLegacyChatComponent() {
    JsonElement description = new JsonParser().parse(
      "{\"text\":\"§aLobby \",\"extra\":[{\"text\":\"1.8.8\"}]}"
    );

    assertEquals("§aLobby 1.8.8", ServerPing.extractMotd(description));
  }

  @Test
  public void returnsEmptyMotdWhenDescriptionIsMissing() {
    assertEquals("", ServerPing.extractMotd(null));
  }
}
