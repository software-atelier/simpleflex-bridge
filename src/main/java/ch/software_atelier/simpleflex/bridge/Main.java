package ch.software_atelier.simpleflex.bridge;

import ch.software_atelier.simpleflex.SimpleFlexBase;
import ch.software_atelier.simpleflex.conf.DomainConfig;
import ch.software_atelier.simpleflex.conf.GlobalConfig;
import ch.software_atelier.simpleflex.conf.WebAppConfig;
import java.util.List;

public final class Main {
    private Main() {}
    public static void main(String[] args) {
        Config config = Config.from(System.getenv());
        GlobalConfig global = new GlobalConfig();
        global.setPort(config.port);
        DomainConfig domain = new DomainConfig("DEFAULT");
        domain.setDefaultWebAppConfig(new WebAppConfig(BridgeApp.class.getName(), ""));
        new SimpleFlexBase(global, List.of(domain)).start();
    }
}
