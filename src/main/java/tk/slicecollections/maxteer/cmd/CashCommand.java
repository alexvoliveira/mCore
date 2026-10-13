package tk.slicecollections.maxteer.cmd;

import tk.slicecollections.maxteer.utils.MessageUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.cash.CashException;
import tk.slicecollections.maxteer.cash.CashManager;
import tk.slicecollections.maxteer.player.role.Role;
import tk.slicecollections.maxteer.utils.StringUtils;

/**
 * @author Maxter
 */
public class CashCommand extends Commands {

  public CashCommand() {
    super("cash");
  }

  @Override
  public void perform(CommandSender sender, String label, String[] args) {
    if (args.length == 0) {
      if (sender instanceof Player) {
        sender.sendMessage(MessageUtils.info("Saldo de CASH: §a§l" + StringUtils.formatNumber(CashManager.getCash(sender.getName()))));
        return;
      }

      sender.sendMessage(MessageUtils.info("§a§lAJUDA §7• COMANDOS DE CASH\n§a/cash set [jogador] [quantia] §7➜ Definir o saldo.\n§a/cash add [jogador] [quantia] §7➜ Adicionar saldo.\n§a/cash remove [jogador] [quantia] §7➜ Remover saldo.\n "));
      return;
    }

    if (!sender.hasPermission("mcore.cmd.cash")) {
      sender.sendMessage(MessageUtils.info("Saldo de CASH: §a§l" + StringUtils.formatNumber(CashManager.getCash(sender.getName()))));
      return;
    }

    String action = args[0];
    if (!action.equalsIgnoreCase("set") && !action.equalsIgnoreCase("add") && !action.equalsIgnoreCase("remove")) {
      sender.sendMessage(MessageUtils.info("§a§lAJUDA §7• COMANDOS DE CASH\n§a/cash set [jogador] [quantia] §7➜ Definir o saldo.\n§a/cash add [jogador] [quantia] §7➜ Adicionar saldo.\n§a/cash remove [jogador] [quantia] §7➜ Remover saldo.\n "));
      return;
    }

    if (args.length <= 2) {
      sender.sendMessage(MessageUtils.error("SINTAXE", "Utilize /cash " + action + " [jogador] [quantia]."));
      return;
    }

    long amount = 0L;
    try {
      if (args[2].startsWith("-")) {
        throw new NumberFormatException();
      }

      amount = Long.parseLong(args[2]);
    } catch (NumberFormatException ex) {
      sender.sendMessage(MessageUtils.error("VALOR INVÁLIDO", "Informe uma quantia numérica e positiva."));
      return;
    }

    try {
      switch (action.toLowerCase()) {
        case "set":
          CashManager.setCash(args[1], amount);
          sender.sendMessage(MessageUtils.success("CASH ATUALIZADO", "saldo de " + Role.getColored(args[1]) + " definido para §a§l" + StringUtils.formatNumber(amount) + "§7."));
          break;
        case "add":
          CashManager.addCash(args[1], amount);
          sender.sendMessage(MessageUtils.success("CASH ADICIONADO", "§a§l" + StringUtils.formatNumber(amount) + " §7adicionado ao saldo de " + Role.getColored(args[1]) + "§7."));
          break;
        case "remove":
          CashManager.removeCash(args[1], amount);
          sender.sendMessage(MessageUtils.success("CASH REMOVIDO", "§a§l" + StringUtils.formatNumber(amount) + " §7removido do saldo de " + Role.getColored(args[1]) + "§7."));
      }
    } catch (CashException ex) {
      sender.sendMessage(MessageUtils.error("JOGADOR OFFLINE", "O jogador precisa estar conectado para alterar o CASH."));
    }
  }
}
