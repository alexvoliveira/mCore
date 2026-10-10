package tk.slicecollections.maxteer.utils;

import org.bukkit.entity.Player;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import tk.slicecollections.maxteer.nms.NMS;
import tk.slicecollections.maxteer.plugin.MPlugin;
import tk.slicecollections.maxteer.plugin.logger.MLogger;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * @author Maxter
 */
public class SliceUpdater {

  private MPlugin plugin;
  private MLogger logger;
  private int resourceId;
  public boolean canDownload;

  public SliceUpdater(MPlugin plugin, int resourceId) {
    this.plugin = plugin;
    this.logger = ((MLogger) this.plugin.getLogger()).getModule("UPDATER");
    this.resourceId = resourceId;
  }

  public void run() {
    this.logger.info("Procurando updates...");
    String latest = getVersion(this.resourceId);
    String current = this.plugin.getDescription().getVersion();
    if (latest == null) {
      logger.severe("Nao foi possivel buscar updates.");
    } else {
      int siteVersion = Integer.parseInt(latest.replace(".", ""));
      int pluginVersion = Integer.parseInt(current.replace(".", ""));

      if (pluginVersion >= siteVersion) {
        this.logger.info("Plugin se encontra em sua ultima versao.");
      } else {
        this.logger.warning("Encontramos um update. Utilize /mc atualizar para prosseguir.");
        UPDATER = this;
        this.canDownload = true;
      }
    }
  }

  public void downloadUpdate(Player player) {
    this.canDownload = false;
    player.sendMessage("§cDownload automático desativado: o serviço não fornece uma assinatura/hash confiável para validar o JAR.");
    player.sendMessage("§7Baixe a versão por um canal oficial e substitua o arquivo após verificar sua autenticidade.");
  }

  public static SliceUpdater UPDATER;

  private static String getVersion(int resourceId) {
    try {
      HttpsURLConnection connection = (HttpsURLConnection) new URL("https://www.slicecollections.com.br/api/v1/plugin/" + resourceId).openConnection();
      connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
      JSONObject object = (JSONObject) new JSONParser().parse(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
      return ((JSONObject) object.get("version")).get("id").toString();
    } catch (Exception ex) {
      return null;
    }
  }
}
