package tk.slicecollections.maxteer.bungee.cmd;

import tk.slicecollections.maxteer.utils.MessageUtils;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import tk.slicecollections.maxteer.bungee.party.BungeeParty;
import tk.slicecollections.maxteer.bungee.party.BungeePartyManager;
import tk.slicecollections.maxteer.player.role.Role;
import tk.slicecollections.maxteer.utils.StringUtils;

/**
 * @author Maxter
 */
public class PartyChatCommand extends Commands {

  public PartyChatCommand() {
    super("p");
  }

  @Override
  public void perform(CommandSender sender, String[] args) {
    if (!(sender instanceof ProxiedPlayer)) {
      sender.sendMessage(TextComponent.fromLegacyText(MessageUtils.error("COMANDO", "Apenas jogadores podem utilizar este comando.")));
      return;
    }

    ProxiedPlayer player = (ProxiedPlayer) sender;
    if (args.length == 0) {
      player.sendMessage(TextComponent.fromLegacyText(MessageUtils.error("SINTAXE", "Utilize /p [mensagem] para conversar com a sua Party.")));
      return;
    }

    BungeeParty party = BungeePartyManager.getMemberParty(player.getName());
    if (party == null) {
      player.sendMessage(TextComponent.fromLegacyText(MessageUtils.error("PARTY", "Você não pertence a uma Party.")));
      return;
    }

    party.broadcast("§7[§d§lPARTY§7] ➜ " + Role.getPrefixed(player.getName()) + "§7: " + StringUtils.join(args, " "));
  }
}
