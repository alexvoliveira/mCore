package tk.slicecollections.maxteer.bungee.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.event.EventHandler;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import tk.slicecollections.maxteer.bungee.Bungee;
import tk.slicecollections.maxteer.bungee.party.BungeeParty;
import tk.slicecollections.maxteer.bungee.party.BungeePartyManager;
import tk.slicecollections.maxteer.database.Database;
import tk.slicecollections.maxteer.reflection.Accessors;
import tk.slicecollections.maxteer.reflection.acessors.FieldAccessor;
import tk.slicecollections.maxteer.utils.StringUtils;

import javax.sql.rowset.CachedRowSet;
import java.util.*;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * @author Maxter
 */
public class Listeners implements Listener {

  private static final Map<String, Object> PROPERTY_BACKUP = new HashMap<>();
  private static final Map<String, Long> PROTECTION_LOBBY = new ConcurrentHashMap<>();
  private static final Map<String, Boolean> TELL_CACHE = new ConcurrentHashMap<>(), PROTECTION_CACHE = new ConcurrentHashMap<>();
  private static final Set<String> PREFERENCE_LOOKUPS = ConcurrentHashMap.newKeySet();
  private static final FieldAccessor<Map> COMMAND_MAP = Accessors.getField(PluginManager.class, "commandMap", Map.class);

  @EventHandler
  public void onPlayerDisconnect(PlayerDisconnectEvent evt) {
    TELL_CACHE.remove(evt.getPlayer().getName().toLowerCase());
    PROTECTION_CACHE.remove(evt.getPlayer().getName().toLowerCase());
    PROTECTION_LOBBY.remove(evt.getPlayer().getName().toLowerCase());
    PROPERTY_BACKUP.remove(evt.getPlayer().getName().toLowerCase());
  }

  @EventHandler
  public void onPostLogin(PostLoginEvent evt) {
    String name = evt.getPlayer().getName();
    String key = name.toLowerCase(Locale.ROOT);
    TELL_CACHE.remove(key);
    PROTECTION_CACHE.remove(key);
    this.loadPreferenceCache(name, "pm", TELL_CACHE);
    this.loadPreferenceCache(name, "pl", PROTECTION_CACHE);
  }

  @EventHandler
  public void onPluginMessage(PluginMessageEvent evt) {
    if (evt.getSender() instanceof Server && evt.getReceiver() instanceof ProxiedPlayer) {
      if (evt.getTag().equalsIgnoreCase("mCore")) {
        ProxiedPlayer player = (ProxiedPlayer) evt.getReceiver();

        ByteArrayDataInput in = ByteStreams.newDataInput(evt.getData());
        String subChannel = in.readUTF();
        if (subChannel.equalsIgnoreCase("FAKE_SKIN")) {
          Object profile = this.getLoginProfile(player);
          if (profile != null) {
            String name = player.getName().toLowerCase(Locale.ROOT);
            try {
              String[] data = in.readUTF().split(":");
              PROPERTY_BACKUP.put(name, this.getProperties(profile));
              this.modifyProperties(profile, data);
            } catch (Exception ex) {
              Object properties = PROPERTY_BACKUP.remove(name);
              if (properties != null) {
                try {
                  this.setProperties(profile, properties);
                } catch (Exception restoreError) {
                  ex.addSuppressed(restoreError);
                }
              }
              Bungee.getInstance().getLogger().log(Level.WARNING, "Could not apply fake skin to " + player.getName() + ".", ex);
            }
          }
        }
      }
    }
  }

  @EventHandler(priority = (byte) 128)
  public void onServerConnected(ServerConnectedEvent evt) {
    ProxiedPlayer player = evt.getPlayer();

    BungeeParty party = BungeePartyManager.getLeaderParty(player.getName());
    if (party != null) {
      party.sendData(evt.getServer().getInfo());
    }

    if (Bungee.isFake(player.getName())) {
      String skin = Bungee.getSkin(player.getName());
      // Enviar dados desse jogador que está utilizando Fake para o servidor processar.
      ByteArrayDataOutput out = ByteStreams.newDataOutput();
      out.writeUTF("FAKE");
      out.writeUTF(player.getName());
      out.writeUTF(Bungee.getFake(player.getName()));
      out.writeUTF(StringUtils.stripColors(Bungee.getRole(player.getName()).getName()));
      out.writeUTF(skin);
      evt.getServer().sendData("mCore", out.toByteArray());

      try {
        Object profile = this.getLoginProfile(player);
        if (profile != null) {
          this.modifyProperties(profile, skin.split(":"));
        }
      } catch (Exception ex) {
        Bungee.getInstance().getLogger().log(Level.WARNING, "Could not apply fake skin to " + player.getName() + ".", ex);
      }
    }
  }

