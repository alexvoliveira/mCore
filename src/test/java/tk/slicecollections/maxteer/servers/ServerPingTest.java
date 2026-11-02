package tk.slicecollections.maxteer.servers;

import com.google.gson.JsonParser;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class ServerPingTest {

  @Test
  public void extractsTextAndExtraFromLegacyChatComponent() throws Exception {
    ServerPing ping = new ServerPing("127.0.0.1", 25565);
    setStatusResponse(ping,
      "{\"description\":{\"text\":\"§aLobby \",\"extra\":[{\"text\":\"1.8.8\"}]},"
        + "\"players\":{\"max\":20,\"online\":3}}"
    );

    processData(ping);

    assertEquals("§aLobby 1.8.8", ping.getMotd());
  }

  @Test
  public void returnsEmptyMotdWhenDescriptionIsMissing() throws Exception {
    ServerPing ping = new ServerPing("127.0.0.1", 25565);
    setStatusResponse(ping, "{\"players\":{\"max\":20,\"online\":3}}");

    processData(ping);

    assertEquals("", ping.getMotd());
  }

  private void setStatusResponse(ServerPing ping, String response) throws Exception {
    Field jsonField = ServerPing.class.getDeclaredField("json");
    jsonField.setAccessible(true);
    jsonField.set(ping, new JsonParser().parse(response).getAsJsonObject());
  }

  private void processData(ServerPing ping) throws Exception {
    Method processData = ServerPing.class.getDeclaredMethod("processData");
    processData.setAccessible(true);
    processData.invoke(ping);
  }
}
