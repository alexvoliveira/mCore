package tk.slicecollections.maxteer.utils;

import org.bukkit.entity.Player;
import java.util.logging.Level;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import tk.slicecollections.maxteer.plugin.MPlugin;
import tk.slicecollections.maxteer.plugin.logger.MLogger;

import javax.net.ssl.HttpsURLConnection;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * @author Maxter
 */
public class SliceUpdater {

  private MPlugin plugin;
  private MLogger logger;
  private int resourceId;
  public volatile boolean canDownload;
  public volatile boolean checkCompleted;
  public volatile boolean checkFailed;
  public volatile boolean updateAvailable;
  public static volatile SliceUpdater UPDATER;

  private static final Pattern VERSION_PATTERN = Pattern.compile("[0-9]+(?:\\.[0-9]+)*");

  public SliceUpdater(MPlugin plugin, int resourceId) {
    this.plugin = plugin;
    this.logger = ((MLogger) this.plugin.getLogger()).getModule("UPDATER");
    this.resourceId = resourceId;
    UPDATER = this;
  }

  public void run() {
    this.checkCompleted = false;
    this.checkFailed = false;
    this.updateAvailable = false;
    this.canDownload = false;
    this.logger.info("Procurando updates...");

    final String latest = getVersion(this.resourceId);
    if (latest == null || !VERSION_PATTERN.matcher(latest).matches()) {
      this.checkFailed = true;
      this.checkCompleted = true;
      this.logger.warning("Não foi possível verificar atualizações com uma resposta válida; consulte os logs e use somente um artefato verificado.");
      return;
    }

    final String current = this.plugin.getDescription().getVersion();
    if (!VERSION_PATTERN.matcher(current).matches()) {
      this.checkFailed = true;
      this.checkCompleted = true;
      this.logger.warning("A versão atual do mCore não possui um formato reconhecido para comparação.");
      return;
    }

    try {
      int siteVersion = Integer.parseInt(latest.replace(".", ""));
      int pluginVersion = Integer.parseInt(current.replace(".", ""));
      this.updateAvailable = pluginVersion < siteVersion;
      this.canDownload = this.updateAvailable;
      this.checkCompleted = true;

      if (this.updateAvailable) {
        this.logger.warning("Existe uma atualização. A instalação automática está desativada; consulte /mc atualizar para instruções manuais.");
      } else {
        this.logger.info("Plugin se encontra em sua última versão.");
      }
    } catch (NumberFormatException ex) {
      this.checkFailed = true;
      this.checkCompleted = true;
      this.logger.log(Level.WARNING, "A versão retornada pelo serviço não pôde ser comparada.", ex);
    }
  }

  public void downloadUpdate(Player player) {
    this.canDownload = false;
    player.sendMessage("§cA instalação automática está desativada por segurança.");
    player.sendMessage("§7Baixe manualmente o JAR de um canal autorizado e confirme a autenticidade do artefato antes de substituir o plugin.");
  }

  private String getVersion(int resourceId) {
    HttpsURLConnection connection = null;
    try {
      connection = (HttpsURLConnection) new URL("https://www.slicecollections.com.br/api/v1/plugin/" + resourceId).openConnection();
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", "mCore/4.1");

      try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
        Object parsed = new JSONParser().parse(reader);
        if (!(parsed instanceof JSONObject)) {
          throw new IllegalStateException("Update service response is not a JSON object.");
        }
        Object versionValue = ((JSONObject) parsed).get("version");
        if (!(versionValue instanceof JSONObject)) {
          throw new IllegalStateException("Update service response is missing the version object.");
        }
        Object id = ((JSONObject) versionValue).get("id");
        if (!(id instanceof String)) {
          throw new IllegalStateException("Update service response is missing a string version ID.");
        }
        return (String) id;
      }
    } catch (Exception ex) {
      this.logger.log(Level.WARNING, "Não foi possível consultar o serviço de versões do mCore.", ex);
      return null;
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }
}