  @EventHandler(priority = (byte) 128)
  public void onChat(ChatEvent evt) {
    if (evt.getSender() instanceof ProxiedPlayer) {
      if (evt.isCommand()) {
        ProxiedPlayer player = (ProxiedPlayer) evt.getSender();
        String[] args = evt.getMessage().replace("/", "").split(" ");

        String command = args[0];
        if (COMMAND_MAP.get(ProxyServer.getInstance().getPluginManager()).containsKey("lobby") && command.equals("lobby") && this
          .hasProtectionLobby(player.getName().toLowerCase())) {
          long last = PROTECTION_LOBBY.getOrDefault(player.getName().toLowerCase(), 0L);
          if (last > System.currentTimeMillis()) {
            PROTECTION_LOBBY.remove(player.getName().toLowerCase());
            return;
          }

          evt.setCancelled(true);
          PROTECTION_LOBBY.put(player.getName().toLowerCase(), System.currentTimeMillis() + 3000);
          player.sendMessage(TextComponent.fromLegacyText("§aVocê tem certeza? Utilize /lobby novamente para voltar ao lobby."));
        } else if (COMMAND_MAP.get(ProxyServer.getInstance().getPluginManager()).containsKey("tell") && args.length > 1 && command.equals("tell") && !args[1]
          .equalsIgnoreCase(player.getName())) {
          String recipient = args[1].toLowerCase(Locale.ROOT);
          Boolean canReceive = TELL_CACHE.get(recipient);
          if (canReceive == null) {
            evt.setCancelled(true);
            this.loadPreferenceCache(args[1], "pm", TELL_CACHE);
            player.sendMessage(TextComponent.fromLegacyText("§7As preferências do destinatário estão sendo verificadas. Tente novamente em instantes."));
          } else if (!canReceive) {
            evt.setCancelled(true);
            player.sendMessage(TextComponent.fromLegacyText("§cEste usuário desativou as mensagens privadas."));
          }
        }
      }
    }
  }

  /*
   * BungeeCord 1.8 exposes skin properties through implementation classes rather
   * than its public API. Keep that compatibility boundary isolated in reflection
   * so this module can compile against the published API without packaging internals.
   */
  private Object getLoginProfile(ProxiedPlayer player) {
    try {
      Object pendingConnection = player.getPendingConnection();
      Method method = pendingConnection.getClass().getMethod("getLoginProfile");
      return method.invoke(pendingConnection);
    } catch (ReflectiveOperationException ex) {
      Bungee.getInstance().getLogger().log(Level.WARNING,
        "The running BungeeCord implementation does not expose its legacy login profile as expected.", ex);
      return null;
    }
  }

  private Object getProperties(Object profile) throws ReflectiveOperationException {
    return profile.getClass().getMethod("getProperties").invoke(profile);
  }

  private Method findSetProperties(Object profile) throws NoSuchMethodException {
    for (Method method : profile.getClass().getMethods()) {
      if (method.getName().equals("setProperties")
          && method.getParameterTypes().length == 1
          && method.getParameterTypes()[0].isArray()) {
        return method;
      }
    }
    throw new NoSuchMethodException("BungeeCord login profile does not expose setProperties(array).");
  }

  private void setProperties(Object profile, Object properties) throws ReflectiveOperationException {
    findSetProperties(profile).invoke(profile, properties);
  }

  private void modifyProperties(Object profile, String[] data) throws ReflectiveOperationException {
    if (data == null || data.length < 2) {
      throw new IllegalArgumentException("Fake skin payload must contain texture value and signature.");
    }

    Object current = this.getProperties(profile);
    Method setter = this.findSetProperties(profile);
    Class<?> propertyType = setter.getParameterTypes()[0].getComponentType();
    List<Object> properties = new ArrayList<>();

    if (current != null) {
      if (!current.getClass().isArray()) {
        throw new IllegalStateException("BungeeCord login properties are not an array.");
      }
      for (int i = 0; i < Array.getLength(current); i++) {
        Object property = Array.get(current, i);
        Object propertyName = property.getClass().getMethod("getName").invoke(property);
        if (!(propertyName instanceof String) || !((String) propertyName).equalsIgnoreCase("textures")) {
          properties.add(property);
        }
      }
    }

    Constructor<?> constructor = propertyType.getConstructor(String.class, String.class, String.class);
    properties.add(constructor.newInstance("textures", data[0], data[1]));

    Object replacement = Array.newInstance(propertyType, properties.size());
    for (int i = 0; i < properties.size(); i++) {
      Array.set(replacement, i, properties.get(i));
    }
    setter.invoke(profile, replacement);
  }

  private boolean hasProtectionLobby(String name) {
    String key = name.toLowerCase(Locale.ROOT);
    Boolean cached = PROTECTION_CACHE.get(key);
    if (cached != null) {
      return cached;
    }

    this.loadPreferenceCache(name, "pl", PROTECTION_CACHE);
    // Preserve the safe default while the asynchronous preference read completes.
    return true;
  }

  private void loadPreferenceCache(String name, String preference, Map<String, Boolean> cache) {
    String normalizedName = name.toLowerCase(Locale.ROOT);
    String lookupKey = preference + ":" + normalizedName;
    if (!PREFERENCE_LOOKUPS.add(lookupKey)) {
      return;
    }

    Database database = Database.getInstance();
    if (database == null) {
      PREFERENCE_LOOKUPS.remove(lookupKey);
      return;
    }

    database.getPreferenceAsync(name, preference, true).whenComplete((value, failure) -> {
      try {
        if (failure != null) {
          Bungee.getInstance().getLogger().log(Level.WARNING,
            "Could not load preference '" + preference + "' for " + name + ".", failure);
          return;
        }
        cache.put(normalizedName, value);
      } finally {
        PREFERENCE_LOOKUPS.remove(lookupKey);
      }
    });
  }
}
