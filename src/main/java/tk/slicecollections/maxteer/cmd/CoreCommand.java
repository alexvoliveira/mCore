package tk.slicecollections.maxteer.cmd;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.utils.SliceUpdater;

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
        player.sendMessage("§6" + Core.getInstance().getName() + " §7[" + Core.getInstance().getDescription().getVersion() + "] §f- §7Criado por §5Maxteer§7.");
        return;
      }

      if (args.length == 0) {
        player.sendMessage(" \n§3/mc atualizar §f- §7Atualizar o mCore.\n§3/mc converter §f- §7Instruções para migração segura do banco.\n ");
        return;
      }

      String action = args[0];
      if (action.equalsIgnoreCase("atualizar")) {
        SliceUpdater updater = SliceUpdater.UPDATER;
        if (updater == null || !updater.checkCompleted) {
          player.sendMessage("§7A verificação de versão ainda não foi concluída. Consulte os logs e tente novamente.");
        } else if (updater.checkFailed) {
          player.sendMessage("§cNão foi possível verificar a versão. Não instale JARs cuja autenticidade não tenha sido confirmada.");
        } else if (!updater.updateAvailable) {
          player.sendMessage("§aO plugin já se encontra em sua última versão.");
        } else {
          updater.downloadUpdate(player);
        }
      } else if (action.equalsIgnoreCase("converter")) {
        player.sendMessage("§cA conversão online foi removida para evitar migração insegura.");
        player.sendMessage("§7Use o procedimento offline em docs/DATA-MIGRATION.md e valide o relatório antes de ativar o banco migrado.");
      } else {
        player.sendMessage(" \n§3/mc atualizar §f- §7Atualizar o mCore.\n§3/mc converter §f- §7Instruções para migração segura do banco.\n ");
      }
    } else {
      sender.sendMessage("§cApenas jogadores podem utilizar este comando.");
    }
  }
}
