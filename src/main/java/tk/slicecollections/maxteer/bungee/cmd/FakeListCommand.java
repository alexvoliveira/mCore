package tk.slicecollections.maxteer.bungee.cmd;

import tk.slicecollections.maxteer.utils.MessageUtils;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import tk.slicecollections.maxteer.bungee.Bungee;

import java.util.List;

/**
 * @author Maxter
 */
public class FakeListCommand extends Commands {

  public FakeListCommand() {
    super("fakel");
  }

  @Override
  public void perform(CommandSender sender, String[] args) {
    if (!(sender instanceof ProxiedPlayer)) {
      sender.sendMessage(TextComponent.fromLegacyText(MessageUtils.error("COMANDO", "Apenas jogadores podem utilizar este comando.")));
      return;
    }

    ProxiedPlayer player = (ProxiedPlayer) sender;
    if (!player.hasPermission("mcore.cmd.fakelist")) {
      player.sendMessage(TextComponent.fromLegacyText(MessageUtils.error("PERMISSÃO", "Você não possui permissão para utilizar este comando.")));
      return;
    }

    List<String> nicked = Bungee.listNicked();
    StringBuilder sb = new StringBuilder();
    for (int index = 0; index < nicked.size(); index++) {
      sb.append("§7• §f").append(Bungee.getFake(nicked.get(index))).append(" §7é na verdade §a").append(nicked.get(index)).append(index + 1 == nicked.size() ? "" : "\n");
    }

    nicked.clear();
    if (sb.length() == 0) {
      sb.append("§7Nenhum jogador está utilizando um nickname falso.");
    }

    player.sendMessage(TextComponent.fromLegacyText(MessageUtils.info("§a§lNICKNAMES FALSOS\n\n" + sb.toString())));
  }
}
