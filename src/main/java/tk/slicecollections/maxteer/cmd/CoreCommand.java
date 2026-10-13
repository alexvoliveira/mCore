package tk.slicecollections.maxteer.cmd;

import tk.slicecollections.maxteer.utils.MessageUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.Core;

/**
 * @author Maxter
 */
public class CoreCommand extends Commands {

  public CoreCommand() {
    super("mcore", "mc");
  }

  @Override
  public void perform(CommandSender sender, String label, String[] args) {
    if (sender instanceof Player) {
      Player player = (Player) sender;
      if (!player.hasPermission("mcore.admin")) {
        player.sendMessage(MessageUtils.info("Versão §a§l" + Core.getInstance().getDescription().getVersion() + " §7• Desenvolvido por §fMaxteer§7."));
        return;
      }

      if (args.length == 0) {
        player.sendMessage(MessageUtils.info("§a§lAJUDA §7• COMANDOS DO MCORE\n§a/mc atualizar §7➜ Instruções para atualização manual.\n§a/mc converter §7➜ Instruções para migração segura do banco.\n "));
        return;
      }

      String action = args[0];
      if (action.equalsIgnoreCase("atualizar")) {
        player.sendMessage(MessageUtils.info("A consulta automática de versões foi removida."));
        player.sendMessage(MessageUtils.info("Use somente o JAR publicado nos canais oficiais do projeto."));
        player.sendMessage(MessageUtils.info("Substitua o arquivo manualmente e reinicie o servidor."));
      } else if (action.equalsIgnoreCase("converter")) {
        player.sendMessage(MessageUtils.error("MIGRAÇÃO BLOQUEADA", "A conversão online foi removida para evitar riscos aos dados."));
        player.sendMessage(MessageUtils.info("Use docs/DATA-MIGRATION.md e valide o relatório antes de ativar o banco migrado."));
      } else {
        player.sendMessage(MessageUtils.info("§a§lAJUDA §7• COMANDOS DO MCORE\n§a/mc atualizar §7➜ Instruções para atualização manual.\n§a/mc converter §7➜ Instruções para migração segura do banco.\n "));
      }
    } else {
      sender.sendMessage(MessageUtils.error("COMANDO", "Apenas jogadores podem utilizar este comando."));
    }
  }
}
