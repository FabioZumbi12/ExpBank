package br.net.fabiozumbi12.expbank;

import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;

@ConfigSerializable
public class ConfigCategory {

    @Setting(value = "sign-prefix")
    public String sign_prefix = "[ExpBank]";
    @Setting(value = "max-level", comment = "Max levels allowed on banks.")
    public int max_level = 10000;
    @Setting(value = "level-per-interaction", comment = "The amount of levels will be added/removed from bank per interaction.")
    public int level_per_interaction = 100;
    @Setting(value = "shift-once", comment = "If shift pressed should put/remove all from/to bank.")
    public boolean shift_once = true;
    @Setting(value = "message-onjoin", comment = "If the player has exp to get on login, show message?")
    public boolean message_onjoin = false;

    @Setting
    public stringsCat strings = new stringsCat();

    @ConfigSerializable
    public static class stringsCat {
        @Setting(value = "bank-not-yours")
        public String bank_not_yours = "&cThis Experience bank is not yours.";
        @Setting(value = "bank-created")
        public String bank_created = "&aExperience Bank created with success.";
        @Setting(value = "exp-added")
        public String exp_added = "&6{value} &aexperience(s) added to Bank.";
        @Setting(value = "exp-removed")
        public String exp_removed = "&6{value} &aexperience(s) taken out from Bank.";
        @Setting(value = "exp-removed-other")
        public String exp_removed_other = "&6{value} &aexperience(s) taken out from Bank and sent to &6{other}&a.";
        @Setting(value = "no-permission")
        public String no_permission = "&cYou don't have permission to create or use Experience Banks.";
        @Setting(value = "cant-place")
        public String cant_place = "&cYou can't place experience banks here!";
        @Setting(value = "max-deposit")
        public String max_deposit = "&cYou can't deposit more experience here. The limit is &6{value}&c!";
        @Setting(value = "other-sign-break")
        public String other_sign_break = "&aAn Experience Bank in your name was broken and you get back &6{value} &alevels.";
        @Setting(value = "command-reload")
        public String command_reload = "&a[ExpBank] Reloaded with success!";
        @Setting(value = "command-give")
        public String command_give = "&aGiven &6{value} &alevel(s) to &6{player}&a!";
    }
}
